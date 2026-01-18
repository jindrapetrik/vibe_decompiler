package structure_detector.statement;

import structure_detector.Node;

/**
 * Represents a simple expression statement (like a node label followed by semicolon).
 * 
 * @author JPEXS
 */
public class ExpressionStatement extends Statement {
    
    private final Node node;
    
    /**
     * Creates a new expression statement.
     * 
     * @param node the node for this expression
     */
    public ExpressionStatement(Node node) {
        this.node = node;
    }
    
    /**
     * Gets the node for this expression.
     * 
     * @return the node
     */
    public Node getNode() {
        return node;
    }
    
    /**
     * Gets the expression text (node label).
     * 
     * @return the expression text
     */
    public String getExpression() {
        return node.getLabel();
    }
    
    @Override
    public String toString() {
        return toString("");
    }
    
    @Override
    public String toString(String indent) {
        return indent + node.getLabel() + ";\n";
    }
}
