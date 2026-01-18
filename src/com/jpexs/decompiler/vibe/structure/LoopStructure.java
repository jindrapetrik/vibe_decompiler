package com.jpexs.decompiler.vibe.structure;

import com.jpexs.decompiler.vibe.Node;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Represents a loop structure detected in the CFG.
 */
public class LoopStructure {
    public final Node header;        // loop header node
    public final Set<Node> body;     // all nodes in the loop body
    public final Node backEdgeSource; // node that jumps back to header
    public final List<BreakEdge> breaks;
    public final List<ContinueEdge> continues;

    public LoopStructure(Node header, Set<Node> body, Node backEdgeSource) {
        this.header = header;
        this.body = body;
        this.backEdgeSource = backEdgeSource;
        this.breaks = new ArrayList<>();
        this.continues = new ArrayList<>();
    }

    @Override
    public String toString() {
        return "Loop{header=" + header + 
               ", body=" + body + 
               ", backEdge=" + backEdgeSource + 
               ", breaks=" + breaks + 
               ", continues=" + continues + "}";
    }
}
