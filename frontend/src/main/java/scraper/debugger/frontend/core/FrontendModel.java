package scraper.debugger.frontend.core;

import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import scraper.debugger.dto.ControlFlowGraphDTO;
import scraper.debugger.dto.FlowDTO;
import scraper.debugger.dto.InstanceDTO;
import scraper.debugger.frontend.api.FrontendWebSocket;
import scraper.debugger.frontend.api.FrontendActions;
import scraper.debugger.tree.Trie;

import java.util.*;


public class FrontendModel extends FrontendWebSocket {

    // Complete quasi-static flow tree of the program
    private final Trie<QuasiStaticNode> QUASI_STATIC_TREE = new Trie<>();

    private final Map<QuasiStaticNode, Map<String, QuasiStaticNode>> fromToNode = new HashMap<>();

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
    protected void takeSpecification(InstanceDTO jobIns, ControlFlowGraphDTO jobCFG) {

        /* Quasi-static nodes are created and viewed */
        Set<QuasiStaticNode> viewedNodes = SPECIFICATION.view(jobIns, jobCFG);

        /* Create for each data stream key a value column */
        VALUES.createValueColumns(viewedNodes);

        /* Define for each node's circle its click action */
        viewedNodes.forEach(node -> {
            SPECIFICATION.parentOf(node).ifPresentOrElse(
                    parent -> {
                        fromToNode.compute(parent, (p, map) -> {
                            if (map == null) {
                                return new HashMap<>(Map.of(node.toString(), node));
                            }
                            map.put(node.toString(), node);
                            return map;
                        });
                    },
                    () -> {
                        // root node
                        fromToNode.putIfAbsent(node, new HashMap<>(4));
                    }
            );

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
    }

    @Override
    protected void takeIdentifiedFlow(FlowDTO f) {
        QuasiStaticNode node;
        if (QUASI_STATIC_TREE.isEmpty()) {
            node = SPECIFICATION.getRoot();
            QUASI_STATIC_TREE.put("i", node);
            QUASI_STATIC_TREE_PANE.putInitial(node.circle);
            node.setOnScreen();

        } else {
            String parent = f.getParentIdent();
            QuasiStaticNode parentNode = QUASI_STATIC_TREE.get(parent);
            node = fromToNode.get(parentNode).get(f.getIntoAddress());
            if (!node.isOnScreen()) {
                Line line = QUASI_STATIC_TREE_PANE.put(parentNode.circle, node.circle);
                parentNode.addOutgoingLine(node, line);
                node.setOnScreen();
            }
            QUASI_STATIC_TREE.put(f.getIdent(), node);
            parentNode.addDeparture(parent);
        }

        node.addArrival(f);
    }

    @Override
    protected void takeBreakpointHit(FlowDTO f) {
        QuasiStaticNode node = QUASI_STATIC_TREE.get(f.getIdent());
        node.circle.setFill(Paint.valueOf("darksalmon"));
    }

    @Override
    protected void takeFinishedFlow(FlowDTO f) {
    }

    @Override
    protected void takeLogMessage(String log) {
        //CONTROL.logTextArea.appendText(log.substring(13));
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

}
