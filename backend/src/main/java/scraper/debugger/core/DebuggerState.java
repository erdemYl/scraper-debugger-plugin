package scraper.debugger.core;

import scraper.api.*;

import java.lang.System.Logger.Level;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

public final class DebuggerState {

    final System.Logger l = System.getLogger("DebuggerState");
    final ReentrantLock BARGE_IN = new ReentrantLock(true);

    // flows will wait on this object
    private final Object breaking = new Object();
    private final AtomicBoolean start = new AtomicBoolean(false);

    private final Set<String> breakpoints = new HashSet<>();

    public void waitUntilStart() {
        synchronized (start) {
            try {
                if (!start.get()) {
                    l.log(Level.INFO, "Waiting for debugger to connect");
                    start.wait();
                }
            } catch (Exception ignored) {}
        }
    }

    void setStart() {
        synchronized (start) {
            start.set(true);
            start.notifyAll();
        }
    }

    /**
     * All flows with permission will continue
     */
    void setContinue() {
        synchronized (breaking) {
            breaking.notifyAll();
        }
    }

    void waitOnBreakpoint(Runnable onWait) {
        synchronized (breaking) {
            try {
                onWait.run();
                breaking.wait();
            } catch (InterruptedException e) {
                l.log(Level.INFO, "Continuing because interrupt");
            }
        }
    }

    void waitOnBreakpoint() {
        synchronized (breaking) {
            try {
                breaking.wait();
            } catch (InterruptedException e) {
                l.log(Level.INFO, "Continuing because interrupt");
            }
        }
    }

    boolean isBreakpoint(NodeAddress address) {
        return breakpoints.contains(address.getRepresentation());
    }

    void addBreakpoint(String address) {
        breakpoints.add(address);
    }

    @Override
    public String toString() {
        return "DebuggerState";
    }
}
