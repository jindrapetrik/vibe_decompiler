package com.jpexs.decompiler.vibe.statement;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a while(true) loop with an optional label.
 * 
 * @author JPEXS
 */
public class LoopStatement extends Statement {
    
    private final String label;
    private final int labelId;
    private final List<Statement> body;
    
    /**
     * Creates a new while(true) loop.
     * 
     * @param labelId the global ID of the loop
     * @param body the loop body statements
     */
    public LoopStatement(int labelId, List<Statement> body) {
        this.label = null;
        this.labelId = labelId;
        this.body = body != null ? new ArrayList<>(body) : new ArrayList<>();
    }
    
    /**
     * Creates a new while(true) loop with an optional label.
     * 
     * @param label the loop label (can be null for unlabeled loop)
     * @param labelId the global ID of the loop
     * @param body the loop body statements
     */
    public LoopStatement(String label, int labelId, List<Statement> body) {
        this.label = label;
        this.labelId = labelId;
        this.body = body != null ? new ArrayList<>(body) : new ArrayList<>();
    }
    
    /**
     * Gets the loop label.
     * 
     * @return the label, or null if unlabeled
     */
    public String getLabel() {
        return label;
    }
    
    /**
     * Gets the global ID of the loop.
     * 
     * @return the global ID
     */
    public int getLabelId() {
        return labelId;
    }
    
    /**
     * Checks if this loop has a label.
     * 
     * @return true if this loop has a label
     */
    public boolean hasLabel() {
        return label != null && !label.isEmpty();
    }
    
    /**
     * Gets the loop body statements.
     * 
     * @return the body statements
     */
    public List<Statement> getBody() {
        return new ArrayList<>(body);
    }
    
    @Override
    public String toString() {
        return toString("");
    }
    
    @Override
    public String toString(String indent) {
        StringBuilder sb = new StringBuilder();
        
        // Generate loop header
        if (hasLabel()) {
            sb.append(indent).append(label).append(": while(true) {\n");
        } else {
            sb.append(indent).append("while(true) {\n");
        }
        
        // Generate body
        String innerIndent = indent + "    ";
        for (Statement stmt : body) {
            sb.append(stmt.toString(innerIndent));
        }
        
        sb.append(indent).append("}\n");
        
        return sb.toString();
    }
}
