package structure_detector;

import java.util.ArrayList;
import java.util.List;

public class Node {
    public List<Node> preds = new ArrayList<>();
    public List<Node> succs = new ArrayList<>();
}
