package fr.cnrs.lacito.matchbox.model.project;

import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

import fr.cnrs.lacito.matchboxgrammarapi.GrammarPlugin;

/**
 * Model class representing a file type pattern with associated grammars
 */
public class FileType {

    private String name;
    private String regex;
    private List<GrammarPlugin> grammars;
    private Pattern pattern;

    public FileType() {
        this.grammars = new ArrayList<>();
    }

    public FileType(String name, String regex) {
        this();
        setName(name);
        setRegex(regex);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRegex() {
        return regex;
    }

    public void setRegex(String regex) {
        this.regex = regex;
        try {
            this.pattern = Pattern.compile(regex);
        } catch (java.util.regex.PatternSyntaxException e) {
            e.printStackTrace();
        }
    }

    public List<GrammarPlugin> getGrammars() {
        return grammars;
    }

    public void addGrammar(GrammarPlugin grammar) {
        grammars.add(grammar);
    }

    public void removeGrammar(GrammarPlugin grammar) {
        grammars.remove(grammar);
    }

    public void moveGrammar(int fromIndex, int toIndex) {
        if (
            fromIndex >= 0 &&
            fromIndex < grammars.size() &&
            toIndex >= 0 &&
            toIndex < grammars.size()
        ) {
            GrammarPlugin grammar = grammars.remove(fromIndex);
            grammars.add(toIndex, grammar);
        }
    }

    protected void synchronize() {
        
    }

    public boolean matches(Path filePath) {
        return pattern.matcher(filePath.getFileName().toString()).matches();
    }

}
