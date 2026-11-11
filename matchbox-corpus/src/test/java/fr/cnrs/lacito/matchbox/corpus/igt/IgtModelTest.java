package fr.cnrs.lacito.matchbox.corpus.igt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.cnrs.lacito.matchbox.corpus.Text;
import fr.cnrs.lacito.matchbox.corpus.TextCoordinate;
import fr.cnrs.lacito.matchbox.corpus.TextId;
import fr.cnrs.lacito.matchbox.corpus.capability.Level;
import fr.cnrs.lacito.matchbox.corpus.capability.LineRole;
import fr.cnrs.lacito.matchbox.corpus.capability.Morpheme;
import fr.cnrs.lacito.matchbox.corpus.capability.TimeRange;
import fr.cnrs.lacito.matchbox.corpus.capability.Word;
import fr.cnrs.lacito.matchbox.corpus.capability.WordTiming;
import fr.cnrs.lacito.matchbox.corpus.gloss.GlossLabelList;
import fr.cnrs.lacito.matchbox.corpus.gloss.GlossLabels;
import fr.cnrs.lacito.matchbox.corpus.gloss.GlossParts;
import fr.cnrs.lacito.matchbox.corpus.simple.SimpleInterlinearGlossedText;
import fr.cnrs.lacito.matchbox.corpus.simple.SimpleSection;
import fr.cnrs.lacito.matchbox.corpus.simple.SimpleTextHeader;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Segmentation, setting, glosses, roles, alignment, inheritance and timing of IGTs. */
class IgtModelTest {

    private static final TextId T = new TextId(Path.of("notes.md"));

    static final String FRENCH = """
        :::{id="1.1", olang="fra", mlang="en", media="O1.WAV", offset="1000"}
        je crois pas qu'elle est assez curieuse
        j'crois pas qu'elle est assez curieuse
        1 90 300 420 610 700
        ʒə=kʁwa pa k=ɛl ɛ ase kyRjø-z
        1SG=believe not COMPL=3SG.F be\\3SG.PRES enough curious-FEM
        'I don't think she is curious enough'
        :::
        """;

    private static Text text(String content) {
        return Text.builder(T, content).add("igt", IgtFixture.igts(T, content), List.of()).build();
    }

    private static InterlinearReading only(String content) {
        Text t = text(content);
        return InterlinearReadings.of(t).all().iterator().next();
    }

    private static List<LineRole> roles(InterlinearReading r) {
        return r.lines().stream().map(LineReading::role).toList();
    }

    private static List<InterlinearIssue.Kind> kinds(InterlinearReading r) {
        return r.issues().stream().map(InterlinearIssue::kind).toList();
    }

    // ------------------------------------------------------------------ segmentation

    @Test
    void segmenter_splits_words_and_morphemes() {
        List<Word> words = Segmenter.words(T, "ʒə=kʁwa  kyRjø-z s<um>ulat ba~ba be\\3SG.PRES", 10, 1, Level.MORPHEME);
        assertEquals(5, words.size());
        assertEquals(List.of("ʒə", "kʁwa"), words.get(0).morphemes().stream().map(Morpheme::form).toList());
        assertEquals(Morpheme.Boundary.CLITIC, words.get(0).morphemes().get(1).boundary());
        assertEquals(10, words.get(0).span().startIndex());
        assertEquals(19, words.get(1).span().startIndex(), "two spaces");
        assertEquals(List.of("sulat", "um"), words.get(2).morphemes().stream().map(Morpheme::form).toList());
        assertEquals(Morpheme.Boundary.INFIX, words.get(2).morphemes().get(1).boundary());
        assertEquals(Morpheme.Boundary.REDUPLICATION, words.get(3).morphemes().get(1).boundary());
        assertEquals(1, words.get(4).morphemes().size(), "\\ and . are not boundaries");
        assertEquals(1, Segmenter.words(T, "peut-être", 0, 1, Level.WORD).get(0).morphemes().size());
        assertTrue(Segmenter.hasBoundary("a-b"));
        assertFalse(Segmenter.hasBoundary("je crois"));
    }

    @Test
    void trailing_tags_are_not_part_of_a_line() {
        assertEquals(6, Segmenter.contentEnd("'I go' #\"verb\" #\"todo\""));
        assertEquals(0, Segmenter.contentEnd("#\"to check\""));
        assertEquals(8, Segmenter.contentEnd("a #\"x\" b"), "a tag in the middle stays");
    }

    @Test
    void content_of_a_line() {
        assertEquals(fr.cnrs.lacito.matchbox.corpus.capability.InterlinearLine.Content.TRANSLATION, Segmenter.contentOf(" 'x y' "));
        assertEquals(fr.cnrs.lacito.matchbox.corpus.capability.InterlinearLine.Content.TRANSLATION, Segmenter.contentOf("‘x’"));
        assertEquals(fr.cnrs.lacito.matchbox.corpus.capability.InterlinearLine.Content.TIMELINE, Segmenter.contentOf("1 120-130 4"));
        assertEquals(fr.cnrs.lacito.matchbox.corpus.capability.InterlinearLine.Content.TEXT, Segmenter.contentOf("1SG go"));
    }

    // ------------------------------------------------------------------ setting

    @Test
    void setting_spec() {
        SettingSpec.Result r = SettingSpec.parse("o@fra,t'IPA'@fra:m,g'part of speech'@en");
        assertTrue(r.isValid(), r.toString());
        assertEquals(List.of(LineRole.ORTHOGRAPHIC, LineRole.TRANSCRIPTION, LineRole.GLOSS),
            r.entries().stream().map(SettingSpec.Entry::role).toList());
        assertEquals(Optional.of(Level.MORPHEME), r.entries().get(1).level());
        assertEquals(Optional.of("part of speech"), r.entries().get(2).name());
        assertEquals(Optional.of("en"), r.entries().get(2).language());
        assertEquals("o@fra,t:m'IPA'@fra,g'part of speech'@en", SettingSpec.write(r.entries()));
        assertTrue(SettingSpec.parse("t@tww,g'morphem'@en,g'part of speech'@en").isValid());
        assertEquals(Optional.of("setting, column 3: 'x' is not o, t or g"), SettingSpec.parse("t,x").error());
        assertFalse(SettingSpec.parse("o:w").isValid());
    }

    // ------------------------------------------------------------------ glosses

    @Test
    void gloss_parts_and_labels() {
        assertEquals("be", GlossParts.lexicalGloss("be\\3SG.PRES"));
        assertEquals(List.of("3SG", "PRES"), GlossParts.grammatical("be\\3SG.PRES"));
        assertEquals("come out", GlossParts.lexicalGloss("come.out"));
        assertEquals("go", GlossParts.lexicalGloss("go.PST"));
        assertEquals("go out", GlossParts.lexicalGloss("go_out"));
        assertEquals(GlossParts.Kind.NON_OVERT, GlossParts.of("[3SG]").get(0).kind());
        assertTrue(GlossParts.lexicalGloss("1SG").isEmpty());
        assertTrue(GlossLabels.isLabel("3SG", GlossLabelList.LEIPZIG, Set.of()));
        assertTrue(GlossLabels.isLabel("NPST", GlossLabelList.LEIPZIG, Set.of()));
        assertTrue(GlossLabels.isLabel("CL7", GlossLabelList.LEIPZIG, Set.of()));
        assertTrue(GlossLabels.isLabel("COMPL", GlossLabelList.LEIPZIG, Set.of()));
        assertFalse(GlossLabels.isLabel("COMPL2", GlossLabelList.LEIPZIG, Set.of()));
        assertTrue(GlossLabels.isLabel("COMPL2", GlossLabelList.LEIPZIG, Set.of("COMPL2")));
        assertFalse(GlossLabels.isLabel("APPRH", GlossLabelList.LEIPZIG, Set.of()));
        assertTrue(GlossLabels.isLabel("APPRH", GlossLabelList.LEHMANN, Set.of()));
        assertEquals(Optional.of("completive"), GlossLabels.meaning("COMPL"));
        assertEquals(84, GlossLabels.leipzig().size() + 1, "84 labels, N- excluded");
    }

    // ------------------------------------------------------------------ roles and alignment

    @Test
    void the_french_example_is_read_without_issue() {
        InterlinearReading r = only(FRENCH);
        assertEquals(List.of(LineRole.ORTHOGRAPHIC, LineRole.TRANSCRIPTION, LineRole.TIMELINE, LineRole.TRANSCRIPTION,
            LineRole.GLOSS, LineRole.TRANSLATION), roles(r));
        assertEquals(Optional.of(Level.WORD), r.lines().get(1).level());
        assertEquals(Optional.of(Level.MORPHEME), r.lines().get(3).level());
        assertTrue(r.lines().get(0).reason().contains("7 words"), r.lines().get(0).reason());
        assertEquals(List.of(), r.issues());
        assertEquals("ʒə=kʁwa pa k=ɛl ɛ ase kyRjø-z", r.transcription());
        assertEquals("I don't think she is curious enough", r.translation());
        assertEquals(Optional.of("fra"), r.lines().get(0).language());
        assertEquals(Optional.of("en"), r.lines().get(4).language());
        assertEquals(3, r.formLine().orElseThrow().index());
        assertEquals(4, r.glossLine().orElseThrow().index());
        assertFalse(r.pointed());
    }

    @Test
    void the_old_notebook_form_and_two_lines() {
        InterlinearReading r = only(":::\na mio-jaflo-m-wi-j\n1sg down-canoe-top-m.sg-pred\n'I went down in canoe'\n:::\n");
        assertEquals(List.of(LineRole.TRANSCRIPTION, LineRole.GLOSS, LineRole.TRANSLATION), roles(r));
        assertEquals(List.of(), r.issues());
        InterlinearReading word = only(":::\nnarrow transcription\nnarrow transcription\n:::\n");
        assertEquals(List.of(LineRole.TRANSCRIPTION, LineRole.GLOSS), roles(word));
        assertEquals(List.of(LineRole.TRANSCRIPTION), roles(only(":::\nhello\n:::\n")));
        assertEquals(List.of(LineRole.TRANSLATION), roles(only(":::\n'hello'\n:::\n")));
    }

    @Test
    void morpheme_and_word_counts_are_reported_on_the_odd_line() {
        String igt = ":::\nʒə=kʁwa pa kyRjø-z\nʒə=kʁwa pa kyRjø-z\n1SG=believe not curious\n:::\n";
        InterlinearReading r = only(igt);
        assertEquals(List.of(InterlinearIssue.Kind.MORPHEME_COUNT), kinds(r));
        assertEquals("Word 3 has 1 morpheme, 2 expected (kyRjø-z)", r.issues().get(0).message());
        assertEquals(2, r.issues().get(0).line());

        InterlinearReading words = only(":::\nʒə=kʁwa pa ɛ\n1SG=believe not be\n1SG=believe not\n:::\n");
        assertEquals(List.of(InterlinearIssue.Kind.WORD_COUNT), kinds(words));
        assertEquals(2, words.issues().get(0).line());

        InterlinearReading boundary = only(":::\nʒə=kʁwa pa\n1SG-believe not\n:::\n");
        assertEquals(List.of(InterlinearIssue.Kind.BOUNDARY_MISMATCH), kinds(boundary));
    }

    @Test
    void word_counts_must_coincide_between_levels() {
        InterlinearReading r = only(":::\nʃe ʒə=kʁwa pa\nwell 1SG=believe not\nwell think not now\n:::\n");
        // the word-level line has 4 words, the morpheme level 3
        assertTrue(kinds(r).contains(InterlinearIssue.Kind.WORD_COUNT), r.issues().toString());
    }

    @Test
    void lines_out_of_order_are_read_and_reported() {
        InterlinearReading r = only(":::\nʒə=kʁwa pa\n1SG=believe not\n'I do not'\n1 120\n:::\n");
        assertEquals(LineRole.TIMELINE, r.lines().get(3).role());
        assertEquals(List.of(InterlinearIssue.Kind.ROLE_ORDER), kinds(r));
    }

    @Test
    void the_setting_declares_roles_including_orthographic_lines() {
        InterlinearReading r = only(":::{setting=\"o@fra,t@fra,g@en\"}\nje crois pas\nʒə=kʁwa pa\n1SG=believe not\n:::\n");
        assertEquals(List.of(LineRole.ORTHOGRAPHIC, LineRole.TRANSCRIPTION, LineRole.GLOSS), roles(r));
        assertTrue(r.lines().get(0).declared());
        InterlinearReading mismatch = only(":::{setting=\"t,g,g\"}\nʒə=kʁwa pa\n1SG=believe not\n:::\n");
        assertEquals(List.of(InterlinearIssue.Kind.SETTING), kinds(mismatch));
        assertEquals(List.of(LineRole.TRANSCRIPTION, LineRole.GLOSS), roles(mismatch), "guessed");
    }

    @Test
    void a_leading_line_with_the_same_count_or_ipa_is_not_orthographic() {
        assertEquals(LineRole.TRANSCRIPTION, only(":::\nje crois\nʒə=kʁwa pa\n1SG=believe not\n:::\n").lines().get(0).role());
        assertEquals(LineRole.TRANSCRIPTION,
            only(":::\nʒə kʁwa pa ɛl\nʒə=kʁwa pa\n1SG=believe not\n:::\n").lines().get(0).role());
    }

    @Test
    void pointers_choose_the_compared_lines() {
        InterlinearReading r = only(":::{setting=\"t'IPA',g'gl'\", form=\"IPA\", gloss=\"2\"}\nʒə=kʁwa pa\n1SG=believe not\n:::\n");
        assertTrue(r.pointed());
        assertEquals(0, r.formLine().orElseThrow().index());
        InterlinearReading bad = only(":::{form=\"2\"}\nʒə=kʁwa pa\n1SG=believe not\n:::\n");
        assertEquals(List.of(InterlinearIssue.Kind.POINTER), kinds(bad));
    }

    // ------------------------------------------------------------------ context

    @Test
    void inheritance_is_nearest_first() {
        String content = "---\nh\n---\n" // lines 1-3: the header, offsets 0..9
            + ":::{olang=\"tww\", id=\"1\"}\nʒə=kʁwa pa\n1SG=believe not\n:::\n"
            + ":::{id=\"2\"}\nʒə=kʁwa pa\n1SG=believe not\n:::\n";
        List<SimpleInterlinearGlossedText> igts = IgtFixture.igts(T, content);
        SimpleTextHeader header = new SimpleTextHeader("md:header", new TextCoordinate(T, 0, 9, 1, 3),
            Map.of("olang", "fra", "mlang", "en", "offset", "5"));
        int s = content.indexOf(":::{id");
        SimpleSection section = new SimpleSection("md:section", new TextCoordinate(T, s, content.length() - 1, 8, 12),
            "S", Map.of("mlang", "de"), List.of());
        Text t = Text.builder(T, content).add("md", List.of(header, section), List.of()).add("igt", igts, List.of()).build();
        InterlinearReadings readings = InterlinearReadings.of(t);
        assertSameReadings(readings, t);
        InterlinearReading first = readings.of(igts.get(0)).orElseThrow();
        InterlinearReading second = readings.of(igts.get(1)).orElseThrow();
        assertEquals(MetadataValue.Source.OWN, first.metadata().get("olang").source());
        assertEquals(MetadataValue.Source.HEADER, first.metadata().get("mlang").source());
        assertEquals("tww", second.value("olang").orElseThrow());
        assertEquals(MetadataValue.Source.PREVIOUS_IGT, second.metadata().get("olang").source());
        assertEquals("de", second.value("mlang").orElseThrow());
        assertEquals(MetadataValue.Source.SECTION, second.metadata().get("mlang").source());
        assertTrue(second.value("id").orElseThrow().equals("2"));
        assertTrue(first.value("offset").isEmpty(), "offset is not inherited");
    }

    private static void assertSameReadings(InterlinearReadings readings, Text t) {
        assertTrue(readings == InterlinearReadings.of(t), "cached");
    }

    @Test
    void timing_with_one_or_two_timelines_and_the_next_igt() {
        String content = ":::{offset=\"1000\", end=\"1200\"}\nnarrow transcription\nnarrow transcription\n1 120\n:::\n"
            + ":::{offset=\"1000\"}\nnarrow transcription\nnarrow transcription\n1 120\n118 131\n:::\n"
            + ":::\nnarrow transcription\nnarrow transcription\n1 120\n:::\n";
        Text t = text(content);
        List<InterlinearReading> rs = InterlinearReadings.of(t).all().stream()
            .sorted(java.util.Comparator.comparingInt(r -> r.igt().getTextCoordinate().startIndex())).toList();
        List<WordTiming> one = rs.get(0).timing(Level.WORD).orElseThrow();
        assertEquals(Optional.of(new TimeRange(1001, 1120)), one.get(0).range());
        assertEquals(Optional.of(new TimeRange(1120, 1200)), one.get(1).range(), "end key");
        List<WordTiming> two = rs.get(1).timing(Level.WORD).orElseThrow();
        assertEquals(Optional.of(new TimeRange(1001, 1118)), two.get(0).range());
        assertEquals(Optional.of(new TimeRange(1120, 1131)), two.get(1).range());
        List<WordTiming> last = rs.get(2).timing(Level.WORD).orElseThrow();
        assertTrue(last.get(1).range().isEmpty(), "no end, no next IGT");
        assertEquals(List.of(), rs.get(0).issues());
    }
}
