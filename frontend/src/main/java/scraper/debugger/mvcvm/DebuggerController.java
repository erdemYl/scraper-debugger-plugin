package scraper.debugger.mvcvm;

import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldListCell;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import scraper.debugger.dto.ControlFlowGraphDTO;
import scraper.debugger.dto.FlowMapDTO;
import scraper.debugger.dto.InstanceDTO;
import scraper.debugger.dto.NodeDTO;

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

public class DebuggerController {

    private DebuggerApp debugger;
    private final Set<String> endNodes = new HashSet<>();
    private InstanceDTO instance;


    // FOR SHOWING TREE during EXECUTION (FX part)
    private final List<VBox> nodeBoxes = new ArrayList<>(15);
    private final Map<VBox, Map<Integer, List<Circle>>> nodeBoxSeparation = new HashMap<>();
    private final Map<Line, Entry<Circle, Circle>> lines = new HashMap<>();
    private final Map<Line, Entry<VBox, VBox>> lines2 = new HashMap<>();


    // FOR SHOWING TREE during EXECUTION (DATA part)
    private final FlowTreeViewModel flowTreeDATA = new FlowTreeViewModel(this);


    // DISPLAY COMPONENTS
    private FlowTreeController flowTreeCONT; // instantiated when first flow arrives
    private final ValueSelectionViewModel valueSelectionVM = new ValueSelectionViewModel();
    private final FlowSelectionViewModel flowSelectionVM = new FlowSelectionViewModel();


    // UTILITIES
    private final AtomicBoolean onceStarted = new AtomicBoolean(false);
    private final ReentrantLock lock = new ReentrantLock(true);
    private final ReentrantLock platformLock = new ReentrantLock(true);

    // Background (nothing to do with nodes)
    @FXML Label changeColorLabel;
    @FXML void changeColor() {
        if (changeColorLabel.getStyle().contains("silk")) {
            String blue = "-fx-background-color: lightblue";
            changeColorLabel.setStyle(blue);
            flowTreeFX.setStyle(blue);
        } else {
            String silk = "-fx-background-color: cornsilk";
            changeColorLabel.setStyle(silk);
            flowTreeFX.setStyle(silk);
        }
    }


    //=======
    // BUTTONS
    //=======

    @FXML
    Label connectionLabel;

    @FXML
    Pane startButton;

    @FXML
    Pane stopButton;

    @FXML
    Pane continueBranchButton;

    @FXML
    Label stepButton;

    @FXML
    Label logLabel;

    @FXML Circle startFlowCircle;

    @FXML Circle continueBranchCircle;

    @FXML Circle stepFlowCircle;

    @FXML Circle logCircle;

    @FXML
    Label exitLabel;


    @FXML
    Label valuesLabel;

    @FXML
    Label flowsLabel;


    // Small Flow Buttons

    @FXML
    Pane stepFlow;

    @FXML
    Pane continueFlow;

    @FXML
    Pane execFlowOnNode;


    //======
    // FLOW and FLOW-MAP VIEWING
    //======

    @FXML
    TreeView<String> beforeExecTree;

    @FXML
    ListView<String> flowMapList;

    @FXML
    ScrollPane logPane;

    @FXML
    TextArea logMessages;

    @FXML
    Label inOutLabel;

    @FXML
    Label dynamicStaticLabel;

    @FXML
    AnchorPane flowTreeFX;
    private Circle runtimeRoot;

    @FXML
    TreeView<Label> selectableValuesList;

    @FXML
    TreeView<Label> selectableFlowsList;


    //=================
    // Button Actions
    //=================

    @FXML
    void changeConnectionStatus() {
        connectionLabel.setText("Connected");
        debugger.openConnection();
    }

    @FXML
    void exitLabelClicked() {
        Platform.exit();
        System.exit(0);
    }

    @FXML
    void startButtonClicked() {
        synchronized (onceStarted) {
            if (onceStarted.get()) {
                flowSelectionVM.setOverallFlowAction(true);
                debugger.sendContinueExecAll();
                stepButton.setOpacity(0.3);
            } else {
                onceStarted.set(true);
                debugger.startExecution();
            }
            startButton.setVisible(false);
            stopButton.setVisible(true);
        }
    }

    @FXML
    void stopButtonClicked() {
        debugger.sendStopAllFlow();
        stopButton.setVisible(false);
        startButton.setVisible(true);
        stepButton.setOpacity(1);
    }

    @FXML
    void stepButtonClicked() {
        if (stepButton.getOpacity() == 1) {
            stepButton.setOpacity(0.3);
            flowSelectionVM.setOverallFlowAction(true);
            debugger.sendStepAll();
        }
    }

    @FXML
    void continueBranchButtonClicked() {
        if (continueBranchButton.getOpacity() == 1) {
            Optional<List<String>> flowIds = flowTreeCONT.branchContFlows;
            flowIds.ifPresent(ids ->
                    ids.forEach(id ->
                            debugger.sendContinueFlow(id)));
            flowSelectionVM.setOverallFlowAction(true);
            continueBranchButton.setOpacity(0.3);
            debugger.sendContinueExec();
        }
    }

    @FXML
    void logLabelClicked() {
        if (logCircle.getStrokeWidth() == 1) {
            logCircle.setStrokeWidth(5);
            beforeExecTree.setVisible(false);
            logPane.setVisible(true);
        } else {
            logCircle.setStrokeWidth(1);
            logPane.setVisible(false);
            beforeExecTree.setVisible(true);
        }
    }

    @FXML
    void valuesLabelClicked() {
        flowsLabel.setOpacity(0.3);
        valuesLabel.setOpacity(1);
        selectableFlowsList.setVisible(false);
        selectableValuesList.setVisible(true);
    }

    @FXML
    void flowsLabelClicked() {
        valuesLabel.setOpacity(0.3);
        flowsLabel.setOpacity(1);
        selectableValuesList.setVisible(false);
        selectableFlowsList.setVisible(true);
    }

    @FXML
    void stepFlowButtonClicked() {
        if (stepFlow.getOpacity() == 1) {
            flowSelectionVM
                    .selectedFlows.values()
                    .forEach(set -> set.forEach(f -> debugger.sendStepFlow(f)));
            stepFlow.setOpacity(0.3);
            continueFlow.setOpacity(0.3);
            flowSelectionVM.removeSelectedFlowsFromWaitingList();
            flowSelectionVM.setOverallFlowAction(false);
            debugger.sendContinueExec();
        }
    }

    @FXML
    void continueFlowButtonClicked() {
        if (continueFlow.getOpacity() == 1) {
            flowSelectionVM
                    .selectedFlows.values()
                    .forEach(set -> set.forEach(f -> debugger.sendContinueFlow(f)));
            continueFlow.setOpacity(0.3);
            stepFlow.setOpacity(0.3);
            flowSelectionVM.removeSelectedFlowsFromWaitingList();
            flowSelectionVM.setOverallFlowAction(false);
            debugger.sendContinueExec();
        }
    }

    @FXML
    void execFlowOnNodeButtonClicked() {

    }

    public void setConnectionToDebugger(DebuggerApp d) {
        debugger = d;
    }

    public void displayLogMessage(String str) {
        // wheat, tan, lightsteelblue, thistle, darksalmon, olive, mistyrose, cornflowerblue, darkseagreen
        Platform.runLater(() -> {
            String t = logMessages.getText();
            logMessages.setText(t + str.substring(13));
        });
    }


    //=======
    // BEFORE EXECUTION
    //=======

    public void generateBeforeExecTree(InstanceDTO ins, ControlFlowGraphDTO cfg) {
        instance = ins;
        Platform.runLater(() -> {
            setChildNodes(cfg, setRootNode(cfg.getStart()));
        });
        endNodes.addAll(cfg.getEndNodes());
    }

    private TreeItem<String> setRootNode(String start) {
        TreeItem<String> root = new TreeItem<>(
                " (" + instance.getRoutes().get("<" + start + ">").getType() + ") "
        );
        root.setExpanded(true);
        Label l = new Label(start);
        addBreakpointClickActions(l);
        root.setGraphic(l);
        beforeExecTree.setRoot(root);
        return root;
    }

    private void setChildNodes(ControlFlowGraphDTO cfg, TreeItem<String> root) {
        String start = cfg.getStart();
        Map<String, Set<String>> outgoingAdr = cfg.getOutgoingAdrMap();
        Set<String> goes = outgoingAdr.get(start);
        Map<String, TreeItem<String>> itemFinder = new HashMap<>();

        addChildren(root, goes, itemFinder, outgoingAdr);
    }

    private void addChildren(TreeItem<String> parent,
                             Set<String> children,
                             Map<String, TreeItem<String>> finder,
                             Map<String, Set<String>> outgoingAdr)
    {
        Map<String, NodeDTO> routes = instance.getRoutes();
        for (String adr : children) {
            TreeItem<String> n = new TreeItem<>(
                    " (" + routes.get("<" + adr + ">").getType() + ") ");
            n.setExpanded(true);
            Label l = new Label(adr);
            addBreakpointClickActions(l);
            n.setGraphic(l);
            parent.getChildren().add(n);
            finder.put(adr, n);

            if (!outgoingAdr.get(adr).isEmpty()) {
                addChildren(n, outgoingAdr.get(adr), finder, outgoingAdr);
            }
        }
    }

    private void addBreakpointClickActions(Label adrLabel) {
        adrLabel.setOnMouseClicked(e -> {
            if (e.isControlDown()) {
                debugger.sendBreakpoint(adrLabel.getText(), true);
                adrLabel.setStyle("-fx-background-color: lightsteelblue");
            }
        });
    }


    //=======
    // DURING EXECUTION
    //=======

    public void addRootNode(NodeDTO root, FlowMapDTO o) {
        lock.lock();
        try {
            Platform.runLater(() -> {
                platformLock.lock();
                flowTreeCONT = new FlowTreeController();
                flowMapList.setCellFactory(TextFieldListCell.forListView());
                runtimeRoot = new Circle(9);
                ColoringUtil.burlywoodOrSilver(runtimeRoot, root.getAddress(), endNodes);
                VBox box = createAndAddNewNodeBox(runtimeRoot, o, 50, true);
                flowTreeDATA.addRuntimeNode(root, o, box, runtimeRoot);
            });
        } finally {
            Platform.runLater(platformLock::unlock);
            lock.unlock();
        }
    }

    public void addRuntimeNode(NodeDTO n, FlowMapDTO o) {
        lock.lock();
        try {
            Platform.runLater(() -> {
                platformLock.lock();
                String adr = n.getAddress();
                Circle rn = new Circle(9);
                ColoringUtil.burlywoodOrSilver(rn, adr, endNodes);

                rn.setOnMouseClicked(e -> {
                    flowMapList.getItems().add(adr);
                });

                int lastLevel = nodeBoxes.size() - 1;
                VBox currLevel = introducesNewLevel(o)
                        ? createAndAddNewNodeBox(rn, o, nodeBoxes.get(lastLevel).getLayoutX() + 100, false)
                        : nodeBoxes.get(o.getGraphLevel());

                flowTreeDATA.addRuntimeNode(n, o, currLevel, rn);

                String pIdent = o.getParentIdent();

                // marks flow in parent node as processed
                flowSelectionVM
                        .markPresentationItemAsProcessed(
                                flowTreeDATA.getNodeFX(pIdent), pIdent, false);
            });
        } finally {
            Platform.runLater(platformLock::unlock);
            lock.unlock();
        }
    }

    public void breakpointUpdate(FlowMapDTO o) {
        lock.lock();
        try {
            Platform.runLater(() -> {
                platformLock.lock();
                Circle rn = flowTreeDATA.getNodeFX(o.getIdentification());
                if (!ColoringUtil.isBlack(rn)) {
                    rn.setFill(Paint.valueOf("black"));
                    stopButton.setVisible(false);
                    startButton.setVisible(true);

                    flowSelectionVM.createNewBreakpointFlowList(rn);
                }
                flowSelectionVM.addWaitingFlow(rn, o);
                stepButton.setOpacity(1);
            });
        } finally {
            Platform.runLater(platformLock::unlock);
            lock.unlock();
        }
    }

    public void endNodeFlowUpdate(FlowMapDTO o) {
        Platform.runLater(() -> {
            String ident = o.getIdentification();
            flowSelectionVM
                    .markPresentationItemAsProcessed(
                            flowTreeDATA.getNodeFX(ident), ident, true);
        });
    }

    void addCircleToBox(Circle c1, Circle c2, VBox b1, VBox b2) {
        // adding c2 to b2
        try {
            Map<Integer, List<Circle>> partition = nodeBoxSeparation.get(b2);
            int prevNodeIndex = b1.getChildren().indexOf(c1);
            int add = getAddingIndex(c1, b1, b2);
            b2.getChildren().add(
                    add, c2
            );

            updateNodeBoxSeparation(add, b2);

            List<Circle> goes = partition.get(prevNodeIndex);
            if (goes == null) {
                goes = new LinkedList<>(List.of(c2));
                partition.put(prevNodeIndex, goes);
            }
            else goes.add(c2);
        } catch (Exception e) {
        }
    }

    private void updateNodeBoxSeparation(int afterIndex, VBox b) {
        int thisLevel = nodeBoxes.indexOf(b);
        if (thisLevel < nodeBoxes.size() - 1) {
            VBox next = nodeBoxes.get(thisLevel + 1);
            Map<Integer, List<Circle>> part = nodeBoxSeparation.get(next);
            Map<Integer, List<Circle>> newMap = new HashMap<>();

            if (next != null) {
                part.forEach((i, list) -> {
                    if (i < afterIndex) newMap.put(i, list);
                    else newMap.put(i + 1, list);
                });
                nodeBoxSeparation.put(next, newMap);
            }
        }
    }

    Line drawLineBetween(VBox prevBox, VBox nextBox, Circle n1, Circle n2) {
        ColoringUtil.burlywoodOrDarksalmon(n1);

        Line l = new Line();
        l.setLayoutX(0);
        l.setLayoutY(0);
        l.setStartX(prevBox.getLayoutX() + 9);
        l.setStartY(getNodeCenter(prevBox, n1) + 9);

        l.setEndX(nextBox.getLayoutX() + 9);
        l.setEndY(getNodeCenter(nextBox, n2) + 9);

        flowTreeFX.getChildren().add(l);
        lines.put(l, new AbstractMap.SimpleEntry<>(n1, n2));
        lines2.put(l, new AbstractMap.SimpleEntry<>(prevBox, nextBox));
        updateLines(prevBox, nextBox);
        updateLines(nextBox);
        return l;
    }

    void addNodeInfoDisplaying(Circle runtimeNode, NodeDTO n, FlowMapDTO o) {
        if (flowTreeDATA.refersToDynamic(o)) {
            flowTreeCONT.addClickEventDYNAMIC(runtimeNode, n, o);
            runtimeNode.setOnMouseEntered(e -> {
                dynamicStaticLabel.setText("Dynamic");
                dynamicStaticLabel.setVisible(true);
            });
            runtimeNode.setOnMouseExited(e -> dynamicStaticLabel.setVisible(false));
        } else {
            flowTreeCONT.addClickEventSTATIC(runtimeNode, n, o);
            runtimeNode.setOnMouseEntered(e -> {
                dynamicStaticLabel.setText("Static");
                dynamicStaticLabel.setVisible(true);
            });
        }
    }

    private VBox createAndAddNewNodeBox(Circle node, FlowMapDTO o, double x, boolean initial) {
        VBox nodes = new VBox();
        nodes.setAlignment(Pos.CENTER);
        nodes.setPrefWidth(20);
        nodes.setPrefHeight(330);
        nodes.setSpacing(60);
        nodes.setLayoutY(9);
        nodes.setLayoutX(x);
        nodes.getChildren().add(node);
        flowTreeFX.getChildren().add(nodes);
        nodeBoxes.add(nodes);

        if (!initial) {
            VBox prevNodes = nodeBoxes.get(o.getGraphLevel() - 1);
            Map<Integer, List<Circle>> map = new HashMap<>();
            Circle prev = flowTreeDATA.getParentNodeFX(o);
            map.put(prevNodes.getChildren().indexOf(prev), new LinkedList<>(List.of(node)));
            nodeBoxSeparation.put(nodes, map);
        }
        return nodes;
    }

    private double getNodeCenter(VBox nodes, Circle node) {
        int index = nodes.getChildren().indexOf(node);
        int size = nodes.getChildren().size();
        if (size > 5) return getCenterHelper(nodes, node);
        if (size % 2 == 0) {
            int half = size / 2;
            if (index > half - 1) {
                int k = index - half + 1;
                if (k == 0) {
                    return 165 + 30 + 9;
                } else {
                    return 165 + 30 + (k - 1) * 60 + (k - 1) * 18 + 9;
                }
            } else {
                int k = half - index;
                if (k == 1) {
                    return 165 - 30 - 9;
                }
                else {
                    return 165 - (30 + (k - 1) * 60 + (k - 1) * 18 + 9);
                }
            }
        } else {
            int centerI = size / 2;
            if (centerI == 0) {
                return 165;
            } else {
                if (index < centerI) {
                    int k = centerI - index;
                    return 165 - (9 + k * 60 + (k - 1) * 18 + 9);
                } else {
                    int k = index - centerI;
                    if (k == 0) {
                        return 165;
                    } else {
                        return 165 + 9 + k * 60 + (k - 1) * 18 + 9;
                    }
                }
            }
        }
    }

    private double getCenterHelper(VBox nodes, Circle node) {
        int midI = 2;
        int i = nodes.getChildren().indexOf(node);
        if (i == midI) {
            return 165;
        } else {
            if (i < midI) {
                int k = midI - i;
                return 165 - (9 + 60 * k + 18 * (k - 1) + 9);
            } else {
                int k = i - midI;
                return 165 + 9 + 60 * k + 18 * (k - 1) + 9;
            }
        }
    }

    private int getAddingIndex(Circle c1, VBox b1, VBox b2) {
        // adding c2 to b2, thereafter will follow drawLine between c1 and c2
        Map<Integer, List<Circle>> partOfB2 = nodeBoxSeparation.get(b2);
        int indexC1 = b1.getChildren().indexOf(c1);

        int add = 0;
        for (int i = indexC1 - 1; i >= 0; i--) {
            List<Circle> goes = partOfB2.get(i);
            add += goes == null ? 0 : goes.size();
            /*LinkedList<Circle> goesTo = partOfB2.get(i);
            if (goesTo != null) {
                return b2.getChildren().indexOf(goesTo.getLast()) + 1;
            }*/
        }
        return add;
    }

    private void updateLines(VBox box1, VBox box2) {
        lines2.forEach((l, boxes) -> {
            if (boxes.getKey() == box1 && boxes.getValue() == box2) {
                Entry<Circle, Circle> circles = lines.get(l);
                l.setStartY(getNodeCenter(box1, circles.getKey()) + 9);
                l.setEndY(getNodeCenter(box2, circles.getValue()) + 9);
            }
        });
    }

    private void updateLines(VBox box) {
        // if a box exists after this box, lines between updated
        int level = nodeBoxes.indexOf(box);
        if (level < nodeBoxes.size() - 1) {
            updateLines(box, nodeBoxes.get(level + 1));
        }
    }

    private boolean introducesNewLevel(FlowMapDTO o) {
        return nodeBoxes.size() - 1 < o.getGraphLevel();
    }


    //=================
    // Screen Components
    //=================

    private class FlowTreeController {

        private final List<Line> highlightedLines = new ArrayList<>();
        private final Map<Circle, EventHandler<MouseEvent>> clickEventsControl = new HashMap<>();
        private final Map<Circle, EventHandler<MouseEvent>> clickEventsNoControl = new HashMap<>();

        private Optional<List<String>> branchContFlows = Optional.empty();

        private Optional<Circle> currentMarkedNode = Optional.empty();

        void addClickEventDYNAMIC(Circle rn, NodeDTO n, FlowMapDTO o) {
            EventHandler<MouseEvent> ctrl = event -> {
                if (event.isControlDown()) {
                    flowSelectionVM.makeListTransparentIfNeeded(rn);

                    markUnmarkNode(rn, true);
                    flowMapList.setEditable(true);

                    // clear old to renew again
                    ObservableList<String> items = flowMapList.getItems();
                    items.clear();
                    clickEventsNoControl.forEach((c, e) -> c.removeEventHandler(MouseEvent.MOUSE_CLICKED, e));
                    replaceHighlightedLinesFor(o);

                    // for branch continuing actions
                    addBranchContinueFlowsOf(rn);

                    // selectable value showing
                    List<String> selectable = new ArrayList<>();
                    flowTreeDATA.getDisplayableContent(o).forEach((k, v) -> {
                        items.add(k + ": " + v);
                        if (v.equals("--select value--")) selectable.add(k);
                    });

                    // sel. values are listed in the component
                    valueSelectionVM.listSelectables(selectable, o);

                    if (selectable.isEmpty()) {
                        flowMapList.setEditable(false);
                        addEventsIfAllValuesSelected(o);
                    } else {
                        flowMapList.setOnEditCommit(edit -> {
                            // editing done
                            items.set(edit.getIndex(), edit.getNewValue());
                            addEventsIfAllValuesSelected(o);
                        });
                    }

                }
            };
            rn.addEventHandler(MouseEvent.MOUSE_CLICKED, ctrl);
            clickEventsControl.put(rn, ctrl);
        }

        void addClickEventSTATIC(Circle rn, NodeDTO n, FlowMapDTO o) {
            EventHandler<MouseEvent> ctrl = event -> {
                if (event.isControlDown()) {
                    flowSelectionVM.makeListTransparentIfNeeded(rn);

                    markUnmarkNode(rn, true);
                    flowMapList.setEditable(false);
                    valueSelectionVM.addRoot(false);

                    // renew lines and items
                    ObservableList<String> items = flowMapList.getItems();
                    items.clear();
                    clickEventsNoControl.forEach((c, e) -> c.removeEventHandler(MouseEvent.MOUSE_CLICKED, e));
                    replaceHighlightedLinesFor(o);

                    // for branch continuing actions
                    addBranchContinueFlowsOf(rn);

                    flowTreeDATA.getDisplayableContent(o).forEach((k, v) -> items.add(k + ": " + v));

                    // add actions
                    Deque<Circle> nodes = flowTreeDATA.getNavCirclesOnWay(o);
                    Deque<FlowMapDTO> maps = flowTreeDATA.getFlowMapsOnWay(o);
                    if (nodes.size() == maps.size()) {
                        addNoCtrlClickActionTo(nodes, maps);
                    }
                }
            };
            rn.addEventHandler(MouseEvent.MOUSE_CLICKED, ctrl);
            clickEventsControl.put(rn, ctrl);
        }

        void addEventsIfAllValuesSelected(FlowMapDTO o) {
            ObservableList<String> items = flowMapList.getItems();

            boolean allValuesSelected = items
                    .stream()
                    .noneMatch(i -> i.endsWith("--select value--"));

            if (allValuesSelected) {
                flowMapList.setEditable(false);
                Deque<Circle> nodes = flowTreeDATA.getNavCirclesOnWay(o);
                Deque<FlowMapDTO> maps = getDisplayableMapsDYNAMIC(o);

                if (!maps.isEmpty()) {
                    addNoCtrlClickActionTo(nodes, maps);
                } else {
                    items.replaceAll(s -> s.split(":")[0] + ":");
                    items.add(0, "Such a flow has not reached this node.");
                    deleteNoCtrlClickActions();
                }
            }
        }

        void addBranchContinueFlowsOf(Circle rn) {
            branchContFlows = flowSelectionVM.getWaitingFlowIds(rn);
            if (branchContFlows.isEmpty())
                continueBranchButton.setOpacity(0.3);
            else
                continueBranchButton.setOpacity(1);
        }

        private void replaceHighlightedLinesFor(FlowMapDTO o) {
            highlightedLines.forEach(line -> {
                line.setStrokeWidth(1);
                line.setStroke(Paint.valueOf("black"));
            });
            highlightedLines.clear();
            highlightedLines.addAll(flowTreeDATA.getColorableLines(o));
            highlightedLines.forEach(line -> {
                line.setStrokeWidth(3);
                line.setStroke(Paint.valueOf("burlywood"));
            });
        }

        private void addNoCtrlClickActionTo(Deque<Circle> nodes, Deque<FlowMapDTO> maps) {
            while(!nodes.isEmpty() && !maps.isEmpty()) {
                Circle node = nodes.pop();
                FlowMapDTO map = maps.pop();

                Runnable r = () -> {
                    markUnmarkNode(node, true);
                    ObservableList<String> items = flowMapList.getItems();
                    items.clear();
                    map.getContent().forEach((k, v) -> items.add(k + ": " + v.toString()));
                };
                EventHandler<MouseEvent> noCtrl = ev -> {
                    if (!ev.isControlDown()) {
                        r.run();
                    }
                };
                if (nodes.isEmpty()) r.run();
                node.addEventHandler(MouseEvent.MOUSE_CLICKED, noCtrl);
                clickEventsNoControl.put(node, noCtrl);
            }
        }

        private void deleteNoCtrlClickActions() {
            clickEventsNoControl.forEach((n, e) -> {
                n.removeEventHandler(MouseEvent.MOUSE_CLICKED, e);
            });
            clickEventsNoControl.clear();
        }

        private Deque<FlowMapDTO> getDisplayableMapsDYNAMIC(FlowMapDTO o) {
            LinkedHashMap<String, String> varMap = new LinkedHashMap<>();
            flowMapList.getItems().forEach(item -> {
                String[] split = item.split(":");
                if (split.length > 1) {
                    varMap.put(split[0], item.substring(split[0].length() + 1));
                }
            });
            return flowTreeDATA.findFlowMapsDYNAMIC(varMap, o);
        }

        private void markUnmarkNode(Circle node, boolean mark) {
            //boolean silver = node.getFill().equals(Paint.valueOf("silver"));
            if (mark) {
                currentMarkedNode.ifPresent(circle -> markUnmarkNode(circle, false));
                currentMarkedNode = Optional.of(node);
                ColoringUtil.markIfNeeded(node);
            } else {
                ColoringUtil.unmarkIfNeeded(node);
            }
        }
    }


    private class ValueSelectionViewModel {

        Map<String, Entry<String, Circle>> currentSelected = new HashMap<>();
        int numberOfKeys = 0;

        void listSelectables(List<String> keys, FlowMapDTO o) {
            currentSelected.clear();
            numberOfKeys = keys.size();

            if (keys.isEmpty()) {
                addRoot(false);
                return;
            }

            addRoot(true);
            TreeItem<Label> root = selectableValuesList.getRoot();

            Deque<FlowMapDTO> openerNotForkMaps = flowTreeDATA.getMapsInEmitterNotForksOnTheWay(o);

            if (openerNotForkMaps.peekLast() == o) {
                openerNotForkMaps.removeLast();
            }

            Deque<List<FlowMapDTO>> emitted = openerNotForkMaps
                    .stream()
                    .map(flowTreeDATA::getEmittedFlowMapsAfter)
                    .collect(Collectors.toCollection(LinkedList::new));

            // Now it is always keys.size = emitted.size
            //
            // For each "key" at index I in keys, we have emitted flow list "eList" at index I in emitted.
            // Meaning: for "key" all emitted flow maps will be in "eList" with each flow map has another value at "key".

            keys.forEach(k -> {
                List<FlowMapDTO> emits = emitted.pop();

                TreeItem<Label> title = new TreeItem<>(new Label(k + "  (" + emits.size() + ")"));
                title.getValue().setStyle("-fx-font-size: 13; -fx-font-weight: bold");
                root.getChildren().add(title);

                // for each emit, content at key "k" is different
                emits.forEach(e -> {
                    String val = e.getContent().get(k).toString();
                    TreeItem<Label> item = new TreeItem<>(new Label(val));
                    item.getValue().setStyle("-fx-font-size: 13; -fx-font-weight: bold");

                    Circle c = new Circle(5);
                    c.setFill(Paint.valueOf("burlywood"));
                    c.setTranslateY(5);

                    c.setOnMouseClicked(e2 -> {
                        c.setFill(Paint.valueOf("brown"));
                        displaySelectedKeyValue(k, val, c);
                        if (numberOfKeys == currentSelected.size())
                            flowTreeCONT.addEventsIfAllValuesSelected(o);
                    });

                    item.setGraphic(c);

                    title.getChildren().add(item);
                });
            });
        }

        private void addRoot(boolean existsValues) {
            Label l = existsValues ? new Label("  Values  ") : new Label("  No Value to Select  ");
            l.setStyle("-fx-font-size: 13; -fx-font-weight: bold; -fx-background-color: burlywood");
            selectableValuesList.setRoot(new TreeItem<>(l));
            selectableValuesList.getRoot().setExpanded(true);
        }

        private void displaySelectedKeyValue(String k, String v, Circle c) {
            ObservableList<String> items = flowMapList.getItems();
            for (int i = 0; i < items.size(); i++) {
                if (items.get(i).startsWith(k + ":")) {
                    items.set(i, k + ":" + v);

                    if (currentSelected.containsKey(k)) {
                        Circle prev = currentSelected.get(k).getValue();
                        if (prev != c) prev.setFill(Paint.valueOf("burlywood"));
                    }

                    currentSelected.put(k, new AbstractMap.SimpleEntry<>(v, c));
                    break;
                }
            }
        }
    }


    private class FlowSelectionViewModel {

        private boolean overallFlowAction = false;
        boolean overallFlowAction() { return overallFlowAction; }
        void setOverallFlowAction(boolean val) { overallFlowAction = val; }

        // runtime node (circle) -> set of flow id
        Map<Circle, Set<String>> selectedFlows = new HashMap<>();
        Map<Circle, TreeItem<Label>> titlesForEachBreakpoint = new HashMap<>();
        Map<Circle, Integer> processedFlowNumber = new HashMap<>();

        TreeItem<Label> currentRoot;

        void makeListTransparentIfNeeded(Circle rn) {
            if (!titlesForEachBreakpoint.containsKey(rn))
                selectableFlowsList.setRoot(new TreeItem<>());
        }

        void addWaitingFlow(Circle rn, FlowMapDTO o) {
            // 0 index is always for waiting flows
            TreeItem<Label> root = titlesForEachBreakpoint.get(rn);
            TreeItem<Label> waiting = root.getChildren().get(0);

            root.setExpanded(true);
            waiting.setExpanded(true);

            Label right = new Label();
            TreeItem<Label> flow = new TreeItem<>(right);
            waiting.getChildren().add(flow);

            Label left = new Label();
            left.setStyle("-fx-font-size: 13; -fx-font-weight: bold; -fx-background-color: wheat");
            right.setGraphic(left);

            rn.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> {
                if (e.isControlDown()) {
                    stepFlow.setOpacity(0.3);
                    continueFlow.setOpacity(0.3);
                    if (ColoringUtil.isBlack(rn)) {
                        selectableFlowsList.setRoot(root);
                        currentRoot = root;
                    } else
                        selectableFlowsList.setRoot(new TreeItem<>(new Label(" No Waiting Flows ")));
                }
            });

            Map<String, String> displayable = flowTreeDATA.getDisplayableContent(o);
            StringBuilder sb = new StringBuilder();
            Map<String, ?> flowMap = o.getContent();

            displayable.forEach((s1, s2) -> {
                if (!s1.equals("_")) {
                    String add;
                    if (s2.equals("--select value--"))
                        add = " (" + s1 + ": " + flowMap.get(s1).toString() + ") ";
                    else
                        add = " (" + s1 + ": " + s2 + ") ";
                    sb.append(add);
                }
            });

            // flow item has the text of its key-values AT LEFT
            left.setText(sb.toString());

            // flow item has the text of its ident AT RIGHT
            right.setText(o.getIdentification());
            right.setAccessibleText(o.getId());

            waiting.getValue().setText(" Waiting (" + waiting.getChildren().size() + ") ");
            clickActionForItem(rn, flow, o);
            flowTreeDATA.setPresentationItem(o, flow);
        }

        void markPresentationItemAsProcessed(Circle node, String flowIdent, boolean endNode) {
            processedFlowNumber.computeIfPresent(node, (c, v) -> {
                Optional<TreeItem<Label>> presentation = flowTreeDATA.getPresentationItemOf(flowIdent);
                if (presentation.isPresent()) {
                    TreeItem<Label> item = presentation.get();
                    Label right = item.getValue();
                    String text = right.getText();

                    if (text.contains("Pro")) {
                        int n = Integer.parseInt(text.split(" \\| ")[1]);
                        right.setText(" Processed | " + (n + 1));
                    } else {
                        right.setText(" Processed | 1");
                        right.setAccessibleText("");
                    }

                    if (endNode) {
                        // when all items are processed, remove them from waiting list
                        // 0 index is always waiting list
                        TreeItem<Label> waiting = titlesForEachBreakpoint.get(node).getChildren().get(0);

                        ObservableList<TreeItem<Label>> list = waiting.getChildren();
                        if (v + 1 == list.size()) {
                            list.clear();
                            waiting.getValue().setText(" Waiting (0) ");
                            ColoringUtil.burlywood(node);
                        }
                    }

                    // left graphic
                    right.getGraphic().setMouseTransparent(true);

                    return v + 1;
                }
                return v;
            });
        }

        void createNewBreakpointFlowList(Circle c) {
            selectedFlows.clear();
            if (!titlesForEachBreakpoint.containsKey(c)) {
                TreeItem<Label> root = new TreeItem<>(new Label("  Flows  "));
                TreeItem<Label> waitingTitle = new TreeItem<>(new Label(" Waiting "));
                root.getChildren().add(waitingTitle);
                titlesForEachBreakpoint.put(c, root);
                processedFlowNumber.put(c, 0);
            }
        }

        void removeSelectedFlowsFromWaitingList() {
            for (Circle rn : selectedFlows.keySet()) {
                // 0 index is always waiting list
                TreeItem<Label> waiting = titlesForEachBreakpoint.get(rn).getChildren().get(0);

                ObservableList<TreeItem<Label>> flows = waiting.getChildren();
                List<TreeItem<Label>> filtered = flows.stream()
                        .filter(f -> {
                            Node g = f.getValue().getGraphic();
                            return g.getOpacity() == 0.3 || g.isMouseTransparent();
                        }).collect(Collectors.toList());

                flows.removeAll(filtered);
                waiting.getValue().setText(" Waiting " + "(" + flows.size() + ") ");
                if (flows.isEmpty()) ColoringUtil.burlywood(rn);
            }
        }

        Optional<List<String>> getWaitingFlowIds(Circle c) {
            if (titlesForEachBreakpoint.containsKey(c)) {
                // exists waiting flow titles for c
                // 0 index is waiting flow list
                TreeItem<Label> waiting = titlesForEachBreakpoint.get(c).getChildren().get(0);
                return Optional.of(
                        waiting.getChildren()
                                .stream()
                                .map(item -> item.getValue().getAccessibleText())
                                .filter(text -> !text.isEmpty())
                                .collect(Collectors.toList())
                );
            }

            return Optional.empty();
        }

        private void clickActionForItem(Circle rn, TreeItem<Label> flow, FlowMapDTO o) {
            Label l = (Label) flow.getValue().getGraphic();
            l.setOnMouseClicked(e -> {
                l.setOpacity(0.3);
                selectedFlows.compute(rn, (c1, set) -> {
                    if (set == null) return new HashSet<>(Set.of(o.getId()));
                    else set.add(o.getId());
                    return set;
                });
                stepFlow.setOpacity(1);
                continueFlow.setOpacity(1);
            });
        }
    }


    /**
     * To tidy up the code in the controller, coloring will be done with this utility.
     */
    private static class ColoringUtil {

        /**
         * Coloring node in the first place.
         * From: "addRuntimeNode" and "addRootNode"
         */
        static void burlywoodOrSilver(Circle node, String adr, Set<String> endNodes) {
            if (endNodes.contains(adr)) {
                node.setFill(Paint.valueOf("burlywood"));
                node.setStyle("-fx-stroke: black; -fx-stroke-type: inside");
                node.setStrokeWidth(2);
            } else {
                node.setFill(Paint.valueOf("silver"));
            }
        }

        // Neutral color
        static void burlywood(Circle node) {
            node.setFill(Paint.valueOf("burlywood"));
        }

        /**
         * Coloring while drawing a new line between nodes n1 - n2.
         * Given node is the n1.
         * From: "drawLineBetween"
         */
        static void burlywoodOrDarksalmon(Circle node) {
            if (isBlack(node)) node.setFill(Paint.valueOf("darksalmon"));
            else burlywood(node);
        }

        /**
         * Coloring when given node clicked.
         * From: RuntimeTreeComponent click actions
         */
        static void markIfNeeded(Circle node) {
            boolean silver = node.getFill().equals(Paint.valueOf("silver"));
            if (!isBlack(node) && !silver)
                node.setFill(Paint.valueOf("#896436"));
        }

        /**
         * Coloring when other node than given node clicked.
         * From: RuntimeTreeComponent click actions
         */
        static void unmarkIfNeeded(Circle node) {
            boolean silver = node.getFill().equals(Paint.valueOf("silver"));
            if (!isBlack(node) && !silver)
                node.setFill(Paint.valueOf("burlywood"));
        }

        /**
         * Semantically equals to: node is a breakpoint node.
         * This means, there are/were waiting flows in node.
         */
        static boolean isBlack(Circle node) {
            Paint p = node.getFill();
            return p.equals(Paint.valueOf("black"))
                    || p.equals(Paint.valueOf("darksalmon"));
        }
    }
}
