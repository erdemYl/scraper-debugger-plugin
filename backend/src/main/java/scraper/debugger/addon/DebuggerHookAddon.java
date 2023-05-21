package scraper.debugger.addon;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scraper.annotations.ArgsCommand;
import scraper.annotations.NotNull;
import scraper.api.*;
import scraper.debugger.core.*;
import scraper.debugger.dto.ControlFlowGraphDTO;
import scraper.debugger.dto.InstanceDTO;
import scraper.plugins.core.flowgraph.FlowUtil;
import scraper.plugins.core.flowgraph.api.ControlFlowGraph;
import scraper.utils.StringUtil;

import java.util.*;
import java.util.logging.*;
import java.util.logging.Formatter;

import static java.util.logging.Logger.getLogger;

@ArgsCommand(
        value = "debug",
        doc = "Starts a debugging websocket server and waits for a debugger to be present for processing the flow. If no port is specified with debug-port, then 8890 is used.",
        example = "scraper app.scrape debug"
)
@ArgsCommand(
        value = "debug-ip",
        doc = "Binding ip for debugging. Default is 0.0.0.0",
        example = "scraper app.scrape debug debug-ip:0.0.0.0"
)
@ArgsCommand(
        value = "debug-port",
        doc = "Port for debugging. Default is 8890",
        example = "scraper app.scrape debug debug-port:8890"
)
public class DebuggerHookAddon implements Addon, Hook {
    private final Logger l = LoggerFactory.getLogger("Debugger");
    public static String bindingIp;
    public static int port;
    public static DebuggerActions ACTIONS;
    public static InstanceDTO jobInstance;
    public static ControlFlowGraphDTO jobCFG;

    private static final Map<NodeAddress, DebuggerNodeType> debuggerNodeTypes = new HashMap<>();
    public static DebuggerNodeType getNodeType(NodeAddress address) { return debuggerNodeTypes.get(address); }

    static {
        System.setProperty("org.slf4j.simpleLogger.showThreadName", "false");
        System.setProperty("org.slf4j.simpleLogger.showDateTime", "true");
        System.setProperty("org.slf4j.simpleLogger.dateTimeFormat", "HH:mm:ss");
    }

    @Override
    public void load(@NotNull DIContainer dependencies, @NotNull String[] args) {
        if (StringUtil.getArgument(args, "debug") != null) {
            l.warn("Debugging activated");
            String debugPort = StringUtil.getArgument(args, "debug-port");
            String debugIp = StringUtil.getArgument(args, "debug-ip");
            bindingIp = "0.0.0.0";
            port = 8890;
            if (debugPort != null) port = Integer.parseInt(debugPort);
            if (debugIp != null) bindingIp = debugIp;

            dependencies.addComponent(DebuggerState.class);
            dependencies.addComponent(DebuggerServer.class);
            dependencies.addComponent(FlowPermissions.class);
            dependencies.addComponent(FlowIdentifier.class);
            dependencies.addComponent(DebuggerActions.class);
            dependencies.addComponent(FlowFilter.class);
            dependencies.addComponent(DebuggerNodeHook.class);
            ACTIONS = dependencies.get(DebuggerActions.class);
        }
    }

    @Override
    public void execute(@NotNull DIContainer dependencies, @NotNull String[] args, @NotNull Map<ScrapeSpecification, ScrapeInstance> scraper) {
        if (StringUtil.getArgument(args, "debug") != null) {
            if (scraper.size() != 1) {
                throw new RuntimeException("Debugger can handle only one job, found: " + scraper.size());
            }

            scraper.forEach((spec, ins) -> {
                ControlFlowGraph cfg = FlowUtil.generateControlFlowGraph(ins);

                //noinspection OptionalGetWithoutIsPresent
                Address entry = ins.getEntry().get().getAddress();  // scraper has an entry
                jobInstance = new InstanceDTO(ins);
                jobCFG = new ControlFlowGraphDTO(cfg, entry);

                Handler redirect = new Handler() {
                    final Formatter formatter = new SimpleFormatter();
                    final DebuggerServer SERVER = dependencies.get(DebuggerServer.class);
                    @Override
                    public void publish(LogRecord record) { SERVER.sendLogMessage(formatter.format(record)); }
                    @Override
                    public void flush() {}
                    @Override
                    public void close() throws SecurityException {}
                };

                ins.getRoutes().forEach((adr, n) -> {
                    debuggerNodeTypes.put(n.getAddress(), DebuggerNodeType.of(n));
                    java.util.logging.Logger nl = getLogger(adr.toString());
                    nl.setUseParentHandlers(false);
                    nl.addHandler(redirect);  // redirect node logs to frontend
                });

                ins.getHooks().add(dependencies.get(DebuggerNodeHook.class));
            });

            dependencies.get(DebuggerServer.class).start();
        }
    }

    @Override
    public int order() {
        // default hook order = 100
        // executed after default hooks
        return 200;
    }

    @Override
    public String toString() {
        return "Debugger";
    }
}