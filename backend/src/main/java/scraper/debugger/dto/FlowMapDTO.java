package scraper.debugger.dto;

import scraper.api.FlowMap;

import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("unused") // DTO
public class FlowMapDTO {

    private String ident;
    private final Map<String, Object> content = new HashMap<>();

    public String getIdent() { return ident; }
    public Map<String, ?> getContent() { return content; }

    // Default constructor for Json parser
    public FlowMapDTO() {
    }

    public FlowMapDTO(String ident, FlowMap o) {
        this.ident = ident;
        o.forEach((l,v) -> content.put(l.getLocation().getRaw().toString(), v));
    }
}
