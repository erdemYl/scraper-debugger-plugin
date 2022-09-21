package scraper.debugger.core;


import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scraper.debugger.addon.DebuggerHookAddon;
import scraper.debugger.core.FlowIdentifier.LifecycleFilter;


@SuppressWarnings("unused") // API convention
public final class DebuggerActions {

    // Logger with actually intended name
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

    void continueExecution() {
        STATE.setContinue();
        // auto send finish signal
        if (DebuggerHookAddon.workflowFinished()) {
            STATE.l.info("Workflow finished");
            SERVER.sendFinishSignal();
        }
    }

    void stopExecution() {
        FP.removeAll();
        if (!DebuggerHookAddon.workflowFinished()) STATE.l.info("Workflow stopped");
    }

    void setBreakpoint(CharSequence address) {
        STATE.addBreakpoint(address.toString());
    }

    void stepAll() {
        FI.forEachIdentified(id -> {
            leftMessages.put(id, Message.STEP);
            FP.create(id);
        });
    }

    void resumeAll() {
        FI.forEachIdentified(FP::create);
    }

    void stepSelected(CharSequence ident) {
        UUID id = FI.toUUID(ident);
        leftMessages.put(id, Message.STEP);
        FP.create(id);
    }

    void resumeSelected(CharSequence ident) {
        FP.create(FI.toUUID(ident));
    }

    void abortSelected(CharSequence ident) {
        UUID id = FI.toUUID(ident);
        leftMessages.put(id, Message.ABORT);
        FP.create(id);
    }


    //============
    // QUERY API
    //============

    void queryOneFlow(CharSequence ident) {
        SERVER.sendLifecycle(FI.getLifecycle(LifecycleFilter.ONE, ident));
    }

    void queryWholeLifecycle(CharSequence ident) {
        SERVER.sendLifecycle(FI.getLifecycle(LifecycleFilter.NORMAL, ident));
    }

    void queryToEmitterNodes(CharSequence ident) {
        SERVER.sendLifecycle(FI.getLifecycle(LifecycleFilter.TO_FLOW_EMITTER, ident));
    }

    void queryToEmitterNotForkNodes(CharSequence ident) {
        SERVER.sendLifecycle(FI.getLifecycle(LifecycleFilter.TO_FLOW_EMITTER_NOT_FORK, ident));
    }

    void queryToForkNodes(CharSequence ident) {
        SERVER.sendLifecycle(FI.getLifecycle(LifecycleFilter.TO_FORK, ident));
    }

    void queryNotToEmitterNodes(CharSequence ident) {
        SERVER.sendLifecycle(FI.getLifecycle(LifecycleFilter.NOT_TO_FLOW_EMITTER, ident));
    }


    @Override
    public String toString() {
        return "DebuggerActions";
    }
}
