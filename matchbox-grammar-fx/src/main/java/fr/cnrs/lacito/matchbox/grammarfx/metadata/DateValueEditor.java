package fr.cnrs.lacito.matchbox.grammarfx.metadata;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

/**
 * ISO 8601 dates: shown in the user's locale (ISO value as tooltip); clicking opens a date picker
 * with an optional time. Values that are not dates are shown as plain text.
 */
final class DateValueEditor implements MetadataValueEditor {

    static final DateValueEditor INSTANCE = new DateValueEditor();

    @Override
    public Node display(ValueCell cell) {
        Optional<IsoDates.IsoDate> date = IsoDates.parse(cell.value());
        if (date.isEmpty()) {
            Node plain = PlainValueEditor.INSTANCE.display(cell);
            if (!cell.editable()) return plain;
            Button pick = pickerButton(cell, Optional.empty(), cell.commit());
            return new HBox(4, plain, pick);
        }
        String text = IsoDates.display(date.get(), Locale.getDefault());
        if (!cell.editable()) {
            Label label = new Label(text);
            label.setTooltip(new Tooltip(cell.value()));
            return label;
        }
        Hyperlink link = new Hyperlink(text);
        link.setTooltip(new Tooltip(cell.value() + " — click to change"));
        link.setOnAction(e -> {
            link.setVisited(false);
            choose(link.getScene() == null ? null : link.getScene().getWindow(), date).ifPresent(cell.commit());
        });
        return link;
    }

    @Override
    public Optional<Node> chooser(ValueCell cell, String current, Consumer<String> setValue) {
        return Optional.of(pickerButton(cell, IsoDates.parse(current), setValue));
    }

    private static Button pickerButton(ValueCell cell, Optional<IsoDates.IsoDate> initial, Consumer<String> setValue) {
        Button button = new Button("datepicker");
        button.setOnAction(e -> choose(button.getScene() == null ? null : button.getScene().getWindow(), initial)
            .ifPresent(setValue));
        return button;
    }

    /** The date picker dialog; the ISO value chosen, if any. */
    static Optional<String> choose(Window owner, Optional<IsoDates.IsoDate> initial) {
        Dialog<String> dialog = new Dialog<>();
        if (owner != null) dialog.initOwner(owner);
        dialog.setTitle("Date");
        DatePicker picker = new DatePicker(initial.map(IsoDates.IsoDate::date).orElse(LocalDate.now()));
        CheckBox withTime = new CheckBox("Time");
        LocalTime t = initial.flatMap(IsoDates.IsoDate::time).orElse(LocalTime.of(12, 0));
        Spinner<Integer> hours = new Spinner<>(0, 23, t.getHour());
        Spinner<Integer> minutes = new Spinner<>(0, 59, t.getMinute());
        hours.setEditable(true);
        minutes.setEditable(true);
        hours.setPrefWidth(70);
        minutes.setPrefWidth(70);
        withTime.setSelected(initial.flatMap(IsoDates.IsoDate::time).isPresent());
        hours.disableProperty().bind(withTime.selectedProperty().not());
        minutes.disableProperty().bind(withTime.selectedProperty().not());
        VBox box = new VBox(8, picker, new HBox(6, withTime, hours, new Label(":"), minutes));
        box.setPadding(new Insets(10));
        dialog.getDialogPane().setContent(box);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.setResultConverter(b -> {
            if (b != ButtonType.OK || picker.getValue() == null) return null;
            Optional<LocalTime> time = withTime.isSelected()
                ? Optional.of(LocalTime.of(hours.getValue(), minutes.getValue()))
                : Optional.empty();
            return IsoDates.write(picker.getValue(), time, initial);
        });
        // the calendar opens at once
        dialog.setOnShown(e -> Platform.runLater(picker::show));
        return dialog.showAndWait();
    }
}
