package scraper.debugger.addon;

import scraper.annotations.NotNull;
import scraper.api.*;
import scraper.debugger.core.DebuggerNodeHook;
import scraper.debugger.core.DebuggerServer;
import scraper.debugger.dto.ControlFlowGraphDTO;
import scraper.debugger.dto.InstanceDTO;
import scraper.plugins.core.flowgraph.FlowUtil;
import scraper.plugins.core.flowgraph.api.ControlFlowGraph;
import scraper.utils.StringUtil;

import java.util.*;
import java.util.Map.Entry;


/**
 * Provided to scraper framework.
 * Executed after default hooks like type checker hook.
 * Can handle only one scrape job.
 */
public final class DebuggerHook implements Hook {

    // Logger with actually intended name
    public final System.Logger l = System.getLogger("Debugger");
    private final System.Logger.Level info = System.Logger.Level.INFO;

    public static Entry<InstanceDTO, ControlFlowGraphDTO> spec;
    public static Set<String> endNodes;

    @Override
    public void execute(@NotNull DIContainer dependencies, @NotNull String[] args, @NotNull Map<ScrapeSpecification, ScrapeInstance> scraper) {
        boolean debug = StringUtil.getArgument(args, "debug") != null;
        boolean addDebugger = StringUtil.getArgument(args, "add-debugger") != null;

        if (debug || addDebugger) {
            if (scraper.size() != 1) {
                throw new RuntimeException("Debugger can handle only one job!");
            } else {
                scraper.forEach((s, i) -> {
                    ControlFlowGraph cfg = FlowUtil.generateControlFlowGraph(i);
                    if (i.getEntry().isPresent()) {
                        Address adr = i.getEntry().get().getAddress();
                        spec = new AbstractMap.SimpleImmutableEntry<>(
                                new InstanceDTO(i),
                                new ControlFlowGraphDTO(cfg, adr)
                        );
                    } else throw new RuntimeException("Debugger needs an entry node!");
                });

                endNodes = spec.getValue().getEndNodes();

                if (debug) {
                    DebuggerNodeHook NODE_HOOK = dependencies.get(DebuggerNodeHook.class);
                    DebuggerServer SERVER = dependencies.get(DebuggerServer.class);

                    scraper.values().forEach(i -> i.getHooks().add(NODE_HOOK));

                    SERVER.create();
                    SERVER.start();
                    return;
                }

                l.log(info, "Debugger hook executed");
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
        return "Debugger";
    }
}
