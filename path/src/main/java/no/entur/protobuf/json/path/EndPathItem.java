package no.entur.protobuf.json.path;

/**
 * A matched path. Call {@linkplain #constrain(int)} to continue matching.
 * <br><br>
 * Ported from json-log-filter (Apache-2.0).
 */

class EndPathItem extends PathItem {

    final FilterType filterType;

    EndPathItem(int level, PathItem parent, FilterType filterType) {
        super(level, parent);
        this.filterType = filterType;
    }

    @Override
    FilterType getType() {
        return filterType;
    }

    @Override
    PathItem matchPath(int level, String fieldName) {
        throw new UnsupportedOperationException();
    }

    @Override
    boolean hasType() {
        return true;
    }
}
