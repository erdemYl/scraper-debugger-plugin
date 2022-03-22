package scraper.debugger.core;

import scraper.api.*;
import scraper.debugger.addon.DebuggerHook;
import scraper.debugger.dto.FlowMapDTO;
import scraper.debugger.dto.NodeDTO;

import java.util.AbstractMap;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.*;


public final class DebuggerNodeHook implements NodeHook {

    // debugger components
    private final DebuggerState STATE;
    private final DebuggerActions ACTIONS;
    private final DebuggerServer SERVER;
    private final FlowIdentifier FI;
    private final FlowPermissions FP;

    // Log handler for redirecting logs to frontend
    private final Handler redirect;

    public DebuggerNodeHook(DebuggerState STATE, DebuggerActions ACTIONS, DebuggerServer SERVER, FlowIdentifier FI, FlowPermissions FP) {
        this.STATE = STATE;
        this.ACTIONS = ACTIONS;
        this.SERVER = SERVER;
        this.FI = FI;
        this.FP = FP;

        redirect = new Handler() {
            final Formatter formatter = new SimpleFormatter();

            @Override
            public void publish(LogRecord record) {
                SERVER.sendLogMessage(formatter.format(record));
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() throws SecurityException {
            }
        };
    }

    @Override
    public void beforeProcess(NodeContainer<? extends Node> n, FlowMap o) {

        // sequential identification
        FI.identify(n, o, true);

        redirectLogToFrontend(n);

        // flow stops if necessary, break update will send only once
        boolean sent;
        sent = checkPermission(n, o, true);
        sent = checkBreakpoint(n, o, true, !sent);
        checkException(n, o, !sent);
    }

    @Override
    public void afterProcess(NodeContainer<? extends Node> n, FlowMap o) {
        if (isEndNode(n)) {
            Entry<NodeDTO, FlowMapDTO> dtos = getDTOs(n, o);
            SERVER.sendFinishedFlow(dtos.getKey(), dtos.getValue(), true);
        }

        FI.releaseBranchLock(o.getId());
    }

    @Override
    public int order() {
        return Integer.MIN_VALUE;
    }

    @Override
    public int compareTo(NodeHook other) {
        return NodeHook.super.compareTo(other);
    }


    public void redirectLogToFrontend(NodeContainer<? extends Node> n) {
        Logger l = Logger.getLogger(n.getAddress().toString());
        if (l.getUseParentHandlers()) {
            l.setUseParentHandlers(false);
            l.addHandler(redirect);
        }
    }

    public Entry<NodeDTO, FlowMapDTO> getDTOs(NodeContainer<? extends Node> n, FlowMap o) {
        UUID id = o.getId();
        Optional<UUID> parent = o.getParentId();
        String pIdent = parent.isEmpty() ? "" : FI.getExact(parent.get());

        NodeDTO dto1 = new NodeDTO(n);
        FlowMapDTO dto2 = new FlowMapDTO(
                o,
                FI.getExact(id),
                pIdent,
                FI.treeLevelOf(id),
                FlowIdentifier.FlowTo.get(n)
        );
        return new AbstractMap.SimpleImmutableEntry<>(dto1, dto2);

    }


    public Set<String> getEndNodeAddresses() {
        return DebuggerHook.endNodes;
    }



    //================
    // HOOK METHODS
    //================

    private boolean checkPermission(NodeContainer<? extends Node> n, FlowMap o, boolean before) {
        Optional<UUID> parent = o.getParentId();
        boolean parentPermission = parent.isEmpty() || hasPermission(parent.get());

        if (!parentPermission) {
            UUID id = o.getId();

            // permission inherited from parent
            FP.remove(id);

            Runnable onWait = () -> {
                STATE.l.log(System.Logger.Level.INFO, "STEP TO ({0}): {1} TRIGGERED", before ? "B" : "A", n.getAddress().getRepresentation());
                String ident = FI.getExact(id);
                String pIdent = FI.getExact(parent.get());
                Entry<NodeDTO, FlowMapDTO> dtos = getDTOs(n, o, ident, pIdent, FI.treeLevelOf(id), FlowIdentifier.FlowTo.get(n));

                SERVER.sendBreakpointHit(dtos.getKey(), dtos.getValue());
                FI.releaseBranchLock(id);
            };

            STATE.waitOnBreakpoint(onWait);

            // no further processing until permission
            while(!hasPermission(id)) {
                STATE.waitOnBreakpoint();
            }

            // message box
            ACTIONS.checkLeftMessages(id);

            // processing continues
            FI.acquireBranchLock(id);

            return true;
        }
        return false;
    }

    private boolean checkBreakpoint(NodeContainer<? extends Node> n, FlowMap o, boolean beforeBP, boolean send) {
        if (STATE.isBreakpoint(n.getAddress(), beforeBP)) {
            UUID id = o.getId();
            Optional<UUID> parent = o.getParentId();

            FP.remove(id);

            Runnable onWait = () -> {
                STATE.l.log(System.Logger.Level.INFO, "BREAKPOINT ({0}): {1} TRIGGERED", beforeBP ? "B" : "A", n.getAddress().getRepresentation());
                if (send) {
                    String ident = FI.getExact(id);
                    Optional<String> pIdent = FI.getOptional(parent.orElse(null));
                    Entry<NodeDTO, FlowMapDTO> dtos = getDTOs(
                            n, o, ident, pIdent.orElse(""), FI.treeLevelOf(id), FlowIdentifier.FlowTo.get(n));
                    SERVER.sendBreakpointHit(dtos.getKey(), dtos.getValue());
                }
                FI.releaseBranchLock(id);
            };

            STATE.waitOnBreakpoint(onWait);

            while(!hasPermission(id)) {
               STATE.waitOnBreakpoint();
            }

            // message box
            ACTIONS.checkLeftMessages(id);

            // processing continues
           FI.acquireBranchLock(id);

           return true;
        }
        return false;
    }

    private void checkException(NodeContainer<? extends Node> n, FlowMap o, boolean send) {
        if (n.getKeySpec("failOnException").isPresent()) {
            FlowMap copy = o.copy();
            UUID id = o.getId();
            String adr = n.getAddress().toString();
            Node node = n.getC();
            try {
                // checks only fun-nodes since they can't forward or emit flows
                if (node instanceof FunctionalNode) {
                    ((FunctionalNode) node).modify((FunctionalNodeContainer) n, copy);
                }
            }
            catch (Exception any) {
                FP.remove(id);

                Runnable onWait = () -> {
                    ACTIONS.l.log(System.Logger.Level.INFO, "{0} in {1}", any.getMessage(), adr);
                    if (send) {
                        Entry<NodeDTO, FlowMapDTO> dtos = getDTOs(n, o);
                        SERVER.sendBreakpointHit(dtos.getKey(), dtos.getValue());
                    }
                    FI.releaseBranchLock(id);
                };

                STATE.waitOnBreakpoint(onWait);

                while (!hasPermission(id) && !ACTIONS.checkChangeOrAbortMsg(id)) {
                    // waits until node change or abort
                    STATE.waitOnBreakpoint();
                }

                FI.acquireBranchLock(id);

                // checks until no exception
                checkException(n, o, false);
            }
        }
    }

    private Entry<NodeDTO, FlowMapDTO> getDTOs(
            NodeContainer<? extends Node> n,
            FlowMap o,
            String ident,
            String pIdent,
            int level,
            FlowIdentifier.FlowTo flowTo)
    {
        NodeDTO dto1 = new NodeDTO(n);
        FlowMapDTO dto2 = new FlowMapDTO(o, ident, pIdent, level, flowTo);
        return new AbstractMap.SimpleImmutableEntry<>(dto1, dto2);
    }

    private boolean isEndNode(NodeContainer<? extends Node> n) {
        return DebuggerHook.endNodes.contains(n.getAddress().toString());
    }

    private boolean hasPermission(UUID id) {
        return FP.get(id) || !FI.exists(id);
    }


    @Override
    public String toString() {
        return "DebuggerNodeHook";
    }
}
