package fr.cnrs.lacito.matchbox.corpus;

import fr.cnrs.lacito.matchbox.corpus.capability.Section;
import fr.cnrs.lacito.matchbox.corpus.capability.TextHeader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Immutable snapshot of one parse of one text: its content, the objects and diagnostics of each
 * grammar (in rank order), and what is derived from them (header properties, sections).
 *
 * <p>A new parse gives a new {@code Text}: an object always agrees with the {@code Text} it came
 * from. Objects keep no reference to their text; the text of an object is found through its
 * {@link TextCoordinate#text()} (see {@link CorpusView#textOf(CorpusObject)}).
 */
public final class Text {

    private static final Logger logger = Logger.getLogger(Text.class.getName());

    private final TextId id;
    private final String content;
    /** grammar id → top-level objects, in rank order. */
    private final Map<String, List<CorpusObject>> objectsByGrammar;
    /** grammar id → diagnostics, in rank order. */
    private final Map<String, List<Diagnostic>> diagnosticsByGrammar;

    private final List<CorpusObject> objects;
    private final List<Diagnostic> diagnostics;
    /** object (top-level or nested) → grammar id. */
    private final Map<CorpusObject, String> grammarOf = new IdentityHashMap<>();
    /** nested object → parent. */
    private final Map<CorpusObject, CorpusObject> parentOf = new IdentityHashMap<>();
    private final List<Section> sections;
    private final Map<String, String> properties;

    private Text(TextId id, String content, Map<String, List<CorpusObject>> objectsByGrammar,
                 Map<String, List<Diagnostic>> diagnosticsByGrammar) {
        this.id = id;
        this.content = content;
        this.objectsByGrammar = Collections.unmodifiableMap(objectsByGrammar);
        this.diagnosticsByGrammar = Collections.unmodifiableMap(diagnosticsByGrammar);
        this.objects = objectsByGrammar.values().stream().flatMap(List::stream).toList();
        this.diagnostics = diagnosticsByGrammar.values().stream().flatMap(List::stream).toList();
        objectsByGrammar.forEach((grammarId, list) -> {
            for (CorpusObject o : list) {
                index(o, null, grammarId);
            }
        });
        this.sections = flatten()
            .filter(Section.class::isInstance)
            .map(Section.class::cast)
            .sorted(Comparator.<Section>comparingInt(s -> s.getTextCoordinate().startIndex())
                .thenComparing(Comparator.<Section>comparingInt(s -> s.getTextCoordinate().length()).reversed()))
            .toList();
        Map<String, String> props = new LinkedHashMap<>();
        for (CorpusObject o : objects) {
            if (o instanceof TextHeader header) {
                header.getProperties().forEach(props::putIfAbsent);
            }
        }
        this.properties = Collections.unmodifiableMap(props);
    }

    private void index(CorpusObject o, CorpusObject parent, String grammarId) {
        grammarOf.put(o, grammarId);
        if (parent != null) {
            parentOf.put(o, parent);
        }
        for (CorpusObject child : o.getChildren()) {
            index(child, o, grammarId);
        }
    }

    public static Builder builder(TextId id, String content) {
        return new Builder(id, content);
    }

    // ------------------------------------------------------------------
    // Content
    // ------------------------------------------------------------------

    public TextId id() {
        return id;
    }

    /** The text the spans refer to. */
    public String content() {
        return content;
    }

    /** Grammars that produced this snapshot, in rank order. */
    public List<String> grammarIds() {
        return List.copyOf(objectsByGrammar.keySet());
    }

    /** Top-level objects of all grammars, in rank order. */
    public List<CorpusObject> objects() {
        return objects;
    }

    /** Top-level objects of one grammar. */
    public List<CorpusObject> objects(String grammarId) {
        return objectsByGrammar.getOrDefault(grammarId, List.of());
    }

    /** Objects and all their descendants, depth-first (parents before children). */
    public Stream<CorpusObject> flatten() {
        return objects.stream().flatMap(Corpus::selfAndDescendants);
    }

    /** Objects of the given type, nested objects included. */
    public <T> List<T> objectsOf(Class<T> type) {
        return flatten().filter(type::isInstance).map(type::cast).toList();
    }

    /** Deepest object whose span contains {@code offset}. */
    public Optional<CorpusObject> objectAt(int offset) {
        return deepestAt(objects, offset);
    }

    private static Optional<CorpusObject> deepestAt(List<? extends CorpusObject> objects, int offset) {
        for (CorpusObject o : objects) {
            if (o.getTextCoordinate().contains(offset)) {
                return deepestAt(o.getChildren(), offset).or(() -> Optional.of(o));
            }
        }
        return Optional.empty();
    }

    /** Id of the grammar that produced an object (top-level or nested) of this snapshot. */
    public Optional<String> grammarIdOf(CorpusObject object) {
        return Optional.ofNullable(grammarOf.get(object));
    }

    /** True if the object (top-level or nested) belongs to this snapshot (identity). */
    public boolean owns(CorpusObject object) {
        return grammarOf.containsKey(object);
    }

    /** Parent of an object in its grammar's tree; empty for a top-level object. */
    public Optional<CorpusObject> parentOf(CorpusObject object) {
        return Optional.ofNullable(parentOf.get(object));
    }

    /** Diagnostics of all grammars, in rank order. */
    public List<Diagnostic> diagnostics() {
        return diagnostics;
    }

    public List<Diagnostic> diagnostics(String grammarId) {
        return diagnosticsByGrammar.getOrDefault(grammarId, List.of());
    }

    // ------------------------------------------------------------------
    // Header and sections
    // ------------------------------------------------------------------

    /**
     * Properties of the top-level {@link TextHeader}s, in grammar rank order; the first value wins
     * for a duplicated key. Unmodifiable.
     */
    public Map<String, String> properties() {
        return properties;
    }

    /** All sections of the text, all grammars, nested ones included, in text order. */
    public List<Section> sections() {
        return sections;
    }

    /**
     * Sections of all grammars whose span encloses the object's span, outermost first (the object
     * itself excluded).
     */
    public List<Section> enclosingSections(CorpusObject object) {
        TextCoordinate span = object.getTextCoordinate();
        List<String> ranks = grammarIds();
        return sections.stream()
            .filter(s -> s != object && s.getTextCoordinate().encloses(span))
            .sorted(Comparator.<Section>comparingInt(s -> s.getTextCoordinate().length()).reversed()
                .thenComparingInt(s -> ranks.indexOf(grammarOf.get(s))))
            .toList();
    }

    /**
     * Metadata applying to an object: the text {@linkplain #properties() properties}, overridden by
     * the metadata of each enclosing section from the outermost to the innermost, and by the
     * object's own metadata if it is a section. Unmodifiable.
     */
    public Map<String, String> metadataOf(CorpusObject object) {
        Map<String, String> result = new LinkedHashMap<>(properties);
        for (Section s : enclosingSections(object)) {
            result.putAll(s.getMetadata());
        }
        if (object instanceof Section s) {
            result.putAll(s.getMetadata());
        }
        return Collections.unmodifiableMap(result);
    }

    @Override
    public String toString() {
        return "Text[" + id + ", " + objects.size() + " objects, " + diagnostics.size() + " diagnostics]";
    }

    // ------------------------------------------------------------------
    // Builder
    // ------------------------------------------------------------------

    /** Assembles a {@code Text} from the results of each grammar, validating the spans. */
    public static final class Builder {

        private final TextId id;
        private final String content;
        private final Map<String, List<CorpusObject>> objects = new LinkedHashMap<>();
        private final Map<String, List<Diagnostic>> diagnostics = new LinkedHashMap<>();

        private Builder(TextId id, String content) {
            this.id = Objects.requireNonNull(id, "id");
            this.content = Objects.requireNonNull(content, "content");
        }

        /**
         * Adds what one grammar produced (call in rank order). Objects whose span, a descendant's
         * span or a field span is invalid are dropped and reported as diagnostics.
         */
        public Builder add(String grammarId, List<? extends CorpusObject> objects, List<Diagnostic> diagnostics) {
            List<CorpusObject> valid = new ArrayList<>();
            List<Diagnostic> diags = new ArrayList<>(diagnostics);
            for (CorpusObject o : objects) {
                String problem = o == null ? "null object" : findProblem(o, null);
                if (problem == null) {
                    valid.add(o);
                    if (!o.getKind().startsWith(grammarId + ":")) {
                        logger.warning("Kind '" + o.getKind() + "' is not prefixed by the grammar id '" + grammarId + "'");
                    }
                } else {
                    int line = o == null || o.getTextCoordinate() == null ? 0 : o.getTextCoordinate().startLine();
                    diags.add(Diagnostic.at(id, grammarId, line, 0,
                        "Object ignored, the grammar returned an invalid span: " + problem));
                }
            }
            this.objects.put(grammarId, List.copyOf(valid));
            this.diagnostics.put(grammarId, List.copyOf(diags));
            return this;
        }

        private String findProblem(CorpusObject o, CorpusObject parent) {
            TextCoordinate span = o.getTextCoordinate();
            if (span == null) return o.getKind() + " has no span";
            if (o.getKind() == null) return "object without kind at " + span;
            if (!Objects.equals(span.text(), id)) return o.getKind() + " refers to another text: " + span.text();
            if (span.isEmpty() || !span.isValidFor(content.length())) return o.getKind() + " " + span;
            if (parent != null && !parent.getTextCoordinate().encloses(span)) {
                return o.getKind() + " " + span + " is outside its parent " + parent.getTextCoordinate();
            }
            if (o instanceof HasFields withFields) {
                for (Field f : withFields.getFields()) {
                    TextCoordinate fs = f.span();
                    if (!Objects.equals(fs.text(), id) || !fs.isValidFor(content.length()) || !span.encloses(fs)) {
                        return "field " + f.name() + " of " + o.getKind() + " has an invalid span " + fs;
                    }
                }
            }
            for (CorpusObject child : o.getChildren()) {
                String p = child == null ? "null child of " + o.getKind() : findProblem(child, o);
                if (p != null) return p;
            }
            return null;
        }

        public Text build() {
            return new Text(id, content, new LinkedHashMap<>(objects), new LinkedHashMap<>(diagnostics));
        }
    }
}
