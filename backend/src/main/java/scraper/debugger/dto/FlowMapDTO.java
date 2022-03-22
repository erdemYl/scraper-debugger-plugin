package scraper.debugger.dto;
import scraper.api.FlowMap;
import scraper.debugger.core.FlowIdentifier;


import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("unused") // DTO
public class FlowMapDTO {

    // Headers by scraper
    private final Map<String, Object> content = new HashMap<>();
    private String id;
    private String parentId;
    private int sequence;
    private Integer parentSequence;

    // Headers by debugger
    private String identification;
    private String parentIdent;
    private int graphLevel;
    private String flowTo;

    // Getters
    public Map<String, ?> getContent() { return content; }
    public String getId() { return id; }
    public String getParentId() { return parentId; }
    public Integer getSequence() { return sequence; }
    public Integer getParentSequence() { return parentSequence; }

    // Getters
    public String getIdentification() { return identification; }
    public String getParentIdent() { return parentIdent; }
    public Integer getGraphLevel() { return graphLevel; }
    public String getFlowTo() { return flowTo; }

    // Default Constructor for JSON Parser
    public FlowMapDTO() {
    }


    // Init without debugger headers
    public FlowMapDTO(FlowMap o) {
        o.forEach((l,v) -> content.put(l.getLocation().getRaw().toString(), v));

        if(o.getParentId().isPresent()) {
            parentId = o.getParentId().get().toString();
            this.parentSequence = o.getParentSequence().get();
        } else {
            parentId = null;
            parentSequence = null;
        }

        this.id = o.getId().toString();
        this.sequence = o.getSequence();
    }


    // Init with debugger headers
    public FlowMapDTO(FlowMap o, String ident, String pIdent, int level, FlowIdentifier.FlowTo flowTo) {
        o.forEach((l,v) -> content.put(l.getLocation().getRaw().toString(), v));

        if(o.getParentId().isPresent()) {
            parentId = o.getParentId().get().toString();
            this.parentSequence = o.getParentSequence().get();
        } else {
            parentId = null;
            parentSequence = null;
        }

        this.id = o.getId().toString();
        this.sequence = o.getSequence();

        this.identification = ident;
        this.parentIdent = pIdent;
        this.graphLevel = level;
        this.flowTo = flowTo.toString();
    }
}
