package no.entur.protobuf.json.filter;

import java.util.Random;

public final class RandomStrings {

    // quotes, backslash, slash, control characters, 2 and 3 byte UTF-8, and a surrogate pair
    private static final String[] PARTS = {"a", "b", "z", "A", " ", "\"", "\\", "/", "\n", "\t", "\b", "\f", "\r", "\u0000", "\u0001", "\u001f", "\u007f",
            "<", "&", "=", "'", "æ", "é", "ࠀ", "€", "中", " ", "😀", "0", "9", "."};

    private RandomStrings() {
    }

    public static String randomString(Random random, int length) {
        StringBuilder builder = new StringBuilder();
        while (builder.length() < length) {
            builder.append(PARTS[random.nextInt(PARTS.length)]);
        }
        return builder.toString();
    }

    public static String escape(String value) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < 0x20 || c > 0x7e) {
                builder.append(String.format("\\u%04x", (int) c));
            } else {
                builder.append(c);
            }
        }
        return builder.toString();
    }
}
