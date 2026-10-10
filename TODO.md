## add a save project property

##

State:

- at each point everything referes to the current state.
eg.

I create an entry :

c /mami
  s "pig"

Then a gloss

:::{lookup="true"}
mami
:::


If I create later one a new mami:

c /mami
  s "homonymous"

Then how the previous mami is turned into mami1? 

Either it can not been changed,
- the view show the correct new current form
- a button "edit it", copy it in the current file

##

Unsynchronized files
- files saved at quit but not reparsed.

## Improve the text editor

- vertical slider
- automatic wrap line or not
- standard shortcuts for reaching the end/start of line and file


## Media mode

List of media files found in all (resolved) mediaref object.

## I18N

- I18N: plan pour externaliser toutes les strings localisées de l'interface (info-bulle, etc.)

## Matching vs highlight vs sub-object

- pb de section : match toute la zone et non pas seulement le header
  pb pour lexem : the whole lexem, the definition, the sense? the view show sensibility to both

## Plugin settings system

Un bouton setting avec chaque plugin activé dans la page projet. En lien avec le fait qu'un plugin puisse avoir un menu

What is the difference between plugin kind and corpusobject sub-types?

- shortcut
- button in text editor

## Avoid hard-coding plugin list

- Pas de hard-coding des plugins dans le code
- no hard-code of various object

## Project settings

dans Project settings:
- rajouter une dictionaire d'entrée (optional)
- Order corpus file: total order needed
  - date (when parsed)
  - id
  - last: file name

## Metadata block

- metadata : type (sentence, ...), title, id
  -> grouper par type

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
permettre 1 à trois ligne. Bouton pour aligner avec des espaces. Aligner tout le fichier.
édition en editeur

## An import / export from Elan: annotation by dependant tiers in hierarchy, @media when needed.

## text view

Section, subsection... use title on file

The file view is mostly devoted to checking that the file are correctly parsed,
to update the text if necessary in order for all corpus object to be correctly
recognized.

# CoNLL editable with drag and drop

Create a detailled component

# lift-patchbox plugin

I would you like to generate a plan for a new plugin (in the grammars
directory). You will look at the corresponding skill for writting plugins.

The plugin will be called "liftpatchbox".

The plugins will recognize and parse LiftPatchShort syntax, i.e. a mutation
command language for editing a dictionary.

The liftPatchShort syntax is defined in the lift-patchbox module, in the
directory ../lift-patchbox, it is also installed under the coordinate
io.github.sylvainloiseau:lift-patchbox:0.1-SNAPSHOT in the local maven
repository and on the github repository. The plugin should therefore declare the following dependency and repository in its pom:

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

Please report if you cannot access the lift-patchbox library.

The lift-patchbox module already contains a parser for LiftPatchShort syntax.
You will check that you will be able to wire the LiftPatchShort parser to the application.

You will create a new CorpusObject subclass "LexiconEditCommand" in the capacity
package and a LexiconCommandImpl in the simple package for holding a
LiftPatchShort command. A LexiconEditCommont should contains for instance a
command name (delete, update, upsert, create...), the path to the component targeted by the command, and other key component of a mutation command.

Note that two sub-types of commands exist: normal syntax (one-liner) and
"block" syntax (on several lines). the block syntax is a set of one-liner
grouped hierarchically and whose path is inherited by the command holding
it.

As for the view of the plugin:

- in the objectKindTabPane tab, the summary table will give for each command the
line it start on and simply the text of the command.

- in the objectDetailPane, a new view will be created for LexiconEdit command.
It will represent the command in LiftPathRef language, which is more verbose and
complete.

- In the Text Mode, the plugin does not offer to actually apply the mutation
commands. It simply parses the syntactically correct commands, but does not check
the commands for semantic validity. If the lift-patchbox does not offer to easily parse
the file and get the parsed three without entering yet the semantic validation, please offer a modification
of lift-patchbox that would allow this.
- THe plugin must however provide a function

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
