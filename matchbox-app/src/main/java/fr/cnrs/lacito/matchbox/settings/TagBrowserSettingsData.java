package fr.cnrs.lacito.matchbox.settings;

import java.util.function.Consumer;

/**
 * Settings of the Tag Browser mode, part of {@link SettingsData}. Immutable.
 *
 * @param contextBefore  lines shown before each tag (0–50)
 * @param contextAfter   lines shown after each tag (0–50)
 * @param sort           order of the tree: "name" or "count"
 * @param includeSubTags a node lists the tags of its whole subtree
 */
public record TagBrowserSettingsData(Integer contextBefore, Integer contextAfter, String sort, Boolean includeSubTags) {

    public static final int MAX_CONTEXT = 50;
    public static final String SORT_NAME = "name";
    public static final String SORT_COUNT = "count";

    public static TagBrowserSettingsData defaults() {
        return new TagBrowserSettingsData(5, 5, SORT_NAME, false);
    }

    /** Copy where missing or invalid values are replaced by the defaults. */
    public TagBrowserSettingsData validated(Consumer<String> problems) {
        TagBrowserSettingsData d = defaults();
        return new TagBrowserSettingsData(
            lines(contextBefore, d.contextBefore, "tagBrowser.contextBefore", problems),
            lines(contextAfter, d.contextAfter, "tagBrowser.contextAfter", problems),
            SORT_NAME.equals(sort) || SORT_COUNT.equals(sort) ? sort : invalidSort(sort, d.sort, problems),
            includeSubTags == null ? d.includeSubTags : includeSubTags
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
        return new TagBrowserSettingsData(before, after, sort, includeSubTags);
    }

    public TagBrowserSettingsData withSort(String newSort) {
        return new TagBrowserSettingsData(contextBefore, contextAfter, newSort, includeSubTags);
    }

    public TagBrowserSettingsData withIncludeSubTags(boolean include) {
        return new TagBrowserSettingsData(contextBefore, contextAfter, sort, include);
    }
}
