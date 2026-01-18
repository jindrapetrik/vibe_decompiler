package com.jpexs.decompiler.vibe;

/**
 * Edge from one Node to another.
 * @author JPEXS
 */
public class Edge {

    final Node from;
    final Node to;

    public Edge(Node from, Node to) {
        this.from = from;
        this.to = to;
    }

    @Override
    public String toString() {
        return "" + from + " -> " + to;
    }        
}
