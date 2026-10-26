package no.entur.protobuf.json.path;

/**
 * Matches any field name.
 * <br><br>
 * Ported from json-log-filter (Apache-2.0).
 */

public class StarPathItem extends PathItem {

    private PathItem next;

    public StarPathItem(int level, PathItem parent) {
        super(level, parent);
    }

    public void setNext(PathItem next) {
        this.next = next;
    }

    @Override
    public PathItem matchPath(int level, String fieldName) {
        if (level != this.level) {
            return this;
        }
        return next;
    }
}
