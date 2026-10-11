package fr.cnrs.lacito.matchbox.settings;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * User preferences as stored in {@code settings.json}. Immutable.
 *
 * <p>Values read from a file may be missing ({@code null}) or invalid: {@link #validated} replaces
 * them by the defaults, so that the rest of the application only sees valid values.
 *
 * @param version            format version of the file
 * @param charset            charset used to read and write the text files
 * @param morphemeSeparators characters separating morphemes
 * @param fileExtensions     extensions of the files added from a directory, lower case without dot;
 *                           empty means all files
 * @param filePatterns       file patterns
 * @param maxRecentFiles     size of the recent files list
 * @param maxRecentProjects  size of the recent projects list
 * @param openLastProject    reopen the last project at startup
 * @param logFile            log file chosen by the user, {@code null} for the default location
 * @param assist             settings of the AI assistant
 * @param allowInvalidViewEdits apply edits made in object views even if they add parse errors
 * @param tagBrowser         settings of the Tag Browser mode
 * @param editor             settings of the text editor of File Mode
 */
public record SettingsData(
    Integer version,
    String charset,
    String morphemeSeparators,
    List<String> fileExtensions,
    List<String> filePatterns,
    Integer maxRecentFiles,
    Integer maxRecentProjects,
    Boolean openLastProject,
    String logFile,
    AssistSettingsData assist,
    Boolean allowInvalidViewEdits,
    TagBrowserSettingsData tagBrowser,
    EditorSettingsData editor
) {

    public static final int CURRENT_VERSION = 2;


    public static SettingsData defaults() {
        return new SettingsData(
            CURRENT_VERSION,
            StandardCharsets.UTF_8.name(),
            "-,/",
            List.of("txt", "md"),
            List.of(),
            10,
            10,
            false,
            null,
            AssistSettingsData.defaults(),
            false,
            TagBrowserSettingsData.defaults(),
            EditorSettingsData.defaults()
        );
    }

    /**
     * Copy where missing or invalid values are replaced by the defaults. Each replacement of an
     * invalid (not just missing) value is reported to {@code problems}.
     */
    public SettingsData validated(Consumer<String> problems) {
        SettingsData d = defaults();
        String validCharset = d.charset;
        if (charset != null) {
            if (isSupportedCharset(charset)) {
                validCharset = Charset.forName(charset).name();
            } else {
                problems.accept("Unknown charset '" + charset + "', using " + d.charset);
            }
        }
        return new SettingsData(
            CURRENT_VERSION,
            validCharset,
            morphemeSeparators == null ? d.morphemeSeparators : morphemeSeparators,
            fileExtensions == null ? d.fileExtensions : normalizeExtensions(fileExtensions),
            filePatterns == null ? d.filePatterns : normalizeList(filePatterns),
            positive(maxRecentFiles, d.maxRecentFiles, "maxRecentFiles", problems),
            positive(maxRecentProjects, d.maxRecentProjects, "maxRecentProjects", problems),
            openLastProject == null ? d.openLastProject : openLastProject,
            logFile == null || logFile.isBlank() ? null : logFile.strip(),
            assist == null ? d.assist : assist.validated(problems),
            allowInvalidViewEdits == null ? d.allowInvalidViewEdits : allowInvalidViewEdits,
            tagBrowser == null ? d.tagBrowser : tagBrowser.validated(problems),
            editor == null ? d.editor : editor.validated(problems)
        );
    }

    /** Copy with other assistant settings. */
    public SettingsData withAssist(AssistSettingsData newAssist) {
        return new SettingsData(version, charset, morphemeSeparators, fileExtensions,
            filePatterns, maxRecentFiles, maxRecentProjects, openLastProject, logFile, newAssist, allowInvalidViewEdits,
            tagBrowser, editor);
    }

    /** Copy with other Tag Browser settings. */
    public SettingsData withTagBrowser(TagBrowserSettingsData newTagBrowser) {
        return new SettingsData(version, charset, morphemeSeparators, fileExtensions,
            filePatterns, maxRecentFiles, maxRecentProjects, openLastProject, logFile, assist, allowInvalidViewEdits,
            newTagBrowser, editor);
    }

    /** Copy with other editor settings. */
    public SettingsData withEditor(EditorSettingsData newEditor) {
        return new SettingsData(version, charset, morphemeSeparators, fileExtensions,
            filePatterns, maxRecentFiles, maxRecentProjects, openLastProject, logFile, assist, allowInvalidViewEdits,
            tagBrowser, newEditor);
    }

    /** Validated copy, problems ignored. */
    public SettingsData validated() {
        return validated(p -> {});
    }

    public Charset charsetValue() {
        return Charset.forName(Objects.requireNonNull(charset, "charset (call validated() first)"));
    }

    /** Extensions in lower case, without leading dot, blanks and duplicates removed. */
    public static List<String> normalizeExtensions(List<String> extensions) {
        Set<String> result = new LinkedHashSet<>();
        for (String e : extensions) {
            if (e == null) continue;
            String ext = e.strip();
            while (ext.startsWith(".")) {
                ext = ext.substring(1);
            }
            if (!ext.isEmpty()) {
                result.add(ext.toLowerCase(Locale.ROOT));
            }
        }
        return List.copyOf(result);
    }

    private static List<String> normalizeList(List<String> values) {
        List<String> result = new ArrayList<>();
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                result.add(v.strip());
            }
        }
        return List.copyOf(result);
    }

    static boolean isSupportedCharset(String name) {
        try {
            return Charset.isSupported(name);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static int positive(Integer value, int fallback, String name, Consumer<String> problems) {
        if (value == null) return fallback;
        if (value > 0) return value;
        problems.accept("Invalid value " + value + " for " + name + ", using " + fallback);
        return fallback;
    }
}
