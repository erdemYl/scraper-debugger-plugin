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

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

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

    private static final Map<NodeAddress, NodeType> debuggerNodeTypes = new HashMap<>();

    public static NodeType getNodeType(NodeAddress address) {
        return debuggerNodeTypes.get(address);
    }

    public enum NodeType {
        FORK, STREAM_NODE, MAP, MAP_MAP, ON_WAY;

        public boolean isFlowEmitter() {
            return this != NodeType.ON_WAY;
        }

        public boolean isFork() {
            return this.equals(FORK);
        }

        private static NodeType of(NodeContainer<? extends Node> n) {
            Optional<?> t = n.getKeySpec("type");
            String nodeType = t.isEmpty()
                    ? (String) n.getKeySpec("f").get()
                    : (String) t.get();
            switch (nodeType) {
                case "Fork": {
                    return NodeType.FORK;
                }
                case "Map": {
                    return NodeType.MAP;
                }
                case "MapMap": {
                    return NodeType.MAP_MAP;
                }
                default: {
                    if (n instanceof StreamNodeContainer)
                        return NodeType.STREAM_NODE;
                    else
                        return NodeType.ON_WAY;
                }
            }
        }
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
            ACTIONS = dependencies.get(DebuggerActions.class);
        }
    }

    @Override
    public void execute(@NotNull DIContainer dependencies, @NotNull String[] args, @NotNull Map<ScrapeSpecification, ScrapeInstance> scraper) {
        if (StringUtil.getArgument(args, "debug") != null) {
            if (scraper.size() != 1) {
                throw new RuntimeException("Debugger can handle only one job!");
            }

            scraper.forEach((spec, ins) -> {
                ControlFlowGraph cfg = FlowUtil.generateControlFlowGraph(ins);
                if (ins.getEntry().isPresent()) {
                    Address adr = ins.getEntry().get().getAddress();
                    jobInstance = new InstanceDTO(ins);
                    jobCFG = new ControlFlowGraphDTO(cfg, adr);
                } else throw new RuntimeException("Debugger needs an entry node!");

                ins.getRoutes().values().forEach(n -> {
                    debuggerNodeTypes.put(n.getAddress(), NodeType.of(n));
                });

                ins.getHooks().add(
                        new DebuggerNodeHook(
                                dependencies.get(DebuggerServer.class),
                                dependencies.get(FlowIdentifier.class),
                                dependencies.get(FlowFilter.class)));
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