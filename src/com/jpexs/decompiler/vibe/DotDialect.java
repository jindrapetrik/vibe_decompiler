package com.jpexs.decompiler.vibe;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 *
 * @author JPEXS
 */
public class DotDialect implements Dialect {
    
    public static final DotDialect INSTANCE = new DotDialect();
    
    private DotDialect() {
        
    }
    
    private Map<String, String> getAttributesMap(Node node) {
        Map<String, String> ret = (Map<String, String>) node.getCustomData();
        if (ret == null) {
            ret = new LinkedHashMap<>();
            node.setCustomData(ret);
        }
        return ret;
    }

    @Override
    public boolean isStrictEqualsIf(Node node) {
        if (!node.isConditional()) {
            return false;
        }                
        return "===".equals(getAtttribute(node, "_operator"));
    }
    
    @Override
    public boolean isStrictNotEqualsIf(Node node) {
        if (!node.isConditional()) {
            return false;
        }                
        return "!==".equals(getAtttribute(node, "_operator"));
    }
    
    public void setAttribute(Node node, String key, String value) {
        getAttributesMap(node).put(key, value);
    }
    
    public String getAtttribute(Node node, String key) {
        Map<String, String> map = getAttributesMap(node);
        if (!map.containsKey(key)) {
            return "";
        }
        return map.get(key);
    }
}
