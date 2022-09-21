package scraper.debugger.frontend.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import scraper.debugger.dto.FlowMapDTO;

import java.util.Deque;
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

    public void requestStartExecution() {
        executionSender.execute(() -> {
            if (!executionStarted && socket.isOpen()) {
                executionStarted = true;
                socket.send(wrap("startExecution", ""));
            }
        });
    }

    public void requestContinueExecution() {
        executionSender.execute(() -> {
            if (executionStarted && socket.isOpen()) socket.send(wrap("continueExecution", ""));
        });
    }

    public void requestStopExecution() {
        executionSender.execute(() -> {
            if (executionStarted && socket.isOpen()) socket.send(wrap("stopExecution", ""));
        });
    }

    public void requestSetBreakpoint(String address) {
        executionSender.execute(() -> {
            socket.send(wrap("setBreakpoint", address));
        });
    }

    public void requestResumeSelected(CharSequence ident) {
        executionSender.execute(() -> {
            if (executionStarted && socket.isOpen()) socket.send(wrap("resumeSelected", ident));
        });
    }

    public void requestStopSelected(CharSequence ident) {
        executionSender.execute(() -> {
            if (executionStarted && socket.isOpen()) socket.send(wrap("stopSelected", ident));
        });
    }

    public void requestResumeAllContinueExecution() {
        executionSender.execute(() -> {
            if (executionStarted && socket.isOpen()) socket.send(wrap("resumeAllContinueExecution", ""));
        });
    }

    public void requestStepAll() {
        executionSender.execute(() -> {
            if (executionStarted && socket.isOpen()) socket.send(wrap("stepAll", ""));
        });
    }

    public void requestStepAllContinueExecution() {
        executionSender.execute(() -> {
            if (executionStarted && socket.isOpen()) socket.send(wrap("stepAllContinueExecution", ""));
        });
    }

    public void requestStepSelected(CharSequence ident) {
        executionSender.execute(() -> {
            if (executionStarted && socket.isOpen()) socket.send(wrap("stepSelected", ident));
        });
    }

    public void requestAbortSelected(CharSequence ident) {
        executionSender.execute(() -> {
            if (executionStarted && socket.isOpen()) socket.send(wrap("abortSelected", ident));
        });
    }


    //=============
    // QUERY API
    //=============

    public enum LifecycleQuery {
        ONE,
        WHOLE,
        TO_EMITTER_NODES,
        TO_EMITTER_NOT_FORK_NODES,
        TO_FORK_NODES,
        NOT_TO_EMITTER_NODES;

        private String toRequestString() {
            switch (this) {
                case ONE: {
                    return "queryOneFlow";
                }
                case WHOLE: {
                    return "queryWholeLifecycle";
                }
                case TO_EMITTER_NODES: {
                    return "queryToEmitterNodes";
                }
                case TO_EMITTER_NOT_FORK_NODES: {
                    return "queryToEmitterNotForkNodes";
                }
                case TO_FORK_NODES: {
                    return "queryToForkNodes";
                }
                case NOT_TO_EMITTER_NODES: {
                    return "queryNotToEmitterNodes";
                }
            }
            return "queryWholeLifecycle";
        }
    }


    public Deque<FlowMapDTO> requestLifecycleQuery(LifecycleQuery query, CharSequence ident) {
        Future<Deque<FlowMapDTO>> response = querySender.submit(() -> {
            try {
                socket.send(wrap(query.toRequestString(), ident));
                return socket.getQueryQueue().take();
            } catch (Exception any) {
                throw new RuntimeException("Cannot request lifecycle query.");
            }
        });

        try {
            return response.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Error during getting response from query.");
        }
    }

    public FlowMapDTO requestDataflowQuery(CharSequence ident) {
        return requestLifecycleQuery(LifecycleQuery.ONE, ident).pop();
    }


    private String wrap(String request, Object content) {
        try {
            return m.writeValueAsString(java.util.Map.of("request", request, "content", content));
        } catch (JsonProcessingException e) {
            return "Error: " + e.getMessage();
        }
    }
}
