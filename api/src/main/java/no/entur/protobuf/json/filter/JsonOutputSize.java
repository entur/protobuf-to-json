package no.entur.protobuf.json.filter;

/**
 * Size of JSON as written by a Jackson generator with the default escaping and without a pretty printer,
 * in characters, or in bytes for generators which write UTF-8 to an output stream.
 * <br><br>
 * Jackson 2 generators which write UTF-8 escape each half of a surrogate pair as <code>&#92;uXXXX</code> when writing strings
 * (unless {@code JsonWriteFeature.COMBINE_UNICODE_SURROGATES_IN_UTF8} is enabled), Jackson 3 generators write the 4 byte UTF-8 sequence.
 * <br><br>
 * Ported from json-log-filter (Apache-2.0).
 */

public final class JsonOutputSize {

    public static final int TRUE = 4;
    public static final int FALSE = 5;
    public static final int NULL = 4;

    private JsonOutputSize() {
    }

    /**
     * Size of a field name and the colon.
     *
     * @param name field name
     * @param utf8 true if writing UTF-8 bytes, false if characters
     * @return size
     */

    public static int getNameSize(CharSequence name, boolean utf8) {
        return getQuotedSize(name, utf8) + 1;
    }

    public static int getNameSize(CharSequence name, boolean utf8, boolean escapeSurrogates) {
        return getQuotedSize(name, utf8, escapeSurrogates) + 1;
    }

    /**
     * Size of a string, including quotes and escapes.
     *
     * @param value string
     * @param utf8  true if writing UTF-8 bytes, false if characters
     * @return size
     */

    public static int getQuotedSize(CharSequence value, boolean utf8) {
        return getQuotedSize(value, 0, value.length(), utf8, false);
    }

    public static int getQuotedSize(CharSequence value, boolean utf8, boolean escapeSurrogates) {
        return getQuotedSize(value, 0, value.length(), utf8, escapeSurrogates);
    }

    public static int getQuotedSize(CharSequence value, int start, int end, boolean utf8) {
        return getQuotedSize(value, start, end, utf8, false);
    }

    /**
     * Size of (part of) a string, including quotes and escapes.
     *
     * @param value            string
     * @param start            start index
     * @param end              end index
     * @param utf8             true if writing UTF-8 bytes, false if characters
     * @param escapeSurrogates true if surrogates are escaped in UTF-8 output
     * @return size
     */

    public static int getQuotedSize(CharSequence value, int start, int end, boolean utf8, boolean escapeSurrogates) {
        int size = end - start + 2;
        if (value instanceof String) {
            String string = (String) value;
            for (int i = start; i < end; i++) {
                char c = string.charAt(i);
                size += c < 0x80 ? ASCII_EXTRA_SIZES[c] : getExtraSize(c, utf8, escapeSurrogates);
            }
        } else {
            for (int i = start; i < end; i++) {
                size += getExtraSize(value.charAt(i), utf8, escapeSurrogates);
            }
        }
        return size;
    }

    public static int getQuotedSize(char[] chars, int offset, int length, boolean utf8) {
        return getQuotedSize(chars, offset, length, utf8, false);
    }

    public static int getQuotedSize(char[] chars, int offset, int length, boolean utf8, boolean escapeSurrogates) {
        int size = length + 2;
        int end = offset + length;
        for (int i = offset; i < end; i++) {
            char c = chars[i];
            size += c < 0x80 ? ASCII_EXTRA_SIZES[c] : getExtraSize(c, utf8, escapeSurrogates);
        }
        return size;
    }

    /**
     * Size of a UTF-8 encoded string, including quotes and escapes.
     *
     * @param bytes  UTF-8 bytes
     * @param offset offset
     * @param length number of bytes
     * @param utf8   true if writing UTF-8 bytes, false if characters
     * @return size
     */

    public static int getQuotedUtf8Size(byte[] bytes, int offset, int length, boolean utf8) {
        int size = (utf8 ? length : MaxStringLength.getUtf16Length(bytes, offset, length)) + 2;
        int end = offset + length;
        for (int i = offset; i < end; i++) {
            byte b = bytes[i];
            if (b >= 0 && b < 0x20) {
                size += getControlCharacterExtraSize(b);
            } else if (b == '"' || b == '\\') {
                size++;
            }
        }
        return size;
    }

    /**
     * Size of a value written as is.
     *
     * @param raw  raw JSON
     * @param utf8 true if writing UTF-8 bytes, false if characters
     * @return size
     */

    public static int getRawSize(CharSequence raw, boolean utf8) {
        int length = raw.length();
        int size = length;
        if (utf8) {
            for (int i = 0; i < length; i++) {
                size += getUtf8ExtraSize(raw.charAt(i));
            }
        }
        return size;
    }

    public static int getSize(int value) {
        return getSize((long) value);
    }

    public static int getSize(long value) {
        if (value < 0) {
            if (value == Long.MIN_VALUE) {
                return 20;
            }
            return 1 + getDigits(-value);
        }
        return getDigits(value);
    }

    private static int getDigits(long value) {
        if (value <= Integer.MAX_VALUE) {
            return MaxStringLength.lengthToDigits((int) value);
        }
        int digits = 10;
        long limit = 10_000_000_000L;
        while (digits < 19 && value >= limit) {
            digits++;
            limit *= 10;
        }
        return digits;
    }

    /** the extra size of escaped ASCII characters, which is the same for characters and UTF-8 */
    private static final byte[] ASCII_EXTRA_SIZES = new byte[0x80];

    static {
        for (char c = 0; c < 0x80; c++) {
            ASCII_EXTRA_SIZES[c] = (byte) getExtraSize(c, false, false);
        }
    }

    private static int getExtraSize(char c, boolean utf8, boolean escapeSurrogates) {
        if (c < 0x20) {
            return getControlCharacterExtraSize(c);
        }
        if (c == '"' || c == '\\') {
            return 1;
        }
        if (utf8 && c >= 0x80) {
            if (escapeSurrogates && Character.isSurrogate(c)) {
                return 5; // i.e. backslash, u and four hex digits
            }
            return getUtf8ExtraSize(c);
        }
        return 0;
    }

    private static int getControlCharacterExtraSize(int c) {
        switch (c) {
            case '\b':
            case '\t':
            case '\n':
            case '\f':
            case '\r':
                return 1; // i.e. backslash and letter
            default:
                return 5; // i.e. backslash, u and four hex digits
        }
    }

    private static int getUtf8ExtraSize(char c) {
        if (c < 0x80) {
            return 0;
        }
        // 2 bytes up to 0x7FF, 3 bytes otherwise, and 4 bytes for a surrogate pair (2 chars)
        return c < 0x800 || Character.isSurrogate(c) ? 1 : 2;
    }
}
