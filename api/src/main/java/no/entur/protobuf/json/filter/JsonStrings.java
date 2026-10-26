package no.entur.protobuf.json.filter;

/**
 * JSON string escaping, like the Jackson generators do by default: quotes, backslashes and control characters
 * are escaped, everything else is written as is.
 */

public final class JsonStrings {

    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    private JsonStrings() {
    }

    /**
     * Quote and escape a string.
     *
     * @param value string
     * @return JSON string, with quotes
     */

    public static String quote(String value) {
        StringBuilder builder = new StringBuilder(value.length() + 2);
        builder.append('"');
        escape(value, builder);
        builder.append('"');
        return builder.toString();
    }

    /**
     * Escape a string.
     *
     * @param value string
     * @return JSON string contents, without quotes
     */

    public static String escape(String value) {
        StringBuilder builder = new StringBuilder(value.length());
        escape(value, builder);
        return builder.toString();
    }

    private static void escape(String value, StringBuilder builder) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < 0x20) {
                switch (c) {
                    case '\b':
                        builder.append("\\b");
                        break;
                    case '\t':
                        builder.append("\\t");
                        break;
                    case '\n':
                        builder.append("\\n");
                        break;
                    case '\f':
                        builder.append("\\f");
                        break;
                    case '\r':
                        builder.append("\\r");
                        break;
                    default:
                        builder.append("\\u00").append(HEX[c >> 4]).append(HEX[c & 0xF]);
                }
            } else if (c == '"' || c == '\\') {
                builder.append('\\').append(c);
            } else {
                builder.append(c);
            }
        }
    }
}
