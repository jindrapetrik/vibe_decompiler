/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Main.java to edit this template
 */
package structure_detector;

import java.util.*;
import structure_detector.statement.*;

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
     * Represents an if-statement structure detected in the CFG.
     */
    public static class IfStructure {
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

    /**
     * Represents a loop structure detected in the CFG.
     */
    public static class LoopStructure {
        public final Node header;        // loop header node
        public final Set<Node> body;     // all nodes in the loop body
        public final Node backEdgeSource; // node that jumps back to header
        public final List<BreakEdge> breaks;
        public final List<ContinueEdge> continues;

        public LoopStructure(Node header, Set<Node> body, Node backEdgeSource) {
            this.header = header;
            this.body = body;
            this.backEdgeSource = backEdgeSource;
            this.breaks = new ArrayList<>();
            this.continues = new ArrayList<>();
        }

        @Override
        public String toString() {
            return "Loop{header=" + header + 
                   ", body=" + body + 
                   ", backEdge=" + backEdgeSource + 
                   ", breaks=" + breaks + 
                   ", continues=" + continues + "}";
        }
    }

    /**
     * Represents a break edge - an edge that exits a loop early.
     */
    public static class BreakEdge {
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

    /**
     * Represents a continue edge - an edge that jumps back to loop header.
     */
    public static class ContinueEdge {
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

    /**
     * Represents a labeled block structure detected in the CFG.
     * A labeled block is a region of code that can be exited early with "break label;".
     * Unlike loops, labeled blocks have no back edge.
     */
    public static class LabeledBlockStructure {
        public final String label;
        public final Node startNode;       // first node in the block
        public final Node endNode;         // node after the block (break target)
        public final Set<Node> body;       // all nodes in the block
        public final List<LabeledBreakEdge> breaks;

        public LabeledBlockStructure(String label, Node startNode, Node endNode, Set<Node> body) {
            this.label = label;
            this.startNode = startNode;
            this.endNode = endNode;
            this.body = body;
            this.breaks = new ArrayList<>();
        }

        @Override
        public String toString() {
            return "LabeledBlock{label=" + label + 
                   ", start=" + startNode + 
                   ", end=" + endNode + 
                   ", body=" + body + 
                   ", breaks=" + breaks + "}";
        }
    }

    /**
     * Represents a break edge from within a labeled block to its exit.
     */
    public static class LabeledBreakEdge {
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

    private static final String RETURN_BLOCK_LABEL = "r_block";
    
    private final List<Node> allNodes;
    private final Node entryNode;
    private final List<LabeledBlockStructure> labeledBlocks = new ArrayList<>();

    /**
     * Creates a new StructureDetector for the given CFG.
     * 
     * @param entryNode the entry node of the CFG
     */
    public StructureDetector(Node entryNode) {
        this.entryNode = entryNode;
        this.allNodes = collectAllNodes(entryNode);
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
            }
        }
        
        if (firstNode == null) {
            throw new IllegalArgumentException("No nodes found in DOT string");
        }
        
        return new StructureDetector(firstNode);
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
     * Registers a labeled block structure.
     * A labeled block is a region where control can jump to the end using "break label;".
     * 
     * @param label the label name for the block
     * @param startNode the first node inside the labeled block
     * @param endNode the node after the labeled block (the break target)
     */
    public void addLabeledBlock(String label, Node startNode, Node endNode) {
        addLabeledBlock(label, startNode, endNode, null);
    }
    
    /**
     * Registers a labeled block structure with loop awareness.
     * Edges that are normal loop exits (false branch of loop condition) are not counted as labeled breaks.
     * 
     * @param label the label name for the block
     * @param startNode the first node inside the labeled block
     * @param endNode the node after the labeled block (the break target)
     * @param loopHeaders map of loop headers to their loop structures, may be null
     */
    private void addLabeledBlock(String label, Node startNode, Node endNode, Map<Node, LoopStructure> loopHeaders) {
        Set<Node> body = new HashSet<>();
        // Collect all nodes in the block (reachable from start but before end)
        collectBlockBody(startNode, endNode, body);
        
        LabeledBlockStructure block = new LabeledBlockStructure(label, startNode, endNode, body);
        
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
                        block.breaks.add(new LabeledBreakEdge(node, endNode, label));
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
        }
        
        // All other branches lead to endNode, so this is a normal merge
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
                    if (reachable2.contains(succ)) {
                        return succ; // Found common node
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
                    if (reachable1.contains(succ)) {
                        return succ; // Found common node
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
     * Checks if target is reachable from start using early-exit BFS.
     */
    private boolean isReachable(Node start, Node target) {
        if (start.equals(target)) {
            return true;
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
                if (!visited.contains(succ)) {
                    visited.add(succ);
                    queue.add(succ);
                }
            }
        }
        return false;
    }
    
    /**
     * Checks if target is reachable from start within a loop's body.
     * Only considers nodes within the loop.
     */
    private boolean isReachableWithinLoop(Node start, Node target, LoopStructure loop) {
        if (start.equals(target)) {
            return true;
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
        List<BackEdge> backEdges = new ArrayList<>();
        for (Node node : allNodes) {
            Set<Node> nodeDominators = dominators.get(node);
            for (Node succ : node.succs) {
                if (nodeDominators != null && nodeDominators.contains(succ)) {
                    backEdges.add(new BackEdge(node, succ));
                }
            }
        }
        
        // For each back edge, identify the natural loop
        for (BackEdge backEdge : backEdges) {
            Node header = backEdge.to;
            Node tail = backEdge.from;
            Set<Node> loopBody = findNaturalLoop(header, tail);
            LoopStructure loop = new LoopStructure(header, loopBody, tail);
            
            // Detect breaks and continues within the loop
            detectBreaksAndContinues(loop, dominators);
            
            loops.add(loop);
        }
        
        return loops;
    }

    private static class BackEdge {
        final Node from;
        final Node to;
        
        BackEdge(Node from, Node to) {
            this.from = from;
            this.to = to;
        }
    }

    /**
     * Computes dominators for all nodes using iterative dataflow analysis.
     * A node D dominates node N if every path from entry to N goes through D.
     */
    private Map<Node, Set<Node>> computeDominators() {
        Map<Node, Set<Node>> dominators = new HashMap<>();
        
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
                
                // Handle nodes with no predecessors - they are only dominated by themselves
                if (node.preds.isEmpty()) {
                    newDom = new HashSet<>();
                } else {
                    newDom = new HashSet<>(allNodesSet);
                    // Intersect dominators of all predecessors
                    for (Node pred : node.preds) {
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
    private void detectBreaksAndContinues(LoopStructure loop, Map<Node, Set<Node>> dominators) {
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
        
        // For each main loop, find edges that jump to nodes inside the loop (not header)
        // These represent continue-like jumps that need labeled blocks
        for (LoopStructure loop : mainLoops.values()) {
            Node backEdgeSource = loop.backEdgeSource;
            
            // Skip if the back-edge source is the loop header itself (simple while loop)
            if (backEdgeSource.equals(loop.header)) {
                continue;
            }
            
            // Also skip if there are direct continues to the header from within the loop body
            // (not from the back-edge source). This indicates a simple while loop pattern
            // where we can use simple 'continue;' statements instead of labeled blocks.
            boolean hasDirectContinueToHeader = false;
            for (Node node : loop.body) {
                if (!node.equals(loop.header) && !node.equals(backEdgeSource)) {
                    // Check if this node has a direct edge to the header
                    for (Node succ : node.succs) {
                        if (succ.equals(loop.header)) {
                            hasDirectContinueToHeader = true;
                            break;
                        }
                    }
                }
                if (hasDirectContinueToHeader) break;
            }
            
            // If there are direct continues to the header, we don't need labeled blocks
            // because we can use simple 'continue;' statements
            if (hasDirectContinueToHeader) {
                continue;
            }
            
            // Find all paths that lead to the back-edge source from conditional nodes
            // These are "skip" jumps (continue semantics).
            boolean needsLabeledBlock = false;
            
            // Check all nodes in ALL loops (not just this one) for paths to this loop's back-edge source
            for (Node node : allNodes) {
                // Skip the back-edge source itself and the loop header
                if (node.equals(backEdgeSource) || node.equals(loop.header)) {
                    continue;
                }
                
                // Check if this node leads (directly or indirectly) to the back-edge source
                // via a path that doesn't go through the loop header
                if (leadsToBackEdgeSource(node, backEdgeSource, loop.header)) {
                    // This node eventually reaches the back-edge source
                    // If it's a conditional node (has 2 successors), it might be a continue pattern
                    if (node.succs.size() >= 2) {
                        needsLabeledBlock = true;
                        break;
                    }
                }
            }
            
            // Continue block detection is now handled by detectSkipBlocksInLoops
            // which creates separate labeled blocks for each skip pattern within the loop.
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
                
                Node trueBranch = ifStruct.trueBranch;
                Node falseBranch = ifStruct.falseBranch;
                Node mergeNode = ifStruct.mergeNode;
                
                if (trueBranch == null || falseBranch == null) continue;
                if (mergeNode == null) continue;
                if (!loop.body.contains(mergeNode)) continue;
                
                // Find the effective merge point:
                // 1. If one branch goes directly to a back-edge source (loop continuation), use that
                // 2. Otherwise, find the first convergence point
                Node effectiveMerge = null;
                boolean branchGoesDirectlyToMerge = false;
                
                // Check if either branch goes directly to a back-edge source (inner loop continuation)
                for (LoopStructure innerLoop : loops) {
                    if (!loop.body.contains(innerLoop.header)) continue; // Not a nested loop
                    
                    Node backEdgeSrc = innerLoop.backEdgeSource;
                    if (backEdgeSrc != null && loop.body.contains(backEdgeSrc)) {
                        // Check if either branch goes directly to this back-edge source
                        if (trueBranch.equals(backEdgeSrc) || falseBranch.equals(backEdgeSrc)) {
                            effectiveMerge = backEdgeSrc;
                            branchGoesDirectlyToMerge = true;
                            break;
                        }
                    }
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
                
                // If one branch goes directly to the merge, create a block
                // This handles patterns like: if (cond) { complex_body } else { goto merge }
                // In this case, cond itself is the skip source - when the condition's false branch
                // goes directly to the merge, the condition acts as a "break" point.
                // The SkipPattern with cond as both condNode and skipSource indicates that
                // the condition should be treated as a labeled block boundary.
                if (branchGoesDirectlyToMerge) {
                    skipsByConvergence.computeIfAbsent(effectiveMerge, k -> new ArrayList<>())
                               .add(new SkipPattern(cond, cond, effectiveMerge, loop));
                }
                
                // Check true branch for skip
                Node skipSource = findDirectJumpToMergeInLoop(trueBranch, effectiveMerge, loop);
                if (skipSource != null) {
                    skipsByConvergence.computeIfAbsent(effectiveMerge, k -> new ArrayList<>())
                               .add(new SkipPattern(cond, skipSource, effectiveMerge, loop));
                }
                
                // Check false branch for skip
                skipSource = findDirectJumpToMergeInLoop(falseBranch, effectiveMerge, loop);
                if (skipSource != null) {
                    skipsByConvergence.computeIfAbsent(effectiveMerge, k -> new ArrayList<>())
                               .add(new SkipPattern(cond, skipSource, effectiveMerge, loop));
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
                String label = blockStart.getLabel() + "_block";
                
                // Check if this block already exists
                boolean exists = false;
                for (LabeledBlockStructure block : labeledBlocks) {
                    if (block.startNode.equals(blockStart) && block.endNode.equals(convergencePoint)) {
                        exists = true;
                        break;
                    }
                }
                
                if (!exists) {
                    LoopStructure loop = patterns.get(0).loop;
                    addLabeledBlock(label, blockStart, convergencePoint, mainLoops);
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
                    String label = innerLoop.header.getLabel() + "_block";
                    
                    // Check if this block already exists
                    boolean exists = false;
                    for (LabeledBlockStructure block : labeledBlocks) {
                        if (block.startNode.equals(innerLoop.header) && block.endNode.equals(convergencePoint)) {
                            exists = true;
                            break;
                        }
                    }
                    
                    if (!exists) {
                        addLabeledBlock(label, innerLoop.header, convergencePoint, mainLoops);
                    }
                }
            }
        }
    }
    
    // Helper class for skip patterns
    private static class SkipPattern {
        Node condNode;
        Node skipSource;
        Node mergeNode;
        LoopStructure loop;
        
        SkipPattern(Node condNode, Node skipSource, Node mergeNode, LoopStructure loop) {
            this.condNode = condNode;
            this.skipSource = skipSource;
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
            if (pred.equals(loop.header)) continue;
            
            // Check if pred is a condition node
            for (IfStructure ifStruct : ifs) {
                if (ifStruct.conditionNode.equals(pred)) {
                    // Check if both branches eventually lead to paths that reach mergeNode or cond
                    if (isReachableWithinLoop(ifStruct.trueBranch, cond, loop) ||
                        isReachableWithinLoop(ifStruct.falseBranch, cond, loop)) {
                        // This is a potential parent
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
     * Finds the correct end node for a continue block.
     * This is the first node in the chain from loop body to back-edge source
     * where multiple paths converge (the entry point of the continue chain).
     */
    private Node findContinueBlockEnd(LoopStructure loop, Node backEdgeSource) {
        // Trace backwards from backEdgeSource to find the first convergence point
        // where multiple distinct paths from the loop body meet
        
        Set<Node> visited = new HashSet<>();
        Queue<Node> queue = new LinkedList<>();
        queue.add(backEdgeSource);
        
        Node blockEnd = backEdgeSource;
        
        while (!queue.isEmpty()) {
            Node current = queue.poll();
            if (visited.contains(current)) continue;
            visited.add(current);
            
            // Count predecessors from the loop body
            List<Node> loopPreds = new ArrayList<>();
            for (Node pred : current.preds) {
                if (loop.body.contains(pred) && !pred.equals(loop.header)) {
                    loopPreds.add(pred);
                }
            }
            
            // If we have multiple predecessors from different paths, this is a convergence point
            if (loopPreds.size() >= 2) {
                // Check if these predecessors are from truly different code paths
                // (not just two branches of the same conditional merging immediately)
                boolean fromDifferentPaths = false;
                for (int i = 0; i < loopPreds.size() && !fromDifferentPaths; i++) {
                    for (int j = i + 1; j < loopPreds.size(); j++) {
                        Node pred1 = loopPreds.get(i);
                        Node pred2 = loopPreds.get(j);
                        
                        // Check if pred1 and pred2 have a common immediate parent conditional
                        // If not, they're from truly different paths
                        boolean shareImmediateParent = false;
                        for (Node p1Parent : pred1.preds) {
                            for (Node p2Parent : pred2.preds) {
                                if (p1Parent.equals(p2Parent) && p1Parent.succs.size() >= 2) {
                                    shareImmediateParent = true;
                                    break;
                                }
                            }
                            if (shareImmediateParent) break;
                        }
                        
                        if (!shareImmediateParent) {
                            fromDifferentPaths = true;
                            break;
                        }
                    }
                }
                
                if (fromDifferentPaths) {
                    blockEnd = current;
                    break;
                }
            }
            
            // Continue searching backwards
            for (Node pred : loopPreds) {
                queue.add(pred);
            }
        }
        
        // Make sure blockEnd is different from bodyStart - if they're the same,
        // we don't need a labeled block (empty body)
        Node bodyStart = null;
        for (Node succ : loop.header.succs) {
            if (loop.body.contains(succ) && !succ.equals(loop.header)) {
                bodyStart = succ;
                break;
            }
        }
        if (blockEnd != null && blockEnd.equals(bodyStart)) {
            return null; // No labeled block needed
        }
        
        return blockEnd;
    }
    
    /**
     * Checks if a node leads to a back-edge source through a path that doesn't go through the header.
     * The path must be a single-successor chain (no branching) from this node's successor to the target.
     */
    private boolean leadsToBackEdgeSource(Node node, Node backEdgeSource, Node header) {
        for (Node succ : node.succs) {
            // Follow single-successor chains to see if they reach the back-edge source
            Node current = succ;
            Set<Node> visited = new HashSet<>();
            
            while (current != null && !visited.contains(current)) {
                visited.add(current);
                
                // Found the back-edge source
                if (current.equals(backEdgeSource)) {
                    return true;
                }
                
                // Don't go through the header
                if (current.equals(header)) {
                    break;
                }
                
                // Only follow single-successor chains (non-conditional nodes)
                // This detects paths like: check_e20 -> trace_Y -> inc_c
                if (current.succs.size() == 1) {
                    current = current.succs.get(0);
                } else {
                    // Multi-branch node - stop following
                    break;
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
    private void detectSkipBlocks(List<IfStructure> ifs) {
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
                                 .add(new SkipInfo(cond, skipSource, mergeNode));
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
                                 .add(new SkipInfo(cond, skipSource, mergeNode));
                }
            }
        }
        
        // For each skip target, create a labeled block that encompasses all skip sources
        for (Map.Entry<Node, List<SkipInfo>> entry : skipsByTarget.entrySet()) {
            Node skipTarget = entry.getKey();
            List<SkipInfo> skips = entry.getValue();
            
            if (skips.isEmpty()) continue;
            
            // Find the earliest common dominator of all the condition nodes
            Node blockStart = findEarliestConditionNode(skips, ifs);
            
            if (blockStart == null) continue;
            
            // Generate unique label based on block start node to avoid conflicts
            String label = blockStart.getLabel() + "_block";
            
            // Check if this block already exists
            boolean exists = false;
            for (LabeledBlockStructure block : labeledBlocks) {
                if (block.startNode.equals(blockStart) && block.endNode.equals(skipTarget)) {
                    exists = true;
                    break;
                }
            }
            
            if (!exists) {
                addLabeledBlock(label, blockStart, skipTarget);
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
        final Node skipSource;
        final Node skipTarget;
        
        SkipInfo(Node conditionNode, Node skipSource, Node skipTarget) {
            this.conditionNode = conditionNode;
            this.skipSource = skipSource;
            this.skipTarget = skipTarget;
        }
    }
    
    /**
     * Finds the earliest condition node that dominates all skip conditions.
     * This should be the starting point of the labeled block.
     */
    private Node findEarliestConditionNode(List<SkipInfo> skips, List<IfStructure> ifs) {
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
     * Detects if 'skipBranch' directly jumps to a node that 'mainBranch' reaches through a longer path.
     * This indicates a "skip" pattern that may need a labeled block.
     * 
     * A skip is detected when:
     * 1. The skipBranch node directly jumps to a merge point or a node beyond the if structure
     * 2. The mainBranch reaches that same node through a significantly longer path (2+ more nodes)
     * 
     * This avoids detecting normal if-else patterns as skips.
     * 
     * @param cond The condition node
     * @param mainBranch The branch that takes the longer path
     * @param skipBranch The branch that might be skipping
     * @return The skip target node if a skip pattern is detected, null otherwise
     */
    private Node detectSkipTarget(Node cond, Node mainBranch, Node skipBranch) {
        // A skip is only valid if the skipBranch node itself directly reaches a merge point
        // that the mainBranch reaches through a longer path.
        // 
        // We check each direct successor of skipBranch to see if:
        // 1. It's reachable from mainBranch
        // 2. The path from mainBranch is at least 2 nodes longer
        // 3. The skipBranch has only ONE successor (indicating it's a terminal node that jumps to merge)
        
        // Restriction: only consider branches with exactly one successor.
        // This ensures we only detect true "skip" patterns where a terminal node jumps
        // directly to a merge point, avoiding false positives from conditional nodes
        // that have multiple paths forward.
        if (skipBranch.succs.size() != 1) {
            return null;
        }
        
        Node potentialTarget = skipBranch.succs.get(0);
        
        // Check if mainBranch can reach this target
        Set<Node> mainReachable = new HashSet<>();
        Queue<Node> queue = new LinkedList<>();
        queue.add(mainBranch);
        while (!queue.isEmpty()) {
            Node n = queue.poll();
            if (mainReachable.contains(n)) continue;
            mainReachable.add(n);
            for (Node succ : n.succs) {
                queue.add(succ);
            }
        }
        
        if (!mainReachable.contains(potentialTarget)) {
            return null;
        }
        
        // Check if mainBranch takes significantly longer to reach the target
        int pathFromMain = shortestPathLengthTo(mainBranch, potentialTarget, new HashSet<>());
        int pathFromSkip = 1; // skipBranch -> potentialTarget directly
        
        // A skip occurs when the mainBranch takes significantly longer to reach the target.
        // We require at least 2 extra nodes to avoid detecting simple if-else merges
        // as skip patterns.
        if (pathFromMain > pathFromSkip + 1) {
            return potentialTarget;
        }
        
        return null;
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
                        // The false branch leads to exit
                        Node falseBranch = ifStruct.falseBranch;
                        if (terminalNodes.contains(falseBranch)) {
                            exitNode = falseBranch;
                        } else {
                            // Follow the path to find exit
                            Node current = falseBranch;
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
        // Find the start node (first node after entry that leads to a loop)
        Node blockStart = null;
        for (Node succ : entryNode.succs) {
            blockStart = succ;
            break;
        }
        
        if (blockStart == null || exitNode == null) {
            return null;
        }
        
        // Create the return block
        String label = RETURN_BLOCK_LABEL;
        
        // Check if this block already exists
        for (LabeledBlockStructure block : labeledBlocks) {
            if (block.label.equals(label)) {
                return block;
            }
        }
        
        // Create labeled block body - all nodes except entry
        Set<Node> body = new HashSet<>(allNodes);
        body.remove(entryNode);
        
        LabeledBlockStructure returnBlock = new LabeledBlockStructure(label, blockStart, exitNode, body);
        
        // Add break edges for each return node
        // The 'from' field is the return node itself (the node that initiates the break)
        for (Node returnNode : returnNodes) {
            returnBlock.breaks.add(new LabeledBreakEdge(returnNode, exitNode, label));
        }
        
        labeledBlocks.add(returnBlock);
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
        List<Statement> result = new ArrayList<>();
        Set<Node> visited = new HashSet<>();
        List<LoopStructure> loops = detectLoops();
        List<IfStructure> ifs = detectIfs();
        
        // Automatically detect labeled blocks for continue semantics
        detectContinueBlocks(loops);
        
        // Automatically detect labeled blocks for skip patterns
        detectSkipBlocks(ifs);
        
        // Automatically detect labeled blocks for "return" patterns (nodes with no successors)
        LabeledBlockStructure returnBlock = detectReturnBlocks(loops, ifs);
        
        // Create lookup maps for quick access
        Map<Node, LoopStructure> loopHeaders = new HashMap<>();
        for (LoopStructure loop : loops) {
            // Only add the loop with the largest body for each header (handles nested loops)
            LoopStructure existing = loopHeaders.get(loop.header);
            if (existing == null || loop.body.size() > existing.body.size()) {
                loopHeaders.put(loop.header, loop);
            }
        }
        
        Map<Node, IfStructure> ifConditions = new HashMap<>();
        for (IfStructure ifStruct : ifs) {
            ifConditions.put(ifStruct.conditionNode, ifStruct);
        }
        
        // Create lookup maps for labeled blocks (exclude return block from starts to not interfere with loops)
        Map<Node, LabeledBlockStructure> blockStarts = new HashMap<>();
        for (LabeledBlockStructure block : labeledBlocks) {
            // Don't add return block to blockStarts - it's handled specially at top level
            if (returnBlock != null && block.label.equals(RETURN_BLOCK_LABEL)) {
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
            // Check if there's any labeled block inside this loop
            // If so, breaks inside that block might need to target this loop explicitly
            for (LabeledBlockStructure block : labeledBlocks) {
                if (loop.body.contains(block.startNode)) {
                    // This labeled block is inside the loop
                    // The loop needs a label because breaks inside the block might target the loop
                    loopsNeedingLabels.add(loop.header);
                    break;
                }
            }
        }
        
        // If there's a return block, wrap output in it
        if (returnBlock != null) {
            // Output entry node first
            result.add(new ExpressionStatement(entryNode.getLabel()));
            visited.add(entryNode);
            
            // Generate the rest of the code inside the block
            List<Statement> blockBody = new ArrayList<>();
            for (Node succ : entryNode.succs) {
                blockBody.addAll(generateStatements(succ, visited, loopHeaders, ifConditions, blockStarts, labeledBreakEdges, loopsNeedingLabels, null, returnBlock, null));
            }
            
            // Add return block
            result.add(new BlockStatement(returnBlock.label, blockBody));
        } else {
            result.addAll(generateStatements(entryNode, visited, loopHeaders, ifConditions, blockStarts, labeledBreakEdges, loopsNeedingLabels, null, null, null));
        }
        
        return result;
    }



    /**
     * Finds the path of intermediate nodes from start to target.
     * Returns a list of intermediate nodes that should be output before the break statement.
     * Stops at the target node (not including it in the path).
     */
    private List<Node> findPathToTarget(Node start, Node target, Map<Node, IfStructure> ifConditions, Set<Node> stopNodes) {
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
    
    // Overload without stop nodes for backward compatibility
    private List<Node> findPathToTarget(Node start, Node target, Map<Node, IfStructure> ifConditions) {
        return findPathToTarget(start, target, ifConditions, null);
    }


    /**
     * Finds the appropriate break label for a break to a target outside the current loop.
     * Returns the loop header label with _loop suffix if breaking to an outer loop, empty string for current loop break.
     */
    private String findBreakLabel(Node breakTarget, Map<Node, LoopStructure> loopHeaders, LoopStructure currentLoop) {
        // Check which loop this target is outside of
        for (LoopStructure loop : loopHeaders.values()) {
            if (loop == currentLoop) continue;
            
            // If this loop contains the current loop and the target is outside this loop
            if (loop.body.contains(currentLoop.header) && !loop.body.contains(breakTarget)) {
                // Return loop label with _loop suffix (e.g., "outer_loop")
                return loop.header.getLabel() + "_loop";
            }
        }
        
        // Breaking out of current loop (or can't determine) - no label needed
        return "";
    }

    /**
     * Represents the result of analyzing a branch target - either a break out of the loop,
     * a break to a labeled block, or a normal flow.
     */
    private static class BranchTargetResult {
        final Node target;           // The target node (break destination or labeled block end)
        final String breakLabel;     // The break label to use (loop header or block label)
        final boolean isLabeledBlockBreak; // True if this is a break to a labeled block end node
        
        BranchTargetResult(Node target, String breakLabel, boolean isLabeledBlockBreak) {
            this.target = target;
            this.breakLabel = breakLabel;
            this.isLabeledBlockBreak = isLabeledBlockBreak;
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
        
        // Find the loop's natural exit point (the false branch of the loop condition)
        Node loopExitPoint = null;
        IfStructure loopCondIf = ifConditions.get(currentLoop.header);
        if (loopCondIf != null) {
            loopExitPoint = loopCondIf.falseBranch;
        }
        
        while (current != null && !visited.contains(current)) {
            visited.add(current);
            
            // If this is a conditional node inside the loop, stop - no break path
            if (ifConditions.containsKey(current) && currentLoop.body.contains(current)) {
                return null;
            }
            
            // Track if we've gone outside the loop
            if (!currentLoop.body.contains(current)) {
                foundOutsideLoop = true;
            }
            
            // Check if this is the loop's natural exit point
            // This should be reported as a simple break (without label for innermost loop)
            if (current.equals(loopExitPoint)) {
                return new BranchTargetResult(current, "", false);
            }
            
            // Check if this node is a labeled block's end node
            // This takes priority because it represents continue semantics
            // Only report as labeled break if the block has actual breaks that need labeling
            // But skip the return block (r_block) - that's handled specially for return nodes only
            for (LabeledBlockStructure block : labeledBlocks) {
                if (current.equals(block.endNode) && !block.breaks.isEmpty() && !block.label.equals(RETURN_BLOCK_LABEL)) {
                    // This path leads to a labeled block's end node - it's a break to that block
                    return new BranchTargetResult(current, block.label, true);
                }
            }
            
            // If we're outside the loop and hit a conditional (like outer loop header), stop
            if (foundOutsideLoop && ifConditions.containsKey(current)) {
                String breakLabel = findBreakLabel(current, loopHeaders, currentLoop);
                return new BranchTargetResult(current, breakLabel, false);
            }
            
            // If this node has no successors (end node like 'exit'), it's the target
            if (current.succs.isEmpty()) {
                if (foundOutsideLoop) {
                    // Check if this is a "return" node that should target the return block
                    // (i.e., a node with no successors that is NOT the normal loop exit target)
                    for (LabeledBlockStructure block : labeledBlocks) {
                        if (block.label.equals(RETURN_BLOCK_LABEL)) {
                            // Check if this node is a return node (has a break edge in the return block)
                            for (LabeledBreakEdge breakEdge : block.breaks) {
                                if (breakEdge.from.equals(current)) {
                                    // This is a return node - use the return block label
                                    return new BranchTargetResult(current, block.label, true);
                                }
                            }
                            break;
                        }
                    }
                    // Normal break target
                    String breakLabel = findBreakLabel(current, loopHeaders, currentLoop);
                    return new BranchTargetResult(current, breakLabel, false);
                }
                return null;
            }
            
            // If this is the loop header, stop - we've gone back to the loop condition
            if (current.equals(currentLoop.header)) {
                return null;
            }
            
            // Follow single-successor chains only (non-conditional nodes)
            if (current.succs.size() == 1) {
                current = current.succs.get(0);
            } else {
                // Multiple successors (conditional) - if we're already outside, this is the target
                if (foundOutsideLoop) {
                    String breakLabel = findBreakLabel(current, loopHeaders, currentLoop);
                    return new BranchTargetResult(current, breakLabel, false);
                }
                return null;
            }
        }
        
        return null;
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

    // ============ Statement-based pseudocode generation methods ============
    
    private List<Statement> generateStatements(Node node, Set<Node> visited,
                                               Map<Node, LoopStructure> loopHeaders, Map<Node, IfStructure> ifConditions,
                                               Map<Node, LabeledBlockStructure> blockStarts, Map<Node, LabeledBreakEdge> labeledBreakEdges,
                                               Set<Node> loopsNeedingLabels,
                                               LoopStructure currentLoop, LabeledBlockStructure currentBlock, Node stopAt) {
        List<Statement> result = new ArrayList<>();
        
        if (node == null || visited.contains(node)) {
            return result;
        }
        
        // Stop at merge node, loop exit, or block end
        if (stopAt != null && node.equals(stopAt)) {
            return result;
        }
        
        // Check if this is a labeled block start (before marking as visited)
        // Only render the block if there are actual breaks targeting it
        LabeledBlockStructure block = blockStarts.get(node);
        if (block != null && currentBlock != block && !block.breaks.isEmpty()) {
            // Generate body of the block - process the start node's content and successors
            Set<Node> blockVisited = new HashSet<>();
            List<Statement> blockBody = generateStatementsInBlock(node, blockVisited, loopHeaders, ifConditions, 
                                      labeledBreakEdges, block);
            
            result.add(new BlockStatement(block.label, blockBody));
            
            // Continue after the block
            visited.add(node);
            visited.addAll(blockVisited);
            result.addAll(generateStatements(block.endNode, visited, loopHeaders, ifConditions, 
                              blockStarts, labeledBreakEdges, loopsNeedingLabels, currentLoop, null, stopAt));
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
                
                if (breakOnTrue) {
                    onTrue.add(new BreakStatement(labeledBreak.label));
                    Set<Node> elseVisited = new HashSet<>(visited);
                    onFalse.addAll(generateStatements(ifStruct.falseBranch, elseVisited, loopHeaders, ifConditions, 
                                      blockStarts, labeledBreakEdges, loopsNeedingLabels, currentLoop, currentBlock, stopAt));
                } else {
                    Set<Node> thenVisited = new HashSet<>(visited);
                    onTrue.addAll(generateStatements(ifStruct.trueBranch, thenVisited, loopHeaders, ifConditions, 
                                      blockStarts, labeledBreakEdges, loopsNeedingLabels, currentLoop, currentBlock, stopAt));
                    onFalse.add(new BreakStatement(labeledBreak.label));
                }
                
                result.add(new IfStatement(node.getLabel(), false, onTrue, onFalse));
                return result;
            }
        }
        
        // Check if this is a loop header
        LoopStructure loop = loopHeaders.get(node);
        if (loop != null && currentLoop != loop) {
            // Find the node that continues the loop (not the exit)
            Node loopContinue = null;
            Node loopExit = null;
            for (Node succ : node.succs) {
                if (loop.body.contains(succ) && !succ.equals(node)) {
                    loopContinue = succ;
                } else if (!loop.body.contains(succ)) {
                    loopExit = succ;
                }
            }
            
            // Build loop body
            List<Statement> loopBody = new ArrayList<>();
            
            // If header has 2 successors (condition check), output the break condition first
            if (loopExit != null && node.succs.size() == 2) {
                List<Statement> breakBody = new ArrayList<>();
                breakBody.add(new BreakStatement());
                loopBody.add(new IfStatement(node.getLabel(), true, breakBody));
            } else if (node.succs.size() == 1) {
                // Do-while style: header has only 1 successor, output the header as a statement
                loopBody.add(new ExpressionStatement(node.getLabel()));
            }
            
            // Generate body of the loop
            if (loopContinue != null) {
                Set<Node> loopVisited = new HashSet<>();
                loopVisited.add(node); // Don't revisit header
                loopBody.addAll(generateStatementsInLoop(loopContinue, loopVisited, loopHeaders, ifConditions, labeledBreakEdges, blockStarts, loopsNeedingLabels, loop, currentBlock));
            }
            
            // Determine loop label
            String loopLabel = loopsNeedingLabels.contains(node) ? node.getLabel() + "_loop" : null;
            result.add(new LoopStatement(loopLabel, loopBody));
            
            // Continue after the loop
            if (loopExit != null) {
                result.addAll(generateStatements(loopExit, visited, loopHeaders, ifConditions, blockStarts, labeledBreakEdges, loopsNeedingLabels, currentLoop, currentBlock, stopAt));
            }
            return result;
        }
        
        // Check if this is an if condition
        IfStructure ifStruct = ifConditions.get(node);
        if (ifStruct != null) {
            // A) If true branch is empty (goes directly to merge) but false has content, negate condition
            boolean trueIsEmpty = ifStruct.trueBranch.equals(ifStruct.mergeNode);
            boolean falseIsEmpty = ifStruct.falseBranch.equals(ifStruct.mergeNode);
            
            if (trueIsEmpty && !falseIsEmpty) {
                // Negate condition: if (cond) {} else { X } -> if (!cond) { X }
                Set<Node> falseVisited = new HashSet<>(visited);
                List<Statement> onTrue = generateStatements(ifStruct.falseBranch, falseVisited, loopHeaders, ifConditions, blockStarts, labeledBreakEdges, loopsNeedingLabels, currentLoop, currentBlock, ifStruct.mergeNode);
                result.add(new IfStatement(node.getLabel(), true, onTrue));
                
                if (ifStruct.mergeNode != null) {
                    visited.addAll(falseVisited);
                    result.addAll(generateStatements(ifStruct.mergeNode, visited, loopHeaders, ifConditions, blockStarts, labeledBreakEdges, loopsNeedingLabels, currentLoop, currentBlock, stopAt));
                }
                return result;
            }
            
            // Standard if-else
            Set<Node> trueVisited = new HashSet<>(visited);
            List<Statement> onTrue = generateStatements(ifStruct.trueBranch, trueVisited, loopHeaders, ifConditions, blockStarts, labeledBreakEdges, loopsNeedingLabels, currentLoop, currentBlock, ifStruct.mergeNode);
            
            Set<Node> falseVisited = new HashSet<>(visited);
            List<Statement> onFalse = generateStatements(ifStruct.falseBranch, falseVisited, loopHeaders, ifConditions, blockStarts, labeledBreakEdges, loopsNeedingLabels, currentLoop, currentBlock, ifStruct.mergeNode);
            
            result.add(new IfStatement(node.getLabel(), false, onTrue, onFalse));
            
            // Continue after merge
            if (ifStruct.mergeNode != null) {
                visited.addAll(trueVisited);
                visited.addAll(falseVisited);
                result.addAll(generateStatements(ifStruct.mergeNode, visited, loopHeaders, ifConditions, blockStarts, labeledBreakEdges, loopsNeedingLabels, currentLoop, currentBlock, stopAt));
            }
            return result;
        }
        
        // Regular node - just output it
        result.add(new ExpressionStatement(node.getLabel()));
        
        // Continue with successors
        for (Node succ : node.succs) {
            result.addAll(generateStatements(succ, visited, loopHeaders, ifConditions, blockStarts, labeledBreakEdges, loopsNeedingLabels, currentLoop, currentBlock, stopAt));
        }
        
        return result;
    }
    
    private List<Statement> generateStatementsInLoop(Node node, Set<Node> visited,
                                           Map<Node, LoopStructure> loopHeaders, Map<Node, IfStructure> ifConditions,
                                           Map<Node, LabeledBreakEdge> labeledBreakEdges,
                                           Map<Node, LabeledBlockStructure> blockStarts,
                                           Set<Node> loopsNeedingLabels,
                                           LoopStructure currentLoop, LabeledBlockStructure currentBlock) {
        return generateStatementsInLoop(node, visited, loopHeaders, ifConditions, labeledBreakEdges, blockStarts, loopsNeedingLabels, currentLoop, currentBlock, null);
    }
    
    private List<Statement> generateStatementsInLoop(Node node, Set<Node> visited,
                                           Map<Node, LoopStructure> loopHeaders, Map<Node, IfStructure> ifConditions,
                                           Map<Node, LabeledBreakEdge> labeledBreakEdges,
                                           Map<Node, LabeledBlockStructure> blockStarts,
                                           Set<Node> loopsNeedingLabels,
                                           LoopStructure currentLoop, LabeledBlockStructure currentBlock, Node stopAt) {
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
        
        // Check if this is a labeled block start (before marking as visited)
        // Only render the block if there are actual breaks targeting it
        LabeledBlockStructure block = blockStarts.get(node);
        if (block != null && currentBlock != block && !block.breaks.isEmpty()) {
            // Generate body of the block within the loop
            Set<Node> blockVisited = new HashSet<>();
            List<Statement> blockBody = generateStatementsInLoop(node, blockVisited, loopHeaders, ifConditions, 
                                     labeledBreakEdges, blockStarts, loopsNeedingLabels, currentLoop, block);
            
            result.add(new BlockStatement(block.label, blockBody));
            
            // Continue after the block (with the end node)
            visited.addAll(blockVisited);
            if (currentLoop.body.contains(block.endNode)) {
                result.addAll(generateStatementsInLoop(block.endNode, visited, loopHeaders, ifConditions, 
                                        labeledBreakEdges, blockStarts, loopsNeedingLabels, currentLoop, null));
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
                breakBody.add(new BreakStatement());
                loopBody.add(new IfStatement(node.getLabel(), true, breakBody));
            }
            
            if (loopContinue != null) {
                Set<Node> nestedVisited = new HashSet<>();
                nestedVisited.add(node);
                loopBody.addAll(generateStatementsInLoop(loopContinue, nestedVisited, loopHeaders, ifConditions, labeledBreakEdges, blockStarts, loopsNeedingLabels, nestedLoop, currentBlock));
            }
            
            // Determine loop label
            String loopLabel = loopsNeedingLabels.contains(node) ? node.getLabel() + "_loop" : null;
            result.add(new LoopStatement(loopLabel, loopBody));
            
            if (loopExit != null && currentLoop.body.contains(loopExit)) {
                result.addAll(generateStatementsInLoop(loopExit, visited, loopHeaders, ifConditions, labeledBreakEdges, blockStarts, loopsNeedingLabels, currentLoop, currentBlock));
            }
            return result;
        }
        
        // Check for labeled break first (higher priority than regular break)
        LabeledBreakEdge labeledBreak = labeledBreakEdges.get(node);
        if (labeledBreak != null) {
            IfStructure ifStruct = ifConditions.get(node);
            if (ifStruct != null) {
                // Determine which branch is the labeled break
                boolean breakOnTrue = ifStruct.trueBranch.equals(labeledBreak.to);
                boolean breakOnFalse = ifStruct.falseBranch.equals(labeledBreak.to);
                
                // A) & B) Apply optimizations: negate and flatten
                if (breakOnTrue) {
                    // B) True branch is break - output break first with condition, then flatten false branch
                    List<Statement> breakBody = new ArrayList<>();
                    breakBody.add(new BreakStatement(labeledBreak.label));
                    result.add(new IfStatement(node.getLabel(), false, breakBody));
                    
                    Set<Node> elseVisited = new HashSet<>(visited);
                    elseVisited.add(node);
                    // Check if false branch goes outside the loop (break to outer loop)
                    if (!currentLoop.body.contains(ifStruct.falseBranch)) {
                        result.addAll(generateBreakOrNodeStatements(ifStruct.falseBranch, loopHeaders, currentLoop));
                    } else {
                        result.addAll(generateStatementsInLoop(ifStruct.falseBranch, elseVisited, loopHeaders, ifConditions, labeledBreakEdges, blockStarts, loopsNeedingLabels, currentLoop, currentBlock));
                    }
                } else if (breakOnFalse) {
                    // A) False branch is break - negate condition and flatten
                    List<Statement> breakBody = new ArrayList<>();
                    breakBody.add(new BreakStatement(labeledBreak.label));
                    result.add(new IfStatement(node.getLabel(), true, breakBody));
                    
                    Set<Node> thenVisited = new HashSet<>(visited);
                    thenVisited.add(node);
                    // Check if true branch goes outside the loop (break to outer loop)
                    if (!currentLoop.body.contains(ifStruct.trueBranch)) {
                        result.addAll(generateBreakOrNodeStatements(ifStruct.trueBranch, loopHeaders, currentLoop));
                    } else {
                        result.addAll(generateStatementsInLoop(ifStruct.trueBranch, thenVisited, loopHeaders, ifConditions, labeledBreakEdges, blockStarts, loopsNeedingLabels, currentLoop, currentBlock));
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
                        onTrue.addAll(generateStatementsInLoop(ifStruct.trueBranch, thenVisited, loopHeaders, ifConditions, labeledBreakEdges, blockStarts, loopsNeedingLabels, currentLoop, currentBlock));
                    }
                    
                    List<Statement> onFalse = new ArrayList<>();
                    Set<Node> elseVisited = new HashSet<>(visited);
                    elseVisited.add(node);
                    // Check if false branch goes outside the loop
                    if (!currentLoop.body.contains(ifStruct.falseBranch)) {
                        onFalse.addAll(generateBreakOrNodeStatements(ifStruct.falseBranch, loopHeaders, currentLoop));
                    } else {
                        onFalse.addAll(generateStatementsInLoop(ifStruct.falseBranch, elseVisited, loopHeaders, ifConditions, labeledBreakEdges, blockStarts, loopsNeedingLabels, currentLoop, currentBlock));
                    }
                    
                    result.add(new IfStatement(node.getLabel(), false, onTrue, onFalse));
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
                        List<Node> falsePath = findPathToTarget(ifStruct.falseBranch, falseBranchTarget.target, ifConditions);
                        List<Statement> breakBody = outputPathAndBreakStatements(falsePath, falseBranchTarget.breakLabel, currentLoop, currentBlock, falseBranchTarget.target);
                        result.add(new IfStatement(node.getLabel(), true, breakBody));
                        return result;
                    }
                    
                    // A) If false branch is continue (loop header) and true branch is break, keep normal
                    if (falseBranchIsLoopHeader && trueBranchTarget != null) {
                        List<Node> path = findPathToTarget(ifStruct.trueBranch, trueBranchTarget.target, ifConditions);
                        List<Statement> breakBody = outputPathAndBreakStatements(path, trueBranchTarget.breakLabel, currentLoop, currentBlock, trueBranchTarget.target);
                        result.add(new IfStatement(node.getLabel(), false, breakBody));
                        return result;
                    }
                    
                    // C) Special case: true branch breaks to labeled block, false branch breaks to loop
                    if (trueBranchTarget != null && trueBranchTarget.isLabeledBlockBreak && 
                        falseBranchTarget != null && !falseBranchTarget.isLabeledBlockBreak) {
                        List<Node> falsePath = findPathToTarget(ifStruct.falseBranch, falseBranchTarget.target, ifConditions);
                        List<Statement> breakBody = outputPathAndBreakStatements(falsePath, falseBranchTarget.breakLabel, currentLoop, currentBlock, falseBranchTarget.target);
                        result.add(new IfStatement(node.getLabel(), true, breakBody));
                        // Continue with true branch content at same indent level (flattened), without the final break
                        List<Node> truePath = findPathToTarget(ifStruct.trueBranch, trueBranchTarget.target, ifConditions);
                        for (Node n : truePath) {
                            result.add(new ExpressionStatement(n.getLabel()));
                        }
                        return result;
                    }
                    
                    // B) If true branch leads to break, flatten the else
                    if (trueBranchTarget != null) {
                        List<Node> path = findPathToTarget(ifStruct.trueBranch, trueBranchTarget.target, ifConditions);
                        List<Statement> breakBody = outputPathAndBreakStatements(path, trueBranchTarget.breakLabel, currentLoop, currentBlock, trueBranchTarget.target);
                        result.add(new IfStatement(node.getLabel(), false, breakBody));
                        
                        // Continue with false branch at same indent level (flattened)
                        if (falseBranchTarget != null) {
                            List<Node> falsePath = findPathToTarget(ifStruct.falseBranch, falseBranchTarget.target, ifConditions);
                            result.addAll(outputPathAndBreakStatements(falsePath, falseBranchTarget.breakLabel, currentLoop, currentBlock, falseBranchTarget.target));
                        } else {
                            Set<Node> falseVisited = new HashSet<>(visited);
                            falseVisited.add(node);
                            result.addAll(generateStatementsInLoop(ifStruct.falseBranch, falseVisited, loopHeaders, ifConditions, labeledBreakEdges, blockStarts, loopsNeedingLabels, currentLoop, currentBlock));
                        }
                        return result;
                    }
                    
                    // Standard handling - false branch is break, true branch continues
                    if (falseBranchTarget != null) {
                        List<Node> falsePath = findPathToTarget(ifStruct.falseBranch, falseBranchTarget.target, ifConditions);
                        List<Statement> breakBody = outputPathAndBreakStatements(falsePath, falseBranchTarget.breakLabel, currentLoop, currentBlock, falseBranchTarget.target);
                        result.add(new IfStatement(node.getLabel(), true, breakBody));
                        // Continue with true branch at same indent level (flattened)
                        Set<Node> thenVisited = new HashSet<>(visited);
                        thenVisited.add(node);
                        result.addAll(generateStatementsInLoop(ifStruct.trueBranch, thenVisited, loopHeaders, ifConditions, labeledBreakEdges, blockStarts, loopsNeedingLabels, currentLoop, currentBlock));
                        return result;
                    }
                    
                    // Fallback to original logic
                    if (!currentLoop.body.contains(ifStruct.trueBranch)) {
                        List<Statement> onTrue = new ArrayList<>();
                        onTrue.add(new BreakStatement());
                        Set<Node> elseVisited = new HashSet<>(visited);
                        elseVisited.add(node);
                        List<Statement> onFalse = generateStatementsInLoop(ifStruct.falseBranch, elseVisited, loopHeaders, ifConditions, labeledBreakEdges, blockStarts, loopsNeedingLabels, currentLoop, currentBlock);
                        result.add(new IfStatement(node.getLabel(), false, onTrue, onFalse));
                    } else {
                        Set<Node> thenVisited = new HashSet<>(visited);
                        thenVisited.add(node);
                        List<Statement> onTrue = generateStatementsInLoop(ifStruct.trueBranch, thenVisited, loopHeaders, ifConditions, labeledBreakEdges, blockStarts, loopsNeedingLabels, currentLoop, currentBlock);
                        List<Statement> onFalse = new ArrayList<>();
                        onFalse.add(new BreakStatement());
                        result.add(new IfStatement(node.getLabel(), false, onTrue, onFalse));
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
                        continueBody.add(new ContinueStatement());
                        result.add(new IfStatement(node.getLabel(), false, continueBody));
                        // Continue with false branch flattened
                        Set<Node> elseVisited = new HashSet<>(visited);
                        elseVisited.add(node);
                        result.addAll(generateStatementsInLoop(ifStruct.falseBranch, elseVisited, loopHeaders, ifConditions, labeledBreakEdges, blockStarts, loopsNeedingLabels, currentLoop, currentBlock));
                    } else {
                        // False branch is continue - negate condition
                        List<Statement> continueBody = new ArrayList<>();
                        continueBody.add(new ContinueStatement());
                        result.add(new IfStatement(node.getLabel(), true, continueBody));
                        // Continue with true branch flattened
                        Set<Node> thenVisited = new HashSet<>(visited);
                        thenVisited.add(node);
                        result.addAll(generateStatementsInLoop(ifStruct.trueBranch, thenVisited, loopHeaders, ifConditions, labeledBreakEdges, blockStarts, loopsNeedingLabels, currentLoop, currentBlock));
                    }
                    return result;
                }
            }
        }
        
        visited.add(node);
        
        // Check if this is an if condition inside the loop
        IfStructure ifStruct = ifConditions.get(node);
        if (ifStruct != null) {
            // Check if true branch leads to a break
            BranchTargetResult trueBranchTarget = findBranchTarget(ifStruct.trueBranch, currentLoop, ifConditions, loopHeaders);
            BranchTargetResult falseBranchTarget = findBranchTarget(ifStruct.falseBranch, currentLoop, ifConditions, loopHeaders);
            
            boolean trueIsEmpty = ifStruct.trueBranch.equals(ifStruct.mergeNode) || 
                                  (!currentLoop.body.contains(ifStruct.trueBranch) && trueBranchTarget == null);
            boolean falseIsEmpty = ifStruct.falseBranch.equals(ifStruct.mergeNode) || 
                                   (!currentLoop.body.contains(ifStruct.falseBranch) && falseBranchTarget == null);
            
            if (trueIsEmpty && !falseIsEmpty) {
                // Negate condition
                List<Statement> onTrue = new ArrayList<>();
                if (falseBranchTarget != null) {
                    List<Node> path = findPathToTarget(ifStruct.falseBranch, falseBranchTarget.target, ifConditions);
                    onTrue.addAll(outputPathAndBreakStatements(path, falseBranchTarget.breakLabel, currentLoop, currentBlock, falseBranchTarget.target));
                } else {
                    Set<Node> falseVisited = new HashSet<>(visited);
                    onTrue.addAll(generateStatementsInLoop(ifStruct.falseBranch, falseVisited, loopHeaders, ifConditions, labeledBreakEdges, blockStarts, loopsNeedingLabels, currentLoop, currentBlock, ifStruct.mergeNode));
                }
                result.add(new IfStatement(node.getLabel(), true, onTrue));
                
                if (ifStruct.mergeNode != null && currentLoop.body.contains(ifStruct.mergeNode)) {
                    Set<Node> mergeVisited = new HashSet<>(visited);
                    result.addAll(generateStatementsInLoop(ifStruct.mergeNode, mergeVisited, loopHeaders, ifConditions, labeledBreakEdges, blockStarts, loopsNeedingLabels, currentLoop, currentBlock, stopAt));
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
                        onTrue.add(new ExpressionStatement(n.getLabel()));
                    }
                    result.add(new IfStatement(node.getLabel(), false, onTrue));
                    List<Node> falsePath = findPathToTarget(ifStruct.falseBranch, falseBranchTarget.target, ifConditions);
                    result.addAll(outputPathAndBreakStatements(falsePath, falseBranchTarget.breakLabel, currentLoop, currentBlock, falseBranchTarget.target));
                    return result;
                }
                
                // D) Special case: true branch breaks to labeled block, false branch breaks to loop
                if (trueBranchTarget.isLabeledBlockBreak && falseBranchTarget != null && !falseBranchTarget.isLabeledBlockBreak) {
                    List<Node> falsePath = findPathToTarget(ifStruct.falseBranch, falseBranchTarget.target, ifConditions);
                    List<Statement> breakBody = outputPathAndBreakStatements(falsePath, falseBranchTarget.breakLabel, currentLoop, currentBlock, falseBranchTarget.target);
                    result.add(new IfStatement(node.getLabel(), true, breakBody));
                    List<Node> truePath = findPathToTarget(ifStruct.trueBranch, trueBranchTarget.target, ifConditions);
                    for (Node n : truePath) {
                        result.add(new ExpressionStatement(n.getLabel()));
                    }
                    return result;
                }
                
                List<Node> path = findPathToTarget(ifStruct.trueBranch, trueBranchTarget.target, ifConditions);
                List<Statement> breakBody = outputPathAndBreakStatements(path, trueBranchTarget.breakLabel, currentLoop, currentBlock, trueBranchTarget.target);
                result.add(new IfStatement(node.getLabel(), false, breakBody));
                
                // Continue with false branch
                if (stopAt != null && (ifStruct.falseBranch.equals(stopAt) || isReachableWithinLoop(ifStruct.falseBranch, stopAt, currentLoop))) {
                    List<Node> pathToStop = findPathToNode(ifStruct.falseBranch, stopAt, ifConditions, currentLoop);
                    for (Node n : pathToStop) {
                        result.add(new ExpressionStatement(n.getLabel()));
                    }
                } else if (falseBranchTarget != null) {
                    List<Node> falsePath = findPathToTarget(ifStruct.falseBranch, falseBranchTarget.target, ifConditions);
                    if (falseBranchTarget.isLabeledBlockBreak && currentBlock != null && 
                        falseBranchTarget.target.equals(currentBlock.endNode)) {
                        for (Node n : falsePath) {
                            result.add(new ExpressionStatement(n.getLabel()));
                        }
                    } else {
                        result.addAll(outputPathAndBreakStatements(falsePath, falseBranchTarget.breakLabel, currentLoop, currentBlock, falseBranchTarget.target));
                    }
                } else if (currentLoop.body.contains(ifStruct.falseBranch)) {
                    Set<Node> falseVisited = new HashSet<>(visited);
                    result.addAll(generateStatementsInLoop(ifStruct.falseBranch, falseVisited, loopHeaders, ifConditions, labeledBreakEdges, blockStarts, loopsNeedingLabels, currentLoop, currentBlock, ifStruct.mergeNode));
                    
                    if (ifStruct.mergeNode != null && currentLoop.body.contains(ifStruct.mergeNode)) {
                        result.addAll(generateStatementsInLoop(ifStruct.mergeNode, falseVisited, loopHeaders, ifConditions, labeledBreakEdges, blockStarts, loopsNeedingLabels, currentLoop, currentBlock, stopAt));
                    }
                }
                return result;
            }
            
            // C) false branch reachable from true branch
            if (trueBranchTarget == null && falseBranchTarget != null &&
                isReachableWithinLoop(ifStruct.trueBranch, ifStruct.falseBranch, currentLoop)) {
                Set<Node> trueVisited = new HashSet<>(visited);
                List<Statement> onTrue = generateStatementsInLoop(ifStruct.trueBranch, trueVisited, loopHeaders, ifConditions, labeledBreakEdges, blockStarts, loopsNeedingLabels, currentLoop, currentBlock, ifStruct.falseBranch);
                result.add(new IfStatement(node.getLabel(), false, onTrue));
                List<Node> falsePath = findPathToTarget(ifStruct.falseBranch, falseBranchTarget.target, ifConditions);
                if (falseBranchTarget.isLabeledBlockBreak && currentBlock != null && 
                    falseBranchTarget.target.equals(currentBlock.endNode)) {
                    for (Node n : falsePath) {
                        result.add(new ExpressionStatement(n.getLabel()));
                    }
                } else {
                    result.addAll(outputPathAndBreakStatements(falsePath, falseBranchTarget.breakLabel, currentLoop, currentBlock, falseBranchTarget.target));
                }
                return result;
            }
            
            // Standard if-else
            Set<Node> trueVisited = new HashSet<>(visited);
            List<Statement> onTrue = generateStatementsInLoop(ifStruct.trueBranch, trueVisited, loopHeaders, ifConditions, labeledBreakEdges, blockStarts, loopsNeedingLabels, currentLoop, currentBlock, ifStruct.mergeNode);
            
            List<Statement> onFalse = new ArrayList<>();
            if (falseBranchTarget != null) {
                List<Node> path = findPathToTarget(ifStruct.falseBranch, falseBranchTarget.target, ifConditions);
                onFalse.addAll(outputPathAndBreakStatements(path, falseBranchTarget.breakLabel, currentLoop, currentBlock, falseBranchTarget.target));
            } else {
                Set<Node> falseVisited = new HashSet<>(visited);
                onFalse.addAll(generateStatementsInLoop(ifStruct.falseBranch, falseVisited, loopHeaders, ifConditions, labeledBreakEdges, blockStarts, loopsNeedingLabels, currentLoop, currentBlock, ifStruct.mergeNode));
            }
            
            result.add(new IfStatement(node.getLabel(), false, onTrue, onFalse));
            
            if (ifStruct.mergeNode != null && currentLoop.body.contains(ifStruct.mergeNode)) {
                Set<Node> mergeVisited = new HashSet<>(visited);
                result.addAll(generateStatementsInLoop(ifStruct.mergeNode, mergeVisited, loopHeaders, ifConditions, labeledBreakEdges, blockStarts, loopsNeedingLabels, currentLoop, currentBlock, stopAt));
            }
            return result;
        }
        
        // Regular node
        result.add(new ExpressionStatement(node.getLabel()));
        
        // Continue with successors inside the loop
        for (Node succ : node.succs) {
            if (currentLoop.body.contains(succ) && !succ.equals(currentLoop.header)) {
                result.addAll(generateStatementsInLoop(succ, visited, loopHeaders, ifConditions, labeledBreakEdges, blockStarts, loopsNeedingLabels, currentLoop, currentBlock, stopAt));
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
            result.add(new BreakStatement(targetLoop.header.getLabel() + "_loop"));
        } else {
            result.add(new ExpressionStatement(node.getLabel()));
        }
        
        return result;
    }

    private List<Statement> outputPathAndBreakStatements(List<Node> path, String breakLabel,
                                                         LoopStructure currentLoop, LabeledBlockStructure currentBlock, Node target) {
        List<Statement> result = new ArrayList<>();
        
        for (Node n : path) {
            result.add(new ExpressionStatement(n.getLabel()));
        }
        
        // Check if target is a return node
        if (target != null && target.succs.isEmpty()) {
            boolean isReturnNode = false;
            for (LabeledBlockStructure block : labeledBlocks) {
                if (block.label.equals(RETURN_BLOCK_LABEL)) {
                    for (LabeledBreakEdge breakEdge : block.breaks) {
                        if (breakEdge.from.equals(target)) {
                            isReturnNode = true;
                            break;
                        }
                    }
                    break;
                }
            }
            if (isReturnNode) {
                result.add(new ExpressionStatement(target.getLabel()));
            }
        }
        
        if (breakLabel != null && !breakLabel.isEmpty()) {
            result.add(new BreakStatement(breakLabel));
        } else if (currentBlock != null && currentLoop != null) {
            result.add(new BreakStatement(currentLoop.header.getLabel() + "_loop"));
        } else {
            result.add(new BreakStatement());
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
                
                if (breakOnTrue) {
                    List<Statement> breakBody = new ArrayList<>();
                    breakBody.add(new BreakStatement(labeledBreak.label));
                    result.add(new IfStatement(node.getLabel(), false, breakBody));
                    Set<Node> elseVisited = new HashSet<>(visited);
                    elseVisited.add(node);
                    result.addAll(generateStatementsInBlock(ifStruct.falseBranch, elseVisited, loopHeaders, ifConditions, 
                                              labeledBreakEdges, currentBlock));
                } else {
                    List<Statement> breakBody = new ArrayList<>();
                    breakBody.add(new BreakStatement(labeledBreak.label));
                    result.add(new IfStatement(node.getLabel(), true, breakBody));
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
                Set<Node> falseVisited = new HashSet<>(visited);
                List<Statement> onTrue = generateStatementsInBlock(ifStruct.falseBranch, falseVisited, loopHeaders, ifConditions, 
                                          labeledBreakEdges, currentBlock, internalMerge);
                result.add(new IfStatement(node.getLabel(), true, onTrue));
                
                if (internalMerge != null && currentBlock.body.contains(internalMerge)) {
                    visited.addAll(falseVisited);
                    result.addAll(generateStatementsInBlock(internalMerge, visited, loopHeaders, ifConditions, 
                                              labeledBreakEdges, currentBlock));
                }
                return result;
            }
            
            if (trueBranchExits && !falseIsEmpty) {
                Set<Node> trueVisited = new HashSet<>(visited);
                List<Statement> onTrue = generateStatementsInBlock(ifStruct.trueBranch, trueVisited, loopHeaders, ifConditions, 
                                          labeledBreakEdges, currentBlock, internalMerge);
                result.add(new IfStatement(node.getLabel(), false, onTrue));
                Set<Node> falseVisited = new HashSet<>(visited);
                falseVisited.addAll(trueVisited);
                result.addAll(generateStatementsInBlock(ifStruct.falseBranch, falseVisited, loopHeaders, ifConditions, 
                                          labeledBreakEdges, currentBlock, internalMerge));
                
                if (internalMerge != null && currentBlock.body.contains(internalMerge)) {
                    visited.addAll(falseVisited);
                    result.addAll(generateStatementsInBlock(internalMerge, visited, loopHeaders, ifConditions, 
                                              labeledBreakEdges, currentBlock, stopAt));
                }
                return result;
            }
            
            if (falseBranchExits && !trueIsEmpty) {
                Set<Node> falseVisited = new HashSet<>(visited);
                List<Statement> onTrue = generateStatementsInBlock(ifStruct.falseBranch, falseVisited, loopHeaders, ifConditions, 
                                          labeledBreakEdges, currentBlock, internalMerge);
                result.add(new IfStatement(node.getLabel(), true, onTrue));
                Set<Node> trueVisited = new HashSet<>(visited);
                trueVisited.addAll(falseVisited);
                result.addAll(generateStatementsInBlock(ifStruct.trueBranch, trueVisited, loopHeaders, ifConditions, 
                                          labeledBreakEdges, currentBlock, internalMerge));
                
                if (internalMerge != null && currentBlock.body.contains(internalMerge)) {
                    visited.addAll(trueVisited);
                    result.addAll(generateStatementsInBlock(internalMerge, visited, loopHeaders, ifConditions, 
                                              labeledBreakEdges, currentBlock, stopAt));
                }
                return result;
            }
            
            if (falseIsEmpty && !trueIsEmpty) {
                Set<Node> trueVisited = new HashSet<>(visited);
                List<Statement> onTrue = generateStatementsInBlock(ifStruct.trueBranch, trueVisited, loopHeaders, ifConditions, 
                                          labeledBreakEdges, currentBlock, internalMerge);
                result.add(new IfStatement(node.getLabel(), false, onTrue));
                
                if (internalMerge != null && currentBlock.body.contains(internalMerge)) {
                    visited.addAll(trueVisited);
                    result.addAll(generateStatementsInBlock(internalMerge, visited, loopHeaders, ifConditions, 
                                              labeledBreakEdges, currentBlock));
                }
                return result;
            }
            
            // Standard if-else
            Set<Node> trueVisited = new HashSet<>(visited);
            List<Statement> onTrue = generateStatementsInBlock(ifStruct.trueBranch, trueVisited, loopHeaders, ifConditions, 
                                      labeledBreakEdges, currentBlock, internalMerge);
            
            Set<Node> falseVisited = new HashSet<>(visited);
            List<Statement> onFalse = generateStatementsInBlock(ifStruct.falseBranch, falseVisited, loopHeaders, ifConditions, 
                                      labeledBreakEdges, currentBlock, internalMerge);
            
            result.add(new IfStatement(node.getLabel(), false, onTrue, onFalse));
            
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
                result.add(new ExpressionStatement(node.getLabel()));
                if (stopAt != null) {
                    result.add(new BreakStatement(currentBlock.label));
                }
                return result;
            }
            if (!currentBlock.body.contains(succ)) {
                leadsOutside = true;
            }
        }
        
        // Regular node
        result.add(new ExpressionStatement(node.getLabel()));
        
        for (Node succ : node.succs) {
            if (currentBlock.body.contains(succ) && (stopAt == null || !succ.equals(stopAt))) {
                result.addAll(generateStatementsInBlock(succ, visited, loopHeaders, ifConditions, 
                                          labeledBreakEdges, currentBlock, stopAt));
            } else if (leadsOutside && succ.equals(currentBlock.endNode)) {
                result.add(new BreakStatement(currentBlock.label));
            }
        }
        
        return result;
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
        
        // Auto-detect labeled blocks for continue semantics
        detectContinueBlocks(loops);
        
        // Auto-detect labeled blocks for skip patterns (outside of loops)
        detectSkipBlocks(ifs);
        
        System.out.println("Labeled Block Structures (" + labeledBlocks.size() + "):");
        for (LabeledBlockStructure block : labeledBlocks) {
            System.out.println("  " + block);
        }
    }

    /**
     * Demonstration with example CFGs.
     */
    /**
     * Runs an example with the given DOT source and description.
     * Creates a StructureDetector, analyzes it, and prints pseudocode and graphviz output.
     * 
     * @param description the description of the example
     * @param dot the DOT/Graphviz source code
     */
    private static void runExample(String description, String dot) {
        System.out.println("===== " + description + " =====");
        StructureDetector detector = StructureDetector.fromGraphviz(dot);
        detector.analyze();
        System.out.println("\n--- Pseudocode ---");
        System.out.println(detector.toPseudocode());
        System.out.println("--- Graphviz/DOT ---");
        System.out.println(detector.toGraphviz());
    }
    
    /**
     * Runs an example with the given DOT source and description, also printing detected structures.
     * 
     * @param description the description of the example
     * @param dot the DOT/Graphviz source code
     * @param printStructures if true, prints detected if, loop, and labeled block structures
     */
    private static void runExample(String description, String dot, boolean printStructures) {
        System.out.println("===== " + description + " =====");
        StructureDetector detector = StructureDetector.fromGraphviz(dot);
        detector.analyze();
        
        if (printStructures) {
            System.out.println("\n--- Detected Structures ---");
            System.out.println("If Structures: " + detector.detectIfs().size());
            for (IfStructure s : detector.detectIfs()) {
                System.out.println("  " + s);
            }
            System.out.println("Loop Structures: " + detector.detectLoops().size());
            for (LoopStructure s : detector.detectLoops()) {
                System.out.println("  " + s);
            }
            System.out.println("Labeled Block Structures: " + detector.getLabeledBlocks().size());
            for (LabeledBlockStructure s : detector.getLabeledBlocks()) {
                System.out.println("  " + s);
            }
        }
        
        System.out.println("\n--- Pseudocode ---");
        System.out.println(detector.toPseudocode());
        System.out.println("--- Graphviz/DOT ---");
        System.out.println(detector.toGraphviz());
    }

    /**
     * Demonstration with example CFGs.
     */
    public static void main(String[] args) {
        // Example 1: Simple if-else
        runExample("Example 1: Simple If-Else",
            "digraph {\n" +
            "  entry->if_cond;\n" +
            "  if_cond->then;\n" +
            "  if_cond->else;\n" +
            "  then->merge;\n" +
            "  else->merge;\n" +
            "  merge->exit;\n" +
            "}"
        );
        
        // Example 2: While loop
        System.out.println();
        runExample("Example 2: While Loop",
            "digraph {\n" +
            "  entry->loop_header;\n" +
            "  loop_header->loop_body;\n" +
            "  loop_header->exit;\n" +
            "  loop_body->loop_header;\n" +
            "}"
        );
        
        // Example 3: Loop with break and continue
        System.out.println();
        runExample("Example 3: Loop with Break and Continue",
            "digraph {\n" +
            "  entry->loop_header;\n" +
            "  loop_header->body_1;\n" +
            "  loop_header->exit;\n" +
            "  body_1->cond_break;\n" +
            "  cond_break->body_2;\n" +
            "  cond_break->exit;\n" +
            "  body_2->cond_continue;\n" +
            "  cond_continue->body_3;\n" +
            "  cond_continue->loop_header;\n" +
            "  body_3->loop_header;\n" +
            "}"
        );
        
        // Example 4: Nested loops
        System.out.println();
        runExample("Example 4: Nested Loops",
            "digraph {\n" +
            "  entry->outer_header;\n" +
            "  outer_header->inner_header;\n" +
            "  outer_header->exit;\n" +
            "  inner_header->inner_body;\n" +
            "  inner_header->outer_end;\n" +
            "  inner_body->inner_header;\n" +
            "  outer_end->outer_header;\n" +
            "}"
        );
        
        // Example 5: If inside loop
        System.out.println();
        runExample("Example 5: If Inside Loop",
            "digraph {\n" +
            "  entry->loop_header;\n" +
            "  loop_header->if_cond;\n" +
            "  loop_header->exit;\n" +
            "  if_cond->if_then;\n" +
            "  if_cond->if_else;\n" +
            "  if_then->loop_end;\n" +
            "  if_else->loop_end;\n" +
            "  loop_end->loop_header;\n" +
            "}"
        );
        
        // Example 6: Chained edges (demonstrating a->b->c syntax)
        System.out.println();
        runExample("Example 6: Chained Edges",
            "digraph {\n" +
            "  start->if1;\n" +
            "  if1->ontrue;\n" +
            "  if1->onfalse;\n" +
            "  ontrue->merge;\n" +
            "  onfalse->merge;\n" +
            "  merge->after->exit;\n" +
            "}"
        );
        
        // Example 7: Complex nested loops with labeled breaks and returns
        System.out.println();
        runExample("Example 7: Complex Nested Loops with Labeled Breaks",
            "digraph {\n" +
            "  entry->loop_a_cond;\n" +
            "  loop_a_cond->loop_b_cond;\n" +
            "  loop_a_cond->exit;\n" +
            "  loop_b_cond->inner_cond;\n" +
            "  loop_b_cond->trace_hello;\n" +
            "  inner_cond->check_e9;\n" +
            "  inner_cond->inc_d;\n" +
            "  trace_hello->my_cont;\n" +
            "  check_e9->trace_X;\n" +
            "  check_e9->ifr;\n" +
            "  ifr->check_e20;\n" +
            "  ifr->r1;\n" +
            "  trace_X->trace_hello;\n" +
            "  check_e20->trace_Y;\n" +
            "  check_e20->ifr2;\n" +
            "  ifr2->check_e8;\n" +
            "  ifr2->r2;\n" +
            "  trace_Y->my_cont;\n" +
            "  check_e8->trace_Z;\n" +
            "  check_e8->trace_BA;\n" +
            "  trace_Z->inc_d;\n" +
            "  trace_BA->exit;\n" +
            "  inc_d->loop_b_cond;\n" +
            "  my_cont->ternar;\n" +
            "  ternar->pre_inc_a;\n" +
            "  ternar->pre_inc_b;\n" +
            "  pre_inc_a->inc_c;\n" +
            "  pre_inc_b->inc_c;\n" +
            "  inc_c->loop_a_cond;\n" +
            "}"
        );
        
        // Example 8: Do-while style loop (body first, then condition)
        System.out.println();
        runExample("Example 8: Do-While Style Loop",
            "digraph {\n" +
            "  entry->body;\n" +
            "  body->cond;\n" +
            "  cond->body;\n" +
            "  cond->exit;\n" +
            "}"
        );

        // Example 9: Labeled block with nested if-statements
        System.out.println();
        runExample("Example 9: Labeled Block with Nested Ifs",
            "digraph {\n" +
            "  start->ifa;\n" +
            "  ifa->x;\n" +
            "  ifa->A1;\n" +
            "  x->ifc;\n" +
            "  ifc->y;\n" +
            "  ifc->z;\n" +
            "  y->A2;\n" +
            "  z->A1;\n" +
            "  A1->d;\n" +
            "  d->A2;\n" +
            "  A2->end;\n" +
            "}",
            true
        );

        // Example 10: Two nested while loops with labeled blocks inside
        System.out.println();
        runExample("Example 10: Nested Ifs with While Loop",
            "digraph {\n" +
            "  start->ifa;\n" +
            "  ifa->x;\n" +
            "  ifa->A1;\n" +
            "  x->ifc;\n" +
            "  ifc->y;\n" +
            "  ifc->z;\n" +
            "  y->A2;\n" +
            "  z->A1;\n" +
            "  A1->d;\n" +
            "  d->A2;\n" +
            "  A2->start2;\n" +
            "  start2->ifex2;\n" +
            "  ifex2->end;\n" +
            "  ifex2->ifa2;\n" +
            "  ifa2->x2;\n" +
            "  ifa2->A12;\n" +
            "  x2->ifc2;\n" +
            "  ifc2->y2;\n" +
            "  ifc2->z2;\n" +
            "  y2->A22;\n" +
            "  z2->A12;\n" +
            "  A12->d2;\n" +
            "  d2->A22;\n" +
            "  A22->start3;\n" +
            "  start3->ifex3;\n" +
            "  ifex3->end;\n" +
            "  ifex3->ifa3;\n" +
            "  ifa3->x3;\n" +
            "  ifa3->A13;\n" +
            "  x3->ifc3;\n" +
            "  ifc3->y3;\n" +
            "  ifc3->z3;\n" +
            "  y3->A23;\n" +
            "  z3->A13;\n" +
            "  A13->d3;\n" +
            "  d3->A23;\n" +
            "  A23->start2;\n" +
            "}",
            true
        );
    }
}
