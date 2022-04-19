package scraper.debugger.dto;


import scraper.api.FlowMap;
import scraper.api.NodeAddress;

import java.util.HashMap;
import java.util.Map;

public class FlowDTO {
    private String ident;
    private String parentIdent;
    private String intoAddress;
    private final Map<String, Object> content = new HashMap<>();

    public String getIdent() { return ident; }
    public String getParentIdent() { return parentIdent; }
    public String getIntoAddress() { return intoAddress; }
    public Map<String, ?> getContent() { return content; }

    // Default constructor for Json parser
    public FlowDTO() {
    }

    public FlowDTO(String ident, String parentIdent, NodeAddress intoAddress, FlowMap o) {
        this.ident = ident;
        this.parentIdent = parentIdent;
        this.intoAddress = intoAddress.getRepresentation();
        o.forEach((l,v) -> content.put(l.getLocation().getRaw().toString(), v));
    }
}
