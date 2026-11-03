- magalie: wero
- parcoursup : dépôt
- parcoursup : fiche

## Chronological project / non chronological

If a project is chronological, only one file at a time is editable, the one that is the last according to the key producing a total ordering of files. Either name, or header value: date, or number. File rejected while no date or number. Corpus blocked if a file has no date or number anymore.

If the project is non-chronological, then no patch logic: lift-patchbox cannot be used.

## Project settings

Also in the Project property dialog, I need you to add a panel for allowing the user to define the key to use for sorting the files. The panel is titled "Ordering file of the corpus". Below the label is a expanatory text, in the same style as the explanatory text below the "File types: patterns matching file names and the grammars applied to them" label; the explanatory text says: "Order the key for ordering the files of the corpus. Files must be totally ordered". The list contains the following entries:

  - file name (directory name, then filename)
  - file creation date
  - date in the file header (when parsed)
  - id in the file header (when parsed)

## Keyboard shortcut

Move to File menu, under "Settings..."

Note to CLAUDE.md to add to keyboard shortcut

## "Save project file set" menu item

I would like you to check something. The "Project properties..." dialog has two buttons: save or cancel. This means that the properties that can be set in this dialog are never "unsaved": the in-memory state is always sinchronized with the project property file. Is this correct? If this is correct, it means that the "Save project" menu item under the File menu can only save the state the file set of the project, which are not edited in the "Project properties..." dialog: file added or removed, directory added or removed, directory settings modified. If this is correct, I would like, for clarity, the "Save project" to be renamed "Save project's file set", and I would like this menu item to be disable as long as no change has been made to the file set, and enabled when any change as been made (file added or removed, directory added or removed, directory settings modified).

Regarding the two things you just mentioned while reading the code:
- The "changed" state can't go back to "unchanged". Project.definitionChanged is only cleared by loading or saving. Adding a file and then removing it still leaves the project marked as changed, so the title keeps "•" and Close asks a question for nothing.
- Project properties' Save button doesn't write the file. It only applies the changes in memory. They reach the .mbx through File › Save project or the close dialog. File › Save project already writes the file set along with everything else.

Please make a plan, making sure to achieve a clean state management.

## Matching vs highlight vs sub-object

PB with pluging markdownheading
- pb de section : match toute la zone et non pas seulement le header
  pb pour lexem : the whole lexem, the definition, the sense? the view show sensibility to both

## An edit command language for annotated sentence

You write forms. Each form is mapped to the ID it designates at that point in the stream, and the updated text is a computed view. You never type IDs for lexemes, and they never appear in your notebook files.

In practice:

create /mami
  create sense "pig"

:::
a mami jefi
:::

update /mami /memi
What you write and keep: a mami jefi, unchanged forever.
What the compiler computes: on replay, at the position of the text, mami is looked up in the lexicon as it was then and resolves to E17. That binding is internal, rebuilt on each compile and never written into your file.
What you can display:
as written: a mami jefi
current state: E17 now has the form memi, so a memi jefi

The IDs exist because two different entries can have the same form at different times. Forms are what you write and see; IDs are what the compiler uses to keep references unambiguous across renames.

There are two small exceptions, and neither puts lexeme IDs in your text:

Ambiguity at writing time. If two entries share the form mami at that point, you add a disambiguator in your own terms, e.g. mami:pig (by sense) or mami#2. It’s still form-based and human-readable, and only needed when the compiler can’t decide.
Sentence anchors. To revise an old sentence by appending a command (revise @2026-10-03.4 …), the sentence needs a stable label. That labels the sentence, not the lexemes in it.

Optionally, the tool can cache the computed bindings in a separate generated file, like a lockfile, so you can diff them between compiles and see when an edit to an old file changed how later texts resolve. That file is derived and never edited by hand.





When displaying a text, there will be two modes:

- Historical mode: Show the citation forms as they were when the text was written (using the lexeme states at that timestamp).
- Current mode: Show the latest citation forms for all referenced lexemes.

##

You will write a plan about creating a plugin for parsing a lexicon mutation command language called LiftPatchRef and a Mode called Lexicon history mode.

The matchbox application is an alternative to traditional database of
interlinear texts and associated lexicon used in descriptive linguistics for
organizing fieldwork data, such as SIL Flex or SIL Toolbox.

In these traditional databases, there is no record of the order in which
information has been entered. Everthing is always in sync: if you change the
form of a lexem, all places in texts where the lexems is used (all the
occurrences of the lexem) are updated.

The drawback of these databases is for instance that linguist have to copy their
notebook; and there is no record of the history of corrections / new text / new
form.

In the application I'm planning, however, by contrast, I would like to offer
another workflow, where the information is entered directly, continuously,
following the order of the linguist decisions, rather than being copied latter.
The linguist will use short
conventions that can be parsed by a ANTLR grammars. The files are ordered
chronologically.

Some of these conventions are mutation commands, they define a flow of mutation commands, such as: add such example to this lexem, change
lexem form, etc. 

- create a new form
- rename such form
- delete such form
- add sense, example, note, etc. to form
- ...

There will be a mutation commands flow for two type of mutating objects:

- dictionary commands
- IGT annotated sentences 

This plan is about the *dictionary mutating commands flow*. A future plan will be about the annotated sentence mutation commands.

The matchbox application is therefore also an application for recording
linguistic fieldnote as a stream of small commands mutating lexical information
(the dictionary) and annotated sentences (the corpus).

For those objects that are manipulated through mutation commands, Matchbox is a
historical system. The matchbox application will contain a time-ordered event
log (like Git commits). Each mutation is a new state delta. The events will be
given in the files of the project through parsable commands and will mutate
two kinds of objects:

- event mutating an lexical information in a dictionary
- event mutating a annotated sentence. 

File content should not be mutated for those object types. Changing an
information is made by adding a new mutation command in a new file, that will
update the content.

The text + lexicon stream is the source of truth. Each reference is resolved
once, at its point in time, to a stable hidden identity. Everything synchronised
(current lexicon, updated texts, exports) is a derived view. Mutations that
can’t map old references unambiguously, like split and delete, produce review
tasks instead of silent changes.

I would you like, then, to generate a plan for a new plugin (in the grammars
directory). You will look at the corresponding skill for writting plugins.

The plugin will be called "matchbox-grammar-liftpatchbox".

The plugins will recognize and parse LiftPatchShort syntax, i.e. a mutation
command language for editing a dictionary.

The liftPatchShort syntax is defined in the lift-patchbox module, in the
directory ../lift-patchbox, it is also installed under the coordinate
io.github.sylvainloiseau:lift-patchbox:0.1-SNAPSHOT in the local maven
repository and on the github repository. The plugin should therefore declare the
following dependency and repository in its pom:

```
  <repositories>
    <repository>
      <id>github</id>
      <name>GitHub Packages</name>
      <url>https://maven.pkg.github.com/sylvainloiseau/*</url>
    </repository>
  </repositories>

  <dependencies>
    <dependency>
      <groupId>io.github.sylvainloiseau</groupId>
      <artifactId>lift-patchbox</artifactId>
      <version>0.1-SNAPSHOT</version>
    </dependency>
  </dependencies>
```

The lift-patchbox library use the lift-api library for representing a the components of a lift dictionary. The lift-api module is   in the
directory ../lift-api, it is also installed under the coordinate
io.github.sylvainloiseau:lift-api:0.1-SNAPSHOT in the local maven
repository and on the github repository. The plugin should therefore declare the
following dependency and repository in its pom:

```
  <repositories>
    <repository>
      <id>github</id>
      <name>GitHub Packages</name>
      <url>https://maven.pkg.github.com/sylvainloiseau/*</url>
    </repository>
  </repositories>

  <dependencies>
    <dependency>
      <groupId>io.github.sylvainloiseau</groupId>
      <artifactId>lift-api</artifactId>
      <version>0.1-SNAPSHOT</version>
    </dependency>
  </dependencies>
```


Please report if you cannot access the lift-patchbox or lift-api library.

The lift-patchbox module already contains a parser for LiftPatchShort syntax
(in `src/main/antlr4/fr/cnrs/lacito/liftpatchbox/parser/LiftPatchShort.g4`). You will
check that you will be able to wire the LiftPatchShort parser to the
application. If the lift-patchbox library do not provide the entry point you
need, please report this and include the needed lift-patchbox modifications into
your plan.

Note that two sub-types of commands exist: normal syntax (one-liner) and "block"
syntax (on several lines). The block syntax is a set of one-liner grouped
hierarchically (by identation) and where an inner command path is inherited from
its outer command.

You will create a new CorpusObject subclass "LexiconMutationCommand" in the
capacity package and a SimpleLexiconMutationCommand in the simple package for
holding a LiftPatchShort command. A LexiconMutationCommont should contain a
reference to lift-patchbox object with all the information necessary for
patching the dictionary, together with the file name and line number it occurred
on, in order for the command to be totally ordered.

As for the view of the plugin: in the `objectKindTabPane` tab, the summary table
will give for each command (or each command bloc) the line it start on, the form
of the lexical entry it operates on, and simply the text of the command. The
`objectDetailPane` will display a read-only form of this entry at this point of
modification. In order to get this state of this entry: the programm should
apply the mutation commands up to the current command thanks to lift-patchbox,
this will result in a LiftObject belonging to the lift-api library. Report if
the lift-patchbox does not offer to retreive the lift object at any point of the
mutation event chain. The lift-object, in turn, can be displayed thanks to the
form available.

In the Text mode, the plugin does not offer to actually apply the mutation
commands. It simply recognize, with the ANTLR parser, the commands, and display
error in the error tab as other plugins do. It does not check the commands for
semantic validity, as is also offered by the lift-patchbox library. If the
lift-patchbox does not offer to easily parse the file and get the parsed three
without entering yet the semantic validation, please offer a modification of
lift-patchbox that would allow this.

The Matchbox gui will offer a Lexicon Mode that display the lexicon as it result from the application of the set of mutation command.

The lexicon is presented as a fiterable list of entries in a list on the left.

A toolbox 

## Not followed advice from Mistral

Mapping for Updated Views

To render a text in "current mode," you:
Parse the text to extract lexeme references (e.g., [/lexeme/123]).
For each reference, look up the latest state of that lexeme.
Replace the reference with the current citation form (e.g., memi).

Additional Considerations

1. Stable References in Texts

If you must allow texts to reference lexemes by form (e.g., for manual editing or legacy compatibility), you can:
Resolve the form to a lexeme ID at the time the text is written (e.g., mami → /lexeme/123).
Store this resolution in the text metadata.
If the form is ambiguous (e.g., two lexemes have the form mami at that time), flag it for the user to disambiguate.
2. Handling Lexeme Splits/Merges

If a lexeme is split into two (e.g., /lexeme/123 becomes /lexeme/123 and /lexeme/456), you need to:
Decide which historical references map to which new lexeme (this may require user input).
Store this mapping as part of the split operation.
Similarly, merges require resolving conflicts in historical references.
3. Querying and Analysis

Your system will need to support queries like:
"Show me all texts where lexeme /lexeme/123 appeared as mami."
"Show me the state of the lexicon as of [date]."
"Show me all changes to lexeme /lexeme/123."
This is feasible with the immutable history approach but impossible with the retroactive-update approach.
4. Performance Optimizations

For large corpora, pre-compute and cache:
The current citation form for each lexeme.
The citation form of each lexeme at the time each text was written (to speed up historical rendering).
Use a database or index (e.g., SQLite, Elasticsearch) to efficiently query lexeme states by timestamp.
5. User Experience

Provide clear affordances for:
Viewing a text in "original" vs. "current" mode.
Seeing the history of a lexeme (e.g., a diff view of all its changes).
Searching/filtering texts by lexeme at the time of writing or current state.

============


You will give me advice as to the logic of an application.


However, I'm not sure how to deal with the annotated sentences part in such a workflow. Annotated sentences are create at a moment,
as a string of citation form of lexem forms refering to the existing entries in the dictionary. But if the entry is latter modified, and its citation form changed, how to keep the alignement text / lexicon?

- One possibility could be to change back all the occurrences in previous files: it is a database logic. The issue is that the text is not any more in the state it was written the first time, and inconsistent with the surrounding information that may be describing a lexical entry with another form
- Another possibility is to consider the whole text + lexicon as a flow of commands: the text is to be considered in the context in which it was written, and the lexem that are mentioned in the text can be mapped to their latter form and updated to their final form, if needed.

Here is an example to illustrates this last option:

===

For instance, lets say I create an entry "mami" with a sense "pig" with my mutation command syntax:

create /mami
  create sense "pig"

Then in can create a gloss a refer to the mami form:

:::
a mami jefi
:::

If letter I update the form of mami to memi:

update /mami /memi

The previous text should not be changed, but the system can trace that the form mami has been turned into memi and, if asked, display
an updated form:

:::
a memi jefi
:::

===

which solution do you think is the better, or do you have alternate suggestion?


##

Unsynchronized files
- the view show the correct new current form
- a button "edit it", copy it in the current file
- the whole corpus is a set of edition for both corpus and text

Or:

- The dictionary only is concerned by this edition
- files saved at quit but not reparsed.


## Media mode

Create a new mode for 
List of media files found in all (resolved) mediaref object.

## I18N

- I18N: plan pour externaliser toutes les strings localisées de l'interface (info-bulle, etc.)

## Plugin settings system

Un bouton setting avec chaque plugin activé dans la page projet. En lien avec le fait qu'un plugin puisse avoir un menu

What is the difference between plugin kind and corpusobject sub-types?

- shortcut
- button in text editor

## Avoid hard-coding plugin list

- Pas de hard-coding des plugins dans le code
- no hard-code of various object

## Metadata block

- metadata :
- type: (sentence, ...)
-> grouper par type
- title:
- id: # error if already exist
- feature: (featurename:value)
- supersedes
  
## Refactoring the notebook plugin

Matching rule

IGT editing view

A view for editing IGT.
- a set of zero or more lines of narrow phonetic transcription, without word or morphemes boundaries
- a set of zero or more lines with the same number of words and, in each word, the same number of morphemes and clitics (separated by "-" and "=" in word)
- a set of zero or more translation lines. They may have a type (litteral, free) and a language.

- retirer de notebook le lex et l'appeler IGT. Matcher
:::igt
:::

:::img{feature:value}
:::
permettre 1 à trois ligne. Bouton pour aligner avec des espaces. Aligner tout le fichier.
édition en editeur

## An import / export from Elan: annotation by dependant tiers in hierarchy, @media when needed.

## text view

Section, subsection... use title on file

The file view is mostly devoted to checking that the file are correctly parsed,
to update the text if necessary in order for all corpus object to be correctly
recognized.

## CoNLL editable with drag and drop

Refactor the view of the CoNLL plugin (or its shared view)

Rather that a table, where each token is a row and each feature of the tokens (form, lemma, POS, etc.) is a column, I would like a pivoted table where each tokens is a column and each feature is a row, so that the text is more readable.

Moreover, I would like the dependant->head relation between tokens (which is given in the "dep" row: on each dependant,it gives the index of its head) to be represented by rounded arrows, above the table, starting from the middle of the column representing a dependant, and pointing to the middle of the column representing its head, whose index number is given in the dep column of the dependant. In order for the arrows not to cross each other, the more far-streatching arrow will naturally go higher due to the rounding shape.

cell of the token row to be dragable and droped on another cell of the same row. This drag and drop express, the dependant->head relation between the two token, and 

## lift-patchbox plugin

## Tooltip


## Corpus view

- Search replace
- undo redo
- italics for modified text
- display error of antlr4 parsing
lexer: CONTENT, select markeur by user, tag
filter sur file list
igt table -> jump to good text
  ... or display only igt of text?
ask for save before close

clic in text -> move to good object

tooltip
https://docs.oracle.com/javase/8/javafx/api/javafx/scene/control/Tooltip.html

Test if changing text update correctly igt and lexical

## Done

### Varia -> PROJECT_WINDOW_PLAN.md

Could you please make a plan for the following small independant modifications. For each one, please provide a implementation plan, ask questions or offer enhancements you can think off:

1/ rename the "View" menu as "Mode", and its variable name viewMenu as modeMenu, in the fxml file and in its controller.

2/ More clean state at application startup: actually, (when the "Reopen last open project" is not checked in "Settings" dialog -> "Application tab"), an empty project is open at startup. I would like that, when this option is not checked, no empty project is created. Similarly, when a project is closed, no empty project is created. A variable should reflect the state that no project is open, and all button and menu item should be bind to that variable in order to be turned in a disabled state. When no project is open, only the "New project", "Open project", "Open Recent projects", "Settings" and "quit" items must stay enabled. When a project is open, the menu item "Open project", "New project" and "Open Recent projects" should be disabled.

When no project is open, I would like the main window to contain only a empty space with, in the middle, buttons for New project, Open project, and the list of recent projects as Link which, when clicked, open the project. As soon as a project is opened or a new project created, the main window turn in the File mode. This window for no-opened-project could be a mode, without a button in the toolbar, if you think that it is a convenient and sensible way to manage this.
 
3/ Managing color of plugin. The plugin can be discovered at startup and its not wise to hard-code the list of plugin in a separated place. Plus, the color for plugin is project-specific. Therefore:
  - remove the "Color" tab from the "Settings" dialog. 
  - In the "Project properties" dialog, add a button "plugin display" next to each plugin name-description in the plugin list, right aligned. The button is disabled when the plugin is unchecked and enabled when the button is checked. The button open a window for specifying font properties, containing all standards property of font: font family, font decoration, font color, background color. After the user select a font specification (and the project definition is saved), the parameters are stored in the project property files. If a user uncheck a plugin, it is noted as unchecked in the project property file, but the font decoration are kept, so that if the user check the plugin again, making the buton enabled, he or she can edit the font property and found them in the state he or she has defined earlier.

4/ When a project is closed:
  - if the project definition has changed (files and directory list, plugin and their configuration...) but the content of the files has not changed (no file with its property couldBeWritten to "true"), then a modal dialog should ask: "The file definition has changed. Would You like to save the changes"?
  - If only the file contents have been modified and not saved, a modal dialog
  with the text: "the following files have changed. Select the files you want to save:"
  then a a list of the file pathname, preceded with a checkbox, with a box "select all" and "unselect all".
  - If both the project definition and some files have changed, the modal dialog show first
    - a line with the message "The file definition has change.  Would You like to save the changes"?
    - a check box with "yes"
    - "the following files have changed. Select the files you want to save:"
    - then a a list of the file pathname, preceded with a checkbox, with a box "select all" and "unselect all".

5/ Add new menu, "Current project", next to the File menu, and move in that menu the following entries that are now in the File menu: Project properties, Add file, Add files in directory, Add recent, Save current file, Save all files, Close file, Close all files. Remove "Close file" as "Remove file" (since it also remove the file from the project) and "Close all files" as "Remove all files from the project" for the same reason.
 
6/ rename the "Settings" menu item as "Settings…"

## Addendum to the previous -> PROJECT_WINDOW_PLAN_addendum.md

Thanks. I have one modification question: in the "Project properties" dialog, the "Display" button for each plugin is at the left of the plugin name. However, it has two drawbacks: 1/ They are not visible at first, and the user has to use the horizontal slider in order to make them visible. 2/ By displaying these buttons at this place, i.e. on a plugin names selected for a fileset, it implies that the plugin display parameters applies to this plugin of this fileset only, whereas the plan decided to make the display of a plugin kind identical accross the fileset using that plugin. I'm wondering whether it wouldn't be better to have a new section, below the "File types" area, called "plugin parameters". There will be a list of all plugin, and only the plugins selected at least in one fileset, will have the Display button enabled. Moreover, I would like you to remove the Fileset area: list of files and "Add files..." and "Add directory..." buttons: files and directory can already be added with menu items and are already displayed in the "File mode" in a larger and more easy to scroll list. Could you make a new plan, with the same name as the previous plan suffixed with "_addendum" ?

With the Fileset section gone, a directory can only leave the project through 'Remove all files'. A file removed from File Mode comes back at the next open if its directory is still in the project. What should replace it?

- Plugins that are no longer installed: shown as a greyed "(not installed)" row with a Forget button.

- Removing a directory: a new Current project › Remove directory from project… item lists the project's directories; the one you pick leaves the project, and its files stay on disk.

That leaves the suggestions:

- S1 "Aa" swatch: a small sample in each Plugin parameters row, showing how that plugin's objects currently look in the editor.
- S2 "Used in": each row names the file types that check the plugin (e.g. "All files, Markdown files"), or "not used".
- S3 test hook: a non-blocking way to open Project properties in the automated GUI tests, so the new section can be tested. Today that dialog blocks until closed, so the tests cannot drive it.
- S4 multi-select "Add file…": the menu item accepts several files at once, as the removed Fileset section's "Add file…" button did.

Includes all these suggestions.
     
Before implementing: this discussion make clear that something else is missing. I would you to use a tree view now in the file list of "File mode", in order to group files that
  belong to a directory that were added (by contrast, two files that have been individually are not grouped, even when they belong to the same directory). Under a directory node,
  subdirectories are displayed as subnodes. When a top directory node is selected (i.e. a directory that were manually added), a context menu can be displayed by right-click with
  entry "Remove this directory from the project" and "Edit directory settings...". The latter open a modal dialog for designing glob pattern of file to add and exclude together with
  a checkbox "Recursively include sub-directory". Those parameters should be recorded in the project property file. Please include this in the plan and ask any question if it is
  unclear.

│ Today the files a directory brings in are filtered by the global "File extensions" setting (Settings › Files). How should it combine with the new per-directory include/exclude patterns?
->
❯ 1. Default for new dirs (Recommended)
     The global setting only pre-fills the include patterns of a newly added directory (e.g. *.txt, *.md). After that the directory's own patterns decide, so the project gives the same files on every computer.

## Improve the text editor

Can you make a plan for improving the text editor in the Text mode.

The text editor lacks standard features of a text editor such as:

- vertical slider
- an option for automatic wraping line or not
- an option for displaying line number in the left margin
- standard shortcuts for reaching the end/start of line and file

Can you make a plan for such improvements as well as other one you can think off, typical of text editor?

## 

There has been a regression during the implementation of the PROJECT_WINDOW_PLAN and PROJECT_WINDOW_PLAN_addendum : now, when opening a project, lines are not highlighted

##

I want you to check that when a new project is created (with the New project menu item), the project settings contains one File set with a catch-all regexps (".*\..*") and that all plugins found are checked. Is this what happen?

I want defaut project setting at project creation to be: all plugins, only one file pattern, "All files"

## File set order in Project settings -> DROPPED. Files can match several file types. Is stated in the project property dialog and in CLAUDE.md

If I understand correclty files from the project file set are assigned to the first File type that they match. This means that if a file match a catch-all pattern (such as ".*\..*") it will be assigned to this fileset, even if it would have matched a more specific pattern (such as ".*\.md"). Is this correct? If this correct, I would like that, in the "Project properties" dialog, the file types can be ordered. Above the Add and Remove buttons, please add a Up and Down button that reorder the file types in the list. Please make a plan, ensuring to achieve a clear API and GUI design.


Ok, drop this plan. I just would like to insert an informative message, such as "File will be included in all 'File types' they match, and would be parsed with the union of the plugins associated with this file types." in the Project property dialog, just below the bold title "File types: patterns of file names and the grammars applied to them" (which should maybe be corrected into "File types: patterns matching file names and the grammars applied to them". Could you also check that this logic is stated in the CLAUDE.md file under "### Project Configuration"

## Project settings

In the "Project property" dialog, I need you to add two more properties, to be inserted between the "Plugin parameters" and the "AI assistant" panels.

The two properties will be in a panel of their own, title "Inherited context".

- The first property will have a label "Context dictionary", then a read-only text field that display the file path, then a file chooser for selecting the file. Below the label is a expanatory text, in the same style as the explanatory text below the "File types: patterns matching file names and the grammars applied to them" label; the explanatory text says: "Provide a dictionary if you want the lexical information provided in your project to be added to it. The dictionary should be in LIFT format.".
- The second property will have a label "Context corpus", then a read-only text field that diplay the file path, then a file chooser for selecting the file. Below the label is a expanatory text, in the same style as the explanatory text below the "File types: patterns matching file names and the grammars applied to them" label; the explanatory text says: "Provide a corpus if you want the data provided in your project to be added to it. The corpus should be a Matchbox corpus.".

This two properties will be stored in the Project class.
