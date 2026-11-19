package fr.cnrs.lacito.matchbox.settings;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Settings of the Metadata mode, part of {@link SettingsData}. Immutable. The filters are not kept
 * (a filter restored at launch would hide rows for no visible reason).
 *
 * @param perProject          the columns of each project, by project file; at most {@link #MAX_PROJECTS}
 * @param showWithoutMetadata rows of the objects without metadata block are shown
 * @param columnOrder         FIRST_APPEARANCE, FREQUENCY or ALPHABETIC, for the columns the user has not moved
 * @param showInherited       empty cells show the inherited values
 * @param groupBy             NONE, FILE or TYPE
 */
public record MetadataModeSettingsData(Map<String, ProjectColumns> perProject, Boolean showWithoutMetadata,
                                       String columnOrder, Boolean showInherited, String groupBy) {

    public static final int MAX_PROJECTS = 50;
    public static final Set<String> ORDERS = Set.of("FIRST_APPEARANCE", "FREQUENCY", "ALPHABETIC");
    public static final Set<String> GROUPS = Set.of("NONE", "FILE", "TYPE");

    /**
     * The columns of a project.
     *
     * @param order  the keys in the order the user gave (moved columns)
     * @param hidden the hidden keys
     * @param widths the widths by column id
     * @param sort   the sort, e.g. {@code ["speaker:asc", "date:desc"]}
     */
    public record ProjectColumns(List<String> order, List<String> hidden, Map<String, Double> widths, List<String> sort) {

        public static final ProjectColumns EMPTY = new ProjectColumns(List.of(), List.of(), Map.of(), List.of());

        public ProjectColumns validated() {
            Map<String, Double> w = new LinkedHashMap<>();
            if (widths != null) widths.forEach((k, v) -> {
                if (k != null && v != null && v > 10 && v < 5000) w.put(k, v);
            });
            return new ProjectColumns(clean(order), clean(hidden), Map.copyOf(w),
                sort == null ? List.of() : sort.stream().filter(s -> s != null && (s.endsWith(":asc") || s.endsWith(":desc"))).toList());
        }

        private static List<String> clean(List<String> values) {
            return values == null ? List.of() : values.stream().filter(v -> v != null && !v.isEmpty()).distinct().toList();
        }
    }

    public static MetadataModeSettingsData defaults() {
        return new MetadataModeSettingsData(Map.of(), true, "FIRST_APPEARANCE", false, "NONE");
    }

    public MetadataModeSettingsData validated(Consumer<String> problems) {
        MetadataModeSettingsData d = defaults();
        Map<String, ProjectColumns> projects = new LinkedHashMap<>();
        if (perProject != null) perProject.forEach((k, v) -> {
            if (k != null && v != null && projects.size() < MAX_PROJECTS) projects.put(k, v.validated());
        });
        String order = columnOrder;
        if (order != null && !ORDERS.contains(order)) {
            problems.accept("metadataMode: unknown column order '" + order + "'");
            order = null;
        }
        String group = groupBy;
        if (group != null && !GROUPS.contains(group)) {
            problems.accept("metadataMode: unknown grouping '" + group + "'");
            group = null;
        }
        return new MetadataModeSettingsData(Map.copyOf(projects),
            showWithoutMetadata == null ? d.showWithoutMetadata : showWithoutMetadata,
            order == null ? d.columnOrder : order,
            showInherited == null ? d.showInherited : showInherited,
            group == null ? d.groupBy : group);
    }

    public ProjectColumns columnsOf(String project) {
        return perProject == null ? ProjectColumns.EMPTY : perProject.getOrDefault(project, ProjectColumns.EMPTY);
    }

    public MetadataModeSettingsData withColumns(String project, ProjectColumns columns) {
        Map<String, ProjectColumns> m = new LinkedHashMap<>(perProject == null ? Map.of() : perProject);
        m.remove(project);
        m.put(project, columns);
        while (m.size() > MAX_PROJECTS) m.remove(m.keySet().iterator().next());
        return new MetadataModeSettingsData(Map.copyOf(m), showWithoutMetadata, columnOrder, showInherited, groupBy);
    }

    public MetadataModeSettingsData withShowWithoutMetadata(boolean v) {
        return new MetadataModeSettingsData(perProject, v, columnOrder, showInherited, groupBy);
    }

    public MetadataModeSettingsData withColumnOrder(String v) {
        return new MetadataModeSettingsData(perProject, showWithoutMetadata, v, showInherited, groupBy);
    }

    public MetadataModeSettingsData withShowInherited(boolean v) {
        return new MetadataModeSettingsData(perProject, showWithoutMetadata, columnOrder, v, groupBy);
    }

    public MetadataModeSettingsData withGroupBy(String v) {
        return new MetadataModeSettingsData(perProject, showWithoutMetadata, columnOrder, showInherited, v);
    }
}
