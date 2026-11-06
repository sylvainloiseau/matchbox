package fr.cnrs.lacito.matchbox.model.project;

import fr.cnrs.lacito.matchbox.corpus.Corpus;
import fr.cnrs.lacito.matchbox.corpus.Text;
import fr.cnrs.lacito.matchbox.corpus.TextId;
import fr.cnrs.lacito.matchbox.grammarapi.GrammarPlugin;
import fr.cnrs.lacito.matchbox.grammarapi.TextParser;
import fr.cnrs.lacito.matchbox.model.DocumentChange;
import fr.cnrs.lacito.matchbox.model.DocumentListener;
import fr.cnrs.lacito.matchbox.model.DocumentOptions;
import fr.cnrs.lacito.matchbox.model.TextDocument;
import fr.cnrs.lacito.matchbox.model.EditJournal;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;

/**
 * Model class representing a notebook project : a set of documents / directory,
 * with definition of file types, that are applied different grammar patterns.
 */
public class Project {

    public static final String DEFAULT_PLUGIN_ID = "notebook-grammar";
    /** Regular expression of the "All files" file type of new projects: every file with an extension. */
    public static final String ALL_FILES_REGEX = ".*\\..*";
    private String name;
    private String description;

    private List<FileType> fileTypes = new ArrayList<>();
    private final Corpus corpus;

    private List<GrammarPlugin> plugins;

    private boolean isDesynchronized = true;

    private static final Logger logger = Logger.getLogger(Project.class.getName());
    private Executor applyExecutor = Runnable::run;
    private final DocumentOptions documentOptions;
    /** Files of the project definition that could not be read (with the reason). */
    private final List<String> loadProblems = new ArrayList<>();
    private ExecutorService parserExecutor;

    /**
     * True when the definition (name, description, files and directories, file types, display
     * styles, inherited context, assistant setting) differs from the project file: false after loading, creating and
     * saving, true after a change.
     */
    private final SimpleBooleanProperty definitionChanged = new SimpleBooleanProperty(false);
    private final ReadOnlyIntegerWrapper definitionRevision = new ReadOnlyIntegerWrapper(0);

    /** How the objects of each grammar and kind are shown in the editor. */
    private final ReadOnlyObjectWrapper<DisplayStyles> displayStyles = new ReadOnlyObjectWrapper<>(DisplayStyles.NONE);

    /** Whether the AI assistant may send text of this project to providers outside this computer. */
    private boolean cloudAssistAllowed = true;

    /** A LIFT dictionary the lexical information of the project is added to (null: none). */
    private Path contextDictionary;
    /** A Matchbox corpus the data of the project is added to (null: none). */
    private Path contextCorpus;

    /**
     * The list of files and directories in the project
     * as edited by the user.
     */
    private List<Path> filesAndDirectories = new ArrayList<>();

    /**
     * For all actual files, map the Path to the TextDocument.
     */
    private ObservableMap<Path, TextDocument> textDocumentByPath =
        FXCollections.observableHashMap();

    /** The same documents, by text id (spans and corpus texts refer to documents by id). */
    private final Map<TextId, TextDocument> documentById = new HashMap<>();
    /** Undo journal of all the documents, kept as long as they are in the project. */
    private final EditJournal journal = new EditJournal();
    /** Parses a document after a change that does not come from typing (view, assistant, undo…). */
    private final DocumentListener reparseOnChange = this::onDocumentChanged;
    private final ReadOnlyBooleanWrapper anyDocumentDirty = new ReadOnlyBooleanWrapper(false);
    private final javafx.beans.value.ChangeListener<Boolean> dirtyListener = (obs, o, n) -> updateAnyDocumentDirty();

    /**
     * Derived from textDocumentByPath. used by #parseProject
     */
    private ObservableList<TextDocument> derivedFileList =
        FXCollections.observableArrayList();

    /**
     * Used in add directory and remove directory.
     */
    private Map<Path, List<Path>> filesByDir = new HashMap<>();

    /** Settings of the directories of the definition. */
    private final Map<Path, DirectorySettings> directorySettings = new HashMap<>();

    private SimpleObjectProperty<File> projectFile = new SimpleObjectProperty<>(
        null
    );

    protected Project(List<GrammarPlugin> plugins) {
        this(plugins, DocumentOptions.DEFAULT);
    }

    protected Project(List<GrammarPlugin> plugins, DocumentOptions documentOptions) {
        this.documentOptions = Objects.requireNonNull(documentOptions);
        this.corpus = new Corpus();
        this.name = "";
        this.description = "";
        this.plugins = plugins;

        // derive the passiveTextList from the parserMap
        this.textDocumentByPath.values().forEach(textParser -> {
            this.derivedFileList.add(textParser);
        });
        this.textDocumentByPath.addListener(
            (
                MapChangeListener.Change<
                    ? extends Path,
                    ? extends TextDocument
                > change
            ) -> {
                if (change.wasRemoved()) {
                    TextDocument removed = change.getValueRemoved();
                    derivedFileList.remove(removed);
                    documentById.remove(removed.getId());
                    journal.untrack(removed);
                    removed.removeChangeListener(reparseOnChange);
                    removed.getCouldBeWrittenBooleanProperty().removeListener(dirtyListener);
                    corpus.remove(removed.getId());
                }
                if (change.wasAdded()) {
                    TextDocument added = change.getValueAdded();
                    derivedFileList.add(added);
                    documentById.put(added.getId(), added);
                    journal.track(added);
                    added.addChangeListener(reparseOnChange);
                    added.getCouldBeWrittenBooleanProperty().addListener(dirtyListener);
                }
                updateAnyDocumentDirty();
            }
        );
    }

    public static final Project loadProject(
        List<GrammarPlugin> plugins,
        File projectFile
    ) throws Exception {
        return loadProject(plugins, projectFile, DocumentOptions.DEFAULT);
    }

    /**
     * Loads a project file. Files of the project that cannot be read are skipped and listed in
     * {@link #getLoadProblems()}.
     */
    public static final Project loadProject(
        List<GrammarPlugin> plugins,
        File projectFile,
        DocumentOptions documentOptions
    ) throws Exception {
        return ProjectXMLSerializer.load(projectFile, plugins, documentOptions);
    }

    /**
     * Creates a default project: a single file type "All files" ({@value #ALL_FILES_REGEX}: every
     * file with an extension) with every installed plugin, sorted by name.
     */
    public static final Project createDefaultProject(
        List<GrammarPlugin> plugins
    ) {
        return createDefaultProject(plugins, DocumentOptions.DEFAULT);
    }

    public static final Project createDefaultProject(
        List<GrammarPlugin> plugins,
        DocumentOptions documentOptions
    ) {
        Project project = new Project(plugins, documentOptions);
        FileType fileType = new FileType("All files", ALL_FILES_REGEX);
        plugins.stream()
            .sorted(Comparator.comparing(GrammarPlugin::getDisplayName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(GrammarPlugin::getId))
            .forEach(fileType::addGrammar);
        project.fileTypes.add(fileType);
        project.definitionChanged.set(false);
        return project;
    }

    /** See {@link #definitionChanged}; set it to false once the definition is saved. */
    public SimpleBooleanProperty definitionChangedProperty() {
        return definitionChanged;
    }

    private void markDefinitionChanged() {
        definitionChanged.set(true);
        definitionRevision.set(definitionRevision.get() + 1);
    }

    /**
     * Incremented by every change of the definition (files, directories and their settings, file
     * types…): listen to it to follow the definition, {@link #definitionChangedProperty()} staying
     * true after the first change.
     */
    public ReadOnlyIntegerProperty definitionRevisionProperty() {
        return definitionRevision.getReadOnlyProperty();
    }

    public void parseProject() {
        for (TextDocument textParser : derivedFileList) {
            parseTextObject(textParser);
        }
        isDesynchronized = false;
    }

    /** Parses a file synchronously, on the calling thread. */
    public void parsePath(Path filePath) {
        TextDocument text = getParsedText(filePath);
        parseTextObject(text);
    }

    /** Parses a document synchronously and applies the results on the calling thread. */
    public void parseTextObject(TextDocument document) {
        ParseRun run = computeParse(
            document.getId(),
            document.getCurrentText(),
            document.getVersion(),
            getGrammarsForFile(document.getFilePath())
        );
        applyParse(document, run);
    }

    /** Parses a document in the background. */
    public CompletableFuture<Void> parseInBackground(TextId id) {
        return document(id)
            .map(d -> parseInBackground(d.getFilePath()))
            .orElseGet(() -> CompletableFuture.completedFuture(null));
    }

    private void onDocumentChanged(DocumentChange change) {
        // typing is parsed on demand; other changes must refresh the views at once
        if (!change.origin().isTyping()) {
            parseInBackground(change.document().getFilePath()).exceptionally(e -> {
                logger.log(Level.WARNING, "Parsing failed after a change of " + change.document().getFilePath(), e);
                return null;
            });
        }
    }

    /**
     * Parses a file on a background thread. The results are applied through the
     * {@linkplain #setApplyExecutor apply executor}, and discarded if the text changed meanwhile
     * or the file was removed.
     */
    public CompletableFuture<Void> parseInBackground(Path filePath) {
        TextDocument document = getParsedText(filePath);
        // snapshot on the calling thread
        String text = document.getCurrentText();
        long version = document.getVersion();
        TextId id = document.getId();
        List<GrammarPlugin> grammars = getGrammarsForFile(filePath);
        return CompletableFuture
            .supplyAsync(() -> computeParse(id, text, version, grammars), parserExecutor())
            .thenAcceptAsync(run -> {
                if (textDocumentByPath.get(filePath) == document && document.getVersion() == run.version()) {
                    applyParse(document, run);
                }
            }, applyExecutor);
    }

    /**
     * Executor used to apply background parse results; the GUI sets it to
     * {@code Platform::runLater} so that observable lists are only changed on the JavaFX thread.
     */
    public void setApplyExecutor(Executor applyExecutor) {
        this.applyExecutor = Objects.requireNonNull(applyExecutor);
    }

    /** Grammars applied to a file, in file type and rank order, without duplicates. */
    public List<GrammarPlugin> getGrammarsForFile(Path filePath) {
        Map<String, GrammarPlugin> grammars = new LinkedHashMap<>();
        for (FileType fileType : fileTypes) {
            if (fileType.matches(filePath)) {
                for (GrammarPlugin plugin : fileType.getGrammars()) {
                    grammars.putIfAbsent(plugin.getId(), plugin);
                }
            }
        }
        return List.copyOf(grammars.values());
    }

    /** Result of one parse of a document. */
    private record ParseRun(Text text, long version) {}

    /**
     * Runs the grammars and validates their results; thread-safe, does not touch observable
     * state.
     */
    private static ParseRun computeParse(TextId id, String text, long version, List<GrammarPlugin> grammars) {
        return new ParseRun(TextParser.parse(id, text, grammars), version);
    }

    private void applyParse(TextDocument document, ParseRun run) {
        corpus.put(run.text());
        document.markParsed(run.version());
    }

    private synchronized ExecutorService parserExecutor() {
        if (parserExecutor == null) {
            parserExecutor = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "matchbox-parser");
                t.setDaemon(true);
                return t;
            });
        }
        return parserExecutor;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
        markDefinitionChanged();
    }

    /** False if the AI assistant may only use providers on this computer for this project. */
    public boolean isCloudAssistAllowed() {
        return cloudAssistAllowed;
    }

    public void setCloudAssistAllowed(boolean allowed) {
        if (allowed != cloudAssistAllowed) {
            this.cloudAssistAllowed = allowed;
            markDefinitionChanged();
        }
    }

    /** The LIFT dictionary the lexical information of the project is added to, if any. */
    public Optional<Path> getContextDictionary() {
        return Optional.ofNullable(contextDictionary);
    }

    /** @param dictionary a LIFT file, or null for none */
    public void setContextDictionary(Path dictionary) {
        if (!Objects.equals(dictionary, contextDictionary)) {
            this.contextDictionary = dictionary;
            markDefinitionChanged();
        }
    }

    /** The Matchbox corpus the data of the project is added to, if any. */
    public Optional<Path> getContextCorpus() {
        return Optional.ofNullable(contextCorpus);
    }

    /** @param corpus a Matchbox corpus, or null for none */
    public void setContextCorpus(Path corpus) {
        if (!Objects.equals(corpus, contextCorpus)) {
            this.contextCorpus = corpus;
            markDefinitionChanged();
        }
    }

    /** The display styles of the project (by grammar and kind). */
    public DisplayStyles getDisplayStyles() {
        return displayStyles.get();
    }

    public ReadOnlyObjectProperty<DisplayStyles> displayStylesProperty() {
        return displayStyles.getReadOnlyProperty();
    }

    public void setDisplayStyles(DisplayStyles styles) {
        Objects.requireNonNull(styles, "styles");
        if (!styles.equals(displayStyles.get())) {
            displayStyles.set(styles);
            markDefinitionChanged();
        }
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
        markDefinitionChanged();
    }

    // ----------------------
    // Files and directory
    // ----------------------

    /**
     * Returns the actual list of files in the project
     * (built from the user-defined list of directories and files).
     *
     */
    public ObservableList<TextDocument> getDerivedFileList() {
        return derivedFileList;
    }

    /**
     * Adds a directory with the default settings: its own files (not those of its sub-directories)
     * with the extensions of the document options.
     */
    public void addDirectory(Path path) throws IOException {
        addDirectory(path, false);
    }

    /** Adds a directory with the default extensions, optionally with its sub-directories. */
    public void addDirectory(Path path, boolean recursive) throws IOException {
        addDirectory(path, DirectorySettings.of(documentOptions.fileExtensions(), recursive));
    }

    /**
     * Adds a directory: the files its settings select are added and parsed (files already in the
     * project are kept as they are).
     *
     * @throws IOException listing the files that could not be read (the others are added)
     */
    public void addDirectory(Path path, DirectorySettings settings) throws IOException {
        if (!Files.isDirectory(path)) {
            throw new IllegalArgumentException(
                "The provided path is not a directory."
            );
        }
        filesAndDirectories.add(path);
        directorySettings.put(path, settings);
        List<Path> files = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        for (Path p : settings.scan(path)) {
            if (textDocumentByPath.containsKey(p)) continue;
            try {
                addFileInternal(p);
                files.add(p);
            } catch (IOException e) {
                failures.add(e.getMessage());
            }
        }
        filesByDir.put(path, files);
        markDefinitionChanged();
        if (!failures.isEmpty()) {
            throw new IOException(
                failures.size() + " file(s) of " + path + " could not be read:\n" + String.join("\n", failures)
            );
        }
    }

    /** The settings of a directory of the project definition. */
    public Optional<DirectorySettings> directorySettings(Path directory) {
        return Optional.ofNullable(directorySettings.get(directory));
    }

    /** Directories of the project definition, in order. */
    public List<Path> getDefinitionDirectories() {
        return filesAndDirectories.stream().filter(directorySettings::containsKey).toList();
    }

    /** The files a directory of the project gives, sorted. */
    public List<Path> filesOf(Path directory) {
        return List.copyOf(filesByDir.getOrDefault(directory, List.of()));
    }

    /** The directory of the project definition a file comes from; empty for a file added alone. */
    public Optional<Path> directoryOf(Path file) {
        return filesByDir.entrySet().stream().filter(e -> e.getValue().contains(file)).map(Map.Entry::getKey).findFirst();
    }

    /**
     * Files of the project that new settings of a directory would remove (to ask about unsaved
     * changes first).
     */
    public List<TextDocument> documentsRemovedBy(Path directory, DirectorySettings settings) throws IOException {
        List<Path> kept = settings.scan(directory);
        return filesOf(directory).stream().filter(f -> !kept.contains(f))
            .map(textDocumentByPath::get).filter(Objects::nonNull).toList();
    }

    /**
     * Gives a directory new settings, then scans it again: the files it now selects are added and
     * parsed, those it no longer selects leave the project (unsaved changes are lost: ask first,
     * see {@link #documentsRemovedBy}).
     *
     * @return the files added and removed
     */
    public DirectoryChange setDirectorySettings(Path directory, DirectorySettings settings) throws IOException {
        if (!directorySettings.containsKey(directory)) {
            throw new IllegalArgumentException("Not a directory of the project: " + directory);
        }
        List<Path> before = filesOf(directory);
        List<Path> selected = settings.scan(directory);
        List<Path> removed = before.stream().filter(f -> !selected.contains(f)).toList();
        List<Path> added = new ArrayList<>();
        List<Path> files = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        removed.forEach(textDocumentByPath::remove);
        for (Path p : selected) {
            if (before.contains(p)) {
                files.add(p);
            } else if (!textDocumentByPath.containsKey(p)) {
                try {
                    addFileInternal(p);
                    files.add(p);
                    added.add(p);
                } catch (IOException e) {
                    failures.add(e.getMessage());
                }
            }
        }
        filesByDir.put(directory, files);
        if (!settings.equals(directorySettings.put(directory, settings))) {
            markDefinitionChanged();
        }
        if (!failures.isEmpty()) {
            throw new IOException(
                failures.size() + " file(s) of " + directory + " could not be read:\n" + String.join("\n", failures)
            );
        }
        return new DirectoryChange(added, removed);
    }

    /** Scans a directory again with its settings (files created or deleted on the disk). */
    public DirectoryChange rescanDirectory(Path directory) throws IOException {
        return setDirectorySettings(directory, directorySettings.get(directory));
    }

    /** Files added to and removed from the project by a change of a directory. */
    public record DirectoryChange(List<Path> added, List<Path> removed) {
        public DirectoryChange {
            added = List.copyOf(added);
            removed = List.copyOf(removed);
        }
    }

    private void addFileInternal(Path filePath) throws IOException {
        TextDocument textParser = new TextDocument(filePath, documentOptions.charset());
        textDocumentByPath.put(filePath, textParser);
        parseTextObject(textParser);
        //isDesynchronized = true;
        markDefinitionChanged();
    }

    /**
     *
     * @param filePath
     * @throws IOException
     */
    public void addFile(Path filePath) throws IOException {
        if (!Files.isRegularFile(filePath)) {
            throw new IllegalArgumentException(
                "The provided path is not a regular file:" + filePath.toString()
            );
        }
        if (textDocumentByPath.containsKey(filePath)) {
            throw new IllegalArgumentException(
                "The file is already loaded: " + filePath.toString()
            );
        }
        filesAndDirectories.add(filePath);
        addFileInternal(filePath);
    }

    public TextDocument getParsedText(Path filePath) {
        TextDocument textParser = textDocumentByPath.get(filePath);
        if (textParser != null) {
            return textParser;
        }
        throw new IllegalArgumentException(
            "No TextParser found for the given file path."
        );
    }

    /** The document of a text, if it is in the project. */
    public Optional<TextDocument> document(TextId id) {
        return Optional.ofNullable(documentById.get(id));
    }

    /** Undo journal of the project: the changes of all its documents, in order. */
    public EditJournal journal() {
        return journal;
    }

    /** Documents with unsaved changes, in file list order. */
    public List<TextDocument> getDirtyDocuments() {
        return derivedFileList.stream().filter(TextDocument::getCouldBeWritten).toList();
    }

    /** True while at least one document has unsaved changes. */
    public ReadOnlyBooleanProperty anyDocumentDirtyProperty() {
        return anyDocumentDirty.getReadOnlyProperty();
    }

    private void updateAnyDocumentDirty() {
        anyDocumentDirty.set(derivedFileList.stream().anyMatch(TextDocument::getCouldBeWritten));
    }

    public void removeFile(String filePath) {
        removeFile(Path.of(filePath));
    }

    public void removeFile(Path selected) {
        filesAndDirectories.remove(selected);
        textDocumentByPath.remove(selected);
        markDefinitionChanged();
    }

    /** Files and directories of the project definition, as given (including missing ones), in order. */
    public List<Path> getDefinitionPaths() {
        return List.copyOf(filesAndDirectories);
    }

    /** True if the path was added as a directory of the project definition. */
    public boolean isDefinitionDirectory(Path path) {
        return directorySettings.containsKey(path) || filesByDir.containsKey(path) || Files.isDirectory(path);
    }

    public List<String> getFiles() {
        return filesAndDirectories
            .stream()
            .filter(p -> Files.isRegularFile(p))
            .map(p -> p.toString())
            .collect(Collectors.toList());
    }

    /** Adds a file of the project definition; a failure is recorded in {@link #getLoadProblems()}. */
    public void addFile(String filePath) {
        try {
            addFile(Path.of(filePath));
        } catch (IOException | RuntimeException e) {
            recordLoadProblem(filePath, e);
        }
    }

    public List<String> getDirectories() {
        return filesAndDirectories
            .stream()
            .filter(p -> Files.isDirectory(p))
            .map(p -> p.toString())
            .collect(Collectors.toList());
    }

    /** Adds a directory of the project definition with its settings; failures are recorded in {@link #getLoadProblems()}. */
    public void addDirectory(String dirPath, DirectorySettings settings) {
        try {
            addDirectory(Path.of(dirPath), settings);
        } catch (IOException | RuntimeException e) {
            recordLoadProblem(dirPath, e);
        }
    }

    /** Adds a directory of the project definition; failures are recorded in {@link #getLoadProblems()}. */
    public void addDirectory(String dirPath) {
        try {
            addDirectory(Path.of(dirPath));
        } catch (IOException | RuntimeException e) {
            recordLoadProblem(dirPath, e);
        }
    }

    private void recordLoadProblem(String path, Exception e) {
        String message = e.getMessage() == null ? path + ": " + e : e.getMessage();
        logger.warning(message);
        loadProblems.add(message);
    }

    /** Records a problem of the project definition, shown to the user after loading. */
    void recordLoadProblem(String message) {
        logger.warning(message);
        loadProblems.add(message);
    }

    /**
     * Problems found when the project was loaded: files or directories that could not be read,
     * file types with an invalid regular expression.
     */
    public List<String> getLoadProblems() {
        return List.copyOf(loadProblems);
    }

    public DocumentOptions getDocumentOptions() {
        return documentOptions;
    }

    public void removeDirectory(Path dirPath) {
        filesAndDirectories.remove(dirPath);
        directorySettings.remove(dirPath);
        for (Path p : filesByDir.getOrDefault(
            dirPath,
            Collections.emptyList()
        )) {
            textDocumentByPath.remove(p);
        }
        filesByDir.remove(dirPath);
        markDefinitionChanged();
    }

    public void removeDirectory(String dirPath) {
        removeDirectory(Path.of(dirPath));
    }

    /** Writes the document of a file. */
    public void saveFile(Path editedFilePath) throws IOException {
        getParsedText(editedFilePath).writeFile();
    }

    /** Writes every document with unsaved changes; stops at the first failure. */
    public void saveAll() throws IOException {
        for (TextDocument d : getDirtyDocuments()) {
            d.writeFile();
        }
    }

    // ------------------------
    // File types
    // ------------------------

    public List<FileType> getFileTypes() {
        return fileTypes;
    }

    public void addFileType(FileType type) {
        fileTypes.add(type);
        isDesynchronized = true;
        markDefinitionChanged();
    }

    /** Replaces the file types (order = precedence of their grammars). */
    public void setFileTypes(List<FileType> types) {
        fileTypes.clear();
        fileTypes.addAll(types);
        isDesynchronized = true;
        markDefinitionChanged();
    }

    public void removeFileType(FileType type) {
        fileTypes.remove(type);
        isDesynchronized = true;
        markDefinitionChanged();
    }

    public Corpus getCorpus() {
        return this.corpus;
    }

    public void setFile(File projectFile) {
        this.projectFile.set(projectFile);
    }

    public SimpleObjectProperty<File> fileProperty() {
        return this.projectFile;
    }
}
