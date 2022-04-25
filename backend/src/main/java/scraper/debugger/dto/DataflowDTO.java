package scraper.debugger.dto;


import scraper.api.FlowMap;
import scraper.api.NodeAddress;

import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("unused") // DTO
public class DataflowDTO {
    private String ident;
    private String parentIdent;
    private String nodeAddress;
    private final Map<String, Object> content = new HashMap<>();

    public String getIdent() { return ident; }
    public String getParentIdent() { return parentIdent; }
    public String getNodeAddress() { return nodeAddress; }
    public Map<String, ?> getContent() { return content; }

    // Default constructor for Json parser
    public DataflowDTO() {
    }

    public DataflowDTO(String ident, String parentIdent, NodeAddress nodeAddress, FlowMap o) {
        this.ident = ident;
        this.parentIdent = parentIdent;
        this.nodeAddress = nodeAddress.getRepresentation();
        o.forEach((l,v) -> content.put(l.getLocation().getRaw().toString(), v));
    }
}
