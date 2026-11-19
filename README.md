# matchbox -- an application for parsing field notes and building a corpus from plain text

Write field notes in plain text with lightweight conventions and automatically extract  annotated texts, lexical description, or any other corpus component, without copying them manually into dedicated apps.

## Scenario

This app experiments with an alternative way of working with linguistic notes (text, lexicon) using plain text.

The typical workflow for linguists working on a new language is to take notes --
following idiosyncratic conventions -- and later on to copy data (lexical
description, texts) into dedicated applications, such as FLEx or ELAN. This
workflow has the following drawbacks:

- the copy process is lengthy and tedious;
- due to that, a lot of field notes stay unexploited: there is not enough time to
  copy everything;
- once the main material has been extracted, the other pieces of information
  (context, references to drawings or photos, ideas...) written
  down in notebooks (manuscript or digital) are not looked at again. Primary
  linguistic material is separated from contextual material that was considered
  worth writing during the data session. Even
  some primary material (tested variants of construction...) can be lost because
  it does not fit well into the corpus building app;
- it is not easy to keep the link between a piece of information in a lexical
  database and the notebook pages it comes from. The timeline is then lost,
  and often it is difficult to know which is the last version for some pieces of data.

The matchbox application prototype offers a way to experiment with another workflow, which may be useful in some situations. The idea is to write digital notebooks in plain text, using lightweight conventions for interlinear gloss, lexical information, or other pieces of data. Thanks to those conventions the corresponding piece of information can be extracted from the files, aggregated, and converted into other formats for processing. The benefits of this method are:

- The field notes are the primary data; extracted information stays intertwined with the context it was written down in.
- Nothing is ``copied''. There is no need for a GUI for entering the data piece by piece.
- Field notebooks can be ordered chronologically so that an earlier version (the form of a lexeme) can be corrected later by a new one that supersedes it. The history of the versions is preserved.

The matchbox application offers several small parsers for matching information
according to various conventions -- and the list can be extended thanks to a
plugin system.

For instance, if you write down your field notes using the following format:

```
<mami> 'pig'
<jaflo> 'kanu'

:::
a mio-jaflo-m-wi-j
1sg down-canoe-top-m.sg-pred
'I went down in canoe'
:::
```

you can further use matchbox to extract the lexical information and add them to a LIFT dictionary; to extract text into a text database or to convert it into flextext (for ingestion by Flex), or Elan, etc. matchbox offers various ways to aggregate the information found into existing dictionaries or text databases.

# Installation

Download the latest release from the [GitHub releases page](https://github.com/sylvainloiseau/matchbox/releases) for your system. The installers include their Java runtime: nothing else is needed.

| System | File |
|---|---|
| macOS, Apple Silicon (M1 and later) | `Matchbox-<version>-apple-silicon.dmg` |
| macOS, Intel | `Matchbox-<version>-intel.dmg` |
| Windows | `Matchbox-<version>.exe` |
| Linux x86_64, Debian / Ubuntu | `matchbox_<version>_amd64.deb` |
| Linux ARM 64-bit, Debian / Ubuntu | `matchbox_<version>_arm64.deb` |
| Linux x86_64, any distribution | `Matchbox-<version>-x86_64.AppImage` |
| Linux ARM 64-bit, any distribution | `Matchbox-<version>-aarch64.AppImage` |

Each file comes with a `.sha256` checksum (`sha256sum -c Matchbox-<version>-x86_64.AppImage.sha256`).

An AppImage needs no installation: make it executable (`chmod +x Matchbox-*.AppImage`) and run it.

Once Matchbox is installed (DMG, EXE, DEB), a double-click on a project file (`.mbx`) opens it in Matchbox. A project file given on the command line (`Matchbox notes.mbx`) is opened at startup instead of the last project.

### First launch on macOS

The macOS application is **not notarized by Apple**, because notarization requires a paid
Developer ID. As a consequence, the first time you open the downloaded application, macOS
displays a warning of the form:

> *Apple could not verify "matchbox" is free of malware…*

This is expected and does not mean the application is broken. Downloading a file sets the
`com.apple.quarantine` attribute on it, and macOS refuses to run a quarantined application
that carries no notarization ticket. You only have to lift that restriction **once**; every
later launch works normally. Choose either of the two routes below.

#### Route 1 — System Settings (works on all recent macOS, including 15 Sequoia)

1. Open the downloaded `.dmg` and drag **matchbox.app** into your `/Applications` folder.
2. Double-click **matchbox** in `/Applications`. The warning above appears: dismiss it
   with **Done**.
3. Open **System Settings → Privacy & Security** and scroll to the bottom of the
   **Security** section, where a notice reads *"matchbox" was blocked…*.
4. Click **Open Anyway** and confirm with Touch ID or your password.
5. On macOS 15 (Sequoia) you may need to double-click the application a second time and
   confirm once more. After that, matchbox opens directly.

#### Route 2 — Terminal (a single command)

After copying **matchbox.app** to `/Applications`, run:

```bash
xattr -dr com.apple.quarantine /Applications/matchbox.app
```

This removes the quarantine attribute, so the notarization check no longer applies and the
application opens on a normal double-click.

> **Note:** up to macOS 14, right-clicking the application and choosing **Open**
> was enough to bypass this warning. Apple removed that Control-click override in macOS 15
> (Sequoia), so instructions you may find elsewhere telling you to right-click → Open no
> longer work — use one of the two routes above instead.

## Existing parser

Parsers exist for the following conventions:

### Simple lexical declaration:

```
```

### Interlinear glossed text (IGT)

The `igt-grammar` plugin reads interlinear glossed text between two `:::` lines, after the
[Leipzig Glossing Rules](https://www.eva.mpg.de/lingua/resources/glossing-rules.php) (Comrie,
Haspelmath, Bickel, 2008); every other line is ignored:

```
:::{id="1.1", olang="fra", mlang="en", media="O1.WAV", offset="1000"}
je crois pas qu'elle est assez curieuse
j'crois pas qu'elle est assez curieuse
1 90 300 420 610 700
ʒə=kʁwa pa k=ɛl ɛ ase kyRjø-z
1SG=believe not COMPL=3SG.F be\3SG.PRES enough curious-FEM
'I don't think she is curious enough'
:::
```

- The lines come in this order: **orthographic lines** (optional: the sentence in a spelling whose
  words need not match the transcription, not segmented), the **word level** (transcriptions,
  glosses, at most two timelines), the **morpheme level** (the same, words cut with `-` affix,
  `=` clitic, `~` reduplication, `<…>` infix), then **translations** between single quotes.
- Every segmented line has the same number of words; at the morpheme level, each word the same
  number of morphemes and the same boundaries. Lines that do not agree are marked (◆ in the margin,
  the Semantic issues tab, a `!` in the IGT view).
- A **timeline** has only numbers: milliseconds after `offset`; one timeline gives the starts (a
  word ends where the next starts, the last one at `end`, else at the next IGT), a second the ends.
- The role of a line is guessed (IPA letters → transcription, labels such as `1SG` → gloss, a leading
  line with another number of words → orthographic line) or declared with
  `setting="o@fra,t'IPA'@fra,g@en"` (`o` orthographic, `t` transcription, `g` gloss).
- `setting`, `olang`, `mlang`, `form`, `gloss` and `media` are **inherited**: a key an IGT does not
  give is taken from the previous IGTs, then from the enclosing sections, then from the file header.
- `form="2"` / `gloss="3"` (a line number or a line name of the setting) choose the lines compared
  with the context dictionary.
- `::: note` and `::: {.class}` are Pandoc divs, not IGTs. Tags written at the end of a line of an IGT
  apply to the IGT.

The **view** of an IGT shows its metadata (inherited values with their source), the orthographic
lines, the words in a grid (word level and morpheme level aligned in columns, ▶ to play a word,
a cell edits its word: a word that does not fit its column is not written without asking), and the
translations. The ✚ menu of the first line offers Format IGT (align the columns), Insert word,
Add metadata block, Declare line roles, Number the IGTs of this file.

**Gloss check** (Current project › Project properties… › Corpus tab, Gloss check): the grammatical labels of the
glosses are checked against the Leipzig list, Christian Lehmann's list of
[grammatical category labels](https://christianlehmann.eu/includes/gramm_category_labels.inc), both
or none, plus the project's own labels; with a **context dictionary** (a LIFT file, Project
properties › Inherited context), each (form, lexical gloss) pair of the morpheme level is looked up:
a form missing from the dictionary, or without a sense with that gloss, is reported with a fix,
"Declare this form", which writes `p /form/gloss` above the IGT (a notation that will be read later).
Current project › Reload context dictionary reads the file again.

The notebook grammar no longer reads `:::` blocks: a project whose file types use it gets the IGT
grammar added when it is opened (saved with the project).

### Text title and reference

### Link to audio file

### Conll

### Markdown sections and metadata

The `markdown-heading-grammar` plugin reads the structure of Markdown notes (`.md` files): headings make nested sections, and YAML blocks hold metadata.

```markdown
---
language: Tuwari
speaker: Elaisa
---

Session 1
=========

---
date: 2024-05-12T14:30
media: recordings/session1.wav
speakers: Elaisa, John
---

## Nouns
```

- Headings: `#` … `######` + title, or a single line underlined by `===` / `---`.
- Metadata: `key: value` lines between two `---` lines, on the first line of the file (header of the
  text) or right after a heading (metadata of the section); the objects of a section (IGTs, entries…)
  inherit them.
- In the section and header views, values can be edited, deleted (×) and added (`*`): `date` opens
  a date picker, `media` a file chooser with an audio/video player (or the browser for an http link),
  `speakers` shows badges with `+`. A file without header shows an "Add header" button in the
  editor margin (or type two `---` lines on the first line and parse).
- Media playback uses JavaFX Media (mp3, wav, aiff, m4a/aac, mp4/H.264). On Linux, AAC and H.264 need
  the system libavcodec (FFmpeg) libraries.

### Media references

The `mediaref-grammar` plugin reads one-line media markers, in any note:

```
@media recordings/s1.wav 12000-15500
@media "field trip/s1.wav" 0:12.000-0:15.500 greeting
@media 0:12.5-0:15
@media recordings/jaflo.wav pronunciation of jaflo
```

- `@media`, then a file and/or a range, then an optional caption. Times are milliseconds or clock
  times (`m:ss.mmm`, `h:mm:ss.mmm`). Without a range, the marker refers to the whole file; without a
  file, to the `media` metadata of the enclosing section, or of the header.
- A media file (after `@media`, or as the `media` of a header, a section or an IGT) is an absolute
  path, or a path relative to **the directory of the note**, or, if no such file is there, to **the
  project directory** (the directory of the `.mbx` file). A file chosen with a file chooser is
  written relative to the note's directory if it is inside it, else to the project directory, else
  absolute. When a file is not found, the views say where it was looked for ("looked in
  notes/rec/a.wav, then rec/a.wav"); a found file's tooltip says which directory it comes from. A path
  with spaces, or that looks like a time range, is written between double quotes.
- **Media check**: each media file written in a note that does not exist is a warning ("Media file
  not found: …") in the Semantic issues tab and the editor margin, on the file name; references that
  only inherit a missing file are not reported again. It is checked when the note is parsed: after
  copying a media file, use File Mode › Parse file.
- Each marker line has a ▶ button in the editor margin; `Shortcut+Shift+Space` (⇧⌘Space on macOS)
  plays the segment at the caret, or the nearest one above it in the same section. The view of a
  marker lets you edit its times (±100 ms, or from the player position), file and caption.

### Tags

The `tag-grammar` plugin reads inline tags, anywhere in a line, several per line:

```
'I went down in canoe' #"verb/motion"
#"to check" #“elicitation”
```

- `#` followed by a keyword between straight or curly quotes; `/` separates levels
  (`verb/motion`). Tags in fenced code blocks are ignored.
- A tag applies to the object (IGT, entry, media marker…) on the line just above it, else just below
  it (no blank line in between; lines containing only tags are skipped), else to the object it is
  written in (a sense, a section), else to the whole text. The view of an object shows its tags.
- The Tag tab of the objects table lists the tags; editing a cell renames that tag in the text.
- Avoid tags in the transcription and gloss lines of an IGT (they break the alignment) and in YAML
  metadata values (` #` starts a YAML comment there).

### Dictionary commands (LiftPatch)

Two plugins read commands that change a LIFT dictionary, written with the
[LiftPatch](https://github.com/sylvainloiseau/lift-patchbox) language:

```
Monday, with Paul. The word for pig:
c e("mami", s("pig"))          # create the entry mami with a sense "pig"
p /efe/door                    # make sure efe has a sense "door"
s /mami/pig (d@en = "A pig")   # set the definition of that sense
```

- **LiftPatch commands (concise)** reads the concise syntax (LiftPatchShort) among the other lines of
  your notes: a line is a command when it starts like one (`c`, `p`, `e`, `d`, `m`, `s`, `u`, `l` and
  what a command starts with); indented lines below a command form a block. If a line of prose looks
  like a command (`p /a/ before i…`), click ✚ **Declare LiftPatch sigil**: only the lines starting with
  `>` are then commands.
- **LiftPatch commands (reference)** reads the reference syntax (LiftPatchRef) in files holding
  commands only: files named `*.liftpatchref`, or declaring `%liftpatch 1.1 syntax="LiftPatchRef"`.
- The commands are applied, in the **order of the files** of the project and then of each text, to the
  **context dictionary** (Project properties › Corpus tab, Inherited context), or to an empty dictionary (its
  languages in Settings › Dictionary history). The context dictionary file is never changed:
  **Dictionary History › Export patched dictionary…** writes the result to a new file.
- **While any syntax or static error remains in the project, no command is applied** (the Parsing errors
  and Semantic issues tabs list them). Applying **stops at the first command that fails** (creating an
  entry that exists, changing one that does not…): its file is rolled back and the later commands are
  not applied; the error is in Semantic issues and in the margin.
- In File Mode the objects table shows, for each command, its entry and what became of it (Applied, No
  change, Error, Rolled back, Not applied, Not tried); the detail shows the entry as it was after the
  command, read-only, and **Show history of …**.
- **Dictionary History** ("History" button, Shortcut+3): the entries of the dictionary as the commands
  leave it (form and gloss, in the object and meta languages you choose; filter; changed, created or
  deleted entries), the selected entry, and the commands that shaped it; select a command to see the
  entry as it was after it, Enter to open it in the editor (Back returns). File Mode › **Show history of
  entry at caret** (⇧⌘H) opens the entry of a command or of the word at the caret.

## Projects

- Without an open project the window shows a start page: **New project**, **Open project…**, and your recent projects (click one to open it, × removes it from the list). Check "Reopen the last project at startup" to skip it. You can also drop a `.mbx` file on the window.
- **Project properties** (Current project › Project properties…) has five tabs: **General** (name, description), **File types** (the file types and their plugins), **Plugins** (display of each plugin), **Corpus** (inherited context, file order, gloss check) and **AI assistant**. A tab title shows • when it has unsaved changes and ⚠ when something prevents saving (Save then shows the faulty field). It reopens on the last tab used; messages such as "No context dictionary" have a link to the right tab.
- A new project has a single file type, "All files" (`.*\..*`: every file with an extension), with every installed plugin checked, in the order of their names; change it in **Current project › Project properties…** (File types tab).
- The **File** menu deals with project files (new, open, save, close) and the application (Settings…, Quit); the **Current project** menu with its content: Project properties…, adding files (several at once) and directories, creating a new empty file (**Create file…** ⌥⌘N, also "New file here…" on a directory of the file tree: it is added to the project, among the files of its directory when the directory's settings select it, and opened in the editor; an existing file is never overwritten), saving files, removing files and directories from the project (the files stay on the disk).
- **Closing a project** (or opening another one, or quitting) asks what to save: the project definition if it changed, and the modified files, each with a check box.
- **Project directory**: the directory containing the project file (`.mbx`). Files and directories inside it (or in its sub-directories) are saved in the project file with paths relative to it, so the project folder can be moved, copied or shared (e.g. with git) and still opens, on any system; files elsewhere are saved with their absolute path and marked with **!** in the file list ("This file is not in the project directory"). A project file written by hand may use relative paths, including `../`. After **Save project as…**, the status line says how many entries are outside the new project directory (Show selects the first one). File choosers open in the project directory.
- **Files and directories** are listed in File Mode as a tree, in path order: a directory added to the project groups its files, with its sub-directories; files added one by one stay apart. When you add a directory, choose which files it gives: glob patterns to include (`*.txt`) and exclude (`drafts/**`, `*-old.txt`), and whether sub-directories are included, with a preview. Right-click the directory later: **Edit directory settings…**, **Rescan directory** (files created since), **Remove this directory from the project**. A file of a directory is not removed alone: right-click it, **Exclude this file** (or a sub-directory: **Exclude this sub-directory**). These settings are saved in the project file. A pattern without "/" matches the file name in any sub-directory; case is ignored.
- **When several plugins read the same text**: a file is parsed by the plugins of every file type it matches, in the order of the list. A section (a Markdown heading and what follows it) and a tag may contain what other plugins read: an IGT inside a section, a tag inside an IGT. But two readings of the same characters by two plugins are not both kept: **the plugin listed first wins**; the other reading is not kept, and a warning says so (◆ in the margin, the Parsing errors tab, the **Overlaps** tab of File Mode, which lists them for the whole project; Enter shows both readings). Change the order of the plugins in Project properties (File types tab), or remove one.
- **Display of the objects**: in Project properties, the **Plugins** tab lists the plugins; **Display…** (for the plugins checked in a file type) sets the font family, bold, italic, underline, strikethrough, text and background colours, for all the objects of the plugin and, if you wish, for each kind of object (e.g. glossed sentences and lexical entries). The display of a plugin is the same in every file type; it is saved in the project file and kept when you uncheck the plugin.
- **Inherited context**: in Project properties (**Corpus** tab), choose a **Context dictionary** (a LIFT file) to which the lexical information of your project is to be added, and a **Context corpus** (a Matchbox corpus) to which its data is to be added. Both are saved in the project file; × removes them.
- **File order**: in Project properties (**Corpus** tab), the order of the files — of the dictionary history (the commands are applied in it) and of the file list: **by path** (the files and folders of a folder sorted by name together: `a/b/x.txt` before `a/y.txt`), **by last modification date** (saving an old file moves it, with its commands, to the end; copying or checking out files may change the dates), or **by the date of the file header** (`date: 2025-07-14`; undated files last). Ordered by a date, the file list is flat, with the folder and the date of each file; the files holding dictionary commands show their position in the history (1 · notes.txt).

## The text editor

- Scroll bars, line numbers in the margin (File Mode › Editor › Show line numbers), and **Wrap lines** for long lines; the font size changes with ⌘= / ⌘− / ⌘0 (Ctrl elsewhere). These options, the font family, the highlight of the current line, invisible characters (tabs and trailing spaces tinted) and auto-indentation are also in Settings › Editor, and apply to every file.
- The margin has the same width on every line, so columns stay aligned: line number, then ▶ (play the media segment of the line), ✚ (actions offered by plugins, e.g. Add header) a red ● on lines with parse errors and an amber ◆ on lines with warnings (point at it for the message; for a reading not kept, the text it covered is outlined). Marks beside the scroll bar show where the errors (red) and warnings (amber) are in the whole file; click one to go there. A thin blue band in the margin shows the lines of the section containing the cursor: sections are coloured on their heading only, so that the objects inside keep their own colours. The Parsing errors tab lists errors and, unless "Show warnings" is unchecked, warnings.
- **Problems**: the **Parsing errors** tab lists what does not follow a notation; the **Semantic issues** tab what follows it but is wrong or doubtful (a media range ending before it starts, an empty tag, a metadata key given twice, a CoNLL head outside its sentence). Check **All files** to list the problems of the whole project and go through them: Enter (or a double-click) opens the file at the problem, ◀ ▶ go to the previous / next one, Back returns. When a correction is obvious a **Fix** button applies it (also in the ✚ menu of the line, and ⌘. / Ctrl+. at the caret), as one change you can undo. F8 / Shift+F8 go to the next / previous problem of the file, Alt+F8 / Alt+Shift+F8 of the project. Export… writes the list as a CSV file.
- Keys follow your system. macOS: ⌘← / ⌘→ start / end of line, ⌘↑ / ⌘↓ start / end of file, ⌥← / ⌥→ by word, ⌘⌫ delete to the start of the line, ⌥⌫ delete a word. Windows / Linux: Home / End, Ctrl+Home / Ctrl+End, Ctrl+← / Ctrl+→. Add Shift to select. Home goes to the first non-blank character, then to column 1; Enter keeps the indentation of the line.
- **Find** (⌘F) opens a bar above the text: match case, whole word, regular expression, number of matches, Enter / ⇧Enter or ⌘G / ⇧⌘G for the next / previous one; **Replace** (⌥⌘F, Ctrl+H elsewhere) and Replace all (one undo step). **Go to line** (⌘L, or click "Ln, Col" above the text). **Lines**: duplicate (⌘D), delete (⇧⌘K), move up / down (⌥↑ / ⌥↓). **Insert special character…** (⇧⌘I): IPA letters, diacritics, tones…, searchable by name ("glottal", "tilde"), recently used first.
- Coming back to a file puts you where you were. File Mode › Keyboard shortcuts lists all the shortcuts.

## Modes, undo and navigation

- **Tag Browser** ("Tags" button, Shortcut+2): all the tags of the project as a tree of their levels (`#"pos/verb/transitive"` → pos › verb › transitive) with the number of tags of each node. Select a node to see its occurrences with a few lines of context (Before / After), filter them by the type of object they apply to, and click **Edit** (or double-click) to open the tag in File Mode; Back returns. **Rename** (F2) renames a level everywhere; **drag and drop** a node onto another (or **Move to…**, F6) moves it with its sub-tags, merging with a node of the same name if there is one. Both show what will change, change every file as one undo step, and save nothing. Right-click a tag badge in any view: "Show all … tags".
- **Metadata** ("Metadata" button, Shortcut+4): every metadata block of the project in one table — the header of a file and the block of a section (`---` … `---`), the `{key="value"}` block of an IGT, the `# key = value` comments of a CoNLL sentence — one row per block, one column per key. Columns **File**, **Type**, **Object**, **Line**, then the keys (`Media` and `media` are two columns, marked ⚠). Each column has a **filter** under its title (⚙: contains / equals / regular expression, match case, empty or non-empty cells only, invert); the search field looks in every cell; click a title to **sort** (Shift+click adds a second key); drag a key column by its title, or ⌥⇧← / ⌥⇧→, to **move** it; **Columns** shows or hides keys and orders them (first appearance, frequency, A–Z). **Edit a cell** by double-clicking it, pressing Enter or F2, or just typing: Enter writes the value into the file in the block's own syntax (one undo step, nothing saved); clearing a cell (or Delete) removes the entry; *Set empty value* writes an empty one. Typing in a row without block (**＋**, shown while *Objects without metadata* is checked) creates the block with that entry; a file without header gets one (when one of its plugins can write it). While typing, the values already used in the column are offered (↑ ↓, Enter). `date` and `media` cells have a date picker and a file chooser. Several cells at once: select them (Shift / ⌘ click), then **Fill down** (⌘D), **Set value…**, Delete, or paste a block copied from a spreadsheet (⌘V); **Rename key…** (F6) renames a key in every block. Changes of several blocks or files are previewed, made all or nothing, and undone in one step. *Inherited values* shows, greyed, what an object inherits from its section, the header or a previous IGT; cells with a problem (e.g. a missing media file) are outlined, ⌘. applies its fix. *Group by file / type*; **Export CSV…**, ⌘C copies as TSV. Double-click a File/Type/Object/Line cell or ⌘E to see the block in File Mode (Back returns); File Mode › *Show metadata of the object at caret* (⇧⌘M) does the reverse. Column order, widths, hidden keys and sort are remembered per project; filters are not.
- **Modes** are the ways to browse the corpus in the main area of the window, chosen with the buttons of the toolbar or the Mode menu (Shortcut+1…). **File Mode** shows three columns: the files of the project, the editor, and the objects found in the file with its problems (Objects, Parsing errors, Semantic issues, Overlaps). Each mode keeps its state when you switch to another one, and adds its own menu next to Mode (e.g. "File Mode": search in the file, the AI assistant at the cursor).
- **Panels**: the buttons at the right end of the toolbar (**Files**, **Editor**, **Objects** in File Mode; **Tags**, **Occurrences** in the Tag Browser) show or hide the panels of the mode; also in the mode's menu › Panels, with ⌥⌘1 / ⌥⌘2 / ⌥⌘3 (Ctrl+Shift+F1 / F2 / F3 elsewhere) and ⌥⌘0 (Ctrl+Shift+F4) to show all. Double-click a divider to collapse the narrower panel next to it, and again to bring it back. File Mode › Panels also has layouts in one step (Writing: editor only; Reviewing: editor and objects; Browsing: files and objects), "Objects under the editor" for the former arrangement, and "Reset panel sizes". Going to a place in a file (a problem, Find, Go to line, "show in editor") shows the editor again. The panels and their sizes are remembered. In a narrow panel the problem tables show their essential columns; hover a message for the details.
- **Undo/Redo is project-wide**: Edit › Undo (or Ctrl+Z / ⌘Z) reverts the last change made anywhere in the project, typed in the editor or made in a table or a view, whatever the file. The menu says what will be undone and in which file, e.g. "Undo Edit Translation (notes.txt)". When the change was in a file that is not shown, the status bar says so, with a **Show** link.
- **Nothing is saved automatically**: changes made by views or undo in files that are not displayed mark them as unsaved (`*` in the file list); use Save all files. You are asked about unsaved files before closing them or quitting.
- **Back/Forward** (◀ ▶ in the toolbar, Mode menu, Shortcut+Alt+← / →) return to where you were before a jump, e.g. after "show in editor" from a view.

## Create parser for new conventions

Each parser is a plugin that maps a notation to corpus objects: lexical entries, glossed sentences, sections, tags… Several plugins can offer different notations for the same kind of object, for instance a quick convention for writing lexical entries during a session and a more detailed syntax for full entries. Choose, for each type of file, the plugins that match the conventions you write in. The rest of the application (browsing, tags, exports) works the same whatever notation created the objects. See `matchbox-grammar-api/README.md`.

## 

A set of parsers, matching various conventions, can be associated with your fieldnote files. For instance, it is possible to add markdown-like headings for organizing texts.

```
# data session : lexicon
---
date:
type:text
---

<mami> 'pig'
<jaflo> 'kanu'

'''
a mio-jaflo-m-wi-j
1sg down-canoe-top-m.sg-pred
'I went down in canoe'
'''
```


If your lexical information is more structured, you can use a more complete language for defining lexical information:

```
# Lexical data session : lexicon
---
date:
type:text
---

//mami/pig/d@en 'wild or domesticated pig'


:::
a mio-jaflo-m-wi-j
1sg down-canoe-top-m.sg-pred
'I went down in canoe'
:::
```


## Mixing annotation

## Adding to existing database

if not already present

# Run in dev mode

```
mvn -pl matchbox-gui javafx:run   

mvn -e -pl matchbox-distribution -am clean package  
mvn -pl matchbox-distribution package
mvn -pl matchbox-distribution org.apache.maven.plugins:maven-assembly-plugin:single

mvn -pl matchbox-distribution help:evaluate -Dexpression=project.build.directory

mvn -pl grammars/matchbox-grammar-notebook package
mvn -pl matchbox-gui -am clean package

bash build/assembly/matchbox-distribution-1.0.0-distribution/bin/matchbox
```

### Settings and logs

Settings, recent files and logs are stored in the standard per-user locations of each OS:

| OS | Settings (`settings.json`) | Recent files (`recent.json`) | Logs |
|---|---|---|---|
| macOS | `~/Library/Application Support/Matchbox/` | same | `~/Library/Logs/Matchbox/` |
| Windows | `%APPDATA%\Matchbox\` | `%LOCALAPPDATA%\Matchbox\` | `%LOCALAPPDATA%\Matchbox\Logs\` |
| Linux | `~/.config/matchbox/` (or `$XDG_CONFIG_HOME`) | `~/.local/state/matchbox/` (or `$XDG_STATE_HOME`) | `~/.local/state/matchbox/logs/` |

`-Dmatchbox.settings.dir=<dir>` (or the environment variable `MATCHBOX_SETTINGS_DIR`) stores everything in one directory instead, e.g. for a portable installation.

At the first start, the settings of older versions (`~/.config/matchbox/configuration.txt` and `*.history`) are imported, then these files and `~/.matchbox.log` are deleted.

### Grammar plugins

Grammar plugins are jars in the `plugins` directory. Plugins built before version 2 of the grammar API (package `fr.cnrs.lacito.matchbox.language.corpusObject`) are not loaded and must be rebuilt; the log names the incompatible jars.

### Releases GitHub

A GitHub workflow (`.github/workflows/release.yml`) automatically builds the installers for **Linux**, **Windows** and **macOS** when a **tag** is pushed, for example :

```bash
git tag v1.0.0
git push origin v1.0.0
```

The release is named after the tag, and the installers take its version (`v1.2.0` → `1.2.0`). jpackage needs a numeric version whose first number is at least 1: a tag such as `v0.9` or `v1.2.0-beta` stops the workflow at its first step.

Five jobs run in parallel and attach their files, each with a `.sha256` checksum, to the release: a DEB and an AppImage for Linux x86_64 (`ubuntu-latest`) and aarch64 (`ubuntu-24.04-arm`), an EXE for Windows, a DMG for Apple Silicon (`macos-latest`, `-apple-silicon`) and for Intel (`macos-26-intel`, `-intel`). The tests run in every job.

**Actions › Release › Run workflow** builds the same installers with the version of the pom and keeps them for 7 days as artifacts of the run, without creating a release: a way to try the packaging before tagging.

Locally, `mvn install` builds the installer of the current system in `build/target/installer` (DMG on macOS, EXE on Windows, DEB on Linux); `-Dapp.version=1.2.3` sets its version, `-Djpackage.type=…` its type.
