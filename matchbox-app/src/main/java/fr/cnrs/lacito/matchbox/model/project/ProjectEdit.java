package fr.cnrs.lacito.matchbox.model.project;

import fr.cnrs.lacito.matchbox.grammarapi.GrammarPlugin;
import fr.cnrs.lacito.matchbox.model.TextDocument;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * A working copy of the definition of a project (name, description, files and directories, file
 * types with their grammars, display styles), edited by the project properties dialog and applied to the project
 * only on {@link #applyTo}: until then the project is unchanged, so cancelling discards everything.
 */
public final class ProjectEdit {

    /** A file type being edited; {@code original} is the project's file type it comes from, if any. */
    public static final class FileTypeDraft {
        private final FileType original;
        private String name;
        private String regex;
        private final List<GrammarPlugin> grammars;

        public FileTypeDraft(String name, String regex, List<GrammarPlugin> grammars) {
            this(null, name, regex, grammars);
        }

        private FileTypeDraft(FileType original, String name, String regex, List<GrammarPlugin> grammars) {
            this.original = original;
            this.name = name == null ? "" : name;
            this.regex = regex == null ? "" : regex;
            this.grammars = new ArrayList<>(grammars);
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name == null ? "" : name;
        }

        public String getRegex() {
            return regex;
        }

        public void setRegex(String regex) {
            this.regex = regex == null ? "" : regex;
        }

        /** Grammars applied to the matching files, in rank order (selected grammars only). */
        public List<GrammarPlugin> getGrammars() {
            return grammars;
        }

        public boolean uses(GrammarPlugin grammar) {
            return grammars.stream().anyMatch(g -> g.getId().equals(grammar.getId()));
        }

        /** Selects or deselects a grammar; a selected grammar is added last. */
        public void setUsed(GrammarPlugin grammar, boolean used) {
            grammars.removeIf(g -> g.getId().equals(grammar.getId()));
            if (used) grammars.add(grammar);
        }

        /** Moves a selected grammar one rank up (-1) or down (+1). */
        public void move(GrammarPlugin grammar, int direction) {
            int i = -1;
            for (int k = 0; k < grammars.size(); k++) {
                if (grammars.get(k).getId().equals(grammar.getId())) i = k;
            }
            int j = i + direction;
            if (i >= 0 && j >= 0 && j < grammars.size()) {
                grammars.add(j, grammars.remove(i));
            }
        }

        @Override
        public String toString() {
            return name.isBlank() ? "(unnamed)" : name;
        }
    }

    /**
     * Result of {@link #applyTo}.
     *
     * @param problems files or directories that could not be added
     * @param reparsed documents parsed again because their grammars changed
     */
    public record Result(List<String> problems, int reparsed) {}

    private String name;
    private String description;
    private boolean cloudAssistAllowed;
    private Path contextDictionary;
    private Path contextCorpus;
    private DisplayStyles displayStyles;
    private final List<Path> paths;
    private final List<FileTypeDraft> fileTypes;

    private ProjectEdit(String name, String description, boolean cloudAssistAllowed, DisplayStyles displayStyles,
                        List<Path> paths, List<FileTypeDraft> fileTypes) {
        this.name = name;
        this.displayStyles = displayStyles;
        this.description = description;
        this.cloudAssistAllowed = cloudAssistAllowed;
        this.paths = new ArrayList<>(paths);
        this.fileTypes = new ArrayList<>(fileTypes);
    }

    /** A copy of the definition of a project. */
    public static ProjectEdit of(Project project) {
        List<FileTypeDraft> types = new ArrayList<>();
        for (FileType t : project.getFileTypes()) {
            types.add(new FileTypeDraft(t, t.getName(), t.getRegex(), t.getGrammars()));
        }
        ProjectEdit edit = new ProjectEdit(project.getName(), project.getDescription(), project.isCloudAssistAllowed(),
            project.getDisplayStyles(), project.getDefinitionPaths(), types);
        edit.contextDictionary = project.getContextDictionary().orElse(null);
        edit.contextCorpus = project.getContextCorpus().orElse(null);
        return edit;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name == null ? "" : name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description == null ? "" : description;
    }

    public boolean isCloudAssistAllowed() {
        return cloudAssistAllowed;
    }

    public void setCloudAssistAllowed(boolean allowed) {
        this.cloudAssistAllowed = allowed;
    }

    /** The LIFT dictionary the lexical information is added to, if any. */
    public Optional<Path> getContextDictionary() {
        return Optional.ofNullable(contextDictionary);
    }

    /** @param dictionary a LIFT file, or null for none */
    public void setContextDictionary(Path dictionary) {
        this.contextDictionary = dictionary;
    }

    /** The Matchbox corpus the data is added to, if any. */
    public Optional<Path> getContextCorpus() {
        return Optional.ofNullable(contextCorpus);
    }

    /** @param corpus a Matchbox corpus, or null for none */
    public void setContextCorpus(Path corpus) {
        this.contextCorpus = corpus;
    }

    /** Display styles by grammar and kind. */
    public DisplayStyles getDisplayStyles() {
        return displayStyles;
    }

    public void setDisplayStyles(DisplayStyles styles) {
        this.displayStyles = Objects.requireNonNull(styles, "styles");
    }

    /** Files and directories of the definition (mutable, in order). */
    public List<Path> getPaths() {
        return paths;
    }

    /** File types (mutable, in order). */
    public List<FileTypeDraft> getFileTypes() {
        return fileTypes;
    }

    /** Ids of the grammars checked in at least one file type, in order of first use. */
    public List<String> usedGrammarIds() {
        Set<String> ids = new LinkedHashSet<>();
        for (FileTypeDraft t : fileTypes) {
            for (GrammarPlugin g : t.getGrammars()) ids.add(g.getId());
        }
        return List.copyOf(ids);
    }

    /** Names of the file types checking a grammar, in order. */
    public List<String> fileTypesUsing(String grammarId) {
        return fileTypes.stream()
            .filter(t -> t.getGrammars().stream().anyMatch(g -> g.getId().equals(grammarId)))
            .map(FileTypeDraft::toString)
            .toList();
    }

    /** What prevents applying the edit (invalid regular expressions, unnamed file types). */
    public List<String> problems() {
        List<String> problems = new ArrayList<>();
        for (FileTypeDraft t : fileTypes) {
            if (t.getName().isBlank()) {
                problems.add("A file type has no name");
            }
            String regexProblem = FileType.checkRegex(t.getRegex());
            if (regexProblem != null) {
                problems.add("File type '" + t + "': " + regexProblem);
            }
        }
        return problems;
    }

    /** Documents of the project that applying the edit would remove (their paths were removed). */
    public List<TextDocument> droppedDocuments(Project project) {
        Set<Path> removed = new LinkedHashSet<>(project.getDefinitionPaths());
        paths.forEach(removed::remove);
        List<TextDocument> dropped = new ArrayList<>();
        for (TextDocument d : project.getDerivedFileList()) {
            Path file = d.getFilePath();
            if (removed.stream().anyMatch(r -> file.equals(r) || file.startsWith(r))) {
                dropped.add(d);
            }
        }
        return dropped;
    }

    /**
     * Applies the edit: name, description, assistant setting, file types (then documents whose
     * grammars changed are parsed again), removed and added files and directories (new files are
     * parsed when added).
     *
     * @throws IllegalStateException if {@link #problems()} is not empty
     */
    public Result applyTo(Project project) {
        if (!problems().isEmpty()) {
            throw new IllegalStateException("Invalid project definition: " + problems());
        }
        if (!Objects.equals(name, project.getName())) project.setName(name);
        if (!Objects.equals(description, project.getDescription())) project.setDescription(description);
        project.setCloudAssistAllowed(cloudAssistAllowed);
        project.setContextDictionary(contextDictionary);
        project.setContextCorpus(contextCorpus);
        project.setDisplayStyles(displayStyles);

        // file types first, so that added files are parsed with the new grammars
        Map<TextDocument, List<String>> grammarsBefore = new HashMap<>();
        for (TextDocument d : project.getDerivedFileList()) {
            grammarsBefore.put(d, ids(project.getGrammarsForFile(d.getFilePath())));
        }
        if (fileTypesChanged(project)) {
            List<FileType> types = new ArrayList<>();
            for (FileTypeDraft draft : fileTypes) {
                FileType t = draft.original != null ? draft.original : new FileType();
                t.setName(draft.getName());
                t.setRegex(draft.getRegex());
                t.getGrammars().clear();
                t.getGrammars().addAll(draft.getGrammars());
                types.add(t);
            }
            project.setFileTypes(types);
        }

        List<Path> before = project.getDefinitionPaths();
        for (Path p : before) {
            if (!paths.contains(p)) {
                if (project.isDefinitionDirectory(p)) project.removeDirectory(p);
                else project.removeFile(p);
            }
        }
        List<String> problems = new ArrayList<>();
        for (Path p : paths) {
            if (before.contains(p)) continue;
            try {
                if (Files.isDirectory(p)) project.addDirectory(p);
                else project.addFile(p);
            } catch (IOException | RuntimeException e) {
                problems.add(e.getMessage() == null ? p + ": " + e : e.getMessage());
            }
        }

        int reparsed = 0;
        for (var e : grammarsBefore.entrySet()) {
            TextDocument d = e.getKey();
            if (project.getDerivedFileList().contains(d)
                && !ids(project.getGrammarsForFile(d.getFilePath())).equals(e.getValue())) {
                project.parseTextObject(d);
                reparsed++;
            }
        }
        return new Result(problems, reparsed);
    }

    private boolean fileTypesChanged(Project project) {
        List<FileType> current = project.getFileTypes();
        if (current.size() != fileTypes.size()) return true;
        for (int i = 0; i < current.size(); i++) {
            FileType t = current.get(i);
            FileTypeDraft d = fileTypes.get(i);
            if (d.original != t || !Objects.equals(t.getName(), d.getName()) || !Objects.equals(t.getRegex(), d.getRegex())
                || !ids(t.getGrammars()).equals(ids(d.getGrammars()))) {
                return true;
            }
        }
        return false;
    }

    private static List<String> ids(List<GrammarPlugin> grammars) {
        return grammars.stream().map(GrammarPlugin::getId).toList();
    }
}
