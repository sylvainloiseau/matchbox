package fr.cnrs.lacito.matchbox.corpus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Applies the rule of {@link MatchRole} while a {@link Text} is built, one grammar at a time in
 * rank order:
 * <ul>
 *   <li>a top-level {@link MatchRole#EXCLUSIVE exclusive} object meeting a claim of an earlier
 *       grammar is not kept ({@link MatchConflict.Kind#DROPPED});</li>
 *   <li>an exclusive descendant of a scope or annotation meeting such a claim is kept and reported
 *       ({@link MatchConflict.Kind#NESTED_OVERLAP}): objects are immutable, a child cannot be
 *       removed from its parent;</li>
 *   <li>a {@link MatchRole#SCOPE scope} and an exclusive object of another grammar that meet
 *       without the scope enclosing the object are reported ({@link MatchConflict.Kind#SCOPE_CROSSING});</li>
 *   <li>overlapping top-level exclusive objects of one grammar are reported
 *       ({@link MatchConflict.Kind#SAME_GRAMMAR}).</li>
 * </ul>
 * Every conflict gives a warning {@link Diagnostic} of the later grammar. Not thread-safe (owned by
 * one builder).
 */
final class OverlapResolver {

    /** A kept node of a grammar: its object, grammar and display name. */
    private record Placed(CorpusObject object, String grammarId, String grammarName) {
        TextCoordinate span() {
            return object.getTextCoordinate();
        }
    }

    /** What is kept of a grammar's objects, and the warnings to add to its diagnostics. */
    record Outcome(List<CorpusObject> kept, List<Diagnostic> warnings) {}

    private final TextId id;
    /** Outermost exclusive nodes of the kept objects, in grammar order. */
    private final List<Placed> claims = new ArrayList<>();
    /** Scope nodes of the kept objects. */
    private final List<Placed> scopes = new ArrayList<>();
    private final List<MatchConflict> conflicts = new ArrayList<>();

    OverlapResolver(TextId id) {
        this.id = id;
    }

    /** Claims of the grammars added so far, sorted by position. */
    List<Claim> claims() {
        return claims.stream()
            .map(p -> new Claim(p.span(), p.grammarId(), p.object().getKind()))
            .sorted(Comparator.comparingInt((Claim c) -> c.span().startIndex())
                .thenComparingInt(c -> c.span().stopIndex()))
            .toList();
    }

    List<MatchConflict> conflicts() {
        return List.copyOf(conflicts);
    }

    /**
     * Resolves the top-level objects of the next grammar against those of the earlier ones.
     *
     * @param grammarId   id of the grammar
     * @param grammarName name shown in the messages
     * @param objects     its top-level objects, spans already validated
     */
    Outcome add(String grammarId, String grammarName, List<CorpusObject> objects) {
        SpanIndex earlierClaims = new SpanIndex(claims);
        SpanIndex earlierScopes = new SpanIndex(scopes);
        List<CorpusObject> kept = new ArrayList<>();
        List<Diagnostic> warnings = new ArrayList<>();
        List<Placed> newClaims = new ArrayList<>();
        List<Placed> topClaims = new ArrayList<>();
        List<Placed> newScopes = new ArrayList<>();

        for (CorpusObject o : objects) {
            if (o.getMatchRole() == MatchRole.EXCLUSIVE) {
                Optional<Placed> winner = earlierClaims.firstMeeting(o.getTextCoordinate());
                if (winner.isPresent()) {
                    report(MatchConflict.Kind.DROPPED, o, grammarId, winner.get(), warnings);
                    continue;
                }
            }
            kept.add(o);
            Placed self = new Placed(o, grammarId, grammarName);
            if (o.getMatchRole() == MatchRole.EXCLUSIVE) {
                newClaims.add(self);
                topClaims.add(self);
            } else {
                collect(o, grammarId, grammarName, newClaims, newScopes);
            }
        }

        // exclusive nodes inside scopes or annotations
        for (Placed c : newClaims) {
            if (topClaims.contains(c)) continue;
            earlierClaims.firstMeeting(c.span()).ifPresent(w ->
                report(MatchConflict.Kind.NESTED_OVERLAP, c.object(), grammarId, w, warnings));
        }
        // scopes crossing exclusive objects, in both directions
        for (Placed s : newScopes) {
            earlierClaims.meeting(s.span()).stream()
                .filter(c -> !s.span().encloses(c.span()))
                .findFirst()
                .ifPresent(c -> report(MatchConflict.Kind.SCOPE_CROSSING, s.object(), grammarId, c, warnings));
        }
        for (Placed c : newClaims) {
            earlierScopes.meeting(c.span()).stream()
                .filter(s -> !s.span().encloses(c.span()))
                .findFirst()
                .ifPresent(s -> report(MatchConflict.Kind.SCOPE_CROSSING, c.object(), grammarId, s, warnings));
        }
        // overlapping top-level objects of this grammar
        List<Placed> sorted = new ArrayList<>(topClaims);
        sorted.sort(Comparator.comparingInt(p -> p.span().startIndex()));
        Placed reach = null;
        for (Placed p : sorted) {
            if (reach != null && p.span().startIndex() <= reach.span().stopIndex()) {
                report(MatchConflict.Kind.SAME_GRAMMAR, p.object(), grammarId, reach, warnings);
            }
            if (reach == null || p.span().stopIndex() > reach.span().stopIndex()) {
                reach = p;
            }
        }

        claims.addAll(newClaims);
        scopes.addAll(newScopes);
        return new Outcome(List.copyOf(kept), List.copyOf(warnings));
    }

    /** Outermost exclusive nodes and scope nodes below a non-exclusive node (itself included). */
    private static void collect(CorpusObject o, String grammarId, String grammarName,
                                List<Placed> exclusives, List<Placed> scopes) {
        switch (o.getMatchRole()) {
            case EXCLUSIVE -> exclusives.add(new Placed(o, grammarId, grammarName));
            case SCOPE -> {
                scopes.add(new Placed(o, grammarId, grammarName));
                for (CorpusObject child : o.getChildren()) {
                    collect(child, grammarId, grammarName, exclusives, scopes);
                }
            }
            case ANNOTATION -> {
                // an annotation owns no characters, and nothing below it does
            }
        }
    }

    private void report(MatchConflict.Kind kind, CorpusObject object, String grammarId, Placed other,
                        List<Diagnostic> warnings) {
        String message = message(kind, object, other);
        conflicts.add(new MatchConflict(kind, object, grammarId, other.object(), other.grammarId(), message));
        TextCoordinate span = object.getTextCoordinate();
        warnings.add(Diagnostic.warning(id, grammarId, span.startLine(), 0, message,
            span.startIndex(), span.stopIndex()));
    }

    static String message(MatchConflict.Kind kind, CorpusObject object, Placed other) {
        String otherWhat = name(other.object().getKind()) + " (" + lines(other.span()) + ")";
        return switch (kind) {
            case DROPPED -> "Not kept: " + lines(object.getTextCoordinate()) + " already read as "
                + otherWhat + " by " + other.grammarName() + ", which comes first for this file."
                + " Change the order of the grammars in the project properties, or remove one.";
            case NESTED_OVERLAP -> capitalized(name(object.getKind())) + " also read as " + otherWhat
                + " by " + other.grammarName() + ", which comes first for this file.";
            case SCOPE_CROSSING -> capitalized(name(object.getKind())) + " (" + lines(object.getTextCoordinate())
                + ") crosses " + otherWhat + " read by " + other.grammarName()
                + ": one should contain the other.";
            case SAME_GRAMMAR -> "The grammar returned overlapping objects: " + name(object.getKind())
                + " (" + lines(object.getTextCoordinate()) + ") and " + otherWhat + ".";
        };
    }

    /** {@code "igt"} for {@code "notebook-grammar:igt"}. */
    private static String name(String kind) {
        int colon = kind.indexOf(':');
        return colon >= 0 ? kind.substring(colon + 1) : kind;
    }

    private static String capitalized(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String lines(TextCoordinate span) {
        return span.startLine() == span.endLine()
            ? "line " + span.startLine()
            : "lines " + span.startLine() + "–" + span.endLine();
    }

    /**
     * Nodes sorted by start, with the running maximum of their ends, to find those meeting a range
     * without scanning all of them.
     */
    private static final class SpanIndex {
        /** Nodes sorted by start; {@code order[i]} is the position of {@code sorted[i]} in grammar order. */
        private final List<Placed> sorted = new ArrayList<>();
        private final int[] order;
        private final int[] maxStop;

        SpanIndex(List<Placed> nodes) {
            Integer[] idx = new Integer[nodes.size()];
            for (int i = 0; i < idx.length; i++) idx[i] = i;
            java.util.Arrays.sort(idx, Comparator.comparingInt(i -> nodes.get(i).span().startIndex()));
            order = new int[idx.length];
            maxStop = new int[idx.length];
            int max = Integer.MIN_VALUE;
            for (int i = 0; i < idx.length; i++) {
                Placed p = nodes.get(idx[i]);
                sorted.add(p);
                order[i] = idx[i];
                max = Math.max(max, p.span().stopIndex());
                maxStop[i] = max;
            }
        }

        /** Nodes sharing a character with {@code span}, earliest grammar first, then by position. */
        List<Placed> meeting(TextCoordinate span) {
            int start = span.startIndex();
            int stop = span.stopIndex();
            // last node starting at or before stop
            int lo = 0;
            int hi = sorted.size() - 1;
            int last = -1;
            while (lo <= hi) {
                int mid = (lo + hi) >>> 1;
                if (sorted.get(mid).span().startIndex() <= stop) {
                    last = mid;
                    lo = mid + 1;
                } else {
                    hi = mid - 1;
                }
            }
            List<Integer> found = new ArrayList<>();
            for (int i = last; i >= 0 && maxStop[i] >= start; i--) {
                if (sorted.get(i).span().stopIndex() >= start) {
                    found.add(i);
                }
            }
            found.sort(Comparator.comparingInt(i -> order[i]));
            return found.stream().map(sorted::get).toList();
        }

        Optional<Placed> firstMeeting(TextCoordinate span) {
            List<Placed> m = meeting(span);
            return m.isEmpty() ? Optional.empty() : Optional.of(m.get(0));
        }
    }
}
