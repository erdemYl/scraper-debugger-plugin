package scraper.debugger.addon;

import scraper.annotations.ArgsCommand;
import scraper.annotations.NotNull;
import scraper.api.*;
import scraper.debugger.core.DebuggerServer;
import scraper.debugger.core.FlowFilter;
import scraper.debugger.core.FlowIdentifier;
import scraper.debugger.dto.ControlFlowGraphDTO;
import scraper.debugger.dto.InstanceDTO;
import scraper.plugins.core.flowgraph.FlowUtil;
import scraper.plugins.core.flowgraph.api.ControlFlowGraph;
import scraper.utils.StringUtil;

import java.util.*;


@ArgsCommand(
        value = "debug",
        doc = "Starts a debugging websocket server and creates a debugging node hook.",
        example = "scraper app.scrape debug"
)
public class DebuggerHook implements Hook {

    // Logger with actually intended name
    public final System.Logger l = System.getLogger("Debugger");
    private final System.Logger.Level info = System.Logger.Level.INFO;

    private static InstanceDTO jobInstance;
    private static ControlFlowGraphDTO jobCFG;
    private static final Map<NodeAddress, NodeType> debuggerNodeTypes = new HashMap<>();

    @Override
    public void execute(@NotNull DIContainer dependencies, @NotNull String[] args, @NotNull Map<ScrapeSpecification, ScrapeInstance> scraper) {
        boolean debugArg = StringUtil.getArgument(args, "debug") != null;
        boolean backendArg = StringUtil.getArgument(args, "debugger-backend") != null;

        if (debugArg || backendArg) {
            if (scraper.size() != 1) {
                throw new RuntimeException("Debugger can handle only one job!");
            } else {
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
                });

                if (debugArg) {
                    DebuggerServer SERVER = dependencies.get(DebuggerServer.class);

                    DebuggerNodeHook NODE_HOOK = new DebuggerNodeHook(
                            SERVER,
                            dependencies.get(FlowIdentifier.class),
                            dependencies.get(FlowFilter.class)
                    );
                    scraper.values().forEach(i -> i.getHooks().add(NODE_HOOK));

                    SERVER.start();
                    return;
                }

                l.log(info, "Debugger hook executed");
            }
        }
    }

    public static InstanceDTO getJobInstance() { return jobInstance; }

    public static ControlFlowGraphDTO getJobCFG() { return jobCFG; }

    public static NodeType getNodeType(NodeAddress address) {
        return debuggerNodeTypes.get(address);
    }


    public enum NodeType {
        FORK, INT_RANGE, MAP, ON_WAY;

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
                case "IntRange": {
                    return NodeType.INT_RANGE;
                }
                default: {
                    return NodeType.ON_WAY;
                }
            }
        }
    }


    @Override
    public int order() {
        // Default hook order is 100.
        // Since hooks are sorted before execution, this hook
        // will be executed after default hooks.
        return 200;
    }

    @Override
    public String toString() {
        return "DebuggerHook";
    }
}
