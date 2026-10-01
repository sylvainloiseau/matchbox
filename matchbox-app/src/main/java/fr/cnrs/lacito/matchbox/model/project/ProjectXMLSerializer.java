package fr.cnrs.lacito.matchbox.model.project;

import fr.cnrs.lacito.matchbox.PluginManager;
import fr.cnrs.lacito.matchbox.grammarapi.GrammarPlugin;
import fr.cnrs.lacito.matchbox.model.DocumentOptions;
import java.io.File;
import java.util.List;
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

        // Add fileset element
        Element filesetElement = doc.createElement("fileset");
        rootElement.appendChild(filesetElement);

        // Add files
        for (String filePath : project.getFiles()) {
            Element fileElement = doc.createElement("file");
            fileElement.setAttribute("path", filePath);
            filesetElement.appendChild(fileElement);
        }

        // Add directories
        for (String dirPath : project.getDirectories()) {
            Element dirElement = doc.createElement("directory");
            dirElement.setAttribute("path", dirPath);
            filesetElement.appendChild(dirElement);
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

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(file);

        Element rootElement = doc.getDocumentElement();

        // Load fileset
        NodeList filesetList = rootElement.getElementsByTagName("fileset");
        if (filesetList.getLength() > 0) {
            Element filesetElement = (Element) filesetList.item(0);

            // Load files
            NodeList fileList = filesetElement.getElementsByTagName("file");
            for (int i = 0; i < fileList.getLength(); i++) {
                Element fileElement = (Element) fileList.item(i);
                String path = fileElement.getAttribute("path");
                project.addFile(path);
            }

            // Load directories
            NodeList dirList = filesetElement.getElementsByTagName("directory");
            for (int i = 0; i < dirList.getLength(); i++) {
                Element dirElement = (Element) dirList.item(i);
                String path = dirElement.getAttribute("path");
                project.addDirectory(path);
            }
        }

        // Load patterns
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

        NodeList assistantList = rootElement.getElementsByTagName("assistant");
        if (assistantList.getLength() > 0) {
            Element assistantElement = (Element) assistantList.item(0);
            project.setCloudAssistAllowed(!"false".equals(assistantElement.getAttribute("allowCloud")));
        }

        return project;
    }
}
