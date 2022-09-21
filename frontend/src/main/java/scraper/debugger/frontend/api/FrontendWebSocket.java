package scraper.debugger.frontend.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scraper.debugger.dto.*;

import java.net.URI;
import java.util.*;
import java.util.concurrent.*;

public abstract class FrontendWebSocket extends WebSocketClient {

    protected final Logger l = LoggerFactory.getLogger("DebuggerClient");

    // Query response queue
    private final BlockingQueue<Deque<FlowMapDTO>> queryQueue = new SynchronousQueue<>(true);

    // Query response bringing thread
    private final ExecutorService queryBringer = Executors.newSingleThreadExecutor();

    private final ObjectMapper m = new ObjectMapper();
    private boolean firstConnection = true;

    public FrontendWebSocket(String bindingIp, int port) {
        super(URI.create("ws://" + bindingIp + ":" + port));
        setReuseAddr(true);
    }

    @Override
    public void onOpen(ServerHandshake serverHandshake) {
        Thread.currentThread().setName("channel-f");
    }

    @Override
    public void onMessage(String msg) {
        try {
            Map<String, Object> data = m.readValue(msg, Map.class);
            String type = (String) data.get("type");
            switch (type) {
                case "specification": {
                    if (firstConnection) {
                        Map<String, String> dto = (Map<String, String>) data.get("data");
                        InstanceDTO ins = m.readValue(dto.get("instance"), InstanceDTO.class);
                        ControlFlowGraphDTO cfg = m.readValue(dto.get("cfg"), ControlFlowGraphDTO.class);
                        takeSpecification(ins, cfg);
                        firstConnection = false;
                    }
                    return;
                }
                case "identifiedFlow": {
                    Map<String, String> dto = (Map<String, String>) data.get("data");
                    FlowDTO f = m.readValue(dto.get("flow"), FlowDTO.class);
                    takeIdentifiedFlow(f);
                    return;
                }
                case "breakpointHit": {
                    Map<String, String> dto = (Map<String, String>) data.get("data");
                    FlowDTO f = m.readValue(dto.get("flow"), FlowDTO.class);
                    takeBreakpointHit(f);
                    return;
                }
                case "finishedFlow": {
                    Map<String, String> dto = (Map<String, String>) data.get("data");
                    FlowDTO f = m.readValue(dto.get("flow"), FlowDTO.class);
                    takeFinishedFlow(f);
                    return;
                }
                case "log": {
                    String log = (String) data.get("data");
                    takeLogMessage(log);
                    return;
                }
                case "finish": {
                    takeFinishSignal();
                    return;
                }
                case "flowLifecycle": {
                    queryBringer.execute(() -> {
                        try {
                            List<String> dto = (List<String>) data.get("data");
                            Deque<FlowMapDTO> converted = new LinkedList<>();
                            for (String str : dto) {
                                converted.add(m.readValue(str, FlowMapDTO.class));
                            }
                            queryQueue.put(converted);
                        } catch (JsonProcessingException | InterruptedException e) {
                            l.warn("Query error");
                            e.printStackTrace();
                            queryQueue.add(new LinkedList<>());
                        }
                    });
                }
            }

        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onClose(int i, String s, boolean b) {
    }

    @Override
    public void onError(Exception e) {
        l.error("Connection error: {}", e.getMessage());
    }


    //=================
    // Update Taking
    //=================

    protected abstract void takeSpecification(InstanceDTO jobIns, ControlFlowGraphDTO jobCFG);

    protected abstract void takeIdentifiedFlow(FlowDTO f);

    protected abstract void takeBreakpointHit(FlowDTO f);

    protected abstract void takeFinishedFlow(FlowDTO f);

    protected abstract void takeLogMessage(String log);

    protected abstract void takeFinishSignal();

    BlockingQueue<Deque<FlowMapDTO>> getQueryQueue() {
        return queryQueue;
    }
}
