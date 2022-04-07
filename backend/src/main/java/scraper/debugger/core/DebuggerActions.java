package scraper.debugger.core;


import scraper.debugger.addon.DebuggerHook;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


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

    public void requestSpecification() {
        // Note that debugger can handle only one specification
        l.log(info, "Requesting specification");
        SERVER.sendSpecification(DebuggerHook.spec.getKey(), DebuggerHook.spec.getValue());
    }

    public void startExecution() {
        STATE.setStart();
    }

    public void setBreakpoint(String address) {
        STATE.addBreakpoint(address);
    }

    public void continueExecution() {
        STATE.setContinue();
    }

    public void stopExecution() {
        try {
            STATE.BARGE_IN.lock();
            FP.removeAll();
        } finally {
            STATE.l.log(info, "ALL FLOWS STOPPED");
            STATE.BARGE_IN.unlock();
        }
    }

    public void stepAll() {
        FI.forEachIdentified(id -> {
            leftMessages.addStopMsg(id);
            FP.create(id);
        });
        continueExecution();
    }

    public void stepSelected(String uuid) {
        UUID id = UUID.fromString(uuid);
        leftMessages.addStopMsg(id);
        FP.create(id);
    }

    public void resumeAll() {
        FI.forEachIdentified(FP::create);
    }

    public void resumeSelected(String uuid) {
        FP.create(UUID.fromString(uuid));
    }

    public void stopSelected(String uuid) {
        FP.remove(UUID.fromString(uuid));
    }

    public void resumeAllContinueExecution() {
        resumeAll();
        continueExecution();
    }

    @Override
    public String toString() {
        return "DebuggerActions";
    }
}
