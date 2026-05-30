Matchbox is an application for applying ANTLR grammar to set of files.



Inside the current maven repository you will write a java application named "Notebook".

The java code will be in subpackages of fr.cnrs.lacito.notebook.

un test possible
DET N ADJ
'a possible test'

First, the application will have an antlr grammar that will parse text files and recognize group of three consecutive lines with a last line starting and ending by a quote. This three lines groupes are separated from the rest of the text by two empty lines. The pom files should compile the ant grammar into a lexer and parser. The dependancy to antlr should be declared in the pom.

Second, the application will also have a GUI in javafx. The design of the application will be specified in an fxml file. It will contain a menu. It will follow a MVC (model view controller) pattern. The code will be in the fr.cnrs.lacito.notebook.gui package. 
- 1. The gui will contain a menu with a "File" entry that offers to open a file or directory with a file chooser dialog. It File entry also contains a "open recent" entry that list the files previously open. The file previously open are stored in a configuration file named "fieldnode.history" in the ".config/notebook" directory of the user home. The File entry also contains a "settings" entry and a "quit" entry. The setting entries open a dialog for listing the character set that will be used further in the application. The configuration is stored in a file name "configuration.txt" in the ".config/notebook" directory in the user home.
- 2. The application, below the menu, will contain a main area first divided in a top pane and a bottom pane. I first describe the top half that you will create. The top half will be further vertically divided in a left pane and a right pane. The left pane contains the list of the files ending with the "md" or "txt" extension found in the directory selected by the user, or the unique file selected by the user if he has selected a file. The right pane contain the text of the file selected by the user in the left pane. Thanks to the antlr grammar, group of three lines as defined above are highlighted. The text area is editable. Each time the user modify the text, it is parsed again using the antlr grammar in order to highlight the text again. I then describe the botom half that you will create. The botom half contains a table with three column, one column for each of the three lines of the three line groupes. Each tree lines group founds thanks to the grammar is displayed in the table. When the user edit the text in the top right pane, it is parsed again and highlighted as already describe, but if a new group of three lines is found it is also displayed in the table. When the user select a row in the table, it select in the top left pane the file in which this three line group is found, and it move the cursor in the top right pane to the location of this three line groupes.









In the File menu, add a "Create project..." entry. This menu entry will first open a
File Chooser dialog asking for a filename. The filename will be given a "mbx"
extension. When the filename is selected, the file chooser is closed and a dialog box titled "Project properties"
is displayed for definition of the properties of the project. This dialog box first has a text
field for the project name with the title "Project name". It then has a text field for the project description with the title "Project description".
It then has an area called "Fileset" that contains a list of files or directories. The file
currently opened in the application by the user are automatically listed in this list. At the bottom of the 
list there are two buttons: "Add" and "Remove" to add or remove files or directories
from the project. After the "Fileset" section, there is a section titled "Grammars by file type". This section is divided horizontally into two areas: on the left, a list of file type names. On the
right, the details of the selected file type. Below the left area list, there are two buttons:
"Add" and "Remove" to add or remove file type. On the right
area, where the selected file type is displayed, there is first a text field for
the file type name, then a text field for the regular expression itself, called "matching files". Below, there is vertically displayed list of the available
grammars to be apply to the files matching the file type, each grammar having a name and a
description and a tickbox for wether or not to apply the grammar to the matching
files. The grammars are the following (in the following two lines, the first
column give a widget name and the second column gives its description) :

"conll" "CONLL file format"
"lex" "Lexical file format"
"igt" "interlinear glossed text"

The grammar list can be reordered by drag and drop.

At the bottom of this "Project property" dialog box there is a button "Save" to save the project properties and close the dialog box, or a button "Cancel" to discard the changes and close the dialog box. If the user clicks "Save", the project properties are saved in a XML file, with the following structures:
- root element : "/project"
  - child element of project: "fileset"
    - child element : "file" (repeated for each file in the fileset), with the file path, relative to the project root directory, in a "path" attribute
    - child element : "directory" (repeated for each directory in the fileset) with the file path, relative to the project root directory, in a "path" attribute
  - child element of project: "pattern" (repeated for each pattern)
    - a child element "name" for the pattern name
    - a child element "regex" for the pattern regex
    - child element : "grammar" (repeated for each grammar), with an attribute "name" for the grammar name, an attribute "rank" for the grammar rank, and an attribute "selected" for weither the grammar is selected or not.
  
This is the end of the description of the "Project properties" dialog box.

Changes Made:
1. Menu Integration - Modified MainView.fxml and MainController.java
Added "Create project..." menu item to the File menu
Added handler method that launches the project creation workflow
2. Data Model - New Project.java class
Main Project class with name, description, files, and directories
Inner GrammarPattern class for file type patterns with name and regex
Inner Grammar class for individual grammars with name, description, rank, and selection status
3. XML Serialization - New ProjectXMLSerializer.java class
Saves projects to .mbx XML files with the structure you specified
Root element: <project>
Child elements: <fileset> with <file> and <directory> entries
Child elements: <pattern> with <name>, <regex>, and <grammar> elements
4. Create Project Dialog - New CreateProjectDialog.java class
Shows a FileChooser dialog for selecting/creating an .mbx file
Launches the Project Properties dialog for detailed configuration
Saves the project to XML when complete
5. Project Properties Dialog - New ProjectPropertiesDialog.java class
A comprehensive dialog with all requested features:

Project name text field
Project description text area
Fileset section with:
Auto-populated list of currently opened files
Add/Remove buttons for managing files and directories
Grammars by file type section with:
Left panel: List of file types with Add/Remove buttons
Right panel: File type details including:
File type name text field
Regex pattern field (matching files)
Grammar list with checkboxes for selection
Drag-and-drop reordering of grammars
Three predefined grammars: conll, lex, igt
Save/Cancel buttons
6. Helper Method - Added to FileViewController.java
getOpenedFiles() method that retrieves the list of currently opened files for auto-population
How It Works:
User clicks "File" → "Create project..."
FileChooser dialog appears for selecting/creating a .mbx file
Project Properties dialog opens with:
Currently opened files pre-populated in the fileset
Ability to add/remove files and directories
Ability to define file type patterns with grammars
User clicks "Save" to save the project to an XML file
The XML file contains all project configuration for later loading
The implementation is complete, builds successfully, and follows your exact specifications for the UI layout and XML structure.


##




You will now refactor this maven project into a maven multi-module project. The root pom.xml file will have the module id 'matchbox'. A first sub-module, named 'matchbox-app', will contains all the existing code but the ANTLR grammar. You will then create a subdirectory "grammars". For each Lexer/Parser file pairs found in src/main/antlr4/fr/lacito/matchbox, you will create a submodule, in this "grammars" subdirectory, whose name will be made of "matchbox-grammar-" followed by the grammar name (without Parser or Lexer suffix). In these submodules, the lexer and parser files will be in a directory named after the grammar and put in a src/main/antlr4/fr/lacito/matchbox directory. You will take care of the the type of the artifact in each pom.xml file (articfact = "pom" or "jar"), the dependancy relation, and to keep only relevant dependencies in each pom.xml file.

You will then move the NotebookListener class, which is the listener for the Noteboook grammar, in the corresponding submodule (grammars/matchbox-grammar-notebook), in the src/java directory for java code obviously.
 


#

At this point, the various grammar are submodules in the "grammars" directory. The rest of the application does not know about them.  Here is how you will refactor this application.

You will now create a matchbox-api submodule. you will also create a matchbox-core submodule.


- The contract followed by the grammars is defined in the matchbox-api submodule. This contract contains an interface with the following accessors : getId (returning a String), getDescription (returning a String), getLexicalItemDefinition (returning a list of LexicalDefinition object)

 The grammars are loaded by the matchbox-core submodule, which is responsible for 

The matchbox-core is for the cor eLe noyau applicatif
le contrat d’extension
les plugins de chercheurs de motifs
le chargement dynamique


 and the NotebookListener class and the CollectingErrorListener class
