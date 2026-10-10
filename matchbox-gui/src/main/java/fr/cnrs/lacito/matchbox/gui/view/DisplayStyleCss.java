package fr.cnrs.lacito.matchbox.gui.view;

import fr.cnrs.lacito.matchbox.grammarapi.DisplayStyle;

/** JavaFX CSS of a {@link DisplayStyle}: for the editor's text, and for labels (previews, badges). */
public final class DisplayStyleCss {

    private DisplayStyleCss() {
    }

    /** Inline style of a run of the editor ({@code InlineCssTextArea}). */
    public static String editor(DisplayStyle s) {
        StringBuilder css = font(s);
        if (Boolean.TRUE.equals(s.underline())) css.append("-fx-underline: true;");
        if (Boolean.TRUE.equals(s.strikethrough())) css.append("-fx-strikethrough: true;");
        if (s.textColor() != null) css.append("-fx-fill: ").append(s.textColor()).append(';');
        if (s.backgroundColor() != null) css.append("-rtfx-background-color: ").append(s.backgroundColor()).append(';');
        return css.toString();
    }

    /** Style of a {@code Label} (strikethrough is not available on labels). */
    public static String label(DisplayStyle s) {
        StringBuilder css = font(s);
        if (Boolean.TRUE.equals(s.underline())) css.append("-fx-underline: true;");
        if (s.textColor() != null) css.append("-fx-text-fill: ").append(s.textColor()).append(';');
        if (s.backgroundColor() != null) css.append("-fx-background-color: ").append(s.backgroundColor()).append(';');
        return css.toString();
    }

    private static StringBuilder font(DisplayStyle s) {
        StringBuilder css = new StringBuilder();
        if (s.fontFamily() != null) {
            css.append("-fx-font-family: \"").append(s.fontFamily().replaceAll("[\"';{}]", "")).append("\";");
        }
        if (s.bold() != null) css.append("-fx-font-weight: ").append(s.bold() ? "bold" : "normal").append(';');
        if (s.italic() != null) css.append("-fx-font-style: ").append(s.italic() ? "italic" : "normal").append(';');
        return css;
    }
}
