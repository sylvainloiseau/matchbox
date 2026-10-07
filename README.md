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

Download the latest release from the [GitHub releases page](https://github.com/sylvainloiseau/matchbox/releases) for your OS.

### Prerequisite

A Java runtime environment (JRE or JDK), version 17 or later, available on your `PATH`.

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

The `markdown-heading-grammar` plugin reads the structure of Markdown notes (`.md` files of new
projects): headings make nested sections, and YAML blocks hold metadata.

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

## Create parser for new conventions

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

The artifacts are attached to the corresponding **Release** on GitHub (`.deb`, `.msi`, `.exe`, `.dmg` files according to the successful jobs).
