package com.jpexs.decompiler.vibe.structure;

import com.jpexs.decompiler.vibe.Node;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a switch structure detected in the CFG.
 * A switch is detected when there's a chain of conditional nodes where each has:
 * - One branch going to the next condition (fall-through)
 * - One branch going to a case body
 * - All case bodies lead to the same merge node
 */
public class SwitchStructure {
    public final Node startNode;         // first condition node
    public final List<SwitchCase> cases; // list of cases
    public final Node mergeNode;         // where all cases converge (e.g., "end")
    
    public SwitchStructure(Node startNode, List<SwitchCase> cases, Node mergeNode) {
        this.startNode = startNode;
        this.cases = new ArrayList<>(cases);
        this.mergeNode = mergeNode;
    }
    
    @Override
    public String toString() {
        return "Switch{start=" + startNode + ", cases=" + cases.size() + ", merge=" + mergeNode + "}";
    }
}
