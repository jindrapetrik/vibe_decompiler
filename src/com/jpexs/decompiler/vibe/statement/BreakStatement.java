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
     * Creates a new break statement.
     * 
     * @param labelId the global ID of the target block/loop
     */
    public BreakStatement(int labelId) {
        this.label = null;
        this.labelId = labelId;
    }
    
    /**
     * Creates a new break statement with a label.
     * 
     * @param label the label to break to (can be null for unlabeled break)
     * @param labelId the global ID of the target block/loop
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
     * @return the global ID
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
