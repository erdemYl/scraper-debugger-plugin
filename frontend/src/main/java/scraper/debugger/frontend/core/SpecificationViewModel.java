package scraper.debugger.frontend.core;

import javafx.application.Platform;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.scene.control.Cell;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Line;
import javafx.util.Callback;
import scraper.debugger.dto.ControlFlowGraphDTO;
import scraper.debugger.dto.InstanceDTO;
import scraper.debugger.dto.NodeDTO;
import scraper.debugger.frontend.api.FrontendActions;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class SpecificationViewModel {

    // Colors used
    private static final String BLUE = "-fx-background-color: lightsteelblue";
    private static final String KHAKI = "-fx-background-color: khaki";
    private static final Paint BLACK = Paint.valueOf("black");
    private static final Paint BROWN = Paint.valueOf("burlywood");

    // Actions dependency
    private final FrontendActions ACTIONS;

    // Quasi-static flow tree view, modified dynamically
    private final TreeView<QuasiStaticNode> QUASI_STATIC_TREE;

    // Selection service
    private final Service<Deque<QuasiStaticNode>> NODE_SELECTION;

    // Newly marked cells
    private final Map<QuasiStaticNode, TreeCell<QuasiStaticNode>> MARKED_CELLS = new HashMap<>();

    // Newly marked lines
    private final Collection<Line> MARKED_LINES = new LinkedList<>();

    // Defined breakpoints
    private final Set<QuasiStaticNode> BREAKPOINTS = new HashSet<>();

    // Selecting nodes until this
    private QuasiStaticNode current = null;


    /**
     * Creates cell factory and defines node selection service
     */
    SpecificationViewModel(FrontendActions ACTIONS, FrontendModel MODEL, TreeView<QuasiStaticNode> specificationTreeView) {
        this.ACTIONS = ACTIONS;
        QUASI_STATIC_TREE = specificationTreeView;
        NODE_SELECTION = createSelectionService(MODEL);
        NODE_SELECTION.setExecutor(Executors.newSingleThreadExecutor());
        createCellFactory();
    }


    Set<QuasiStaticNode> view(InstanceDTO jobIns, ControlFlowGraphDTO jobCFG) {
        Map<String, NodeDTO> nodeDTO = jobIns.getRoutes();
        Map<String, Set<String>> outgoingAddressMap = jobCFG.getOutgoingAddressMap();
        Set<String> endNodes = jobCFG.getEndNodes();
        String start = jobCFG.getStart();

        // root
        QuasiStaticNode rootNode = QuasiStaticNode.createFrom(
                nodeDTO.get(start),
                endNodes.contains(start)
        );
        TreeItem<QuasiStaticNode> root = rootNode.treeItem;

        // all nodes
        Set<QuasiStaticNode> NODES = new HashSet<>();
        NODES.add(rootNode);

        recursiveViewer(root, NODES, nodeDTO, outgoingAddressMap, outgoingAddressMap.get(start), endNodes);

        Platform.runLater(() -> {
            QUASI_STATIC_TREE.setRoot(root);
            QUASI_STATIC_TREE.setShowRoot(true);
        });

        return NODES;
    }

    QuasiStaticNode getRoot() {
        return QUASI_STATIC_TREE.getRoot().getValue();
    }

    /**
     * Designed to response user events for node selection.
     */
    void selectNodesUntil(QuasiStaticNode node) {
        current = Objects.requireNonNull(node);
        NODE_SELECTION.start();
    }


    private void recursiveViewer(TreeItem<QuasiStaticNode> parent,
                                 Set<QuasiStaticNode> NODES,
                                 Map<String, NodeDTO> nodeDTO,
                                 Map<String, Set<String>> outgoingAddressMap,
                                 Set<String> outgoings,
                                 Set<String> endNodes)
    {
        parent.setExpanded(true);
        for (String out : outgoings) {
            QuasiStaticNode node = QuasiStaticNode.createFrom(
                    nodeDTO.get(out),
                    endNodes.contains(out)
            );
            TreeItem<QuasiStaticNode> item = node.treeItem;
            NODES.add(node);
            parent.getChildren().add(item);
            recursiveViewer(item, NODES, nodeDTO, outgoingAddressMap, outgoingAddressMap.get(out), endNodes);
        }
    }


    private Service<Deque<QuasiStaticNode>> createSelectionService(FrontendModel MODEL) {
        return new Service<>() {
            @Override
            protected Task<Deque<QuasiStaticNode>> createTask() {
                return new Task<>() {
                    @Override
                    protected Deque<QuasiStaticNode> call() {

                        // Clear old way
                        MARKED_CELLS.forEach((qsn, cell) -> cell.setStyle(breakpoint(qsn) ? BLUE : null));
                        MARKED_LINES.forEach(line -> {
                            line.setStrokeWidth(1);
                            line.setStroke(BLACK);
                        });
                        MARKED_CELLS.clear();
                        MARKED_LINES.clear();

                        // New node way with new line markings
                        Deque<QuasiStaticNode> nodesUntilCurrent = new LinkedList<>(List.of(current));
                        QuasiStaticNode node = current;
                        Optional<QuasiStaticNode> parent = node.getParent();
                        boolean cellWayExists = node.treeCell != null;

                        while(parent.isPresent()) {
                            QuasiStaticNode pNode = parent.get();
                            pNode.lineTo(node).ifPresent(line -> {
                                line.setStrokeWidth(3);
                                line.setStroke(BROWN);
                                MARKED_LINES.add(line);
                            });
                            nodesUntilCurrent.addFirst(pNode);
                            node = pNode;
                            parent = pNode.getParent();
                            cellWayExists = node.treeCell != null;
                        }

                        // Marking cells on the new way
                        if (cellWayExists) {
                            nodesUntilCurrent.forEach(qsn -> {
                                TreeCell<QuasiStaticNode> cell = qsn.treeCell;
                                cell.setStyle(breakpoint(qsn) ? BLUE : KHAKI);
                                MARKED_CELLS.put(qsn, cell);
                            });
                        }

                        return nodesUntilCurrent;
                    }

                    @Override
                    protected void succeeded() {
                        MODEL.takeSelectedNodes(getValue());
                        reset();
                    }

                    @Override
                    protected void failed() {
                        reset();
                    }
                };
            }
        };
    }

    private void createCellFactory() {

        QUASI_STATIC_TREE.setCellFactory(new Callback<>() {

            final Pattern nodeNamePattern = Pattern.compile("\\.");

            @Override
            public TreeCell<QuasiStaticNode> call(TreeView<QuasiStaticNode> treeView) {
                return new TreeCell<>() {
                    @Override
                    protected void updateItem(QuasiStaticNode node, boolean empty) {
                        super.updateItem(node, empty);
                        if (empty) {
                            setText(null);
                            setOnMouseClicked(null);
                            setStyle(null);
                        } else {
                            String address = node.toString();
                            int last = nodeNamePattern.split(address)[0].length();
                            setText("(" + node.getType() + ") " + address.substring(last + 1));
                            node.treeCell = this;

                            // Event handler for this cell
                            setOnMouseClicked(e -> {
                                if (e.isControlDown()) {
                                    if (!breakpoint(node) && !node.isOnScreen()) {
                                        ACTIONS.requestSetBreakpoint(node.toString());
                                        BREAKPOINTS.add(node);
                                        setStyle(BLUE);
                                    }
                                    // do selection until this cell
                                    selectNodesUntil(node);
                                }
                            });
                        }
                    }
                };
            }
        });
    }

    private boolean breakpoint(QuasiStaticNode node) {
        return BREAKPOINTS.contains(node);
    }
}
