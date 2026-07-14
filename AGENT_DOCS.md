Instructions for writting tests for a maven module implementing a ANTLR grammar parser in the plugin subdirectory
`
# Skill : write unit tests for maven grammar plugin

You are creating test for a maven plugin module. Such modules:

- are in the plugins/ directory.
- implement the plugin interface 
- containing an ANTLR grammar. 

In order to test the module, you will first generate input text files based on the inspection of the grammar. Those input text files will be stored in the test/resources subdirectory of the module.

You will then generate JUnit test in test/java directory, in the appropriate package subdirectory system. The tests will :

- load the antlr grammar and parse the files directly, in order to check that no exception is thrown
- load the antlr grammar, parse the file and test if the expected number of object are found.
- load the antlr plugin, parse the file and check that the plugin is behaving consistently




# AI Coding Agent Documentation: Matchbox

This document provides contextual information about the `matchbox` project to help AI coding agents understand its purpose, structure, and key details.

---

## 1. Project Overview

**Purpose**:

Matchbox is a **JavaFX application** for parsing linguistic field notes files, extracting lexical information and texts from these notes, and showing the resulting corpora. It allows users to:

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
| `matchbox-grammar-api`         | Defines the contract for grammars (interfaces, base classes).                              |
| `matchbox-gui`                 | JavaFX GUI code (MVC pattern).                                                              |
| `matchbox-language`            | Language-specific logic or utilities.                                                       |
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

#### `matchbox-grammar-api`

- Defines interfaces for grammars (e.g., `getId()`, `getDescription()`, `getLexicalItemDefinition()`).

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

- **Package structure**: `fr.cnrs.lacito.matchbox.*` (e.g., `fr.cnrs.lacito.matchbox.gui`).
- **Configuration files**: Stored in `~/.config/notebook/` (e.g., `fieldnote.history`, `configuration.txt`).

---

## 7. Entry Points

### Application

- **Main class**: `fr.cnrs.lacito.matchbox.MatchboxApplication` (JavaFX entry point).
- **GUI**: Launched via `mvn -pl matchbox-gui javafx:run`.

### Grammars

- Each grammar submodule (e.g., `matchbox-grammar-notebook`) is loaded dynamically by `matchbox-core`.

---

## 8. Testing Strategy

- **Unit tests**: Likely in `src/test/java/` for each module (not yet confirmed).
- **Grammar tests**: ANTLR grammars should be tested with sample inputs (e.g., in `src/test/resources/`).

---

## 9. Configuration

### User Configuration

- **History**: `~/.config/notebook/fieldnote.history` (tracks recently opened files).
- **Settings**: `~/.config/notebook/configuration.txt` (e.g., character sets).

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
| `Grammar` (interface)         | `matchbox-grammar-api`     | Contract for grammars (e.g., `getId()`, `getDescription()`).                                |

---

## 13. Next Steps for the AI Agent

1. **Explore grammars**: Review `.g4` files in `grammars/` to understand parsing logic.
2. **Inspect GUI**: Check `matchbox-gui/src/main/java/fr/cnrs/lacito/matchbox/gui` for UI components.
3. **Validate build**: Run `mvn clean package` to ensure the project builds successfully.
4. **Test parsing**: Use sample inputs to test grammar parsing (e.g., lexical entries, IGT).
