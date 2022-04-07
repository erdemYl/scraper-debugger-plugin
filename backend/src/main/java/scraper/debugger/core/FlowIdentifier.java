package scraper.debugger.core;

import scraper.api.*;
import scraper.debugger.dto.FlowMapDTO;
import scraper.debugger.dto.NodeDTO;
import scraper.debugger.graph.Trie;

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;


public class FlowIdentifier {

    // Stores each identified flow
    private final Map<UUID, IdentifiedFlow> identifiedFlows = new ConcurrentHashMap<>();
    private final Set<UUID> identifiedUUIDs = identifiedFlows.keySet();

    // Quasi-static flow tree
    private final Trie<IdentifiedFlow> quasiStaticTree = new Trie<>();

    // Stores flow updates to be sent
    private final Map<UUID, Runnable> flowUpdates = new ConcurrentHashMap<>();

    // Default lock provider
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
        final String ident;
        final NodeAddress address;
        final FlowMap map;
        final boolean toFlowOpener;
        final boolean toFork;
        int postfix = 0;

        IdentifiedFlow(String ident, NodeContainer<? extends Node> n, FlowMap o) {
            this.ident = ident;
            address = n.getAddress();
            map = o;
            FlowTo ft = FlowTo.get(n);
            toFlowOpener = ft.isFlowOpener();
            toFork = ft.isFork();
        }

        String next() {
            String post = toFlowOpener
                    ? ident + postfix + "."
                    : ident + postfix;
            postfix++;
            return post;
        }
    }


    public enum FlowTo {
        FORK, INT_RANGE, MAP, ON_WAY;

        boolean isFlowOpener() {
            switch (this) {
                case FORK, MAP, INT_RANGE -> {
                    return true;
                }
                default -> {
                    return false;
                }
            }
        }

        boolean openerAndNotFork() {
            switch (this) {
                case MAP, INT_RANGE -> {
                    return true;
                }
                default -> {
                    return false;
                }
            }
        }

        boolean isFork() {
            return this.equals(FORK);
        }

        public static FlowTo get(NodeContainer<? extends Node> n) {
            Optional<?> t = n.getKeySpec("type");
            String nodeType = t.isEmpty()
                    ? (String) n.getKeySpec("f").get()
                    : (String) t.get();
            switch (nodeType) {
                case "Fork" -> {
                    return FlowTo.FORK;
                }
                case "Map" -> {
                    return FlowTo.MAP;
                }
                case "IntRange" -> {
                    return FlowTo.INT_RANGE;
                }
                default -> {
                    return FlowTo.ON_WAY;
                }
            }
        }
    }


    public static class IdentificationScheduler {
        private final Queue<Entry<UUID, Object>> mutexes = new ConcurrentLinkedQueue<>();
        private final ReentrantLock sequenceMutex;

        private IdentificationScheduler(ReentrantLock sequenceMutex) {
            this.sequenceMutex = sequenceMutex;
        }

        public void enqueue(UUID id, Runnable with, boolean wait) {
            try {
                sequenceMutex.lock();
                Object mutex = new Object();
                mutexes.add(new AbstractMap.SimpleImmutableEntry<>(id, mutex));
                synchronized (mutex) {
                    with.run();
                    sequenceMutex.unlock();
                    if (wait) {
                        try {mutex.wait();} catch (InterruptedException e) {e.printStackTrace();}
                    }
                }
            } finally {
                if (sequenceMutex.isHeldByCurrentThread()) sequenceMutex.unlock();
            }
        }

        public void dequeue() {
            if (!mutexes.isEmpty()) {
                Object mutex = mutexes.poll();
                synchronized (mutex) {
                    mutex.notify();
                }
            }
        }
    }


    public static class LockProvider {
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


    public IdentificationScheduler newScheduler(ReentrantLock sequenceMutex) {
        return new IdentificationScheduler(sequenceMutex);
    }


    public LockProvider newLockProvider() {
        return new LockProvider();
    }


    /**
     * Acquires inherently the branch lock object of this flow.
     */
    public void identify(NodeContainer<? extends Node> n, FlowMap o, boolean send) {
        try {
            STATE.BARGE_IN.lock();
            FlowMap o2 = o.copy();
            UUID id = o2.getId();
            identifyNew(n, o2);
            acquireBranchLock(id);
            if (send) sendIdentified(id);

        } catch (Exception e) {
            STATE.l.log(System.Logger.Level.WARNING, "A flow in node {0} cannot be identified.", n.getAddress().toString());
        } finally {
            STATE.BARGE_IN.unlock();
        }
    }

    public boolean sendIdentified(UUID id) {
        Runnable r = flowUpdates.get(id);
        if (r == null) return false;
        flowUpdates.remove(id);
        r.run();
        return true;
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
        identifiedUUIDs.forEach(consumer);
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
        return identifiedUUIDs.contains(id);
    }

    private void identifyNew(NodeContainer<? extends Node> n, FlowMap o) {
        Optional<UUID> parent = o.getParentId();
        UUID id = o.getId();
        FP.create(id);
        FlowTo flowTo = FlowTo.get(n);
        Runnable update;

        if (parent.isEmpty() || !identifiedUUIDs.contains(parent.get())) {
            // initial flow, no identified parent
            IdentifiedFlow init = new IdentifiedFlow("i", n, o);
            identifiedFlows.put(id, init);
            quasiStaticTree.put("i", init);
            update = () -> SERVER.sendIdentifiedFlow(
                    new NodeDTO(n),
                    new FlowMapDTO(o, "i", "_", 0, flowTo),
                    true
            );
        } else {
            UUID parentId = parent.get();
            IdentifiedFlow parentFlow = identifiedFlows.get(parentId);
            String pIdent = parentFlow.ident;
            String ident =  parentFlow.next();

            IdentifiedFlow thisFlow = new IdentifiedFlow(ident, n, o);

            identifiedFlows.put(id, thisFlow);
            quasiStaticTree.put(ident, thisFlow);

            update = () -> SERVER.sendIdentifiedFlow(
                    new NodeDTO(n),
                    new FlowMapDTO(o, ident, pIdent, treeLevelOf(ident), flowTo),
                    false
            );

            if (parentFlow.toFork) lockProvider.generateLock(ident);
        }

        flowUpdates.put(id, update);
    }

    public int treeLevelOf(UUID id) {
        IdentifiedFlow f = identifiedFlows.get(id);
        return f == null ? 0 : treeLevelOf(f.ident);
    }

    private int treeLevelOf(String ident) {
        return quasiStaticTree.getValuesOn(ident).size() - 1;
    }

    @Override
    public String toString() {
        return "DebuggerFlowIdentifier";
    }
}
