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

`grammars/matchbox-grammar-notebook` (reuses the `Simple*` classes) and `grammars/matchbox-grammar-conll` (ships its own view) are the reference examples.

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
- `getResult(Path, String)`:
  - must be re-entrant and thread-safe: it runs on a background thread. Use local variables, not instance fields, for the lists you build;
  - returns `new ParseResult(objects, errors)`, never `null`;
  - collects syntax errors with `CollectingErrorListener`;
  - catches `RuntimeException` and reports it as a `ParseError` instead of printing it;
  - skips rules produced by ANTLR error recovery (`ctx.exception != null`, or missing tokens) instead of failing the whole file.

# Corpus objects

Every object returned implements `fr.cnrs.lacito.matchbox.corpus.CorpusObject`:

- `getTextCoordinate()`: build it with `Spans.of(filePath, ctx)` (grammar API). The stop index is inclusive.
- `getKind()`: `"<plugin id>:<name>"`, e.g. `"conll-grammar:sentence"`. Declare the kinds as constants.
- `getLabel()`: short text for tables and trees.
- `getChildren()`: nested objects. A child's span must lie inside its parent's span.

Objects with an invalid span are dropped by the application and reported as parse errors.

Rules:
- objects are immutable once returned, and their lists unmodifiable (`List.copyOf`);
- objects keep no reference to ANTLR contexts, tokens or token streams: copy the text out (`ctx.getText()`, `token.getText()`), otherwise every parse tree stays in memory;
- no arrays in public accessors (use `List<Integer>`).

Which type to use:
1. If the data is a concept the application knows (`fr.cnrs.lacito.matchbox.corpus.capability`: `LexicalEntry`, `Sense`, `Example`, `InterlinearSentence`, `DependencySentence`), implement that interface, so lexicon features and exports work. The immutable classes of `fr.cnrs.lacito.matchbox.corpus.simple` (`SimpleLexicalDefinition`, `SimpleIGT`…) can be reused; they take the kind as first constructor argument.
2. Otherwise, write your own class implementing `CorpusObject` (or extending `simple.AbstractCorpusObject`) in the grammar module. Do not add classes or interfaces to `matchbox-corpus` for a grammar-specific type: a capability interface is added there only when an application feature needs it.

The application displays any object with a generic view (kind, position, tree of children) and highlights it in the editor.

# Optional: a view for the objects

Implement `fr.cnrs.lacito.matchbox.grammarfx.ObjectViewFactory<T>` and list it in `src/main/resources/META-INF/services/fr.cnrs.lacito.matchbox.grammarfx.ObjectViewFactory`:

- `getType()`: the class or interface rendered. The application uses the factory of the closest supertype of the object's class, so a factory for a capability interface also applies to other grammars' implementations.
- `createView(object, context)`: called on the JavaFX thread. Use `context.selectInEditor(span)` to navigate.
- `getColumns()`: `ObjectColumn`s for the table of objects of this kind.
- If the view loads FXML: `loader.setClassLoader(getClass().getClassLoader())`, otherwise the plugin's classes are not found. CSS: `getClass().getResource(...).toExternalForm()`.

See `ConllSentenceViewFactory` in `grammars/matchbox-grammar-conll`.

# Tests

Put tests in `src/test/java` with sample files in `src/test/resources`. Add the `matchbox-corpus` test-jar (`<type>test-jar</type>`, `<scope>test</scope>`) and use `CorpusAssertions.assertSpansNested(...)` and `assertKindsPrefixed(...)`. Also test that two successive `getResult` calls return independent results.
