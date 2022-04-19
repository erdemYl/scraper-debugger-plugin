import scraper.api.Addon;
import scraper.api.Hook;
import scraper.debugger.addon.DebuggerAddon;
import scraper.debugger.addon.DebuggerHook;
import scraper.debugger.addon.WaitHook;

open module debugger.backend {
    // scraper
    requires scraper.api;
    requires scraper.utils;
    requires scraper.core;
    requires scraper.core.plugins;

    // json
    requires com.fasterxml.jackson.core;
    requires com.fasterxml.jackson.databind;

    // websocket
    requires Java.WebSocket;

    // logging
    requires org.slf4j;
    requires java.logging;

    exports scraper.debugger.core;
    exports scraper.debugger.dto;
    exports scraper.debugger.tree;

    provides Addon with DebuggerAddon;
    provides Hook with DebuggerHook, WaitHook;
}