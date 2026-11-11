package fr.cnrs.lacito.matchbox.corpus.igt;

import fr.cnrs.lacito.matchbox.corpus.capability.InterlinearLine;
import fr.cnrs.lacito.matchbox.corpus.capability.Level;
import fr.cnrs.lacito.matchbox.corpus.capability.LineRole;
import fr.cnrs.lacito.matchbox.corpus.capability.Morpheme;
import fr.cnrs.lacito.matchbox.corpus.capability.Word;
import fr.cnrs.lacito.matchbox.corpus.gloss.GlossLabelList;
import fr.cnrs.lacito.matchbox.corpus.gloss.GlossLabels;
import fr.cnrs.lacito.matchbox.corpus.gloss.GlossParts;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reads the role of each line of an IGT:
 * <ol>
 *   <li>translations (between quotes) and timelines (digits only) by their characters;</li>
 *   <li>the text lines by the {@code setting}, when it has one entry per text line;</li>
 *   <li>otherwise: the leading text lines without IPA nor non-Latin letters whose word count differs
 *       from the count shared by the segmented lines after them are orthographic lines; the other
 *       text lines get their level from their characters and, per level, are cut into transcription
 *       lines above and gloss lines below, the cut agreeing best with the evidence (IPA or non-Latin
 *       letters: transcription; known grammatical labels: gloss); without evidence the last line of a
 *       level is its gloss line; two text lines in all are a transcription and a gloss.</li>
 * </ol>
 * Pure.
 */
public final class LineClassifier {

    /** IPA letters and symbols outside basic Latin (a selection: enough to recognise a transcription). */
    private static final String IPA = "ɐɑɒæɓʙβɔɕçɗɖðʤəɘɚɛɜɝɞɟʄɡɠɢʛɦɧħɥʜɨɪʝɭɬɫɮʟɱɯɰŋɳɲɴøɵɸœɶɾʀʁɽʂʃʈʧʉʊʋⱱʌɣɤʍχʎʏʑʐʒʔʡʕʢǀǁǂǃˈˌːˑʰʲʷˠˤ̩̯̪̥̬̃θ";
    private static final Pattern LOWER_PERSON = Pattern.compile("[123](sg|du|pl|exc|inc)");

    /** The roles of the lines, and the problems of the setting. */
    public record Classification(List<LineReading> lines, List<InterlinearIssue> issues) {}

    private LineClassifier() {}

    /**
     * @param setting the effective {@code setting} value, if any
     * @param olang   default language of orthographic and transcription lines (may be empty)
     * @param mlang   default language of glosses and translations (may be empty)
     */
    public static Classification classify(List<InterlinearLine> lines, Optional<MetadataValue> setting,
                                          Optional<String> olang, Optional<String> mlang) {
        List<InterlinearIssue> issues = new ArrayList<>();
        List<Integer> text = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).content() == InterlinearLine.Content.TEXT) text.add(i);
        }
        Map<Integer, LineReading> readings = new HashMap<>();
        for (int i = 0; i < lines.size(); i++) {
            InterlinearLine l = lines.get(i);
            if (l.content() == InterlinearLine.Content.TRANSLATION) {
                readings.put(i, new LineReading(l, i, LineRole.TRANSLATION, Optional.empty(), Optional.empty(),
                    mlang, false, "between quotes"));
            } else if (l.content() == InterlinearLine.Content.TIMELINE) {
                readings.put(i, new LineReading(l, i, LineRole.TIMELINE, l.segmentation(), Optional.empty(),
                    Optional.empty(), false, "only numbers"));
            }
        }

        boolean declared = false;
        if (setting.isPresent()) {
            SettingSpec.Result spec = SettingSpec.parse(setting.get().value());
            String from = setting.get().isInherited() ? " (inherited)" : "";
            if (!spec.isValid()) {
                issues.add(InterlinearIssue.warning(InterlinearIssue.Kind.SETTING, spec.error().get() + from,
                    lines.isEmpty() ? null : lines.get(0).getTextCoordinate(), -1, -1));
            } else if (spec.entries().size() != text.size()) {
                issues.add(InterlinearIssue.warning(InterlinearIssue.Kind.SETTING,
                    "The setting" + from + " declares " + spec.entries().size() + " line"
                        + (spec.entries().size() == 1 ? "" : "s") + ", this IGT has " + text.size()
                        + " text line" + (text.size() == 1 ? "" : "s") + ": the roles are guessed",
                    lines.isEmpty() ? null : lines.get(0).getTextCoordinate(), -1, -1));
            } else {
                declared = true;
                for (int k = 0; k < text.size(); k++) {
                    int i = text.get(k);
                    SettingSpec.Entry e = spec.entries().get(k);
                    InterlinearLine l = lines.get(i);
                    Optional<Level> level = e.role() == LineRole.ORTHOGRAPHIC ? Optional.empty()
                        : Optional.of(e.level().orElse(l.segmentation().orElse(Level.WORD)));
                    Optional<String> lang = e.language().or(() -> e.role() == LineRole.GLOSS ? mlang : olang);
                    readings.put(i, new LineReading(l, i, e.role(), level, e.name(), lang, true,
                        "declared in the setting" + from));
                }
            }
        }
        if (!declared) guess(lines, text, olang, mlang, readings);

        List<LineReading> result = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) result.add(readings.get(i));
        return new Classification(List.copyOf(result), List.copyOf(issues));
    }

    private static void guess(List<InterlinearLine> lines, List<Integer> text, Optional<String> olang,
                              Optional<String> mlang, Map<Integer, LineReading> readings) {
        // orthographic lines: leading text lines whose word count differs from the lines after them
        List<Integer> rest = new ArrayList<>(text);
        int firstNonText = 0;
        while (firstNonText < lines.size() && lines.get(firstNonText).content() == InterlinearLine.Content.TEXT) {
            firstNonText++;
        }
        while (!rest.isEmpty() && rest.get(0) < firstNonText) {
            int i = rest.get(0);
            InterlinearLine l = lines.get(i);
            if (transcriptionEvidence(l.text()) > 0) break;
            Optional<Integer> shared = sharedCount(lines, i);
            int count = l.words().size();
            if (shared.isEmpty() || shared.get() == count) break;
            readings.put(i, new LineReading(l, i, LineRole.ORTHOGRAPHIC, Optional.empty(), Optional.empty(), olang,
                false, "guessed orthographic: " + count + " words, the other lines have " + shared.get()));
            rest.remove(0);
        }
        if (rest.size() == 2) {
            put(lines, rest.get(0), LineRole.TRANSCRIPTION, olang, "two text lines: the first is the transcription", readings);
            put(lines, rest.get(1), LineRole.GLOSS, mlang, "two text lines: the second is the gloss", readings);
            return;
        }
        for (Level level : Level.values()) {
            List<Integer> ofLevel = rest.stream()
                .filter(i -> lines.get(i).segmentation().orElse(Level.WORD) == level).toList();
            int n = ofLevel.size();
            if (n == 0) continue;
            int[] t = new int[n];
            int[] g = new int[n];
            for (int k = 0; k < n; k++) {
                InterlinearLine l = lines.get(ofLevel.get(k));
                t[k] = transcriptionEvidence(l.text());
                g[k] = glossEvidence(l.words());
            }
            int defaultCut = n == 1 ? 1 : n - 1;
            int best = defaultCut;
            int bestScore = Integer.MIN_VALUE;
            for (int cut = 0; cut <= n; cut++) {
                int score = 0;
                for (int k = 0; k < n; k++) score += k < cut ? t[k] - g[k] : g[k] - t[k];
                if (score > bestScore || (score == bestScore && Math.abs(cut - defaultCut) < Math.abs(best - defaultCut))) {
                    best = cut;
                    bestScore = score;
                }
            }
            for (int k = 0; k < n; k++) {
                int i = ofLevel.get(k);
                boolean transcription = k < best;
                String reason = transcription
                    ? (t[k] > 0 ? "guessed transcription: contains IPA or non-Latin letters" : "guessed transcription: above the glosses")
                    : (g[k] > 0 ? "guessed gloss: contains grammatical labels" : "guessed gloss: the last line of its level");
                put(lines, i, transcription ? LineRole.TRANSCRIPTION : LineRole.GLOSS,
                    transcription ? olang : mlang, reason, readings);
            }
        }
    }

    private static void put(List<InterlinearLine> lines, int i, LineRole role, Optional<String> lang, String reason,
                            Map<Integer, LineReading> readings) {
        InterlinearLine l = lines.get(i);
        readings.put(i, new LineReading(l, i, role, Optional.of(l.segmentation().orElse(Level.WORD)), Optional.empty(),
            lang, false, reason));
    }

    /** The word count most of the segmented lines after {@code i} have (ties: the later count). */
    private static Optional<Integer> sharedCount(List<InterlinearLine> lines, int i) {
        Map<Integer, Integer> counts = new HashMap<>();
        for (int j = i + 1; j < lines.size(); j++) {
            InterlinearLine l = lines.get(j);
            if (l.content() == InterlinearLine.Content.TRANSLATION) continue;
            counts.merge(l.words().size(), 1, Integer::sum);
        }
        return counts.entrySet().stream()
            .max(Map.Entry.<Integer, Integer>comparingByValue().thenComparing(Map.Entry.comparingByKey()))
            .map(Map.Entry::getKey);
    }

    /** IPA symbols, combining diacritics, letters of another script than Latin. */
    static int transcriptionEvidence(String text) {
        String nfc = Normalizer.normalize(text, Normalizer.Form.NFC);
        int n = 0;
        for (int i = 0; i < nfc.length(); ) {
            int cp = nfc.codePointAt(i);
            i += Character.charCount(cp);
            if (IPA.indexOf(cp) >= 0 || (cp >= 0x0300 && cp <= 0x036F)) {
                n++;
            } else if (Character.isLetter(cp)) {
                Character.UnicodeScript script = Character.UnicodeScript.of(cp);
                if (script != Character.UnicodeScript.LATIN && script != Character.UnicodeScript.COMMON
                    && script != Character.UnicodeScript.INHERITED) {
                    n++;
                }
            }
        }
        return n;
    }

    /** Known grammatical labels ({@code 1SG}, {@code COMPL}, {@code 3SG.F}; {@code 1sg}, {@code m.sg} in lower case). */
    static int glossEvidence(List<Word> words) {
        int n = 0;
        for (Word w : words) {
            for (Morpheme m : w.morphemes()) {
                List<GlossParts.Part> parts = GlossParts.of(m.form());
                for (GlossParts.Part p : parts) {
                    String text = p.text();
                    if (p.kind() == GlossParts.Kind.GRAMMATICAL) {
                        if ((text.length() > 1 || parts.size() > 1)
                            && GlossLabels.isLabel(text, GlossLabelList.BOTH, Set.of())) {
                            n++;
                        }
                    } else if (p.kind() == GlossParts.Kind.LEXICAL) {
                        if (LOWER_PERSON.matcher(text).matches()
                            || (parts.size() > 1 && GlossLabels.isLabel(text.toUpperCase(Locale.ROOT), GlossLabelList.LEIPZIG, Set.of())
                                && text.length() <= 4)) {
                            n++;
                        }
                    }
                }
            }
        }
        return n;
    }
}
