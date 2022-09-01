package scraper.debugger.addon;

import scraper.annotations.NotNull;
import scraper.api.DIContainer;
import scraper.api.Hook;
import scraper.api.ScrapeInstance;
import scraper.api.ScrapeSpecification;
import scraper.debugger.core.DebuggerState;
import scraper.utils.StringUtil;

import java.util.Map;

public class WaitHook implements Hook {

    @Override
    public void execute(@NotNull DIContainer dependencies, @NotNull String[] args, @NotNull Map<ScrapeSpecification, ScrapeInstance> scraper)  {
        if (StringUtil.getArgument(args, "debug") != null) {
            dependencies.get(DebuggerState.class).waitUntilStart();
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
