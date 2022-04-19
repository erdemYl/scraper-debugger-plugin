package scraper.debugger.frontend.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import scraper.debugger.dto.FlowDTO;

import java.util.Deque;
import java.util.LinkedList;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class FrontendActions {

    // Socket to backend
    private final FrontendWebSocket socket;

    // Threads for communication
    private final ExecutorService executionSender, querySender;

    // Json object mapper
    private final ObjectMapper m = new ObjectMapper();

    private final AtomicBoolean connected = new AtomicBoolean(false);
    private boolean executionStarted = false;

    public FrontendActions(FrontendWebSocket socket) {
        this.socket = socket;
        executionSender = Executors.newSingleThreadExecutor();
        querySender = Executors.newSingleThreadExecutor();
    }

    public void connectToBackend() {
        synchronized (connected) {
            if (!connected.get()) {
                socket.connect();
                connected.set(true);
            }
        }
    }

    //================
    // EXECUTION API
    //================

    public synchronized void requestStartExecution() {
        if (!executionStarted) {
            executionStarted = true;
            executionSender.execute(() -> socket.send(wrap("startExecution", "")));
        }
    }

    public synchronized void requestContinueExecution() {
        if (executionStarted) executionSender.execute(() -> socket.send(wrap("continueExecution", "")));
    }

    public synchronized void requestStopExecution() {
        if (executionStarted) executionSender.execute(() -> socket.send(wrap("stopExecution", "")));
    }

    public synchronized void requestSetBreakpoint(String address) {
        executionSender.execute(() -> socket.send(wrap("setBreakpoint", address)));
    }

    public synchronized void requestResumeSelected(String flowIdent) {
        if (executionStarted) executionSender.execute(() -> socket.send(wrap("resumeSelected", flowIdent)));
    }

    public synchronized void requestStopSelected(String flowIdent) {
        if (executionStarted) executionSender.execute(() -> socket.send(wrap("stopSelected", flowIdent)));
    }

    public synchronized void requestResumeAllContinueExecution() {
        if (executionStarted) executionSender.execute(() -> socket.send(wrap("resumeAllContinueExecution", "")));
    }

    public synchronized void requestStepAll() {
        if (executionStarted) executionSender.execute(() -> socket.send(wrap("stepAll", "Stop")));
    }

    public synchronized void requestStepAllContinueExecution() {
        if (executionStarted) executionSender.execute(() -> socket.send(wrap("stepAllContinueExecution", "")));
    }

    public synchronized void requestStepSelected(String flowIdent) {
        if (executionStarted) executionSender.execute(() -> socket.send(wrap("stepSelected", flowIdent)));
    }


    //=============
    // QUERY API
    //=============

    public enum LifecycleQuery {
        WHOLE,
        TO_EMITTER_NODES,
        TO_EMITTER_NOT_FORK_NODES,
        TO_FORK_NODES,
        NOT_TO_EMITTER_NODES;

        private String toRequestString() {
            switch (this) {
                case WHOLE -> {
                    return "queryWholeLifecycle";
                }
                case TO_EMITTER_NODES -> {
                    return "queryToEmitterNodes";
                }
                case TO_EMITTER_NOT_FORK_NODES -> {
                    return "queryToEmitterNotForkNodes";
                }
                case TO_FORK_NODES -> {
                    return "queryToForkNodes";
                }
                case NOT_TO_EMITTER_NODES -> {
                    return "queryNotToEmitterNodes";
                }
            }
            return "queryWholeLifecycle";
        }
    }


    public synchronized Deque<FlowDTO> requestLifecycleQuery(LifecycleQuery query, String ident) throws ExecutionException, InterruptedException {
        if (executionStarted) {
            Future<Deque<FlowDTO>> response = querySender.submit(() -> {
                socket.send(wrap(query.toRequestString(), ident));
                return socket.getQueryQueue().remove();
            });
            return response.get();
        }
        throw new RuntimeException("Execution not started yet!");
    }


    private String wrap(String request, Object content) {
        try {
            return m.writeValueAsString(java.util.Map.of("request", request, "content", content));
        } catch (JsonProcessingException e) {
            return "Error: " + e.getMessage();
        }
    }
}
