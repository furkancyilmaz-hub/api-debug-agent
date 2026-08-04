package com.furkan.apidebugagent.schema;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.furkan.apidebugagent.sqllog.DemoApiUnavailableException;

@Component
public class ForeignKeyCache {

    private static final Logger log = LoggerFactory.getLogger(ForeignKeyCache.class);

    private final SchemaClient schemaClient;

    private volatile Map<String, ForeignKey> foreignKeysByChildKey = Map.of();

    public ForeignKeyCache(SchemaClient schemaClient) {
        this.schemaClient = schemaClient;
    }

    @EventListener(ApplicationReadyEvent.class)
    void onApplicationReady() {
        try {
            refresh();
        }
        catch (DemoApiUnavailableException e) {
            log.warn("Could not load foreign key metadata at startup; starting with empty cache. Reason: {}",
                e.getMessage());
        }
    }

    public synchronized void refresh() {
        List<ForeignKey> foreignKeys = schemaClient.fetchForeignKeys();
        Map<String, ForeignKey> newMap = foreignKeys.stream()
            .collect(Collectors.toUnmodifiableMap(fk -> key(fk.childTable(), fk.childColumn()), fk -> fk,
                (a, b) -> b));
        this.foreignKeysByChildKey = newMap;
        log.info("Loaded {} foreign key(s)", newMap.size());
    }

    public Optional<ForeignKey> byChildColumn(String table, String column) {
        Map<String, ForeignKey> current = foreignKeysByChildKey;
        if (current.isEmpty()) {
            try {
                refresh();
            }
            catch (DemoApiUnavailableException e) {
                throw new SchemaUnavailableException(
                    "Foreign key metadata is unavailable; cannot resolve " + table + "." + column, e);
            }
            current = foreignKeysByChildKey;
            if (current.isEmpty()) {
                throw new SchemaUnavailableException(
                    "Foreign key metadata is unavailable; cannot resolve " + table + "." + column);
            }
        }
        return Optional.ofNullable(current.get(key(table, column)));
    }

    private String key(String table, String column) {
        return table.toLowerCase(Locale.ROOT) + "." + column.toLowerCase(Locale.ROOT);
    }

}
