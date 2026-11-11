package fr.cnrs.lacito.matchbox.model.check;

import fr.cnrs.lacito.matchbox.corpus.Diagnostic;
import fr.cnrs.lacito.matchbox.corpus.Text;
import fr.cnrs.lacito.matchbox.corpus.capability.Level;
import fr.cnrs.lacito.matchbox.corpus.capability.LineRole;
import fr.cnrs.lacito.matchbox.corpus.capability.Morpheme;
import fr.cnrs.lacito.matchbox.corpus.capability.Word;
import fr.cnrs.lacito.matchbox.corpus.gloss.GlossParts;
import fr.cnrs.lacito.matchbox.corpus.igt.InterlinearReading;
import fr.cnrs.lacito.matchbox.corpus.igt.InterlinearReadings;
import fr.cnrs.lacito.matchbox.corpus.igt.LineReading;
import fr.cnrs.lacito.matchbox.model.lexicon.LexiconIndex;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The (form, lexical gloss) pairs of the IGTs against the context dictionary: for each column of the
 * lines compared ({@code form=} / {@code gloss=}, else the transcription and gloss of the morpheme
 * level whose languages the dictionary has), a warning when the form is not in the dictionary, or has
 * no sense with that gloss; fix: declare the pair ({@link Declarations}). Grammatical glosses
 * ({@code 1SG}, {@code -FEM}) are not looked up.
 */
public final class LexiconCheck implements CorpusCheck {

    public static final String ID = "Dictionary check";
    public static final String CODE_FORM = "gloss.form-unknown";
    public static final String CODE_GLOSS = "gloss.gloss-unknown";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<Diagnostic> check(Text text, CheckInputs inputs) {
        if (!inputs.settings().dictionary() || inputs.dictionary().isEmpty()) return List.of();
        LexiconIndex dictionary = inputs.dictionary().get();
        List<Diagnostic> result = new ArrayList<>();
        for (InterlinearReading r : InterlinearReadings.of(text).all()) {
            Optional<LineReading> form = r.formLine();
            Optional<LineReading> gloss = r.glossLine();
            if (!r.pointed() && form.isPresent()) {
                Level level = form.get().level().orElse(Level.WORD);
                form = preferred(r, LineRole.TRANSCRIPTION, level, dictionary.objectLanguages()).or(() -> r.formLine());
                gloss = preferred(r, LineRole.GLOSS, level, dictionary.metaLanguages()).or(() -> r.glossLine());
            }
            if (form.isEmpty() || gloss.isEmpty()) continue;
            check(text, r, form.get(), gloss.get(), dictionary, result);
        }
        return result;
    }

    private static Optional<LineReading> preferred(InterlinearReading r, LineRole role, Level level, java.util.Set<String> languages) {
        return r.lines().stream()
            .filter(l -> l.role() == role && l.level().orElse(Level.WORD) == level)
            .filter(l -> l.language().map(languages::contains).orElse(false))
            .findFirst();
    }

    private static void check(Text text, InterlinearReading r, LineReading formLine, LineReading glossLine,
                              LexiconIndex dictionary, List<Diagnostic> result) {
        List<Word> forms = formLine.words();
        List<Word> glosses = glossLine.words();
        int igtStart = r.igt().getTextCoordinate().startIndex();
        for (int w = 0; w < Math.min(forms.size(), glosses.size()); w++) {
            List<Morpheme> fm = forms.get(w).morphemes();
            List<Morpheme> gm = glosses.get(w).morphemes();
            for (int m = 0; m < Math.min(fm.size(), gm.size()); m++) {
                String lexical = GlossParts.lexicalGloss(gm.get(m).form());
                String form = fm.get(m).form();
                if (lexical.isEmpty() || form.isBlank()) continue;
                LexiconIndex.Lookup lookup = dictionary.lookup(form, formLine.language(), glossLine.language());
                String message;
                String code;
                if (lookup instanceof LexiconIndex.Lookup.FormUnknown) {
                    message = form + " / " + lexical + ": " + form + " is not in " + dictionary.name();
                    code = CODE_FORM;
                } else if (lookup instanceof LexiconIndex.Lookup.Known known && !known.hasGloss(lexical)) {
                    message = form + " / " + lexical + ": " + form + " is in " + dictionary.name()
                        + ", but no sense is glossed '" + lexical + "'"
                        + (known.written().isEmpty() ? "" : " (glosses: " + String.join(", ", known.written()) + ")");
                    code = CODE_GLOSS;
                } else {
                    continue;
                }
                Morpheme at = gm.get(m);
                Diagnostic d = CorpusCheck.warning(text, ID, at.span().startIndex(),
                    Math.max(at.span().startIndex(), at.span().stopIndex()), at.span().startLine(), message, code);
                if (!Declarations.declared(text.content(), igtStart, form, lexical)) {
                    d = d.withFixes(List.of(new Diagnostic.Fix("Declare " + form + " / " + lexical,
                        List.of(Declarations.insert(text.content(), igtStart, List.<String[]>of(new String[] {form, lexical}))),
                        "Declare " + form + " / " + lexical)));
                }
                result.add(d);
            }
        }
    }
}
