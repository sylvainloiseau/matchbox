package fr.cnrs.lacito.matchbox.grammar.markdownheading;

import fr.cnrs.lacito.matchbox.corpus.Field;
import fr.cnrs.lacito.matchbox.corpus.HasFields;
import fr.cnrs.lacito.matchbox.corpus.TextCoordinate;
import fr.cnrs.lacito.matchbox.corpus.capability.Section;
import fr.cnrs.lacito.matchbox.corpus.simple.AbstractCorpusObject;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A Markdown section: a heading (ATX {@code ## Title} or Setext {@code Title / ---}), its
 * metadata block, and everything up to the next heading of the same or a higher level.
 */
public final class MarkdownSection extends AbstractCorpusObject implements Section, HasFields {

    /** How the heading is written. */
    public enum Style { ATX, SETEXT }

    private final int level;
    private final Style style;
    private final String title;
    private final Map<String, String> metadata;
    private final List<MarkdownSection> content;
    private final TextCoordinate headingCoordinate;
    private final TextCoordinate metadataCoordinate;
    private final List<Field> fields;

    /**
     * @param headingCoordinate  the heading line(s), line endings and Setext underline included
     * @param metadataCoordinate the metadata block, delimiters included; {@code null} if none
     */
    public MarkdownSection(String kind, TextCoordinate coordinate, int level, Style style, String title,
                           Map<String, String> metadata, List<MarkdownSection> content,
                           TextCoordinate headingCoordinate, TextCoordinate metadataCoordinate, List<Field> fields) {
        super(kind, coordinate);
        this.level = level;
        this.style = Objects.requireNonNull(style, "style");
        this.title = title == null ? "" : title;
        this.metadata = Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
        this.content = List.copyOf(content);
        this.headingCoordinate = Objects.requireNonNull(headingCoordinate, "headingCoordinate");
        this.metadataCoordinate = metadataCoordinate;
        this.fields = List.copyOf(fields);
    }

    /** 1–6 for ATX headings (number of '#'), 1 ({@code ===}) or 2 ({@code ---}) for Setext ones. */
    public int getLevel() {
        return level;
    }

    public Style getStyle() {
        return style;
    }

    @Override
    public String getTitle() {
        return title;
    }

    @Override
    public Map<String, String> getMetadata() {
        return metadata;
    }

    /** Subsections, in text order (this grammar extracts nothing else). */
    @Override
    public List<MarkdownSection> getContent() {
        return content;
    }

    public TextCoordinate getHeadingCoordinate() {
        return headingCoordinate;
    }

    public Optional<TextCoordinate> getMetadataCoordinate() {
        return Optional.ofNullable(metadataCoordinate);
    }

    /** {@link Section#TITLE} and {@link Section#metadataField(String)} for each key. */
    @Override
    public List<Field> getFields() {
        return fields;
    }
}
