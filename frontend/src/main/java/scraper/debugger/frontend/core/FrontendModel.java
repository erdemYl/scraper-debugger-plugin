package scraper.debugger.frontend.core;

import com.googlecode.concurrenttrees.radix.ConcurrentRadixTree;
import com.googlecode.concurrenttrees.radix.RadixTree;
import com.googlecode.concurrenttrees.radix.node.concrete.DefaultCharSequenceNodeFactory;
import javafx.application.Platform;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import scraper.debugger.dto.ControlFlowGraphDTO;
import scraper.debugger.dto.FlowDTO;
import scraper.debugger.dto.FlowMapDTO;
import scraper.debugger.dto.InstanceDTO;
import scraper.debugger.frontend.api.FrontendWebSocket;
import scraper.debugger.frontend.api.FrontendActions;

import java.util.*;


public class FrontendModel extends FrontendWebSocket {

    // Complete quasi-static flow tree of the program
    private final RadixTree<QuasiStaticNode> TREE = new ConcurrentRadixTree<>(new DefaultCharSequenceNodeFactory());

    // How nodes are connected, n1 -> map(n2.address, n2)
    private final Map<QuasiStaticNode, Map<String, QuasiStaticNode>> EDGES = new HashMap<>();

    // Displayed quasi-static flow tree, modified dynamically
    private final TreePane TREE_PANE;

    // Frontend components
    private final FrontendController CONTROL;
    private final SpecificationViewModel SPECIFICATION;
    private final ValuesViewModel VALUES;

    // Actions dependency
    public final FrontendActions ACTIONS;

    private Deque<QuasiStaticNode> CURRENT_SELECTED_NODES = null;

    private FlowMapDTO CURRENT_SELECTED_MAP = null;


    public FrontendModel(FrontendController CONTROL, String bindingIp, int port) {
        super(bindingIp, port);
        this.CONTROL = CONTROL;
        TREE_PANE = new TreePane(CONTROL.dynamicFlowTree);
        VALUES = new ValuesViewModel(this, CONTROL.valueTable, CONTROL.flowMapList, CONTROL.flowMapLabel);
        ACTIONS = new FrontendActions(this);
        SPECIFICATION = new SpecificationViewModel(ACTIONS, this, CONTROL.specificationTreeView);
    }


    @Override
    protected void takeSpecification(InstanceDTO jobIns, ControlFlowGraphDTO jobCFG) {

        /* Quasi-static nodes are created and viewed */
        Set<QuasiStaticNode> viewedNodes = SPECIFICATION.view(jobIns, jobCFG);

        /* Create for each data stream key a value column */
        VALUES.createValueColumns(viewedNodes);

        /* For each node capture the edges and create click actions */
        viewedNodes.forEach(node -> {
            node.getParent().ifPresentOrElse(
                    parent -> {
                        EDGES.compute(parent, (p, map) -> {
                            if (map == null) {
                                return new HashMap<>(Map.of(node.toString(), node));
                            }
                            map.put(node.toString(), node);
                            return map;
                        });
                    },
                    () -> {
                        // root node
                        EDGES.putIfAbsent(node, new HashMap<>(4));
                    }
            );

            Circle circle = node.circle;

            /* Handler for control down clicks */
            circle.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
                if (event.isControlDown() && node.isOnScreen()) {
                    VALUES.visibleMap(false);
                    SPECIFICATION.selectNodesUntil(node);
                }
            });

            /* Handler for control up clicks */
            circle.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
                if (!event.isControlDown() && node.isOnScreen()) {
                    VALUES.visibleMap(true);
                    SPECIFICATION.selectNodesUntil(node);
                }
            });
        });
    }

    @Override
    protected void takeIdentifiedFlow(FlowDTO f) {
        QuasiStaticNode node;
        String ident = f.getIdent().toString().intern();

        if (ident.equals("i")) {
            node = SPECIFICATION.getRoot();
            TREE_PANE.putInitial(node.circle);
            node.setOnScreen();
        } else {
            QuasiStaticNode parentNode = TREE.getValueForExactKey(f.getParentIdent());
            node = EDGES.get(parentNode).get(f.getNodeAddress());
            if (!node.isOnScreen()) {
                Line line = TREE_PANE.put(parentNode.circle, node.circle);
                parentNode.addOutgoingLine(node, line);
                node.setOnScreen();
            }
        }
        TREE.put(ident, node);
        node.addArrival(ident);
    }

    @Override
    protected void takeBreakpointHit(FlowDTO f) {
        QuasiStaticNode node = TREE.getValueForExactKey(f.getIdent());
        node.circle.setFill(Paint.valueOf("darksalmon"));
    }

    @Override
    protected void takeFinishedFlow(FlowDTO f) {
        String ident = f.getIdent().toString().intern();
        QuasiStaticNode node = TREE.getValueForExactKey(ident);
        node.addDeparture(ident);
    }

    @Override
    protected void takeLogMessage(String log) {
        Platform.runLater(() -> {
            CONTROL.logTextArea.appendText(log.substring(13));
        });
    }

    void takeSelectedNodes(Deque<QuasiStaticNode> nodes) {
        CURRENT_SELECTED_NODES = nodes;
        VALUES.viewValues();
    }

    void takeSelectedFlow(FlowMapDTO f) {
        CURRENT_SELECTED_MAP = f;
    }

    Optional<Deque<QuasiStaticNode>> currentSelectedNodes() {
        return Optional.ofNullable(CURRENT_SELECTED_NODES);
    }

    Optional<FlowMapDTO> currentSelectedMap() {
        return Optional.ofNullable(CURRENT_SELECTED_MAP);
    }
}
