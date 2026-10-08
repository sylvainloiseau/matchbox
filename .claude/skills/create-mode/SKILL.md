---
name: create-mode
description: Create a new mode of the matchbox GUI (a way to show the corpus in the main area, e.g. tag browser, corpus view, lexicon); Mode class, FXML, registration in Modes, its menu, wiring to the window services (project, corpus, object views, edits, undo journal, status line, navigation).
---

# What is a mode

A mode is a general organisation of the main area of the application window. With the exception of
the menu bar and the toolbar at the top, and the status bar at the bottom of the window, all the
remaining area is defined by a mode.

The different modes are different ways to display the information about the corpus, to navigate the
data, etc. Modes belong to the application: plugins never contribute modes. A plugin maps a notation
to corpus objects; a mode works on the **capabilities** of the objects (`Tag`, `LexicalEntry`,
`Section`…), whatever plugin and notation created them (see `matchbox-grammar-api/README.md`).

Switching between modes is done with the mode buttons of the toolbar or the View menu
(Shortcut+1, Shortcut+2…). Both are generated from the list of modes. A mode is created the first
time it is shown and then **kept alive** while the window lives, so it finds its state again when the
user comes back to it. There is no shutdown.

The architecture is described in `.claude/plans/MODE_PLAN.md`. `FileMode` is the reference
implementation.

# Files

In the `matchbox-gui` module:

- the mode class, e.g. `TagMode`, in `src/main/java/fr/cnrs/lacito/matchbox/gui/mode/`. It implements
  `Mode`, preferably by extending `AbstractMode`.
- its FXML (optional), e.g. `TagMode.fxml`, in
  `src/main/resources/fr/cnrs/lacito/matchbox/gui/mode/`. It has no `fx:controller` attribute: the mode
  loads it itself with `loader.setController(this)`, as in `FileMode`'s constructor. Its root must have
  all four `AnchorPane` anchors set to 0 (`ModeHost` sets them too).
- a package for the classes specific to the mode, e.g. `gui.mode.tagMode` (as `gui.mode.fileMode`).
- `gui.mode.common` (Java and resources) holds code and FXML shared by several modes.

# Registering the mode

Add one `ModeDescriptor` to `Modes.builtIn()`. It is the only place where modes are listed:

```java
new ModeDescriptor("tag", "Tags", "Tag Mode",
    "Browse, search, edit and organise all the tags in the corpus",
    shortcut(KeyCode.DIGIT3), TagMode::new)
```

The toolbar button (with the description as tooltip), the View menu item and the shortcut follow
from it. Do not edit `MainView.fxml` or `MainController` for a mode.

# The contract (`gui.mode.Mode`)

- `root()`: the content of the main area, built once (in the constructor).
- `activate()` / `deactivate()`: the mode is shown or hidden. With `AbstractMode`, override
  `onActivated()` / `onDeactivated()` instead.
- `focusProperty()`: what the window's commands target while the mode is active: the text, range
  and object the user works on (`Focus`). Save current file, Close file and Play media segment act on
  it, and the status bar shows it. With `AbstractMode`, call `setFocus(...)` when the user selects
  something.
- `reveal(Focus)`: show and select a place or an object in this mode (used by navigation,
  `ViewContext.select`, Back/Forward); return false if the mode cannot show it, and the window then
  uses File Mode.
- `menu()`: the mode's own menu ("Tag Mode"), shown after the View menu while the mode is active.

Each mode has **its own selection**: keep it in a `SelectionTracker` (`AbstractMode.selection`). It
moves the selection to the re-parsed object after each parse. Selections are not shared between modes.

# Window services (`gui.workbench.Workbench`, given to the constructor)

Use them; never create your own copy:

| Service | Use |
|---|---|
| `session()` | current project (`projectProperty()`), `corpus()` (`CorpusView`), `addCorpusListener` (listeners follow project changes; called on the JavaFX thread) |
| `viewContext()` | the single `ViewContext`, to give to object views and to edit |
| `viewRegistry()` | `createView`, `updateView`, `columnsFor(type, context, status)`: object views and table columns of every plugin |
| `navigator()` | `showInEditor(span)` (switches to File Mode), `reveal(object)`, `switchTo(modeId)` |
| `status()` | the status line: `info`, `warn`, `info(text, link, action)` |
| `mediaPlayers()`, `playback()` | media players shared by all modes; play/pause a `MediaSegment` |
| `journal()`, `undo()`, `redo()` | the project-wide undo journal |
| `highlightStyles()`, `settings()`, `assist()`, `window()` | colours, user settings, AI assistant, owner of dialogs |

`AbstractMode` listens to the corpus for you. While the mode is active, changes are given to
`onTextsChanged(Set<TextId>)`. While it is hidden, they are accumulated and given once on activation.
A change of project supersedes them and calls `onProjectChanged()`. Keep the work cheap while the
mode is hidden.

# Rules

- **The text is the only source of truth.** Read through `session().corpus()` and the capabilities
  (`Text.objectsOf(Tag.class)`, `metadataOf`, `enclosingSections`, `targetOf`/`tagsOf`). Never test a
  plugin id or an object kind to decide what an object is.
- **Change data only through the `ViewContext`**: `setField`, `addField`, `removeField`,
  `applyEdits`, `runTextAction(textId, action)`. These go to `ViewEditService`, then the `TextDocument`,
  then a re-parse, and every mode sees the new objects. Never change corpus objects or write files.
  Each change is one entry of the undo journal. A change of several files can be made one entry with
  `project.journal().grouped(description, changes)`. Nothing is saved automatically: report changes
  to files the user cannot see on the status line (e.g. a "Save all" link).
- **Show objects** with `viewRegistry()` and the shared controls of `matchbox-grammar-fx`
  (`MetadataPane`, `TagBadges`, `MediaPlayerBox`) rather than per-type code: plugin views then
  appear in the mode automatically.
- **Menus and keys**:
  - put the mode's own functions in `menu()`, with accelerators that the window's menus do not
    use (`ModeHost` logs a collision);
  - never add items to the window's menus (File, Edit, View, Assistant, Export) or rebind them; a
    command that makes sense in every mode belongs in `WorkbenchCommands`;
  - Ctrl+Z is the project journal: do not consume it, unless a control has its own local undo, such
    as a text field.
- **Resources**: own no thread and nothing to close. Use the window's services.

# Tests and documentation

- Put the logic in pure classes without JavaFX (as `ObjectSuccessor`, `SegmentAtCaret`,
  `ErrorFilter`, `ViewEditPlanner`) and unit-test them. `AbstractModeTest` shows how to test a mode
  with a `ProjectSession` and no window.
- Finish with `mvn clean install` (incremental builds can keep stale classes), then a smoke launch
  with `mvn -pl matchbox-gui javafx:run` and a manual checklist.
- Write a plan in `.claude/plans/` first, as for the other features. Then update CLAUDE.md §3
  (matchbox-gui, Modes) and the README section "Modes, undo and navigation".
