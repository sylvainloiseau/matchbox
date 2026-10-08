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
- `model.TextDocument` **owns the text of a file**: every change goes through `applyEdits(edits, EditOrigin)` (typing forwarded by the editor, views, assistant, search, undo) and is sent to listeners as a `DocumentChange`; `couldBeWritten` is computed (text ≠ saved text). `model.EditJournal`: **project-wide** undo/redo, the changes of all the documents in the order they were made (consecutive typing in one document merged, one entry per view edit, `grouped(description, changes)` for one entry spanning several documents; entries of a removed document dropped; 1000 entries; labels such as "Edit Translation (notes.txt)" from `EditOrigin.description`). `Project`: `document(TextId)`, `journal()`, `getDirtyDocuments()`, `anyDocumentDirtyProperty()`, `saveAll()`; parses a document at once after a change that does not come from typing.
- **Entry point**: `fr.cnrs.lacito.matchbox.MatchboxApplication` (JavaFX main class).

#### `matchbox-gui`

- contains a graphical interface to the application using javafx (plan: `.claude/plans/MODE_PLAN.md`).
- **Window** (`MainController`, `MainView.fxml`): menus, toolbar (Back/Forward, one button per mode) and status bar. The window's menus (File, Edit, View, Assistant, Export) are mode-independent (`gui.workbench.WorkbenchCommands`): they act on the project, the undo journal, or the **focus of the active mode** (Save current file, Close file, Play media segment). Ctrl+Z not handled by the focused control undoes in the journal.
- **Window services** (`gui.workbench`, facade `Workbench`, given to every mode): `ProjectSession` (current project; corpus listeners that follow project changes, run on the JavaFX thread), `EditJournalView`, `ViewEditService`, the single `ViewContext` (`WorkbenchViewContext`), `SharedMediaPlayers`, `SegmentPlayback`, `StatusLine` (left of the status bar, messages with an optional link, e.g. "Undone: … in other.txt — Show"), `Navigator` (switch mode, "show in editor" = File Mode, reveal, Back/Forward `NavigationHistory`, Shortcut+Alt+←/→), `ObjectViewRegistry`, `HighlightStyles`, `EditHints` (where views edited, what a text action selects after the parse), `UserSettings`, `AssistController`. `Workbench.undo()` reports a change of a file that is not the active mode's focus (Show link) or of several files (Save all link); nothing is saved automatically.
- **Modes** (`gui.mode`): `Mode` (`root()`, `activate`/`deactivate`, `focusProperty()`, `reveal(Focus)`, `menu()`; no shutdown: modes stay alive when hidden), `ModeDescriptor` (id, title, menu title, description, shortcut, factory), `Modes.builtIn()` (the one list of modes; plugins contribute none; File Mode only for now), `ModeHost` (creates modes lazily, shows the active one in `viewPane` and its menu after View, toolbar buttons and View menu items, focus of the active mode), `ModeSwitcher`, `ModeMenus`, `AbstractMode` (recommended base: corpus changes accumulated while hidden and given on activation, `SelectionTracker`). Each mode has its own selection (`SelectionTracker`, kept across re-parses by `ObjectSuccessor`) and exposes a `Focus` (text, range, object).
- **File Mode** (`mode.FileMode`, `mode/FileMode.fxml`, `mode.fileMode.EditorController`): file list, editor, objects tables and detail pane, errors tab, suggestions panel; its menu "File Mode": Search…, Parse file, the assistant at the caret (completion, inline suggestions, explain, review block/selection/file, suggestions panel), Insert.
- **Dialogs**: Project creation/management dialogs (e.g., `CreateProjectDialog.java`, `ProjectPropertiesDialog.java`).

#### `matchbox-corpus`

- `fr.cnrs.lacito.matchbox.corpus`: `CorpusObject` (interface: `getTextCoordinate()`, `getKind()`, `getLabel()`, `getChildren()`) and `TextCoordinate(TextId text, …)` (stop index inclusive; empty span `stopIndex = startIndex - 1`, used only for fields).
  - `TextId`: identity of a text (`TextId.of(path)` normalises the path), known before parsing and given to the grammar.
  - `Text`: immutable snapshot of one parse of a text (content, objects and `Diagnostic`s per grammar in rank order); lookups `objectAt`, `flatten`, `objectsOf`, `grammarIdOf`, `parentOf`, `owns`, header `properties()`, `sections()`, `enclosingSections(o)` (sections of any grammar enclosing an object), `metadataOf(o)` (text properties overridden by enclosing sections). Built with `Text.builder(id, content).add(grammarId, objects, diagnostics)`, which drops objects with invalid spans (fields included) and reports them.
  - `Corpus` (implements the read-only `CorpusView`): one `Text` per `TextId`, replaced copy-on-write (consistent reads from any thread), `CorpusListener(id, before, after)`; `textOf(object)` finds the text of an object (empty for an object of an outdated parse).
  - `Field`, `HasFields`: named values of an object with the span of their raw text, which makes the object editable; `TextEdit` (replacement of a range, `replace(content, span, value)`, `inverse()`).
- `fr.cnrs.lacito.matchbox.corpus.capability`: concepts the application works with (`LexicalEntry`, `Sense`, `Example`, `InterlinearSentence`, `DependencySentence`, `Section` (title, own metadata, content with subsections), `TextHeader` (properties of the whole text), `MediaSegment` (a media file reference and/or a `TimeRange`, a caption; field names `media`, `range`, `start`, `end`, `caption`), `Tag` (a keyword, `/` levels: `getPath()`; field `tag`)). `Text.targetOf(tag)` / `tagsOf(object)` / `textTags()`: a tag applies to the object on the line just above it, else just below (no blank line, tag-only lines skipped, outermost, inside the containing object), else to the innermost object containing it, else to the text (`TagAttachment`). **Admission rule**: an interface is added here only when an application feature (lexicon mode, export, cross-linking, search) needs it; a grammar-specific type implements `CorpusObject` in its own module.
- `fr.cnrs.lacito.matchbox.corpus.simple`: immutable reference implementations that grammars may reuse.

#### `matchbox-grammar-api`

- `GrammarPlugin` (`API_VERSION = 3`): `getId()` (stable, stored in .mbx files), `getDisplayName()`, `getDisplayDescription()`, `getResult(TextId, String)` (re-entrant, thread-safe, runs off the JavaFX thread), `getApiVersion()` (implemented in each plugin as `return API_VERSION;`), `getObjectEditor()` (default `ObjectEditor.VERBATIM`).
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
- **Media** (`grammarfx.media`): `MediaPlayers` (from `ViewContext.mediaPlayers()`, one `SharedMediaPlayers` per window, closed when the project changes): one JavaFX player per resolved file, opened once (`PlayerCache`, at most 8 files), one plays at a time, `playingProperty()`; `MediaResolver.resolve(segment, corpus, projectDirectory)`: explicit file, else `media` metadata of the innermost enclosing section, else of the header, then `MediaPaths` (project directory, then text directory); `MediaResolver.mediaFiles(text, …)`. The editor shows a ▶ button in the margin of each media segment line, highlights the segment playing, and `Shortcut+Shift+Space` plays the segment at the caret (`SegmentAtCaret`: on the line, else the nearest above in the same section).
- **Tags** (`grammarfx.tags.TagBadges`): the tags applying to an object as clickable badges, shown by the built-in views (generic, IGT, lexical entry and its senses, section, media segment) and the header view (with the tags of the whole text). Tags have their own highlight colour (`tagColor` setting) and are drawn last in the editor.
- **Text actions** (`TextActionProvider`, registered by plugins like view factories): actions on a parsed text (`TextAction`: label, offset, `TextEdit`s, offset to select after the parse) shown as buttons in the editor margin (never in the text) and applied by `ViewContext.runTextAction`; e.g. "Add header" of the markdown-heading plugin. The GUI never depends on a particular plugin.
- **Editable views**: a view never changes a corpus object; it asks `ViewContext` for text edits (`canEdit`, `setField`, `addField`, `removeField`, `applyEdits`, `undo`, `redo`), which return an `EditOutcome` (`Applied`, `Stale`, `Refused`, `Invalid`, `NotEditable`). `gui.edit.ViewEditService` applies them to the object's `TextDocument` (displayed or not) as one entry of the undo journal, after `ViewEditPlanner` has relocated them in the current text and, unless the `allowInvalidViewEdits` setting is on, refused those adding parse errors (`EditValidator`); the project then parses the document and the views are refreshed. `ViewContext.corpus()` gives the texts (object's text, sections, metadata).

- A plugin without a factory: objects implementing `InterlinearSentence`, `LexicalEntry` or `Section` get the corresponding view; any other object only shows what `CorpusObject` exposes (kind, label, position, children), so give it an informative `getLabel()` and a meaningful `getChildren()` tree. `DependencySentence`, `Sense` and `Example` have no built-in view (tokens/heads of a `DependencySentence` are not shown by the fallback).

#### `grammars/`

- Each submodule (e.g., `matchbox-grammar-notebook`) contains:
  - ANTLR grammar files (`.g4`) in `src/main/antlr4/fr/cnrs/lacito/matchbox/grammar/<name>/`.
  - Java listeners/visitors (e.g., `NotebookListener.java`).
- `matchbox-grammar-tag` (id `tag-grammar`): inline tags `#"keyword"` or `#“keyword”` anywhere in a line, not in fenced code blocks, as `SimpleTag`; `TagViewFactory` (empty detail pane; columns Tag (editable: renames this occurrence, written with straight quotes), Path, Applies to); added to "All files" by new default projects when installed.
- `matchbox-grammar-mediaref` (id `mediaref-grammar`, no view, no JavaFX): `@media <file> [<start>-<end>] [<caption>]` or `@media <start>-<end> [<caption>]` lines as `MediaMarker` (`MediaSegment` + `HasFields`, time formats kept by edits); every other line ignored; added to "All files" by new default projects when installed.
- `matchbox-grammar-markdownheading` (id `markdown-heading-grammar`): sections from ATX and Setext headings (`MarkdownSection`, with level and style) and YAML `key: value` metadata (file header on line 1 → `SimpleTextHeader`; block after a heading → section metadata); every other line ignored, so it runs next to the notebook grammar on `.md` files (the default project applies it to `.*\.md` when installed). Whole-line lexer tokens; context decisions (Setext underline vs metadata block vs separator, fences) in lexer actions. Ships `TextHeaderViewFactory` and the "Add header" text action; `ObjectEditor` with `addField`/`removeField`.

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
- **GUI**: Launched via `mvn -pl matchbox-gui javafx:run`.

### Grammars

- Each grammar submodule (e.g., `matchbox-grammar-notebook`) is loaded dynamically by `matchbox-core`.

---

## 8. Testing Strategy

- **Unit tests**: JUnit 5 (declared in the root pom), in `src/test/java/` of each module: `Corpus`, `Text`, sections, `TextEdit` (`matchbox-corpus`), grammar tests and field editing (`grammars/*`), `TextDocument`, `EditJournal` and `Project` parsing (`matchbox-app`), view registry, `ViewEditPlanner`, `SectionViewFactory`, `ObjectSuccessor`, `SelectionTracker`, `NavigationHistory`, `ModeSwitcher`, `AbstractMode` (`matchbox-gui`).
- **GUI tests** (`matchbox-gui`, TestFX + Monocle, test scope): the real main window, rendered off-screen (surefire sets `glass.platform=Monocle`, `monocle.platform=Headless`, `testfx.headless=true`), no permission needed and nothing shown on screen. `MainWindowFixture` builds the window as `MatchboxApplication` does, with settings and `user.home` in a temporary directory; the test class declares `@Start`/`@Stop` calling `startWindow`/`stopWindow` (TestFX ignores inherited ones); `addFile`, `showFile`, `waitUntil` (parses are applied asynchronously). Tagged `gui`: `mvn install -DexcludedGroups=gui` leaves them out; where JavaFX cannot start headless they are skipped (a "WARNING: GUI tests skipped…" line in the build log), not failed. `MainWindowTest` covers the checklist of `MODE_PLAN.md` §9 (undo journal labels, Show link, ⌘Z, show in editor and Back, play segment, new project, File Mode menu). Run with `mvn test` (use `mvn clean install` after API changes: incremental builds can keep stale classes).
- **Grammar tests**: parse sample inputs from `src/test/resources/`; the `matchbox-corpus` test-jar provides `CorpusAssertions` (`assertSpansNested`, `assertKindsPrefixed`, `assertOwnedBy`).

---

## 9. Configuration

### User Configuration

Managed by `fr.cnrs.lacito.matchbox.settings` (matchbox-app):

| Class | Role |
|---|---|
| `AppDirectories` | Per-user directories of each OS (below); override with `-Dmatchbox.settings.dir=<dir>` or `MATCHBOX_SETTINGS_DIR` |
| `UserSettings` | What the application uses: JavaFX properties (charset, colours, file extensions…), `RecentItems` for recent files/projects, `documentOptions()` for projects; saved 500 ms after a change, `close()` on exit |
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
- `fileExtensions` selects the files added from a directory (empty list: all files).
- In tests, use `UserSettings.load(dirs, home, delay)` or `-Dmatchbox.settings.dir` and `-Duser.home` pointing to a temporary directory: loading migrates and **deletes** the legacy files of the given home.

### Project Configuration

- **`.mbx` files**: XML-based project files with:
  - Fileset (files/directories included in the project).
  - File type patterns (regex + associated grammars).
  - Example structure:
    ```xml
    <project>
      <fileset>
        <file path="notes.txt" />
        <directory path="data/" />
      </fileset>
      <pattern>
        <name>Lexical Entries</name>
        <regex>.*\.txt</regex>
        <grammar name="lex" rank="1" selected="true" />
      </pattern>
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
