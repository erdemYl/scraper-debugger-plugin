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
    private final Set<UUID> leftMessages;

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
        leftMessages = ConcurrentHashMap.newKeySet();
    }

    void checkLeftMessages(UUID id) {
        if (leftMessages.remove(id))
            FP.remove(id);
    }

    boolean checkChangeOrAbortMsg(UUID id) {
        return false;
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

    void setBreakpoint(CharSequence address) {
        STATE.addBreakpoint(address.toString());
    }

    void continueExecution() {
        STATE.setContinue();
    }

    void stopExecution() {
        FP.removeAll();
        STATE.l.info("Workflow stopped");
    }

    void stepAll() {
        FI.forEachIdentified(id -> {
            leftMessages.add(id);
            FP.create(id);
        });
    }

    void stepSelected(CharSequence ident) {
        UUID id = FI.toUUID(ident);
        leftMessages.add(id);
        FP.create(id);
    }

    void resumeAll() {
        FI.forEachIdentified(FP::create);
    }

    void resumeSelected(CharSequence ident) {
        FP.create(FI.toUUID(ident));
    }

    void stopSelected(CharSequence ident) {
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
