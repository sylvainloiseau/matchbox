package fr.cnrs.lacito.matchbox.grammarapi;

import java.util.regex.Pattern;

/**
 * How the objects of a grammar (or of one of its kinds) are shown in the editor: font family,
 * weight, posture, decorations, text and background colours. Every property is optional:
 * {@code null} means "not set", and the value comes from a more general style ({@link #over}).
 *
 * <p>A project stores one style per grammar and per kind; a grammar may give defaults with
 * {@link GrammarPlugin#getDefaultDisplayStyle(String)}.
 *
 * @param fontFamily      font family name; null for the editor font
 * @param bold            bold text
 * @param italic          italic text
 * @param underline       underlined text
 * @param strikethrough   struck-through text
 * @param textColor       CSS colour {@code #rrggbb} of the text
 * @param backgroundColor CSS colour {@code #rrggbb} of the background
 */
public record DisplayStyle(String fontFamily, Boolean bold, Boolean italic, Boolean underline,
                           Boolean strikethrough, String textColor, String backgroundColor) {

    /** No property set. */
    public static final DisplayStyle EMPTY = new DisplayStyle(null, null, null, null, null, null, null);

    private static final Pattern COLOR = Pattern.compile("#([0-9a-fA-F]{3}|[0-9a-fA-F]{6})");

    public DisplayStyle {
        fontFamily = blankToNull(fontFamily);
        textColor = blankToNull(textColor);
        backgroundColor = blankToNull(backgroundColor);
    }

    /** A style setting only the background colour. */
    public static DisplayStyle background(String color) {
        return EMPTY.withBackgroundColor(color);
    }

    /** True for a CSS colour written {@code #rgb} or {@code #rrggbb}. */
    public static boolean isColor(String value) {
        return value != null && COLOR.matcher(value.strip()).matches();
    }

    /** This style, where each property not set is taken from {@code base}. */
    public DisplayStyle over(DisplayStyle base) {
        if (base == null) return this;
        return new DisplayStyle(
            fontFamily != null ? fontFamily : base.fontFamily,
            bold != null ? bold : base.bold,
            italic != null ? italic : base.italic,
            underline != null ? underline : base.underline,
            strikethrough != null ? strikethrough : base.strikethrough,
            textColor != null ? textColor : base.textColor,
            backgroundColor != null ? backgroundColor : base.backgroundColor);
    }

    /** True if no property is set. */
    public boolean isEmpty() {
        return equals(EMPTY);
    }

    /** A copy without the colours that are not {@code #rgb} / {@code #rrggbb}. */
    public DisplayStyle validated() {
        return new DisplayStyle(fontFamily, bold, italic, underline, strikethrough,
            isColor(textColor) ? textColor.strip() : null,
            isColor(backgroundColor) ? backgroundColor.strip() : null);
    }

    public DisplayStyle withFontFamily(String value) {
        return new DisplayStyle(value, bold, italic, underline, strikethrough, textColor, backgroundColor);
    }

    public DisplayStyle withBold(Boolean value) {
        return new DisplayStyle(fontFamily, value, italic, underline, strikethrough, textColor, backgroundColor);
    }

    public DisplayStyle withItalic(Boolean value) {
        return new DisplayStyle(fontFamily, bold, value, underline, strikethrough, textColor, backgroundColor);
    }

    public DisplayStyle withUnderline(Boolean value) {
        return new DisplayStyle(fontFamily, bold, italic, value, strikethrough, textColor, backgroundColor);
    }

    public DisplayStyle withStrikethrough(Boolean value) {
        return new DisplayStyle(fontFamily, bold, italic, underline, value, textColor, backgroundColor);
    }

    public DisplayStyle withTextColor(String value) {
        return new DisplayStyle(fontFamily, bold, italic, underline, strikethrough, value, backgroundColor);
    }

    public DisplayStyle withBackgroundColor(String value) {
        return new DisplayStyle(fontFamily, bold, italic, underline, strikethrough, textColor, value);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

}
