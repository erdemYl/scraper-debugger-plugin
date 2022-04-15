package scraper.debugger.frontend.core;

import javafx.application.Platform;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.scene.control.Cell;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Paint;
import javafx.util.Callback;
import scraper.debugger.dto.ControlFlowGraphDTO;
import scraper.debugger.frontend.api.FrontendActions;
import scraper.debugger.frontend.core.FrontendModel.QuasiStaticNode;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class SpecificationViewModel {

    // Colors used
    private static final String BLUE = "-fx-background-color: lightsteelblue";
    private static final String KHAKI = "-fx-background-color: khaki";
    private static final Paint BLACK = Paint.valueOf("black");
    private static final Paint BROWN = Paint.valueOf("brown");

    // Actions dependency
    private final FrontendActions ACTIONS;

    // Quasi-static flow tree view, modified dynamically
    private final TreeView<QuasiStaticNode> QUASI_STATIC_TREE;

    // Selection of nodes
    private final Service<Deque<QuasiStaticNode>> NODE_SELECTION;

    // Registered cells, once registered not modified
    private final Map<QuasiStaticNode, TreeCell<QuasiStaticNode>> CELLS = new HashMap<>();

    // Fresh selected cells
    private final Deque<TreeCell<QuasiStaticNode>> SELECTED_CELLS = new LinkedList<>();

    // Defined breakpoints
    private final Set<QuasiStaticNode> BREAKPOINTS = new HashSet<>();

    // Selecting nodes until this
    private TreeCell<QuasiStaticNode> current = null;


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


    void view(Map<String, QuasiStaticNode> NODES, ControlFlowGraphDTO cfg) {
        Platform.runLater(() -> {
            Map<String, Set<String>> outgoingAddressMap = cfg.getOutgoingAddressMap();
            String start = cfg.getStart();
            TreeItem<QuasiStaticNode> root = new TreeItem<>();
            root.setValue(NODES.get(start));
            recursiveViewer(root, NODES, outgoingAddressMap, outgoingAddressMap.get(start));
            QUASI_STATIC_TREE.setRoot(root);
            QUASI_STATIC_TREE.setShowRoot(true);
            // write connected
        });
    }

    Optional<QuasiStaticNode> parentOf(QuasiStaticNode node) {
        TreeCell<QuasiStaticNode> cell = CELLS.get(node);
        TreeItem<QuasiStaticNode> parent = cell.getTreeItem().getParent();
        return parent == null ? Optional.empty() : Optional.of(parent.getValue());
    }

    /**
     * Designed to response user events for node selection.
     */
    void selectNodesUntil(QuasiStaticNode node) {
        Objects.requireNonNull(node);
        current = CELLS.get(node);
        NODE_SELECTION.start();
    }


    private void recursiveViewer(TreeItem<QuasiStaticNode> parent,
                                 Map<String, QuasiStaticNode> NODES,
                                 Map<String, Set<String>> outgoingAddressMap,
                                 Set<String> outgoings) {
        for (String out : outgoings) {
            TreeItem<QuasiStaticNode> child = new TreeItem<>();
            child.setValue(NODES.get(out));
            Platform.runLater(() -> parent.getChildren().add(child));
            recursiveViewer(child, NODES, outgoingAddressMap, outgoingAddressMap.get(out));
        }
    }


    private Service<Deque<QuasiStaticNode>> createSelectionService(FrontendModel MODEL) {
        return new Service<>() {
            @Override
            protected Task<Deque<QuasiStaticNode>> createTask() {
                return new Task<>() {
                    @Override
                    protected Deque<QuasiStaticNode> call() {
                        /* re-color old cell way */
                        TreeCell<QuasiStaticNode> someCell = SELECTED_CELLS.pollFirst();
                        if (someCell != null) {
                            QuasiStaticNode someNode = someCell.getItem();
                            someCell.setStyle(breakpoint(someNode) ? BLUE : null);
                            while (!SELECTED_CELLS.isEmpty()) {
                                TreeCell<QuasiStaticNode> otherCell = SELECTED_CELLS.pop();
                                QuasiStaticNode otherNode = otherCell.getItem();
                                otherCell.setStyle(breakpoint(otherNode) ? BLUE : null);
                                // color line
                                someNode.lineTo(otherNode).ifPresent(line -> {
                                    line.setStrokeWidth(1);
                                    line.setStroke(BLACK);
                                });
                                someNode = otherNode;
                            }
                        }

                        /* select and color new cell way */
                        current.setStyle(breakpoint(current.getItem()) ? BLUE : KHAKI);
                        SELECTED_CELLS.add(current);

                        QuasiStaticNode node = current.getItem();
                        TreeItem<QuasiStaticNode> parent = current.getTreeItem().getParent();
                        while (parent != null) {
                            QuasiStaticNode parentNode = parent.getValue();
                            TreeCell<QuasiStaticNode> parentCell = CELLS.get(parentNode);
                            parentCell.setStyle(breakpoint(parentNode) ? BLUE : KHAKI);
                            SELECTED_CELLS.addFirst(parentCell);
                            // color line
                            parentNode.lineTo(node).ifPresent(line -> {
                                line.setStrokeWidth(3);
                                line.setStroke(BROWN);
                            });
                            node = parentNode;
                            parent = parent.getParent();
                        }
                        return SELECTED_CELLS.stream()
                                .map(Cell::getItem)
                                .collect(Collectors.toCollection(LinkedList::new));
                    }

                    @Override
                    protected void succeeded() {
                        MODEL.takeSelectedNodes(getValue());
                    }
                };
            }
        };
    }


    private void createCellFactory() {

        final Pattern pattern = Pattern.compile("\\.");

        QUASI_STATIC_TREE.setCellFactory(new Callback<>() {

            final Pattern nodeNamePattern = pattern;

            @Override
            public TreeCell<QuasiStaticNode> call(TreeView<QuasiStaticNode> treeView) {
                return new TreeCell<>() {
                    @Override
                    protected void updateItem(QuasiStaticNode node, boolean empty) {
                        super.updateItem(node, empty);
                        if (empty) {
                            setText(null);
                            setStyle(null);
                            setOnMouseClicked(null);
                        } else {
                            String address = node.toString();
                            int last = nodeNamePattern.split(address)[0].length();
                            setText("(" + node.getType() + ") " + address.substring(last + 1));
                            CELLS.put(node, this);

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
