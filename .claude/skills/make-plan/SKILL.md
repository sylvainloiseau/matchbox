---
name: make-plan
description: Use whenever the user asks to plan, write a plan, make a plan, design or propose an architecture for a feature, mode, plugin, refactoring or improvement of Matchbox, before any implementation ("make a plan for…", "plan the…", "write a plan", "how would you design…"). Produces .claude/plans/<NAME>_PLAN.md with an architectural assessment, GUI design, steps, tests, questions with recommendations and suggestions; asks the open questions; does not implement.
---

# Goal

Make a plan with a strong architectural basis, future-proof and according to high standards in
GUI design. Ask questions about unclear points and make suggestions that could improve the GUI
and the user experience.

A plan is written **before** any code, and nothing is implemented until the user says so
("implement the plan", "start implementing").

# Workflow

1. **Read before planning.** Read `CLAUDE.md`, the related plans in `.claude/plans/`, and the code
   the feature touches: the classes, the interfaces it would extend, and the existing features it
   could reuse. Quote real class and method names; never plan against an imagined API.
2. **Write the plan** in `.claude/plans/<NAME>_PLAN.md` (upper case, e.g. `TAG_BROWSER_PLAN.md`),
   following the structure below. If the plan already exists, update it instead of starting a new
   one.
3. **Ask the open questions** with `AskUserQuestion`:
   - at most 4 per call, the recommended option first, labelled "(Recommended)";
   - several calls if needed;
   - only questions whose answer changes the design; for the others, state the default in the plan.
   If the user rejects the tool to clarify, ask what they want to clarify, then ask again.
4. **Fold the answers into the plan.**
   - Record each one under "Decided with the user", and remove it from the open questions.
   - Update every section it affects. Check for contradictions: for example, a refused merge in
     one place must not be offered elsewhere.
5. **Report** in chat, briefly:
   - where the plan is;
   - the architectural problems found;
   - the decisions taken;
   - what remains open, with its default;
   - "tell me when to implement".

When the plan is implemented, set its status to "implemented" and add a final section,
"Differences between the plan and the code".

# Structure of a plan

```
# Plan: <feature>

Status: proposal, not implemented. <where the decisions and questions are>

## 0. Goal                      what the user gets; what is out of scope
## 1. Architectural assessment  problems in the request or the code (P1, P2…), each with a proposal
## 2. User interface            layout (ASCII mock-up), controls, menus, keyboard, empty states
## 3. Model                     pure classes (no JavaFX controls), one line each
## 4. View classes              JavaFX classes and what they do
## 5. Data flow                 from a corpus change / user action to the texts and back
## 6. Settings                  new UserSettings / SettingsData fields, defaults, validation
## 7. Implementation steps      each step compiles and passes `mvn clean install`
## 8. What is not changed       APIs and features left as they are
## 9. Tests                     unit tests of the pure classes; GUI tests (TestFX, headless)
## 10. Files                    new and changed
## 11. Questions                "Decided with the user", then open ones (recommendation first)
## 12. Suggestions              improvements beyond the request (accepted ones go into §7)
```

Leave out sections that do not apply. Use tables for mappings ("today" → "after", class → role).

# Architectural rules to check in every plan

- **The text is the only source of truth.** Views and modes never change corpus objects. Changes
  are text edits through `ViewContext` / `ViewEditService`, then a re-parse. Changes of several
  objects or files go through the batch edit (`setFields`), all or nothing, as one entry of the
  undo journal (`EditJournal`).
- **Plugins map a notation to corpus objects; modes navigate them.**
  - Application features depend on **capabilities** (`matchbox-corpus` `capability`), never on a
    plugin id, a kind or a notation.
  - A capability is added only when an application feature needs it.
  - Plugins contribute no mode, no menu.
- **Plugin-facing APIs** (`matchbox-grammar-api`, `matchbox-grammar-fx`) change only in
  backward-compatible ways (default methods). An incompatible change bumps `API_VERSION` and says
  why.
- **Modes** (`MODE_PLAN.md`, `create-mode` skill):
  - they use the window services (`Workbench`) and own no thread or closable resource;
  - they are created lazily and kept alive, cheap while hidden (`AbstractMode`);
  - each has its own selection and focus, and its own menu; the window menus are mode-independent;
  - "show in editor" goes to File Mode, and jumps are recorded for Back/Forward.
- **References point down**: Corpus → Text → objects. Go up with `CorpusView.textOf`.
- **Threading**: parsing runs off the JavaFX thread; results are applied on it.
- **Settings** go through `UserSettings` (a nested `…SettingsData` record for a feature), validated
  with defaults.
- **Performance**: incremental updates per text, virtualised lists for long results, no work while
  a mode is hidden.
- **Testability**: put the logic in pure classes tested without JavaFX. The GUI is tested with
  TestFX on the real window (`MainWindowFixture`, headless). Say what cannot be tested headless
  (e.g. native drag and drop).

# GUI design standards to apply

- **Every action can be done with the keyboard**: a shortcut or menu item. Drag and drop is never
  the only way.
- **Confirmation and preview** before changes that touch several files or many objects. They say
  what will change ("Renames 12 tags in 4 files…") and what prevents it; nothing is changed if
  anything fails.
- **Undo** in one step, labelled with what it undoes and where. **Nothing is saved
  automatically**; changes to files the user cannot see are reported on the status line, with a
  link (Show, Save all).
- **Empty states**, which say what to do: "No tags in the project. Write #"keyword"…".
- **Tooltips** on controls and counts. Accessible texts on custom cells.
- **The user's choices are remembered** where it helps: sizes, sort, filters.
- **Selection and scroll are kept** across re-parses.
- **No blocking work** on the JavaFX thread.
- **Consistency** with existing modes and dialogs: the status line, the error dialogs
  (`ErrorReporter`), and the wording.

# Questions and suggestions

- Questions point out ambiguities in the request: what is counted, what a click shows, what
  happens on a conflict, what happens on a partial failure, how things are named.
- Each question gives a recommendation and its reason. The alternatives are concrete options.
- Suggestions go beyond the request:
  - filters and sorting;
  - navigation between modes;
  - export;
  - keyboard and accessibility;
  - previews;
  - grouping;
  - later features.
  Each suggestion says what it would need architecturally. Ask which ones to include.

# After the plan

- Do not implement, commit or push until the user asks.
- When implementing:
  - follow the steps;
  - write the tests;
  - run `mvn clean install` (the IDE can leave stale classes in `target/`);
  - update `CLAUDE.md` and `README.md`;
  - add the "Differences between the plan and the code" section.
