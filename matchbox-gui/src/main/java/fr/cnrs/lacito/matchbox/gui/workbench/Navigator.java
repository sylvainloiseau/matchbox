package fr.cnrs.lacito.matchbox.gui.workbench;

import fr.cnrs.lacito.matchbox.corpus.CorpusObject;
import fr.cnrs.lacito.matchbox.corpus.CorpusView;
import fr.cnrs.lacito.matchbox.corpus.Text;
import fr.cnrs.lacito.matchbox.corpus.TextCoordinate;
import fr.cnrs.lacito.matchbox.gui.mode.Mode;
import fr.cnrs.lacito.matchbox.gui.mode.ModeHost;
import fr.cnrs.lacito.matchbox.gui.mode.Modes;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Moves the user between modes and places: switching mode, "show in editor" (always File Mode),
 * revealing an object, and the Back/Forward history of these jumps. Ordinary selections inside a
 * mode are not recorded.
 */
public final class Navigator {

    /** A place: a mode and its focus. */
    public record Place(String modeId, Optional<Focus> focus) {
        public Place {
            Objects.requireNonNull(modeId);
            Objects.requireNonNull(focus);
        }
    }

    private final ProjectSession session;
    private final NavigationHistory<Place> history = new NavigationHistory<>();
    private ModeHost host;

    public Navigator(ProjectSession session) {
        this.session = session;
        session.projectProperty().addListener((obs, o, n) -> history.clear());
        session.addCorpusListener((id, before, after) -> history.replaceAll(p -> update(p, session.corpus())));
    }

    /** Connects the navigator to the modes of the window (once they exist). */
    public void attach(ModeHost modeHost) {
        this.host = modeHost;
    }

    /** The modes of the window; null before {@link #attach}. */
    public ModeHost host() {
        return host;
    }

    public NavigationHistory<Place> history() {
        return history;
    }

    /** Switches to a mode (a jump: recorded in the history). */
    public void switchTo(String modeId) {
        if (host == null || host.activeId().map(modeId::equals).orElse(false)) return;
        here().ifPresent(history::jumped);
        host.activate(modeId);
    }

    /** Shows a span in the text editor (File Mode). */
    public void showInEditor(TextCoordinate span) {
        if (span != null) showInEditor(Focus.of(span));
    }

    /** Shows a place in the text editor (File Mode). */
    public void showInEditor(Focus focus) {
        if (host == null || focus == null) return;
        here().ifPresent(history::jumped);
        host.activate(Modes.FILE).reveal(focus);
    }

    /** Shows an object in the active mode if it can, otherwise in the editor. */
    public void reveal(CorpusObject object) {
        if (host == null) return;
        Focus focus = Focus.of(object);
        Optional<Mode> active = host.activeMode();
        if (active.isPresent() && !host.activeId().map(Modes.FILE::equals).orElse(false)) {
            here().ifPresent(history::jumped);
            if (active.get().reveal(focus)) return;
            host.activate(Modes.FILE).reveal(focus);
            return;
        }
        // in File Mode: an ordinary selection, not a jump
        host.activate(Modes.FILE).reveal(focus);
    }

    public void back() {
        here().flatMap(history::back).ifPresent(this::go);
    }

    public void forward() {
        here().flatMap(history::forward).ifPresent(this::go);
    }

    private void go(Place place) {
        Mode mode = host.activate(place.modeId());
        place.focus().ifPresent(mode::reveal);
    }

    /** The current place: the active mode and its focus. */
    public Optional<Place> here() {
        if (host == null) return Optional.empty();
        return host.activeId().map(id -> new Place(id, host.focus()));
    }

    /** A place after a re-parse: its object replaced by its successor; dropped if its text is gone. */
    static Optional<Place> update(Place place, CorpusView corpus) {
        if (place.focus().isEmpty()) return Optional.of(place);
        Focus f = place.focus().get();
        Optional<Text> text = corpus.text(f.text());
        if (text.isEmpty()) return Optional.empty();
        Optional<CorpusObject> object = f.object();
        if (object.isPresent() && !text.get().owns(object.get())) {
            object = ObjectSuccessor.find(text.get(), object.get(), OptionalInt.empty());
        }
        int length = text.get().content().length();
        int start = Math.min(f.start(), length);
        Focus updated = new Focus(f.text(), start, Math.max(start, Math.min(f.end(), length)), object);
        return Optional.of(new Place(place.modeId(), Optional.of(updated)));
    }
}
