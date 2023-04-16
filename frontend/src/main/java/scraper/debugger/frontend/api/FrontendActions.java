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

    private final AtomicBoolean firstConnection = new AtomicBoolean(true);

    public FrontendActions(FrontendWebSocket socket) {
        this.socket = socket;
        executionSender = Executors.newSingleThreadExecutor();
        querySender = Executors.newSingleThreadExecutor();
    }

    public void connect() {
        executionSender.execute(() -> {
            if (firstConnection.getAndSet(false)) socket.connect();
            else socket.reconnect();
        });
    }

    public void disconnect() {
        executionSender.execute(socket::close);
    }

    //================
    // EXECUTION API
    //================

    public boolean requestStartExecution() {
        return event(() -> socket.send(wrap("startExecution", "")));
    }

    public void requestContinueExecution() {
        event(() -> socket.send(wrap("continueExecution", "")));
    }

    public void requestStopExecution() {
        event(() -> socket.send(wrap("stopExecution", "")));
    }

    public boolean requestSetBreakpoint(String address) {
        return event(() -> socket.send(wrap("setBreakpoint", address)));
    }

    public void requestResumeSelected(CharSequence ident) {
        event(() -> socket.send(wrap("resumeSelected", ident)));
    }

    public void requestResumeAll() {
        event(() -> socket.send(wrap("resumeAll", "")));
    }

    public void requestStepAll() {
        event(() -> socket.send(wrap("stepAll", "")));
    }

    public void requestStepSelected(CharSequence ident) { event(() -> socket.send(wrap("stepSelected", ident))); }

    public void requestAbortSelected(CharSequence ident) { event(() -> socket.send(wrap("abortSelected", ident))); }

    public FlowMapDTO requestFlowMap(CharSequence ident) {
        Future<Deque<FlowMapDTO>> response = querySender.submit(() -> {
            try {
                socket.send(wrap("requestFlowMap", ident));
                return socket.getQueryQueue().take();
            } catch (Exception any) {
                throw new RuntimeException("Cannot request flow map.");
            }
        });
        try {
            return response.get().pop();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Error during getting response from query.");
        }
    }

    private boolean event(Runnable run) {
        if (socket.isOpen()) {
            executionSender.execute(run);
            return true;
        }
        return false;
    }

    private String wrap(String request, Object content) {
        try {
            return m.writeValueAsString(java.util.Map.of("request", request, "content", content));
        } catch (JsonProcessingException e) {
            return "Error: " + e.getMessage();
        }
    }
}
