# Plan: `Corpus`, `Text` and `TextId` in matchbox-corpus

Status: implemented (not committed). Decisions taken: `ParseError` is removed (replaced by
`Diagnostic`), `TextCoordinate` refers to a `TextId` rather than a `Path`. Amended by
`EDITABLE_VIEWS_PLAN.md` (see §7); differences between the plan and the code in §8.

## 0. Goal

- Move `Corpus` from `matchbox-app` (`fr.cnrs.lacito.matchbox.model`) to `matchbox-corpus`
  (`fr.cnrs.lacito.matchbox.corpus`), next to `CorpusObject`.
- Introduce `Text`: the parsed state of one file (its content, its objects, its diagnostics, its
  header properties), so that objects can be looked up by text and an object's text can be found.
- Let features working on an object reach its text and the whole corpus.
- Add sections (title, metadata, content with subsections) as a capability, with a reference
  implementation and a default view (§2.9).

Design in one sentence: **strong references only point down (corpus → text → object → coordinate
→ text id); going up is a lookup by `TextId` in the corpus; code that needs the corpus receives it
as a parameter.**

```
Corpus ──owns──▶ Text ──owns──▶ CorpusObject ──has──▶ TextCoordinate ──has──▶ TextId
  ▲                                                                            │
  └──────────── corpus.text(id) / corpus.textOf(object) ◀──────────────────────┘
```

---

## 1. Current state

| Concern | Where | Problem for the goal |
|---|---|---|
| `Corpus` | `matchbox-app/.../model/Corpus.java` | Uses `ObservableList` (JavaFX) and `ParseResult`/`ParseError` (`matchbox-grammar-api`, which depends on `matchbox-corpus`): moving it as is creates a JavaFX dependency and a module cycle |
| Per-file state | `Corpus`: `Map<Path, Map<String, ParseResult>>` + `Map<Path, String> parsedTexts` | No object for "a parsed text"; every query takes a `Path` |
| Span | `TextCoordinate(Path path, …)` | Paths are compared with `Objects.equals` without normalisation |
| Errors | `grammarapi.ParseError`: mutable class, public fields, optional ANTLR references (`RecognitionException`, `Recognizer`, offending symbol) | ANTLR getters are used nowhere outside the class; ANTLR references would keep parsers in memory |
| Observable lists | `Corpus.getCorpus()`, `Corpus.getErrors()` | Used only in `CorpusTest`; the GUI relies on `addFileListener` |
| Parsing outside the corpus | `assist.validation.EditValidator.parse` | Parses candidate texts that never enter the corpus |

### Why no back-pointers (object → `Text`, `Text` → `Corpus`)

1. Objects are built by the grammar, off the JavaFX thread, before a `Text` exists; a back-pointer
   needs a setter or a lazy holder, contradicting immutability.
2. `TextCoordinate` is a record: `equals`/`hashCode` would recurse through `Text` → objects →
   coordinates → `Text`.
3. Re-parsing replaces a file's results: back-pointers would either show a `Text` that no longer
   matches the object (mutable `Text`) or an outdated one (replaced `Text`).
4. `EditValidator` parses texts that belong to no corpus.

---

## 2. Target design (package `fr.cnrs.lacito.matchbox.corpus`)

### 2.1 `TextId`

```java
/** Identity of a text, known before it is parsed. */
public record TextId(Path path) {
    public TextId { Objects.requireNonNull(path, "path"); }
    /** Absolute, normalised path: two spellings of the same file give equal ids. */
    public static TextId of(Path path) { return new TextId(path.toAbsolutePath().normalize()); }
    /** File name, for display. */
    public String displayName() { ... }
    @Override public String toString() { return path.toString(); }
}
```

- Created by the application (`Project`, `EditValidator`) and passed to the grammar.
- The canonical constructor does not normalise (tests may use relative paths); `of` is what the
  application uses.

### 2.2 `TextCoordinate`

```java
public record TextCoordinate(TextId text, int startIndex, int stopIndex, int startLine, int endLine)
```

- `path` → `text`; `encloses` compares `text`; `contains`, `isValidFor` unchanged.

### 2.3 `Diagnostic` (replaces `grammarapi.ParseError`)

```java
/**
 * A problem reported while parsing a text: syntax error, invalid object, internal grammar error.
 *
 * @param startIndex first offending character (0-based), -1 if unknown; may be the text length
 *                   for an error at end of file
 * @param stopIndex  last offending character (inclusive), -1 if unknown; startIndex - 1 for an
 *                   empty range
 * @param expected   tokens expected here, as ANTLR displays them ("'<'", "IMB_LINE", "<EOF>")
 */
public record Diagnostic(TextId text, String grammarId, int line, int column, String message,
                         int startIndex, int stopIndex, List<String> expected) {
    public Diagnostic { expected = List.copyOf(expected); }
    /** Diagnostic without offsets. */
    public static Diagnostic at(TextId text, String grammarId, int line, int column, String message) { ... }
    /** Span of the offending input, if known and not empty (same rule as ParseError.span()). */
    public Optional<TextCoordinate> span() { ... }
}
```

- No ANTLR type: `matchbox-corpus` stays free of ANTLR.
- Renames for callers: `getFile()` → `text()` (a `TextId`), `getCharPositionInLine()` → `column()`,
  `getLine()` → `line()`, `getMessage()` → `message()`, `getGrammarId()` → `grammarId()`,
  `getStartIndex()`/`getStopIndex()`/`getExpected()` → record accessors. `toString()` keeps
  `"line L:C message"`.

### 2.4 `Text`

Immutable snapshot of one parse of one file. A new parse gives a new `Text`; an object always
agrees with the `Text` it came from.

```java
public final class Text {
    public TextId id();
    public String content();                                   // what the spans refer to
    public List<String> grammarIds();                          // rank order
    public List<CorpusObject> objects();                       // top-level, all grammars, rank order
    public List<CorpusObject> objects(String grammarId);
    public Stream<CorpusObject> flatten();                     // parents before children
    public <T> List<T> objectsOf(Class<T> type);               // nested included
    public Optional<CorpusObject> objectAt(int offset);        // deepest
    public Optional<String> grammarIdOf(CorpusObject object);  // by identity, nested included
    public boolean owns(CorpusObject object);                  // by identity, nested included
    public List<Diagnostic> diagnostics();                     // rank order
    public List<Diagnostic> diagnostics(String grammarId);
    public Map<String, String> properties();                   // header, §2.7
    public Optional<CorpusObject> parentOf(CorpusObject object);   // §2.9.4
    public List<Section> sections();                           // §2.9.4
    public List<Section> enclosingSections(CorpusObject object);   // §2.9.4
    public Map<String, String> metadataOf(CorpusObject object);    // §2.9.4

    public static Builder builder(TextId id, String content);
    public static final class Builder {
        /** Adds what one grammar produced; invalid objects become diagnostics (§2.6). */
        public Builder add(String grammarId, List<? extends CorpusObject> objects, List<Diagnostic> diagnostics);
        public Text build();
    }
}
```

- `owns`/`grammarIdOf` use an identity map built lazily (or in `build()`) from `flatten()`.
- `content` is non-null (edits from editable views are computed against it, see
  `EDITABLE_VIEWS_PLAN.md`); callers passing `null` today (`setResults(..., null)`) pass the
  document text.

### 2.5 `CorpusView` and `Corpus`

```java
/** Read-only access to the texts of a corpus. Safe to use from any thread. */
public interface CorpusView {
    List<Text> texts();                               // sorted by id
    Optional<Text> text(TextId id);
    default Optional<Text> textOf(CorpusObject o) {   // up-lookup; empty for a stale object
        return text(o.getTextCoordinate().text()).filter(t -> t.owns(o));
    }
    default <T> Stream<T> objectsOf(Class<T> type) {
        return texts().stream().flatMap(t -> t.objectsOf(type).stream());
    }
    default Stream<Diagnostic> diagnostics() { ... }
}

/** Called after a text was added, replaced or removed. */
@FunctionalInterface
public interface CorpusListener {
    void textChanged(TextId id, Optional<Text> before, Optional<Text> after);
}

/** The texts of a project. Writes from one thread (the JavaFX thread in the GUI); reads from any. */
public final class Corpus implements CorpusView {
    private volatile Map<TextId, Text> texts = Map.of();   // replaced copy-on-write
    private final List<CorpusListener> listeners = new CopyOnWriteArrayList<>();

    public void put(Text text);        // replaces the text with the same id, fires listeners
    public void remove(TextId id);     // fires listeners if there was a text
    public void addListener(CorpusListener l);
    public void removeListener(CorpusListener l);
    public static Stream<CorpusObject> selfAndDescendants(CorpusObject o);  // kept, used by Text
}
```

- No JavaFX: the observable lists disappear (only `CorpusTest` used them). If a view ever needs
  one, an adapter in `matchbox-gui` builds it from `CorpusListener`.
- The current `Corpus` is not thread-safe; the new one gives consistent reads from any thread
  (assistant, exports) since the map is replaced whole and `Text` is immutable.

### 2.6 Validation

`Corpus.validate`/`findProblem` move to `Text.Builder.add`, same rules and messages:
null object, missing span or kind, span in another text (`"refers to another file"` →
`"refers to another text"`), span invalid for the content, child outside its parent; the object is
dropped and a `Diagnostic` is added. The warning for a kind not prefixed by the grammar id is kept.

### 2.7 Header properties

New capability (admission rule: needed by the "text properties" feature):

```java
package fr.cnrs.lacito.matchbox.corpus.capability;
/** Properties declared in the header of a text (e.g. language, speaker, date). */
public interface TextHeader extends CorpusObject {
    Map<String, String> properties();
}
```

- A grammar recognising a header returns a `TextHeader` among its top-level objects.
- `Text.properties()`: merge of the top-level `TextHeader`s, grammar rank order, first value wins
  for a duplicated key; unmodifiable.
- Optional reference implementation `simple.SimpleTextHeader`.
- No grammar produces one yet: implementing a header in the notebook grammar is a separate task.

### 2.8 Reaching the corpus from object-related code

- No `getText()`/`getCorpus()` on `CorpusObject`.
- `matchbox-grammar-fx`: `ViewContext` gains `CorpusView corpus()` (plugin views can show the
  header, resolve references, list sibling objects).
- Exports, search, cross-linking take a `CorpusView`.
- A future capability that resolves something takes it as a parameter, e.g.
  `Optional<LexicalEntry> resolve(CorpusView corpus)`.

### 2.9 Sections

A section is a titled division of a text (a chapter, an elicitation session, a story, a Markdown
`#`/`##` heading…) with metadata (speaker, date, place…) and its own content, subsections
included. Admission rule: needed by application features (outline of a text, metadata inherited
by the objects of a section, filtering/search and exports by section).

#### 2.9.1 Two ways an object belongs to a section

A file can be parsed by several grammars: a "structure" grammar may produce the sections while
another grammar produces the interlinear sentences of the same file. Hence two relations:

| Relation | Defined by | Covers | Used for |
|---|---|---|---|
| **Content** (`Section.getContent()`) | the grammar that built the section | objects of that grammar only, subsections included | the tree of the section, `getChildren()` |
| **Enclosure** (`Text.enclosingSections(o)`) | spans: sections of *any* grammar whose span encloses the object's span | every object of the text | effective metadata, "in which section is this object", filtering by section |

The view and the effective metadata rely on enclosure, so they work whether or not the sections
and the other objects come from the same grammar. No back-pointer is needed (§1): enclosure is
computed by the `Text` from spans.

#### 2.9.2 Capability interface (`fr.cnrs.lacito.matchbox.corpus.capability.Section`)

```java
package fr.cnrs.lacito.matchbox.corpus.capability;

import fr.cnrs.lacito.matchbox.corpus.CorpusObject;
import java.util.List;
import java.util.Map;

/**
 * A titled division of a text (chapter, session, story…) with metadata and its own content.
 *
 * <p>The span of a section covers its heading, its metadata and its content. The content holds the
 * objects the same grammar found in the section, in text order, subsections included; objects of
 * other grammars lying in the section are found with {@code Text.enclosingSections}.
 */
public interface Section extends CorpusObject {

    /** Title; empty string for an untitled section, never {@code null}. */
    String getTitle();

    /**
     * Metadata declared by the section itself (e.g. {@code speaker}, {@code date}), in declaration
     * order; unmodifiable, never {@code null}. Values inherited from enclosing sections or the
     * text header are not included: see {@code Text.metadataOf}.
     */
    Map<String, String> getMetadata();

    /** Content in text order: corpus objects and subsections; unmodifiable, never {@code null}. */
    List<? extends CorpusObject> getContent();

    /** Direct subsections, in text order. */
    default List<Section> getSubsections() {
        return getContent().stream()
            .filter(Section.class::isInstance)
            .map(Section.class::cast)
            .toList();
    }

    @Override
    default String getLabel() {
        return getTitle().isBlank() ? getKind() : getTitle();
    }

    @Override
    default List<? extends CorpusObject> getChildren() {
        return getContent();
    }
}
```

Notes:

- No `getLevel()`: the nesting depth is given by the tree (`Text.enclosingSections(s).size()`),
  and the notation's own level (number of `#`) is a grammar detail. Add it only if a feature needs
  it.
- `Map<String, String>`: one value per key, as asked. If a notation allows repeated keys (two
  speakers), a grammar joins them (`"A; B"`); a `Map<String, List<String>>` can be introduced later
  if a feature needs it.
- With `EDITABLE_VIEWS_PLAN.md`: a section implementing `HasFields` exposes `title` and
  `metadata:<key>` fields (constants `Section.TITLE = "title"`,
  `static String metadataField(String key)`), making the title and the metadata values editable
  from the view.

#### 2.9.3 Reference implementation (`fr.cnrs.lacito.matchbox.corpus.simple.SimpleSection`)

```java
package fr.cnrs.lacito.matchbox.corpus.simple;

import fr.cnrs.lacito.matchbox.corpus.CorpusObject;
import fr.cnrs.lacito.matchbox.corpus.TextCoordinate;
import fr.cnrs.lacito.matchbox.corpus.capability.Section;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** An immutable section: title, metadata in declaration order, content in text order. */
public final class SimpleSection extends AbstractCorpusObject implements Section {

    private final String title;
    private final Map<String, String> metadata;
    private final List<CorpusObject> content;

    /**
     * @param title    title, {@code null} or empty for an untitled section
     * @param metadata keys and values, neither {@code null}; the iteration order is kept
     * @param content  objects and subsections of this grammar inside the section, in text order
     */
    public SimpleSection(String kind, TextCoordinate coordinate, String title,
                         Map<String, String> metadata, List<? extends CorpusObject> content) {
        super(kind, coordinate);
        this.title = title == null ? "" : title;
        Map<String, String> copy = new LinkedHashMap<>();
        metadata.forEach((k, v) -> copy.put(Objects.requireNonNull(k, "metadata key"),
                                            Objects.requireNonNull(v, "metadata value")));
        this.metadata = Collections.unmodifiableMap(copy);   // Map.copyOf would lose the order
        this.content = List.copyOf(content);
    }

    @Override
    public String getTitle() {
        return title;
    }

    @Override
    public Map<String, String> getMetadata() {
        return metadata;
    }

    @Override
    public List<CorpusObject> getContent() {
        return content;
    }
}
```

- Immutable like the other `Simple*` classes; the general rules apply (content spans inside the
  section span, checked by `Text.Builder`).
- A grammar building nested sections from flat headings (Markdown) keeps a stack of open sections
  and builds each `SimpleSection` when it is closed (next heading of the same or a higher level,
  or end of file), since a section's content and span are only known at that point.

#### 2.9.4 Lookups in `Text` (additions to §2.4)

```java
/** Parent of an object in its grammar's tree; empty for a top-level object. */
public Optional<CorpusObject> parentOf(CorpusObject object);

/** Sections of all grammars whose span encloses the object's span, outermost first (the object itself excluded). */
public List<Section> enclosingSections(CorpusObject object);

/** All sections of the text, all grammars, nested ones included, in text order. */
public List<Section> sections();

/**
 * Metadata applying to an object: the text properties (§2.7), overridden by the metadata of each
 * enclosing section from the outermost to the innermost (and by the object's own metadata if it is
 * a section).
 */
public Map<String, String> metadataOf(CorpusObject object);
```

- `parentOf`: identity map child → parent built with the other identity maps of `Text`.
- `enclosingSections`: filters `sections()` with `encloses` (identity excluded), sorted by
  decreasing span length, then grammar rank. A text has few sections: a linear scan is enough.
- This answers the original need ("access properties of the text an object belongs to"):
  `corpus.textOf(o).map(t -> t.metadataOf(o))`.
- `CorpusView.objectsOf(Section.class)` already gives the sections of the whole corpus.

#### 2.9.5 Relation with `TextHeader` (§2.7)

Kept separate: a header describes the whole text and contains nothing; many texts have a header
and no sections. `Text.metadataOf` combines both (text properties first, then sections).

#### 2.9.6 Default view (`fr.cnrs.lacito.matchbox.gui.view.SectionViewFactory`)

Registered in matchbox-gui's `META-INF/services/fr.cnrs.lacito.matchbox.grammarfx.ObjectViewFactory`.
Detail view, top to bottom:

1. **Title** (bold, larger) and the **position** (`GenericObjectViewFactory.position`, click →
   select the heading in the editor).
2. **Breadcrumb** of enclosing sections (`Text.enclosingSections`), each clickable
   (`context.select`); hidden for a top-level section.
3. **Metadata**: two-column grid key / value, in declaration order; a second, greyed group
   "inherited" shows the values coming from enclosing sections and the text header that the
   section does not override (`Text.metadataOf` minus `getMetadata()`); hidden when empty.
4. **Summary**: "2 subsections · 14 objects (10 igt, 4 entries)", counting the content of all
   grammars lying in the section (enclosure), by kind label.
5. **Outline**: a `TreeView` of the content (subsections expandable, other objects as leaves,
   `label [kind]`), objects of other grammars lying directly in the section included, in text
   order; single click → `context.selectInEditor(span)`; double click → `context.select(object)`
   (shows that object's own view).

Table columns: **Title**, **Metadata** (`key=value; …`), **Content** (number of objects).

```java
package fr.cnrs.lacito.matchbox.gui.view;

/** Built-in view of sections: title, metadata (own and inherited), outline of the content. */
public class SectionViewFactory implements ObjectViewFactory<Section> {

    @Override
    public Class<Section> getType() {
        return Section.class;
    }

    @Override
    public List<ObjectColumn<Section>> getColumns() {
        return List.of(
            new ObjectColumn<>("Title", Section::getTitle),
            new ObjectColumn<>("Metadata", s -> s.getMetadata().entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("; "))),
            new ObjectColumn<>("Content", s -> Integer.toString(s.getContent().size()))
        );
    }

    @Override
    public Node createView(Section section, ViewContext context) {
        Optional<Text> text = context.corpus().textOf(section);   // empty for a stale object

        Label title = new Label(section.getLabel());
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 1.2em;");
        Label position = new Label(GenericObjectViewFactory.position(section.getTextCoordinate()));
        position.setStyle("-fx-text-fill: #777777;");
        position.setOnMouseClicked(e -> context.selectInEditor(section.getTextCoordinate()));

        VBox box = new VBox(6, new HBox(10, title, position));
        box.setStyle("-fx-padding: 8;");
        text.ifPresent(t -> breadcrumb(t, section, context).ifPresent(box.getChildren()::add));
        metadataGrid(section, text).ifPresent(box.getChildren()::add);
        List<CorpusObject> content = text.map(t -> contentInText(t, section))
            .orElse(List.copyOf(section.getContent()));
        box.getChildren().add(new Label(summary(section, content)));
        TreeView<CorpusObject> outline = outline(content, context);
        VBox.setVgrow(outline, Priority.ALWAYS);
        box.getChildren().add(outline);
        return box;
    }

    /** Enclosing sections as links, outermost first; empty for a top-level section. */
    private static Optional<Node> breadcrumb(Text text, Section section, ViewContext context) {
        List<Section> path = text.enclosingSections(section);
        if (path.isEmpty()) return Optional.empty();
        HBox crumbs = new HBox(4);
        for (Section s : path) {
            Hyperlink link = new Hyperlink(s.getLabel());
            link.setOnAction(e -> context.select(s));
            crumbs.getChildren().addAll(link, new Label("›"));
        }
        return Optional.of(crumbs);
    }

    /** Own metadata, then inherited values not overridden (greyed). */
    private static Optional<Node> metadataGrid(Section section, Optional<Text> text) {
        Map<String, String> own = section.getMetadata();
        Map<String, String> inherited = new LinkedHashMap<>(text.map(t -> t.metadataOf(section)).orElse(Map.of()));
        inherited.keySet().removeAll(own.keySet());
        if (own.isEmpty() && inherited.isEmpty()) return Optional.empty();
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(2);
        int row = 0;
        for (var e : own.entrySet()) addRow(grid, row++, e.getKey(), e.getValue(), false);
        for (var e : inherited.entrySet()) addRow(grid, row++, e.getKey(), e.getValue(), true);
        return Optional.of(grid);
    }

    private static void addRow(GridPane grid, int row, String key, String value, boolean inherited) {
        Label k = new Label(key + ":");
        k.setStyle("-fx-font-weight: bold;" + (inherited ? " -fx-text-fill: #888888;" : ""));
        Label v = new Label(value);
        if (inherited) v.setStyle("-fx-text-fill: #888888;");
        grid.addRow(row, k, v);
    }

    /**
     * Content of the section across grammars, in text order: its own content, plus the top-level
     * objects of other grammars whose innermost enclosing section is this one.
     */
    static List<CorpusObject> contentInText(Text text, Section section) {
        List<CorpusObject> result = new ArrayList<>(section.getContent());
        Set<CorpusObject> own = Collections.newSetFromMap(new IdentityHashMap<>());
        Corpus.selfAndDescendants(section).forEach(own::add);
        for (CorpusObject o : text.objects()) {
            if (own.contains(o)) continue;
            List<Section> enclosing = text.enclosingSections(o);
            if (!enclosing.isEmpty() && enclosing.get(enclosing.size() - 1) == section) {
                result.add(o);
            }
        }
        result.sort(Comparator.comparingInt(o -> o.getTextCoordinate().startIndex()));
        return result;
    }

    private static String summary(Section section, List<CorpusObject> content) { ... }  // counts by kind

    private static TreeView<CorpusObject> outline(List<CorpusObject> content, ViewContext context) {
        TreeItem<CorpusObject> root = new TreeItem<>();
        content.forEach(o -> root.getChildren().add(item(o)));
        TreeView<CorpusObject> tree = new TreeView<>(root);
        tree.setShowRoot(false);
        tree.setCellFactory(tv -> new TreeCell<>() {
            @Override
            protected void updateItem(CorpusObject o, boolean empty) {
                super.updateItem(o, empty);
                setText(empty || o == null ? null : o.getLabel() + "   [" + o.getKind() + "]");
            }
        });
        tree.setOnMouseClicked(e -> {
            TreeItem<CorpusObject> selected = tree.getSelectionModel().getSelectedItem();
            if (selected == null || selected.getValue() == null) return;
            if (e.getClickCount() == 2) context.select(selected.getValue());
            else context.selectInEditor(selected.getValue().getTextCoordinate());
        });
        return tree;
    }

    /** Subsections are expanded; other objects show their own children collapsed. */
    private static TreeItem<CorpusObject> item(CorpusObject o) {
        TreeItem<CorpusObject> item = new TreeItem<>(o);
        o.getChildren().forEach(c -> item.getChildren().add(item(c)));
        item.setExpanded(o instanceof Section);
        return item;
    }
}
```

- Uses `ViewContext.corpus()` (§2.8) for the breadcrumb, the inherited metadata and the content
  of other grammars; without a resolvable `Text` (stale object) it falls back to the section's
  own content.
- `contentInText` is only one level deep for other grammars' objects: objects of another grammar
  inside a subsection appear under that subsection only if the subsection's own grammar put them
  there; showing them under every subsection would need the same merge recursively (possible
  improvement, kept simple at first).
- Chosen by `ObjectViewRegistry` for any class implementing `Section` (closest supertype rule);
  a plugin can still provide its own factory for its section class.

#### 2.9.7 First producer (follow-up, not part of this plan)

The notebook module already contains `MarkdownHeadingLexer.g4`/`MarkdownHeadingParser.g4`
(a heading, an optional YAML block of `key: value`, then the lines of the section), not used by
any Java code yet. A grammar plugin "markdown-sections" built on it would produce nested
`SimpleSection`s (nesting by number of `#`, metadata from the YAML block), to be combined with the
notebook grammar on the same files: the case §2.9.1 is designed for.

---

## 3. Module dependencies after the change

```
matchbox-corpus          (Corpus, CorpusView, Text, TextId, Diagnostic, TextCoordinate, CorpusObject; no JavaFX, no ANTLR)
   ▲
matchbox-grammar-api     (GrammarPlugin, ParseResult, CollectingErrorListener, Spans; ANTLR)
   ▲
matchbox-app / matchbox-assist / grammars / matchbox-grammar-fx / matchbox-gui
```

`Text` is assembled in `matchbox-app` (and `matchbox-assist` if needed) from `ParseResult`s: the
corpus module never sees `ParseResult`.

---

## 4. Phases

Phases 1–3 change signatures used everywhere and are done together (the build is broken in
between); afterwards each phase compiles and its tests pass.

### Phase 1 — matchbox-corpus

1. Add `TextId`, `Diagnostic`, `Text` (+ `Builder`), `CorpusView`, `CorpusListener`, `Corpus`,
   `capability/TextHeader` and `capability/Section` (+ `package-info` entries),
   `simple/SimpleSection`, optionally `simple/SimpleTextHeader`.
2. `TextCoordinate`: `Path path` → `TextId text`.
3. Move `TextEdit` from `assist.suggestion` to `fr.cnrs.lacito.matchbox.corpus` (needed by
   `TextDocument`, grammar-api, grammar-fx and the GUI; `EDITABLE_VIEWS_PLAN.md` §4.3 adds
   `replace(String, TextCoordinate, String)` and `inverse()`).
4. `simple.*`, `AbstractCorpusObject`: only Javadoc/`toString` if they mention the path.
5. Test-jar `CorpusAssertions`: adapt if it reads `path()`; add `assertOwnedBy(Text, CorpusObject)`
   if useful.
6. Tests (new, in matchbox-corpus): port `matchbox-app` `CorpusTest` to `Corpus`/`Text`
   (replacement per text, validation rules, `objectAt`, `flatten`, `grammarIdOf`); add
   `textOf` (found, stale object after replacement → empty), `TextId.of` normalisation,
   `Text.properties()` merge order, listener `before`/`after`; sections: `SimpleSection`
   (metadata order kept, null keys/values rejected, `getSubsections`, label of an untitled
   section), `parentOf`, `enclosingSections` across two grammars (sections from one, IGTs from
   the other), `metadataOf` overriding order (text header < outer section < inner section).

### Phase 2 — matchbox-grammar-api

1. Delete `ParseError`.
2. `ParseResult(List<CorpusObject> objects, List<Diagnostic> diagnostics)` (`errors` → `diagnostics`).
3. `CollectingErrorListener(TextId text, String grammarId)` produces `Diagnostic`s; `getErrors()` →
   `getDiagnostics()`.
4. `Spans.of(TextId text, ParserRuleContext ctx)`.
5. `GrammarPlugin.getResult(TextId text, String content)`; `API_VERSION = 3`; Javadoc.
   `EDITABLE_VIEWS_PLAN.md` adds `default ObjectEditor getObjectEditor()` to the same version: if
   both plans are implemented in sequence before a release, keep a single version bump.
6. `AbstractGrammarPlugin`: adapt if it touches these signatures.

### Phase 3 — grammars

1. `NotebookGrammarPlugin`, `ConllGrammarPlugin`: `getResult(TextId, String)`, `Spans.of(text, …)`
   (5 sites), `new CollectingErrorListener(text, getId())`, internal errors as `Diagnostic.at(...)`.
2. `NotebookGrammarPluginTest`: `TextId` and `Diagnostic`.
3. Check the `write-grammar` skill and any grammar template for the old signatures.

### Phase 4 — matchbox-app

1. `Project`:
   - `TextId` derived from the document path (`TextId.of(document.getFilePath())`).
   - `computeParse` calls `getResult(id, text)`; an exception becomes `Diagnostic.at(id, grammarId,
     0, 0, "Internal error of the grammar: " + e)`.
   - `ParseRun` becomes `(Text text, long version)`, built off the JavaFX thread with
     `Text.builder(id, content).add(grammarId, r.objects(), r.diagnostics())…build()` (validation
     also moves off the JavaFX thread).
   - `applyParse`: `corpus.put(run.text())`; `removeFile(p)` → `corpus.remove(TextId.of(p))`.
   - `getCorpus()` returns `fr.cnrs.lacito.matchbox.corpus.Corpus`.
2. Delete `model/Corpus.java` and `model/CorpusTest.java` (ported in phase 1).
3. `ProjectParseTest`, `AssistantProjectTest`: new API.
4. `TextDocument` stays (editable, saveable buffer); Javadoc of `TextDocument` and `Text`
   distinguishes them. It gets `TextId getId()` (computed once with `TextId.of` when the document
   is created) and `Project` keys its documents by `TextId` (`document(TextId)`), so that a
   `Text` or a span leads to its document. `EDITABLE_VIEWS_PLAN.md` §3 then makes
   `TextDocument` the owner of the text (edits, change events, per-document undo history).

### Phase 5 — matchbox-assist

1. `ParseError` → `Diagnostic` in `AssistContext`, `AssistService`, `PromptBuilder`,
   `FewShotSelector`, `GrammarQuickFixes`, `Suggestion` (`Optional<Diagnostic>`), `TokenNames`
   (Javadoc), `EditValidator` (`Baseline`, `Result.newErrors`, `kind`, `inRange`, `parse`).
2. `Path file` → `TextId text` in `AssistContext`, `EditValidator.check/checkAsync/parse`.
3. `FewShotSelector.ParsedFile` replaced by `Text` (`path()`, `text()`, `objects()`, `errors()` →
   `id()`, `content()`, `objects()`, `diagnostics()`); `Map<Path, List<ParseError>>` → per-`Text`.
4. Tests: `BracketGrammar`, `NotebookIntegrationTest`, `PromptAndServiceTest`, `SuggestionModelTest`.

### Phase 6 — matchbox-grammar-fx

1. `ViewContext.corpus()` returning `CorpusView`.
2. `ConllSentenceViewFactory`: nothing unless it uses `path()`.

### Phase 7 — matchbox-gui

1. `EditorController`: listener → `CorpusListener` (react when `id` is the selected text);
   `corpus.objectsFor/errorsFor/flatten/objectAt(path)` → `corpus.text(id)` then `Text` methods
   (empty text when absent); `span.path()` → `span.text()`; error navigation uses `Diagnostic`.
2. `FileModeController`: error table columns read `line()`, `column()`, `message()`; selection of
   a span in another file compares `span.text()` with the document's `TextId`
   (`d.getFilePath()` → `TextId.of(d.getFilePath())`).
3. `AssistController`: iterate `corpus.texts()`; `errorsFor(file)` → `text(id).diagnostics()`.
4. `SuggestionPanelController`: `Diagnostic` accessors.
5. `GenericObjectViewFactory`: file name from `span.text().displayName()`.
6. `ViewContext` implementation: return the project's corpus.
7. `ObjectViewRegistryTest`: `TextId`; `SimpleSection` → `SectionViewFactory`.
8. `gui.view.SectionViewFactory` (§2.9.6), registered in `META-INF/services`; test of
   `contentInText` (own content + objects of another grammar, text order).

### Phase 8 — documentation

- `CLAUDE.md`: §2 table (matchbox-corpus content), §3 matchbox-corpus and matchbox-grammar-api
  (`getResult(TextId, String)`, `Diagnostic`, no `ParseError`), §6 conventions (references point
  down; up-lookup through `CorpusView`; `Text` is an immutable snapshot), §12 (`Corpus` module,
  `Text`, `CorpusView`, `Diagnostic`).
- `HowAViewIsChosen.md` if it mentions `TextCoordinate.path()`.
- `CLAUDE.md` §3: `Section` in the capability list, `SectionViewFactory` in the table of built-in
  factories; content vs enclosure (§2.9.1).
- Update the signatures in the `write-grammar` skill.

---

## 5. Risks and checks

| Risk | Mitigation |
|---|---|
| `TextId.of` normalises but some code builds `TextId` from a raw path → lookups miss | All application code uses `TextId.of`; `Project` keeps one `TextId` per `TextDocument` |
| Stale objects (selected row after a re-parse) | `textOf` returns empty; GUI re-selects by position as today |
| Validation moved off the JavaFX thread | It only reads immutable data: safe |
| External plugins compiled against API 2 | Rejected by the existing `getApiVersion()` check, with its log message |
| Renaming ~80 `ParseError` call sites | Mechanical; compile errors list them all |

## 6. Decisions

| Question | Decision |
|---|---|
| Back-pointers object → `Text` → `Corpus` | No: up-lookup by `TextId` through `CorpusView`, corpus passed as a parameter |
| `ParseError` | Removed; `Diagnostic` record in matchbox-corpus, without ANTLR |
| `Path` vs `TextId` in `TextCoordinate` | `TextId` |
| JavaFX in `Corpus` | None; `CorpusListener` instead of observable lists |
| Header properties | `capability.TextHeader` objects, merged by `Text.properties()` |
| Sections | `capability.Section` (title, own metadata, content), `simple.SimpleSection`, `SectionViewFactory`; membership across grammars by span enclosure (`Text.enclosingSections`), inherited metadata by `Text.metadataOf` |

## 7. Follow-up: editable views

`EDITABLE_VIEWS_PLAN.md` builds on this plan; its amendments (its §9) are integrated above:

- Phase 1: `TextEdit` moves to matchbox-corpus (item 3); `TextCoordinate.isEmpty()` and empty
  spans for fields are added by `EDITABLE_VIEWS_PLAN.md` phase 1.
- Phase 2: `GrammarPlugin.getObjectEditor()` shares the `API_VERSION = 3` change.
- Phase 4: `TextDocument.getId()`, documents keyed by `TextId`; `TextDocument` becomes the owner
  of the text in `EDITABLE_VIEWS_PLAN.md` phase 2.
- Phase 6: `ViewContext` will also get editing methods (`canEdit`, `setField`, `applyEdits`,
  `undo`, `redo`).
- `Text.content()` is non-null (§2.4).

## 8. Implementation notes (differences from the plan)

- `TextHeader.getProperties()` instead of `properties()`, like the getters of the other
  capability interfaces. `simple.SimpleTextHeader` exists; no grammar produces headers or sections
  yet (§2.9.7 is still a follow-up).
- `AssistContext` keeps its component names (`file`, now a `TextId`; `errors`, now
  `List<Diagnostic>`) to limit the changes in the assistant.
- `FewShotSelector.select` takes `List<Text>` (`ParsedFile` and the map-based overload are removed).
- `Corpus.selfAndDescendants` stays a static helper of `Corpus` (used by `Text` and the views).
- Field-name constant of `Sense` is `DEFINITION` (its accessor is `getDefinition()`), not `GLOSS`.
- Unrelated but blocking a clean build: text blocks opened on the same line as `"""` in
  `NotebookGrammarPlugin` and `ConllGrammarPlugin` (already in HEAD, hidden by stale classes of
  incremental builds) were fixed by a line break after `"""`.

