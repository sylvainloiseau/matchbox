# Plan: window without project, project menus, close dialog, per-project plugin styles

Status: implemented (with the suggestions of §12). Decisions in §11.1, defaults in §11.2;
what differs from the plan in §13. Followed by `PROJECT_WINDOW_PLAN_addendum.md` (Plugin parameters
section instead of the Display… button in the grammar list, no Fileset section, File Mode tree and
directory settings).

## 0. Goal

| # | Change | User gets |
|---|---|---|
| 1 | "View" menu → "Mode" (`viewMenu` → `modeMenu`) | a menu named after what it lists |
| 2 | No empty project at startup or after Close project | a start page (New, Open, recent projects); everything that needs a project is disabled |
| 3 | Display style per plugin and per object kind, stored in the project | font family, bold/italic, underline/strikethrough, text and background colours for all the objects of a plugin, overridable per kind (IGT vs lexical entries), set in Project properties; "Color" tab removed from Settings |
| 4 | One dialog when closing a project | says whether the project definition and/or which files changed; choose what to save |
| 5 | "Current project" menu | project content commands (properties, add/remove files, save files) separated from project file commands |
| 6 | "Settings" → "Settings…" | the usual ellipsis for an item opening a dialog |

Out of scope: macOS application menu (Settings/Quit in the app menu), i18n.

## 1. Architectural assessment

**P1. `ProjectSession` cannot hold "no project".** `setProject` requires non-null and every
consumer assumes a project: `FileMode.setProject(null)` would throw (`project.getDerivedFileList()`),
`AbstractMode.projectChanged` rebuilds for a null project, `MainController.project()` is called
everywhere without check. `EditJournalView`, `WorkbenchCommands.bindProject`,
`AssistController.setProject` and `ProjectSession.corpus()` already handle null.
→ `ProjectSession.closeProject()` (sets null, runs `beforeChange`), `projectOpenProperty()`
(`ReadOnlyBooleanProperty`); make `FileMode`, `TagBrowserMode` (via `AbstractMode`) and
`MainController` null-safe: a mode with no project shows an empty content and is not shown anyway
(the start page covers the main area).

**P2. The "project definition changed" flag is always true.** `Project.createDefaultProject` sets
`isProjectDefinitionOutdated` to true, and `MainController.onProjectChangeHook()` sets it to true
after every open, new project and Project properties Save. So today every Close project asks,
which makes item 4 meaningless.
→ The flag is false after `loadProject`, `createDefaultProject` and a successful save; only real
changes set it (setters already do: `setName`, `addFile`, `removeFile`, `setFileTypes`…;
`ProjectEdit.applyTo` goes through them). Delete `onProjectChangeHook`. Rename the property
`definitionChangedProperty()` (keep the old accessor as deprecated alias? No: it is used in 3
places; rename them).

**P3. Bugs in the current close flow** (fixed as part of item 4):
- `handleCloseCurrentProject` compares the answer with `ButtonType.OK`/`CANCEL` while the alert
  uses custom button types: "Yes" never saves, "Cancel" never cancels.
- `handleNewProject` calls `handleCloseCurrentProject` and ignores a cancel: the new project
  replaces the current one anyway.
- `handleOpenRecentFileset` is an empty `onAction` on a `Menu`: remove it.
→ `closeProject(String action)` returns false on cancel; New, Open, Open recent and Quit call it
first.

**P4. The welcome page is not a mode.** A mode "navigates the corpus objects" (CLAUDE.md §6): it
has a selection, a focus, a menu, a toolbar button, and takes part in Back/Forward. The start page
has none of these and exists precisely when there is no corpus. Making it a mode would need a
"hidden" flag on `ModeDescriptor`, exclusions in `ModeHost` (no button, no Mode menu item), in
`Navigator` (no jump recorded) and in `ModeSwitcher`.
→ Decided: a plain `StartPage` node in a `StackPane` over the mode area, visible while no
project is open (`MainController` binds it). Modes are untouched.

**P5. Colours today are per capability, the request is per plugin.** `HighlightStyles.colorFor`
uses `igtColor` for any `InterlinearSentence`, `lexicalColor` for `LexicalEntry`, `tagColor` for
`Tag`, and a palette by kind otherwise. Per plugin:
- one plugin producing several kinds (the notebook grammar makes IGT *and* lexical entries) gets
  one style for all of them: IGT and entries would no longer be distinguished;
- styling by plugin id is acceptable architecturally: it is a user-defined mapping from any
  installed plugin, no plugin id is hard-coded (unlike a feature depending on a plugin);
- the plugin of an object is `Text.grammarIdOf(object)` (not the kind prefix, which is only a
  convention).
→ Decided: a plugin style (all its objects) plus optional **per-kind** styles that override it
property by property. Resolution for an object of kind `k` from plugin `g`: style(`g`,`k`) ⊕
style(`g`) ⊕ default (each property from the first level that sets it).

**P5b. Plugins do not declare their kinds.** Decided: `GrammarPlugin.getObjectKinds()`, a
**default method** returning `List.of()` (backward compatible, `API_VERSION` stays 3), implemented
by the built-in plugins (notebook, conll, markdown-heading, mediaref, tag, lex). The Display dialog
lists the declared kinds ∪ the kinds of this plugin in the current corpus ∪ the kinds already
styled in the project (a kind of an old version of the plugin stays editable and removable).

**P6. Plugin list is per file type, style is per project.** In Project properties the grammar list
belongs to the selected file type; a plugin may be checked in one file type and not in another.
If the style is keyed by plugin id at project level, "unchecked but style kept" is automatic: the
style lives outside the file types. The button is enabled when the plugin is checked in the
selected file type; the same style is shown from any file type.
→ Decided: `<displayStyles>` element at project level, keyed by plugin id (and kind). Unchecked
grammars need no `selected="false"` marker (today they are simply not written).

**P7. Nested objects: `setStyle` replaces.** `EditorController.highlightText` calls
`textArea.setStyle(start, end, css)` per object, parents first: a child's style replaces the
parent's on its span. With background-only styles that looked fine; with fonts, a tag inside a
bold IGT would lose the bold.
→ Compose: each property not set by the child is inherited from the enclosing object
(`StyleLayers`, pure, below); one `setStyleSpans` call instead of N `setStyle` calls (also
faster on long files).

**P8. Font family in a monospace editor.** IGT lines are aligned by columns; a proportional font on
some spans breaks the alignment. → Allowed (the user asked), default "Editor font", tooltip warning
in the dialog. Font size is not offered (line height would change).

**P9. Unknown plugin ids make a project unloadable.** `ProjectXMLSerializer.load` throws "Grammar
not found" for a file type grammar. Display styles of uninstalled plugins must be **kept** (read
and written back unchanged), never an error.

**P10. Tag colour is read outside the editor.** `TagBrowserMode` passes
`settings().tagColorProperty()` to `OccurrenceCell`; `TagBadges` uses `ViewContext.highlightColor`.
→ `OccurrenceCell` takes a `Function<CorpusObject, String>` (the background of the tag's plugin,
from `HighlightStyles`); `ViewContext.highlightColor` keeps its meaning (background colour) and its
signature: no plugin API change.

## 2. User interface

### 2.1 Menus (items 1, 5, 6)

```
File                         Current project                    Edit   Mode          <mode menu> Assistant Export
 New project        ⌘N        Project properties…                 ...   ● File   ⌘1
 Open project…      ⌘O        ──────────                                ○ Tags   ⌘2
 Open recent project ▸        Add file…                                 ──────
 ──────────                   Add files in directory…                   Back   ⌥⌘←
 Save project                 Add recent ▸                              Forward ⌥⌘→
 Save project as…             ──────────
 Close project                Save current file   ⌘S
 ──────────                   Save all files      ⇧⌘S
 Settings…          ⌘,        ──────────
 Quit               ⌘Q        Remove file from project
                              Remove all files from project…
```

- "AI assistant…" leaves the File menu: it duplicates Assistant › "AI assistant settings…".
- Typos fixed: "Open projet…" → "Open project…", "Projet properties…" → "Project properties…".
- "Remove all files from project…" takes an ellipsis: it now asks for confirmation (all files,
  even unchanged ones, leave the project; §12 S5 default: confirm).
- Accelerators: suggestion S4 (today Add file has a strange `control=ANY` Ctrl+O).

### 2.2 Enabled state (item 2)

`projectOpen` = `workbench.session().projectOpenProperty()`.

| Item | No project | Project open |
|---|---|---|
| New project, Open project…, Open recent project | enabled | enabled: they close the current project first (close dialog; Cancel keeps it) — decided with the user |
| Settings…, Quit | enabled | enabled |
| Save project, Save project as…, Close project | disabled | enabled |
| Current project, Edit, Mode, mode menu, Export (whole menus) | disabled | enabled (items keep their own rules) |
| Assistant › Refresh project examples / AI assistant settings… | disabled / enabled | enabled |
| Toolbar: Back/Forward, mode buttons | disabled | enabled |

Implementation: `Menu.disableProperty().bind(projectOpen.not())` for whole menus; the items of
`WorkbenchCommands.bindMenus` already become disabled with no project (their properties are false);
mode buttons: `ModeHost.enabledProperty()` (new) bound by the controller.

### 2.3 Start page (item 2)

```
┌──────────────────────────────────────────────────────────────┐
│ [◀][▶] | [File][Tags]          (disabled)                   │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│                         Matchbox                             │
│                                                              │
│            [ New project ]   [ Open project… ]              │
│                                                              │
│            Recent projects                                   │
│              fieldwork-2025.mbx    ~/notes/fieldwork         │
│              lexicon.mbx           ~/dict                    │
│                                                              │
│            (empty: "No recent projects")                     │
└──────────────────────────────────────────────────────────────┘
```

- Recent projects as `Hyperlink`s (file name; parent directory greyed beside it; full path in a
  tooltip), from `settings.recentProjects().existing()`, refreshed when the list changes.
- Keyboard: Tab through the buttons and links; Enter activates; "New project" has the initial
  focus.
- Opening a project or creating one hides the page and activates File Mode
  (`ModeHost.activate(Modes.FILE)`, no jump recorded: history is cleared on a project change).
- A recent project that fails to load: error dialog (`ErrorReporter`), the page stays.

### 2.4 Close project dialog (item 4)

Used for Close project, New project, Open project / Open recent, Quit (the action word changes:
"before closing the project", "before quitting"…). Not shown when nothing changed.

Case A, definition only:
```
 The project definition (files, file types, plugins, display) has changed.
 Save the changes before closing the project?
                                  [Save]  [Don't save]  [Cancel]
```
Case B, files only:
```
 The following files have changed. Select the files you want to save:
 ┌────────────────────────────────────────────┐
 │ [x] notes/day1.txt                         │  (virtualised list, full path in tooltip,
 │ [x] notes/day2.md                          │   paths relative to the project directory
 └────────────────────────────────────────────┘   when inside it)
 [Select all] [Unselect all]
                      [Save selected and close]  [Cancel]
```
Case C, both: a "[x] Yes" check box under the definition sentence, then case B.

- Defaults: everything checked (the safe choice). Unchecking everything = discard.
- The primary button reads "Save selected and close" / "…and quit"; it says "Close without
  saving" when nothing is checked.
- Saving the definition of a never-saved project opens the "Save project as" chooser; cancelling
  it cancels the close.
- A save that fails: `ErrorReporter`, the project stays open (nothing more is saved).
- Removing a single file (Remove file) and Project properties' dropped files keep
  `UnsavedChangesDialog`.

### 2.5 Display style (item 3)

Project properties, grammar list of a file type:
```
 [x] 1. Notebook — lexical entries and IGT                [Display…]
 [x] 2. Tags — inline #"keyword" tags                     [Display…]
 [ ]    CoNLL — dependency sentences                      [Display…]  (disabled)
```
The cell already has a right spacer (`GrammarCell`); the button goes after it. Tooltip: "Font
and colours of the objects of this plugin in the editor".

`DisplayStyleDialog` (modal, owner = Project properties):
```
 Display of the "Notebook" objects
 ┌ Applies to ──────────────────┐
 │ All objects of the plugin    │   ← plugin style (always present)
 │   igt            (styled)    │   ← kinds: declared ∪ in corpus ∪ styled; the name after
 │   entry                      │     "<grammar id>:"; "(styled)" when it has its own style
 │   example                    │
 └──────────────────────────────┘
 For a kind, each property has "Inherit" (from the plugin style) as first choice.
 Font family     [ Editor font            ▾]   ⚠ tooltip: proportional fonts break IGT alignment
 Style           [ ] Bold  [ ] Italic
 Decoration      [ ] Underline  [ ] Strikethrough
 Text colour     [ ] Default  [■ #222222 ▾]
 Background      [ ] None     [■ #FFF1C1 ▾]
 Preview         ┌───────────────────────────┐
                 │ a mio-jaflo #"verb"        │  (editor font, style applied)
                 └───────────────────────────┘
 [Reset to default]                    [OK] [Cancel]
```
"Reset to default" on a kind removes its own style (it inherits again); on the plugin entry it
clears the plugin style. Switching entries keeps the edits of each entry until OK/Cancel. The
preview shows the resolved style (kind ⊕ plugin ⊕ default).

OK stores the styles in the dialog's `ProjectEdit` copy; Project properties Save applies it (the
project definition is then changed, saved with Save project), Cancel discards.

## 3. Model (pure, no JavaFX controls)

| Class | Module | Role |
|---|---|---|
| `grammarapi.DisplayStyle` (record) | matchbox-grammar-api (S2) | every property optional (null = not set, inherited): `fontFamily`, `bold`, `italic`, `underline`, `strikethrough` (`Boolean`), `textColor`, `backgroundColor`; `EMPTY`; `over(DisplayStyle base)` (property-wise fallback); `validated()` drops invalid colours (CSS `#RRGGBB` checked by regex, no JavaFX) |
| `model.project.StyleKey` (record) | matchbox-app | `grammarId`, `kind` (null = all the objects of the plugin) |
| `model.project.DisplayStyles` | matchbox-app | immutable map `StyleKey` → `DisplayStyle`; `resolve(grammarId, kind)` = kind ⊕ plugin; `with(key, style)` / `without(key)`; kept for unchecked and uninstalled plugins |
| `Project.displayStylesProperty()` | matchbox-app | `ReadOnlyObjectProperty<DisplayStyles>`; `setDisplayStyles(...)` marks the definition changed |
| `ProjectEdit` | matchbox-app | working copy of `DisplayStyles`, applied by `applyTo` |
| `GrammarPlugin.getObjectKinds()` | matchbox-grammar-api | default `List.of()`: the kinds the plugin may produce (P5b) |
| `ProjectSession.closeProject()`, `projectOpenProperty()` | matchbox-gui | P1 |
| `gui.close.UnsavedProjectState` (record) | matchbox-gui | `definitionChanged`, `List<TextDocument> dirty`; `of(project)`; `isEmpty()`; `kind()` A/B/C |
| `gui.close.CloseChoice` (record) | matchbox-gui | `saveDefinition`, `List<TextDocument> toSave` (the dialog's answer, `Optional.empty()` = cancel) |
| `gui.close.ProjectCloser` | matchbox-gui | `confirmAndSave(owner, project, action)`: state → dialog → saves (definition first, then files) → true/false; dialog and saver injected for tests |
| `gui.view.DisplayStyleCss` | matchbox-gui | `DisplayStyle` → RichTextFX inline CSS (`-fx-font-family`, `-fx-font-weight`, `-fx-font-style`, `-fx-underline`, `-fx-strikethrough`, `-fx-fill`, `-rtfx-background-color`) |
| `gui.view.StyleLayers` | matchbox-gui | P7: from (span, style) pairs, parents first, the composed style of each run (child properties override, unset ones inherited); returns runs for a `StyleSpansBuilder` |
| `HighlightStyles` | matchbox-gui | now `styleFor(Text, CorpusObject)` → `DisplayStyle`: `styles.resolve(text.grammarIdOf(o), o.getKind())` over the default (palette background by kind, as today for kinds without a colour setting); `colorFor` kept (background) for `ViewContext.highlightColor`; follows `projectProperty` and the project's `displayStylesProperty` |
| `gui.view.KindCatalog` | matchbox-gui | the kinds offered by the dialog for a plugin: declared ∪ in corpus ∪ styled, sorted |

## 4. View classes

| Class | Role |
|---|---|
| `gui.start.StartPage` | §2.3; callbacks `onNewProject`, `onOpenProject`, `onOpenRecent(Path)` |
| `gui.close.CloseProjectDialog` | §2.4, returns `Optional<CloseChoice>`; `ListView` with `CheckBoxListCell` |
| `gui.DisplayStyleDialog` | §2.5, returns `Optional<DisplayStyle>` |
| `ProjectPropertiesDialog.GrammarCell` | + "Display…" button, disabled unless checked in the current file type |
| `SettingsDialog` / `.fxml` | "Color" tab and its fields removed |
| `MainController` / `MainView.fxml` | menus §2.1, bindings §2.2, `StackPane` (viewPane + start page), `closeProject` flow |
| `ModeHost` | `modeMenu` parameter, `enabledProperty()` for buttons and Mode menu items |
| `EditorController.highlightText` | `StyleLayers` + `setStyleSpans`; listens to the project's style changes instead of the three colour settings |
| `OccurrenceCell` | tag background from `HighlightStyles` (P10) |

## 5. Data flow

- **Startup**: `MainController` constructor loads the last project if `openLastProject` and it
  loads, else none. `initialize()` binds `projectOpen`; start page visible iff no project.
- **New / Open / Open recent**: `ProjectCloser.confirmAndSave` if a project is open (Cancel stops
  here) → `session.setProject(p)`
  → `modes.activate(FILE)`; recent list updated on open.
- **Close project**: `ProjectCloser` → `session.closeProject()` → `beforeChange` (media players
  closed), modes see null, navigator clears history, start page shown.
- **Quit**: `confirmQuit()` → `ProjectCloser` with "quitting" (definition included, unlike today).
- **Style**: Display… → `DisplayStyleDialog` → `ProjectEdit` styles → Save →
  `Project.setDisplayStyles` → property change → `HighlightStyles` → editor re-highlights,
  views refreshed (they read `highlightColor`) → definition changed → Save project writes
  `<displayStyles>`.

## 6. Settings and project file

- `SettingsData`/`UserSettings`: remove `igtColor`, `lexicalColor`, `tagColor` (and from
  `LegacySettingsImporter`). Old `settings.json` keep the keys: unknown keys are ignored on load
  (`SettingsStoreTest` "futureOption"), dropped on the next write. The old colours are not
  migrated (they were per capability, not per plugin, and global).
- `.mbx`:
  ```xml
  <displayStyles>
    <display grammar="tag-grammar" background="#FFF1C1" bold="true"/>
    <display grammar="notebook-grammar" fontFamily="Monaco"/>
    <display grammar="notebook-grammar" kind="notebook-grammar:igt" background="#FFF1C1"/>
    <display grammar="notebook-grammar" kind="notebook-grammar:entry" background="#E2ECE9" italic="true"/>
  </displayStyles>
  ```
  Attributes absent = not set (inherited). Unknown grammar ids and kinds kept. Invalid values → default for that
  attribute plus a load problem (reported as today).

## 7. Implementation steps

Each step compiles and passes `mvn clean install`.

1. **Item 6 + 1**: "Settings…"; `viewMenu` → `modeMenu` (FXML, `MainController`, `ModeHost`
   parameter and javadoc, `ModeDescriptor` javadoc "View menu"), menu text "Mode"; tests
   (`MainWindowTest`, `TagBrowserTest`, `ModeMenusTest`) and CLAUDE.md.
2. **Item 5**: "Current project" menu, items moved and renamed, typos, "AI assistant…" removed
   from File; confirmation of Remove all files; tests of the menu bar.
3. **P2**: definition-changed flag false after load/create/save; `onProjectChangeHook` deleted;
   `ProjectTest` cases.
4. **Item 2 (model)**: `ProjectSession.closeProject`/`projectOpenProperty`; null-safe
   `FileMode`, `AbstractMode`, `MainController`; unit tests.
5. **Item 2 (window)**: `StartPage`, `StackPane`, bindings §2.2, startup without project,
   `ModeHost.enabledProperty`; `MainWindowFixture` opens a default project after `startWindow`;
   GUI tests.
6. **Item 4**: `UnsavedProjectState`, `CloseChoice`, `ProjectCloser`, `CloseProjectDialog`; wired
   to Close, New, Open, Open recent, Quit (P3 fixed); old alert removed; tests.
7. **Item 3 (model and API)**: `DisplayStyle`, `StyleKey`, `DisplayStyles`, `Project`/`ProjectEdit`
   styles, serializer round trip; `GrammarPlugin.getObjectKinds()` default method and its
   implementation in the built-in plugins (one test per plugin: every kind it produces on its
   sample input is declared).
8. **Item 3 (editor)**: `DisplayStyleCss`, `StyleLayers`, `HighlightStyles.styleFor`,
   `EditorController` with `setStyleSpans`, `OccurrenceCell`; colour settings and Color tab
   removed.
9. **Item 3 (dialog)**: `KindCatalog`, `DisplayStyleDialog` (plugin entry + kinds), button in
   `GrammarCell`.
10. Suggestions S2–S7 (§12).
11. CLAUDE.md (§3 matchbox-gui window/menus, §9 settings and `.mbx`), README.md.

## 8. What is not changed

- Plugin APIs apart from the `GrammarPlugin.getObjectKinds()` default method: `ViewContext`
  (`highlightColor` keeps its signature), `ObjectViewFactory`; `API_VERSION` stays 3.
- Modes, `ModeDescriptor`, `Navigator` (apart from the menu name).
- `UnsavedChangesDialog` (Remove file, Project properties).
- File type grammar serialization (`<grammar id rank>`).

## 9. Tests

Unit (no JavaFX):
- `ProjectSessionTest`: `closeProject` runs `beforeChange`, property null, `projectOpen` false;
  corpus listeners detached.
- `ProjectTest`: flag false after `createDefaultProject` and `loadProject`, true after `addFile`,
  `setDisplayStyle`; false after save.
- `ProjectXMLSerializerTest`: display styles round trip (plugin and kind entries); unknown plugin kept; invalid colour →
  default + load problem; style of a plugin unchecked in every file type kept.
- `DisplayStyleTest` (`validated`, `over`), `DisplayStylesTest` (kind ⊕ plugin ⊕ default,
  `without` a kind inherits again), `KindCatalogTest`, `DisplayStyleCssTest`, `StyleLayersTest` (nesting, overlap,
  child overrides only what it sets), `HighlightStylesTest` (project style, palette fallback).
- `ProjectCloserTest`: cases A/B/C/none, cancel, partial selection, save failure stops, never-saved
  project → save-as callback, cancelling it cancels.

GUI (TestFX, headless):
- startup without project: start page visible, menus disabled per §2.2, mode buttons disabled.
- New project from the start page → File Mode, menus enabled, New/Open disabled.
- Close project with nothing changed → start page, no dialog.
- Close with a modified file → dialog lists it; Unselect all → "Close without saving".
- menu bar: `File, Current project, Edit, Mode, File Mode, Assistant, Export`; Settings dialog has
  no "Color" tab.
- Project properties: Display… disabled for an unchecked grammar, enabled when checked.
- New project while a project with a modified file is open → close dialog; Cancel keeps the
  project.
- Not headless-testable: native file choosers (Open project, Save as) — callbacks are injected in
  `ProjectCloser` and the start page for tests.

## 10. Files

New: `gui/start/StartPage.java`, `gui/close/{UnsavedProjectState,CloseChoice,ProjectCloser,CloseProjectDialog}.java`,
`gui/DisplayStyleDialog.java`, `gui/view/{DisplayStyleCss,StyleLayers}.java`,
`grammarapi/DisplayStyle.java`, `model/project/{StyleKey,DisplayStyles}.java`, `gui/view/KindCatalog.java`, tests
above.

Changed: `MainView.fxml`, `MainController`, `ModeHost`, `ModeDescriptor` (javadoc),
`ProjectSession`, `FileMode`, `AbstractMode`, `WorkbenchCommands`, `SettingsDialog(.fxml)`,
`ProjectPropertiesDialog`, `HighlightStyles`, `GrammarPlugin` (default method) and the built-in
plugins, `EditorController`, `OccurrenceCell`,
`TagBrowserMode`, `Project`, `ProjectEdit`, `ProjectXMLSerializer`, `SettingsData`,
`UserSettings`, `LegacySettingsImporter`, settings tests, `MainWindowFixture`, `MainWindowTest`,
`TagBrowserTest`, `ModeMenusTest`, CLAUDE.md, README.md.

## 11. Questions

### 11.1 Decided with the user

- **Start page**: a plain page stacked over the mode area, not a mode (P4).
- **Granularity**: a style per plugin **and** per object kind now, the kind overriding the plugin
  property by property (P5).
- **Kind list**: `GrammarPlugin.getObjectKinds()` default method implemented by the built-in
  plugins, plus the kinds in the corpus and those already styled (P5b).
- **Scope**: styles at project level keyed by plugin id (and kind), shared by all file types,
  kept when the plugin is unchecked or uninstalled (P6).
- **New / Open / Open recent** stay enabled while a project is open; they go through the close
  dialog first, Cancel keeps the current project (§2.2).

### 11.2 Defaults (not asked; say if you want otherwise)

- Wording: "The project definition (files, file types, plugins, display) has changed" (the request
  says "file definition").
- Button label "Display…" (the request says "plugin display").
- With no project, the Assistant menu stays enabled but only "AI assistant settings…" is active
  (it is a user setting, like Settings…); "Refresh project examples" is disabled. §2.2 is read
  this way.
- Close dialog: every file checked by default; two buttons "Select all" / "Unselect all".

## 12. Suggestions (all accepted, implemented in step 11)

- **S2** Plugin default style: `GrammarPlugin.getDefaultDisplayStyle(String kind)` (default method
  returning `DisplayStyle.EMPTY`; `kind` null = all objects of the plugin). `DisplayStyle` moves to
  `matchbox-grammar-api` (pure record, no JavaFX) so that plugins can return it. Resolution:
  project(kind) ⊕ project(plugin) ⊕ plugin default(kind) ⊕ plugin default(plugin) ⊕ palette. The
  notebook grammar gives IGT and lexical entries their former default colours, the tag grammar the
  former tag colour.
- **S3** Start page extras: "Reopen the last project at startup" check box bound to the
  `openLastProject` setting; a × button on each recent project (removes it from the list); a
  `.mbx` file dropped on the window opens it (and on the start page).
- **S4** Accelerators: ⌘N New project, ⌘O Open project…, ⌘S Save current file, ⇧⌘S Save all files,
  ⌘, Settings…, ⌘W Close project, ⌘Q Quit (unchanged). Add file loses its odd Ctrl+O.
- **S5** "Remove all files from project…" asks: "Removes N files and M directories from the
  project. The files are not deleted from the disk."
- **S6** Window title: "Matchbox — <project name or file name or 'New project'>", with "•" when
  the definition or a file is unsaved; "Matchbox" alone with no project.
- **S7** A recent project that fails to load: the error dialog offers "Remove from recent
  projects".

## 13. Differences between the plan and the code

- `StyleKey` is the nested record `DisplayStyles.Key`.
- `DisplayStyle` lives in `matchbox-grammar-api` (S2), with `over`, `validated`, `isColor` and
  withers; `GrammarPlugin.getDefaultDisplayStyle(kind)` and `getObjectKinds()` are default methods
  (`API_VERSION` unchanged). Notebook (IGT, lexical entries) and tag grammars give their former
  colours as defaults; conll, markdown-heading and mediaref only declare their kinds (the lex
  module has no plugin class).
- The project **name and description were never written to the .mbx file** (found while testing
  the title): `<project name="…">` and a `<description>` child are now saved and loaded.
- "Save project as…" is a plain file chooser followed by the save (`MainController.saveProjectAs`,
  also used by the close dialog for a never-saved project); `CreateProjectDialog` (file chooser,
  then Project properties, saved only if that dialog was saved) was removed.
- `WorkbenchCommands.closeFile` / `closeAllFiles` became `removeFile` / `removeAllFiles` (menu
  items `removeFileMenuItem`, `removeAllFilesMenuItem`); the S5 confirmation is in
  `removeAllFiles`.
- `ModeHost` also disables the shown mode's own menu (`enabledProperty`), not only the buttons and
  Mode menu items.
- Display dialog: properties are tri-state check boxes (grey = not set), colours have a "set"
  check box; strikethrough cannot be shown on a label, so the preview says "(struck through)".
- Legacy settings import: the old `igt_paragraph_color` / `lexical_description_color` are ignored
  with an INFO note.
- Not covered by GUI tests: the modal dialogs (`showAndWait`: Project properties with its Display…
  button, Display style, Settings, the close dialog itself — the fixture replaces the latter
  through `MainController.setCloser`), file choosers and drag and drop of a `.mbx` file.
- **Fixed after the implementation**: a project reopened at startup ("Reopen the last project")
  was not highlighted. `ProjectXMLSerializer.load` added (and parsed) the files before reading the
  file types, so they were parsed with no grammar; Open project hid it by calling
  `parseProject()` afterwards, the startup path did not (an older bug, made visible by the start
  page's check box). The loader now reads the file types first; `openProject` no longer parses
  again (`ProjectLoadParseTest`).

