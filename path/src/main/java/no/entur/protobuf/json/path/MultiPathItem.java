package no.entur.protobuf.json.path;

import java.util.List;

/**
 * Ported from json-log-filter (Apache-2.0).
 */

class MultiPathItem extends PathItem {

    private final String[] fieldNames;

    private final PathItem[] next;

    MultiPathItem(List<String> fieldNames, int level, PathItem parent) {
        this(fieldNames.toArray(new String[0]), level, parent);
    }

    MultiPathItem(String[] fieldNames, int level, PathItem parent) {
        super(level, parent);

        // note: always expect two or more field names
        this.fieldNames = fieldNames;
        this.next = new PathItem[fieldNames.length];
    }

    void setNext(PathItem next, int i) {
        this.next[i] = next;
    }

    @Override
    PathItem matchPath(int level, String fieldName) {
        if (level != this.level) {
            return this;
        }
        for (int i = 0; i < fieldNames.length; i++) {
            if (fieldName.equals(fieldNames[i])) {
                return next[i];
            }
        }
        return this;
    }

    @Override
    FilterType getTypeWithin(String[] excluded) {
        FilterType type = null;
        for (int i = 0; i < fieldNames.length; i++) {
            if (!contains(excluded, fieldNames[i])) {
                type = FilterType.strongest(type, next[i].getStrongestType());
            }
        }
        return type;
    }

    @Override
    FilterType getStrongestType() {
        FilterType type = null;
        for (PathItem item : next) {
            type = FilterType.strongest(type, item.getStrongestType());
        }
        return type;
    }
}
