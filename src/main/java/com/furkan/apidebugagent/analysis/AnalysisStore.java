package com.furkan.apidebugagent.analysis;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The analyses this instance still knows about — the last {@value #MAX_RECORDS}, in memory, gone
 * on restart. Debugging sessions are short and a report is cheap to produce again, so there is no
 * storage behind this.
 */
@Component
public class AnalysisStore {

    private static final Logger log = LoggerFactory.getLogger(AnalysisStore.class);

    /** How many analyses are kept; the oldest is dropped when a new one arrives. */
    private static final int MAX_RECORDS = 10;

    private final Map<String, AnalysisRecord> records = Collections.synchronizedMap(
        new LinkedHashMap<>() {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, AnalysisRecord> eldest) {
                return size() > MAX_RECORDS;
            }
        });

    public AnalysisRecord create(Instant from, Instant to) {
        AnalysisRecord record = new AnalysisRecord(UUID.randomUUID().toString(), from, to);
        records.put(record.analysisId(), record);
        return record;
    }

    public Optional<AnalysisRecord> find(String analysisId) {
        return Optional.ofNullable(records.get(analysisId));
    }

    /** Silently ignores an unknown id: the analysis may have been evicted while it was running. */
    public void publish(String analysisId, AnalysisEvent event) {
        AnalysisRecord record = records.get(analysisId);
        if (record == null) {
            log.debug("Dropping a {} event for unknown analysis {}", event.type(), analysisId);
            return;
        }
        record.publish(event);
    }

    public void complete(String analysisId, AnalysisReport report) {
        AnalysisRecord record = records.get(analysisId);
        if (record == null) {
            log.debug("Analysis {} finished but is no longer stored", analysisId);
            return;
        }
        record.complete(report);
    }

}
