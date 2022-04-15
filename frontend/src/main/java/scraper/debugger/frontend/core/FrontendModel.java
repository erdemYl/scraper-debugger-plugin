package scraper.debugger.frontend.core;

import javafx.application.Platform;
import javafx.scene.control.ListView;
import javafx.scene.control.TableView;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import scraper.debugger.dto.ControlFlowGraphDTO;
import scraper.debugger.dto.FlowDTO;
import scraper.debugger.dto.InstanceDTO;
import scraper.debugger.dto.NodeDTO;
import scraper.debugger.frontend.api.FrontendWebSocket;
import scraper.debugger.frontend.api.FrontendActions;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class FrontendModel extends FrontendWebSocket {

    // Quasi-static nodes, not modified once set // address -> node
    private final Map<String, QuasiStaticNode> NODES = new HashMap<>();

    // Displayed quasi-static flow tree, modified dynamically
    private final TreePane QUASI_STATIC_TREE_PANE;

    // Frontend components
    private final FrontendController CONTROL;
    private final SpecificationViewModel SPECIFICATION;
    private final ValuesViewModel VALUES;

    // Actions dependency
    final FrontendActions ACTIONS;

    private Deque<QuasiStaticNode> CURRENT_SELECTED_NODES = null;
    private FlowDTO CURRENT_SELECTED_FLOW = null;


    public FrontendModel(FrontendController CONTROL, String bindingIp, int port) {
        super(bindingIp, port);
        this.CONTROL = CONTROL;
        QUASI_STATIC_TREE_PANE = new TreePane(CONTROL.dynamicFlowTree);
        VALUES = new ValuesViewModel(this, CONTROL.valueTable, CONTROL.flowMapList);
        ACTIONS = new FrontendActions(this);
        SPECIFICATION = new SpecificationViewModel(ACTIONS, this, CONTROL.specificationTreeView);
    }


    @Override
    public void takeSpecification(InstanceDTO jobIns, ControlFlowGraphDTO jobCFG) {
        Set<String> endNodes = jobCFG.getEndNodes();

        /* Create all quasi-static nodes */
        jobIns.getRoutes().forEach(((address, n) -> {
            NODES.put(address, new QuasiStaticNode(n, endNodes.contains(address)));
        }));

        /* Create for each data stream key a value column */
        VALUES.createValueColumns(NODES.values());

        /* Define for each node's circle its click action */
        NODES.forEach((address, node) -> {

            Circle circle = node.circle;

            /* Handler for control down clicks */
            circle.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
                if (event.isControlDown() && node.isOnScreen()) {
                    SPECIFICATION.selectNodesUntil(node);
                }
            });

            /* Handler for control up clicks */
            circle.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
                if (!event.isControlDown() && node.isOnScreen()) {

                }
            });
        });
        SPECIFICATION.view(NODES, jobCFG);
    }

    @Override
    public void takeIdentifiedFlow(FlowDTO f) {
        QuasiStaticNode node = NODES.get(f.getIntoAddress());
        SPECIFICATION.parentOf(node).ifPresentOrElse(
                parent -> {
                    if (!node.isOnScreen()) {
                        Line line = QUASI_STATIC_TREE_PANE.put(parent.circle, node.circle);
                        parent.addOutgoingLine(node, line);
                        node.setOnScreen();
                    }
                    parent.addDeparture(f.getParentIdent());
                },
                () -> {
                    // initial flow, no parent
                    QUASI_STATIC_TREE_PANE.putInitial(node.circle);
                    node.setOnScreen();
                }
        );
    }

    @Override
    public void takeBreakpointHit(FlowDTO f) {
        QuasiStaticNode node = NODES.get(f.getIntoAddress());
        Platform.runLater(() -> node.circle.setFill(Paint.valueOf("black")));
        node.addArrival(f);
    }

    @Override
    public void takeFinishedFlow(FlowDTO f) {
    }

    @Override
    public void takeLogMessage(String log) {
        CONTROL.logTextArea.appendText(log.substring(13));
    }


    void takeSelectedNodes(Deque<QuasiStaticNode> nodes) {
        CURRENT_SELECTED_NODES = nodes;
        VALUES.viewValues(nodes);
    }

    void takeSelectedFlow(FlowDTO f) {
        CURRENT_SELECTED_FLOW = f;
    }

    Optional<Deque<QuasiStaticNode>> currentSelectedNodes() {
        return Optional.ofNullable(CURRENT_SELECTED_NODES);
    }

    Optional<FlowDTO> currentSelectedFlow() {
        return Optional.ofNullable(CURRENT_SELECTED_FLOW);
    }


    static final class QuasiStaticNode {

        // Arriving flows to this node
        private final Map<String, FlowDTO> arrivals = new ConcurrentHashMap<>();

        // Departing flows from this node
        private final Set<FlowDTO> departures = ConcurrentHashMap.newKeySet();

        // Tree pane circle
        private final Circle circle;

        // Whether this node now on screen is
        private final AtomicBoolean onScreen = new AtomicBoolean(false);

        // Outgoing lines to other nodes, set during runtime // node -> Line
        private final Map<QuasiStaticNode, Line> outgoingLines = new HashMap<>(4);

        // In which key this node emits new data, if not, null
        private final String dataStreamKey;

        private final String nodeAddress;
        private final String nodeType;
        private final boolean isEndNode;

        private QuasiStaticNode(NodeDTO n, boolean isEndNode) {
            circle = new Circle(9);
            this.nodeAddress = n.getAddress();
            this.nodeType = n.getType();
            this.isEndNode = isEndNode;
            switch (nodeType) {
                case "IntRange" -> dataStreamKey = (String) n.getNodeConfiguration().get("output");
                case "Map" -> dataStreamKey = (String) n.getNodeConfiguration().get("putElement");
                default -> dataStreamKey = null;
            }
        }

        private void addArrival(FlowDTO f) {
            arrivals.put(f.getIdent(), f);
        }

        private void addDeparture(String ident) {
            arrivals.computeIfPresent(ident, (i, f) -> {
               departures.add(f);
               return null;
            });
        }

        private void addOutgoingLine(QuasiStaticNode other, Line line) {
            synchronized (outgoingLines) {
                outgoingLines.put(other, line);
            }
        }

        private void setOnScreen() {
            synchronized (onScreen) {
                onScreen.set(true);
            }
        }

        Set<FlowDTO> arrivals() {
            return arrivals.values().stream().collect(Collectors.toUnmodifiableSet());
        }

        Set<FlowDTO> departures() {
            return Collections.unmodifiableSet(departures);
        }

        boolean departed(FlowDTO f) {
            return departures.contains(f);
        }

        boolean isOnScreen() {
            synchronized (onScreen) {
                return onScreen.get();
            }
        }

        String getType() {
            return nodeType;
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
        public boolean equals(Object o) {
            if (o instanceof QuasiStaticNode) {
                return this.toString().equals(o.toString());
            }
            return false;
        }

        @Override
        public int hashCode() {
            return nodeAddress.hashCode();
        }

        @Override
        public String toString() {
            return nodeAddress;
        }
    }
}
