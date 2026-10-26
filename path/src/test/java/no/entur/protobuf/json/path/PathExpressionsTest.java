package no.entur.protobuf.json.path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * Unsupported syntax, which would silently never match, is rejected. Ported from json-log-filter.
 */

public class PathExpressionsTest {

    private static void assertRejected(String expression) {
        assertThrows(IllegalArgumentException.class, () -> PathExpressions.validateAnonymizeExpression(expression), expression);
        assertThrows(IllegalArgumentException.class, () -> PathExpressions.validatePruneExpression(expression), expression);
    }

    private static void assertAccepted(String expression) {
        PathExpressions.validateAnonymizeExpression(expression);
        PathExpressions.validatePruneExpression(expression);
    }

    @Test
    public void bracketNotationIsRejected() {
        // arrays are transparent, so $.items.card matches card within an array
        assertRejected("$.items[*].card");
        assertRejected("$.items[0].card");
    }

    @Test
    public void whitespaceAroundSegmentIsRejected() {
        assertRejected("$..card ");
        assertRejected(" $.a");
        assertRejected("$. a.b");
        assertRejected("$.. card");
        assertRejected("$.a .b");
    }

    @Test
    public void partialWildcardIsRejected() {
        assertRejected("$.a*");
        assertRejected("$.a.b*c");
        assertRejected("$..a*");
        assertRejected("$..*");
    }

    @Test
    public void backslashIsRejected() {
        assertRejected("$.a\\b");
        assertRejected("$.a.b\\nc");
        assertRejected("$..\\");
        assertRejected("$..a\\\\b");
        assertRejected("$.a.*.\\u0041");
    }

    @Test
    public void rootIsRejected() {
        assertRejected("$");
        assertRejected("$.");
        assertRejected("");
        assertRejected("$.a..b");
    }

    @Test
    public void slashNotationIsRejected() {
        assertRejected("/");
        assertRejected("/a");
        assertRejected("/a/b");
        assertRejected("/a/*");
        assertRejected("//a");
        assertRejected("//*");
    }

    @Test
    public void supportedExpressionsAreAccepted() {
        assertAccepted("$.a.b");
        assertAccepted("$.a.*.b");
        assertAccepted("$.a.*");
        assertAccepted("$..a");
        // whitespace within a segment is part of the key
        assertAccepted("$.first name");
        assertAccepted("$.first name.x");
        // so is a slash
        assertAccepted("$.a/b");
        assertAccepted("$../a");
        // the @type key of Any
        assertAccepted("$.any.@type");
    }

    @Test
    public void anyPrefix() {
        assertEquals("a", PathExpressions.removeAnyPrefix("$..a"));
        assertEquals("a/b", PathExpressions.removeAnyPrefix("$..a/b"));
        assertThrows(IllegalArgumentException.class, () -> PathExpressions.removeAnyPrefix("$.a"));
        assertThrows(IllegalArgumentException.class, () -> PathExpressions.removeAnyPrefix("//a"));
    }
}
