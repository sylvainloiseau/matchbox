package fr.cnrs.lacito.matchbox.gui.mode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import fr.cnrs.lacito.matchbox.corpus.Text;
import fr.cnrs.lacito.matchbox.corpus.TextId;
import fr.cnrs.lacito.matchbox.grammar.notebook.NotebookGrammarPlugin;
import fr.cnrs.lacito.matchbox.gui.workbench.EditHints;
import fr.cnrs.lacito.matchbox.gui.workbench.ProjectSession;
import fr.cnrs.lacito.matchbox.gui.workbench.SelectionTracker;
import fr.cnrs.lacito.matchbox.model.project.Project;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javafx.scene.Node;
import org.junit.jupiter.api.Test;

class AbstractModeTest {

    private static final TextId A = TextId.of(Path.of("a.txt"));
    private static final TextId B = TextId.of(Path.of("b.txt"));

    private final ProjectSession session = new ProjectSession(Runnable::run);
    private final List<String> calls = new ArrayList<>();
    private final AbstractMode mode = new AbstractMode(null, session, new SelectionTracker(new EditHints())) {
        @Override
        public Node root() {
            return null;
        }

        @Override
        protected void onTextsChanged(Set<TextId> texts) {
            calls.add("texts " + texts.stream().map(TextId::toString).sorted().toList());
        }

        @Override
        protected void onProjectChanged() {
            calls.add("project");
        }
    };

    private Project newProject() {
        return Project.createDefaultProject(List.of(new NotebookGrammarPlugin()));
    }

    private void change(TextId id) {
        session.project().getCorpus().put(Text.builder(id, "x").build());
    }

    @Test
    void theProjectSetBeforeTheFirstActivationIsGivenOnActivation() {
        session.setProject(newProject());
        change(A);
        assertEquals(List.of(), calls);
        mode.activate();
        assertEquals(List.of("project"), calls);
    }

    @Test
    void changesArriveAtOnceWhileActive() {
        session.setProject(newProject());
        mode.activate();
        calls.clear();
        change(A);
        assertEquals(List.of("texts [" + A + "]"), calls);
    }

    @Test
    void changesWhileHiddenAreGivenOnceOnActivation() {
        session.setProject(newProject());
        mode.activate();
        mode.deactivate();
        calls.clear();
        change(A);
        change(B);
        change(A);
        assertEquals(List.of(), calls);
        mode.activate();
        assertEquals(1, calls.size());
        assertEquals("texts " + List.of(A.toString(), B.toString()).stream().sorted().toList(), calls.get(0));
        mode.activate();
        assertEquals(1, calls.size(), "nothing more to give");
    }

    @Test
    void aProjectChangeSupersedesTheTextChanges() {
        session.setProject(newProject());
        mode.activate();
        mode.deactivate();
        calls.clear();
        change(A);
        session.setProject(newProject());
        change(B);
        mode.activate();
        assertEquals(List.of("project"), calls);
    }

    @Test
    void listenersFollowTheNewProject() {
        Project first = newProject();
        session.setProject(first);
        mode.activate();
        session.setProject(newProject());
        calls.clear();
        first.getCorpus().put(Text.builder(A, "x").build());
        assertEquals(List.of(), calls, "the old project is no longer listened to");
        change(B);
        assertEquals(List.of("texts [" + B + "]"), calls);
        assertNull(mode.selection.get());
    }
}
