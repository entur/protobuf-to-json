package no.entur.protobuf.json.filter;

/**
 * Checks the syntax of raw JSON values (RFC 8259), such as the replacements for anonymized and pruned values, which are
 * written as is.
 * <br><br>
 * Internal; public only for the builders.
 */

public final class JsonSyntax {

    private static final int MAX_DEPTH = 1000;

    private final String json;
    private int position;

    private JsonSyntax(String json) {
        this.json = json;
    }

    /**
     * Whether a string is a single JSON value, optionally surrounded by whitespace.
     *
     * @param json string
     * @return true if valid
     */

    public static boolean isValue(String json) {
        if (json == null) {
            return false;
        }
        JsonSyntax syntax = new JsonSyntax(json);
        syntax.whitespace();
        if (!syntax.value(0)) {
            return false;
        }
        syntax.whitespace();
        return syntax.position == json.length();
    }

    /**
     * Check a replacement for anonymized or pruned values, which is written as is.
     *
     * @param raw    raw JSON value
     * @param option the option, for the exception message
     * @throws IllegalArgumentException if not a single JSON value
     */

    public static void requireReplacement(String raw, String option) {
        if (!isValue(raw)) {
            throw new IllegalArgumentException("Expected a JSON value as the " + option + " replacement, got " + (raw == null ? "null" : "'" + raw + "'"));
        }
    }

    private boolean value(int depth) {
        if (position >= json.length()) {
            return false;
        }
        switch (json.charAt(position)) {
            case '{':
                return object(depth + 1);
            case '[':
                return array(depth + 1);
            case '"':
                return string();
            case 't':
                return literal("true");
            case 'f':
                return literal("false");
            case 'n':
                return literal("null");
            default:
                return number();
        }
    }

    private boolean object(int depth) {
        if (depth > MAX_DEPTH) {
            return false;
        }
        position++;
        whitespace();
        if (skip('}')) {
            return true;
        }
        do {
            whitespace();
            if (!string()) {
                return false;
            }
            whitespace();
            if (!skip(':')) {
                return false;
            }
            whitespace();
            if (!value(depth)) {
                return false;
            }
            whitespace();
        } while (skip(','));
        return skip('}');
    }

    private boolean array(int depth) {
        if (depth > MAX_DEPTH) {
            return false;
        }
        position++;
        whitespace();
        if (skip(']')) {
            return true;
        }
        do {
            whitespace();
            if (!value(depth)) {
                return false;
            }
            whitespace();
        } while (skip(','));
        return skip(']');
    }

    private boolean string() {
        if (!skip('"')) {
            return false;
        }
        while (position < json.length()) {
            char c = json.charAt(position++);
            if (c == '"') {
                return true;
            }
            if (c < 0x20) {
                return false;
            }
            if (c == '\\') {
                if (position >= json.length()) {
                    return false;
                }
                switch (json.charAt(position++)) {
                    case '"':
                    case '\\':
                    case '/':
                    case 'b':
                    case 'f':
                    case 'n':
                    case 'r':
                    case 't':
                        break;
                    case 'u':
                        for (int i = 0; i < 4; i++) {
                            if (position >= json.length() || !isHexDigit(json.charAt(position++))) {
                                return false;
                            }
                        }
                        break;
                    default:
                        return false;
                }
            }
        }
        return false;
    }

    private boolean number() {
        skip('-');
        // no leading zeros
        if (!skip('0')) {
            if (!isDigit(1)) {
                return false;
            }
            digits();
        }
        if (skip('.') && digits() == 0) {
            return false;
        }
        if (skip('e') || skip('E')) {
            if (!skip('+')) {
                skip('-');
            }
            return digits() > 0;
        }
        return true;
    }

    private boolean isDigit(int min) {
        if (position >= json.length()) {
            return false;
        }
        char c = json.charAt(position);
        return c >= '0' + min && c <= '9';
    }

    private int digits() {
        int count = 0;
        while (isDigit(0)) {
            position++;
            count++;
        }
        return count;
    }

    private boolean literal(String literal) {
        if (json.startsWith(literal, position)) {
            position += literal.length();
            return true;
        }
        return false;
    }

    private boolean skip(char c) {
        if (position < json.length() && json.charAt(position) == c) {
            position++;
            return true;
        }
        return false;
    }

    private void whitespace() {
        while (position < json.length()) {
            char c = json.charAt(position);
            if (c != ' ' && c != '\t' && c != '\n' && c != '\r') {
                return;
            }
            position++;
        }
    }

    private static boolean isHexDigit(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }
}
