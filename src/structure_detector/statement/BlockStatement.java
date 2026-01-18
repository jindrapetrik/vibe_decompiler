package structure_detector.statement;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a labeled block statement.
 * A labeled block is a region of code that can be exited early with "break label;".
 * 
 * @author JPEXS
 */
public class BlockStatement extends Statement {
    
    private final String label;
    private final List<Statement> body;
    
    /**
     * Creates a new labeled block statement.
     * 
     * @param label the block label
     * @param body the block body statements
     */
    public BlockStatement(String label, List<Statement> body) {
        this.label = label;
        this.body = body != null ? new ArrayList<>(body) : new ArrayList<>();
    }
    
    /**
     * Gets the block label.
     * 
     * @return the label
     */
    public String getLabel() {
        return label;
    }
    
    /**
     * Gets the block body statements.
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
        
        // Generate block header
        sb.append(indent).append(label).append(": {\n");
        
        // Generate body
        String innerIndent = indent + "    ";
        for (Statement stmt : body) {
            sb.append(stmt.toString(innerIndent));
        }
        
        sb.append(indent).append("}\n");
        
        return sb.toString();
    }
}
