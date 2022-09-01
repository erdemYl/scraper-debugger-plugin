package scraper.debugger.dto;


import scraper.api.NodeAddress;

@SuppressWarnings("unused") // DTO
public class FlowDTO {
    private CharSequence ident;
    private CharSequence parentIdent;
    private String nodeAddress;

    public CharSequence getIdent() { return ident; }
    public CharSequence getParentIdent() { return parentIdent; }
    public String getNodeAddress() { return nodeAddress; }

    // Default constructor for Json parser
    public FlowDTO() {
    }

    public FlowDTO(CharSequence ident, CharSequence parentIdent, NodeAddress nodeAddress) {
        this.ident = ident;
        this.parentIdent = parentIdent;
        this.nodeAddress = nodeAddress.getRepresentation();
    }
}
