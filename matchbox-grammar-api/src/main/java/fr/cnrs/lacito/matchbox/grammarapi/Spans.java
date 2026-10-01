package fr.cnrs.lacito.matchbox.grammarapi;

import fr.cnrs.lacito.matchbox.corpus.TextCoordinate;
import java.nio.file.Path;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;

/** Helpers to build {@link TextCoordinate}s from ANTLR parse trees. */
public final class Spans {

    private Spans() {}

    /** Span covered by a rule, from its first to its last token (inclusive). */
    public static TextCoordinate of(Path file, ParserRuleContext ctx) {
        Token start = ctx.getStart();
        Token stop = ctx.getStop() != null ? ctx.getStop() : start;
        if (stop.getStopIndex() < start.getStartIndex()) {
            // empty rule (stop token before start, e.g. at EOF)
            stop = start;
        }
        return new TextCoordinate(
            file,
            start.getStartIndex(),
            stop.getStopIndex(),
            start.getLine(),
            stop.getLine()
        );
    }
}
