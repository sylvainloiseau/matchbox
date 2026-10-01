# Plan: AI-agent suggestions in the Matchbox editor

Status: **implemented** (phases 1–6), except the items listed under "Not done" below. The
grammar documentation itself (phase 1, "Remaining") is still to be written by the grammar authors.

### Implementation notes (2026-10-01)

Where things are:

| Part | Classes |
|---|---|
| Module `matchbox-assist` (no JavaFX) | `AssistService`, `AssistContext`, `AssistOptions`; `provider/` (`AnthropicProvider`, `OpenAiCompatibleProvider`, `HttpTransport`, `ProviderFactory`, `ProviderConfig`, `ProviderKind`); `prompt/` (`PromptPrefix` + `Cache`, `PromptBuilder`, `FewShotSelector`); `suggestion/` (`Suggestion`, `TextEdit`, `Relocation`, `EditLocator`, `GrammarQuickFixes`, `TokenNames`, `Paragraphs`); `validation/EditValidator`; `privacy/PrivacyPolicy` |
| `matchbox-app` | `settings.AssistSettingsData` (in `SettingsData`, version 2; `UserSettings.assistProperty()`); `Project.isCloudAssistAllowed()` + `<assistant allowCloud="false"/>`; `Corpus.files()` / `parsedText()` |
| `matchbox-gui` | `gui.assist`: `AssistController`, `AiModelDialog`, `CloudConsentDialog`, `ApiKeyStore`, `GhostTextController`, `SuggestionPanelController`; changes in `EditorController`, `FileModeController`, `MainController`, `FileMode.fxml`, `MainView.fxml`, `ProjectPropertiesDialog` |

Decisions and differences with the plan:
- **Validation rule** (§7.1), stricter than planned: an error in the edited range (to the end of
  its paragraph) must match an error of the original text of the same grammar and kind
  ("missing", "mismatched input"…) at the shifted position, and the total number of errors must
  not grow. A suggestion that fixes one error but adds another is rejected.
- **Anthropic requests**: no `temperature` (rejected by current models); `output_config.effort`
  (`low` for completion and explanation, `medium` for review), not sent to Haiku 4.5, which
  rejects it; structured output in `output_config.format`; refusals (`stop_reason: "refusal"`)
  reported as errors; server-side refusal fallback (`fallbacks: "default"`, beta
  `server-side-fallback-2026-07-01`) on Fable 5.1 / Opus 5.5 / Opus 5 / Sonnet 5.5 when the
  Anthropic API itself is used. Raw HTTP is used instead of the official SDK (one client for
  OpenAI and Ollama, no OkHttp/Kotlin in the package), as decided in §3.
- **Settings**: one `ObjectProperty<AssistSettingsData>` instead of one property per field; the
  record also holds `inlineEnabled` and `language`.
- **Dialogs** are built in Java code (like `ProjectPropertiesDialog`), not FXML.
- **Undo**: the editor's undo manager ignores ghost-text changes; the undo history is also reset
  when another file is opened (undoing went back to the previous file's text).
- **Accept the next word**: `Ctrl+Right`. Explanation language: a setting (default English).
- **Few-shot examples**: chosen once per project (Assistant › Refresh project examples).
- The *Assistant* menu gathers the actions; *File › AI assistant…* opens the dialog.
- **Packaged runtime**: `matchbox-distribution/pom.xml` lists the JDK modules of the installer's
  runtime explicitly (`addModules`, computed with `jdeps`), including `java.net.http` for the
  providers' HTTP clients. The former `runTimeImage` parameter was misspelled and ignored.

Not done:
- Streaming of completions (SSE); a second attempt after a rejected completion.
- `describeContextAt` and plugin-specific `quickFixes` in the grammar contract (§6.3).
- "Apply all fixes of this kind"; the "Parsing error" tab is kept.
- Headless GUI tests (TestFX/Monocle): the ghost text and panel were checked by building, by
  the unit tests of `matchbox-assist`, and by starting the application, not by automated UI tests.

## 0. Goals

1. **Model selection**: a modal dialog, opened from the File menu, to choose Claude, ChatGPT
   or Ollama (local open-weight models), enter API keys and pick a model.
2. **Inline suggestions**: grey "ghost" text at the caret in the file editor. `Tab` accepts it
   and `Esc` dismisses it.
3. **Suggestion side panel** on the right of the editor. It lists proposed corrections drawn
   from (a) parse errors, (b) deterministic fixes derived from the grammar, and (c) the AI agent.
   Each correction can be previewed, applied or dismissed.
4. **"Explain this parse error"**: a plain-language explanation of an error, with an optional
   proposed fix.
5. **Grammar awareness**: every prompt includes the grammars attached to the edited file, so
   suggestions follow the file's notation.
6. **Grammar check**: a suggested edit that introduces parse errors is never shown.
7. **Privacy**: a clear warning before any text goes to a cloud provider, and a per-project
   option to allow local models only.
8. **Prompt reuse**: a stable, cacheable prompt prefix shared by all AI features.
9. **A separate module** for the provider and prompt code, with no JavaFX dependency.

---

## 1. Current state (after the refactoring)

| Concern | Where | Relevance |
|---|---|---|
| Grammar contract | `grammarapi.GrammarPlugin`: `getId`, `getDisplayName`, `getDisplayDescription`, `getFormatDocumentation`, `getExamples`, `getGrammarSource`, `getResult(Path, String)` (documented re-entrant and thread-safe), `getApiVersion()`; `API_VERSION = 2` | Documentation methods added (§6.1); plugins return placeholders for now |
| Version check | `PluginManager` skips a plugin unless `getApiVersion() == API_VERSION` exactly | Unchanged: the version stays at 2 until the app is published (§6) |
| Parse results | `grammarapi.ParseResult` record `(objects, errors)`, immutable copies | Good for validation |
| Parse errors | `grammarapi.ParseError`: file, grammarId, line (1-based), charPositionInLine, message, `startIndex`/`stopIndex`, `expected`, `span()` | Offsets and expected tokens added (§6.2) |
| Error collection | `grammarapi.CollectingErrorListener` | Fills offsets and expected tokens (§6.2) |
| Corpus objects | `corpus.CorpusObject` (`getTextCoordinate`, `getKind`, `getLabel`, `getChildren`), capabilities `corpus.capability.*` (`InterlinearSentence`, `LexicalEntry`, …) | Source of few-shot examples (§5.3) |
| Grammars of a file | `Project.getGrammarsForFile(Path)` (rank order, no duplicates) | ✓ already exists |
| Background parsing | `Project.parseInBackground(Path)`, single-thread `matchbox-parser` executor, results applied with `Platform::runLater`; `Project.computeParse` is private static | Validation needs its own executor (§7.4) |
| Corpus queries | `Corpus.errorsFor(path)`, `objectsFor`, `flatten`, `objectAt(path, offset)`, `objectsOf(type, path)`, `addFileListener` | Feeds the side panel |
| Editor | `gui.mode.fileMode.EditorController` (`InlineCssTextArea`). `highlightText()` **clears all styles** and re-applies `-rtfx-background-color` on every corpus refresh; `selectInEditor(TextCoordinate)`, `selectLinesInEditor(ParseError)` | Constraints on ghost text (§8.1) |
| Editor → model sync | `changeListener` throttled at 500 ms; `saveEditedFile` and `parseEditedFile` push the latest text | Suggestions read the text area directly |
| Layout | `FileMode.fxml`: left file list, then a vertical `topSplitPane` with [toolbar + editor VBox] above a `TabPane` (Objects / Session / Tag / Parsing error) | Side panel goes next to the editor (§9.1) |
| Settings | `settings.UserSettings` (JavaFX properties, autosave) ↔ `settings.SettingsData` record (JSON, `validated()`, `CURRENT_VERSION = 1`), stored in `AppDirectories` locations | AI settings go here (§4.3) |
| JSON | `jackson-databind` 2.20.1 already in root `dependencyManagement`, used by `matchbox-app` | No new JSON dependency needed |
| Project files | `ProjectXMLSerializer` (`<project>`, `<fileset>`, `<pattern>`, `<grammar id=…>`) | Per-project privacy flag (§10.2) |
| Distribution | `matchbox-distribution/pom.xml` lists the modules explicitly | Must include the new module |

---

## 2. Architecture

### 2.1 New module `matchbox-assist` (no JavaFX)

```
matchbox-corpus ──┐
matchbox-grammar-api ─┼──> matchbox-assist ──> matchbox-gui
jackson-databind ─┘                        (matchbox-app is not required by assist)
```

`matchbox-assist` contains everything that doesn't need a UI, so it can be unit-tested
without JavaFX and reused by a future command-line tool (e.g. `matchbox fix --grammar notebook file.txt`).
It does **not** depend on `matchbox-app`, whose model uses JavaFX properties. The GUI builds
plain `AssistContext` records from `Project` and `Corpus` and passes them in.

```
fr.cnrs.lacito.matchbox.assist
├─ AssistContext            record: path, full text, caret/selection, grammars, errors, few-shot examples
├─ AssistService            entry point: complete(), explainError(), review(); cancellation, staleness
├─ provider/
│   ├─ CompletionProvider   interface: chat(ChatRequest) -> CompletableFuture<ChatResponse>,
│   │                       stream(...), listModels(); cancellable
│   ├─ ChatRequest/Response records (system blocks with cache hints, messages, maxTokens, temperature, jsonSchema)
│   ├─ AnthropicProvider    Messages API, prompt caching, structured output
│   ├─ OpenAiCompatibleProvider  Chat Completions; ChatGPT, and Ollama via http://localhost:11434/v1
│   ├─ ProviderConfig       record: kind, baseUrl, model, apiKey (never logged)
│   └─ ProviderFactory
├─ prompt/
│   ├─ PromptPrefix         the cacheable part (§5.2), built once per (grammars, project) and memoised
│   ├─ PromptBuilder        adds the task-specific user message (complete / explain / review)
│   └─ GrammarDocs          gathers documentation, examples and (optionally) grammar source from GrammarPlugin
├─ suggestion/
│   ├─ Suggestion           record: id, source (PARSE_ERROR | GRAMMAR_FIX | AI), severity, anchor, title,
│   │                       detail, List<TextEdit> edits, status
│   ├─ TextEdit             record: start, end, expectedOriginal, replacement
│   ├─ Anchor               offsets + the original text + a little context, used to relocate after edits
│   └─ GrammarQuickFixes    deterministic fixes from ParseError.getExpected() (§9.3)
├─ validation/
│   └─ EditValidator        applies edits to a copy, reparses with the file's grammars, compares errors (§7)
└─ privacy/
    └─ PrivacyPolicy        is this provider allowed for this project; what is sent
```

### 2.2 In `matchbox-gui`

```
fr.cnrs.lacito.matchbox.gui.assist
├─ AiModelDialog (+ AiModelDialog.fxml)   §4
├─ CloudConsentDialog                     §10.1
├─ ApiKeyStore                            §4.4
├─ GhostTextController                    §8
├─ SuggestionPanelController (+ SuggestionPanel.fxml)  §9
├─ SuggestionCell                         list cell with preview and action buttons
├─ AssistContextFactory                   Project/Corpus/EditorController → AssistContext
└─ FxAssist                               bridges CompletableFutures to the FX thread
```

### 2.3 Build changes
- Root `pom.xml`: add `<module>matchbox-assist</module>` and a `dependencyManagement` entry.
- `matchbox-assist/pom.xml`: depends on `matchbox-corpus`, `matchbox-grammar-api`,
  `jackson-databind`, `junit-jupiter` (test), and the `matchbox-corpus` test-jar (test).
- `matchbox-gui/pom.xml`: add `matchbox-assist`.
- `matchbox-distribution/pom.xml`: add `matchbox-assist`.

---

## 3. HTTP and dependencies

The plan uses the **JDK `java.net.http.HttpClient` plus the existing Jackson**. This adds no
new third-party dependency.

- Ollama provides an OpenAI-compatible endpoint, so `OpenAiCompatibleProvider` with a
  configurable base URL covers ChatGPT, Ollama, LM Studio and vLLM. Only Anthropic needs its own client.
- Streaming uses Server-Sent Events read line by line from `HttpResponse.BodyHandlers.ofLines()`.
- Cancellation: `CompletableFuture.cancel` on the `sendAsync` future aborts the request.
- The official SDKs (`com.anthropic:anthropic-java`, `com.openai:openai-java`,
  `io.github.ollama4j:ollama4j`) remain an option behind `CompletionProvider`. They add OkHttp
  and the Kotlin stdlib to the jpackage image, and it isn't needed.

---

## 4. Model-selection dialog

### 4.1 Menu (`MainView.fxml` / `MainController`)
- File menu, next to **Settings**: `AI assistant…` → `MainController.handleAiAssistant()` opens
  `AiModelDialog` (modal, owner = primary stage, same pattern as `handleSettings`).
- `CheckMenuItem` **Enable AI suggestions**, bound to the `aiEnabled` property.
- View menu (or editor toolbar): `ToggleButton` **Suggestions panel** (§9).

### 4.2 Dialog content
- **Provider**: radio group *Disabled / Claude / ChatGPT / Ollama (local)*.
- **API key**: `PasswordField` with show/hide. Shows "using environment variable
  `ANTHROPIC_API_KEY`" or "`OPENAI_API_KEY`" when that variable is set. Not used for Ollama.
- **Base URL**: default `http://localhost:11434` for Ollama. Editable for OpenAI-compatible servers.
- **Model**: editable `ComboBox` filled by **Refresh models** (`GET /v1/models` for Anthropic
  and OpenAI, `GET /api/tags` for Ollama).
  - Claude defaults: `claude-haiku-4-5` for inline completion (latency) and
    `claude-sonnet-5-5` for explanations and review. The dialog has two model fields,
    **Fast model** for inline completion and **Smart model** for explain/review. They may be
    the same.
  - Model names aren't hard-coded for OpenAI or Ollama. For an empty Ollama list the dialog
    shows the hint `ollama pull <model>`.
- **Test connection**: one tiny request on a background task, showing ✓ or the error message
  (401, connection refused, unknown model…).
- **Inline trigger**: *Manual (Ctrl+Space)* (default) or *Automatic after pause* (ms field).
- **Context**: characters before and after the caret (defaults 4000 and 1000). Checkbox
  *Include ANTLR grammar source in prompts* (default off, see §6.1).
- **Privacy box**: shows whether the selected provider is local or cloud, and what is sent
  (§10).

### 4.3 Non-secret settings: `UserSettings` / `SettingsData`
The application uses only `UserSettings` and never writes settings files itself (CLAUDE.md §9).
- Add a nested record so that `SettingsData` stays readable:
  ```java
  public record AssistSettingsData(
      String provider,          // "none" | "anthropic" | "openai" | "ollama"
      String baseUrl,
      String fastModel,
      String smartModel,
      String trigger,           // "manual" | "auto"
      Integer debounceMs,
      Integer contextBefore,
      Integer contextAfter,
      Boolean includeGrammarSource,
      Boolean suggestionPanelVisible,
      List<String> cloudConsentGiven   // providers for which the warning was acknowledged
  ) { static AssistSettingsData defaults() {…}  AssistSettingsData validated() {…} }
  ```
  and a component `AssistSettingsData assist` in `SettingsData`. Bump `CURRENT_VERSION` to 2.
  `validated()` replaces a missing `assist` with defaults, so v1 files load unchanged.
- `UserSettings` exposes the corresponding properties (`assistProviderProperty()`, …). They
  are autosaved like the others.

### 4.4 API keys: `ApiKeyStore` (gui)
- Keys are never stored in `settings.json`, which users may copy or share.
- Resolution order: environment variable, then `api-keys.json` in the `AppDirectories`
  settings directory, created with POSIX permissions `rw-------` (on Windows the
  per-user profile ACL applies).
- Keys are never written to the log (`ProviderConfig.toString()` masks them).
- Later: the OS keychain (macOS Keychain, libsecret, Windows Credential Manager).

---

## 5. Prompts: grammar-aware and reused

### 5.1 One prefix for all features
All three AI features (inline completion, explain error, review) send the **same system
prefix** for a given file. Only the last user message differs. This lets the providers'
prompt caches be reused across features and across requests.

```
SYSTEM (stable, cacheable; identical bytes from request to request)
  [1] Role and general rules
      - You assist a linguist editing plain-text field notes.
      - Preserve the author's language, transcription, gloss abbreviations and separators.
      - Never invent data (forms, glosses, translations) that the author has not implied.
  [2] For each grammar of the file (rank order):
      ## <displayName> (<id>)
      <getFormatDocumentation()>
      Examples: <getExamples()>
      [<getGrammarSource()> if enabled and non-empty]
      (empty documentation / examples are omitted)
  [3] Examples from this project (few-shot, chosen deterministically, see §5.3)
  ── cache breakpoint ──
USER (per request)
  Task block: COMPLETE | EXPLAIN | REVIEW, with its own output contract
  Context block: text around the caret / error / selection, nearby errors
```

### 5.2 Making the cache work
- `PromptPrefix` is **memoised** per (grammar ids and versions, project file, settings flag) and
  rebuilt only when one of these changes. The bytes must not vary: no timestamps, no
  `HashMap` iteration order, examples in a fixed order.
- **Anthropic**: put `cache_control: {"type": "ephemeral"}` on the last system block. Below the
  model's minimum cacheable length nothing is cached, which is harmless.
- **OpenAI**: caches long, stable prefixes automatically, so the same ordering rule applies.
- **Ollama**: reuses the KV cache for an identical prefix while the model stays loaded. Send
  `keep_alive` (e.g. `"15m"`) through the native API, or rely on the server default.
- The dynamic context always comes **after** the prefix and is never interleaved with it.

### 5.3 Few-shot examples from the user's own data
- Taken from `Corpus` for the project: per capability, up to 3 `InterlinearSentence` and 3
  `LexicalEntry` objects from parsed files without errors in their spans. Their source text is
  read from `TextCoordinate` ranges in the parsed text.
- Selected **deterministically** (e.g. the first N by file path then offset) and frozen for the
  session, so that the prefix and its cache stay stable. A **Refresh examples** action in the
  dialog rebuilds them.
- These examples show the author's actual conventions (gloss abbreviations, separators), which
  the grammar documentation alone doesn't cover.

---

## 6. Changes to `matchbox-grammar-api`

Policy (decided): new contract methods are **mandatory** (no default methods), and
`API_VERSION` **stays at 2**. The application hasn't been published, so no external plugin
needs to keep loading.

### 6.1 Prompt-oriented documentation ✅ done

```java
public interface GrammarPlugin {
    // … getId, getDisplayName, getDisplayDescription, getResult, getApiVersion …

    /** Description of the notation (Markdown allowed), detailed enough for an AI assistant:
     *  what each construct means linguistically, not only its syntax. "" if not documented yet. */
    String getFormatDocumentation();

    /** Canonical snippets, each parsing without errors with this grammar alone. */
    List<GrammarExample> getExamples();

    /** Source of the grammar (e.g. the .g4 lexer and parser, concatenated). "" if not provided. */
    String getGrammarSource();
}

public record GrammarExample(String caption, String text) {}   // grammarapi, non-null components
```

- `AbstractGrammarPlugin` declares the three methods abstract, like the other methods.
- `NotebookGrammarPlugin` and `ConllGrammarPlugin` return placeholders (`""`, `List.of()`,
  `""`) marked `// TODO`. The test grammars in `matchbox-app` (`PluginManagerTest`,
  `DocumentCharsetTest`, `ProjectParseTest`) implement them the same way.
  `matchbox-grammar-lex` has no plugin class yet.
- **Still to do (by the grammar authors):** write the actual documentation, examples and
  source. The suggested layout loads files relative to the plugin class with
  `getClass().getResourceAsStream(...)`. This resolves through the plugin class loader, for the
  same reason `ObjectViewFactory` warns about `FXMLLoader`. A small protected helper
  `AbstractGrammarPlugin.readResource(String)` can be added when the first plugin needs it.
  ```
  src/main/resources/<plugin package>/assist/documentation.md
  src/main/resources/<plugin package>/assist/examples/*.txt     (first line = caption)
  src/main/antlr4/**/*.g4    → copied into the jar with a <resource> entry in the plugin pom
  ```
- `PromptBuilder` treats an empty string or empty list as "not provided" and omits the
  section.

The documentation is more important than the `.g4` source. `NotebookLexer.g4` uses 6 lexer
modes, which models read poorly, while a page of prose plus examples works well. Even when
`getGrammarSource()` is non-empty, it is sent only if the user enables it (§4.2).

### 6.2 Parse errors usable for corrections ✅ done

`ParseError` (still free of ANTLR references by default) now has:

```java
public ParseError(Path file, String grammarId, int line, int charPositionInLine, String message,
                  int startIndex, int stopIndex, List<String> expected);
int getStartIndex();             // offset of the offending input, -1 if unknown;
                                 // may equal the text length for an error at end of file
int getStopIndex();              // inclusive, -1 if unknown; startIndex - 1 for an empty range
List<String> getExpected();      // ANTLR display names: "'<'" (literal, quoted),
                                 // "IMB_LINE" (symbolic), "<EOF>"
Optional<TextCoordinate> span(); // empty if unknown or empty range; end line = start line
```

The existing constructors still work and set `-1, -1, List.of()`. `Project` and `Corpus` keep
using them for internal errors and invalid-span errors, which have no offset.

`CollectingErrorListener.syntaxError` fills the new fields automatically for every plugin that
uses it:
- **Parser errors**: offsets of the offending `Token` (an empty range at EOF), and expected
  tokens from `e.getExpectedTokens()`, or `parser.getExpectedTokens()` when there is no
  exception, through `Vocabulary.getDisplayName`.
- **Lexer errors**: from `lexer._tokenStartCharIndex` to the rejected character (clamped to
  the input). No expected tokens.

This is covered by `NotebookGrammarPluginTest.errorsAreLocatedByOffsetsWithExpectedTokens`.

Follow-up (phase 3): `EditorController.selectLinesInEditor(ParseError)` should use
`getStartIndex()` when it is `>= 0` instead of recomputing the offset from line and column.

### 6.3 Later additions to the contract
Under the same policy (mandatory, version unchanged while the app is unpublished):
```java
/** Grammatical position at an offset, e.g. "inside igt_paragraph, after the transcription
 *  line; a gloss line is expected". "" if the grammar cannot tell. (phase 6) */
String describeContextAt(String content, int offset);

/** Deterministic corrections for an error, in addition to the generic ones of §9.3. (phase 6) */
List<TextEdit> quickFixes(String content, ParseError error);
```
`TextEdit` would then move from `matchbox-assist` to `grammarapi`. Once the app is published,
contract changes will need a version policy, for instance accepting a range of versions in
`PluginManager`, which currently requires `getApiVersion() == API_VERSION` exactly.

---

## 7. Grammar check of suggestions (drop suggestions that add errors)

### 7.1 Rule
`EditValidator.check(text, edits, grammars)`:
1. Apply the edits to a copy of the text.
2. Run every grammar of the file on the copy (`getResult` is thread-safe by contract).
3. Compare with the errors of the current text:
   - **Reject** if any error starts inside the edited range or right after it (up to the end of
     the enclosing paragraph), and that error wasn't there before.
   - **Reject** if the total error count increases.
   - **Accept** otherwise. Mark the suggestion "fixes N errors" if the count decreases.
   - Errors after the edit are compared after shifting their offsets by the length delta.

### 7.2 Where it applies
| Feature | Rejected suggestion |
|---|---|
| Inline ghost text | Never shown. Optionally retried once, asking the model again with the error message |
| AI review edits (panel) | Dropped. The panel shows "2 suggestions discarded (would introduce parse errors)" with a "show anyway" link for transparency |
| Fix proposed with an explanation | The explanation is shown and the fix is hidden, with a note |
| Grammar quick fixes (§9.3) | Same check, since a generic fix can be wrong |

### 7.3 Inline completions in the middle of a block
A ghost suggestion is a partial edit, and the block is often still incomplete while the user
types. "No new error" is measured against the current text, which already contains the
incomplete-block error. So a suggestion that keeps the same error but doesn't add one passes,
and one that completes the block (fewer errors) gets priority.

### 7.4 Cost
- Use a dedicated `matchbox-assist-validate` executor (1–2 daemon threads). Don't use the
  project's single `matchbox-parser` executor, or validation would delay the user's own parses.
- Files above a size threshold (e.g. 200 kB): validate on the enclosing paragraph only,
  assuming paragraphs are independent. This is true for notebook and lex files. A grammar can
  opt out later with a flag.

---

## 8. Inline ghost text

### 8.1 Rendering (insert as provisional text)
IGT suggestions span several lines, so the ghost text must take part in the layout:
`GhostTextController` inserts the text at the caret, styles it
`-fx-fill: #9a9a9a; -fx-font-style: italic;`, keeps the caret before it and records
`ghostStart`/`ghostLength`.

Guards needed by the current `EditorController`:
1. **`changeListener`** must ignore changes while a ghost operation runs. Otherwise
   `Project.updateFileContent` receives the ghost text and marks the file dirty.
2. **`highlightText()` clears every style** (`clearStyle(0, length)`) on each corpus refresh, and
   span offsets after the caret would be shifted by the ghost. `highlightText` must therefore
   call `ghost.suspend()` (remove), highlight, then `ghost.resume()` (re-insert and re-style).
   Simpler alternative: dismiss the ghost on corpus refresh.
3. **Undo**: build the area's undo manager from `plainTextChanges()` filtered on "not a ghost
   operation" (RichTextFX `UndoManagerFactory`).
4. **`saveEditedFile`, `parseEditedFile`, `handleCodeAreaClick`, `selectInEditor`,
   `selectEditedFile`, `clearEditor`** call `ghost.dismiss()` first.
5. `AssistContextFactory` reads the text from the area with the ghost excluded, not from
   `TextDocument`, which may lag behind by up to 500 ms.

If insertion proves fragile, a fallback is an overlay `Text` node placed with
`textArea.getCaretBounds()`. It is single-line only, but needs none of these guards.

### 8.2 Keys (`org.fxmisc.wellbehaved.event.InputMap`)
| Key | Action |
|---|---|
| `Ctrl+Space` | Request a suggestion |
| `Tab` (ghost shown) | Accept: the text becomes normal, one change reaches the model and undo |
| `Ctrl+→` | Accept the next word only |
| `Esc` / typing / caret move / click | Dismiss. If the typed char matches the ghost's first char, consume it instead |

### 8.3 Lifecycle
Debounce with a `PauseTransition` in automatic mode, and cancel the request in flight on new input.
Each request carries `(path, caret, textHash, seq)`, and the response is dropped if any of them
changed. The response is post-processed (strip fences and any echo of the prefix, cap at about
15 lines), then validated (§7), then shown. Streaming can come later, showing the ghost text as
tokens arrive and validating at the end.

---

## 9. Suggestion side panel

### 9.1 Layout (`FileMode.fxml`)
The top item of `topSplitPane` (toolbar + editor `VBox`) becomes a **horizontal `SplitPane`**:
`[editor VBox | SuggestionPanel]`, divider at about 0.72. The panel is collapsible with the
**Suggestions** toggle in the editor toolbar, and its visibility is saved in
`AssistSettingsData.suggestionPanelVisible`.

```
┌ toolbar: Save Parse Close │ Suggestions ▣ ─────────────────────────────┐
│ editor (InlineCssTextArea)              │ Suggestions (7)              │
│                                         │ [Errors 3] [Fixes 2] [AI 2]  │
│                                         │ Review: (block ▾) [Run AI]   │
│                                         │ ──────────────────────────── │
│                                         │ ⛔ 12:4  missing ''' …        │
│                                         │    Insert '''   [Apply]       │
│                                         │    [Explain] [Go to] [✕]     │
│                                         │ ✦ 18:0  gloss count mismatch │
│                                         │    - 1sg down-canoe          │
│                                         │    + 1sg down-canoe-TOP      │
│                                         │    [Apply] [Go to] [✕]       │
└─────────────────────────────────────────┴──────────────────────────────┘
```

### 9.2 Model: `SuggestionPanelController`
- `ObservableList<Suggestion>` sorted by offset, filtered by source chips (Errors / Fixes / AI).
- Fed by:
  1. **Parse errors**: on `Corpus.addFileListener` for the current file, from
     `Corpus.errorsFor(path)`. Each error becomes a `PARSE_ERROR` suggestion without edits.
     AI explanations already attached to an error are kept if an error with the same
     `(grammarId, message, line text)` still exists after reparsing.
  2. **Grammar fixes** (§9.3), computed with the errors.
  3. **AI**: results of *Explain* (§11) and *Review* (§9.4).
- When the file changes in the editor, anchors are relocated (§9.5), and suggestions that can't
  be relocated are greyed out as *outdated*.

### 9.3 Deterministic grammar fixes (no AI needed)
`assist.suggestion.GrammarQuickFixes` builds fixes from the new `ParseError` fields (§6.2):
- A literal expected token is one whose display name is quoted (`'<'`). The fix inserts it with
  the quotes removed and ANTLR escapes (`\'`, `\n`…) decoded.
- *missing X* with a literal expected token: **Insert `X`** at `getStartIndex()`.
- *extraneous input*: **Delete** the offending token.
- Several literal expected tokens: one fix per token, up to 3.
- Symbolic-only expected tokens (e.g. `IMB_LINE`): no edit, but the panel shows "expected: gloss
  line" when the grammar documentation maps token names to descriptions (optional
  `token-names.properties` next to `documentation.md`).

Every fix goes through `EditValidator` (§7) and is shown only if it reduces the error count.
These fixes work with the AI disabled, so phase 3 is useful on its own.

### 9.4 AI review
- Scope selector: *current block* (the deepest top-level `CorpusObject` at the caret, or the
  paragraph), *selection*, or *file*. For files, the text is chunked by paragraphs within the
  context limit.
- Uses the shared prefix (§5) plus a REVIEW task asking for **structured output**:
  ```json
  {"edits": [{"original": "...", "replacement": "...",
              "explanation": "...", "kind": "syntax|consistency|format"}]}
  ```
  Requested with Anthropic structured outputs (tool/JSON schema), OpenAI `response_format`
  `json_schema`, and Ollama `format` (JSON schema). The fallback is lenient JSON extraction.
- **Locating edits**: models give unreliable character offsets, so they return the exact
  `original` text instead. The panel locates it in the scope; it must be unique there, or
  unique nearest to the reviewed error. Edits that can't be located are dropped.
- Each located edit goes through `EditValidator` and is kept only if it adds no errors.
- *consistency* findings (e.g. a gloss abbreviation used differently than elsewhere in the
  project) are the main value beyond syntax. They come from the few-shot examples in the
  prefix.

### 9.5 Applying, previewing and relocating
- **Preview**: hovering an item highlights its range in the editor with a temporary background.
  The item shows a two-line diff (removed in red with strike-through, added in green).
- **Apply**: `textArea.replaceText(start, end, replacement)` as **one undoable change**. Before
  applying, check that `text.substring(start, end).equals(expectedOriginal)`, and relocate if it
  doesn't match. After applying, call `editorController.parseEditedFile()`.
- **Apply all fixes of this kind** (later): apply in reverse offset order, validated as a whole.
- **Relocation** (`Anchor`): when the text changes, shift anchors located after the change by
  its length delta. If the change overlaps an anchor, search for `expectedOriginal` within
  ±200 characters, and mark the suggestion outdated if it isn't found.
- **Keys**: `F8` / `Shift+F8` go to the next/previous suggestion, `Alt+Enter` applies the
  focused one.

### 9.6 The existing "Parsing error" tab
The tab stays for now and gets a context menu: *Explain*, *Show in suggestions panel*. Once the
panel is stable, the tab could be removed, since the panel shows the same errors with more
detail.

---

## 10. Privacy

Field notes may contain consultants' personal data or material under consent restrictions.

### 10.1 Warning before text leaves the machine
- The first time a **cloud** provider (Claude, ChatGPT, or an OpenAI-compatible base URL that
  isn't `localhost`/`127.0.0.1`) is used, `CloudConsentDialog` explains:
  - which provider receives the data and that its terms and retention policy apply,
  - **what is sent**: the text around the caret or the reviewed block, a few examples from the
    project, and the grammar documentation,
  - that Ollama keeps everything on the computer.

  *Continue* records the provider in `cloudConsentGiven`. *Use local model instead* opens the
  dialog with Ollama selected.
- **Status indicator** in the editor toolbar: `AI: Claude ☁` or `AI: Ollama (local)`. Clicking it
  opens `AiModelDialog`.
- The `AiModelDialog` privacy box (§4.2) repeats this.

### 10.2 Per-project restriction
- `.mbx`: `<assistant allowCloud="false"/>` under `<project>`, read and written by
  `ProjectXMLSerializer`, exposed as `Project.isCloudAssistAllowed()`, and editable in
  `ProjectPropertiesDialog` (checkbox *Allow cloud AI providers for this project*).
- When it is false, `PrivacyPolicy` blocks cloud providers for this project and the status
  indicator explains why. Local providers still work.
- Few-shot examples are taken only from the **current project**, never from other projects.

### 10.3 Logging
- Through `LoggingSetup`'s logger: provider, model, task, characters sent and received,
  latency, cache hit tokens when reported. **Not the text** and never the keys.
- An opt-in debug setting (`-Dmatchbox.assist.logPrompts=true`) logs full prompts for
  development.

---

## 11. "Explain this parse error"

### 11.1 Entry points
- **Explain** button on a `PARSE_ERROR` item in the side panel.
- Context menu **Explain** in the *Parsing error* tab.
- Editor context menu **Explain error here** when the caret is inside an error span (uses
  `ParseError.span()`).

### 11.2 Prompt (EXPLAIN task, after the shared prefix)
- The grammar that reported the error (`grammarId` → its documentation is already in the
  prefix, so the task names it).
- Message, line and column, **expected tokens** (§6.2), mapped to readable names when
  `token-names.properties` exists.
- `describeContextAt` output when the plugin implements it.
- Text: ±10 lines around the error, with a `⟦ERROR⟧` marker at `startIndex`.
- Output contract (structured):
  ```json
  {"explanation": "plain-language, 2-4 sentences, no ANTLR jargon",
   "fix": {"original": "...", "replacement": "..."} | null}
  ```
- Explanation language: follows the UI language, with an *Explanation language* option in the
  dialog. The default is English. Linguists may prefer French.

### 11.3 Result
- Shown inside the panel item (expandable). The `fix`, if any, is located (§9.4), validated
  (§7) and offered as **Apply**. If the fix is rejected, the note "the proposed fix would
  introduce other errors" is shown.
- Explanations are cached per `(grammarId, message, hash of the ±10 lines)` for the session, so
  reopening the item costs nothing.
- Uses the *smart* model (§4.2).

---

## 12. Implementation phases

| Phase | Content | Modules |
|---|---|---|
| **1. Grammar API** | ✅ Mandatory `getFormatDocumentation`/`getExamples`/`getGrammarSource` + `GrammarExample`, with placeholders in the plugins; ✅ `ParseError` offsets + expected tokens filled by `CollectingErrorListener`. **Remaining**: the actual `documentation.md`, examples, `.g4` source and `token-names.properties` for the notebook grammar (then conll, lex) | `matchbox-grammar-api`, `grammars/*` |
| **2. Assist module & settings** | `matchbox-assist` skeleton; providers (Anthropic, OpenAI-compatible); `PromptPrefix`/`PromptBuilder`; `EditValidator`; `AssistSettingsData` in `SettingsData`/`UserSettings`; `ApiKeyStore`; `AiModelDialog`; `CloudConsentDialog`; status indicator; `.mbx` `allowCloud` | `matchbox-assist`, `matchbox-app`, `matchbox-gui`, `matchbox-distribution` |
| **3. Side panel, no AI** | Layout change; `SuggestionPanelController` with parse errors and `GrammarQuickFixes`; validation; apply, preview and relocate; F8 navigation | `matchbox-gui`, `matchbox-assist` |
| **4. Explain parse error** | Explain in panel, error tab and editor context menu; structured output; cached explanations; validated fix | both |
| **5. Inline ghost text** | `GhostTextController` with the guards of §8.1; manual trigger; validation; then automatic trigger | `matchbox-gui` |
| **6. AI review & improvements** | Review (block, selection, file); consistency findings; streaming; `describeContextAt` in the notebook plugin; plugin `quickFixes` | all |

Phase 3 comes before the AI-dependent features because it's useful on its own, and it builds
the panel, anchors and validation infrastructure that phases 4 and 6 reuse.

---

## 13. Tests (JUnit 5, `mvn test`)

| Module | Tests |
|---|---|
| `matchbox-grammar-api` | ✅ offsets and expected tokens (in `NotebookGrammarPluginTest`); resource helper loads through the plugin class loader (when added) |
| `grammars/*` | Each `getExamples()` snippet parses with zero errors (keeps the docs in sync with the grammar); once documented, `getFormatDocumentation()` is non-empty |
| `matchbox-app` | `.mbx` round trip with `<assistant allowCloud>`; `SettingsData` v1 → v2 migration |
| `matchbox-assist` | `PromptPrefix` is byte-identical across calls (golden file) and changes when grammars change; `EditValidator` accept/reject cases; `GrammarQuickFixes`; edit location (unique, ambiguous, absent); anchor relocation; providers against the JDK `HttpServer` with recorded JSON/SSE (200, 401, 429, timeout, cancel); keys absent from `toString` and logs |
| `matchbox-gui` | Headless (TestFX + Monocle): ghost text never reaches `TextDocument`, undo or the saved file; ghost survives `highlightText`; applying a suggestion is one undo step |

---

## 14. Other notes
- **Editor sync**: the `changeListener` still *throttles* (it drops edits that arrive within
  500 ms of the previous one). `saveEditedFile` and `parseEditedFile` now push the latest text,
  which limits the damage. A `PauseTransition` debounce would be cleaner and could be shared
  with the automatic suggestion trigger.
- **Convert selection to grammar X** (later): reuses REVIEW with a different task. It rewrites
  loosely written notes into well-formed blocks for the chosen grammar, then validates them (§7).
- **CLI**: `matchbox-assist` has no JavaFX dependency, so a command-line `matchbox review` /
  `matchbox fix` could reuse it unchanged.
