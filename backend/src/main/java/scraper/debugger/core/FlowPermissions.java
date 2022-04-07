package scraper.debugger.core;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FlowPermissions {

    private final Set<UUID> permittedFlows = ConcurrentHashMap.newKeySet(32);

    void create(UUID id) {
        permittedFlows.add(id);
    }

    void remove(UUID id) {
        permittedFlows.remove(id);
    }

    void removeAll() {
        permittedFlows.clear();
    }

    boolean permitted(UUID id) {
        return permittedFlows.contains(id);
    }

    boolean notPermitted(UUID id) {
        return !permitted(id);
    }

    @Override
    public String toString() {
        return "DebuggerFlowPermissions";
    }
}
