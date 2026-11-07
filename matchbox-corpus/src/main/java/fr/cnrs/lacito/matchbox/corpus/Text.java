package fr.cnrs.lacito.matchbox.corpus;

import fr.cnrs.lacito.matchbox.corpus.capability.Section;
import fr.cnrs.lacito.matchbox.corpus.capability.Tag;
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

    /** {@link #version()} of a text whose document version is not known. */
    public static final long UNKNOWN_VERSION = -1;

    private final TextId id;
    private final String content;
    private final long version;
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
    private final List<MatchConflict> conflicts;
    /** Objects in drawing order, computed on first use. */
    private volatile List<CorpusObject> layered;
    /** Targets of the tags, computed on first use. */
    private volatile TagAttachment tagAttachment;

    private Text(TextId id, String content, long version, Map<String, List<CorpusObject>> objectsByGrammar,
                 Map<String, List<Diagnostic>> diagnosticsByGrammar, List<MatchConflict> conflicts) {
        this.id = id;
        this.version = version;
        this.conflicts = List.copyOf(conflicts);
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

    /**
     * Version of the document the content was taken from, {@link #UNKNOWN_VERSION} if not given.
     * An opaque number for this module: the application uses it to move edits computed against
     * this content to the current text of the document.
     */
    public long version() {
        return version;
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

    /**
     * The object drawn on top at {@code offset}: the last object of {@link #layered()} containing
     * it, i.e. the innermost annotation, else the deepest exclusive object, else the deepest scope.
     */
    public Optional<CorpusObject> objectAt(int offset) {
        List<CorpusObject> all = layered();
        for (int i = all.size() - 1; i >= 0; i--) {
            if (all.get(i).getTextCoordinate().contains(offset)) {
                return Optional.of(all.get(i));
            }
        }
        return Optional.empty();
    }

    /**
     * Objects and their descendants in drawing order: by {@link MatchRole} (scopes, then exclusive
     * objects, then annotations), then grammar rank, then depth-first (parents before children).
     * What is drawn last is on top, and is what {@link #objectAt} returns.
     */
    public List<CorpusObject> layered() {
        List<CorpusObject> l = layered;
        if (l == null) {
            List<CorpusObject> all = new ArrayList<>(flatten().toList());
            // stable: keeps rank and depth-first order within a role
            all.sort(Comparator.comparingInt(o -> o.getMatchRole().ordinal()));
            l = List.copyOf(all);
            layered = l;
        }
        return l;
    }

    /**
     * Readings of the same characters by several grammars found when the text was built, in grammar
     * order; those of kind {@link MatchConflict.Kind#DROPPED} are not among the objects.
     */
    public List<MatchConflict> conflicts() {
        return conflicts;
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

    // ------------------------------------------------------------------
    // Tags
    // ------------------------------------------------------------------

    private TagAttachment tags() {
        TagAttachment t = tagAttachment;
        if (t == null) {
            t = new TagAttachment(content, objectsByGrammar);
            tagAttachment = t;
        }
        return t;
    }

    /**
     * The object a tag applies to: the object on the line just above it, else just below it (no
     * blank line in between, lines made only of tags skipped, the outermost object, inside the object
     * containing the tag), else the innermost object containing it; empty when it applies to the
     * whole text.
     */
    public Optional<CorpusObject> targetOf(Tag tag) {
        return tags().target(tag);
    }

    /** Tags applying directly to an object. */
    public List<Tag> tagsOf(CorpusObject object) {
        return tags().tagsOf(object);
    }

    /** Tags applying to the whole text (in no object and next to none). */
    public List<Tag> textTags() {
        return tags().textTags();
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
        private final OverlapResolver resolver;
        private long version = UNKNOWN_VERSION;

        private Builder(TextId id, String content) {
            this.id = Objects.requireNonNull(id, "id");
            this.content = Objects.requireNonNull(content, "content");
            this.resolver = new OverlapResolver(id);
        }

        /** Same as {@link #add(String, String, List, List)} with the id as the grammar's name. */
        public Builder add(String grammarId, List<? extends CorpusObject> objects, List<Diagnostic> diagnostics) {
            return add(grammarId, grammarId, objects, diagnostics);
        }

        /**
         * Adds what one grammar produced (call in rank order: earlier grammars win, see
         * {@link MatchRole}). Objects whose span, a descendant's span or a field span is invalid
         * are dropped and reported as errors; exclusive objects meeting those of an earlier grammar
         * are dropped and reported as warnings.
         *
         * @param grammarName name of the grammar in the warnings (its display name)
         */
        public Builder add(String grammarId, String grammarName, List<? extends CorpusObject> objects,
                           List<Diagnostic> diagnostics) {
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
            OverlapResolver.Outcome outcome = resolver.add(grammarId, grammarName, valid);
            diags.addAll(outcome.warnings());
            this.objects.put(grammarId, outcome.kept());
            this.diagnostics.put(grammarId, List.copyOf(diags));
            return this;
        }

        /**
         * Characters owned by the exclusive objects of the grammars added so far, sorted by
         * position (given to the next grammar, see {@code ParseContext}).
         */
        public List<Claim> claims() {
            return resolver.claims();
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

        /** Version of the document {@code content} was taken from (see {@link Text#version()}). */
        public Builder version(long version) {
            this.version = version;
            return this;
        }

        public Text build() {
            return new Text(id, content, version, new LinkedHashMap<>(objects), new LinkedHashMap<>(diagnostics),
                resolver.conflicts());
        }
    }
}
