import scraper.api.Hook;
import scraper.debugger.frontend.hook.*;

open module scraper.debugger.frontend {
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

    // concurrent radix tree
    requires concurrent.trees;

    // javafx
    requires javafx.graphics;
    requires javafx.controls;
    requires javafx.fxml;

    // backend
    requires scraper.debugger;

    exports scraper.debugger.frontend.api;

    provides Hook with FrontendHook;
}