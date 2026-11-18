package fr.cnrs.lacito.matchbox.gui.properties;

import fr.cnrs.lacito.matchbox.model.project.ProjectEdit.Topic;
import java.util.Set;

/** The tabs of the Project properties dialog, in order; messages and links open the dialog on one of them. */
public enum ProjectPropertiesPage {
    GENERAL("General", "Name and description of the project", Set.of(Topic.GENERAL)),
    FILE_TYPES("File types", "File types and the plugins applied to them", Set.of(Topic.FILE_TYPES)),
    PLUGINS("Plugins", "Display of the objects of each plugin", Set.of(Topic.DISPLAY)),
    CORPUS("Corpus", "Inherited context, order of the files and gloss check", Set.of(Topic.CORPUS)),
    ASSISTANT("AI assistant", "Which AI providers may be used for this project", Set.of(Topic.ASSISTANT));

    private final String title;
    private final String heading;
    private final Set<Topic> topics;

    ProjectPropertiesPage(String title, String heading, Set<Topic> topics) {
        this.title = title;
        this.heading = heading;
        this.topics = topics;
    }

    /** Title of the tab. */
    public String title() {
        return title;
    }

    /** Heading at the top of the tab, also its tooltip. */
    public String heading() {
        return heading;
    }

    /** The parts of the project definition edited in the tab. */
    public Set<Topic> topics() {
        return topics;
    }

    /** Id of the tab, for lookups: {@code tab-general}, {@code tab-fileTypes}… */
    public String nodeId() {
        String[] words = name().toLowerCase(java.util.Locale.ROOT).split("_");
        StringBuilder b = new StringBuilder("tab-").append(words[0]);
        for (int i = 1; i < words.length; i++) {
            b.append(Character.toUpperCase(words[i].charAt(0))).append(words[i].substring(1));
        }
        return b.toString();
    }

    /** The tab where a topic is edited. */
    public static ProjectPropertiesPage of(Topic topic) {
        for (ProjectPropertiesPage p : values()) {
            if (p.topics.contains(topic)) return p;
        }
        throw new IllegalArgumentException(topic.toString());
    }
}
