package no.entur.protobuf.json.path;

/**
 * A state in the path matching state machine. The graph of path items is immutable, so it can be shared between threads;
 * the state of a traversal is the current path item and the current level.
 * <br><br>
 * Levels count JSON objects only, from 1 for the root object. Arrays are transparent.
 * <br><br>
 * Ported from json-log-filter (Apache-2.0).
 */

public abstract class PathItem {

    // from one and upwards
    protected final int level;
    protected final PathItem parent;

    public PathItem(int level, PathItem parent) {
        this.level = level;
        this.parent = parent;
    }

    /**
     * Match a field name.
     *
     * @param level     current level
     * @param fieldName field name
     * @return the next item if the field name matched, otherwise this item (or the wildcard branch)
     */

    public abstract PathItem matchPath(int level, String fieldName);

    public FilterType getType() {
        return null;
    }

    /**
     * Step back up to the item for the given level, i.e. after leaving objects.
     *
     * @param level current level
     * @return this or an ancestor
     */

    public PathItem constrain(int level) {
        if (this.level <= level) {
            return this;
        }
        return parent.constrain(level);
    }

    public int getLevel() {
        return level;
    }

    public boolean hasType() {
        return false;
    }
}
