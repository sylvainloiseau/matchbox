# Plan: settings stored in each OS's standard location

Status: implemented. Decisions in §8, implementation notes in §9.

## 0. Goal

Replace the hand-written `ApplicationSettings` (matchbox-app) with:

- a **typed settings model** with defaults, validation and change notification;
- a **storage layer** that writes atomically to the **standard per-user directories of each OS**;
- separate handling of the three kinds of data mixed together today: **preferences** (colours,
  charset…), **state** (recent files and projects) and **logs**;
- a one-time **migration** from `~/.config/matchbox`, after which the old files are deleted;
- the **charset** and **file extensions** settings actually used by the application (they are
  saved today but have no effect).

---

## 1. Current state

| Concern | Where / how | Problem |
|---|---|---|
| Location | `~/.config/matchbox/` on every OS | Linux convention used on macOS and Windows; `$XDG_CONFIG_HOME` ignored on Linux |
| Files | `configuration.txt` (key=value), `matchbox.history`, `matchbox.project.history`, `matchbox.fileset.history` | Preferences and state mixed in one directory; four ad-hoc formats |
| Log | `~/.matchbox.log` (home directory), XML format | Clutters the home directory; `FileOutputStream` truncates it at each launch, so the log of a crashed session is lost on the next start |
| Parsing | `line.startsWith("charset=")` chains in `loadConfiguration()` | One branch per key; unknown keys silently ignored; no format version |
| Writing | each setter calls `saveConfiguration()` → `Files.write` | Non-atomic (a crash during the write leaves an empty or truncated file); the file is rewritten many times per dialog |
| Errors | `System.err.println` | Invisible in the packaged app |
| Construction | the constructor reads `user.home` and does I/O | Impossible to unit-test without touching the real home directory |
| Change notification | none | Colour changes reach the editor only when it next re-highlights |

**Bugs found while reading the code:**
- **`open_last_project` is never saved.** `saveConfiguration()` doesn't write it, so the **Open last project** checkbox has no effect after a restart. When loading, the value is also only applied if it is `true`.
- **Cancel does not cancel colour changes.** The colour pickers in `SettingsDialog` save immediately, so **Cancel** keeps them.
- **Relative `logfile` paths are resolved wrongly.** They are resolved against `configFilePath`, a *file*, which gives `…/configuration.txt/<path>`.
- **Recent lists are fragile.** `recentFiles = recentFiles.subList(0, max)` keeps a *view* of the old list, and later edits through that view are fragile.
- **The project history limit is fixed.** `maxRecentProjects` is hard-coded to 10 and is not configurable, unlike `maxRecentFiles`.
- **History is logged three times.** "Successfully read history file" is logged once per history file, with nothing saying which file.

**Settings with no effect anywhere in the application:**

| Setting | Effect today |
|---|---|
| `charset` | None. `TextDocument` reads with `new String(Files.readAllBytes(…))` and writes with `content.getBytes()`, i.e. the JVM default charset (UTF-8 since Java 18) |
| `morpheme_separators` | None (the notebook lexer's `setMorphemeSeparators` call is commented out) |
| `file_extension` | None. `Project.addDirectory` hard-codes `.md` / `.txt` |
| `file_patterns` | None |
| `recentFileSets` | None (the menu code is commented out) |

On this machine, `~/.config/matchbox/` holds two history files, and there is no `configuration.txt` (the settings were never saved).

---

## 2. Standard locations

| OS | Preferences | State (recent files/projects) | Logs |
|---|---|---|---|
| macOS | `~/Library/Application Support/Matchbox/` | same | `~/Library/Logs/Matchbox/` |
| Windows | `%APPDATA%\Matchbox\` (roaming) | `%LOCALAPPDATA%\Matchbox\` | `%LOCALAPPDATA%\Matchbox\Logs\` |
| Linux and other Unix | `$XDG_CONFIG_HOME/matchbox/` (default `~/.config/matchbox/`) | `$XDG_STATE_HOME/matchbox/` (default `~/.local/state/matchbox/`) | `$XDG_STATE_HOME/matchbox/logs/` |

Notes:
- On macOS, `~/Library/Preferences` is reserved for property-list files written through the
  macOS preferences API (`NSUserDefaults`). Apple's guidance for other application files is
  `Application Support`.
- On Windows, recent files hold machine-specific paths, so they belong in `LOCALAPPDATA` (not
  roaming); preferences can roam.
- Overrides, in priority order:
  1. system property `-Dmatchbox.settings.dir=<dir>`, which puts all three in one directory, for
     tests, portable installs and CI;
  2. environment variable `MATCHBOX_SETTINGS_DIR`, same effect.

---

## 3. Choice of mechanism

| Option | For | Against |
|---|---|---|
| **A. `java.util.prefs.Preferences`** | Built into the JDK; macOS plist, Windows registry | **Not a standard location on Linux** (`~/.java/.userPrefs`, an XML tree); the Windows registry can't be inspected, backed up or reset by copying a file; 8 KB value limit; lists must be encoded by hand; needs the `java.prefs` module in the jlink runtime |
| **B. `java.util.Properties` file** | Built in, human-readable, no dependency | Flat strings only: lists, typed values and versioning must be encoded by hand (the same kind of code as today) |
| **C. JSON file with Jackson, mapped to Java records** (recommended) | Typed (records with nested lists), human-readable, format version field, unknown keys tolerated, mature library | One dependency: `jackson-databind`, about 2 MB with `jackson-core` and `jackson-annotations` |
| D. TOML/YAML library | Nicer to hand-edit | Smaller or less maintained libraries for TOML; YAML adds a parser for no real gain here |

**Recommendation: C**, plus a small directory resolver written in the project (§4.1) rather than a library:
- `dev.dirs:directories` launches PowerShell on Windows in older versions, and its macOS paths differ from Apple's guidance for logs;
- the rules in §2 fit in about 60 lines and are easy to test.

**Decided: C** (JSON with Jackson).

---

## 4. Target design (matchbox-app, package `fr.cnrs.lacito.matchbox.settings`)

```
AppDirectories        OS detection + XDG/APPDATA rules → configDir, stateDir, logDir (pure, testable)
SettingsData          record: version, preferences (record), defaults, validation      ← JSON on disk
RecentData            record: version, recentFiles, recentProjects                     ← JSON on disk
SettingsStore         load / save JSON atomically, migration, error reporting
UserSettings          the object the application uses: JavaFX properties + RecentItems,
                      saves on change (debounced) through SettingsStore
RecentItems           bounded most-recently-used list of Paths (observable)
LegacySettingsImporter  reads ~/.config/matchbox/* once
LoggingSetup          rotating FileHandler in logDir
```

### 4.1 `AppDirectories`

```java
public record AppDirectories(Path configDir, Path stateDir, Path logDir) {

    /** Directories for the current user and OS, honouring the overrides of §2. */
    public static AppDirectories forCurrentUser() { … }

    /** For tests: explicit OS name, environment and home directory. */
    static AppDirectories resolve(String osName, Map<String, String> env, Path home, String override) { … }
}
```

- OS detection from `os.name` ("Mac", "Windows", otherwise Unix).
- Windows: read `APPDATA` / `LOCALAPPDATA` from the environment; fall back to
  `home\AppData\Roaming` / `home\AppData\Local`.
- XDG: an XDG variable is only honoured if it holds an absolute path (XDG specification rule).
- The directories are created lazily, on first write, not when they are resolved.

### 4.2 Data records (persisted)

```java
public record SettingsData(
    int version,                       // 1; bumped on incompatible change
    String charset,                    // "UTF-8"
    String igtColor,                   // "#fff1c1"
    String lexicalColor,               // "#dfe7fd"
    String morphemeSeparators,         // "-,/"
    List<String> fileExtensions,       // ["txt", "md"]
    List<String> filePatterns,         // []
    int maxRecentFiles,                // 10
    int maxRecentProjects,             // 10
    boolean openLastProject,           // false
    String logFile                     // null = default location
) {
    public static SettingsData defaults() { … }
    /** Replaces invalid values (unknown charset, bad colour, max ≤ 0…) by defaults and logs them. */
    public SettingsData validated() { … }
}

public record RecentData(int version, List<String> recentFiles, List<String> recentProjects) {}
```

- Jackson: `FAIL_ON_UNKNOWN_PROPERTIES = false`, so a newer file can still be read by an older version. Missing fields get their defaults by merging with `defaults()`.
- Files are `config/settings.json` and `state/recent.json`. Example:

```json
{
  "version" : 1,
  "charset" : "UTF-8",
  "igtColor" : "#fff1c1",
  "lexicalColor" : "#dfe7fd",
  "morphemeSeparators" : "-,/",
  "fileExtensions" : [ "txt", "md" ],
  "filePatterns" : [ ],
  "maxRecentFiles" : 10,
  "maxRecentProjects" : 10,
  "openLastProject" : false
}
```

### 4.3 `SettingsStore`

- `load()`:
  - a missing file means defaults;
  - an unreadable or invalid file is renamed to `settings.json.broken-<timestamp>`, defaults are used, and a warning is logged and returned, so the GUI can tell the user.
- `save(data)`: write to `settings.json.tmp`, then `Files.move(…, ATOMIC_MOVE, REPLACE_EXISTING)`, falling back to a non-atomic move if the file system refuses. Same for `recent.json`.
- Thread-safety: saves go through a single-threaded executor, so the JavaFX thread never blocks on disk I/O. `flush()` is called on application exit.

### 4.4 `UserSettings` (what the rest of the application uses)

```java
public final class UserSettings {
    public ObjectProperty<Charset> charsetProperty();
    public StringProperty igtColorProperty();
    public StringProperty lexicalColorProperty();
    public StringProperty morphemeSeparatorsProperty();
    public ObservableList<String> fileExtensions();
    public ObservableList<String> filePatterns();
    public IntegerProperty maxRecentFilesProperty();
    public BooleanProperty openLastProjectProperty();
    public RecentItems recentFiles();
    public RecentItems recentProjects();
    public Path logFile();
    public AppDirectories directories();

    public static UserSettings load(AppDirectories dirs);   // store + migration
    public SettingsData snapshot();                          // for the dialog's "edit a copy"
    public void apply(SettingsData edited);                  // OK button
    public void flush();                                     // on exit
}
```

- Any property change schedules a save, debounced to 500 ms, through `SettingsStore`.
- **Live updates:** `HighlightStyles` / `EditorController` listen to the colour properties and re-highlight, so the user sees the effect of a colour change immediately.
- Properties come from `javafx-base`, which `matchbox-app` already depends on. No GUI toolkit dependency is added.

### 4.5 `RecentItems`

- Stores `Path`s, not strings, and removes duplicates by normalised absolute path.
- `add(path)` moves the path to the top and trims to the maximum; the maximum is a property, so changing it trims immediately.
- `existing()` filters out entries whose file no longer exists, for the menus. Missing entries are kept on disk, because a removable drive may simply be unplugged.

### 4.6 Logging (`LoggingSetup`, called by `MatchboxApplication.start`)

- `java.util.logging.FileHandler` writes to `logDir/matchbox-%g.log`, appending, with 1 MB per file and 5 files kept.
- **Plain text** (decided): `SimpleFormatter` with a one-line pattern (`-Djava.util.logging.SimpleFormatter.format`), readable without tools. The XML format is dropped.
- A `logFile` setting, when present, overrides the directory. A relative path is resolved against `configDir`, which fixes the current bug.

### 4.7 Migration (`LegacySettingsImporter`)

Runs once, when `settings.json` **and** `recent.json` are both missing and `~/.config/matchbox/` exists:

1. `configuration.txt`: parse the known keys with the current rules (comma-separated extensions become a list).
2. `matchbox.history` → `recentFiles`; `matchbox.project.history` → `recentProjects`; `matchbox.fileset.history` is dropped (feature unused).
3. Write the new files. **Only after both are written successfully**, delete the old files (decided):
   - delete exactly the legacy file names: `configuration.txt`, `matchbox.history`,
     `matchbox.project.history`, `matchbox.fileset.history`. Never delete the directory
     recursively: it may contain other files, and on Linux it *is* the new config directory
     (`~/.config/matchbox/`, where the new `settings.json` now lives);
   - then remove `~/.config/matchbox/` if it is empty and is not the new config directory;
   - delete `~/.matchbox.log`, once logging writes to the new log directory;
   - a deletion that fails is logged and retried at the next start (the importer runs while
     legacy files remain and the new files exist, but then only deletes and does not import again).
4. If writing the new files fails, nothing is deleted and the import is retried at the next start.
5. The migration is logged (which values and files were imported, which files were deleted).

---

## 5. Changes in the GUI

| File | Change |
|---|---|
| `MatchboxApplication` | `UserSettings.load(AppDirectories.forCurrentUser())`; `LoggingSetup.init(settings)` before anything logs; `settings.flush()` in `stop()`; show a warning dialog if the store reported a broken file |
| `SettingsDialog` | Edits a `SettingsData` copy (`snapshot()`); colour pickers only update the preview; **OK** → `apply(copy)`; **Cancel** discards everything (fixes the colour bug); adds the missing **Max recent projects** field; extension and pattern lists are edited as one item per line |
| `MainController` | Recent menus read from `settings.recentFiles().existing()` / `recentProjects()` and rebuild when the lists change (`ListChangeListener`), instead of being rebuilt by hand after each action |
| `FileModeController`, `EditorController`, `HighlightStyles` | Take `UserSettings`; `HighlightStyles` reads the colour properties, and `EditorController` re-highlights when they change |

### 5.1 Making the unused settings work (decided)

| Setting | Wiring |
|---|---|
| `charset` | `TextDocument` reads and writes with the configured charset (`Files.readString(path, cs)` / `Files.writeString`) instead of the JVM default. Since Java 18 the default is UTF-8 on every OS, so today every file is read and written as UTF-8 whatever the setting says. A field-notes file in another encoding (e.g. ISO-8859-1) shows wrong characters, and saving it rewrites it as UTF-8 |
| `fileExtensions` | `Project.addDirectory` uses the setting instead of the hard-coded `.md`/`.txt` |
| `morphemeSeparators`, `filePatterns` | No consumer yet; **kept** in the model and the dialog (decided) |

Details for `charset`:
- The charset is applied when a file is opened. Changing the setting affects files opened
  afterwards, not files already open (which would otherwise be silently re-decoded or saved in
  another encoding than they were read with).
- `TextDocument` keeps the charset it was read with and saves with the same one.
- A file that is not valid in the configured charset (e.g. ISO-8859-1 bytes read as UTF-8) is
  reported to the user: decoding uses `CharsetDecoder` with `CodingErrorAction.REPORT`, and the
  error message names the file and the charset, instead of silently replacing characters with
  `\uFFFD` (which would then be written back on save).

Details for `fileExtensions`:
- Compared without case and without a leading dot (`.TXT`, `txt` and `Txt` are equivalent).
- An empty list means "all files" rather than "no files", so a project can't become empty
  by mistake; this is documented in the dialog.

---

## 6. Implementation phases

Each phase builds, passes the tests and can be committed on its own.

### Phase 1: directories
`AppDirectories` + unit tests for the three OSes. Tests use `resolve(osName, env, home, override)` with fake environments, so all OS rules are tested on any machine: XDG set and unset, relative XDG path ignored, `APPDATA` missing, override property.

### Phase 2: model and store
1. Add `jackson-databind` to `matchbox-app` (version managed in the root pom).
2. `SettingsData`, `RecentData`, `SettingsStore`.
3. Tests with `@TempDir`:
   - JSON round trip;
   - missing file gives defaults;
   - unknown keys ignored;
   - missing keys get defaults;
   - invalid values replaced;
   - broken file renamed and defaults used;
   - atomic save leaves no `.tmp` file.

### Phase 3: `UserSettings` and `RecentItems`
Properties, debounced saving, `snapshot()` / `apply()`. Tests:
- the MRU list: order, duplicates, trimming when the maximum shrinks;
- a change saves after the debounce delay;
- `flush()` writes pending changes.

### Phase 4: migration
`LegacySettingsImporter` + tests with fixture files copied from the current formats (including `open_last_project`, comma-separated extensions and a relative `logfile`).

### Phase 5: logging
`LoggingSetup` with a rotating handler; a test that two successive initialisations append instead of truncating.

### Phase 6: GUI switch-over
1. Replace `ApplicationSettings` in the GUI classes of §5.
2. Rewrite `SettingsDialog` to edit a copy.
3. Live re-highlighting on colour change.
4. Delete `ApplicationSettings`.
5. Check by driving the GUI (same scripted driver as for the corpus work): change a colour and cancel, change it and press OK, restart, open a project and check the recent menu.

### Phase 7: make the charset and file-extension settings work
`charset` in `TextDocument`, `fileExtensions` in `Project.addDirectory` (§5.1). Tests:
- round trip of a file with non-ASCII characters in ISO-8859-1 and in UTF-8;
- an ISO-8859-1 file opened as UTF-8 is reported, not silently altered;
- a file keeps the charset it was opened with after the setting changes;
- extension matching (case, leading dot, empty list).

### Phase 8: packaging and documentation
- ~~If the jlink runtime is used (see the `runTimeImage` → `runtimeImage` typo found earlier), check the modules required by Jackson with `jdeps`.~~ Done (2026-10-01): the ignored `runTimeImage` parameter was replaced by an explicit `addModules` list in `matchbox-distribution/pom.xml` (jpackage runs jlink itself); Jackson needs `java.sql`, which is in the list.
- Update `CLAUDE.md` §9: its "Configuration" section still names `~/.config/notebook/fieldnote.history`.
- Update `README.md` with the location table and the `matchbox.settings.dir` override.

---

## 7. Risks

| Risk | Mitigation |
|---|---|
| A user runs an old and a new version side by side | After migration the old version finds no settings or history and starts with defaults; it recreates `~/.config/matchbox/` files, which the new version does not import again (it only imports when its own files are missing). Accepted consequence of deleting the old files |
| Deleting something that isn't ours | Only the four legacy file names and `~/.matchbox.log` are deleted, never a directory recursively, and only after the new files are written |
| Charset changed while files are open | The charset applies to files opened afterwards; open files keep their charset (§5.1) |
| Sandboxed macOS app (not the case today) | `Application Support` is redirected into the sandbox container automatically; nothing to change |
| Windows roaming profile with a path from another machine in the recent list | Recent lists are in `LOCALAPPDATA` (not roaming); `existing()` hides missing entries |
| Settings saved from a background thread while the dialog edits them | The dialog edits a copy; saves go through one executor and save a snapshot taken on the JavaFX thread |
| New dependency (Jackson) | Widely used and maintained; option B (Properties) remains possible with the same design |

---

## 8. Decisions

| # | Question | Decision |
|---|---|---|
| 1 | Storage format | **JSON with Jackson** |
| 2 | Make the unused settings work | **Yes**, the charset and file-extension settings (phase 7 is part of the work) |
| 3 | Remove `morphemeSeparators` and `filePatterns` from the dialog | **No**, keep them |
| 4 | Log format | **Plain text** |
| 5 | Old files after migration | **Delete them**, after the new files are written (§4.7) |

---

## 9. Implementation notes

What differs from the text above, or was found while implementing.

| Topic | Implementation |
|---|---|
| Jackson | `jackson-databind` 2.20.1 (version in the root pom). `jdeps` shows it needs only `java.base`, so a jlink runtime needs no extra module |
| `SettingsData` | Boxed component types (`Integer`, `Boolean`), so that a key missing from the file is `null` and replaced by its default in `validated()`. Colours are checked with a pattern (hex or name) because `matchbox-app` has no `javafx.graphics`; the GUI still checks them with `Color.web` |
| Charset in the dialog | The charset had no field in the dialog: a **Files** tab now has a charset selector, the extensions and the patterns (one per line; commas also accepted for extensions) |
| `DocumentOptions` | New interface in `model`: the charset and extensions are passed to `Project.createDefaultProject` / `loadProject`, because a project reads its files while it is being loaded, before the GUI could set them |
| Unreadable files | `TextDocument` used to swallow read errors (content `null`), and `Project.addFile(String)` / `addDirectory(String)`, used to load project files, only printed them. Now: `addDirectory` adds the readable files and then reports the others; files of a project file that cannot be read (wrong charset, missing file) are listed by `Project.getLoadProblems()` and shown when the project is opened. A missing file used to make the whole project fail to load |
| Saving with the charset | `TextDocument.writeFile` saves with the charset the file was read with; characters that charset cannot encode are reported and the file is left unchanged |
| Reopening the last project | It never worked (`open_last_project` was never saved). Now that it does, a failure to reopen the project (moved, corrupt) falls back to an empty project instead of leaving `project` null, which would have crashed the main window |
| Recent files menu | `MainController.loadRecentFilesMenu()` was never called, so **Open recent** was always empty. The recent menus now rebuild when the lists change |
| Old deletion retry | When the new files exist and legacy files are present (failed deletion, or recreated by an older version), they are deleted without being imported again |
| Log messages of the loading | Settings are loaded before logging is set up, so the messages of the import are collected and written to the log by `LoggingSetup.init` |

### Verification

- Unit tests: 68 in total, 45 of them in `matchbox-app`:
  - directories of the three OSes with fake environments;
  - store: round trip, missing and unknown keys, invalid values, broken file, atomic save;
  - recent lists;
  - `UserSettings`: debounced saving, `close()`, `apply()`;
  - migration: values, deletion, Linux same-directory case, failure keeps the old files, no re-import;
  - logging: appends, plain-text format;
  - charset and extensions: round trips, wrong charset reported, unencodable characters, project loading.
- Real application (`Starter`) started with a temporary home holding files in the old formats: values imported, old files, empty directory and old log deleted, new files in `~/Library/Application Support/Matchbox` and log in `~/Library/Logs/Matchbox`.
- GUI driven with scripted JavaFX drivers:
  - recent files menu filled;
  - ISO-8859-1 file opened with the imported charset;
  - dialog: **Cancel** keeps the old colour, **OK** applies and saves;
  - a file in the wrong charset gives an error message;
  - last project reopened at startup, broken project file gives an empty project;
  - a colour change re-highlights the editor immediately.
