package scraper.debugger.core;

import scraper.api.FlowMap;
import scraper.api.Node;
import scraper.api.NodeContainer;
import scraper.debugger.dto.FlowMapDTO;
import scraper.debugger.dto.NodeDTO;
import scraper.debugger.graph.Trie;

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;



public final class FlowIdentifier {

    // Stores each identified flow
    private final Map<UUID, IdentifiedFlow> identifiedFlows = new ConcurrentHashMap<>();

    // Tree structure of identified flows
    private final Trie<UUID> treeStructure = new Trie<>();

    // Stores flow updates to be sent
    private final Map<UUID, Runnable> flowUpdates = new ConcurrentHashMap<>();

    // Default lock provider
    private final LockProvider lockProvider = new LockProvider();

    // Debugger components
    private final DebuggerState STATE;
    private final DebuggerServer SERVER;
    private final FlowPermissions FP;

    public FlowIdentifier(DebuggerState STATE, DebuggerServer SERVER, FlowPermissions FP) {
        this.STATE = STATE;
        this.SERVER = SERVER;
        this.FP = FP;
    }


    private static class IdentifiedFlow {
        final String identification;
        final boolean toFlowOpener;
        final boolean toFork;
        int postfix = 0;

        IdentifiedFlow(String identification, FlowTo flowTo) {
            this.identification = identification;
            toFlowOpener = flowTo.isFlowOpener();
            toFork = flowTo.isFork();
        }

        String nextPostfix() {
            String post = toFlowOpener ? postfix + "." : String.valueOf(postfix);
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
        private final Trie<ReentrantLock> locks;

        private LockProvider() {
            locks = new Trie<>();
            locks.put("i", new ReentrantLock(true));   // default lock
        }

        public void generateLock(String ident) {
            locks.put(ident, new ReentrantLock(true));
        }


        public void lock(String ident) {
            ReentrantLock lock;
            if (ident.startsWith("i")) {
                lock = locks.getLongestMatchedEntry(ident).getValue();
            } else {
                lock = locks.get("i");
            }
            lock.lock();
        }

        public void unlock(String ident) {
            ReentrantLock lock;
            if (ident.startsWith("i")) {
                lock = locks.getLongestMatchedEntry(ident).getValue();
            } else {
                lock = locks.get("i");
            }
            if (lock.isHeldByCurrentThread()) lock.unlock();
        }
    }


    public IdentificationScheduler newScheduler(ReentrantLock sequenceMutex) {
        return new IdentificationScheduler(sequenceMutex);
    }


    public LockProvider newLockProvider() {
        return new LockProvider();
    }


    /**
     * Implicitly uses branch lock. Make sure that "releaseBranchLock" is used
     * after this method called. Otherwise, branch lock remains locked.
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

    /**
     * Use together with "identify".
     */
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
        return f == null ? Optional.empty() : Optional.of(f.identification);
    }

    /**
     * Gets identification without wrapping in optional.
     * Returns empty string if given uuid not identified.
     */
    public String getExact(UUID id) {
        IdentifiedFlow f = identifiedFlows.get(id);
        return f == null ? "" : f.identification;
    }

    /**
     * A query method. Returns all parent ids (with right sequence) of given flow id.
     * Assumes that parent flows also identified.
     * Otherwise, returns an empty set.
     */
    public List<UUID> getLifecycle(UUID id) {
        return treeStructure.getValuesOn(getExact(id));
    }

    public boolean exists(UUID id) {
        return identifiedFlows.containsKey(id);
    }

    private void identifyNew(NodeContainer<? extends Node> n, FlowMap o) {
        Optional<UUID> parent = o.getParentId();
        UUID id = o.getId();
        FP.create(id);
        FlowTo flowTo = FlowTo.get(n);
        Runnable update;

        if (parent.isEmpty() || !identifiedFlows.containsKey(parent.get())) {
            // initial flow, no identified parent
            IdentifiedFlow init = new IdentifiedFlow("i", flowTo);
            identifiedFlows.put(id, init);
            treeStructure.put("i", id);
            update = () -> SERVER.sendIdentifiedFlow(
                    new NodeDTO(n),
                    new FlowMapDTO(o, "i", "_", 0, flowTo),
                    true
            );
        } else {
            UUID parentId = parent.get();
            IdentifiedFlow parentFlow = identifiedFlows.get(parentId);
            String pIdent = parentFlow.identification;
            String ident =  pIdent + parentFlow.nextPostfix();

            identifiedFlows.put(id, new IdentifiedFlow(ident, flowTo));
            treeStructure.put(ident, id);

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
        return f == null ? 0 : treeLevelOf(f.identification);
    }

    private int treeLevelOf(String ident) {
        return treeStructure.getValuesOn(ident).size() - 1;
    }

    @Override
    public String toString() {
        return "FlowIdentifier";
    }
}
