package no.entur.protobuf.json.path;

import no.entur.protobuf.json.path.any.AnyPathFilters;

/**
 * Matches the field names of one JSON document against compiled path expressions, in document order. Holds the state of
 * the traversal, so it is not thread-safe: create one per document with {@linkplain PathFilter#newMatcher()}.
 * <br><br>
 * Levels count JSON objects only, from 1 for the root object. Arrays are transparent.
 */

public final class PathMatcher {

    private final AnyPathFilters anyPathFilters;
    private PathItem pathItem; // null without absolute paths

    PathMatcher(PathItem pathItem, AnyPathFilters anyPathFilters) {
        this.pathItem = pathItem;
        this.anyPathFilters = anyPathFilters;
    }

    /**
     * Match a field name in the current object. Call it for every field name, in document order, except within values
     * which are anonymized or pruned as a whole.
     *
     * @param level current level, the number of open objects
     * @param name  field name
     * @return {@linkplain FilterType#PRUNE}, {@linkplain FilterType#ANON}, or null if no path matched
     */

    public FilterType matchName(int level, String name) {
        FilterType type = null;
        if (pathItem != null) {
            PathItem item = pathItem.constrain(level).matchPath(level, name);
            if (item.hasType()) {
                type = item.getType();
                item = item.constrain(level);
            }
            pathItem = item;
        }
        if (anyPathFilters != null) {
            type = FilterType.strongest(type, anyPathFilters.matchPath(name));
        }
        return type;
    }

    /**
     * Match the paths which could reach into a value which is written opaquely in the current object, i.e. the payload of an
     * unresolved google.protobuf.Any, written as base64 next to its type url: the any-paths, and the absolute paths which go
     * on into the current object other than through the given field names (a wildcard counts). Does not change the state of
     * the matcher.
     *
     * @param level      current level, the number of open objects
     * @param fieldNames names of the fields of the current object which are written as they are
     * @return {@linkplain FilterType#PRUNE} if such a path prunes, otherwise {@linkplain FilterType#ANON} if such a path
     * anonymizes, or null if no path could reach into the value
     */

    public FilterType matchOpaque(int level, String... fieldNames) {
        FilterType type = null;
        if (pathItem != null) {
            type = pathItem.constrain(level).getTypeWithin(level, fieldNames);
        }
        if (anyPathFilters != null) {
            type = FilterType.strongest(type, anyPathFilters.getStrongestType());
        }
        return type;
    }
}
