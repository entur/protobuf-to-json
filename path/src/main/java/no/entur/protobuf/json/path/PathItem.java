package no.entur.protobuf.json.path;

/**
 * A state in the path matching state machine. The graph of path items is immutable, so it can be shared between threads;
 * the state of a traversal is the current path item and the current level.
 * <br><br>
 * Levels count JSON objects only, from 1 for the root object. Arrays are transparent.
 * <br><br>
 * Ported from json-log-filter (Apache-2.0).
 */

abstract class PathItem {

    // from one and upwards
    final int level;
    final PathItem parent;

    PathItem(int level, PathItem parent) {
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

    abstract PathItem matchPath(int level, String fieldName);

    FilterType getType() {
        return null;
    }

    /**
     * Step back up to the item for the given level, i.e. after leaving objects.
     *
     * @param level current level
     * @return this or an ancestor
     */

    PathItem constrain(int level) {
        if (this.level <= level) {
            return this;
        }
        return parent.constrain(level);
    }

    int getLevel() {
        return level;
    }

    boolean hasType() {
        return false;
    }

    /**
     * The strongest filter type of the paths which go on from this item into the fields of an object at the given level, other
     * than through the given field names. These are the paths which could match within the object if only those fields can be
     * matched, and the rest of its content is written opaquely, i.e. the payload of an unresolved google.protobuf.Any. A
     * wildcard counts, as it would match the hidden fields too. Paths covered by a shorter path are not counted, as they are
     * not part of the state machine.
     *
     * @param level      level of the object, i.e. the current level
     * @param fieldNames names of the fields of the object which can be matched
     * @return the strongest filter type, or null if no path goes on into the object other than through the field names
     */

    FilterType getTypeWithin(int level, String... fieldNames) {
        if (this.level != level) {
            return null;
        }
        return getTypeWithin(fieldNames);
    }

    /**
     * @param fieldNames names of fields which are not counted
     * @return the strongest filter type of the paths which go on from this item, other than through the field names
     */

    FilterType getTypeWithin(String[] fieldNames) {
        return null;
    }

    /**
     * @return the strongest filter type of this item and the items after it, or null if there is none
     */

    FilterType getStrongestType() {
        return null;
    }

    static boolean contains(String[] names, String name) {
        for (String candidate : names) {
            if (candidate.equals(name)) {
                return true;
            }
        }
        return false;
    }
}
