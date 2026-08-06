package com.furkan.apidebugagent.analysis;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One analysis as the server remembers it: every event it has produced so far, whoever is
 * listening, and the report once it is done.
 *
 * <p>Events are kept, not just forwarded. The client asks for the stream in a second request, so
 * by the time it connects the first stages may already be over; a subscriber is replayed what it
 * missed and then joined to the live stream, under the same lock, so nothing is lost or doubled.
 */
public final class AnalysisRecord {

    private static final Logger log = LoggerFactory.getLogger(AnalysisRecord.class);

    private final String analysisId;

    private final Instant from;

    private final Instant to;

    private final List<AnalysisEvent> events = new ArrayList<>();

    private final List<Consumer<AnalysisEvent>> listeners = new ArrayList<>();

    /** Read without the lock by {@link #status()} and {@link #report()}. */
    private volatile AnalysisReport report;

    AnalysisRecord(String analysisId, Instant from, Instant to) {
        this.analysisId = analysisId;
        this.from = from;
        this.to = to;
    }

    public String analysisId() {
        return analysisId;
    }

    public Instant from() {
        return from;
    }

    public Instant to() {
        return to;
    }

    public AnalysisStatus status() {
        AnalysisReport current = report;
        return current == null ? AnalysisStatus.RUNNING : current.status();
    }

    /** Empty until the analysis is over, whether it succeeded or failed. */
    public Optional<AnalysisReport> report() {
        return Optional.ofNullable(report);
    }

    /**
     * Sends everything that has happened so far, then attaches for what is still to come.
     *
     * @return how to detach; safe to run more than once
     */
    public synchronized Runnable subscribe(Consumer<AnalysisEvent> listener) {
        for (AnalysisEvent event : events) {
            deliver(listener, event);
        }
        if (finished()) {
            return () -> {
            };
        }
        listeners.add(listener);
        return () -> unsubscribe(listener);
    }

    synchronized void publish(AnalysisEvent event) {
        events.add(event);
        // A listener may detach while being delivered to — the terminal event closes its stream.
        for (Consumer<AnalysisEvent> listener : List.copyOf(listeners)) {
            deliver(listener, event);
        }
    }

    synchronized void complete(AnalysisReport report) {
        this.report = report;
        listeners.clear();
    }

    private synchronized void unsubscribe(Consumer<AnalysisEvent> listener) {
        listeners.remove(listener);
    }

    /**
     * A listener is a client connection. One of them going away is normal and must not stop the
     * analysis or the other listeners.
     */
    private void deliver(Consumer<AnalysisEvent> listener, AnalysisEvent event) {
        try {
            listener.accept(event);
        }
        catch (RuntimeException e) {
            log.debug("Listener of analysis {} rejected a {} event", analysisId, event.type(), e);
        }
    }

    private boolean finished() {
        return !events.isEmpty() && events.get(events.size() - 1).terminal();
    }

}
