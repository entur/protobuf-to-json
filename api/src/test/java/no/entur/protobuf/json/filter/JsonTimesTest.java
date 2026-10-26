package no.entur.protobuf.json.filter;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Random;

import static com.google.common.truth.Truth.assertThat;

public class JsonTimesTest {

    @Test
    public void timestamps() {
        assertThat(JsonTimes.formatTimestamp(0, 0)).isEqualTo("1970-01-01T00:00:00Z");
        assertThat(JsonTimes.formatTimestamp(JsonTimes.TIMESTAMP_SECONDS_MIN, 0)).isEqualTo("0001-01-01T00:00:00Z");
        assertThat(JsonTimes.formatTimestamp(JsonTimes.TIMESTAMP_SECONDS_MAX, 999999999)).isEqualTo("9999-12-31T23:59:59.999999999Z");
        assertThat(JsonTimes.formatTimestamp(1700000000, 120000000)).isEqualTo("2023-11-14T22:13:20.120Z");
        assertThat(JsonTimes.formatTimestamp(-1, 1000)).isEqualTo("1969-12-31T23:59:59.000001Z");

        // the same as java.time, which also writes 0, 3, 6 or 9 fraction digits
        Random random = new Random(1);
        for (int i = 0; i < 100000; i++) {
            long seconds = JsonTimes.TIMESTAMP_SECONDS_MIN + (long) (random.nextDouble() * (JsonTimes.TIMESTAMP_SECONDS_MAX - JsonTimes.TIMESTAMP_SECONDS_MIN));
            int nanos;
            switch (random.nextInt(4)) {
                case 0:
                    nanos = 0;
                    break;
                case 1:
                    nanos = random.nextInt(1000) * 1000000;
                    break;
                case 2:
                    nanos = random.nextInt(1000000) * 1000;
                    break;
                default:
                    nanos = random.nextInt(1000000000);
            }
            assertThat(JsonTimes.formatTimestamp(seconds, nanos)).isEqualTo(DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochSecond(seconds, nanos)));
        }
    }

    @Test
    public void timestampRange() {
        assertThat(JsonTimes.isValidTimestamp(JsonTimes.TIMESTAMP_SECONDS_MIN, 0)).isTrue();
        assertThat(JsonTimes.isValidTimestamp(JsonTimes.TIMESTAMP_SECONDS_MIN - 1, 0)).isFalse();
        assertThat(JsonTimes.isValidTimestamp(JsonTimes.TIMESTAMP_SECONDS_MAX + 1, 0)).isFalse();
        assertThat(JsonTimes.isValidTimestamp(0, -1)).isFalse();
        assertThat(JsonTimes.isValidTimestamp(0, 1000000000)).isFalse();
    }

    @Test
    public void durations() {
        assertThat(JsonTimes.formatDuration(1, 0)).isEqualTo("1s");
        assertThat(JsonTimes.formatDuration(0, 0)).isEqualTo("0s");
        assertThat(JsonTimes.formatDuration(-3, -500)).isEqualTo("-3.000000500s");
        assertThat(JsonTimes.formatDuration(0, -500000000)).isEqualTo("-0.500s");
        assertThat(JsonTimes.formatDuration(1, 500000000)).isEqualTo("1.500s");
        assertThat(JsonTimes.formatDuration(315576000000L, 999999999)).isEqualTo("315576000000.999999999s");

        assertThat(JsonTimes.isValidDuration(315576000000L, 999999999)).isTrue();
        assertThat(JsonTimes.isValidDuration(315576000001L, 0)).isFalse();
        assertThat(JsonTimes.isValidDuration(1, -1)).isFalse();
        assertThat(JsonTimes.isValidDuration(-1, 1)).isFalse();
        assertThat(JsonTimes.isValidDuration(0, -999999999)).isTrue();
    }

    @Test
    public void formatsIntoBuffers() {
        char[] buffer = new char[JsonTimes.MAX_TIMESTAMP_LENGTH];
        int length = JsonTimes.formatTimestamp(JsonTimes.TIMESTAMP_SECONDS_MAX, 999999999, buffer);
        assertThat(new String(buffer, 0, length)).isEqualTo("9999-12-31T23:59:59.999999999Z");
        assertThat(length).isEqualTo(JsonTimes.MAX_TIMESTAMP_LENGTH);

        buffer = new char[JsonTimes.MAX_DURATION_LENGTH];
        length = JsonTimes.formatDuration(-JsonTimes.DURATION_SECONDS_MAX, -999999999, buffer);
        assertThat(new String(buffer, 0, length)).isEqualTo("-315576000000.999999999s");
        assertThat(length).isEqualTo(JsonTimes.MAX_DURATION_LENGTH);
    }
}
