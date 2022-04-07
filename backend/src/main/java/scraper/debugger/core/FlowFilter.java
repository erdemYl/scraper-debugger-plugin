package scraper.debugger.core;

import scraper.api.*;
import scraper.debugger.dto.FlowMapDTO;
import scraper.debugger.dto.NodeDTO;

import java.util.AbstractMap;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.UUID;
import java.lang.System.Logger.Level;


public class FlowFilter {

    // Debugger components
    private final DebuggerActions ACTIONS;
    private final DebuggerServer SERVER;
    private final DebuggerState STATE;
    private final FlowIdentifier FI;
    private final FlowPermissions FP;

    public FlowFilter(DebuggerActions ACTIONS, DebuggerServer SERVER, DebuggerState STATE, FlowIdentifier FI, FlowPermissions FP) {
        this.ACTIONS = ACTIONS;
        this.SERVER = SERVER;
        this.STATE = STATE;
        this.FI = FI;
        this.FP = FP;
    }

    public void filter(NodeContainer<? extends Node> n, FlowMap o) {
        Optional<UUID> parent = o.getParentId();
        UUID id = o.getId();
        boolean parentPermission = parent.isEmpty()
                || FP.permitted(parent.get())
                || !FI.exists(parent.get());

        boolean step = false;

        // check permission, inherited from parent
        if (!parentPermission) {
            FP.remove(id);
            step = true;
        } else {
            // check breakpoint
            if (STATE.isBreakpoint(n.getAddress())) {
                FP.remove(id);
            }
        }

        if (FP.notPermitted(id)) {
            String format = step ? "STEP -> {0}" : "BREAKPOINT -> {0}";
            STATE.waitOnBreakpoint(() -> {
                STATE.l.log(Level.INFO, format, n.getAddress().getRepresentation());
                Entry<NodeDTO, FlowMapDTO> dto = getDTOs(n, o);
                SERVER.sendBreakpointHit(dto.getKey(), dto.getValue());
                FI.releaseBranchLock(id);
            });

            while(FP.notPermitted(id)) {
                STATE.waitOnBreakpoint();
            }

            // message box
            ACTIONS.checkLeftMessages(id);

            // processing continues
            FI.acquireBranchLock(id);

            checkException(n, o, false);
            return;
        }

        checkException(n, o, true);
    }

    private void checkException(NodeContainer<? extends Node> n, FlowMap o, boolean sendBreak) {
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
                STATE.waitOnBreakpoint(() -> {
                    ACTIONS.l.log(System.Logger.Level.INFO, "{0} in {1}", any.getMessage(), adr);
                    Entry<NodeDTO, FlowMapDTO> dto = getDTOs(n, o);
                    if (sendBreak) SERVER.sendBreakpointHit(dto.getKey(), dto.getValue());
                    FI.releaseBranchLock(id);
                });

                while (FP.notPermitted(id) && !ACTIONS.checkChangeOrAbortMsg(id)) {
                    // waits until node change or abort
                    STATE.waitOnBreakpoint();
                }

                FI.acquireBranchLock(id);

                // checks until no exception
                checkException(n, o, false);
            }
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

    public String toString() {
        return "DebuggerFlowFilter";
    }
}
