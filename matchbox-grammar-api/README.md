# matchbox-grammar-api

The contract between Matchbox and its grammar plugins (`GrammarPlugin`, `ParseResult`, `ObjectEditor`, `Spans`, `TimeCodes`, `CollectingErrorListener`). No JavaFX.

## What a plugin is

**A grammar plugin is a mapping from a textual notation to corpus objects.** It recognises the conventions a linguist uses in their plain-text notes (a way of writing a lexical entry, an interlinear gloss, a heading, a tag…) and turns each occurrence into a `CorpusObject` (`matchbox-corpus`) anchored to the span of text it was written in.

That is the whole job of a plugin:

- **in**: the content of a text (`getResult(TextId, String)`);
- **out**: the objects found, each with its span, and the diagnostics (`ParseResult`);
- **back**: optionally, how to write a changed value in the plugin's notation (`getObjectEditor()`), so that views can edit the objects through the text.

A plugin may also ship, with `matchbox-grammar-fx`, a view of its own grammar-specific objects and actions offered in the editor margin. It never contributes modes, menus or other application features: browsing, searching and navigating the corpus belong to the application (its *modes*), which work the same whatever plugins produced the objects.

## Several notations for the same concept

Several plugins may create objects of **the same concept** with **different notations**. Examples:

- a lightweight convention to write a lexical entry quickly during a session (`<mami> 'pig'`), and a more elaborate syntax for entries with senses, examples and notes: two plugins, two notations, both producing objects that implement `LexicalEntry`;
- `#"keyword"` (the tag plugin) is one way to write a `Tag`; other plugins may offer other tag conventions, suited to other linguists' habits.

A linguist chooses, for each type of file of their project, the plugins matching the conventions they write in.

This is made possible by the distinction between:

| | What it says | Defined by |
|---|---|---|
| **kind** (`"<grammar id>:<name>"`, e.g. `"notebook-grammar:lexical-entry"`) | which plugin and which notation produced the object | each plugin |
| **capability** (`LexicalEntry`, `InterlinearSentence`, `Section`, `Tag`, `MediaSegment`…) | what the object is for the application | `matchbox-corpus` (`fr.cnrs.lacito.matchbox.corpus.capability`) |

The application's features (modes, built-in views, exports, cross-linking) depend on capabilities only, never on a plugin, a kind or a notation. A plugin whose objects implement a capability gets these features for free; a new notation for an existing concept needs no change to the application.

Consequences for plugin authors:

- if the data is a concept the application knows, implement its capability interface (the `Simple*` classes of `matchbox-corpus` can be reused);
- otherwise, define a grammar-specific type in the plugin module; a capability is added to `matchbox-corpus` only when an application feature needs it;
- give the kinds the plugin's id as prefix, and never change the id (it is stored in project files).

How to write a plugin: see the `write-grammar` skill (`.claude/skills/write-grammar/SKILL.md`) and the reference plugins `grammars/matchbox-grammar-notebook` and `grammars/matchbox-grammar-conll`.
