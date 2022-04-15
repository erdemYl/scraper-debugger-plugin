package scraper.debugger.dto;

import scraper.api.Address;
import scraper.plugins.core.flowgraph.api.ControlFlowEdge;
import scraper.plugins.core.flowgraph.api.ControlFlowGraph;

import java.util.*;

@SuppressWarnings("unused") // DTO
public class ControlFlowGraphDTO {

    private final Map<String, Set<String>> outgoingAddressMap = new HashMap<>();
    private final Set<String> endNodes = new HashSet<>();
    private String start;

    public Map<String, Set<String>> getOutgoingAddressMap() { return outgoingAddressMap; }
    public Set<String> getEndNodes() { return endNodes; }
    public String getStart() { return start; }

    // Default Constructor for JSON Parser
    public ControlFlowGraphDTO() {

    }

    public ControlFlowGraphDTO(ControlFlowGraph cfg, Address start) {
        this.start = start.getRepresentation();

        cfg.getNodes().forEach((a, n) -> {
            String adr = a.getRepresentation();
            Set<String> goes = new HashSet<>();

            for (ControlFlowEdge out : cfg.getOutgoingEdges(a)) {
                goes.add(out.getToAddress().getRepresentation());
            }

            if (goes.isEmpty()) endNodes.add(adr);

            outgoingAddressMap.put(adr, goes);
        });
    }
}
