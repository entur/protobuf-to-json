package no.entur.protobuf.json.path.any;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import no.entur.protobuf.json.path.FilterType;

/**
 * Field names to match at any depth.
 * <br><br>
 * Ported from json-log-filter (Apache-2.0).
 */

public class AnyPathFilters {

    public static AnyPathFilters create(AnyPathFilter... any) {
        return create(Arrays.asList(any));
    }

    public static AnyPathFilters create(List<AnyPathFilter> any) {
        // if the same name is both pruned and anonymized, prune takes precedence
        Map<String, FilterType> map = new HashMap<>();
        for (AnyPathFilter anyPathFilter : any) {
            map.merge(anyPathFilter.pathString, anyPathFilter.filterType, FilterType::strongest);
        }
        return new AnyPathFilters(map);
    }

    private final Map<String, FilterType> names;
    private final FilterType strongestType;

    private AnyPathFilters(Map<String, FilterType> names) {
        this.names = names;
        FilterType type = null;
        for (FilterType value : names.values()) {
            type = FilterType.strongest(type, value);
        }
        this.strongestType = type;
    }

    /**
     * The strongest filter type of all the field names, i.e. for a value which is written opaquely, within which any of
     * them could match.
     *
     * @return the strongest filter type, or null if there are no field names
     */

    public FilterType getStrongestType() {
        return strongestType;
    }

    /**
     * Match a field name.
     *
     * @param key field name
     * @return filter type, or null if not matched
     */

    public FilterType matchPath(String key) {
        return names.get(key);
    }
}
