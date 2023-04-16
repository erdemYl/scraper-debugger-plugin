package scraper.debugger.core;


import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scraper.debugger.addon.DebuggerHookAddon;


@SuppressWarnings("unused") // API convention
public final class DebuggerActions {
    final Logger l = LoggerFactory.getLogger("Debugger");

    // Frontend left-messages
    private final Map<UUID, Message> leftMessages;

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
        leftMessages = new ConcurrentHashMap<>();
    }

    private enum Message {
        STEP, ABORT
    }

    void checkLeftMessages(UUID id, Runnable ifStep, Runnable ifAbort) {
        Message msg = leftMessages.remove(id);
        if (msg == Message.ABORT) ifAbort.run();
        if (msg == Message.STEP) ifStep.run();
    }


    //==============
    // CLIENT API
    //==============

    void requestSpecification() {
        SERVER.sendSpecification(DebuggerHookAddon.jobInstance, DebuggerHookAddon.jobCFG);
    }

    void startExecution() {
        STATE.setStart();
    }

    void continueExecution() { STATE.setContinue(); }

    void stopExecution() { FP.removeAll(); }

    void setBreakpoint(CharSequence address) {
        STATE.addBreakpoint(address.toString());
    }

    void resumeAll() {
        FI.forEachIdentified(FP::create);
    }

    void resumeSelected(CharSequence ident) {
        FP.create(FI.toUUID(ident));
    }

    void stepAll() {
        FI.forEachIdentified(id -> {
            leftMessages.put(id, Message.STEP);
            FP.create(id);
        });
    }

    void stepSelected(CharSequence ident) {
        UUID id = FI.toUUID(ident);
        leftMessages.put(id, Message.STEP);
        FP.create(id);
    }

    void abortSelected(CharSequence ident) {
        UUID id = FI.toUUID(ident);
        leftMessages.put(id, Message.ABORT);
        FP.create(id);
    }

    void requestFlowMap(CharSequence ident) {
        SERVER.sendFlowMap(FI.getFlowMapDTO(ident));
    }

    void requestLifecycle(CharSequence ident) {
        SERVER.sendLifecycle(FI.getLifecycle(ident));
    }

    @Override
    public String toString() {
        return "DebuggerActions";
    }
}
