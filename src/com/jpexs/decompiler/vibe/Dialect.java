package com.jpexs.decompiler.vibe;

/**
 *
 * @author JPEXS
 */
public interface Dialect {
    public boolean isStrictEqualsIf(Node node);
    public boolean isStrictNotEqualsIf(Node node);
}
