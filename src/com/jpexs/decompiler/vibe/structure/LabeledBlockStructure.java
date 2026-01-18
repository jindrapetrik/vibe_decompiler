package com.jpexs.decompiler.vibe.structure;

import com.jpexs.decompiler.vibe.Node;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Represents a labeled block structure detected in the CFG.
 * A labeled block is a region of code that can be exited early with "break label;".
 * Unlike loops, labeled blocks have no back edge.
 */
public class LabeledBlockStructure {
    public final String label;
    public final Node startNode;       // first node in the block
    public final Node endNode;         // node after the block (break target)
    public final Set<Node> body;       // all nodes in the block
    public final List<LabeledBreakEdge> breaks;

    public LabeledBlockStructure(String label, Node startNode, Node endNode, Set<Node> body) {
        this.label = label;
        this.startNode = startNode;
        this.endNode = endNode;
        this.body = body;
        this.breaks = new ArrayList<>();
    }

    @Override
    public String toString() {
        return "LabeledBlock{label=" + label + 
               ", start=" + startNode + 
               ", end=" + endNode + 
               ", body=" + body + 
               ", breaks=" + breaks + "}";
    }
}
