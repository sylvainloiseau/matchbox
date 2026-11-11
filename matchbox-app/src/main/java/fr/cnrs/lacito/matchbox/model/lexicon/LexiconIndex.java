package fr.cnrs.lacito.matchbox.model.lexicon;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * An immutable index of a {@link Lexicon} for the gloss check: forms normalised (NFC, lower case with
 * {@link Locale#ROOT}), glosses split on {@code ;} and {@code ,}, lower case, {@code _} and {@code .}
 * read as spaces. Thread-safe.
 */
public final class LexiconIndex {

    /** What the dictionary says of a form. */
    public sealed interface Lookup {
        /** The form is in no entry. */
        record FormUnknown() implements Lookup {}

        /** The form is known; the glosses of its entries (normalised and as written). */
        record Known(Set<String> glosses, List<String> written) implements Lookup {
            public boolean hasGloss(String gloss) {
                return glosses.contains(normaliseGloss(gloss));
            }
        }
    }

    private record Key(String language, String form) {}

    private final String name;
    private final Set<String> objectLanguages;
    private final Set<String> metaLanguages;
    private final int entries;
    /** form (any language: language "") → entries */
    private final Map<Key, List<LexiconEntry>> byForm = new HashMap<>();

    public LexiconIndex(Lexicon lexicon) {
        this.name = lexicon.name();
        this.objectLanguages = Set.copyOf(lexicon.objectLanguages());
        this.metaLanguages = Set.copyOf(lexicon.metaLanguages());
        this.entries = lexicon.entries().size();
        for (LexiconEntry e : lexicon.entries()) {
            e.forms().forEach((language, forms) -> {
                for (String f : forms) {
                    String form = normaliseForm(f);
                    if (form.isEmpty()) continue;
                    byForm.computeIfAbsent(new Key(language, form), k -> new ArrayList<>()).add(e);
                    byForm.computeIfAbsent(new Key("", form), k -> new ArrayList<>()).add(e);
                }
            });
        }
    }

    public String name() {
        return name;
    }

    public int size() {
        return entries;
    }

    public Set<String> objectLanguages() {
        return objectLanguages;
    }

    public Set<String> metaLanguages() {
        return metaLanguages;
    }

    /**
     * @param formLanguage  the language of the form, if known and in the dictionary (else any)
     * @param glossLanguage the language of the glosses, if known and in the dictionary (else any)
     */
    public Lookup lookup(String form, Optional<String> formLanguage, Optional<String> glossLanguage) {
        String key = normaliseForm(form);
        String language = formLanguage.filter(objectLanguages::contains).orElse("");
        List<LexiconEntry> found = byForm.getOrDefault(new Key(language, key), List.of());
        if (found.isEmpty()) return new Lookup.FormUnknown();
        Set<String> glosses = new LinkedHashSet<>();
        List<String> written = new ArrayList<>();
        Optional<String> gl = glossLanguage.filter(metaLanguages::contains);
        for (LexiconEntry e : found) {
            e.glosses().forEach((lang, values) -> {
                if (gl.isPresent() && !gl.get().equals(lang)) return;
                for (String v : values) {
                    for (String part : v.split("[;,]")) {
                        String g = normaliseGloss(part);
                        if (g.isEmpty()) continue;
                        if (glosses.add(g)) written.add(part.strip());
                    }
                }
            });
        }
        return new Lookup.Known(Set.copyOf(glosses), List.copyOf(written));
    }

    public static String normaliseForm(String form) {
        return Normalizer.normalize(form.strip(), Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
    }

    public static String normaliseGloss(String gloss) {
        return Normalizer.normalize(gloss, Normalizer.Form.NFC).replace('_', ' ').replace('.', ' ')
            .strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
