package no.entur.protobuf.json.path;

/**
 * Matches any field name.
 * <br><br>
 * Ported from json-log-filter (Apache-2.0).
 */

class StarPathItem extends PathItem {

    private PathItem next;

    StarPathItem(int level, PathItem parent) {
        super(level, parent);
    }

    void setNext(PathItem next) {
        this.next = next;
    }

    @Override
    PathItem matchPath(int level, String fieldName) {
        if (level != this.level) {
            return this;
        }
        return next;
    }

    @Override
    FilterType getTypeWithin(String[] fieldNames) {
        // the wildcard also matches the fields which are not listed
        return next.getStrongestType();
    }

    @Override
    FilterType getStrongestType() {
        return next.getStrongestType();
    }
}
