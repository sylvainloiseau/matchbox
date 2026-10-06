If a plugin doesn't register its own view, the GUI uses one of its own built-in views in matchbox-gui/src/main/java/fr/cnrs/lacito/matchbox/gui/view/. Which one depends on the object's type.

How the factory is chosen

ObjectViewRegistry (in gui/view/ObjectViewRegistry.java) loads every ObjectViewFactory service. That includes the plugins' factories and the built-in ones listed in matchbox-gui/src/main/resources/META-INF/services/fr.cnrs.lacito.matchbox.grammarfx.ObjectViewFactory. For an object of class C, it picks the factory in this order:

1. The factory whose getType() is the closest supertype of C. It searches breadth-first through the superclass and interfaces, and C itself counts first.
2. If two factories tie, the one with the higher getPriority() wins (built-in factories use 0).
3. If still tied, a plugin factory wins over a built-in one.
4. If nothing matches, it uses GenericObjectViewFactory.

The same fallback also applies at runtime: if a plugin's createView throws an exception or returns null, the generic view is shown. If one of its columns throws, that cell is left empty. The failure is logged once per factory.

The three built-in factories

┌────────────────────────────────┬────────────────────────────────┬───────────────────────────────────────────────────────┬──────────────────────┐
│            Factory             │            Handles             │                      Detail view                      │    Table columns     │
├────────────────────────────────┼────────────────────────────────┼───────────────────────────────────────────────────────┼──────────────────────┤
│                                │                                │ Header with getKind() and the file/line range, then a │ None, so you get     │
│ GenericObjectViewFactory       │ CorpusObject (any object)      │  TreeView of the object and its getChildren(), each   │ Line + Label         │
│                                │                                │ shown as label [kind]. Double-clicking a node selects │ (getLabel())         │
│                                │                                │  its span in the editor.                              │                      │
├────────────────────────────────┼────────────────────────────────┼───────────────────────────────────────────────────────┼──────────────────────┤
│                                │                                │ Grid with Transcription, Glosses and Translation      │ Transcription,       │
│ InterlinearSentenceViewFactory │ capability.InterlinearSentence │ (monospace), plus a clickable position                │ Morphemic glosses,   │
│                                │                                │                                                       │ Translation          │
├────────────────────────────────┼────────────────────────────────┼───────────────────────────────────────────────────────┼──────────────────────┤
│                                │                                │                                                       │ Headword, Senses     │
│ LexicalEntryViewFactory        │ capability.LexicalEntry        │ LexicalEntryView.fxml (headword, senses, examples)    │ (definitions joined  │
│                                │                                │                                                       │ with ;)              │
└────────────────────────────────┴────────────────────────────────┴───────────────────────────────────────────────────────┴──────────────────────┘

The registry always adds a Line column (startLine) first.

What this means for a plugin without its own view

- If its objects implement InterlinearSentence or LexicalEntry, they get the matching structured view without any extra code.
- Otherwise, the generic view can only show what the CorpusObject interface provides: kind, label, position (TextCoordinate) and the tree of children. Any other fields on a grammar-specific class are not visible. A plugin can make the fallback more useful by giving each object an informative getLabel() and by exposing its structure through getChildren().

Capability interfaces without a built-in view

These are DependencySentence, Sense and Example. They fall th For DependencySentence, that means the tokens, heads andmetadata are not displayed; the table only shows the label, which defaults to getSentenceId(). That's why the conll plugin needs its own ConllSentenceViewFactory. A built-in DependencySentenceViewFa to any plugin that produces dependency sentences.
