package com.furkan.apidebugagent.analysis;

import java.util.Map;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The stage names are part of the contract with {@code debug-console}, so they are pinned here
 * rather than derived from the enum constants.
 */
class AnalysisEventTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    void shouldSerializeStageNamesAsTheContractSpellsThem() {
        assertThat(json(AnalysisEvent.stageStarted(Stage.LOGS))).contains("\"stage\":\"loglar\"");
        assertThat(json(AnalysisEvent.stageStarted(Stage.PARSING))).contains("\"stage\":\"ayrıştırma\"");
        assertThat(json(AnalysisEvent.stageStarted(Stage.DETECTION))).contains("\"stage\":\"tespit\"");
        assertThat(json(AnalysisEvent.stageStarted(Stage.ENRICHMENT))).contains("\"stage\":\"zenginleştirme\"");
    }

    @Test
    void shouldTakeTheKindFromTheStage() {
        assertThat(AnalysisEvent.stageStarted(Stage.DETECTION).kind()).isEqualTo(StageKind.LOCAL);
        assertThat(AnalysisEvent.stageStarted(Stage.ENRICHMENT).kind()).isEqualTo(StageKind.MODEL);
    }

    @Test
    void shouldTreatOnlyReportAndErrorAsTerminal() {
        assertThat(AnalysisEvent.stageStarted(Stage.LOGS).terminal()).isFalse();
        assertThat(AnalysisEvent.stageFinished(Stage.LOGS, 12, Map.of("logLines", 2)).terminal()).isFalse();
        assertThat(AnalysisEvent.error(Stage.LOGS, "Hedef servise ulaşılamıyor.").terminal()).isTrue();
    }

    @Test
    void shouldCarryOnlySummaryNumbersOnAFinishedStage() {
        AnalysisEvent event = AnalysisEvent.stageFinished(Stage.PARSING, 12, Map.of("queries", 214));

        assertThat(json(event)).contains("\"queries\":214").doesNotContain("select");
        assertThat(event.durationMs()).isEqualTo(12);
    }

    private String json(AnalysisEvent event) {
        return jsonMapper.writeValueAsString(event);
    }

}
