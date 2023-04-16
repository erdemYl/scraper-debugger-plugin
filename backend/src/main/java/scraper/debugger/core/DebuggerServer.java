package scraper.debugger.core;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.java_websocket.WebSocket;
import org.java_websocket.drafts.Draft;
import org.java_websocket.exceptions.InvalidDataException;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.handshake.ServerHandshakeBuilder;
import org.java_websocket.server.WebSocketServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scraper.debugger.addon.DebuggerHookAddon;
import scraper.debugger.dto.*;

import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Server component of the Debugger.
 */
public final class DebuggerServer extends WebSocketServer {

    private final Logger l = LoggerFactory.getLogger("DebuggerServer");
    private final ReentrantLock lock = new ReentrantLock();
    private final ObjectMapper m;
    private volatile WebSocket debugger = null;
    private final DebuggerState STATE;

    public DebuggerServer(DebuggerState STATE) {
        super(new InetSocketAddress(DebuggerHookAddon.bindingIp, DebuggerHookAddon.port));
        setReuseAddr(true);
        this.STATE = STATE;
        m = new ObjectMapper()
                .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
                .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);  // all data in flow-map can be serialized

        // clean shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                l.warn("Shutting down system");
                stop();
                STATE.setContinue();
                STATE.setConnected();
                l.warn("Graceful shutdown");
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
    public void onCloseInitiated(WebSocket conn, int code, String reason) {
        super.onCloseInitiated(conn, code, reason);
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake clientHandshake) {
        try {
            lock.lock();
            debugger = conn;
            STATE.setConnected();  // for the case of reconnection
            l.info("Debugger connected, sending workflow specification");
            sendSpecification(DebuggerHookAddon.jobInstance, DebuggerHookAddon.jobCFG);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void onClose(WebSocket conn, int i, String s, boolean b) {
        try {
            lock.lock();
            debugger = null;
            DebuggerHookAddon.ACTIONS.stopExecution();
            l.warn("Debugger disconnected");
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void onMessage(WebSocket conn, String msg) {
        try {
            lock.lock();
            Map<String, String> request = m.readValue(msg, Map.class);
            String cmd = request.get("request");
            String content = request.get("content");
            Method m;
            if (content.isEmpty()) {
                m = DebuggerActions.class.getDeclaredMethod(cmd);
                m.invoke(DebuggerHookAddon.ACTIONS);
            } else {
                m = DebuggerActions.class.getDeclaredMethod(cmd, CharSequence.class);
                m.invoke(DebuggerHookAddon.ACTIONS, content);
            }
        } catch (Exception e) {
            l.warn("Invalid message detected from front-end: {}", msg);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void onError(WebSocket conn, Exception e) {
        l.error("Connection error: {}", e.getMessage());
        DebuggerHookAddon.ACTIONS.stopExecution();
    }

    @Override
    public void onStart() {}


    //=============
    // SEND API
    //=============

    /**
     * Wraps with type "specification".
     */
    void sendSpecification(InstanceDTO instance, ControlFlowGraphDTO cfg) {
        if (debugger != null) {
            try {
                debugger.send(wrap("specification",
                        Map.of("instance", m.writeValueAsString(instance),
                                "cfg", m.writeValueAsString(cfg))));
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }
        }
    }


    /**
     * Wraps with type "identifiedFlow".
     */
    void sendIdentifiedFlow(FlowDTO f) {
        if (debugger == null) STATE.waitOnConnectionLoss();
        try {
            debugger.send(wrap("identifiedFlow", Map.of(
                    "flow", m.writeValueAsString(f))
            ));
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
    }


    /**
     * Wraps with type "breakpointHit"
     */
    void sendBreakpointHit(FlowDTO f) {
        if (debugger == null) STATE.waitOnConnectionLoss();
        try {
            debugger.send(wrap("breakpointHit", Map.of(
                    "flow", m.writeValueAsString(f))
            ));
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
    }


    /**
     * Wraps with type "finishedFlow".
     */
    public void sendFinishedFlow(FlowDTO f) {
        if (debugger == null) STATE.waitOnConnectionLoss();
        try {
            debugger.send(wrap("finishedFlow", Map.of(
                    "flow", m.writeValueAsString(f))
            ));
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
    }


    /**
     * Wraps with type "log".
     */
    public void sendLogMessage(String formattedMsg) {
        if (debugger == null) STATE.waitOnConnectionLoss();
        debugger.send(wrap("log", formattedMsg));
    }


    void sendFlowMap(FlowMapDTO o) {
        if (debugger != null) {
            try {
                debugger.send(wrap("flowMap", Map.of(
                        "map", m.writeValueAsString(o))
                ));
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }
        }
    }


    /**
     * Wraps with type "flowLifecycle"
     */
    void sendLifecycle(List<FlowMapDTO> flows) {
        if (debugger != null) {
            try {
                List<String> written = new LinkedList<>();
                for (FlowMapDTO o : flows) {
                    written.add(m.writeValueAsString(o));
                }
                debugger.send(wrap("flowLifecycle", written));
            } catch (JsonProcessingException e) {
                l.warn("Unable to send flow lifecycle");
            }
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
