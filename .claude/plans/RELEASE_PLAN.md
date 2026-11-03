# Plan: release installers for every architecture

Status: implemented (2026-11-03), except the first real release run (step 8). Decisions in §11; differences in §13.

Model: `../DictionaryEditor/.github/workflows/release.yml` and its `pom.xml` (commits `d6d90bc`
"Release AppImages for Linux x86_64 and aarch64, and an Intel Mac DMG" and `d2e3e6c` "Keep the
Linux installer options out of the jpackage app image").

## 0. Goal

On push of a tag `v*`, the GitHub release named after the tag (e.g. `v1.2.0`) carries:

| Runner | Artifacts |
|---|---|
| `ubuntu-latest` (x86_64) | `matchbox_1.2.0_amd64.deb` (as today), `Matchbox-1.2.0-x86_64.AppImage` (new) |
| `ubuntu-24.04-arm` (aarch64, new) | `matchbox_1.2.0_arm64.deb`, `Matchbox-1.2.0-aarch64.AppImage` |
| `windows-latest` | `Matchbox-1.2.0.exe` (as today) |
| `macos-latest` (Apple Silicon) | `Matchbox-1.2.0-apple-silicon.dmg` |
| `macos-26-intel` (Intel, new) | `Matchbox-1.2.0-intel.dmg` |

The installers take their version from the tag (`v1.2.0` → `1.2.0`), not from the pom.

Each installer comes with a `.sha256` checksum file. Installing Matchbox associates `.mbx`
files with it (double-click opens the project), and the DEB adds a menu entry. A manual run of the
workflow builds the installers without creating a release.

Out of scope: code signing / notarisation of the DMGs, Windows ARM, RPM, a Flatpak, a universal
macOS binary.

## 1. Assessment

- **P1 — the installer version is fixed.** `jpackage-maven-plugin` uses
  `<appVersion>${project.version}</appVersion>`; `project.version` is `1.0.0` (the parent version
  is a literal in every child pom, so `-Drevision=` cannot change it cleanly).
  → New property `app.version` (default `${revision}`) in the root pom, used as `appVersion`;
  the workflow passes `-Dapp.version=${GITHUB_REF_NAME#v}`. Same solution as DictionaryEditor.
  jpackage requires a numeric version (`1.2.0`, first component ≥ 1 on macOS): a tag `v0.9` or
  `v1.2.0-beta` would fail the DMG/MSI step. → Add a first workflow step that checks the tag
  against `^v[1-9][0-9]*(\.[0-9]+){0,2}$` and fails with a clear message.
- **P2 — jpackage cannot be run a second time with another type.** Its whole configuration is
  inside an anonymous `<execution>`, so `mvn jpackage:jpackage` from the command line would see
  no configuration. The type is not set (jpackage default per OS: DEB / EXE / DMG).
  → Give the execution an id (`installer`), add `<type>${jpackage.type}</type>` and
  `<destination>${build.root}/target/installer${jpackage.destSuffix}</destination>`; set
  `jpackage.type` in the existing `win` (EXE), `mac` (DMG), `linux` (DEB) profiles so the
  default build is unchanged. The workflow then calls
  `mvn -pl matchbox-distribution jpackage:jpackage@installer -Djpackage.type=APP_IMAGE -Djpackage.destSuffix=-appimage`
  after `mvn install` (calling the goal runs no `initialize` phase, so `clean-assembly` does not
  empty the assembly).
- **P3 — Linux installer-only options.** DictionaryEditor had to move `linuxShortcut`,
  `linuxMenuGroup`, `linuxAppCategory` to a `linux-installer` profile because jpackage refuses
  them with `--type app-image`. Matchbox sets none of them today, so nothing to move. If we add a
  menu entry for the DEB (suggestion S1), it goes in such a profile, inactive when
  `jpackage.type=APP_IMAGE`.
- **P4 — JavaFX native jars per architecture.** DictionaryEditor passes `-Djavafx.platform=…`
  because it uses jlink with modules. Matchbox puts the JavaFX jars in the assembly (`lib/`); the
  openjfx poms detect the platform from the build machine (`os.name` / `os.arch`), and each job
  builds natively on its own architecture. → No `javafx.platform` needed; to be verified in the
  first run by listing `lib/javafx-graphics-*-linux-aarch64.jar` and `*-mac.jar` (step 8).
- **P5 — `$APPDIR` in the AppImage.** The launcher option `-Dmatchbox.plugins.dir=$APPDIR/plugins`
  is expanded by the jpackage launcher itself (from its own location), not from the environment,
  so the `APPDIR` variable set by the AppImage runtime does not interfere. → Verify once on the
  built AppImage (`--appimage-extract-and-run`, plugins listed in Project properties).
- **P6 — release name.** `softprops/action-gh-release` names the release after the tag by default;
  set `name: ${{ github.ref_name }}` explicitly so it does not depend on the action's defaults.
  Five jobs publish to the same release: the action creates it on the first and updates it on the
  others (as today with three jobs).
- **P8 — the application ignores the file it is opened with.** `Starter.main` /
  `MatchboxApplication.main` pass `args` to `launch` but nobody reads `getParameters()`, and
  macOS does not pass the file as an argument: it sends an "open file" event. A file association
  alone would start Matchbox on the start page. → (S2) Linux/Windows: the first raw parameter
  ending in `.mbx` is opened at startup, instead of "Reopen the last project". macOS:
  `java.awt.Desktop.getDesktop().setOpenFileHandler(…)` registered in `Starter.main` before
  `launch` (events received before the window exists are kept and opened once it is shown;
  `java.desktop` is already in the runtime). Both go through the path used by a dropped `.mbx`
  (`MainController.openProject(File)`, hence `ProjectCloser` asks before closing a modified
  project). Risk: JavaFX's own macOS toolkit may take the "open file" event before AWT sees it;
  this must be checked on a real DMG. If it does not work, the association is kept on macOS
  (Finder shows the icon and starts Matchbox) and the limitation is documented; the only other
  route (`com.sun.glass.ui.Application` open-files handler) is not public API and is not used.
- **P7 — the AppImage must not upload the app-image directory.** As in DictionaryEditor, move the
  jpackage image out of the uploaded directory (`build/target/installer-appimage/Matchbox` →
  `build/target/Matchbox.AppDir`) and write the `.AppImage` into `build/target/installer/`, the
  only directory the release step uploads.

## 5. Data flow (one Linux job)

```
mvn install                             → build/target/installer/matchbox_1.2.0_amd64.deb
mvn -pl matchbox-distribution jpackage:jpackage@installer -Djpackage.type=APP_IMAGE …
                                        → build/target/installer-appimage/Matchbox/{bin,lib}
mv … build/target/Matchbox.AppDir; write AppRun, matchbox.desktop, icon (.DirIcon)
appimagetool (1.9.1, SHA-256 checked)   → build/target/installer/Matchbox-1.2.0-x86_64.AppImage
action-gh-release                       → uploads build/target/installer/*
```

macOS jobs: `mvn install`, then rename `build/target/installer/*.dmg` to
`*-${{ matrix.mac_label }}.dmg` (`apple-silicon`, `intel`).

## 7. Implementation steps

1. **pom.xml (root)**: property `app.version` = `${revision}`, with a comment ("the release
   workflow passes the tag").
2. **matchbox-distribution/pom.xml**: execution id `installer`; `appVersion` = `${app.version}`;
   `type` = `${jpackage.type}`; destination with `${jpackage.destSuffix}` (property default
   empty); `jpackage.type` set in the `win` / `mac` / `linux` profiles. Check locally on macOS that
   `mvn clean install` still writes `build/target/installer/Matchbox-1.0.0.dmg`, and that
   `-Dapp.version=1.2.3` gives `Matchbox-1.2.3.dmg`.
3. **release.yml**, following DictionaryEditor:
   - matrix: add `ubuntu-24.04-arm` (`appimage_arch: aarch64`) and `macos-26-intel`
     (`mac_label: intel`); `appimage_arch: x86_64` on `ubuntu-latest`, `mac_label: apple-silicon`
     on `macos-latest`; `appimagetool_sha256` per Linux entry (same values as DictionaryEditor:
     appimagetool 1.9.1); job name from the runner;
   - step "Check the tag" (P1);
   - `mvn -B -ntp install "-Dapp.version=${GITHUB_REF_NAME#v}"` (tests kept; GitHub Packages
     credentials kept, needed by matchbox);
   - Linux: `fakeroot` installed for the DEB if missing; app image; AppImage step (AppRun calling
     `bin/Matchbox`, `matchbox.desktop` with `Exec=Matchbox %f`, `Icon=matchbox`,
     `Categories=Office;Education;`, `MimeType` (step 7), icon
     `matchbox-distribution/src/main/resources/icon.png`, `APPIMAGE_EXTRACT_AND_RUN=1` since the
     runner has no FUSE);
   - macOS: rename the DMG with the architecture label;
   - publish: `name: ${{ github.ref_name }}`, `tag_name` implicit, `generate_release_notes: true`,
     `files: build/target/installer/*`; keep `softprops/action-gh-release@v3`.
4. **Checksums (S3)**: after the installers are built, in every job,
   `cd build/target/installer && for f in *; do sha256sum "$f" > "$f.sha256"; done`
   (`shasum -a 256` on macOS, bash on Windows has `sha256sum`); uploaded with the same glob.
5. **Manual run (S4)**: `workflow_dispatch` trigger. Without a tag (`github.ref_type != 'tag'`)
   the tag check is skipped and the version is the pom's (`app.version` not passed); the publish
   step runs only on tags (`if: github.ref_type == 'tag'`); otherwise `actions/upload-artifact@v4`
   uploads `build/target/installer/*` as `installers-${{ matrix.runner }}` (kept 7 days).
6. **DEB menu entry (S1)**: profile `linux-installer` in `matchbox-distribution/pom.xml` with
   `linuxShortcut=true`, `linuxMenuGroup=Matchbox`, `linuxAppCategory=Office`. Activation needs
   both conditions (Maven ≥ 3.2.2 ANDs them): `<os><family>linux</family></os>` (jpackage
   rejects `--linux-*` options on macOS/Windows) and property `jpackage.type` = `!APP_IMAGE`
   (jpackage rejects them for an app image; the APP_IMAGE call passes `-Djpackage.type` on the
   command line, which profile activation sees — a type set by another profile would not be).
7. **`.mbx` association (S2)**:
   - `matchbox-distribution/src/main/jpackage/mbx.properties`: `extension=mbx`,
     `mime-type=application/x-matchbox-project`, `description=Matchbox project`, `icon` per OS
     (the existing icon); passed with `<fileAssociations>` of jpackage-maven-plugin (DEB, DMG, EXE;
     not the app image);
   - AppImage: `MimeType=application/x-matchbox-project;` in the desktop file and a
     `usr/share/mime/packages/matchbox.xml` in the AppDir (registered by AppImage integrators
     such as appimaged / Gear Lever only);
   - application (P8): `Starter` registers the macOS open-file handler; `MatchboxApplication`
     reads `getParameters().getRaw()`; a small pure class `LaunchFiles` (matchbox-gui) picks the
     project to open from the arguments and queued events (unit-tested); `MainController` gets
     `openProjectAtLaunch(File)` = `openProject` without the "reopen the last project" step.
8. **First release test** on a throw-away tag (`v1.0.1-test` is refused by P1 → use a fork or a
   tag such as `v1.0.1` then delete the release): check the five jobs, the file names, the JavaFX
   classifiers in each artifact (P4), launch the AppImage on an x86_64 Linux and the Intel DMG.
9. **Docs**: README (Installation: which file to download per system, `chmod +x` for the
   AppImage, Gatekeeper "Open anyway" for the unsigned DMG), CLAUDE.md §5 (release on tag,
   `-Dapp.version`, `jpackage.type`).

## 8. What is not changed

- `mvn clean package` / `mvn install` locally: same installer type and place as today.
- The assembly (`build/assembly`), the `bin/matchbox` scripts, the plugin directory layout.
- The Windows job (one EXE), the "no clean after install on Windows" rule.
- The start page and "Reopen the last project" when Matchbox is started without a file.

## 9. Tests

- `LaunchFilesTest` (pure): no argument → none; `.mbx` among other arguments → it; a missing
  file → none (and a status-line message "Cannot open … : file not found"); event queued before
  the window → opened once the window is ready.
- GUI test (`StartPageTest`): the fixture starts with a `.mbx` argument → the project is open,
  the start page is not shown. The macOS open-file event cannot be tested headless (manual check
  on the DMG, step 8).
- Release checks: local macOS build with and without `-Dapp.version` (step 2); the
release run of step 8. The tag check can be tried locally with a few tag strings in bash.

## 10. Files

| File | Change |
|---|---|
| `.github/workflows/release.yml` | matrix of 5 runners, tag check, version, AppImage, DMG names, release name |
| `pom.xml` | `app.version` property |
| `matchbox-distribution/pom.xml` | execution id, `appVersion`, `type`, `destination` suffix, per-OS `jpackage.type` |
| `matchbox-distribution/src/main/jpackage/mbx.properties`, `matchbox.xml` (new) | file association, MIME type |
| `matchbox-gui/.../Starter.java`, `MatchboxApplication.java`, `MainController.java` | open the `.mbx` given at launch |
| `matchbox-gui/.../LaunchFiles.java`, `LaunchFilesTest.java` (new) | choice of the project to open |
| `README.md`, `CLAUDE.md` | release and download instructions, double-click on `.mbx` |

## 11. Questions

### Decided with the user

- DMG for Apple Silicon and Intel; AppImage for Linux x86_64 and aarch64; release named after
  the tag; same approach as DictionaryEditor.
- Q1. The DEB is kept next to the AppImage on both Linux runners.
- Q2. The tests run on all five runners (`mvn install`, `fail-fast: false`).
- Q3. Tags jpackage cannot use (`v0.x`, `-beta`) fail at the first step with a message.
- S1–S4 accepted: steps 4–7.

### Open

None.

## 12. Suggestions (all accepted, see §7)

- S1. Menu entry for the DEB (`linuxShortcut`, `linuxMenuGroup`, `linuxAppCategory`) in a
  `linux-installer` profile inactive for `APP_IMAGE` (P3).
- S2. `.mbx` file association: `MimeType=application/x-matchbox-project` in the desktop file and
  jpackage `--file-associations` for DEB/DMG/EXE (needs a properties file and a MIME XML).
- S3. Publish SHA-256 checksums of the installers (`sha256sum > …sha256`) with the release.
- S4. A `workflow_dispatch` trigger building the installers as workflow artifacts without
  creating a release, to test the packaging before tagging.

## 13. Differences between the plan and the code

- **Missing launch file**: no status-line message. A `.mbx` given at launch that cannot be loaded
  is reported like a failed reopen of the last project (`reportOpenFailure`: error dialog), once
  the window is shown. `LaunchFiles.projectIn` does not check that the file exists.
- **`MainController`**: the launch project is a third constructor argument
  (`MainController(settings, plugins, Optional<Path>)`, the two-argument constructor delegates);
  `openProjectFile(File)` is public (brings the window to the front, then the usual
  `openProject`). There is no separate `openProjectAtLaunch`.
- **macOS open-file handler**: installed in `MatchboxApplication.main` (called by `Starter.main`),
  so that `mvn javafx:run` gets it too. Checked on a locally built DMG: the event that launches
  the app opens the project, and a second `.mbx` sent to the running app replaces it. The
  `Info.plist` declares the `.mbx` type (`CFBundleDocumentTypes`).
- **`file-association`** is its own profile (active unless `APP_IMAGE`, every system), separate
  from `linux-installer` (Linux and not `APP_IMAGE`): one properties file without icon (the
  application icon is used).
- **Workflow**: a "Version" step computes `APP_VERSION` (tag, or the pom's `app.version` through
  `help:evaluate` when run by hand); `mvn install` always gets `-Dapp.version`.
  `fail_on_unmatched_files: true` on publish (the checksums guarantee files exist).
  `GITHUB_TOKEN` is now passed to the Maven steps (credentials of the GitHub Packages server
  written by `setup-java`).
- **README**: the obsolete "Java 17 on the PATH" prerequisite was replaced (the installers bundle
  their runtime) by a table of the files per system.
- Not verifiable locally: DEB/AppImage builds, the AppImage's `$APPDIR` (P5), the JavaFX
  classifiers per architecture (P4), Windows: first release run (step 8).
