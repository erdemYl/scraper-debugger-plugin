package scraper.debugger.client;

import scraper.debugger.mvcvm.DebuggerApp;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import scraper.debugger.dto.ControlFlowGraphDTO;
import scraper.debugger.dto.FlowMapDTO;
import scraper.debugger.dto.InstanceDTO;
import scraper.debugger.dto.NodeDTO;

import java.io.IOException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

public class DebuggerWebsocket extends WebSocketClient {

    final DebuggerApp debugger;
    final ObjectMapper m = new ObjectMapper();
    final ReentrantLock lock = new ReentrantLock(true);

    public DebuggerWebsocket(URI serverURI, DebuggerApp debugger) {
        super(serverURI);
        this.debugger = debugger;
    }

    @Override
    public void onOpen(ServerHandshake serverHandshake) {
        send(wrap("RequestSpec", ""));
    }

    @Override
    public void onMessage(String s) {
        lock.lock();
        try {
            Map<String, Object> data = m.readValue(s, Map.class);
            String type = (String) data.get("type");
            switch (type) {
                case "Specification" -> {
                    Map<String, String> dto = (Map<String, String>) data.get("data");
                    InstanceDTO i = m.readValue(dto.get("spec"), InstanceDTO.class);
                    ControlFlowGraphDTO cfg = m.readValue(dto.get("cfg"), ControlFlowGraphDTO.class);
                    debugger.takeSpecification(i, cfg);
                }
                case "Initial" -> {
                    Map<String, String> dto = (Map<String, String>) data.get("data");
                    debugger.takeInitialUpdate(m.readValue(dto.get("node"), NodeDTO.class), m.readValue(dto.get("flow"), FlowMapDTO.class));
                }
                case "Update" -> {
                    Map<String, String> dto = (Map<String, String>) data.get("data");
                    debugger.takeUpdate(m.readValue(dto.get("node"), NodeDTO.class), m.readValue(dto.get("flow"), FlowMapDTO.class));
                }
                case "Instant" -> {
                    Map<String, String> dto = (Map<String, String>) data.get("data");
                    LinkedHashMap<NodeDTO, FlowMapDTO> finalData = new LinkedHashMap<>();
                    for (int i = 0; i < dto.size(); i++) {
                        try {
                            finalData.put(m.readValue(dto.get("node" + i), NodeDTO.class), m.readValue(dto.get("flow" + i), FlowMapDTO.class));
                        } catch (Exception e) {
                            break;
                        }
                    }
                    debugger.takeInstantUpdate(finalData);
                }
                case "Breakpoint" -> {
                    Map<String, String> dto = (Map<String, String>) data.get("data");
                    debugger.takeBreakpointUpdate(m.readValue(dto.get("flow"), FlowMapDTO.class));
                }
                case "EndNodeFlow" -> {
                    Map<String, String> dto = (Map<String, String>) data.get("data");
                    debugger.takeProcessedEndNodeFlow(m.readValue(dto.get("flow"), FlowMapDTO.class));
                }
                case "Log" -> {
                    String log = (String) data.get("data");
                    debugger.takeLogMessage(log);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void onClose(int i, String s, boolean b) {

    }

    @Override
    public void onError(Exception e) {

    }

    public void sendExecute() {
        send(wrap("Execute", ""));
    }

    public void sendBreakpoint(String addrRepresentation, boolean b) {
        send(wrap(b ? "BeforeBP" : "AfterBP", addrRepresentation));
    }

    public void sendPermission(String flow, boolean permission) {
        send(wrap(permission ? "Granted" : "NotGranted", flow));
    }

    public void sendContinueExec() {
        send(wrap("ContinueExec", ""));
    }

    public void sendContinueExecAll() {
        send(wrap("ContinueExecAll", ""));
    }

    public void sendStepAll() {
        send(wrap("StepAll", "Stop"));
    }

    public void sendStepFlow(String flowID) {
        send(wrap("StepFlow", flowID));
    }

    public void sendStopAll() {
        send(wrap("StopExec", ""));
    }

    private String wrap(String request, Object content) {
        try {
            return m.writeValueAsString(java.util.Map.of("request", request, "content", content));
        } catch (JsonProcessingException e) {
            return "Error: " + e.getMessage();
        }
    }
}
