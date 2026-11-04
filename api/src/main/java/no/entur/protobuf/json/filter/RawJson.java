package no.entur.protobuf.json.filter;

/**
 * Checks that a raw replacement is exactly one JSON value. The replacement is written as is, so more than one value,
 * or text which is not a value, would make the surrounding JSON invalid.
 */

public final class RawJson {

    private RawJson() {
    }

    /**
     * Require exactly one JSON value, optionally surrounded by whitespace.
     *
     * @param raw replacement written as is
     * @throws IllegalArgumentException if {@code raw} is null, empty, or not one JSON value
     */

    public static void requireValue(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("Expected a JSON value, got null");
        }
        int end = skipValue(raw, skipWhitespace(raw, 0));
        if (skipWhitespace(raw, end) != raw.length()) {
            throw new IllegalArgumentException("Expected a single JSON value, got: " + excerpt(raw));
        }
    }

    private static int skipValue(String raw, int i) {
        if (i >= raw.length()) {
            throw invalid(raw, i, "a JSON value");
        }
        char c = raw.charAt(i);
        if (c == '"') {
            return skipString(raw, i);
        }
        if (c == '{') {
            return skipObject(raw, i);
        }
        if (c == '[') {
            return skipArray(raw, i);
        }
        if (c == 't') {
            return skipLiteral(raw, i, "true");
        }
        if (c == 'f') {
            return skipLiteral(raw, i, "false");
        }
        if (c == 'n') {
            return skipLiteral(raw, i, "null");
        }
        if (c == '-' || isDigit(c)) {
            return skipNumber(raw, i);
        }
        throw invalid(raw, i, "a JSON value");
    }

    private static int skipLiteral(String raw, int i, String literal) {
        if (raw.regionMatches(i, literal, 0, literal.length())) {
            return i + literal.length();
        }
        throw invalid(raw, i, "a JSON value");
    }

    private static int skipNumber(String raw, int i) {
        int start = i;
        if (raw.charAt(i) == '-') {
            i++;
        }
        if (i >= raw.length() || !isDigit(raw.charAt(i))) {
            throw invalid(raw, start, "a JSON number");
        }
        if (raw.charAt(i) == '0') {
            i++;
        } else {
            while (i < raw.length() && isDigit(raw.charAt(i))) {
                i++;
            }
        }
        if (i < raw.length() && raw.charAt(i) == '.') {
            i++;
            int fraction = i;
            while (i < raw.length() && isDigit(raw.charAt(i))) {
                i++;
            }
            if (i == fraction) {
                throw invalid(raw, start, "a JSON number");
            }
        }
        if (i < raw.length() && (raw.charAt(i) == 'e' || raw.charAt(i) == 'E')) {
            i++;
            if (i < raw.length() && (raw.charAt(i) == '+' || raw.charAt(i) == '-')) {
                i++;
            }
            int exponent = i;
            while (i < raw.length() && isDigit(raw.charAt(i))) {
                i++;
            }
            if (i == exponent) {
                throw invalid(raw, start, "a JSON number");
            }
        }
        return i;
    }

    private static int skipString(String raw, int i) {
        int start = i;
        i++;
        while (i < raw.length()) {
            char c = raw.charAt(i);
            if (c == '"') {
                return i + 1;
            }
            if (c == '\\') {
                i++;
                if (i >= raw.length()) {
                    throw invalid(raw, start, "a JSON string");
                }
                char escaped = raw.charAt(i);
                if (escaped == '"' || escaped == '\\' || escaped == '/' || escaped == 'b' || escaped == 'f' || escaped == 'n' || escaped == 'r' || escaped == 't') {
                    i++;
                } else if (escaped == 'u') {
                    if (i + 4 >= raw.length() || !isHex(raw.charAt(i + 1)) || !isHex(raw.charAt(i + 2)) || !isHex(raw.charAt(i + 3)) || !isHex(raw.charAt(i + 4))) {
                        throw invalid(raw, start, "a JSON string");
                    }
                    i += 5;
                } else {
                    throw invalid(raw, start, "a JSON string");
                }
            } else if (c < 0x20) {
                throw invalid(raw, start, "a JSON string");
            } else {
                i++;
            }
        }
        throw invalid(raw, start, "a JSON string");
    }

    private static int skipObject(String raw, int i) {
        int start = i;
        i = skipWhitespace(raw, i + 1);
        if (i < raw.length() && raw.charAt(i) == '}') {
            return i + 1;
        }
        while (true) {
            if (i >= raw.length() || raw.charAt(i) != '"') {
                throw invalid(raw, start, "a JSON object");
            }
            i = skipWhitespace(raw, skipString(raw, i));
            if (i >= raw.length() || raw.charAt(i) != ':') {
                throw invalid(raw, start, "a JSON object");
            }
            i = skipWhitespace(raw, skipValue(raw, skipWhitespace(raw, i + 1)));
            if (i >= raw.length()) {
                throw invalid(raw, start, "a JSON object");
            }
            char c = raw.charAt(i);
            if (c == '}') {
                return i + 1;
            }
            if (c != ',') {
                throw invalid(raw, start, "a JSON object");
            }
            i = skipWhitespace(raw, i + 1);
            if (i < raw.length() && raw.charAt(i) == '}') {
                throw invalid(raw, start, "a JSON object");
            }
        }
    }

    private static int skipArray(String raw, int i) {
        int start = i;
        i = skipWhitespace(raw, i + 1);
        if (i < raw.length() && raw.charAt(i) == ']') {
            return i + 1;
        }
        while (true) {
            i = skipWhitespace(raw, skipValue(raw, i));
            if (i >= raw.length()) {
                throw invalid(raw, start, "a JSON array");
            }
            char c = raw.charAt(i);
            if (c == ']') {
                return i + 1;
            }
            if (c != ',') {
                throw invalid(raw, start, "a JSON array");
            }
            i = skipWhitespace(raw, i + 1);
            if (i < raw.length() && raw.charAt(i) == ']') {
                throw invalid(raw, start, "a JSON array");
            }
        }
    }

    private static int skipWhitespace(String raw, int i) {
        while (i < raw.length()) {
            char c = raw.charAt(i);
            if (c != ' ' && c != '\t' && c != '\n' && c != '\r') {
                return i;
            }
            i++;
        }
        return i;
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static boolean isHex(char c) {
        return isDigit(c) || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    private static IllegalArgumentException invalid(String raw, int index, String expected) {
        return new IllegalArgumentException("Expected " + expected + " at index " + index + ", got: " + excerpt(raw));
    }

    private static String excerpt(String raw) {
        if (raw.length() <= 60) {
            return raw;
        }
        return raw.substring(0, 60) + "...";
    }
}
