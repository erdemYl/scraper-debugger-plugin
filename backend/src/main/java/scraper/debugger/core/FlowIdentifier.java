package scraper.debugger.core;

import scraper.api.*;
import scraper.debugger.dto.FlowDTO;
import scraper.debugger.tree.Trie;

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static scraper.debugger.addon.DebuggerHook.getNodeType;


public class FlowIdentifier {

    // Every identified flow
    private final Map<UUID, IdentifiedFlow> identifiedFlows = new ConcurrentHashMap<>();


    // Quasi-static flow tree
    private final Trie<IdentifiedFlow> quasiStaticTree = new Trie<>();


    // Branch lock provider
    private final LockProvider lockProvider = new LockProvider();


    // Debugger components
    private final DebuggerServer SERVER;
    private final DebuggerState STATE;
    private final FlowPermissions FP;


    public FlowIdentifier(DebuggerServer SERVER, DebuggerState STATE, FlowPermissions FP) {
        this.SERVER = SERVER;
        this.STATE = STATE;
        this.FP = FP;
    }


    private static class IdentifiedFlow {

        final UUID id;

        /** Identification of this flow */
        final String ident;

        /**  Transfer object */
        final FlowDTO toSent;

        /** Whether this flow is flowing to a node, which can emit new flows */
        final boolean toFlowEmitterNode;

        /** Whether this flow is flowing to a fork node */
        final boolean toForkNode;

        int postfix = 0;

        IdentifiedFlow(String ident, String pIdent, NodeContainer<? extends Node> n, FlowMap o) {
            NodeAddress address = n.getAddress();
            this.id = o.getId();
            toSent = new FlowDTO(ident, pIdent, address, o);
            this.ident = ident;
            toFlowEmitterNode = getNodeType(address).isFlowEmitter();
            toForkNode = getNodeType(address).isFork();
        }

        synchronized String next() {
            String post = toFlowEmitterNode
                    ? ident + postfix + "."
                    : ident + postfix;
            postfix++;
            return post;
        }
    }


    private static class LockProvider {
        private final Trie<Lock> locks;
        private final Lock defaultLock;

        private LockProvider() {
            locks = new Trie<>();
            defaultLock = new ReentrantLock(true);
            locks.put("i", defaultLock);
        }

        public void generateLock(String ident) {
            locks.put(ident, new ReentrantLock(true));
        }


        public void lock(String ident) {
            Lock l;
            if (ident.startsWith("i")) {
                l = locks.getLongestMatchedEntry(ident).getValue();
            } else {
                l = defaultLock;
            }
            l.lock();
        }

        public void unlock(String ident) {
            Lock l;
            if (ident.startsWith("i")) {
                l = locks.getLongestMatchedEntry(ident).getValue();
            } else {
                l = defaultLock;
            }
            l.unlock();
        }
    }



    //=============
    // Identify
    //=============

    public void identify(NodeContainer<? extends Node> n, FlowMap o) {

        // each flow initially has permission
        FP.create(o.getId());

        IdentifiedFlow f = identifyNew(n, o);
        SERVER.sendIdentifiedFlow(f.toSent);
    }

    private IdentifiedFlow identifyNew(NodeContainer<? extends Node> n, FlowMap o) {
        UUID parent = o.getParentId().orElse(null);
        UUID id = o.getId();

        String ident;
        IdentifiedFlow flow;

        if (parent == null || !exists(parent)) {
            // initial flow
            ident = "i";
            flow = new IdentifiedFlow("i", "", n, o);
        } else {
            IdentifiedFlow pFlow = identifiedFlows.get(parent);
            ident =  pFlow.next();
            flow = new IdentifiedFlow(ident, pFlow.ident, n, o);
            if (pFlow.toForkNode) lockProvider.generateLock(ident);
        }

        identifiedFlows.put(id, flow);
        quasiStaticTree.put(ident, flow);
        return flow;
    }


    public void acquireBranchLock(UUID id) {
        String ident = getOptional(id).orElse("i");
        lockProvider.lock(ident);
    }


    public void releaseBranchLock(UUID id) {
        String ident = getOptional(id).orElse("i");
        lockProvider.unlock(ident);
    }


    public void forEachIdentified(Consumer<UUID> consumer) {
        identifiedFlows.keySet().forEach(consumer);
    }

    /**
     * Gets identification wrapped in optional.
     */
    public Optional<String> getOptional(UUID id) {
        if (id == null) return Optional.empty();
        IdentifiedFlow f = identifiedFlows.get(id);
        return f == null ? Optional.empty() : Optional.of(f.ident);
    }

    /**
     * Gets identification without wrapping in optional.
     * Returns empty string if given uuid not identified.
     */
    public String getExact(UUID id) {
        IdentifiedFlow f = identifiedFlows.get(id);
        return f == null ? "" : f.ident;
    }

    public boolean exists(UUID id) {
        return identifiedFlows.containsKey(id);
    }

    public int treeLevelOf(UUID id) {
        IdentifiedFlow f = identifiedFlows.get(id);
        return f == null ? 0 : treeLevelOf(f.ident);
    }

    public FlowDTO getDTO(UUID id) {
        IdentifiedFlow f = identifiedFlows.get(id);
        return f == null ? null : f.toSent;
    }

    UUID toUUID(String ident) {
        IdentifiedFlow f = quasiStaticTree.get(ident);
        return f == null ? null : f.id;
    }


    //=============
    // Lifecycle
    //=============

    enum LifecycleFilter {
        NORMAL,                    // all lifecycle
        TO_FLOW_EMITTER,           // flow to nodes that introduce new flows
        TO_FLOW_EMITTER_NOT_FORK,  // flow to nodes that introduce new flows except fork nodes
        TO_FORK,                   // flow to fork node
        NOT_TO_FLOW_EMITTER        // flow to nodes that do not introduce new flows
    }

    Deque<FlowDTO> getLifecycle(LifecycleFilter filter, String ident) {
        switch (filter) {
            case NORMAL -> {
                return quasiStaticTree.getValuesOn(ident)
                        .stream()
                        .map(f -> f.toSent)
                        .collect(Collectors.toCollection(LinkedList::new));
            }
            case TO_FLOW_EMITTER -> {
                return quasiStaticTree.getValuesOn(ident)
                        .stream()
                        .filter(f -> f.toFlowEmitterNode)
                        .map(f -> f.toSent)
                        .collect(Collectors.toCollection(LinkedList::new));
            }
            case TO_FLOW_EMITTER_NOT_FORK -> {
                return quasiStaticTree.getValuesOn(ident)
                        .stream()
                        .filter(f -> f.toFlowEmitterNode && !f.toForkNode)
                        .map(f -> f.toSent)
                        .collect(Collectors.toCollection(LinkedList::new));
            }
            case TO_FORK -> {
                return quasiStaticTree.getValuesOn(ident)
                        .stream()
                        .filter(f -> f.toForkNode)
                        .map(f -> f.toSent)
                        .collect(Collectors.toCollection(LinkedList::new));
            }
            case NOT_TO_FLOW_EMITTER -> {
                return quasiStaticTree.getValuesOn(ident)
                        .stream()
                        .filter(f -> !f.toFlowEmitterNode)
                        .map(f -> f.toSent)
                        .collect(Collectors.toCollection(LinkedList::new));
            }
        }
        return new LinkedList<>();
    }



    private int treeLevelOf(String ident) {
        return quasiStaticTree.getValuesOn(ident).size() - 1;
    }

    @Override
    public String toString() {
        return "DebuggerFlowIdentifier";
    }
}
