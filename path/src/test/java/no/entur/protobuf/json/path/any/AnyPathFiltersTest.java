package no.entur.protobuf.json.path.any;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import no.entur.protobuf.json.path.FilterType;

public class AnyPathFiltersTest {

    @Test
    public void match() {
        AnyPathFilters filters = AnyPathFilters.create(new AnyPathFilter("a", FilterType.ANON), new AnyPathFilter("b", FilterType.PRUNE));

        assertEquals(FilterType.ANON, filters.matchPath("a"));
        assertEquals(FilterType.PRUNE, filters.matchPath("b"));
        assertNull(filters.matchPath("c"));
        assertNull(filters.matchPath(""));
    }

    @Test
    public void prunePrecedence() {
        AnyPathFilters filters = AnyPathFilters.create(new AnyPathFilter("a", FilterType.ANON), new AnyPathFilter("a", FilterType.PRUNE), new AnyPathFilter("a", FilterType.ANON));

        assertEquals(FilterType.PRUNE, filters.matchPath("a"));
    }

    @Test
    public void starIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new AnyPathFilter("*", FilterType.ANON));
    }
}
