package no.entur.protobuf.json.path.any;

import no.entur.protobuf.json.path.FilterType;

/**
 * A field name to match at any depth, i.e. {@code $..name}.
 * <br><br>
 * Ported from json-log-filter (Apache-2.0).
 */

public class AnyPathFilter {

    public final String pathString;

    public final FilterType filterType;

    public AnyPathFilter(String pathString, FilterType filterType) {
        if (pathString.equals("*")) {
            throw new IllegalArgumentException("Unexpected any match for *");
        }
        this.pathString = pathString;
        this.filterType = filterType;
    }

    public FilterType getFilterType() {
        return filterType;
    }

    @Override
    public String toString() {
        return pathString + " (" + filterType + ")";
    }
}
