package scraper.debugger.core;

import scraper.api.*;
import scraper.debugger.dto.FlowDTO;
import scraper.debugger.dto.NodeDTO;

import java.util.Map.Entry;
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
        UUID parent = o.getParentId().orElse(null);
        UUID id = o.getId();
        boolean parentPermitted = parent == null || FP.exists(parent) || !FI.exists(parent);

        boolean step = false;

        // check permission, inherited from parent
        if (!parentPermitted) {
            FP.remove(id);
            step = true;
        } else {
            // check breakpoint
            if (STATE.isBreakpoint(n.getAddress())) {
                FP.remove(id);
            }
        }

        if (!FP.exists(id)) {
            String format = step ? "STEP -> {0}" : "BREAKPOINT -> {0}";

            STATE.waitOnBreakpoint(() -> {
                STATE.l.log(Level.INFO, format, n.getAddress().getRepresentation());
                SERVER.sendBreakpointHit(FI.getDTO(id));
                FI.releaseBranchLock(id);
            });

            while(!FP.exists(id)) {
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
                    ACTIONS.l.log(Level.INFO, "{0} in {1}", any.getMessage(), adr);
                    if (sendBreak) SERVER.sendBreakpointHit(FI.getDTO(id));
                    FI.releaseBranchLock(id);
                });

                while (!FP.exists(id) && !ACTIONS.checkChangeOrAbortMsg(id)) {
                    // waits until node change or abort
                    STATE.waitOnBreakpoint();
                }

                FI.acquireBranchLock(id);

                // checks until no exception
                checkException(n, o, false);
            }
        }
    }

    public String toString() {
        return "DebuggerFlowFilter";
    }
}
