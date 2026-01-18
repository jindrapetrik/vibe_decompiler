package com.jpexs.decompiler.vibe.structure;

import com.jpexs.decompiler.vibe.Node;

/**
 * Represents a continue edge - an edge that jumps back to loop header.
 */
public class ContinueEdge {
    public final Node from;
    public final Node to;

    public ContinueEdge(Node from, Node to) {
        this.from = from;
        this.to = to;
    }

    @Override
    public String toString() {
        return "Continue{" + from + " -> " + to + "}";
    }
}
