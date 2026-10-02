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
| `grammars/`                    | Contains submodules for ANTLR grammars (e.g., `matchbox-grammar-notebook`, `matchbox-grammar-lex`). |
| `matchbox-app`                 | Main application logic (excluding grammars).                                                |
| `matchbox-distribution`        | Packaging and distribution logic (e.g., assembly scripts).                                  |
| `matchbox-corpus`              | Corpus objects produced by grammars: `CorpusObject` interface, capability interfaces, `Simple*` implementations (no JavaFX). |
| `matchbox-grammar-api`         | Defines the contract for grammars (`GrammarPlugin`, `ParseResult`, `ParseError`, `Spans`), no JavaFX. |
| `matchbox-assist`              | AI assistant without JavaFX: providers (Anthropic, OpenAI-compatible/Ollama), prompts, grammar validation of suggested edits, suggestion model (see `AI_SUGGESTIONS_PLAN.md`). |
| `matchbox-grammar-fx`          | Optional JavaFX contract for plugins that provide views of their objects (`ObjectViewFactory`, `ObjectColumn`, `ViewContext`). |
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
- **Entry point**: `fr.cnrs.lacito.matchbox.MatchboxApplication` (JavaFX main class).

#### `matchbox-gui`

- contains a graphical interface to the application using javafx
- **FXML files**: UI layouts (e.g., `MainView.fxml`).
- **Controllers**: MVC controllers (e.g., `MainController.java`, `FileViewController.java`).
- **Dialogs**: Project creation/management dialogs (e.g., `CreateProjectDialog.java`, `ProjectPropertiesDialog.java`).

#### `matchbox-corpus`

- `fr.cnrs.lacito.matchbox.corpus`: `CorpusObject` (interface: `getTextCoordinate()`, `getKind()`, `getLabel()`, `getChildren()`) and `TextCoordinate` (stop index inclusive).
- `fr.cnrs.lacito.matchbox.corpus.capability`: concepts the application works with (`LexicalEntry`, `Sense`, `Example`, `InterlinearSentence`, `DependencySentence`). **Admission rule**: an interface is added here only when an application feature (lexicon mode, export, cross-linking, search) needs it; a grammar-specific type implements `CorpusObject` in its own module.
- `fr.cnrs.lacito.matchbox.corpus.simple`: immutable reference implementations that grammars may reuse.

#### `matchbox-grammar-api`

- `GrammarPlugin`: `getId()` (stable, stored in .mbx files), `getDisplayName()`, `getDisplayDescription()`, `getResult(Path, String)` (re-entrant, thread-safe, runs off the JavaFX thread), `getApiVersion()` (implemented in each plugin as `return API_VERSION;`).
- `ParseResult` (record of top-level objects and errors), `ParseError`, `CollectingErrorListener`, `Spans.of(path, ctx)`.
- Object kinds are `"<grammar id>:<name>"`.

#### `matchbox-grammar-fx`

- `ObjectViewFactory<T>`: view (`createView`) and table columns (`getColumns`, default empty) of a type of object, registered by plugins in `META-INF/services/fr.cnrs.lacito.matchbox.grammarfx.ObjectViewFactory` (e.g. `ConllSentenceViewFactory`).
- **Choice of the factory** (`gui.view.ObjectViewRegistry`, matchbox-gui): for an object of class `C`, the factory whose `getType()` is the closest supertype of `C` (breadth-first, `C` itself first), then the highest `getPriority()` (built-in: 0), then plugin factories before built-in ones; otherwise `GenericObjectViewFactory`. If a plugin's `createView` throws or returns `null`, the generic view is shown; a failing column gives an empty cell (logged once per factory). Tables always start with a **Line** column; a factory without columns gets a **Label** column (`getLabel()`).
- **Built-in factories** (`matchbox-gui/src/main/java/fr/cnrs/lacito/matchbox/gui/view/`, registered in matchbox-gui's `META-INF/services`):

  | Factory | Type | Detail view | Columns |
  |---|---|---|---|
  | `GenericObjectViewFactory` | `CorpusObject` (fallback) | Kind and file/line range, then a `TreeView` of the object and its `getChildren()` (`label [kind]`); double-click selects the span in the editor | none (Line + Label) |
  | `InterlinearSentenceViewFactory` | `InterlinearSentence` | Transcription, glosses, translation, clickable position | Transcription, Morphemic glosses, Translation |
  | `LexicalEntryViewFactory` | `LexicalEntry` | `LexicalEntryView.fxml` (headword, senses, examples) | Headword, Senses |

- A plugin without a factory: objects implementing `InterlinearSentence` or `LexicalEntry` get the corresponding view; any other object only shows what `CorpusObject` exposes (kind, label, position, children), so give it an informative `getLabel()` and a meaningful `getChildren()` tree. `DependencySentence`, `Sense` and `Example` have no built-in view (tokens/heads of a `DependencySentence` are not shown by the fallback).

#### `grammars/`

- Each submodule (e.g., `matchbox-grammar-notebook`) contains:
  - ANTLR grammar files (`.g4`) in `src/main/antlr4/fr/lacito/matchbox/`.
  - Java listeners/visitors (e.g., `NotebookListener.java`).

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

### Code Conventions

- **Package structure**: `fr.cnrs.lacito.matchbox.*` (e.g., `fr.cnrs.lacito.matchbox.gui`, `fr.cnrs.lacito.matchbox.corpus`, `fr.cnrs.lacito.matchbox.grammarapi`, `fr.cnrs.lacito.matchbox.grammarfx`).
- **Corpus objects** are immutable, keep no reference to ANTLR objects, and a child's span lies inside its parent's span.
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

- **Unit tests**: JUnit 5 (declared in the root pom), in `src/test/java/` of each module: grammar tests (`grammars/*`), `Corpus` and `Project` parsing (`matchbox-app`), view registry (`matchbox-gui`). Run with `mvn test`.
- **Grammar tests**: parse sample inputs from `src/test/resources/`; the `matchbox-corpus` test-jar provides `CorpusAssertions` (`assertSpansNested`, `assertKindsPrefixed`).

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
| `Corpus`                      | `matchbox-app`             | Objects and errors per file and grammar; generic queries (`objectsOf`, `objectAt`, `flatten`). |
| `ObjectViewFactory`           | `matchbox-grammar-fx`      | Optional plugin-provided view of a type of corpus object.                                   |
| `ObjectViewRegistry`          | `matchbox-gui`             | Chooses the view factory of an object.                                                      |

---

## 13. Next Steps for the AI Agent

1. **Explore grammars**: Review `.g4` files in `grammars/` to understand parsing logic.
2. **Inspect GUI**: Check `matchbox-gui/src/main/java/fr/cnrs/lacito/matchbox/gui` for UI components.
3. **Validate build**: Run `mvn clean package` to ensure the project builds successfully.
4. **Test parsing**: Use sample inputs to test grammar parsing (e.g., lexical entries, IGT).
