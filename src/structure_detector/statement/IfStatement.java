package structure_detector.statement;

import structure_detector.Node;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents an if statement with optional else branch.
 * 
 * @author JPEXS
 */
public class IfStatement extends Statement {
    
    private final Node conditionNode;
    private final boolean negated;
    private final List<Statement> onTrue;
    private final List<Statement> onFalse;
    
    /**
     * Creates a new if statement without else branch.
     * 
     * @param conditionNode the condition node
     * @param negated whether the condition is negated (produces "if (!condition)")
     * @param onTrue the statements to execute when condition is true
     */
    public IfStatement(Node conditionNode, boolean negated, List<Statement> onTrue) {
        this.conditionNode = conditionNode;
        this.negated = negated;
        this.onTrue = onTrue != null ? new ArrayList<>(onTrue) : new ArrayList<>();
        this.onFalse = new ArrayList<>();
    }
    
    /**
     * Creates a new if-else statement.
     * 
     * @param conditionNode the condition node
     * @param negated whether the condition is negated (produces "if (!condition)")
     * @param onTrue the statements to execute when condition is true
     * @param onFalse the statements to execute when condition is false
     */
    public IfStatement(Node conditionNode, boolean negated, List<Statement> onTrue, List<Statement> onFalse) {
        this.conditionNode = conditionNode;
        this.negated = negated;
        this.onTrue = onTrue != null ? new ArrayList<>(onTrue) : new ArrayList<>();
        this.onFalse = onFalse != null ? new ArrayList<>(onFalse) : new ArrayList<>();
    }
    
    /**
     * Gets the condition node.
     * 
     * @return the condition node
     */
    public Node getConditionNode() {
        return conditionNode;
    }
    
    /**
     * Gets the condition expression (node label).
     * 
     * @return the condition expression
     */
    public String getCondition() {
        return conditionNode.getLabel();
    }
    
    /**
     * Checks if the condition is negated.
     * 
     * @return true if negated
     */
    public boolean isNegated() {
        return negated;
    }
    
    /**
     * Gets the statements to execute when condition is true.
     * 
     * @return the true branch statements
     */
    public List<Statement> getOnTrue() {
        return new ArrayList<>(onTrue);
    }
    
    /**
     * Gets the statements to execute when condition is false.
     * 
     * @return the false branch statements
     */
    public List<Statement> getOnFalse() {
        return new ArrayList<>(onFalse);
    }
    
    /**
     * Checks if this if statement has an else branch.
     * 
     * @return true if there is an else branch
     */
    public boolean hasElse() {
        return !onFalse.isEmpty();
    }
    
    @Override
    public String toString() {
        return toString("");
    }
    
    @Override
    public String toString(String indent) {
        StringBuilder sb = new StringBuilder();
        
        // Generate if condition
        String condition = conditionNode.getLabel();
        if (negated) {
            sb.append(indent).append("if (!").append(condition).append(") {\n");
        } else {
            sb.append(indent).append("if (").append(condition).append(") {\n");
        }
        
        // Generate true branch
        String innerIndent = indent + "    ";
        for (Statement stmt : onTrue) {
            sb.append(stmt.toString(innerIndent));
        }
        
        // Generate else branch if present
        if (hasElse()) {
            sb.append(indent).append("} else {\n");
            for (Statement stmt : onFalse) {
                sb.append(stmt.toString(innerIndent));
            }
        }
        
        sb.append(indent).append("}\n");
        
        return sb.toString();
    }
}
