# Plan: `matchbox-grammar-markdownheading` (sections, YAML metadata, metadata views)

Status: implemented (not committed). Decisions in §12; differences between the plan and the code in §14.

## 0. Goal

A grammar plugin that reads the **structure** of Markdown field notes, and nothing else:

- ATX headings (`#` … `######` + title) and Setext headings (title line + `===` / `---`) →
  nested **sections** (`capability.Section`);
- a YAML metadata block on the first line of the file → the **text header**
  (`capability.TextHeader`);
- a YAML metadata block right after a heading → the **metadata of that section**;
- every other line is ignored.

It runs **next to** the notebook grammar on the same `.md` files: the notebook grammar finds IGTs
and lexical entries, this one finds sections and metadata, and the application relates them by span
enclosure (`Text.enclosingSections`, `Text.metadataOf`).

Views:
- sections: the built-in `SectionViewFactory` (matchbox-gui), whose metadata becomes editable;
- header: a new `TextHeaderViewFactory` shipped **by this plugin**;
- both use a shared **metadata editor** (in matchbox-grammar-fx) with typed values — `date` (date
  picker), `media` (link, file chooser or browser, audio/video player), `speakers` (list of badges
  with `+`) — a delete button per entry and a `*` button to add an entry;
- an **"Add header" action** shown in the editor's margin when a file has no header, through a new
  generic contract (`TextActionProvider`) the GUI renders without knowing the plugin (§8b).

```markdown
---
language: Tuwari
speaker: Elaisa
---

Session 1
=========

---
date: 2024-05-12T14:30
media: recordings/session1.wav
---

## Nouns of the -mo class

<mami> {pig}

## Verbs
…
# Session 2
```

gives: a header `{language, speaker}`; section "Session 1" (Setext, level 1) `{date, media}` with
subsections "Nouns of the -mo class" and "Verbs"; section "Session 2". The lexical entry `<mami>`
(notebook grammar) gets, through `Text.metadataOf`, `language`, `speaker`, `date`, `media`.

---

## 1. Existing pieces

| Piece | Where | Use |
|---|---|---|
| `Section` (`getTitle`, `getMetadata`, `getContent`, `TITLE`, `metadataField(key)`) | matchbox-corpus `capability` | implemented by `MarkdownSection` |
| `TextHeader` (`getProperties`), `SimpleTextHeader` | matchbox-corpus | the file header |
| `HasFields`, `Field`, empty spans, `TextEdit` | matchbox-corpus | editable title and values |
| `Text.enclosingSections`, `Text.metadataOf`, `Text.properties` | matchbox-corpus | relate other grammars' objects to sections |
| `ObjectEditor`, `EditProposal`, `Spans`, `CollectingErrorListener` | matchbox-grammar-api | writing values, spans, diagnostics |
| `ViewContext` (`setField`, `canEdit`, `corpus()`…), `EditOutcome`, `ObjectViewFactory.updateView` | matchbox-grammar-fx | view → text edits |
| `SectionViewFactory`, `ViewEditService` | matchbox-gui | section view; applies view edits to documents |
| `grammars/matchbox-grammar-conll/pom.xml` | | template of a plugin pom that ships a view |
| `MarkdownHeadingLexer.g4` / `MarkdownHeadingParser.g4` in the notebook module | | old unused draft, compiled into the notebook jar: **removed** (§10) |

---

## 2. Module

| Item | Value |
|---|---|
| Directory / artifact | `grammars/matchbox-grammar-markdownheading` (added to the root pom `<modules>`) |
| Java package | `fr.cnrs.lacito.matchbox.grammar.markdownheading` |
| ANTLR sources | `src/main/antlr4/fr/cnrs/lacito/matchbox/grammar/markdownheading/MarkdownHeading{Lexer,Parser}.g4` |
| Plugin id (never to change) | `markdown-heading-grammar` |
| Kinds | `markdown-heading-grammar:section`, `markdown-heading-grammar:header` |
| Services | `META-INF/services/fr.cnrs.lacito.matchbox.grammarapi.GrammarPlugin` → `MarkdownHeadingGrammarPlugin`; `META-INF/services/fr.cnrs.lacito.matchbox.grammarfx.ObjectViewFactory` → `TextHeaderViewFactory` |

pom (from the conll pom, which already ships a view):

- `provided`: `matchbox-corpus`, `matchbox-grammar-api`, `matchbox-grammar-fx`,
  `javafx-controls`, `javafx-media`; `test`: `matchbox-corpus` test-jar;
- `antlr4-runtime` bundled (jar-with-dependencies), `antlr4-maven-plugin` (`visitor=true`,
  `listener=false`), `maven-antrun-plugin` copying the jar to `${build.root}/plugins` (picked up by
  the distribution assembly, nothing to change there);
- the `.g4` files copied into the jar as resources, for `getGrammarSource()`.

Jar check (skill): no `javafx/**`, no `fr/cnrs/lacito/matchbox/{corpus,grammarapi,grammarfx}/**`.

---

## 3. What is recognised

### 3.1 ATX headings (CommonMark subset)

A line that starts with 0–3 spaces, 1–6 `#`, then a space/tab and the title, or the end of the line
(empty heading). The title is trimmed and an optional closing sequence is removed
(`## Verbs ##` → "Verbs"). Not headings: `#hashtag`, seven `#`, lines indented by 4 spaces or more,
lines inside fenced code blocks.

### 3.2 Setext headings (restricted, decided with the user)

```
Session 1          Nouns
=========          -----
```

- An underline is a line of 0–3 spaces, then only `=` (level 1) or only `-` (level 2), at least one
  character (`---` included), trailing spaces allowed.
- It makes a heading **only if the line just above is a single text line**: that line is preceded
  by a blank line, the start of the file, a heading, a closed metadata block or a closed fence, and
  it is not an IGT delimiter `:::`, not a heading, a fence or a metadata line. The title is that line,
  trimmed.
- Otherwise `===` is an ordinary line and `---` is a separator (horizontal rule), or the opening of
  a metadata block where one is allowed (§3.4).

So a `---` separator after a block of several lines, or right after an IGT block, never turns that
block into a heading.

### 3.3 Fenced code blocks

A line (0–3 spaces) of at least three `` ` `` or `~` opens a block, closed by a line with the same
character repeated at least as many times; an unclosed block runs to the end of the file. Nothing
is recognised inside (no headings, no metadata, no underlines).

### 3.4 Metadata blocks (YAML subset)

```
---
key: value
key: "quoted value"
key: 'single quoted'
key:
# a comment
key: value # a comment
---
```

- Delimiters: lines `---` (trailing spaces allowed).
- **Where**: the file header only if `---` is the very first line of the file; a section's block
  only on the first non-blank line after the heading (after the underline for a Setext heading).
- **Closed or nothing**: a `---` opens a block only if a closing `---` line follows before the next
  heading line (checked by the lexer); otherwise it is an ordinary line, so an unclosed block never
  swallows the following headings. A text line + `---` is a Setext heading first (§3.2), not a
  block.
- **Entries**: `key: value`, one per line; key up to the first `:` (trimmed, non-empty, not starting
  with `#`, `-` or a space); value = the rest, trimmed. Double-quoted: `\"`, `\\` unescaped;
  single-quoted: `''` unescaped; unquoted: ` #` starts a comment; `key:` alone → empty value; blank
  and `# comment` lines ignored.
- **Not supported**, diagnostic on the line, line skipped, rest of the block kept: list items
  (`- x`), indented (nested) lines, block scalars (`|`, `>`). Flow collections (`[a, b]`) are kept as
  plain text.
- **Duplicate key**: last value wins, with a diagnostic. All values are strings, declaration order
  kept.

---

## 4. ANTLR grammar

Line-oriented lexer with modes; context-dependent decisions are lexer `@members` predicates
(as in `NotebookLexer`): start of file / after a heading (`metadataAllowed`), closed block ahead
(`closedAhead()`, scans `_input.LA(i)` without consuming), inside a fence (`FENCE` mode, opening
character and length kept in members), previous line kind (`previousLine` ∈ {START, BLANK,
SINGLE_TEXT, MULTI_TEXT, IGT_DELIMITER, STRUCTURE}) for Setext underlines.

### 4.1 Lexer (sketch)

```antlr
lexer grammar MarkdownHeadingLexer;

// default mode: one token per line; any line is at least a TEXT_LINE, so the default mode never fails
FENCE_OPEN       : {atLineStart()}? INDENT? ('```' '`'* | '~~~' '~'*) ~[\r\n]* EOL? {startFence();} -> pushMode(FENCE) ;
SETEXT_UNDERLINE : {atLineStart() && previousLine == SINGLE_TEXT}? INDENT? ('='+ | '-'+) [ \t]* EOL? {underline();} ;
META_OPEN        : {atLineStart() && metadataAllowed && closedAhead()}? '---' [ \t]* EOL -> pushMode(META) ;
HEADING_MARK     : {atLineStart() && markEndsHere()}? INDENT? '#' '#'? '#'? '#'? '#'? '#'? {heading();} -> pushMode(HEADING) ;
BLANK_LINE       : [ \t]* EOL {blank();} ;
TEXT_LINE        : ~[\r\n]+ EOL? {text();} ;   // ignored by the visitor except as a Setext title

mode HEADING;    HEADING_TEXT : ~[\r\n]+ ;  HEADING_END : EOL -> popMode ;
mode META;       META_CLOSE : {atLineStart()}? '---' [ \t]* EOL? {closed();} -> popMode ;
                 META_COMMENT : {atLineStart()}? [ \t]* '#' ~[\r\n]* EOL? -> skip ;
                 META_BLANK : [ \t]* EOL -> skip ;
                 META_KEY : {atLineStart()}? ~[ \t#\-:\r\n] ~[:\r\n]* ;
                 META_SEP : ':' -> pushMode(META_VALUE) ;
                 META_INVALID : {atLineStart()}? ~[\r\n]+ EOL? ;   // list item, nested line → diagnostic
mode META_VALUE; META_TEXT : ~[\r\n]+ ;  META_EOL : EOL -> popMode ;
mode FENCE;      FENCE_CLOSE : {atLineStart() && closesFence()}? … EOL? -> popMode ;
                 FENCE_LINE : ~[\r\n]* EOL -> skip ;

fragment INDENT : ' ' ' '? ' '? ;
fragment EOL    : '\r'? '\n' | '\r' ;
```

`text()` sets `previousLine` to `SINGLE_TEXT` after a blank/start/structure line, to `MULTI_TEXT`
after another text line, to `IGT_DELIMITER` for a `:::` line; `metadataAllowed` becomes false on any
text line. The exact rules are fixed by the tests (§11).

### 4.2 Parser

```antlr
parser grammar MarkdownHeadingParser;
options { tokenVocab = MarkdownHeadingLexer; }

document : metadata? (atxHeading | setextHeading | ignored)* EOF ;
atxHeading    : HEADING_MARK HEADING_TEXT? (HEADING_END | EOF) BLANK_LINE* metadata? ;
setextHeading : TEXT_LINE SETEXT_UNDERLINE BLANK_LINE* metadata? ;
metadata : META_OPEN entry* META_CLOSE ;
entry    : META_KEY META_SEP META_TEXT? META_EOL?   # keyValue
         | META_INVALID                             # unsupported
         ;
ignored  : TEXT_LINE | BLANK_LINE | FENCE_OPEN FENCE_CLOSE? ;
```

The tree is flat (headings in text order); nesting is computed in Java (§5).

---

## 5. Corpus objects

### 5.1 Classes

```java
/** A Markdown section: a heading, its metadata block, and everything up to the next heading of the same or a higher level. */
public final class MarkdownSection extends AbstractCorpusObject implements Section, HasFields {
    public enum Style { ATX, SETEXT }
    public int getLevel();                                  // 1–6 (Setext: 1 or 2)
    public Style getStyle();
    public String getTitle();                               // "" for an empty ATX heading
    public Map<String, String> getMetadata();               // own block only, declaration order
    public List<MarkdownSection> getContent();              // subsections
    public TextCoordinate getHeadingCoordinate();           // heading line(s), underline included
    public Optional<TextCoordinate> getMetadataCoordinate(); // the block, delimiters included
    public List<Field> getFields();                         // Section.TITLE, Section.metadataField(key)…
}
```

Header: `simple.SimpleTextHeader`, extended in matchbox-corpus with an optional `List<Field>`
(it becomes `HasFields`, like the other `Simple*` classes), and `TextHeader.propertyField(key)`
(`"property:" + key`) mirroring `Section.metadataField`.

### 5.2 Visitor and nesting

The visitor walks `document` once and collects a flat list of headings (level, style, title,
offsets, metadata, fields) and the header; no ANTLR object is kept. Nesting with a stack: a heading
closes the open sections of level ≥ its level (they end at the character before it), then is
pushed; skipped levels allowed (`#` then `###`: child); at EOF all open sections end at the last
character. Sections are built bottom-up when closed (immutable).

Spans:
- section: from the first character of the heading (the title line for Setext) to the character
  before the next heading of the same or higher level, or the last character;
- title field: title without indentation, marks, surrounding spaces and closing sequence (Setext:
  the trimmed title line); empty ATX title → empty span after the mark;
- metadata value field: raw value without quotes; empty value → empty span after `key:` (+ space);
- header: from the opening to the end of the closing `---` line.

Result: `[header?] + top-level sections`, text order.

### 5.3 `MarkdownHeadingGrammarPlugin`

`getResult(TextId, String)` (lexer, parser, `CollectingErrorListener`, visitor, diagnostics of
§3.4, internal errors as `Diagnostic.at`; local state only); `getFormatDocumentation()` (Markdown
description of §3 for the AI assistant); `getExamples()` (header + sections, Setext, section
metadata, fenced code containing `#`, each without diagnostics); `getGrammarSource()` (the `.g4`
files from the jar); `getObjectEditor()` → `MarkdownHeadingObjectEditor`; `getApiVersion()`.

---

## 6. Writing changes: `ObjectEditor` and the new `addField`

### 6.1 API additions (backward compatible: default methods, no `API_VERSION` change)

matchbox-grammar-api:
```java
/** Edits adding a field (e.g. a metadata entry); refused by default. */
default EditProposal addField(CorpusObject object, String fieldName, String value, String content) {
    return new EditProposal.Refused("This grammar cannot add fields to " + object.getKind());
}
```

matchbox-grammar-fx `ViewContext`:
```java
default EditOutcome addField(CorpusObject object, String fieldName, String value) { … NotEditable … }
default EditOutcome removeField(CorpusObject object, String fieldName) { … NotEditable … }
/** Directory of the saved project file, if any (resolution of relative paths). */
default Optional<Path> projectDirectory() { return Optional.empty(); }
/** Opens a URI in the system browser (or the default application). */
default void showDocument(String uri) {}
/** Types of metadata values by key, and vocabularies of list values (§7.2). */
default MetadataSchema metadataSchema() { return MetadataSchema.DEFAULT; }
/** Applies the edits of a text action (§8b) and selects the object at the given offset after the parse. */
default EditOutcome runTextAction(TextAction action) { … NotEditable … }
```

and in matchbox-grammar-api, next to `addField`:
```java
/** Edits removing a field (e.g. a metadata entry); refused by default. */
default EditProposal removeField(CorpusObject object, String fieldName, String content) { … Refused … }
```

matchbox-gui: `ViewEditService.addField` / `removeField` (same path as `setField`: grammar's
`ObjectEditor`, then `applyEdits`) and `runTextAction` (§8b); `FileModeController`'s `ViewContext`
implements them, `projectDirectory()` (from `Project.fileProperty()`) and `showDocument`
(`HostServices` of `MatchboxApplication`, passed down to the controllers). `ViewEditService.canEdit` accepts any `HasFields` object of a
writable document, even with no field yet (a header or section to which entries can be added).

### 6.2 `MarkdownHeadingObjectEditor`

- **Title** (`Section.TITLE`): no line break, trimmed; refused if it ends with ` #` (would read as a
  closing sequence); empty ATX heading written `#` → inserts `" " + value`. For a Setext heading the
  underline is kept (its length does not have to match).
- **Value** (`Section.metadataField(k)`, `TextHeader.propertyField(k)`): no line break; written
  plain when it reads back unchanged, otherwise double-quoted with `\` and `"` escaped (contains
  `: ` or ` #`, leading/trailing spaces, starts with a quote, `-`, `[`, `{`, `>`, `|`, `&`, `*`, `!`,
  `%`, `@`, `` ` ``, or is empty); replaces the whole raw value, quotes included.
- **Add** (`addField`): key must be non-empty, without `:` or line break, not starting with `#`/`-`,
  and not already present (refused with "edit the existing value"); the line `key: value` is
  inserted before the closing `---` of the block; for a section without a block, a block
  `---` / `key: value` / `---` is inserted right after the heading line (after the underline for
  Setext). Line endings follow the text (`\r\n` if the text uses them).
- **Remove** (`removeField`): deletes the whole `key: value` line (line ending included); an emptied
  block is kept (`---` / `---`), so entries can be added again; removing the title is refused.

Round-trips (set/add, parse again, same value) are tested for every case.

---

## 7. Shared metadata editor (matchbox-grammar-fx, package `grammarfx.metadata`)

Used by `TextHeaderViewFactory` (plugin) and `SectionViewFactory` (matchbox-gui). It lives in
matchbox-grammar-fx because both already depend on it (the plugin with `provided` scope): one copy at
runtime, same classes as `ViewContext`. Its public classes become an API that plugins compile against:
changes must stay backward compatible.

### 7.1 `MetadataPane`

```java
/** Rows key / value of an object's metadata, editable through a ViewContext. */
public final class MetadataPane extends VBox {
    /**
     * @param fieldName key → field name (Section::metadataField, TextHeader::propertyField)
     */
    public MetadataPane(CorpusObject owner, Map<String, String> own, Map<String, String> inherited,
                        Function<String, String> fieldName, ViewContext context);
    /** Shows the values of a re-parsed owner, keeping the rows being edited and the players of unchanged media. */
    public void update(CorpusObject owner, Map<String, String> own, Map<String, String> inherited);
}
```

- One row per own entry: key (bold), value displayed by its value editor, and a delete button (×,
  confirmation-free, undoable) → `context.removeField(owner, fieldName(key))`; editable when
  `context.canEdit(owner)`.
- Inherited entries (sections only: from the header and enclosing sections), greyed, read-only, but
  dates and media are still displayed with their editors in read-only mode (formatted date, link
  opening nothing, player working).
- `*` button: adds a new row with a key field and a value field; when the key typed is `date` or
  `media` (trimmed, case-insensitive), a **datepicker** or **filechooser** button appears next to
  the value field (and for a list key, its `+` picker), as given by the schema (§7.2); Enter (or the button's choice) → `context.addField(owner, fieldName(key), value)`;
  Escape removes the row.
- An `EditOutcome` other than `Applied` shows its message under the row (red border on the field).

### 7.2 Value editors

```java
/** How a metadata value is displayed and edited, chosen by key. */
public interface MetadataValueEditor {
    boolean handles(String key);                 // "date", "media"; the plain editor handles everything else
    Node display(ValueCell cell);                // value shown in a row
    Optional<Node> chooserButton(ValueCell cell); // for the "*" row (datepicker / filechooser)
}
```

`ValueCell` gives the key, the raw value, whether it is editable, a `commit(String)` callback, the
base directories (§7.4), the schema and the window.

The editor of a key is chosen from a **`MetadataSchema`** given by `ViewContext.metadataSchema()`, so
the application (not the views) owns the configuration:

```java
public sealed interface ValueType {
    record Plain() implements ValueType {}
    record Date() implements ValueType {}
    record Media() implements ValueType {}
    /** Comma-separated values, picked from a vocabulary (may be empty: free values only). */
    record ValueList(String vocabularyId) implements ValueType {}
}

public interface MetadataSchema {
    ValueType typeOf(String key);              // case-insensitive key
    Vocabulary vocabulary(String vocabularyId); // never null; Vocabulary.EMPTY if unknown
    MetadataSchema DEFAULT = …;                // date → Date, media → Media, speakers → ValueList("speakers"), else Plain
}

/** Known values of a list field (e.g. the speakers of a project). */
public interface Vocabulary {
    List<Choice> choices();                    // offered by the "+" button
    Optional<Choice> lookup(String value);     // recognised value → badge with its label and tooltip
    record Choice(String value, String label, String description) {}
    Vocabulary EMPTY = …;
}
```

Built-in editors: `PlainValueEditor`, `DateValueEditor`, `MediaValueEditor`, `ListValueEditor`,
one per `ValueType`. For now the GUI's `ViewContext` returns `MetadataSchema.DEFAULT` with an empty
`speakers` vocabulary; loading the speakers list (project file, settings…) is a later change of the
application only: views and plugins do not change.

### 7.3 `date`

- Parsing (`IsoDates`, pure, tested): ISO 8601 forms, tried in order:
  `2024-05-12T14:30:15.250+02:00` / `Z` (offset date-time, seconds and fraction optional),
  `2024-05-12T14:30[:15]` (local date-time; a space instead of `T` accepted), `2024-05-12`,
  basic `20240512`, `2024-05` (year-month), `2024` (year). Unrecognised → plain value.
- Display (decided): **localised** in the user's locale — `FormatStyle.MEDIUM` date, plus
  `SHORT` time when present, offset appended when present (e.g. "12 May 2024, 14:30"; year-month
  "May 2024") — with the **ISO value as tooltip**.
- Editing: clicking the date opens a small dialog with a JavaFX `DatePicker` (opened directly),
  an optional time (check box "time" + hour/minute spinners), OK/Cancel. The value written stays
  ISO 8601: `yyyy-MM-dd`, or `yyyy-MM-ddTHH:mm` when a time is given (the original offset is kept if
  there was one; seconds kept if they were written and the time did not change). A year-month or
  year value is replaced by the full date chosen.

### 7.4 `media`

- Resolution (`MediaPaths`, pure, tested), decided with the user: an absolute path is used as is;
  a relative path is resolved first against the **project directory** (`ViewContext.projectDirectory()`,
  only if the project is saved) and, if the file does not exist there or the project is not saved,
  against the **directory of the text file** (from the owner's `TextId`). `file:` URIs accepted.
- `http://` and `https://` values: a link opening the system browser (`ViewContext.showDocument`),
  no player; an edit button (✎) next to it changes the value (text field, or file chooser to
  replace the URL by a file).
- Display: a link with the file name (tooltip: resolved path); a missing file is shown in red with
  "not found" (the link still opens the chooser).
- Clicking the link (editable rows) opens a `FileChooser` (initial directory: the file's directory
  or the base directory; filters for the supported formats and "All files"). The chosen path is
  written relative to the project directory if it is inside it, otherwise relative to the text file's
  directory if inside it, otherwise absolute; `/` as separator.
- Player next to the link, for formats JavaFX plays: audio `mp3`, `wav`, `aif(f)`, `m4a`/`aac`;
  video `mp4`/`m4v` (H.264/AAC), `flv`. Play/pause, position slider, time label; a `MediaView`
  (fit width 360 px) for video. No autoplay. Other formats: link only, with "format not supported
  by the player". Load errors (`MediaPlayer.setOnError`) shown as text.
- Lifecycle: the `MediaPlayer` is disposed when its row leaves the scene (scene listener) or its
  value changes; `MetadataPane.update` keeps the player when the resolved path is unchanged, so a
  re-parse (e.g. after editing another entry) does not stop playback.

### 7.5 Lists (`ValueList`, e.g. `speakers`)

- In the text: comma-separated values, `speakers: Elaisa, John Smith` (a value containing a comma is
  double-quoted inside the list: `"Smith, John"`). A YAML flow list `[Elaisa, John]` is read too and
  rewritten in the same style when edited. Parsing and writing in `ValueLists` (pure, tested).
- Display: one badge per value; a value recognised by the vocabulary is a filled badge with the
  vocabulary label and a tooltip (description); an unknown value is an outlined badge. Each badge
  has a × (remove the value); a `+` button opens a picker of the vocabulary choices not yet used,
  with a text field to type a value that is not in the vocabulary.
- Each change rewrites the whole value through `commit` (one `setField`, one undo step).
- Inherited lists are shown as read-only badges.

### 7.6 Dependencies

`javafx-media` (`${javafx.version}`) added to the root `dependencyManagement`; matchbox-grammar-fx
(`provided`), matchbox-gui and matchbox-distribution (bundled, like the other JavaFX jars; the
platform jar contains the native media libraries), the plugin (`provided`). Linux needs the system
`libavcodec` for AAC/H.264 (documented in README). jpackage `addModules` unchanged (JavaFX stays on
the class path); checked by running the installer build.

---

## 8. `TextHeaderViewFactory` (plugin)

- `getType()`: `TextHeader` — the view therefore applies to the header of any grammar when this
  plugin is installed (closest-supertype rule); without the plugin, headers fall back to the generic
  view.
- Detail view: "Header" title, file and position (click → select in the editor), then a
  `MetadataPane` (own = properties, no inherited, field names `TextHeader::propertyField`).
- `updateView`: `MetadataPane.update`, so editing a value does not rebuild the view.
- Columns: Properties (`key=value; …`).

## 8b. Text actions in the editor ("Add header")

The global GUI must not depend on a plugin; plugins may offer actions on a text through a generic
contract, rendered by the GUI without knowing what they do.

matchbox-grammar-fx:
```java
/** Actions a plugin offers on a parsed text, shown in the editor (registered as a service, like ObjectViewFactory). */
public interface TextActionProvider {
    /** Called on the JavaFX thread after each parse of a text; empty if nothing to offer. */
    List<TextAction> actionsFor(Text text, ViewContext context);
}

/**
 * @param offset        where the action is shown (its line)
 * @param edits         edits relative to {@code text.content()}, applied as one undoable change
 * @param selectOffset  offset of the object to select after the new parse (-1: none)
 */
public record TextAction(String label, String description, int offset, List<TextEdit> edits, int selectOffset) {}
```

matchbox-gui:
- `TextActionProvider`s loaded with `PluginManager.loadServices` (as the view factories); after each
  parse of the displayed text, the editor asks every provider (guarded: a failing provider is logged
  once and ignored) and shows each action as a small button in the left margin of its line
  (RichTextFX paragraph graphic factory), never in the text;
- clicking: `ViewContext.runTextAction` → the edits go through `ViewEditPlanner` (stale check,
  relocation; it gets a variant taking a `TextId` instead of an object) and the document
  (`EditOrigin.Kind.VIEW`, one undo step); the document is parsed again (non-typing change); the
  object at `selectOffset` is then selected, so its view opens.

Plugin: `MarkdownHeadingActions implements TextActionProvider` (registered in
`META-INF/services/fr.cnrs.lacito.matchbox.grammarfx.TextActionProvider`): when the text was parsed
by this grammar (`text.grammarIds()` contains the id) and has no header, one action "Add header"
at offset 0, edits = insert `---⏎---⏎` at offset 0 (line ending of the text), `selectOffset` = 0.
The empty header then opens in `TextHeaderViewFactory`, whose `*` button adds the first entry.
Without the plugin, nothing is shown; writing the two `---` lines and parsing still works.

## 9. `SectionViewFactory` changes (matchbox-gui)

- The read-only metadata grid is replaced by a `MetadataPane` (own entries editable through
  `Section::metadataField`, inherited entries read-only, `*` to add an entry, which creates the
  block if the section has none).
- `updateView` implemented (title, breadcrumb, summary and outline refreshed; `MetadataPane.update`
  keeps edits and players), so the view is no longer rebuilt after each edit.
- Unchanged: breadcrumb, summary, outline, editable Title column.

---

## 10. Changes outside the module

| Where | Change |
|---|---|
| root `pom.xml` | new module; `javafx-media` in `dependencyManagement` |
| matchbox-corpus | `SimpleTextHeader` with optional fields (`HasFields`); `TextHeader.propertyField(key)`; tests |
| matchbox-grammar-api | `ObjectEditor.addField`, `removeField` (default refused) |
| matchbox-grammar-fx | `ViewContext.addField`, `removeField`, `projectDirectory()`, `showDocument`, `metadataSchema()`, `runTextAction`; package `metadata` (§7: pane, value types, schema, vocabularies, editors); `TextActionProvider`, `TextAction` (§8b); `javafx-media` provided |
| matchbox-gui | `ViewEditService.addField`/`removeField`/`runTextAction`, `canEdit` for objects without fields yet, `ViewEditPlanner` variant by `TextId`; `ViewContext` implementation (`HostServices` passed from `MatchboxApplication`); text actions in the editor margin (§8b); `SectionViewFactory` (§9); `javafx-media` dependency |
| matchbox-distribution | `javafx-media` bundled |
| matchbox-app `Project.createDefaultProject` | file type "Markdown files" `.*\.md` with the markdown-heading grammar **when the plugin is loaded** (no failure otherwise); the notebook grammar already applies to `.md` through "All files"; `Project.MARKDOWN_HEADING_PLUGIN_ID`; test with a fake plugin |
| notebook module | delete the old `MarkdownHeading*.g4` and `.antlr/MarkdownHeadingLexer.*` |
| docs | `CLAUDE.md` (module table, grammar-fx now also holds reusable controls, `addField`, built-in/plugin factories table), write-grammar skill (reference example of a plugin reusing `Section`/`TextHeader` and the metadata editor), `README.md` (headings, metadata, `date`/`media`, Linux codecs) |

---

## 11. Tests

Plugin (`MarkdownHeadingGrammarPluginTest`, with `CorpusAssertions`):
- sample of §0: header, titles, levels, styles, nesting, metadata, no diagnostics;
- ATX: closing sequence, empty heading, `#hashtag`, seven `#`, 4-space indent; skipped levels;
  content before the first heading;
- Setext: `===` and `---` after a single line; not after two text lines; not after `:::`; `---`
  after a blank line is a separator; Setext heading followed by a metadata block; a text line
  followed by `---` at the start of a section is a heading, not a block;
- fences (``` and ~~~, longer closing fence, unclosed) hide headings, underlines and blocks;
- metadata: header only on line 1, block after heading with blank lines between, unclosed block →
  ordinary lines and following headings kept, quotes/escapes, comments, empty value, duplicate key,
  list item and nested line;
- CRLF and CR line endings, no final newline, empty file, header only;
- two successive `getResult` calls independent; with `Text.builder(…)`: `metadataOf` of a
  subsection merges header, outer and inner metadata, and a fake other-grammar object inside a
  subsection has it in `enclosingSections`.

Editing (`MarkdownHeadingEditingTest`): title (ATX, empty ATX, Setext), value (plain, needs quotes,
empty), add to header, add to a section block, add to a section without block (ATX and Setext),
refusals (line break, trailing ` #`, existing key, invalid key), CRLF text; after each edit, parse
again: new value, other objects unchanged.

matchbox-grammar-fx: `IsoDates` (each format, invalid values, write-back with/without time and
offset), `MediaPaths` (absolute, relative to project, fallback to text directory, not found,
relativisation for writing, Windows-style separators, http(s) detection), `ValueLists` (split,
quoted items, flow style, join), `MetadataSchema.DEFAULT` types.

Plugin, in addition: `removeField` (entry removed, emptied block kept, header and section);
`MarkdownHeadingActions` (action offered only without header and only for texts of this grammar;
applying its edits then parsing gives an empty header at offset 0).

matchbox-gui: registry picks `TextHeaderViewFactory` for a `TextHeader` when registered;
`ViewEditService.addField`/`removeField` with the plugin's editor and `runTextAction` (stale action
refused after typing that changed the place; test dependency on the plugin, as for the notebook
grammar); failing `TextActionProvider` ignored.

Build: full `mvn clean install`, jar check (§2). Manual GUI check: new project with a `.md` file;
Sections tab; detail view (breadcrumb, own/inherited metadata, outline with notebook objects); edit a
title, a value, a date (with and without time), a media path and an http link (browser); play an
audio and a video file; add entries with `*` (plain, `date`, `media`, `speakers`), including to a
section without a block; remove entries; speakers badges and `+`; "Add header" button on a file
without header, then the header view; undo of each of these.

---

## 12. Decisions (taken with the user)

| Question | Decision |
|---|---|
| Metadata syntax | YAML `key: value`, one per line (subset of §3.4) |
| Section class | `MarkdownSection` in the plugin (level, style), `Section` + `HasFields` |
| ATX headings | CommonMark subset, fenced code blocks respected |
| Setext headings | Supported, `===` and `---`, only when the line above is a single text line that is not `:::` |
| Editing | Title and values editable; entries can be added (`*`) |
| Naming | `matchbox-grammar-markdownheading`, package `…grammar.markdownheading`, id `markdown-heading-grammar` |
| Old draft grammar in notebook | Removed |
| Where metadata blocks may appear | File header on line 1 only; section block on the first non-blank line after the heading |
| Default project | "Markdown files" (`.*\.md`) file type with this grammar when the plugin is present |
| Header view | `TextHeaderViewFactory` in this plugin |
| Section metadata | Editable in `SectionViewFactory`, same component as the header view |
| `date` | ISO 8601 forms parsed; localised display, ISO tooltip; DatePicker with optional time |
| `media` | Resolved against the project directory, then the text file's directory; link + file chooser + audio/video player |
| Shared component | matchbox-grammar-fx, package `grammarfx.metadata` (recommended, see §7) |
| Removing entries | Delete button per own entry (`removeField`) |
| Adding a header | No plugin-specific GUI code: generic `TextActionProvider`; the plugin offers "Add header" in the editor margin; typing two `---` lines and parsing also works |
| `http(s)` media | Link opening the browser, no player |
| Lists | Generic `ValueList` type (comma-separated, badges, `+` picker from a vocabulary); `speakers` is a list; its vocabulary will be loaded later by the application |

Further choices made in this plan (to confirm or change): keys `date`/`media` matched exactly,
case-insensitively; an unclosed `---` is an ordinary line; duplicate keys: last wins + diagnostic;
unsupported YAML lines: diagnostic, skipped; chosen media paths written relative when possible, `/`
separators; year-month/year dates replaced by a full date when edited with the picker.

## 13. Open questions

1. **Speakers vocabulary**: where the list comes from (project file, a shared file, settings) —
   to be specified later; only the application's `metadataSchema()` will change.
2. **Typed values outside lists**: should unknown list values typed with `+` be proposed for
   addition to the vocabulary? Depends on (1).

## 14. Implementation notes (differences from the plan)

- **Lexer**: every token is a whole line (ANTLR picks the longest match, so a token covering only
  part of a line — a heading mark, a YAML key — always lost against the "other line" rule). The
  context-dependent decisions are made in lexer actions with `setType`: a `-` line becomes
  `SETEXT_UNDERLINE`, `META_OPEN` or `RULE_LINE`; fence lines are skipped until the closing fence. A
  metadata entry is one `META_ENTRY` token, split into key and value in Java (`YamlEntry`).
- **`#` lines inside a block**: a YAML comment, unless a blank line precedes it — then it is a
  heading and the `---` above is not a block opening (so an unclosed block is still not allowed to
  swallow a heading written in the usual Markdown style, with a blank line before it).
- **Setext and `---`**: as in CommonMark, a single text line followed by `---` is a heading also
  after a heading or a closed metadata block (e.g. `text` / `---` in a section); only multi-line
  paragraphs and `:::` lines are excluded, as decided.
- `MetadataPane` shows refusal messages in one line under the rows (not under each row).
- `MetadataValueEditor` API: `display(ValueCell)` and `chooser(ValueCell, currentText, setValue)`;
  `ValueCell` is a record (owner, key, value, editable, commit, context) giving the schema and the
  base directories. Built-in editors are package-private; `MetadataValueEditor.of(ValueType)` picks one.
- Date editing: a dialog with a `DatePicker` (opened at once), a "Time" check box and hour/minute
  spinners.
- Media player (`MediaPlayerBox`): released when it stays out of the scene (deferred check, so a row
  moved by `MetadataPane.update` keeps playing).
- Text actions are computed after each parse of the displayed text and shown with RichTextFX's
  paragraph graphic factory (buttons in the margin of their line); "Add header" selects the header
  with `EditorController.selectAfterParse`.
- `ViewEditService.canEdit` accepts any `HasFields` object of a writable document (a header or block
  without entries can receive the first one).
- matchbox-gui has a test dependency on the plugin (registry and planner tests).
- Verified: unit tests of every module, full `mvn clean install`, jar content, application startup
  (plugin and view factory loaded). Not verified by hand yet: the interactive behaviour listed in §11
  (date picker, media playback, badges, "Add header" button, undo).

