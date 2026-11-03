# AI Coding Agent Documentation: Matchbox

This document provides contextual information about the `matchbox` project to help AI coding agents understand its purpose, structure, and key details.

---

## 1. Project Overview

**Purpose**:

Matchbox is an **application** for parsing linguistic field notes files, extracting lexical information and texts from these notes according to various grammars, and showing the resulting corpora. It allows users to:

- Write field notes in plain text using lightweight conventions.
- Parse and extract structured data (e.g., lexical entries, interlinear glossed text) using ANTLR grammars.
- Organize and export data to formats like LIFT (for dictionaries), FLEx, or ELAN.

**Key Features**:

- **Grammar-based parsing**: Uses ANTLR to define and apply grammars for extracting structured data from text.
- **GUI**: JavaFX-based interface for browsing, editing, and visualizing parsed data.
- **Project management**: Save and load projects with custom grammars, file sets, and configurations.
- **Extensible**: Supports dynamic loading of grammars as plugins.

---

## 2. Project Structure

The project is a **Maven multi-module** repository with the following structure:

| Directory/Module               | Purpose                                                                                     |
|--------------------------------|---------------------------------------------------------------------------------------------|
| `grammars/`                    | Contains submodules for ANTLR grammars (`matchbox-grammar-notebook`, `matchbox-grammar-conll`, `matchbox-grammar-markdownheading`, `matchbox-grammar-mediaref`, `matchbox-grammar-tag`, `matchbox-grammar-lex`). |
| `matchbox-app`                 | Main application logic (excluding grammars).                                                |
| `matchbox-distribution`        | Packaging and distribution logic (e.g., assembly scripts).                                  |
| `matchbox-corpus`              | The corpus: `Corpus` (texts of a project), `Text` (one parse of a text), `TextId`, `TextCoordinate`, `Diagnostic`, `TextEdit`, `CorpusObject`, capability interfaces, `Simple*` implementations (no JavaFX, no ANTLR). |
| `matchbox-grammar-api`         | Defines the contract for grammars (`GrammarPlugin`, `ParseResult`, `ObjectEditor`, `CollectingErrorListener`, `Spans`), no JavaFX. What a plugin is and is not: `matchbox-grammar-api/README.md`. |
| `matchbox-assist`              | AI assistant without JavaFX: providers (Anthropic, OpenAI-compatible/Ollama), prompts, grammar validation of suggested edits, suggestion model (see `AI_SUGGESTIONS_PLAN.md`). |
| `matchbox-grammar-fx`          | JavaFX code shared by plugins and the GUI: contracts for views and text actions (`ObjectViewFactory`, `ObjectColumn`, `ViewContext`, `TextActionProvider`) and reusable controls (`grammarfx.metadata`: `MetadataPane`, typed value editors; `grammarfx.media`: shared media players, `MediaResolver`, `MediaPlayerBox`). |
| `matchbox-gui`                 | JavaFX GUI code (MVC pattern).                                                              |
| `.github/`                     | GitHub workflows and templates.                                                             |
| `.vscode/`                     | VS Code configuration.                                                                      |

---

## 3. Key Files
### Root Directory
| File               | Purpose                                                                                     |
|--------------------|---------------------------------------------------------------------------------------------|
| `pom.xml`          | Maven root configuration. Defines modules, dependencies, and build settings.                |
| `README.md`        | High-level project documentation (installation, workflow, examples).                        |
| `Notebook.md`      | Detailed technical notes, design decisions, and implementation history.                     |
| `TODO`             | Pending tasks or features.                                                                  |
| `AGENT_DOCS.md`    | This file. Documentation for AI coding agents.                                              |

### Module-Specific Files

#### `matchbox-app`

- Contains core application logic (e.g., project management, file handling). This logic is common to various application interfaces (GUI, command line...)
- `model.TextDocument` **owns the text of a file**: every change goes through `applyEdits(edits, EditOrigin)` (typing forwarded by the editor, views, assistant, search, undo) and is sent to listeners as a `DocumentChange`; `couldBeWritten` is computed (text ≠ saved text). `model.EditJournal`: **project-wide** undo/redo, the changes of all the documents in the order they were made (consecutive typing in one document merged, one entry per view edit, `grouped(description, changes)` for one entry spanning several documents; entries of a removed document dropped; 1000 entries; labels such as "Edit Translation (notes.txt)" from `EditOrigin.description`). `Project`: `document(TextId)`, `journal()`, `getDirtyDocuments()`, `anyDocumentDirtyProperty()`, `saveAll()`; parses a document at once after a change that does not come from typing. `definitionChangedProperty()`: the definition (name, description, files, file types, display styles, inherited context, assistant) differs from the .mbx file (false after create, load and save). `model.project.DisplayStyles`: display styles of the project, keyed by grammar id and kind (`DisplayStyles.Key`), `resolve(grammar, kind)` = kind style over grammar style; kept for unchecked or uninstalled grammars; edited through `ProjectEdit` (`usedGrammarIds()`, `fileTypesUsing(id)`). `definitionRevisionProperty()` is incremented by every definition change (to follow it; `definitionChangedProperty` stays true). Directories of the definition have `model.project.DirectorySettings` (include / exclude globs, recursive; `scan(dir)`, `accepts(relativePath)`, `withExclude`), globs are `Glob` (gitignore-like: without "/" on the file name at any depth, with "/" on the path inside the directory, case ignored); `Project.addDirectory(Path, DirectorySettings)`, `directorySettings(dir)`, `getDefinitionDirectories()`, `filesOf(dir)`, `directoryOf(file)` (empty for a file added alone), `documentsRemovedBy(dir, settings)`, `setDirectorySettings` / `rescanDirectory` (→ `DirectoryChange` added/removed).
- **Entry point**: `fr.cnrs.lacito.matchbox.MatchboxApplication` (JavaFX main class).

#### `matchbox-gui`

- contains a graphical interface to the application using javafx (plan: `.claude/plans/MODE_PLAN.md`).
- **Window** (`MainController`, `MainView.fxml`, plan `.claude/plans/PROJECT_WINDOW_PLAN.md`): menus, toolbar (Back/Forward, one button per mode) and status bar; title "Matchbox — <project> •" (• = unsaved). The window's menus are mode-independent (`gui.workbench.WorkbenchCommands`): **File** (the project file and the application: New project ⌘N, Open project… ⌘O, Open recent, Save project, Save project as…, Close project ⌘W, Settings… ⌘,, Quit), **Current project** (Project properties…, Add file…, Add files in directory…, Add recent, Save current file ⌘S, Save all files ⇧⌘S, Remove file from project, Remove all files from project… (confirmed)), Edit, **Mode**, Assistant, Export; they act on the project, the undo journal, or the **focus of the active mode**. Ctrl+Z not handled by the focused control undoes in the journal. **No project** is a state of the window (`ProjectSession.closeProject()`, `projectOpenProperty()`): at startup unless "Reopen the last project" is set and works, and after Close project; the main area then shows `gui.start.StartPage` (New project, Open project…, recent projects as links with ×, the reopen setting; not a mode) and every command needing a project is disabled (whole menus, mode buttons and the mode's menu via `ModeHost.enabledProperty()`). A dropped `.mbx` file is opened. Closing a project (Close, New, Open, Quit) goes through `gui.close.ProjectCloser` (`UnsavedProjectState`, `CloseProjectDialog`: yes/no if only the definition changed, else the modified files with check boxes, all checked, and "Yes" for the definition; `CloseChoice`); nothing asked when nothing would be lost.
- **Window services** (`gui.workbench`, facade `Workbench`, given to every mode): `ProjectSession` (current project; corpus listeners that follow project changes, run on the JavaFX thread), `EditJournalView`, `ViewEditService`, the single `ViewContext` (`WorkbenchViewContext`), `SharedMediaPlayers`, `SegmentPlayback`, `StatusLine` (left of the status bar, messages with an optional link, e.g. "Undone: … in other.txt — Show"), `Navigator` (switch mode, "show in editor" = File Mode, reveal, Back/Forward `NavigationHistory`, Shortcut+Alt+←/→), `ObjectViewRegistry`, `HighlightStyles`, `EditHints` (where views edited, what a text action selects after the parse), `ProjectFiles` (files and directories of the project: add a directory with its settings, edit settings, rescan, exclude a file or a sub-directory, remove a directory, remove a file added alone — a file of a directory cannot be removed alone; dialogs replaceable for tests with `setDialogs`), `UserSettings`, `AssistController`. `Workbench.undo()` reports a change of a file that is not the active mode's focus (Show link) or of several files (Save all link); nothing is saved automatically.
- **Modes** (`gui.mode`): `Mode` (`root()`, `activate`/`deactivate`, `focusProperty()`, `reveal(Focus)`, `menu()`; no shutdown: modes stay alive when hidden), `ModeDescriptor` (id, title, menu title, description, shortcut, factory), `Modes.builtIn()` (the one list of modes; plugins contribute none; File Mode only for now), `ModeHost` (creates modes lazily, shows the active one in `viewPane` and its menu after the Mode menu, toolbar buttons and Mode menu items, focus of the active mode), `ModeSwitcher`, `ModeMenus`, `AbstractMode` (recommended base: corpus changes accumulated while hidden and given on activation, `SelectionTracker`). Each mode has its own selection (`SelectionTracker`, kept across re-parses by `ObjectSuccessor`) and exposes a `Focus` (text, range, object).
- **Tag Browser** (`mode.TagBrowserMode`, id `tag`, button "Tags", Shortcut+2, plan `.claude/plans/TAG_BROWSER_PLAN.md`): every `Tag` of the corpus (any plugin) as a tree of the `/` levels with subtree counts (filter, sort A–Z / count), the occurrences of a node (exact, or with sub-tags) with N lines of context before/after (spinners, saved in `TagBrowserSettingsData`), file header rows, the tagged object's lines tinted, an "Applies to" filter by target type (`TagTargetTypes`: by capability, else kind, else "Whole text"); Edit → File Mode (Back returns); Rename (F2, plain: no `/`, no existing sibling) and Move (drag and drop, Move to… F6; may merge, always confirmed) as one batch edit; copy / CSV export. Pure model in `mode.tagBrowser` (`TagIndex`, `TagTree`/`TagNode`, `TagPathEdit`, `ContextExcerpt`, `OccurrenceRow`, `OccurrenceExport`). "Show all … tags" on tag badges reaches it through `ViewContext.browse` (`ModeDescriptor.browses`, `Navigator.browse`).
- **Batch edits** (`gui.edit`): `ViewEditService.setFields(List<FieldChange>, description)` changes fields of many objects in many files, all or nothing (`BatchEditPlanner`: edits computed by each grammar against the same parse, no overlap, each document checked by `ViewEditPlanner`), one undo entry (`EditJournal.grouped`); `checkFields` is the dry run used by dialogs.
- **File Mode** (`mode.FileMode`, `mode/FileMode.fxml`, `mode.fileMode.EditorController`): file tree (`fileTreeView`, pure model `mode.fileMode.FileTree`: a top node per definition entry in order, a directory groups its files with sub-directories as nodes, directories first then files by name, files added alone never grouped, sub-directories without file hidden; filter keeps ancestors; expanded state and selection kept across rebuilds; directory nodes show their file count, bold when a file below is unsaved; context menus: top directory "Edit directory settings…" (`DirectorySettingsDialog`, live preview `Preview`), "Rescan directory", "Remove this directory from the project"; sub-directory "Exclude this sub-directory"; file "Remove file from project" (disabled for a file of a directory), "Exclude this file"), editor, objects tables and detail pane, errors tab, suggestions panel; its menu "File Mode": Find… ⌘F / Find next ⌘G / Find previous ⇧⌘G / Replace… (⌥⌘F, Ctrl+H elsewhere), Go to line… ⌘L, Insert special character… ⇧⌘I, Parse file, Directory settings… / Remove directory from project (selected directory), Lines (Duplicate ⌘D, Delete ⇧⌘K, Move up/down ⌥↑/↓), Editor (options as check items, Zoom in/out/Actual size ⌘=/⌘−/⌘0), the assistant at the caret (completion, inline suggestions, explain, review block/selection/file, suggestions panel), Insert, Keyboard shortcuts.
- **Text editor** (plan `.claude/plans/EDITOR_PLAN.md`): `InlineCssTextArea` in a `VirtualizedScrollPane` (`editorScrollPane`) with an `ErrorRuler` beside it (marks of the lines with parse errors, click → line), the `FindBar` above (match case, whole word, regex, count, Replace / Replace all one undo entry; replaces the former Search dialog) and an empty state. Options in `UserSettings.editorProperty()` (`EditorSettingsData`: wrap lines, line numbers, font size/family, current line highlight, invisible characters, auto-indent, recent special characters), global, also in Settings › Editor. Margin (`EditorGutter`, layout by the pure `GutterLayout`): same width on every line — line number, then a marker column with ▶ (media segment), ✚ (menu of the line's text actions) and ● (parse errors, tooltip). Keys: `EditorKeymap` (pure, per system: macOS ⌘←/→ line, ⌘↑/↓ file, ⌥←/→ word, ⌘⌫/⌥⌫/⌥⌦ deletions, Ctrl+A/E; elsewhere Home/End, Ctrl+Home/End, Ctrl+←/→, Ctrl+Backspace/Delete; Shift selects), installed with `Nodes.addInputMap` over RichTextFX's defaults; smart Home and auto-indent (`Indentation`). Line commands (`LineCommands`) go through `TextDocument.applyEdits` with `EditOrigin.Kind.COMMAND` (one undo entry each). `CaretMemory` restores caret, selection and scroll per file while the project is open. Pure helpers: `LineIndex`, `FindModel`, `Invisibles`, `SpecialCharacters` (palette from `special-characters.txt`, Unicode names); views `SpecialCharacterPopup`, `ShortcutsWindow`; the caret position "Ln, Col (n selected)" is shown in the editor toolbar (click → Go to line).
- **Dialogs**: `ProjectPropertiesDialog` (name, description, file types with their grammars (labels shortened with an ellipsis), **Plugin parameters**: one row per installed plugin (used ones first), "Aa" sample of its display, the file types using it, "Display…" enabled when checked in at least one file type of the working copy, opening `DisplayStyleDialog` (style of all the objects of the grammar and of each kind, `KindCatalog`: declared ∪ in corpus ∪ styled, tri-state properties, preview); greyed rows with "Forget" for plugins not installed that have styles; **Inherited context**: "Context dictionary" (a LIFT file) and "Context corpus" (a Matchbox corpus), each a read-only path, "Choose…" (file chooser) and "×" (none), with an explanation (`Project.getContextDictionary()` / `getContextCorpus()`, `Optional<Path>`, also in `ProjectEdit`; stored only, not used yet); AI assistant; no fileset: files are added with the Current project menu; non-modal `show()` for tests), `DirectorySettingsDialog`, `SettingsDialog` (no colours: they are display styles of the project), `UnsavedChangesDialog` (removing files).
- **Editor styles** (`gui.view.HighlightStyles`): style of an object = project style of its kind ⊕ project style of its grammar ⊕ grammar default for the kind ⊕ grammar default ⊕ palette background by kind (none for a `Sense`); `StyleLayers` composes nested spans (a child overrides only what it sets; tags and playing segments last), `DisplayStyleCss` turns a style into editor / label CSS; `ViewContext.highlightColor` = the background.

#### `matchbox-corpus`

- `fr.cnrs.lacito.matchbox.corpus`: `CorpusObject` (interface: `getTextCoordinate()`, `getKind()`, `getLabel()`, `getChildren()`) and `TextCoordinate(TextId text, …)` (stop index inclusive; empty span `stopIndex = startIndex - 1`, used only for fields).
  - `TextId`: identity of a text (`TextId.of(path)` normalises the path), known before parsing and given to the grammar.
  - `Text`: immutable snapshot of one parse of a text (content, objects and `Diagnostic`s per grammar in rank order); lookups `objectAt`, `flatten`, `objectsOf`, `grammarIdOf`, `parentOf`, `owns`, header `properties()`, `sections()`, `enclosingSections(o)` (sections of any grammar enclosing an object), `metadataOf(o)` (text properties overridden by enclosing sections). Built with `Text.builder(id, content).add(grammarId, objects, diagnostics)`, which drops objects with invalid spans (fields included) and reports them.
  - `Corpus` (implements the read-only `CorpusView`): one `Text` per `TextId`, replaced copy-on-write (consistent reads from any thread), `CorpusListener(id, before, after)`; `textOf(object)` finds the text of an object (empty for an object of an outdated parse).
  - `Field`, `HasFields`: named values of an object with the span of their raw text, which makes the object editable; `TextEdit` (replacement of a range, `replace(content, span, value)`, `inverse()`).
- `fr.cnrs.lacito.matchbox.corpus.capability`: concepts the application works with (`LexicalEntry`, `Sense`, `Example`, `InterlinearSentence`, `DependencySentence`, `Section` (title, own metadata, content with subsections), `TextHeader` (properties of the whole text), `MediaSegment` (a media file reference and/or a `TimeRange`, a caption; field names `media`, `range`, `start`, `end`, `caption`), `Tag` (a keyword, `/` levels: `getPath()`; field `tag`)). `Text.targetOf(tag)` / `tagsOf(object)` / `textTags()`: a tag applies to the object on the line just above it, else just below (no blank line, tag-only lines skipped, outermost, inside the containing object), else to the innermost object containing it, else to the text (`TagAttachment`). **Admission rule**: an interface is added here only when an application feature (lexicon mode, export, cross-linking, search) needs it; a grammar-specific type implements `CorpusObject` in its own module.
- `fr.cnrs.lacito.matchbox.corpus.simple`: immutable reference implementations that grammars may reuse.

#### `matchbox-grammar-api`

- `GrammarPlugin` (`API_VERSION = 3`): `getId()` (stable, stored in .mbx files), `getDisplayName()`, `getDisplayDescription()`, `getResult(TextId, String)` (re-entrant, thread-safe, runs off the JavaFX thread), `getApiVersion()` (implemented in each plugin as `return API_VERSION;`), `getObjectEditor()` (default `ObjectEditor.VERBATIM`), `getObjectKinds()` (default empty: the kinds offered in the Display dialog), `getDefaultDisplayStyle(kind)` (default `DisplayStyle.EMPTY`; kind null = all the grammar's objects).
- `DisplayStyle` (record, every property optional: font family, bold, italic, underline, strikethrough, text and background colours `#rrggbb`; `over(base)`, `validated()`).
- `ParseResult` (record of top-level objects and `Diagnostic`s), `CollectingErrorListener(TextId, grammarId)`, `Spans.of(text, ctx)` / `Spans.of(text, token)`.
- `ObjectEditor`: turns a new value of a field into `TextEdit`s in the grammar's notation, or refuses it (`EditProposal.Edits` / `EditProposal.Refused`).
- `TimeCodes`: media times in milliseconds or clock time (`h:mm:ss.mmm`): parse, write in a given format (without losing precision), display.
- Object kinds are `"<grammar id>:<name>"`.

#### `matchbox-grammar-fx`

- `ObjectViewFactory<T>`: columns may need the application: `getColumns(ViewContext)` (default: `getColumns()`); view (`createView`), in-place refresh after a re-parse (`updateView`, default false: rebuilt) and table columns (`getColumns`, default empty; `ObjectColumn.editable(title, field, value)` for an editable column) of a type of object, registered by plugins in `META-INF/services/fr.cnrs.lacito.matchbox.grammarfx.ObjectViewFactory` (e.g. `ConllSentenceViewFactory`).
- **Choice of the factory** (`gui.view.ObjectViewRegistry`, matchbox-gui): for an object of class `C`, the factory whose `getType()` is the closest supertype of `C` (breadth-first, `C` itself first), then the highest `getPriority()` (built-in: 0), then plugin factories before built-in ones; otherwise `GenericObjectViewFactory`. If a plugin's `createView` throws or returns `null`, the generic view is shown; a failing column gives an empty cell (logged once per factory). Tables always start with a **Line** column; a factory without columns gets a **Label** column (`getLabel()`).
- **Built-in factories** (`matchbox-gui/src/main/java/fr/cnrs/lacito/matchbox/gui/view/`, registered in matchbox-gui's `META-INF/services`):

  | Factory | Type | Detail view | Columns |
  |---|---|---|---|
  | `GenericObjectViewFactory` | `CorpusObject` (fallback) | Kind and file/line range, then a `TreeView` of the object and its `getChildren()` (`label [kind]`); double-click selects the span in the editor | none (Line + Label) |
  | `InterlinearSentenceViewFactory` | `InterlinearSentence` | Transcription, glosses, translation, clickable position | Transcription, Morphemic glosses, Translation |
  | `LexicalEntryViewFactory` | `LexicalEntry` | `LexicalEntryView.fxml` (headword, senses, examples) | Headword (editable), Senses |
  | `MediaSegmentViewFactory` | `MediaSegment` | Caption, media file and where it comes from (explicit / section / header), player of the range or whole file, editable times with ±100 ms nudges, "set from player", mark a segment on a whole file | Start, End (editable), Duration, Caption (editable), Media |
  | `SectionViewFactory` | `Section` | Title, position, breadcrumb of enclosing sections, own and inherited metadata, summary, outline of the content (objects of other grammars included) | Title (editable), Metadata, Content |

  `InterlinearSentenceViewFactory` edits the three lines in its detail view and table (when the object `HasFields`). `SectionViewFactory` shows the metadata with the shared `MetadataPane` and refreshes in place (`updateView`). Plugin factory: `TextHeaderViewFactory` (markdown-heading plugin, type `TextHeader`: header properties in a `MetadataPane`).
- **Metadata editor** (`grammarfx.metadata`, public API for plugins: keep changes backward compatible): `MetadataPane(owner, own, inherited, key → field name, context)` shows key/value rows (own editable with a × delete button, inherited greyed), a `*` button to add an entry, and `update(...)` keeps unchanged rows (and media players) after a re-parse. Values are shown by the editor of their `ValueType`, given by `ViewContext.metadataSchema()` (`MetadataSchema.DEFAULT`: `date` → date picker, ISO 8601 via `IsoDates`; `media` → link + file chooser + audio/video player, or browser for http(s), resolution via `MediaPaths`: project directory then text directory; `speakers` → `ValueList` of badges with `+`, `ValueLists`, `Vocabulary` (empty for now); other keys plain). The application owns the schema; loading vocabularies (e.g. speakers) is an application change only.
- **Media** (`grammarfx.media`): `MediaPlayers` (from `ViewContext.mediaPlayers()`, one `SharedMediaPlayers` per window, closed when the project changes): one JavaFX player per resolved file, opened once (`PlayerCache`, at most 8 files), one plays at a time, `playingProperty()`; `MediaResolver.resolve(segment, corpus, projectDirectory)`: explicit file, else `media` metadata of the innermost enclosing section, else of the header, then `MediaPaths` (project directory, then text directory); `MediaResolver.mediaFiles(text, …)`. The editor shows a ▶ marker in the margin of each media segment line, highlights the segment playing, and `Shortcut+Shift+Space` plays the segment at the caret (`SegmentAtCaret`: on the line, else the nearest above in the same section).
- **Tags** (`grammarfx.tags.TagBadges`): the tags applying to an object as clickable badges (right-click: "Show all … tags" when `ViewContext.canBrowse`), shown by the built-in views (generic, IGT, lexical entry and its senses, section, media segment) and the header view (with the tags of the whole text). Tags are drawn last in the editor; their colour is the display style of their grammar (default of the tag grammar: pink).
- **Text actions** (`TextActionProvider`, registered by plugins like view factories): actions on a parsed text (`TextAction`: label, offset, `TextEdit`s, offset to select after the parse) shown in the editor margin, behind the ✚ menu of their line (never in the text) and applied by `ViewContext.runTextAction`; e.g. "Add header" of the markdown-heading plugin. The GUI never depends on a particular plugin.
- **Editable views**: a view never changes a corpus object; it asks `ViewContext` for text edits (`canEdit`, `setField`, `addField`, `removeField`, `applyEdits`, `undo`, `redo`), which return an `EditOutcome` (`Applied`, `Stale`, `Refused`, `Invalid`, `NotEditable`). `gui.edit.ViewEditService` applies them to the object's `TextDocument` (displayed or not) as one entry of the undo journal, after `ViewEditPlanner` has relocated them in the current text and, unless the `allowInvalidViewEdits` setting is on, refused those adding parse errors (`EditValidator`); the project then parses the document and the views are refreshed. `ViewContext.corpus()` gives the texts (object's text, sections, metadata).

- A plugin without a factory: objects implementing `InterlinearSentence`, `LexicalEntry` or `Section` get the corresponding view; any other object only shows what `CorpusObject` exposes (kind, label, position, children), so give it an informative `getLabel()` and a meaningful `getChildren()` tree. `DependencySentence`, `Sense` and `Example` have no built-in view (tokens/heads of a `DependencySentence` are not shown by the fallback).

#### `grammars/`

- Each submodule (e.g., `matchbox-grammar-notebook`) contains:
  - ANTLR grammar files (`.g4`) in `src/main/antlr4/fr/cnrs/lacito/matchbox/grammar/<name>/`.
  - Java listeners/visitors (e.g., `NotebookListener.java`).
- `matchbox-grammar-tag` (id `tag-grammar`): inline tags `#"keyword"` or `#“keyword”` anywhere in a line, not in fenced code blocks, as `SimpleTag`; `TagViewFactory` (empty detail pane; columns Tag (editable: renames this occurrence, written with straight quotes), Path, Applies to).
- `matchbox-grammar-mediaref` (id `mediaref-grammar`, no view, no JavaFX): `@media <file> [<start>-<end>] [<caption>]` or `@media <start>-<end> [<caption>]` lines as `MediaMarker` (`MediaSegment` + `HasFields`, time formats kept by edits); every other line ignored.
- `matchbox-grammar-markdownheading` (id `markdown-heading-grammar`): sections from ATX and Setext headings (`MarkdownSection`, with level and style) and YAML `key: value` metadata (file header on line 1 → `SimpleTextHeader`; block after a heading → section metadata); every other line ignored, so it runs next to the notebook grammar on `.md` files. Whole-line lexer tokens; context decisions (Setext underline vs metadata block vs separator, fences) in lexer actions. Ships `TextHeaderViewFactory` and the "Add header" text action; `ObjectEditor` with `addField`/`removeField`.

---

## 4. Dependencies
### Core Dependencies
| Dependency               | Purpose                                                                                     |
|--------------------------|---------------------------------------------------------------------------------------------|
| **ANTLR**                | Parser/lexer generator for grammars.                                                        |
| **JavaFX**               | GUI framework.                                                                              |
| **RichTextFX**           | Editable rich text areas for highlighting parsed content.                                   |
| **Maven**                | Build system.                                                                               |

### Key Versions

- Java: 25
- JavaFX: 23.0.2
- ANTLR: 4.13.1

---

## 5. Build and Run Instructions

### Build

1. **Clean and package**:
   ```bash
   mvn clean package
   ```
2. **Build a specific module** (e.g., `matchbox-gui`):
   ```bash
   mvn -pl matchbox-gui -am clean package
   ```

### Run

1. **Development mode**:
   ```bash
   mvn -pl matchbox-gui javafx:run
   ```
2. **Distribution**:
   - Build the distribution:
     ```bash
     mvn -pl matchbox-distribution package
     ```
   - Run the assembled binary:
     ```bash
     bash build/assembly/matchbox-distribution-1.0.0-distribution/bin/matchbox
     ```
3. **Installers** (jpackage, `matchbox-distribution`, execution `installer`): `mvn install` writes the installer of the current system in `build/target/installer${jpackage.destSuffix}`; type `jpackage.type` (default per OS profile: DMG, EXE, DEB), version `app.version` (root pom, default `${revision}`; `-Dapp.version=1.2.3`). Profiles active unless `-Djpackage.type=APP_IMAGE`: `file-association` (`.mbx`, `src/main/jpackage/mbx.properties`) and, on Linux, `linux-installer` (menu entry).
4. **Release** (`.github/workflows/release.yml`, plan `.claude/plans/RELEASE_PLAN.md`): on a tag `v<n>[.<n>[.<n>]]` (first number ≥ 1, else the first step fails), five runners build DEB + AppImage (Linux x86_64 and aarch64; AppImage from `jpackage:jpackage@installer -Djpackage.type=APP_IMAGE`, appimagetool pinned with its SHA-256), EXE (Windows), DMG `-apple-silicon` / `-intel` (macOS), each with a `.sha256`, published on the release named after the tag; `workflow_dispatch` keeps them as run artifacts instead.

---

## 6. Design Patterns and Conventions

### Architecture

- **MVC**: Used in the GUI (`matchbox-gui`).
- **Plugin System**: Grammars are loaded dynamically as plugins via `matchbox-grammar-api`.
- **ANTLR**: Grammars are defined in `.g4` files and compiled into Java listeners/visitors.

### Plugins create objects, modes navigate them

- **A grammar plugin is a mapping from a textual notation to corpus objects**: it recognises the conventions a linguist writes in their notes and turns each occurrence into a `CorpusObject` anchored to its span. That is all a plugin does. It may add a view of its own grammar-specific objects or actions in the editor margin (`matchbox-grammar-fx`), but it never contributes modes, menus or application features.
- **Several plugins may create the same concept with different notations.** For instance, one plugin may offer a lightweight convention to write a lexical entry quickly (`<mami> 'pig'`) and another a more elaborate syntax for entries with senses, examples and notes; both produce objects implementing `LexicalEntry`. Likewise `#"keyword"` (tag plugin) is one way to write a `Tag`, and other notations may come later. A linguist chooses, per file type, the plugins matching their conventions.
- **Kind vs capability**: an object's *kind* (`"<grammar id>:<name>"`) says which plugin and notation produced it; a *capability* (`matchbox-corpus` `capability` package) says what it is for the application. Application features depend on capabilities only, never on a plugin, a kind or a notation; this is why capabilities live in `matchbox-corpus` (admission rule in §3).
- **Modes** (`matchbox-gui`, see `.claude/plans/MODE_PLAN.md`) are the application's ways to navigate, browse and edit the corpus objects: File Mode (files and their parse), Corpus Mode, Tag Mode… They belong to the application, work on capabilities, and are independent of the plugins: the tag browser lists every `Tag`, whatever plugin and notation created it. All changes made in a mode are text edits; the text stays the only source of truth.

### Code Conventions

- **Package structure**: `fr.cnrs.lacito.matchbox.*` (e.g., `fr.cnrs.lacito.matchbox.gui`, `fr.cnrs.lacito.matchbox.corpus`, `fr.cnrs.lacito.matchbox.grammarapi`, `fr.cnrs.lacito.matchbox.grammarfx`).
- **Corpus objects** are immutable, keep no reference to ANTLR objects, and a child's span lies inside its parent's span.
- **References point down**: `Corpus` → `Text` → `CorpusObject` → `TextCoordinate` → `TextId`. Objects hold no reference to their text or corpus; go up with `CorpusView.textOf(object)`, and pass a `CorpusView` to code that needs the corpus.
- **Every change of a text goes through its `TextDocument`** (`applyEdits` with an `EditOrigin`); the editor only mirrors the displayed document. Views propose `TextEdit`s through `ViewContext`, never mutate objects.
- **Threading**: parsing runs on a background thread (`Project.parseInBackground`); results are applied to `Corpus` on the JavaFX thread (`Project.setApplyExecutor(Platform::runLater)`).
- **Plugin dependencies** on matchbox modules and JavaFX use the `provided` scope.
- **User settings**: `fr.cnrs.lacito.matchbox.settings.UserSettings` (observable properties, saved automatically); never read or write settings files directly (see §9).

---

## 7. Entry Points

### Application

- **Main class**: `fr.cnrs.lacito.matchbox.MatchboxApplication` (JavaFX entry point).
- **Project given at launch**: a `.mbx` argument (Linux, Windows) or a macOS "open file" event (`java.awt.Desktop` handler installed in `main` before JavaFX starts) is opened instead of the last project (`MainController(settings, plugins, launchProject)`); later events open it with `MainController.openProjectFile` (asks before closing). Pure helper `LaunchFiles` (argument choice, events queued until the window exists). Tests: `LaunchFilesTest`, `LaunchProjectTest` (fixture hook `launchProject()`).
- **GUI**: Launched via `mvn -pl matchbox-gui javafx:run`.

### Grammars

- Each grammar submodule (e.g., `matchbox-grammar-notebook`) is loaded dynamically by `matchbox-core`.

---

## 8. Testing Strategy

- **Unit tests**: JUnit 5 (declared in the root pom), in `src/test/java/` of each module: `Corpus`, `Text`, sections, `TextEdit` (`matchbox-corpus`), grammar tests and field editing (`grammars/*`), `TextDocument`, `EditJournal` and `Project` parsing (`matchbox-app`), view registry, `ViewEditPlanner`, `SectionViewFactory`, `ObjectSuccessor`, `SelectionTracker`, `NavigationHistory`, `ModeSwitcher`, `AbstractMode`, `BatchEditPlanner`, Tag Browser model (`TagModelTest`, `TagPathEditTest`) (`matchbox-gui`).
- **GUI tests** (`matchbox-gui`, TestFX + Monocle, test scope): the real main window, rendered off-screen (surefire sets `glass.platform=Monocle`, `monocle.platform=Headless`, `testfx.headless=true`), no permission needed and nothing shown on screen. `MainWindowFixture` builds the window as `MatchboxApplication` does, with settings and `user.home` in a temporary directory; the test class declares `@Start`/`@Stop` calling `startWindow`/`stopWindow` (TestFX ignores inherited ones); `addFile`, `showFile`, `waitUntil` (parses are applied asynchronously). Tagged `gui`: `mvn install -DexcludedGroups=gui` leaves them out; where JavaFX cannot start headless they are skipped (a "WARNING: GUI tests skipped…" line in the build log), not failed. `TagBrowserTest` covers the Tag Browser (drag and drop tests are skipped headless: Monocle has no native drag and drop; Move to… covers the same logic). `MainWindowTest` covers the checklist of `MODE_PLAN.md` §9 (undo journal labels, Show link, ⌘Z, show in editor and Back, play segment, new project, File Mode menu). The fixture starts with a new project (`openProjectAtStart()`, false for the start page) and replaces the close dialog (`closeQuestions`, `closeAnswer`); `StartPageTest` covers the window without project, the File / Current project menus and closing a project; `EditorGuiTest` the text editor (scroll pane, options, margin, keys sent as key events, line commands, find bar, caret memory, Go to line); `FileTreeGuiTest` the file tree and directory commands (the fixture answers the directory dialogs: `chosenDirectory`, `directorySettingsAnswer`, `confirmations`); `ProjectPropertiesDialogTest` the Plugin parameters section (dialog shown non-modal). Modal dialogs (`showAndWait`: Display…, Directory settings, Settings) are not driven by the GUI tests. Run with `mvn test` (use `mvn clean install` after API changes: incremental builds can keep stale classes).
- **Grammar tests**: parse sample inputs from `src/test/resources/`; the `matchbox-corpus` test-jar provides `CorpusAssertions` (`assertSpansNested`, `assertKindsPrefixed`, `assertOwnedBy`).

---

## 9. Configuration

### User Configuration

Managed by `fr.cnrs.lacito.matchbox.settings` (matchbox-app):

| Class | Role |
|---|---|
| `AppDirectories` | Per-user directories of each OS (below); override with `-Dmatchbox.settings.dir=<dir>` or `MATCHBOX_SETTINGS_DIR` |
| `UserSettings` | What the application uses: JavaFX properties (charset, file extensions…), `RecentItems` for recent files/projects, `documentOptions()` for projects; saved 500 ms after a change, `close()` on exit |
| `SettingsData`, `RecentData` | Immutable records written as JSON (Jackson); `validated()` replaces missing or invalid values by defaults |
| `SettingsStore` | Atomic reads/writes; an unreadable file is renamed `*.broken-<timestamp>` |
| `LegacySettingsImporter` | One-time import of `~/.config/matchbox/configuration.txt` and `*.history`, then deletes them and `~/.matchbox.log` |
| `LoggingSetup` | Plain-text rotating log (`matchbox-0.log` … `matchbox-4.log`, 1 MB each), appended across launches |

| OS | `settings.json` | `recent.json` | Logs |
|---|---|---|---|
| macOS | `~/Library/Application Support/Matchbox/` | same | `~/Library/Logs/Matchbox/` |
| Windows | `%APPDATA%\Matchbox\` | `%LOCALAPPDATA%\Matchbox\` | `%LOCALAPPDATA%\Matchbox\Logs\` |
| Linux | `$XDG_CONFIG_HOME/matchbox/` (`~/.config/matchbox/`) | `$XDG_STATE_HOME/matchbox/` (`~/.local/state/matchbox/`) | `$XDG_STATE_HOME/matchbox/logs/` |

- The `charset` setting is applied when a file is opened (`TextDocument` keeps it and saves with it); invalid bytes are reported, not replaced.
- `fileExtensions`: default include patterns (`*.txt`…) of a new directory (empty list: all files); afterwards each directory has its own settings in the project file.
- In tests, use `UserSettings.load(dirs, home, delay)` or `-Dmatchbox.settings.dir` and `-Duser.home` pointing to a temporary directory: loading migrates and **deletes** the legacy files of the given home.

### Project Configuration

- **`.mbx` files**: XML-based project files with:
  - Fileset (files and directories in definition order; a directory with its settings: `recursive`, `<include>`, `<exclude>` globs; a `<directory>` without settings loads as before: non-recursive, global extensions).
  - File type patterns (regex on the file name, not the path, + associated grammars in rank order). **A file belongs to every file type it matches**, not only the first: it is parsed with the union of their grammars (`Project.getGrammarsForFile`), in the order of the file types then of the grammars, each grammar once (at its first place). A catch-all type never hides a more specific one; the order of the file types only sets the order of a file's grammars.
  - New project (`Project.createDefaultProject`): a single file type "All files", regex `.*\..*` (`Project.ALL_FILES_REGEX`: every file with an extension), with every installed plugin, sorted by display name.
  - Display styles by grammar and kind (attributes absent = not set; unknown grammars kept).
  - Inherited context: `<inheritedContext>` with `<dictionary path="…"/>` and `<corpus path="…"/>` (absent: none).
  - Loading reads the file types before the fileset, so each file is parsed once, with its grammars, when it is added: a loaded project needs no `parseProject()`.
  - Example structure:
    ```xml
    <project name="Fieldwork">
      <description>Notes of 2025</description>
      <fileset>
        <file path="notes.txt" />
        <directory path="/home/me/data" recursive="true">
          <include>*.txt</include>
          <exclude>drafts/**</exclude>
        </directory>
      </fileset>
      <pattern>
        <name>Lexical Entries</name>
        <regex>.*\.txt</regex>
        <grammar id="notebook-grammar" rank="0" />
      </pattern>
      <displayStyles>
        <display grammar="notebook-grammar" fontFamily="Monaco"/>
        <display grammar="notebook-grammar" kind="notebook-grammar:igt" background="#fff1c1" bold="true"/>
      </displayStyles>
    </project>
    ```

---

## 10. Known Issues or Limitations

- **Documentation gaps**: Some features (e.g., "Mixing annotation") are mentioned in `README.md` but not fully documented.
- **Testing**: No explicit mention of test coverage or CI/CD pipelines.
- **Error handling**: Limited details on how errors (e.g., parsing failures) are surfaced to users.

---

## 11. Example Workflow

1. **Write field notes** in plain text (e.g., `notes.txt`):
   ```
   <mami> 'pig'
   <jaflo> 'kanu'

   '''
   a mio-jaflo-m-wi-j
   1sg down-canoe-top-m.sg-pred
   'I went down in canoe'
   '''
   ```
2. **Create a project**:

   - Use the GUI to create a `.mbx` file.
   - Define file types and associate grammars (e.g., `lex` for lexical entries, `igt` for interlinear glossed text).
   
3. **Parse and extract data**:

   - The GUI highlights parsed content and populates tables with extracted data.
   
4. **Export data**:

   - Export to formats like LIFT, FLEx, or ELAN.

---

## 12. Key Classes/Interfaces

| Class/Interface               | Module                     | Purpose                                                                                     |
|-------------------------------|----------------------------|---------------------------------------------------------------------------------------------|
| `MatchboxApplication`         | `matchbox-app`             | JavaFX entry point.                                                                         |
| `MainController`              | `matchbox-gui`             | MVC controller for the main view.                                                           |
| `Project`                     | `matchbox-app`             | Data model for project configuration.                                                       |
| `ProjectXMLSerializer`        | `matchbox-app`             | Serializes/deserializes `.mbx` files.                                                       |
| `NotebookListener`            | `grammars/matchbox-grammar-notebook` | ANTLR listener for the "notebook" grammar.                                  |
| `GrammarPlugin` (interface)   | `matchbox-grammar-api`     | Contract for grammars (`getId()`, `getResult()`, `getApiVersion()`).                        |
| `CorpusObject` (interface)    | `matchbox-corpus`          | Anything a grammar extracts and anchors to a span of a text.                                |
| `Corpus`, `CorpusView`        | `matchbox-corpus`          | The texts of a project, one `Text` snapshot per `TextId`; `textOf(object)`, `objectsOf(type)`. |
| `Text`                        | `matchbox-corpus`          | One parse of a text: objects and diagnostics per grammar, lookups, header properties, sections, metadata. |
| `TextDocument`, `EditJournal` | `matchbox-app`             | Owner of a file's text (all changes, dirty state); project-wide undo journal.               |
| `Workbench`                   | `matchbox-gui`             | Services of a window shared by all modes (project, edits, journal, view context, media, status, navigation). |
| `Mode`, `ModeHost`            | `matchbox-gui`             | A way to navigate the corpus in the main area; shows the active mode, its menu and toolbar button. |
| `ViewEditService`             | `matchbox-gui`             | Applies the edits requested by object views to the documents.                               |
| `ObjectViewFactory`           | `matchbox-grammar-fx`      | Optional plugin-provided view of a type of corpus object.                                   |
| `TextActionProvider`          | `matchbox-grammar-fx`      | Optional plugin-provided actions on a text, shown in the editor margin.                     |
| `MetadataPane`                | `matchbox-grammar-fx`      | Shared editor of metadata (typed values, add/delete entries).                               |
| `ObjectViewRegistry`          | `matchbox-gui`             | Chooses the view factory of an object.                                                      |

---

## 13. Next Steps for the AI Agent

1. **Explore grammars**: Review `.g4` files in `grammars/` to understand parsing logic.
2. **Inspect GUI**: Check `matchbox-gui/src/main/java/fr/cnrs/lacito/matchbox/gui` for UI components.
3. **Validate build**: Run `mvn clean package` to ensure the project builds successfully.
4. **Test parsing**: Use sample inputs to test grammar parsing (e.g., lexical entries, IGT).
