package structure_detector;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Node {
    private static int nextId = 0;
    
    private final int id;
    private String label;
    public List<Node> preds = new ArrayList<>();
    public List<Node> succs = new ArrayList<>();
    
    public Node() {
        this.id = nextId++;
        this.label = "node" + id;
    }
    
    public Node(String label) {
        this.id = nextId++;
        this.label = label;
    }
    
    public int getId() {
        return id;
    }
    
    public String getLabel() {
        return label;
    }
    
    public void setLabel(String label) {
        this.label = label;
    }
    
    public void addSuccessor(Node succ) {
        if (!succs.contains(succ)) {
            succs.add(succ);
        }
        if (!succ.preds.contains(this)) {
            succ.preds.add(this);
        }
    }
    
    public void addPredecessor(Node pred) {
        if (!preds.contains(pred)) {
            preds.add(pred);
        }
        if (!pred.succs.contains(this)) {
            pred.succs.add(this);
        }
    }
    
    public boolean isConditional() {
        return succs.size() == 2;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Node node = (Node) o;
        return id == node.id;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
    
    @Override
    public String toString() {
        return label;
    }
    
    public static void resetIdCounter() {
        nextId = 0;
    }
}
