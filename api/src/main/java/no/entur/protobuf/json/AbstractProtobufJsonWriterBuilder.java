package no.entur.protobuf.json;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import no.entur.protobuf.json.filter.JsonFilter;
import no.entur.protobuf.json.filter.JsonStrings;

/**
 * Builder with the filter options, as in json-log-filter:
 * <ul>
 *     <li>anonymize paths: keep the structure, but replace every scalar value with {@code "*"}</li>
 *     <li>prune paths: replace the whole value with {@code "[removed]"}</li>
 *     <li>max string length: truncate long string values to {@code "<start>... + <number of removed characters>"}</li>
 *     <li>max size: stop writing at the first value which does not fit, and close the open objects and arrays</li>
 * </ul>
 * Path expressions refer to the JSON field names, i.e. {@code $.customer.email}, {@code /customer/email},
 * {@code $.*.email} or {@code $..email} (any depth). See {@linkplain no.entur.protobuf.json.path.PathExpressions}.
 *
 * @param <B> builder type
 */

public abstract class AbstractProtobufJsonWriterBuilder<B extends AbstractProtobufJsonWriterBuilder<B>> {

    protected int maxStringLength = -1;
    protected int maxSize = -1;

    protected final List<String> anonymizeFilters = new ArrayList<>();
    protected final List<String> pruneFilters = new ArrayList<>();

    protected String anonymizeRawJsonValue = JsonFilter.DEFAULT_ANONYMIZE_RAW_JSON_VALUE;
    protected String pruneRawJsonValue = JsonFilter.DEFAULT_PRUNE_RAW_JSON_VALUE;
    protected String truncateMessage = JsonFilter.DEFAULT_TRUNCATE_MESSAGE;

    @SuppressWarnings("unchecked")
    protected B self() {
        return (B) this;
    }

    /**
     * Truncate string values longer than this. Field names, and strings which are not text (64-bit integers, enum names,
     * timestamps, durations, and NaN and infinity), are never truncated.
     *
     * @param maxStringLength max length in UTF-16 characters, or -1 for no limit
     * @return this builder
     */

    public B withMaxStringLength(int maxStringLength) {
        if (maxStringLength < -1 || maxStringLength > JsonFilter.MAX_STRING_LENGTH) {
            throw new IllegalArgumentException("Expected -1 or a max string length from 0 to " + JsonFilter.MAX_STRING_LENGTH + ", got " + maxStringLength);
        }
        this.maxStringLength = maxStringLength;
        return self();
    }

    /**
     * Max size of the JSON output, in bytes when writing to an output stream, otherwise in characters.
     * The output is cut at the first value which does not fit, and the open objects and arrays are closed.
     *
     * @param maxSize max size, or -1 for no limit
     * @return this builder
     */

    public B withMaxSize(int maxSize) {
        if (maxSize < -1) {
            throw new IllegalArgumentException("Expected -1 or a non-negative max size, got " + maxSize);
        }
        this.maxSize = maxSize;
        return self();
    }

    public B withAnonymizePaths(String... paths) {
        return withAnonymizePaths(Arrays.asList(paths));
    }

    public B withAnonymizePaths(Collection<String> paths) {
        anonymizeFilters.addAll(paths);
        return self();
    }

    public B withPrunePaths(String... paths) {
        return withPrunePaths(Arrays.asList(paths));
    }

    public B withPrunePaths(Collection<String> paths) {
        pruneFilters.addAll(paths);
        return self();
    }

    /**
     * Anonymize fields with these names, at any depth.
     *
     * @param keys field names
     * @return this builder
     */

    public B withAnonymizeKeys(String... keys) {
        return withAnonymizeKeys(Arrays.asList(keys));
    }

    public B withAnonymizeKeys(Collection<String> keys) {
        for (String key : keys) {
            anonymizeFilters.add("$.." + key);
        }
        return self();
    }

    /**
     * Prune fields with these names, at any depth.
     *
     * @param keys field names
     * @return this builder
     */

    public B withPruneKeys(String... keys) {
        return withPruneKeys(Arrays.asList(keys));
    }

    public B withPruneKeys(Collection<String> keys) {
        for (String key : keys) {
            pruneFilters.add("$.." + key);
        }
        return self();
    }

    /**
     * Replacement for anonymized values, written as a JSON string.
     *
     * @param message message
     * @return this builder
     */

    public B withAnonymizeMessage(String message) {
        return withAnonymizeRawJsonValue(JsonStrings.quote(message));
    }

    /**
     * Replacement for pruned values, written as a JSON string.
     *
     * @param message message
     * @return this builder
     */

    public B withPruneMessage(String message) {
        return withPruneRawJsonValue(JsonStrings.quote(message));
    }

    /**
     * Replacement for anonymized values, written as is.
     *
     * @param raw JSON value, i.e. {@code "\"*\""} or {@code "null"}
     * @return this builder
     */

    public B withAnonymizeRawJsonValue(String raw) {
        this.anonymizeRawJsonValue = raw;
        return self();
    }

    /**
     * Replacement for pruned values, written as is.
     *
     * @param raw JSON value, i.e. {@code "\"[removed]\""} or {@code "null"}
     * @return this builder
     */

    public B withPruneRawJsonValue(String raw) {
        this.pruneRawJsonValue = raw;
        return self();
    }

    /**
     * Text between the start of a truncated string and the number of removed characters.
     *
     * @param message message, i.e. {@code "... + "}
     * @return this builder
     */

    public B withTruncateMessage(String message) {
        this.truncateMessage = message;
        return self();
    }

    /**
     * Compile the filter options.
     *
     * @return the filter, or null if no filter option is set
     * @throws IllegalArgumentException if a path expression is not valid
     */

    protected JsonFilter buildFilter() {
        if (anonymizeFilters.isEmpty() && pruneFilters.isEmpty() && maxStringLength == -1 && maxSize == -1) {
            return null;
        }
        return new JsonFilter(maxStringLength, maxSize, anonymizeFilters, pruneFilters, anonymizeRawJsonValue, pruneRawJsonValue, truncateMessage);
    }
}
