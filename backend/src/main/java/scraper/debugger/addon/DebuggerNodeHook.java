package scraper.debugger.addon;

import scraper.annotations.NotNull;
import scraper.api.*;
import scraper.debugger.core.DebuggerServer;
import scraper.debugger.core.FlowFilter;
import scraper.debugger.core.FlowIdentifier;

import java.util.Set;
import java.util.logging.*;

import static scraper.debugger.addon.DebuggerHookAddon.activeFlows;

public class DebuggerNodeHook implements NodeHook {

    // Debugger components
    private final DebuggerServer SERVER;
    private final FlowIdentifier FI;
    private final FlowFilter FF;

    DebuggerNodeHook(Set<Address> addresses, DebuggerServer SERVER, FlowIdentifier FI, FlowFilter FF) {
        this.SERVER = SERVER;
        this.FI = FI;
        this.FF = FF;

        Handler redirect = new Handler() {
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

        // redirect log to frontend
        addresses.forEach(adr -> {
            Logger nl = Logger.getLogger(adr.toString());
            nl.setUseParentHandlers(false);
            nl.addHandler(redirect);
        });
    }

    @Override
    public void beforeProcess(@NotNull NodeContainer<? extends Node> n, @NotNull FlowMap o) {
        activeFlows.add(o.getId());
        FI.identify(n, o);
        FF.filter(n, o);
    }

    @Override
    public void afterProcess(@NotNull NodeContainer<? extends Node> n, @NotNull FlowMap o) {
        SERVER.sendFinishedFlow(FI.getFlowDTO(o.getId()));
        o.getParentId().ifPresent(activeFlows::remove);
        if (n.getGoTo().isEmpty()) activeFlows.remove(o.getId());
    }

    @Override
    public int order() {
        return Integer.MIN_VALUE;
    }

    @Override
    public String toString() {
        return "DebuggerNodeHook";
    }
}
