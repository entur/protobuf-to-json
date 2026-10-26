package no.entur.protobuf.json.filter;

/**
 * Timestamps and durations in the <a href="https://protobuf.dev/programming-guides/json/">ProtoJSON format</a>, formatted
 * like protobuf's Timestamps and Durations (BSD-3-Clause) and JsonFormat do, but faster, and without allocating when formatting
 * into a buffer.
 */

public final class JsonTimes {

    // like com.google.protobuf.util.Timestamps
    public static final long TIMESTAMP_SECONDS_MIN = -62135596800L; // 0001-01-01T00:00:00Z
    public static final long TIMESTAMP_SECONDS_MAX = 253402300799L; // 9999-12-31T23:59:59Z

    // like com.google.protobuf.util.Durations
    public static final long DURATION_SECONDS_MAX = 315576000000L;

    /** length of the longest timestamp, i.e. 9999-12-31T23:59:59.999999999Z */
    public static final int MAX_TIMESTAMP_LENGTH = 30;

    /** length of the longest duration, i.e. -315576000000.999999999s */
    public static final int MAX_DURATION_LENGTH = 24;

    private static final int NANOS_PER_SECOND = 1000000000;

    private JsonTimes() {
    }

    /**
     * @param seconds seconds since the epoch
     * @param nanos   nanos
     * @return true if the timestamp can be formatted
     */

    public static boolean isValidTimestamp(long seconds, int nanos) {
        return seconds >= TIMESTAMP_SECONDS_MIN && seconds <= TIMESTAMP_SECONDS_MAX && nanos >= 0 && nanos < NANOS_PER_SECOND;
    }

    /**
     * @param seconds seconds
     * @param nanos   nanos
     * @return true if the duration can be formatted: in range, and seconds and nanos do not have different signs
     */

    public static boolean isValidDuration(long seconds, int nanos) {
        if (seconds < -DURATION_SECONDS_MAX || seconds > DURATION_SECONDS_MAX) {
            return false;
        }
        if (nanos <= -NANOS_PER_SECOND || nanos >= NANOS_PER_SECOND) {
            return false;
        }
        return !((seconds < 0 && nanos > 0) || (seconds > 0 && nanos < 0));
    }

    /**
     * Format a valid timestamp like Timestamps.toString, i.e. 1972-01-01T10:00:20.021Z.
     *
     * @param seconds seconds since the epoch
     * @param nanos   nanos
     * @return RFC 3339 date-time in UTC
     */

    public static String formatTimestamp(long seconds, int nanos) {
        char[] buffer = new char[MAX_TIMESTAMP_LENGTH];
        return new String(buffer, 0, formatTimestamp(seconds, nanos, buffer));
    }

    /**
     * Format a valid timestamp like Timestamps.toString, i.e. 1972-01-01T10:00:20.021Z.
     *
     * @param seconds seconds since the epoch
     * @param nanos   nanos
     * @param buffer  buffer of at least {@linkplain #MAX_TIMESTAMP_LENGTH} characters
     * @return the length of the RFC 3339 date-time in UTC, written at the start of the buffer
     */

    public static int formatTimestamp(long seconds, int nanos, char[] buffer) {
        long days = Math.floorDiv(seconds, 86400L);
        int secondsOfDay = (int) Math.floorMod(seconds, 86400L);

        // civil date from days since the epoch, in the proleptic Gregorian calendar (Howard Hinnant)
        long z = days + 719468;
        long era = (z >= 0 ? z : z - 146096) / 146097;
        long dayOfEra = z - era * 146097;
        long yearOfEra = (dayOfEra - dayOfEra / 1460 + dayOfEra / 36524 - dayOfEra / 146096) / 365;
        long dayOfYear = dayOfEra - (365 * yearOfEra + yearOfEra / 4 - yearOfEra / 100);
        long monthIndex = (5 * dayOfYear + 2) / 153;
        int day = (int) (dayOfYear - (153 * monthIndex + 2) / 5 + 1);
        int month = (int) (monthIndex < 10 ? monthIndex + 3 : monthIndex - 9);
        int year = (int) (yearOfEra + era * 400 + (month <= 2 ? 1 : 0));

        appendDigits(buffer, 0, year, 4);
        buffer[4] = '-';
        appendDigits(buffer, 5, month, 2);
        buffer[7] = '-';
        appendDigits(buffer, 8, day, 2);
        buffer[10] = 'T';
        appendDigits(buffer, 11, secondsOfDay / 3600, 2);
        buffer[13] = ':';
        appendDigits(buffer, 14, (secondsOfDay / 60) % 60, 2);
        buffer[16] = ':';
        appendDigits(buffer, 17, secondsOfDay % 60, 2);
        int length = appendNanos(buffer, 19, nanos);
        buffer[length++] = 'Z';
        return length;
    }

    /**
     * Format a valid duration like Durations.toString, i.e. 1.5s.
     *
     * @param seconds seconds
     * @param nanos   nanos, with the same sign as seconds
     * @return duration
     */

    public static String formatDuration(long seconds, int nanos) {
        char[] buffer = new char[MAX_DURATION_LENGTH];
        return new String(buffer, 0, formatDuration(seconds, nanos, buffer));
    }

    /**
     * Format a valid duration like Durations.toString, i.e. 1.5s.
     *
     * @param seconds seconds
     * @param nanos   nanos, with the same sign as seconds
     * @param buffer  buffer of at least {@linkplain #MAX_DURATION_LENGTH} characters
     * @return the length of the duration, written at the start of the buffer
     */

    public static int formatDuration(long seconds, int nanos, char[] buffer) {
        int length = 0;
        if (seconds < 0 || nanos < 0) {
            buffer[length++] = '-';
            seconds = -seconds;
            nanos = -nanos;
        }
        int digits = 1;
        for (long value = seconds; value >= 10; value /= 10) {
            digits++;
        }
        for (int i = length + digits - 1; i >= length; i--) {
            buffer[i] = (char) ('0' + seconds % 10);
            seconds /= 10;
        }
        length = appendNanos(buffer, length + digits, nanos);
        buffer[length++] = 's';
        return length;
    }

    /**
     * Append nanos as 3, 6 or 9 digits, like Timestamps.formatNanos, with a leading period. Nothing is appended for zero.
     *
     * @return the new length
     */

    private static int appendNanos(char[] buffer, int offset, int nanos) {
        if (nanos == 0) {
            return offset;
        }
        buffer[offset++] = '.';
        if (nanos % 1000000 == 0) {
            appendDigits(buffer, offset, nanos / 1000000, 3);
            return offset + 3;
        }
        if (nanos % 1000 == 0) {
            appendDigits(buffer, offset, nanos / 1000, 6);
            return offset + 6;
        }
        appendDigits(buffer, offset, nanos, 9);
        return offset + 9;
    }

    private static void appendDigits(char[] buffer, int offset, int value, int digits) {
        for (int i = offset + digits - 1; i >= offset; i--) {
            buffer[i] = (char) ('0' + value % 10);
            value /= 10;
        }
    }
}
