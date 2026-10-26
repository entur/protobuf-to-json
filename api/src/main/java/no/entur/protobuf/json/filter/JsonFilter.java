package no.entur.protobuf.json.filter;

import java.nio.charset.StandardCharsets;
import java.util.List;

import no.entur.protobuf.json.path.PathFilter;

/**
 * Compiled filter options. Immutable, so it can be shared between threads; use {@linkplain #newContext(boolean)}
 * for the state of a single write.
 */

public final class JsonFilter {

    public static final String DEFAULT_ANONYMIZE_RAW_JSON_VALUE = "\"*\"";
    public static final String DEFAULT_PRUNE_RAW_JSON_VALUE = "\"[removed]\"";
    public static final String DEFAULT_TRUNCATE_MESSAGE = "... + ";

    /** Make room for quotes, without overflow */
    public static final int MAX_STRING_LENGTH = Integer.MAX_VALUE - 2;

    private final int maxStringLength;
    private final long maxSize;
    private final boolean maxSizeLimit;

    private final PathFilter pathFilter;

    private final String anonymizeRawJsonValue;
    private final int anonymizeCharSize;
    private final int anonymizeUtf8Size;

    private final String pruneRawJsonValue;
    private final int pruneCharSize;
    private final int pruneUtf8Size;

    private final String truncateMessage;
    private final char[] truncateMessageChars;
    private final byte[] truncateMessageUtf8;
    private final int truncateMessageLength;
    private final int truncateCharSize;
    private final int truncateUtf8Size;

    /**
     * Constructor.
     *
     * @param maxStringLength       max string length, or -1 for no limit
     * @param maxSize               max size, or -1 for no limit
     * @param anonymizes            anonymize path expressions
     * @param prunes                prune path expressions
     * @param anonymizeRawJsonValue raw JSON value for anonymized values
     * @param pruneRawJsonValue     raw JSON value for pruned values
     * @param truncateMessage       truncate message (not escaped)
     */

    public JsonFilter(int maxStringLength, int maxSize, List<String> anonymizes, List<String> prunes, String anonymizeRawJsonValue, String pruneRawJsonValue, String truncateMessage) {
        if (maxStringLength < -1 || maxStringLength > MAX_STRING_LENGTH) {
            throw new IllegalArgumentException("Expected -1 or a max string length from 0 to " + MAX_STRING_LENGTH + ", got " + maxStringLength);
        }
        if (maxSize < -1) {
            throw new IllegalArgumentException("Expected -1 or a non-negative max size, got " + maxSize);
        }
        this.maxStringLength = maxStringLength == -1 ? MAX_STRING_LENGTH : maxStringLength;
        this.maxSizeLimit = maxSize != -1;
        this.maxSize = maxSize == -1 ? Long.MAX_VALUE : maxSize;

        if ((anonymizes != null && !anonymizes.isEmpty()) || (prunes != null && !prunes.isEmpty())) {
            this.pathFilter = PathFilter.create(anonymizes, prunes);
        } else {
            this.pathFilter = null;
        }

        this.anonymizeRawJsonValue = anonymizeRawJsonValue;
        this.anonymizeCharSize = JsonOutputSize.getRawSize(anonymizeRawJsonValue, false);
        this.anonymizeUtf8Size = JsonOutputSize.getRawSize(anonymizeRawJsonValue, true);

        this.pruneRawJsonValue = pruneRawJsonValue;
        this.pruneCharSize = JsonOutputSize.getRawSize(pruneRawJsonValue, false);
        this.pruneUtf8Size = JsonOutputSize.getRawSize(pruneRawJsonValue, true);

        this.truncateMessage = truncateMessage;
        this.truncateMessageChars = truncateMessage.toCharArray();
        this.truncateMessageUtf8 = truncateMessage.getBytes(StandardCharsets.UTF_8);
        String escaped = JsonStrings.escape(truncateMessage);
        this.truncateMessageLength = escaped.length();
        this.truncateCharSize = JsonOutputSize.getRawSize(escaped, false);
        this.truncateUtf8Size = JsonOutputSize.getRawSize(escaped, true);
    }

    /**
     * Create the state for writing a single value.
     *
     * @param utf8 true if writing UTF-8 bytes, false if writing characters
     * @return new context
     */

    public FilterContext newContext(boolean utf8) {
        return new FilterContext(this, utf8, false);
    }

    /**
     * Create the state for writing a single value.
     *
     * @param utf8             true if writing UTF-8 bytes, false if writing characters
     * @param escapeSurrogates true if the generator escapes surrogates when writing strings as UTF-8, like Jackson 2 does by default
     * @return new context
     */

    public FilterContext newContext(boolean utf8, boolean escapeSurrogates) {
        return new FilterContext(this, utf8, escapeSurrogates);
    }

    /**
     * Max string length.
     *
     * @return max string length, very large if there is no limit
     */

    public int getMaxStringLength() {
        return maxStringLength;
    }

    public boolean hasMaxStringLength() {
        return maxStringLength != MAX_STRING_LENGTH;
    }

    public long getMaxSize() {
        return maxSize;
    }

    public boolean hasMaxSize() {
        return maxSizeLimit;
    }

    /**
     * Path expressions.
     *
     * @return compiled paths, or null if there are no anonymize or prune paths
     */

    public PathFilter getPathFilter() {
        return pathFilter;
    }

    public boolean hasPaths() {
        return pathFilter != null;
    }

    public String getAnonymizeRawJsonValue() {
        return anonymizeRawJsonValue;
    }

    public int getAnonymizeSize(boolean utf8) {
        return utf8 ? anonymizeUtf8Size : anonymizeCharSize;
    }

    public String getPruneRawJsonValue() {
        return pruneRawJsonValue;
    }

    public int getPruneSize(boolean utf8) {
        return utf8 ? pruneUtf8Size : pruneCharSize;
    }

    /**
     * Truncate message, not escaped.
     *
     * @return message
     */

    public String getTruncateMessage() {
        return truncateMessage;
    }

    public char[] getTruncateMessageChars() {
        return truncateMessageChars;
    }

    public byte[] getTruncateMessageUtf8() {
        return truncateMessageUtf8;
    }

    /**
     * Length of the escaped truncate message, which a string must be longer than to be truncated.
     *
     * @return length in characters
     */

    public int getTruncateMessageLength() {
        return truncateMessageLength;
    }

    /**
     * Size of the escaped truncate message in the output.
     *
     * @param utf8 true if writing UTF-8 bytes, false if characters
     * @return size
     */

    public int getTruncateMessageSize(boolean utf8) {
        return utf8 ? truncateUtf8Size : truncateCharSize;
    }
}
