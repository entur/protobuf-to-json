package no.entur.protobuf.json.path;

import java.util.List;

/**
 * Field names and a wildcard; field names which do not match go to the wildcard branch.
 * <br><br>
 * Ported from json-log-filter (Apache-2.0).
 */

class StarMultiPathItem extends PathItem {

    private final String[] fieldNames;

    private final PathItem[] next;
    private PathItem any;

    StarMultiPathItem(List<String> fieldNames, int level, PathItem parent) {
        this(fieldNames.toArray(new String[0]), level, parent);
    }

    StarMultiPathItem(String[] fieldNames, int level, PathItem parent) {
        super(level, parent);

        this.fieldNames = fieldNames;
        this.next = new PathItem[fieldNames.length];
    }

    void setNext(PathItem next, int i) {
        this.next[i] = next;
    }

    void setAny(PathItem any) {
        this.any = any;
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
        return any;
    }

    @Override
    FilterType getTypeWithin(String[] excluded) {
        // the wildcard also matches the fields which are not listed
        FilterType type = any.getStrongestType();
        for (int i = 0; i < fieldNames.length; i++) {
            if (!contains(excluded, fieldNames[i])) {
                type = FilterType.strongest(type, next[i].getStrongestType());
            }
        }
        return type;
    }

    @Override
    FilterType getStrongestType() {
        FilterType type = any.getStrongestType();
        for (PathItem item : next) {
            type = FilterType.strongest(type, item.getStrongestType());
        }
        return type;
    }
}
