package no.entur.protobuf.json.path;

/**
 * Ported from json-log-filter (Apache-2.0).
 */

public class SinglePathItem extends PathItem {

    private final String fieldName;

    private PathItem next;

    public SinglePathItem(int level, String fieldName, PathItem parent) {
        super(level, parent);
        this.fieldName = fieldName;
    }

    public void setNext(PathItem next) {
        this.next = next;
    }

    @Override
    public PathItem matchPath(int level, String fieldName) {
        if (level == this.level && this.fieldName.equals(fieldName)) {
            return next;
        }
        return this;
    }

    @Override
    FilterType getTypeWithin(String[] fieldNames) {
        return contains(fieldNames, fieldName) ? null : next.getStrongestType();
    }

    @Override
    FilterType getStrongestType() {
        return next.getStrongestType();
    }
}
