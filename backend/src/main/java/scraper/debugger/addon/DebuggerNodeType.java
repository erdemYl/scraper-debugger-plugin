package scraper.debugger.addon;

import scraper.api.Node;
import scraper.api.NodeContainer;
import scraper.api.StreamNodeContainer;

import java.util.Optional;

public enum DebuggerNodeType {
    FORK, MAP, MAP_MAP, STREAM_NODE, ON_WAY;

    public boolean isFlowEmitter() { return this != ON_WAY; }

    static DebuggerNodeType of(NodeContainer<? extends Node> n) {
        String t = (String) n.getNodeConfiguration().get("type");
        String tt = t == null ? (String) n.getNodeConfiguration().get("f") : t;

        switch (tt.toLowerCase()) {
            case "fork": {
                return FORK;
            }
            case "map": {
                return MAP;
            }
            case "mapmap": {
                return MAP_MAP;
            }
            default: {
                return n instanceof StreamNodeContainer ? STREAM_NODE : ON_WAY;
            }
        }
    }
}
