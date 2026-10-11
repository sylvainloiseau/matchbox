package fr.cnrs.lacito.matchbox.model.project;

import fr.cnrs.lacito.matchbox.PluginManager;
import fr.cnrs.lacito.matchbox.grammarapi.DisplayStyle;
import fr.cnrs.lacito.matchbox.grammarapi.GrammarPlugin;
import fr.cnrs.lacito.matchbox.model.DocumentOptions;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.*;

/**
 * Handles serialization and deserialization of Project objects to/from XML
 */
public class ProjectXMLSerializer {

    /**
     * Saves a project to an XML file
     * @param project the project to save
     * @param file the file to save to
     * @throws Exception if there's an error during serialization
     */
    public static void save(Project project) throws Exception {
        File file = project.fileProperty().get();
        if (file == null) {
            throw new IllegalArgumentException("Project file is not set");
        }

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.newDocument();

        // Create root element
        Element rootElement = doc.createElement("project");
        doc.appendChild(rootElement);
        if (project.getName() != null && !project.getName().isBlank()) {
            rootElement.setAttribute("name", project.getName());
        }
        if (project.getDescription() != null && !project.getDescription().isBlank()) {
            Element descriptionElement = doc.createElement("description");
            descriptionElement.setTextContent(project.getDescription());
            rootElement.appendChild(descriptionElement);
        }

        // Add fileset element
        Element filesetElement = doc.createElement("fileset");
        rootElement.appendChild(filesetElement);

        // Files and directories, in the order of the definition (the top nodes of the file tree)
        for (java.nio.file.Path path : project.getDefinitionPaths()) {
            if (project.isDefinitionDirectory(path)) {
                Element dirElement = doc.createElement("directory");
                dirElement.setAttribute("path", path.toString());
                project.directorySettings(path).ifPresent(settings -> {
                    dirElement.setAttribute("recursive", String.valueOf(settings.recursive()));
                    for (String include : settings.includes()) {
                        Element e = doc.createElement("include");
                        e.setTextContent(include);
                        dirElement.appendChild(e);
                    }
                    for (String exclude : settings.excludes()) {
                        Element e = doc.createElement("exclude");
                        e.setTextContent(exclude);
                        dirElement.appendChild(e);
                    }
                });
                filesetElement.appendChild(dirElement);
            } else if (java.nio.file.Files.isRegularFile(path)) {
                Element fileElement = doc.createElement("file");
                fileElement.setAttribute("path", path.toString());
                filesetElement.appendChild(fileElement);
            }
        }

        // Add patterns
        for (FileType pattern : project.getFileTypes()) {
            Element patternElement = doc.createElement("pattern");
            rootElement.appendChild(patternElement);

            // Add name
            Element nameElement = doc.createElement("name");
            nameElement.setTextContent(pattern.getName());
            patternElement.appendChild(nameElement);

            // Add regex
            Element regexElement = doc.createElement("regex");
            regexElement.setTextContent(pattern.getRegex());
            patternElement.appendChild(regexElement);

            // Add grammars
            for (int i = 0; i < pattern.getGrammars().size(); i++) {
                GrammarPlugin grammar = pattern.getGrammars().get(i);
                Element grammarElement = doc.createElement("grammar");
                grammarElement.setAttribute("id", grammar.getId());
                grammarElement.setAttribute("rank", String.valueOf(i));
                patternElement.appendChild(grammarElement);
            }
        }

        // Restrictions of the AI assistant (absent: no restriction)
        if (!project.isCloudAssistAllowed()) {
            Element assistantElement = doc.createElement("assistant");
            assistantElement.setAttribute("allowCloud", "false");
            rootElement.appendChild(assistantElement);
        }

        // Display styles by grammar and kind (absent attribute: not set)
        if (!project.getDisplayStyles().isEmpty()) {
            Element stylesElement = doc.createElement("displayStyles");
            rootElement.appendChild(stylesElement);
            project.getDisplayStyles().asMap().forEach((key, style) ->
                stylesElement.appendChild(styleElement(doc, key, style)));
        }

        // Write to file
        TransformerFactory transformerFactory =
            TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty("indent", "yes");
        transformer.setOutputProperty(
            "{http://xml.apache.org/xslt}indent-amount",
            "2"
        );

        DOMSource source = new DOMSource(doc);
        StreamResult result = new StreamResult(file);
        transformer.transform(source, result);
        project.definitionChangedProperty().set(false);
    }

    /** The settings of a {@code <directory>}, null if it has none (older project files). */
    private static DirectorySettings directorySettings(Element e, Project project) {
        List<String> includes = childTexts(e, "include");
        List<String> excludes = childTexts(e, "exclude");
        if (!e.hasAttribute("recursive") && includes.isEmpty() && excludes.isEmpty()) return null;
        DirectorySettings settings = new DirectorySettings(includes, excludes, "true".equals(e.getAttribute("recursive")));
        List<String> problems = settings.problems();
        if (!problems.isEmpty()) {
            project.recordLoadProblem("Directory " + e.getAttribute("path") + ": " + String.join("; ", problems)
                + ". Its invalid patterns are ignored.");
            settings = new DirectorySettings(
                includes.stream().filter(g -> Glob.problem(g) == null).toList(),
                excludes.stream().filter(g -> Glob.problem(g) == null).toList(),
                settings.recursive());
        }
        return settings;
    }

    private static List<String> childTexts(Element parent, String tag) {
        List<String> texts = new java.util.ArrayList<>();
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e && e.getTagName().equals(tag)) texts.add(e.getTextContent());
        }
        return texts;
    }

    private static Element styleElement(Document doc, DisplayStyles.Key key, DisplayStyle style) {
        Element e = doc.createElement("display");
        e.setAttribute("grammar", key.grammarId());
        if (key.kind() != null) e.setAttribute("kind", key.kind());
        if (style.fontFamily() != null) e.setAttribute("fontFamily", style.fontFamily());
        if (style.bold() != null) e.setAttribute("bold", style.bold().toString());
        if (style.italic() != null) e.setAttribute("italic", style.italic().toString());
        if (style.underline() != null) e.setAttribute("underline", style.underline().toString());
        if (style.strikethrough() != null) e.setAttribute("strikethrough", style.strikethrough().toString());
        if (style.textColor() != null) e.setAttribute("textColor", style.textColor());
        if (style.backgroundColor() != null) e.setAttribute("background", style.backgroundColor());
        return e;
    }

    /** The display styles of the project file; invalid values are dropped and reported. */
    private static void loadDisplayStyles(Element rootElement, Project project) {
        NodeList stylesList = rootElement.getElementsByTagName("displayStyles");
        if (stylesList.getLength() == 0) return;
        NodeList displayList = ((Element) stylesList.item(0)).getElementsByTagName("display");
        Map<DisplayStyles.Key, DisplayStyle> styles = new LinkedHashMap<>();
        for (int i = 0; i < displayList.getLength(); i++) {
            Element e = (Element) displayList.item(i);
            String grammar = e.getAttribute("grammar");
            if (grammar.isBlank()) {
                project.recordLoadProblem("A display style has no grammar: ignored");
                continue;
            }
            DisplayStyles.Key key = new DisplayStyles.Key(grammar, e.hasAttribute("kind") ? e.getAttribute("kind") : null);
            String where = "display style of " + (key.kind() == null ? grammar : key.kind());
            DisplayStyle style = new DisplayStyle(
                attribute(e, "fontFamily"),
                bool(e, "bold", where, project),
                bool(e, "italic", where, project),
                bool(e, "underline", where, project),
                bool(e, "strikethrough", where, project),
                color(e, "textColor", where, project),
                color(e, "background", where, project));
            styles.put(key, style);
        }
        project.setDisplayStyles(DisplayStyles.of(styles));
    }

    private static String attribute(Element e, String name) {
        return e.hasAttribute(name) ? e.getAttribute(name) : null;
    }

    private static Boolean bool(Element e, String name, String where, Project project) {
        String value = attribute(e, name);
        if (value == null) return null;
        if (value.equals("true") || value.equals("false")) return Boolean.valueOf(value);
        project.recordLoadProblem("Invalid value '" + value + "' for " + name + " in the " + where + ": ignored");
        return null;
    }

    private static String color(Element e, String name, String where, Project project) {
        String value = attribute(e, name);
        if (value == null || DisplayStyle.isColor(value)) return value == null ? null : value.strip();
        project.recordLoadProblem("Invalid colour '" + value + "' for " + name + " in the " + where + ": ignored");
        return null;
    }

    /**
     * Loads a project from an XML file
     * @param file the XML file to load
     * @return the loaded project
     * @throws Exception if there's an error during deserialization
     */
    public static Project load(File file, List<GrammarPlugin> plugins)
        throws Exception {
        return load(file, plugins, DocumentOptions.DEFAULT);
    }

    public static Project load(File file, List<GrammarPlugin> plugins, DocumentOptions documentOptions)
        throws Exception {
        Project project = new Project(plugins, documentOptions);
        // "Save project" writes back to the file the project was loaded from
        project.setFile(file);

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(file);

        Element rootElement = doc.getDocumentElement();
        project.setName(rootElement.getAttribute("name"));
        // a direct child (file types have their own elements)
        for (Node n = rootElement.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e && e.getTagName().equals("description")) {
                project.setDescription(e.getTextContent());
            }
        }

        // File types first: each file is parsed with its grammars when it is added
        NodeList patternList = rootElement.getElementsByTagName("pattern");
        for (int i = 0; i < patternList.getLength(); i++) {
            Element patternElement = (Element) patternList.item(i);

            // Get name
            String name = "";
            NodeList nameList = patternElement.getElementsByTagName("name");
            if (nameList.getLength() > 0) {
                name = nameList.item(0).getTextContent();
            }

            // Get regex
            String regex = "";
            NodeList regexList = patternElement.getElementsByTagName("regex");
            if (regexList.getLength() > 0) {
                regex = regexList.item(0).getTextContent();
            }

            FileType pattern = new FileType();
            pattern.setName(name);
            String problem = FileType.checkRegex(regex);
            if (problem == null) {
                pattern.setRegex(regex);
            } else {
                // kept as is, so that it can be corrected in the project properties
                pattern.setInvalidRegex(regex);
                project.recordLoadProblem("The file type '" + name + "' matches no file: " + problem);
            }

            // Load grammars for this pattern
            NodeList grammarList = patternElement.getElementsByTagName(
                "grammar"
            );
            for (int j = 0; j < grammarList.getLength(); j++) {
                Element grammarElement = (Element) grammarList.item(j);
                String grammarId = grammarElement.getAttribute("id");

                GrammarPlugin grammar = PluginManager.getPluginById(grammarId);
                if (grammar == null) {
                    throw new IllegalArgumentException(
                        "Grammar not found: " + grammarId
                    );
                }
                pattern.addGrammar(grammar);
            }

            project.addFileType(pattern);
        }

        // Load fileset
        NodeList filesetList = rootElement.getElementsByTagName("fileset");
        if (filesetList.getLength() > 0) {
            Element filesetElement = (Element) filesetList.item(0);

            // files and directories, in order
            for (Node n = filesetElement.getFirstChild(); n != null; n = n.getNextSibling()) {
                if (!(n instanceof Element e)) continue;
                String path = e.getAttribute("path");
                if (e.getTagName().equals("file")) {
                    project.addFile(path);
                } else if (e.getTagName().equals("directory")) {
                    DirectorySettings settings = directorySettings(e, project);
                    if (settings == null) {
                        // written before directory settings: as it was loaded then
                        project.addDirectory(path);
                    } else {
                        project.addDirectory(path, settings);
                    }
                }
            }
        }

        NodeList assistantList = rootElement.getElementsByTagName("assistant");
        if (assistantList.getLength() > 0) {
            Element assistantElement = (Element) assistantList.item(0);
            project.setCloudAssistAllowed(!"false".equals(assistantElement.getAttribute("allowCloud")));
        }

        loadDisplayStyles(rootElement, project);

        // the project is what its file says
        project.definitionChangedProperty().set(false);
        return project;
    }
}
