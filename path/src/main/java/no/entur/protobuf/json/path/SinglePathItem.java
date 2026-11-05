package no.entur.protobuf.json.path;

/**
 * Ported from json-log-filter (Apache-2.0).
 */

class SinglePathItem extends PathItem {

    private final String fieldName;

    private PathItem next;

    SinglePathItem(int level, String fieldName, PathItem parent) {
        super(level, parent);
        this.fieldName = fieldName;
    }

    void setNext(PathItem next) {
        this.next = next;
    }

    @Override
    PathItem matchPath(int level, String fieldName) {
        if (level == this.level && this.fieldName.equals(fieldName)) {
            return next;
        }
        return this;
    }
}
