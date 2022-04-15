package scraper.debugger.frontend.hook;

import javafx.application.Platform;
import javafx.stage.Stage;
import scraper.annotations.ArgsCommand;
import scraper.annotations.NotNull;
import scraper.api.DIContainer;
import scraper.api.Hook;
import scraper.api.ScrapeInstance;
import scraper.api.ScrapeSpecification;
import scraper.debugger.frontend.app.FrontendApp;
import scraper.utils.StringUtil;

import java.util.Map;


@ArgsCommand(
        value = "debug",
        doc = "Starts debugger frontend application if debug flag given.",
        example = "scraper app.scrape debug"
)
public final class FrontendHook implements Hook {

    @Override
    public void execute(@NotNull DIContainer dependencies, @NotNull String[] args, @NotNull Map<ScrapeSpecification, ScrapeInstance> scraper) {
        if (StringUtil.getArgument(args, "debug") != null) {
            String debugPort = StringUtil.getArgument(args, "debug-port");
            String debugIp = StringUtil.getArgument(args, "debug-ip");
            Platform.startup(() -> {
                new FrontendApp(
                        debugIp == null ? "0.0.0.0" : debugIp,
                        debugPort == null ? 8890 : Integer.parseInt(debugPort)
                ).start(new Stage());
            });
        }
    }

    @Override
    public int order() {
        // executed after debugger hook
        return 205;
    }
}
