package scraper.debugger.frontend.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import scraper.debugger.dto.DataflowDTO;

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
            if (!executionStarted) {
                executionStarted = true;
                socket.send(wrap("startExecution", ""));
            }
        });
    }

    public void requestContinueExecution() {
        executionSender.execute(() -> {
            if (executionStarted) socket.send(wrap("continueExecution", ""));
        });
    }

    public void requestStopExecution() {
        executionSender.execute(() -> {
            if (executionStarted) socket.send(wrap("stopExecution", ""));
        });
    }

    public void requestSetBreakpoint(String address) {
        executionSender.execute(() -> {
            socket.send(wrap("setBreakpoint", address));
        });
    }

    public void requestResumeSelected(String flowIdent) {
        executionSender.execute(() -> {
            if (executionStarted) socket.send(wrap("resumeSelected", flowIdent));
        });
    }

    public void requestStopSelected(String flowIdent) {
        executionSender.execute(() -> {
            if (executionStarted) socket.send(wrap("stopSelected", flowIdent));
        });
    }

    public void requestResumeAllContinueExecution() {
        executionSender.execute(() -> {
            if (executionStarted) socket.send(wrap("resumeAllContinueExecution", ""));
        });
    }

    public void requestStepAll() {
        executionSender.execute(() -> {
            if (executionStarted) socket.send(wrap("stepAll", "Stop"));
        });
    }

    public void requestStepAllContinueExecution() {
        executionSender.execute(() -> {
            if (executionStarted) socket.send(wrap("stepAllContinueExecution", ""));
        });
    }

    public void requestStepSelected(String flowIdent) {
        executionSender.execute(() -> {
            if (executionStarted) socket.send(wrap("stepSelected", flowIdent));
        });
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


    public Deque<DataflowDTO> requestLifecycleQuery(LifecycleQuery query, String ident) throws ExecutionException, InterruptedException {
        Future<Deque<DataflowDTO>> response = querySender.submit(() -> {
            try {
                socket.send(wrap(query.toRequestString(), ident));
                return socket.getQueryQueue().remove();
            } catch (Exception any) {
                throw new RuntimeException("Cannot request lifecycle query.");
            }
        });
        return response.get();
    }


    private String wrap(String request, Object content) {
        try {
            return m.writeValueAsString(java.util.Map.of("request", request, "content", content));
        } catch (JsonProcessingException e) {
            return "Error: " + e.getMessage();
        }
    }
}
