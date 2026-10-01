# Plan: open corpus-object model with plugin-provided views

Status: implemented (phases 0–7, not committed). Decisions in §6; differences between the plan and the implementation in §9.

## 0. Goal

Let grammars add new kinds of corpus objects without changing `matchbox-corpus`, `matchbox-app`
or `matchbox-gui`, while keeping what makes the application useful across grammars: a single
lexicon built from several files, highlighting and navigation in the editor, and exports.

Design in one sentence: **grammars own their object types and may ship a view for them; the app
needs only a small anchoring interface, plus a short list of capability interfaces for the concepts
it actually works with; the parsing contract contains no GUI code.**

---

## 1. Current state

| Concern | Where | Problem |
|---|---|---|
| Base type | `CorpusObject` (class in `matchbox-corpus`) holding a `TextCoordinate` | Concrete class; every new type is a subclass in `matchbox-corpus` |
| Package | `fr.cnrs.lacito.matchbox.language.corpusObject` (+ an empty `corpusObject/lexicon/` directory) | Does not match the module name; camel-case package |
| Concrete types | `SimpleIGT`, `SimpleLexicalDefinition`, `SimpleSense`, `SimpleExample`, `SimpleDependencyTree` | `SimpleDependencyTree` ignores its constructor arguments and stores nothing |
| Query | `Corpus.getIgtForFile`, `Corpus.getLexicalDefinitionsForFile` | One `instanceof` method per type |
| Highlighting | `EditorController.highlightText` | One loop per type, one colour setting per type in `ApplicationSettings` |
| Click in editor | `EditorController.handleCodeAreaClick` | Looks only at IGTs; `TODO: notify ... to select this IGT` |
| Panels | `FileModeController`: hard-coded `TableView<SimpleIGT>` + lexical `VBox` | CoNLL objects are never shown |
| Lexicon views | `gui/mode/common/lexicon/*Controller` + FXML | Bound to `Simple*` classes |
| Plugins | `PluginManager`: one `URLClassLoader` per jar, parent = app classloader, `ServiceLoader<GrammarPlugin>` | Classloaders are not kept, so other services cannot be looked up in plugin jars |
| Plugin state | `NotebookGrammarPlugin`, `ConllGrammarPlugin` | `objects`/`errors` are instance fields, cleared and returned on each call: the same list instance is shared between results (not re-entrant) |
| Plugin failures | `getResult` catches `Exception` and prints to `System.err` | The user sees an empty result, not an error |
| Threading | `Project.parseTextObject` runs synchronously, on the JavaFX thread, while the user types (throttled at 500 ms) | A slow grammar freezes the editor |
| Tests | none in any module | No safety net for the refactor |

Packaging is already correct for the plan: grammar modules declare `matchbox-corpus` and
`matchbox-grammar-api` as `provided`, so `jar-with-dependencies` bundles only ANTLR.

---

## 2. Target architecture

```
matchbox-corpus          fr.cnrs.lacito.matchbox.corpus             CorpusObject (interface), TextCoordinate
                         fr.cnrs.lacito.matchbox.corpus.capability  LexicalEntry, Sense, Example, InterlinearSentence, DependencySentence
                         fr.cnrs.lacito.matchbox.corpus.simple      SimpleXxx = immutable reference implementations
        │
matchbox-grammar-api     GrammarPlugin, ParseResult(List<CorpusObject>, List<ParseError>)   (no JavaFX)
        │
matchbox-app             Corpus: generic queries (objectsFor, objectsOf, objectAt), span validation
                         PluginManager: keeps classloaders, loadServices(Class<S>), API version check
        │
matchbox-grammar-fx  NEW fr.cnrs.lacito.matchbox.grammarfx  ObjectViewFactory<T>, ObjectColumn<T>, ViewContext   (JavaFX)
        │
matchbox-gui             ObjectViewRegistry (plugin factories → built-in capability views → generic fallback)
                         generic highlighting, click-to-select, dynamic panels
```

Dependencies of a grammar plugin:

| Plugin wants to… | Depends on (`provided`) |
|---|---|
| Produce objects the app understands (lexicon, IGT…) | `matchbox-corpus`, `matchbox-grammar-api` |
| Produce its own private objects | same; implement `CorpusObject` directly |
| Also ship a custom view | + `matchbox-grammar-fx` |

### 2.1 Why `CorpusObject` becomes an interface rather than an envelope

An earlier sketch used an envelope `Located<T>(span, kind, label, value, children)`. It was
dropped in favour of **turning `CorpusObject` into an interface**:

- an envelope plus nested domain objects describes the hierarchy twice (`entry.senses()` *and*
  `located.children()`), and the two can drift apart;
- a nested sense or example needs its own span to be selectable, so spans belong to the objects;
- keeping the name `CorpusObject` and its `getTextCoordinate()` accessor reduces churn.

Any class a grammar writes becomes a corpus object by implementing a four-method interface,
without being declared in `matchbox-corpus`.

---

## 3. The contracts

### 3.1 `CorpusObject` (matchbox-corpus)

```java
package fr.cnrs.lacito.matchbox.corpus;

/** Anything a grammar extracts from a text and anchors to a span of it. */
public interface CorpusObject {

    /** Span in the source file (used for highlighting and navigation). */
    TextCoordinate getTextCoordinate();

    /** Namespaced, stable type id: "<grammarId>:<name>", e.g. "notebook-grammar:igt". */
    String getKind();

    /** Short human-readable text for lists, trees and status bars. */
    default String getLabel() { return getKind(); }

    /** Nested anchored objects (senses of an entry, examples of a sense…). */
    default List<? extends CorpusObject> getChildren() { return List.of(); }
}
```

Rules, to be written in the javadoc:
- `getKind()` is `"<grammarId>:<name>"` where `<grammarId>` is the producing plugin's `getId()`.
  It drives CSS classes, highlight colours and panel grouping. `Corpus` logs a warning when the
  prefix does not match the plugin that produced the object (see §7.6).
- A child's span lies inside its parent's span (checked by a test helper, see phase 0).
- Objects are immutable after `getResult` returns; collections are unmodifiable copies.
- Objects must not keep references to ANTLR parse-tree nodes or token streams (see §7.9).

`TextCoordinate` moves to `fr.cnrs.lacito.matchbox.corpus` and keeps its fields. Optional later
improvement: an exclusive end index, to remove the `+ 1` scattered in `EditorController`.

### 3.2 Capability interfaces (`fr.cnrs.lacito.matchbox.corpus.capability`)

Only concepts the app itself uses. Initial set:

```java
public interface LexicalEntry extends CorpusObject {
    String getHeadword();
    List<? extends Sense> getSenses();
    @Override default List<? extends CorpusObject> getChildren() { return getSenses(); }
}

public interface Sense extends CorpusObject {
    String getDefinition();
    List<? extends Example> getExamples();
    @Override default List<? extends CorpusObject> getChildren() { return getExamples(); }
}

public interface Example extends CorpusObject {
    String getTranscription();
    String getTranslation();
}

public interface InterlinearSentence extends CorpusObject {
    String getTranscription();
    String getMorphemes();        // later: List<Word> with morpheme/gloss alignment
    String getFreeTranslation();
}

public interface DependencySentence extends CorpusObject {   // phase 6, for CoNLL
    String getSentenceId();
    Map<String, String> getMetadata();
    List<Map<String, String>> getTokens();
    List<Integer> getHeads();     // not int[]: arrays are mutable and break equals (§7.2)
}
```

**Admission rule** (to put in CLAUDE.md): an interface is added here only when an application
feature (lexicon mode, an exporter, cross-linking, search) needs it. A new grammar-specific type
does not justify a new interface.

### 3.3 Reference implementations (`fr.cnrs.lacito.matchbox.corpus.simple`)

`SimpleIGT`, `SimpleLexicalDefinition`, `SimpleSense`, `SimpleExample`, `SimpleDependencyTree`
become **immutable** classes implementing the capability interfaces. Grammars may reuse them
(notebook will) or write their own.

- Senses and examples are built before the entry, which is already the order
  `NotebookGrammarPlugin` follows. `addSense` / `addExample` are removed.
- Lists are copied with `List.copyOf` in the constructor.
- Implementation form: Java `record`s with explicit `getX()` methods, or plain final classes. See
  §7.1 for why a bare record does not fit the interfaces.

### 3.4 `GrammarPlugin` / `ParseResult` (matchbox-grammar-api)

- `ParseResult` becomes `record ParseResult(List<CorpusObject> objects, List<ParseError> errors)`
  with a compact constructor applying `List.copyOf`. The old `getCorpusObjects()` / `getErrors()`
  are **removed, not deprecated**: the package rename already breaks binary compatibility with
  every plugin, and all plugins live in this repository, so a transition period adds nothing (§7.5).
- Add to `GrammarPlugin`: `default int getApiVersion() { return 2; }`, plus a constant
  `GrammarPlugin.API_VERSION = 2`. `PluginManager` skips and logs plugins with another version.
- Javadoc on `getResult`: must be re-entrant and thread-safe (it will run off the JavaFX thread,
  §7.3), return new lists, never return `null`, and report failures as `ParseError`s instead of
  printing them.

### 3.5 View SPI (new module `matchbox-grammar-fx`)

```java
package fr.cnrs.lacito.matchbox.grammarfx;

public interface ObjectViewFactory<T extends CorpusObject> {

    /** Class or interface this factory renders. Resolution walks the object's type hierarchy. */
    Class<T> getType();

    /** Detailed view of one object (shown in the side panel). Called on the JavaFX thread. */
    Node createView(T object, ViewContext context);

    /** Columns when objects of this type are shown as a table. Empty = list of labels. */
    default List<ObjectColumn<T>> getColumns() { return List.of(); }

    /** Higher wins when two factories handle the same type. Built-ins use 0. */
    default int getPriority() { return 0; }
}

public record ObjectColumn<T>(String title, Function<T, String> value) {}

/** Services the GUI offers to views. */
public interface ViewContext {
    void selectInEditor(TextCoordinate span);
    void openFile(Path path);
    ApplicationStyle style();   // colours/fonts from ApplicationSettings, read-only
}
```

- `ObjectColumn` is deliberately not a `TableColumn`: the GUI builds the `TableColumn`, so plugins
  do not deal with cell value factories, and the same descriptor could later feed a TSV export.
- Plugins register factories in `META-INF/services/fr.cnrs.lacito.matchbox.grammarfx.ObjectViewFactory`.
- Module dependencies: `matchbox-corpus`, `javafx-controls`. It does **not** depend on `matchbox-app`
  or `matchbox-gui`.
- A plugin view that loads FXML must call `loader.setClassLoader(getClass().getClassLoader())`
  (§7.8).

---

## 4. Application and GUI changes

### 4.1 `Corpus` (matchbox-app)

Replace the per-type methods:

```java
List<CorpusObject> objectsFor(Path file);                          // top-level objects
<T> List<T> objectsOf(Class<T> type, Path file);                   // includes nested objects (depth-first)
<T> List<T> objectsOf(Class<T> type);                              // whole project (lexicon mode)
Optional<CorpusObject> objectAt(Path file, int offset);            // deepest object containing offset
Stream<CorpusObject> flatten(Path file);                           // self + descendants
```

- Results are stored per `(path, grammarId)` (`Map<Path, Map<String, ParseResult>>`) instead of one
  flat list. Re-parsing one file then replaces exactly what that file produced. The public
  `ObservableList<CorpusObject>` remains as a derived view for existing listeners.
  `Project.parseTextObject` passes `plugin.getId()`.
- **Validation on insert** (§7.4): an object whose span is outside the text, inverted, or not
  inside its parent's span is dropped and turned into a `ParseError` naming the grammar. Same for
  a `RuntimeException` thrown by `getResult`.
- All mutations of observable state happen on the JavaFX thread (§7.3).

### 4.2 `PluginManager` (matchbox-app)

- Keep the list of plugin `URLClassLoader`s.
- Add `public static <S> List<S> loadServices(Class<S> service)`: loads from the app classloader
  first (built-ins), then from each plugin classloader, **removing duplicates by class name**. This
  is required, not cosmetic: `ServiceLoader.load(S, pluginLoader)` also returns the parent's
  providers, so without it each built-in factory would be registered once per plugin jar.
- Catch `LinkageError` per jar (old plugins compiled against `fr.cnrs.lacito.matchbox.language.*`
  throw `NoClassDefFoundError`) and log the jar name with a "rebuild against API v2" message.
- `matchbox-app` stays independent of JavaFX controls: it never references `ObjectViewFactory`;
  the GUI calls `loadServices(ObjectViewFactory.class)`.

Classloading note: plugin classloaders have the app classloader as parent, so JavaFX and
`matchbox-grammar-fx` classes resolve to the same `Class` objects as in the GUI. The distribution
must put `matchbox-grammar-fx` on the application classpath/module path (and never in plugin jars,
hence `provided`).

### 4.3 `ObjectViewRegistry` (matchbox-gui, new, package `gui.view`)

```java
Node createView(CorpusObject o, ViewContext ctx);
List<TableColumn<CorpusObject, String>> columnsFor(String kind, Class<?> type);
```

Resolution for an object of class `C`:
1. factories whose `getType()` is `C` itself;
2. otherwise the closest supertype/interface of `C` (breadth-first over superclasses and
   interfaces, so `InterlinearSentence` matches any grammar's IGT class);
3. ties broken by `getPriority()`, then plugin before built-in;
4. otherwise `GenericObjectViewFactory`.

Results are cached per class. Calls to plugin code (`createView`, column functions) are wrapped:
an exception falls back to the generic view (or an empty cell) and is logged once per factory
(§7.4).

Built-in factories (in matchbox-gui, registered through the same `META-INF/services` file):

| Factory | Type | Implementation |
|---|---|---|
| `LexicalEntryViewFactory` | `LexicalEntry` | Wraps the existing `LexicalEntryController` + FXML, retyped from `SimpleLexicalDefinition` to `LexicalEntry` (same for `SenseController`, `ExampleController`) |
| `InterlinearSentenceViewFactory` | `InterlinearSentence` | Three aligned labels for the detail view; columns transcription / gloss / translation, i.e. what `igtTableView` shows today |
| `GenericObjectViewFactory` | `CorpusObject` | `TreeView` of `getLabel()` over `getChildren()`, with kind, file and lines; double click → `selectInEditor` |

### 4.4 `EditorController`

- `highlightText()`: a single loop over `corpus.flatten(selectedFilePath)`, colour chosen by
  `HighlightStyles.forObject(o)`:
  - capability → existing settings (`getIgtParagraphColor`, `getLexicalDescriptionColor`);
  - otherwise a colour from a fixed palette of light background colours, derived from a hash of
    `getKind()` (per-kind overrides in `ApplicationSettings` later);
  - grammars drawn in `FileType` rank order, nested objects after their parents so they stay visible.
- `handleCodeAreaClick`: `corpus.objectAt(path, caret)` and publish it on a new
  `ObjectProperty<CorpusObject> selectedObjectProperty()`. This resolves the existing TODO.
- `selectLinesInEditor(SimpleIGT)` → `selectInEditor(TextCoordinate)`; `EditorController`
  implements the editor part of `ViewContext`.
- `currentFileIgts` / `currentFileLexicalDefinition` → one `ObservableList<CorpusObject> currentFileObjects`.

### 4.5 `FileModeController` and `FileMode.fxml`

Replace the hard-coded IGT tab and the lexical `VBox` with:

- **"Objects" tab**: one sub-tab per `getKind()` present in the file. Each is a
  `TableView<CorpusObject>` whose columns come from `registry.columnsFor(kind, type)`, or a single
  label column when there are none.
- **Detail pane** under it (in a `SplitPane`): `registry.createView(selected, ctx)` for the
  selection in the table *or* in the editor (`selectedObjectProperty`).
- **Selection survives re-parsing** (§7.7): after a re-parse, re-select the object of the same
  kind whose span contains the caret (or the previous start offset, shifted by the edit).
- The error tab is unchanged.

The working tree currently has uncommitted changes to `MainView.fxml` and `FileMode.fxml`: commit
or stash them before phase 5 so the FXML edits do not get mixed.

---

## 5. Implementation phases

Each phase builds and runs (`mvn clean package`, `mvn -pl matchbox-gui javafx:run`) and can be its own commit.

### Phase 0: safety net and plugin fixes
1. Add JUnit 5 to the root `pom.xml` (`dependencyManagement` + surefire).
2. `grammars/matchbox-grammar-notebook/src/test`: parse a sample file from `src/test/resources`,
   assert number/content/spans of IGTs, entries, senses and examples.
3. Same for CoNLL (number of sentences, `SENT_ID`, spans).
4. Fix plugin re-entrancy: local `objects`/`errors` lists in `getResult` (notebook, conll).
5. Test helper `CorpusAssertions.assertSpansNested(List<CorpusObject>, String text)`, placed in a
   `test-jar` of `matchbox-corpus` so grammar modules can reuse it.

These tests are written against the current API and must pass again after phase 1, with only
import and accessor changes. This shows the move did not change parsing results.

### Phase 1a: package rename (separate commit, no code changes)
1. `git mv matchbox-corpus/src/main/java/fr/cnrs/lacito/matchbox/language/corpusObject/*.java
   matchbox-corpus/src/main/java/fr/cnrs/lacito/matchbox/corpus/`; delete the empty `lexicon/` directory
   and the `language/` directory.
2. Update `package` and `import` lines in every user (listed in §8).
3. `mvn clean package` + phase 0 tests. Commit as "rename package … → fr.cnrs.lacito.matchbox.corpus".

Keeping the rename in its own commit with no content changes lets git track the moves (`git log
--follow`, blame), and makes the following commit's diff show only real changes.

### Phase 1b: `CorpusObject` becomes an interface
1. Turn `CorpusObject` into the interface from §3.1.
2. Add the `capability` interfaces (§3.2, except `DependencySentence`).
3. Turn the `Simple*` classes into immutable implementations; move them to `corpus.simple`.
4. Update `NotebookGrammarPlugin` (build senses/examples before entries; kinds
   `notebook-grammar:igt`, `…:lexical-entry`, `…:sense`, `…:example`) and `ConllGrammarPlugin`
   (`conll-grammar:sentence`, still nearly empty, see phase 6). These prefixes are the current
   `getId()` values.
5. `ParseResult` → record; old accessors removed; `getApiVersion()`.
6. GUI and app compile with minimal changes (`Simple*` imports, accessor names).
7. Phase 0 tests pass.

### Phase 2: generic `Corpus`
1. Methods of §4.1, per-`(path, grammarId)` storage, validation on insert, `Project.parseTextObject`
   passes the grammar id and catches plugin exceptions.
2. Unit tests for `objectAt` (nested objects, offsets at boundaries), re-parsing one file, and
   rejection of invalid spans.
3. Remove `getIgtForFile` / `getLexicalDefinitionsForFile` once callers have moved (phase 4).

### Phase 3: view SPI and plugin services
1. Create `matchbox-grammar-fx` (add to root `<modules>` and `dependencyManagement`).
2. `PluginManager`: keep classloaders, `loadServices` with duplicate removal, version check,
   `LinkageError` handling.
3. `ObjectViewRegistry` + the three built-in factories; retype the lexicon controllers to the
   capability interfaces; wrap plugin calls.
4. Unit test for the registry's resolution order (no JavaFX toolkit needed if `createView` is not called).
5. Add `matchbox-grammar-fx` to the distribution (`matchbox-distribution` assembly and jpackage input).

### Phase 4: editor
`EditorController` changes of §4.4: generic highlighting, `objectAt` on click,
`selectedObjectProperty`, `ViewContext`.

### Phase 5: panels and background parsing
1. `FileModeController` / `FileMode.fxml` changes of §4.5, including selection kept across re-parses.
2. Move parsing off the JavaFX thread (§7.3): a single-threaded executor in `Project` (or a JavaFX
   `Service` in the GUI). Discard a result if the text changed since that parse started (compare
   a version counter in `TextDocument`), then apply results with `Platform.runLater`.
3. Manual check: notebook file (IGT and lexicon tabs, detail view, click in the editor selects the
   row and vice versa, typing does not lose the selection), CoNLL file (shown through the generic view).

### Phase 6: CoNLL as the demonstration plugin
1. `ConllListener` extracts tokens, metadata and heads; `SimpleDependencyTree` stores them and
   implements `DependencySentence` (add the interface to `capability`).
2. In the conll module: `ConllSentenceViewFactory` (table of tokens: ID, FORM, LEMMA, UPOS, HEAD,
   DEPREL; optionally an arc diagram later) + `META-INF/services` entry; dependency on
   `matchbox-grammar-fx` as `provided`.
3. Check that a CoNLL plugin jar dropped in `plugins/` displays with its own view, and that the app
   still works without the jar.

### Phase 7: documentation
- `CLAUDE.md`:
  - module table: add `matchbox-corpus` and `matchbox-grammar-fx`, remove the nonexistent
    `matchbox-language`;
  - new package names;
  - the contract and the admission rule for capability interfaces;
  - threading and immutability rules for grammars.
- `.claude/skills/write-grammar`:
  - how a grammar declares object kinds;
  - when to reuse `Simple*` classes;
  - how to ship a view (FXML classloader, `provided` scope);
  - a check that the plugin jar contains no `javafx/**` and no `fr/cnrs/lacito/matchbox/{corpus,grammarapi,grammarfx}/**`.
- `README.md`: note that plugin jars built before API v2 must be rebuilt.

### Later (outside this plan, same pattern)
- **Exporter SPI** (`Exporter<T>` keyed by capability: LIFT for `LexicalEntry`, FLEx/ELAN for
  `InterlinearSentence`), in `matchbox-app` because it is toolkit-free.
- **Project-wide lexicon mode** using `corpus.objectsOf(LexicalEntry.class)`, merging entries from
  all grammars.
- **Stable object identity** across re-parses, beyond the caret-based re-selection of phase 5.

---

## 6. Decisions

| Topic | Decision |
|---|---|
| Immutable `Simple*` instead of mutable classes | **Adopted.** Grammars collect children first; a grammar that really needs incremental building keeps its own mutable classes and freezes them before returning. Form of implementation: see §7.1 |
| `getChildren()` vs typed getters | **Adopted.** For capability interfaces `getChildren()` is a default method derived from the typed getter, so there is one source of truth |
| JavaFX in plugins | **Adopted.** `provided` scope for `matchbox-grammar-fx`; the `write-grammar` skill checks that the jar contains no `javafx/**` |
| Old plugin jars in `plugins/` | **Adopted.** `getApiVersion()` check, plus `LinkageError` caught and logged per jar |
| Several grammars on one file | **Adopted.** Highlights drawn in `FileType` grammar rank order, nested objects last |
| Colours for unknown kinds | **Adopted.** Fixed palette of light background colours chosen by kind hash; per-kind overrides in `ApplicationSettings` later |
| Package name | **Rename now** (not postponed): `fr.cnrs.lacito.matchbox.language.corpusObject` → `fr.cnrs.lacito.matchbox.corpus` (+ `.capability`, `.simple`), in a dedicated commit (phase 1a) |

Why renaming now costs little: phase 1b already breaks binary compatibility for every plugin
(`CorpusObject` changes from class to interface), so doing the rename in the same release adds no
extra breakage for plugin authors, and all known plugins are in this repository.

---

## 7. Further issues and drawbacks found in review

### 7.1 Records do not fit getter-style interfaces
A record `SimpleSense(TextCoordinate textCoordinate, String definition, …)` generates `definition()`,
not `getDefinition()`. To implement `Sense`, each record needs an explicit `getDefinition()` that
returns `definition`, so each value has two public accessors. Switching the interfaces to
record-style names (`definition()`, `span()`) would go against the rest of the codebase (JavaBean
getters everywhere, `getTextCoordinate()` used in the GUI).
**Resolution:** keep `getX()` interfaces. Implement the `Simple*` types as final classes with
`final` fields, or as records with explicit getters, and choose one form for all of them in
phase 1b. Recommendation: final classes. There are only five, value equality is not needed (§7.2),
and there is no duplicate API.

### 7.2 Equality and arrays
Record value equality would make two textually identical objects at different spans unequal only
through their span, and an `int[]` component compares by identity. JavaFX `TableView` selection
and `ListChangeListener` diffs depend on `equals`.
**Resolution:** identity equality (default for final classes); no arrays in the contracts
(`getHeads()` returns `List<Integer>`); unmodifiable collections.

### 7.3 Parsing on the JavaFX thread
Today parsing runs synchronously on the FX thread. With third-party grammars and larger files this
freezes the editor, but moving it to a background thread affects the contracts:
`ObservableList` must only be changed on the FX thread, and `getResult` must be thread-safe (the
old instance-field lists would have been a data race).
**Resolution:** the thread-safety requirement goes into the `getResult` javadoc now (phase 1b); the
re-entrancy fix is in phase 0; background parsing is done in phase 5 with stale-result discarding.

### 7.4 Faulty plugins can break the GUI
- A span outside the text makes `InlineCssTextArea.setStyle` throw `IndexOutOfBoundsException`,
  which breaks highlighting for the whole file.
- An exception in `createView` or in a column function would propagate into JavaFX layout code.
- Exceptions in `getResult` are swallowed today.

**Resolution:** span validation on insert (§4.1), wrapped plugin calls in the registry (§4.3),
exceptions turned into `ParseError`s shown in the existing error tab.

### 7.5 No transition period needed
Deprecated `ParseResult` aliases (proposed earlier) are pointless: old plugin jars fail at class
loading because of the rename anyway, and in-repo plugins are migrated in the same commit.
**Resolution:** removed from the plan.

### 7.6 Kind ids depend on plugin ids
`getId()` returns values like `"notebook-grammar"`, so kinds become `notebook-grammar:igt`. If a
plugin id changes, kind ids change too, and so do future per-kind colour settings.
**Resolution:** document that `getId()` is a stable identifier (it is already stored in `.mbx`
project files, so it cannot change freely anyway); `Corpus` warns on a prefix mismatch.

### 7.7 Re-parsing every 500 ms resets the views
Every re-parse replaces all objects. With a detail view tied to the selected object, typing would
clear the selection and rebuild the panel twice a second.
**Resolution:** caret/offset-based re-selection by kind in phase 5 (moved forward from "later");
the detail pane is rebuilt only when the re-selected object's content differs (compare labels and
spans) to avoid flicker.

### 7.8 FXML in plugin views
`FXMLLoader` resolves controller classes and `<?import?>`s with the thread context classloader by
default, which is the app classloader, not the plugin's. A plugin view using FXML would then fail
with `ClassNotFoundException`.
**Resolution:** documented in the SPI javadoc and the `write-grammar` skill:
`loader.setClassLoader(getClass().getClassLoader())`. Plugin CSS via
`getClass().getResource(...).toExternalForm()` works as is.

### 7.9 Memory: parse trees kept alive
If a grammar's object kept a reference to its ANTLR `ParserRuleContext` (tempting for lazy
getters), every parse tree of the project would stay in memory, and a new tree is produced every
500 ms while typing.
**Resolution:** rule in the `CorpusObject` javadoc: copy text out; keep no ANTLR references.

### 7.10 Cost of the change for a small codebase
The plan adds one module, two SPIs and a registry. That is more indirection for a codebase with
three grammars and no external plugin authors yet. The phases are ordered so that the most useful
part comes first: phases 0–2 (interface, capabilities, generic `Corpus`) are worthwhile even
without plugin views. Phases 3–6 can be delayed if no grammar needs a custom view yet, because the
generic view already displays any object.

---

## 8. Files touched by the rename (phase 1a)

| Module | File |
|---|---|
| matchbox-corpus | the 7 classes of `fr/cnrs/lacito/matchbox/language/corpusObject/` (moved) |
| matchbox-grammar-api | `grammarapi/ParseResult.java` |
| matchbox-app | `model/Corpus.java` |
| matchbox-gui | `mode/FileModeController.java`, `mode/fileMode/EditorController.java`, `mode/common/lexicon/{LexicalEntry,Sense,Example}Controller.java` |
| grammars | `notebook/NotebookGrammarPlugin.java`, `conll/ConllGrammarPlugin.java` |

No FXML, resource or `META-INF/services` file references the old package (checked with grep).

---

## 9. Implementation notes

What differs from the text above, and why.

| Topic | Plan | Implementation |
|---|---|---|
| `Simple*` form (§3.3, §7.1) | records or final classes | Final immutable classes extending `simple.AbstractCorpusObject` (holds kind and span). The kind is the first constructor argument, so reused classes are namespaced by the grammar |
| `getApiVersion()` (§3.4) | `default` method returning 2 | **Abstract**, implemented in each plugin as `return API_VERSION;`. A default method lives in the application's copy of the interface and always returns the application's version, so the check could never fail. An old plugin without the method throws `AbstractMethodError`, which is caught |
| Re-parsing while typing (§7.7) | "every 500 ms" | Wrong premise: the editor only *pushes* its text to the model every 500 ms; parsing happens on load and on **Parse**. Selection retention is still implemented (after **Parse**): same kind and label, closest to the previous position; otherwise the object containing the previous start offset |
| Stale text on **Parse** / **Save** | not in plan | Fixed: because of the 500 ms throttle the model could miss the last keystrokes. Both now push the editor text first |
| Panels not refreshed after **Parse** | not in plan | Fixed: `Corpus.addFileListener` notifies the editor, which reloads objects, errors and highlighting |
| Errors accumulated on each parse | not in plan | Fixed by the per-(file, grammar) storage in `Corpus` (`removeCorpusObjectsForFile` never removed errors) |
| Highlighting | colour by type | The old code used `-fx-background-color`, which RichTextFX text ignores, so highlighting was never visible. It now uses `-rtfx-background-color`. The colour defaults in `ApplicationSettings` were `#000000` (black boxes once visible) and are now `#fff1c1` (IGT) and `#dfe7fd` (lexicon). Users with an existing `configuration.txt` keep the colours stored there |
| `ParseError` memory (§7.9) | not in plan | `CollectingErrorListener` no longer stores the recognizer, exception and offending token (nothing read them, and they kept the parser alive). New 5-argument constructor |
| Error recovery | not in plan | The notebook and CoNLL plugins skip the incomplete rule (`ctx.exception != null`, missing tokens) instead of losing every object of the file to an exception |
| Helpers | not in plan | `grammarapi.Spans.of(path, ctx)`; `TextCoordinate.contains / encloses / isValidFor`; `gui.view.Kinds.title(kind)` for tab titles |
| `ViewContext` (§3.5) | `selectInEditor`, `openFile`, `style()` | `selectInEditor(span)` (opens the file if needed), `select(object)`, `highlightColor(object)` |
| Error table headers | — | Were "Transcription / Morphemic glosses / Translation"; now "Line / Position / Message" |
| `selectLinesInEditor(ParseError)` | — | Fixed off-by-one-line offset computation (ANTLR lines are 1-based, newlines were not counted) |

### Verification

- Unit tests: 26 (notebook 6, CoNLL 3, `Corpus` 7, `Project` 4, registry 6).
- GUI: driven through the real `FileMode.fxml` + `FileModeController` with a scripted JavaFX driver:
  - per-kind tabs (IGT, lexical entry);
  - row selection selects the span in the editor and shows the detail view;
  - a click in the editor selects the row, nested objects included;
  - highlighting is visible and readable;
  - **Parse** refreshes the tables and keeps the selection;
  - a CoNLL file uses the plugin's own view, loaded from the jar in `build/plugins`.
- Distribution: `matchbox-grammar-fx` is in `build/assembly/…/lib`.

Not verified: loading a real pre-v2 plugin jar (the `LinkageError` path), the full main window (`MatchboxApplication`), and running without the CoNLL jar (only the registry fallback is unit-tested).
