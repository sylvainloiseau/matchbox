---
name: write-grammar
description: write a grammar for a specific type of files for the matchbox APP
---

# What is a matchbox grammar

- a grammar has a name (refered to as `<grammar-name>` below) that reflects the file type it supports or the parser it exposes.
- A grammar is a maven submodule located in the `grammars` directory and named `matchbox-grammar-<grammar-name>`.
- The java sources are in sub-packages of `fr.cnrs.lacito.matchbox.grammar.<grammar-name>`
- It implements the matchbox-grammar-api, which is a maven module in the current directory.
- it has a file `fr.cnrs.lacito.matchbox.grammarapi.GrammarPlugin` under src/main/resources/META-INF/services/, which contains the qualified name of the class that implements the `GrammarPlugin` interface, e.g. `fr.cnrs.lacito.matchbox.grammar.conll.ConllGrammarPlugin` for the grammar that supports CONLL files.

If a grammar contains it own ANTLR parser, it is under the `fr.cnrs.lacito.matchbox.grammar.<grammar-name>` namespace, in the src/main/antlr4 directory

`grammars/matchbox-grammar-notebook` (reuses the `Simple*` classes), `grammars/matchbox-grammar-conll` (ships its own view) and `grammars/matchbox-grammar-markdownheading` (implements the `Section` and `TextHeader` capabilities, ships a view built on the shared `MetadataPane`, a text action, and an `ObjectEditor` adding and removing fields) are the reference examples; `grammars/matchbox-grammar-mediaref` is a pure grammar (no view, no JavaFX) implementing a capability (`MediaSegment`) that the application displays and plays.

# Dependencies

All matchbox dependencies use the `provided` scope: the application supplies them, and the plugin jar must not contain them.

| Dependency | When |
|---|---|
| `matchbox-corpus`, `matchbox-grammar-api` | always |
| `matchbox-grammar-fx`, `org.openjfx:javafx-controls` | only if the grammar ships a view (see below) |

Check the built jar: `unzip -l build/plugins/matchbox-grammar-<grammar-name>-*.jar` must list no `javafx/**` and no `fr/cnrs/lacito/matchbox/{corpus,grammarapi,grammarfx}/**`.

# The `GrammarPlugin` class

- `getId()`: a stable id, e.g. `"<grammar-name>-grammar"`. It is stored in project (.mbx) files and prefixes the kinds of the objects, so never change it.
- `getApiVersion()`: implement it as `return API_VERSION;` in the plugin class itself. A plugin compiled against another version of the API is not loaded.
- `getResult(TextId text, String content)`:
  - must be re-entrant and thread-safe: it runs on a background thread. Use local variables, not instance fields, for the lists you build;
  - returns `new ParseResult(objects, diagnostics)`, never `null`;
  - collects syntax errors with `new CollectingErrorListener(text, getId())` (`getDiagnostics()`);
  - catches `RuntimeException` and reports it as `Diagnostic.at(text, getId(), 0, 0, "Internal error of the grammar: " + e)` instead of printing it;
  - skips rules produced by ANTLR error recovery (`ctx.exception != null`, or missing tokens) instead of failing the whole file.

# Corpus objects

Every object returned implements `fr.cnrs.lacito.matchbox.corpus.CorpusObject`:

- `getTextCoordinate()`: build it with `Spans.of(text, ctx)` or `Spans.of(text, token)` (grammar API), `text` being the `TextId` given to `getResult`. The stop index is inclusive.
- `getKind()`: `"<plugin id>:<name>"`, e.g. `"conll-grammar:sentence"`. Declare the kinds as constants.
- `getLabel()`: short text for tables and trees.
- `getChildren()`: nested objects. A child's span must lie inside its parent's span.

Objects with an invalid span are dropped by the application and reported as parse errors.

Objects keep no reference to their text or to the corpus: the application finds the text of an object from `getTextCoordinate().text()` (`CorpusView.textOf`), with its header properties and the sections enclosing the object (`Text.metadataOf`, `Text.enclosingSections`).

Rules:
- objects are immutable once returned, and their lists unmodifiable (`List.copyOf`);
- objects keep no reference to ANTLR contexts, tokens or token streams: copy the text out (`ctx.getText()`, `token.getText()`), otherwise every parse tree stays in memory;
- no arrays in public accessors (use `List<Integer>`).

Which type to use:
1. If the data is a concept the application knows (`fr.cnrs.lacito.matchbox.corpus.capability`: `LexicalEntry`, `Sense`, `Example`, `InterlinearSentence`, `DependencySentence`, `Section` for a titled division with metadata and content, `TextHeader` for the properties of the whole text), implement that interface, so lexicon features and exports work. The immutable classes of `fr.cnrs.lacito.matchbox.corpus.simple` (`SimpleLexicalDefinition`, `SimpleIGT`…) can be reused; they take the kind as first constructor argument.
2. Otherwise, write your own class implementing `CorpusObject` (or extending `simple.AbstractCorpusObject`) in the grammar module. Do not add classes or interfaces to `matchbox-corpus` for a grammar-specific type: a capability interface is added there only when an application feature needs it.

The application displays any object with a generic view (kind, position, tree of children) and highlights it in the editor.

# Optional: editable objects

To let views edit an object (table cells, detail views), give the spans of its values:

- implement `fr.cnrs.lacito.matchbox.corpus.HasFields`: one `Field(name, value, span)` per editable value, the span covering the raw text of the value without its delimiters (e.g. the text between the quotes of a translation); an empty value has an empty span (`stopIndex = startIndex - 1`, `startIndex` being where the value would be inserted). The `Simple*` classes take the fields as last constructor argument; use the field-name constants of the capability interfaces (`InterlinearSentence.FREE_TRANSLATION`…);
- if values need escaping or some characters would break the notation, override `GrammarPlugin.getObjectEditor()`: it turns a new value into `TextEdit`s or refuses it (`EditProposal.Refused` with a reason for the user). The default, `ObjectEditor.VERBATIM`, replaces the raw text of the span.

The application never changes objects: it applies the edits to the text and parses it again. Edits adding parse errors are refused by default. See `NotebookGrammarPlugin` and `NotebookEditingTest`.

# Optional: a view for the objects

Implement `fr.cnrs.lacito.matchbox.grammarfx.ObjectViewFactory<T>` and list it in `src/main/resources/META-INF/services/fr.cnrs.lacito.matchbox.grammarfx.ObjectViewFactory`:

- `getType()`: the class or interface rendered. The application uses the factory of the closest supertype of the object's class, so a factory for a capability interface also applies to other grammars' implementations.
- `createView(object, context)`: called on the JavaFX thread. Use `context.selectInEditor(span)` to navigate.
- `getColumns()`: `ObjectColumn`s for the table of objects of this kind; `ObjectColumn.editable(title, field, value)` makes the cells edit a field.
- To edit from the view: `context.canEdit(object)`, then `context.setField(object, field, value)` or `context.applyEdits(object, edits)`; the returned `EditOutcome` says whether the change was applied, and why not. `context.corpus()` gives the text of the object, its sections and metadata.
- `updateView(view, object, context)`: optionally shows the re-parsed object in the existing view (keeps focus and edits); return `false` to have the view rebuilt.
- Metadata (key/value maps): reuse `fr.cnrs.lacito.matchbox.grammarfx.metadata.MetadataPane` (typed values from `context.metadataSchema()`, `*` to add, × to delete); it calls `context.addField`/`removeField`, so the grammar's `ObjectEditor` must implement `addField`/`removeField`. Needs `javafx-media` (`provided`).

# Media segments

Objects that refer to audio or video (a file and/or a time range) should implement `capability.MediaSegment` with the fields `media`, `range`, `start`, `end`, `caption` (`HasFields`) and an `ObjectEditor` supporting them: the application then shows them with its segment view and player, a ▶ button in the editor margin and the play shortcut, with no view code in the plugin. Use `grammarapi.TimeCodes` to read and write times.

# Tags

A grammar producing keywords attached to places of a text should implement `capability.Tag` (or reuse `simple.SimpleTag`, with the field `tag`): the application then shows them in the views of the objects they apply to (`Text.targetOf`) and highlights them. See `grammars/matchbox-grammar-tag`.

# Optional: actions on a text

Implement `fr.cnrs.lacito.matchbox.grammarfx.TextActionProvider` (service file `META-INF/services/fr.cnrs.lacito.matchbox.grammarfx.TextActionProvider`): from a parsed `Text`, return `TextAction`s (label, offset of the line, `TextEdit`s relative to `text.content()`, offset to select after the parse). The application shows them in the editor margin and applies them as one undoable change; return actions only for texts your grammar parsed (`text.grammarIds()`). See `MarkdownHeadingActions`.
- If the view loads FXML: `loader.setClassLoader(getClass().getClassLoader())`, otherwise the plugin's classes are not found. CSS: `getClass().getResource(...).toExternalForm()`.

See `ConllSentenceViewFactory` in `grammars/matchbox-grammar-conll`.

# Tests

Put tests in `src/test/java` with sample files in `src/test/resources`. Add the `matchbox-corpus` test-jar (`<type>test-jar</type>`, `<scope>test</scope>`) and use `CorpusAssertions.assertSpansNested(...)` and `assertKindsPrefixed(...)`. Also test that two successive `getResult` calls return independent results, and, for editable objects, that setting a field then parsing again gives the new value.
