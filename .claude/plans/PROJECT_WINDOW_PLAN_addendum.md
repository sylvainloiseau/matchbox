# Plan: Project properties — "Plugin parameters" section, no Fileset section; File Mode file tree and directory settings

Status: implemented (parts A and B). Addendum to `PROJECT_WINDOW_PLAN.md` (implemented). What
differs from this plan: §C.
Part A (§0–§12): Project properties; all questions decided, all suggestions accepted.
Part B (§B0–§B12): File Mode tree and directory settings; all questions decided, all suggestions
accepted.

## 0. Goal

- The display styles of a plugin are edited in a new **Plugin parameters** section, below File
  types: one row per plugin, with its **Display…** button enabled when the plugin is checked in at
  least one file type. The button leaves the grammar list of a file type.
- The **Fileset** section (list of files and directories, Add file…, Add directory…, Remove) is
  removed: files are added with the Current project menu and listed in File Mode.

Out of scope: other per-plugin parameters (the section is named so that they can come later),
changes to the display style dialog itself.

## 1. Architectural assessment

**P1. The Display… button is hidden and misleading.** In `ProjectPropertiesDialog.GrammarCell`
the row is `HBox(check, label, spacer, display)`; the label's preferred width is the whole
"name — description" text, so the `ListView` gets a horizontal scroll bar and the button is off
screen. And a button on the row of a grammar *in a file type* suggests a per-file-type style,
whereas styles are per project (decision of the main plan, P6).
→ Remove the button from `GrammarCell`; make its label shrink with an ellipsis (`setMinWidth(0)`,
`HBox.setHgrow`, full text in a tooltip), so that the grammar list never scrolls sideways.

**P2. "Checked in at least one file type" must follow the working copy.** The dialog edits a
`ProjectEdit`; until Save the project's file types are unchanged. The enabled state of each
Display… button is computed from the drafts (`FileTypeDraft.getGrammars()`), and refreshed when a
grammar is checked or unchecked, and when a file type is added or removed.
→ `ProjectEdit.usedGrammarIds()` (pure, ordered set of the ids checked in any draft); the dialog
refreshes the Plugin parameters list after each of these actions (the places that already call
`refreshGrammarRows()`, plus Add/Remove file type).

**P3. Removing the Fileset section removes the only way to…**
- **remove a directory entry**: "Remove file from project" (`Project.removeFile`) drops the
  document but not the directory it came from, so the file comes back the next time the project
  is opened; "Remove all files from project…" is the only other way out. → Decided: "Remove directory from project…" in the Current project menu.
- **add several files at once**: the section's chooser was multi-select, the menu's is not.
  → "Add file…" becomes multi-select (`showOpenMultipleDialog`).
- **see missing files**: files that cannot be read are already reported and dropped when the
  project is loaded, so the "(missing)" marker only showed files deleted while the project was
  open. No replacement needed.
- The load-problem message says "Correct them in Current project › Project properties…", which is
  no longer true for files. → "Correct the file types in Current project › Project properties…;
  add missing files again with Current project › Add file…" (worded from what failed).

**P4. `ProjectEdit` keeps its paths.** The dialog no longer changes them, so `applyTo` finds them
unchanged (no-op) and `droppedDocuments` is always empty: the dialog stops calling
`UnsavedChangesDialog` on Save. `ProjectEdit`'s API is not changed (its tests and future dialogs
may use paths).

**P5. Styles of grammars that are not installed** are kept in the project (main plan, P9) but
would appear in no list. → Decided: a greyed row with Forget.

## 2. User interface

```
┌ Project properties ────────────────────────────────────────────────────┐
│ Project name         [ Fieldwork 2025                              ]   │
│ Project description  [ …                                           ]   │
│ ┌ File types: patterns of file names and the grammars applied ──────┐  │
│ │ All files        │ File type name: [All files]                    │  │
│ │ Markdown files   │ Matching files: [.+]                           │  │
│ │                  │ Grammars:                                      │  │
│ │                  │  [x] 1. Notebook — lexical entries and IG…     │  │  ← ellipsis, tooltip
│ │                  │  [x] 2. Tags — inline #"keyword" tags          │  │
│ │ [Add] [Remove]   │  [ ]    CoNLL — dependency sentences           │  │
│ └───────────────────────────────────────────────────────────────────┘  │
│ ┌ Plugin parameters ────────────────────────────────────────────────┐  │
│ │ Notebook   lexical entries and IGT     [Aa] All files [Display…]  │  │
│ │ Tags       inline #"keyword" tags      [Aa] All files [Display…]  │  │
│ │ Markdown   sections and metadata       [Aa] Markdown… [Display…]  │  │
│ │ CoNLL      dependency sentences        [Aa] not used  [Display…]  │  │  ← disabled
│ │ Old plugin (not installed)             [Aa]           [Forget]    │  │  ← not installed
│ │ The display of a plugin is the same in every file type using it.  │  │
│ └───────────────────────────────────────────────────────────────────┘  │
│ ┌ AI assistant ─────────────────────────────────────────────────────┐  │
│ │ [x] Allow cloud AI providers for this project                     │  │
│ └───────────────────────────────────────────────────────────────────┘  │
│                                                       [Save] [Cancel]  │
└────────────────────────────────────────────────────────────────────────┘
```

- Rows: installed plugins (`PluginManager.getAvailableGrammars()`), in the order of the first file
  type using them, then the unused ones by name. A `ListView` (or a `VBox` if there are few
  plugins), no horizontal scrolling: name and description shrink with an ellipsis, the
  "Aa" swatch, the "used in" text and the button keep their size and stay right-aligned.
- **Aa** swatch (S1): the plugin's resolved style (project style over plugin default), updated
  after the Display dialog; tooltip "Display of all the objects of this plugin; some kinds may
  have their own".
- **Used in** (S2): the names of the file types checking the plugin, "not used" otherwise; full
  list in a tooltip.
- Display… disabled with the tooltip "Check this plugin in a file type first". Its styles are
  kept and come back when the plugin is checked again.
- Keyboard: the buttons are focus traversable; the list keeps Tab order name → button.
- The help line under the list says the style is shared by every file type.

## 3. Model

| Class | Role |
|---|---|
| `ProjectEdit.usedGrammarIds()` | ids of the grammars checked in at least one file type draft, in order of first use |
| `ProjectEdit.fileTypesUsing(grammarId)` | names of the drafts checking it (S2) |

## 4. View classes

| Class | Change |
|---|---|
| `ProjectPropertiesDialog` | `filesetSection()` and `addPath` removed; `pluginParametersSection()` added after `fileTypesSection()`; `GrammarCell` loses its button, label shrinks; `refreshPluginRows()` called after check/uncheck, Add/Remove file type and after the Display dialog; Save no longer checks dropped documents |
| `ProjectPropertiesDialog.PluginRow` (cell) | name, description, swatch, used-in, Display… (or Forget for a plugin that is not installed) |
| `MainController` | "Add file…" multi-select; load-problem message reworded; "Remove directory from project…" (choice dialog of the project's directories) |

## 5. Data flow

Check a grammar in a file type → `FileTypeDraft.setUsed` → `refreshGrammarRows()` and
`refreshPluginRows()` → its Display… button becomes enabled → `DisplayStyleDialog` (unchanged) →
`ProjectEdit.setDisplayStyles` → swatch refreshed → Save → `ProjectEdit.applyTo` (styles, file
types; paths unchanged) → project definition changed → editor re-highlighted.

## 7. Implementation steps

1. `ProjectEdit.usedGrammarIds()`, `fileTypesUsing()`; tests.
2. Plugin parameters section with the "Aa" swatch (S1) and "Used in" (S2); Display… removed
   from `GrammarCell`; label ellipsis.
3. Fileset section removed; Save without dropped-documents check; "Add file…" multi-select (S4);
   load-problem message.
4. Forget row for plugins not installed; "Remove directory from project…" (`WorkbenchCommands.removeDirectory`, menu item bound to "the project has a directory").
5. Non-modal `show()` test hook and `ProjectPropertiesDialogTest` (S3); CLAUDE.md, README.md;
   pointer in `PROJECT_WINDOW_PLAN.md`; plan status.

## 8. What is not changed

`DisplayStyleDialog`, `DisplayStyles`, the `.mbx` format, `ProjectEdit`'s paths API, the
Current project menu items (apart from the new "Remove directory from project…"), File Mode.

## 9. Tests

- `ProjectEditTest`: `usedGrammarIds` follows `setUsed`, added and removed drafts; order of first
  use; `fileTypesUsing`.
- GUI (S3): the dialog is modal (`showAndWait`), so the GUI tests do not drive it today. A
  package-private `show()` (non-modal, returns the stage) lets `TestFX` check: no Fileset section;
  a Plugin parameters row per installed plugin; Display… of an unchecked plugin disabled, enabled
  after checking it in a file type, disabled again after unchecking; no horizontal scroll bar in
  the grammar list at the default size.

## 10. Files

Changed: `ProjectEdit`, `ProjectEditTest`, `ProjectPropertiesDialog`, `MainController`,
`WorkbenchCommands` (multi-select add, `removeDirectory`), `MainView.fxml`, CLAUDE.md, README.md,
`PROJECT_WINDOW_PLAN.md` (pointer to this addendum). New: `ProjectPropertiesDialogTest` (S3).

## 11. Questions

### 11.1 Decided with the user

- Display styles are edited in a **Plugin parameters** section below File types, one row per
  plugin; Display… is enabled when the plugin is checked in at least one file type (of the working
  copy).
- The **Fileset** section is removed.
- Styles of plugins that are not installed: a greyed row "(not installed)" with a **Forget**
  button removing them (P5).
- Removing a directory: **Current project › Remove directory from project…** lists the project's
  directories; the chosen one leaves the project, its files stay on disk (P3). Disabled when the
  project has no directory; unsaved files of the directory go through `UnsavedChangesDialog`.

- Suggestions S1–S4 are all accepted and part of the steps (§7).

### 11.2 Open

None.

## 12. Suggestions (all accepted)

- **S1** "Aa" swatch of the plugin's style in each row.
- **S2** "Used in: All files, Markdown files" in each row.
- **S3** A non-modal `show()` test hook for `ProjectPropertiesDialog` and a GUI test of the new
  section.
- **S4** "Add file…" accepts several files (replaces the multi-select of the removed section).

---

# Part B — File Mode file tree and directory settings

## B0. Goal

- The file list of File Mode becomes a **tree**: each directory added to the project is a node
  grouping its files, with its subdirectories as subnodes; files added one by one are top-level
  nodes, never grouped (even two files of the same directory).
- Right-click on a **top directory node** (a directory added to the project): **Remove this
  directory from the project** and **Edit directory settings…**.
- **Directory settings** (modal dialog): glob patterns of the files to include and to exclude,
  and "Recursively include sub-directories". Recorded in the project file; applying them rescans
  the directory.

Out of scope: watching the disk for new files (S8 offers a manual rescan), relative paths in the
project file, drag and drop in the tree.

## B1. Architectural assessment

**B-P1. Directories lose their recursion when the project is reopened.** "Add files in
directory…" calls `Project.addDirectory(path, true)` (recursive), but `ProjectXMLSerializer.load`
calls `addDirectory(String)` → `addDirectory(path, false)`: after reopening, the files of the
subdirectories are gone. The `<directory>` element records nothing but the path.
→ `DirectorySettings` stored on each `<directory>`; loading and adding use them.

**B-P2. Which files a directory gives is decided by a global setting.** `Project.addDirectory`
keeps the files accepted by `DocumentOptions.acceptsFile` (the `fileExtensions` setting, Settings
› Files). With per-directory patterns, the project file says which files belong to the project,
so the same project gives the same files on every computer. → Decided: the global setting only
pre-fills the include patterns of a new directory; afterwards the directory's patterns decide.

**B-P3. The project keeps no tree.** `Project` has `filesAndDirectories` (definition entries, in
order) and `filesByDir` (directory → files found), but File Mode shows the flat
`getDerivedFileList()`.
→ A pure `FileTree` built from the definition entries and the documents: a top node per entry in
definition order; under a directory, subdirectory nodes (relative path segments) and files,
directories first, each group sorted by name. Rebuilt on changes of `getDerivedFileList()` and of
the definition (a new observable `definitionVersion` or listening to `definitionChangedProperty`
is not enough: it stays true; → `Project.definitionChanges()`, an `InvalidationListener` hook
fired by every definition change).

**B-P4. Removing one file of a directory does not last** (addendum P3): the file comes back at
the next open. → Decided: removing a single file of an added directory is **not allowed**. "Remove
file from project" (Current project menu, File Mode's Close button, file context menu) is
disabled for such a file, with the tooltip / status message "This file comes from the directory
…: exclude it in Directory settings". `WorkbenchCommands` needs `isInDirectory(document)`
(`Project.directoryOf(Path)`), and its `textFocused` binding becomes "a file added alone is
focused".

**B-P5. Applying settings changes the files of the project.** Newly included files are added and
parsed; excluded ones are removed — their unsaved changes go through `UnsavedChangesDialog` first,
and their undo entries are dropped (as for any removed document). The dialog previews the result
(S5) so that nothing surprising happens.

**B-P6. Keyboard access and the window menus.** The context menu is not the only way: the tree
supports the context-menu key / Shift+F10, and the **File Mode** menu gets "Directory settings…"
and "Remove directory from project" acting on the selected top directory (they belong to File
Mode: they act on its tree's selection). The mode-independent **Current project › Remove
directory from project…** (decided in part A) stays: it lists the directories, with the one
selected in File Mode preselected.

**B-P7. Existing projects.** A `<directory>` without settings loads exactly as today:
non-recursive, files accepted by the global extensions. Its settings are written on the next save
(`recursive="false"`, includes from the extensions at load time), so nothing changes silently.

**B-P8. Tests address the list.** `MainWindowFixture.showFile` looks up `.list-cell`s and
`MainWindowTest` uses `queryListView()` on `#fileListView`. → Adapted to tree cells
(`fileTreeView`).

## B2. User interface

### File Mode tree

```
 Files
 [Filter                ]
 ▾ 📁 data/fieldwork            (14)      ← top directory node (added), context menu
     ▾ 📁 2024                  (6)
         day1.txt
         * day2.txt                        ← unsaved: "* ", bold italic (as today)
     ▸ 📁 2025                  (8)
     notes.md
 ▾ 📁 lexicon                   (3)
     ...
   interview.txt   ~/notes                 ← file added alone: name, parent directory greyed
   draft.txt       ~/notes                 ← same directory, not grouped
```

- Directory nodes: icon or "▾/▸", relative name, number of files below in grey; bold when a file
  below is unsaved (S6). Top directory nodes show the directory's path abbreviated (`~/…`),
  tooltip: full path and a summary of the settings ("*.txt, *.md · excludes drafts/** ·
  recursive").
- File nodes: file name; tooltip full path. Selecting a file shows it in the editor (as today);
  selecting a directory leaves the editor as it is.
- Expanded state kept across rebuilds (by path); top directories expanded at first.
- Filter: shows the files whose path contains the text, with their ancestor directories, all
  expanded; cleared filter restores the expanded state.
- Subdirectories with no included file are not shown.
- Context menu of a top directory node: **Edit directory settings…**, **Rescan directory** (S8),
  separator, **Remove this directory from the project**. Of a file node: **Remove file from
  project** (S7; disabled for a file of a directory), **Exclude this file** (S10, files of a
  directory). Subdirectory nodes: **Exclude this sub-directory** (S9).

### Directory settings dialog

```
┌ Directory settings — ~/notes/data/fieldwork ───────────────────────┐
│ Include files matching (one glob per line)                         │
│ ┌───────────────────────────────────────┐                          │
│ │ *.txt                                 │                          │
│ │ *.md                                  │                          │
│ └───────────────────────────────────────┘                          │
│ Exclude files matching                                             │
│ ┌───────────────────────────────────────┐                          │
│ │ drafts/**                             │                          │
│ │ *-old.txt                             │                          │
│ └───────────────────────────────────────┘                          │
│ [x] Recursively include sub-directories                            │
│ Help: *.txt any .txt file · drafts/** everything under drafts ·    │
│       a pattern without "/" matches the file name at any depth     │
│ ┌ Preview: 14 files (2 added, 1 removed) ───────────────────────┐  │
│ │ + 2025/day9.txt                                               │  │
│ │ − drafts/old.txt                                              │  │
│ │   2024/day1.txt …                                             │  │
│ └───────────────────────────────────────────────────────────────┘  │
│ ⚠ Invalid pattern "[a-": unclosed bracket                          │
│                                                     [OK] [Cancel]  │
└────────────────────────────────────────────────────────────────────┘
```

- Opened right after the directory chooser of "Add files in directory…", pre-filled with the
  defaults (includes from the global extensions, recursive); OK adds the directory, Cancel adds
  nothing (decided). Title "Add directory — …" then, "Directory settings — …" afterwards.
- Glob semantics (decided, gitignore-like): a pattern without "/" matches the file name at any
  depth; a pattern with "/" matches the path relative to the directory (`**` any number of
  directories). Implemented with `PathMatcher("glob:…")` on the file name or the relative path.
- Empty include list = every file. Invalid globs are shown under the lists and disable OK.
- The preview (S5) is computed off the JavaFX thread (large directories), debounced, and lists
  added (+) and removed (−) files first.
- OK applies: added files are parsed, removed files leave the project (unsaved ones asked first);
  the project definition is changed (saved with Save project).

## B3. Model (pure)

| Class | Role |
|---|---|
| `model.project.DirectorySettings` (record) | `includes`, `excludes` (lists of globs), `recursive`; `of(fileExtensions)` (default from the global setting: `*.txt`, `*.md`); `validate()` → problems |
| `model.project.DirectoryScanner` | files of a directory for its settings: walks (depth 1 or unlimited), keeps regular files matching an include (or all if none) and no exclude; gitignore-like globs (§B2); sorted |
| `Project.directorySettings(Path)`, `setDirectorySettings(Path, DirectorySettings)` | read; replace and rescan (returns the documents added and removed, for the caller's dialogs); marks the definition changed |
| `Project.addDirectory(Path, DirectorySettings)` | adds a directory with its settings (the `boolean recursive` variants delegate) |
| `Project.directoryOf(Path)` | the added directory a file comes from, empty for a file added alone |
| `Project.rescanDirectory(Path)` | S8 |
| `Project.definitionChanges()` | listeners called on every definition change (B-P3) |
| `gui.mode.fileMode.FileTree` | the tree: `Node` = top directory, subdirectory or file (document), built from definition entries + documents + filter; pure, tested without JavaFX |

`.mbx`:
```xml
<directory path="/home/me/notes/data/fieldwork" recursive="true">
  <include>*.txt</include>
  <include>*.md</include>
  <exclude>drafts/**</exclude>
</directory>
```
No attribute and no child: today's behaviour (B-P7).

## B4. View classes

| Class | Change |
|---|---|
| `FileMode` / `FileMode.fxml` | `TreeView<FileTree.Node> fileTreeView` replaces `fileListView`; cells, context menus, filter, expanded state; File Mode menu items "Directory settings…", "Remove directory from project" |
| `gui.DirectorySettingsDialog` | the dialog of §B2, returns `Optional<DirectorySettings>` |
| `WorkbenchCommands` | `addDirectory` opens the dialog first (decided), `removeFile` only for files added alone (decided); `removeDirectory(Path)`; `editDirectorySettings(Path)` (dialog → `setDirectorySettings` → unsaved files asked) |
| `MainController` | "Remove directory from project…" preselects the File Mode selection |
| `ProjectXMLSerializer` | settings on `<directory>` |

## B5. Data flow

Right-click top directory → Edit directory settings… → dialog (preview by `DirectoryScanner` on a
background thread) → OK → removed documents with unsaved changes → `UnsavedChangesDialog` (Cancel
= nothing applied) → `Project.setDirectorySettings` → documents added (parsed) / removed →
`getDerivedFileList()` and `definitionChanges()` fire → `FileTree` rebuilt, expanded state kept →
title shows "•" → Save project writes the settings.

## B6. Settings

`fileExtensions` (Settings › Files) is kept: default include patterns of a new directory
(decided).
Its label in the Settings dialog becomes "Default file extensions of new directories".

## B7. Implementation steps

1. `DirectorySettings`, `DirectoryScanner` and their tests (glob semantics, recursion, empty
   include, invalid globs).
2. `Project`: settings per directory, `addDirectory(Path, DirectorySettings)`,
   `setDirectorySettings` (added/removed), `rescanDirectory`, `definitionChanges()`; serializer
   (+ legacy `<directory>`); tests (round trip, B-P1 regression: a recursive directory is still
   recursive after reopening).
3. `FileTree` (pure) and tests.
4. File Mode tree: cells, selection → editor, filter, expanded state, context menus, File Mode
   menu items; fixture and tests adapted (B-P8).
5. `DirectorySettingsDialog` with preview; "Add files in directory…" opens it after the chooser;
   Remove directory; "Remove file from project" disabled for files of a directory.
6. S6 (counts, bold), S7 and S10 (file context menu: Remove file / Exclude this file), S8
   (Rescan directory), S9 (Exclude this sub-directory); CLAUDE.md, README.md; plan status.

Part A and part B are independent; part A first (smaller), as planned.

## B8. What is not changed

Corpus, parsing, `TextDocument`, undo journal; files added one by one (no settings); the Settings
dialog apart from one label.

## B9. Tests

- `DirectoryScannerTest`: `*.txt` matches at any depth; `drafts/**` excludes a subtree; exclude
  wins over include; non-recursive ignores subdirectories; empty include = all; invalid glob →
  problem.
- `ProjectTest` / serializer: settings round trip; legacy `<directory>` loads non-recursive with
  the global extensions; recursive directory still recursive after reopening;
  `setDirectorySettings` reports added/removed documents and marks the definition changed.
- `FileTreeTest`: two files added alone in the same directory are two top nodes; subdirectories
  as nodes; order (definition order, then directories first, by name); filter keeps ancestors;
  empty subdirectories hidden.
- GUI: the tree shows a directory node with its subdirectory; selecting a file shows it; the
  context menu of a top directory has the two entries, a subdirectory's does not; Remove this
  directory removes its files (no dialog when nothing is unsaved); "Remove file from project" is
  disabled for a file of a directory and enabled for a file added alone. The settings dialog is modal:
  the same non-modal `show()` hook as part A (S3) lets the tests check the preview and that OK
  rescans.

## B10. Files

New: `DirectorySettings`, `DirectoryScanner`, `FileTree`, `DirectorySettingsDialog`, their tests.
Changed: `Project`, `ProjectXMLSerializer`, `FileMode`, `FileMode.fxml`, `WorkbenchCommands`,
`MainController`, `SettingsDialog.fxml` (label), `MainWindowFixture`, `MainWindowTest`,
CLAUDE.md, README.md.

## B11. Questions

### B11.1 Decided with the user

- Tree in File Mode; directories added to the project group their files, subdirectories are
  subnodes; files added one by one are never grouped.
- Context menu of a top directory node: "Remove this directory from the project", "Edit directory
  settings…".
- Directory settings: include and exclude globs, "Recursively include sub-directories", recorded
  in the project file.
- The global "File extensions" setting only pre-fills the include patterns of a new directory.
- Removing a single file of an added directory is not allowed (B-P4).
- The settings dialog opens right after the directory chooser of "Add files in directory…".
- Globs are gitignore-like.

- Suggestions S5–S10 are all accepted (step 6 of §B7, S5 in step 5).

### B11.2 Open

None.

## B12. Suggestions (all accepted)

- **S5** Live preview in the settings dialog (files added / removed, count).
- **S6** Directory nodes show their number of files and are bold when a file below is unsaved.
- **S7** Context menu on file nodes: Remove file from project.
- **S8** "Rescan directory" (context menu): picks up files created on disk since the project was
  opened.
- **S9** "Exclude this sub-directory" on subdirectory nodes (adds `sub/path/**` to the excludes).
- **S10** "Exclude this file" on file nodes of a directory: adds its relative path to the
  directory's excludes (the explicit way to take one file out, since removing it is not allowed).

---

## C. Differences between the plan and the code

- **`DirectoryScanner` is `DirectorySettings.scan(dir)`** (with `accepts(relativePath)`), and the
  glob matching is a class of its own, `model.project.Glob`: a small glob → regex translation
  rather than `PathMatcher`, whose "/" handling differs on Windows.
- **Globs ignore case** (`*.txt` matches `NOTES.TXT`): the former extension filter ignored case,
  and macOS / Windows file systems do. Leading "/" anchors a pattern, trailing "/" means everything
  under a directory; "Exclude this file" writes `/relative/path`, "Exclude this sub-directory"
  `/relative/path/`.
- **Definition changes are followed with `Project.definitionRevisionProperty()`** (an integer
  incremented by every change) instead of a listener list `definitionChanges()`.
- **Fileset entries are saved in definition order** (files and directories mixed), so the top nodes
  of the tree keep their order after reopening; loading reads the `<fileset>` children in order
  (older files, files then directories, load as before).
- **The directory actions are a window service, `gui.workbench.ProjectFiles`** (`Workbench.files()`),
  shared by File Mode's tree and menu, the Current project menu and the toolbar Close button; its
  dialogs (settings, confirmation, unsaved files, directory chooser) are replaceable for the GUI
  tests (`setDialogs`).
- **Removing a directory is confirmed** ("Removes the directory and its N files from the project.
  The files are not deleted from the disk."), like "Remove all files".
- **File Mode's Close button** follows the same rule as "Remove file from project": disabled for a
  file of a directory.
- **A directory already in the project** is refused by "Add files in directory…" (status line
  message).
- The load-problem message now points to Project properties (file types, display), File Mode's
  tree (directories) and Add file… (missing files).
- Not driven by the GUI tests: the Display style and Directory settings dialogs themselves (modal;
  the fixture answers the latter), the directory chooser.

