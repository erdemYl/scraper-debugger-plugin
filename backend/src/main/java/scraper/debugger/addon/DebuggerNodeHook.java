package scraper.debugger.addon;

import scraper.api.FlowMap;
import scraper.api.Node;
import scraper.api.NodeContainer;
import scraper.api.NodeHook;
import scraper.debugger.core.DebuggerServer;
import scraper.debugger.core.FlowFilter;
import scraper.debugger.core.FlowIdentifier;
import scraper.debugger.dto.FlowDTO;

import java.util.Set;
import java.util.UUID;
import java.util.logging.*;

public class DebuggerNodeHook implements NodeHook {

    private final FlowIdentifier FI;
    private final FlowFilter FF;

    private final Handler redirect;

    DebuggerNodeHook(DebuggerServer SERVER, FlowIdentifier FI, FlowFilter FF) {
        this.FI = FI;
        this.FF = FF;

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
        FI.identify(n, o);
        redirectLogToFrontend(n);
        FF.filter(n, o);
    }

    @Override
    public void afterProcess(NodeContainer<? extends Node> n, FlowMap o) {
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

    @Override
    public String toString() {
        return "DebuggerNodeHook";
    }
}
