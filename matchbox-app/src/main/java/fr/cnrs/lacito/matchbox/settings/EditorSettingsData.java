package fr.cnrs.lacito.matchbox.settings;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Settings of the text editor of File Mode, part of {@link SettingsData}. Immutable; global to
 * every file and project.
 *
 * @param wrapLines            wrap long lines at the width of the editor
 * @param lineNumbers          show line numbers in the margin
 * @param fontSize             font size in points (8–40)
 * @param fontFamily           font family; null for the default monospaced font
 * @param highlightCurrentLine light background on the caret's line
 * @param showInvisibles       show tabs and trailing spaces
 * @param autoIndent           Enter keeps the indentation of the current line
 * @param recentCharacters     special characters inserted recently, most recent first
 */
public record EditorSettingsData(Boolean wrapLines, Boolean lineNumbers, Integer fontSize, String fontFamily,
                                 Boolean highlightCurrentLine, Boolean showInvisibles, Boolean autoIndent,
                                 List<String> recentCharacters) {

    public static final int MIN_FONT_SIZE = 8;
    public static final int MAX_FONT_SIZE = 40;
    public static final int DEFAULT_FONT_SIZE = 13;
    public static final int MAX_RECENT_CHARACTERS = 24;

    public static EditorSettingsData defaults() {
        return new EditorSettingsData(false, true, DEFAULT_FONT_SIZE, null, true, false, true, List.of());
    }

    /** Copy where missing or invalid values are replaced by the defaults. */
    public EditorSettingsData validated(Consumer<String> problems) {
        EditorSettingsData d = defaults();
        int size = d.fontSize;
        if (fontSize != null) {
            if (fontSize < MIN_FONT_SIZE || fontSize > MAX_FONT_SIZE) {
                problems.accept("Invalid editor.fontSize " + fontSize + ", using " + clamp(fontSize));
                size = clamp(fontSize);
            } else {
                size = fontSize;
            }
        }
        return new EditorSettingsData(
            wrapLines == null ? d.wrapLines : wrapLines,
            lineNumbers == null ? d.lineNumbers : lineNumbers,
            size,
            fontFamily == null || fontFamily.isBlank() ? null : fontFamily.strip(),
            highlightCurrentLine == null ? d.highlightCurrentLine : highlightCurrentLine,
            showInvisibles == null ? d.showInvisibles : showInvisibles,
            autoIndent == null ? d.autoIndent : autoIndent,
            recentCharacters == null ? List.of()
                : recentCharacters.stream().filter(c -> c != null && !c.isEmpty()).distinct()
                    .limit(MAX_RECENT_CHARACTERS).toList());
    }

    public static int clamp(int size) {
        return Math.max(MIN_FONT_SIZE, Math.min(MAX_FONT_SIZE, size));
    }

    public EditorSettingsData withWrapLines(boolean value) {
        return new EditorSettingsData(value, lineNumbers, fontSize, fontFamily, highlightCurrentLine, showInvisibles, autoIndent, recentCharacters);
    }

    public EditorSettingsData withLineNumbers(boolean value) {
        return new EditorSettingsData(wrapLines, value, fontSize, fontFamily, highlightCurrentLine, showInvisibles, autoIndent, recentCharacters);
    }

    public EditorSettingsData withFontSize(int value) {
        return new EditorSettingsData(wrapLines, lineNumbers, clamp(value), fontFamily, highlightCurrentLine, showInvisibles, autoIndent, recentCharacters);
    }

    public EditorSettingsData withFontFamily(String value) {
        return new EditorSettingsData(wrapLines, lineNumbers, fontSize, value == null || value.isBlank() ? null : value,
            highlightCurrentLine, showInvisibles, autoIndent, recentCharacters);
    }

    public EditorSettingsData withHighlightCurrentLine(boolean value) {
        return new EditorSettingsData(wrapLines, lineNumbers, fontSize, fontFamily, value, showInvisibles, autoIndent, recentCharacters);
    }

    public EditorSettingsData withShowInvisibles(boolean value) {
        return new EditorSettingsData(wrapLines, lineNumbers, fontSize, fontFamily, highlightCurrentLine, value, autoIndent, recentCharacters);
    }

    public EditorSettingsData withAutoIndent(boolean value) {
        return new EditorSettingsData(wrapLines, lineNumbers, fontSize, fontFamily, highlightCurrentLine, showInvisibles, value, recentCharacters);
    }

    /** A copy where {@code character} is the most recent special character. */
    public EditorSettingsData withRecentCharacter(String character) {
        List<String> recent = new ArrayList<>();
        recent.add(character);
        if (recentCharacters != null) recentCharacters.stream().filter(c -> !c.equals(character)).forEach(recent::add);
        return new EditorSettingsData(wrapLines, lineNumbers, fontSize, fontFamily, highlightCurrentLine, showInvisibles,
            autoIndent, recent.stream().limit(MAX_RECENT_CHARACTERS).toList());
    }
}
