package scraper.debugger.addon;

import scraper.annotations.ArgsCommand;
import scraper.annotations.NotNull;
import scraper.api.Addon;
import scraper.api.DIContainer;
import scraper.utils.StringUtil;

import java.util.logging.Handler;
import java.util.logging.Level;

@ArgsCommand(
        value = "log",
        doc = "Prints all kinds of logs to console during execution.",
        example = "scraper app.scrape log"
)
public class LogAddon implements Addon {

    @Override
    public void load(@NotNull DIContainer dependencies, @NotNull String[] args) {
        if (StringUtil.getArgument(args, "log") != null) {
            java.util.logging.Logger root = java.util.logging.Logger.getLogger("");
            root.setLevel(Level.ALL);
            for (Handler handler : root.getHandlers()) {
                handler.setLevel(Level.ALL);
            }
        }
    }

    @Override
    public String toString() {
        return "FineLogging";
    }
}
