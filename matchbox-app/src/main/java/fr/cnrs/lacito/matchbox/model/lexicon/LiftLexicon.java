package fr.cnrs.lacito.matchbox.model.lexicon;

import fr.cnrs.lacito.liftapi.LiftDictionary;
import fr.cnrs.lacito.liftapi.model.Form;
import fr.cnrs.lacito.liftapi.model.LiftEntry;
import fr.cnrs.lacito.liftapi.model.LiftSense;
import fr.cnrs.lacito.liftapi.model.LiftVariant;
import fr.cnrs.lacito.liftapi.model.MultiText;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** A LIFT dictionary read with lift-api (the only class of Matchbox importing lift-api). */
public final class LiftLexicon implements Lexicon {

    private final String name;
    private final List<LexiconEntry> entries = new ArrayList<>();
    private final Set<String> objectLanguages = new LinkedHashSet<>();
    private final Set<String> metaLanguages = new LinkedHashSet<>();
    private final int loadDiagnostics;

    private LiftLexicon(Path file, LiftDictionary dictionary) {
        this(file.getFileName() == null ? file.toString() : file.getFileName().toString(), dictionary);
    }

    /**
     * The lexicon of a dictionary in memory (e.g. the context dictionary patched by the commands of
     * the project). Read now: later changes of the dictionary are not seen.
     */
    public static LiftLexicon of(String name, LiftDictionary dictionary) {
        return new LiftLexicon(name, dictionary);
    }

    private LiftLexicon(String name, LiftDictionary dictionary) {
        this.name = name;
        this.loadDiagnostics = dictionary.getLoadDiagnostics().size();
        for (LiftEntry e : dictionary.getLiftDictionaryRegistry().getEntries()) {
            Map<String, List<String>> forms = new HashMap<>();
            add(forms, e.getForms());
            for (LiftVariant v : e.getVariants()) add(forms, v.getForms());
            Map<String, List<String>> glosses = new HashMap<>();
            for (LiftSense s : e.getSenses()) senses(s, glosses);
            objectLanguages.addAll(forms.keySet());
            metaLanguages.addAll(glosses.keySet());
            entries.add(new LexiconEntry(e.getId().orElse(""), forms, glosses));
        }
    }

    /** Reads a LIFT file (on the calling thread: call it in the background). */
    public static LiftLexicon load(Path file) throws Exception {
        return new LiftLexicon(file, LiftDictionary.loadDictionaryFromFile(new File(file.toString())));
    }

    private static void senses(LiftSense sense, Map<String, List<String>> glosses) {
        add(glosses, sense.getGlosses());
        for (LiftSense sub : sense.getSenses()) senses(sub, glosses);
    }

    private static void add(Map<String, List<String>> map, MultiText text) {
        for (Form f : text.getForms()) {
            String value = f.toPlainText();
            if (value != null && !value.isBlank()) map.computeIfAbsent(f.getLang(), k -> new ArrayList<>()).add(value);
        }
    }

    /** Number of problems lift-api reported while reading (skipped or undeclared parts). */
    public int loadDiagnostics() {
        return loadDiagnostics;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Set<String> objectLanguages() {
        return objectLanguages;
    }

    @Override
    public Set<String> metaLanguages() {
        return metaLanguages;
    }

    @Override
    public List<LexiconEntry> entries() {
        return entries;
    }
}
