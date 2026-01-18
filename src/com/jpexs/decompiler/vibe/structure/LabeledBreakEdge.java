package com.jpexs.decompiler.vibe.structure;

import com.jpexs.decompiler.vibe.Node;

/**
 * Represents a break edge from within a labeled block to its exit.
 */
public class LabeledBreakEdge {
    public final Node from;
    public final Node to;
    public final String label;

    public LabeledBreakEdge(Node from, Node to, String label) {
        this.from = from;
        this.to = to;
        this.label = label;
    }

    @Override
    public String toString() {
        return "LabeledBreak{" + from + " -> " + to + " (break " + label + ")}";
    }
}
