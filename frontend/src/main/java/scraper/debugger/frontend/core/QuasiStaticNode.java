package scraper.debugger.frontend.core;

import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.StrokeType;

import scraper.debugger.dto.NodeDTO;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;


public final class QuasiStaticNode {

    /** Arriving flows to this node */
    private final Set<String> arrivals = ConcurrentHashMap.newKeySet();


    /** Departing flows from this node */
    private final Set<String> departures = ConcurrentHashMap.newKeySet();


    /** Circle for tree pane */
    final Circle circle;


    /** Tree item for specification view-model */
    final TreeItem<QuasiStaticNode> treeItem;


    /** Tree cell for specification view-model */
    volatile TreeCell<QuasiStaticNode> treeCell = null;


    /** Whether this node is currently displayed */
    private final AtomicBoolean onScreen = new AtomicBoolean(false);


    /** Outgoing lines to other nodes, set during runtime */
    private final Map<QuasiStaticNode, Line> outgoingLines = new HashMap<>(4);


    /** In which key this node emits new data, if not, null */
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
        nodeType = n.getType();

        switch (nodeType.toLowerCase()) {
            case "intrange": {
                dataStreamKey = (String) n.getNodeConfiguration().get("output");
                return;
            }
            case "map":
            case "mapmap": {
                dataStreamKey = (String) n.getNodeConfiguration().get("putElement");
                return;
            }
            default: dataStreamKey = null;
        }
    }

    void addArrival(String ident) {
        arrivals.add(ident);
    }

    void addDeparture(String ident) {
        arrivals.remove(ident);
        departures.add(ident);
    }

    Set<String> arrivals() {
        return Collections.unmodifiableSet(arrivals);
    }

    Set<String> departures() {
        return Collections.unmodifiableSet(departures);
    }

    boolean departed(CharSequence ident) {
        return departures.contains(ident.toString());
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
