package com.jpexs.decompiler.vibe.structure;

import com.jpexs.decompiler.vibe.Node;
import java.util.List;
import java.util.Set;

/**
 * Represents a grouped try structure with multiple catch handlers.
 * Used when multiple exceptions have the same try body.
 */
public class GroupedTryStructure {
    public final Set<Node> tryBody;                    // nodes in the try block
    public final List<TryStructure> catchHandlers;     // list of catch handlers with their indices
    
    public GroupedTryStructure(Set<Node> tryBody, List<TryStructure> catchHandlers) {
        this.tryBody = tryBody;
        this.catchHandlers = catchHandlers;
    }
}
