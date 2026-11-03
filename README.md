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

### Simple IGT

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
- Relative paths are resolved against the project directory, then the directory of the note. A path
  with spaces, or that looks like a time range, is written between double quotes.
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

## Projects

- Without an open project the window shows a start page: **New project**, **Open project…**, and your recent projects (click one to open it, × removes it from the list). Check "Reopen the last project at startup" to skip it. You can also drop a `.mbx` file on the window.
- A new project has a single file type, "All files" (`.*\..*`: every file with an extension), with every installed plugin checked, in the order of their names; change it in **Current project › Project properties…**.
- The **File** menu deals with project files (new, open, save, close) and the application (Settings…, Quit); the **Current project** menu with its content: Project properties…, adding files (several at once) and directories, saving files, removing files and directories from the project (the files stay on the disk).
- **Closing a project** (or opening another one, or quitting) asks what to save: the project definition if it changed, and the modified files, each with a check box.
- **Files and directories** are listed in File Mode as a tree: a directory added to the project groups its files, with its sub-directories; files added one by one stay apart. When you add a directory, choose which files it gives: glob patterns to include (`*.txt`) and exclude (`drafts/**`, `*-old.txt`), and whether sub-directories are included, with a preview. Right-click the directory later: **Edit directory settings…**, **Rescan directory** (files created since), **Remove this directory from the project**. A file of a directory is not removed alone: right-click it, **Exclude this file** (or a sub-directory: **Exclude this sub-directory**). These settings are saved in the project file. A pattern without "/" matches the file name in any sub-directory; case is ignored.
- **Display of the objects**: in Project properties, the **Plugin parameters** section lists the plugins; **Display…** (for the plugins checked in a file type) sets the font family, bold, italic, underline, strikethrough, text and background colours, for all the objects of the plugin and, if you wish, for each kind of object (e.g. glossed sentences and lexical entries). The display of a plugin is the same in every file type; it is saved in the project file and kept when you uncheck the plugin.
- **Inherited context**: in Project properties, choose a **Context dictionary** (a LIFT file) to which the lexical information of your project is to be added, and a **Context corpus** (a Matchbox corpus) to which its data is to be added. Both are saved in the project file; × removes them.

## The text editor

- Scroll bars, line numbers in the margin (File Mode › Editor › Show line numbers), and **Wrap lines** for long lines; the font size changes with ⌘= / ⌘− / ⌘0 (Ctrl elsewhere). These options, the font family, the highlight of the current line, invisible characters (tabs and trailing spaces tinted) and auto-indentation are also in Settings › Editor, and apply to every file.
- The margin has the same width on every line, so columns stay aligned: line number, then ▶ (play the media segment of the line), ✚ (actions offered by plugins, e.g. Add header) and a red ● on lines with parse errors (point at it for the message). Red marks beside the scroll bar show where the errors are in the whole file; click one to go there.
- Keys follow your system. macOS: ⌘← / ⌘→ start / end of line, ⌘↑ / ⌘↓ start / end of file, ⌥← / ⌥→ by word, ⌘⌫ delete to the start of the line, ⌥⌫ delete a word. Windows / Linux: Home / End, Ctrl+Home / Ctrl+End, Ctrl+← / Ctrl+→. Add Shift to select. Home goes to the first non-blank character, then to column 1; Enter keeps the indentation of the line.
- **Find** (⌘F) opens a bar above the text: match case, whole word, regular expression, number of matches, Enter / ⇧Enter or ⌘G / ⇧⌘G for the next / previous one; **Replace** (⌥⌘F, Ctrl+H elsewhere) and Replace all (one undo step). **Go to line** (⌘L, or click "Ln, Col" above the text). **Lines**: duplicate (⌘D), delete (⇧⌘K), move up / down (⌥↑ / ⌥↓). **Insert special character…** (⇧⌘I): IPA letters, diacritics, tones…, searchable by name ("glottal", "tilde"), recently used first.
- Coming back to a file puts you where you were. File Mode › Keyboard shortcuts lists all the shortcuts.

## Modes, undo and navigation

- **Tag Browser** ("Tags" button, Shortcut+2): all the tags of the project as a tree of their levels (`#"pos/verb/transitive"` → pos › verb › transitive) with the number of tags of each node. Select a node to see its occurrences with a few lines of context (Before / After), filter them by the type of object they apply to, and click **Edit** (or double-click) to open the tag in File Mode; Back returns. **Rename** (F2) renames a level everywhere; **drag and drop** a node onto another (or **Move to…**, F6) moves it with its sub-tags, merging with a node of the same name if there is one. Both show what will change, change every file as one undo step, and save nothing. Right-click a tag badge in any view: "Show all … tags".
- **Modes** are the ways to browse the corpus in the main area of the window, chosen with the buttons of the toolbar or the Mode menu (Shortcut+1…). **File Mode** lists the files of the project, shows a file in the editor with the objects found in it and the parsing errors. Each mode keeps its state when you switch to another one, and adds its own menu next to Mode (e.g. "File Mode": search in the file, the AI assistant at the cursor, insert).
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

mvn -pl matchbox-gui javafx:run   

mvn -e -pl matchbox-distribution -am clean package  
mvn -pl matchbox-distribution package
mvn -pl matchbox-distribution org.apache.maven.plugins:maven-assembly-plugin:single

mvn -pl matchbox-distribution help:evaluate -Dexpression=project.build.directory

mvn -pl grammars/matchbox-grammar-notebook package
mvn -pl matchbox-gui -am clean package

bash build/assembly/matchbox-distribution-1.0.0-distribution/bin/matchbox

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
