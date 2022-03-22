package scraper.debugger.mvcvm;

import javafx.scene.control.Label;
import javafx.scene.control.TreeItem;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import scraper.debugger.dto.FlowMapDTO;
import scraper.debugger.dto.NodeDTO;
import scraper.debugger.graph.Trie;

import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

public class FlowTreeViewModel {

    private final DebuggerController flowController;

    // TREE STRUCTURE
    private final Trie<RuntimeNode> treeDATA;
    private final Trie<NavigationNode> treeNAV;

    FlowTreeViewModel(DebuggerController flowController) {
        treeDATA = new Trie<>();
        treeNAV = new Trie<>();
        this.flowController = flowController;
    }


    //============
    // ADDING a NODE
    //===========

    void addRuntimeNode(NodeDTO n, FlowMapDTO o, VBox box, Circle c) {
        NavigationNode nav;
        if (canBeAddedAsNAV(n, o)) {
            nav = addAsNAV(box, c, o, needsStaticNAV(o));
            if (treeNAV.size() > 1) {
                NavigationNode parent = getParentNAV(o);
                flowController.addCircleToBox(parent.nodeFX, c, parent.nodeBox, box);
                addLineBetween(parent, nav, n, o);
            }
            addAsDATA(n, o, nav);
            flowController.addNodeInfoDisplaying(c, n, o);
        } else {
            // always dynamic
            nav = getLastNAV(n, o);
            ((DynamicNavNode) nav).addNavigationFor(o);
            addAsDATA(n, o, nav);
        }
    }

    private void addAsDATA(NodeDTO n, FlowMapDTO o, NavigationNode nav) {
        treeDATA.put(o.getIdentification(), new RuntimeNode(n, o, nav));
    }

    private NavigationNode addAsNAV(VBox box, Circle c, FlowMapDTO o, boolean isStatic) {
        NavigationNode navNode;
        if (treeNAV.isEmpty()) {
            navNode = isStatic
                    ? new StaticNavNode(box, c, "i", o.getIdentification())
                    : new DynamicNavNode(box, c, "i", o);
        } else {
            NavigationNode prev = getParentNAV(o);
            if (!isStatic && toFork(o.getFlowTo())) {
                navNode = new DynamicForkNavNode(box, c, prev.getNextName(), o);
            } else {
                navNode = isStatic
                        ? new StaticNavNode(box, c, prev.getNextName(), o.getIdentification())
                        : new DynamicNavNode(box, c, prev.getNextName(), o);
            }
        }

        treeNAV.put(navNode.getNavIdent(), navNode);
        return navNode;
    }

    private void addLineBetween(NavigationNode parent, NavigationNode child, NodeDTO n, FlowMapDTO o) {
        Line l = flowController.drawLineBetween(
                parent.nodeBox, child.nodeBox, parent.nodeFX, child.nodeFX
        );
        if (staticNavigation(parent)) {
            ((StaticNavNode) parent).addOutgoingLine(o.getIdentification(), new OutgoingLine(l, child));
        } else {
            if (dynamicNavigation(parent)) ((DynamicNavNode) parent).setOutgoingLine(new OutgoingLine(l, child));
            else {
                String postfix = getPostfixOf(o);
                ((DynamicForkNavNode) parent).addOutgoingLine(n.getAddress(), postfix, child.navIdent, new OutgoingLine(l, child));
            }
        }
    }

    private boolean canBeAddedAsNAV(NodeDTO n, FlowMapDTO o) {
        return treeNAV.isEmpty() || !alreadyExistsNAV(n, o);
    }

    private boolean alreadyExistsNAV(NodeDTO n, FlowMapDTO o) {
        NavigationNode parent = getParentNAV(o);
        if (staticNavigation(parent)) {
            return false;
        } else {
            if (dynamicNavigation(parent)) {
                return ((DynamicNavNode) parent).outgoingLineExists();
            } else {
                String postfix = getPostfixOf(o);
                return ((DynamicForkNavNode) parent).existsLineToAddress(n.getAddress(), postfix);
            }
        }
    }

    private NavigationNode getParentNAV(FlowMapDTO o) {
        return treeDATA.get(o.getParentIdent()).navNode;
    }

    private DynamicNavNode getLastNAV(NodeDTO n, FlowMapDTO o) {
        NavigationNode par = getParentNAV(o);
        if (dynamicNavigation(par)) {
            return (DynamicNavNode) ((DynamicNavNode) getParentNAV(o)).outLine.goesTo;
        }
        return (DynamicNavNode) ((DynamicForkNavNode) par).outLinesPostfix.get(getPostfixOf(o)).goesTo;
    }

    private boolean staticNavigation(NavigationNode n) {
        return n instanceof StaticNavNode;
    }

    private boolean dynamicNavigation(NavigationNode n) {
        return n instanceof DynamicNavNode && !(n instanceof DynamicForkNavNode);
    }

    private boolean dynamicForkNavigation(NavigationNode n) {
        return n instanceof DynamicForkNavNode;
    }

    private boolean needsDynamicNAV(FlowMapDTO o) {
        Queue<RuntimeNode> way = new LinkedList<>(treeDATA.getValuesOn(o.getParentIdent()));
        while (!way.isEmpty()) {
            String flowTo = way.poll().flowTo();
            if (toEmitterNotFork(flowTo)) return true;
        }
        return toEmitterNotFork(o.getFlowTo());
    }

    private boolean needsStaticNAV(FlowMapDTO o) {
        return !needsDynamicNAV(o);
    }

    private boolean toFork(String flowTo) {
        return flowTo.equals("FORK");
    }

    private boolean toEmitterNotFork(String flowTo) {
        return !toFork(flowTo) && toEmitter(flowTo);
    }

    private boolean toEmitter(String flowTo) {
        switch (flowTo) {
            case "FORK", "MAP", "INT_RANGE" -> {
                return true;
            }
            default -> {
                return false;
            }
        }
    }


    //===========
    // GETTING INFOS
    //===========

    boolean refersToDynamic(FlowMapDTO o) {
        NavigationNode nav = treeDATA.get(o.getIdentification()).navNode;
        return dynamicNavigation(nav) || dynamicForkNavigation(nav);
    }


    RuntimeNode firstEmitterNotForkParent(FlowMapDTO o) {
        Deque<RuntimeNode> way = new ArrayDeque<>(treeDATA.getValuesOn(o.getParentIdent()));
        while (!way.isEmpty()) {
            RuntimeNode n = way.removeLast();
            if (toEmitterNotFork(n.flowTo())) return n;
        }
        return null;
    }

    Deque<FlowMapDTO> getMapsInEmitterNotForksOnTheWay(FlowMapDTO o) {
        return getFlowMapsOnWay(o)
                .stream()
                .filter(fm -> toEmitterNotFork(fm.getFlowTo()))
                .collect(Collectors.toCollection(LinkedList::new));
    }

    Circle getParentNodeFX(FlowMapDTO o) {
        return treeDATA.get(o.getParentIdent()).navNode.nodeFX;
    }

    Circle getNodeFX(String flowIdent) {
        assert treeDATA.containsKey(flowIdent);
        return treeDATA.get(flowIdent).navNode.nodeFX;
    }

    Deque<FlowMapDTO> findFlowMapsDYNAMIC(LinkedHashMap<String, String> vars, FlowMapDTO o) {
        Deque<NavigationNode> navs = getNavNodesOnWay(o);

        StringBuilder s = new StringBuilder();
        while (!navs.isEmpty()) {
            NavigationNode n = navs.pop();
            if (n.getIdent().isEmpty()) {
                navs.addFirst(n);
                break;
            } else {
                s = new StringBuilder(n.getIdent());
            }
        }

        List<String> postfixes = new LinkedList<>();
        navs.forEach(nav -> {
            List<String> post = vars.keySet()
                    .stream()
                    .map(k -> ((DynamicNavNode) nav).getFlowMapPostfixFor(k, vars.get(k)))
                    .filter(p -> !p.equals("0"))
                    .collect(Collectors.toList());
            postfixes.add(post.isEmpty() ? "0" : post.get(0));
        });

        for (String post : postfixes) {
            s.append(post);
        }

        Deque<FlowMapDTO> flowMaps = treeDATA.getValuesOn(s.toString())
                .stream().map(n -> n.flowMap)
                .collect(Collectors.toCollection(LinkedList::new));

        return flowMaps.size() == treeDATA.getValuesOn(o.getIdentification()).size()
                ? flowMaps
                : new LinkedList<>();
    }

    /**
     * Returns selectable values in a sequential order.
     */
    Map<String, String> getDisplayableContent(FlowMapDTO o) {
        List<String> selectables = selectableVariablesTO(o);
        Map<String, String> res = new LinkedHashMap<>();
        if (selectables.isEmpty()) {
            o.getContent().forEach((k, v) -> res.put(k, v.toString()));
        } else {
            selectables.forEach(k -> res.put(k, "--select value--"));
        }
        return res;

    }

    List<Line> getColorableLines(FlowMapDTO o) {
        List<Line> lines = new LinkedList<>();
        Deque<RuntimeNode> way = new LinkedList<>(treeDATA.getValuesOn(o.getIdentification()));
        RuntimeNode n1 = null;
        RuntimeNode n2 = null;
        while(!way.isEmpty()) {
            if (n1 != null) n2 = n1;
            n1 = way.removeLast();
            if (n2 != null) {
                NavigationNode nav = n1.navNode;
                if (staticNavigation(nav)) {
                    lines.add(
                            ((StaticNavNode) nav).outLines.get(n2.getIdentification()).line
                    );
                    continue;
                }
                if (dynamicNavigation(nav)) {
                    lines.add(
                            ((DynamicNavNode) nav).outLine.line
                    );
                    continue;
                }
                if (dynamicForkNavigation(nav)) {
                    lines.add(
                            ((DynamicForkNavNode) nav).outLines.get(n2.navNode.navIdent).line
                    );
                }
            }
        }
        return lines;
    }

    Deque<FlowMapDTO> getFlowMapsOnWay(FlowMapDTO o) {
        return treeDATA.getValuesOn(o.getIdentification())
                .stream()
                .map(n -> n.flowMap)
                .collect(Collectors.toCollection(LinkedList::new));
    }

    Deque<Circle> getNavCirclesOnWay(FlowMapDTO o) {
        return getNavNodesOnWay(o)
                .stream()
                .map(nav -> nav.nodeFX)
                .collect(Collectors.toCollection(LinkedList::new));
    }


    Entry<Map<String, ?>, Map<String, ?>> looseStaticParentBeforeAfterMap(FlowMapDTO o) {
        Deque<RuntimeNode> way = new LinkedList<>(treeDATA.getValuesOn(o.getParentIdent()));
        RuntimeNode n = null;
        RuntimeNode n2 = null;
        while (!way.isEmpty()) {
            if (n != null) n2 = n;
            n = way.removeLast();
            if (n.flowTo().equals("ON_WAY")) {
                if (n2 == null) {
                    return new AbstractMap.SimpleEntry<>(n.getMapContent(), o.getContent());
                }
                return new AbstractMap.SimpleEntry<>(n.getMapContent(), n2.getMapContent());
            }
        }
        return new AbstractMap.SimpleEntry<>(new HashMap<>(), new HashMap<>());
    }

    List<FlowMapDTO> getEmittedFlowMapsAfter(FlowMapDTO o) {
        if (toEmitterNotFork(o.getFlowTo())) {
            return treeDATA.getDirectChildValuesOf(o.getIdentification())
                    .stream()
                    .map(rn -> rn.flowMap)
                    .collect(Collectors.toList());
        }
        return List.of();
    }

    private Deque<NavigationNode> getNavNodesOnWay(FlowMapDTO o) {
        return new LinkedList<>(treeNAV.getValuesOn(treeDATA.get(o.getIdentification()).navNode.navIdent));
    }

    private List<String> selectableVariablesTO(FlowMapDTO o) {
        List<String> vars = new LinkedList<>();
        Deque<RuntimeNode> way = new LinkedList<>(treeDATA.getValuesOn(o.getParentIdent()));

        while(!way.isEmpty()) {
            RuntimeNode n = way.pop();
            switch (n.flowTo()) {
                case "INT_RANGE" -> {
                    vars.add((String) n.getNodeConfiguration().get("output"));
                }
                case "MAP" -> {
                    vars.add((String) n.getNodeConfiguration().get("putElement"));
                }
            }
        }
        return vars;
    }

    private String getPostfixOf(FlowMapDTO o) {
        return o.getIdentification().replace(o.getParentIdent(), "");
    }

    Optional<TreeItem<Label>> getPresentationItemOf(String flowIdent) {
        return treeDATA.get(flowIdent).getPresentationItem();
    }


    //================
    // SETTING AT RUNTIME
    //================

    void setPresentationItem(FlowMapDTO o, TreeItem<Label> item) {
        treeDATA.get(o.getIdentification()).setPresentationItem(item);
    }


    //=========
    // NODES
    //=========

    private static class StaticNavNode
            extends NavigationNode
    {
        final String identification;

        // identification - outgoing
        Map<String, OutgoingLine> outLines;

        StaticNavNode(VBox box, Circle n, String navIdent, String identification) {
            super(box, n, navIdent);
            this.identification = identification;
            outLines = new HashMap<>();
        }

        void addOutgoingLine(String ident, OutgoingLine l) {
            outLines.put(ident, l);
        }

        boolean containsLine(String ident) {
            return outLines.get(ident) != null;
        }

        @Override
        String getIdent() {
            return identification;
        }
    }


    private class DynamicForkNavNode
            extends DynamicNavNode
    {

        // navigation ident -> outgoing
        final Map<String, OutgoingLine> outLines;

        // node address -> outgoing
        final Map<String, OutgoingLine> outLinesAdr;

        // flow ident postfix -> outgoing
        final Map<String, OutgoingLine> outLinesPostfix;

        DynamicForkNavNode(VBox box, Circle n, String navIdent, FlowMapDTO o) {
            super(box, n, navIdent, o);
            outLines = new HashMap<>();
            outLinesAdr = new HashMap<>();
            outLinesPostfix = new HashMap<>();
        }

        void addOutgoingLine(String address, String postfix, String navIdent, OutgoingLine l) {
            outLines.put(navIdent, l);
            outLinesAdr.put(address, l);
            outLinesPostfix.put(postfix, l);
        }

        boolean existsLineToAddress(String adr, String postfix) {
            if (outLinesAdr.get(adr) != null) {
                return outLinesPostfix.containsKey(postfix);
            }
            return false;
        }

        boolean containsLine(String ident) {
            return outLines.get(ident) != null;
        }
    }


    private class DynamicNavNode
            extends NavigationNode
    {
        final String identification;
        OutgoingLine outLine;
        private final boolean useZeroPostfix;

        // for navigating with values in treeDATA
        Map<String, Map<String, String>> navigation;

        DynamicNavNode(VBox box, Circle n, String navIdent, FlowMapDTO o) {
            super(box, n, navIdent);
            if (o.getIdentification().equals("i")) {
                identification = "i";
                useZeroPostfix = true;
            } else {
                boolean staticPar = staticNavigation(getParentNAV(o));
                identification = staticPar
                        ? o.getIdentification()
                        : "";
                useZeroPostfix = !staticPar &&
                        !toEmitter(treeDATA.get(o.getParentIdent()).flowTo());
            }
            navigation = new HashMap<>();
            addNavigationFor(o);
        }

        void setOutgoingLine(OutgoingLine l) {
            outLine = l;
        }

        boolean outgoingLineExists() {
            return outLine != null;
        }

        void addNavigationFor(FlowMapDTO o) {
            if (identification.isEmpty() && !useZeroPostfix) {
                RuntimeNode run = firstEmitterNotForkParent(o);
                if (run != null) {
                    String key;
                    String val;
                    String postfix = getPostfixOf(o);
                    switch (run.flowTo()) {
                        case "MAP" -> {
                            key = (String) run.getNodeConfiguration().get("putElement");
                            val = o.getContent().get(key).toString();
                            addNavigation(key, val, postfix);
                        }
                        case "INT_RANGE" -> {
                            key = (String) run.getNodeConfiguration().get("output");
                            val = o.getContent().get(key).toString();
                            addNavigation(key, val, postfix);
                        }
                    }
                }
            }
        }

        String getFlowMapPostfixFor(String key, String value) {
            if (useZeroPostfix) return "0";
            return navigation.getOrDefault(key, new HashMap<>()).getOrDefault(value, "0");
        }

        @Override
        String getIdent() {
            return identification;
        }

        private void addNavigation(String key, String val, String postfix) {
            if (navigation.containsKey(key)) {
                navigation.get(key).putIfAbsent(val, postfix);
            } else {
                navigation.put(key, new HashMap<>(Map.of(val, postfix)));
            }
        }

    }


    private static class NavigationNode {
        final VBox nodeBox;
        final Circle nodeFX;
        final String navIdent;
        int next = 0;

        NavigationNode(VBox box, Circle n, String navIdent) {
            nodeBox = box;
            nodeFX = n;
            this.navIdent = navIdent;
        }

        String getNavIdent() {
            return navIdent;
        }

        String getNextName() {
            next++;
            return navIdent + next + ".";
        }

        String getIdent() {
            return "";
        }
    }



    private static class RuntimeNode {
        final NodeDTO node;
        final FlowMapDTO flowMap;
        final NavigationNode navNode;
        private Optional<TreeItem<Label>> presentationItem = Optional.empty();

        RuntimeNode(NodeDTO n, FlowMapDTO o, NavigationNode navNode) {
            node = n;
            flowMap = o;
            this.navNode = navNode;
        }

        String getIdentification() {
            return flowMap.getIdentification();
        }

        String getAddress() {
            return node.getAddress();
        }

        String getNodeType() {
            return node.getType();
        }

        String flowTo() {
            return flowMap.getFlowTo();
        }

        Map<String, ?> getNodeConfiguration() {
            return node.getNodeConfiguration();
        }

        Map<String, ?> getMapContent() {
            return flowMap.getContent();
        }

        void setPresentationItem(TreeItem<Label> item) {
            if (item == null) presentationItem = Optional.empty();
            else presentationItem = Optional.of(item);
        }

        Optional<TreeItem<Label>> getPresentationItem() {
            return presentationItem;
        }
    }



    private static class OutgoingLine {
        final Line line;
        final NavigationNode goesTo;

        OutgoingLine(Line line, NavigationNode goesTo) {
            this.line = line;
            this.goesTo = goesTo;
        }
    }
}
