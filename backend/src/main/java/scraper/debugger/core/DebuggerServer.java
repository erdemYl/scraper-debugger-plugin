package scraper.debugger.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.java_websocket.WebSocket;
import org.java_websocket.drafts.Draft;
import org.java_websocket.exceptions.InvalidDataException;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.handshake.ServerHandshakeBuilder;
import org.java_websocket.server.WebSocketServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scraper.debugger.addon.DebuggerAddon;
import scraper.debugger.dto.ControlFlowGraphDTO;
import scraper.debugger.dto.FlowMapDTO;
import scraper.debugger.dto.InstanceDTO;
import scraper.debugger.dto.NodeDTO;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import java.lang.System.Logger.Level;

/**
 * Server component of the Debugger.
 */
public final class DebuggerServer {

    public final System.Logger l = System.getLogger("DebuggerServer");
    private final Logger l2 = LoggerFactory.getLogger("DebuggerServer");
    private WebSocketServer defaultServer = null;
    private WebSocket debugger = null;
    private final ObjectMapper m = new ObjectMapper();
    private final ReentrantLock sendMutex = new ReentrantLock(true);
    private final DebuggerState STATE;

    public DebuggerServer(DebuggerState STATE) {
        this.STATE = STATE;
    }


    @SuppressWarnings("unused")
    public String getIp() { return DebuggerAddon.bindingIp; }
    @SuppressWarnings("unused")
    public int getPort() { return DebuggerAddon.port; }

    /**
     * Creates a default websocket server instance.
     */
    public void create() {
        if (defaultServer != null) {
            l2.warn("A server has already created, cannot create another.");
            return;
        }

        InetSocketAddress adr = new InetSocketAddress(DebuggerAddon.bindingIp, DebuggerAddon.port);

        defaultServer = new WebSocketServer(adr)
        {
            final ReentrantLock lock = new ReentrantLock();

            // only a single debugger allowed to be connected at the same time
            @Override
            public ServerHandshakeBuilder onWebsocketHandshakeReceivedAsServer(WebSocket conn, Draft draft, ClientHandshake request) throws InvalidDataException {
                ServerHandshakeBuilder builder = super.onWebsocketHandshakeReceivedAsServer( conn, draft, request );
                try {
                    lock.lock();
                    if (debugger != null) throw new InvalidDataException(409, "A debugger has already connected");
                } finally {
                    lock.unlock();
                }
                return builder;
            }

            @Override
            public void onOpen(WebSocket conn, ClientHandshake clientHandshake) {
                try {
                    lock.lock();
                    debugger = conn;
                } finally {
                    lock.unlock();
                }
                l.log(Level.INFO, "Debugger connected");
            }

            @Override
            public void onClose(WebSocket conn, int i, String s, boolean b) {
                try {
                    lock.lock();
                    debugger = null;
                } finally {
                    lock.unlock();
                }
                l.log(Level.WARNING, "Debugger disconnected");
            }

            @Override
            public void onMessage(WebSocket conn, String s) {
                try {
                    lock.lock();
                    defaultOnMessage(s, lock);
                } finally {
                    if (lock.isHeldByCurrentThread()) lock.unlock();
                }
            }

            @Override
            public void onError(WebSocket conn, Exception e) {
                // NYI
            }

            @Override
            public void onStart() {
                // NYI
            }
        };

        defaultServer.setReuseAddr(true);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                l2.warn("Shutting down system");
                STATE.setContinue();
                stop();
                // why only slf4j logger prints this?
                l2.warn("Graceful shutdown");
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }));
    }


    /**
     * Creates server instance from given value.
     */
    @SuppressWarnings("unused")
    public void create(WebSocketServer server) throws InvalidDataException {
        if (defaultServer != null) {
            throw new InvalidDataException(409, "A server has already created");
        }

        defaultServer = server;

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                l2.warn("Shutting down system");
                STATE.setContinue();
                stop();
                l2.warn("Graceful shutdown");
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }));
    }


    /**
     * Starts created server.
     */
    public void start() {
        if (defaultServer != null) {
            defaultServer.start();
        } else {
            l2.warn("Server cannot start: create a server first.");
        }
    }


    public void stop() throws InterruptedException {
        if (defaultServer != null) {
            defaultServer.stop();
        }
    }


    /**
     * API convention: call this method in your websocket server when a debugger connects.
     * @throws InvalidDataException if a debugger has already connected.
     */
    @SuppressWarnings("unused")
    public void connected(WebSocket conn) throws InvalidDataException {
        if (debugger != null) throw new InvalidDataException(409, "A debugger has already connected");
        else debugger = conn;
    }


    // returns a socket, if a scraper.debugger.client is connected
    @SuppressWarnings("unused")
    public Optional<WebSocket> getFrontend() {
        return Optional.ofNullable(debugger);
    }



    //=============
    // SEND API
    //=============

    /**
     * Wraps with type "Specification".
     */
    public void sendSpecification(InstanceDTO spec, ControlFlowGraphDTO cfg) {
        if (debugger != null) {
            try {
                debugger.send(wrap("Specification",
                        Map.of("spec", m.writeValueAsString(spec),
                                "cfg", m.writeValueAsString(cfg))));
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }
        }
    }


    /**
     * Wraps with types "Initial" or "Update".
     */
    public void sendIdentifiedFlow(NodeDTO n, FlowMapDTO o, boolean initial) {
        try {
            // Why need a mutex again although this method always called with another lock?
            sendMutex.lock();
            if (debugger != null) {
                String t = initial ? "Initial" : "Update";
                try {
                    debugger.send(wrap(t, Map.of(
                            "node", m.writeValueAsString(n),
                            "flow", m.writeValueAsString(o))
                    ));
                } catch (JsonProcessingException e) {
                    e.printStackTrace();
                }
            }
        } finally {
            sendMutex.unlock();
        }
    }


    /**
     * Wraps with type "Breakpoint"
     */
    public void sendBreakpointHit(NodeDTO n, FlowMapDTO o) {
        try {
            sendMutex.lock();
            if (debugger != null) {
                try {
                    debugger.send(wrap("Breakpoint", Map.of(
                            "node", m.writeValueAsString(n),
                            "flow", m.writeValueAsString(o))
                    ));
                } catch (JsonProcessingException e) {
                    e.printStackTrace();
                }
            }
        } finally {
            sendMutex.unlock();
        }
    }


    /**
     * Wraps with type "EndNodeFlow".
     */
    public void sendFinishedFlow(NodeDTO n, FlowMapDTO o, boolean endNodeFlow) {
        try {
            sendMutex.lock();
            if (endNodeFlow) {
                if (debugger != null) {
                    try {
                        debugger.send(wrap("EndNodeFlow", Map.of(
                                "flow", m.writeValueAsString(o))
                        ));
                    } catch (JsonProcessingException e) {
                        e.printStackTrace();
                    }
                }
            }
            else {
                // NYI
            }
        } finally {
            sendMutex.unlock();
        }
    }


    /**
     * Wraps with type "Log".
     */
    public void sendLogMessage(String formattedMsg) {
        if (debugger != null) {
            debugger.send(wrap("Log", formattedMsg));
        }
    }


    /**
     * Default message handler for default server.
     * Assumes that messages come from default scraper.debugger.client.
     */
    private void defaultOnMessage(String msg, ReentrantLock toUnlock) {
        try {
            Map<String, String> request = m.readValue(msg, Map.class);
            switch (request.get("request")) {
                case "RequestSpec" -> {
                    DebuggerAddon.ACTIONS.requestSpecification();
                }
                case "Execute" -> {
                    DebuggerAddon.ACTIONS.setReady();
                }
                case "BeforeBP" -> {
                    DebuggerAddon.ACTIONS.setBreakpoint(request.get("content"), true);
                }
                case "AfterBP" -> {
                    DebuggerAddon.ACTIONS.setBreakpoint(request.get("content"), false);
                }
                case "Granted" -> {
                    // requested flow has permission to continue
                    DebuggerAddon.ACTIONS.permit(request.get("content"));
                }
                case "NotGranted" -> {
                    // requested flow prohibited its continue
                    DebuggerAddon.ACTIONS.notPermit(request.get("content"));
                }
                case "ContinueExecAll" -> {
                    DebuggerAddon.ACTIONS.permitAll();
                    DebuggerAddon.ACTIONS.continueExec();
                }
                case "ContinueExec" -> {
                    // program execution continues
                    DebuggerAddon.ACTIONS.continueExec();
                }
                case "StopExec" -> {
                    // program execution stops
                    toUnlock.unlock();
                    DebuggerAddon.ACTIONS.stopExec();
                }
                case "StepAll" -> {
                    DebuggerAddon.ACTIONS.stepAll();
                    DebuggerAddon.ACTIONS.continueExec();
                }
                case "StepFlow" -> {
                    DebuggerAddon.ACTIONS.stepSelected(request.get("content"));
                }
                default -> l.log(Level.WARNING, "Unexpected Request: {0}", request.get("request"));
            }

        } catch (JsonProcessingException e) {
            l.log(Level.WARNING, "Unable to read a message from frontend. (Bad message format)");
        }
    }


    /**
     * Data to string wrapper. The wrapping format is a map with
     * keys "type" and "data". Type is a string which denotes the
     * kind of data, where data is, in our case, a DTO object.
     */
    private String wrap(String type, Object data) {
        try {
            return m.writeValueAsString(Map.of("type", type, "data", data));
        } catch (JsonProcessingException e) {
            return "Error: " + e.getMessage();
        }
    }

    @Override
    public String toString() {
        return "DebuggerServer";
    }
}
