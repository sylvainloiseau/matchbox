package fr.cnrs.lacito.matchbox.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.cnrs.lacito.matchbox.corpus.Diagnostic;
import fr.cnrs.lacito.matchbox.corpus.Text;
import fr.cnrs.lacito.matchbox.corpus.capability.InterlinearGlossedText;
import fr.cnrs.lacito.matchbox.corpus.gloss.GlossLabelList;
import fr.cnrs.lacito.matchbox.grammar.igt.IgtGrammarPlugin;
import fr.cnrs.lacito.matchbox.grammarapi.GrammarPlugin;
import fr.cnrs.lacito.matchbox.grammarfx.ViewFinding;
import fr.cnrs.lacito.matchbox.gui.mode.FileMode;
import fr.cnrs.lacito.matchbox.gui.mode.Modes;
import fr.cnrs.lacito.matchbox.gui.problems.ProblemRow;
import fr.cnrs.lacito.matchbox.model.check.GlossCheckSettings;
import fr.cnrs.lacito.matchbox.model.check.LexiconCheck;
import fr.cnrs.lacito.matchbox.model.project.FileType;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.Start;
import org.testfx.framework.junit5.Stop;

/** The checks of IGTs in the window: findings in the margin, the Semantic issues tab and the view; fixes. */
class GlossCheckGuiTest extends MainWindowFixture {

    static final String NOTES = """
        :::{olang="fra-x-ipa", mlang="en"}
        ʒə=kʁwa pa ase
        1SG=believe not enough
        :::
        """;

    @Start
    void start(Stage stage) throws IOException {
        startWindow(stage);
    }

    @Stop
    void stop() {
        stopWindow();
    }

    private FileMode fileMode() {
        return (FileMode) workbench().modes().orElseThrow().get(Modes.FILE);
    }

    private void grammars(FxRobot robot, GrammarPlugin... plugins) {
        robot.interact(() -> {
            FileType type = new FileType("All files", ".*\\..*");
            for (GrammarPlugin p : plugins) type.addGrammar(p);
            project().setFileTypes(List.of(type));
        });
    }

    private Text text(Path file) {
        return workbench().session().corpus().text(document(file).getId()).orElseThrow();
    }

    private Path setUp(FxRobot robot) throws IOException {
        grammars(robot, new IgtGrammarPlugin());
        Path lift = Files.createTempFile("small", ".lift");
        try (InputStream in = getClass().getResourceAsStream("/small.lift")) {
            Files.copy(in, lift, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        robot.interact(() -> project().setContextDictionary(lift));
        try {
            waitUntil(() -> workbench().problems().dictionary().index().isPresent());
        } catch (AssertionError e) {
            throw new AssertionError("dictionary: " + workbench().problems().dictionary().state()
                + " project dictionary " + project().getContextDictionary(), e);
        }
        Path file = addFile(robot, "igt.md", NOTES);
        waitUntil(() -> workbench().session().corpus().text(document(file).getId()).isPresent());
        showFile(robot, file);
        return file;
    }

    private List<ProblemRow> dictionaryRows() {
        return fileMode().semanticIssuesPane().table().getItems().stream()
            .filter(r -> r.diagnostic().grammarId().equals(LexiconCheck.ID)).toList();
    }

    @Test
    void findings_are_shown_and_a_pair_is_declared_in_one_change(FxRobot robot) throws IOException {
        Path file = setUp(robot);
        // kʁwa/believe: gloss unknown; pa/not: form unknown; ase/enough: known; 1SG: grammatical
        waitUntil(() -> dictionaryRows().size() == 2);
        assertTrue(fileMode().getEditorController().getCurrentFileErrors().stream()
            .anyMatch(d -> d.grammarId().equals(LexiconCheck.ID)), "in the margin too");

        ProblemRow pa = dictionaryRows().stream().filter(r -> r.diagnostic().message().startsWith("pa / not")).findFirst().orElseThrow();
        Diagnostic.Fix fix = pa.diagnostic().fixes().get(0);
        assertEquals("Declare pa / not", fix.label());
        robot.interact(() -> workbench().problems().applyFix(pa, fix));
        waitUntil(() -> document(file).getCurrentText().startsWith("p /pa/not\n:::"));
        // the warning stays (the p notation is not read yet), but its fix is not offered again
        waitUntil(() -> dictionaryRows().stream().anyMatch(r -> r.diagnostic().message().startsWith("pa / not")
            && r.diagnostic().fixes().isEmpty()));
        robot.interact(() -> workbench().undo());
        waitUntil(() -> document(file).getCurrentText().equals(NOTES));
    }

    @Test
    void declare_all_from_the_view_is_one_change(FxRobot robot) throws IOException {
        Path file = setUp(robot);
        waitUntil(() -> dictionaryRows().size() == 2);
        InterlinearGlossedText igt = text(file).objectsOf(InterlinearGlossedText.class).get(0);
        List<ViewFinding> findings = workbench().viewContext().findings(igt);
        assertEquals(2, findings.size());
        robot.interact(() -> workbench().viewContext().applyFixes(igt, findings, "Declare all"));
        waitUntil(() -> document(file).getCurrentText().startsWith("p /kʁwa/believe\np /pa/not\n:::"));
        robot.interact(() -> workbench().undo());
        waitUntil(() -> document(file).getCurrentText().equals(NOTES));
    }

    @Test
    void the_settings_of_the_project_change_the_findings(FxRobot robot) throws IOException {
        setUp(robot);
        waitUntil(() -> dictionaryRows().size() == 2);
        robot.interact(() -> project().setGlossCheck(new GlossCheckSettings(GlossLabelList.LEIPZIG, Set.of(), false)));
        waitUntil(() -> dictionaryRows().isEmpty());
        robot.interact(() -> project().setGlossCheck(GlossCheckSettings.defaults()));
        waitUntil(() -> dictionaryRows().size() == 2);
    }
}
