package com.jpexs.decompiler.vibe.statement;

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
        private final List<Statement> body;
        private final boolean isDefault;
        
        /**
         * Creates a new case with condition.
         * 
         * @param condition the case condition (e.g., "if1")
         * @param body the statements in this case
         */
        public Case(String condition, List<Statement> body) {
            this.condition = condition;
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
            this.body = body != null ? new ArrayList<>(body) : new ArrayList<>();
            this.isDefault = true;
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
                sb.append(innerIndent).append("case ").append(c.getCondition()).append(":\n");
            }
            
            for (Statement stmt : c.getBody()) {
                sb.append(stmt.toString(bodyIndent));
            }
        }
        
        sb.append(indent).append("}\n");
        
        return sb.toString();
    }
}
