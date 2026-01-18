package com.jpexs.decompiler.vibe.structure;

import com.jpexs.decompiler.vibe.Node;

/**
 * Represents a break edge from within a labeled block to its exit.
 */
public class LabeledBreakEdge {
    public final Node from;
    public final Node to;
    public final String label;
    public final int labelId;

    public LabeledBreakEdge(Node from, Node to, String label, int labelId) {
        this.from = from;
        this.to = to;
        this.label = label;
        this.labelId = labelId;
    }

    @Override
    public String toString() {
        return "LabeledBreak{" + from + " -> " + to + " (break " + label + ")}";
    }
}
