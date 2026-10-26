package no.entur.protobuf.json.path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

// ported from json-log-filter
public class PathItemFactoryTest {

    private final ExpressionNodeFactory expressionNodeFactory = new ExpressionNodeFactory();
    private final PathItemFactory factory = new PathItemFactory();

    private PathItem create(String[] expressions, FilterType[] types) {
        return factory.create(expressionNodeFactory.toExpressionNode(expressions, types));
    }

    @Test
    public void singlePath() {
        PathItem root = create(new String[] {"$.a.b.c"}, new FilterType[] {FilterType.ANON});

        assertEquals(FilterType.ANON, root.matchPath(1, "a").matchPath(2, "b").matchPath(3, "c").getType());
        // no match keeps the current item
        assertSame(root, root.matchPath(1, "x"));
        // wrong level keeps the current item
        assertSame(root, root.matchPath(2, "a"));
    }

    @Test
    public void multiplePaths() {
        PathItem root = create(new String[] {"$.a.b", "$.a.c"}, new FilterType[] {FilterType.ANON, FilterType.PRUNE});
        assertEquals(FilterType.ANON, root.matchPath(1, "a").matchPath(2, "b").getType());
        assertEquals(FilterType.PRUNE, root.matchPath(1, "a").matchPath(2, "c").getType());

        root = create(new String[] {"$.a.b.c", "$.a.b.d"}, new FilterType[] {FilterType.ANON, FilterType.ANON});
        assertEquals(FilterType.ANON, root.matchPath(1, "a").matchPath(2, "b").matchPath(3, "c").getType());
        assertEquals(FilterType.ANON, root.matchPath(1, "a").matchPath(2, "b").matchPath(3, "d").getType());
    }

    @Test
    public void wildcards() {
        PathItem root = create(new String[] {"$.a.*.c", "$.a.*.d"}, new FilterType[] {FilterType.ANON, FilterType.ANON});
        assertEquals(FilterType.ANON, root.matchPath(1, "a").matchPath(2, "b").matchPath(3, "c").getType());
        assertEquals(FilterType.ANON, root.matchPath(1, "a").matchPath(2, "b").matchPath(3, "d").getType());

        root = create(new String[] {"$.a.b.c", "$.a.*.d"}, new FilterType[] {FilterType.ANON, FilterType.PRUNE});
        assertEquals(FilterType.PRUNE, root.matchPath(1, "a").matchPath(2, "x").matchPath(3, "d").getType());
        assertEquals(FilterType.ANON, root.matchPath(1, "a").matchPath(2, "b").matchPath(3, "c").getType());
        // the explicit branch also contains the wildcard branch
        assertEquals(FilterType.PRUNE, root.matchPath(1, "a").matchPath(2, "b").matchPath(3, "d").getType());

        root = create(new String[] {"$.*"}, new FilterType[] {FilterType.ANON});
        assertEquals(FilterType.ANON, root.matchPath(1, "a").getType());

        root = create(new String[] {"$.a.*.c.d", "$.a.*.*.c"}, new FilterType[] {FilterType.ANON, FilterType.PRUNE});
        assertEquals(FilterType.ANON, root.matchPath(1, "a").matchPath(2, "b").matchPath(3, "c").matchPath(4, "d").getType());
        assertEquals(FilterType.PRUNE, root.matchPath(1, "a").matchPath(2, "b").matchPath(3, "c").matchPath(4, "c").getType());
    }

    @Test
    public void constrain() {
        PathItem root = create(new String[] {"$.a.*.c", "$.a.*.d"}, new FilterType[] {FilterType.ANON, FilterType.ANON});
        assertEquals(1, root.getLevel());
        assertEquals(2, root.matchPath(1, "a").getLevel());
        assertEquals(3, root.matchPath(1, "a").matchPath(2, "b").getLevel());

        PathItem matched = root.matchPath(1, "a").matchPath(2, "b").matchPath(3, "c");
        assertEquals(4, matched.getLevel());
        assertEquals(4, matched.constrain(4).getLevel());
        assertEquals(3, matched.constrain(3).getLevel());
        assertEquals(2, matched.constrain(2).getLevel());
        assertSame(root, matched.constrain(1));

        root = create(new String[] {"$.a.*.c", "$.a.*.d", "$.a.1.e"}, new FilterType[] {FilterType.ANON, FilterType.ANON, FilterType.PRUNE});
        PathItem pruned = root.matchPath(1, "a").matchPath(2, "1").matchPath(3, "e");
        assertEquals(FilterType.PRUNE, pruned.getType());
        assertSame(root.matchPath(1, "a"), pruned.constrain(2));
    }

    @Test
    public void endPathItem() {
        PathItem end = new EndPathItem(3, null, FilterType.ANON);
        assertTrue(end.hasType());
        assertEquals(FilterType.ANON, end.getType());
        assertThrows(UnsupportedOperationException.class, () -> end.matchPath(3, "a"));

        SinglePathItem single = new SinglePathItem(1, "a", null);
        assertFalse(single.hasType());
        assertNull(single.getType());
    }

    @Test
    public void handBuiltStateMachine() {
        // /a/c/f, /a/d/g, /a/e/h
        SinglePathItem p1 = new SinglePathItem(1, "a", null);
        MultiPathItem p2 = new MultiPathItem(new String[] {"c", "d", "e"}, 2, p1);

        SinglePathItem p31 = new SinglePathItem(3, "f", p2);
        SinglePathItem p32 = new SinglePathItem(3, "g", p2);
        SinglePathItem p33 = new SinglePathItem(3, "h", p2);

        p1.setNext(p2);
        p2.setNext(p31, 0);
        p2.setNext(p32, 1);
        p2.setNext(p33, 2);

        p31.setNext(new EndPathItem(4, p31, FilterType.ANON));
        p32.setNext(new EndPathItem(4, p32, FilterType.ANON));
        p33.setNext(new EndPathItem(4, p33, FilterType.ANON));

        assertSame(p2, p1.matchPath(1, "a"));
        assertSame(p31, p2.matchPath(2, "c"));
        assertEquals(FilterType.ANON, p1.matchPath(1, "a").matchPath(2, "d").matchPath(3, "g").getType());
        assertEquals(FilterType.ANON, p1.matchPath(1, "a").matchPath(2, "e").matchPath(3, "h").getType());
        assertSame(p2, p2.matchPath(2, "x"));
    }

    @Test
    public void starMultiPathItem() {
        StarMultiPathItem item = new StarMultiPathItem(new String[] {"a", "b"}, 1, null);
        PathItem a = new EndPathItem(2, item, FilterType.ANON);
        PathItem b = new EndPathItem(2, item, FilterType.PRUNE);
        PathItem any = new EndPathItem(2, item, FilterType.ANON);
        item.setNext(a, 0);
        item.setNext(b, 1);
        item.setAny(any);

        assertSame(a, item.matchPath(1, "a"));
        assertSame(b, item.matchPath(1, "b"));
        assertSame(any, item.matchPath(1, "c"));
        assertSame(item, item.matchPath(2, "a"));
    }
}
