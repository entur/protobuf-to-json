package no.entur.protobuf.json;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import no.entur.protobuf.json.filter.JsonFilter;
import no.entur.protobuf.json.filter.JsonStrings;
import no.entur.protobuf.json.filter.JsonSyntax;

/**
 * Builder with the filter options, as in json-log-filter:
 * <ul>
 *     <li>anonymize paths: keep the structure, but replace every scalar value with {@code "*"}. Field names and map keys are written as-is</li>
 *     <li>prune paths: replace the whole value with {@code "[removed]"}</li>
 *     <li>max string length: truncate long string values to {@code "<start>... + <number of removed characters>"}</li>
 *     <li>max size: stop writing at the first value which does not fit, and close the open objects and arrays</li>
 * </ul>
 * Path expressions refer to the JSON field names, i.e. {@code $.customer.email}, {@code $.*.email}
 * or {@code $..email} (any depth). There is no slash notation. See {@linkplain no.entur.protobuf.json.path.PathExpressions}.
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

    protected UnresolvedAny unresolvedAny = UnresolvedAny.FAIL;

    @SuppressWarnings("unchecked")
    protected B self() {
        return (B) this;
    }

    /**
     * Truncate string values longer than this. Field names and map keys, and strings which are not text (64-bit integers,
     * enum names, timestamps, durations, and NaN and infinity), are never truncated.
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
     * The output is cut at the first value which does not fit, and the open objects and arrays are closed;
     * {@linkplain ProtobufJsonWriter#write(Object, Object)} then returns false.
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

    /**
     * Prune these paths. A present value is replaced and not written. A proto3 scalar without presence is still read,
     * to tell it apart from the default. See the README.
     *
     * @param paths path expressions
     * @return this builder
     */

    public B withPrunePaths(String... paths) {
        return withPrunePaths(Arrays.asList(paths));
    }

    public B withPrunePaths(Collection<String> paths) {
        pruneFilters.addAll(paths);
        return self();
    }

    /**
     * Anonymize fields with these names, at any depth, including a map entry whose key is one of these names.
     * The name itself is still written.
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
     * Prune fields with these names, at any depth, including a map entry whose key is one of these names.
     * The name itself is still written.
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
     * Replacement for anonymized values, written as a JSON string. The default is {@code "*"}.
     *
     * @param message message, not null
     * @return this builder
     */

    public B withAnonymizeMessage(String message) {
        if (message == null) {
            throw new IllegalArgumentException("Expected an anonymize message");
        }
        return withAnonymizeRawJsonValue(JsonStrings.quote(message));
    }

    /**
     * Replacement for pruned values, written as a JSON string. The default is {@code "[removed]"}.
     *
     * @param message message, not null
     * @return this builder
     */

    public B withPruneMessage(String message) {
        if (message == null) {
            throw new IllegalArgumentException("Expected a prune message");
        }
        return withPruneRawJsonValue(JsonStrings.quote(message));
    }

    /**
     * Replacement for anonymized values, written as is.
     *
     * @param raw a single JSON value, for example {@code "\"*\""} or {@code "null"}
     * @return this builder
     * @throws IllegalArgumentException if not a single JSON value
     */

    public B withAnonymizeRawJsonValue(String raw) {
        JsonSyntax.requireReplacement(raw, "anonymize");
        this.anonymizeRawJsonValue = raw;
        return self();
    }

    /**
     * Replacement for pruned values, written as is.
     *
     * @param raw a single JSON value, for example {@code "\"[removed]\""} or {@code "null"}
     * @return this builder
     * @throws IllegalArgumentException if not a single JSON value
     */

    public B withPruneRawJsonValue(String raw) {
        JsonSyntax.requireReplacement(raw, "prune");
        this.pruneRawJsonValue = raw;
        return self();
    }

    /**
     * Text between the start of a truncated string and the number of removed characters, escaped as needed. The default is
     * {@code "... + "}, which truncates to {@code "<start>... + <number of removed characters>"}.
     *
     * @param message message, not null
     * @return this builder
     */

    public B withTruncateMessage(String message) {
        if (message == null) {
            throw new IllegalArgumentException("Expected a truncate message");
        }
        this.truncateMessage = message;
        return self();
    }

    /**
     * What to do when a google.protobuf.Any type is not registered.
     * {@link UnresolvedAny#FAIL}, the default, matches JsonFormat: the write fails.
     * {@link UnresolvedAny#VALUE} writes <code>{"@type": url, "@value": base64}</code> instead, without parsing the payload;
     * a filtering writer prunes or anonymizes the payload if a path could have matched within it.
     *
     * @param unresolvedAny behaviour, not null
     * @return this builder
     */

    public B withUnresolvedAny(UnresolvedAny unresolvedAny) {
        if (unresolvedAny == null) {
            throw new IllegalArgumentException("Expected an unresolved Any behaviour");
        }
        this.unresolvedAny = unresolvedAny;
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
