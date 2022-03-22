package scraper.debugger.dto;

import scraper.api.ScrapeInstance;

import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("unused") // DTO
public class InstanceDTO {

    private String name;
    private String entry;
    private Map<String, Object> entryArguments;
    private Map<String, InstanceDTO> importedInstances = new HashMap<>();
    private Map<String, NodeDTO> routes = new HashMap<>();

    public String getEntry() { return entry; }
    public String getName() { return name; }
    public Map<String, Object> getEntryArguments() { return entryArguments; }
    public Map<String, InstanceDTO> getImportedInstances() { return importedInstances; }
    public Map<String, NodeDTO> getRoutes() { return routes; }

    // Default Constructor for JSON Parser
    public InstanceDTO() {

    }

    public InstanceDTO(ScrapeInstance i) {
        this.name = i.getName();
        this.entryArguments = i.getEntryArguments();

        if(i.getEntry().isPresent()) {
            this.entry = i.getEntry().get().getAddress().toString();
        } else {
            this.entry = null;
        }
        i.getRoutes().forEach((address, nodeContainer) -> routes.put(address.toString(), new NodeDTO(nodeContainer)));
        i.getImportedInstances().forEach((adr, impl) -> importedInstances.put(adr.toString(), new InstanceDTO(impl)));
    }
}
