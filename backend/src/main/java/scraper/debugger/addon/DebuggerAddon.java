package scraper.debugger.addon;

import scraper.annotations.ArgsCommand;
import scraper.annotations.NotNull;
import scraper.api.Addon;
import scraper.api.DIContainer;
import scraper.debugger.core.*;
import scraper.utils.StringUtil;

@ArgsCommand(
        value = "debug",
        doc = "Starts a debugging websocket server and waits for a debugger to be present for processing the flow. If no port is specified with debug-port, then 8890 is used.",
        example = "scraper app.scrape debug"
)
@ArgsCommand(
        value = "add-debugger",
        doc = "Only adds debugger components to dependencies. Server won't start and flows won't wait.",
        example = "scraper app.scrape add-debugger"
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
public final class DebuggerAddon implements Addon {

    private final System.Logger l = System.getLogger("Debugger");
    private final System.Logger.Level warn = System.Logger.Level.WARNING;

    public static String bindingIp;
    public static int port;
    public static DebuggerActions ACTIONS;

    @Override
    public void load(@NotNull DIContainer dependencies, @NotNull String[] args) {
        boolean debug = StringUtil.getArgument(args, "debug") != null;
        boolean addDebugger = StringUtil.getArgument(args, "add-debugger") != null;

        if (debug || addDebugger) {
            l.log(warn, "Debugging activated");
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
            dependencies.addComponent(DebuggerNodeHook.class);

            ACTIONS = dependencies.get(DebuggerActions.class);
        }
    }

    @Override
    public String toString() {
        return "Debugger";
    }
}
