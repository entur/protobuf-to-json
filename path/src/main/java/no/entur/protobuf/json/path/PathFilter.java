package no.entur.protobuf.json.path;

import java.util.ArrayList;
import java.util.List;

import no.entur.protobuf.json.path.any.AnyPathFilter;
import no.entur.protobuf.json.path.any.AnyPathFilters;

/**
 * Compiled anonymize and prune expressions: a {@linkplain PathItem} state machine for absolute paths, and a lookup for any-paths.
 * Immutable, so it can be shared between threads.
 * <br><br>
 * Matching a field name, given the current item and level (the number of open objects):
 * <pre>
 * item = item.constrain(level).matchPath(level, name);
 * if (item.hasType()) {
 *     type = item.getType();
 *     item = item.constrain(level);
 * }
 * type = FilterType.strongest(type, anyPathFilters.matchPath(name)); // if any-paths are present
 * </pre>
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
     * Root of the state machine for absolute paths. Never null; without absolute paths it never matches.
     *
     * @return root item, for level 1
     */

    public PathItem getPathItem() {
        return pathItem;
    }

    /**
     * Any-path filters.
     *
     * @return any-path filters, or null if there are none
     */

    public AnyPathFilters getAnyPathFilters() {
        return anyPathFilters;
    }

    public boolean hasAbsolutePaths() {
        return absolutePaths;
    }

    public boolean hasAnyPaths() {
        return anyPathFilters != null;
    }
}
