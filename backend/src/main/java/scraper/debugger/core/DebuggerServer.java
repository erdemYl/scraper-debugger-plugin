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

import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.lang.System.Logger.Level;

/**
 * Server component of the Debugger.
 */
public final class DebuggerServer extends WebSocketServer {

    public final System.Logger l = System.getLogger("DebuggerServer");
    private final Logger l2 = LoggerFactory.getLogger("DebuggerServer");
    private final ReentrantLock lock = new ReentrantLock();
    private final ReentrantLock sendMutex = new ReentrantLock(true);
    private final ObjectMapper m = new ObjectMapper();
    private WebSocket debugger = null;

    public DebuggerServer(DebuggerState STATE) {
        super(new InetSocketAddress(DebuggerAddon.bindingIp, DebuggerAddon.port));
        setReuseAddr(true);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Thread.currentThread().setName("DebuggerShutDown");
            try {
                l2.warn("Shutting down system");
                STATE.setContinue();
                stop();
                // Why only slf4j logger prints this?
                l2.warn("Graceful shutdown");
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }));
    }


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
    public void onMessage(WebSocket conn, String msg) {
        try {
            lock.lock();
            Map<String, String> request = m.readValue(msg, Map.class);
            String cmd = request.get("request");
            switch (cmd) {
                case "stepSelected", "resumeSelected", "stopSelected", "setBreakpoint" -> {
                    Method m = DebuggerActions.class.getMethod(cmd, String.class);
                    m.invoke(DebuggerAddon.ACTIONS, request.get("content"));
                }
                default -> {
                    Method m = DebuggerActions.class.getDeclaredMethod(cmd);
                    m.invoke(DebuggerAddon.ACTIONS);
                }
            }
        } catch (Exception e) {
            l.log(Level.WARNING, "Invalid message detected from front-end.");
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void onError(WebSocket conn, Exception e) {
    }

    @Override
    public void onStart() {
    }



    //=============
    // SEND API
    //=============

    /**
     * Wraps with type "specification".
     */
    public void sendSpecification(InstanceDTO spec, ControlFlowGraphDTO cfg) {
        if (debugger != null) {
            try {
                debugger.send(wrap("specification",
                        Map.of("spec", m.writeValueAsString(spec),
                                "cfg", m.writeValueAsString(cfg))));
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }
        }
    }


    /**
     * Wraps with types "initialFlow" or "flow".
     */
    public void sendIdentifiedFlow(NodeDTO n, FlowMapDTO o, boolean initial) {
        try {
            // Why need a mutex again although this method always called with another lock?
            sendMutex.lock();
            if (debugger != null) {
                String t = initial ? "initialFlow" : "flow";
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
     * Wraps with type "breakpointHit"
     */
    public void sendBreakpointHit(NodeDTO n, FlowMapDTO o) {
        try {
            sendMutex.lock();
            if (debugger != null) {
                try {
                    debugger.send(wrap("breakpointHit", Map.of(
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
     * Wraps with type "finishedFlow".
     */
    public void sendFinishedFlow(NodeDTO n, FlowMapDTO o, boolean endNodeFlow) {
        try {
            sendMutex.lock();
            if (endNodeFlow) {
                if (debugger != null) {
                    try {
                        debugger.send(wrap("finishedFlow", Map.of(
                                "flow", m.writeValueAsString(o))
                        ));
                    } catch (JsonProcessingException e) {
                        e.printStackTrace();
                    }
                }
            }
        } finally {
            sendMutex.unlock();
        }
    }


    /**
     * Wraps with type "log".
     */
    public void sendLogMessage(String formattedMsg) {
        if (debugger != null) {
            debugger.send(wrap("log", formattedMsg));
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
