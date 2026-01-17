/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Main.java to edit this template
 */
package structure_detector;

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
        Set<Node> body = new HashSet<>();
        // Collect all nodes in the block (reachable from start but before end)
        collectBlockBody(startNode, endNode, body);
        
        LabeledBlockStructure block = new LabeledBlockStructure(label, startNode, endNode, body);
        
        // Detect breaks within the block (edges that go to endNode from within the block)
        for (Node node : body) {
            for (Node succ : node.succs) {
                if (succ.equals(endNode)) {
                    block.breaks.add(new LabeledBreakEdge(node, endNode, label));
                }
            }
        }
        
        labeledBlocks.add(block);
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
     */
    private Node findMergeNode(Node branch1, Node branch2) {
        // Collect all nodes reachable from branch1
        Set<Node> reachableFromBranch1 = getReachableNodes(branch1);
        
        // Find the first common node reachable from branch2 that is also reachable from branch1
        Queue<Node> queue = new LinkedList<>();
        Set<Node> visited = new HashSet<>();
        queue.add(branch2);
        visited.add(branch2);
        
        // Check if branch2 itself is reachable from branch1
        if (reachableFromBranch1.contains(branch2)) {
            return branch2;
        }
        // Check if branch1 is reachable from branch2 using early-exit search
        if (isReachable(branch2, branch1)) {
            return branch1;
        }
        
        while (!queue.isEmpty()) {
            Node current = queue.poll();
            for (Node succ : current.succs) {
                if (reachableFromBranch1.contains(succ)) {
                    return succ;
                }
                if (!visited.contains(succ)) {
                    visited.add(succ);
                    queue.add(succ);
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
        StringBuilder sb = new StringBuilder();
        Set<Node> visited = new HashSet<>();
        List<LoopStructure> loops = detectLoops();
        List<IfStructure> ifs = detectIfs();
        
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
        
        // Create lookup maps for labeled blocks
        Map<Node, LabeledBlockStructure> blockStarts = new HashMap<>();
        for (LabeledBlockStructure block : labeledBlocks) {
            blockStarts.put(block.startNode, block);
        }
        
        // Create lookup for labeled break edges
        Map<Node, LabeledBreakEdge> labeledBreakEdges = new HashMap<>();
        for (LabeledBlockStructure block : labeledBlocks) {
            for (LabeledBreakEdge breakEdge : block.breaks) {
                labeledBreakEdges.put(breakEdge.from, breakEdge);
            }
        }
        
        generatePseudocode(entryNode, visited, sb, "", loopHeaders, ifConditions, blockStarts, labeledBreakEdges, null, null, null);
        
        return sb.toString();
    }

    private void generatePseudocode(Node node, Set<Node> visited, StringBuilder sb, String indent,
                                     Map<Node, LoopStructure> loopHeaders, Map<Node, IfStructure> ifConditions,
                                     Map<Node, LabeledBlockStructure> blockStarts, Map<Node, LabeledBreakEdge> labeledBreakEdges,
                                     LoopStructure currentLoop, LabeledBlockStructure currentBlock, Node stopAt) {
        if (node == null || visited.contains(node)) {
            return;
        }
        
        // Stop at merge node, loop exit, or block end
        if (stopAt != null && node.equals(stopAt)) {
            return;
        }
        
        // Check if this is a labeled block start (before marking as visited)
        LabeledBlockStructure block = blockStarts.get(node);
        if (block != null && currentBlock != block) {
            sb.append(indent).append(block.label).append(": {\n");
            
            // Generate body of the block - process the start node's content and successors
            Set<Node> blockVisited = new HashSet<>();
            generatePseudocodeInBlock(node, blockVisited, sb, indent + "    ", loopHeaders, ifConditions, 
                                      labeledBreakEdges, block);
            
            sb.append(indent).append("}\n");
            
            // Continue after the block
            visited.add(node);
            visited.addAll(blockVisited);
            generatePseudocode(block.endNode, visited, sb, indent, loopHeaders, ifConditions, 
                              blockStarts, labeledBreakEdges, currentLoop, null, stopAt);
            return;
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
                
                sb.append(indent).append("if (").append(node.getLabel()).append(") {\n");
                
                if (breakOnTrue) {
                    sb.append(indent).append("    break ").append(labeledBreak.label).append(";\n");
                    sb.append(indent).append("} else {\n");
                    Set<Node> elseVisited = new HashSet<>(visited);
                    generatePseudocode(ifStruct.falseBranch, elseVisited, sb, indent + "    ", loopHeaders, ifConditions, 
                                      blockStarts, labeledBreakEdges, currentLoop, currentBlock, stopAt);
                } else {
                    Set<Node> thenVisited = new HashSet<>(visited);
                    generatePseudocode(ifStruct.trueBranch, thenVisited, sb, indent + "    ", loopHeaders, ifConditions, 
                                      blockStarts, labeledBreakEdges, currentLoop, currentBlock, stopAt);
                    sb.append(indent).append("} else {\n");
                    sb.append(indent).append("    break ").append(labeledBreak.label).append(";\n");
                }
                
                sb.append(indent).append("}\n");
                return;
            }
        }
        
        // Check if this is a loop header
        LoopStructure loop = loopHeaders.get(node);
        if (loop != null && currentLoop != loop) {
            sb.append(indent).append("while (").append(node.getLabel()).append(") {\n");
            
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
            
            // Generate body of the loop
            if (loopContinue != null) {
                Set<Node> loopVisited = new HashSet<>();
                loopVisited.add(node); // Don't revisit header
                generatePseudocodeInLoop(loopContinue, loopVisited, sb, indent + "    ", loopHeaders, ifConditions, labeledBreakEdges, blockStarts, loop, currentBlock);
            }
            
            sb.append(indent).append("}\n");
            
            // Continue after the loop
            if (loopExit != null) {
                generatePseudocode(loopExit, visited, sb, indent, loopHeaders, ifConditions, blockStarts, labeledBreakEdges, currentLoop, currentBlock, stopAt);
            }
            return;
        }
        
        // Check if this is an if condition
        IfStructure ifStruct = ifConditions.get(node);
        if (ifStruct != null) {
            sb.append(indent).append("if (").append(node.getLabel()).append(") {\n");
            
            // Generate true branch
            Set<Node> trueVisited = new HashSet<>(visited);
            generatePseudocode(ifStruct.trueBranch, trueVisited, sb, indent + "    ", loopHeaders, ifConditions, blockStarts, labeledBreakEdges, currentLoop, currentBlock, ifStruct.mergeNode);
            
            sb.append(indent).append("} else {\n");
            
            // Generate false branch
            Set<Node> falseVisited = new HashSet<>(visited);
            generatePseudocode(ifStruct.falseBranch, falseVisited, sb, indent + "    ", loopHeaders, ifConditions, blockStarts, labeledBreakEdges, currentLoop, currentBlock, ifStruct.mergeNode);
            
            sb.append(indent).append("}\n");
            
            // Continue after merge
            if (ifStruct.mergeNode != null) {
                visited.addAll(trueVisited);
                visited.addAll(falseVisited);
                generatePseudocode(ifStruct.mergeNode, visited, sb, indent, loopHeaders, ifConditions, blockStarts, labeledBreakEdges, currentLoop, currentBlock, stopAt);
            }
            return;
        }
        
        // Regular node - just output it
        sb.append(indent).append(node.getLabel()).append(";\n");
        
        // Continue with successors
        for (Node succ : node.succs) {
            generatePseudocode(succ, visited, sb, indent, loopHeaders, ifConditions, blockStarts, labeledBreakEdges, currentLoop, currentBlock, stopAt);
        }
    }

    private void generatePseudocodeInLoop(Node node, Set<Node> visited, StringBuilder sb, String indent,
                                           Map<Node, LoopStructure> loopHeaders, Map<Node, IfStructure> ifConditions,
                                           Map<Node, LabeledBreakEdge> labeledBreakEdges,
                                           Map<Node, LabeledBlockStructure> blockStarts,
                                           LoopStructure currentLoop, LabeledBlockStructure currentBlock) {
        if (node == null || visited.contains(node)) {
            return;
        }
        
        // Don't go outside the loop
        if (!currentLoop.body.contains(node)) {
            return;
        }
        
        // Stop at the current block's end node (we'll process it after the block closes)
        if (currentBlock != null && node.equals(currentBlock.endNode)) {
            return;
        }
        
        // Check if this is a labeled block start (before marking as visited)
        LabeledBlockStructure block = blockStarts.get(node);
        if (block != null && currentBlock != block) {
            sb.append(indent).append(block.label).append(": {\n");
            
            // Generate body of the block within the loop
            Set<Node> blockVisited = new HashSet<>();
            generatePseudocodeInLoop(node, blockVisited, sb, indent + "    ", loopHeaders, ifConditions, 
                                     labeledBreakEdges, blockStarts, currentLoop, block);
            
            sb.append(indent).append("}\n");
            
            // Continue after the block (with the end node)
            visited.addAll(blockVisited);
            if (currentLoop.body.contains(block.endNode)) {
                generatePseudocodeInLoop(block.endNode, visited, sb, indent, loopHeaders, ifConditions, 
                                        labeledBreakEdges, blockStarts, currentLoop, null);
            }
            return;
        }
        
        // Check for labeled break first (higher priority than regular break)
        LabeledBreakEdge labeledBreak = labeledBreakEdges.get(node);
        if (labeledBreak != null) {
            IfStructure ifStruct = ifConditions.get(node);
            if (ifStruct != null) {
                sb.append(indent).append("if (").append(node.getLabel()).append(") {\n");
                
                // Determine which branch is the labeled break
                if (ifStruct.trueBranch.equals(labeledBreak.to)) {
                    sb.append(indent).append("    break ").append(labeledBreak.label).append(";\n");
                    sb.append(indent).append("} else {\n");
                    Set<Node> elseVisited = new HashSet<>(visited);
                    elseVisited.add(node);
                    generatePseudocodeInLoop(ifStruct.falseBranch, elseVisited, sb, indent + "    ", loopHeaders, ifConditions, labeledBreakEdges, blockStarts, currentLoop, currentBlock);
                } else if (ifStruct.falseBranch.equals(labeledBreak.to)) {
                    Set<Node> thenVisited = new HashSet<>(visited);
                    thenVisited.add(node);
                    generatePseudocodeInLoop(ifStruct.trueBranch, thenVisited, sb, indent + "    ", loopHeaders, ifConditions, labeledBreakEdges, blockStarts, currentLoop, currentBlock);
                    sb.append(indent).append("} else {\n");
                    sb.append(indent).append("    break ").append(labeledBreak.label).append(";\n");
                } else {
                    // Neither branch is the direct labeled break target, continue normally
                    Set<Node> thenVisited = new HashSet<>(visited);
                    thenVisited.add(node);
                    generatePseudocodeInLoop(ifStruct.trueBranch, thenVisited, sb, indent + "    ", loopHeaders, ifConditions, labeledBreakEdges, blockStarts, currentLoop, currentBlock);
                    sb.append(indent).append("} else {\n");
                    Set<Node> elseVisited = new HashSet<>(visited);
                    elseVisited.add(node);
                    generatePseudocodeInLoop(ifStruct.falseBranch, elseVisited, sb, indent + "    ", loopHeaders, ifConditions, labeledBreakEdges, blockStarts, currentLoop, currentBlock);
                }
                sb.append(indent).append("}\n");
                return;
            }
        }
        
        // Check for break
        for (BreakEdge breakEdge : currentLoop.breaks) {
            if (breakEdge.from.equals(node)) {
                // This node has a break
                IfStructure ifStruct = ifConditions.get(node);
                if (ifStruct != null) {
                    sb.append(indent).append("if (").append(node.getLabel()).append(") {\n");
                    
                    // Determine which branch is break
                    if (!currentLoop.body.contains(ifStruct.trueBranch)) {
                        sb.append(indent).append("    break;\n");
                        sb.append(indent).append("} else {\n");
                        Set<Node> elseVisited = new HashSet<>(visited);
                        elseVisited.add(node);
                        generatePseudocodeInLoop(ifStruct.falseBranch, elseVisited, sb, indent + "    ", loopHeaders, ifConditions, labeledBreakEdges, blockStarts, currentLoop, currentBlock);
                    } else {
                        Set<Node> thenVisited = new HashSet<>(visited);
                        thenVisited.add(node);
                        generatePseudocodeInLoop(ifStruct.trueBranch, thenVisited, sb, indent + "    ", loopHeaders, ifConditions, labeledBreakEdges, blockStarts, currentLoop, currentBlock);
                        sb.append(indent).append("} else {\n");
                        sb.append(indent).append("    break;\n");
                    }
                    sb.append(indent).append("}\n");
                    return;
                }
            }
        }
        
        // Check for continue
        for (ContinueEdge continueEdge : currentLoop.continues) {
            if (continueEdge.from.equals(node)) {
                IfStructure ifStruct = ifConditions.get(node);
                if (ifStruct != null) {
                    sb.append(indent).append("if (").append(node.getLabel()).append(") {\n");
                    
                    // Determine which branch is continue
                    if (ifStruct.trueBranch.equals(currentLoop.header)) {
                        sb.append(indent).append("    continue;\n");
                        sb.append(indent).append("} else {\n");
                        Set<Node> elseVisited = new HashSet<>(visited);
                        elseVisited.add(node);
                        generatePseudocodeInLoop(ifStruct.falseBranch, elseVisited, sb, indent + "    ", loopHeaders, ifConditions, labeledBreakEdges, blockStarts, currentLoop, currentBlock);
                    } else {
                        Set<Node> thenVisited = new HashSet<>(visited);
                        thenVisited.add(node);
                        generatePseudocodeInLoop(ifStruct.trueBranch, thenVisited, sb, indent + "    ", loopHeaders, ifConditions, labeledBreakEdges, blockStarts, currentLoop, currentBlock);
                        sb.append(indent).append("} else {\n");
                        sb.append(indent).append("    continue;\n");
                    }
                    sb.append(indent).append("}\n");
                    return;
                }
            }
        }
        
        visited.add(node);
        
        // Check if this is a nested loop header
        LoopStructure nestedLoop = loopHeaders.get(node);
        if (nestedLoop != null && nestedLoop != currentLoop) {
            sb.append(indent).append("while (").append(node.getLabel()).append(") {\n");
            
            Node loopContinue = null;
            Node loopExit = null;
            for (Node succ : node.succs) {
                if (nestedLoop.body.contains(succ) && !succ.equals(node)) {
                    loopContinue = succ;
                } else if (!nestedLoop.body.contains(succ)) {
                    loopExit = succ;
                }
            }
            
            if (loopContinue != null) {
                Set<Node> nestedVisited = new HashSet<>();
                nestedVisited.add(node);
                generatePseudocodeInLoop(loopContinue, nestedVisited, sb, indent + "    ", loopHeaders, ifConditions, labeledBreakEdges, blockStarts, nestedLoop, currentBlock);
            }
            
            sb.append(indent).append("}\n");
            
            if (loopExit != null && currentLoop.body.contains(loopExit)) {
                generatePseudocodeInLoop(loopExit, visited, sb, indent, loopHeaders, ifConditions, labeledBreakEdges, blockStarts, currentLoop, currentBlock);
            }
            return;
        }
        
        // Check if this is an if condition inside the loop
        IfStructure ifStruct = ifConditions.get(node);
        if (ifStruct != null) {
            sb.append(indent).append("if (").append(node.getLabel()).append(") {\n");
            
            Set<Node> trueVisited = new HashSet<>(visited);
            generatePseudocodeInLoop(ifStruct.trueBranch, trueVisited, sb, indent + "    ", loopHeaders, ifConditions, labeledBreakEdges, blockStarts, currentLoop, currentBlock);
            
            sb.append(indent).append("} else {\n");
            
            Set<Node> falseVisited = new HashSet<>(visited);
            generatePseudocodeInLoop(ifStruct.falseBranch, falseVisited, sb, indent + "    ", loopHeaders, ifConditions, labeledBreakEdges, blockStarts, currentLoop, currentBlock);
            
            sb.append(indent).append("}\n");
            
            if (ifStruct.mergeNode != null && currentLoop.body.contains(ifStruct.mergeNode)) {
                visited.addAll(trueVisited);
                visited.addAll(falseVisited);
                generatePseudocodeInLoop(ifStruct.mergeNode, visited, sb, indent, loopHeaders, ifConditions, labeledBreakEdges, blockStarts, currentLoop, currentBlock);
            }
            return;
        }
        
        // Regular node
        sb.append(indent).append(node.getLabel()).append(";\n");
        
        // Continue with successors inside the loop
        for (Node succ : node.succs) {
            if (currentLoop.body.contains(succ) && !succ.equals(currentLoop.header)) {
                generatePseudocodeInLoop(succ, visited, sb, indent, loopHeaders, ifConditions, labeledBreakEdges, blockStarts, currentLoop, currentBlock);
            }
        }
    }

    /**
     * Generates pseudocode for nodes inside a labeled block.
     */
    private void generatePseudocodeInBlock(Node node, Set<Node> visited, StringBuilder sb, String indent,
                                            Map<Node, LoopStructure> loopHeaders, Map<Node, IfStructure> ifConditions,
                                            Map<Node, LabeledBreakEdge> labeledBreakEdges,
                                            LabeledBlockStructure currentBlock) {
        if (node == null || visited.contains(node)) {
            return;
        }
        
        // Don't go outside the block
        if (!currentBlock.body.contains(node)) {
            return;
        }
        
        // Check if this node has a labeled break (is a conditional that can break)
        LabeledBreakEdge labeledBreak = labeledBreakEdges.get(node);
        if (labeledBreak != null && currentBlock.label.equals(labeledBreak.label)) {
            // This is a conditional node with a labeled break
            IfStructure ifStruct = ifConditions.get(node);
            if (ifStruct != null) {
                // Check if the break is on true or false branch
                boolean breakOnTrue = !currentBlock.body.contains(ifStruct.trueBranch);
                
                sb.append(indent).append("if (").append(node.getLabel()).append(") {\n");
                
                if (breakOnTrue) {
                    sb.append(indent).append("    break ").append(labeledBreak.label).append(";\n");
                    sb.append(indent).append("} else {\n");
                    Set<Node> elseVisited = new HashSet<>(visited);
                    elseVisited.add(node);
                    generatePseudocodeInBlock(ifStruct.falseBranch, elseVisited, sb, indent + "    ", loopHeaders, ifConditions, 
                                              labeledBreakEdges, currentBlock);
                } else {
                    Set<Node> thenVisited = new HashSet<>(visited);
                    thenVisited.add(node);
                    generatePseudocodeInBlock(ifStruct.trueBranch, thenVisited, sb, indent + "    ", loopHeaders, ifConditions, 
                                              labeledBreakEdges, currentBlock);
                    sb.append(indent).append("} else {\n");
                    sb.append(indent).append("    break ").append(labeledBreak.label).append(";\n");
                }
                
                sb.append(indent).append("}\n");
                return;
            }
        }
        
        visited.add(node);
        
        // Check if this is an if condition inside the block
        IfStructure ifStruct = ifConditions.get(node);
        if (ifStruct != null && !labeledBreakEdges.containsKey(node)) {
            sb.append(indent).append("if (").append(node.getLabel()).append(") {\n");
            
            Set<Node> trueVisited = new HashSet<>(visited);
            generatePseudocodeInBlock(ifStruct.trueBranch, trueVisited, sb, indent + "    ", loopHeaders, ifConditions, 
                                      labeledBreakEdges, currentBlock);
            
            sb.append(indent).append("} else {\n");
            
            Set<Node> falseVisited = new HashSet<>(visited);
            generatePseudocodeInBlock(ifStruct.falseBranch, falseVisited, sb, indent + "    ", loopHeaders, ifConditions, 
                                      labeledBreakEdges, currentBlock);
            
            sb.append(indent).append("}\n");
            
            if (ifStruct.mergeNode != null && currentBlock.body.contains(ifStruct.mergeNode)) {
                visited.addAll(trueVisited);
                visited.addAll(falseVisited);
                generatePseudocodeInBlock(ifStruct.mergeNode, visited, sb, indent, loopHeaders, ifConditions, 
                                          labeledBreakEdges, currentBlock);
            }
            return;
        }
        
        // Regular node
        sb.append(indent).append(node.getLabel()).append(";\n");
        
        // Continue with successors inside the block (natural fall-through to end doesn't need break)
        for (Node succ : node.succs) {
            if (currentBlock.body.contains(succ)) {
                generatePseudocodeInBlock(succ, visited, sb, indent, loopHeaders, ifConditions, 
                                          labeledBreakEdges, currentBlock);
            }
        }
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
        
        System.out.println("Labeled Block Structures (" + labeledBlocks.size() + "):");
        for (LabeledBlockStructure block : labeledBlocks) {
            System.out.println("  " + block);
        }
    }

    /**
     * Demonstration with example CFGs.
     */
    public static void main(String[] args) {
        // Example 1: Simple if-else
        System.out.println("===== Example 1: Simple If-Else =====");
        StructureDetector detector1 = StructureDetector.fromGraphviz(
            "digraph {\n" +
            "  entry->if_cond;\n" +
            "  if_cond->then;\n" +
            "  if_cond->else;\n" +
            "  then->merge;\n" +
            "  else->merge;\n" +
            "  merge->exit;\n" +
            "}"
        );
        detector1.analyze();
        System.out.println("\n--- Pseudocode ---");
        System.out.println(detector1.toPseudocode());
        System.out.println("--- Graphviz/DOT ---");
        System.out.println(detector1.toGraphviz());
        
        // Example 2: While loop
        System.out.println("\n===== Example 2: While Loop =====");
        StructureDetector detector2 = StructureDetector.fromGraphviz(
            "digraph {\n" +
            "  entry->loop_header;\n" +
            "  loop_header->loop_body;\n" +
            "  loop_header->exit;\n" +
            "  loop_body->loop_header;\n" +
            "}"
        );
        detector2.analyze();
        System.out.println("\n--- Pseudocode ---");
        System.out.println(detector2.toPseudocode());
        System.out.println("--- Graphviz/DOT ---");
        System.out.println(detector2.toGraphviz());
        
        // Example 3: Loop with break and continue
        System.out.println("\n===== Example 3: Loop with Break and Continue =====");
        StructureDetector detector3 = StructureDetector.fromGraphviz(
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
        detector3.analyze();
        System.out.println("\n--- Pseudocode ---");
        System.out.println(detector3.toPseudocode());
        System.out.println("--- Graphviz/DOT ---");
        System.out.println(detector3.toGraphviz());
        
        // Example 4: Nested loops
        System.out.println("\n===== Example 4: Nested Loops =====");
        StructureDetector detector4 = StructureDetector.fromGraphviz(
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
        detector4.analyze();
        System.out.println("\n--- Pseudocode ---");
        System.out.println(detector4.toPseudocode());
        System.out.println("--- Graphviz/DOT ---");
        System.out.println(detector4.toGraphviz());
        
        // Example 5: If inside loop
        System.out.println("\n===== Example 5: If Inside Loop =====");
        StructureDetector detector5 = StructureDetector.fromGraphviz(
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
        detector5.analyze();
        System.out.println("\n--- Pseudocode ---");
        System.out.println(detector5.toPseudocode());
        System.out.println("--- Graphviz/DOT ---");
        System.out.println(detector5.toGraphviz());
        
        // Example 6: Chained edges (demonstrating a->b->c syntax)
        System.out.println("\n===== Example 6: Chained Edges =====");
        StructureDetector detector6 = StructureDetector.fromGraphviz(
            "digraph {\n" +
            "  start->if1;\n" +
            "  if1->ontrue;\n" +
            "  if1->onfalse;\n" +
            "  ontrue->merge;\n" +
            "  onfalse->merge;\n" +
            "  merge->after->exit;\n" +
            "}"
        );
        detector6.analyze();
        System.out.println("\n--- Pseudocode ---");
        System.out.println(detector6.toPseudocode());
        System.out.println("--- Graphviz/DOT ---");
        System.out.println(detector6.toGraphviz());
        
        // Example 7: Labeled block with break
        // Demonstrates Java-style labeled block: 
        // outer: { 
        //     block_start; 
        //     if (cond) { 
        //         break outer; 
        //     }
        //     continue_in_block;
        //     block_end;
        // }
        // after_block;
        System.out.println("\n===== Example 7: Labeled Block with Break =====");
        StructureDetector detector7 = StructureDetector.fromGraphviz(
            "digraph {\n" +
            "  entry->block_start;\n" +
            "  block_start->cond;\n" +
            "  cond->after_block;\n" +        // break outer; - jumps directly to after block
            "  cond->continue_in_block;\n" +  // else continue in block
            "  continue_in_block->block_end;\n" +
            "  block_end->after_block;\n" +
            "  after_block->exit;\n" +
            "}"
        );
        // Register the labeled block: starts at block_start, ends at after_block
        // Need to get the nodes from the detector
        Node blockStart = null;
        Node afterBlock = null;
        for (Node n : detector7.allNodes) {
            if (n.getLabel().equals("block_start")) blockStart = n;
            if (n.getLabel().equals("after_block")) afterBlock = n;
        }
        if (blockStart != null && afterBlock != null) {
            detector7.addLabeledBlock("outer", blockStart, afterBlock);
        }
        detector7.analyze();
        System.out.println("\n--- Pseudocode ---");
        System.out.println(detector7.toPseudocode());
        System.out.println("--- Graphviz/DOT ---");
        System.out.println(detector7.toGraphviz());
        
        // Example 8: Complex nested loops with labeled breaks and continues
        // Represents a for loop as while with proper increment handling:
        //
        // loop_a: for (var c = 0; c < 8; c = c + 1) {
        //   loop_b: for (var d = 0; d < 25; d++) {
        //     for (var e = 0; e < 50; e++) {
        //       if (e == 9) { break loop_b; }
        //       if (e == 20) { continue loop_a; }  // goes to c = c + 1
        //       if (e == 8) { break; }             // goes to d++
        //       break loop_a;
        //     }
        //   }
        //   trace("hello");
        // }
        //
        // Modeled with labeled blocks for continue semantics:
        // while (loop_a_cond) {
        //   loop_a_cont: {
        //     while (loop_b_cond) {
        //       loop_b_cont: {
        //         while (inner_cond) {
        //           inner_cont: {
        //             if (e == 9) { break loop_b_cont; }  // break loop_b
        //             if (e == 20) { break loop_a_cont; } // continue loop_a
        //             if (e == 8) { break inner_cont; }   // break inner
        //             break loop_a;
        //           }
        //           inc_e;
        //         }
        //       }
        //       inc_d;
        //     }
        //     trace_hello;
        //   }
        //   inc_c;
        // }
        System.out.println("\n===== Example 8: Complex Nested Loops with Labeled Breaks =====");
        StructureDetector detector8 = StructureDetector.fromGraphviz(
            "digraph {\n" +
            "  entry->loop_a_cond;\n" +
            // loop_a: outer for loop condition
            "  loop_a_cond->loop_b_cond;\n" +          // c < 8 -> enter body (loop_b)
            "  loop_a_cond->exit;\n" +                 // c >= 8 -> exit
            // loop_b: middle for loop condition
            "  loop_b_cond->inner_cond;\n" +           // d < 25 -> enter inner
            "  loop_b_cond->trace_hello;\n" +          // d >= 25 -> trace (after loop_b)
            // inner: anonymous innermost for loop condition
            "  inner_cond->check_e9;\n" +              // e < 50 -> enter body
            "  inner_cond->inc_d;\n" +                 // e >= 50 -> d++ (exit inner normally)
            // if (e == 9) { break loop_b; }
            "  check_e9->trace_hello;\n" +             // break loop_b -> trace (skip d++)
            "  check_e9->check_e20;\n" +               // else continue
            // if (e == 20) { continue loop_a; } -> goes to c++
            "  check_e20->inc_c;\n" +                  // continue loop_a -> c++
            "  check_e20->check_e8;\n" +               // else continue
            // if (e == 8) { break; } -> goes to d++ (exit inner)
            "  check_e8->inc_d;\n" +                   // break inner -> d++
            "  check_e8->break_loop_a;\n" +            // else -> break loop_a
            // break loop_a - exits outer loop entirely
            "  break_loop_a->exit;\n" +
            // inner loop: normal body end goes to e++
            // (all code paths in this example break/continue, so no normal path to inc_e)
            "  inc_e->inner_cond;\n" +                 // e++ -> back to inner condition
            // loop_b: d++ after inner loop exits normally
            "  inc_d->loop_b_cond;\n" +                // d++ -> back to loop_b condition
            // trace("hello") at end of loop_a body
            "  trace_hello->inc_c;\n" +                // trace -> c++
            // loop_a: c++ at end of iteration
            "  inc_c->loop_a_cond;\n" +                // c++ -> back to loop_a condition
            "}"
        );
        
        // Register labeled blocks for continue semantics
        // loop_a_cont: wraps body before inc_c (for continue loop_a)
        Node loopACont_start = null;
        Node loopACont_end = null;  // inc_c is after the block
        // loop_b_cont: wraps body before inc_d (for break loop_b / continue loop_b)
        Node loopBCont_start = null;
        Node loopBCont_end = null;  // inc_d is after the block
        // inner_cont: wraps body before inc_e (for break inner)
        Node innerCont_start = null;
        Node innerCont_end = null;  // inc_d is where it breaks to
        
        for (Node n : detector8.allNodes) {
            if (n.getLabel().equals("loop_b_cond")) loopACont_start = n;
            if (n.getLabel().equals("inc_c")) loopACont_end = n;
            if (n.getLabel().equals("inner_cond")) loopBCont_start = n;
            if (n.getLabel().equals("inc_d")) loopBCont_end = n;
            if (n.getLabel().equals("check_e9")) innerCont_start = n;
        }
        
        // Register labeled block for continue loop_a (body before inc_c)
        if (loopACont_start != null && loopACont_end != null) {
            detector8.addLabeledBlock("loop_a_cont", loopACont_start, loopACont_end);
        }
        // Register labeled block for break loop_b (body before inc_d - but break loop_b skips to trace)
        if (loopBCont_start != null && loopBCont_end != null) {
            detector8.addLabeledBlock("loop_b_cont", loopBCont_start, loopBCont_end);
        }
        
        detector8.analyze();
        System.out.println("\n--- Pseudocode ---");
        System.out.println(detector8.toPseudocode());
        System.out.println("--- Graphviz/DOT ---");
        System.out.println(detector8.toGraphviz());
    }
}
