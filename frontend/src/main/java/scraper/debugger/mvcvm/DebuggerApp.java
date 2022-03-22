package scraper.debugger.mvcvm;

import scraper.debugger.client.DebuggerWebsocket;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import scraper.debugger.dto.ControlFlowGraphDTO;
import scraper.debugger.dto.FlowMapDTO;
import scraper.debugger.dto.InstanceDTO;
import scraper.debugger.dto.NodeDTO;

import java.io.IOException;
import java.net.URI;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

public class DebuggerApp extends Application {

    private final DebuggerWebsocket socket;
    final ReentrantLock lock = new ReentrantLock();
    final AtomicBoolean started = new AtomicBoolean(false);

    private DebuggerController displayController;

    public DebuggerApp(int port) {
        socket = new DebuggerWebsocket(URI.create("ws://localhost:" + port), this);
    }

    @Override
    public void start(Stage stage) {
        try {
            FXMLLoader loader = new FXMLLoader(DebuggerApp.class.getResource("/Debugger.fxml"));
            Parent loaded = loader.load();
            displayController = loader.getController();
            displayController.setConnectionToDebugger(this);
            stage.setScene(new Scene(loaded));
            stage.initStyle(StageStyle.UNDECORATED);
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void takeSpecification(InstanceDTO ins, ControlFlowGraphDTO cfg) {
        displayController.generateBeforeExecTree(ins, cfg);
    }

    public void takeInstantUpdate(LinkedHashMap<NodeDTO, FlowMapDTO> dto) {
        // NYI
    }

    public void takeInitialUpdate(NodeDTO n, FlowMapDTO o) {
        displayController.addRootNode(n, o);
    }

    public void takeUpdate(NodeDTO n, FlowMapDTO o) {
        displayController.addRuntimeNode(n, o);
    }

    public void takeBreakpointUpdate(FlowMapDTO o) {
        displayController.breakpointUpdate(o);
    }

    public void takeProcessedEndNodeFlow(FlowMapDTO o) {
        displayController.endNodeFlowUpdate(o);
    }

    public void takeLogMessage(String str) {
        displayController.displayLogMessage(str);
    }


    //============
    // CLIENT REQUESTS
    //============

    private boolean alreadyConnected = false;
    public void openConnection() {
        if (!alreadyConnected) {
            alreadyConnected = true;
            socket.connect();
        }
    }

    public void startExecution() {
        try {
            lock.lock();
            if (socket.getConnection().isOpen()) {
                if (!started.get()) {
                    started.set(true);
                    socket.sendExecute();
                }
            }
        } finally {
            lock.unlock();
        }
    }

    public void sendBreakpoint(String address, boolean before) {
        socket.sendBreakpoint(address, before);
    }

    public void sendContinueExec() {
        socket.sendContinueExec();
    }

    public void sendContinueExecAll() {
        socket.sendContinueExecAll();
    }

    public void sendContinueFlow(String flowID) {
        socket.sendPermission(flowID, true);
    }

    public void sendStepAll() {
        socket.sendStepAll();
    }

    public void sendStepFlow(String flowID) {
        socket.sendStepFlow(flowID);
    }

    public void sendStopFlow(String flowID) {
        socket.sendPermission(flowID, false);
    }

    public void sendStopAllFlow() {
        socket.sendStopAll();
    }
}
