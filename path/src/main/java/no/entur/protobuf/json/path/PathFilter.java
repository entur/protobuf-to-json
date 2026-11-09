package no.entur.protobuf.json.path;

import java.util.ArrayList;
import java.util.List;

import no.entur.protobuf.json.path.any.AnyPathFilter;
import no.entur.protobuf.json.path.any.AnyPathFilters;

/**
 * Compiled anonymize and prune expressions: a state machine for absolute paths, and a lookup for any-paths.
 * Immutable, so it can be shared between threads; match the field names of a document with a {@linkplain PathMatcher}
 * from {@linkplain #newMatcher()}.
 */

public final class PathFilter {

    private static final PathItemFactory FACTORY = new PathItemFactory();
    private static final ExpressionNodeFactory NODE_FACTORY = new ExpressionNodeFactory();

    /**
     * Compile path expressions.
     *
     * @param anonymizes anonymize expressions, or null
     * @param prunes     prune expressions, or null
     * @return compiled expressions
     * @throws IllegalArgumentException if an expression is not valid
     */

    public static PathFilter create(List<String> anonymizes, List<String> prunes) {
        List<String> paths = new ArrayList<>();
        List<FilterType> types = new ArrayList<>();

        List<AnyPathFilter> any = new ArrayList<>();
        if (prunes != null) {
            for (String prune : prunes) {
                PathExpressions.validatePruneExpression(prune);
                if (PathExpressions.hasAnyPrefix(prune)) {
                    any.add(new AnyPathFilter(PathExpressions.removeAnyPrefix(prune), FilterType.PRUNE));
                } else {
                    paths.add(prune);
                    types.add(FilterType.PRUNE);
                }
            }
        }
        if (anonymizes != null) {
            for (String anonymize : anonymizes) {
                PathExpressions.validateAnonymizeExpression(anonymize);
                if (PathExpressions.hasAnyPrefix(anonymize)) {
                    any.add(new AnyPathFilter(PathExpressions.removeAnyPrefix(anonymize), FilterType.ANON));
                } else {
                    paths.add(anonymize);
                    types.add(FilterType.ANON);
                }
            }
        }
        AnyPathFilters anyPathFilters = any.isEmpty() ? null : AnyPathFilters.create(any);

        PathItem pathItem = FACTORY.create(NODE_FACTORY.toExpressionNode(paths, types));

        return new PathFilter(pathItem, anyPathFilters, !paths.isEmpty());
    }

    private final PathItem pathItem;
    private final AnyPathFilters anyPathFilters;
    private final boolean absolutePaths;

    private PathFilter(PathItem pathItem, AnyPathFilters anyPathFilters, boolean absolutePaths) {
        this.pathItem = pathItem;
        this.anyPathFilters = anyPathFilters;
        this.absolutePaths = absolutePaths;
    }

    /**
     * Create a matcher, for matching the field names of one document.
     *
     * @return new matcher
     */

    public PathMatcher newMatcher() {
        return new PathMatcher(absolutePaths ? pathItem : null, anyPathFilters);
    }

    /**
     * Whether there are absolute paths, e.g. {@code $.a.b} or {@code $.a.*.b}.
     *
     * @return true if there are absolute paths
     */

    public boolean hasAbsolutePaths() {
        return absolutePaths;
    }

    /**
     * Whether there are any-paths, e.g. {@code $..b}.
     *
     * @return true if there are any-paths
     */

    public boolean hasAnyPaths() {
        return anyPathFilters != null;
    }
}
