package no.entur.protobuf.json.path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

public class PathFilterTest {

    /**
     * Walk a document given as field names per level, i.e. a, b, c visits a at level 1, b at level 2 and c at level 3,
     * stepping like the writers do.
     */

    private static FilterType match(PathFilter filter, String... names) {
        PathItem item = filter.getPathItem();
        FilterType type = null;
        for (int i = 0; i < names.length; i++) {
            int level = i + 1;
            item = item.constrain(level).matchPath(level, names[i]);
            type = null;
            if (item.hasType()) {
                type = item.getType();
                item = item.constrain(level);
            }
            if (filter.hasAnyPaths()) {
                type = FilterType.strongest(type, filter.getAnyPathFilters().matchPath(names[i]));
            }
            if (type != null) {
                return i == names.length - 1 ? type : null;
            }
        }
        return type;
    }

    @Test
    public void absoluteAndAnyPaths() {
        PathFilter filter = PathFilter.create(Arrays.asList("$.a.b", "$..secret"), Collections.singletonList("$.c.*.d"));

        assertTrue(filter.hasAbsolutePaths());
        assertTrue(filter.hasAnyPaths());

        assertEquals(FilterType.ANON, match(filter, "a", "b"));
        assertNull(match(filter, "a", "c"));
        assertEquals(FilterType.ANON, match(filter, "x", "y", "secret"));
        assertEquals(FilterType.ANON, match(filter, "secret"));
        assertEquals(FilterType.PRUNE, match(filter, "c", "x", "d"));
        assertNull(match(filter, "c", "x", "e"));
    }

    @Test
    public void anyPathPrunePrecedence() {
        List<String> anonymizes = Arrays.asList("$..a", "$.b");
        List<String> prunes = Arrays.asList("$..a", "$..b");
        PathFilter filter = PathFilter.create(anonymizes, prunes);

        assertEquals(FilterType.PRUNE, match(filter, "a"));
        assertEquals(FilterType.PRUNE, match(filter, "b"));
    }

    @Test
    public void slashIsPartOfTheName() {
        PathFilter filter = PathFilter.create(Arrays.asList("$.a/b.c", "$..x/y"), null);

        assertEquals(FilterType.ANON, match(filter, "a/b", "c"));
        assertNull(match(filter, "a", "b", "c"));
        assertEquals(FilterType.ANON, match(filter, "q", "x/y"));
        assertNull(match(filter, "x", "y"));
    }

    @Test
    public void onlyAnyPaths() {
        PathFilter filter = PathFilter.create(Collections.singletonList("$..a"), null);
        assertFalse(filter.hasAbsolutePaths());
        assertEquals(FilterType.ANON, match(filter, "x", "a"));
        assertNull(match(filter, "x", "b"));
    }

    @Test
    public void noPaths() {
        PathFilter filter = PathFilter.create(null, null);
        assertFalse(filter.hasAbsolutePaths());
        assertFalse(filter.hasAnyPaths());
        assertNull(match(filter, "a", "b", "c"));
    }

    @Test
    public void invalidExpressions() {
        assertThrows(IllegalArgumentException.class, () -> PathFilter.create(Collections.singletonList("a.b"), null));
        assertThrows(IllegalArgumentException.class, () -> PathFilter.create(null, Collections.singletonList("$.a[0]")));
    }
}
