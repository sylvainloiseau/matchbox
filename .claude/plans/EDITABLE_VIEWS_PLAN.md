# Plan: editable views (view → text)

Status: implemented (not committed). Depends on `TEXT_CORPUS_PLAN.md` (`TextId`, `Text`,
`CorpusView`, `Diagnostic`), implemented with it. Differences between the plan and the code in §10.

Decisions taken (§8): edits go through the text, corpus objects stay immutable; `TextDocument`
owns the text so that views can edit files not shown in the editor; edits adding parse errors are
refused by default; an empty field has the span `stopIndex = startIndex - 1`.

## 0. Goal

Let a view (detail pane, table, a lexicon merging several files) change the data it shows: the
user edits a cell or a field, validates (Enter, focus lost, a button), and the change appears in
the text of the right file, in the corpus objects, in the view and, if that file is displayed, in
the editor; the file becomes savable; none of these drift apart.

Design in two sentences: **the text stays the only source of truth and a view never changes a
corpus object: it proposes text edits, and the objects are re-derived by parsing. Each file's text
is owned by its `TextDocument`, which applies every change (typing, views, assistant, search,
undo) and notifies the editor and the project.**

```
   typing ──▶ Editor (CodeArea) ──edits──┐          ┌──changes──▶ Editor mirrors the displayed document
                                         ▼          │
   View ──── ViewContext ──edits──▶ TextDocument ───┼──changes──▶ TextHistory (undo/redo, per document)
                                   (owns the text)  │
   Assistant, search, undo ─edits──▶      │         └──changes──▶ Project: dirty flag, parse ──▶ Corpus (Text)
                                          └──save──▶ file                                          │
   View ◀──────────────────────── rebuilt / updated with the re-parsed object ◀────────────────────┘
```

Corpus objects stay immutable: no refactoring of `CorpusObject` or of the grammars' object classes.

---

## 1. What already exists

| Need | Existing piece |
|---|---|
| A text edit with a guard against a changed text | `assist.suggestion.TextEdit(start, end, expectedOriginal, replacement)`, `matches`, `applyAll` |
| Applying edits as one change | `EditorController.applyEdits(List<TextEdit>)` (AI suggestions), on the `CodeArea` |
| Marking the file dirty → Save enabled | `CodeArea.textProperty` listener → `Project.updateFileContent` → `TextDocument.updateText` → `couldBeWritten` → `EditorController.currentTextCouldBeWrittenDownProperty` → Save button (`MainController`, `FileModeController`) |
| Re-parsing and refreshing views | `Project.parseInBackground(path)` (any file) → `Corpus` listener → `EditorController.refreshFromCorpus` |
| Keeping the selection after a re-parse | `EditorController.findSuccessor` |
| Not rebuilding the detail view for an equal object | `FileModeController.showInDetailPane` / `sameContent` |
| Finding an edit again after the text moved | `assist.suggestion.Relocation.find` |
| Refusing edits that add parse errors | `assist.validation.EditValidator.check` |
| Provisional text kept out of the model | `EditorController.runGhostOperation` / `ghostOperation` flag |
| Precedent of the whole loop | `SuggestionPanelController` (~l. 295): relocate → `applyEdits` → `parseEditedFile` |

### What has to change in the text ownership

Today the `CodeArea` owns the text of the selected file and pushes it to its `TextDocument`
(at most every 500 ms); the undo history belongs to the `CodeArea` and is forgotten when another
file is selected (`forgetHistory()` in `selectEditedFile`). Changing the `TextDocument` of a file
directly would therefore be invisible in the editor, overwritten by the next push, and not
undoable. Hence §3: the document owns the text, the editor mirrors it.

Other places that change the `CodeArea` directly and must go through the document:
`SearchDialog.doReplace`/`doReplaceAll`, `EditorController.applyEdits`, `MainController` undo/redo
(`fileContentCodeArea.undo()/redo()`), `GhostTextController` (accepting a suggestion inserts real
text: it becomes ordinary typing).

---

## 2. Why not mutable corpus objects

Making the view change the object, then patch the text, keeps two representations to synchronise
by hand, which is exactly the drift to avoid:

1. **The grammar decides what the text means.** A translation containing a `'`, or a gloss
   containing a space, may be parsed differently from what the view assumed. Only re-parsing tells
   what the new text contains; a mutated object can claim a value the text does not hold.
2. **Offsets.** Changing one field changes the length of the text: every following span of the
   file (all objects, all grammars) becomes wrong until re-parse.
3. **Threads and sharing.** Objects are read by background threads (assistant, exports) and held
   by the immutable `Text` snapshots of `TEXT_CORPUS_PLAN.md`.
4. **Undo.** Undo restores the text, not a mutated object.

With edits-through-text, all four are free. The cost is one re-parse per validated change; field
notes parse in milliseconds, and the view can keep showing the typed value until the refreshed
object arrives.

---

## 3. `TextDocument` owns the text

### 3.1 API (matchbox-app, `fr.cnrs.lacito.matchbox.model`)

```java
public class TextDocument {

    public TextId getId();                       // TEXT_CORPUS_PLAN; replaces getFilePath() in lookups
    public Path getFilePath();
    public Charset getCharset();
    public String getCurrentText();              // volatile snapshot, readable from any thread
    public long getVersion();                    // +1 per applied change

    /**
     * Applies non-overlapping edits, all relative to the current text, as one change.
     * JavaFX thread only.
     *
     * @return false (nothing applied) if an edit no longer matches the text (TextEdit.matches)
     */
    public boolean applyEdits(List<TextEdit> edits, EditOrigin origin);

    /** Applies edits one after the other (each relative to the text left by the previous) as one change. */
    public boolean applySequential(List<TextEdit> edits, EditOrigin origin);

    /** Replaces the whole text, as one minimal edit (common prefix and suffix kept). */
    public void replaceAll(String newText, EditOrigin origin);

    public void addChangeListener(DocumentListener listener);
    public void removeChangeListener(DocumentListener listener);

    public ReadOnlyBooleanProperty couldBeWrittenProperty();   // dirty: text differs from the saved text
    public ReadOnlyBooleanProperty couldBeReparsedProperty();  // changed since the last applied parse
    public void markParsed(long version);                      // only if version is still current
    public void writeFile() throws IOException;
}

/** One applied change: edits relative to the text before it, sorted, non-overlapping. */
public record DocumentChange(TextDocument document, List<TextEdit> edits, EditOrigin origin,
                             long versionBefore, long versionAfter) {
    public DocumentChange inverse() { ... }      // used by undo
}

@FunctionalInterface
public interface DocumentListener { void changed(DocumentChange change); }

/**
 * Who made a change. {@code source} lets a listener recognise its own changes (the editor ignores
 * the changes it forwarded); {@code kind} drives undo merging and parsing.
 */
public record EditOrigin(Kind kind, Object source) {
    public enum Kind { TYPING, VIEW, ASSIST, SEARCH, UNDO, REDO }
}
```

- `updateText(String)` and `Project.updateFileContent(...)` are removed; `Project.saveFile(path,
  text)` becomes `saveFile(TextId)` (the document already holds the text).
- `applySequential` exists for the `CodeArea`'s multi-changes (e.g. drag-and-drop of a selection:
  a deletion then an insertion); it is stored as one `DocumentChange` whose `edits` are converted
  to the "relative to the text before" form.
- **Dirty state** is computed, not just set: `couldBeWritten = !content.equals(savedContent)`
  (length compared first), so undoing back to the saved text disables Save again. `writeFile()`
  sets `savedContent = content`.
- All mutations on the JavaFX thread (assert in `applyEdits`); parse threads only read
  `getCurrentText()`/`getVersion()`, as today.
- `couldBeWritten`/`couldBeReParsed` become read-only properties (currently public mutable
  fields); the getters used by the GUI keep their names.

### 3.2 Undo: `TextHistory` per document (matchbox-app)

```java
public final class TextHistory {
    public TextHistory(TextDocument document);   // listens to its changes
    public boolean undo();                       // applies the inverse of the last step, origin UNDO
    public boolean redo();                       // origin REDO
    public ReadOnlyBooleanProperty undoAvailableProperty();
    public ReadOnlyBooleanProperty redoAvailableProperty();
    public void preventMerge();
}
```

- One history per document, created with the document by `Project` and kept as long as the
  document is in the project: switching files no longer forgets it, and edits of files that are
  not displayed are undoable.
- Records every change except `UNDO`/`REDO` (a new change clears the redo stack).
- Merging: consecutive `TYPING` changes that are contiguous and less than 500 ms apart
  (RichTextFX's `DEFAULT_PREVENT_MERGE_DELAY`) form one step; `VIEW`, `ASSIST`, `SEARCH` changes
  are never merged: one validated view edit = one undo step.
- Plain Java + `javafx-base` properties (already a dependency of matchbox-app); no ReactFX.
- `Project.history(TextId)` gives access to it.

### 3.3 The editor mirrors the displayed document (matchbox-gui, `EditorController`)

- **Showing a document**: `textArea.replaceText(document.getCurrentText())` under a `mirroring`
  flag, subscribe to the document, unsubscribe from the previous one.
- **User changes → document**: `textArea.multiPlainChanges()` filtered by `!ghostOperation &&
  !mirroring`; each `PlainTextChange(position, removed, inserted)` becomes
  `new TextEdit(position, position + removed.length(), removed, inserted)`; the list goes to
  `document.applySequential(edits, new EditOrigin(TYPING, this))`. Synchronous, no throttle: the
  document is never behind the editor (removes the 500 ms push, `PARSE_DELAY_MS` and the "push
  first" code in `saveEditedFile`/`parseEditedFile`).
- **Document → editor**: for a change whose `origin.source() != this`: dismiss the ghost text
  (it is provisional and its offsets would be wrong), then apply the edits to the `CodeArea` in
  reverse order with one `createMultiChange` under `mirroring` (the code of today's `applyEdits`).
  The caret and the scroll position move with the text; check RichTextFX's behaviour for an edit
  covering the caret.
- **Safety net**: after mirroring, if `textArea.getLength() != document.getCurrentText().length()`
  (or, in tests, the texts differ), log a warning and resynchronise the whole text.
- **Undo/redo**: the `CodeArea` gets a no-op undo manager (`UndoUtils.noOpUndoManager()`);
  Ctrl-Z / Ctrl-Shift-Z / Ctrl-Y are rebound in the `CodeArea` input map to
  `project.history(id).undo()/redo()`; `MainController`'s undo/redo menu items call the same and
  bind their `disable` to `undoAvailable`/`redoAvailable` of the displayed document.
- `EditorController.applyEdits(edits)` delegates to `document.applyEdits(edits, origin)`
  (`ASSIST` for suggestions); the `CodeArea` is updated by mirroring. `currentText()` returns the
  document text (no ghost text by construction).
- Ghost text: unchanged principle; it lives only in the `CodeArea`, never reaches the document.

### 3.4 Other writers

| Writer | Change |
|---|---|
| `SearchDialog.doReplace` | `document.applyEdits([replace selection], SEARCH)` |
| `SearchDialog.doReplaceAll` | one `applyEdits` with one `TextEdit` per occurrence (`SEARCH`): one undo step, minimal changes |
| `SuggestionPanelController` | unchanged call to `editor.applyEdits` (now through the document) |
| `GhostTextController` accept | real insertion in the `CodeArea` outside `runGhostOperation` → forwarded as `TYPING` (unchanged) |

### 3.5 Parsing after a change

`Project` listens to all its documents:

- every change: `couldBeReparsed = true` (as today);
- `VIEW`, `ASSIST`, `SEARCH`, `UNDO`, `REDO` changes: `parseInBackground(id)` immediately, for any
  document, displayed or not (the view must be refreshed; undo of a view edit must refresh the
  view);
- `TYPING`: unchanged behaviour (parse on demand); a debounced automatic parse can be added later
  with the same mechanism.

`parseInBackground` already discards results of an outdated version.

### 3.6 Saving and unsaved changes

Edits can now happen in files the user is not looking at:

- **File list**: show each document's dirty state with a pseudo-class bound in the cell (the
  current `updateItem` adds a new listener at each call and never removes it).
- **Save** (selected document): unchanged binding. **Save all**: new action, enabled by
  `Project.anyDocumentDirtyProperty()`.
- **Unsaved-changes dialog** (does not exist today): on closing a file, removing a file or a
  directory from the project, closing the project and quitting, list the dirty documents
  concerned: Save all / Discard / Cancel.
- Status bar or detail pane: after a view edit of a file that is not displayed, say which file was
  changed (e.g. "notes2.txt modified — not saved").

---

## 4. Where the editable parts are: fields with spans

A whole-object span is not enough to change one field: rewriting the whole object would drop
comments, spacing and anything the object does not model.

### 4.1 `Field` and `HasFields` (matchbox-corpus)

```java
/**
 * A named value of a corpus object and the span of its raw text (delimiters excluded, e.g. the
 * text between the quotes of a translation). An empty value has an empty span:
 * stopIndex = startIndex - 1, startIndex being the offset where the value would be inserted.
 */
public record Field(String name, String value, TextCoordinate span) {}

/** Objects exposing their values with their spans, which makes them editable field by field. */
public interface HasFields {
    List<Field> getFields();
    default Optional<Field> field(String name) { ... }
}
```

- Admission rule (CLAUDE.md §3): needed by an application feature (editing; also usable by search
  and by the generic view, which can list fields).
- **Empty span**: `stopIndex = startIndex - 1` (the convention already used by
  `ParseError`/`Diagnostic` for empty ranges). `TextCoordinate` gets `isEmpty()`; `isValidFor`
  accepts an empty span with `0 <= startIndex <= textLength`; `encloses` accepts an empty span
  whose `startIndex` lies in `[parent.startIndex, parent.stopIndex + 1]`. Empty spans are allowed
  only for fields: `Text.Builder` keeps rejecting empty spans of corpus objects.
- Field spans lie inside the object's span; `Text.Builder` validates them like child spans.
- Standard names on the capability interfaces, as constants:
  `InterlinearSentence.TRANSCRIPTION = "transcription"`, `MORPHEMES`, `FREE_TRANSLATION`;
  `LexicalEntry.HEADWORD`; `Sense.GLOSS`… so that built-in views and grammars agree.
  `Section.TITLE = "title"` and `Section.metadataField(key)` (`"metadata:" + key`) make the title
  and metadata values of sections (`TEXT_CORPUS_PLAN.md` §2.9) editable in `SectionViewFactory`.
- `simple.*`: `SimpleIGT`, `SimpleLexicalDefinition`, `SimpleSense`, `SimpleExample` gain optional
  field spans (new constructor; the old one gives no fields, i.e. read-only).

### 4.2 Writing a value: `ObjectEditor` (matchbox-grammar-api)

```java
/** Turns a change of value into text edits, in the notation of a grammar. No JavaFX. */
public interface ObjectEditor {

    /**
     * Edits that set a field of an object.
     *
     * @param content the text the object was parsed from ({@code Text.content()})
     */
    EditProposal setField(CorpusObject object, String fieldName, String value, String content);

    /** Replaces the raw text of the field's span; refuses a line break in a one-line span. */
    ObjectEditor VERBATIM = ...;
}

public sealed interface EditProposal {
    record Edits(List<TextEdit> edits) implements EditProposal {}
    record Refused(String reason) implements EditProposal {}   // shown to the user
}
```

- `GrammarPlugin` gains `default ObjectEditor getObjectEditor() { return ObjectEditor.VERBATIM; }`
  (part of the `API_VERSION = 3` change of `TEXT_CORPUS_PLAN.md`).
- `VERBATIM` on an empty field inserts at `startIndex` (`TextEdit.insert`).
- Grammars override it where the notation requires (e.g. the notebook grammar for `'` in
  translations). Inserting and deleting whole objects (add a sense, a row) can be added later to
  the same interface.

### 4.3 `TextEdit` moves to matchbox-corpus

Needed by matchbox-app (`TextDocument`), grammar-api, grammar-fx, assist and the GUI: move it next
to `TextCoordinate`, and add the conversion from an inclusive span (empty spans included):

```java
/** Replaces the text of a span (stopIndex inclusive; empty span = insertion) of {@code content}. */
public static TextEdit replace(String content, TextCoordinate span, String replacement)
/** The edit that undoes this one once applied. */
public TextEdit inverse()
```

---

## 5. The view → text path

### 5.1 `ViewContext` additions (matchbox-grammar-fx)

```java
/** Whether edits of this object can be applied now (see §5.2). */
boolean canEdit(CorpusObject object);

/** Sets a field through the grammar of the object's text ({@link ObjectEditor}). */
EditOutcome setField(CorpusObject object, String fieldName, String value);

/**
 * Applies edits computed against the content the object was parsed from, as one undoable change
 * of the object's document (displayed or not), then re-parses it. For views building their own edits.
 */
EditOutcome applyEdits(CorpusObject object, List<TextEdit> edits);

/** Undoes / redoes the last change of the object's document. */
boolean undo(CorpusObject object);
boolean redo(CorpusObject object);
```

```java
public sealed interface EditOutcome {
    record Applied() implements EditOutcome {}                    // the view will be refreshed by the parse
    record Stale(String message) implements EditOutcome {}        // the text changed, edit not found again
    record Refused(String reason) implements EditOutcome {}       // ObjectEditor refused the value
    record Invalid(List<Diagnostic> newDiagnostics) implements EditOutcome {} // would add parse errors
    record NotEditable(String reason) implements EditOutcome {}   // no document, file read-only, no field…
}
```

The view needs no reference to the editor, the `TextDocument`, the history or the Save button.
Synchronous: everything except the re-parse runs on the JavaFX thread and is fast.

### 5.2 Implementation: `ViewEditService` (matchbox-gui, `gui.edit`)

Moved out of the anonymous `ViewContext` of `FileModeController` into a class that can be tested;
steps 1–4 in a class without JavaFX types.

`applyEdits(object, edits)`:

1. **Resolve the text**: `corpus.textOf(object)`; empty (object of an older parse) → `Stale`.
2. **Resolve the document**: `project.document(text.id())`; absent → `NotEditable`. The editor is
   not involved: the document may or may not be displayed.
3. **Check freshness**: `current = document.getCurrentText()`.
   - `current.equals(text.content())` → edits apply as they are;
   - otherwise (typing not parsed yet, parse in flight) → `Relocation.find` each edit in
     `current`; any not found → `Stale`.
4. **Validate** (default, §5.4): `EditValidator.check(id, current, edits, grammars)`; new errors →
   `Invalid(newDiagnostics)`, nothing applied.
5. **Apply**: `document.applyEdits(edits, new EditOrigin(VIEW, this))` → one undo step in the
   document's `TextHistory`; `couldBeWritten = true`; the editor mirrors the change if it displays
   that document. `false` (text changed in between) → `Stale`.
6. **Re-parse**: triggered by `Project` for a `VIEW` change (§3.5) → `Applied`.

`setField(object, name, value)`: grammar of the object (`text.grammarIdOf(object)`) →
`getObjectEditor().setField(object, name, value, text.content())` → `Refused`, or
`applyEdits(object, edits)`. No `HasFields` or no such field → `NotEditable`.

`canEdit(object)`: text resolved, document present, file writable (`Files.isWritable`), the
object `HasFields`. Views use it to disable editing controls instead of failing on commit.

### 5.3 Invariants against drift

| Pair | Invariant | Mechanism |
|---|---|---|
| object ↔ text | Objects only come from parsing a `Text` content | Immutable objects; no other way to create them |
| edit ↔ text | An edit applies only to the text it was computed for | `expectedOriginal`, content comparison, `Relocation` (step 3), `applyEdits` atomic refusal |
| document ↔ editor | The editor shows the displayed document's text (plus ghost text) | Single writer (`TextDocument`), synchronous forwarding and mirroring, length check + resync |
| document ↔ undo | Every change is undoable, displayed or not | `TextHistory` per document, fed by the document's changes |
| document ↔ file | Save enabled iff the text differs from the saved text | Computed `couldBeWritten`; unsaved-changes dialog |
| document ↔ corpus | Non-typing changes are re-parsed at once; outdated results discarded | §3.5, version check in `parseInBackground` |
| view ↔ object | A view shows the current object or is rebuilt | Re-parse → refresh → `findSuccessor` → `updateView`/rebuild (§5.5) |

### 5.4 Validation policy (decided)

Edits that add parse errors are **refused by default**: a user editing a table may not see the
text, and an edit that breaks the parse (e.g. an unbalanced quote) could make the object disappear
from the table being edited. Nothing is applied and the view shows the new diagnostics. It reuses
`EditValidator` (parses a window of the edited text with the file's grammars, so other grammars'
objects are protected too). A user setting (`UserSettings`, "Allow view edits that introduce parse
errors", default off) applies such edits anyway.

### 5.5 After the parse: keeping the view and the selection

- `findSuccessor` prefers an object with the same kind and label; after an edit the label usually
  changed. `ViewEditService` records `(TextId, offset)` of the last applied edit and
  `findSuccessor` prefers the object of the same kind containing that offset. `findSuccessor` must
  also work for the selected object of a document that is not displayed (it uses the corpus, not
  the editor).
- The detail pane rebuilds the view when the label changed (`sameContent`), losing focus, scroll
  and the cell being edited. Add to `ObjectViewFactory`:

  ```java
  /** Shows a re-parsed version of the object in an existing view. False: the view is rebuilt. */
  default boolean updateView(Node view, T object, ViewContext context) { return false; }
  ```
  called before `createView` when the old and new objects have the same kind and factory.
- Tables: replace items in place and keep the edited row selected so that Enter → next row keeps
  working. Tables showing objects of several texts refresh on any `CorpusListener` event, not only
  for the displayed file.

### 5.6 Editable table columns

```java
public record ObjectColumn<T>(String title, Function<? super T, String> value, Optional<String> field) {
    public ObjectColumn(String title, Function<? super T, String> value) { this(title, value, Optional.empty()); }
    public static <T> ObjectColumn<T> editable(String title, String field, Function<? super T, String> value);
}
```

- The application builds a `TextFieldTableCell` for an editable column; commit (Enter; focus lost
  with an explicit commit, since `TableView` cancels on focus loss by default) →
  `viewContext.setField(row, field, newValue)`; Escape cancels.
- Outcome other than `Applied`: keep the cell in edit mode with the typed value and show the
  reason (tooltip and status bar); `Invalid` lists the diagnostics.
- Ctrl-Z / Ctrl-Y in a table that is not editing a cell → `viewContext.undo/redo(selected row)`.
- Built-in columns: `InterlinearSentenceViewFactory` (transcription, morphemic glosses,
  translation), `LexicalEntryViewFactory` (headword) are editable when the row `HasFields`.
- Detail views: same pattern with `TextField`s committing on Enter (e.g. `LexicalEntryView`).

---

## 6. Other recommendations

- **No direct link from views to `couldBeWritten`**: Save follows the document's dirty state;
  since view edits change the document, Save becomes available with no new binding. A direct
  binding would allow a file to be marked dirty without a change of its text, or the reverse.
- **Assistant interplay**: AI suggestions and view edits use the same path; pending suggestions
  are relocated or marked outdated after a view edit as after typing (they listen to the
  document's changes instead of `EditorController.userChanges()`).
- **External modification of files** (another program changes a file on disk) remains out of
  scope; with documents owning the text it can later be handled in one place (`TextDocument`).

---

## 7. Phases

Prerequisite: `TEXT_CORPUS_PLAN.md` implemented (with the amendments of §9).

### Phase 1 — corpus model (matchbox-corpus)
1. Move `TextEdit` from matchbox-assist; add `replace(String, TextCoordinate, String)`, `inverse()`.
2. `TextCoordinate.isEmpty()`; empty spans in `isValidFor`/`encloses` (§4.1).
3. `Field`, `HasFields`; field-name constants on capability interfaces.
4. `simple.*` with optional field spans.
5. `Text.Builder` validates field spans; still rejects empty object spans.
6. Tests: span conversion (inclusive/exclusive, empty), `inverse`, validation of field spans.

### Phase 2 — `TextDocument` owns the text (matchbox-app)
1. `TextDocument.applyEdits`, `applySequential`, `replaceAll`, `DocumentChange`,
   `DocumentListener`, `EditOrigin`; computed `couldBeWritten`; read-only properties;
   `markParsed(version)`; remove `updateText`.
2. `TextHistory` (merging rules of §3.2).
3. `Project`: one `TextHistory` per document (`history(TextId)`); `document(TextId)`;
   listener triggering parses (§3.5); `anyDocumentDirtyProperty()`; `saveFile(TextId)`; remove
   `updateFileContent`.
4. Tests: atomic refusal of stale edits, sequential edits, versions, dirty back to false after
   undoing to the saved text, history merge/undo/redo, parse triggered by origin.

### Phase 3 — editor as a mirror (matchbox-gui)
1. `EditorController`: forwarding of user changes, mirroring of other changes, `mirroring` flag,
   resync safety net; remove the 500 ms push and the "push first" code.
2. Undo/redo: no-op `CodeArea` undo manager, input-map bindings and `MainController` menu items
   on `TextHistory`.
3. `EditorController.applyEdits`, `SearchDialog` through the document.
4. File list dirty pseudo-class; Save all; unsaved-changes dialog (close file, remove
   file/directory, close project, quit).
5. Assistant: suggestions listen to document changes.
6. Tests: `PlainTextChange` → `TextEdit` conversion (pure function); manual check list: typing,
   paste, drag-and-drop, undo across file switches, ghost text accept/dismiss, search/replace all.

This phase is useful on its own (undo kept per file, no lag between editor and document) and can
be merged before the following ones.

### Phase 4 — grammar API and grammars
1. `ObjectEditor`, `EditProposal`, `ObjectEditor.VERBATIM`; `GrammarPlugin.getObjectEditor()`.
2. Notebook grammar: field spans for IGT lines and lexical entries (`Spans.of` on the sub-rules of
   transcription, glosses, translation, headword, gloss), and an `ObjectEditor` handling its
   quoting; tests: set a field (also an empty one), re-parse, check the new value and that other
   objects are unchanged.
3. CoNLL grammar: fields per token column if its view becomes editable (optional).

### Phase 5 — view contract (matchbox-grammar-fx)
1. `ViewContext.canEdit`, `setField`, `applyEdits`, `undo`, `redo`; `EditOutcome`.
2. `ObjectColumn.field` + `editable(...)`.
3. `ObjectViewFactory.updateView` (default false).

### Phase 6 — editable views (matchbox-gui)
1. `gui.edit.ViewEditService` (+ pure planner class) implementing §5.2; `FileModeController`'s
   `ViewContext` delegates to it.
2. `findSuccessor` with the last edit offset, for any document.
3. Detail pane: `updateView` before `createView`.
4. Tables: editable cells, error display, undo/redo keys, selection kept across re-parse,
   refresh for any text.
5. Built-in views: editable columns and fields for `InterlinearSentence`, `LexicalEntry`.
6. `UserSettings`: "allow edits introducing parse errors" (default false).
7. Tests: planner (fresh / relocated / stale / invalid / refused / not editable), edit of a
   document that is not displayed (document changed, editor unchanged, history has one step),
   `ObjectViewRegistry` with `updateView`.

### Phase 7 — documentation
- `CLAUDE.md`: §3 matchbox-corpus (`Field`, `HasFields`, `TextEdit`, empty spans), matchbox-app
  (`TextDocument` owns the text, `TextHistory`), matchbox-grammar-api (`ObjectEditor`),
  matchbox-grammar-fx (editing methods, editable columns, `updateView`); §6 conventions: "every
  change of a text goes through its `TextDocument`; views never mutate objects, they propose
  `TextEdit`s through `ViewContext`".
- `HowAViewIsChosen.md`: editable views.
- `write-grammar` skill: how to expose field spans and an `ObjectEditor`.

---

## 8. Decisions

| Question | Decision |
|---|---|
| Mutable objects vs edits through the text | Edits through the text (§2) |
| Owner of the text | `TextDocument`; the editor mirrors the displayed document (§3) |
| Files not displayed in the editor | Editable (the reason for §3) |
| Undo | One `TextHistory` per document, kept while the document is in the project |
| Validation default | Refuse edits that add parse errors; user setting to allow them |
| Empty field span | `stopIndex = startIndex - 1`, accepted only for fields |
| Who escapes values | The grammar (`ObjectEditor`), `VERBATIM` by default |

## 9. Amendments to `TEXT_CORPUS_PLAN.md`

- Phase 1: `TextEdit` moves to matchbox-corpus; `TextCoordinate` gets `isEmpty()` (empty spans
  for fields, §4.1).
- Phase 2: `GrammarPlugin.getObjectEditor()` is part of the `API_VERSION = 3` change.
- Phase 4: `TextDocument` gets `getId()`; `Project` keys documents by `TextId`
  (`document(TextId)`), which this plan's `TextDocument` work builds on.
- Phase 6: `ViewContext` gets, besides `corpus()`, the editing methods (can be added later without
  a second break, since `ViewContext` is implemented only by the application).
- `Text.content()` must be non-null: edits are computed against it.

## 10. Implementation notes (differences from the plan)

- The `TEXT_CORPUS_PLAN.md` migration and this plan were implemented together, so `Project`,
  `TextDocument` and `EditorController` were rewritten once.
- `TextDocument` keeps its getter names (`getCouldBeWritten()`, `getCouldBeWrittenBooleanProperty()`…),
  now returning read-only properties; `markParsed(long version)` replaces `markParsed()`.
- Undo/redo key bindings of the code area are installed with WellBehavedFX
  (`Nodes.addInputMap`) over a no-op RichTextFX undo manager; `MainController`'s undo/redo menu
  items call `EditorController.undo()/redo()` and are disabled from the document history (the old
  binding was inverted).
- `EditorController.userChanges()` now reports every change of the shown text (typing and
  mirrored changes), so AI suggestions are relocated after view edits too.
- `ViewEditPlanner` (pure) and `ViewEditService` are in `gui.edit`; `FieldCell` (editable table
  cell) in `gui.view`. A refused edit leaves the cell in edit mode with a red border, a tooltip and
  the message in the assistant message label (used as status line).
- Detail-view editing is implemented in `InterlinearSentenceViewFactory` (with `updateView`);
  `LexicalEntryView.fxml` is not editable (its headword column is). `SectionViewFactory` has an
  editable Title column, effective only for sections giving a `Section.TITLE` field.
- New setting `allowInvalidViewEdits` (`SettingsData`, `UserSettings`, check box in the settings
  dialog), default false.
- New "Save all files" menu item; unsaved-changes dialog (`gui.UnsavedChangesDialog`) on closing a
  file, closing or opening a project, quitting (menu and window close). `handleCloseCurrentProject`
  now also gives the new project to the file mode (it kept showing the old one).
- matchbox-gui has a test-scope dependency on `matchbox-grammar-notebook` (real grammar for
  `ViewEditPlannerTest`).
- Not done: "Tables showing objects of several texts" (§5.5) — no such table exists yet;
  external modification of files (§6) remains out of scope.

