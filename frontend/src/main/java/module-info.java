import scraper.api.Hook;
import scraper.debugger.hook.FrontendHook;

module debugger.frontend {
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

    exports scraper.debugger.hook;
    exports scraper.debugger.mvcvm;
    opens scraper.debugger.mvcvm;

    provides Hook with FrontendHook;
}