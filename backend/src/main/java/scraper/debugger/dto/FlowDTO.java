package scraper.debugger.dto;

import scraper.api.FlowMap;

import java.util.HashMap;
import java.util.Map;

public class FlowDTO {
    private final Map<String, Object> content = new HashMap<>();
    private String ident;

    // Default constructor for Json parser
    public FlowDTO() {
    }

    public FlowDTO(String ident, FlowMap o) {

    }
}
