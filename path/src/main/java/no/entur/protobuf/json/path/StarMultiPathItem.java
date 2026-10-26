package no.entur.protobuf.json.path;

import java.util.List;

/**
 * Field names and a wildcard; field names which do not match go to the wildcard branch.
 * <br><br>
 * Ported from json-log-filter (Apache-2.0).
 */

public class StarMultiPathItem extends PathItem {

    private final String[] fieldNames;

    private final PathItem[] next;
    private PathItem any;

    public StarMultiPathItem(List<String> fieldNames, int level, PathItem parent) {
        this(fieldNames.toArray(new String[0]), level, parent);
    }

    public StarMultiPathItem(String[] fieldNames, int level, PathItem parent) {
        super(level, parent);

        this.fieldNames = fieldNames;
        this.next = new PathItem[fieldNames.length];
    }

    public void setNext(PathItem next, int i) {
        this.next[i] = next;
    }

    public void setAny(PathItem any) {
        this.any = any;
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
        return any;
    }
}
