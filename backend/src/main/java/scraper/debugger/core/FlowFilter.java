package scraper.debugger.core;

import scraper.api.*;

import java.util.UUID;

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
        UUID parent = o.getParentId().orElseThrow(() -> new NodeException("Distinct service group needed!"));
        UUID id = o.getId();
        boolean step = false;

        // check permission, inherited from parent
        if (!FP.exists(parent)) {
            FP.remove(id);
            step = true;
        } else {
            // check breakpoint
            if (STATE.isBreakpoint(n.getAddress())) {
                FP.remove(id);
            }
        }

        if (!FP.exists(id)) {
            String format = step ? "Step" : "Breakpoint";

            STATE.waitOnBreakpoint(() -> {
                STATE.l.info("{} -> {}", format, n.getAddress().getRepresentation());
                SERVER.sendBreakpointHit(FI.getFlowDTO(id));
            });

            while(!FP.exists(id)) {
                STATE.waitOnBreakpoint();
            }

            // message box
            ACTIONS.checkLeftMessages(id,
                    () -> FP.remove(id),
                    () -> {
                        FI.markAborted(n.getAddress(), o);
                        STATE.l.info("Abort");
                        throw new NodeException("Abort");
                    }
            );

            checkException(n, o, false);
            return;
        }

        checkException(n, o, true);
    }

    private void checkException(NodeContainer<? extends Node> n, FlowMap o, boolean sendBreak) {
        if (n.getKeySpec("failOnException").isPresent()) {
            FlowMap copy = o.copy();
            UUID id = o.getId();
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
                    ACTIONS.l.info(any.getMessage());
                    if (sendBreak) SERVER.sendBreakpointHit(FI.getFlowDTO(id));
                });

                // flow waits until abort
                while (!FP.exists(id)) {
                    STATE.waitOnBreakpoint();
                    ACTIONS.checkLeftMessages(id,
                            () -> {},
                            () -> {
                                FI.markAborted(n.getAddress(), o);
                                STATE.l.info("Abort");
                                throw new NodeException("Abort");
                            }
                    );
                    FP.remove(id);
                }

                // checks until no exception
                checkException(n, o, false);
            }
        }
    }

    public String toString() {
        return "DebuggerFlowFilter";
    }
}
