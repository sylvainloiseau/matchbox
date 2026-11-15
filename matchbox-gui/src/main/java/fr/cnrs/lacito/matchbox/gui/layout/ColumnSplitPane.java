package fr.cnrs.lacito.matchbox.gui.layout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.SplitPane;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;

/**
 * A {@link SplitPane} whose panes can be hidden and shown again at their place and size, driven
 * by a {@link ColumnLayout} property (the only state). Changing the property shows the panes it
 * says, with its sizes; the user's changes come back into it: moving a divider (recorded when the
 * mouse is released, not on every resize of the window) and double-clicking a divider
 * ({@link ColumnLayout#dividerDoubleClicked}). A pane holding the keyboard focus gives it, before
 * it is hidden, to the first shown pane of {@link #setFocusOrder the focus order}.
 */
public final class ColumnSplitPane {

    private static final double EPSILON = 1e-4;

    private final SplitPane pane = new SplitPane();
    private final List<Node> panes;
    private final ObjectProperty<ColumnLayout> layout;
    private List<Integer> focusOrder;
    private List<Node> focusTargets;
    /** True while the split is set from the layout (its divider changes are not the user's). */
    private boolean applying;

    public ColumnSplitPane(Orientation orientation, List<? extends Node> panes, ColumnLayout initial) {
        Objects.requireNonNull(initial, "initial");
        if (panes.size() != initial.count()) {
            throw new IllegalArgumentException(panes.size() + " panes for a layout of " + initial.count());
        }
        this.panes = List.copyOf(panes);
        this.layout = new SimpleObjectProperty<>(initial);
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < panes.size(); i++) order.add(i);
        this.focusOrder = order;
        this.focusTargets = this.panes;
        pane.setOrientation(orientation);
        apply(initial);
        layout.addListener((obs, o, n) -> {
            if (n == null) {
                layout.set(o);
                return;
            }
            apply(n);
        });
        pane.addEventFilter(MouseEvent.MOUSE_RELEASED, e -> {
            if (e.getButton() == MouseButton.PRIMARY && ownDivider(e) >= 0) recordDividers();
        });
        pane.addEventFilter(MouseEvent.MOUSE_CLICKED, e -> {
            int divider = ownDivider(e);
            if (divider >= 0 && e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) {
                e.consume();
                layout.set(layout.get().dividerDoubleClicked(divider));
            }
        });
    }

    /** The split to put in the scene. */
    public SplitPane node() {
        return pane;
    }

    /** The layout shown; set it to show other panes or sizes. */
    public ObjectProperty<ColumnLayout> layoutProperty() {
        return layout;
    }

    /**
     * Where the focus of a hidden pane goes: the first shown pane of {@code order} (indexes),
     * to its node in {@code targets} (one per pane, e.g. the editor of the editor pane).
     */
    public void setFocusOrder(List<Integer> order, List<? extends Node> targets) {
        if (targets.size() != panes.size()) throw new IllegalArgumentException("one focus target per pane");
        this.focusOrder = List.copyOf(order);
        this.focusTargets = List.copyOf(targets);
    }

    /** Sets the panes and the dividers of the split from a layout. */
    private void apply(ColumnLayout l) {
        applying = true;
        try {
            List<Node> wanted = new ArrayList<>();
            for (int i : l.shownPanes()) wanted.add(panes.get(i));
            if (!wanted.equals(pane.getItems())) {
                moveFocusOutOfHidden(wanted);
                pane.getItems().setAll(wanted);
            }
            double[] positions = l.dividerPositions();
            if (!Arrays.equals(round(positions), round(pane.getDividerPositions()))) {
                pane.setDividerPositions(positions);
            }
        } finally {
            applying = false;
        }
    }

    /** The user moved a divider: its new positions go into the layout. */
    private void recordDividers() {
        if (applying) return;
        double[] positions = pane.getDividerPositions();
        double[] expected = layout.get().dividerPositions();
        if (positions.length != expected.length) return;
        boolean changed = false;
        for (int i = 0; i < positions.length; i++) {
            if (Math.abs(positions[i] - expected[i]) > EPSILON) changed = true;
        }
        if (changed) layout.set(layout.get().dragged(positions));
    }

    /** Index of the divider of this split under the event (not of a nested split), or -1. */
    private int ownDivider(MouseEvent e) {
        Object target = e.getTarget();
        Node node = target instanceof Node n ? n : null;
        while (node != null && node != pane) {
            if (node.getStyleClass().contains("split-pane-divider") && node.getParent() == pane) {
                int index = 0;
                for (Node child : pane.getChildrenUnmodifiable()) {
                    if (child == node) return index;
                    if (child.getStyleClass().contains("split-pane-divider")) index++;
                }
                return -1;
            }
            node = node.getParent();
        }
        return -1;
    }

    private void moveFocusOutOfHidden(List<Node> wanted) {
        Scene scene = pane.getScene();
        Node owner = scene == null ? null : scene.getFocusOwner();
        if (owner == null) return;
        for (Node p : pane.getItems()) {
            if (!wanted.contains(p) && isInside(owner, p)) {
                for (int i : focusOrder) {
                    Node candidate = panes.get(i);
                    if (wanted.contains(candidate)) {
                        focusTargets.get(i).requestFocus();
                        return;
                    }
                }
                pane.requestFocus();
                return;
            }
        }
    }

    private static boolean isInside(Node node, Node ancestor) {
        for (Node n = node; n != null; n = n.getParent()) {
            if (n == ancestor) return true;
        }
        return false;
    }

    private static double[] round(double[] values) {
        double[] r = new double[values.length];
        for (int i = 0; i < values.length; i++) r[i] = Math.round(values[i] / EPSILON) * EPSILON;
        return r;
    }
}
