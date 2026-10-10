package fr.cnrs.lacito.matchbox.model.project;

import java.util.regex.Pattern;

/**
 * A file pattern of a directory, matched like a {@code .gitignore} line: a pattern without "/"
 * matches the file name at any depth ({@code *.txt}); a pattern with "/" matches the path relative
 * to the directory ({@code drafts/**}, {@code 2024/*.md}); a leading "/" only anchors it; a trailing
 * "/" means everything under that directory. {@code *} and {@code ?} do not cross "/", {@code **}
 * does; {@code [abc]}, {@code [!abc]} and {@code {a,b}} as usual. Paths use "/" on every system.
 * Case is ignored ({@code *.txt} matches {@code NOTES.TXT}), as file systems of macOS and Windows do.
 */
public final class Glob {

    private final String text;
    private final boolean onName;
    private final Pattern regex;

    private Glob(String text, boolean onName, Pattern regex) {
        this.text = text;
        this.onName = onName;
        this.regex = regex;
    }

    /** @throws IllegalArgumentException with a message for the user if the pattern is invalid */
    public static Glob of(String pattern) {
        String p = pattern.strip();
        if (p.isEmpty()) throw new IllegalArgumentException("Empty pattern");
        if (p.endsWith("/")) p = p + "**";
        boolean onName = !p.contains("/");
        if (p.startsWith("/")) p = p.substring(1);
        return new Glob(pattern.strip(), onName, Pattern.compile(toRegex(p), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));
    }

    /** Null if the pattern is valid, else why it is not. */
    public static String problem(String pattern) {
        try {
            of(pattern);
            return null;
        } catch (IllegalArgumentException e) {
            return "Invalid pattern \"" + pattern.strip() + "\": " + e.getMessage();
        }
    }

    /** @param relativePath path relative to the directory, with "/" separators */
    public boolean matches(String relativePath) {
        String subject = onName ? relativePath.substring(relativePath.lastIndexOf('/') + 1) : relativePath;
        return regex.matcher(subject).matches();
    }

    @Override
    public String toString() {
        return text;
    }

    private static String toRegex(String glob) {
        StringBuilder re = new StringBuilder();
        int braces = 0;
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            switch (c) {
                case '*' -> {
                    if (i + 1 < glob.length() && glob.charAt(i + 1) == '*') {
                        i++;
                        if (i + 1 < glob.length() && glob.charAt(i + 1) == '/') {
                            // "**/": any number of directories, none included
                            i++;
                            re.append("(?:.*/)?");
                        } else {
                            re.append(".*");
                        }
                    } else {
                        re.append("[^/]*");
                    }
                }
                case '?' -> re.append("[^/]");
                case '[' -> {
                    int end = glob.indexOf(']', i + 2);
                    if (end < 0) throw new IllegalArgumentException("unclosed [");
                    String set = glob.substring(i + 1, end);
                    if (set.startsWith("!")) set = "^" + set.substring(1);
                    re.append('[').append(set.replace("\\", "\\\\")).append(']');
                    i = end;
                }
                case '{' -> {
                    braces++;
                    re.append("(?:");
                }
                case '}' -> {
                    if (braces == 0) throw new IllegalArgumentException("} without {");
                    braces--;
                    re.append(')');
                }
                case ',' -> re.append(braces > 0 ? "|" : ",");
                default -> re.append(Pattern.quote(String.valueOf(c)));
            }
        }
        if (braces > 0) throw new IllegalArgumentException("unclosed {");
        return re.toString();
    }
}
