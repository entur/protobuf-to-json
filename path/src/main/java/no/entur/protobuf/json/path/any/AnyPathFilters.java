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

    private AnyPathFilters(Map<String, FilterType> names) {
        this.names = names;
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
