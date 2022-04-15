import scraper.api.Hook;
import scraper.debugger.frontend.hook.*;

open module debugger.frontend {
    requires scraper.api;
    requires scraper.utils;

    // json processing
    requires com.fasterxml.jackson.core;
    requires com.fasterxml.jackson.databind;

    // websocket
    requires Java.WebSocket;

    // logging
    requires org.slf4j;
    requires java.logging;

    // javafx
    requires javafx.graphics;
    requires javafx.controls;
    requires javafx.fxml;

    // backend
    requires debugger.backend;

    exports scraper.debugger.frontend.api;
    exports scraper.debugger.frontend.hook;
    exports scraper.debugger.frontend.core;

    provides Hook with FrontendHook;
}