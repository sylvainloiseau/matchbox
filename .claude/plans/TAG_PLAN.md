# Plan: `matchbox-grammar-tag` (inline tags)

Status: implemented (not committed). Decisions taken with the user in §10; later work in §11; differences between the plan and the code in §12.

## 0. Goal

Let the user tag any place of their notes with a keyword, anywhere in a line, several per line:

```markdown
# Session 1 #"elicitation"

:::
a mio-jaflo-m-wi-j
1sg down-canoe-top-m.sg-pred
'I went down in canoe' #"verb/motion"
:::
#“to check”

<mami> {pig #"noun/animal"}
```

- A **tag** is `#` + a quoted keyword: `#"infinitive"` or `#“infinitive”`.
- A keyword may be **hierarchical**: `#"verb/infinitive"` is "infinitive" under "verb".
- Each tag **applies to** an object (of any grammar): the object it is written in, or the object on
  the line just above or below it (no blank line in between), else its section or header, else the
  text; views show the tags of an object.
- Tags are listed in the objects table (one tab, editable Tag column) and highlighted in the editor;
  selecting one shows an empty detail pane.

---

## 1. The syntax and its risks

### 1.1 Why `#"…"` is a good choice

- **Markdown headings**: an ATX heading needs a space, a tab or the end of the line after the
  `#`s; `#"x"` is never a heading, at the start of a line or elsewhere.
- **Hashtags** (`#word`) would be shorter but cannot contain spaces, and `#word` at the start of a
  line is read as a heading by some Markdown dialects; the quotes avoid both.
- `@`-prefixed alternatives would collide with media markers (`@media`).

### 1.2 Remaining interactions (documented, no change to other grammars)

| Where | What happens | Consequence |
|---|---|---|
| CoNLL | a line *starting* with `#"…"` is a CoNLL comment; next to a `sent_id` it becomes a metadata line of the sentence (no `=`, no value stored) | harmless; the tag applies to that sentence |
| YAML metadata (markdown-heading) | in `speakers: Elaisa #"todo"`, ` #` starts a YAML comment: the value becomes `Elaisa` | documented: put tags on their own line or outside metadata values; the tag applies to the header or section |
| Notebook entries | `<mami> {pig #"noun"}`: the tag is part of the definition text of the entry | documented; the tag applies to the sense |
| IGT lines | a tag inside the transcription or gloss line breaks the morpheme alignment (notebook error) | documented: put tags on the translation line or outside the block |
| Fenced code blocks | not read as tags (decided) | code examples containing `#"…"` are safe |

---

## 2. The convention (decided)

```
#"<keyword>"      or      #“<keyword>”
```

- **Delimiters** (decided): straight quotes `"…"` or curly quotes `“…”` (U+201C … U+201D), the
  closing quote matching the opening one. Edited or new tags are written with straight quotes.
- **Keyword** (decided): any characters except the closing quote and line breaks; surrounding
  spaces are ignored (`#" past tense "` is `past tense`); `#""` (or only spaces) is not a tag and is
  reported as a diagnostic. No escapes.
- **Hierarchy** (decided): `/` separates levels: `#"verb/infinitive"` has the path
  `[verb, infinitive]`; spaces around `/` and empty levels are ignored (`#"verb / / inf"` →
  `[verb, inf]`). The keyword itself is kept as written (trimmed).
- Anywhere in a line, several per line; an unclosed `#"…` (no closing quote before the end of the
  line) is plain text.
- **Not read inside fenced code blocks** (decided): ``` / ~~~ fences, same rule as the markdown-heading
  grammar (closing fence of the same character, at least as long; unclosed fence runs to the end).

---

## 3. Corpus objects

### 3.1 Capability `capability.Tag` (matchbox-corpus)

```java
/** A keyword attached to a place of a text (#"keyword"); "/" separates the levels of a hierarchy. */
public interface Tag extends CorpusObject {
    /** Field name of the keyword (see HasFields). */
    String TAG = "tag";

    /** The keyword as written, trimmed (e.g. "verb/infinitive"). */
    String getName();

    /** Levels of the keyword: "verb/infinitive" → [verb, infinitive]; spaces and empty levels ignored. */
    default List<String> getPath() { … }

    /** The last level ("infinitive"). */
    default String getLeaf() { … }

    @Override
    default String getLabel() { return getName(); }
}
```

Admission rule: tag listing, attachment to objects (views), highlighting, later search and filtering
by tag need it; other grammars can produce tags too.

### 3.2 `simple.SimpleTag` (matchbox-corpus)

Immutable: kind, span (the whole `#"…"`), name, and the field `tag` (span of the keyword inside the
quotes, trimmed) — it implements `HasFields`, which makes the Tag column editable.

### 3.3 Attachment (decided: now), computed in `Text`

The target of a tag is computed after parsing, from the spans of the objects of all grammars (tags
excluded); no grammar is involved.

1. **Containment**: `C` = the innermost object containing the tag (smallest span; equal spans: the
   object of the later grammar in rank order, then the deeper one), if any.
2. **Adjacency** (decided): the *neighbour lines* of the tag are the line just above and the line
   just below its line, skipping lines that contain only tags (and spaces); a blank line (empty or
   spaces only) stops the search. An object is *adjacent* if it ends on the neighbour line above or
   starts on the neighbour line below, does not contain the tag, and lies inside `C` (anywhere if
   there is no `C`). Among the objects adjacent on one side, the **outermost** is taken (the entry
   rather than its last sense, the block rather than its parts).
3. The target is the adjacent object **above** if any, else the adjacent object **below**, else `C`,
   else the **text**.

Consequences:
- a tag inside an IGT or a definition stays with it (an adjacent object would have to lie inside it);
- a tag on a line of its own (or on a free text line) just below or above an IGT, an entry, a media
  marker… applies to it, even though the section contains the tag;
- consecutive lines of tags all apply to the same object;
- a tag separated by a blank line applies to its section (or header), else to the text.

`Text` (matchbox-corpus) gains, computed once per snapshot:

```java
/** The object a tag applies to; empty when it applies to the whole text. */
public Optional<CorpusObject> targetOf(Tag tag);
/** Tags applying directly to an object. */
public List<Tag> tagsOf(CorpusObject object);
/** Tags applying to the whole text (in no object and next to none). */
public List<Tag> textTags();
```

Implementation: a line index of the content (start of each line, blank lines, lines made only of
tags), objects indexed by their start and end lines; about 80 lines of pure Java, tested without
JavaFX.

---

## 4. The grammar module

| Item | Value |
|---|---|
| Directory / artifact | `grammars/matchbox-grammar-tag` (root pom `<modules>`) |
| Package | `fr.cnrs.lacito.matchbox.grammar.tag` |
| Plugin id (never to change) | `tag-grammar` |
| Kind | `tag-grammar:tag` |
| Services | `GrammarPlugin` → `TagGrammarPlugin`; `ObjectViewFactory` → `TagViewFactory` (empty pane) |
| Dependencies (`provided`) | `matchbox-corpus`, `matchbox-grammar-api`, `matchbox-grammar-fx`, `javafx-controls` (for the view); `antlr4-runtime` bundled; corpus test-jar for tests |

### 4.1 ANTLR grammar

Lexer (`TagLexer.g4`): the text is scanned for tags; everything else is skipped.

- default mode:
  - `TAG : '#' ('"' ~["\r\n]* '"' | '“' ~[”\r\n]* '”')` — the longest match makes a
    complete tag win over a lone `#`;
  - `FENCE_OPEN : {at line start}? ' '? ' '? ' '? ('```' '`'* | '~~~' '~'*) ~[\r\n]* -> pushMode(FENCE), skip`
    (fence character and length kept in `@members`);
  - `NL : ('\r'? '\n' | '\r') -> skip`;
  - `TEXT : ~[#\r\n`~]+ -> skip`, `OTHER : . -> skip` (a `#` not starting a tag, a backtick or tilde
    not starting a fence).
- mode `FENCE`: one token per line, skipped, until the closing fence (`popMode`), as in the
  markdown-heading lexer; "at line start" checks the previous character (lone `\r` line endings).

Parser (`TagParser.g4`): `document : TAG* EOF ;`

### 4.2 `TagGrammarPlugin`

- `getResult`: one `SimpleTag` per `TAG` token: keyword = text between the quotes, trimmed; span of
  the object = the token; field `tag` = span of the trimmed keyword; an empty keyword gives a
  diagnostic ("empty tag") and no object. Local state only.
- `getFormatDocumentation()` (§2, for the AI assistant), `getExamples()`, `getGrammarSource()`,
  `getObjectEditor()` (§6), `getApiVersion()`.

---

## 5. Views and editor

### 5.1 Objects table and detail pane

- Tags are top-level objects: the objects table gets a **Tag** tab. `TagViewFactory` (plugin):
  - `getColumns()`: **Tag** (editable, field `tag`, shows the keyword), **Path** (`verb › infinitive`),
    **Applies to** (label and kind of the target, or "(text)"; computed through
    `ViewContext.corpus().textOf(tag).targetOf(tag)` — `ObjectColumn` has no context, so this column
    is provided by a new `ObjectViewFactory.getColumns(ViewContext)` default method delegating to
    `getColumns()`; see §7);
  - `createView()`: an **empty pane** (decided).

### 5.2 Tags shown in the views of the objects they apply to (attachment decided)

A shared component in matchbox-grammar-fx, `grammarfx.tags.TagBadges.of(CorpusObject, ViewContext)`:
a "Tags:" line of badges (`verb › infinitive`), each clickable (selects the tag in the editor);
nothing when the object has no tag. Added to the built-in views — generic,
`InterlinearSentenceViewFactory`, `LexicalEntryViewFactory` (and its senses), `SectionViewFactory`,
`MediaSegmentViewFactory` — and to `TextHeaderViewFactory` (markdown-heading plugin), which also
shows the tags of the whole text (`Text.textTags()`).

### 5.3 Highlighting (decided)

- New colour setting `tagColor` (default `#ffd6e8`): `SettingsData`, `UserSettings`, colour picker in
  the settings dialog; `HighlightStyles.colorFor` returns it for `Tag`.
- `EditorController.highlightText` draws tags **after** every other object, so that a tag inside an
  IGT or an entry stays visible.

---

## 6. Editing (decided: this occurrence)

`TagObjectEditor` (plugin): `setField(tag, "tag", value)`:
- value trimmed; refused if empty, or if it contains `"` or a line break;
- the whole tag is rewritten as `#"value"` (straight quotes, even if it was curly), one undo step;
- this is the "rename this tag" action: editing the Tag cell of the objects table changes the tag
  in the text (decided). Renaming every occurrence of a tag in the corpus is not part of this plan
  (it will come with the tag browser, §11).

---

## 7. Changes outside the module

| Where | Change |
|---|---|
| root `pom.xml` | new module |
| matchbox-corpus | `capability.Tag`, `simple.SimpleTag`; `Text.targetOf`, `tagsOf`, `textTags`; tests |
| matchbox-grammar-fx | `tags.TagBadges`; `ObjectViewFactory.getColumns(ViewContext)` (default: `getColumns()`) |
| matchbox-gui | `ObjectViewRegistry.columnsFor` calls `getColumns(context)`; tag badges in the built-in views; `HighlightStyles` (tag colour), tags drawn last in `EditorController`; settings dialog colour picker |
| matchbox-app | `tagColor` in `SettingsData`/`UserSettings`; `Project.createDefaultProject`: tag grammar added to "All files" when installed (`Project.TAG_PLUGIN_ID`); tests |
| markdown-heading plugin | header view: tags of the header and of the whole text |
| docs | `CLAUDE.md`, write-grammar skill (a grammar producing `Tag`s gets the table, badges and highlight), `README.md` (convention, interactions of §1.2) |

---

## 8. Tests

- Plugin: straight and curly tags; several per line; anywhere in a line, at the start of a line;
  trimming; empty tag → diagnostic; unclosed tag → nothing; mismatched quotes (`#"x”`) → nothing;
  `#` alone, hashtags, Markdown headings, CoNLL comments → nothing; fenced code blocks (``` and ~~~,
  longer closing fence, unclosed) → nothing inside; CRLF and lone CR; independent results; examples;
  `assertSpansNested`, `assertKindsPrefixed`; field span of the keyword.
- `Tag.getPath()` / `getLeaf()`: levels, spaces, empty levels.
- `Text.targetOf` / `tagsOf` / `textTags`: tag in an IGT, in a sense (not the entry), in a header;
  tag line just below an IGT, just above an entry (the entry, not its sense), between two objects
  (the one above), after other tag lines (same target), separated by a blank line (the section),
  outside everything (the text); free text line with a tag next to an object; objects of several
  grammars; equal spans.
- Editing: rename (straight quotes written, curly replaced), refusals (empty, quote, line break).
- matchbox-gui: registry (empty view for `Tag`), `getColumns(context)` delegation.
- Build: full `mvn clean install`, jar content check (skill). Manual: Tag tab and editing, empty
  detail pane, badges in the IGT, entry, section, media segment and header views, highlight colour and
  its setting, tags in a code block ignored.

---

## 9. Phases

1. matchbox-corpus: `Tag`, `SimpleTag`, attachment in `Text`; tests.
2. Plugin: grammar, plugin class, editor, empty view; tests.
3. matchbox-grammar-fx and matchbox-gui: `getColumns(ViewContext)`, `TagBadges` in the views,
   highlight and setting; header view; default project; tests.
4. Docs; full build; manual check.

---

## 10. Decisions (taken with the user)

| Question | Decision |
|---|---|
| Syntax | `#"keyword"` (anywhere, several per line) |
| Quotes | Straight `"…"` and curly `“…”`; edits write straight quotes |
| Keyword | Any text except the closing quote and line breaks, trimmed, non-empty; no escapes |
| Structure | Hierarchy with `/` (`Tag.getPath()`) |
| Ignored | Inside fenced code blocks |
| Attachment | Now, computed in `Text`: adjacent object above, else below (no blank line; tag-only lines skipped; outermost; inside the containing object), else the containing object, else the text; shown in the views |
| Detail view | Empty pane (plugin `TagViewFactory`) |
| Editing | Editing the Tag cell renames this occurrence in the text; no rename everywhere in this plan |
| Defaults | Added to "All files" when installed; tags highlighted (new `tagColor` setting) |

## 11. Later (not in this plan)

1. **Tag browser**: a corpus-wide list of tags (by path, with counts and occurrences) — the next
   feature.
2. **Rename a tag everywhere** (all occurrences in the corpus): with the tag browser.

## 12. Implementation notes (differences from the plan)

- The attachment is computed by a package-private class `TagAttachment` (matchbox-corpus), created
  lazily by `Text` on the first call to `targetOf`/`tagsOf`/`textTags`. Object lines are computed from
  the offsets (a span ending with its line ending ends on that line).
- The plugin's view depends on `matchbox-grammar-fx` and `javafx-controls` (provided), not on
  `javafx-media`.
- `TagBadges` (matchbox-grammar-fx, package `tags`): `of(object, context)` and `ofText(text, context)`;
  a badge selects the tag in the editor. Lexical entries show the badges of the entry and of each
  sense below the FXML view.
- Fences: like the markdown-heading grammar, a longer fence closes a shorter one, a shorter one does
  not close a longer one.
- Verified: unit tests of every module, full `mvn clean install`, plugin jar content, application
  startup with the plugin. Not verified by hand yet: the Tag tab and its editing, the empty detail
  pane, badges in the views, highlight and its setting (§8, manual checks).

