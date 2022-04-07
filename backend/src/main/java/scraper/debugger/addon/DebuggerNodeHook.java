package scraper.debugger.addon;

import scraper.api.FlowMap;
import scraper.api.Node;
import scraper.api.NodeContainer;
import scraper.api.NodeHook;
import scraper.debugger.core.DebuggerServer;
import scraper.debugger.core.FlowFilter;
import scraper.debugger.core.FlowIdentifier;
import scraper.debugger.dto.FlowMapDTO;
import scraper.debugger.dto.NodeDTO;

import java.util.Map;
import java.util.Set;
import java.util.logging.*;

public class DebuggerNodeHook implements NodeHook {

    // Debugger components
    private final DebuggerServer SERVER;
    private final FlowIdentifier FI;
    private final FlowFilter FF;

    private final Set<String> endNodes;
    private final Handler redirect;

    DebuggerNodeHook(DebuggerServer SERVER, FlowIdentifier FI, FlowFilter FF, Set<String> endNodes) {
        this.SERVER = SERVER;
        this.FI = FI;
        this.FF = FF;
        this.endNodes = endNodes;

        redirect = new Handler() {
            final Formatter formatter = new SimpleFormatter();

            @Override
            public void publish(LogRecord record) {
                SERVER.sendLogMessage(formatter.format(record));
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() throws SecurityException {
            }
        };
    }

    @Override
    public void beforeProcess(NodeContainer<? extends Node> n, FlowMap o) {
        FI.identify(n, o, true);
        redirectLogToFrontend(n);
        FF.filter(n, o);
    }

    @Override
    public void afterProcess(NodeContainer<? extends Node> n, FlowMap o) {
        if (isEndNode(n)) {
            Map.Entry<NodeDTO, FlowMapDTO> dto = FF.getDTOs(n, o);
            SERVER.sendFinishedFlow(dto.getKey(), dto.getValue(), true);
        }
        FI.releaseBranchLock(o.getId());
    }

    @Override
    public int order() {
        return Integer.MIN_VALUE;
    }

    private void redirectLogToFrontend(NodeContainer<? extends Node> n) {
        Logger l = Logger.getLogger(n.getAddress().toString());
        if (l.getUseParentHandlers()) {
            l.setUseParentHandlers(false);
            l.addHandler(redirect);
        }
    }

    private boolean isEndNode(NodeContainer<? extends Node> n) {
        return endNodes.contains(n.getAddress().toString());
    }

    @Override
    public String toString() {
        return "DebuggerNodeHook";
    }
}
