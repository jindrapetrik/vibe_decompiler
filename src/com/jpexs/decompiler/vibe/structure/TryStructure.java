package com.jpexs.decompiler.vibe.structure;

import com.jpexs.decompiler.vibe.Node;
import java.util.Set;

/**
 * Represents a try-catch structure (exception range) in the CFG.
 * The try body contains nodes that are protected by the exception handler.
 * The catch body contains nodes that handle the exception.
 */
public class TryStructure {
    public final Set<Node> tryBody;     // nodes in the try block
    public final Set<Node> catchBody;   // nodes in the catch block
    public final int exceptionIndex;    // global index of this exception
    
    public TryStructure(Set<Node> tryBody, Set<Node> catchBody, int exceptionIndex) {
        this.tryBody = tryBody;
        this.catchBody = catchBody;
        this.exceptionIndex = exceptionIndex;
    }
    
    @Override
    public String toString() {
        return "Try{try=" + tryBody + ", catch=" + catchBody + ", index=" + exceptionIndex + "}";
    }
}
