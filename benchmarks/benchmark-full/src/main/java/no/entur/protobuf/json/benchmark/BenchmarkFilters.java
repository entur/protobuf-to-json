package no.entur.protobuf.json.benchmark;

import no.entur.protobuf.json.AbstractProtobufJsonWriterBuilder;

import java.util.List;
import java.util.Map;

/**
 * The filters of the filtered benchmarks, like in the benchmarks of json-log-filter, with paths of fields in each payload:
 * <ul>
 *     <li>none: no filtering, the plain writers</li>
 *     <li>maxStringLength: strings longer than 32 characters are truncated</li>
 *     <li>maxSize: half the size of the unfiltered output</li>
 *     <li>anyPaths3: anonymize three field names, at any depth</li>
 *     <li>mixedPaths5: anonymize an absolute path and an any-path, prune an absolute path, a path with a wildcard and an any-path</li>
 *     <li>all: mixedPaths5, maxStringLength and maxSize</li>
 * </ul>
 * The same as for the lite benchmarks.
 */

public final class BenchmarkFilters {

    public static final int MAX_STRING_LENGTH = 32;

    public static final Map<String, List<String>> ANY_PATHS = Map.of(
            "small", List.of("$..optionalString", "$..optionalInt64", "$..a"),
            "medium", List.of("$..login", "$..fullName", "$..description"),
            "large", List.of("$..name", "$..jsonName", "$..typeUrl"),
            "xlarge", List.of("$..login", "$..fullName", "$..description"),
            "xxlarge", List.of("$..login", "$..fullName", "$..description"));

    public static final Map<String, List<String>> MIXED_ANONYMIZE = Map.of(
            "small", List.of("$.optionalString", "$..optionalInt32"),
            "medium", List.of("$.items.owner.login", "$..description"),
            "large", List.of("$.fields.name", "$..jsonName"),
            "xlarge", List.of("$.items.owner.login", "$..description"),
            "xxlarge", List.of("$.items.owner.login", "$..description"));

    public static final Map<String, List<String>> MIXED_PRUNE = Map.of(
            "small", List.of("$.optionalTimestamp", "$.*.a", "$..optionalInt64"),
            "medium", List.of("$.items.cloneUrl", "$.items.*.avatarUrl", "$..htmlUrl"),
            "large", List.of("$.oneofs", "$.*.fileName", "$..typeUrl"),
            "xlarge", List.of("$.items.cloneUrl", "$.items.*.avatarUrl", "$..htmlUrl"),
            "xxlarge", List.of("$.items.cloneUrl", "$.items.*.avatarUrl", "$..htmlUrl"));

    private BenchmarkFilters() {
    }

    /**
     * @param builder        writer builder
     * @param filter         none, maxStringLength, maxSize, anyPaths3, mixedPaths5 or all
     * @param payload        small, medium, large, xlarge or xxlarge
     * @param unfilteredSize size of the unfiltered output
     * @return the builder
     */

    public static <B extends AbstractProtobufJsonWriterBuilder<B>> B configure(B builder, String filter, String payload, int unfilteredSize) {
        boolean all = filter.equals("all");
        if (all || filter.equals("maxStringLength")) {
            builder.withMaxStringLength(MAX_STRING_LENGTH);
        }
        if (all || filter.equals("maxSize")) {
            builder.withMaxSize(unfilteredSize / 2);
        }
        if (filter.equals("anyPaths3")) {
            builder.withAnonymizePaths(ANY_PATHS.get(payload));
        }
        if (all || filter.equals("mixedPaths5")) {
            builder.withAnonymizePaths(MIXED_ANONYMIZE.get(payload)).withPrunePaths(MIXED_PRUNE.get(payload));
        }
        return builder;
    }
}
