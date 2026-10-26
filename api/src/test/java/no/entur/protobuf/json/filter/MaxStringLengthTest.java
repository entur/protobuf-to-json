package no.entur.protobuf.json.filter;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import java.nio.charset.StandardCharsets;
import java.util.Random;

import org.junit.jupiter.api.Test;

public class MaxStringLengthTest {

    private static final char[] MESSAGE = JsonFilter.DEFAULT_TRUNCATE_MESSAGE.toCharArray();

    private static String truncate(String value, int maxStringLength) {
        int keep = MaxStringLength.getTruncatedLength(value, maxStringLength, MESSAGE.length);
        if (keep == -1) {
            return value;
        }
        char[] buffer = new char[keep + MESSAGE.length + 10];
        int length = MaxStringLength.fill(value, keep, MESSAGE, value.length() - keep, buffer);
        return new String(buffer, 0, length);
    }

    private static String truncateUtf8(String value, int maxStringLength) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        long truncated = MaxStringLength.getTruncatedUtf8(bytes, 0, bytes.length, maxStringLength, MESSAGE.length);
        if (truncated == -1) {
            return value;
        }
        byte[] message = JsonFilter.DEFAULT_TRUNCATE_MESSAGE.getBytes(StandardCharsets.UTF_8);
        int keepBytes = MaxStringLength.getKeepBytes(truncated);
        byte[] buffer = new byte[keepBytes + message.length + 10];
        int length = MaxStringLength.fill(bytes, 0, keepBytes, message, MaxStringLength.getRemovedCharacters(truncated), buffer);
        return new String(buffer, 0, length, StandardCharsets.UTF_8);
    }

    @Test
    public void jsonLogFilterExamples() {
        assertThat(truncate("A".repeat(100), 5)).isEqualTo("AAAAA... + 95");
        assertThat(truncate("\u0001".repeat(20), 3)).isEqualTo("\u0001\u0001\u0001... + 17");
        // do not split a surrogate pair
        assertThat(truncate("ABCDE😀FGHIJKLMNOPQRSTU", 6)).isEqualTo("ABCDE... + 18");
        // only truncate if shorter
        assertThat(truncate("value", 3)).isEqualTo("value");
        assertThat(truncate("abcdefghijklmnopqrst", 3)).isEqualTo("abc... + 17");
        assertThat(truncate("s".repeat(28), 5)).isEqualTo("sssss... + 23");
        assertThat(truncate("abcdefghij", 0)).isEqualTo("... + 10");
    }

    @Test
    public void utf8IsSameAsCharacters() {
        Random random = new Random(1);
        for (int i = 0; i < 20000; i++) {
            String value = RandomStrings.randomString(random, random.nextInt(40));
            int maxStringLength = random.nextInt(30);
            assertWithMessage(RandomStrings.escape(value) + " " + maxStringLength).that(truncateUtf8(value, maxStringLength)).isEqualTo(truncate(value, maxStringLength));
        }
    }

    @Test
    public void utf16Length() {
        Random random = new Random(1);
        for (int i = 0; i < 1000; i++) {
            String value = RandomStrings.randomString(random, random.nextInt(40));
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            assertThat(MaxStringLength.getUtf16Length(bytes, 0, bytes.length)).isEqualTo(value.length());
        }
    }

    @Test
    public void digits() {
        int[] values = {0, 9, 10, 99, 100, 9999, 10000, 99999, 100000, 999999, 1000000, 9999999, 10000000, 99999999, 100000000, 999999999, 1000000000, Integer.MAX_VALUE};
        for (int value : values) {
            assertThat(MaxStringLength.lengthToDigits(value)).isEqualTo(Integer.toString(value).length());
        }
    }

    @Test
    public void base64Length() {
        for (int i = 0; i < 100; i++) {
            assertThat(MaxStringLength.getBase64Length(i)).isEqualTo(java.util.Base64.getEncoder().encodeToString(new byte[i]).length());
        }
    }
}
