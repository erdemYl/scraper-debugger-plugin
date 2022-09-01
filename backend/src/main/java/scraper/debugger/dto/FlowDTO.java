package scraper.debugger.dto;


import scraper.api.NodeAddress;

@SuppressWarnings("unused") // DTO
public class FlowDTO {
    private String ident;
    private String parentIdent;
    private String nodeAddress;

    public String getIdent() { return ident; }
    public String getParentIdent() { return parentIdent; }
    public String getNodeAddress() { return nodeAddress; }

    // Default constructor for Json parser
    public FlowDTO() {
    }

    public FlowDTO(String ident, String parentIdent, NodeAddress nodeAddress) {
        this.ident = ident;
        this.parentIdent = parentIdent;
        this.nodeAddress = nodeAddress.getRepresentation();
    }
}
