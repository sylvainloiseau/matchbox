package fr.cnrs.lacito.matchbox.grammarapi;

import fr.cnrs.lacito.matchbox.corpus.Claim;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * What the application knows when a grammar parses a text: the characters already owned by the
 * {@linkplain fr.cnrs.lacito.matchbox.corpus.MatchRole#EXCLUSIVE exclusive} objects of the grammars
 * ranked before it for this file.
 *
 * <p>An exclusive object of this grammar meeting one of these claims will not be kept (a warning
 * says so), whatever the grammar does. A grammar may use the claims to skip that text, or not to
 * report syntax errors there (e.g. a grammar that flags every line it does not understand).
 *
 * <p>Immutable and thread-safe.
 */
public final class ParseContext {

    /** No earlier grammar, or nothing claimed. */
    public static final ParseContext NONE = new ParseContext(List.of());

    private final List<Claim> claims;
    /** {@code maxStop[i]}: the largest stop index of {@code claims[0..i]}. */
    private final int[] maxStop;

    /** @param claims claims of the earlier grammars (sorted by position here) */
    public ParseContext(List<Claim> claims) {
        List<Claim> sorted = new ArrayList<>(claims);
        sorted.sort(Comparator.comparingInt((Claim c) -> c.span().startIndex())
            .thenComparingInt(c -> c.span().stopIndex()));
        this.claims = List.copyOf(sorted);
        this.maxStop = new int[sorted.size()];
        int max = Integer.MIN_VALUE;
        for (int i = 0; i < sorted.size(); i++) {
            max = Math.max(max, sorted.get(i).span().stopIndex());
            maxStop[i] = max;
        }
    }

    /** Claims of the earlier grammars, sorted by position; unmodifiable. */
    public List<Claim> claims() {
        return claims;
    }

    /** True if a character of {@code [start, stop]} (inclusive offsets) is claimed. */
    public boolean isClaimed(int start, int stop) {
        // last claim starting at or before stop, then back while a claim may still reach start
        int lo = 0;
        int hi = claims.size() - 1;
        int last = -1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (claims.get(mid).span().startIndex() <= stop) {
                last = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        for (int i = last; i >= 0 && maxStop[i] >= start; i--) {
            if (claims.get(i).meets(start, stop)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return "ParseContext" + claims;
    }
}
