package fr.cnrs.lacito.matchbox.model.project;

import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import fr.cnrs.lacito.matchbox.grammarapi.GrammarPlugin;

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

    /**
     * Sets the regular expression matched against file names.
     *
     * @throws IllegalArgumentException if it is not a valid regular expression; the previous
     *                                  value is kept, and the message can be shown to the user
     */
    public void setRegex(String regex) {
        String problem = checkRegex(regex);
        if (problem != null) {
            throw new IllegalArgumentException(
                "Invalid pattern for the file type '" + name + "': " + problem
            );
        }
        this.regex = regex;
        this.pattern = Pattern.compile(regex);
    }

    /**
     * Keeps a regular expression that does not compile (read from a project file), so that it
     * is saved back unchanged and can be corrected; such a file type matches no file.
     */
    void setInvalidRegex(String regex) {
        this.regex = regex;
        this.pattern = null;
    }

    /** False if the regular expression does not compile: the file type then matches no file. */
    public boolean isValid() {
        return pattern != null;
    }

    /**
     * Checks a regular expression of file names.
     *
     * @return null if valid, otherwise a message for the user (with the regular expression to
     *         use when the value looks like a wildcard pattern such as {@code *.txt})
     */
    public static String checkRegex(String regex) {
        if (regex == null) {
            return "no regular expression";
        }
        try {
            Pattern.compile(regex);
            return null;
        } catch (PatternSyntaxException e) {
            String message = "'" + regex + "' is not a valid regular expression (" +
                e.getDescription() + " at position " + Math.max(0, e.getIndex()) + ")";
            String suggestion = globToRegex(regex);
            if (suggestion != null) {
                message += ". Did you mean " + suggestion + " ? (regular expressions are used, not " +
                    "wildcard patterns: '.*' matches any characters, '\\.' a dot)";
            }
            return message;
        }
    }

    /**
     * The regular expression equivalent to a wildcard pattern ({@code *.txt} gives
     * {@code .*\.txt}), or null if the value does not look like one.
     */
    static String globToRegex(String glob) {
        if (!glob.contains("*") && !glob.contains("?")) {
            return null;
        }
        StringBuilder b = new StringBuilder();
        for (char c : glob.toCharArray()) {
            switch (c) {
                case '*' -> b.append(".*");
                case '?' -> b.append('.');
                case '.', '\\', '+', '(', ')', '[', ']', '{', '}', '^', '$', '|' -> b.append('\\').append(c);
                default -> b.append(c);
            }
        }
        String regex = b.toString();
        return checkRegexQuietly(regex) ? regex : null;
    }

    private static boolean checkRegexQuietly(String regex) {
        try {
            Pattern.compile(regex);
            return true;
        } catch (PatternSyntaxException e) {
            return false;
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

    /** True if the file name matches; always false if the regular expression is invalid. */
    public boolean matches(Path filePath) {
        if (pattern == null) {
            return false;
        }
        return pattern.matcher(filePath.getFileName().toString()).matches();
    }

}
