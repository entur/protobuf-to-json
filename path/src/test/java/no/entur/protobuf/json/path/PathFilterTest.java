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
        PathMatcher matcher = filter.newMatcher();
        FilterType type = null;
        for (int i = 0; i < names.length; i++) {
            type = matcher.matchName(i + 1, names[i]);
            if (type != null) {
                return i == names.length - 1 ? type : null;
            }
        }
        return type;
    }

    /**
     * Walk to an object, given as the field names to it, and get the type of the paths which go on into it other than
     * through the given field names.
     */

    private static FilterType typeWithin(PathFilter filter, List<String> names, String... fieldNames) {
        PathMatcher matcher = filter.newMatcher();
        for (int i = 0; i < names.size(); i++) {
            assertNull(matcher.matchName(i + 1, names.get(i)));
        }
        return matcher.matchOpaque(names.size() + 1, fieldNames);
    }

    @Test
    public void typeWithin() {
        String[] fields = {"@type", "@value"};
        List<String> any = Arrays.asList("a", "b");

        // paths through the given field names, or which do not reach the object, do not count
        assertNull(typeWithin(PathFilter.create(Arrays.asList("$.a.b.@type", "$.a.b.@value.x", "$.a.c.x", "$.x"), null), any, fields));
        assertNull(typeWithin(PathFilter.create(Arrays.asList("$.a.b.x"), null), Arrays.asList("a", "c"), fields));
        // other names count, also deeper
        assertEquals(FilterType.ANON, typeWithin(PathFilter.create(Arrays.asList("$.a.b.x"), null), any, fields));
        assertEquals(FilterType.PRUNE, typeWithin(PathFilter.create(Arrays.asList("$.a.b.x"), Arrays.asList("$.a.b.y.z")), any, fields));
        assertEquals(FilterType.PRUNE, typeWithin(PathFilter.create(null, Arrays.asList("$.a.*.y", "$.a.b.@type")), any, fields));
        // a wildcard counts
        assertEquals(FilterType.ANON, typeWithin(PathFilter.create(Arrays.asList("$.a.b.*"), null), any, fields));
        assertEquals(FilterType.PRUNE, typeWithin(PathFilter.create(Arrays.asList("$.a.b.@type"), Arrays.asList("$.a.b.*.x")), any, fields));
        assertEquals(FilterType.ANON, typeWithin(PathFilter.create(Arrays.asList("$.*.*.x"), null), any, fields));
        // a path covered by a shorter path does not count
        assertEquals(FilterType.ANON, typeWithin(PathFilter.create(Arrays.asList("$.a.b.x"), Arrays.asList("$.a.b.x.y")), any, fields));
        assertEquals(FilterType.ANON, typeWithin(PathFilter.create(Arrays.asList("$.a.b.*"), Arrays.asList("$.a.b.x.y")), any, fields));
        // root
        assertEquals(FilterType.ANON, typeWithin(PathFilter.create(Arrays.asList("$.x"), null), Collections.emptyList(), fields));
        assertNull(typeWithin(PathFilter.create(Arrays.asList("$.@value"), null), Collections.emptyList(), fields));
        // only at the level of the item
        assertNull(PathFilter.create(Arrays.asList("$.a.b.x"), null).newMatcher().matchOpaque(2, fields));
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
    public void matcherFollowsTheDocument() {
        PathMatcher matcher = PathFilter.create(Collections.singletonList("$.a.b"), null).newMatcher();

        assertNull(matcher.matchName(1, "a"));
        assertNull(matcher.matchName(2, "x"));
        assertEquals(FilterType.ANON, matcher.matchName(2, "b"));
        assertNull(matcher.matchName(1, "b"));
        assertNull(matcher.matchName(1, "a"));
        assertEquals(FilterType.ANON, matcher.matchName(2, "b"));
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
