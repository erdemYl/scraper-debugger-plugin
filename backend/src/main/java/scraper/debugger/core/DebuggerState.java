package scraper.debugger.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scraper.api.*;
import scraper.util.NodeUtil;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public final class DebuggerState {

    final Logger l = LoggerFactory.getLogger("DebuggerState");

    // flows will wait on this object
    private final Object breaking = new Object();
    private final AtomicBoolean start = new AtomicBoolean(false);

    private final Set<Address> breakpoints = new HashSet<>();

    public void waitUntilStart() {
        synchronized (start) {
            try {
                if (!start.get()) {
                    l.info("Waiting for debugger to connect");
                    start.wait();
                }
            } catch (Exception ignored) {}
        }
    }

    void setStart() {
        synchronized (start) {
            start.set(true);
            l.info("Workflow started");
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
                l.warn("Continuing because interrupt");
            }
        }
    }

    void waitOnBreakpoint() {
        synchronized (breaking) {
            try {
                breaking.wait();
            } catch (InterruptedException e) {
                l.warn("Continuing because interrupt");
            }
        }
    }

    boolean isBreakpoint(NodeAddress address) {
        return breakpoints.contains(address);
    }

    void addBreakpoint(String address) {
        if (breakpoints.add(NodeUtil.addressOf(address)))
            l.debug("Breakpoint added: {}", address);
    }

    @Override
    public String toString() {
        return "DebuggerState";
    }
}
