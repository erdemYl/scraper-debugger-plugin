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
import java.util.stream.Stream;

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

        /** Next assignable postfix integer for identification */
        final AtomicInteger postfix = new AtomicInteger(0);

        Dataflow(CharSequence ident, CharSequence pIdent, NodeAddress address, FlowMap o) {
            id = o.getId();
            infoSend = new FlowDTO(ident, pIdent, address);
            contentSend = new FlowMapDTO(ident, o);
            this.ident = ident;
            toFlowEmitterNode = getNodeType(address).isFlowEmitter();
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

        Dataflow f = identifyNew(n.getAddress(), o);

        // info dto
        SERVER.sendIdentifiedFlow(f.infoSend);
    }

    private Dataflow identifyNew(NodeAddress address, FlowMap o) {
        UUID parent = o.getParentId().orElseThrow(() -> new NodeException("Distinct service group needed!"));
        UUID id = o.getId();

        CharSequence ident;
        Dataflow flow;

        if (!exists(parent)) { // initial flow
            FP.create(parent); // parent of initial flow has always permission
            ident = "i";
            flow = new Dataflow("i", "", address, o);
        } else {
            Dataflow pFlow = identifiedFlows.get(parent);
            ident =  pFlow.next();
            flow = new Dataflow(ident, pFlow.ident, address, o);
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

    public FlowMapDTO getFlowMapDTO(CharSequence ident) {
        Dataflow f = quasiStaticTree.getValueForExactKey(ident);
        return f == null ? null : f.contentSend;
    }

    void markAborted(NodeAddress address, FlowMap o) {
        // replace already identified flow with the aborted flow
        identifiedFlows.computeIfPresent(o.getId(), (id, f) -> {
            Dataflow ab = new Dataflow(f.ident + "XX", f.infoSend.getParentIdent(), address, o);
            quasiStaticTree.put(f.ident, ab);
            quasiStaticTree.put(ab.ident, ab);
            return ab;
        });
    }

    UUID toUUID(CharSequence ident) {
        Dataflow f = quasiStaticTree.getValueForExactKey(ident);
        return f == null ? null : f.id;
    }

    List<FlowMapDTO> getLifecycle(CharSequence ident) {
        Iterable<Dataflow> lifecycle = quasiStaticTree.getValuesForKeysContainedIn(ident);
        Stream.Builder<Dataflow> flows = Stream.builder();
        lifecycle.forEach(flows::add);

        return flows.build()
                .map(f -> f.contentSend)
                .collect(Collectors.toList());
    }

    @Override
    public String toString() {
        return "DebuggerFlowIdentifier";
    }
}