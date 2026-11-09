package no.entur.protobuf.json.path;

import java.util.ArrayList;
import java.util.List;

/**
 * Tree of parsed path expressions.
 * <br><br>
 * Ported from json-log-filter (Apache-2.0).
 */

class ExpressionNode {

    private String path;
    private int index;
    private FilterType filterType;
    private List<ExpressionNode> children = new ArrayList<>();

    ExpressionNode find(String path) {
        for (ExpressionNode expressionNode : children) {
            if (expressionNode.path.equals(path)) {
                return expressionNode;
            }
        }
        return null;
    }

    /**
     * Merge a node into the children of this node.
     *
     * @param node node to merge, it is copied and not modified
     */

    void append(ExpressionNode node) {
        if (filterType != null) {
            // this node is filtered as a whole, which covers anything below it
            return;
        }
        ExpressionNode preexisting = find(node.path);
        if (preexisting == null) {
            children.add(node.copy());
        } else if (node.filterType != null) {
            // the node is filtered as a whole, which covers anything below the preexisting node
            preexisting.cover(node.filterType);
        } else {
            for (ExpressionNode child : node.children) {
                preexisting.append(child);
            }
        }
    }

    /**
     * Filter this node as a whole. Rules below this node are dropped, as they are covered by this node.
     * If this node already has a filter type, the strongest type wins.
     *
     * @param type filter type
     */

    void cover(FilterType type) {
        this.filterType = FilterType.strongest(this.filterType, type);
        this.children.clear();
    }

    ExpressionNode copy() {
        ExpressionNode copy = new ExpressionNode();
        copy.path = path;
        copy.index = index;
        copy.filterType = filterType;
        for (ExpressionNode child : children) {
            copy.children.add(child.copy());
        }
        return copy;
    }

    String print() {
        StringBuilder builder = new StringBuilder();

        print(builder);

        return builder.toString().trim();
    }

    void print(StringBuilder builder) {
        for (int i = 0; i < index; i++) {
            builder.append(' ');
        }
        if (path != null) {
            builder.append(path);
        }
        if (filterType != null) {
            builder.append(" (");
            builder.append(filterType);
            builder.append(')');
        }
        builder.append('\n');

        for (ExpressionNode expressionNode : children) {
            expressionNode.print(builder);
        }
    }

    boolean hasFilterType() {
        return filterType != null;
    }

    void add(ExpressionNode node) {
        children.add(node);
    }

    String getPath() {
        return path;
    }

    void setPath(String path) {
        this.path = path;
    }

    int getIndex() {
        return index;
    }

    void setIndex(int index) {
        this.index = index;
    }

    FilterType getFilterType() {
        return filterType;
    }

    void setFilterType(FilterType filterType) {
        this.filterType = filterType;
    }

    List<ExpressionNode> getChildren() {
        return children;
    }

    void setChildren(List<ExpressionNode> children) {
        this.children = children;
    }
}
