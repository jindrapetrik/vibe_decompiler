package com.jpexs.decompiler.vibe.structure;

import com.jpexs.decompiler.vibe.Node;

/**
 * Represents a break edge - an edge that exits a loop early.
 */
public class BreakEdge {
    public final Node from;
    public final Node to;

    public BreakEdge(Node from, Node to) {
        this.from = from;
        this.to = to;
    }

    @Override
    public String toString() {
        return "Break{" + from + " -> " + to + "}";
    }
}
