package fr.cnrs.lacito.matchbox.gui.view;

import fr.cnrs.lacito.matchbox.corpus.CorpusObject;
import fr.cnrs.lacito.matchbox.corpus.capability.InterlinearSentence;
import fr.cnrs.lacito.matchbox.corpus.capability.LexicalEntry;
import fr.cnrs.lacito.matchbox.corpus.capability.Sense;
import fr.cnrs.lacito.matchbox.model.ApplicationSettings;
import java.util.List;
import javafx.scene.paint.Color;

/** Editor highlight colours of corpus objects. */
public class HighlightStyles {

    /** Light background colours for kinds without a colour setting. */
    private static final List<String> PALETTE = List.of(
        "#fde2e4", "#dfe7fd", "#fff1c1", "#e2ece9",
        "#e8dff5", "#d4f0f0", "#fce1c8", "#e0f2d8"
    );

    private final ApplicationSettings settings;

    public HighlightStyles(ApplicationSettings settings) {
        this.settings = settings;
    }

    /**
     * CSS colour for an object, or {@code null} if it has no highlight of its own (it then shows
     * its parent's colour).
     */
    public String colorFor(CorpusObject object) {
        if (object instanceof InterlinearSentence) {
            return validOr(settings.getIgtParagraphColor(), object);
        }
        if (object instanceof LexicalEntry) {
            return validOr(settings.getLexicalDescriptionColor(), object);
        }
        if (object instanceof Sense) {
            return null;
        }
        return paletteColor(object.getKind());
    }

    /** The configured colour, or the palette colour if it is not a valid CSS colour. */
    private static String validOr(String color, CorpusObject object) {
        try {
            Color.web(color);
            return color;
        } catch (RuntimeException e) {
            return paletteColor(object.getKind());
        }
    }

    static String paletteColor(String kind) {
        return PALETTE.get(Math.floorMod(kind.hashCode(), PALETTE.size()));
    }
}
