package structure_detector.statement;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a while(true) loop with an optional label.
 * 
 * @author JPEXS
 */
public class LoopStatement extends Statement {
    
    private final String label;
    private final List<Statement> body;
    
    /**
     * Creates a new unlabeled while(true) loop.
     * 
     * @param body the loop body statements
     */
    public LoopStatement(List<Statement> body) {
        this.label = null;
        this.body = body != null ? new ArrayList<>(body) : new ArrayList<>();
    }
    
    /**
     * Creates a new labeled while(true) loop.
     * 
     * @param label the loop label (can be null for unlabeled loop)
     * @param body the loop body statements
     */
    public LoopStatement(String label, List<Statement> body) {
        this.label = label;
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
