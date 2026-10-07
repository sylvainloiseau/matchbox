package fr.cnrs.lacito.matchbox.corpus.capability;

/**
 * A time range in a media file, in milliseconds.
 *
 * @param startMs start (inclusive), 0 or more
 * @param endMs   end, after the start
 */
public record TimeRange(long startMs, long endMs) {

    public TimeRange {
        if (startMs < 0 || endMs <= startMs) {
            throw new IllegalArgumentException("Invalid time range " + startMs + "-" + endMs);
        }
    }

    public long durationMs() {
        return endMs - startMs;
    }
}
