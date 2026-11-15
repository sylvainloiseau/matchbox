package fr.cnrs.lacito.matchbox.settings;

import java.util.List;
import java.util.function.Consumer;

/**
 * Settings of the Tag Browser mode, part of {@link SettingsData}. Immutable.
 *
 * @param contextBefore  lines shown before each tag (0–50)
 * @param contextAfter   lines shown after each tag (0–50)
 * @param sort           order of the tree: "name" or "count"
 * @param includeSubTags a node lists the tags of its whole subtree
 * @param showTree       the tag tree is shown
 * @param showOccurrences the occurrences are shown
 * @param widths         widths of the tree and of the occurrences, fractions summing to 1
 */
public record TagBrowserSettingsData(Integer contextBefore, Integer contextAfter, String sort, Boolean includeSubTags,
                                     Boolean showTree, Boolean showOccurrences, List<Double> widths) {

    public static final List<Double> DEFAULT_WIDTHS = List.of(0.25, 0.75);

    /** Settings without the layout (written before it existed): default layout. */
    public TagBrowserSettingsData(Integer contextBefore, Integer contextAfter, String sort, Boolean includeSubTags) {
        this(contextBefore, contextAfter, sort, includeSubTags, null, null, null);
    }

    public static final int MAX_CONTEXT = 50;
    public static final String SORT_NAME = "name";
    public static final String SORT_COUNT = "count";

    public static TagBrowserSettingsData defaults() {
        return new TagBrowserSettingsData(5, 5, SORT_NAME, false, true, true, DEFAULT_WIDTHS);
    }

    /** Copy where missing or invalid values are replaced by the defaults. */
    public TagBrowserSettingsData validated(Consumer<String> problems) {
        TagBrowserSettingsData d = defaults();
        boolean tree = showTree == null ? d.showTree : showTree;
        boolean occurrences = showOccurrences == null ? d.showOccurrences : showOccurrences;
        if (!tree && !occurrences) {
            problems.accept("tagBrowser: no panel shown, showing both");
            tree = occurrences = true;
        }
        return new TagBrowserSettingsData(
            lines(contextBefore, d.contextBefore, "tagBrowser.contextBefore", problems),
            lines(contextAfter, d.contextAfter, "tagBrowser.contextAfter", problems),
            SORT_NAME.equals(sort) || SORT_COUNT.equals(sort) ? sort : invalidSort(sort, d.sort, problems),
            includeSubTags == null ? d.includeSubTags : includeSubTags,
            tree, occurrences,
            FileModeSettingsData.sizes(widths, d.widths, "tagBrowser.widths", problems)
        );
    }

    private static int lines(Integer value, int fallback, String name, Consumer<String> problems) {
        if (value == null) return fallback;
        if (value < 0 || value > MAX_CONTEXT) {
            problems.accept("Invalid " + name + " " + value + ", using " + fallback);
            return fallback;
        }
        return value;
    }

    private static String invalidSort(String value, String fallback, Consumer<String> problems) {
        if (value != null) problems.accept("Invalid tagBrowser.sort '" + value + "', using " + fallback);
        return fallback;
    }

    public TagBrowserSettingsData withContext(int before, int after) {
        return new TagBrowserSettingsData(before, after, sort, includeSubTags, showTree, showOccurrences, widths);
    }

    public TagBrowserSettingsData withSort(String newSort) {
        return new TagBrowserSettingsData(contextBefore, contextAfter, newSort, includeSubTags, showTree, showOccurrences, widths);
    }

    public TagBrowserSettingsData withIncludeSubTags(boolean include) {
        return new TagBrowserSettingsData(contextBefore, contextAfter, sort, include, showTree, showOccurrences, widths);
    }

    public TagBrowserSettingsData withLayout(boolean tree, boolean occurrences, List<Double> newWidths) {
        return new TagBrowserSettingsData(contextBefore, contextAfter, sort, includeSubTags, tree, occurrences, newWidths);
    }
}
