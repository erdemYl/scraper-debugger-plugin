package scraper.debugger.addon;

import scraper.annotations.ArgsCommand;
import scraper.annotations.NotNull;
import scraper.api.DIContainer;
import scraper.api.Hook;
import scraper.api.ScrapeInstance;
import scraper.api.ScrapeSpecification;
import scraper.debugger.core.DebuggerState;
import scraper.utils.StringUtil;

import java.util.Map;


@ArgsCommand(
        value = "debug",
        doc = "Waits for a start signal from debugger to execute workflows.",
        example = "scraper app.scrape debug"
)
public class WaitHook implements Hook {

    @Override
    public void execute(@NotNull DIContainer dependencies, @NotNull String[] args, @NotNull Map<ScrapeSpecification, ScrapeInstance> scraper)  {
        boolean debugArg = StringUtil.getArgument(args, "debug") != null;
        boolean backendArg = StringUtil.getArgument(args, "debugger-backend") != null;
        if (debugArg || backendArg) {
            DebuggerState STATE = dependencies.get(DebuggerState.class);
            STATE.waitUntilStart();
        }
    }

    @Override
    public int order() {
        return Integer.MAX_VALUE;
    }

    @Override
    public String toString() {
        return "DebuggerWaitHook";
    }
}
