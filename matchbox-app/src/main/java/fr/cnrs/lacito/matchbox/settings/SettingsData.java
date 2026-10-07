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
import java.util.regex.Pattern;

/**
 * User preferences as stored in {@code settings.json}. Immutable.
 *
 * <p>Values read from a file may be missing ({@code null}) or invalid: {@link #validated} replaces
 * them by the defaults, so that the rest of the application only sees valid values.
 *
 * @param version            format version of the file
 * @param charset            charset used to read and write the text files
 * @param igtColor           highlight colour of interlinear glossed texts (CSS colour)
 * @param lexicalColor       highlight colour of lexical entries (CSS colour)
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
 * @param tagColor           highlight colour of tags (CSS colour)
 */
public record SettingsData(
    Integer version,
    String charset,
    String igtColor,
    String lexicalColor,
    String morphemeSeparators,
    List<String> fileExtensions,
    List<String> filePatterns,
    Integer maxRecentFiles,
    Integer maxRecentProjects,
    Boolean openLastProject,
    String logFile,
    AssistSettingsData assist,
    Boolean allowInvalidViewEdits,
    String tagColor
) {

    public static final int CURRENT_VERSION = 2;

    private static final Pattern COLOR = Pattern.compile(
        "#(\\p{XDigit}{3}|\\p{XDigit}{4}|\\p{XDigit}{6}|\\p{XDigit}{8})|[a-zA-Z]+"
    );

    public static SettingsData defaults() {
        return new SettingsData(
            CURRENT_VERSION,
            StandardCharsets.UTF_8.name(),
            "#fff1c1",
            "#dfe7fd",
            "-,/",
            List.of("txt", "md"),
            List.of(),
            10,
            10,
            false,
            null,
            AssistSettingsData.defaults(),
            false,
            "#ffd6e8"
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
            color(igtColor, d.igtColor, "igtColor", problems),
            color(lexicalColor, d.lexicalColor, "lexicalColor", problems),
            morphemeSeparators == null ? d.morphemeSeparators : morphemeSeparators,
            fileExtensions == null ? d.fileExtensions : normalizeExtensions(fileExtensions),
            filePatterns == null ? d.filePatterns : normalizeList(filePatterns),
            positive(maxRecentFiles, d.maxRecentFiles, "maxRecentFiles", problems),
            positive(maxRecentProjects, d.maxRecentProjects, "maxRecentProjects", problems),
            openLastProject == null ? d.openLastProject : openLastProject,
            logFile == null || logFile.isBlank() ? null : logFile.strip(),
            assist == null ? d.assist : assist.validated(problems),
            allowInvalidViewEdits == null ? d.allowInvalidViewEdits : allowInvalidViewEdits,
            color(tagColor, d.tagColor, "tagColor", problems)
        );
    }

    /** Copy with other assistant settings. */
    public SettingsData withAssist(AssistSettingsData newAssist) {
        return new SettingsData(version, charset, igtColor, lexicalColor, morphemeSeparators, fileExtensions,
            filePatterns, maxRecentFiles, maxRecentProjects, openLastProject, logFile, newAssist, allowInvalidViewEdits, tagColor);
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

    static boolean isValidColor(String value) {
        return value != null && COLOR.matcher(value.strip()).matches();
    }

    private static String color(String value, String fallback, String name, Consumer<String> problems) {
        if (value == null) return fallback;
        if (isValidColor(value)) return value.strip();
        problems.accept("Invalid colour '" + value + "' for " + name + ", using " + fallback);
        return fallback;
    }

    private static int positive(Integer value, int fallback, String name, Consumer<String> problems) {
        if (value == null) return fallback;
        if (value > 0) return value;
        problems.accept("Invalid value " + value + " for " + name + ", using " + fallback);
        return fallback;
    }
}
