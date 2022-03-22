package scraper.debugger.hook;

import javafx.application.Platform;
import javafx.stage.Stage;
import scraper.annotations.ArgsCommand;
import scraper.annotations.NotNull;
import scraper.api.DIContainer;
import scraper.api.Hook;
import scraper.api.ScrapeInstance;
import scraper.api.ScrapeSpecification;
import scraper.debugger.mvcvm.DebuggerApp;
import scraper.debugger.core.DebuggerServer;
import scraper.utils.StringUtil;

import java.util.Map;


@ArgsCommand(
        value = "debug",
        doc = "If debugger backend added, starts the frontend part of the debugger.",
        example = "scraper app.scrape debug"
)
public final class FrontendHook implements Hook {

    @Override
    public void execute(@NotNull DIContainer dependencies, @NotNull String[] args, @NotNull Map<ScrapeSpecification, ScrapeInstance> scraper) {
        if (StringUtil.getArgument(args, "debug") != null) {
            DebuggerServer SERVER = dependencies.get(DebuggerServer.class);
            if (SERVER == null) throw new RuntimeException("No backend for debugger!");
            Platform.startup(() -> {
                new DebuggerApp(SERVER.getPort()).start(new Stage());
            });
        }
    }

    @Override
    public int order() {
        // executed after debugger hook
        return 205;
    }
}
