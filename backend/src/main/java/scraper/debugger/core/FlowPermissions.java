package scraper.debugger.core;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class FlowPermissions {

    private final Set<UUID> permittedFlows = ConcurrentHashMap.newKeySet(32);

    public FlowPermissions() {
    }

    public void create(UUID id) {
        permittedFlows.add(id);
    }

    public void remove(UUID id) {
        permittedFlows.remove(id);
    }

    public void removeAll() {
        permittedFlows.clear();
    }

    public boolean get(UUID id) {
        return permittedFlows.contains(id);
    }

    @Override
    public String toString() {
        return "FlowPermissions";
    }
}
