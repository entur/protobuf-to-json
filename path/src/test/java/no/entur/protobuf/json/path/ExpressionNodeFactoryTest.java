package no.entur.protobuf.json.path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

// ported from json-log-filter
public class ExpressionNodeFactoryTest {

    private final ExpressionNodeFactory expressionNodeFactory = new ExpressionNodeFactory();

    private String print(String expression1, FilterType type1, String expression2, FilterType type2) {
        return expressionNodeFactory.toExpressionNode(new String[] {expression1, expression2}, new FilterType[] {type1, type2}).print();
    }

    @Test
    public void singlePath() {
        ExpressionNode expressionNode = expressionNodeFactory.toExpressionNode(new String[] {"$.a.b.c"}, new FilterType[] {FilterType.ANON});

        assertEquals("a\n b\n  c (ANON)", expressionNode.print());
    }

    @Test
    public void wildcard() {
        String[] expressions = new String[] {"$.a.*.c.d", "$.a.*.*.c"};
        ExpressionNode expressionNode = expressionNodeFactory.toExpressionNode(expressions, new FilterType[] {FilterType.ANON, FilterType.PRUNE});

        assertEquals(
                  "a\n"
                + " *\n"
                + "  c\n"
                + "   d (ANON)\n"
                + "   c (PRUNE)\n"
                + "  *\n"
                + "   c (PRUNE)", expressionNode.print());
    }

    @Test
    public void wildcards() {
        FilterType[] types = new FilterType[] {FilterType.ANON, FilterType.PRUNE, FilterType.ANON};
        assertNotNull(expressionNodeFactory.toExpressionNode(new String[] {"$.a.b.c.d.e", "$.a.*.*.*.f", "$.a.*.*.*.g"}, types));
        assertNotNull(expressionNodeFactory.toExpressionNode(new String[] {"$.a.b.c.d.e.i", "$.a.*.*.*.e.i", "$.a.*.*.*.g.i"}, types));
        assertNotNull(expressionNodeFactory.toExpressionNode(new String[] {"$.a.*.*.*.*.e", "$.a.*.*.*.f"}, new FilterType[] {FilterType.ANON, FilterType.PRUNE}));
    }

    @Test
    public void duplicateExpressions() {
        assertEquals("a\n b\n  c (PRUNE)", print("$.a.b.c", FilterType.ANON, "$.a.b.c", FilterType.PRUNE));
    }

    @Test
    public void dotNotation() {
        ExpressionNode expressionNode = expressionNodeFactory.toExpressionNode(new String[] {"$.a.b.c"}, new FilterType[] {FilterType.ANON});
        assertEquals("a\n b\n  c (ANON)", expressionNode.print());
    }

    @Test
    public void listVariant() {
        ExpressionNode expressionNode = expressionNodeFactory.toExpressionNode(Arrays.asList("$.a.b.c"), Arrays.asList(FilterType.ANON));
        assertEquals("a\n b\n  c (ANON)", expressionNode.print());
    }

    @Test
    public void prefixPathCoversExtension() {
        ExpressionNode expressionNode = expressionNodeFactory.toExpressionNode(new String[] {"$.one", "$.one.two"}, new FilterType[] {FilterType.ANON, FilterType.PRUNE});
        ExpressionNode oneNode = expressionNode.find("one");
        assertNotNull(oneNode);
        assertTrue(oneNode.hasFilterType());
        assertTrue(oneNode.getChildren().isEmpty());
    }

    @Test
    public void samePathPrunePrecedence() {
        assertEquals("a\n b (PRUNE)", print("$.a.b", FilterType.PRUNE, "$.a.b", FilterType.ANON));
        assertEquals("a\n b (PRUNE)", print("$.a.b", FilterType.ANON, "$.a.b", FilterType.PRUNE));
        assertEquals("a\n b (PRUNE)", print("$.a.b", FilterType.PRUNE, "$.a.b", FilterType.ANON));
    }

    @Test
    public void mixedNotation() {
        assertEquals("user\n address (PRUNE)", print("$.user.address", FilterType.PRUNE, "$.user.address.zip", FilterType.ANON));
        assertEquals("user\n address (ANON)", print("$.user.address", FilterType.ANON, "$.user.address.zip", FilterType.PRUNE));
        assertEquals("a\n b (PRUNE)", print("$.a.b", FilterType.ANON, "$.a.b", FilterType.PRUNE));
        assertEquals("a\n b (PRUNE)", print("$.a.b", FilterType.PRUNE, "$.a.b", FilterType.ANON));
    }

    @Test
    public void wildcardLeafCoversSiblings() {
        // /a/* matches /a/b too, so b is anonymized as a whole
        assertEquals("a\n * (ANON)\n b (ANON)", print("$.a.*", FilterType.ANON, "$.a.b.c", FilterType.ANON));
        assertEquals("a\n * (ANON)\n b (PRUNE)", print("$.a.*", FilterType.ANON, "$.a.b", FilterType.PRUNE));
        assertEquals("a\n * (PRUNE)\n b (PRUNE)", print("$.a.*", FilterType.PRUNE, "$.a.b", FilterType.ANON));
        // /*/password matches /user/password too, so it is pruned as a whole
        assertEquals("*\n password (PRUNE)\nuser\n password (PRUNE)", print("$.*.password", FilterType.PRUNE, "$.user.password.x", FilterType.ANON));
    }
}
