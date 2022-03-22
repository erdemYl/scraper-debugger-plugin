package scraper.debugger.core;

import scraper.api.*;

import java.lang.System.Logger.Level;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

public final class DebuggerState {

    public final System.Logger l = System.getLogger("DebuggerState");
    public final ReentrantLock BARGE_IN = new ReentrantLock(true);

    // flows will wait on this object
    private static final AtomicBoolean breaking = new AtomicBoolean(false);
    private static final AtomicBoolean ready = new AtomicBoolean(false);

    private static final Set<String> breakpointsBefore = new HashSet<>();
    private static final Set<String> breakpointsAfter = new HashSet<>();

    public DebuggerState() {
    }

    public void waitUntilReady() {
        try {
            synchronized (ready) {
                if(!ready.get()) {
                    l.log(Level.INFO, "Waiting for debugger to connect");
                    ready.wait();
                }
            }
        } catch (Exception ignored) {}
    }

    public void setReady(boolean b) {
        synchronized (ready) {
            ready.set(true);

            // if ready, notify every flow to wake up
            if (b) ready.notifyAll();
        }
    }

    public void waitOnBreakpoint(Runnable onWait, Runnable onCont) {
        synchronized (breaking) {
            try {
                onWait.run();
                breaking.wait();
                onCont.run();
            } catch (InterruptedException e) {
                l.log(Level.INFO, "Continuing because interrupt");
            }
        }
    }

    public void waitOnBreakpoint(Runnable onWait) {
        synchronized (breaking) {
            try {
                onWait.run();
                breaking.wait();
            } catch (InterruptedException e) {
                l.log(Level.INFO, "Continuing because interrupt");
            }
        }
    }

    public void waitOnBreakpoint() {
        synchronized (breaking) {
            try {
                breaking.wait();
            } catch (InterruptedException e) {
                l.log(Level.INFO, "Continuing because interrupt");
            }
        }
    }

    public boolean isBreakpoint(NodeAddress address, boolean beforeBP) {
        String a = address.getRepresentation();
        return beforeBP ? breakpointsBefore.contains(a) : breakpointsAfter.contains(a);
    }

    /**
     * All flows with permission will continue
     */
    public void setContinue() {
        synchronized (breaking) {
            breaking.notifyAll();
        }
    }

    public void addBreakpoint(String br, boolean b) {
        if (b) breakpointsBefore.add(br);
        else breakpointsAfter.add(br);
    }

    @Override
    public String toString() {
        return "DebuggerState";
    }
}
