package structure_detector.statement;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a try-catch statement with one or more catch blocks.
 * 
 * @author JPEXS
 */
public class TryStatement extends Statement {
    
    private final List<Statement> tryBody;
    private final List<CatchBlock> catchBlocks;
    
    /**
     * Represents a single catch block with its exception index.
     */
    public static class CatchBlock {
        private final int exceptionIndex;
        private final List<Statement> body;
        
        public CatchBlock(int exceptionIndex, List<Statement> body) {
            this.exceptionIndex = exceptionIndex;
            this.body = body != null ? new ArrayList<>(body) : new ArrayList<>();
        }
        
        public int getExceptionIndex() {
            return exceptionIndex;
        }
        
        public List<Statement> getBody() {
            return new ArrayList<>(body);
        }
    }
    
    /**
     * Creates a new try-catch statement with a single catch block.
     * 
     * @param tryBody the statements in the try block
     * @param catchBody the statements in the catch block
     */
    public TryStatement(List<Statement> tryBody, List<Statement> catchBody) {
        this(tryBody, catchBody, 0);
    }
    
    /**
     * Creates a new try-catch statement with a single catch block and exception index.
     * 
     * @param tryBody the statements in the try block
     * @param catchBody the statements in the catch block
     * @param exceptionIndex the global exception index
     */
    public TryStatement(List<Statement> tryBody, List<Statement> catchBody, int exceptionIndex) {
        this.tryBody = tryBody != null ? new ArrayList<>(tryBody) : new ArrayList<>();
        this.catchBlocks = new ArrayList<>();
        this.catchBlocks.add(new CatchBlock(exceptionIndex, catchBody));
    }
    
    /**
     * Creates a new try-catch statement with multiple catch blocks.
     * 
     * @param tryBody the statements in the try block
     * @param catchBlocks the list of catch blocks
     */
    public TryStatement(List<Statement> tryBody, List<CatchBlock> catchBlocks) {
        this.tryBody = tryBody != null ? new ArrayList<>(tryBody) : new ArrayList<>();
        this.catchBlocks = catchBlocks != null ? new ArrayList<>(catchBlocks) : new ArrayList<>();
    }
    
    /**
     * Gets the try body statements.
     * 
     * @return the try body statements
     */
    public List<Statement> getTryBody() {
        return new ArrayList<>(tryBody);
    }
    
    /**
     * Gets the catch blocks.
     * 
     * @return the catch blocks
     */
    public List<CatchBlock> getCatchBlocks() {
        return new ArrayList<>(catchBlocks);
    }
    
    /**
     * Gets the catch body statements (for single catch block compatibility).
     * 
     * @return the catch body statements of the first catch block
     */
    public List<Statement> getCatchBody() {
        if (catchBlocks.isEmpty()) {
            return new ArrayList<>();
        }
        return catchBlocks.get(0).getBody();
    }
    
    @Override
    public String toString() {
        return toString("");
    }
    
    @Override
    public String toString(String indent) {
        StringBuilder sb = new StringBuilder();
        
        // Generate try block
        sb.append(indent).append("try {\n");
        
        String innerIndent = indent + "    ";
        for (Statement stmt : tryBody) {
            sb.append(stmt.toString(innerIndent));
        }
        
        // Generate catch blocks
        for (CatchBlock catchBlock : catchBlocks) {
            sb.append(indent).append("} catch(").append(catchBlock.getExceptionIndex()).append(") {\n");
            
            for (Statement stmt : catchBlock.getBody()) {
                sb.append(stmt.toString(innerIndent));
            }
        }
        
        sb.append(indent).append("}\n");
        
        return sb.toString();
    }
}
