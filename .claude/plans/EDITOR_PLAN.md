# Plan: File Mode text editor — scroll bars, line wrapping, line numbers, standard shortcuts

Status: implemented. All questions decided (§11.1); all suggestions accepted (§12). What differs
from the plan: §13.

## 0. Goal

The editor of File Mode (`EditorController`, `InlineCssTextArea fileContentCodeArea`) gets what
users expect of a text editor:

- vertical scroll bar (and a horizontal one when lines are not wrapped);
- a "Wrap lines" option;
- a "Show line numbers" option (numbers in the left margin);
- standard shortcuts to reach the start/end of the line and of the file, and to move by word,
  following the conventions of each system;
- the options remembered between sessions.

Plus all the improvements of §12 (accepted). Out of scope: syntax colouring beyond the display
styles (already done), code folding, multiple cursors, spell checking.

## 1. Architectural assessment

**P1. No scroll bars.** `FileMode.fxml` puts the `InlineCssTextArea` directly in
`editorSplitPane`. RichTextFX areas are virtualised and show scroll bars only inside a
`VirtualizedScrollPane` (flowless, already a dependency of RichTextFX 0.11.7). Without it, long
files can only be scrolled with the wheel or the caret, and the user cannot see where they are.
→ Wrap the area in a `VirtualizedScrollPane` (in `FileMode.initialize`, replacing the area in
`editorSplitPane`; the `fx:id` and every reference to `fileContentCodeArea` stay). The suggestions
panel, added to and removed from `editorSplitPane`, is unaffected.

**P2. The margin shifts the text.** `EditorController.refreshTextActions` installs a
`ParagraphGraphicFactory` that returns, for some paragraphs only, an `HBox` of ▶ play buttons and
text-action buttons ("Add header"…), and `null` for the others. A paragraph graphic sits left of
the text of its paragraph, so **lines with buttons are indented** and the others are not; IGT
columns no longer line up across such lines. Line numbers need the same factory.
→ One **fixed-width gutter** for every paragraph: line number (width from the number of digits of
the last line), then a marker column of fixed width (▶, and the error dot of S6). Text actions,
whose labels have variable widths, become a small "✚" button in the marker column opening a menu
of the actions of that line. The gutter is built by `EditorGutter` (layout computed by the pure `GutterLayout`),
so the text always starts at the same x. (Decided.)

**P3. Shortcuts are the same on every system.** `GenericStyledAreaBehavior` (RichTextFX) maps:
Home/End → line start/end, Shortcut+Home/End → file start/end, Shortcut+←/→ → previous/next
word, Shift variants extend the selection. On macOS, Shortcut is ⌘, so ⌘←/→ move by word (macOS:
line start/end), ⌘↑/↓ do nothing (macOS: file start/end), ⌥←/→ do nothing (macOS: word),
⌘⌫ deletes a word (macOS: to line start).
→ A pure `EditorKeymap` (platform → key combination → `EditorAction`), installed with
`Nodes.addInputMap` (maps added this way take precedence over RichTextFX's defaults, as the
existing Ctrl+Z map does). Windows/Linux keep the RichTextFX keys; macOS gets its own conventions,
Home/End and ⌘Home/⌘End still working (decided).

**P4. Visual line vs paragraph.** With wrapping on, "line start" must mean the visual line
(`lineStart`/`lineEnd` of `GenericStyledArea` already do), while "line number" means the paragraph
(the line of the file). The gutter numbers paragraphs; wrapped continuation lines have no number.

**P5. Options are view state, not project state.** Wrapping, line numbers, font size are the
user's preferences: `UserSettings`, nested record `EditorSettingsData` (like
`TagBrowserSettingsData`), global to all files and projects (decided). The File Mode menu toggles them;
the Settings dialog gets an "Editor" tab for the same values.

**P6. The caret is reset when switching files.** `EditorController` moves the caret to 0 when a
file is shown (`textArea.moveTo(0)`): coming back to a file loses the place. → S2: a pure `CaretMemory`
(document → caret, anchor, first visible paragraph), cleared when the project changes.

**P7. Invisible characters cannot be drawn as glyphs.** `InlineCssTextArea` renders the text as it
is; replacing spaces with "·" would change the document. → S12 shows tabs and trailing spaces as
a light hatched background (one more `StyleLayers` layer, under the display styles), not glyphs.

**P8. Error marks beside the scroll bar** need a component of their own: a narrow `ErrorRuler`
next to the `VirtualizedScrollPane` (in an `HBox`), marks at `paragraph / paragraphCount` of its
height (approximate when lines are wrapped). Fed by `Text.diagnostics()` of the displayed text.

**P9. The find bar replaces `SearchDialog`.** Its replace-all path (`EditorController
.applySearchEdits`, one undo entry) is kept; the matching becomes a pure `FindModel` (options:
case, whole word, regex; invalid regex reported, not thrown). Matches are highlighted by one more
`StyleLayers` layer (above the display styles, below the selection).

**P10. Line commands and auto-indent are text edits.** They compute `TextEdit`s in pure classes
(`LineCommands`, `Indentation`) and apply them through the editor (`replaceText`, forwarded to the
document as typing): one undo entry each, nothing bypasses `TextDocument`.

## 2. User interface

```
┌ File Mode ────────────────────────────────────────────────────────────────────┐
│ [Save] [Parse] [Close] | [Suggestions]                       Ln 12, Col 5     │
│ ┌───────────────────────────────────────────────────────────────────────┬──┐ │
│ │  9    │ <mami> {pig}                                                  │▲ │ │
│ │ 10    │                                                               │█ │ │
│ │ 11 ✚  │ ---                                                           │  │ │  ← ✚: text actions of the line (menu)
│ │ 12 ▶  │ @media 0:01.200-0:03.400 greeting                             │■ │ │  ← ▶ play; ■ error mark on the ruler (S6)
│ │ 13 ●  │ a mio-jaflo-m-wi-j                                            │  │ │  ← ● parse error (tooltip) (S6)
│ │       │   (continuation of a wrapped line: no number)                 │▼ │ │
│ └───────────────────────────────────────────────────────────────────────┴──┘ │
└───────────────────────────────────────────────────────────────────────────────┘
```

**File Mode menu** (new group "Editor"):

| Item | Kind | Shortcut |
|---|---|---|
| Find… (S13, replaces Search…) | opens the find bar | ⌘F / Ctrl+F |
| Find next / previous | | ⌘G / ⇧⌘G, F3 / ⇧F3 |
| Replace… | find bar with the replace row | ⌥⌘F / Ctrl+H |
| Go to line… (S1) | | ⌘L / Ctrl+L |
| Insert special character… (S9) | | ⌃⌘Space / Ctrl+Shift+U |
| — Editor — | | |
| Wrap lines | check item | ⌥Z / Alt+Z |
| Show line numbers | check item | — |
| Highlight current line (S4) | check item | — |
| Show invisible characters (S12) | check item | — |
| Zoom in / Zoom out / Actual size (S3) | | ⌘+ / ⌘− / ⌘0 |
| — Lines (S7) — | | |
| Duplicate line | | ⌘D / Ctrl+D |
| Delete line | | ⇧⌘K / Ctrl+Shift+K |
| Move line up / down | | ⌥↑ / ⌥↓ (Alt+↑/↓) |
| Keyboard shortcuts (S10) | opens the help window | — |

The accelerators are checked against the window's (`ModeHost.checkAccelerators` logs clashes):
none of these is used today (⌘N/O/S/W/, ⌘1/2, ⌘Z/Y, ⇧⌘Space, Ctrl+Space, F2, F6, F8, ⌥Enter).

**Shortcuts** (`EditorKeymap`; "select" = with Shift, extends the selection):

| Action | macOS | Windows / Linux |
|---|---|---|
| Line start / end | ⌘← / ⌘→, Home / End, Ctrl+A / Ctrl+E | Home / End |
| File start / end | ⌘↑ / ⌘↓, ⌘Home / ⌘End | Ctrl+Home / Ctrl+End |
| Previous / next word | ⌥← / ⌥→ | Ctrl+← / Ctrl+→ |
| Page up / down | PgUp / PgDn, Fn+↑/↓ | PgUp / PgDn |
| Delete to line start | ⌘⌫ | — |
| Delete previous / next word | ⌥⌫ / ⌥⌦ | Ctrl+Backspace / Ctrl+Delete |

All the actions also exist with Shift (select). Smart Home (S8): Home (and ⌘← on macOS) goes to the
first non-blank character of the line, pressed again to column 1. Enter keeps the indentation of
the current line (S8). The shortcuts are listed in the "Keyboard shortcuts" window (S10).

**Find bar** (S13), above the editor, closed with Esc:
```
 Find [mio          ] 3 of 12  [↑][↓]  [Aa] [W] [.*]   [Replace ▸]                  [×]
 Replace [mi-o      ]          [Replace] [Replace all]
```
Aa match case, W whole word, .* regular expression (an invalid pattern turns the field red with
the message in a tooltip); all matches highlighted, the current one stronger; Replace all is one
undo entry (as today).

**Go to line** (S1): small dialog "Line (1–482):", Enter jumps (recorded for Back).
**Status** (S5): "Ln 12, Col 5 (34 selected)" at the right of the editor toolbar; click → Go to line.
**Special characters** (S9): a popup with a search field, recently used characters first, then
groups (IPA consonants, vowels, diacritics, tones, suprasegmentals, other); Enter or click inserts
at the caret; character name in a tooltip; recently used saved in the settings.

**Settings › Editor tab**: Wrap lines, Show line numbers, Highlight current line, Show invisible
characters, Auto-indent, Font size, Font family (monospaced by default; warning that proportional
fonts break IGT alignment).

Empty state: no file displayed → the editor shows "Choose a file in the list, or add files with
Current project › Add file…" (grey, centred) instead of an empty white area (S11).

## 3. Model (pure)

| Class | Role |
|---|---|
| `settings.EditorSettingsData` (record) | `wrapLines` (false), `lineNumbers` (true), `fontSize` (13, 8–40), `fontFamily` (null = monospaced), `highlightCurrentLine` (true), `showInvisibles` (false), `autoIndent` (true), `recentCharacters` (≤ 24); `defaults()`, `validated(problems)` |
| `gui.mode.fileMode.EditorAction` (enum) | LINE_START, LINE_END, FILE_START, FILE_END, WORD_LEFT, WORD_RIGHT, DELETE_TO_LINE_START, DELETE_WORD_LEFT, DELETE_WORD_RIGHT… each with `select` variant |
| `gui.mode.fileMode.EditorKeymap` | `forPlatform(Platform)`: list of (KeyCombination, EditorAction); `Platform.current()` from `os.name`; no JavaFX toolkit needed |
| `gui.mode.fileMode.GutterLayout` | digits for a line count, marker column contents of a paragraph (play, actions, error) from the parsed text — testable without controls |
| `gui.mode.fileMode.CaretMemory` | S2: document id → caret, anchor, first visible paragraph |
| `gui.mode.fileMode.LineCommands` | S7: `TextEdit`s duplicating, deleting, moving the lines of a selection |
| `gui.mode.fileMode.Indentation` | S8: indentation to insert after Enter; smart Home target |
| `gui.mode.fileMode.FindModel` | S13: matches of a query with options in a text, current index, replacements (`TextEdit`s), invalid regex message |
| `gui.mode.fileMode.ErrorMarks` | S6: paragraphs with diagnostics and their messages; ruler positions |
| `gui.mode.fileMode.Invisibles` | S12: spans of tabs and trailing spaces |
| `gui.mode.fileMode.SpecialCharacters` | S9: the palette (groups, names) from a resource file, search by name or character |

## 4. View classes

| Class | Change |
|---|---|
| `FileMode` | wraps the area in a `VirtualizedScrollPane`; Editor items in its menu bound to the settings; status of line/column (S5) |
| `EditorController` | applies `EditorSettingsData` (wrap, font size, gutter on/off) and follows its changes; installs `EditorKeymap` (`Nodes.addInputMap`); the paragraph graphic factory is the gutter |
| `gui.mode.fileMode.EditorGutter` | builds the node of a paragraph: number label (fixed width, right aligned, grey), marker column (▶, ✚ menu, ●); refreshed when the digit count changes, after a parse (markers), when playback changes |
| `SettingsDialog` / `.fxml` | "Editor" tab |
| `gui.mode.fileMode.FindBar` | S13: the find/replace bar (replaces `SearchDialog`, removed) |
| `gui.mode.fileMode.ErrorRuler` | S6: marks beside the scroll bar, click jumps |
| `gui.mode.fileMode.GoToLineDialog` | S1 |
| `gui.mode.fileMode.SpecialCharacterPopup` | S9 |
| `gui.ShortcutsWindow` | S10: shortcuts of the editor (`EditorKeymap`) and of the menus (accelerators), for the current system |

## 5. Data flow

Toggle "Wrap lines" (menu or Settings) → `UserSettings.editorProperty()` (saved 500 ms later) →
`EditorController` listener → `textArea.setWrapText(…)`; the horizontal scroll bar disappears.
Parse result → `refreshTextActions` → gutter markers rebuilt (only visible paragraphs are created:
the area is virtualised). Key press → `EditorKeymap` input map → `GenericStyledArea` navigation
(`lineStart`, `start`, `wordBreaksForwards`…, selection policy) or a deletion through
`replaceText` (forwarded to the document as typing, one undo entry like any typing).

## 6. Settings

`SettingsData` gets `EditorSettingsData editor` (nullable in the JSON: defaults). `UserSettings`:
`editorProperty()`. Validation: font size clamped to 8–40, other fields default when missing.

## 7. Implementation steps

1. `VirtualizedScrollPane` around the editor (P1); empty state (S11).
2. `EditorSettingsData` + `UserSettings` + Settings "Editor" tab; wrap lines, font size and family,
   zoom (S3); File Mode "Editor" menu items.
3. Gutter (`GutterLayout`, `EditorGutter`): line numbers, fixed marker column, ▶ and the ✚ menu
   of text actions; error dots (S6) and `ErrorRuler`.
4. `EditorAction`, `EditorKeymap` (system conventions), smart Home and auto-indent (S8).
5. Navigation: `CaretMemory` (S2), Go to line (S1), line/column status (S5), current line (S4).
6. `LineCommands` (S7); invisible characters (S12).
7. `FindModel` + `FindBar` (S13), `SearchDialog` removed.
8. `SpecialCharacters` + popup (S9); `ShortcutsWindow` (S10).
9. CLAUDE.md, README.md; plan status.

## 8. What is not changed

`TextDocument` and the edit flow (typing → document → journal), highlighting by display styles
(`StyleLayers`, which gets more layers but no change of rule), `TextActionProvider` API (plugins still return `TextAction`s; only their
presentation changes), the assistant's ghost text, the suggestions panel.

## 9. Tests

- `EditorKeymapTest`: macOS maps ⌘→ to LINE_END and ⌥→ to WORD_RIGHT; Windows keeps Ctrl+→ for
  WORD_RIGHT and Home/End; every action has its Shift variant; no two actions share a key.
- `EditorSettingsDataTest` (in `SettingsStoreTest`/`UserSettingsTest`): defaults, font size
  clamped, missing record → defaults.
- `GutterLayoutTest`: digits (9 → 1, 10 → 2, 1000 → 4); a paragraph with a media segment and a
  text action gets ▶ and ✚.
- `CaretMemoryTest`, `LineCommandsTest` (duplicate/delete/move first and last line, selection over
  several lines, file without final newline), `IndentationTest`, `FindModelTest` (case, whole word,
  regex, invalid regex, replace all edits, zero-length matches skipped), `ErrorMarksTest`,
  `InvisiblesTest`, `SpecialCharactersTest` (resource loads, search by name).
- GUI (TestFX): the area's parent is a `VirtualizedScrollPane` with a visible vertical bar for a
  long file; "Wrap lines" toggles `isWrapText` and is remembered in the settings; line numbers
  shown/hidden; every paragraph's gutter has the same width; pressing the platform's "file end"
  keys moves the caret to the end (key presses work headless with Monocle); the find bar opens with
  ⌘F/Ctrl+F, counts matches and Replace all is one undo entry; switching files and back restores
  the caret; Go to line moves the caret. Not headless: the visual result of the hatched invisibles
  and of the current-line highlight (checked by their styles only).

## 10. Files

New: `EditorSettingsData`, `EditorAction`, `EditorKeymap`, `GutterLayout`, `EditorGutter`,
`CaretMemory`, `LineCommands`, `Indentation`, `FindModel`, `FindBar`, `ErrorMarks`, `ErrorRuler`,
`Invisibles`, `SpecialCharacters` (+ resource `special-characters.tsv`), `SpecialCharacterPopup`,
`GoToLineDialog`, `ShortcutsWindow`, tests. Removed: `SearchDialog`. Changed: `FileMode`,
`FileMode.fxml`, `EditorController`, `SettingsData`, `UserSettings`, `SettingsDialog` (+ fxml),
CLAUDE.md, README.md.

## 11. Questions

### 11.1 Decided with the user

- Options are global user settings (`EditorSettingsData`), toggled from the File Mode menu and the
  Settings "Editor" tab.
- Fixed-width gutter for every line: number, then a marker column with ▶, the ✚ menu of the line's
  text actions and the error dot.
- Shortcuts follow each system's conventions (macOS: ⌘←/→ line, ⌘↑/↓ file, ⌥←/→ word, ⌘⌫ to line
  start; Home/End and ⌘Home/⌘End kept).
- All suggestions S1–S13 accepted.

### 11.2 Open

None.

## 12. Suggestions (all accepted)

- **S1 Go to line…** (⌘L / Ctrl+L): a small dialog "Line (1–482):", the caret goes to the start
  of the line, recorded for Back.
- **S2 Place kept per file**: caret, selection and scroll position of each document remembered
  while the project is open; coming back to a file restores them (replaces `moveTo(0)`).
- **S3 Zoom and font**: ⌘+ / ⌘− / ⌘0 change the font size (saved); font family setting
  (monospaced by default, warning that proportional fonts break IGT alignment).
- **S4 Current line highlight**: a light background on the caret's line (paragraph style, under
  the display styles).
- **S5 Line and column in the toolbar/status**: "Ln 12, Col 5 (34 selected)"; click → Go to line.
- **S6 Parse errors in the margin and on the scroll bar**: a red ● in the marker column of lines
  with diagnostics (tooltip: the messages), and marks beside the vertical scroll bar showing where
  the errors are in the whole file (click → jump). Uses `Text.diagnostics()`; the errors tab stays.
- **S7 Line editing commands**: duplicate line (⌘D / Ctrl+D), delete line (⌘⇧K / Ctrl+Shift+K),
  move line up/down (⌥↑/↓ / Alt+↑/↓), each one undo entry.
- **S8 Auto-indent and smart Home**: Enter keeps the indentation of the current line; Home goes to
  the first non-blank character, then to column 1.
- **S9 Insert special character…** (IPA and diacritics palette, recently used first, search by
  name): inserts at the caret; useful for transcriptions.
- **S10 Keyboard shortcuts help**: a window listing the editor and window shortcuts of the current
  system (from `EditorKeymap` and the menus' accelerators).
- **S11 Empty state** of the editor when no file is displayed.
- **S12 Show invisible characters**: tabs (→), trailing spaces (·), useful to check IGT alignment.
- **S13 Find bar** inside the editor instead of the Search / Replace window: match case, whole
  word, regular expression, all matches highlighted with their count, ⌘G / F3 next.

## 13. Differences between the plan and the code

- **No ⌥Z for "Wrap lines".** On macOS ⌥ + letter types a character (⌥Z = Ω) that the editor
  would insert besides running the menu item; the option is toggled from File Mode › Editor and
  Settings › Editor only.
- **Insert special character… is ⇧⌘I / Ctrl+Shift+I**, not ⌃⌘Space (the macOS emoji picker) or
  Ctrl+Shift+U (Unicode input on Linux).
- **Find next has one accelerator, ⌘G / Ctrl+G** (no F3); Enter / ⇧Enter in the find field also
  go to the next / previous match. Zoom in is ⌘= (the ⌘+ key without Shift).
- **The menu groups**: line commands in a "Lines" sub-menu, options and zoom in an "Editor"
  sub-menu of File Mode (with Auto-indent as a check item too).
- **Line end and smart Home work on the line of the file** (paragraph), also when lines are
  wrapped; the other movements (word, page) are RichTextFX's.
- **`ErrorMarks` is part of `GutterLayout`** (`Markers.errors()`, `rulerPosition`), and a
  `LineIndex` class (line of an offset, start/end of a line) serves the pure classes.
- **New `EditOrigin.Kind.COMMAND`** for the line commands (one undo entry, never merged with
  typing); their journal labels are "Duplicate line", "Move line down"…
- **Bug fixed**: `EditorController.selectRange` with an empty range selected one character, so
  "show in editor" of a position (Back/Forward, Go to line) put the caret one character too far.
- **The default font of the editor is now monospaced** ("monospace"), as planned; before, no font
  was set (the system's proportional font).
- Not checked by the tests: the visual result of the current-line background, the tinted invisible
  characters and the ruler marks (checked once on a headless snapshot); the special character
  popup (its palette is unit-tested).

