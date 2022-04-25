package scraper.debugger.frontend.core;

import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.StrokeType;
import scraper.debugger.dto.DataflowDTO;
import scraper.debugger.dto.NodeDTO;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;


public final class QuasiStaticNode {

    // Arriving flows to this node
    private final Map<String, DataflowDTO> arrivals = new HashMap<>();


    // Departing flows from this node
    private final Map<String, DataflowDTO> departures = new HashMap<>();


    // Circle for tree pane
    final Circle circle;


    // Tree item for specification view-model
    final TreeItem<QuasiStaticNode> treeItem;


    // Tree cell for specification view-model
    volatile TreeCell<QuasiStaticNode> treeCell = null;


    // Whether this node is currently displayed
    private final AtomicBoolean onScreen = new AtomicBoolean(false);


    // Outgoing lines to other nodes, set during runtime
    private final Map<QuasiStaticNode, Line> outgoingLines = new HashMap<>(4);


    // In which key this node emits new data, if not, null
    private final String dataStreamKey;


    private final String nodeAddress;
    private final String nodeType;


    QuasiStaticNode(NodeDTO n, boolean endNode) {
        circle = new Circle(9);
        circle.setFill(Paint.valueOf("burlywood"));
        if (endNode) {
            circle.setStrokeType(StrokeType.INSIDE);
            circle.setStrokeWidth(2);
            circle.setStroke(Paint.valueOf("#896436"));
        }
        treeItem = new TreeItem<>(this);

        nodeAddress = n.getAddress();
        circle.setAccessibleText(nodeAddress);
        this.nodeType = n.getType();
        switch (nodeType) {
            case "IntRange" -> dataStreamKey = (String) n.getNodeConfiguration().get("output");
            case "Map" -> dataStreamKey = (String) n.getNodeConfiguration().get("putElement");
            default -> dataStreamKey = null;
        }
    }

    synchronized void addArrival(DataflowDTO f) {
        arrivals.put(f.getIdent(), f);
    }

    synchronized void addDeparture(String ident) {
        arrivals.computeIfPresent(ident, (i, f) -> {
            departures.put(i, f);
            return null;
        });
    }

    synchronized Set<DataflowDTO> arrivals() {
        return Set.copyOf(arrivals.values());
    }

    synchronized Set<DataflowDTO> departures() {
        return Set.copyOf(departures.values());
    }

    synchronized boolean departed(DataflowDTO f) {
        return departures.containsKey(f.getIdent());
    }

    void addOutgoingLine(QuasiStaticNode other, Line line) {
        synchronized (outgoingLines) {
            outgoingLines.put(other, line);
        }

    }

    void setOnScreen() {
        synchronized (onScreen) {
            onScreen.set(true);
        }
    }

    boolean isOnScreen() {
        synchronized (onScreen) {
            return onScreen.get();
        }
    }

    String getType() {
        return nodeType;
    }

    Optional<QuasiStaticNode> getParent() {
        TreeItem<QuasiStaticNode> parentItem = treeItem.getParent();
        return parentItem == null ? Optional.empty() : Optional.of(parentItem.getValue());
    }

    Optional<Line> lineTo(QuasiStaticNode other) {
        synchronized (outgoingLines) {
            // always line not null
            return Optional.ofNullable(outgoingLines.get(other));
        }
    }

    Optional<String> dataStreamKey() {
        return Optional.ofNullable(dataStreamKey);
    }

    @Override
    public String toString() {
        return nodeAddress;
    }
}
