package com.jpexs.decompiler.vibe.structure;

import com.jpexs.decompiler.vibe.Node;

/**
 * Represents an if-statement structure detected in the CFG.
 */
public class IfStructure {
    public final Node conditionNode;
    public final Node trueBranch;
    public final Node falseBranch;
    public final Node mergeNode; // may be null if branches don't merge

    public IfStructure(Node conditionNode, Node trueBranch, Node falseBranch, Node mergeNode) {
        this.conditionNode = conditionNode;
        this.trueBranch = trueBranch;
        this.falseBranch = falseBranch;
        this.mergeNode = mergeNode;
    }

    @Override
    public String toString() {
        return "If{condition=" + conditionNode + 
               ", true=" + trueBranch + 
               ", false=" + falseBranch + 
               ", merge=" + mergeNode + "}";
    }
}
