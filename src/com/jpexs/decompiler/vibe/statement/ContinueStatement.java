package com.jpexs.decompiler.vibe.statement;

/**
 * Represents a continue statement with an optional label.
 * 
 * @author JPEXS
 */
public class ContinueStatement extends Statement {
    
    private final String label;
    private final int labelId;
    
    /**
     * Creates a new unlabeled continue statement.
     */
    public ContinueStatement() {
        this.label = null;
        this.labelId = -1;
    }
    
    /**
     * Creates a new labeled continue statement.
     * 
     * @param label the label to continue to (can be null for unlabeled continue)
     * @param labelId the global ID of the target loop (-1 if unlabeled)
     */
    public ContinueStatement(String label, int labelId) {
        this.label = label;
        this.labelId = labelId;
    }
    
    /**
     * Gets the label.
     * 
     * @return the label, or null if unlabeled
     */
    public String getLabel() {
        return label;
    }
    
    /**
     * Gets the global ID of the target loop.
     * 
     * @return the global ID, or -1 if unlabeled
     */
    public int getLabelId() {
        return labelId;
    }
    
    /**
     * Checks if this is a labeled continue.
     * 
     * @return true if this continue has a label
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
        if (hasLabel()) {
            return indent + "continue " + label + ";\n";
        } else {
            return indent + "continue;\n";
        }
    }
}
