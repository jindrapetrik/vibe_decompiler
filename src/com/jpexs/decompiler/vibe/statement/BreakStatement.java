package com.jpexs.decompiler.vibe.statement;

/**
 * Represents a break statement with an optional label.
 * 
 * @author JPEXS
 */
public class BreakStatement extends Statement {
    
    private final String label;
    private final int labelId;
    
    /**
     * Creates a new unlabeled break statement.
     */
    public BreakStatement() {
        this.label = null;
        this.labelId = -1;
    }
    
    /**
     * Creates a new labeled break statement.
     * 
     * @param label the label to break to (can be null for unlabeled break)
     * @param labelId the global ID of the target block/loop (-1 if unlabeled)
     */
    public BreakStatement(String label, int labelId) {
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
     * Gets the global ID of the target block/loop.
     * 
     * @return the global ID, or -1 if unlabeled
     */
    public int getLabelId() {
        return labelId;
    }
    
    /**
     * Checks if this is a labeled break.
     * 
     * @return true if this break has a label
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
            return indent + "break " + label + ";\n";
        } else {
            return indent + "break;\n";
        }
    }
}
