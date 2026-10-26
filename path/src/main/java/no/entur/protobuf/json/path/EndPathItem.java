package no.entur.protobuf.json.path;

/**
 * A matched path. Call {@linkplain #constrain(int)} to continue matching.
 * <br><br>
 * Ported from json-log-filter (Apache-2.0).
 */

public class EndPathItem extends PathItem {

    public final FilterType filterType;

    public EndPathItem(int level, PathItem parent, FilterType filterType) {
        super(level, parent);
        this.filterType = filterType;
    }

    @Override
    public FilterType getType() {
        return filterType;
    }

    @Override
    public PathItem matchPath(int level, String fieldName) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean hasType() {
        return true;
    }
}
