package com.jpexs.decompiler.vibe;

import com.jpexs.decompiler.vibe.statement.IfStatement;
import com.jpexs.decompiler.vibe.statement.SwitchStatement;
import com.jpexs.decompiler.vibe.statement.LoopStatement;
import com.jpexs.decompiler.vibe.statement.TryStatement;
import com.jpexs.decompiler.vibe.statement.ExpressionStatement;
import com.jpexs.decompiler.vibe.statement.BlockStatement;
import com.jpexs.decompiler.vibe.statement.ContinueStatement;
import com.jpexs.decompiler.vibe.statement.BreakStatement;
import com.jpexs.decompiler.vibe.statement.Statement;
import com.jpexs.decompiler.vibe.structure.BreakEdge;
import com.jpexs.decompiler.vibe.structure.ContinueEdge;
import com.jpexs.decompiler.vibe.structure.GroupedTryStructure;
import com.jpexs.decompiler.vibe.structure.IfStructure;
import com.jpexs.decompiler.vibe.structure.LabeledBlockStructure;
import com.jpexs.decompiler.vibe.structure.LabeledBreakEdge;
import com.jpexs.decompiler.vibe.structure.LoopStructure;
import com.jpexs.decompiler.vibe.structure.SwitchCase;
import com.jpexs.decompiler.vibe.structure.SwitchStructure;
import com.jpexs.decompiler.vibe.structure.TryStructure;
import java.util.*;

/**
 * Detects control flow structures in a Control Flow Graph (CFG).
 * Capable of detecting:
 * - If statements (conditional branches)
 * - Loops (cycles in the graph)
 * - Break statements (edges that exit a loop early)
 * - Continue statements (edges that jump back to loop header)
 *
 * @author JPEXS
 */
public class StructureDetector {
    
    /**
     * Minimum number of chained conditions required to detect a switch structure.
     * Set to 3 to avoid false positives with simple sequential if-else patterns.
     */
    private static final int MIN_SWITCH_CHAIN_SIZE = 3;
    
    private final List<Node> allNodes;
    private final Node entryNode;
    private final List<LabeledBlockStructure> labeledBlocks = new ArrayList<>();
    private final List<SwitchStructure> switchStructures = new ArrayList<>();
    private final List<TryStructure> tryStructures = new ArrayList<>();
    private Map<String, Node> nodesByLabel = new LinkedHashMap<>();
    
    // Global label counter for loops and blocks (shared sequence: loop0, block1, loop2, etc.)
    private int globalLabelCounter = 0;
    // Maps loop header nodes to their assigned labels (e.g., "loop0")
    private final Map<Node, String> loopLabels = new HashMap<>();
    // Maps loop header nodes to their assigned IDs
    private final Map<Node, Integer> loopLabelIds = new HashMap<>();
    // Maps block label strings (old style like "node_block") to new style (e.g., "block1")
    private final Map<String, String> blockLabelMapping = new HashMap<>();
    // Maps block label strings to their assigned IDs
    private final Map<String, Integer> blockLabelIds = new HashMap<>();
    // Reference to the detected return block (for special handling)
    private LabeledBlockStructure detectedReturnBlock = null;
    //Code dialect
    private Dialect dialect;

    /**
     * Creates a new StructureDetector for the given CFG.
     * 
     * @param entryNode the entry node of the CFG
     * @param dialect Code dialect
     */
    public StructureDetector(Node entryNode, Dialect dialect) {
        this.entryNode = entryNode;
        this.allNodes = collectAllNodes(entryNode);
        // Build nodesByLabel map
        for (Node node : allNodes) {
            nodesByLabel.put(node.getLabel(), node);
        }
        this.dialect = dialect;
    }
    
    /**
     * Creates a new StructureDetector with the given entry node and all parsed nodes.
     * This is used by fromGraphviz to preserve all nodes including those not reachable from entry.
     * 
     * @param entryNode the entry node of the CFG
     * @param parsedNodes map of all parsed nodes by their labels
     * @param dialect Code dialect
     */
    private StructureDetector(Node entryNode, Map<String, Node> parsedNodes, Dialect dialect) {
        this.entryNode = entryNode;
        this.allNodes = collectAllNodes(entryNode);
        // Store all parsed nodes (including those not reachable from entry)
        this.nodesByLabel = new LinkedHashMap<>(parsedNodes);
        this.dialect = dialect;
    }
    
    /**
     * Gets or assigns a loop label for a given loop header node.
     * Uses global counter for sequential naming (loop_0, loop_1, etc.).
     */
    private String getLoopLabel(Node loopHeader) {
        if (!loopLabels.containsKey(loopHeader)) {
            int id = globalLabelCounter++;
            loopLabels.put(loopHeader, "loop_" + id);
            loopLabelIds.put(loopHeader, id);
        }
        return loopLabels.get(loopHeader);
    }
    
    /**
     * Gets or assigns a global ID for a loop.
     * All loops get a unique ID, even if they don't need a visible label.
     * @param loopHeader the loop header node
     * @return the global ID (never -1)
     */
    private int getLoopLabelId(Node loopHeader) {
        Integer id = loopLabelIds.get(loopHeader);
        if (id == null) {
            // Assign a new ID for this loop
            id = globalLabelCounter++;
            loopLabelIds.put(loopHeader, id);
            loopLabels.put(loopHeader, "loop_" + id);
        }
        return id;
    }
    
    /**
     * Gets or assigns a switch label for a given switch start node.
     * Uses the same labeling system as loops (loop_0, loop_1, etc.) since switches
     * can be targets of break statements similar to loops.
     */
    private String getSwitchLabel(Node switchStart) {
        if (!loopLabels.containsKey(switchStart)) {
            int id = globalLabelCounter++;
            loopLabels.put(switchStart, "loop_" + id);
            loopLabelIds.put(switchStart, id);
        }
        return loopLabels.get(switchStart);
    }
    
    /**
     * Gets or assigns a global ID for a switch.
     * All switches get a unique ID, even if they don't need a visible label.
     * @param switchStart the switch start node
     * @return the global ID (never -1)
     */
    private int getSwitchLabelId(Node switchStart) {
        Integer id = loopLabelIds.get(switchStart);
        if (id == null) {
            // Assign a new ID for this switch
            id = globalLabelCounter++;
            loopLabelIds.put(switchStart, id);
            loopLabels.put(switchStart, "loop_" + id);
        }
        return id;
    }
    
    /**
     * Gets the mapped block label, converting old-style (node_block) to new-style (block_0).
     * If no mapping exists, creates a new one with the global counter.
     */
    private String getBlockLabel(String oldLabel) {
        if (!blockLabelMapping.containsKey(oldLabel)) {
            int id = globalLabelCounter++;
            blockLabelMapping.put(oldLabel, "block_" + id);
            blockLabelIds.put(oldLabel, id);
        }
        return blockLabelMapping.get(oldLabel);
    }
    
    /**
     * Gets the global ID for a block label.
     * @param oldLabel the original block label
     * @return the global ID, or -1 if not found
     */
    private int getBlockLabelId(String oldLabel) {
        Integer id = blockLabelIds.get(oldLabel);
        return id != null ? id : -1;
    }
    
    /**
     * Resets the global label counter and mappings.
     * Called at the start of toPseudocode() to ensure consistent labeling.
     */
    private void resetLabelCounters() {
        globalLabelCounter = 0;
        loopLabels.clear();
        loopLabelIds.clear();
        blockLabelMapping.clear();
        blockLabelIds.clear();
    }
    
    /**
     * Detects labeled blocks and pre-assigns loop labels in the correct order.
     * This ensures labels are numbered sequentially as they appear in the output:
     * 1. First, blocks outside loops are detected (e.g., block_0)
     * 2. Then, loops with breaks get their labels pre-assigned (e.g., loop_1)
     * 3. Finally, blocks inside loops are detected (e.g., block_2, block_3)
     * 
     * @param loops the detected loop structures
     * @param ifs the detected if structures
     */
    private void detectBlocksAndPreAssignLoopLabels(List<LoopStructure> loops, List<IfStructure> ifs, List<SwitchStructure> switches) {
        // First, detect labeled blocks for skip patterns (outside of loops)
        // Pass switches so blocks that will be absorbed by switches don't consume counter values
        detectSkipBlocks(ifs, switches);
        
        // Build a lookup map for loops
        Map<Node, LoopStructure> loopHeaders = new HashMap<>();
        for (LoopStructure loop : loops) {
            LoopStructure existing = loopHeaders.get(loop.header);
            if (existing == null) {
                loopHeaders.put(loop.header, loop);
            } else {
                // Merge loop bodies when there are multiple back-edges to the same header
                // This handles cases where different paths through the loop have different back-edges
                Set<Node> mergedBody = new HashSet<>(existing.body);
                mergedBody.addAll(loop.body);
                
                // Keep the back-edge source from the larger body (primary path)
                Node backEdgeSrc = existing.body.size() >= loop.body.size() ? 
                                   existing.backEdgeSource : loop.backEdgeSource;
                
                // Merge breaks from both loop structures, but only if target is OUTSIDE merged body
                List<BreakEdge> mergedBreaks = new ArrayList<>();
                for (BreakEdge brk : existing.breaks) {
                    if (!mergedBody.contains(brk.to)) {
                        mergedBreaks.add(brk);
                    }
                }
                for (BreakEdge brk : loop.breaks) {
                    if (mergedBody.contains(brk.to)) {
                        continue; // Skip breaks where target is now inside merged body
                    }
                    boolean alreadyExists = false;
                    for (BreakEdge existingBrk : mergedBreaks) {
                        if (existingBrk.from.equals(brk.from) && existingBrk.to.equals(brk.to)) {
                            alreadyExists = true;
                            break;
                        }
                    }
                    if (!alreadyExists) {
                        mergedBreaks.add(brk);
                    }
                }
                
                // Merge continues from both loop structures
                List<ContinueEdge> mergedContinues = new ArrayList<>(existing.continues);
                for (ContinueEdge cont : loop.continues) {
                    boolean alreadyExists = false;
                    for (ContinueEdge existingCont : mergedContinues) {
                        if (existingCont.from.equals(cont.from) && existingCont.to.equals(cont.to)) {
                            alreadyExists = true;
                            break;
                        }
                    }
                    if (!alreadyExists) {
                        mergedContinues.add(cont);
                    }
                }
                
                LoopStructure merged = new LoopStructure(existing.header, mergedBody, backEdgeSrc);
                merged.breaks.clear();
                merged.breaks.addAll(mergedBreaks);
                merged.continues.clear();
                merged.continues.addAll(mergedContinues);
                loopHeaders.put(loop.header, merged);
            }
        }
        
        // Pre-assign switch labels for switches that need them (before loop labels)
        // Switches need labels when they have breaks from inside nested loops
        Map<Node, LabeledBlockStructure> blockStarts = new HashMap<>();
        for (LabeledBlockStructure block : labeledBlocks) {
            blockStarts.put(block.startNode, block);
        }
        
        for (SwitchStructure sw : switches) {
            LabeledBlockStructure switchBlock = blockStarts.get(sw.startNode);
            if (switchBlock != null && !switchBlock.breaks.isEmpty() && switchBlock.endNode.equals(sw.mergeNode)) {
                // Check if any break is from inside a nested loop
                boolean needsLabel = false;
                for (LabeledBreakEdge breakEdge : switchBlock.breaks) {
                    for (LoopStructure loop : loopHeaders.values()) {
                        if (loop.body.contains(breakEdge.from)) {
                            needsLabel = true;
                            break;
                        }
                    }
                    if (needsLabel) break;
                }
                if (needsLabel) {
                    getSwitchLabel(sw.startNode);
                }
            }
        }
        
        // Pre-assign loop labels before detecting blocks inside loops
        // This ensures loops get sequential numbers before their inner blocks
        for (LoopStructure loop : loops) {
            if (!loop.breaks.isEmpty()) {
                getLoopLabel(loop.header);
            }
        }
        
        // Then, detect labeled blocks for continue semantics (inside loops)
        detectContinueBlocks(loops);
    }

    /**
     * Parses a Graphviz/DOT format string and builds a CFG.
     * The first node encountered becomes the entry node.
     * Supports chained edges like: a->b->c
     * 
     * @param dot the DOT format string
     * @return a StructureDetector for the parsed CFG
     */
    public static StructureDetector fromGraphviz(String dot) {
        Node.resetIdCounter();
        Map<String, Node> nodes = new LinkedHashMap<>();
        Node firstNode = null;
        DotDialect dotDialect = DotDialect.INSTANCE;
        
        // Remove "digraph {" and "}" wrapper
        String content = dot.trim();
        if (content.startsWith("digraph")) {
            int start = content.indexOf('{');
            int end = content.lastIndexOf('}');
            if (start != -1 && end != -1) {
                content = content.substring(start + 1, end);
            }
        }
        
        // Parse each line/statement
        String[] statements = content.split(";");
        for (String statement : statements) {
            statement = statement.trim();
            if (statement.isEmpty()) continue;
            
            // Handle edge definitions (may be chained: a->b->c)
            if (statement.contains("->")) {
                String[] parts = statement.split("->");
                for (int i = 0; i < parts.length - 1; i++) {
                    String fromLabel = parts[i].trim();
                    String toLabel = parts[i + 1].trim();
                    
                    // Get or create nodes
                    Node fromNode = nodes.get(fromLabel);
                    if (fromNode == null) {
                        fromNode = new Node(fromLabel);
                        nodes.put(fromLabel, fromNode);
                        if (firstNode == null) {
                            firstNode = fromNode;
                        }
                    }
                    
                    Node toNode = nodes.get(toLabel);
                    if (toNode == null) {
                        toNode = new Node(toLabel);
                        nodes.put(toLabel, toNode);
                    }
                    
                    // Add edge
                    fromNode.addSuccessor(toNode);
                }
            } else if (statement.contains("[")) {
                // Handle standalone node with attributes: nodeName [key1=value1 key2="value2" ...]
                int bracketStart = statement.indexOf('[');
                int bracketEnd = statement.lastIndexOf(']');
                if (bracketStart != -1 && bracketEnd != -1 && bracketEnd > bracketStart) {
                    String nodeLabel = statement.substring(0, bracketStart).trim();
                    String attributesStr = statement.substring(bracketStart + 1, bracketEnd).trim();
                    
                    // Get or create the node
                    Node node = nodes.get(nodeLabel);
                    if (node == null) {
                        node = new Node(nodeLabel);
                        nodes.put(nodeLabel, node);
                        if (firstNode == null) {
                            firstNode = node;
                        }
                    }
                    
                    // Parse attributes
                    parseAndSetAttributes(node, attributesStr, dotDialect);
                }
            }
        }
        
        if (firstNode == null) {
            throw new IllegalArgumentException("No nodes found in DOT string");
        }
        
        return new StructureDetector(firstNode, nodes, dotDialect);
    }
    
    /**
     * Parses DOT attribute string and sets attributes on the node.
     * Handles both quoted and unquoted keys and values.
     * Format: key1=value1 "key2"=value2 key3="value3" "key4"="value 4"
     * 
     * @param node the node to set attributes on
     * @param attributesStr the attributes string (content between [ and ])
     * @param dotDialect the DOT dialect for setting attributes
     */
    private static void parseAndSetAttributes(Node node, String attributesStr, DotDialect dotDialect) {
        int pos = 0;
        int len = attributesStr.length();
        
        while (pos < len) {
            // Skip whitespace
            while (pos < len && Character.isWhitespace(attributesStr.charAt(pos))) {
                pos++;
            }
            if (pos >= len) break;
            
            // Parse key (may be quoted or unquoted identifier)
            String key;
            if (attributesStr.charAt(pos) == '"') {
                // Quoted key
                int endQuote = attributesStr.indexOf('"', pos + 1);
                if (endQuote == -1) break;
                key = attributesStr.substring(pos + 1, endQuote);
                pos = endQuote + 1;
            } else {
                // Unquoted identifier
                int start = pos;
                while (pos < len && isIdentifierChar(attributesStr.charAt(pos))) {
                    pos++;
                }
                if (pos == start) break;
                key = attributesStr.substring(start, pos);
            }
            
            // Skip whitespace before '='
            while (pos < len && Character.isWhitespace(attributesStr.charAt(pos))) {
                pos++;
            }
            
            // Expect '='
            if (pos >= len || attributesStr.charAt(pos) != '=') {
                break;
            }
            pos++; // Skip '='
            
            // Skip whitespace after '='
            while (pos < len && Character.isWhitespace(attributesStr.charAt(pos))) {
                pos++;
            }
            if (pos >= len) break;
            
            // Parse value (may be quoted or unquoted identifier)
            String value;
            if (attributesStr.charAt(pos) == '"') {
                // Quoted value
                int endQuote = attributesStr.indexOf('"', pos + 1);
                if (endQuote == -1) break;
                value = attributesStr.substring(pos + 1, endQuote);
                pos = endQuote + 1;
            } else {
                // Unquoted identifier
                int start = pos;
                while (pos < len && isIdentifierChar(attributesStr.charAt(pos))) {
                    pos++;
                }
                if (pos == start) break;
                value = attributesStr.substring(start, pos);
            }
            
            // Set the attribute
            dotDialect.setAttribute(node, key, value);
        }
    }
    
    /**
     * Checks if a character is valid in an unquoted DOT identifier.
     */
    private static boolean isIdentifierChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    /**
     * Returns the entry node of the CFG.
     * 
     * @return the entry node
     */
    public Node getEntryNode() {
        return entryNode;
    }

    /**
     * Adds a try-catch structure (exception range) to the CFG.
     * The try body nodes are those protected by the exception handler.
     * The catch body nodes handle the exception.
     * 
     * @param tryNodes nodes in the try block
     * @param catchNodes nodes in the catch block
     */
    public void addException(Set<Node> tryNodes, Set<Node> catchNodes) {
        int exceptionIndex = tryStructures.size();  // Use current size as the index
        TryStructure tryStruct = new TryStructure(tryNodes, catchNodes, exceptionIndex);
        tryStructures.add(tryStruct);
        
        // Add catch nodes to allNodes if not already present
        for (Node catchNode : catchNodes) {
            if (!allNodes.contains(catchNode)) {
                allNodes.add(catchNode);
            }
            if (!nodesByLabel.containsKey(catchNode.getLabel())) {
                nodesByLabel.put(catchNode.getLabel(), catchNode);
            }
        }
    }
    
    /**
     * Parses exception definitions in string format and adds them to the detector.
     * Format: "node1, node2, node3 => catchnode1, catchnode2, catchnode3; ..."
     * 
     * Multiple exception ranges are separated by semicolons.
     * Left side of "=>" contains try body node labels.
     * Right side of "=>" contains catch body node labels.
     * 
     * Note: Try body nodes must exist in the graph (parsed from DOT). Catch body nodes 
     * are looked up in the existing graph first. If a catch node doesn't exist, this
     * method will NOT create a new node - use the full DOT graph including catch body
     * edges to ensure all nodes are properly created with their edges.
     * 
     * @param exceptionDef the exception definition string
     * @throws IllegalArgumentException if a try body node label is not found in the graph
     */
    public void parseExceptions(String exceptionDef) {
        if (exceptionDef == null || exceptionDef.trim().isEmpty()) {
            return;
        }
        
        // Split by semicolons for multiple exception ranges
        String[] ranges = exceptionDef.split(";");
        for (String range : ranges) {
            range = range.trim();
            if (range.isEmpty()) {
                continue;
            }
            
            // Split by "=>"
            String[] parts = range.split("=>");
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid exception format: " + range + ". Expected 'tryNodes => catchNodes'");
            }
            
            String tryPart = parts[0].trim();
            String catchPart = parts[1].trim();
            
            // Parse try nodes
            Set<Node> tryNodes = new LinkedHashSet<>();
            for (String label : tryPart.split(",")) {
                label = label.trim();
                if (!label.isEmpty()) {
                    Node node = nodesByLabel.get(label);
                    if (node == null) {
                        throw new IllegalArgumentException("Node not found: " + label);
                    }
                    tryNodes.add(node);
                }
            }
            
            // Parse catch nodes
            Set<Node> catchNodes = new LinkedHashSet<>();
            for (String label : catchPart.split(",")) {
                label = label.trim();
                if (!label.isEmpty()) {
                    Node node = nodesByLabel.get(label);
                    if (node == null) {
                        throw new IllegalArgumentException("Node not found: " + label + 
                            ". Make sure the DOT graph includes edges for catch body nodes.");
                    }
                    catchNodes.add(node);
                }
            }
            
            if (!tryNodes.isEmpty() && !catchNodes.isEmpty()) {
                addException(tryNodes, catchNodes);
            }
        }
    }
    
    /**
     * Returns all registered try-catch structures.
     * 
     * @return list of try structures
     */
    public List<TryStructure> getTryStructures() {
        return new ArrayList<>(tryStructures);
    }

    /**
     * Registers a labeled block structure.
     * A labeled block is a region where control can jump to the end using "break label;".
     * 
     * @param label the label name for the block
     * @param labelId the global ID of the block
     * @param startNode the first node inside the labeled block
     * @param endNode the node after the labeled block (the break target)
     */
    public void addLabeledBlock(String label, int labelId, Node startNode, Node endNode) {
        addLabeledBlock(label, labelId, startNode, endNode, null);
    }
    
    /**
     * Registers a labeled block structure with loop awareness.
     * Edges that are normal loop exits (false branch of loop condition) are not counted as labeled breaks.
     * 
     * @param label the label name for the block
     * @param labelId the global ID of the block
     * @param startNode the first node inside the labeled block
     * @param endNode the node after the labeled block (the break target)
     * @param loopHeaders map of loop headers to their loop structures, may be null
     */
    private void addLabeledBlock(String label, int labelId, Node startNode, Node endNode, Map<Node, LoopStructure> loopHeaders) {
        Set<Node> body = new HashSet<>();
        // Collect all nodes in the block (reachable from start but before end)
        collectBlockBody(startNode, endNode, body);
        
        LabeledBlockStructure block = new LabeledBlockStructure(label, labelId, startNode, endNode, body);
        
        // Detect breaks within the block (edges that go to endNode from within the block)
        // Exclude edges that are normal loop exits (will be rendered as 'break;' from while(true))
        // Also exclude edges that are just normal merge points of if-statements
        for (Node node : body) {
            for (Node succ : node.succs) {
                if (succ.equals(endNode)) {
                    // Check if this is a normal loop exit (should not count as labeled break)
                    boolean isNormalLoopExit = false;
                    if (loopHeaders != null) {
                        LoopStructure loop = loopHeaders.get(node);
                        if (loop != null && !loop.body.contains(endNode)) {
                            // This node is a loop header and endNode is outside the loop
                            // This is a normal loop exit condition, not a labeled break
                            isNormalLoopExit = true;
                        }
                    }
                    
                    // Check if this is just a normal merge of an if-statement (not a real break)
                    // If endNode is the natural convergence point for ALL paths from this node,
                    // then it's not a labeled break, it's just normal flow
                    boolean isNormalMerge = isNormalMergePoint(node, endNode, body);
                    
                    if (!isNormalLoopExit && !isNormalMerge) {
                        block.breaks.add(new LabeledBreakEdge(node, endNode, label, labelId));
                    }
                }
            }
        }
        
        labeledBlocks.add(block);
    }
    
    /**
     * Checks if endNode is the natural merge point for all paths from node within the body.
     * If all paths from node converge at endNode without any branching to other destinations,
     * then endNode is a natural merge point (not a labeled break).
     * 
     * For single-successor nodes, we need to check if this node is on a "skip" path - i.e.,
     * a path that bypasses other code in the block. If there are other, longer paths from
     * the block that go through more code before reaching endNode, then this is a skip.
     */
    private boolean isNormalMergePoint(Node node, Node endNode, Set<Node> body) {
        // If node has only one successor (going to endNode), we need to check if this
        // is on a "skip" path. We do this by checking if node's predecessors have
        // other paths that would go through more code before reaching endNode.
        if (node.succs.size() <= 1) {
            // Check if this node is on a conditional branch where the other branch
            // goes through more code before reaching endNode
            for (Node pred : node.preds) {
                if (body.contains(pred) && pred.succs.size() >= 2) {
                    // This node is the successor of a conditional
                    // Check if OTHER successors of that conditional lead to longer paths
                    for (Node otherSucc : pred.succs) {
                        if (!otherSucc.equals(node) && body.contains(otherSucc)) {
                            // First check: can the other branch also reach THIS node?
                            // If yes, then this node is a convergence point, not a skip
                            if (canReachNodeWithin(otherSucc, node, body)) {
                                // Other branch can reach this node, so this is a convergence point
                                // The edge from this node to endNode is normal flow, not a skip
                                continue;
                            }
                            
                            // Check if the other path reaches endNode eventually
                            // and goes through different/more nodes
                            int pathLengthFromThis = shortestPathLength(node, endNode, body);
                            int pathLengthFromOther = shortestPathLength(otherSucc, endNode, body);
                            
                            // If the other path is longer, this path is a "skip"
                            if (pathLengthFromOther > pathLengthFromThis) {
                                return false; // Not a normal merge, it's a skip
                            }
                        }
                    }
                }
            }
            // No skip detected, it's normal flow
            return true;
        }
        
        // For a conditional node (2 successors), check if BOTH branches eventually
        // lead only to endNode (i.e., endNode is the merge point of this conditional)
        // This means the edge to endNode is just the normal merge, not a labeled break
        // HOWEVER, if one branch goes DIRECTLY to endNode while others take longer paths,
        // it IS a skip pattern that needs a labeled break.
        int directPathLength = 1; // Direct edge to endNode
        for (Node succ : node.succs) {
            if (succ.equals(endNode)) {
                continue; // This is the edge we're checking
            }
            // Check if the other branch eventually reaches endNode through simple paths
            if (!allPathsLeadTo(succ, endNode, body, new HashSet<>())) {
                // The other branch doesn't exclusively lead to endNode
                // So this might be a real labeled break
                return false;
            }
            // Check if the other path is longer than the direct path
            // Note: Since allPathsLeadTo returned true, shortestPathLength will return a valid distance.
            // If somehow no path exists, it returns Integer.MAX_VALUE which is > 1, correctly triggering
            // the skip detection.
            int otherPathLength = shortestPathLength(succ, endNode, body);
            if (otherPathLength > directPathLength) {
                // The other branch takes a longer path to endNode
                // This means our direct edge is a "skip" - not a normal merge
                return false;
            }
        }
        
        // All other branches lead to endNode with similar path lengths, so this is a normal merge
        return true;
    }
    
    /**
     * Returns the shortest path length from start to target within body.
     * Returns Integer.MAX_VALUE if no path exists.
     */
    private int shortestPathLength(Node start, Node target, Set<Node> body) {
        if (start.equals(target)) {
            return 0;
        }
        
        Map<Node, Integer> dist = new HashMap<>();
        Queue<Node> queue = new LinkedList<>();
        queue.add(start);
        dist.put(start, 0);
        
        while (!queue.isEmpty()) {
            Node current = queue.poll();
            int currentDist = dist.get(current);
            
            for (Node succ : current.succs) {
                if (succ.equals(target)) {
                    return currentDist + 1;
                }
                if (body.contains(succ) && !dist.containsKey(succ)) {
                    dist.put(succ, currentDist + 1);
                    queue.add(succ);
                }
            }
        }
        
        return Integer.MAX_VALUE;
    }
    
    /**
     * Checks if all paths from start within body lead to target.
     */
    private boolean allPathsLeadTo(Node start, Node target, Set<Node> body, Set<Node> visited) {
        if (start.equals(target)) {
            return true;
        }
        if (!body.contains(start) || visited.contains(start)) {
            return false;
        }
        visited.add(start);
        
        if (start.succs.isEmpty()) {
            return false; // Dead end that's not the target
        }
        
        for (Node succ : start.succs) {
            if (!allPathsLeadTo(succ, target, body, visited)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Collects all nodes in a labeled block body.
     */
    private void collectBlockBody(Node start, Node end, Set<Node> body) {
        if (start == null || start.equals(end) || body.contains(start)) {
            return;
        }
        body.add(start);
        for (Node succ : start.succs) {
            collectBlockBody(succ, end, body);
        }
    }

    /**
     * Returns all registered labeled blocks.
     * 
     * @return list of labeled block structures
     */
    public List<LabeledBlockStructure> getLabeledBlocks() {
        return new ArrayList<>(labeledBlocks);
    }

    /**
     * Collects all nodes reachable from the entry node using BFS.
     */
    private List<Node> collectAllNodes(Node entry) {
        List<Node> result = new ArrayList<>();
        Set<Node> visited = new HashSet<>();
        Queue<Node> queue = new LinkedList<>();
        queue.add(entry);
        visited.add(entry);

        while (!queue.isEmpty()) {
            Node current = queue.poll();
            result.add(current);
            for (Node succ : current.succs) {
                if (!visited.contains(succ)) {
                    visited.add(succ);
                    queue.add(succ);
                }
            }
        }
        return result;
    }

    /**
     * Detects all if-statement structures in the CFG.
     * An if-statement is identified by a node with exactly 2 successors.
     * 
     * @return list of detected if structures
     */
    public List<IfStructure> detectIfs() {
        List<IfStructure> ifs = new ArrayList<>();
        
        for (Node node : allNodes) {
            if (node.isConditional()) {
                Node trueBranch = node.succs.get(0);
                Node falseBranch = node.succs.get(1);
                Node mergeNode = findMergeNode(trueBranch, falseBranch);
                ifs.add(new IfStructure(node, trueBranch, falseBranch, mergeNode));
            }
        }
        
        return ifs;
    }

    /**
     * Finds the merge node where two branches come together.
     * Uses post-dominator analysis simplified approach.
     * Avoids false positives from loop back-edges by doing level-by-level BFS from both branches.
     */
    private Node findMergeNode(Node branch1, Node branch2) {
        // Special case: if one branch directly leads to the other
        if (branch1.succs.contains(branch2)) {
            return branch2;
        }
        if (branch2.succs.contains(branch1)) {
            return branch1;
        }
        
        // Do BFS from both branches simultaneously, looking for the first common node
        // This avoids false positives from loop cycles
        Set<Node> reachable1 = new HashSet<>();
        Set<Node> reachable2 = new HashSet<>();
        Queue<Node> queue1 = new LinkedList<>();
        Queue<Node> queue2 = new LinkedList<>();
        
        reachable1.add(branch1);
        reachable2.add(branch2);
        queue1.add(branch1);
        queue2.add(branch2);
        
        // If branches are the same, that's the merge
        if (branch1.equals(branch2)) {
            return branch1;
        }
        
        // Expand both frontiers level by level, checking for intersection
        int maxIterations = allNodes.size() * 2; // Prevent infinite loops
        for (int i = 0; i < maxIterations && (!queue1.isEmpty() || !queue2.isEmpty()); i++) {
            // Expand frontier 1
            if (!queue1.isEmpty()) {
                Node current = queue1.poll();
                for (Node succ : current.succs) {
                    // Found common node - but don't consider starting branches as merge points
                    // This prevents false merges when paths cycle back through the loop
                    if (reachable2.contains(succ) && !succ.equals(branch1) && !succ.equals(branch2)) {
                        return succ;
                    }
                    if (!reachable1.contains(succ)) {
                        reachable1.add(succ);
                        queue1.add(succ);
                    }
                }
            }
            
            // Expand frontier 2
            if (!queue2.isEmpty()) {
                Node current = queue2.poll();
                for (Node succ : current.succs) {
                    // Found common node - but don't consider starting branches as merge points
                    if (reachable1.contains(succ) && !succ.equals(branch1) && !succ.equals(branch2)) {
                        return succ;
                    }
                    if (!reachable2.contains(succ)) {
                        reachable2.add(succ);
                        queue2.add(succ);
                    }
                }
            }
        }
        
        return null; // No merge node found (e.g., branches don't converge)
    }   
    
    /**
     * Checks if target is reachable from start within a loop's body.
     * Only considers nodes within the loop.
     */
    private boolean isReachableWithinLoop(Node start, Node target, LoopStructure loop) {
        if (start.equals(target)) {
            return true;
        }
        // If target is the loop header, it should only be reachable if we explicitly
        // want to check for back-edge reachability, which this function doesn't support.
        // The header is the loop entry point and shouldn't be "reachable" from within
        // the loop body in the context of skip block detection.
        if (target.equals(loop.header)) {
            return false;
        }
        Set<Node> visited = new HashSet<>();
        Queue<Node> queue = new LinkedList<>();
        queue.add(start);
        visited.add(start);
        
        while (!queue.isEmpty()) {
            Node current = queue.poll();
            for (Node succ : current.succs) {
                if (succ.equals(target)) {
                    return true;
                }
                if (!visited.contains(succ) && loop.body.contains(succ) && !succ.equals(loop.header)) {
                    visited.add(succ);
                    queue.add(succ);
                }
            }
        }
        return false;
    }
    
    /**
     * Finds the path from start to target, stopping at conditionals.
     * Returns nodes between start (inclusive) and target (exclusive).
     * Returns empty list if target is not reachable without going through conditionals that don't lead to target.
     */
    private List<Node> findPathToNode(Node start, Node target, Map<Node, IfStructure> ifConditions, LoopStructure loop) {
        List<Node> path = new ArrayList<>();
        Node current = start;
        Set<Node> visited = new HashSet<>();
        
        while (current != null && !current.equals(target) && !visited.contains(current)) {
            visited.add(current);
            
            // If this is a conditional, check if target is reachable from either branch
            if (ifConditions.containsKey(current)) {
                IfStructure ifStruct = ifConditions.get(current);
                
                // Check if target is reachable from true branch
                if (isReachableWithinLoop(ifStruct.trueBranch, target, loop)) {
                    // Don't add the conditional node to path, recurse into true branch
                    List<Node> truePath = findPathToNode(ifStruct.trueBranch, target, ifConditions, loop);
                    path.addAll(truePath);
                    return path;
                }
                // Check if target is reachable from false branch
                if (isReachableWithinLoop(ifStruct.falseBranch, target, loop)) {
                    // Don't add the conditional node to path, recurse into false branch
                    List<Node> falsePath = findPathToNode(ifStruct.falseBranch, target, ifConditions, loop);
                    path.addAll(falsePath);
                    return path;
                }
                // Target not reachable from either branch, return empty path
                return new ArrayList<>();
            }
            
            path.add(current);
            
            // Follow single successor
            if (current.succs.size() == 1) {
                current = current.succs.get(0);
            } else {
                break;
            }
        }
        
        return path;
    }

    /**
     * Gets all nodes reachable from the given node.
     */
    private Set<Node> getReachableNodes(Node start) {
        Set<Node> reachable = new HashSet<>();
        Queue<Node> queue = new LinkedList<>();
        queue.add(start);
        reachable.add(start);
        
        while (!queue.isEmpty()) {
            Node current = queue.poll();
            for (Node succ : current.succs) {
                if (!reachable.contains(succ)) {
                    reachable.add(succ);
                    queue.add(succ);
                }
            }
        }
        
        return reachable;
    }

    /**
     * Checks if a node directly reaches (is a direct successor of) another node.
     */
    private boolean nodeDirectlyReaches(Node from, Node to) {
        if (from == null || to == null) return false;
        for (Node succ : from.succs) {
            if (succ.equals(to)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Detects all loop structures in the CFG using back-edge detection.
     * A back-edge is an edge from a node to one of its dominators,
     * indicating a loop.
     * 
     * @return list of detected loop structures
     */
    public List<LoopStructure> detectLoops() {
        List<LoopStructure> loops = new ArrayList<>();
        
        // Compute dominators
        Map<Node, Set<Node>> dominators = computeDominators();
        
        // Find back edges: edge (u, v) where v dominates u
        List<Edge> backEdges = new ArrayList<>();
        for (Node node : allNodes) {
            Set<Node> nodeDominators = dominators.get(node);
            for (Node succ : node.succs) {
                if (nodeDominators != null && nodeDominators.contains(succ)) {
                    backEdges.add(new Edge(node, succ));
                }
            }
        }
        
        // For each back edge, identify the natural loop
        for (Edge backEdge : backEdges) {
            Node header = backEdge.to;
            Node tail = backEdge.from;
            Set<Node> loopBody = findNaturalLoop(header, tail);
            LoopStructure loop = new LoopStructure(header, loopBody, tail);
            
            // Detect breaks and continues within the loop
            detectBreaksAndContinues(loop);
            
            loops.add(loop);
        }
        
        return loops;
    }
  

    /**
     * Computes dominators for all nodes using iterative dataflow analysis.
     * A node D dominates node N if every path from entry to N goes through D.
     * Exception handler entry points (nodes with no predecessors) are treated specially
     * to avoid breaking dominator relationships for loop detection.
     */
    private Map<Node, Set<Node>> computeDominators() {
        Map<Node, Set<Node>> dominators = new HashMap<>();
        
        // Identify catch block entry points (nodes with no predecessors except the entry node)
        Set<Node> catchEntryNodes = new HashSet<>();
        for (Node node : allNodes) {
            if (!node.equals(entryNode) && node.preds.isEmpty()) {
                catchEntryNodes.add(node);
            }
        }
        
        // Also identify all nodes reachable only from catch entry points
        Set<Node> catchBodyNodes = new HashSet<>(catchEntryNodes);
        boolean added = true;
        while (added) {
            added = false;
            for (Node node : allNodes) {
                if (catchBodyNodes.contains(node)) continue;
                // Check if all predecessors (excluding self-loops) are in catchBodyNodes
                boolean allPredsAreCatch = !node.preds.isEmpty();
                for (Node pred : node.preds) {
                    // Skip self-loops when checking
                    if (pred.equals(node)) continue;
                    if (!catchBodyNodes.contains(pred)) {
                        allPredsAreCatch = false;
                        break;
                    }
                }
                // Also check that the node has at least one non-self predecessor
                boolean hasNonSelfPred = false;
                for (Node pred : node.preds) {
                    if (!pred.equals(node)) {
                        hasNonSelfPred = true;
                        break;
                    }
                }
                if (allPredsAreCatch && hasNonSelfPred) {
                    catchBodyNodes.add(node);
                    added = true;
                }
            }
        }
        
        // Initialize: entry dominates only itself, others are dominated by all
        Set<Node> allNodesSet = new HashSet<>(allNodes);
        for (Node node : allNodes) {
            if (node.equals(entryNode)) {
                Set<Node> entryDom = new HashSet<>();
                entryDom.add(entryNode);
                dominators.put(node, entryDom);
            } else {
                dominators.put(node, new HashSet<>(allNodesSet));
            }
        }
        
        // Iterate until no changes
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Node node : allNodes) {
                if (node.equals(entryNode)) continue;
                
                Set<Node> newDom;
                
                // Get predecessors, filtering out catch body nodes for dominator computation
                List<Node> filteredPreds = new ArrayList<>();
                for (Node pred : node.preds) {
                    if (!catchBodyNodes.contains(pred)) {
                        filteredPreds.add(pred);
                    }
                }
                
                // Handle nodes with no filtered predecessors - they are only dominated by themselves
                if (filteredPreds.isEmpty()) {
                    newDom = new HashSet<>();
                } else {
                    newDom = new HashSet<>(allNodesSet);
                    // Intersect dominators of all filtered predecessors
                    for (Node pred : filteredPreds) {
                        Set<Node> predDom = dominators.get(pred);
                        if (predDom != null) {
                            newDom.retainAll(predDom);
                        }
                    }
                }
                
                // Add the node itself
                newDom.add(node);
                
                if (!newDom.equals(dominators.get(node))) {
                    dominators.put(node, newDom);
                    changed = true;
                }
            }
        }
        
        return dominators;
    }

    /**
     * Finds the natural loop given a back edge.
     * The natural loop is the set of nodes that can reach the tail
     * without going through the header, plus the header itself.
     */
    private Set<Node> findNaturalLoop(Node header, Node tail) {
        Set<Node> loop = new HashSet<>();
        loop.add(header);
        
        if (!header.equals(tail)) {
            loop.add(tail);
            Stack<Node> stack = new Stack<>();
            stack.push(tail);
            
            while (!stack.isEmpty()) {
                Node node = stack.pop();
                for (Node pred : node.preds) {
                    if (!loop.contains(pred)) {
                        loop.add(pred);
                        stack.push(pred);
                    }
                }
            }
        }
        
        return loop;
    }

    /**
     * Detects break and continue edges within a loop.
     * - Break: edge from a loop body node to a node outside the loop
     * - Continue: edge from a loop body node (not the back-edge source) to the header
     */
    private void detectBreaksAndContinues(LoopStructure loop) {
        for (Node node : loop.body) {
            for (Node succ : node.succs) {
                // Break: edge going outside the loop
                if (!loop.body.contains(succ)) {
                    loop.breaks.add(new BreakEdge(node, succ));
                }
                // Continue: edge to header that is not the main back-edge
                else if (succ.equals(loop.header) && !node.equals(loop.backEdgeSource)) {
                    loop.continues.add(new ContinueEdge(node, succ));
                }
            }
        }
    }

    /**
     * Automatically detects labeled blocks needed for for-loop continue semantics.
     * A labeled block is needed when there's an edge (direct or indirect) that jumps 
     * to the back-edge source (increment node) from within the loop body.
     * 
     * This pattern occurs in for-loops where "continue" jumps to the increment,
     * not directly to the condition check.
     * 
     * Enhanced to detect:
     * 1. Direct jumps to the back-edge source
     * 2. Indirect paths through intermediate non-conditional nodes
     * 3. Cross-loop continues (from inner loop to outer loop's increment)
     * 
     * @param loops the detected loops
     */
    private void detectContinueBlocks(List<LoopStructure> loops) {
        // First, find the main loop for each header (the one with the largest body)
        Map<Node, LoopStructure> mainLoops = new HashMap<>();
        for (LoopStructure loop : loops) {
            LoopStructure existing = mainLoops.get(loop.header);
            if (existing == null || loop.body.size() > existing.body.size()) {
                mainLoops.put(loop.header, loop);
            }
        }               
        
        // Detect skip blocks within loops (separate labeled blocks for skip patterns)
        detectSkipBlocksInLoops(loops, mainLoops);
    }
    
    /**
     * Detects labeled blocks for skip patterns within loops.
     * These are patterns where one branch of an if skips directly to a merge point
     * within the same loop iteration.
     */
    private void detectSkipBlocksInLoops(List<LoopStructure> loops, Map<Node, LoopStructure> mainLoops) {
        List<IfStructure> ifs = detectIfs();
        
        // First, collect all skip patterns grouped by their actual convergence point
        // (not just the if's merge, but where branches first meet)
        Map<Node, List<SkipPattern>> skipsByConvergence = new LinkedHashMap<>();
        
        for (LoopStructure loop : mainLoops.values()) {
            for (IfStructure ifStruct : ifs) {
                Node cond = ifStruct.conditionNode;
                if (!loop.body.contains(cond)) continue;
                
                // Skip if the condition is inside a NESTED loop - it should only be processed
                // in the context of its innermost loop, not outer loops.
                boolean isInNestedLoop = false;
                for (LoopStructure innerLoop : mainLoops.values()) {
                    if (innerLoop == loop) continue;
                    if (loop.body.contains(innerLoop.header) && innerLoop.body.contains(cond)) {
                        isInNestedLoop = true;
                        break;
                    }
                }
                if (isInNestedLoop) continue;
                
                Node trueBranch = ifStruct.trueBranch;
                Node falseBranch = ifStruct.falseBranch;
                Node mergeNode = ifStruct.mergeNode;
                
                if (trueBranch == null || falseBranch == null) continue;
                
                // Find the effective merge point:
                // 1. If one branch goes directly to a back-edge source (loop continuation), use that
                // 2. Otherwise, find the first convergence point
                Node effectiveMerge = null;
                boolean branchGoesDirectlyToMerge = false;
                
                // First, check if either branch goes directly to the CURRENT loop's back-edge source
                // This handles the case where an if inside a loop has one branch that skips to the
                // end of the loop body (back-edge source) directly.
                // This is important because the if's detected merge node might be OUTSIDE the loop,
                // but we still need to detect skip patterns to the back-edge source.
                Node currentBackEdgeSrc = loop.backEdgeSource;
                boolean hasValidBackEdgeSrc = currentBackEdgeSrc != null 
                    && !currentBackEdgeSrc.equals(loop.header)
                    && !currentBackEdgeSrc.equals(cond) 
                    && loop.body.contains(currentBackEdgeSrc);
                if (hasValidBackEdgeSrc) {
                    if (trueBranch.equals(currentBackEdgeSrc)) {
                        // First branch goes directly to back-edge source - NOT a skip pattern
                        // It's a normal if statement: if (!cond) { falseBranch } // then back-edge
                        // Don't set effectiveMerge
                    } else if (falseBranch.equals(currentBackEdgeSrc)) {
                        effectiveMerge = currentBackEdgeSrc;
                        branchGoesDirectlyToMerge = true;
                    }
                }
                
                // Also check nested loops' back-edge sources
                if (effectiveMerge == null) {
                    for (LoopStructure innerLoop : loops) {
                        if (!loop.body.contains(innerLoop.header)) continue; // Not a nested loop
                        if (innerLoop.header.equals(loop.header)) continue; // Skip loops with same header (different sizes)
                        
                        Node backEdgeSrc = innerLoop.backEdgeSource;
                        if (backEdgeSrc != null && loop.body.contains(backEdgeSrc)) {
                            // Check if either branch goes directly to this back-edge source
                            if (trueBranch.equals(backEdgeSrc)) {
                                // First branch goes directly to back-edge source - this is NOT a skip pattern.
                                // It's a normal if statement: if (!cond) { falseBranch } // then continue
                                // Don't set effectiveMerge to skip block creation
                                continue;
                            } else if (falseBranch.equals(backEdgeSrc)) {
                                effectiveMerge = backEdgeSrc;
                                branchGoesDirectlyToMerge = true;
                                break;
                            }
                        }
                    }
                }
                
                // Skip blocks are used for patterns where one branch jumps directly to a merge point
                // (like the back-edge source) while another branch takes a longer path.
                // When we detect such a skip pattern to the back-edge source, we need to process it
                // even if the if-structure's original merge node is outside the current loop.
                boolean hasSkipPatternToBackEdge = branchGoesDirectlyToMerge && effectiveMerge != null;
                if (!hasSkipPatternToBackEdge) {
                    // Standard case: require merge node to be inside the loop
                    if (mergeNode == null) continue;
                    if (!loop.body.contains(mergeNode)) continue;
                }
                
                // If either branch goes OUTSIDE the current loop, this is a BREAK pattern, not a skip.
                // Skip this conditional for skip block detection - it will be handled as a loop break.
                // This check is ALWAYS performed, even if branchGoesDirectlyToMerge is true,
                // because when one branch exits the loop (BREAK), we don't need a labeled block -
                // the loop break handling will take care of it.
                if (!loop.body.contains(trueBranch) || !loop.body.contains(falseBranch)) {
                    continue;
                }
                
                // If no back-edge source found, use first convergence
                if (effectiveMerge == null) {
                    Node convergence = findFirstConvergence(trueBranch, falseBranch, loop);
                    effectiveMerge = convergence != null ? convergence : mergeNode;
                }
                
                // If the effective merge is the loop header, this is a continue pattern, not a skip.
                // We don't need a labeled block - we can use simple continue statements.
                if (effectiveMerge.equals(loop.header)) {
                    continue;
                }
                
                // If one branch goes directly to the loop header (a continue), and the other branch
                // eventually leads to a back-edge source (which also continues the loop), this is
                // a simple if-continue pattern, not a skip that needs a labeled block.
                // Example: if (cond) { body_3; } // where body_3 -> loop_header (back edge)
                //          else { continue; }    // direct jump to loop_header
                if (trueBranch.equals(loop.header) || falseBranch.equals(loop.header)) {
                    continue;
                }
                
                // If a branch IS the merge point, this is a simple if-then pattern, not a skip
                // Example: if (!cond) { body; } where trueBranch==merge means empty true branch
                // No labeled block is needed for this pattern - it's just a normal if statement
                // EXCEPT when the merge is the back-edge source and a branch goes directly to it -
                // in that case, we DO need a labeled block to handle the skip pattern.
                if ((trueBranch.equals(effectiveMerge) || falseBranch.equals(effectiveMerge)) && !branchGoesDirectlyToMerge) {
                    continue;
                }
                
                // If one branch goes directly to the merge, create a block
                // This handles patterns like: if (cond) { complex_body } else { goto merge }
                // In this case, cond itself is the skip source - when the condition's false branch
                // goes directly to the merge, the condition acts as a "break" point.
                // The SkipPattern with cond as both condNode and skipSource indicates that
                // the condition should be treated as a labeled block boundary.
                if (branchGoesDirectlyToMerge) {
                    skipsByConvergence.computeIfAbsent(effectiveMerge, k -> new ArrayList<>())
                               .add(new SkipPattern(cond, effectiveMerge, loop));
                }
                
                // Check true branch for skip
                Node skipSource = findDirectJumpToMergeInLoop(trueBranch, effectiveMerge, loop);
                if (skipSource != null) {
                    skipsByConvergence.computeIfAbsent(effectiveMerge, k -> new ArrayList<>())
                               .add(new SkipPattern(cond, effectiveMerge, loop));
                }
                
                // Check false branch for skip
                skipSource = findDirectJumpToMergeInLoop(falseBranch, effectiveMerge, loop);
                if (skipSource != null) {
                    skipsByConvergence.computeIfAbsent(effectiveMerge, k -> new ArrayList<>())
                               .add(new SkipPattern(cond, effectiveMerge, loop));
                }
            }
        }
        
        // For each convergence point, find the outermost condition that needs a block
        for (Map.Entry<Node, List<SkipPattern>> entry : skipsByConvergence.entrySet()) {
            Node convergencePoint = entry.getKey();
            List<SkipPattern> patterns = entry.getValue();
            
            if (patterns.isEmpty()) continue;
            
            // Find the earliest condition node that dominates all skip patterns for this merge
            Node blockStart = findEarliestSkipBlockStart(patterns, ifs);
            
            if (blockStart != null && !blockStart.equals(convergencePoint)) {
                // Check if this is a normal if-else that converges at the convergence point.
                // If the block start is an if-condition whose detected merge point equals
                // the convergence point, and both branches can reach it, then it's a normal
                // if-else structure that doesn't need a labeled block.
                // HOWEVER, if there's a skip pattern from a NESTED condition (not the block start itself),
                // we DO need a labeled block because one branch has a shortcut/skip to the convergence point.
                boolean isNormalIfElse = false;
                
                // First check if any skip pattern comes from a nested condition (not blockStart)
                boolean hasNestedSkip = false;
                for (SkipPattern pattern : patterns) {
                    if (!pattern.condNode.equals(blockStart)) {
                        hasNestedSkip = true;
                        break;
                    }
                }
                
                // Only consider it a normal if-else if there are no nested skips
                if (!hasNestedSkip) {
                    for (IfStructure ifStruct : ifs) {
                        if (ifStruct.conditionNode.equals(blockStart)) {
                            // Found the if for the block start
                            Node trueBranch = ifStruct.trueBranch;
                            Node falseBranch = ifStruct.falseBranch;
                            Node mergeNode = ifStruct.mergeNode;
                            
                            // If the if's merge point equals the convergence point,
                            // check if both branches can reach it
                            if (mergeNode != null && mergeNode.equals(convergencePoint)) {
                                // Get the loop containing this if
                                LoopStructure containingLoop = null;
                                for (LoopStructure loop : mainLoops.values()) {
                                    if (loop.body.contains(blockStart)) {
                                        containingLoop = loop;
                                        break;
                                    }
                                }
                                
                                if (containingLoop != null) {
                                    boolean trueReaches = canReachNodeWithin(trueBranch, convergencePoint, containingLoop.body);
                                    boolean falseReaches = canReachNodeWithin(falseBranch, convergencePoint, containingLoop.body);
                                    
                                    if (trueReaches && falseReaches) {
                                        // Both branches naturally converge at the merge point
                                        // This is a normal if-else, not a skip pattern
                                        isNormalIfElse = true;
                                    }
                                }
                            }
                            break;
                        }
                    }
                }
                
                if (isNormalIfElse) {
                    continue; // Skip creating block for normal if-else
                }
                
                String oldLabel = blockStart.getLabel() + "_block";
                String label = getBlockLabel(oldLabel);
                int labelId = getBlockLabelId(oldLabel);
                
                // Check if this block already exists
                boolean exists = false;
                for (LabeledBlockStructure block : labeledBlocks) {
                    if (block.startNode.equals(blockStart) && block.endNode.equals(convergencePoint)) {
                        exists = true;
                        break;
                    }
                }
                
                if (!exists) {
                    addLabeledBlock(label, labelId, blockStart, convergencePoint, mainLoops);
                }
            }
        }
        
        // Detect labeled blocks for inner-loop-to-outer-convergence patterns:
        // When a break from an inner loop leads to the same point as the normal loop exit path,
        // we need a block around the inner loop that ends at the convergence point.
        detectInnerLoopSkipBlocks(loops, mainLoops, ifs);
    }
    
    /**
     * Detects labeled blocks for patterns where breaking out of an inner loop
     * leads to the same convergence point as the normal loop exit path.
     * 
     * Example: when loop_b_cond=false goes to trace_hello->my_cont,
     * and a break from inside the loop goes to trace_Y->my_cont,
     * we need a block around the inner loop + trace_hello that ends at my_cont.
     */
    private void detectInnerLoopSkipBlocks(List<LoopStructure> loops, Map<Node, LoopStructure> mainLoops, List<IfStructure> ifs) {
        for (LoopStructure outerLoop : mainLoops.values()) {
            for (LoopStructure innerLoop : loops) {
                // Check if innerLoop is nested in outerLoop
                if (!outerLoop.body.contains(innerLoop.header)) continue;
                if (innerLoop.equals(outerLoop)) continue;
                
                // Find the normal exit path from inner loop (loop condition false branch)
                IfStructure loopCondIf = null;
                for (IfStructure ifStruct : ifs) {
                    if (ifStruct.conditionNode.equals(innerLoop.header)) {
                        loopCondIf = ifStruct;
                        break;
                    }
                }
                if (loopCondIf == null) continue;
                
                // The normal exit path is the false branch of the loop condition
                Node normalExitStart = loopCondIf.falseBranch;
                if (normalExitStart == null || !outerLoop.body.contains(normalExitStart)) continue;
                
                // Find the convergence point - where the normal exit path leads to
                // This is typically the first successor of the normal exit start
                // that could also be reached by skipping the normal exit path
                Node convergencePoint = null;
                if (normalExitStart.succs.size() == 1) {
                    convergencePoint = normalExitStart.succs.get(0);
                } else if (normalExitStart.succs.size() > 1) {
                    // normalExitStart is a condition, use its merge point
                    for (IfStructure ifStruct : ifs) {
                        if (ifStruct.conditionNode.equals(normalExitStart)) {
                            convergencePoint = ifStruct.mergeNode;
                            break;
                        }
                    }
                }
                if (convergencePoint == null || !outerLoop.body.contains(convergencePoint)) continue;
                
                // Check if any break from inside the inner loop leads directly to convergencePoint
                boolean hasSkipPattern = false;
                for (BreakEdge breakEdge : innerLoop.breaks) {
                    Node breakTarget = breakEdge.to;
                    // Follow the path from breakTarget
                    Node current = breakTarget;
                    Set<Node> visited = new HashSet<>();
                    while (current != null && outerLoop.body.contains(current) && !visited.contains(current)) {
                        visited.add(current);
                        if (current.equals(convergencePoint)) {
                            hasSkipPattern = true;
                            break;
                        }
                        // Don't follow through normalExitStart (that's the normal path)
                        if (current.equals(normalExitStart)) break;
                        // Follow single-successor chains
                        if (current.succs.size() == 1) {
                            current = current.succs.get(0);
                        } else {
                            break;
                        }
                    }
                    if (hasSkipPattern) break;
                }
                
                if (hasSkipPattern) {
                    // Create a block from inner loop header to convergence point
                    String oldLabel = innerLoop.header.getLabel() + "_block";
                    String label = getBlockLabel(oldLabel);
                    int labelId = getBlockLabelId(oldLabel);
                    
                    // Check if this block already exists
                    boolean exists = false;
                    for (LabeledBlockStructure block : labeledBlocks) {
                        if (block.startNode.equals(innerLoop.header) && block.endNode.equals(convergencePoint)) {
                            exists = true;
                            break;
                        }
                    }
                    
                    if (!exists) {
                        addLabeledBlock(label, labelId, innerLoop.header, convergencePoint, mainLoops);
                    }
                }
            }
        }
    }
    
    // Helper class for skip patterns
    private static class SkipPattern {
        Node condNode;
        Node mergeNode;
        LoopStructure loop;
        
        SkipPattern(Node condNode, Node mergeNode, LoopStructure loop) {
            this.condNode = condNode;
            this.mergeNode = mergeNode;
            this.loop = loop;
        }
    }
    
    /**
     * Finds the earliest condition node that should start a skip block,
     * ensuring we don't create nested blocks.
     */
    private Node findEarliestSkipBlockStart(List<SkipPattern> patterns, List<IfStructure> ifs) {
        // Find the condition that is NOT reachable from any other condition in patterns
        Node earliest = null;
        for (SkipPattern pattern : patterns) {
            Node cond = pattern.condNode;
            
            // Check if this condition is reachable from any other condition
            boolean reachableFromOther = false;
            for (SkipPattern other : patterns) {
                if (other == pattern) continue;
                if (other.condNode.equals(cond)) continue; // Skip duplicate patterns
                if (isReachableWithinLoop(other.condNode, cond, pattern.loop)) {
                    reachableFromOther = true;
                    break;
                }
            }
            
            if (!reachableFromOther) {
                // This condition is not reachable from others, so it's a candidate for earliest
                // But we also need to check if there's a parent condition that leads to this
                Node parent = findParentConditionForSkip(cond, pattern.mergeNode, pattern.loop, ifs);
                if (parent != null) {
                    // Use the parent as the earliest if it's not already in patterns
                    boolean parentInPatterns = false;
                    for (SkipPattern p : patterns) {
                        if (p.condNode.equals(parent)) {
                            parentInPatterns = true;
                            break;
                        }
                    }
                    if (!parentInPatterns) {
                        if (earliest == null || isReachableWithinLoop(parent, earliest, pattern.loop)) {
                            earliest = parent;
                        }
                        // Don't override parent with cond - the parent condition encompasses
                        // a broader scope and should remain as the block start since the skip
                        // pattern originates from within the parent's branches
                        continue;
                    }
                }
                
                if (earliest == null || isReachableWithinLoop(cond, earliest, pattern.loop)) {
                    earliest = cond;
                }
            }
        }
        
        return earliest;
    }
    
    /**
     * Finds a parent condition node that leads to this condition and also has paths to mergeNode.
     */
    private Node findParentConditionForSkip(Node cond, Node mergeNode, LoopStructure loop, List<IfStructure> ifs) {
        // Check each predecessor of cond
        for (Node pred : cond.preds) {
            if (!loop.body.contains(pred)) continue;
            // Don't use the merge node itself as a parent - it would cause the block
            // start to equal the convergence point, preventing block creation
            if (pred.equals(mergeNode)) continue;
            
            // Check if pred is a condition node
            for (IfStructure ifStruct : ifs) {
                if (ifStruct.conditionNode.equals(pred)) {
                    // Check if both branches eventually lead to paths that reach mergeNode or cond
                    if (isReachableWithinLoop(ifStruct.trueBranch, cond, loop) ||
                        isReachableWithinLoop(ifStruct.falseBranch, cond, loop)) {
                        // This is a potential parent
                        // If the parent is the loop header, use it as the block start without
                        // searching for a grandparent. The loop header represents the natural
                        // boundary for skip block detection within loop structures, and searching
                        // beyond it would incorrectly extend the block scope outside the loop body.
                        if (pred.equals(loop.header)) {
                            return pred;
                        }
                        // Recursively check for even earlier parent
                        Node grandparent = findParentConditionForSkip(pred, mergeNode, loop, ifs);
                        return grandparent != null ? grandparent : pred;
                    }
                }
            }
        }
        return null;
    }
    
    /**
     * Finds a node in the branch that directly jumps to the merge point within a loop.
     */
    private Node findDirectJumpToMergeInLoop(Node branchStart, Node mergeNode, LoopStructure loop) {
        Set<Node> visited = new HashSet<>();
        Queue<Node> queue = new LinkedList<>();
        queue.add(branchStart);
        
        while (!queue.isEmpty()) {
            Node current = queue.poll();
            if (visited.contains(current)) continue;
            visited.add(current);
            
            // Don't go outside the loop
            if (!loop.body.contains(current)) continue;
            // Found: current has a direct edge to merge
            for (Node succ : current.succs) {
                if (succ.equals(mergeNode)) {
                    return current;
                }
            }
            
            // Don't follow through conditional nodes (they create their own paths)
            if (current.succs.size() <= 1) {
                for (Node succ : current.succs) {
                    if (loop.body.contains(succ) && !succ.equals(mergeNode)) {
                        queue.add(succ);
                    }
                }
            }
        }
        return null;
    }
    
    /**
     * Finds the first convergence point where both branches of a condition meet.
     * This is the earliest node that both branches eventually reach.
     */
    private Node findFirstConvergence(Node trueBranch, Node falseBranch, LoopStructure loop) {
        // BFS from both branches simultaneously to find first common node
        Set<Node> trueReachable = new HashSet<>();
        Set<Node> falseReachable = new HashSet<>();
        Queue<Node> trueQueue = new LinkedList<>();
        Queue<Node> falseQueue = new LinkedList<>();
        
        trueQueue.add(trueBranch);
        falseQueue.add(falseBranch);
        
        while (!trueQueue.isEmpty() || !falseQueue.isEmpty()) {
            // Expand from true branch
            if (!trueQueue.isEmpty()) {
                Node current = trueQueue.poll();
                if (loop.body.contains(current) && !trueReachable.contains(current)) {
                    if (falseReachable.contains(current)) {
                        return current; // Found convergence
                    }
                    trueReachable.add(current);
                    for (Node succ : current.succs) {
                        // Only add if not already visited to avoid redundant work
                        if (loop.body.contains(succ) && !trueReachable.contains(succ)) {
                            trueQueue.add(succ);
                        }
                    }
                }
            }
            
            // Expand from false branch
            if (!falseQueue.isEmpty()) {
                Node current = falseQueue.poll();
                if (loop.body.contains(current) && !falseReachable.contains(current)) {
                    if (trueReachable.contains(current)) {
                        return current; // Found convergence
                    }
                    falseReachable.add(current);
                    for (Node succ : current.succs) {
                        // Only add if not already visited to avoid redundant work
                        if (loop.body.contains(succ) && !falseReachable.contains(succ)) {
                            falseQueue.add(succ);
                        }
                    }
                }
            }
        }
        
        return null;
    }
    
    /**
     * Checks if a node can reach a target node within a set of allowed nodes.
     * 
     * @param start the starting node
     * @param target the target node to reach
     * @param allowedNodes the set of nodes to search within
     * @return true if target is reachable from start within allowedNodes
     */
    private boolean canReachNodeWithin(Node start, Node target, Set<Node> allowedNodes) {
        if (start == null || target == null) {
            return false;
        }
        if (start.equals(target)) {
            return true;
        }
        
        Set<Node> visited = new HashSet<>();
        Queue<Node> queue = new LinkedList<>();
        queue.add(start);
        
        while (!queue.isEmpty()) {
            Node current = queue.poll();
            if (!visited.add(current)) continue; // Already visited
            
            for (Node succ : current.succs) {
                if (succ.equals(target)) {
                    return true;
                }
                if (allowedNodes.contains(succ) && !visited.contains(succ)) {
                    queue.add(succ);
                }
            }
        }
        return false;
    }        

    /**
     * Detects labeled blocks for if-statement skip patterns outside of loops.
     * This handles cases where one branch of an if skips over some code to a common merge point.
     * 
     * Pattern example:
     * if (cond) {
     *   x;
     *   if (cond2) {
     *     y;
     *   } else {
     *     // skip to A1
     *   }
     * } else {
     *   // skip to A1
     * }
     * A1: label_end: { ... }
     */
    private void detectSkipBlocks(List<IfStructure> ifs, List<SwitchStructure> switches) {
        // Build a set of switch start nodes - these blocks will be absorbed by switches
        Set<Node> switchStartNodes = new HashSet<>();
        for (SwitchStructure sw : switches) {
            switchStartNodes.add(sw.startNode);
        }
        
        // Get all nodes that are inside loops
        List<LoopStructure> loops = detectLoops();
        Set<Node> nodesInLoops = new HashSet<>();
        for (LoopStructure loop : loops) {
            nodesInLoops.addAll(loop.body);
        }
        
        // Find all skip edges: edges where one if branch goes directly to a merge point
        // that the other branch reaches through a longer path
        // Group skip edges by their target (the merge point)
        Map<Node, List<SkipInfo>> skipsByTarget = new LinkedHashMap<>();
        
        for (IfStructure ifStruct : ifs) {
            Node cond = ifStruct.conditionNode;
            Node trueBranch = ifStruct.trueBranch;
            Node falseBranch = ifStruct.falseBranch;
            Node mergeNode = ifStruct.mergeNode;
            
            if (trueBranch == null || falseBranch == null) continue;
            if (nodesInLoops.contains(cond)) continue;
            if (mergeNode == null) continue;
            
            // Check if true branch contains a node that skips directly to the merge point
            // while the false branch reaches it through a longer path
            Node skipSource = findDirectJumpToMerge(trueBranch, mergeNode);
            if (skipSource != null) {
                int pathFromFalse = shortestPathLengthTo(falseBranch, mergeNode, new HashSet<>());
                int pathFromSkip = 1; // skipSource -> mergeNode directly
                
                // Only detect as skip if the other branch takes significantly longer
                if (pathFromFalse > pathFromSkip + 1) {
                    skipsByTarget.computeIfAbsent(mergeNode, k -> new ArrayList<>())
                                 .add(new SkipInfo(cond));
                }
            }
            
            // Check if false branch contains a node that skips directly to the merge point
            // while the true branch reaches it through a longer path
            skipSource = findDirectJumpToMerge(falseBranch, mergeNode);
            if (skipSource != null) {
                int pathFromTrue = shortestPathLengthTo(trueBranch, mergeNode, new HashSet<>());
                int pathFromSkip = 1; // skipSource -> mergeNode directly
                
                // Only detect as skip if the other branch takes significantly longer
                if (pathFromTrue > pathFromSkip + 1) {
                    skipsByTarget.computeIfAbsent(mergeNode, k -> new ArrayList<>())
                                 .add(new SkipInfo(cond));
                }
            }
        }
        
        // For each skip target, create a labeled block that encompasses all skip sources
        for (Map.Entry<Node, List<SkipInfo>> entry : skipsByTarget.entrySet()) {
            Node skipTarget = entry.getKey();
            List<SkipInfo> skips = entry.getValue();
            
            if (skips.isEmpty()) continue;
            
            // Find the earliest common dominator of all the condition nodes
            Node blockStart = findEarliestConditionNode(skips);
            
            if (blockStart == null) continue;
            
            // Generate unique label based on block start node to avoid conflicts
            String oldLabel = blockStart.getLabel() + "_block";
            
            // If this block will be absorbed by a switch, don't consume a counter value
            // The switch will use its own label (or no label if not needed)
            String label;
            int labelId;
            if (switchStartNodes.contains(blockStart)) {
                // Use a placeholder label that won't consume the counter
                label = oldLabel;  // Use the original label as placeholder
                labelId = -1;      // No global ID
            } else {
                label = getBlockLabel(oldLabel);
                labelId = getBlockLabelId(oldLabel);
            }
            
            // Check if this block already exists
            boolean exists = false;
            for (LabeledBlockStructure block : labeledBlocks) {
                if (block.startNode.equals(blockStart) && block.endNode.equals(skipTarget)) {
                    exists = true;
                    break;
                }
            }
            
            if (!exists) {
                addLabeledBlock(label, labelId, blockStart, skipTarget);
            }
        }
    }
    
    /**
     * Finds a node in the branch that directly jumps to the merge point.
     * This traverses the branch to find any node that has mergeNode as a direct successor.
     * Returns null if no such node exists.
     */
    private Node findDirectJumpToMerge(Node branch, Node mergeNode) {
        Set<Node> visited = new HashSet<>();
        Queue<Node> queue = new LinkedList<>();
        queue.add(branch);
        
        while (!queue.isEmpty()) {
            Node current = queue.poll();
            if (visited.contains(current)) continue;
            visited.add(current);
            
            // Check if this node has a direct edge to the merge point
            if (current.succs.contains(mergeNode)) {
                return current;
            }
            
            // Continue searching in successors (but not the merge node itself)
            for (Node succ : current.succs) {
                if (!succ.equals(mergeNode) && !visited.contains(succ)) {
                    queue.add(succ);
                }
            }
        }
        
        return null;
    }
    
    /**
     * Helper class to store information about a skip edge.
     */
    private static class SkipInfo {
        final Node conditionNode;
        
        SkipInfo(Node conditionNode) {
            this.conditionNode = conditionNode;
        }
    }
    
    /**
     * Finds the earliest condition node that dominates all skip conditions.
     * This should be the starting point of the labeled block.
     */
    private Node findEarliestConditionNode(List<SkipInfo> skips) {
        if (skips.isEmpty()) return null;
        if (skips.size() == 1) return skips.get(0).conditionNode;
        
        // Find the condition node that is earliest (dominates others)
        // by checking which one is reachable from the others
        Set<Node> conditionNodes = new HashSet<>();
        for (SkipInfo skip : skips) {
            conditionNodes.add(skip.conditionNode);
        }
        
        Node earliest = null;
        for (SkipInfo skip : skips) {
            Node cond = skip.conditionNode;
            boolean isDominator = true;
            
            // Check if all other condition nodes are reachable from this one
            Set<Node> reachable = getReachableNodes(cond);
            for (Node other : conditionNodes) {
                if (!other.equals(cond) && !reachable.contains(other)) {
                    isDominator = false;
                    break;
                }
            }
            
            if (isDominator) {
                earliest = cond;
                break;
            }
        }
        
        return earliest != null ? earliest : skips.get(0).conditionNode;
    }   

    /**
     * Computes the shortest path length from 'from' to 'to'.
     * Returns Integer.MAX_VALUE if no path exists.
     */
    private int shortestPathLengthTo(Node from, Node to, Set<Node> visited) {
        if (from.equals(to)) return 0;
        if (visited.contains(from)) return Integer.MAX_VALUE;
        visited.add(from);
        
        int minLength = Integer.MAX_VALUE;
        for (Node succ : from.succs) {
            int length = shortestPathLengthTo(succ, to, new HashSet<>(visited));
            if (length != Integer.MAX_VALUE && length + 1 < minLength) {
                minLength = length + 1;
            }
        }
        return minLength;
    }

    /**
     * Detects labeled blocks for "return" patterns - nodes with no successors
     * that are reachable from within loops but are not the normal exit node.
     * These nodes represent early exits (like return statements) and need
     * a top-level labeled block to properly break out of all enclosing structures.
     * 
     * @param loops the detected loops
     * @param ifs the detected if structures
     * @return the return block if one was created, null otherwise
     */
    private LabeledBlockStructure detectReturnBlocks(List<LoopStructure> loops, List<IfStructure> ifs) {
        // Find the normal exit node (typically the last node reachable from all paths)
        // This is usually the node that the main loop condition branches to on false
        Node exitNode = null;
        
        // Find nodes with no successors
        List<Node> terminalNodes = new ArrayList<>();
        for (Node node : allNodes) {
            if (node.succs.isEmpty()) {
                terminalNodes.add(node);
            }
        }
        
        if (terminalNodes.isEmpty()) {
            return null;
        }
        
        // If there's only one terminal node, it's the normal exit - no return block needed
        if (terminalNodes.size() == 1) {
            return null;
        }
        
        // Find the "normal" exit node - the one that's reachable from the outermost loop's
        // false branch (i.e., the natural exit when loop condition is false)
        for (LoopStructure loop : loops) {
            // Find the outermost loop (not contained in any other loop)
            boolean isOutermost = true;
            for (LoopStructure other : loops) {
                if (other != loop && other.body.contains(loop.header)) {
                    isOutermost = false;
                    break;
                }
            }
            
            if (isOutermost) {
                // Find the if structure for this loop's header
                for (IfStructure ifStruct : ifs) {
                    if (ifStruct.conditionNode.equals(loop.header)) {
                        // Find which branch is the exit (the one NOT in the loop body)
                        Node exitBranch = null;
                        if (!loop.body.contains(ifStruct.trueBranch)) {
                            exitBranch = ifStruct.trueBranch;
                        } else if (!loop.body.contains(ifStruct.falseBranch)) {
                            exitBranch = ifStruct.falseBranch;
                        }
                        
                        if (exitBranch != null) {
                            if (terminalNodes.contains(exitBranch)) {
                                exitNode = exitBranch;
                            } else {
                                // Follow the path to find exit
                                Node current = exitBranch;
                                Set<Node> visited = new HashSet<>();
                                while (current != null && !visited.contains(current)) {
                                    visited.add(current);
                                    if (terminalNodes.contains(current)) {
                                        exitNode = current;
                                        break;
                                    }
                                    if (current.succs.size() == 1) {
                                        current = current.succs.get(0);
                                    } else {
                                        break;
                                    }
                                }
                            }
                        }
                        break;
                    }
                }
                break;
            }
        }
        
        // Find "return" nodes - terminal nodes that are NOT the normal exit
        // and are reachable from within loops
        List<Node> returnNodes = new ArrayList<>();
        for (Node terminal : terminalNodes) {
            if (!terminal.equals(exitNode)) {
                // Check if this terminal is reachable from within any loop
                for (LoopStructure loop : loops) {
                    for (Node loopNode : loop.body) {
                        if (loopNode.succs.contains(terminal)) {
                            returnNodes.add(terminal);
                            break;
                        }
                    }
                    if (returnNodes.contains(terminal)) break;
                }
            }
        }
        
        if (returnNodes.isEmpty()) {
            return null;
        }
        
        // We need a top-level labeled block
        // Find the start node - the outermost loop header
        // This ensures nodes before the loop are outside the block
        Node blockStart = null;
        for (LoopStructure loop : loops) {
            // Find the outermost loop (not contained in any other loop)
            boolean isOutermost = true;
            for (LoopStructure other : loops) {
                if (other != loop && other.body.contains(loop.header)) {
                    isOutermost = false;
                    break;
                }
            }
            
            if (isOutermost) {
                blockStart = loop.header;
                break;
            }
        }
        
        // Fallback to first successor of entry if no loop found
        if (blockStart == null) {
            for (Node succ : entryNode.succs) {
                blockStart = succ;
                break;
            }
        }
        
        if (blockStart == null || exitNode == null) {
            return null;
        }
        
        // Create the return block using the global label counter
        String oldLabel = "return_block";
        String label = getBlockLabel(oldLabel);
        int labelId = getBlockLabelId(oldLabel);
        
        // Check if this block already exists
        for (LabeledBlockStructure block : labeledBlocks) {
            if (block.startNode.equals(blockStart) && block.endNode.equals(exitNode)) {
                return block;
            }
        }
        
        // Create labeled block body - all nodes except entry
        Set<Node> body = new HashSet<>(allNodes);
        body.remove(entryNode);
        
        LabeledBlockStructure returnBlock = new LabeledBlockStructure(label, labelId, blockStart, exitNode, body);
        
        // Add break edges for each return node
        // The 'from' field is the return node itself (the node that initiates the break)
        for (Node returnNode : returnNodes) {
            returnBlock.breaks.add(new LabeledBreakEdge(returnNode, exitNode, label, labelId));
        }
        
        labeledBlocks.add(returnBlock);
        
        // Store reference for special handling
        detectedReturnBlock = returnBlock;
        
        return returnBlock;
    }

    /**
     * Generates a Graphviz/DOT representation of the CFG.
     * 
     * @return DOT format string representing the CFG
     */
    public String toGraphviz() {
        StringBuilder sb = new StringBuilder();
        sb.append("digraph {\n");
        
        for (Node node : allNodes) {
            for (Node succ : node.succs) {
                sb.append("  ").append(node.getLabel()).append("->").append(succ.getLabel()).append(";\n");
            }
        }
        
        sb.append("}");
        return sb.toString();
    }

    /**
     * Generates a pseudocode representation of the detected structures.
     * Outputs the control flow as structured pseudocode with if/else blocks, loops, and labeled blocks.
     * 
     * @return pseudocode string representing the detected structures
     */
    public String toPseudocode() {
        List<Statement> statements = toStatementList();
        StringBuilder sb = new StringBuilder();
        for (Statement stmt : statements) {
            sb.append(stmt.toString(""));
        }
        return sb.toString();
    }
    
    /**
     * Generates a list of Statement objects representing the pseudocode.
     * This is the structured representation that can be used for further processing.
     * 
     * @return list of statements representing the detected structures
     */
    public List<Statement> toStatementList() {
        // Reset label counters for consistent labeling
        resetLabelCounters();
        
        List<Statement> result = new ArrayList<>();
        Set<Node> visited = new HashSet<>();
        List<LoopStructure> loops = detectLoops();
        List<IfStructure> ifs = detectIfs();
        
        // Automatically detect switch structures
        switchStructures.clear();
        switchStructures.addAll(detectSwitches(ifs));
        
        // Detect return block FIRST so it gets block_0 if needed
        // This ensures the outermost block gets the lowest number
        LabeledBlockStructure returnBlock = detectReturnBlocks(loops, ifs);
        
        // Detect blocks and pre-assign loop labels in correct order
        detectBlocksAndPreAssignLoopLabels(loops, ifs, switchStructures);
        
        // Create lookup maps for quick access
        // Merge loop bodies when there are multiple back-edges to the same header
        Map<Node, LoopStructure> loopHeaders = new HashMap<>();
        for (LoopStructure loop : loops) {
            LoopStructure existing = loopHeaders.get(loop.header);
            if (existing == null) {
                loopHeaders.put(loop.header, loop);
            } else {
                // Merge loop bodies when there are multiple back-edges to the same header
                Set<Node> mergedBody = new HashSet<>(existing.body);
                mergedBody.addAll(loop.body);
                
                // Keep the back-edge source from the larger body (primary path)
                Node backEdgeSrc = existing.body.size() >= loop.body.size() ? 
                                   existing.backEdgeSource : loop.backEdgeSource;
                
                // Merge breaks from both loop structures, but only if target is OUTSIDE merged body
                List<BreakEdge> mergedBreaks = new ArrayList<>();
                for (BreakEdge brk : existing.breaks) {
                    if (!mergedBody.contains(brk.to)) {
                        mergedBreaks.add(brk);
                    }
                }
                for (BreakEdge brk : loop.breaks) {
                    if (mergedBody.contains(brk.to)) {
                        continue; // Skip breaks where target is now inside merged body
                    }
                    boolean alreadyExists = false;
                    for (BreakEdge existingBrk : mergedBreaks) {
                        if (existingBrk.from.equals(brk.from) && existingBrk.to.equals(brk.to)) {
                            alreadyExists = true;
                            break;
                        }
                    }
                    if (!alreadyExists) {
                        mergedBreaks.add(brk);
                    }
                }
                
                // Merge continues from both loop structures
                List<ContinueEdge> mergedContinues = new ArrayList<>(existing.continues);
                for (ContinueEdge cont : loop.continues) {
                    boolean alreadyExists = false;
                    for (ContinueEdge existingCont : mergedContinues) {
                        if (existingCont.from.equals(cont.from) && existingCont.to.equals(cont.to)) {
                            alreadyExists = true;
                            break;
                        }
                    }
                    if (!alreadyExists) {
                        mergedContinues.add(cont);
                    }
                }
                
                LoopStructure merged = new LoopStructure(existing.header, mergedBody, backEdgeSrc);
                merged.breaks.clear();
                merged.breaks.addAll(mergedBreaks);
                merged.continues.clear();
                merged.continues.addAll(mergedContinues);
                loopHeaders.put(loop.header, merged);
            }
        }
        
        Map<Node, IfStructure> ifConditions = new HashMap<>();
        for (IfStructure ifStruct : ifs) {
            ifConditions.put(ifStruct.conditionNode, ifStruct);
        }
        
        // Create lookup map for switch structures (by start node)
        Map<Node, SwitchStructure> switchStarts = new HashMap<>();
        for (SwitchStructure sw : switchStructures) {
            switchStarts.put(sw.startNode, sw);
        }
        
        // Create lookup maps for labeled blocks (exclude return block from starts to not interfere with loops)
        Map<Node, LabeledBlockStructure> blockStarts = new HashMap<>();
        for (LabeledBlockStructure block : labeledBlocks) {
            // Don't add return block to blockStarts - it's handled specially at top level
            if (returnBlock != null && block == returnBlock) {
                continue;
            }
            blockStarts.put(block.startNode, block);
        }
        
        // Create lookup for labeled break edges
        Map<Node, LabeledBreakEdge> labeledBreakEdges = new HashMap<>();
        for (LabeledBlockStructure block : labeledBlocks) {
            for (LabeledBreakEdge breakEdge : block.breaks) {
                labeledBreakEdges.put(breakEdge.from, breakEdge);
            }
        }
        
        // Track loops that need labels (targeted by breaks from inside labeled blocks)
        // A loop needs a label only when an unlabeled break inside a labeled block would need
        // to exit the loop (not the labeled block). This is detected in outputPathAndBreak.
        Set<Node> loopsNeedingLabels = new HashSet<>();
        for (LoopStructure loop : loops) {
            // Check if there's any labeled block inside this loop with breaks
            // that could potentially target this loop
            for (LabeledBlockStructure block : labeledBlocks) {
                // Skip the return block since it encompasses the entire program
                if (block == returnBlock) {
                    continue;
                }
                if (loop.body.contains(block.startNode) && !block.breaks.isEmpty()) {
                    // This labeled block is inside the loop AND has breaks
                    // Check if any break targets the loop's exit (outside the loop body)
                    for (LabeledBreakEdge breakEdge : block.breaks) {
                        if (!loop.body.contains(breakEdge.to)) {
                            // This break exits the loop, so the loop needs a label
                            loopsNeedingLabels.add(loop.header);
                            break;
                        }
                    }
                }
            }
            
            // Also check if there are loop breaks from inside a labeled block
            // In this case, the IF condition that breaks needs to use the loop label
            for (BreakEdge breakEdge : loop.breaks) {
                // Skip if the break is from the loop header itself - that's the natural loop exit
                // and doesn't need a labeled break (just unlabeled break works)
                if (breakEdge.from.equals(loop.header)) {
                    continue;
                }
                // Check if this break originates from inside a labeled block
                for (LabeledBlockStructure block : labeledBlocks) {
                    // Skip the return block since it encompasses the entire program
                    if (block == returnBlock) {
                        continue;
                    }
                    if (block.body.contains(breakEdge.from)) {
                        // The break is from inside a labeled block, so loop needs a label
                        loopsNeedingLabels.add(loop.header);
                        break;
                    }
                }
            }
        }
        
        // If there's a return block, wrap output in it
        if (returnBlock != null) {
            // Output entry node first
            result.add(new ExpressionStatement(entryNode));
            visited.add(entryNode);
            
            // Output nodes between entry and block start (outside the block)
            // These are nodes that come before the loop but after entry
            Node current = null;
            for (Node succ : entryNode.succs) {
                current = succ;
                break;
            }
            
            // Walk through single-successor nodes until we reach the block start
            // The visited check prevents infinite loops if there's a cycle
            while (current != null && !current.equals(returnBlock.startNode)) {
                if (visited.contains(current)) {
                    break; // Already visited - prevent cycle
                }
                if (current.succs.size() == 1) {
                    visited.add(current);
                    result.add(new ExpressionStatement(current));
                    current = current.succs.get(0);
                } else {
                    // Multi-successor node - stop here
                    break;
                }
            }
            
            // Generate the code inside the block starting from block start
            List<Statement> blockBody = new ArrayList<>();
            if (!visited.contains(returnBlock.startNode)) {
                blockBody.addAll(generateStatements(returnBlock.startNode, visited, loopHeaders, ifConditions, blockStarts, labeledBreakEdges, loopsNeedingLabels, null, returnBlock, null, switchStarts));
            }
            
            // Add return block
            result.add(new BlockStatement(returnBlock.label, returnBlock.labelId, blockBody));
        } else {
            result.addAll(generateStatements(entryNode, visited, loopHeaders, ifConditions, blockStarts, labeledBreakEdges, loopsNeedingLabels, null, null, null, switchStarts));
        }
        
        // Renumber labels to ensure they appear in sequential order (0, 1, 2, ...) 
        // based on their order of appearance in the output
        return renumberLabels(result);
    }
    
    /**
     * Renumbers labels in statements to ensure they appear in sequential order (0, 1, 2, ...)
     * based on their order of appearance in the output (top to bottom, depth-first).
     * This ensures consistent label naming regardless of detection order.
     * 
     * @param statements the statements to renumber
     * @return the renumbered statements
     */
    private List<Statement> renumberLabels(List<Statement> statements) {
        // Build a mapping from (structure type + old ID) -> new sequential ID
        // This handles cases where different structure types might have the same old ID
        Map<String, Integer> labelMapping = new LinkedHashMap<>();
        int[] nextId = {0};
        
        // First pass: collect all label definitions in order of appearance
        collectLabelDefinitions(statements, labelMapping, nextId);
        
        // Second pass: rewrite statements with new label IDs
        return rewriteStatementsWithMapping(statements, labelMapping, nextId);
    }
    
    /**
     * Collects label definitions from statements in order of appearance.
     * Each definition gets assigned a new sequential ID.
     */
    private void collectLabelDefinitions(List<Statement> statements, Map<String, Integer> labelMapping, int[] nextId) {
        for (Statement stmt : statements) {
            collectLabelDefinitionFromStatement(stmt, labelMapping, nextId);
        }
    }
    
    /**
     * Collects label definition from a single statement and its children.
     * Uses a unique key combining statement identity (via System.identityHashCode) to handle 
     * cases where multiple structures have the same old ID.
     */
    private void collectLabelDefinitionFromStatement(Statement stmt, Map<String, Integer> labelMapping, int[] nextId) {
        if (stmt instanceof BlockStatement) {
            BlockStatement block = (BlockStatement) stmt;
            // Use identity hash to make key unique per statement instance
            String key = "block_" + System.identityHashCode(block);
            labelMapping.put(key, nextId[0]++);
            collectLabelDefinitions(block.getBody(), labelMapping, nextId);
        } else if (stmt instanceof LoopStatement) {
            LoopStatement loop = (LoopStatement) stmt;
            String key = "loop_" + System.identityHashCode(loop);
            labelMapping.put(key, nextId[0]++);
            collectLabelDefinitions(loop.getBody(), labelMapping, nextId);
        } else if (stmt instanceof SwitchStatement) {
            SwitchStatement sw = (SwitchStatement) stmt;
            String key = "switch_" + System.identityHashCode(sw);
            labelMapping.put(key, nextId[0]++);
            for (SwitchStatement.Case c : sw.getCases()) {
                collectLabelDefinitions(c.getBody(), labelMapping, nextId);
            }
        } else if (stmt instanceof IfStatement) {
            IfStatement ifStmt = (IfStatement) stmt;
            collectLabelDefinitions(ifStmt.getOnTrue(), labelMapping, nextId);
            collectLabelDefinitions(ifStmt.getOnFalse(), labelMapping, nextId);
        } else if (stmt instanceof TryStatement) {
            TryStatement tryStmt = (TryStatement) stmt;
            collectLabelDefinitions(tryStmt.getTryBody(), labelMapping, nextId);
            for (TryStatement.CatchBlock catchBlock : tryStmt.getCatchBlocks()) {
                collectLabelDefinitions(catchBlock.getBody(), labelMapping, nextId);
            }
        }
    }
    
    /**
     * Rewrites statements with new label IDs based on the mapping.
     * Also builds a secondary mapping from old label IDs to new IDs for break/continue references.
     */
    private List<Statement> rewriteStatementsWithMapping(List<Statement> statements, Map<String, Integer> labelMapping, int[] nextId) {
        // Build a mapping from old label ID to new label ID for this traversal
        // This will be updated as we encounter each labeled structure
        Map<Integer, Integer> oldToNewId = new HashMap<>();
        return rewriteStatementsImpl(statements, labelMapping, oldToNewId);
    }
    
    /**
     * Recursively rewrites statements with new label IDs.
     */
    private List<Statement> rewriteStatementsImpl(List<Statement> statements, Map<String, Integer> labelMapping, Map<Integer, Integer> oldToNewId) {
        List<Statement> result = new ArrayList<>();
        for (Statement stmt : statements) {
            result.add(rewriteStatementImpl(stmt, labelMapping, oldToNewId));
        }
        return result;
    }
    
    /**
     * Rewrites a single statement with new label IDs.
     */
    private Statement rewriteStatementImpl(Statement stmt, Map<String, Integer> labelMapping, Map<Integer, Integer> oldToNewId) {
        if (stmt instanceof BlockStatement) {
            BlockStatement block = (BlockStatement) stmt;
            String key = "block_" + System.identityHashCode(block);
            int newId = labelMapping.getOrDefault(key, block.getLabelId());
            // Record the mapping from old ID to new ID (only if not already mapped)
            if (!oldToNewId.containsKey(block.getLabelId())) {
                oldToNewId.put(block.getLabelId(), newId);
            }
            String newLabel = "block_" + newId;
            return new BlockStatement(newLabel, newId, rewriteStatementsImpl(block.getBody(), labelMapping, oldToNewId));
        } else if (stmt instanceof LoopStatement) {
            LoopStatement loop = (LoopStatement) stmt;
            String key = "loop_" + System.identityHashCode(loop);
            int newId = labelMapping.getOrDefault(key, loop.getLabelId());
            // Record the mapping from old ID to new ID (only if not already mapped)
            if (!oldToNewId.containsKey(loop.getLabelId())) {
                oldToNewId.put(loop.getLabelId(), newId);
            }
            String newLabel = loop.hasLabel() ? "loop_" + newId : null;
            return new LoopStatement(newLabel, newId, rewriteStatementsImpl(loop.getBody(), labelMapping, oldToNewId));
        } else if (stmt instanceof SwitchStatement) {
            SwitchStatement sw = (SwitchStatement) stmt;
            String key = "switch_" + System.identityHashCode(sw);
            int newId = labelMapping.getOrDefault(key, sw.getLabelId());
            // Record the mapping from old ID to new ID (only if not already mapped)
            if (!oldToNewId.containsKey(sw.getLabelId())) {
                oldToNewId.put(sw.getLabelId(), newId);
            }
            String newLabel = sw.hasLabel() ? "loop_" + newId : null;
            List<SwitchStatement.Case> newCases = new ArrayList<>();
            for (SwitchStatement.Case c : sw.getCases()) {
                if (c.isDefault()) {
                    newCases.add(new SwitchStatement.Case(rewriteStatementsImpl(c.getBody(), labelMapping, oldToNewId)));
                } else {
                    newCases.add(new SwitchStatement.Case(c.getCondition(), c.getConditionNode(), c.isNegated(), rewriteStatementsImpl(c.getBody(), labelMapping, oldToNewId)));
                }
            }
            return new SwitchStatement(newCases, newLabel, newId);
        } else if (stmt instanceof IfStatement) {
            IfStatement ifStmt = (IfStatement) stmt;
            return new IfStatement(ifStmt.getConditionNode(), ifStmt.isNegated(), 
                    rewriteStatementsImpl(ifStmt.getOnTrue(), labelMapping, oldToNewId), 
                    rewriteStatementsImpl(ifStmt.getOnFalse(), labelMapping, oldToNewId));
        } else if (stmt instanceof TryStatement) {
            TryStatement tryStmt = (TryStatement) stmt;
            List<TryStatement.CatchBlock> newCatches = new ArrayList<>();
            for (TryStatement.CatchBlock catchBlock : tryStmt.getCatchBlocks()) {
                newCatches.add(new TryStatement.CatchBlock(catchBlock.getExceptionIndex(), 
                        rewriteStatementsImpl(catchBlock.getBody(), labelMapping, oldToNewId)));
            }
            return TryStatement.withMultipleCatch(rewriteStatementsImpl(tryStmt.getTryBody(), labelMapping, oldToNewId), newCatches);
        } else if (stmt instanceof BreakStatement) {
            BreakStatement brk = (BreakStatement) stmt;
            int oldId = brk.getLabelId();
            int newId = oldToNewId.getOrDefault(oldId, oldId);
            if (brk.hasLabel()) {
                // Determine if it's a block or loop label based on the original label
                String oldLabel = brk.getLabel();
                String newLabel;
                if (oldLabel.startsWith("block_")) {
                    newLabel = "block_" + newId;
                } else {
                    newLabel = "loop_" + newId;
                }
                return new BreakStatement(newLabel, newId);
            } else {
                return new BreakStatement(newId);
            }
        } else if (stmt instanceof ContinueStatement) {
            ContinueStatement cont = (ContinueStatement) stmt;
            int oldId = cont.getLabelId();
            int newId = oldToNewId.getOrDefault(oldId, oldId);
            if (cont.hasLabel()) {
                String newLabel = "loop_" + newId;
                return new ContinueStatement(newLabel, newId);
            } else {
                return new ContinueStatement(newId);
            }
        } else {
            // ExpressionStatement and others don't have labels
            return stmt;
        }
    }



    // Overload without stop nodes for backward compatibility
    private List<Node> findPathToTarget(Node start, Node target, Map<Node, IfStructure> ifConditions) {
        return findPathToTarget(start, target, ifConditions, null, null);
    }
    
    /**
     * Finds the path of intermediate nodes from start to target, only including nodes inside the loop.
     * When a break occurs, only nodes INSIDE the loop should be output before the break.
     * Nodes outside the loop will be output after the loop ends.
     * 
     * @param loopBody if non-null, only include nodes that are in this set (used for breaks inside loops)
     */
    private List<Node> findPathToTargetInLoop(Node start, Node target, Map<Node, IfStructure> ifConditions, Set<Node> loopBody) {
        return findPathToTarget(start, target, ifConditions, null, loopBody);
    }
    
    /**
     * Finds the path of intermediate nodes from start to target.
     * Returns a list of intermediate nodes that should be output before the break statement.
     * Stops at the target node (not including it in the path).
     * 
     * @param stopNodes if non-null, stop when encountering a node in this set (exclusive stop)
     * @param includedNodes if non-null, only include nodes that are in this set (used for loop body checks)
     */
    private List<Node> findPathToTarget(Node start, Node target, Map<Node, IfStructure> ifConditions, Set<Node> stopNodes, Set<Node> includedNodes) {
        List<Node> path = new ArrayList<>();
        Node current = start;
        Set<Node> visited = new HashSet<>();
        
        while (current != null && !visited.contains(current)) {
            visited.add(current);
            
            // If we've reached the target, stop (don't include target in path)
            if (current.equals(target)) {
                break;
            }
            
            // If this is a stop node, stop (don't include it)
            if (stopNodes != null && stopNodes.contains(current)) {
                break;
            }
            
            // If includedNodes is specified and this node is not in it, stop
            // This is used for loop body checks - nodes outside the loop should not be included
            if (includedNodes != null && !includedNodes.contains(current)) {
                break;
            }
            
            // If this is a conditional node, stop - we don't follow through conditionals
            if (ifConditions.containsKey(current)) {
                break;
            }
            
            // Add this node to the path (it's an intermediate statement)
            path.add(current);
            
            // Follow the single successor (non-conditional nodes should have 1 successor)
            if (current.succs.size() == 1) {
                current = current.succs.get(0);
            } else if (current.succs.isEmpty()) {
                // End of path (e.g., exit node)
                break;
            } else {
                // Multiple successors - shouldn't happen for non-conditional nodes, but stop here
                break;
            }
        }
        
        return path;
    }


    /**
     * Finds the appropriate break label for a break to a target outside the current loop.
     * Returns the loop header label with _loop suffix if breaking to an outer loop, empty string for current loop break.
     */
    private String findBreakLabel(Node breakTarget, Map<Node, LoopStructure> loopHeaders, LoopStructure currentLoop) {
        Node loopNode = findBreakLabelLoop(breakTarget, loopHeaders, currentLoop);
        return loopNode != null ? getLoopLabel(loopNode) : "";
    }
    
    private int findBreakLabelId(Node breakTarget, Map<Node, LoopStructure> loopHeaders, LoopStructure currentLoop) {
        Node loopNode = findBreakLabelLoop(breakTarget, loopHeaders, currentLoop);
        return loopNode != null ? getLoopLabelId(loopNode) : -1;
    }
    
    private Node findBreakLabelLoop(Node breakTarget, Map<Node, LoopStructure> loopHeaders, LoopStructure currentLoop) {
        // First, check if the target is inside an outer loop but outside current loop
        // In this case, we need a labeled break for the CURRENT loop
        for (LoopStructure loop : loopHeaders.values()) {
            if (loop == currentLoop) continue;
            
            // If the outer loop contains both the current loop AND the break target,
            // but the current loop doesn't contain the target,
            // then we're breaking out of the current loop to stay in the outer loop
            // This requires a labeled break to the current loop
            if (loop.body.contains(currentLoop.header) && loop.body.contains(breakTarget)) {
                // Target is inside an outer loop - need to label break to current loop
                return currentLoop.header;
            }
            
            // If this outer loop contains the current loop and the target is outside this outer loop
            if (loop.body.contains(currentLoop.header) && !loop.body.contains(breakTarget)) {
                // Breaking out of the outer loop entirely
                return loop.header;
            }
        }
        
        // Breaking out of current loop to a target outside all loops - no label needed
        return null;
    }

    /**
     * Represents the result of analyzing a branch target - either a break out of the loop,
     * a break to a labeled block, or a normal flow.
     */
    private static class BranchTargetResult {
        final Node target;           // The target node (break destination or labeled block end)
        final String breakLabel;     // The break label to use (loop header or block label)
        final int breakLabelId;      // The global ID of the break label
        final boolean isLabeledBlockBreak; // True if this is a break to a labeled block end node
        final boolean isContinue;    // True if this is a continue to the loop header
        
        BranchTargetResult(Node target, String breakLabel, int breakLabelId, boolean isLabeledBlockBreak) {
            this(target, breakLabel, breakLabelId, isLabeledBlockBreak, false);
        }
        
        BranchTargetResult(Node target, String breakLabel, int breakLabelId, boolean isLabeledBlockBreak, boolean isContinue) {
            this.target = target;
            this.breakLabel = breakLabel;
            this.breakLabelId = breakLabelId;
            this.isLabeledBlockBreak = isLabeledBlockBreak;
            this.isContinue = isContinue;
        }
    }

    /**
     * Finds if a path from start leads to a break target (outside loop or labeled block end).
     * Returns a BranchTargetResult with the target and appropriate label, or null if normal flow.
     * 
     * The target is the "significant" node where the break leads - this is used to determine
     * the break label. The path from start to target contains intermediate nodes to output.
     * 
     * Priority:
     * 1. Labeled block end node (break to that block) - highest priority
     * 2. Loop's natural exit point (the merge node of the loop condition)
     * 3. Outer loop header or structure
     * 4. Exit node
     */
    private BranchTargetResult findBranchTarget(Node start, LoopStructure currentLoop, 
                                                 Map<Node, IfStructure> ifConditions,
                                                 Map<Node, LoopStructure> loopHeaders) {
        Node current = start;
        Set<Node> visited = new HashSet<>();
        boolean foundOutsideLoop = false;
        Node firstNodeOutside = null;  // Track the first node where we went outside
        
        // Find the loop's natural exit point (the branch NOT in the loop body)
        Node loopExitPoint = null;
        IfStructure loopCondIf = ifConditions.get(currentLoop.header);
        if (loopCondIf != null) {
            // Find which branch is outside the loop body - that's the exit
            if (!currentLoop.body.contains(loopCondIf.trueBranch)) {
                loopExitPoint = loopCondIf.trueBranch;
            } else if (!currentLoop.body.contains(loopCondIf.falseBranch)) {
                loopExitPoint = loopCondIf.falseBranch;
            }
        }
        
        // Check if the start is the back-edge source - this is NOT a continue, it's normal loop body
        // When the branch goes directly to the back-edge source, it will eventually continue the loop,
        // but we shouldn't treat this as a "continue" statement - it's just normal loop execution
        boolean startIsBackEdgeSrc = start.equals(currentLoop.backEdgeSource);
        
        while (current != null && !visited.contains(current)) {
            visited.add(current);
            
            // If this is the loop header, this might be a continue
            // Check this BEFORE the conditional check since the header is often a conditional
            // BUT: only if we haven't gone outside the loop yet - if we went outside and came back,
            // it's actually a break that happens to lead back to the loop header through the outer loop
            // ALSO: if we started from the back-edge source, this is normal loop continuation, not a continue stmt
            if (current.equals(currentLoop.header) && !foundOutsideLoop) {
                if (startIsBackEdgeSrc) {
                    // Starting from back-edge source to loop header is NOT a continue statement
                    // It's normal loop body execution that will be followed by more loop iterations
                    return null; // Return null to indicate normal flow (not a break/continue target)
                }
                // Return continue result - no label needed for innermost loop continue
                // The label will be added by the caller if needed for outer loop continue
                return new BranchTargetResult(current, "", -1, false, true);
            }
            
            // If this is a conditional node inside the loop, stop - no break path
            // BUT: only if we haven't gone outside the loop yet. If we went outside and came back,
            // this is still a break pattern (the path goes outside then returns through outer loop)
            if (ifConditions.containsKey(current) && currentLoop.body.contains(current) && !foundOutsideLoop) {
                return null;
            }
            
            // Track if we've gone outside the loop
            if (!currentLoop.body.contains(current)) {
                if (!foundOutsideLoop) {
                    firstNodeOutside = current;  // Remember the first node outside
                }
                foundOutsideLoop = true;
                
                // Check if this is the loop's natural exit point
                // This should be reported as a simple break (without label for innermost loop)
                if (current.equals(loopExitPoint)) {
                    return new BranchTargetResult(current, "", -1, false);
                }
                
                // Check if this node is a labeled block's end node
                for (LabeledBlockStructure block : labeledBlocks) {
                    if (current.equals(block.endNode) && !block.breaks.isEmpty() && block != detectedReturnBlock) {
                        return new BranchTargetResult(current, block.label, block.labelId, true);
                    }
                }
                
                // Check for terminal nodes (no successors) - these might be return nodes
                // This must be checked BEFORE the "multiple or no successors" case below
                if (current.succs.isEmpty()) {
                    // Check if this is a "return" node that should target the return block
                    if (detectedReturnBlock != null) {
                        for (LabeledBreakEdge breakEdge : detectedReturnBlock.breaks) {
                            if (breakEdge.from.equals(current)) {
                                // This is a return node - use the return block label
                                return new BranchTargetResult(current, detectedReturnBlock.label, detectedReturnBlock.labelId, true);
                            }
                        }
                    }
                    // Terminal but not a return node - use normal break
                    String breakLabel = findBreakLabel(current, loopHeaders, currentLoop);
                    int breakLabelId = findBreakLabelId(current, loopHeaders, currentLoop);
                    return new BranchTargetResult(current, breakLabel, breakLabelId, false);
                }
                
                // We've gone outside the loop - but if this node leads to the exit point,
                // continue following to find the actual break target
                // Only return immediately if this node doesn't have a single successor path to exit
                if (current.succs.size() == 1) {
                    // Continue following to find the actual exit point
                    // (don't return yet - the while loop will handle it)
                } else {
                    // Multiple successors outside the loop - return this as the break target
                    String breakLabel = findBreakLabel(current, loopHeaders, currentLoop);
                    int breakLabelId = findBreakLabelId(current, loopHeaders, currentLoop);
                    return new BranchTargetResult(current, breakLabel, breakLabelId, false);
                }
            } else if (foundOutsideLoop) {
                // We went outside the loop and came back in - this is a break
                // Return the FIRST node outside as the break target (so path is empty)
                String breakLabel = findBreakLabel(firstNodeOutside, loopHeaders, currentLoop);
                int breakLabelId = findBreakLabelId(firstNodeOutside, loopHeaders, currentLoop);
                return new BranchTargetResult(firstNodeOutside, breakLabel, breakLabelId, false);
            }
            
            // Check if this is the loop's natural exit point
            // This should be reported as a simple break (without label for innermost loop)
            if (current.equals(loopExitPoint)) {
                return new BranchTargetResult(current, "", -1, false);
            }
            
            // Check if this node is a labeled block's end node
            // This takes priority because it represents continue semantics
            // Only report as labeled break if the block has actual breaks that need labeling
            // But skip the return block - that's handled specially for return nodes only
            for (LabeledBlockStructure block : labeledBlocks) {
                if (current.equals(block.endNode) && !block.breaks.isEmpty() && block != detectedReturnBlock) {
                    // This path leads to a labeled block's end node - it's a break to that block
                    return new BranchTargetResult(current, block.label, block.labelId, true);
                }
            }
            
            // If we're outside the loop and hit a conditional (like outer loop header), stop
            if (foundOutsideLoop && ifConditions.containsKey(current)) {
                String breakLabel = findBreakLabel(current, loopHeaders, currentLoop);
                int breakLabelId = findBreakLabelId(current, loopHeaders, currentLoop);
                return new BranchTargetResult(current, breakLabel, breakLabelId, false);
            }
            
            // If this node has no successors (end node like 'exit'), it's the target
            if (current.succs.isEmpty()) {
                if (foundOutsideLoop) {
                    // Check if this is a "return" node that should target the return block
                    // (i.e., a node with no successors that is NOT the normal loop exit target)
                    if (detectedReturnBlock != null) {
                        // Check if this node is a return node (has a break edge in the return block)
                        for (LabeledBreakEdge breakEdge : detectedReturnBlock.breaks) {
                            if (breakEdge.from.equals(current)) {
                                // This is a return node - use the return block label
                                return new BranchTargetResult(current, detectedReturnBlock.label, detectedReturnBlock.labelId, true);
                            }
                        }
                    }
                    // Normal break target
                    String breakLabel = findBreakLabel(current, loopHeaders, currentLoop);
                    int breakLabelId = findBreakLabelId(current, loopHeaders, currentLoop);
                    return new BranchTargetResult(current, breakLabel, breakLabelId, false);
                }
                return null;
            }
            
            // Follow single-successor chains only (non-conditional nodes)
            if (current.succs.size() == 1) {
                current = current.succs.get(0);
            } else {
                // Multiple successors (conditional) - if we're already outside, this is the target
                if (foundOutsideLoop) {
                    String breakLabel = findBreakLabel(current, loopHeaders, currentLoop);
                    int breakLabelId = findBreakLabelId(current, loopHeaders, currentLoop);
                    return new BranchTargetResult(current, breakLabel, breakLabelId, false);
                }
                return null;
            }
        }
        
        return null;
    }

    /**
     * Finds a TryStructure that starts at the given node.
     * A node starts a try structure if it's the first node in the try body
     * (has no predecessors that are also in the try body).
     */
    private TryStructure findTryStructureStartingAt(Node node) {
        TryStructure result = null;
        int smallestSize = Integer.MAX_VALUE;
        
        for (TryStructure tryStruct : tryStructures) {
            if (tryStruct.tryBody.contains(node)) {
                // Skip if this node is in a catch body of another try structure
                // (it can't start a try from within a catch block)
                boolean inOtherCatch = false;
                for (TryStructure otherTry : tryStructures) {
                    if (otherTry != tryStruct && otherTry.catchBody.contains(node)) {
                        inOtherCatch = true;
                        break;
                    }
                }
                if (inOtherCatch) {
                    continue;
                }
                
                // Check if this node is the first in the try body
                // (no predecessors in the try body)
                boolean isFirst = true;
                for (Node pred : node.preds) {
                    if (tryStruct.tryBody.contains(pred)) {
                        isFirst = false;
                        break;
                    }
                }
                if (isFirst) {
                    // Prefer the smallest try structure (innermost)
                    if (tryStruct.tryBody.size() < smallestSize) {
                        smallestSize = tryStruct.tryBody.size();
                        result = tryStruct;
                    }
                }
            }
        }
        return result;
    }
    
    /**
     * Finds all try structures that start at the given node and have the same try body.
     * Returns a GroupedTryStructure containing multiple catch handlers.
     */
    private GroupedTryStructure findGroupedTryStructureStartingAt(Node node) {
        TryStructure firstTry = findTryStructureStartingAt(node);
        if (firstTry == null) {
            return null;
        }
        
        // Find all try structures with the same try body
        List<TryStructure> handlers = new ArrayList<>();
        for (TryStructure tryStruct : tryStructures) {
            if (tryStruct.tryBody.equals(firstTry.tryBody)) {
                handlers.add(tryStruct);
            }
        }
        
        // Sort by exception index to maintain order
        handlers.sort((a, b) -> Integer.compare(a.exceptionIndex, b.exceptionIndex));
        
        return new GroupedTryStructure(firstTry.tryBody, handlers);
    }
    
    /**
     * Finds the first node in the catch body (one without predecessors in try body or earlier catch body).
     */
    private Node findCatchStartNode(TryStructure tryStruct) {
        for (Node node : tryStruct.catchBody) {
            boolean hasCatchPred = false;
            
            for (Node pred : node.preds) {
                if (tryStruct.tryBody.contains(pred)) {
                } else if (tryStruct.catchBody.contains(pred)) {
                    hasCatchPred = true;
                }
            }
            
            // The catch start node is one that doesn't have predecessors from within catch body
            // (it can have predecessors from try body as that's the exception edge)
            if (!hasCatchPred) {
                return node;
            }
        }
        // Fallback: return the first node in catch body, or null if empty
        if (tryStruct.catchBody.isEmpty()) {
            return null;
        }
        return tryStruct.catchBody.iterator().next();
    }
    
    /**
     * Finds the merge node where try and catch blocks converge.
     * This is typically the first node that both try and catch bodies reach.
     */
    private Node findTryCatchMergeNode(TryStructure tryStruct) {
        // Find successors of try body nodes that are not in try body
        Set<Node> tryExits = new HashSet<>();
        for (Node node : tryStruct.tryBody) {
            for (Node succ : node.succs) {
                if (!tryStruct.tryBody.contains(succ) && !tryStruct.catchBody.contains(succ)) {
                    tryExits.add(succ);
                }
            }
        }
        
        // Find successors of catch body nodes that are not in catch body
        Set<Node> catchExits = new HashSet<>();
        for (Node node : tryStruct.catchBody) {
            for (Node succ : node.succs) {
                if (!tryStruct.catchBody.contains(succ) && !tryStruct.tryBody.contains(succ)) {
                    catchExits.add(succ);
                }
            }
        }
        
        // Find common exit node
        for (Node tryExit : tryExits) {
            if (catchExits.contains(tryExit)) {
                return tryExit;
            }
        }
        
        // If no common exit, return the first try exit
        if (!tryExits.isEmpty()) {
            return tryExits.iterator().next();
        }
        
        return null;
    }
    
    /**
     * Generates statements for a try body, handling if-conditions at the start.
     * This is a helper method used by both generateStatements and generateStatementsForNodeSet.
     */
    private List<Statement> generateTryBodyStatements(Node startNode, TryStructure tryStruct, Set<Node> tryVisited,
                                               Map<Node, LoopStructure> loopHeaders, Map<Node, IfStructure> ifConditions,
                                               Map<Node, LabeledBlockStructure> blockStarts, Map<Node, LabeledBreakEdge> labeledBreakEdges,
                                               Set<Node> loopsNeedingLabels,
                                               LoopStructure currentLoop, LabeledBlockStructure currentBlock,
                                               Map<Node, SwitchStructure> switchStarts) {
        List<Statement> tryBodyStmts = new ArrayList<>();
        tryVisited.add(startNode);  // Mark the start node as already visited
        
        IfStructure ifStruct = ifConditions.get(startNode);
        if (ifStruct != null && tryStruct.tryBody.contains(ifStruct.trueBranch) && tryStruct.tryBody.contains(ifStruct.falseBranch)) {
            // The start node is an if-condition with both branches in the try body
            // Find merge node within the try body
            Node mergeNode = ifStruct.mergeNode;
            if (mergeNode != null && !tryStruct.tryBody.contains(mergeNode)) {
                mergeNode = findMergeNode(ifStruct.trueBranch, ifStruct.falseBranch);
                if (mergeNode != null && !tryStruct.tryBody.contains(mergeNode)) {
                    mergeNode = null;
                }
            }
            
            Set<Node> trueVisited = new HashSet<>(tryVisited);
            List<Statement> onTrue = generateStatementsForNodeSet(ifStruct.trueBranch, tryStruct.tryBody, trueVisited,
                                       loopHeaders, ifConditions, blockStarts, labeledBreakEdges,
                                       loopsNeedingLabels, currentLoop, currentBlock, switchStarts, mergeNode);
            
            Set<Node> falseVisited = new HashSet<>(tryVisited);
            List<Statement> onFalse = generateStatementsForNodeSet(ifStruct.falseBranch, tryStruct.tryBody, falseVisited,
                                       loopHeaders, ifConditions, blockStarts, labeledBreakEdges,
                                       loopsNeedingLabels, currentLoop, currentBlock, switchStarts, mergeNode);
            
            tryBodyStmts.add(new IfStatement(startNode, false, onTrue, onFalse));
            
            tryVisited.addAll(trueVisited);
            tryVisited.addAll(falseVisited);
            
            // Generate code after the merge node within the try body
            if (mergeNode != null && tryStruct.tryBody.contains(mergeNode) && !tryVisited.contains(mergeNode)) {
                tryBodyStmts.addAll(generateStatementsForNodeSet(mergeNode, tryStruct.tryBody, tryVisited,
                               loopHeaders, ifConditions, blockStarts, labeledBreakEdges,
                               loopsNeedingLabels, currentLoop, currentBlock, switchStarts, null));
            }
        } else {
            // Not an if condition or only one branch in try body - treat as regular node
            tryBodyStmts.add(new ExpressionStatement(startNode));
            
            // Process successors within the try body
            for (Node succ : startNode.succs) {
                if (tryStruct.tryBody.contains(succ) && !tryVisited.contains(succ)) {
                    tryBodyStmts.addAll(generateStatementsForNodeSet(succ, tryStruct.tryBody, tryVisited, 
                                        loopHeaders, ifConditions, blockStarts, labeledBreakEdges, 
                                        loopsNeedingLabels, currentLoop, currentBlock, switchStarts));
                }
            }
        }
        
        return tryBodyStmts;
    }
    
    /**
     * Generates statements for a catch body that is inside a loop.
     * Handles continue statements when catch body has edges back to the loop header.
     * Handles break statements when catch body has edges outside the loop.
     * Handles if-conditions and nested while loops within the catch body.
     */
    private List<Statement> generateCatchBodyInLoop(Node startNode, Set<Node> catchBody, Set<Node> visited,
                                               Map<Node, LoopStructure> loopHeaders, Map<Node, IfStructure> ifConditions,
                                               Map<Node, LabeledBlockStructure> blockStarts, Map<Node, LabeledBreakEdge> labeledBreakEdges,
                                               Set<Node> loopsNeedingLabels,
                                               LoopStructure currentLoop, LabeledBlockStructure currentBlock,
                                               Map<Node, SwitchStructure> switchStarts) {
        List<Statement> result = new ArrayList<>();
        
        if (startNode == null || visited.contains(startNode)) {
            return result;
        }
        
        visited.add(startNode);
        
        // Check if this node is a nested while loop header inside the catch body (self-loop)
        boolean isSelfLoop = startNode.succs.contains(startNode);
        if (isSelfLoop && startNode.succs.size() == 2) {
            // This is a while loop header inside catch body
            Node loopExit = null;
            for (Node succ : startNode.succs) {
                if (!succ.equals(startNode)) {
                    loopExit = succ;
                    break;
                }
            }
            
            // Generate while(true) { if (H) { break; } }
            List<Statement> whileBody = new ArrayList<>();
            List<Statement> breakBody = new ArrayList<>();
            breakBody.add(new BreakStatement(getLoopLabelId(startNode)));
            whileBody.add(new IfStatement(startNode, false, breakBody));
            result.add(new LoopStatement(getLoopLabelId(startNode), whileBody));
            
            // Continue with the exit node
            if (loopExit != null && catchBody.contains(loopExit) && !visited.contains(loopExit)) {
                result.addAll(generateCatchBodyInLoop(loopExit, catchBody, visited,
                                loopHeaders, ifConditions, blockStarts, labeledBreakEdges,
                                loopsNeedingLabels, currentLoop, currentBlock, switchStarts));
            }
            return result;
        }
        
        // Check if this is an if-condition with 2 successors
        if (startNode.succs.size() == 2) {
            Node trueSucc = startNode.succs.get(0);
            Node falseSucc = startNode.succs.get(1);
            
            // Determine which branch is inside the catch body and which exits the loop
            boolean trueInCatch = catchBody.contains(trueSucc);
            boolean falseInCatch = catchBody.contains(falseSucc);
            boolean trueIsBreak = currentLoop != null && !currentLoop.body.contains(trueSucc) && !catchBody.contains(trueSucc);
            boolean falseIsBreak = currentLoop != null && !currentLoop.body.contains(falseSucc) && !catchBody.contains(falseSucc);
            boolean trueIsContinue = currentLoop != null && trueSucc.equals(currentLoop.header);
            boolean falseIsContinue = currentLoop != null && falseSucc.equals(currentLoop.header);
            
            // Case: one branch exits (break/continue), the other continues in catch body
            if ((trueIsBreak || trueIsContinue) && (falseInCatch || falseIsBreak || falseIsContinue)) {
                // True branch exits the loop
                List<Statement> trueBody = new ArrayList<>();
                if (trueIsBreak) {
                    trueBody.add(new BreakStatement(getLoopLabelId(currentLoop.header)));
                } else if (trueIsContinue) {
                    trueBody.add(new ContinueStatement(getLoopLabelId(currentLoop.header)));
                }
                result.add(new IfStatement(startNode, false, trueBody));
                
                // Process false branch (the rest of catch body or exit)
                if (falseInCatch && !visited.contains(falseSucc)) {
                    result.addAll(generateCatchBodyInLoop(falseSucc, catchBody, visited,
                                    loopHeaders, ifConditions, blockStarts, labeledBreakEdges,
                                    loopsNeedingLabels, currentLoop, currentBlock, switchStarts));
                } else if (falseIsBreak) {
                    result.add(new BreakStatement(getLoopLabelId(currentLoop.header)));
                } else if (falseIsContinue) {
                    result.add(new ContinueStatement(getLoopLabelId(currentLoop.header)));
                }
                return result;
            } else if ((falseIsBreak || falseIsContinue) && trueInCatch) {
                // False branch exits, true branch continues
                List<Statement> falseBody = new ArrayList<>();
                if (falseIsBreak) {
                    falseBody.add(new BreakStatement(getLoopLabelId(currentLoop.header)));
                } else if (falseIsContinue) {
                    falseBody.add(new ContinueStatement(getLoopLabelId(currentLoop.header)));
                }
                result.add(new IfStatement(startNode, true, falseBody));  // negated condition
                
                // Process true branch (the rest of catch body)
                if (!visited.contains(trueSucc)) {
                    result.addAll(generateCatchBodyInLoop(trueSucc, catchBody, visited,
                                    loopHeaders, ifConditions, blockStarts, labeledBreakEdges,
                                    loopsNeedingLabels, currentLoop, currentBlock, switchStarts));
                }
                return result;
            } else if (trueInCatch && falseInCatch) {
                // Both branches are in catch body - check for inner while loop pattern
                // e.g., d->H where H has a self-loop and d->k where k is the loop exit
                boolean trueIsSelfLoop = trueSucc.succs.contains(trueSucc);
                boolean falseIsSelfLoop = falseSucc.succs.contains(falseSucc);
                
                if (trueIsSelfLoop && !falseIsSelfLoop) {
                    // True branch is a while loop header, false branch is the merge/exit point
                    // Find the loop exit of the true branch (should be the same as false branch)
                    Node trueLoopExit = null;
                    for (Node succ : trueSucc.succs) {
                        if (!succ.equals(trueSucc)) {
                            trueLoopExit = succ;
                            break;
                        }
                    }
                    
                    // Generate: if (d) { while(true) { if (H) { break; } } }
                    List<Statement> ifBody = new ArrayList<>();
                    List<Statement> whileBody = new ArrayList<>();
                    List<Statement> breakBody = new ArrayList<>();
                    breakBody.add(new BreakStatement(getLoopLabelId(trueSucc)));
                    whileBody.add(new IfStatement(trueSucc, false, breakBody));
                    ifBody.add(new LoopStatement(getLoopLabelId(trueSucc), whileBody));
                    result.add(new IfStatement(startNode, false, ifBody));
                    
                    // Mark the loop header as visited
                    visited.add(trueSucc);
                    
                    // Process the merge point (false branch / loop exit)
                    // If both branches merge at the same node, process it once
                    Node mergeNode = falseSucc;
                    if (trueLoopExit != null && trueLoopExit.equals(falseSucc)) {
                        // Both branches merge at the same node
                        mergeNode = falseSucc;
                    }
                    
                    if (!visited.contains(mergeNode)) {
                        result.addAll(generateCatchBodyInLoop(mergeNode, catchBody, visited,
                                        loopHeaders, ifConditions, blockStarts, labeledBreakEdges,
                                        loopsNeedingLabels, currentLoop, currentBlock, switchStarts));
                    }
                    return result;
                } else if (falseIsSelfLoop && !trueIsSelfLoop) {
                    // False branch is a while loop header - negate condition
                    // Find the loop exit of the false branch
                    Node falseLoopExit = null;
                    for (Node succ : falseSucc.succs) {
                        if (!succ.equals(falseSucc)) {
                            falseLoopExit = succ;
                            break;
                        }
                    }
                    
                    // Generate: if (!d) { while(true) { if (H) { break; } } }  (negated)
                    List<Statement> ifBody = new ArrayList<>();
                    List<Statement> whileBody = new ArrayList<>();
                    List<Statement> breakBody = new ArrayList<>();
                    breakBody.add(new BreakStatement(getLoopLabelId(falseSucc)));
                    whileBody.add(new IfStatement(falseSucc, false, breakBody));
                    ifBody.add(new LoopStatement(getLoopLabelId(falseSucc), whileBody));
                    result.add(new IfStatement(startNode, true, ifBody));  // negated
                    
                    // Mark the loop header as visited
                    visited.add(falseSucc);
                    
                    // Process the merge point (true branch / loop exit)
                    Node mergeNode = trueSucc;
                    if (falseLoopExit != null && falseLoopExit.equals(trueSucc)) {
                        mergeNode = trueSucc;
                    }
                    
                    if (!visited.contains(mergeNode)) {
                        result.addAll(generateCatchBodyInLoop(mergeNode, catchBody, visited,
                                        loopHeaders, ifConditions, blockStarts, labeledBreakEdges,
                                        loopsNeedingLabels, currentLoop, currentBlock, switchStarts));
                    }
                    return result;
                }
            }
        }
        
        // Default: output the statement
        result.add(new ExpressionStatement(startNode));
        
        // Check successors
        for (Node succ : startNode.succs) {
            if (catchBody.contains(succ) && !visited.contains(succ)) {
                // Continue processing within catch body
                result.addAll(generateCatchBodyInLoop(succ, catchBody, visited,
                                loopHeaders, ifConditions, blockStarts, labeledBreakEdges,
                                loopsNeedingLabels, currentLoop, currentBlock, switchStarts));
            } else if (currentLoop != null && succ.equals(currentLoop.header)) {
                // Edge to loop header = continue statement
                result.add(new ContinueStatement(getLoopLabelId(currentLoop.header)));
            } else if (currentLoop != null && !currentLoop.body.contains(succ)) {
                // Edge to node outside the loop = break statement
                result.add(new BreakStatement(getLoopLabelId(currentLoop.header)));
            }
        }
        
        return result;
    }
    
    /**
     * Generates statements for a specific set of nodes, starting from a given node.
     * Only generates statements for nodes that are in the given nodeSet.
     */
    private List<Statement> generateStatementsForNodeSet(Node node, Set<Node> nodeSet, Set<Node> visited,
                                               Map<Node, LoopStructure> loopHeaders, Map<Node, IfStructure> ifConditions,
                                               Map<Node, LabeledBlockStructure> blockStarts, Map<Node, LabeledBreakEdge> labeledBreakEdges,
                                               Set<Node> loopsNeedingLabels,
                                               LoopStructure currentLoop, LabeledBlockStructure currentBlock,
                                               Map<Node, SwitchStructure> switchStarts) {
        return generateStatementsForNodeSet(node, nodeSet, visited, loopHeaders, ifConditions, 
                                           blockStarts, labeledBreakEdges, loopsNeedingLabels,
                                           currentLoop, currentBlock, switchStarts, null);
    }
    
    /**
     * Generates statements for a specific set of nodes, starting from a given node.
     * Only generates statements for nodes that are in the given nodeSet.
     * Stops at stopAt node if provided.
     */
    private List<Statement> generateStatementsForNodeSet(Node node, Set<Node> nodeSet, Set<Node> visited,
                                               Map<Node, LoopStructure> loopHeaders, Map<Node, IfStructure> ifConditions,
                                               Map<Node, LabeledBlockStructure> blockStarts, Map<Node, LabeledBreakEdge> labeledBreakEdges,
                                               Set<Node> loopsNeedingLabels,
                                               LoopStructure currentLoop, LabeledBlockStructure currentBlock,
                                               Map<Node, SwitchStructure> switchStarts, Node stopAt) {
        List<Statement> result = new ArrayList<>();
        
        if (node == null || visited.contains(node) || !nodeSet.contains(node)) {
            return result;
        }
        
        // Stop at the merge node
        if (stopAt != null && node.equals(stopAt)) {
            return result;
        }
        
        visited.add(node);
        
        // Check if this node starts a nested try block (check before if structure)
        GroupedTryStructure groupedTry = findGroupedTryStructureStartingAt(node);
        if (groupedTry != null) {
            // Use the first handler's try body for generating statements
            TryStructure firstTry = groupedTry.catchHandlers.get(0);
            
            // Generate try body statements using the helper method
            Set<Node> tryVisited = new HashSet<>();
            List<Statement> tryBodyStmts = generateTryBodyStatements(node, firstTry, tryVisited,
                                        loopHeaders, ifConditions, blockStarts, labeledBreakEdges, 
                                        loopsNeedingLabels, currentLoop, currentBlock, switchStarts);
            
            // Generate catch blocks for each handler
            List<TryStatement.CatchBlock> catchBlocks = new ArrayList<>();
            for (TryStructure handler : groupedTry.catchHandlers) {
                Node catchStart = findCatchStartNode(handler);
                Set<Node> catchVisited = new HashSet<>();
                List<Statement> catchBody = new ArrayList<>();
                if (catchStart != null) {
                    catchBody = generateStatementsForNodeSet(catchStart, handler.catchBody, catchVisited,
                                                loopHeaders, ifConditions, blockStarts, labeledBreakEdges,
                                                loopsNeedingLabels, currentLoop, currentBlock, switchStarts);
                }
                catchBlocks.add(new TryStatement.CatchBlock(handler.exceptionIndex, catchBody));
                
                // Mark catch nodes as visited
                visited.addAll(handler.catchBody);
            }
            
            result.add(TryStatement.withMultipleCatch(tryBodyStmts, catchBlocks));
            
            // Mark all try nodes as visited in the main visited set
            visited.addAll(groupedTry.tryBody);
            
            // Find the merge node and continue
            Node tryCatchMerge = findTryCatchMergeNode(firstTry);
            if (tryCatchMerge != null && nodeSet.contains(tryCatchMerge) && !visited.contains(tryCatchMerge)) {
                result.addAll(generateStatementsForNodeSet(tryCatchMerge, nodeSet, visited,
                               loopHeaders, ifConditions, blockStarts, labeledBreakEdges,
                               loopsNeedingLabels, currentLoop, currentBlock, switchStarts, stopAt));
            }
            
            return result;
        }
        
        // Check if this is an if condition
        IfStructure ifStruct = ifConditions.get(node);
        if (ifStruct != null) {
            boolean trueInSet = nodeSet.contains(ifStruct.trueBranch);
            boolean falseInSet = nodeSet.contains(ifStruct.falseBranch);
            
            // Find merge node within the set
            Node mergeNode;
            if (ifStruct.mergeNode != null && nodeSet.contains(ifStruct.mergeNode)) {
                mergeNode = ifStruct.mergeNode;
            } else {
                mergeNode = findMergeNode(ifStruct.trueBranch, ifStruct.falseBranch);
                if (mergeNode != null && !nodeSet.contains(mergeNode)) {
                    mergeNode = null;
                }
            }
            
            if (trueInSet && falseInSet) {
                // Both branches are in the set - standard if-else
                // Stop at merge node when generating branches
                Set<Node> trueVisited = new HashSet<>(visited);
                List<Statement> onTrue = generateStatementsForNodeSet(ifStruct.trueBranch, nodeSet, trueVisited,
                                           loopHeaders, ifConditions, blockStarts, labeledBreakEdges,
                                           loopsNeedingLabels, currentLoop, currentBlock, switchStarts, mergeNode);
                
                Set<Node> falseVisited = new HashSet<>(visited);
                List<Statement> onFalse = generateStatementsForNodeSet(ifStruct.falseBranch, nodeSet, falseVisited,
                                           loopHeaders, ifConditions, blockStarts, labeledBreakEdges,
                                           loopsNeedingLabels, currentLoop, currentBlock, switchStarts, mergeNode);
                
                result.add(new IfStatement(node, false, onTrue, onFalse));
                
                visited.addAll(trueVisited);
                visited.addAll(falseVisited);
                
                // Generate code after the merge node
                if (mergeNode != null && !visited.contains(mergeNode)) {
                    result.addAll(generateStatementsForNodeSet(mergeNode, nodeSet, visited,
                                   loopHeaders, ifConditions, blockStarts, labeledBreakEdges,
                                   loopsNeedingLabels, currentLoop, currentBlock, switchStarts, stopAt));
                }
            } else if (trueInSet) {
                // Only true branch is in set
                result.add(new ExpressionStatement(node));
                result.addAll(generateStatementsForNodeSet(ifStruct.trueBranch, nodeSet, visited,
                               loopHeaders, ifConditions, blockStarts, labeledBreakEdges,
                               loopsNeedingLabels, currentLoop, currentBlock, switchStarts, stopAt));
            } else if (falseInSet) {
                // Only false branch is in set
                result.add(new ExpressionStatement(node));
                result.addAll(generateStatementsForNodeSet(ifStruct.falseBranch, nodeSet, visited,
                               loopHeaders, ifConditions, blockStarts, labeledBreakEdges,
                               loopsNeedingLabels, currentLoop, currentBlock, switchStarts, stopAt));
            } else {
                // Neither branch in set - just output node
                result.add(new ExpressionStatement(node));
            }
            
            return result;
        }
        
        // Regular node
        result.add(new ExpressionStatement(node));
        
        // Continue with successors that are in the set
        for (Node succ : node.succs) {
            if (nodeSet.contains(succ) && !visited.contains(succ) && !succ.equals(stopAt)) {
                result.addAll(generateStatementsForNodeSet(succ, nodeSet, visited,
                               loopHeaders, ifConditions, blockStarts, labeledBreakEdges,
                               loopsNeedingLabels, currentLoop, currentBlock, switchStarts, stopAt));
            }
        }
        
        return result;
    }
    
    /**
     * Finds the internal merge point where both branches converge within a given body.
     * This is different from the global merge which might be outside the body.
     */
    private Node findInternalMerge(Node branch1, Node branch2, Set<Node> body) {
        // Find nodes reachable from branch1 within the body
        Set<Node> reachable1 = new HashSet<>();
        Queue<Node> queue = new LinkedList<>();
        queue.add(branch1);
        while (!queue.isEmpty()) {
            Node n = queue.poll();
            if (reachable1.contains(n)) continue;
            reachable1.add(n);
            for (Node succ : n.succs) {
                if (body.contains(succ)) {
                    queue.add(succ);
                }
            }
        }
        
        // Check if branch2 is reachable from branch1 (through a path in the body)
        // If so, branch2 is the merge point
        if (reachable1.contains(branch2)) {
            return branch2;
        }
        
        // Find nodes reachable from branch2 within the body
        Set<Node> reachable2 = new HashSet<>();
        queue.add(branch2);
        while (!queue.isEmpty()) {
            Node n = queue.poll();
            if (reachable2.contains(n)) continue;
            reachable2.add(n);
            for (Node succ : n.succs) {
                if (body.contains(succ)) {
                    queue.add(succ);
                }
            }
        }
        
        // The internal merge is the first node in branch2's path that branch1 also reaches
        // Use BFS from branch2 to find the first node that's in reachable1
        Set<Node> visited = new HashSet<>();
        queue.add(branch2);
        while (!queue.isEmpty()) {
            Node n = queue.poll();
            if (visited.contains(n)) continue;
            visited.add(n);
            
            if (!n.equals(branch2) && reachable1.contains(n)) {
                return n;
            }
            
            for (Node succ : n.succs) {
                if (body.contains(succ)) {
                    queue.add(succ);
                }
            }
        }
        
        return null;
    }
    
    /**
     * Checks if a branch contains a node with a labeled break to the current block.
     * This means the branch exits the block.
     */
    private boolean branchHasLabeledBreak(Node branch, Map<Node, LabeledBreakEdge> labeledBreakEdges, 
                                           LabeledBlockStructure currentBlock) {
        // Check if the branch itself has a labeled break
        LabeledBreakEdge breakEdge = labeledBreakEdges.get(branch);
        if (breakEdge != null && currentBlock.label.equals(breakEdge.label)) {
            return true;
        }
        
        // Check if any node reachable from branch (within the block) has a labeled break
        Set<Node> visited = new HashSet<>();
        Queue<Node> queue = new LinkedList<>();
        queue.add(branch);
        
        while (!queue.isEmpty()) {
            Node n = queue.poll();
            if (visited.contains(n)) continue;
            visited.add(n);
            
            breakEdge = labeledBreakEdges.get(n);
            if (breakEdge != null && currentBlock.label.equals(breakEdge.label)) {
                return true;
            }
            
            for (Node succ : n.succs) {
                if (currentBlock.body.contains(succ)) {
                    queue.add(succ);
                }
            }
        }
        
        return false;
    }
    
    /**
     * Finds the common break target if all loop breaks go to the same node.
     * Returns null if breaks go to different targets or there are no breaks.
     */
    private Node findCommonBreakTarget(LoopStructure loop) {
        if (loop.breaks.isEmpty()) {
            return null;
        }
        Node breakTarget = loop.breaks.get(0).to;
        for (BreakEdge breakEdge : loop.breaks) {
            if (!breakEdge.to.equals(breakTarget)) {
                return null; // Different targets
            }
        }
        return breakTarget;
    }
    
    /**
     * Checks if a node should be processed as a labeled block.
     * Returns false if the node is also a loop header that should be processed as a loop instead.
     */
    private boolean shouldProcessAsLabeledBlock(LabeledBlockStructure block, LabeledBlockStructure currentBlock,
                                                 LoopStructure loopAtNode, LoopStructure currentLoop) {
        if (block == null || currentBlock == block || block.breaks.isEmpty()) {
            return false;
        }
        // If this node is also a loop header (and not the current loop), let loop handling take care of it
        if (loopAtNode != null && currentLoop != loopAtNode) {
            return false;
        }
        return true;
    }

    // ============ Statement-based pseudocode generation methods ============
    
    private List<Statement> generateStatements(Node node, Set<Node> visited,
                                               Map<Node, LoopStructure> loopHeaders, Map<Node, IfStructure> ifConditions,
                                               Map<Node, LabeledBlockStructure> blockStarts, Map<Node, LabeledBreakEdge> labeledBreakEdges,
                                               Set<Node> loopsNeedingLabels,
                                               LoopStructure currentLoop, LabeledBlockStructure currentBlock, Node stopAt,
                                               Map<Node, SwitchStructure> switchStarts) {
        List<Statement> result = new ArrayList<>();
        
        if (node == null || visited.contains(node)) {
            return result;
        }
        
        // Stop at merge node, loop exit, or block end
        if (stopAt != null && node.equals(stopAt)) {
            return result;
        }
        
        // Check if this node is the start of a try block
        GroupedTryStructure groupedTry = findGroupedTryStructureStartingAt(node);
        if (groupedTry != null) {
            // Use the first handler's try body for generating statements
            TryStructure firstTry = groupedTry.catchHandlers.get(0);
            
            // Generate try body statements using the helper method
            Set<Node> tryVisited = new HashSet<>();
            List<Statement> tryBodyStmts = generateTryBodyStatements(node, firstTry, tryVisited,
                                        loopHeaders, ifConditions, blockStarts, labeledBreakEdges, 
                                        loopsNeedingLabels, currentLoop, currentBlock, switchStarts);
            
            // Generate catch blocks for each handler
            List<TryStatement.CatchBlock> catchBlocks = new ArrayList<>();
            for (TryStructure handler : groupedTry.catchHandlers) {
                Node catchStart = findCatchStartNode(handler);
                Set<Node> catchVisited = new HashSet<>();
                List<Statement> catchBody = new ArrayList<>();
                if (catchStart != null) {
                    catchBody = generateStatementsForNodeSet(catchStart, handler.catchBody, catchVisited,
                                                loopHeaders, ifConditions, blockStarts, labeledBreakEdges,
                                                loopsNeedingLabels, currentLoop, currentBlock, switchStarts);
                }
                catchBlocks.add(new TryStatement.CatchBlock(handler.exceptionIndex, catchBody));
                
                // Mark catch nodes as visited
                visited.addAll(handler.catchBody);
            }
            
            result.add(TryStatement.withMultipleCatch(tryBodyStmts, catchBlocks));
            
            // Mark all try nodes as visited
            visited.addAll(groupedTry.tryBody);
            
            // Find the merge node (common successor of try and catch blocks)
            Node tryCatchMerge = findTryCatchMergeNode(firstTry);
            if (tryCatchMerge != null && !visited.contains(tryCatchMerge)) {
                result.addAll(generateStatements(tryCatchMerge, visited, loopHeaders, ifConditions, 
                    blockStarts, labeledBreakEdges, loopsNeedingLabels, currentLoop, currentBlock, stopAt, switchStarts));
            }
            
            return result;
        }
        
        // Check if this is a switch start node
        SwitchStructure switchStruct = switchStarts != null ? switchStarts.get(node) : null;
        if (switchStruct != null) {
            // Check if there's a labeled block around this switch (for labeled breaks from within case bodies)
            LabeledBlockStructure switchBlock = blockStarts.get(node);
            String switchLabel = null;
            int switchLabelId = -1;
            if (switchBlock != null && !switchBlock.breaks.isEmpty() && switchBlock.endNode.equals(switchStruct.mergeNode)) {
                // Check if any break is from inside a nested loop - only then do we need a label
                boolean needsLabel = false;
                for (LabeledBreakEdge breakEdge : switchBlock.breaks) {
                    // Check if this break is from inside a loop (not just a simple case break)
                    for (LoopStructure loop : loopHeaders.values()) {
                        if (loop.body.contains(breakEdge.from)) {
                            needsLabel = true;
                            break;
                        }
                    }
                    if (needsLabel) break;
                }
                
                if (needsLabel) {
                    // This switch needs a label for breaks from within nested loops
                    // Use the switch label (with loop prefix) instead of block label
                    switchLabel = getSwitchLabel(node);
                    switchLabelId = getSwitchLabelId(node);
                }
            }
            
            // Generate switch statement
            List<SwitchStatement.Case> switchCases = new ArrayList<>();
            
            // Collect all case body nodes and the next case body for each (for fall-through detection)
            Map<Node, Node> caseBodyToNextBody = new HashMap<>();
            for (int i = 0; i < switchStruct.cases.size(); i++) {
                SwitchCase sc = switchStruct.cases.get(i);
                if (sc.caseBody != null) {
                    // Find the next case body (for fall-through stop point)
                    Node nextBody = null;
                    for (int j = i + 1; j < switchStruct.cases.size(); j++) {
                        if (switchStruct.cases.get(j).caseBody != null) {
                            nextBody = switchStruct.cases.get(j).caseBody;
                            break;
                        }
                    }
                    caseBodyToNextBody.put(sc.caseBody, nextBody);
                }
            }
            
            for (int i = 0; i < switchStruct.cases.size(); i++) {
                SwitchCase sc = switchStruct.cases.get(i);
                boolean isLastCase = (i == switchStruct.cases.size() - 1);
                List<Statement> caseBody = new ArrayList<>();
                
                // Generate full case body content (not just the label)
                if (sc.caseBody != null) {
                    Set<Node> caseVisited = new HashSet<>();
                    // Stop at the merge node or the next case body (for fall-through)
                    Node stopNode;
                    if (sc.hasBreak) {
                        stopNode = switchStruct.mergeNode;
                    } else {
                        // Fall-through case: stop at next case body.
                        // For the last case, there's no next body, so use merge node to prevent
                        // merge node content from being included in the case body.
                        stopNode = caseBodyToNextBody.get(sc.caseBody);
                        if (stopNode == null) {
                            stopNode = switchStruct.mergeNode;
                        }
                    }
                    List<Statement> bodyStatements = generateStatements(sc.caseBody, caseVisited, loopHeaders, ifConditions, blockStarts, labeledBreakEdges, loopsNeedingLabels, currentLoop, currentBlock, stopNode, switchStarts);
                    
                    // If we have a switch label, replace block breaks with switch breaks
                    if (switchLabel != null && switchBlock != null) {
                        bodyStatements = replaceBlockBreaksWithSwitchBreaks(bodyStatements, switchBlock.label, switchLabel, switchLabelId);
                    }
                    
                    caseBody.addAll(bodyStatements);
                }
                
                // Add break statement only if this case has a break.
                // Don't add break for the last case - execution naturally exits the switch.
                if (sc.hasBreak && !isLastCase) {
                    caseBody.add(new BreakStatement(switchLabelId));
                }
                
                if (sc.isDefault) {
                    switchCases.add(new SwitchStatement.Case(caseBody));
                } else {
                    switchCases.add(new SwitchStatement.Case(sc.conditionNode.getLabel(), sc.conditionNode, sc.negated, caseBody));
                }
            }
            
            result.add(new SwitchStatement(switchCases, switchLabel, switchLabelId));
            
            // Mark all switch condition nodes as visited (case bodies are handled by recursive generation)
            for (SwitchCase sc : switchStruct.cases) {
                if (sc.conditionNode != null) {
                    visited.add(sc.conditionNode);
                }
            }
            
            // Mark the switch block as consumed so it won't be rendered separately
            if (switchBlock != null && switchLabel != null) {
                visited.add(node); // Prevent labeled block from being rendered
            }
            
            // Continue after the switch (at the merge node)
            if (switchStruct.mergeNode != null) {
                result.addAll(generateStatements(switchStruct.mergeNode, visited, loopHeaders, ifConditions, blockStarts, labeledBreakEdges, loopsNeedingLabels, currentLoop, currentBlock, stopAt, switchStarts));
            }
            return result;
        }
        
        // Check if this is a labeled block start (before marking as visited)
        // Only render the block if there are actual breaks targeting it
        // BUT: if this node is also a loop header, let the loop handling take care of it instead
        LabeledBlockStructure block = blockStarts.get(node);
        LoopStructure loopAtNode = loopHeaders.get(node);
        if (shouldProcessAsLabeledBlock(block, currentBlock, loopAtNode, currentLoop)) {
            // Generate body of the block - process the start node's content and successors
            Set<Node> blockVisited = new HashSet<>();
            List<Statement> blockBody = generateStatementsInBlock(node, blockVisited, loopHeaders, ifConditions, 
                                      labeledBreakEdges, block);
            
            result.add(new BlockStatement(block.label, block.labelId, blockBody));
            
            // Continue after the block
            visited.add(node);
            visited.addAll(blockVisited);
            result.addAll(generateStatements(block.endNode, visited, loopHeaders, ifConditions, 
                              blockStarts, labeledBreakEdges, loopsNeedingLabels, currentLoop, null, stopAt, switchStarts));
            return result;
        }
        
        visited.add(node);
        
        // Check if this node has a labeled break
        LabeledBreakEdge labeledBreak = labeledBreakEdges.get(node);
        if (labeledBreak != null && currentBlock != null && currentBlock.label.equals(labeledBreak.label)) {
            // This is a conditional node with a labeled break
            IfStructure ifStruct = ifConditions.get(node);
            if (ifStruct != null) {
                // Check if the break is on true or false branch
                boolean breakOnTrue = ifStruct.trueBranch.equals(labeledBreak.to);
                
                List<Statement> onTrue = new ArrayList<>();
                List<Statement> onFalse = new ArrayList<>();
                
                // Use unlabeled break when breaking out of immediately enclosing block
                boolean useUnlabeledBreak = currentBlock != null && labeledBreak.label.equals(currentBlock.label);
                
                if (breakOnTrue) {
                    onTrue.add(useUnlabeledBreak ? new BreakStatement(currentBlock.labelId) : new BreakStatement(labeledBreak.label, labeledBreak.labelId));
                    Set<Node> elseVisited = new HashSet<>(visited);
                    onFalse.addAll(generateStatements(ifStruct.falseBranch, elseVisited, loopHeaders, ifConditions, 
                                      blockStarts, labeledBreakEdges, loopsNeedingLabels, currentLoop, currentBlock, stopAt, switchStarts));
                } else {
                    Set<Node> thenVisited = new HashSet<>(visited);
                    onTrue.addAll(generateStatements(ifStruct.trueBranch, thenVisited, loopHeaders, ifConditions, 
                                      blockStarts, labeledBreakEdges, loopsNeedingLabels, currentLoop, currentBlock, stopAt, switchStarts));
                    onFalse.add(useUnlabeledBreak ? new BreakStatement(currentBlock.labelId) : new BreakStatement(labeledBreak.label, labeledBreak.labelId));
                }
                
                result.add(new IfStatement(node, false, onTrue, onFalse));
                return result;
            }
        }
        
        // Check if this is a loop header
        LoopStructure loop = loopHeaders.get(node);
        if (loop != null && currentLoop != loop) {
            // Find the node that continues the loop (not the exit)
            Node loopContinue = null;
            Node loopExit = null;
            Node secondContinue = null; // Second branch that's also in loop body
            for (Node succ : node.succs) {
                if (loop.body.contains(succ) && !succ.equals(node)) {
                    if (loopContinue == null) {
                        loopContinue = succ;
                    } else {
                        secondContinue = succ;
                    }
                } else if (!loop.body.contains(succ)) {
                    loopExit = succ;
                }
            }
            
            // Build loop body
            List<Statement> loopBody = new ArrayList<>();
            
            // If header has 2 successors (condition check), output the break condition first
            if (loopExit != null && node.succs.size() == 2) {
                List<Statement> breakBody = new ArrayList<>();
                breakBody.add(new BreakStatement(getLoopLabelId(node)));
                // Check if exit is on first edge (true branch) or second edge (false branch)
                boolean exitOnTrueBranch = node.succs.get(0).equals(loopExit);
                // If exit is on true branch, condition is NOT negated: if (cond) { break; }
                // If exit is on false branch, condition IS negated: if (!cond) { break; }
                loopBody.add(new IfStatement(node, !exitOnTrueBranch, breakBody));
            } else if (node.succs.size() == 2 && secondContinue != null) {
                // Both branches are inside the loop - generate if-else for the header condition
                // First edge is true branch, second edge is false branch
                Node trueBranch = node.succs.get(0);
                Node falseBranch = node.succs.get(1);
                
                Set<Node> loopVisited = new HashSet<>();
                loopVisited.add(node); // Don't revisit header
                
                // Check if there's a labeled block starting at the loop header
                // If so, wrap the loop body content in this block
                LabeledBlockStructure headerBlock = blockStarts.get(node);
                boolean hasHeaderBlock = headerBlock != null && !headerBlock.breaks.isEmpty() 
                    && loop.body.contains(headerBlock.endNode);
                
                // Check if the loop header has an IfStructure with a merge node inside the loop
                IfStructure headerIfStruct = ifConditions.get(node);
                Node mergeNode = (headerIfStruct != null && headerIfStruct.mergeNode != null && 
                                  loop.body.contains(headerIfStruct.mergeNode)) ? headerIfStruct.mergeNode : null;
                
                // Content that will go inside the block (or directly in loop if no block)
                List<Statement> blockContent = new ArrayList<>();
                
                if (mergeNode != null) {
                    // Both branches merge at a common node inside the loop
                    // Check if the false branch can flow into the true branch, making the
                    // true branch a continuation point rather than an alternative branch.
                    // This handles cases like: if (!cond) { false_branch } true_branch
                    // where false_branch paths either break out or converge at true_branch.
                    boolean falseBranchReachesTrueBranch = canReachNodeWithin(falseBranch, trueBranch, loop.body);
                    
                    if (falseBranchReachesTrueBranch) {
                        // False branch can reach true branch (true branch is convergence point)
                        // Generate: if (!cond) { falseBranch content } trueBranch (as continuation)
                        Set<Node> falseVisited = new HashSet<>(loopVisited);
                        LabeledBlockStructure blockForGeneration = hasHeaderBlock ? headerBlock : currentBlock;
                        List<Statement> falseBody = generateStatementsInLoop(falseBranch, falseVisited, loopHeaders, ifConditions, labeledBreakEdges, blockStarts, loopsNeedingLabels, loop, blockForGeneration, trueBranch, switchStarts);
                        
                        // Only create if statement if false branch has content
                        if (!falseBody.isEmpty()) {
                            blockContent.add(new IfStatement(node, true, falseBody)); // negated condition
                        }
                        
                        // Generate the true branch content (after the if) - this is the convergence point
                        loopVisited.addAll(falseVisited);
                        Node stopNode = hasHeaderBlock ? headerBlock.endNode : null;
                        blockContent.addAll(generateStatementsInLoop(trueBranch, loopVisited, loopHeaders, ifConditions, labeledBreakEdges, blockStarts, loopsNeedingLabels, loop, blockForGeneration, stopNode, switchStarts));
                    } else {
                        // True and false branches don't converge at true branch
                        // Generate proper if-else: if (!cond) { falseBranch } else { trueBranch } then mergeNode
                        Set<Node> falseVisited = new HashSet<>(loopVisited);
                        LabeledBlockStructure blockForGeneration = hasHeaderBlock ? headerBlock : currentBlock;
                        List<Statement> falseBody = generateStatementsInLoop(falseBranch, falseVisited, loopHeaders, ifConditions, labeledBreakEdges, blockStarts, loopsNeedingLabels, loop, blockForGeneration, mergeNode, switchStarts);
                        
                        Set<Node> trueVisited = new HashSet<>(loopVisited);
                        List<Statement> trueBody = generateStatementsInLoop(trueBranch, trueVisited, loopHeaders, ifConditions, labeledBreakEdges, blockStarts, loopsNeedingLabels, loop, blockForGeneration, mergeNode, switchStarts);
                        
                        // Generate if-else structure
                        blockContent.add(new IfStatement(node, true, falseBody, trueBody)); // negated condition
                        
                        // Continue with the merge node (up to block end if we have a header block)
                        loopVisited.addAll(falseVisited);
                        loopVisited.addAll(trueVisited);
                        Node stopNode = hasHeaderBlock ? headerBlock.endNode : null;
                        blockContent.addAll(generateStatementsInLoop(mergeNode, loopVisited, loopHeaders, ifConditions, labeledBreakEdges, blockStarts, loopsNeedingLabels, loop, blockForGeneration, stopNode, switchStarts));
                    }
                } else {
                    // No merge node - use original behavior
                    // The header becomes an if condition: if (!cond) { falseBranch } then trueBranch
                    // Generate the false branch content (inside if (!cond) { ... })
                    // Use trueBranch as stopAt since both branches converge there
                    Set<Node> falseVisited = new HashSet<>(loopVisited);
                    LabeledBlockStructure blockForGeneration = hasHeaderBlock ? headerBlock : currentBlock;
                    List<Statement> falseBody = generateStatementsInLoop(falseBranch, falseVisited, loopHeaders, ifConditions, labeledBreakEdges, blockStarts, loopsNeedingLabels, loop, blockForGeneration, trueBranch, switchStarts);
                    
                    // Only create if statement if false branch has content
                    if (!falseBody.isEmpty()) {
                        blockContent.add(new IfStatement(node, true, falseBody)); // negated condition
                    }
                    
                    // Generate the true branch content (after the if)
                    loopVisited.addAll(falseVisited);
                    Node stopNode = hasHeaderBlock ? headerBlock.endNode : null;
                    blockContent.addAll(generateStatementsInLoop(trueBranch, loopVisited, loopHeaders, ifConditions, labeledBreakEdges, blockStarts, loopsNeedingLabels, loop, blockForGeneration, stopNode, switchStarts));
                }
                
                // If there's a header block, wrap the content in a BlockStatement
                if (hasHeaderBlock) {
                    loopBody.add(new BlockStatement(headerBlock.label, headerBlock.labelId, blockContent));
                    // Continue with the block end node (back-edge source check)
                    loopBody.addAll(generateStatementsInLoop(headerBlock.endNode, loopVisited, loopHeaders, ifConditions, labeledBreakEdges, blockStarts, loopsNeedingLabels, loop, currentBlock, switchStarts));
                } else {
                    loopBody.addAll(blockContent);
                }
                
                // Determine loop label - always get loopLabelId, but only show label if needed
                String loopLabel = loopsNeedingLabels.contains(node) ? getLoopLabel(node) : null;
                int loopLabelId = getLoopLabelId(node);
                result.add(new LoopStatement(loopLabel, loopLabelId, loopBody));
                
                // Continue after the loop (from breaks)
                Node breakTarget = findCommonBreakTarget(loop);
                if (breakTarget != null && !visited.contains(breakTarget)) {
                    result.addAll(generateStatements(breakTarget, visited, loopHeaders, ifConditions, blockStarts, labeledBreakEdges, loopsNeedingLabels, currentLoop, currentBlock, stopAt, switchStarts));
                }
                return result;
            } else if (node.succs.size() == 1) {
                // Do-while style: header has only 1 successor, output the header as a statement
                loopBody.add(new ExpressionStatement(node));
            }
            
            // Generate body of the loop
            if (loopContinue != null) {
                Set<Node> loopVisited = new HashSet<>();
                loopVisited.add(node); // Don't revisit header
                loopBody.addAll(generateStatementsInLoop(loopContinue, loopVisited, loopHeaders, ifConditions, labeledBreakEdges, blockStarts, loopsNeedingLabels, loop, currentBlock, switchStarts));
            }
            
            // Determine loop label - always get loopLabelId, but only show label if needed
            String loopLabel = loopsNeedingLabels.contains(node) ? getLoopLabel(node) : null;
            int loopLabelId = getLoopLabelId(node);
            result.add(new LoopStatement(loopLabel, loopLabelId, loopBody));
            
            // Continue after the loop
            if (loopExit != null) {
                result.addAll(generateStatements(loopExit, visited, loopHeaders, ifConditions, blockStarts, labeledBreakEdges, loopsNeedingLabels, currentLoop, currentBlock, stopAt, switchStarts));
            } else {
                // No natural exit from loop header, but there may be breaks - output the common break target
                Node breakTarget = findCommonBreakTarget(loop);
                if (breakTarget != null && !visited.contains(breakTarget)) {
                    result.addAll(generateStatements(breakTarget, visited, loopHeaders, ifConditions, blockStarts, labeledBreakEdges, loopsNeedingLabels, currentLoop, currentBlock, stopAt, switchStarts));
                }
            }
            return result;
        }
        
        // Check if this is an if condition
        IfStructure ifStruct = ifConditions.get(node);
        if (ifStruct != null) {
            // A) If true branch is empty (goes directly to merge) but false has content, negate condition
            // Rule: merge on 1st branch => if (!myif) { branch2; }
            boolean trueIsEmpty = ifStruct.trueBranch.equals(ifStruct.mergeNode);
            boolean falseIsEmpty = ifStruct.falseBranch.equals(ifStruct.mergeNode);
            
            if (trueIsEmpty && !falseIsEmpty) {
                // Negate condition: if (cond) {} else { X } -> if (!cond) { X }
                Set<Node> falseVisited = new HashSet<>(visited);
                List<Statement> onTrue = generateStatements(ifStruct.falseBranch, falseVisited, loopHeaders, ifConditions, blockStarts, labeledBreakEdges, loopsNeedingLabels, currentLoop, currentBlock, ifStruct.mergeNode, switchStarts);
                result.add(new IfStatement(node, true, onTrue));
                
                if (ifStruct.mergeNode != null) {
                    visited.addAll(falseVisited);
                    result.addAll(generateStatements(ifStruct.mergeNode, visited, loopHeaders, ifConditions, blockStarts, labeledBreakEdges, loopsNeedingLabels, currentLoop, currentBlock, stopAt, switchStarts));
                }
                return result;
            }
            
            // B) If false branch is empty (goes directly to merge) but true has content, NO negation
            // Rule: merge on 2nd branch => if (myif) { branch1; }
            if (falseIsEmpty && !trueIsEmpty) {
                // No negation: if (cond) { X }
                Set<Node> trueVisited = new HashSet<>(visited);
                List<Statement> onTrue = generateStatements(ifStruct.trueBranch, trueVisited, loopHeaders, ifConditions, blockStarts, labeledBreakEdges, loopsNeedingLabels, currentLoop, currentBlock, ifStruct.mergeNode, switchStarts);
                result.add(new IfStatement(node, false, onTrue));
                
                if (ifStruct.mergeNode != null) {
                    visited.addAll(trueVisited);
                    result.addAll(generateStatements(ifStruct.mergeNode, visited, loopHeaders, ifConditions, blockStarts, labeledBreakEdges, loopsNeedingLabels, currentLoop, currentBlock, stopAt, switchStarts));
                }
                return result;
            }
            
            // Standard if-else - negate condition and swap branches so falseBranch content becomes 'then'
            // Rule: merge on neither branch => if (!myif) { branch2; } else { branch1; }
            Set<Node> falseVisited = new HashSet<>(visited);
            List<Statement> onTrue = generateStatements(ifStruct.falseBranch, falseVisited, loopHeaders, ifConditions, blockStarts, labeledBreakEdges, loopsNeedingLabels, currentLoop, currentBlock, ifStruct.mergeNode, switchStarts);
            
            Set<Node> trueVisited = new HashSet<>(visited);
            List<Statement> onFalse = generateStatements(ifStruct.trueBranch, trueVisited, loopHeaders, ifConditions, blockStarts, labeledBreakEdges, loopsNeedingLabels, currentLoop, currentBlock, ifStruct.mergeNode, switchStarts);
            
            result.add(new IfStatement(node, true, onTrue, onFalse));
            
            // Continue after merge
            if (ifStruct.mergeNode != null) {
                visited.addAll(trueVisited);
                visited.addAll(falseVisited);
                result.addAll(generateStatements(ifStruct.mergeNode, visited, loopHeaders, ifConditions, blockStarts, labeledBreakEdges, loopsNeedingLabels, currentLoop, currentBlock, stopAt, switchStarts));
            }
            return result;
        }
        
        // Regular node - just output it
        result.add(new ExpressionStatement(node));
        
        // Check if this node has a labeled break (non-conditional node)
        LabeledBreakEdge regularNodeLabeledBreak = labeledBreakEdges.get(node);
        if (regularNodeLabeledBreak != null && currentBlock != null) {
            // Use unlabeled break when breaking out of immediately enclosing block
            if (regularNodeLabeledBreak.label.equals(currentBlock.label)) {
                result.add(new BreakStatement(currentBlock.labelId));
            } else {
                result.add(new BreakStatement(regularNodeLabeledBreak.label, regularNodeLabeledBreak.labelId));
            }
            return result;
        }
        
        // Continue with successors
        for (Node succ : node.succs) {
            result.addAll(generateStatements(succ, visited, loopHeaders, ifConditions, blockStarts, labeledBreakEdges, loopsNeedingLabels, currentLoop, currentBlock, stopAt, switchStarts));
        }
        
        return result;
    }
    
    private List<Statement> generateStatementsInLoop(Node node, Set<Node> visited,
                                           Map<Node, LoopStructure> loopHeaders, Map<Node, IfStructure> ifConditions,
                                           Map<Node, LabeledBreakEdge> labeledBreakEdges,
                                           Map<Node, LabeledBlockStructure> blockStarts,
                                           Set<Node> loopsNeedingLabels,
                                           LoopStructure currentLoop, LabeledBlockStructure currentBlock,
                                           Map<Node, SwitchStructure> switchStarts) {
        return generateStatementsInLoop(node, visited, loopHeaders, ifConditions, labeledBreakEdges, blockStarts, loopsNeedingLabels, currentLoop, currentBlock, null, switchStarts);
    }
    
    private List<Statement> generateStatementsInLoop(Node node, Set<Node> visited,
                                           Map<Node, LoopStructure> loopHeaders, Map<Node, IfStructure> ifConditions,
                                           Map<Node, LabeledBreakEdge> labeledBreakEdges,
                                           Map<Node, LabeledBlockStructure> blockStarts,
                                           Set<Node> loopsNeedingLabels,
                                           LoopStructure currentLoop, LabeledBlockStructure currentBlock, Node stopAt,
                                           Map<Node, SwitchStructure> switchStarts) {
        List<Statement> result = new ArrayList<>();
        
        if (node == null || visited.contains(node)) {
            return result;
        }
        
        // Stop at merge node if specified
        if (stopAt != null && node.equals(stopAt)) {
            return result;
        }
        
        // Don't go outside the loop
        if (!currentLoop.body.contains(node)) {
            return result;
        }
        
        // Stop at the current block's end node (we'll process it after the block closes)
        if (currentBlock != null && node.equals(currentBlock.endNode)) {
            return result;
        }
        
        // Check if this is a switch start node (before checking labeled blocks and if-conditions)
        SwitchStructure switchStruct = switchStarts != null ? switchStarts.get(node) : null;
        if (switchStruct != null && switchStruct.mergeNode != null && currentLoop.body.contains(switchStruct.mergeNode)) {
            // Check if we need a labeled block around the switch (for cases that skip the merge)
            boolean needsOuterBlock = switchStruct.hasOuterMerge() && 
                                      currentLoop.body.contains(switchStruct.outerMergeNode);
            String outerBlockLabel = null;
            int outerBlockLabelId = -1;
            
            if (needsOuterBlock) {
                // Check if there's already a labeled block that matches
                LabeledBlockStructure existingBlock = null;
                for (LabeledBlockStructure block : labeledBlocks) {
                    if (block.startNode.equals(node) && block.endNode.equals(switchStruct.outerMergeNode)) {
                        existingBlock = block;
                        break;
                    }
                }
                if (existingBlock != null) {
                    // Use the existing block's label and ID directly
                    outerBlockLabel = existingBlock.label;
                    outerBlockLabelId = existingBlock.labelId;
                } else {
                    outerBlockLabelId = globalLabelCounter++;
                    outerBlockLabel = "block_" + outerBlockLabelId;
                }
            }
            
            // Generate switch statement inside the loop
            List<SwitchStatement.Case> switchCases = new ArrayList<>();
            
            // Collect all case body nodes and the next case body for each (for fall-through detection)
            Map<Node, Node> caseBodyToNextBody = new HashMap<>();
            for (int i = 0; i < switchStruct.cases.size(); i++) {
                SwitchCase sc = switchStruct.cases.get(i);
                if (sc.caseBody != null) {
                    Node nextBody = null;
                    for (int j = i + 1; j < switchStruct.cases.size(); j++) {
                        if (switchStruct.cases.get(j).caseBody != null) {
                            nextBody = switchStruct.cases.get(j).caseBody;
                            break;
                        }
                    }
                    caseBodyToNextBody.put(sc.caseBody, nextBody);
                }
            }
            
            for (int i = 0; i < switchStruct.cases.size(); i++) {
                SwitchCase sc = switchStruct.cases.get(i);
                boolean isLastCase = (i == switchStruct.cases.size() - 1);
                List<Statement> caseBody = new ArrayList<>();
                
                // Generate full case body content
                if (sc.caseBody != null) {
                    Set<Node> caseVisited = new HashSet<>();
                    // Determine stop node based on case type
                    // For fall-through cases (hasBreak=false), always use next case body as stop
                    // Even if skipsMerge is true - the skip is handled internally, case still falls through
                    Node stopNode;
                    if (!sc.hasBreak) {
                        // Fall-through case - stop at next case body or merge
                        stopNode = caseBodyToNextBody.get(sc.caseBody);
                        if (stopNode == null) {
                            stopNode = switchStruct.mergeNode;
                        }
                    } else if (sc.skipsMerge && needsOuterBlock) {
                        stopNode = switchStruct.outerMergeNode;
                    } else {
                        stopNode = switchStruct.mergeNode;
                    }
                    
                    // Special handling for fall-through cases with conditional body that can skip to outer merge
                    // Check if the case body is a conditional where one branch goes to outer merge
                    IfStructure caseBodyIf = ifConditions.get(sc.caseBody);
                    boolean needsSpecialSkipHandling = !sc.hasBreak && needsOuterBlock && 
                                                       caseBodyIf != null &&
                                                       (caseBodyIf.falseBranch.equals(switchStruct.outerMergeNode) ||
                                                        caseBodyIf.trueBranch.equals(switchStruct.outerMergeNode));
                    
                    if (needsSpecialSkipHandling) {
                        // Generate: if (condition is skip) { break block } else_content
                        boolean skipOnTrue = caseBodyIf.trueBranch.equals(switchStruct.outerMergeNode);
                        
                        // Generate the break-to-outer-block if statement
                        List<Statement> breakBody = new ArrayList<>();
                        breakBody.add(new BreakStatement(outerBlockLabel, outerBlockLabelId));
                        // If skip is on true branch, negate condition (if !cond { break })
                        // If skip is on false branch, use condition as-is (if cond { break })
                        caseBody.add(new IfStatement(sc.caseBody, !skipOnTrue, breakBody));
                        
                        // Generate the continuation path (the non-skip branch)
                        Node continuationNode = skipOnTrue ? caseBodyIf.falseBranch : caseBodyIf.trueBranch;
                        caseVisited.add(sc.caseBody);
                        List<Statement> continuationStatements = generateStatementsInLoop(continuationNode, caseVisited, loopHeaders, ifConditions, labeledBreakEdges, blockStarts, loopsNeedingLabels, currentLoop, currentBlock, stopNode, switchStarts);
                        caseBody.addAll(continuationStatements);
                    } else {
                        List<Statement> bodyStatements = generateStatementsInLoop(sc.caseBody, caseVisited, loopHeaders, ifConditions, labeledBreakEdges, blockStarts, loopsNeedingLabels, currentLoop, currentBlock, stopNode, switchStarts);
                        caseBody.addAll(bodyStatements);
                    }
                }
                
                // Add break statement based on case type.
                // Don't add break for the last case - execution naturally exits the switch.
                if (sc.hasBreak && !isLastCase) {
                    if (sc.skipsMerge && needsOuterBlock) {
                        // Case skips merge - use labeled break to outer block
                        caseBody.add(new BreakStatement(outerBlockLabel, outerBlockLabelId));
                    } else {
                        // Normal switch break
                        caseBody.add(new BreakStatement(getSwitchLabelId(node)));
                    }
                }
                // If hasBreak is false, case falls through (no break added)
                
                if (sc.isDefault) {
                    switchCases.add(new SwitchStatement.Case(caseBody));
                } else {
                    switchCases.add(new SwitchStatement.Case(sc.conditionNode.getLabel(), sc.conditionNode, sc.negated, caseBody));
                }
            }
            
            // Build the switch statement
            List<Statement> switchAndMerge = new ArrayList<>();
            switchAndMerge.add(new SwitchStatement(switchCases, getSwitchLabelId(node)));
            
            // Mark all switch condition nodes as visited
            for (SwitchCase sc : switchStruct.cases) {
                if (sc.conditionNode != null) {
                    visited.add(sc.conditionNode);
                }
            }
            
            // Add merge node content after the switch
            Node effectiveStopAt = needsOuterBlock ? switchStruct.outerMergeNode : stopAt;
            switchAndMerge.addAll(generateStatementsInLoop(switchStruct.mergeNode, visited, loopHeaders, ifConditions, labeledBreakEdges, blockStarts, loopsNeedingLabels, currentLoop, currentBlock, effectiveStopAt, switchStarts));
            
            if (needsOuterBlock) {
                // Wrap switch + merge in a labeled block
                result.add(new BlockStatement(outerBlockLabel, outerBlockLabelId, switchAndMerge));
                
                // Continue after the outer merge
                result.addAll(generateStatementsInLoop(switchStruct.outerMergeNode, visited, loopHeaders, ifConditions, labeledBreakEdges, blockStarts, loopsNeedingLabels, currentLoop, currentBlock, stopAt, switchStarts));
            } else {
                result.addAll(switchAndMerge);
            }
            
            return result;
        }
        
        // Check if this node is the start of a try block inside the loop
        GroupedTryStructure groupedTry = findGroupedTryStructureStartingAt(node);
        if (groupedTry != null) {
            // Use the first handler's try body for generating statements
            TryStructure firstTry = groupedTry.catchHandlers.get(0);
            
            // Generate try body statements
            Set<Node> tryVisited = new HashSet<>();
            List<Statement> tryBodyStmts = generateTryBodyStatements(node, firstTry, tryVisited,
                                        loopHeaders, ifConditions, blockStarts, labeledBreakEdges, 
                                        loopsNeedingLabels, currentLoop, currentBlock, switchStarts);
            
            // Generate catch blocks for each handler
            List<TryStatement.CatchBlock> catchBlocks = new ArrayList<>();
            for (TryStructure handler : groupedTry.catchHandlers) {
                Node catchStart = findCatchStartNode(handler);
                Set<Node> catchVisited = new HashSet<>();
                List<Statement> catchBody = new ArrayList<>();
                if (catchStart != null) {
                    // Generate catch body statements - catch blocks may have continue to loop header
                    catchBody = generateCatchBodyInLoop(catchStart, handler.catchBody, catchVisited,
                                                loopHeaders, ifConditions, blockStarts, labeledBreakEdges,
                                                loopsNeedingLabels, currentLoop, currentBlock, switchStarts);
                }
                catchBlocks.add(new TryStatement.CatchBlock(handler.exceptionIndex, catchBody));
                
                // Mark catch nodes as visited
                visited.addAll(handler.catchBody);
            }
            
            result.add(TryStatement.withMultipleCatch(tryBodyStmts, catchBlocks));
            
            // Mark all try nodes as visited
            visited.addAll(groupedTry.tryBody);
            
            // Find the merge node (node after try-catch) and continue in the loop
            Node tryCatchMerge = findTryCatchMergeNode(firstTry);
            if (tryCatchMerge != null && currentLoop.body.contains(tryCatchMerge) && !visited.contains(tryCatchMerge)) {
                result.addAll(generateStatementsInLoop(tryCatchMerge, visited, loopHeaders, ifConditions,
                               labeledBreakEdges, blockStarts, loopsNeedingLabels, currentLoop, currentBlock, stopAt, switchStarts));
            }
            
            return result;
        }
        
        // Check if this is a labeled block start (before marking as visited)
        // Only render the block if there are actual breaks targeting it
        LabeledBlockStructure block = blockStarts.get(node);
        if (block != null && currentBlock != block && !block.breaks.isEmpty()) {
            // Generate body of the block within the loop
            Set<Node> blockVisited = new HashSet<>();
            List<Statement> blockBody = generateStatementsInLoop(node, blockVisited, loopHeaders, ifConditions, 
                                     labeledBreakEdges, blockStarts, loopsNeedingLabels, currentLoop, block, switchStarts);
            
            result.add(new BlockStatement(block.label, block.labelId, blockBody));
            
            // Continue after the block (with the end node)
            visited.addAll(blockVisited);
            if (currentLoop.body.contains(block.endNode)) {
                result.addAll(generateStatementsInLoop(block.endNode, visited, loopHeaders, ifConditions, 
                                        labeledBreakEdges, blockStarts, loopsNeedingLabels, currentLoop, null, switchStarts));
            }
            return result;
        }
        
        // Check if this is a nested loop header (before checking breaks/continues)
        LoopStructure nestedLoop = loopHeaders.get(node);
        if (nestedLoop != null && nestedLoop != currentLoop) {
            visited.add(node);
            Node loopContinue = null;
            Node loopExit = null;
            for (Node succ : node.succs) {
                if (nestedLoop.body.contains(succ) && !succ.equals(node)) {
                    loopContinue = succ;
                } else if (!nestedLoop.body.contains(succ)) {
                    loopExit = succ;
                }
            }
            
            // Build loop body
            List<Statement> loopBody = new ArrayList<>();
            
            // If header has 2 successors (condition check), output the break condition first
            if (loopExit != null && node.succs.size() == 2) {
                List<Statement> breakBody = new ArrayList<>();
                breakBody.add(new BreakStatement(getLoopLabelId(node)));
                // Check if exit is on first edge (true branch) or second edge (false branch)
                boolean exitOnTrueBranch = node.succs.get(0).equals(loopExit);
                // If exit is on true branch, condition is NOT negated: if (cond) { break; }
                // If exit is on false branch, condition IS negated: if (!cond) { break; }
                loopBody.add(new IfStatement(node, !exitOnTrueBranch, breakBody));
            } else if (node.succs.size() == 1) {
                // Unconditional loop header - output the header as a statement
                loopBody.add(new ExpressionStatement(node));
            }
            
            if (loopContinue != null) {
                Set<Node> nestedVisited = new HashSet<>();
                nestedVisited.add(node);
                loopBody.addAll(generateStatementsInLoop(loopContinue, nestedVisited, loopHeaders, ifConditions, labeledBreakEdges, blockStarts, loopsNeedingLabels, nestedLoop, currentBlock, switchStarts));
            }
            
            // Determine loop label - always get loopLabelId, but only show label if needed
            String loopLabel = loopsNeedingLabels.contains(node) ? getLoopLabel(node) : null;
            int loopLabelId = getLoopLabelId(node);
            result.add(new LoopStatement(loopLabel, loopLabelId, loopBody));
            
            if (loopExit != null && currentLoop.body.contains(loopExit)) {
                result.addAll(generateStatementsInLoop(loopExit, visited, loopHeaders, ifConditions, labeledBreakEdges, blockStarts, loopsNeedingLabels, currentLoop, currentBlock, stopAt, switchStarts));
            } else if (loopExit == null) {
                // No natural exit from header - check for break targets inside outer loop
                // Find the most common break target that's inside the outer loop
                Node breakContinuation = null;
                for (BreakEdge breakEdge : nestedLoop.breaks) {
                    if (currentLoop.body.contains(breakEdge.to)) {
                        if (breakContinuation == null) {
                            breakContinuation = breakEdge.to;
                        }
                        // Use the first one found (could improve to find most common)
                    }
                }
                if (breakContinuation != null && !visited.contains(breakContinuation)) {
                    result.addAll(generateStatementsInLoop(breakContinuation, visited, loopHeaders, ifConditions, labeledBreakEdges, blockStarts, loopsNeedingLabels, currentLoop, currentBlock, stopAt, switchStarts));
                }
            }
            return result;
        }
        
        // Check for labeled break first (higher priority than regular break)
        LabeledBreakEdge labeledBreak = labeledBreakEdges.get(node);
        // Skip labeled break if target is the stopAt node (handled by caller, e.g., switch case)
        // BUT: don't skip if we're inside a labeled block and the break targets the block's end node.
        // These breaks need explicit break statements to exit the block, even though they target
        // the stopAt node - without the break statement, control would incorrectly fall through.
        if (labeledBreak != null && stopAt != null && labeledBreak.to.equals(stopAt)) {
            boolean isBreakToCurrentBlock = currentBlock != null && labeledBreak.to.equals(currentBlock.endNode);
            if (!isBreakToCurrentBlock) {
                labeledBreak = null; // Treat as regular node
            }
        }
        if (labeledBreak != null) {
            IfStructure ifStruct = ifConditions.get(node);
            if (ifStruct != null) {
                // Determine which branch is the labeled break
                boolean breakOnTrue = ifStruct.trueBranch.equals(labeledBreak.to);
                boolean breakOnFalse = ifStruct.falseBranch.equals(labeledBreak.to);
                
                // Use unlabeled break when breaking out of immediately enclosing block
                boolean useUnlabeledBreak = currentBlock != null && labeledBreak.label.equals(currentBlock.label);
                
                // A) & B) Apply optimizations: negate and flatten
                if (breakOnTrue) {
                    // B) True branch is break - output break first with condition, then flatten false branch
                    List<Statement> breakBody = new ArrayList<>();
                    breakBody.add(useUnlabeledBreak ? new BreakStatement(currentBlock.labelId) : new BreakStatement(labeledBreak.label, labeledBreak.labelId));
                    result.add(new IfStatement(node, false, breakBody));
                    
                    Set<Node> elseVisited = new HashSet<>(visited);
                    elseVisited.add(node);
                    // Check if false branch goes outside the loop (break to outer loop)
                    if (!currentLoop.body.contains(ifStruct.falseBranch)) {
                        result.addAll(generateBreakOrNodeStatements(ifStruct.falseBranch, loopHeaders, currentLoop));
                    } else {
                        result.addAll(generateStatementsInLoop(ifStruct.falseBranch, elseVisited, loopHeaders, ifConditions, labeledBreakEdges, blockStarts, loopsNeedingLabels, currentLoop, currentBlock, stopAt, switchStarts));
                    }
                } else if (breakOnFalse) {
                    // A) False branch is break - negate condition and flatten
                    List<Statement> breakBody = new ArrayList<>();
                    breakBody.add(useUnlabeledBreak ? new BreakStatement(currentBlock.labelId) : new BreakStatement(labeledBreak.label, labeledBreak.labelId));
                    result.add(new IfStatement(node, true, breakBody));
                    
                    Set<Node> thenVisited = new HashSet<>(visited);
                    thenVisited.add(node);
                    // Check if true branch goes outside the loop (break to outer loop)
                    if (!currentLoop.body.contains(ifStruct.trueBranch)) {
                        result.addAll(generateBreakOrNodeStatements(ifStruct.trueBranch, loopHeaders, currentLoop));
                    } else {
                        result.addAll(generateStatementsInLoop(ifStruct.trueBranch, thenVisited, loopHeaders, ifConditions, labeledBreakEdges, blockStarts, loopsNeedingLabels, currentLoop, currentBlock, stopAt, switchStarts));
                    }
                } else {
                    // Neither branch is the direct labeled break target, continue normally with standard if-else
                    List<Statement> onTrue = new ArrayList<>();
                    Set<Node> thenVisited = new HashSet<>(visited);
                    thenVisited.add(node);
                    // Check if true branch goes outside the loop
                    if (!currentLoop.body.contains(ifStruct.trueBranch)) {
                        onTrue.addAll(generateBreakOrNodeStatements(ifStruct.trueBranch, loopHeaders, currentLoop));
                    } else {
                        onTrue.addAll(generateStatementsInLoop(ifStruct.trueBranch, thenVisited, loopHeaders, ifConditions, labeledBreakEdges, blockStarts, loopsNeedingLabels, currentLoop, currentBlock, stopAt, switchStarts));
                    }
                    
                    List<Statement> onFalse = new ArrayList<>();
                    Set<Node> elseVisited = new HashSet<>(visited);
                    elseVisited.add(node);
                    // Check if false branch goes outside the loop
                    if (!currentLoop.body.contains(ifStruct.falseBranch)) {
                        onFalse.addAll(generateBreakOrNodeStatements(ifStruct.falseBranch, loopHeaders, currentLoop));
                    } else {
                        onFalse.addAll(generateStatementsInLoop(ifStruct.falseBranch, elseVisited, loopHeaders, ifConditions, labeledBreakEdges, blockStarts, loopsNeedingLabels, currentLoop, currentBlock, stopAt, switchStarts));
                    }
                    
                    result.add(new IfStatement(node, false, onTrue, onFalse));
                }
                return result;
            }
            // Handle non-conditional nodes with labeled breaks
            // This is for simple statements that lead directly to a block's end node
            if (node.succs.size() == 1 && node.succs.get(0).equals(labeledBreak.to)) {
                visited.add(node);
                // Output the node as a statement, then break to the block
                result.add(new ExpressionStatement(node));
                // Use unlabeled break when breaking out of immediately enclosing block
                boolean useUnlabeledBreak = currentBlock != null && labeledBreak.label.equals(currentBlock.label);
                if (useUnlabeledBreak) {
                    result.add(new BreakStatement(currentBlock.labelId));
                } else {
                    result.add(new BreakStatement(labeledBreak.label, labeledBreak.labelId));
                }
                return result;
            }
        }
        
        // Check for break
        for (BreakEdge breakEdge : currentLoop.breaks) {
            if (breakEdge.from.equals(node)) {
                // This node has a break
                IfStructure ifStruct = ifConditions.get(node);
                if (ifStruct != null) {
                    // Check if true branch leads to a break (possibly through intermediate nodes)
                    BranchTargetResult trueBranchTarget = findBranchTarget(ifStruct.trueBranch, currentLoop, ifConditions, loopHeaders);
                    // Check if false branch leads to a break (possibly through intermediate nodes)
                    BranchTargetResult falseBranchTarget = findBranchTarget(ifStruct.falseBranch, currentLoop, ifConditions, loopHeaders);
                    
                    // Check if true branch is empty (loop header = continue) and false branch has break
                    boolean trueBranchIsLoopHeader = ifStruct.trueBranch.equals(currentLoop.header);
                    boolean falseBranchIsLoopHeader = ifStruct.falseBranch.equals(currentLoop.header);
                    
                    // A) If true branch is continue (loop header) and false branch is break, negate condition
                    if (trueBranchIsLoopHeader && falseBranchTarget != null) {
                        // Check if false branch goes directly to a break target that will be output after the loop
                        // In this case, we should just output "break;" without any intermediate nodes
                        Node commonBreakTarget = findCommonBreakTarget(currentLoop);
                        boolean falseIsDirect = commonBreakTarget != null && ifStruct.falseBranch.equals(commonBreakTarget);
                        
                        List<Node> falsePath;
                        if (falseIsDirect) {
                            // False branch goes directly to the common break target - no path needed
                            falsePath = new ArrayList<>();
                        } else {
                            falsePath = findPathToTarget(ifStruct.falseBranch, falseBranchTarget.target, ifConditions);
                        }
                        List<Statement> breakBody = outputPathAndBreakStatements(falsePath, falseBranchTarget.breakLabel, falseBranchTarget.breakLabelId, currentLoop, currentBlock, falseBranchTarget.target);
                        result.add(new IfStatement(node, true, breakBody));
                        return result;
                    }
                    
                    // A) If false branch is continue (loop header) and true branch is break, keep normal
                    if (falseBranchIsLoopHeader && trueBranchTarget != null) {
                        List<Node> path = findPathToTarget(ifStruct.trueBranch, trueBranchTarget.target, ifConditions);
                        List<Statement> breakBody = outputPathAndBreakStatements(path, trueBranchTarget.breakLabel, trueBranchTarget.breakLabelId, currentLoop, currentBlock, trueBranchTarget.target);
                        result.add(new IfStatement(node, false, breakBody));
                        return result;
                    }
                    
                    // C) Special case: true branch breaks to labeled block, false branch breaks to loop
                    if (trueBranchTarget != null && trueBranchTarget.isLabeledBlockBreak && 
                        falseBranchTarget != null && !falseBranchTarget.isLabeledBlockBreak) {
                        List<Node> falsePath = findPathToTarget(ifStruct.falseBranch, falseBranchTarget.target, ifConditions);
                        List<Statement> breakBody = outputPathAndBreakStatements(falsePath, falseBranchTarget.breakLabel, falseBranchTarget.breakLabelId, currentLoop, currentBlock, falseBranchTarget.target);
                        result.add(new IfStatement(node, true, breakBody));
                        // Continue with true branch content at same indent level (flattened), without the final break
                        List<Node> truePath = findPathToTarget(ifStruct.trueBranch, trueBranchTarget.target, ifConditions);
                        for (Node n : truePath) {
                            result.add(new ExpressionStatement(n));
                        }
                        return result;
                    }
                    
                    // B) If true branch leads to break, flatten the else
                    if (trueBranchTarget != null && !trueBranchTarget.isContinue) {
                        // Only include nodes that are INSIDE the loop in the path
                        // Nodes outside the loop will be output after the loop ends
                        List<Node> path = findPathToTargetInLoop(ifStruct.trueBranch, trueBranchTarget.target, ifConditions, currentLoop.body);
                        // Use the break label from trueBranchTarget, which is computed by findBranchTarget
                        // This correctly handles breaks to outer loops and labeled blocks
                        String breakLabel = trueBranchTarget.breakLabel;
                        int breakLabelId = trueBranchTarget.breakLabelId;
                        // Only override if no label was provided and we need one for labeled block breaks
                        if ((breakLabel == null || breakLabel.isEmpty()) && !currentLoop.body.contains(trueBranchTarget.target)) {
                            // Target is outside the loop - check if we need a loop label
                            breakLabel = loopsNeedingLabels.contains(currentLoop.header) ? getLoopLabel(currentLoop.header) : null;
                            breakLabelId = getLoopLabelId(currentLoop.header);
                        }
                        List<Statement> breakBody = outputPathAndBreakStatements(path, breakLabel, breakLabelId, currentLoop, currentBlock, trueBranchTarget.target);
                        result.add(new IfStatement(node, false, breakBody));
                        
                        // Continue with false branch at same indent level (flattened)
                        if (falseBranchTarget != null) {
                            // Check if false branch target is the stopAt node (switch merge) - just output path, no break
                            if (stopAt != null && falseBranchTarget.target.equals(stopAt)) {
                                // Don't output break - it's handled by switch case
                                List<Node> falsePath = findPathToTarget(ifStruct.falseBranch, falseBranchTarget.target, ifConditions);
                                for (Node n : falsePath) {
                                    result.add(new ExpressionStatement(n));
                                }
                            } else {
                                // Use loop break label when target is outside the loop
                                String falseBreakLabel = falseBranchTarget.breakLabel;
                                int falseBreakLabelId = falseBranchTarget.breakLabelId;
                                if (!currentLoop.body.contains(falseBranchTarget.target)) {
                                    falseBreakLabel = loopsNeedingLabels.contains(currentLoop.header) ? getLoopLabel(currentLoop.header) : null;
                                    falseBreakLabelId = getLoopLabelId(currentLoop.header);
                                }
                                List<Node> falsePath = findPathToTarget(ifStruct.falseBranch, falseBranchTarget.target, ifConditions);
                                result.addAll(outputPathAndBreakStatements(falsePath, falseBreakLabel, falseBreakLabelId, currentLoop, currentBlock, falseBranchTarget.target));
                            }
                        } else {
                            Set<Node> falseVisited = new HashSet<>(visited);
                            falseVisited.add(node);
                            result.addAll(generateStatementsInLoop(ifStruct.falseBranch, falseVisited, loopHeaders, ifConditions, labeledBreakEdges, blockStarts, loopsNeedingLabels, currentLoop, currentBlock, stopAt, switchStarts));
                        }
                        return result;
                    }
                    
                    // B2) If true branch leads to continue, flatten the else
                    // Standard handling - false branch is break, true branch continues
                    // This takes PRIORITY over continue handling to prefer: if (!cond) { break; } body
                    // over: if (cond) { body; continue; }
                    if (falseBranchTarget != null && !falseBranchTarget.isContinue) {
                        // Only include nodes that are INSIDE the loop in the path
                        // Nodes outside the loop will be output after the loop ends
                        List<Node> falsePath = findPathToTargetInLoop(ifStruct.falseBranch, falseBranchTarget.target, ifConditions, currentLoop.body);
                        List<Statement> breakBody = outputPathAndBreakStatements(falsePath, falseBranchTarget.breakLabel, falseBranchTarget.breakLabelId, currentLoop, currentBlock, falseBranchTarget.target);
                        result.add(new IfStatement(node, true, breakBody));  // negated condition
                        // Continue with true branch at same indent level (flattened)
                        Set<Node> thenVisited = new HashSet<>(visited);
                        thenVisited.add(node);
                        result.addAll(generateStatementsInLoop(ifStruct.trueBranch, thenVisited, loopHeaders, ifConditions, labeledBreakEdges, blockStarts, loopsNeedingLabels, currentLoop, currentBlock, stopAt, switchStarts));
                        return result;
                    }
                    
                    if (trueBranchTarget != null && trueBranchTarget.isContinue) {
                        List<Node> path = findPathToTarget(ifStruct.trueBranch, trueBranchTarget.target, ifConditions);
                        List<Statement> continueBody = outputPathAndContinueStatements(path, trueBranchTarget.breakLabel, trueBranchTarget.breakLabelId, currentLoop);
                        result.add(new IfStatement(node, false, continueBody));
                        
                        // Continue with false branch at same indent level (flattened)
                        if (falseBranchTarget != null && !falseBranchTarget.isContinue) {
                            // Check if false branch target is the stopAt node (switch merge) - just output path, no break
                            if (stopAt != null && falseBranchTarget.target.equals(stopAt)) {
                                List<Node> falsePath = findPathToTarget(ifStruct.falseBranch, falseBranchTarget.target, ifConditions);
                                for (Node n : falsePath) {
                                    result.add(new ExpressionStatement(n));
                                }
                            } else {
                                List<Node> falsePath = findPathToTarget(ifStruct.falseBranch, falseBranchTarget.target, ifConditions);
                                result.addAll(outputPathAndBreakStatements(falsePath, falseBranchTarget.breakLabel, falseBranchTarget.breakLabelId, currentLoop, currentBlock, falseBranchTarget.target));
                            }
                        } else {
                            Set<Node> falseVisited = new HashSet<>(visited);
                            falseVisited.add(node);
                            result.addAll(generateStatementsInLoop(ifStruct.falseBranch, falseVisited, loopHeaders, ifConditions, labeledBreakEdges, blockStarts, loopsNeedingLabels, currentLoop, currentBlock, stopAt, switchStarts));
                        }
                        return result;
                    }
                    
                    // Fallback when only false branch has break (no continue in true branch)
                    // Fallback to original logic
                    if (!currentLoop.body.contains(ifStruct.trueBranch)) {
                        List<Statement> breakBody = new ArrayList<>();
                        // Use labeled loop break when inside a loop (needed for switches inside loops)
                        String loopLabel = loopsNeedingLabels.contains(currentLoop.header) ? getLoopLabel(currentLoop.header) : null;
                        int loopLabelId = getLoopLabelId(currentLoop.header);
                        breakBody.add(new BreakStatement(loopLabel, loopLabelId));
                        // Output if with break, negate condition so break is on false branch (flatten else)
                        result.add(new IfStatement(node, true, breakBody));
                        // Continue with false branch flattened (at same level, not in else block)
                        Set<Node> elseVisited = new HashSet<>(visited);
                        elseVisited.add(node);
                        result.addAll(generateStatementsInLoop(ifStruct.falseBranch, elseVisited, loopHeaders, ifConditions, labeledBreakEdges, blockStarts, loopsNeedingLabels, currentLoop, currentBlock, stopAt, switchStarts));
                    } else {
                        List<Statement> breakBody = new ArrayList<>();
                        // Use labeled loop break when inside a loop (needed for switches inside loops)
                        String loopLabel = loopsNeedingLabels.contains(currentLoop.header) ? getLoopLabel(currentLoop.header) : null;
                        int loopLabelId = getLoopLabelId(currentLoop.header);
                        breakBody.add(new BreakStatement(loopLabel, loopLabelId));
                        // Output if with break (don't negate - break is on false branch)
                        result.add(new IfStatement(node, false, breakBody));
                        // Continue with true branch flattened (at same level, not in else block)
                        Set<Node> thenVisited = new HashSet<>(visited);
                        thenVisited.add(node);
                        result.addAll(generateStatementsInLoop(ifStruct.trueBranch, thenVisited, loopHeaders, ifConditions, labeledBreakEdges, blockStarts, loopsNeedingLabels, currentLoop, currentBlock, stopAt, switchStarts));
                    }
                    return result;
                }
            }
        }
        
        // Check for continue
        for (ContinueEdge continueEdge : currentLoop.continues) {
            if (continueEdge.from.equals(node)) {
                IfStructure ifStruct = ifConditions.get(node);
                if (ifStruct != null) {
                    // Determine which branch is continue
                    if (ifStruct.trueBranch.equals(currentLoop.header)) {
                        // True branch is continue
                        List<Statement> continueBody = new ArrayList<>();
                        continueBody.add(new ContinueStatement(getLoopLabelId(currentLoop.header)));
                        result.add(new IfStatement(node, false, continueBody));
                        // Continue with false branch flattened
                        Set<Node> elseVisited = new HashSet<>(visited);
                        elseVisited.add(node);
                        result.addAll(generateStatementsInLoop(ifStruct.falseBranch, elseVisited, loopHeaders, ifConditions, labeledBreakEdges, blockStarts, loopsNeedingLabels, currentLoop, currentBlock, stopAt, switchStarts));
                    } else {
                        // False branch is continue - negate condition
                        List<Statement> continueBody = new ArrayList<>();
                        continueBody.add(new ContinueStatement(getLoopLabelId(currentLoop.header)));
                        result.add(new IfStatement(node, true, continueBody));
                        // Continue with true branch flattened
                        Set<Node> thenVisited = new HashSet<>(visited);
                        thenVisited.add(node);
                        result.addAll(generateStatementsInLoop(ifStruct.trueBranch, thenVisited, loopHeaders, ifConditions, labeledBreakEdges, blockStarts, loopsNeedingLabels, currentLoop, currentBlock, stopAt, switchStarts));
                    }
                    return result;
                }
            }
        }
        
        visited.add(node);
        
        // Check if this is an if condition inside the loop
        IfStructure ifStruct = ifConditions.get(node);
        if (ifStruct != null) {
            // Special case: inside a labeled block, when true branch goes directly to an internal
            // merge point and false branch contains a skip to the block's end.
            // Use negated condition to avoid node duplication: if (!cond) { false_branch_with_skip } internal_merge;
            if (currentBlock != null && !currentBlock.breaks.isEmpty()) {
                // Check if true branch goes to an internal node (not the block end)
                // and false branch has a skip pattern to the block end
                Node trueBranch = ifStruct.trueBranch;
                Node falseBranch = ifStruct.falseBranch;
                
                // Find if there's a labeled break in the false branch path
                boolean falseBranchHasSkip = false;
                LabeledBreakEdge falseBreakEdge = null;
                for (LabeledBreakEdge breakEdge : currentBlock.breaks) {
                    if (isReachableWithinLoop(falseBranch, breakEdge.from, currentLoop)) {
                        falseBranchHasSkip = true;
                        falseBreakEdge = breakEdge;
                        break;
                    }
                }
                
                // Check if true branch goes to an internal merge point (not the block end)
                // and both the true branch and the non-skip path of false branch converge there
                if (falseBranchHasSkip && currentBlock.body.contains(trueBranch) && 
                    !trueBranch.equals(currentBlock.endNode)) {
                    // Check if the non-skip paths from false branch also reach trueBranch
                    boolean falseBranchReachesTrueBranch = isReachableWithinLoop(falseBranch, trueBranch, currentLoop);
                    
                    if (falseBranchReachesTrueBranch) {
                        // Use negated condition: if (!cond) { false_branch } trueBranch;
                        List<Statement> onTrue = new ArrayList<>();
                        Set<Node> falseVisited = new HashSet<>(visited);
                        // Process false branch, stopping at trueBranch (internal merge)
                        onTrue.addAll(generateStatementsInLoop(falseBranch, falseVisited, loopHeaders, ifConditions, 
                                      labeledBreakEdges, blockStarts, loopsNeedingLabels, currentLoop, currentBlock, trueBranch, switchStarts));
                        result.add(new IfStatement(node, true, onTrue)); // negated condition
                        
                        // Output the internal merge point (trueBranch) and continue from there
                        Set<Node> mergeVisited = new HashSet<>(visited);
                        mergeVisited.addAll(falseVisited);
                        result.addAll(generateStatementsInLoop(trueBranch, mergeVisited, loopHeaders, ifConditions, 
                                      labeledBreakEdges, blockStarts, loopsNeedingLabels, currentLoop, currentBlock, stopAt, switchStarts));
                        return result;
                    } else if (falseBreakEdge != null && falseBranch.equals(falseBreakEdge.from)) {
                        // False branch is DIRECTLY the skip source (doesn't converge with true branch)
                        // Pattern: if (!cond) { skip_node; break block; } true_branch_content;
                        List<Statement> onTrue = new ArrayList<>();
                        Set<Node> falseVisited = new HashSet<>(visited);
                        // Process false branch (which is just the skip node + break)
                        onTrue.addAll(generateStatementsInLoop(falseBranch, falseVisited, loopHeaders, ifConditions, 
                                      labeledBreakEdges, blockStarts, loopsNeedingLabels, currentLoop, currentBlock, stopAt, switchStarts));
                        result.add(new IfStatement(node, true, onTrue)); // negated condition
                        
                        // Continue with true branch flattened
                        Set<Node> trueVisited = new HashSet<>(visited);
                        trueVisited.add(node);
                        result.addAll(generateStatementsInLoop(trueBranch, trueVisited, loopHeaders, ifConditions, 
                                      labeledBreakEdges, blockStarts, loopsNeedingLabels, currentLoop, currentBlock, stopAt, switchStarts));
                        return result;
                    }
                }
            }
            
            // Check if true branch leads to a break
            BranchTargetResult trueBranchTarget = findBranchTarget(ifStruct.trueBranch, currentLoop, ifConditions, loopHeaders);
            BranchTargetResult falseBranchTarget = findBranchTarget(ifStruct.falseBranch, currentLoop, ifConditions, loopHeaders);
            
            // Special case: if both branches merge at the same node inside the loop,
            // and neither branch is empty, treat as a standard if-else
            // This prevents detecting "continue" when both branches naturally converge
            if (ifStruct.mergeNode != null && currentLoop.body.contains(ifStruct.mergeNode) &&
                !ifStruct.trueBranch.equals(ifStruct.mergeNode) && 
                !ifStruct.falseBranch.equals(ifStruct.mergeNode) &&
                trueBranchTarget != null && falseBranchTarget != null &&
                trueBranchTarget.isContinue && falseBranchTarget.isContinue &&
                trueBranchTarget.target.equals(falseBranchTarget.target)) {
                // Both branches lead to the loop header through the merge node
                // Use standard if-else instead of treating as continue
                trueBranchTarget = null;
                falseBranchTarget = null;
            }
            
            boolean trueIsEmpty = ifStruct.trueBranch.equals(ifStruct.mergeNode) || 
                                  (!currentLoop.body.contains(ifStruct.trueBranch) && trueBranchTarget == null);
            boolean falseIsEmpty = ifStruct.falseBranch.equals(ifStruct.mergeNode) || 
                                   (!currentLoop.body.contains(ifStruct.falseBranch) && falseBranchTarget == null);
            
            // Rule: merge on 1st branch => if (!myif) { branch2; }
            if (trueIsEmpty && !falseIsEmpty) {
                // Negate condition
                List<Statement> onTrue = new ArrayList<>();
                // When the true branch IS the merge point, don't use falseBranchTarget
                // The break should be handled at the merge point, not inside the if-body
                if (falseBranchTarget != null && !ifStruct.trueBranch.equals(ifStruct.mergeNode)) {
                    List<Node> path = findPathToTarget(ifStruct.falseBranch, falseBranchTarget.target, ifConditions);
                    onTrue.addAll(outputPathAndBreakStatements(path, falseBranchTarget.breakLabel, falseBranchTarget.breakLabelId, currentLoop, currentBlock, falseBranchTarget.target));
                } else {
                    Set<Node> falseVisited = new HashSet<>(visited);
                    onTrue.addAll(generateStatementsInLoop(ifStruct.falseBranch, falseVisited, loopHeaders, ifConditions, labeledBreakEdges, blockStarts, loopsNeedingLabels, currentLoop, currentBlock, ifStruct.mergeNode, switchStarts));
                }
                result.add(new IfStatement(node, true, onTrue));
                
                if (ifStruct.mergeNode != null && currentLoop.body.contains(ifStruct.mergeNode)) {
                    Set<Node> mergeVisited = new HashSet<>(visited);
                    result.addAll(generateStatementsInLoop(ifStruct.mergeNode, mergeVisited, loopHeaders, ifConditions, labeledBreakEdges, blockStarts, loopsNeedingLabels, currentLoop, currentBlock, stopAt, switchStarts));
                }
                return result;
            }
            
            // Rule: merge on 2nd branch => if (myif) { branch1; } (NO negation)
            if (falseIsEmpty && !trueIsEmpty) {
                // No negation - output true branch content
                List<Statement> onTrue = new ArrayList<>();
                if (trueBranchTarget != null) {
                    List<Node> path = findPathToTarget(ifStruct.trueBranch, trueBranchTarget.target, ifConditions);
                    if (trueBranchTarget.isContinue) {
                        onTrue.addAll(outputPathAndContinueStatements(path, trueBranchTarget.breakLabel, trueBranchTarget.breakLabelId, currentLoop));
                    } else {
                        onTrue.addAll(outputPathAndBreakStatements(path, trueBranchTarget.breakLabel, trueBranchTarget.breakLabelId, currentLoop, currentBlock, trueBranchTarget.target));
                    }
                } else {
                    Set<Node> trueVisited = new HashSet<>(visited);
                    onTrue.addAll(generateStatementsInLoop(ifStruct.trueBranch, trueVisited, loopHeaders, ifConditions, labeledBreakEdges, blockStarts, loopsNeedingLabels, currentLoop, currentBlock, ifStruct.mergeNode, switchStarts));
                }
                result.add(new IfStatement(node, false, onTrue));  // NO negation
                
                if (ifStruct.mergeNode != null && currentLoop.body.contains(ifStruct.mergeNode)) {
                    Set<Node> mergeVisited = new HashSet<>(visited);
                    result.addAll(generateStatementsInLoop(ifStruct.mergeNode, mergeVisited, loopHeaders, ifConditions, labeledBreakEdges, blockStarts, loopsNeedingLabels, currentLoop, currentBlock, stopAt, switchStarts));
                }
                return result;
            }
            
            // B) If true branch ends with break/continue, flatten the else
            if (trueBranchTarget != null) {
                // C) Special case: both branches lead to breaks with the same target
                if (falseBranchTarget != null && 
                    trueBranchTarget.target.equals(falseBranchTarget.target) &&
                    isReachableWithinLoop(ifStruct.trueBranch, ifStruct.falseBranch, currentLoop)) {
                    List<Node> truePath = findPathToNode(ifStruct.trueBranch, ifStruct.falseBranch, ifConditions, currentLoop);
                    List<Statement> onTrue = new ArrayList<>();
                    for (Node n : truePath) {
                        onTrue.add(new ExpressionStatement(n));
                    }
                    result.add(new IfStatement(node, false, onTrue));
                    List<Node> falsePath = findPathToTarget(ifStruct.falseBranch, falseBranchTarget.target, ifConditions);
                    result.addAll(outputPathAndBreakStatements(falsePath, falseBranchTarget.breakLabel, falseBranchTarget.breakLabelId, currentLoop, currentBlock, falseBranchTarget.target));
                    return result;
                }
                
                // D) Special case: true branch breaks to labeled block, false branch breaks to loop
                if (trueBranchTarget.isLabeledBlockBreak && falseBranchTarget != null && !falseBranchTarget.isLabeledBlockBreak) {
                    List<Node> falsePath = findPathToTarget(ifStruct.falseBranch, falseBranchTarget.target, ifConditions);
                    List<Statement> breakBody = outputPathAndBreakStatements(falsePath, falseBranchTarget.breakLabel, falseBranchTarget.breakLabelId, currentLoop, currentBlock, falseBranchTarget.target);
                    result.add(new IfStatement(node, true, breakBody));
                    List<Node> truePath = findPathToTarget(ifStruct.trueBranch, trueBranchTarget.target, ifConditions);
                    for (Node n : truePath) {
                        result.add(new ExpressionStatement(n));
                    }
                    return result;
                }
                
                // E) Handle continue - true branch leads to loop header
                // But if false branch has a break, prioritize break over continue
                // This produces cleaner: if (!cond) { break; } path_to_continue;
                if (trueBranchTarget.isContinue) {
                    // Check if false branch has break - prioritize break over continue
                    if (falseBranchTarget != null && !falseBranchTarget.isContinue) {
                        // False branch has break - use: if (!cond) { break; } then true_branch
                        List<Node> falsePath = findPathToTarget(ifStruct.falseBranch, falseBranchTarget.target, ifConditions);
                        List<Statement> breakBody = outputPathAndBreakStatements(falsePath, falseBranchTarget.breakLabel, falseBranchTarget.breakLabelId, currentLoop, currentBlock, falseBranchTarget.target);
                        result.add(new IfStatement(node, true, breakBody));  // negated condition
                        // After the if, flatten the true branch (which continues to loop header)
                        List<Node> truePath = findPathToTarget(ifStruct.trueBranch, trueBranchTarget.target, ifConditions);
                        for (Node n : truePath) {
                            result.add(new ExpressionStatement(n));
                        }
                        return result;
                    }
                    
                    // Normal continue handling - no break in false branch
                    List<Node> path = findPathToTarget(ifStruct.trueBranch, trueBranchTarget.target, ifConditions);
                    List<Statement> continueBody = outputPathAndContinueStatements(path, trueBranchTarget.breakLabel, trueBranchTarget.breakLabelId, currentLoop);
                    result.add(new IfStatement(node, false, continueBody));
                    
                    // Continue with false branch flattened
                    if (stopAt != null && (ifStruct.falseBranch.equals(stopAt) || isReachableWithinLoop(ifStruct.falseBranch, stopAt, currentLoop))) {
                        List<Node> pathToStop = findPathToNode(ifStruct.falseBranch, stopAt, ifConditions, currentLoop);
                        for (Node n : pathToStop) {
                            result.add(new ExpressionStatement(n));
                        }
                    } else if (currentLoop.body.contains(ifStruct.falseBranch)) {
                        Set<Node> falseVisited = new HashSet<>(visited);
                        result.addAll(generateStatementsInLoop(ifStruct.falseBranch, falseVisited, loopHeaders, ifConditions, labeledBreakEdges, blockStarts, loopsNeedingLabels, currentLoop, currentBlock, stopAt, switchStarts));
                    }
                    return result;
                }
                
                List<Node> path = findPathToTarget(ifStruct.trueBranch, trueBranchTarget.target, ifConditions);
                List<Statement> breakBody = outputPathAndBreakStatements(path, trueBranchTarget.breakLabel, trueBranchTarget.breakLabelId, currentLoop, currentBlock, trueBranchTarget.target);
                result.add(new IfStatement(node, false, breakBody));
                
                // Continue with false branch
                if (stopAt != null && (ifStruct.falseBranch.equals(stopAt) || isReachableWithinLoop(ifStruct.falseBranch, stopAt, currentLoop))) {
                    List<Node> pathToStop = findPathToNode(ifStruct.falseBranch, stopAt, ifConditions, currentLoop);
                    for (Node n : pathToStop) {
                        result.add(new ExpressionStatement(n));
                    }
                } else if (falseBranchTarget != null) {
                    List<Node> falsePath = findPathToTarget(ifStruct.falseBranch, falseBranchTarget.target, ifConditions);
                    if (falseBranchTarget.isLabeledBlockBreak && currentBlock != null && 
                        falseBranchTarget.target.equals(currentBlock.endNode)) {
                        for (Node n : falsePath) {
                            result.add(new ExpressionStatement(n));
                        }
                    } else if (falseBranchTarget.isContinue) {
                        // False branch is a continue - output continue instead of break
                        result.addAll(outputPathAndContinueStatements(falsePath, falseBranchTarget.breakLabel, falseBranchTarget.breakLabelId, currentLoop));
                    } else {
                        result.addAll(outputPathAndBreakStatements(falsePath, falseBranchTarget.breakLabel, falseBranchTarget.breakLabelId, currentLoop, currentBlock, falseBranchTarget.target));
                    }
                } else if (currentLoop.body.contains(ifStruct.falseBranch)) {
                    Set<Node> falseVisited = new HashSet<>(visited);
                    result.addAll(generateStatementsInLoop(ifStruct.falseBranch, falseVisited, loopHeaders, ifConditions, labeledBreakEdges, blockStarts, loopsNeedingLabels, currentLoop, currentBlock, ifStruct.mergeNode, switchStarts));
                    
                    if (ifStruct.mergeNode != null && currentLoop.body.contains(ifStruct.mergeNode)) {
                        result.addAll(generateStatementsInLoop(ifStruct.mergeNode, falseVisited, loopHeaders, ifConditions, labeledBreakEdges, blockStarts, loopsNeedingLabels, currentLoop, currentBlock, stopAt, switchStarts));
                    }
                }
                return result;
            }
            
            // C) false branch reachable from true branch
            if (trueBranchTarget == null && falseBranchTarget != null &&
                isReachableWithinLoop(ifStruct.trueBranch, ifStruct.falseBranch, currentLoop)) {
                Set<Node> trueVisited = new HashSet<>(visited);
                List<Statement> onTrue = generateStatementsInLoop(ifStruct.trueBranch, trueVisited, loopHeaders, ifConditions, labeledBreakEdges, blockStarts, loopsNeedingLabels, currentLoop, currentBlock, ifStruct.falseBranch, switchStarts);
                result.add(new IfStatement(node, false, onTrue));
                List<Node> falsePath = findPathToTarget(ifStruct.falseBranch, falseBranchTarget.target, ifConditions);
                if (falseBranchTarget.isLabeledBlockBreak && currentBlock != null && 
                    falseBranchTarget.target.equals(currentBlock.endNode)) {
                    for (Node n : falsePath) {
                        result.add(new ExpressionStatement(n));
                    }
                } else {
                    result.addAll(outputPathAndBreakStatements(falsePath, falseBranchTarget.breakLabel, falseBranchTarget.breakLabelId, currentLoop, currentBlock, falseBranchTarget.target));
                }
                return result;
            }
            
            // D) True branch is back-edge source (normal loop continuation) - treat as "merge on 1st branch"
            // Rule: merge on 1st branch => if (!cond) { branch2; } then continue with branch1
            // This handles cases where the first branch leads to normal loop continuation
            if (trueBranchTarget == null && falseBranchTarget == null &&
                ifStruct.trueBranch.equals(currentLoop.backEdgeSource)) {
                // Generate: if (!cond) { falseBranch content } trueBranch;
                Set<Node> falseVisited = new HashSet<>(visited);
                // Use hel2 (the trueBranch/back-edge source) as the stopAt for falseBranch processing
                // This ensures we process ifa8 content up to the point where it would merge back
                List<Statement> onTrue = generateStatementsInLoop(ifStruct.falseBranch, falseVisited, loopHeaders, ifConditions, labeledBreakEdges, blockStarts, loopsNeedingLabels, currentLoop, currentBlock, ifStruct.trueBranch, switchStarts);
                result.add(new IfStatement(node, true, onTrue));  // negated condition
                // After the if, continue with the true branch (back-edge source)
                Set<Node> trueVisited = new HashSet<>(visited);
                trueVisited.add(node);
                result.addAll(generateStatementsInLoop(ifStruct.trueBranch, trueVisited, loopHeaders, ifConditions, labeledBreakEdges, blockStarts, loopsNeedingLabels, currentLoop, currentBlock, stopAt, switchStarts));
                return result;
            }
            
            // E) True branch is reachable from false branch within the loop - use true branch as merge point
            // This handles cases where both branches eventually converge at the true branch node
            // Pattern: if (!cond) { falseBranch content } then trueBranch;
            if (trueBranchTarget == null && falseBranchTarget == null &&
                currentLoop.body.contains(ifStruct.trueBranch) &&
                isReachableWithinLoop(ifStruct.falseBranch, ifStruct.trueBranch, currentLoop)) {
                // Generate: if (!cond) { falseBranch content up to trueBranch } trueBranch;
                Set<Node> falseVisited = new HashSet<>(visited);
                // Use trueBranch as the stopAt for falseBranch processing
                // This ensures we process false branch content up to the merge point
                List<Statement> onTrue = generateStatementsInLoop(ifStruct.falseBranch, falseVisited, loopHeaders, ifConditions, labeledBreakEdges, blockStarts, loopsNeedingLabels, currentLoop, currentBlock, ifStruct.trueBranch, switchStarts);
                result.add(new IfStatement(node, true, onTrue));  // negated condition
                // After the if, continue with the true branch (merge point)
                Set<Node> trueVisited = new HashSet<>(visited);
                trueVisited.add(node);
                result.addAll(generateStatementsInLoop(ifStruct.trueBranch, trueVisited, loopHeaders, ifConditions, labeledBreakEdges, blockStarts, loopsNeedingLabels, currentLoop, currentBlock, stopAt, switchStarts));
                return result;
            }
            
            // F) False branch is a continue - flatten the structure
            // When false branch ends with continue, use simple if (no else)
            // since continue returns to loop header, code after the if only runs when condition is false
            if (falseBranchTarget != null && falseBranchTarget.isContinue && trueBranchTarget == null) {
                // Generate: if (!cond) { continue; } trueBranch;
                List<Node> path = findPathToTarget(ifStruct.falseBranch, falseBranchTarget.target, ifConditions);
                List<Statement> continueBody = outputPathAndContinueStatements(path, falseBranchTarget.breakLabel, falseBranchTarget.breakLabelId, currentLoop);
                result.add(new IfStatement(node, true, continueBody));  // negated condition
                // After the if, continue with the true branch (flattened)
                Set<Node> trueVisited = new HashSet<>(visited);
                trueVisited.add(node);
                result.addAll(generateStatementsInLoop(ifStruct.trueBranch, trueVisited, loopHeaders, ifConditions, labeledBreakEdges, blockStarts, loopsNeedingLabels, currentLoop, currentBlock, stopAt, switchStarts));
                return result;
            }
            
            // Standard if-else - negate condition and swap branches
            // Rule: merge on neither branch => if (!myif) { branch2; } else { branch1; }
            Set<Node> falseVisited = new HashSet<>(visited);
            List<Statement> onTrue = new ArrayList<>();
            if (falseBranchTarget != null) {
                List<Node> path = findPathToTarget(ifStruct.falseBranch, falseBranchTarget.target, ifConditions);
                if (falseBranchTarget.isContinue) {
                    onTrue.addAll(outputPathAndContinueStatements(path, falseBranchTarget.breakLabel, falseBranchTarget.breakLabelId, currentLoop));
                } else {
                    onTrue.addAll(outputPathAndBreakStatements(path, falseBranchTarget.breakLabel, falseBranchTarget.breakLabelId, currentLoop, currentBlock, falseBranchTarget.target));
                }
            } else {
                onTrue.addAll(generateStatementsInLoop(ifStruct.falseBranch, falseVisited, loopHeaders, ifConditions, labeledBreakEdges, blockStarts, loopsNeedingLabels, currentLoop, currentBlock, ifStruct.mergeNode, switchStarts));
            }
            
            Set<Node> trueVisited = new HashSet<>(visited);
            List<Statement> onFalse = generateStatementsInLoop(ifStruct.trueBranch, trueVisited, loopHeaders, ifConditions, labeledBreakEdges, blockStarts, loopsNeedingLabels, currentLoop, currentBlock, ifStruct.mergeNode, switchStarts);
            
            result.add(new IfStatement(node, true, onTrue, onFalse));  // negated condition
            
            if (ifStruct.mergeNode != null && currentLoop.body.contains(ifStruct.mergeNode)) {
                Set<Node> mergeVisited = new HashSet<>(visited);
                result.addAll(generateStatementsInLoop(ifStruct.mergeNode, mergeVisited, loopHeaders, ifConditions, labeledBreakEdges, blockStarts, loopsNeedingLabels, currentLoop, currentBlock, stopAt, switchStarts));
            }
            return result;
        }
        
        // Regular node
        result.add(new ExpressionStatement(node));
        
        // Continue with successors inside the loop
        for (Node succ : node.succs) {
            // Skip if successor is the stopAt node (will be handled by caller)
            if (stopAt != null && succ.equals(stopAt)) {
                continue;
            }
            if (currentLoop.body.contains(succ) && !succ.equals(currentLoop.header)) {
                result.addAll(generateStatementsInLoop(succ, visited, loopHeaders, ifConditions, labeledBreakEdges, blockStarts, loopsNeedingLabels, currentLoop, currentBlock, stopAt, switchStarts));
            }
        }
        
        return result;
    }

    private List<Statement> generateBreakOrNodeStatements(Node node,
                                               Map<Node, LoopStructure> loopHeaders, LoopStructure currentLoop) {
        List<Statement> result = new ArrayList<>();
        
        LoopStructure targetLoop = null;
        for (LoopStructure loop : loopHeaders.values()) {
            if (loop == currentLoop) continue;
            if (loop.body.contains(currentLoop.header) && !loop.body.contains(node)) {
                if (targetLoop == null || loop.body.size() < targetLoop.body.size()) {
                    targetLoop = loop;
                }
            }
        }
        
        if (targetLoop != null) {
            result.add(new BreakStatement(getLoopLabel(targetLoop.header), getLoopLabelId(targetLoop.header)));
        } else {
            result.add(new ExpressionStatement(node));
        }
        
        return result;
    }
    
    /**
     * Replaces break statements with oldLabel to use newLabel.
     * This is used when converting labeled block breaks to switch label breaks.
     */
    private List<Statement> replaceBlockBreaksWithSwitchBreaks(List<Statement> statements, String oldLabel, String newLabel, int newLabelId) {
        List<Statement> result = new ArrayList<>();
        for (Statement stmt : statements) {
            if (stmt instanceof BreakStatement) {
                BreakStatement breakStmt = (BreakStatement) stmt;
                if (breakStmt.getLabel() != null && breakStmt.getLabel().equals(oldLabel)) {
                    result.add(new BreakStatement(newLabel, newLabelId));
                } else {
                    result.add(stmt);
                }
            } else if (stmt instanceof IfStatement) {
                IfStatement ifStmt = (IfStatement) stmt;
                List<Statement> newOnTrue = replaceBlockBreaksWithSwitchBreaks(ifStmt.getOnTrue(), oldLabel, newLabel, newLabelId);
                List<Statement> newOnFalse = replaceBlockBreaksWithSwitchBreaks(ifStmt.getOnFalse(), oldLabel, newLabel, newLabelId);
                result.add(new IfStatement(ifStmt.getConditionNode(), ifStmt.isNegated(), newOnTrue, newOnFalse));
            } else if (stmt instanceof LoopStatement) {
                LoopStatement loopStmt = (LoopStatement) stmt;
                List<Statement> newBody = replaceBlockBreaksWithSwitchBreaks(loopStmt.getBody(), oldLabel, newLabel, newLabelId);
                result.add(new LoopStatement(loopStmt.getLabel(), loopStmt.getLabelId(), newBody));
            } else if (stmt instanceof BlockStatement) {
                BlockStatement blockStmt = (BlockStatement) stmt;
                List<Statement> newBody = replaceBlockBreaksWithSwitchBreaks(blockStmt.getBody(), oldLabel, newLabel, newLabelId);
                result.add(new BlockStatement(blockStmt.getLabel(), blockStmt.getLabelId(), newBody));
            } else if (stmt instanceof TryStatement) {
                TryStatement tryStmt = (TryStatement) stmt;
                List<Statement> newTryBody = replaceBlockBreaksWithSwitchBreaks(tryStmt.getTryBody(), oldLabel, newLabel, newLabelId);
                List<TryStatement.CatchBlock> newCatches = new ArrayList<>();
                for (TryStatement.CatchBlock catchBlock : tryStmt.getCatchBlocks()) {
                    List<Statement> newCatchBody = replaceBlockBreaksWithSwitchBreaks(catchBlock.getBody(), oldLabel, newLabel, newLabelId);
                    newCatches.add(new TryStatement.CatchBlock(catchBlock.getExceptionIndex(), newCatchBody));
                }
                result.add(TryStatement.withMultipleCatch(newTryBody, newCatches));
            } else if (stmt instanceof SwitchStatement) {
                SwitchStatement switchStmt = (SwitchStatement) stmt;
                List<SwitchStatement.Case> newCases = new ArrayList<>();
                for (SwitchStatement.Case caseStmt : switchStmt.getCases()) {
                    List<Statement> newCaseBody = replaceBlockBreaksWithSwitchBreaks(caseStmt.getBody(), oldLabel, newLabel, newLabelId);
                    if (caseStmt.isDefault()) {
                        newCases.add(new SwitchStatement.Case(newCaseBody));
                    } else {
                        newCases.add(new SwitchStatement.Case(caseStmt.getCondition(), caseStmt.getConditionNode(), caseStmt.isNegated(), newCaseBody));
                    }
                }
                result.add(new SwitchStatement(newCases, switchStmt.getLabel(), switchStmt.getLabelId()));
            } else {
                result.add(stmt);
            }
        }
        return result;
    }

    private List<Statement> outputPathAndBreakStatements(List<Node> path, String breakLabel, int breakLabelId,
                                                         LoopStructure currentLoop, LabeledBlockStructure currentBlock, Node target) {
        List<Statement> result = new ArrayList<>();
        
        for (Node n : path) {
            result.add(new ExpressionStatement(n));
        }
        
        // Check if target is a return node
        if (target != null && target.succs.isEmpty()) {
            boolean isReturnNode = false;
            if (detectedReturnBlock != null) {
                for (LabeledBreakEdge breakEdge : detectedReturnBlock.breaks) {
                    if (breakEdge.from.equals(target)) {
                        isReturnNode = true;
                        break;
                    }
                }
            }
            if (isReturnNode) {
                result.add(new ExpressionStatement(target));
            }
        }
        
        // Use unlabeled break when breaking out of immediately enclosing block
        // BUT: if we're inside a loop within the block, we need a labeled break
        // because unlabeled break would only exit the loop, not the block
        if (breakLabel != null && !breakLabel.isEmpty()) {
            if (currentBlock != null && breakLabel.equals(currentBlock.label) && currentLoop == null) {
                // Breaking out of the immediately enclosing block (no loop in between) - use unlabeled break
                result.add(new BreakStatement(currentBlock.labelId));
            } else {
                result.add(new BreakStatement(breakLabel, breakLabelId));
            }
        } else if (breakLabelId == -1) {
            // breakLabel is empty AND breakLabelId is -1 - this is a break to the natural loop exit
            // Use unlabeled break (no label string, just the loop ID for tracking)
            // The BreakStatement(int) constructor creates a break without a visible label
            if (currentLoop != null) {
                result.add(new BreakStatement(getLoopLabelId(currentLoop.header)));
            } else {
                result.add(new BreakStatement(-1));
            }
        } else if (currentBlock != null && currentLoop != null) {
            result.add(new BreakStatement(getLoopLabel(currentLoop.header), getLoopLabelId(currentLoop.header)));
        } else if (currentLoop != null) {
            result.add(new BreakStatement(getLoopLabelId(currentLoop.header)));
        } else {
            // Fallback - this is an unexpected state; use breakLabelId if available
            result.add(new BreakStatement(breakLabelId));
        }
        
        return result;
    }
    
    private List<Statement> outputPathAndContinueStatements(List<Node> path, String continueLabel, int continueLabelId, LoopStructure currentLoop) {
        List<Statement> result = new ArrayList<>();
        
        for (Node n : path) {
            result.add(new ExpressionStatement(n));
        }
        
        // If continueLabelId is -1 (innermost loop continue), use the current loop's label ID
        int labelId = continueLabelId >= 0 ? continueLabelId : getLoopLabelId(currentLoop.header);
        
        if (continueLabel != null && !continueLabel.isEmpty()) {
            result.add(new ContinueStatement(continueLabel, labelId));
        } else {
            // Innermost loop continue - no label needed
            result.add(new ContinueStatement(labelId));
        }
        
        return result;
    }
    
    private List<Statement> generateStatementsInBlock(Node node, Set<Node> visited,
                                            Map<Node, LoopStructure> loopHeaders, Map<Node, IfStructure> ifConditions,
                                            Map<Node, LabeledBreakEdge> labeledBreakEdges,
                                            LabeledBlockStructure currentBlock) {
        return generateStatementsInBlock(node, visited, loopHeaders, ifConditions, labeledBreakEdges, currentBlock, null);
    }
    
    private List<Statement> generateStatementsInBlock(Node node, Set<Node> visited,
                                            Map<Node, LoopStructure> loopHeaders, Map<Node, IfStructure> ifConditions,
                                            Map<Node, LabeledBreakEdge> labeledBreakEdges,
                                            LabeledBlockStructure currentBlock, Node stopAt) {
        List<Statement> result = new ArrayList<>();
        
        if (node == null || visited.contains(node)) {
            return result;
        }
        
        if (stopAt != null && node.equals(stopAt)) {
            return result;
        }
        
        if (!currentBlock.body.contains(node)) {
            return result;
        }
        
        // Check if this node has a labeled break
        LabeledBreakEdge labeledBreak = labeledBreakEdges.get(node);
        if (labeledBreak != null && currentBlock.label.equals(labeledBreak.label)) {
            IfStructure ifStruct = ifConditions.get(node);
            if (ifStruct != null) {
                boolean breakOnTrue = !currentBlock.body.contains(ifStruct.trueBranch);
                
                // Use unlabeled break when breaking out of immediately enclosing block
                boolean useUnlabeledBreak = labeledBreak.label.equals(currentBlock.label);
                
                if (breakOnTrue) {
                    List<Statement> breakBody = new ArrayList<>();
                    breakBody.add(useUnlabeledBreak ? new BreakStatement(currentBlock.labelId) : new BreakStatement(labeledBreak.label, labeledBreak.labelId));
                    result.add(new IfStatement(node, false, breakBody));
                    Set<Node> elseVisited = new HashSet<>(visited);
                    elseVisited.add(node);
                    result.addAll(generateStatementsInBlock(ifStruct.falseBranch, elseVisited, loopHeaders, ifConditions, 
                                              labeledBreakEdges, currentBlock));
                } else {
                    List<Statement> breakBody = new ArrayList<>();
                    breakBody.add(useUnlabeledBreak ? new BreakStatement(currentBlock.labelId) : new BreakStatement(labeledBreak.label, labeledBreak.labelId));
                    result.add(new IfStatement(node, true, breakBody));
                    Set<Node> thenVisited = new HashSet<>(visited);
                    thenVisited.add(node);
                    result.addAll(generateStatementsInBlock(ifStruct.trueBranch, thenVisited, loopHeaders, ifConditions, 
                                              labeledBreakEdges, currentBlock));
                }
                return result;
            }
        }
        
        visited.add(node);
        
        // Check if this is an if condition
        IfStructure ifStruct = ifConditions.get(node);
        if (ifStruct != null && !labeledBreakEdges.containsKey(node)) {
            Node internalMerge = findInternalMerge(ifStruct.trueBranch, ifStruct.falseBranch, currentBlock.body);
            
            boolean trueIsEmpty = ifStruct.trueBranch.equals(internalMerge) || 
                                  !currentBlock.body.contains(ifStruct.trueBranch);
            boolean falseIsEmpty = ifStruct.falseBranch.equals(internalMerge) || 
                                   !currentBlock.body.contains(ifStruct.falseBranch);
            
            boolean trueBranchExits = branchHasLabeledBreak(ifStruct.trueBranch, labeledBreakEdges, currentBlock);
            boolean falseBranchExits = branchHasLabeledBreak(ifStruct.falseBranch, labeledBreakEdges, currentBlock);
            
            if (trueIsEmpty && !falseIsEmpty) {
                // If internalMerge is null, use the outer stopAt to prevent over-generation
                Node effectiveStopAt = internalMerge != null ? internalMerge : stopAt;
                Set<Node> falseVisited = new HashSet<>(visited);
                List<Statement> onTrue = generateStatementsInBlock(ifStruct.falseBranch, falseVisited, loopHeaders, ifConditions, 
                                          labeledBreakEdges, currentBlock, effectiveStopAt);
                result.add(new IfStatement(node, true, onTrue));
                
                if (internalMerge != null && currentBlock.body.contains(internalMerge)) {
                    visited.addAll(falseVisited);
                    result.addAll(generateStatementsInBlock(internalMerge, visited, loopHeaders, ifConditions, 
                                              labeledBreakEdges, currentBlock));
                }
                return result;
            }
            
            if (trueBranchExits && !falseIsEmpty) {
                // If internalMerge is null, use the outer stopAt to prevent over-generation
                Node effectiveStopAt = internalMerge != null ? internalMerge : stopAt;
                Set<Node> trueVisited = new HashSet<>(visited);
                List<Statement> onTrue = generateStatementsInBlock(ifStruct.trueBranch, trueVisited, loopHeaders, ifConditions, 
                                          labeledBreakEdges, currentBlock, effectiveStopAt);
                result.add(new IfStatement(node, false, onTrue));
                Set<Node> falseVisited = new HashSet<>(visited);
                falseVisited.addAll(trueVisited);
                result.addAll(generateStatementsInBlock(ifStruct.falseBranch, falseVisited, loopHeaders, ifConditions, 
                                          labeledBreakEdges, currentBlock, effectiveStopAt));
                
                if (internalMerge != null && currentBlock.body.contains(internalMerge)) {
                    visited.addAll(falseVisited);
                    result.addAll(generateStatementsInBlock(internalMerge, visited, loopHeaders, ifConditions, 
                                              labeledBreakEdges, currentBlock, stopAt));
                }
                return result;
            }
            
            if (falseBranchExits && !trueIsEmpty) {
                // If internalMerge is null, use the outer stopAt to prevent over-generation
                Node effectiveStopAt = internalMerge != null ? internalMerge : stopAt;
                Set<Node> falseVisited = new HashSet<>(visited);
                List<Statement> onTrue = generateStatementsInBlock(ifStruct.falseBranch, falseVisited, loopHeaders, ifConditions, 
                                          labeledBreakEdges, currentBlock, effectiveStopAt);
                result.add(new IfStatement(node, true, onTrue));
                Set<Node> trueVisited = new HashSet<>(visited);
                trueVisited.addAll(falseVisited);
                result.addAll(generateStatementsInBlock(ifStruct.trueBranch, trueVisited, loopHeaders, ifConditions, 
                                          labeledBreakEdges, currentBlock, effectiveStopAt));
                
                if (internalMerge != null && currentBlock.body.contains(internalMerge)) {
                    visited.addAll(trueVisited);
                    result.addAll(generateStatementsInBlock(internalMerge, visited, loopHeaders, ifConditions, 
                                              labeledBreakEdges, currentBlock, stopAt));
                }
                return result;
            }
            
            if (falseIsEmpty && !trueIsEmpty) {
                // If internalMerge is null, use the outer stopAt to prevent over-generation
                Node effectiveStopAt = internalMerge != null ? internalMerge : stopAt;
                Set<Node> trueVisited = new HashSet<>(visited);
                List<Statement> onTrue = generateStatementsInBlock(ifStruct.trueBranch, trueVisited, loopHeaders, ifConditions, 
                                          labeledBreakEdges, currentBlock, effectiveStopAt);
                result.add(new IfStatement(node, false, onTrue));
                
                if (internalMerge != null && currentBlock.body.contains(internalMerge)) {
                    visited.addAll(trueVisited);
                    result.addAll(generateStatementsInBlock(internalMerge, visited, loopHeaders, ifConditions, 
                                              labeledBreakEdges, currentBlock));
                }
                return result;
            }
            
            // Standard if-else
            // If internalMerge is null, use the outer stopAt to prevent over-generation
            Node effectiveStopAt = internalMerge != null ? internalMerge : stopAt;
            Set<Node> trueVisited = new HashSet<>(visited);
            List<Statement> onTrue = generateStatementsInBlock(ifStruct.trueBranch, trueVisited, loopHeaders, ifConditions, 
                                      labeledBreakEdges, currentBlock, effectiveStopAt);
            
            Set<Node> falseVisited = new HashSet<>(visited);
            List<Statement> onFalse = generateStatementsInBlock(ifStruct.falseBranch, falseVisited, loopHeaders, ifConditions, 
                                      labeledBreakEdges, currentBlock, effectiveStopAt);
            
            result.add(new IfStatement(node, false, onTrue, onFalse));
            
            if (internalMerge != null && currentBlock.body.contains(internalMerge)) {
                visited.addAll(trueVisited);
                visited.addAll(falseVisited);
                result.addAll(generateStatementsInBlock(internalMerge, visited, loopHeaders, ifConditions, 
                                          labeledBreakEdges, currentBlock));
            }
            return result;
        }
        
        // Check if regular node leads outside the block
        boolean leadsOutside = false;
        for (Node succ : node.succs) {
            if (succ.equals(currentBlock.endNode) && node.succs.size() == 1) {
                result.add(new ExpressionStatement(node));
                // Only add break if there's a stopAt (meaning we're in a partial traversal, like if body)
                // and the node is a labeled break source
                LabeledBreakEdge breakEdge = labeledBreakEdges.get(node);
                if (breakEdge != null && breakEdge.to.equals(currentBlock.endNode)) {
                    // This is a labeled break source - add the break
                    result.add(new BreakStatement(currentBlock.labelId));
                }
                return result;
            }
            if (!currentBlock.body.contains(succ)) {
                leadsOutside = true;
            }
        }
        
        // Regular node
        result.add(new ExpressionStatement(node));
        
        for (Node succ : node.succs) {
            if (currentBlock.body.contains(succ) && (stopAt == null || !succ.equals(stopAt))) {
                result.addAll(generateStatementsInBlock(succ, visited, loopHeaders, ifConditions, 
                                          labeledBreakEdges, currentBlock, stopAt));
            } else if (leadsOutside && succ.equals(currentBlock.endNode)) {
                // Check if this node is a labeled break source
                LabeledBreakEdge breakEdge = labeledBreakEdges.get(node);
                if (breakEdge != null && breakEdge.to.equals(currentBlock.endNode)) {
                    // Use unlabeled break since we're breaking out of the immediately enclosing block
                    result.add(new BreakStatement(currentBlock.labelId));
                }
            }
        }
        
        return result;
    }

    /**
     * Detects switch structures in the CFG.
     * 
     * A switch structure is detected when there's a chain of conditional nodes where:
     * - Each condition's TRUE branch (first edge) goes to a case body
     * - Each condition's FALSE branch (second edge) goes to the next condition
     * - All case bodies eventually lead to the same merge node
     * - Cases may have fall-through edges to the next case
     * 
     * @param ifs the detected if structures
     * @return list of detected switch structures
     */
    private List<SwitchStructure> detectSwitches(List<IfStructure> ifs) {
        List<SwitchStructure> switches = new ArrayList<>();
        Set<Node> processedNodes = new HashSet<>();
        
        // Get loop headers to exclude from switch detection
        List<LoopStructure> loops = detectLoops();
        Set<Node> loopHeaders = new HashSet<>();
        for (LoopStructure loop : loops) {
            loopHeaders.add(loop.header);
        }
        
        // Build maps for quick lookup
        Set<Node> conditionNodes = new HashSet<>();
        Map<Node, IfStructure> ifMap = new HashMap<>();
        for (IfStructure ifStruct : ifs) {
            conditionNodes.add(ifStruct.conditionNode);
            ifMap.put(ifStruct.conditionNode, ifStruct);
        }
        
        // Look for chains of conditions
        for (IfStructure ifStruct : ifs) {
            Node startCond = ifStruct.conditionNode;
            
            // Skip if already part of a detected switch or if it's a loop header
            if (processedNodes.contains(startCond) || loopHeaders.contains(startCond)) {
                continue;
            }
            
            // Skip if this condition is not a strict equals (===) or strict not equals (!==) - required for switch pattern
            // For !==, branches are swapped (true branch -> next condition, false branch -> case body)
            boolean startCondIsNotEquals = false;
            if (!dialect.isStrictEqualsIf(startCond)) {
                if (dialect.isStrictNotEqualsIf(startCond)) {
                    startCondIsNotEquals = true;
                } else {
                    continue;
                }
            }
            
            // Check if the appropriate branch leads to another condition (switch pattern)
            // For ===: true branch -> case body, false branch -> next condition
            // For !==: false branch -> case body, true branch -> next condition (swapped)
            Node startNextCondBranch = startCondIsNotEquals ? ifStruct.trueBranch : ifStruct.falseBranch;
            if (!conditionNodes.contains(startNextCondBranch)) {
                continue; // Not a switch pattern - next condition branch should be another condition
            }
            
            // Try to build a switch chain starting from this condition
            // First pass: collect all conditions and their case bodies
            List<Node> conditionChain = new ArrayList<>();
            List<Boolean> conditionChainNegated = new ArrayList<>();
            List<Node> caseBodies = new ArrayList<>();
            Node currentCond = startCond;
            Node defaultBody = null;
            
            while (currentCond != null && !processedNodes.contains(currentCond)) {
                // Prevent cycles - check if we've already added this condition in this chain
                if (conditionChain.contains(currentCond)) {
                    break;
                }
                
                // Check if this is a strict equals (===) or strict not equals (!==)
                // For !==, branches are swapped
                boolean isNotEquals = false;
                if (!dialect.isStrictEqualsIf(currentCond)) {
                    if (dialect.isStrictNotEqualsIf(currentCond)) {
                        isNotEquals = true;
                    } else {
                        // This condition is not a strict equals or not equals - treat the rest as default body
                        defaultBody = currentCond;
                        break;
                    }
                }
                
                IfStructure currentIf = ifMap.get(currentCond);
                
                if (currentIf == null || currentIf.trueBranch == null || currentIf.falseBranch == null) {
                    break;
                }
                
                // For ===: case body is trueBranch, next condition is falseBranch
                // For !==: case body is falseBranch, next condition is trueBranch (swapped)
                Node caseBody = isNotEquals ? currentIf.falseBranch : currentIf.trueBranch;
                Node nextCondBranch = isNotEquals ? currentIf.trueBranch : currentIf.falseBranch;
                
                conditionChain.add(currentCond);
                conditionChainNegated.add(isNotEquals);
                caseBodies.add(caseBody);
                
                if (conditionNodes.contains(nextCondBranch)) {
                    currentCond = nextCondBranch;
                } else {
                    defaultBody = nextCondBranch;
                    break;
                }
            }
            
            if (conditionChain.size() < MIN_SWITCH_CHAIN_SIZE || defaultBody == null) {
                continue;
            }
            
            // Collect all unique case bodies including default
            Set<Node> allCaseBodies = new HashSet<>(caseBodies);
            allCaseBodies.add(defaultBody);
            
            // Detect if we're inside a loop
            Node switchLoopHeader = null;
            for (Node loopHead : loopHeaders) {
                // Check if the switch start is reachable from this loop header
                Set<Node> loopReachable = getReachableNodes(loopHead);
                if (loopReachable.contains(startCond)) {
                    switchLoopHeader = loopHead;
                    break;
                }
            }
            
            // Find the merge node - prefer DIRECT successors of case bodies
            // Count direct successors (1-step) vs indirect successors (2+ steps)
            Map<Node, Integer> directCount = new HashMap<>();
            Map<Node, Integer> indirectCount = new HashMap<>();
            
            for (Node caseBodyNode : allCaseBodies) {
                Set<Node> countedDirect = new HashSet<>();
                Set<Node> countedIndirect = new HashSet<>();
                
                // Level 1: direct successors
                for (Node succ : caseBodyNode.succs) {
                    if (conditionChain.contains(succ) || allCaseBodies.contains(succ) || loopHeaders.contains(succ)) {
                        continue;
                    }
                    if (!countedDirect.contains(succ)) {
                        directCount.put(succ, directCount.getOrDefault(succ, 0) + 1);
                        countedDirect.add(succ);
                    }
                    
                    // Level 2+: indirect successors (via BFS)
                    Queue<Node> queue = new LinkedList<>();
                    queue.add(succ);
                    Set<Node> visited = new HashSet<>();
                    visited.add(caseBodyNode);
                    visited.add(succ);
                    
                    while (!queue.isEmpty()) {
                        Node current = queue.poll();
                        for (Node succ2 : current.succs) {
                            if (visited.contains(succ2) || conditionChain.contains(succ2) || 
                                allCaseBodies.contains(succ2) || loopHeaders.contains(succ2)) {
                                continue;
                            }
                            visited.add(succ2);
                            if (!countedIndirect.contains(succ2) && !countedDirect.contains(succ2)) {
                                indirectCount.put(succ2, indirectCount.getOrDefault(succ2, 0) + 1);
                                countedIndirect.add(succ2);
                            }
                            queue.add(succ2);
                        }
                    }
                }
            }
            
            // Find the best merge node:
            // Priority 1: Node with most direct successors (at least 2)
            // Priority 2: Node with most indirect successors (at least 2)
            Node mergeNode = null;
            Node outerMergeNode = null;
            int maxDirectCount = 0;
            int maxIndirectCount = 0;
            
            for (Map.Entry<Node, Integer> entry : directCount.entrySet()) {
                Node candidate = entry.getKey();
                int count = entry.getValue();
                
                // Check if this node is inside the same loop as the switch
                // If not in a loop (switchLoopHeader is null), treat as not in loop context
                boolean isInLoop = false;
                if (switchLoopHeader != null) {
                    Set<Node> loopReachable = getReachableNodes(switchLoopHeader);
                    isInLoop = loopReachable.contains(candidate) && 
                               getReachableNodes(candidate).contains(switchLoopHeader);
                }
                
                // Accept nodes inside loop, or any node if not in loop context
                if ((isInLoop || switchLoopHeader == null) && count >= 2 && count > maxDirectCount) {
                    maxDirectCount = count;
                    mergeNode = candidate;
                }
            }
            
            // If we found a merge node, look for an outer merge (where skipping cases go)
            if (mergeNode != null) {
                for (Map.Entry<Node, Integer> entry : indirectCount.entrySet()) {
                    Node candidate = entry.getKey();
                    int count = entry.getValue();
                    
                    // Check if this is reachable from the merge node
                    Set<Node> mergeReachable = getReachableNodes(mergeNode);
                    if (!mergeReachable.contains(candidate)) {
                        continue;
                    }
                    
                    // Check if this node is inside the same loop
                    boolean isInLoop = false;
                    if (switchLoopHeader != null) {
                        Set<Node> loopReachable = getReachableNodes(switchLoopHeader);
                        isInLoop = loopReachable.contains(candidate) && 
                                   getReachableNodes(candidate).contains(switchLoopHeader);
                    }
                    
                    if ((isInLoop || switchLoopHeader == null) && count > maxIndirectCount) {
                        maxIndirectCount = count;
                        outerMergeNode = candidate;
                    }
                }
                
                // Also check if any direct successor is a potential outer merge
                // (case bodies that skip the merge node entirely)
                for (Map.Entry<Node, Integer> entry : directCount.entrySet()) {
                    Node candidate = entry.getKey();
                    if (candidate.equals(mergeNode)) continue;
                    
                    // Check if merge node leads to this candidate
                    Set<Node> mergeReachable = getReachableNodes(mergeNode);
                    if (mergeReachable.contains(candidate)) {
                        boolean isInLoop = false;
                        if (switchLoopHeader != null) {
                            Set<Node> loopReachable = getReachableNodes(switchLoopHeader);
                            isInLoop = loopReachable.contains(candidate) && 
                                       getReachableNodes(candidate).contains(switchLoopHeader);
                        }
                        
                        if ((isInLoop || switchLoopHeader == null) && (outerMergeNode == null || entry.getValue() > maxIndirectCount)) {
                            outerMergeNode = candidate;
                        }
                    }
                }
            }
            
            // Fallback: if no direct merge found, use indirect
            if (mergeNode == null) {
                for (Map.Entry<Node, Integer> entry : indirectCount.entrySet()) {
                    Node candidate = entry.getKey();
                    int count = entry.getValue();
                    
                    boolean isInLoop = false;
                    if (switchLoopHeader != null) {
                        Set<Node> loopReachable = getReachableNodes(switchLoopHeader);
                        isInLoop = loopReachable.contains(candidate) && 
                                   getReachableNodes(candidate).contains(switchLoopHeader);
                    }
                    
                    if ((isInLoop || switchLoopHeader == null) && count > maxIndirectCount) {
                        maxIndirectCount = count;
                        mergeNode = candidate;
                    }
                }
            }
            
            if (mergeNode == null) {
                continue;
            }
            
            // Second pass: build cases with merged conditions, fall-through, and skip detection
            // Also detect where default should be inserted:
            // 1. When case body equals default body (default inserted after that case)
            // 2. When default body leads to a case body (default inserted before that case)
            List<SwitchCase> cases = new ArrayList<>();
            boolean defaultInserted = false;
            
            // Find which case body (if any) the default body leads to (first match wins).
            // We only need the first match because we want to insert default at the earliest
            // position where it can fall through to a case body.
            int defaultLeadsToIndex = -1;
            for (int i = 0; i < caseBodies.size(); i++) {
                if (nodeDirectlyReaches(defaultBody, caseBodies.get(i))) {
                    defaultLeadsToIndex = i;
                    break;
                }
            }
            
            for (int i = 0; i < conditionChain.size(); i++) {
                Node cond = conditionChain.get(i);
                boolean negated = conditionChainNegated.get(i);
                Node body = caseBodies.get(i);
                
                // Check if default should be inserted BEFORE this case
                // (when default body leads to this case's body).
                // The `!body.equals(defaultBody)` check prevents handling this scenario here
                // when the case body equals default body, because that case is handled below
                // with different logic (label-only case + default inserted after).
                if (!defaultInserted && defaultLeadsToIndex == i && !body.equals(defaultBody)) {
                    // Insert default before this case (falls through to this case's body)
                    cases.add(new SwitchCase(null, false, defaultBody, true, false, false));
                    defaultInserted = true;
                }
                
                // Check if this case body equals the default body
                // In that case, add a label-only case and insert default right after
                // Note: if multiple cases share the default body, all of them become label-only
                // and default is inserted after the first one
                if (body.equals(defaultBody)) {
                    // This case shares body with default - add label-only case
                    cases.add(new SwitchCase(cond, negated, null, false, false, false));
                    
                    // Only insert default once (after the first case that shares its body)
                    if (!defaultInserted) {
                        // Insert default here with the shared body
                        // Check if default falls through to next case body or merge
                        boolean defaultFallsThrough = false;
                        Node nextCaseBody = (i + 1 < caseBodies.size()) ? caseBodies.get(i + 1) : null;
                        
                        if (nextCaseBody != null) {
                            // Check if default body leads to next case body
                            for (Node succ : defaultBody.succs) {
                                if (succ.equals(nextCaseBody)) {
                                    defaultFallsThrough = true;
                                    break;
                                }
                            }
                        }
                        
                        // If not falling through to next case, check if it falls through to merge
                        if (!defaultFallsThrough) {
                            defaultFallsThrough = nodeDirectlyReaches(defaultBody, mergeNode);
                        }
                        
                        cases.add(new SwitchCase(null, false, defaultBody, true, !defaultFallsThrough, false));
                        defaultInserted = true;
                    }
                    continue;
                }
                
                // Check if the next condition has the same case body (merged case)
                // In that case, add a label-only case (no body, no break)
                if (i + 1 < conditionChain.size() && caseBodies.get(i + 1).equals(body)) {
                    // This is a label-only merged case
                    cases.add(new SwitchCase(cond, negated, null, false, false, false));
                } else {
                    // Check if this case body falls through to the next case body or default
                    boolean hasFallThrough = false;
                    Node nextTarget;
                    
                    // If default was already inserted, next target is next case body (or merge)
                    // Otherwise, default is still at the end, so fall through to next case or default
                    if (defaultInserted) {
                        nextTarget = (i + 1 < conditionChain.size()) ? caseBodies.get(i + 1) : mergeNode;
                    } else {
                        nextTarget = (i + 1 < conditionChain.size()) ? caseBodies.get(i + 1) : defaultBody;
                    }
                    
                    // Check direct fall-through
                    if (nextTarget != null) {
                        for (Node succ : body.succs) {
                            if (succ.equals(nextTarget)) {
                                hasFallThrough = true;
                                break;
                            }
                        }
                    }
                    
                    // Check indirect fall-through via intermediate nodes (for case bodies with conditionals)
                    if (!hasFallThrough && nextTarget != null) {
                        Set<Node> visited = new HashSet<>();
                        Queue<Node> queue = new LinkedList<>();
                        queue.add(body);
                        while (!queue.isEmpty() && !hasFallThrough) {
                            Node current = queue.poll();
                            if (visited.contains(current)) continue;
                            visited.add(current);
                            
                            for (Node succ : current.succs) {
                                if (succ.equals(nextTarget)) {
                                    hasFallThrough = true;
                                    break;
                                }
                                // Don't traverse through merge or outer merge
                                if (succ.equals(mergeNode) || succ.equals(outerMergeNode) ||
                                    conditionChain.contains(succ) || allCaseBodies.contains(succ)) {
                                    continue;
                                }
                                queue.add(succ);
                            }
                        }
                    }
                    
                    // Check if this case body skips the merge node (goes directly to outer merge)
                    boolean skipsMerge = false;
                    if (outerMergeNode != null) {
                        for (Node succ : body.succs) {
                            if (succ.equals(outerMergeNode)) {
                                skipsMerge = true;
                                break;
                            }
                        }
                    }
                    
                    // Add case with body
                    cases.add(new SwitchCase(cond, negated, body, false, !hasFallThrough, skipsMerge));
                }
            }
            
            // Add default case at the end if not already inserted
            if (!defaultInserted) {
                boolean defaultFallsThrough = nodeDirectlyReaches(defaultBody, mergeNode);
                cases.add(new SwitchCase(null, false, defaultBody, true, !defaultFallsThrough, false));
            }
            
            // Mark all condition nodes AND case body nodes as processed
            for (int i = 0; i < conditionChain.size(); i++) {
                processedNodes.add(conditionChain.get(i));
                processedNodes.add(caseBodies.get(i));
            }
            processedNodes.add(defaultBody);
            
            switches.add(new SwitchStructure(startCond, cases, mergeNode, outerMergeNode));
        }
        
        return switches;
    }
    
    /**
     * Detects all control flow structures in the CFG.
     */
    public void analyze() {
        System.out.println("=== Control Flow Structure Analysis ===\n");
        
        System.out.println("Nodes in CFG: " + allNodes);
        System.out.println();
        
        List<IfStructure> ifs = detectIfs();
        System.out.println("Detected If Structures (" + ifs.size() + "):");
        for (IfStructure ifStruct : ifs) {
            System.out.println("  " + ifStruct);
        }
        System.out.println();
        
        List<LoopStructure> loops = detectLoops();
        System.out.println("Detected Loop Structures (" + loops.size() + "):");
        for (LoopStructure loop : loops) {
            System.out.println("  " + loop);
        }
        System.out.println();
        
        // Auto-detect switch structures
        switchStructures.clear();
        switchStructures.addAll(detectSwitches(ifs));
        System.out.println("Detected Switch Structures (" + switchStructures.size() + "):");
        for (SwitchStructure sw : switchStructures) {
            System.out.println("  " + sw);
        }
        System.out.println();
        
        // Detect blocks and pre-assign loop labels in correct order
        detectBlocksAndPreAssignLoopLabels(loops, ifs, switchStructures);
        
        System.out.println("Labeled Block Structures (" + labeledBlocks.size() + "):");
        for (LabeledBlockStructure block : labeledBlocks) {
            System.out.println("  " + block);
        }
    }

    /**
     * Demonstration with example CFGs.
     */
}
