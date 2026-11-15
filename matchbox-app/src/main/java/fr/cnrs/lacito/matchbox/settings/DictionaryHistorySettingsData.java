package fr.cnrs.lacito.matchbox.settings;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Settings of the Dictionary History mode, part of {@link SettingsData}. Immutable.
 *
 * @param emptyObjectLanguage the object language of the empty dictionary a project without context
 *                            dictionary starts from
 * @param emptyMetaLanguage   its meta language
 * @param showEntries         the table of entries is shown
 * @param showEntry           the entry is shown
 * @param showCommands        the commands of the entry are shown
 * @param widths              widths of the table of entries and of the entry/commands column (sum 1)
 * @param heights             heights of the entry and of its commands (sum 1)
 * @param languages           the object and meta language chosen, by project file ("tww en"); at most
 *                            {@link #MAX_PROJECTS}
 * @param changedOnly         the table shows only the entries the commands changed
 */
public record DictionaryHistorySettingsData(String emptyObjectLanguage, String emptyMetaLanguage, Boolean showEntries,
                                            Boolean showEntry, Boolean showCommands, List<Double> widths,
                                            List<Double> heights, Map<String, String> languages, Boolean changedOnly) {

    public static final int MAX_PROJECTS = 50;
    public static final List<Double> DEFAULT_WIDTHS = List.of(0.28, 0.72);
    public static final List<Double> DEFAULT_HEIGHTS = List.of(0.6, 0.4);

    public static DictionaryHistorySettingsData defaults() {
        return new DictionaryHistorySettingsData("und", "en", true, true, true, DEFAULT_WIDTHS, DEFAULT_HEIGHTS,
            Map.of(), false);
    }

    /** Copy where missing or invalid values are replaced by the defaults. */
    public DictionaryHistorySettingsData validated(Consumer<String> problems) {
        DictionaryHistorySettingsData d = defaults();
        boolean entries = showEntries == null ? d.showEntries : showEntries;
        boolean entry = showEntry == null ? d.showEntry : showEntry;
        boolean commands = showCommands == null ? d.showCommands : showCommands;
        if (!entries && !entry && !commands) {
            problems.accept("dictionaryHistory: no panel shown, showing them all");
            entries = entry = commands = true;
        }
        Map<String, String> langs = new LinkedHashMap<>();
        if (languages != null) {
            languages.forEach((k, v) -> {
                if (k != null && v != null && langs.size() < MAX_PROJECTS) langs.put(k, v);
            });
        }
        return new DictionaryHistorySettingsData(
            language(emptyObjectLanguage, d.emptyObjectLanguage),
            language(emptyMetaLanguage, d.emptyMetaLanguage),
            entries, entry, commands, sizes(widths, d.widths), sizes(heights, d.heights), Map.copyOf(langs),
            changedOnly == null ? d.changedOnly : changedOnly);
    }

    private static String language(String value, String fallback) {
        return value == null || value.isBlank() || value.strip().contains(" ") ? fallback : value.strip();
    }

    private static List<Double> sizes(List<Double> values, List<Double> fallback) {
        if (values == null || values.size() != fallback.size() || values.stream().anyMatch(v -> v == null || v <= 0)) {
            return fallback;
        }
        double sum = values.stream().mapToDouble(Double::doubleValue).sum();
        return values.stream().map(v -> v / sum).toList();
    }

    /** The languages chosen for a project ("object meta"), or null. */
    public String languagesOf(String project) {
        return languages == null ? null : languages.get(project);
    }

    public DictionaryHistorySettingsData withLanguages(String project, String objectLanguage, String metaLanguage) {
        Map<String, String> m = new LinkedHashMap<>(languages == null ? Map.of() : languages);
        m.remove(project);
        m.put(project, objectLanguage + " " + metaLanguage);
        while (m.size() > MAX_PROJECTS) m.remove(m.keySet().iterator().next());
        return new DictionaryHistorySettingsData(emptyObjectLanguage, emptyMetaLanguage, showEntries, showEntry,
            showCommands, widths, heights, m, changedOnly);
    }

    public DictionaryHistorySettingsData withPanels(boolean entries, boolean entry, boolean commands,
                                                    List<Double> newWidths, List<Double> newHeights) {
        return new DictionaryHistorySettingsData(emptyObjectLanguage, emptyMetaLanguage, entries, entry, commands,
            newWidths, newHeights, languages, changedOnly);
    }

    public DictionaryHistorySettingsData withEmptyLanguages(String objectLanguage, String metaLanguage) {
        return new DictionaryHistorySettingsData(objectLanguage, metaLanguage, showEntries, showEntry, showCommands,
            widths, heights, languages, changedOnly);
    }

    public DictionaryHistorySettingsData withChangedOnly(boolean only) {
        return new DictionaryHistorySettingsData(emptyObjectLanguage, emptyMetaLanguage, showEntries, showEntry,
            showCommands, widths, heights, languages, only);
    }
}
