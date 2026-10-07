# Plan: `matchbox-grammar-mediaref` (media markers, segment view, shared media player)

Status: implemented (not committed). Decisions taken with the user in §12; open questions in §13; differences between the plan and the code in §14.

## 0. Goal

Let field notes point to their recordings, or to segments of them, and play them from Matchbox:

```markdown
---
media: recordings/session1.wav
---

# Session 1

@media 0:12.000-0:15.500 greeting
<mami> {pig}

@media recordings/session2.mp4 12000-15500
:::
a mio-jaflo-m-wi-j
1sg down-canoe-top-m.sg-pred
'I went down in canoe'
:::

@media recordings/jaflo.wav pronunciation of jaflo
<jaflo> {canoe}
```

- A **media marker** is one line: `@media`, then a media file and/or a time range, then an optional
  caption. Three forms: file and range, range only (the file comes from the metadata), file only
  (the whole file, for short recordings).
- Without a file, the file is the `media` metadata of the nearest enclosing section, else of the
  text header (markdown-heading grammar), as `Text.metadataOf` already computes.
- Markers implement a new capability, **`MediaSegment`** (a media file with an optional time range);
  every media segment, of this grammar or another one, is shown by a built-in view with a player
  (audio or video) and editable file, range and caption.
- In the editor, a **▶ button in the margin** of each marker line and a **keyboard shortcut** play
  the segment.
- A media file is opened **once** and its player shared by every view that plays it (segments and
  the `media` value of the metadata editor); only one plays at a time.

---

## 1. Design constraints (from the existing architecture)

- **A grammar sees only its own results.** The range-only form takes its file from the `media`
  metadata of another grammar (markdown-heading). Objects of several grammars are combined only after
  parsing, in `Text` (`Text.metadataOf(object)` = header properties overridden by the enclosing
  sections, innermost last). So the file of a segment is **resolved after parsing**, through
  `ViewContext.corpus()`, not by the grammar.
- **Relative paths** follow the rule of the metadata editor (`MediaPaths`): the project directory if
  the project is saved, then the directory of the text. A grammar does not know the project: one
  more reason to resolve after parsing.
- **No list of media on `TextHeader`.** A header is an immutable object produced by one grammar: it
  cannot know the markers of another grammar, and a text may have no header. The media files of a
  text are derived from the `Text` (§4.2), and "open each file once" is the job of a player cache
  keyed by resolved file (§5).
- **The segment view, the margin buttons and the shortcut are application features**: they work on
  the capability `MediaSegment`, so they belong to matchbox-gui (like `SectionViewFactory`), and the
  plugin stays a pure grammar, without JavaFX.
- **Coexistence:** lines starting with `@` are ignored by the notebook, markdown-heading and CoNLL
  grammars; this grammar ignores every line that is not a marker.

---

## 2. The convention (decided: `@media` line)

```
@media <file> [<start>-<end>] [<caption>]      file, with or without a range
@media <start>-<end> [<caption>]               range only: the file comes from the "media" metadata
```

- The line starts with `@media` (column 0), then spaces. A marker has a file, a range, or both;
  `@media` alone is a diagnostic.
- `<file>`: a path, relative or absolute, or a `file:` URI; a path containing spaces is
  double-quoted (`"field trip/s1.wav"`, `\"` and `\\` escaped).
- `<start>-<end>`: two times separated by `-` (spaces allowed around it; an en dash `–` is accepted).
  Each time is either:
  - **milliseconds**: `12000`;
  - **clock time**: `m:ss`, `m:ss.f…`, `h:mm:ss`, `h:mm:ss.fff` (1–3 decimals), e.g. `0:12.5`,
    `1:02:03.250`.
  Both are stored in milliseconds; the two times of a range may use different formats; end > start.
- **File only** (decided): `@media recordings/jaflo.wav` refers to the whole file; it plays from the
  beginning to the end.
- `<caption>` (optional): free text after the range, or after the file when there is no range, up to
  the end of the line (e.g. `greeting`, `story of the canoe, part 2`); trimmed; shown in lists, trees
  and the view.
- Reading rules: the first element after `@media` is a range if it has the form of one, otherwise the
  file; after the file comes a range if the next element has the form of one, otherwise the caption.
  So a file name that looks like a range (`1-2`, `0:12-0:15`) must be quoted; names with digits and
  dashes such as `session-2024-05.wav` need no quotes.
- `http(s)://` media: not played in this version (the view says so, with a link opening the
  browser, as in the metadata editor).

Examples:

```
@media recordings/s1.wav 12000-15500
@media "field trip/s1.wav" 0:12.000 - 0:15.500 greeting
@media 1:02:03.250-1:02:07
@media 15500–21300 story of the canoe, part 2
@media recordings/jaflo.wav
@media recordings/jaflo.wav pronunciation of jaflo
```

---

## 3. The grammar module

| Item | Value |
|---|---|
| Directory / artifact | `grammars/matchbox-grammar-mediaref` (root pom `<modules>`) |
| Package | `fr.cnrs.lacito.matchbox.grammar.mediaref` |
| Plugin id (never to change) | `mediaref-grammar` |
| Kind | `mediaref-grammar:marker` |
| Services | `GrammarPlugin` → `MediaRefGrammarPlugin` (no view: the built-in segment view applies) |
| Dependencies (`provided`) | `matchbox-corpus`, `matchbox-grammar-api` (no JavaFX); `antlr4-runtime` bundled; corpus test-jar for tests |

pom from the markdown-heading pom without the JavaFX dependencies (ANTLR plugin with visitor,
`.g4` files as resources for `getGrammarSource()`, copy of the jar to `build/plugins`).

### 3.1 ANTLR grammar

Lexer (`MediaRefLexer.g4`), with the lessons of the other grammars (longest match, whole lines,
end-of-line token emitted for a last line without line ending). A member predicate `rangeAhead()`
says whether a range (`TIME [ \t]* [-–] [ \t]* TIME`, followed by a space or the end of the line)
starts at the current position.

- default mode:
  - `MEDIA_START : {col 0}? '@media' [ \t]+ -> pushMode(MARKER)`;
  - `OTHER_LINE : {col 0 && not "@media"}? ~[\r\n]+` (one token per ignored line);
  - `NL`.
- mode `MARKER` (first element):
  - `RANGE : {rangeAhead()}? TIME [ \t]* ('-' | '–') [ \t]* TIME -> mode(CAPTION)`;
  - `QUOTED_FILE : '"' ('\\' . | ~["\\\r\n])* '"' -> mode(AFTER_FILE)`;
  - `FILE : ~[ \t\r\n"]+ -> mode(AFTER_FILE)`; with `RANGE` defined first, `12000-15500` is a range,
    while `session-2024-05.wav` (longer as a file) is a file;
  - `MARKER_END` (line ending) → `popMode` (`@media` alone: diagnostic).
- mode `AFTER_FILE`:
  - `WS -> skip`;
  - `RANGE_AFTER_FILE : {rangeAhead()}? TIME … TIME -> type(RANGE), mode(CAPTION)`;
  - `CAPTION_AFTER_FILE : {!rangeAhead()}? ~[ \t\r\n] ~[\r\n]* -> type(CAPTION)` (the predicate
    keeps a range from being read as a caption, which would otherwise be the longest match);
  - line ending → `type(MARKER_END), popMode`.
- mode `CAPTION`:
  - `WS -> skip`; `CAPTION : ~[ \t\r\n] ~[\r\n]*` (trailing spaces trimmed in Java);
  - line ending → `type(MARKER_END), popMode`.
- `fragment TIME : [0-9]+ | ([0-9]+ ':')? [0-9]+ ':' [0-9][0-9] ('.' [0-9] [0-9]? [0-9]?)?`.

Parser (`MediaRefParser.g4`):

```antlr
document : (marker | ignored)* EOF ;
marker   : MEDIA_START ( (FILE | QUOTED_FILE) RANGE? | RANGE ) CAPTION? MARKER_END ;
ignored  : OTHER_LINE NL? | NL ;
```

An `@media` line that does not match (nothing after `@media`, unquoted file with spaces) is reported
as a diagnostic on its line and produces no object.

### 3.2 Corpus objects

**Capability** (decided), in matchbox-corpus, `capability.MediaSegment`:

```java
/** A media file (audio or video), or a segment of it. */
public interface MediaSegment extends CorpusObject {
    /** Field names (see HasFields). */
    String MEDIA = "media";
    String RANGE = "range";
    String START = "start";
    String END = "end";
    String CAPTION = "caption";

    /** The media file as written; empty when it is given by the "media" metadata of the enclosing sections or header. */
    Optional<String> getMediaReference();
    /** The part of the media; empty for the whole media. */
    Optional<TimeRange> getRange();
    /** Free text describing the segment (e.g. "greeting"); empty if none. Not getLabel(), which every object has. */
    default String getCaption() { return ""; }
    default boolean isWholeMedia() { return getRange().isEmpty(); }
}

/** A time range in a media, in milliseconds (0 ≤ start < end). */
public record TimeRange(long startMs, long endMs) {
    public long durationMs() { return endMs - startMs; }
}
```

A marker has a file or a range (or both): the grammar never produces an object with neither.

Admission rule: playback (shared player, segment view, margin buttons, shortcut) and future exports
(ELAN) need it; other grammars (IGTs with time codes, an ELAN import) can implement it and get the
view, the player and the shortcut for free.

**Plugin class** `MediaMarker extends AbstractCorpusObject implements MediaSegment, HasFields`:
media reference (optional), range (optional), caption, the **format** of each time as written
(milliseconds, or clock with its number of decimals: edits keep it). Fields:
- `media`: span of the file (inside the quotes); empty span after `@media ` when absent;
- `range`: span of the whole range; empty span after the file when absent (file-only form);
- `start`, `end`: spans of the two times, only when there is a range;
- `caption`: span of the caption; empty span at the end of the line when absent.

Label: the caption if any, else `0:12.000–0:15.500` or `whole file`, plus the file name when explicit.

Diagnostics (with offsets): invalid time (e.g. `0:75`), end ≤ start, malformed line.

### 3.3 `TimeCodes` (matchbox-grammar-api, pure)

Shared by the plugin (parsing, writing back) and the GUI (display, nudges), next to `Spans`:

- `OptionalLong parse(String)` — milliseconds or clock time;
- `Optional<TimeRange> parseRange(String)` — `start-end` (`-` or `–`, spaces allowed);
- `Format formatOf(String)` — milliseconds, or clock with *n* decimals;
- `String write(long ms, Format)` — in a given format (edits keep the format of the time they replace);
- `String display(long ms)` — `m:ss.mmm` (hours when needed).

### 3.4 Plugin class `MediaRefGrammarPlugin`

`getResult` (lexer, parser, `CollectingErrorListener`, visitor, diagnostics; local state only),
`getFormatDocumentation()` (§2, for the AI assistant), `getExamples()` (the examples of §2, each
without diagnostics), `getGrammarSource()`, `getObjectEditor()` (§7), `getApiVersion()`.

---

## 4. Resolving the media file (after parsing, decided)

### 4.1 `MediaResolver` (matchbox-grammar-fx, package `media`, pure)

```java
/** The media file of a segment: its explicit file, otherwise the "media" metadata of its enclosing sections or header. */
public static Resolution resolve(MediaSegment segment, CorpusView corpus, Optional<Path> projectDirectory);

public record Resolution(Optional<String> reference, Source source, Optional<String> sourceTitle,
                         MediaPaths.Resolution file) {}
public enum Source { EXPLICIT, SECTION, HEADER, NONE }
```

- reference = `segment.getMediaReference()`, else `text.metadataOf(segment).get("media")`; the
  source (and the title of the section) says where it came from, shown in the view ("from section
  'Session 1'", "from the header").
- file = `MediaPaths.resolve(reference, projectDirectory, textDirectory)` (project directory, then
  directory of the text; absolute paths and `file:` URIs as is).
- A range-only marker in a text without `media` metadata: source `NONE`; the view explains how to fix
  it (write a file, or a `media:` entry in the section or header).

### 4.2 Media files of a text

```java
/** Distinct resolved files used by a text: its media segments and its "media" metadata (header, sections). */
public static List<Path> mediaFiles(Text text, CorpusView corpus, Optional<Path> projectDirectory);
```

This replaces the suggested `TextHeader` method (§1). Uses: the header view lists "Media used in
this text" (small addition to `TextHeaderViewFactory`), and later exports.

---

## 5. Shared players (decided: service via `ViewContext`)

### 5.1 `MediaPlayers` (matchbox-grammar-fx, package `media`)

```java
/** Players of media files, shared by all views: each file is opened once; one plays at a time. */
public interface MediaPlayers {
    /** The player of a file, opened on first use; the same instance for every caller. */
    Optional<MediaPlayer> player(Path file);
    /** Plays a range of a file, or the whole file (empty range), pausing any other player. */
    void play(Path file, Optional<TimeRange> range);
    void pause();
    /** What is playing (file and range), for the views and the editor to show their state. */
    ReadOnlyObjectProperty<Optional<Playback>> playingProperty();
    /** Closes every player (project closed, application stopped). */
    void closeAll();

    record Playback(Path file, Optional<TimeRange> range) {}
}
```

- `ViewContext.mediaPlayers()`: new default method (returns a private, unshared instance, for views
  used outside the application and tests); the GUI gives one `SharedMediaPlayers` per window,
  closed when the project changes and at exit.
- `SharedMediaPlayers`: cache path → `MediaPlayer`, created lazily; a bounded number of open files
  (e.g. 8, least recently used and not playing closed first) to limit native resources; errors of a
  file (`MediaPlayer.setOnError`) kept and reported to the views.
- Playback on a shared player: for a range, `setStartTime(start)`, `setStopTime(end)`,
  `seek(start)`, `play()`; for the whole file, start time 0 and stop time the total duration. Two
  views of the same file therefore drive the same player: `playingProperty()` tells each view
  whether *its* segment is the one playing.
- Video: each view has its own `MediaView` on the shared player (allowed by JavaFX).
- The cache logic is a generic class (`PlayerCache<P>`) with an injected factory, tested without
  the JavaFX media toolkit.

### 5.2 The metadata editor uses it too

`MediaPlayerBox` (metadata editor, `media` values) takes its player from
`ViewContext.mediaPlayers()` instead of opening the file itself: a recording named in a section's
metadata and in its markers is opened once. The box no longer disposes the player (the service owns
it); it only removes its listeners when it leaves the scene.

---

## 6. `MediaSegmentViewFactory` (built-in, matchbox-gui, decided)

Registered in matchbox-gui's `META-INF/services/fr.cnrs.lacito.matchbox.grammarfx.ObjectViewFactory`
for the capability **`MediaSegment`**: it applies to the markers of this plugin and to the segments of
any other grammar (closest-supertype rule; a plugin can still provide its own factory for its class).

- Detail view:
  - caption (editable) as title;
  - the media: file name (link: file chooser to change the explicit file), where it comes from
    (explicit / section "…" / header), "not found" or "format not supported" as in the metadata
    editor;
  - for a range: start, end, duration (`TimeCodes.display`); for the whole file: "whole file" and its
    duration once the player knows it;
  - play/pause, a progress slider within the range (or the whole file), time;
  - a `MediaView` for video files;
  - editing (decided):
    - with a range: start and end fields (ms or clock accepted), ±100 ms nudge buttons for each,
      "set start" / "set end" from the current player position, "whole file" (removes the range);
    - whole file: "mark a segment": "set start" and "set end" from the player position write a range
      (field `range`);
    - all edits go through `ViewContext.setField` with the field names of `MediaSegment`, so they
      work for any grammar whose `ObjectEditor` supports them; controls are disabled when `canEdit`
      is false or the object has no such field.
- `updateView`: keeps the view (and the player) after a re-parse, e.g. after a nudge.
- Table columns: Start (editable), End (editable), Duration, Caption (editable), Media (as written,
  or "(from section)"); Start/End/Duration show "whole file" / empty for the file-only form.

---

## 7. Editing (`MediaRefObjectEditor`, plugin)

- `start` / `end` (marker with a range): the value is parsed (ms or clock, `TimeCodes`); refused if
  invalid or if the range would become empty or inverted (end ≤ start); written in the format of the
  time it replaces (ms stays ms, clock keeps its decimals). Nudges send milliseconds.
- `range`: `start-end` sets or replaces the range (new ranges written as clock times with
  milliseconds, `m:ss.mmm`); an empty value removes it (whole file) — refused for a range-only marker,
  which would then have neither file nor range.
- `media`: an empty value removes the file — refused for a file-only marker; a value with spaces, or
  one that looks like a range, is quoted; adding a file to a range-only marker inserts it after
  `@media `. The file chooser writes a path relative to the project or the text when possible
  (`MediaPaths.toValue`).
- `caption`: no line break; empty removes it; setting it on a marker without caption appends
  ` <caption>` at the end of the line.
- No `addField`/`removeField` (the fields of a marker are fixed; `range` covers adding and removing
  the range).
- Round-trips (edit, parse again, same values) tested for every case.

---

## 8. Playing from the editor (built-in, matchbox-gui)

Two entry points, for every `MediaSegment` of the displayed text (no dependency on the plugin):

### 8.1 ▶ button in the margin (decided)

- Each line holding a media segment gets a small ▶ button in the editor margin (the paragraph
  graphic factory that already shows the text actions of plugins; both are shown when a line has
  both). Clicking plays the segment; the button of the segment that is playing shows ⏸ and pauses it.
- The buttons are computed after each parse of the displayed text (`Text.objectsOf(MediaSegment)`,
  line of each segment) and follow `playingProperty()`.
- A file that cannot be resolved: the button is greyed, with the reason as tooltip.

### 8.2 Keyboard shortcut (decided, not configurable)

- `Shortcut+Shift+Space` (Ctrl+Shift+Space on Linux/Windows, ⌘⇧Space on macOS; Ctrl+Space is the AI
  completion) plays the segment at the caret; pressed again while it plays, pauses it. Also in the
  editor's context menu and the Edit menu ("Play media segment at caret").
- Fixed for now: shortcut configuration will come with the planned configuration system for plugins.
- **Which segment**: the segment whose line contains the caret; otherwise the nearest segment above
  the caret within the same innermost section (so the caret anywhere in an IGT or an entry under a
  marker plays it); otherwise nothing, with a message in the status line.

### 8.3 Common behaviour

- Resolution and playback through `MediaResolver` and the shared `MediaPlayers`; a missing file or a
  range-only marker without `media` metadata is reported in the status line (shortcut) or the tooltip
  (button).
- While a segment plays, its marker line is highlighted in the editor.
- Implementation: `EditorController` (input map like undo/redo; margin graphics merged with the text
  actions), a pure helper `SegmentAtCaret.find(Text, offset)` (tested without JavaFX).

---

## 9. Changes outside the module

| Where | Change |
|---|---|
| root `pom.xml` | new module |
| matchbox-corpus | `capability.MediaSegment`, `capability.TimeRange` (+ `package-info`) |
| matchbox-grammar-api | `TimeCodes` |
| matchbox-grammar-fx | package `media`: `MediaPlayers`, `SharedMediaPlayers`, `PlayerCache`, `MediaResolver`; `ViewContext.mediaPlayers()`; `MediaPlayerBox` uses the service |
| matchbox-gui | `MediaSegmentViewFactory` (+ services file); one `SharedMediaPlayers` given by the `ViewContext` implementation, closed on project change and at exit; margin buttons, shortcut, menu items and highlight (`EditorController`, `SegmentAtCaret`) |
| matchbox-app | `Project.createDefaultProject`: the mediaref grammar added to "All files" when the plugin is loaded (`Project.MEDIAREF_PLUGIN_ID`); test with a fake plugin |
| markdown-heading plugin | header view: "Media used in this text" (`MediaResolver.mediaFiles`) |
| docs | `CLAUDE.md` (module, capability, built-in view, media service, margin buttons, shortcut), write-grammar skill (a grammar implementing `MediaSegment` with the `media`/`range`/`start`/`end`/`caption` fields gets the view, player, buttons and shortcut), `README.md` (the convention, buttons and shortcut) |

---

## 10. Tests

- Plugin: the three forms (file and range, range only, file only), each with and without caption;
  ms and clock times (`0:12`, `0:12.5`, `1:02:03.250`), mixed; spaces and en dash around the
  separator; captions with commas, digits or a second range; quoted files with spaces and escapes;
  file names with digits and dashes; a quoted file that looks like a range; an unquoted one read as a
  range; `file:` URI; invalid lines (`@media` alone, unquoted spaces, `0:75`, end ≤ start) →
  diagnostics, no object; other notations (Markdown, notebook, CoNLL, `@other`) ignored without
  diagnostics; CRLF, last line without line ending; independent results; examples without
  diagnostics; `assertSpansNested`, `assertKindsPrefixed`.
- Editing: start/end in ms and clock (format kept), refusals (invalid, inverted); range added to a
  file-only marker, replaced, removed (refused on a range-only marker); media added, changed,
  removed (refused on a file-only marker), quoted; caption added, changed, removed.
- `TimeCodes`: parse/format round-trips, ranges, formats, display.
- matchbox-grammar-fx: `MediaResolver` (explicit, from section, from header, none, relative to the
  project then the text) with `Text`s built from fake sections; `mediaFiles` (distinct);
  `PlayerCache` (one instance per file, LRU closing that spares the playing one, close all).
- matchbox-gui: registry picks `MediaSegmentViewFactory` for a `MediaMarker` and for another class
  implementing `MediaSegment`; `SegmentAtCaret` (on the marker line, below it in the same section,
  not across a heading, none).
- Build: full `mvn clean install`, jar content check (skill). Manual: play markers of an audio and a
  video file, a file-only marker, two markers of the same file (one player), a range-only marker under
  a section with `media:`, captions, nudges, "set from position", "mark a segment" on a whole file,
  undo; the margin buttons (play, pause, greyed); the shortcut on a marker line and in an IGT below
  it; metadata editor and markers playing the same file.

---

## 11. Phases

1. matchbox-corpus: `MediaSegment`, `TimeRange`; matchbox-grammar-api: `TimeCodes`; tests.
2. matchbox-grammar-fx: `media` package (resolver, cache, service), `ViewContext.mediaPlayers()`,
   `MediaPlayerBox` on the service; tests.
3. Plugin: grammar, `MediaMarker`, plugin class, editor; tests.
4. matchbox-gui: `MediaSegmentViewFactory`, service wiring and lifetime, margin buttons, shortcut and
   highlight; header view addition; matchbox-app default project; tests.
5. Docs; full build; manual check.

---

## 12. Decisions (taken with the user)

| Question | Decision |
|---|---|
| Convention | `@media <file> [<start>-<end>] [<caption>]` or `@media <start>-<end> [<caption>]` |
| File only | `@media <file>` refers to the whole file |
| Time format | Milliseconds or clock time (`h:mm:ss.mmm`), stored in ms |
| Caption | Optional free text after the range or the file (`MediaSegment.getCaption()`, field `caption`) |
| Model | Capability `MediaSegment` (file reference, optional `TimeRange`, caption) in matchbox-corpus + `MediaMarker` (plugin) |
| Resolution of the file | After parsing, through `ViewContext.corpus()`: explicit file, else `media` metadata (sections, then header), then `MediaPaths` |
| Players | `MediaPlayers` service given by `ViewContext`: one player per file, opened once, one plays at a time; also used by the metadata editor |
| Ranges | Closed when present (start and end required) |
| View | Built-in `MediaSegmentViewFactory` in matchbox-gui, registered for `MediaSegment`; the plugin has no view and no JavaFX |
| Editing | File, range (start, end), caption editable; nudges; set from player position; mark a segment on a whole file |
| Editor | ▶ button in the margin of each marker line; `Shortcut+Shift+Space` plays/pauses the segment at the caret (or the nearest one above in the same section); not configurable for now |
| Default project | Added to "All files" when the plugin is installed |
| `TextHeader` media list | Not added (§1); `MediaResolver.mediaFiles(text)` instead |

## 13. Open questions

1. **`@media` alone**: a diagnostic for now. Could later mean "the whole file of the `media`
   metadata" (e.g. to place a ▶ button under a section heading).
2. **Configuration**: the shortcut (and later other settings of this plugin) will be configurable
   through the planned configuration system for plugins.

## 14. Implementation notes (differences from the plan)

- **Unquoted file names with spaces** are not reported: after a file, the rest of the line is read as
  a range or a caption, so `@media my file.wav 0-5` is the file `my` with the caption
  `file.wav 0-5` (the view then shows "not found"). The rule is documented: quote such names.
- **Start of line**: the lexer checks the previous character rather than the column, because ANTLR
  does not reset the column after a lone `\r` (old Mac line endings).
- `MediaPlayers` has `play(file, Optional<TimeRange>)` (whole file = empty range) and `error(file)`;
  `MediaPlayerBox` moved to `grammarfx.media` (public), takes the file, an optional range and the
  service, and only follows the shared player while its own file and range play.
- The segment view is refilled by `updateView` (the player is shared, so playback continues);
  "mark a segment" on a whole file keeps the start until "Set end here".
- The play shortcut is handled by the editor's input map (not a menu accelerator, which would toggle
  twice); the Edit menu and the editor context menu have "Play media segment at caret".
- Verified: unit tests of every module, full `mvn clean install`, plugin jar content, application
  startup with the plugin. Not verified by hand yet: playback, margin buttons, highlight, editing from
  the view (§10, manual checks).

