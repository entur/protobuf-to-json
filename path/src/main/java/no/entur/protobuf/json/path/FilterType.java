package no.entur.protobuf.json.path;

/**
 * What to do with a value matched by a path expression.
 * <br><br>
 * Ported from json-log-filter (Apache-2.0).
 */

public enum FilterType {
    /** Keep the structure, but replace every scalar value. */
    ANON,
    /** Replace the whole value. */
    PRUNE;

    /**
     * Resolve two filter types which apply to the same value. The type which reveals the least wins,
     * i.e. prune takes precedence over anonymize.
     *
     * @param a filter type, or null
     * @param b filter type, or null
     * @return the strongest filter type, or null if both are null
     */

    public static FilterType strongest(FilterType a, FilterType b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return a == PRUNE || b == PRUNE ? PRUNE : ANON;
    }
}
