package scraper.debugger.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import scraper.api.Address;
import scraper.api.Node;
import scraper.api.NodeContainer;
import scraper.util.NodeUtil;

import java.util.Map;

@SuppressWarnings("unused") // DTO
public class NodeDTO {
    private Map<String, ?> nodeConfiguration;
    private String address;

    public Map<String, ?> getNodeConfiguration() { return nodeConfiguration; }
    public String getAddress() { return address; }

    @JsonIgnore
    public String getType() {
        String t = (String) nodeConfiguration.get("type");
        return t == null ? (String) nodeConfiguration.get("f") : t;
    }

    // Default Constructor for JSON Parser
    public NodeDTO() {
    }

    public NodeDTO(NodeContainer<? extends Node> n) {
        this.nodeConfiguration = n.getNodeConfiguration();
        this.address = n.getAddress().toString();
    }
}
