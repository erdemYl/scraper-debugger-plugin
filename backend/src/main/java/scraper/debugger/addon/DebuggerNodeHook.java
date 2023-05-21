package scraper.debugger.addon;

import scraper.annotations.NotNull;
import scraper.api.*;
import scraper.debugger.core.DebuggerServer;
import scraper.debugger.core.FlowFilter;
import scraper.debugger.core.FlowIdentifier;


public class DebuggerNodeHook implements NodeHook {

    // Debugger components
    private final DebuggerServer SERVER;
    private final FlowIdentifier FI;
    private final FlowFilter FF;

    public DebuggerNodeHook(DebuggerServer SERVER, FlowIdentifier FI, FlowFilter FF) {
        this.SERVER = SERVER;
        this.FI = FI;
        this.FF = FF;
    }

    @Override
    public void beforeProcess(@NotNull NodeContainer<? extends Node> n, @NotNull FlowMap o) {
        FI.identify(n, o);
        FF.filter(n, o);
    }

    @Override
    public void afterProcess(@NotNull NodeContainer<? extends Node> n, @NotNull FlowMap o) {
        SERVER.sendFinishedFlow(FI.getFlowDTO(o.getId()));
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
