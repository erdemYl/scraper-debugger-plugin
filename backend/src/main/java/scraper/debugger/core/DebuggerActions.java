package scraper.debugger.core;


import scraper.debugger.addon.DebuggerHook;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import scraper.debugger.core.FlowIdentifier.LifecycleFilter;


public final class DebuggerActions {

    // Logger with actually intended name
    public final System.Logger l = System.getLogger("Debugger");
    private final System.Logger.Level info = System.Logger.Level.INFO;

    // Frontend left-messages
    private final LeftMessages leftMessages;

    // Debugger components
    private final DebuggerServer SERVER;
    private final DebuggerState STATE;
    private final FlowIdentifier FI;
    private final FlowPermissions FP;

    public DebuggerActions(DebuggerServer SERVER, DebuggerState STATE, FlowIdentifier FI, FlowPermissions FP) {
        this.SERVER = SERVER;
        this.STATE = STATE;
        this.FI = FI;
        this.FP = FP;
        leftMessages = new LeftMessages(FP);
    }

    private static final class LeftMessages {

        final Map<UUID, String> messages = new ConcurrentHashMap<>();
        final Map<UUID, LeftMsgType> types = new ConcurrentHashMap<>();
        final FlowPermissions FP;

        LeftMessages(FlowPermissions FP) {
            this.FP = FP;
        }

        void addStopMsg(UUID id) {
            messages.putIfAbsent(id, "");
            types.putIfAbsent(id, LeftMsgType.STOP);
        }

        void consumeMsgFor(UUID id) {
            types.computeIfPresent(id, (i, t) -> {
                messages.remove(id);
                switch (t) {
                    case STOP -> {
                        FP.remove(id);
                        return null;
                    }
                    case ABORT -> {
                        System.getLogger("Debugger").log(
                                System.Logger.Level.WARNING, "Aborting"
                        );
                        Thread.currentThread().interrupt();
                    }
                }
                return t;
            });
        }

        boolean nodeChangeOrAbort(UUID id) {
            LeftMsgType type = types.get(id);
            if (type == null) return false;
            switch (type) {
                case CHANGE_NODE_DEF, CHANGE_NODE_CONFIG, ABORT -> {
                    return true;
                }
                default -> {
                    return false;
                }
            }
        }
    }

    private enum LeftMsgType {
        STOP,
        CHANGE_NODE_CONFIG,
        CHANGE_NODE_DEF,
        ABORT
    }


    void checkLeftMessages(UUID id) {
        leftMessages.consumeMsgFor(id);
    }


    boolean checkChangeOrAbortMsg(UUID id) {
        if (leftMessages.nodeChangeOrAbort(id)) {
            leftMessages.consumeMsgFor(id);
            return true;
        }
        return false;
    }


    //==============
    // CLIENT API
    //==============

    void requestSpecification() {
        // Note that debugger can handle only one specification
        l.log(info, "Requesting specification");
        SERVER.sendSpecification(DebuggerHook.getJobInstance(), DebuggerHook.getJobCFG());
    }

    void startExecution() {
        STATE.setStart();
    }

    void setBreakpoint(String address) {
        STATE.addBreakpoint(address);
    }

    void continueExecution() {
        STATE.setContinue();
    }

    void stopExecution() {
        FP.removeAll();
        STATE.l.log(info, "ALL FLOWS STOPPED");
    }

    void stepAll() {
        FI.forEachIdentified(id -> {
            leftMessages.addStopMsg(id);
            FP.create(id);
        });
    }

    void stepSelected(String ident) {
        UUID id = FI.toUUID(ident);
        leftMessages.addStopMsg(id);
        FP.create(id);
    }

    void resumeAll() {
        FI.forEachIdentified(FP::create);
    }

    void resumeSelected(String ident) {
        FP.create(FI.toUUID(ident));
    }

    void stopSelected(String ident) {
        FP.remove(FI.toUUID(ident));
    }

    void stepAllContinueExecution() {
        stepAll();
        continueExecution();
    }

    void resumeAllContinueExecution() {
        resumeAll();
        continueExecution();
    }


    //============
    // QUERY API
    //============

    void queryWholeLifecycle(String ident) {
        SERVER.sendLifecycle(FI.getLifecycle(LifecycleFilter.NORMAL, ident));
    }

    void queryToEmitterNodes(String ident) {
        SERVER.sendLifecycle(FI.getLifecycle(LifecycleFilter.TO_FLOW_EMITTER, ident));
    }

    void queryToEmitterNotForkNodes(String ident) {
        SERVER.sendLifecycle(FI.getLifecycle(LifecycleFilter.TO_FLOW_EMITTER_NOT_FORK, ident));
    }

    void queryToForkNodes(String ident) {
        SERVER.sendLifecycle(FI.getLifecycle(LifecycleFilter.TO_FORK, ident));
    }

    void queryNotToEmitterNodes(String ident) {
        SERVER.sendLifecycle(FI.getLifecycle(LifecycleFilter.NOT_TO_FLOW_EMITTER, ident));

    }


    @Override
    public String toString() {
        return "DebuggerActions";
    }
}
