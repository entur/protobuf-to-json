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
}
