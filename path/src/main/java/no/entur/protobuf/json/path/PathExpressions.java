package no.entur.protobuf.json.path;

import java.util.Collection;

/**
 * Path expression syntax:
 * <ul>
 *     <li>absolute paths: {@code $.a.b.c}</li>
 *     <li>{@code *} as a whole segment matches any field name, i.e. {@code $.a.*.c}</li>
 *     <li>any-paths match a field name at any depth: {@code $..a}, without wildcards</li>
 * </ul>
 * Arrays are transparent, so {@code $.items.price} matches {@code price} in every object of the {@code items} array.
 * Field names containing dots cannot be matched; other characters, including {@code /}, are matched as is.
 * Bracket notation, backslashes, partial wildcards and whitespace around path segments are not supported.
 * <br><br>
 * Ported from json-log-filter (Apache-2.0), without its slash notation.
 */

public final class PathExpressions {

    private static final String SYNTAX_ABSOLUTE_PATH = "^\\$\\.(.*)[^\\.]+$";
    private static final String SYNTAX_ANY_PATH = "^\\$(\\.\\.[^\\.|\\*]+)$";

    public static final String ANY_PREFIX = "$..";

    public static final String STAR = "*";

    private PathExpressions() {
    }

    public static boolean hasAnyPrefix(String string) {
        return string.startsWith(ANY_PREFIX);
    }

    public static String removeAnyPrefix(String string) {
        if (string.startsWith(ANY_PREFIX)) {
            return string.substring(ANY_PREFIX.length());
        }
        throw new IllegalArgumentException("Expected any-path expression, got '" + string + "'");
    }

    public static boolean isAllAnyPrefix(Collection<String> expressions) {
        for (String expression : expressions) {
            if (!hasAnyPrefix(expression)) {
                return false;
            }
        }
        return true;
    }

    public static void validateAnonymizeExpression(String expression) {
        if (!isValidExpression(expression)) {
            throw new IllegalArgumentException("Illegal anonymize expression '" + expression + "'. " + syntax());
        }
    }

    public static void validatePruneExpression(String expression) {
        if (!isValidExpression(expression)) {
            throw new IllegalArgumentException("Illegal prune expression '" + expression + "'. " + syntax());
        }
    }

    private static String syntax() {
        return "Expected expression on the form $.a.b.c with wildcards or $..a without wildcards. "
                + "Bracket notation, backslashes, partial wildcards and whitespace around path segments are not supported";
    }

    public static boolean isValidExpression(String expression) {
        if (expression == null || !hasSupportedSegments(expression)) {
            return false;
        }
        if (!expression.contains("..")) {
            return expression.matches(SYNTAX_ABSOLUTE_PATH);
        }
        return expression.matches(SYNTAX_ANY_PATH);
    }

    /**
     * Reject syntax which is not supported, and so would silently never match: JSONPath bracket notation
     * (arrays are transparent, so use $.items.card rather than $.items[*].card), partial wildcards
     * like 'a*', backslashes and whitespace around a path segment.
     *
     * @param expression expression to check
     * @return true if each segment is supported
     */

    private static boolean hasSupportedSegments(String expression) {
        if (expression.indexOf('[') != -1 || expression.indexOf(']') != -1 || expression.indexOf('\\') != -1) {
            return false;
        }
        for (String segment : expression.split("\\.")) {
            if (hasSurroundingWhitespace(segment)) {
                return false;
            }
            if (segment.indexOf('*') != -1 && !segment.equals(STAR)) {
                return false;
            }
        }
        return true;
    }

    // like !segment.equals(segment.strip()), which is not available on Android 8
    private static boolean hasSurroundingWhitespace(String segment) {
        if (segment.isEmpty()) {
            return false;
        }
        int first = segment.codePointAt(0);
        int last = segment.codePointBefore(segment.length());
        return Character.isWhitespace(first) || Character.isWhitespace(last);
    }

    static String intern(String string) {
        if (string.equals(STAR)) {
            return STAR;
        }
        return string;
    }
}
