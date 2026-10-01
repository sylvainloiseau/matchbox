package fr.cnrs.lacito.matchbox.model.project;

import fr.cnrs.lacito.matchbox.grammarapi.GrammarPlugin;
import fr.cnrs.lacito.matchbox.grammarapi.ParseError;
import fr.cnrs.lacito.matchbox.grammarapi.ParseResult;
import fr.cnrs.lacito.matchbox.model.Corpus;
import fr.cnrs.lacito.matchbox.model.DocumentOptions;
import fr.cnrs.lacito.matchbox.model.TextDocument;
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

    private SimpleBooleanProperty isProjectDefinitionOutdated =
        new SimpleBooleanProperty(false);

    /** Whether the AI assistant may send text of this project to providers outside this computer. */
    private boolean cloudAssistAllowed = true;

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

    /**
     * Derived from textDocumentByPath. used by #parseProject
     */
    private ObservableList<TextDocument> derivedFileList =
        FXCollections.observableArrayList();

    /**
     * Used in add directory and remove directory.
     */
    private Map<Path, List<Path>> filesByDir = new HashMap<>();

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
                if (change.wasAdded()) {
                    derivedFileList.add(change.getValueAdded());
                } else if (change.wasRemoved()) {
                    derivedFileList.remove(change.getValueRemoved());
                }
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

    /** Creates a default project with a single file type "All files" and a single grammar "IGT" applied to all files.
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
        FileType fileType = new FileType("All files", ".+");
        GrammarPlugin defaultPlugin = plugins
            .stream()
            .filter(p -> p.getId().equals(DEFAULT_PLUGIN_ID))
            .findFirst()
            .orElseThrow(() ->
                new RuntimeException("Default plugin not found")
            );
        fileType.addGrammar(defaultPlugin);
        project.fileTypes.add(fileType);
        project.isProjectDefinitionOutdatedProperty().set(true);
        return project;
    }

    public SimpleBooleanProperty isProjectDefinitionOutdatedProperty() {
        return isProjectDefinitionOutdated;
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
            document.getFilePath(),
            document.getCurrentText(),
            document.getVersion(),
            getGrammarsForFile(document.getFilePath())
        );
        applyParse(document, run);
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
        List<GrammarPlugin> grammars = getGrammarsForFile(filePath);
        return CompletableFuture
            .supplyAsync(() -> computeParse(filePath, text, version, grammars), parserExecutor())
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

    /** Results of one parse of a file. */
    private record ParseRun(String text, long version, Map<String, ParseResult> results) {}

    /** Runs the grammars; thread-safe, does not touch observable state. */
    private static ParseRun computeParse(Path filePath, String text, long version, List<GrammarPlugin> grammars) {
        Map<String, ParseResult> results = new LinkedHashMap<>();
        for (GrammarPlugin plugin : grammars) {
            ParseResult result;
            try {
                result = plugin.getResult(filePath, text);
                if (result == null) {
                    throw new IllegalStateException("getResult returned null");
                }
            } catch (RuntimeException | LinkageError e) {
                logger.log(Level.WARNING, "Grammar " + plugin.getId() + " failed on " + filePath, e);
                result = new ParseResult(List.of(), List.of(
                    new ParseError(filePath, plugin.getId(), 0, 0, "Internal error of the grammar: " + e)
                ));
            }
            results.put(plugin.getId(), result);
        }
        return new ParseRun(text, version, results);
    }

    private void applyParse(TextDocument document, ParseRun run) {
        corpus.setResults(document.getFilePath(), run.results(), run.text());
        if (document.getVersion() == run.version()) {
            document.markParsed();
        }
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
        this.isProjectDefinitionOutdatedProperty().set(true);
    }

    /** False if the AI assistant may only use providers on this computer for this project. */
    public boolean isCloudAssistAllowed() {
        return cloudAssistAllowed;
    }

    public void setCloudAssistAllowed(boolean allowed) {
        if (allowed != cloudAssistAllowed) {
            this.cloudAssistAllowed = allowed;
            this.isProjectDefinitionOutdatedProperty().set(true);
        }
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
        this.isProjectDefinitionOutdatedProperty().set(true);
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
     * Sets the current directory or file path and loads markdown/text files
     */
    public void addDirectory(Path path) throws IOException {
        addDirectory(path, false);
    }

    /**
     * Sets the current directory or file path and loads markdown/text files, optionally recursively.
     */
    public void addDirectory(Path path, boolean recursive) throws IOException {
        if (!Files.isDirectory(path)) {
            throw new IllegalArgumentException(
                "The provided path is not a directory."
            );
        }

        filesAndDirectories.add(path);
        List<Path> files = new ArrayList<>();
        List<String> failures = new ArrayList<>();

        int depth = recursive ? Integer.MAX_VALUE : 1;
        try (var stream = Files.walk(path, depth)) {
            stream
                .filter(Files::isRegularFile)
                .filter(documentOptions::acceptsFile)
                .sorted()
                .forEach(p -> {
                    try {
                        addFileInternal(p);
                        files.add(p);
                    } catch (IOException e) {
                        failures.add(e.getMessage());
                    }
                });
        }
        filesByDir.put(path, files);
        this.isProjectDefinitionOutdatedProperty().set(true);
        if (!failures.isEmpty()) {
            throw new IOException(
                failures.size() + " file(s) of " + path + " could not be read:\n" + String.join("\n", failures)
            );
        }
    }

    private void addFileInternal(Path filePath) throws IOException {
        TextDocument textParser = new TextDocument(filePath, documentOptions.charset());
        textDocumentByPath.put(filePath, textParser);
        parseTextObject(textParser);
        //isDesynchronized = true;
        this.isProjectDefinitionOutdatedProperty().set(true);
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

    public void updateFileContent(String fileContent, Path filePath) {
        TextDocument text = getParsedText(filePath);
        text.updateText(fileContent);
    }

    public void removeFile(String filePath) {
        removeFile(Path.of(filePath));
    }

    public void removeFile(Path selected) {
        filesAndDirectories.remove(selected);
        textDocumentByPath.remove(selected);
        corpus.removeFile(selected);
        this.isProjectDefinitionOutdatedProperty().set(true);
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
        for (Path p : filesByDir.getOrDefault(
            dirPath,
            Collections.emptyList()
        )) {
            textDocumentByPath.remove(p);
            corpus.removeFile(p);
        }
        this.isProjectDefinitionOutdatedProperty().set(true);
    }

    public void removeDirectory(String dirPath) {
        removeDirectory(Path.of(dirPath));
    }

    public void saveFile(Path editedFilePath, String text) throws IOException {
        TextDocument textParser = getParsedText(editedFilePath);
        textParser.updateText(text);
        textParser.writeFile();
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
        this.isProjectDefinitionOutdatedProperty().set(true);
    }

    public void removeFileType(FileType type) {
        fileTypes.remove(type);
        isDesynchronized = true;
        this.isProjectDefinitionOutdatedProperty().set(true);
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
