package no.entur.protobuf.json.path;

import java.util.List;

/**
 * Ported from json-log-filter (Apache-2.0).
 */

public class MultiPathItem extends PathItem {

    private final String[] fieldNames;

    private final PathItem[] next;

    public MultiPathItem(List<String> fieldNames, int level, PathItem parent) {
        this(fieldNames.toArray(new String[0]), level, parent);
    }

    public MultiPathItem(String[] fieldNames, int level, PathItem parent) {
        super(level, parent);

        // note: always expect two or more field names
        this.fieldNames = fieldNames;
        this.next = new PathItem[fieldNames.length];
    }

    public void setNext(PathItem next, int i) {
        this.next[i] = next;
    }

    @Override
    public PathItem matchPath(int level, String fieldName) {
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
}
