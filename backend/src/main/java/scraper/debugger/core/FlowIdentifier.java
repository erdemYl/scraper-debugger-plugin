package scraper.debugger.core;

import com.googlecode.concurrenttrees.radix.node.concrete.DefaultCharSequenceNodeFactory;
import com.googlecode.concurrenttrees.radixinverted.ConcurrentInvertedRadixTree;
import com.googlecode.concurrenttrees.radixinverted.InvertedRadixTree;

import scraper.api.*;
import scraper.debugger.dto.FlowDTO;
import scraper.debugger.dto.FlowMapDTO;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static scraper.debugger.addon.DebuggerHookAddon.getNodeType;


public class FlowIdentifier {

    // Every identified flow
    private final ConcurrentMap<UUID, Dataflow> identifiedFlows = new ConcurrentHashMap<>();

    // Quasi-static flow tree
    private final InvertedRadixTree<Dataflow> quasiStaticTree = new ConcurrentInvertedRadixTree<>(new DefaultCharSequenceNodeFactory());

    // Debugger components
    private final DebuggerServer SERVER;
    private final FlowPermissions FP;


    public FlowIdentifier(DebuggerServer SERVER, FlowPermissions FP) {
        this.SERVER = SERVER;
        this.FP = FP;
    }


    private static class Dataflow {

        final UUID id;

        /** Identification of this data flow */
        final CharSequence ident;

        /**  Transfer info object, send directly */
        final FlowDTO infoSend;

        /** Transfer content object, send upon frontend request */
        final FlowMapDTO contentSend;

        /** Whether this flow is flowing to a node, which emits new flows */
        final boolean toFlowEmitterNode;

        /** Whether this flow is flowing to a fork node */
        final boolean toForkNode;

        /** Next assignable postfix integer for identification */
        final AtomicInteger postfix = new AtomicInteger(0);

        Dataflow(CharSequence ident, CharSequence pIdent, NodeContainer<? extends Node> n, FlowMap o) {
            NodeAddress address = n.getAddress();
            id = o.getId();
            infoSend = new FlowDTO(ident, pIdent, address);
            contentSend = new FlowMapDTO(ident, o);
            this.ident = ident;
            toFlowEmitterNode = getNodeType(address).isFlowEmitter();
            toForkNode = getNodeType(address).isFork();
        }

        CharSequence next() {
            // Non-blocking synchronization
            // Java Concurrency in Practice, 2010, Chapter 15
            int post = postfix.getAndIncrement();
            return (toFlowEmitterNode
                    ? ident + String.valueOf(post) + "."
                    : ident + String.valueOf(post))
                    .intern();
        }
    }


    //=============
    // Identify
    //=============

    /**
     * Identifies a data flow to/from node n, with flow-map o.
     */
    public void identify(NodeContainer<? extends Node> n, FlowMap o) {

        // each flow initially has permission
        FP.create(o.getId());

        Dataflow f = identifyNew(n, o);

        // info dto
        SERVER.sendIdentifiedFlow(f.infoSend);
    }

    private Dataflow identifyNew(NodeContainer<? extends Node> n, FlowMap o) {
        UUID parent = o.getParentId().orElseThrow(() -> new NodeException("Distinct service group needed!"));
        UUID id = o.getId();

        CharSequence ident;
        Dataflow flow;

        if (!exists(parent)) {// initial flow
            FP.create(parent); // parent of initial flow has always permission
            ident = "i";
            flow = new Dataflow("i", "", n, o);
        } else {
            Dataflow pFlow = identifiedFlows.get(parent);
            ident =  pFlow.next();
            flow = new Dataflow(ident, pFlow.ident, n, o);
        }

        identifiedFlows.put(id, flow);
        quasiStaticTree.put(ident, flow);
        return flow;
    }

    public void forEachIdentified(Consumer<UUID> consumer) {
        identifiedFlows.keySet().forEach(consumer);
    }

    /**
     * Gets identification wrapped in optional.
     */
    public Optional<CharSequence> getOptional(UUID id) {
        if (id == null) return Optional.empty();
        Dataflow f = identifiedFlows.get(id);
        return f == null ? Optional.empty() : Optional.of(f.ident);
    }

    /**
     * Gets identification without wrapping in optional.
     * Returns empty string if given uuid not identified.
     */
    public CharSequence getExact(UUID id) {
        Dataflow f = identifiedFlows.get(id);
        return f == null ? "" : f.ident;
    }

    public boolean exists(UUID id) {
        return identifiedFlows.containsKey(id);
    }

    public FlowDTO getFlowDTO(UUID id) {
        Dataflow f = identifiedFlows.get(id);
        return f == null ? null : f.infoSend;
    }

    UUID toUUID(CharSequence ident) {
        Dataflow f = quasiStaticTree.getValueForExactKey(ident);
        return f == null ? null : f.id;
    }


    //=============
    // Lifecycle
    //=============

    enum LifecycleFilter {
        ONE,                       // only one flow
        NORMAL,                    // all lifecycle
        TO_FLOW_EMITTER,           // flow to nodes that introduce new flows
        TO_FLOW_EMITTER_NOT_FORK,  // flow to nodes that introduce new flows except fork nodes
        TO_FORK,                   // flow to fork node
        NOT_TO_FLOW_EMITTER        // flow to nodes that do not introduce new flows
    }

    Deque<FlowMapDTO> getLifecycle(LifecycleFilter filter, CharSequence ident) {
        if (filter == LifecycleFilter.ONE) {
            FlowMapDTO f = quasiStaticTree.getValueForExactKey(ident).contentSend;
            return new LinkedList<>(List.of(f));
        }

        Iterable<Dataflow> lifecycle = quasiStaticTree.getValuesForKeysContainedIn(ident);
        List<Dataflow> flows = new LinkedList<>();
        lifecycle.forEach(flows::add);

        switch (filter) {
            case NORMAL: {
                return flows.stream()
                        .map(f -> f.contentSend)
                        .collect(Collectors.toCollection(LinkedList::new));
            }
            case TO_FLOW_EMITTER: {
                return flows.stream()
                        .filter(f -> f.toFlowEmitterNode)
                        .map(f -> f.contentSend)
                        .collect(Collectors.toCollection(LinkedList::new));
            }
            case TO_FLOW_EMITTER_NOT_FORK: {
                return flows.stream()
                        .filter(f -> f.toFlowEmitterNode && !f.toForkNode)
                        .map(f -> f.contentSend)
                        .collect(Collectors.toCollection(LinkedList::new));
            }
            case TO_FORK: {
                return flows.stream()
                        .filter(f -> f.toForkNode)
                        .map(f -> f.contentSend)
                        .collect(Collectors.toCollection(LinkedList::new));
            }
            case NOT_TO_FLOW_EMITTER: {
                return flows.stream()
                        .filter(f -> !f.toFlowEmitterNode)
                        .map(f -> f.contentSend)
                        .collect(Collectors.toCollection(LinkedList::new));
            }
        }
        return new LinkedList<>();
    }

    @Override
    public String toString() {
        return "DebuggerFlowIdentifier";
    }
}
