package fr.cnrs.lacito.matchbox.model.project;

import fr.cnrs.lacito.matchbox.grammarapi.GrammarPlugin;
import fr.cnrs.lacito.matchbox.grammarapi.ParseResult;
import fr.cnrs.lacito.matchbox.model.Corpus;
import fr.cnrs.lacito.matchbox.model.TextDocument;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
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

    private SimpleBooleanProperty isProjectDefinitionOutdated =
        new SimpleBooleanProperty(false);

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
        Project project = ProjectXMLSerializer.load(projectFile, plugins);
        return project;
    }

    /** Creates a default project with a single file type "All files" and a single grammar "IGT" applied to all files.
     */
    public static final Project createDefaultProject(
        List<GrammarPlugin> plugins
    ) {
        Project project = new Project(plugins);
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

    public void parsePath(Path filePath) {
        TextDocument text = getParsedText(filePath);
        parseTextObject(text);
    }

    // // TODO. rather add listener to each text.getIGTs() (an observable list)
    // igtList.stream().filter(p -> {
    //     System.out.println("Filtering IGTs for file: " + filePath.toString());
    //     System.out.println("IGT source: " + p.getFilePath());
    //     return ! p.getFilePath().equals(filePath);
    // }).collect(Collectors.toList());
    // igtList.addAll(text.getIGTs());

    // lexList.stream().filter(p -> {
    //     return ! p.getFilePath().equals(filePath);
    // }).collect(Collectors.toList());
    // lexList.addAll(text.getLexicalDefinitions());

    public void parseTextObject(TextDocument textParser) {
        // TODO or to be done after, when re-synchronizing the corpus with the project?
        corpus.removeCorpusObjectsForFile(textParser.getFilePath());
        for (FileType fileType : fileTypes) {
            if (fileType.matches(textParser.getFilePath())) {
                for (GrammarPlugin plugin : fileType.getGrammars()) {
                    ParseResult result = plugin.getResult(
                        textParser.getFilePath(),
                        textParser.getCurrentText()
                    );
                    // TODO or to be done after, when re-synchronizing the corpus with the project?
                    corpus.addCorpusObjects(result.getCorpusObjects());
                    corpus.addErrors(result.getErrors());
                    // Process the parse result as needed
                }
            }
        }
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
        this.isProjectDefinitionOutdatedProperty().set(true);
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

        int depth = recursive ? Integer.MAX_VALUE : 1;
        try (var stream = Files.walk(path, depth)) {
            stream
                .filter(Files::isRegularFile)
                .filter(p -> {
                    String filename = p.getFileName().toString().toLowerCase();
                    return (
                        filename.endsWith(".md") || filename.endsWith(".txt")
                    );
                })
                .forEach(p -> {
                    try {
                        files.add(p);
                        addFileInternal(p);
                    } catch (IOException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                });
        } catch (IOException e) {
            e.printStackTrace();
        }
        filesByDir.put(path, files);
        this.isProjectDefinitionOutdatedProperty().set(true);
    }

    private void addFileInternal(Path filePath) throws IOException {
        TextDocument textParser = new TextDocument(filePath);
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
        this.isProjectDefinitionOutdatedProperty().set(true);
    }

    public List<String> getFiles() {
        return filesAndDirectories
            .stream()
            .filter(p -> Files.isRegularFile(p))
            .map(p -> p.toString())
            .collect(Collectors.toList());
    }

    public void addFile(String filePath) {
        try {
            addFile(Path.of(filePath));
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public List<String> getDirectories() {
        return filesAndDirectories
            .stream()
            .filter(p -> Files.isDirectory(p))
            .map(p -> p.toString())
            .collect(Collectors.toList());
    }

    public void addDirectory(String dirPath) {
        try {
            addDirectory(Path.of(dirPath));
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public void removeDirectory(Path dirPath) {
        filesAndDirectories.remove(dirPath);
        for (Path p : filesByDir.getOrDefault(
            dirPath,
            Collections.emptyList()
        )) {
            textDocumentByPath.remove(p);
            // TODO or to be done after, when re-synchronizing the corpus with the project?
            corpus.removeCorpusObjectsForFile(p);
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
