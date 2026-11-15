package fr.cnrs.lacito.matchbox.gui.layout;

/**
 * How a layout value shows and hides its panels, for {@link PanelToggles}. Pure.
 *
 * @param <T> the immutable layout ({@link ColumnLayout}, a mode's own layout record)
 */
public interface PanelModel<T> {

    boolean isShown(T layout, int panel);

    /** False when hiding the panel would leave nothing shown. */
    boolean canHide(T layout, int panel);

    /** The layout with the panel shown or hidden (unchanged if refused). */
    T withShown(T layout, int panel, boolean show);

    /** The panels of a {@link ColumnLayout}, one per pane. */
    static PanelModel<ColumnLayout> columns() {
        return new PanelModel<>() {
            @Override
            public boolean isShown(ColumnLayout layout, int panel) {
                return layout.isShown(panel);
            }

            @Override
            public boolean canHide(ColumnLayout layout, int panel) {
                return layout.canHide(panel);
            }

            @Override
            public ColumnLayout withShown(ColumnLayout layout, int panel, boolean show) {
                return layout.withShown(panel, show);
            }
        };
    }
}
