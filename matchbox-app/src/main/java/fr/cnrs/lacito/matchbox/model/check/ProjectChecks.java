package fr.cnrs.lacito.matchbox.model.check;

import fr.cnrs.lacito.matchbox.corpus.Diagnostic;
import fr.cnrs.lacito.matchbox.corpus.Text;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The checks of the application ({@link IgtStructureCheck}, {@link GlossLabelCheck},
 * {@link LexiconCheck}) on the texts of a project: computed when asked for a text, kept per parsed
 * {@link Text} (texts are immutable), all dropped when the inputs change (settings, dictionary), which
 * the listeners are told. Thread-safe.
 */
public final class ProjectChecks {

    private static final Logger logger = Logger.getLogger(ProjectChecks.class.getName());

    private final List<CorpusCheck> checks;
    private final Map<Text, List<Diagnostic>> cache = Collections.synchronizedMap(new WeakHashMap<>());
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private volatile CheckInputs inputs = CheckInputs.defaults();

    public ProjectChecks() {
        this(List.of(new IgtStructureCheck(), new GlossLabelCheck(), new LexiconCheck(), new DictionaryHistoryCheck()));
    }

    public ProjectChecks(List<CorpusCheck> checks) {
        this.checks = List.copyOf(checks);
    }

    /** The findings of the checks on a text. */
    public List<Diagnostic> of(Text text) {
        return cache.computeIfAbsent(text, this::compute);
    }

    private List<Diagnostic> compute(Text text) {
        List<Diagnostic> result = new ArrayList<>();
        CheckInputs in = inputs;
        for (CorpusCheck check : checks) {
            try {
                result.addAll(check.check(text, in));
            } catch (RuntimeException e) {
                logger.log(Level.WARNING, "Check " + check.id() + " failed on " + text.id(), e);
            }
        }
        return List.copyOf(result);
    }

    public CheckInputs inputs() {
        return inputs;
    }

    /** New settings or dictionary: the findings are computed again when asked; listeners are told. */
    public void setInputs(CheckInputs newInputs) {
        if (newInputs.equals(inputs)) return;
        inputs = newInputs;
        cache.clear();
        listeners.forEach(Runnable::run);
    }

    public void addListener(Runnable listener) {
        listeners.add(listener);
    }
}
