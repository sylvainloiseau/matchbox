package fr.cnrs.lacito.matchbox.model.project;

import static org.junit.jupiter.api.Assertions.*;

import fr.cnrs.lacito.matchbox.grammarapi.DisplayStyle;
import fr.cnrs.lacito.matchbox.model.project.DisplayStyles.Key;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DisplayStylesTest {

    @TempDir
    Path dir;

    private static final Key NOTEBOOK = Key.grammar("notebook-grammar");
    private static final Key IGT = new Key("notebook-grammar", "notebook-grammar:igt");

    @Test
    void theKindOverridesTheGrammarPropertyByProperty() {
        DisplayStyles styles = DisplayStyles.NONE
            .with(NOTEBOOK, new DisplayStyle("Serif", true, null, null, null, null, "#eeeeee"))
            .with(IGT, DisplayStyle.background("#fff1c1"));
        assertEquals(new DisplayStyle("Serif", true, null, null, null, null, "#fff1c1"),
            styles.resolve("notebook-grammar", "notebook-grammar:igt"));
        assertEquals("#eeeeee", styles.resolve("notebook-grammar", "notebook-grammar:sense").backgroundColor());
        assertEquals(DisplayStyle.EMPTY, styles.resolve("tag-grammar", "tag-grammar:tag"));
    }

    @Test
    void anEmptyStyleRemovesTheEntry() {
        DisplayStyles styles = DisplayStyles.NONE.with(IGT, DisplayStyle.background("#fff1c1"));
        assertTrue(styles.with(IGT, DisplayStyle.EMPTY).isEmpty());
        assertTrue(DisplayStyles.of(Map.of(IGT, DisplayStyle.EMPTY)).isEmpty());
    }

    private Project project() {
        Project p = Project.createDefaultProject(List.of(new ProjectParseTest.WholeTextGrammar()));
        // no file type: loading does not need the grammars
        List.copyOf(p.getFileTypes()).forEach(p::removeFileType);
        return p;
    }

    @Test
    void stylesAreSavedAndLoadedWithTheProjectFile() throws Exception {
        Project p = project();
        DisplayStyles styles = DisplayStyles.NONE
            .with(NOTEBOOK, new DisplayStyle("Monaco", null, true, null, null, "#204080", null))
            .with(IGT, DisplayStyle.background("#fff1c1").withUnderline(true).withStrikethrough(false))
            // a grammar that is not installed (or unchecked in every file type) keeps its style
            .with(Key.grammar("uninstalled-grammar"), DisplayStyle.EMPTY.withBold(true));
        p.setDisplayStyles(styles);
        p.setName("fieldwork");
        p.setDescription("Notes of 2025");
        Path file = dir.resolve("p.mbx");
        p.setFile(file.toFile());
        ProjectXMLSerializer.save(p);
        assertTrue(Files.readString(file).contains("kind=\"notebook-grammar:igt\""));

        Project loaded = ProjectXMLSerializer.load(file.toFile(), List.of());
        assertEquals(styles, loaded.getDisplayStyles());
        assertEquals("fieldwork", loaded.getName());
        assertEquals("Notes of 2025", loaded.getDescription());
        assertTrue(loaded.getLoadProblems().isEmpty());
    }

    @Test
    void invalidValuesAreIgnoredAndReported() throws Exception {
        Path file = dir.resolve("p.mbx");
        Files.writeString(file, """
            <project>
              <displayStyles>
                <display grammar="tag-grammar" background="pink" bold="yes" italic="true"/>
              </displayStyles>
            </project>
            """);
        Project loaded = ProjectXMLSerializer.load(file.toFile(), List.of());
        assertEquals(DisplayStyle.EMPTY.withItalic(true), loaded.getDisplayStyles().get(Key.grammar("tag-grammar")));
        assertEquals(2, loaded.getLoadProblems().size());
    }

    @Test
    void theDefinitionIsUnchangedAfterCreatingLoadingAndSaving() throws Exception {
        Project p = project();
        // removing the file types is a change
        assertTrue(p.definitionChangedProperty().get());
        Project fresh = Project.createDefaultProject(List.of(new ProjectParseTest.WholeTextGrammar()));
        assertFalse(fresh.definitionChangedProperty().get(), "a new project has nothing to save yet");

        Path file = dir.resolve("p.mbx");
        p.setFile(file.toFile());
        ProjectXMLSerializer.save(p);
        assertFalse(p.definitionChangedProperty().get(), "saved");
        p.setDisplayStyles(DisplayStyles.NONE.with(IGT, DisplayStyle.background("#ffffff")));
        assertTrue(p.definitionChangedProperty().get(), "a style is part of the definition");

        Project loaded = ProjectXMLSerializer.load(file.toFile(), List.of());
        assertFalse(loaded.definitionChangedProperty().get(), "a loaded project is what its file says");
    }

    @Test
    void projectEditAppliesTheStylesAndAnUnchangedEditChangesNothing() {
        Project fresh = Project.createDefaultProject(List.of(new ProjectParseTest.WholeTextGrammar()));
        ProjectEdit.of(fresh).applyTo(fresh);
        assertFalse(fresh.definitionChangedProperty().get(), "nothing edited");
        ProjectEdit edit = ProjectEdit.of(fresh);
        edit.setDisplayStyles(edit.getDisplayStyles().with(IGT, DisplayStyle.background("#fff1c1")));
        assertTrue(fresh.getDisplayStyles().isEmpty(), "the project is unchanged until applied");
        edit.applyTo(fresh);
        assertEquals("#fff1c1", fresh.getDisplayStyles().get(IGT).backgroundColor());
        assertTrue(fresh.definitionChangedProperty().get());
    }
}
