package no.entur.protobuf.json.filter;

/**
 * Truncation of long string values, as in json-log-filter: a string is cut after {@code maxStringLength} UTF-16 characters
 * (one less if that would split a surrogate pair), and the truncate message and the number of removed characters are appended,
 * i.e. {@code "abc... + 17"}. Strings are only truncated if that makes them shorter, i.e. if the number of removed characters
 * is larger than the length of the truncate message plus the number of digits.
 */

public final class MaxStringLength {

    private MaxStringLength() {
    }

    /**
     * Number of characters to keep.
     *
     * @param value                 string value
     * @param maxStringLength       max string length
     * @param truncateMessageLength length of the (escaped) truncate message
     * @return number of characters to keep, or -1 if the string is not truncated
     */

    public static int getTruncatedLength(CharSequence value, int maxStringLength, int truncateMessageLength) {
        int length = value.length();
        if (length <= maxStringLength) {
            return -1;
        }
        int keepLength;
        if (Character.isLowSurrogate(value.charAt(maxStringLength))) {
            keepLength = maxStringLength - 1;
        } else {
            keepLength = maxStringLength;
        }
        if (isTruncated(length - keepLength, truncateMessageLength)) {
            return keepLength;
        }
        return -1;
    }

    /**
     * Number of characters to keep.
     *
     * @param chars                 characters
     * @param offset                offset
     * @param length                number of characters
     * @param maxStringLength       max string length
     * @param truncateMessageLength length of the (escaped) truncate message
     * @return number of characters to keep, or -1 if the string is not truncated
     */

    public static int getTruncatedLength(char[] chars, int offset, int length, int maxStringLength, int truncateMessageLength) {
        if (length <= maxStringLength) {
            return -1;
        }
        int keepLength;
        if (Character.isLowSurrogate(chars[offset + maxStringLength])) {
            keepLength = maxStringLength - 1;
        } else {
            keepLength = maxStringLength;
        }
        if (isTruncated(length - keepLength, truncateMessageLength)) {
            return keepLength;
        }
        return -1;
    }

    /**
     * Truncation of a UTF-8 encoded string, counting UTF-16 characters like for decoded strings.
     *
     * @param bytes                 UTF-8 bytes
     * @param offset                offset
     * @param length                number of bytes
     * @param maxStringLength       max string length
     * @param truncateMessageLength length of the (escaped) truncate message
     * @return -1 if the string is not truncated, otherwise the number of bytes to keep and the number of removed UTF-16 characters,
     * see {@linkplain #getKeepBytes(long)} and {@linkplain #getRemovedCharacters(long)}
     */

    public static long getTruncatedUtf8(byte[] bytes, int offset, int length, int maxStringLength, int truncateMessageLength) {
        if (length <= maxStringLength) {
            // at least one byte per character
            return -1;
        }
        int end = offset + length;
        int characters = 0;
        int i = offset;
        while (i < end) {
            int b = bytes[i] & 0xFF;
            int sequenceLength;
            int sequenceCharacters;
            if (b < 0xC0) {
                sequenceLength = 1;
                sequenceCharacters = 1;
            } else if (b < 0xE0) {
                sequenceLength = 2;
                sequenceCharacters = 1;
            } else if (b < 0xF0) {
                sequenceLength = 3;
                sequenceCharacters = 1;
            } else {
                // surrogate pair
                sequenceLength = 4;
                sequenceCharacters = 2;
            }
            if (characters + sequenceCharacters > maxStringLength) {
                // also when the character would be half of a surrogate pair
                break;
            }
            characters += sequenceCharacters;
            i = Math.min(i + sequenceLength, end);
        }
        if (i >= end) {
            return -1;
        }
        int removed = getUtf16Length(bytes, i, end - i);
        if (!isTruncated(removed, truncateMessageLength)) {
            return -1;
        }
        return ((long) (i - offset) << 32) | removed;
    }

    public static int getKeepBytes(long truncated) {
        return (int) (truncated >>> 32);
    }

    public static int getRemovedCharacters(long truncated) {
        return (int) truncated;
    }

    /**
     * Number of UTF-16 characters of UTF-8 encoded text.
     *
     * @param bytes  UTF-8 bytes
     * @param offset offset
     * @param length number of bytes
     * @return number of characters
     */

    public static int getUtf16Length(byte[] bytes, int offset, int length) {
        int characters = 0;
        int end = offset + length;
        for (int i = offset; i < end; i++) {
            int b = bytes[i] & 0xFF;
            if ((b & 0xC0) != 0x80) {
                characters++;
                if (b >= 0xF0) {
                    // surrogate pair
                    characters++;
                }
            }
        }
        return characters;
    }

    /**
     * Length of the base64 encoding (with padding).
     *
     * @param length number of bytes
     * @return number of characters
     */

    public static int getBase64Length(int length) {
        return ((length + 2) / 3) * 4;
    }

    public static boolean isTruncated(int removed, int truncateMessageLength) {
        // if truncate message + digits is smaller than the removed characters
        return removed - truncateMessageLength - lengthToDigits(removed) > 0;
    }

    /**
     * Number of digits of a non-negative number.
     *
     * @param number number
     * @return number of digits
     */

    public static int lengthToDigits(int number) {
        if (number < 100000) {
            if (number < 100) {
                return number < 10 ? 1 : 2;
            }
            if (number < 1000) {
                return 3;
            }
            return number < 10000 ? 4 : 5;
        }
        if (number < 10000000) {
            return number < 1000000 ? 6 : 7;
        }
        if (number < 100000000) {
            return 8;
        }
        return number < 1000000000 ? 9 : 10;
    }

    /**
     * Write the truncated string contents (not quoted or escaped) to a buffer.
     *
     * @param value   string value
     * @param keep    number of characters to keep
     * @param message truncate message
     * @param removed number of removed characters
     * @param buffer  buffer, at least {@code keep + message.length + 10} long
     * @return number of characters written
     */

    public static int fill(CharSequence value, int keep, char[] message, int removed, char[] buffer) {
        if (value instanceof String) {
            // a bulk copy
            ((String) value).getChars(0, keep, buffer, 0);
        } else {
            for (int i = 0; i < keep; i++) {
                buffer[i] = value.charAt(i);
            }
        }
        System.arraycopy(message, 0, buffer, keep, message.length);
        return appendDigits(removed, buffer, keep + message.length);
    }

    /**
     * Write the truncated string contents (not quoted or escaped) to a buffer.
     *
     * @param bytes     UTF-8 bytes
     * @param offset    offset
     * @param keepBytes number of bytes to keep
     * @param message   truncate message, UTF-8 encoded
     * @param removed   number of removed characters
     * @param buffer    buffer, at least {@code keepBytes + message.length + 10} long
     * @return number of bytes written
     */

    public static int fill(byte[] bytes, int offset, int keepBytes, byte[] message, int removed, byte[] buffer) {
        System.arraycopy(bytes, offset, buffer, 0, keepBytes);
        System.arraycopy(message, 0, buffer, keepBytes, message.length);
        int length = keepBytes + message.length;
        int digits = lengthToDigits(removed);
        for (int i = length + digits - 1; i >= length; i--) {
            buffer[i] = (byte) ('0' + removed % 10);
            removed /= 10;
        }
        return length + digits;
    }

    private static int appendDigits(int number, char[] buffer, int offset) {
        int digits = lengthToDigits(number);
        for (int i = offset + digits - 1; i >= offset; i--) {
            buffer[i] = (char) ('0' + number % 10);
            number /= 10;
        }
        return offset + digits;
    }
}
