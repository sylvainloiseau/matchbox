# Plan: Tag Browser mode

Status: implemented (not committed); differences between the plan and the code in §13. Architectural problems found in the request in §1; questions
in §11 (decided ones first; the plan assumes the recommendation for the others); suggestions in §12.

## 0. Goal

A mode, **Tag Browser**, that lists every `Tag` of the corpus:
- grouped by keyword, as a tree of levels (`#"pos/verb/transitive"` → pos › verb › transitive);
- with the number of occurrences of each node;
- with each occurrence shown in a few lines of context;
- with a way to rename a keyword, or a level of keywords, everywhere;
- with a way to jump to an occurrence in File Mode.

It is the first mode built on the mode contract (`MODE_PLAN.md`). It reuses only what tags already
offer:
- the `Tag` capability: `getName()`, `getPath()`, field `tag`;
- `Text.objectsOf(Tag.class)` and `Text.targetOf(tag)`;
- the tag plugin's `ObjectEditor`, through the view edit service.

It works with any plugin whose objects implement `Tag`, not only `#"…"`.

---

## 1. Architectural assessment of the request

The request fits the mode architecture. Most of it is reading and navigation, which existing
services cover. Eight points need a decision or a small addition.

### P1. "Rename everywhere" needs a batch edit, which does not exist yet

Today an edit is per object: `ViewContext.setField(object, field, value)`. Renaming a label means
changing N tags in M files. Calling `setField` N times has three problems:
- **Stale offsets.** The second edit of a file is computed against a text the first edit has
  already changed. `ViewEditPlanner` can relocate one edit, but N edits relocated one after the
  other, each followed by a background re-parse, is fragile and slow.
- **Undo.** It would create N journal entries; the user expects one ("Undo Rename tag (5 files)").
- **Partial failure.** If one file is read-only, or the grammar refuses a value, half of the tags
  would be renamed.

**Proposal**: add `ViewEditService.setFields(List<FieldChange>, String description)` on the
application side. Plugin APIs do not change.
1. **Group** the changes by text.
2. **Compute** every edit against the same parsed snapshot (`Text.content()`), each with its object's
   grammar's `ObjectEditor`.
3. **Check** that the edits of each text do not overlap.
4. **Preflight** with `ViewEditPlanner.prepare(text, edits)` for every text: writable, still current
   or relocatable, no new parse errors. **If anything fails, nothing is applied**, and the outcome
   lists what failed.
5. **Apply** each document's edits as one change, all inside `project.journal().grouped(description, …)`:
   one undo entry for the whole rename.

The mode calls this through `Workbench.edits()`. Modes are application code, so `ViewContext`
(the plugin API) does not need a batch method now. It could get one later if a plugin view needs
it.

### P2. Renaming a node that is not a leaf is a prefix rewrite

Renaming the node `pos › verb` to `action` rewrites every tag whose path **starts with**
`[pos, verb]`:
- `pos/verb` → `pos/action`
- `pos/verb/transitive` → `pos/action/transitive`

This is computed on `getPath()`, never with string replacement, so `pos/verbal` is untouched. Three
consequences:
- **Spacing is normalised.** A renamed tag is written with its levels joined by `/`, so `pos / verb`
  becomes `pos/action`. Tags that are not renamed keep their spelling.
- **A plain rename only** (decided, §11 Q4). The new name is one level: it may not contain `/`, so
  a rename never moves a subtree. It may not be the name of an existing sibling (`pos › noun`
  when renaming `pos › verb`), so a rename never merges keywords. Both are refused in the dialog,
  with the reason.
- **Moving is a separate action** (§2.5): drag and drop, or Move to…. It is the same prefix
  rewrite, keeping the last level and changing the parent, and it may merge, after confirmation.

### P3. Some tags may not be renamable

A `Tag` from another plugin may not implement `HasFields`, or its `ObjectEditor` may refuse the
value (the tag plugin refuses quotes and line breaks). A file may also be read-only. The preflight
(P1) finds these cases before anything is changed. The dialog then lists them and offers no partial
rename (§11 Q5).

### P4. "Filter by corpus object type" must use capabilities, not plugins

The object a tag is "registered on" is `Text.targetOf(tag)`. It may be empty, when the tag applies
to the whole text. Its "type" must be stable whatever plugin made the object, so the filter
classifies the target by **capability**, in this order:
1. Interlinear sentence, lexical entry, sense, example, media segment, section, text header;
2. any other object: the title of its kind (`Kinds.title`, e.g. "Conll sentence");
3. no target: **Whole text**.

The choice box lists only the types present under the selected node, with their counts. The
classifier is one pure class (`TagTargetTypes`), so a future capability is added in one place.

### P5. One text editor per occurrence does not scale

A label used 500 times would need 500 RichTextFX areas: slow to build, and heavy in memory.
**Proposal**: show the occurrences in a **virtualised `ListView`**, so that only the visible cells
exist. Each cell is a light **read-only snippet**: a `TextFlow`, monospace, with line numbers, the
tag highlighted, and the lines of the object it applies to tinted. It is not an editor: the
occurrences are unmodifiable, and editing happens in File Mode (§2.3). A thin rule separates the
cells, as asked.

### P6. Kept alive while hidden: incremental index

The mode stays in memory when the user is in File Mode, and every keystroke re-parses a text. So
the index of tags is kept **per text**:
- `onTextsChanged(ids)` (from `AbstractMode`) rebuilds only those texts' entries;
- the work is deferred while the mode is hidden;
- the tree is updated in place, **keeping the expanded nodes, the selection and the scroll
  position**.

### P7. Identity of what is selected

After a re-parse all `Tag` objects are new instances. The tree selection is therefore a **path**
(`List<String>`), which stays stable. The selected occurrence is found again with `ObjectSuccessor`,
by text and position.

### P8. What the count means, and what a click on a node shows

The request gives a count for every node, and shows "the tags for which it is the terminal step".
Two cases are ambiguous:
- the count of `pos` when no tag is exactly `pos` but 12 tags are under it. Decided: the subtree
  total (§11 Q1).
- a click on `pos › verb`: does it show the tags exactly `pos/verb`, or also its sub-tags? Decided:
  exactly `pos/verb`, plus an "Include sub-tags" checkbox (§11 Q2). A node without tags of its own
  shows "No tag is exactly pos · 40 in sub-tags", with "Include sub-tags" as a link.

---

## 2. User interface

### 2.1 Layout

```
┌─ toolbar of the window ───────────────────────────────────────────────────────────┐
│ ◀ ▶ │ [File] [Tags]                                                               │
├───────────────────────────────┬───────────────────────────────────────────────────┤
│ [🔍 Filter tags…            ] │ pos › verb   12 occurrences in 4 files            │
│ Sort: (•) A–Z  ( ) Count      │ Before [ 5 ▴▾]  After [ 5 ▴▾]  ☐ Include sub-tags │
│ ▾ pos (40)                    │ Applies to: [All types (12)            ▾]         │
│   ▾ verb (12)                 ├───────────────────────────────────────────────────┤
│       transitive (5)          │ notes.txt:42 · Interlinear sentence       [Edit]  │
│       intransitive (4)        │   40 │ :::                                        │
│   ▸ noun (18)                 │   41 │ a mio-jaflo-m-wi-j                         │
│ ▸ to check (7)                │   42 │ 'I went down in canoe' #"pos/verb"         │
│ ▸ elicitation (3)             │   43 │ :::                                        │
│                               │ ───────────────────────────────────────────────── │
│                               │ session2.md:8 · Lexical entry             [Edit]  │
│                               │    7 │ <jaflo> {canoe #"pos/verb"}                │
│                               │ ───────────────────────────────────────────────── │
├───────────────────────────────┴───────────────────────────────────────────────────┤
│ status line                                          notes.txt:42   AI: off       │
└───────────────────────────────────────────────────────────────────────────────────┘
```

A `SplitPane` (default 25 % / 75 %, divider position remembered) contains:
- **left**: a filter field, a sort choice, and the **tag tree**;
- **right**: a header (the selected path as a breadcrumb, its counts), the **toolbar** (the two
  spinners, Include sub-tags, the Applies to filter), and the **occurrence list**.

### 2.2 The tag tree

- **Structure.** One node per level, children sorted A–Z, or by count with ties A–Z. The sort is a
  choice, remembered (§12).
- **Label.** `label (n)`; the count is drawn muted.
- **Tooltip.** For example "3 tags pos/verb · 9 in sub-tags".
- **Nodes that are both kinds.** A node can be a path prefix and a tag of its own, e.g. `pos/verb`
  with `pos/verb/transitive` under it. Nothing distinguishes the two in the label; the tooltip and
  the occurrence list show both.
- **Filter field.** It keeps the nodes whose path contains the typed text (case-insensitive), with
  their ancestors, and expands them. Esc clears it.
- **Context menu**: Rename…, Move to…, Expand all below, Collapse all below, Copy keyword (e.g. `pos/verb`).
- **Keyboard.** F2 renames. F6 moves. Enter or → expands. Tab moves to the occurrence list.
- **Empty states.**
  - No tags in the project: "No tags in the project. Write #"keyword" in a note to tag it." The
    sentence names the active tag notation only if the tag plugin is installed (P4: no dependency
    on it otherwise).
  - No match for the filter: "No tag matches 'xyz'".

### 2.3 The occurrence list

- **Order and grouping.** By file (file list order), then by position. Each file starts with a
  **header row** "notes.txt — 5 occurrences" (suggestion 7). A bar above the list repeats the file
  of the topmost visible row, so the current file stays visible while scrolling: a "sticky"
  header that a virtualised `ListView` cannot do natively. The list items are a sealed
  `OccurrenceRow` (`FileHeader` | `Occurrence`).
- **Header of each cell**: `file:line`, the type of the target (P4) with its label shortened, e.g.
  "Interlinear sentence · a mio-jaflo-m-wi-j", the other tags of the same target as small badges,
  and an **[Edit]** button.
- **Body of each cell**: N lines before and M lines after the line of the tag.
  - The lines are cut at the text's start and end, never at a section boundary.
  - Long lines wrap.
  - Line numbers are drawn muted.
  - The tag uses the `tagColor` highlight setting.
  - The target object's lines are tinted lightly, so the user sees what the tag applies to (§12).
- **Edit.** **[Edit]**, a double-click, or Enter on a selected cell calls
  `navigator.showInEditor(Focus.of(tag))`. This switches to File Mode with the file open and the tag
  selected and scrolled into view, and records a Back step: ◀ returns to the tag browser with the
  same node and occurrence selected.
- **Keyboard.** ↑/↓ moves through the occurrences. The selected occurrence is the mode's **focus**
  (`Focus.of(tag)`), so File › Save current file, Close file and Edit › Play media segment act on
  it.
- **Spinners.** Before and After, range 0–50, default 5. The cells update in place, with no
  re-query. The values are remembered in the settings (§11 Q7).
- **Include sub-tags** (§11 Q2). When checked, a node shows the occurrences of its whole subtree,
  and each cell header shows the full keyword.
- **Applies to** (P4). Choices "All types (n)", then "Interlinear sentence (n)"… Choosing one filters
  the list. The header then says "12 occurrences · 5 shown".

### 2.4 Rename

1. **Rename…** (F2 on a node, the context menu, or Tag Browser › Rename…) opens a dialog. The field is
   prefilled with the node's level (`verb`); the full path is shown for reference.
2. **Live preview** as the user types: "Renames 12 tags in 4 files: pos/verb → pos/action,
   pos/verb/transitive → pos/action/transitive…".
3. **Validation.** The Rename button stays disabled, with the reason shown under the field, while
   the name:
   - is empty or unchanged;
   - contains `/` ("A rename stays at the same level");
   - is the name of an existing sibling ("pos › noun exists");
   - contains characters the grammar refuses. These come from the grammar's own check, run as a dry
     run (P1, P3): with the tag plugin, quotes and line breaks.
4. **Rename** applies the batch.
   - **Success.** One journal entry, "Rename tag pos/verb → pos/action (4 files)". The status line
     says "Renamed 12 tags in 4 files (unsaved)" with a **Save all** link. Nothing is saved
     automatically (Q9 of `MODE_PLAN.md`).
   - **Failure.** If the preflight fails, the dialog stays open with the reasons, for example
     "notes.txt is read-only", "2 tags of plugin X cannot be edited". Nothing is changed.
5. **Afterwards.** The tree re-selects the renamed node (`pos › action`) once the texts are parsed
   again.

### 2.5 Move by drag and drop

Dragging a node of the tree onto another node changes the path of every tag it holds. The text is
changed, as for a rename (P1): one batch edit, all or nothing, one undo entry.

**What moves**
- **A non-terminal node** (one with sub-tags) moves with its whole subtree. Dragging `pos › verb`
  onto `lexical` gives `pos/verb` → `lexical/verb` and `pos/verb/transitive` →
  `lexical/verb/transitive`.
- **A terminal node** (a tag with no sub-tags) moves alone: `pos/verb/transitive` dropped on `syntax`
  gives `syntax/transitive`.
- **A node that is both** (tags `pos/verb` and `pos/verb/transitive`) moves as it is seen: with its
  whole subtree (decided).
- **To the top level.** Drop on the empty area below the last node, or on a "Top level" row shown at
  the top of the tree while dragging: `pos/verb` → `verb`.
- The dragged node keeps its own name; only its parent changes. Renaming stays a separate, plain
  action (§2.4).

**Refused drops.** The cursor shows "no drop" and a tooltip gives the reason:
- on the node itself or on its current parent (nothing would change);
- on one of its own descendants (`pos` onto `pos › verb`);
- when some of the tags cannot be changed (P3). This is found when the drop is confirmed, since the
  check needs the edit service, and the confirmation dialog lists the obstacles.

**Merge (decided).** If the target already has a child with the dragged name (`lexical › verb`
exists when dropping `pos › verb` on `lexical`), the drop **merges** the two subtrees. Dragging is
the explicit way to merge; Rename never merges. The confirmation says so: "lexical/verb exists: its
7 tags and these 12 will share the keyword". Children with the same name merge too, recursively,
for example `…/transitive` with `…/transitive`. Merging is exact: `Verb` does not merge with
`verb`.

**Confirmation (decided).** A drop opens a small dialog. A drag can be accidental and can touch many
files. The dialog shows:
- the summary: "Move 12 tags in 4 files: pos › verb → lexical › verb";
- the first rewrites (`pos/verb/transitive → lexical/verb/transitive`, …, "and 9 more");
- the merge, if any;
- the obstacles, if any (then only Cancel).

The buttons are **Move** (default) and **Cancel**; Esc cancels. After the move:
- the status line says "Moved 12 tags in 4 files (unsaved)" with a **Save all** link;
- Edit › Undo shows "Undo Move tag pos/verb → lexical/verb (4 files)";
- the tree selects the moved node once the texts are parsed again.

**Drag feedback** (standard tree drag and drop):
- the dragged node's label follows the pointer, with its count ("verb (12)");
- the node under the pointer is highlighted when the drop is allowed;
- hovering a collapsed node for 0.7 s expands it;
- the tree scrolls when the pointer nears its top or bottom edge;
- Esc during the drag cancels it.

Nodes are only dropped *on* a node, never *between* nodes, because the tree is sorted and there is no
manual order. A filtered tree accepts drops too: the target is the node under the pointer.

**Keyboard and accessibility.** "Move to…" (context menu, Tag Browser menu, shortcut F6) does the
same without a mouse:
1. A dialog lists the possible parents as a tree, with a "Top level" entry and a filter field. The
   refused targets are disabled.
2. The same confirmation follows.

Drag and drop is never the only way to move.

### 2.6 The mode's menu: "Tag Browser"

The menu contains:
- Rename tag… (F2)
- Move to… (F6)
- Expand all, Collapse all
- Sort by name / Sort by count (radio items)
- Include sub-tags (check item)
- Edit occurrence in File Mode (Enter)
- Copy keyword
- Copy occurrences (the listed ones, as text: `file:line  keyword  context line`)
- Export occurrences as CSV… (suggestion 5): keyword, file, line, target type, target label, line
  of the tag, and the context lines, UTF-8 with a header row

There is no accelerator that collides with the window menus (the `ModeHost` check).

### 2.7 "Show in Tag Browser" from any view (suggestion 2)

The tag badges (`TagBadges`) shown by every built-in view get a context menu item: "Show all
#"pos/verb" tags". Mode-agnostic design:
- **matchbox-grammar-fx**: `ViewContext` gets a default method
  `default void browse(CorpusObject object) {}`, meaning "show this object where all objects like
  it are listed". The default does nothing, so the addition is backward compatible. `TagBadges`
  offers the item only if the context can browse (`default boolean canBrowse(CorpusObject o)`,
  false by default).
- **matchbox-gui**: `ModeDescriptor` gets a `Predicate<CorpusObject> browses`, so the mode is
  known without creating it. The Tag Browser's descriptor says `o -> o instanceof Tag`.
  `Navigator.browse(o)` picks the first mode that browses the object, records a Back step, shows
  the mode and calls `reveal(Focus.of(o))`. `WorkbenchViewContext` implements `browse` and
  `canBrowse` with it.
- Plugins' views can call `browse` too, and no plugin knows which mode answers.

### 2.8 Toolbar button and registration

- `ModeDescriptor("tag", "Tags", "Tag Browser", "Browse, count and rename all the tags of the
  corpus", Shortcut+2, TagBrowserMode::new)`, placed after File in `Modes.builtIn()`. The button
  sits next to **File**.
- `reveal(focus)`: when the focused object is a `Tag`, select its node and its occurrence. This
  makes Back/Forward and `ViewContext.select(tag)` work.
- **Accessibility.** Every control has a text or tooltip. The counts are part of the tree cell's
  accessible text. Every action is reachable from the keyboard.

---

## 3. Model: pure classes, no JavaFX controls (`gui.mode.tagBrowser`)

| Class | Role |
|---|---|
| `TagOccurrence` | record: `Tag tag`, `TextId text`, `List<String> path`, `Optional<CorpusObject> target`, `String targetType` |
| `TagTargetTypes` | classifies a target by capability, then by kind, else "Whole text" (P4) |
| `TagIndex` | occurrences per text; `update(TextId, Optional<Text>)`, `all()`, `under(path, includeSubTags)`; built from `Text.objectsOf(Tag.class)` and `Text.targetOf` |
| `TagTree` | immutable tree of `TagNode(String level, List<String> path, int own, int total, List<TagNode> children)` built from the index; sorting; `filter(text)`; `find(path)` |
| `ContextExcerpt` | for a text and a span: the lines `before`/`after`, line numbers, offsets of the tag and of the target inside the excerpt |
| `OccurrenceRow` | sealed: `FileHeader(TextId, count)` | `Occurrence(TagOccurrence)`; built from the filtered occurrences |
| `OccurrenceExport` | the occurrences as text lines or CSV (escaping, header) |
| `TagPathEdit` | the path rewrites, without applying anything. `rename(path, newLevel)`: plain, refused for `/`, an existing sibling or an empty name. `move(path, newParent)` (empty parent = top level): refused onto the node itself, its parent or its descendants; reports the merges. Both give the `FieldChange`s, the rewritten keywords (for the preview), the merges and the tags that cannot be changed |

Application side (`gui.edit`):

| Class | Role |
|---|---|
| `FieldChange` | record: object, field name, new value |
| `ViewEditService.setFields(changes, description)` | batch edit, all or nothing, one journal entry (P1) |
| `BatchEditPlanner` | pure part of the batch: grouping, per-snapshot edits, overlap check, preflight; tested without JavaFX |

---

## 4. View classes (`gui.mode`, `gui.mode.tagBrowser`)

- `TagBrowserMode extends AbstractMode`:
  - builds the split pane;
  - `onTextsChanged` → `TagIndex.update` → `TagTree` → refresh that keeps the expansion, selection
    and scroll (P6, P7);
  - `onProjectChanged` → rebuild;
  - sets the focus;
  - `reveal`;
  - `menu()`.
- `TagTreeCell`: label and muted count, tooltip, context menu.
- `OccurrenceCell` (a `ListCell<TagOccurrence>`): header (location, type, badges, Edit) and a
  `TextFlow` snippet built from a `ContextExcerpt`.
- `RenameTagDialog`: field, live preview from `TagPathEdit`, errors from the preflight.
- A FXML file is not needed: the layout is built in code, as it is mostly dynamic. It may be added
  if it grows.

---

## 5. Data flow

```
corpus change ─▶ AbstractMode.onTextsChanged(ids) ─▶ TagIndex.update(id, text)
                                                    └▶ TagTree.of(index) ─▶ tree refresh
selection (path) ─▶ index.under(path, sub) ─▶ filter by type ─▶ ListView items
rename / drop / Move to… ─▶ TagPathEdit ─▶ dialog preview ─▶ ViewEditService.setFields (all-or-nothing,
          journal.grouped) ─▶ documents ─▶ re-parse ─▶ corpus change ─▶ …
edit button ─▶ Navigator.showInEditor(Focus.of(tag)) ─▶ File Mode (Back returns)
```

The text stays the only source of truth: the browser never keeps or changes tags of its own.

---

## 6. Settings

New `SettingsData` fields:
- `tagBrowserContextBefore`, `tagBrowserContextAfter` (default 5, range 0–50);
- `tagBrowserSort` (`name` | `count`);
- `tagBrowserIncludeSubTags` (default false).

Each is validated in `validated()` and exposed as a `UserSettings` property. The divider position
is per session (not saved).

---

## 7. Implementation steps

1. **Batch edit.** `FieldChange`, `BatchEditPlanner`, `ViewEditService.setFields` (all-or-nothing,
   `journal.grouped`), with tests: two tags in one file, tags in two files, a read-only file, a
   refused value, overlap, undo in one step.
2. **Model.** `TagOccurrence`, `TagTargetTypes`, `TagIndex`, `TagTree`, `ContextExcerpt`,
   `TagPathEdit`, with tests.
3. **Mode.**
   - `TagBrowserMode`, `TagTreeCell`, `OccurrenceCell`;
   - the toolbar (spinners, Include sub-tags, Applies to), the filter field and the sort choice;
   - registration in `Modes`, so the button appears next to File;
   - the settings.
4. **Rename.** `RenameTagDialog`, the context menu, F2, and the status line message with Save all.
5. **Navigation.** The Edit button, double-click and Enter, `reveal`, the focus, the menu.
6. **Show in Tag Browser.** `ViewContext.browse`/`canBrowse` (default methods), the badge context
   menu in `TagBadges`, `ModeDescriptor.browses`, `Navigator.browse`.
7. **Export.** `OccurrenceExport`, Copy occurrences, Export as CSV… (a file chooser).
8. **Move.**
   - drag and drop in the tree (feedback, refused targets, auto-expand, auto-scroll, a "Top level"
     row);
   - the Move to… dialog (F6);
   - the confirmation with preview and merge;
   - the status line message.
9. **GUI tests** (TestFX, `MainWindowFixture`): §9.
10. **Documentation.** CLAUDE.md (modes, batch edit), README ("Tags" section: the browser), and this
   plan's status.

---

## 8. What is not changed

- `Tag`, `ObjectEditor` and the tag plugin stay as they are. The only plugin-facing change is the
  two default methods of `ViewContext` (step 6), which are backward compatible.
- File Mode and its Tag tab are unchanged. The Tag column still renames one occurrence.

---

## 9. Tests

Pure classes:
- **`TagTree`**:
  - counts, own and total;
  - a node that is both a prefix and a tag;
  - sorting;
  - filter with ancestors;
  - `pos/ verb` and `pos/verb` in the same node.
- **`TagIndex`**: updating one text leaves the others unchanged; a removed text drops its tags.
- **`TagTargetTypes`**: each capability, an unknown kind, and the whole text.
- **`ContextExcerpt`**:
  - lines at the start and end of the text;
  - 0 lines;
  - a tag on the first or last line;
  - CRLF.
- **`TagPathEdit`**:
  - move: a subtree, a terminal node, a mixed node, to the top level;
  - move refused onto the node itself, its parent or a descendant;
  - merge detection, recursive (`…/transitive` with `…/transitive`), exact case;
  - rename: leaf and prefix;
  - `/` and an existing sibling refused;
  - `pos/verbal` untouched;
  - the plan for 2 files.
- **`BatchEditPlanner` / `setFields`**: see step 1.
- **`OccurrenceRow`**: header rows and counts per file, in file order.
- **`OccurrenceExport`**: CSV escaping (quotes, commas, line breaks), header, UTF-8.

GUI (TestFX, headless):
- the Tags button and Shortcut+2 show the mode, and its menu replaces "File Mode";
- the tree shows `pos (n) › verb (n) › transitive (n)` for a 2-file project;
- selecting a node lists its occurrences, and the spinners change the number of lines;
- Applies to filters the list;
- Rename `verb` → `action`: both files change, the journal shows one entry, Undo restores both,
  and the status line offers Save all;
- Edit opens File Mode on the tag, and Back returns to the same node;
- the tree filter keeps ancestors; sorting by count reorders the nodes;
- "Show all #"…" tags" on a badge in File Mode's detail view opens the Tag Browser on that node, and
  Back returns to File Mode;
- dragging `pos › verb` onto `lexical` (TestFX `drag(...).dropTo(...)`) and confirming: both files
  change, one undo entry, the tree shows `lexical › verb (n)`; Undo restores `pos › verb`;
- a drop on a descendant is refused, and nothing changes.

---

## 10. Files

New:
- `gui/mode/TagBrowserMode.java`;
- `gui/mode/tagBrowser/`: `TagOccurrence`, `TagTargetTypes`, `TagIndex`, `TagTree`, `TagNode`,
  `ContextExcerpt`, `TagPathEdit`, `OccurrenceRow`, `OccurrenceExport`, `TagTreeCell`,
  `OccurrenceCell`, `RenameTagDialog`, `MoveTagDialog` (Move to… parent chooser),
  `PathEditConfirmation` (preview and confirm a move or a merge);
- `gui/edit/`: `FieldChange`, `BatchEditPlanner`;
- tests.

Changed:
- `Modes`, `ModeDescriptor` (`browses`), `Navigator` (`browse`), `WorkbenchViewContext`,
  `ViewEditService`;
- matchbox-grammar-fx: `ViewContext` (`browse`, `canBrowse`, default methods), `TagBadges`
  (context menu);
- `SettingsData`/`UserSettings`;
- CLAUDE.md, README.

---

## 11. Questions (recommendation first)

Decided with the user:
- **Q1. Count**: the total of the node's subtree, `verb (12)`; the tooltip gives the split.
- **Q2. Click on a node**: the tags whose last level is the node, plus an **Include sub-tags**
  checkbox (remembered) for the whole subtree.
- **Q4. Rename**: a plain rename only. No `/`, and no existing sibling name (no move, no merge).
- **Q5. Obstacles**: all or nothing; the dialog lists them.
- **Q3. Case**: exact grouping; `Verb` and `verb` are separate nodes.
- **Q8. Names**: id `tag`, button **Tags** (next to File), menu **Tag Browser**, Shortcut+2.
- **Q9. Target tint**: yes, the target object's lines are tinted in each snippet.
- **Q10. Drag and drop** (requested after the first version of the plan): moving a node changes the
  path of all its tags. Merging with an existing node of the same name is allowed, with
  confirmation. Every drop is confirmed with a preview. A node with both its own tags and sub-tags
  moves with its whole subtree (§2.5).
- **Suggestions accepted** (§12): 1 (filter and sort), 2 (Show in Tag Browser), 5 (export),
  7 (file headers). They are in the steps (§7).

Assumed (as in the request; say if not):

**Q6. "Applies to" filter: single choice or several?** *Recommendation*: a single choice with
"All types" (a `ChoiceBox`, as the plugin filter of the errors tab). A multi-choice menu can come
later if needed.

**Q7. Context lines: two spinners or one?** The request asks for two, default 5, range 0–50.
*Recommendation*: two, remembered in the settings.

---

## 12. Suggestions beyond the request (1, 2, 5, 6 and 7 accepted; the others are for later)

1. **Filter field and sort choice** above the tree (included in §2.2): with hundreds of tags, a tree
   alone is hard to scan.
2. **"Show in Tag Browser"** on the tag badges of every view (context menu of a badge). It would
   take the user from an IGT in File Mode to all the occurrences of its tag, and Back would return.
   Architectural note: `ViewContext.select(tag)` is not enough, because it reveals in the *active*
   mode (File Mode). The badges live in matchbox-grammar-fx, which must not know the modes. So this
   needs a generic, mode-agnostic addition to `ViewContext`, e.g. `default void browse(CorpusObject
   o)` ("show this object where all objects like it are listed"), routed by the `Navigator` to the
   mode that declares it can browse that capability. It is a small, backward-compatible API
   addition, to decide separately.
3. **Remove tag** (context menu): delete all occurrences of a keyword, as one undo entry, with a
   preview, using the same batch edit. It needs `ObjectEditor.removeField` or a removal edit; to be
   designed with the tag plugin.
4. **Near-duplicates hint**: in the tree, flag keywords that differ only by case or by accents
   (`Verb`/`verb`), with an explicit "Merge into…" action (rename refuses existing siblings, §11
   Q4).
5. **Export**: copy the selected occurrences as text, or export a CSV (keyword, file, line, target
   type, target label, context) for analysis outside Matchbox.
6. **Move / merge**: now in the plan as drag and drop and Move to… (§2.5, Q10). A "Merge into…"
   for differently named nodes (e.g. `Verb` into `verb`) remains for later.
7. **Group the occurrences by file**, with file headers that stay visible while scrolling, for labels
   used in many files.

---

## 13. Differences between the plan and the code

- **Dialogs**: one class, `TagDialogs` (rename, Move to… parent chooser, move confirmation),
  instead of `RenameTagDialog`, `MoveTagDialog` and `PathEditConfirmation`.
- **Rename**: the preview updates as the user types; the dry run of the batch edit (read-only
  files, refused values) runs when Rename is pressed, not at every keystroke (it parses the
  files). The dialog stays open with the obstacles.
- **Order of the occurrences**: by file path (`TextId` order), then position — not the order of the
  project's file list.
- **Sticky file header**: a bar above the list repeats the header of the file of the topmost
  visible row; file header rows are in the list.
- **Top level drop**: a dashed "Drop here to move to the top level" strip appears above the tree
  while dragging; the empty space below the last node also accepts the drop.
- **F2 / F6**: also handled by a key filter on the tree, which consumes F2 itself (its edit key),
  so the menu accelerators alone did not work while the tree had the focus.
- **Badge menu** ("Show all … tags"): opened on a secondary click, not on the context-menu event
  (more robust, and needed by the headless tests).
- **Edit** selects the occurrence first, so that Back returns to it.
- **`AbstractMode`** fix found by the GUI tests: a mode created after the project is open (the
  usual case, modes are created lazily) now builds its content on its first activation.
- **GUI tests**: drag and drop cannot run headless (Monocle has no native drag and drop): the two
  drag tests are skipped there and run with a display; Move to… (F6) is tested headless and shares
  the planning, confirmation and batch edit with the drop.
- Not done: the tree is not virtualised further than `TreeView` itself; no accessibility audit
  beyond texts, tooltips and keyboard access.
