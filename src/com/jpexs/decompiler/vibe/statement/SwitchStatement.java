package com.jpexs.decompiler.vibe.statement;

import com.jpexs.decompiler.vibe.Node;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a switch statement with cases and optional default.
 * 
 * @author JPEXS
 */
public class SwitchStatement extends Statement {
    
    /**
     * Represents a single case in a switch statement.
     */
    public static class Case {
        private final String condition;
        private final Node conditionNode;
        private final List<Statement> body;
        private final boolean isDefault;
        private final boolean negated;
        
        /**
         * Creates a new case with condition.
         * 
         * @param condition the case condition (e.g., "if1")
         * @param conditionNode the node representing the case condition
         * @param negated case condition is negated
         * @param body the statements in this case
         */
        public Case(String condition, Node conditionNode, boolean negated, List<Statement> body) {
            this.condition = condition;
            this.conditionNode = conditionNode;
            this.negated = negated;
            this.body = body != null ? new ArrayList<>(body) : new ArrayList<>();
            this.isDefault = false;
        }
        
        /**
         * Creates a new default case.
         * 
         * @param body the statements in the default case
         */
        public Case(List<Statement> body) {
            this.condition = null;
            this.conditionNode = null;
            this.body = body != null ? new ArrayList<>(body) : new ArrayList<>();
            this.isDefault = true;
            this.negated = false;
        }
        
        /**
         * Gets the case condition.
         * 
         * @return the condition, or null for default case
         */
        public String getCondition() {
            return condition;
        }
        
        /**
         * Gets the condition node representing the case condition.
         * 
         * @return the condition node, or null for default case
         */
        public Node getConditionNode() {
            return conditionNode;
        }
        
        /**
         * Gets the case body statements.
         * 
         * @return the body statements
         */
        public List<Statement> getBody() {
            return new ArrayList<>(body);
        }
        
        /**
         * Checks if this is the default case.
         * 
         * @return true if this is the default case
         */
        public boolean isDefault() {
            return isDefault;
        }

        /**
         * Checks if the condition is negated.
         * 
         * @return true if the condition is negated
         */
        public boolean isNegated() {
            return negated;
        }                
    }
    
    private final List<Case> cases;
    private final String label;
    private final int labelId;
    
    /**
     * Creates a new switch statement.
     * 
     * @param cases the list of cases (including default)
     * @param labelId the global ID of the switch
     */
    public SwitchStatement(List<Case> cases, int labelId) {
        this(cases, null, labelId);
    }
    
    /**
     * Creates a new switch statement with an optional label.
     * 
     * @param cases the list of cases (including default)
     * @param label the label for the switch (e.g., "loop1"), or null for no label
     * @param labelId the global ID of the switch
     */
    public SwitchStatement(List<Case> cases, String label, int labelId) {
        this.cases = cases != null ? new ArrayList<>(cases) : new ArrayList<>();
        this.label = label;
        this.labelId = labelId;
    }
    
    /**
     * Gets the cases in this switch statement.
     * 
     * @return the list of cases
     */
    public List<Case> getCases() {
        return new ArrayList<>(cases);
    }
    
    /**
     * Gets the label for this switch statement.
     * 
     * @return the label, or null if no label
     */
    public String getLabel() {
        return label;
    }
    
    /**
     * Gets the global ID of the switch.
     * 
     * @return the global ID
     */
    public int getLabelId() {
        return labelId;
    }
    
    /**
     * Checks if this switch has a label.
     * 
     * @return true if this switch has a label
     */
    public boolean hasLabel() {
        return label != null && !label.isEmpty();
    }
    
    @Override
    public String toString() {
        return toString("");
    }
    
    @Override
    public String toString(String indent) {
        StringBuilder sb = new StringBuilder();
        
        // Generate switch header with optional label
        sb.append(indent);
        if (label != null && !label.isEmpty()) {
            sb.append(label).append(":");
        }
        sb.append("switch {\n");
        
        String innerIndent = indent + "    ";
        String bodyIndent = indent + "        ";
        
        for (Case c : cases) {
            if (c.isDefault()) {
                sb.append(innerIndent).append("default:\n");
            } else {
                if (c.isNegated()) {
                    sb.append(innerIndent).append("case ").append("!").append(c.getCondition()).append(":\n");
                } else {
                    sb.append(innerIndent).append("case ").append(c.getCondition()).append(":\n");
                }
            }
            
            for (Statement stmt : c.getBody()) {
                sb.append(stmt.toString(bodyIndent));
            }
        }
        
        sb.append(indent).append("}\n");
        
        return sb.toString();
    }
}
