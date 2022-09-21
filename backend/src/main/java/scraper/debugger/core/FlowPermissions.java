package scraper.debugger.core;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FlowPermissions {

    private final Set<UUID> permittedFlows = ConcurrentHashMap.newKeySet();

    void create(UUID id) {
        permittedFlows.add(id);
    }

    void remove(UUID id) {
        permittedFlows.remove(id);
    }

    void removeAll() {
        permittedFlows.clear();
    }

    public boolean exists(UUID id) {
        return permittedFlows.contains(id);
    }

    @Override
    public String toString() {
        return "DebuggerFlowPermissions";
    }
}
