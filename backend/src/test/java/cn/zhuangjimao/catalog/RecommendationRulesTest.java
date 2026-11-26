package cn.zhuangjimao.catalog;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.EnumMap;
import java.util.ArrayList;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;
import static cn.zhuangjimao.catalog.HardwareCategory.*;

class RecommendationRulesTest {
    @Test void officeUsesCapacityNodesAndHundredPointWeightedScore() {
        assertThat(RecommendationRules.capacity(HardwareCategory.MEMORY, new BigDecimal("8"))).isEqualByComparingTo("50");
        assertThat(RecommendationRules.capacity(HardwareCategory.MEMORY, new BigDecimal("24"))).isEqualByComparingTo("90");
        assertThat(RecommendationRules.capacity(HardwareCategory.SSD, new BigDecimal("384"))).isEqualByComparingTo("57.5");
        assertThat(RecommendationRules.capacity(HardwareCategory.SSD, new BigDecimal("2000"))).isEqualByComparingTo("100");
    }

    @Test void scenarioWeightsUseTheSameFixedScoresAndDifferentContributions() {
        var scores = Map.of(CPU, new BigDecimal("60"), GPU, new BigDecimal("50"),
                MEMORY, new BigDecimal("80"), SSD, new BigDecimal("75"));
        var expected = Map.of(RecommendationScenario.OFFICE, "69", RecommendationScenario.GAMING, "57.25",
                RecommendationScenario.CREATION, "62");
        expected.forEach((scenario, total) -> {
            var actual = scores.entrySet().stream().map(item -> scenario.contribution(item.getKey(), item.getValue()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            assertThat(actual).as(scenario.name()).isEqualByComparingTo(total);
        });
        assertThat(PerformanceScores.score("CPU", new BigDecimal("110"))).isEqualByComparingTo("35");
        assertThat(PerformanceScores.score("GPU", new BigDecimal("1100"))).isEqualByComparingTo("35");
        assertThat(PerformanceScores.score("GPU", new BigDecimal("1400"))).isEqualByComparingTo("40");
    }

    @Test void scenarioMinimumsIncludeTheBoundaryAndExcludeValuesJustBelowIt() {
        var minimums = Map.of(RecommendationScenario.OFFICE, Map.of(CPU, 25, MEMORY, 8, SSD, 256),
                RecommendationScenario.GAMING, Map.of(CPU, 35, GPU, 40, MEMORY, 16, SSD, 512),
                RecommendationScenario.CREATION, Map.of(CPU, 35, GPU, 35, MEMORY, 16, SSD, 512));
        minimums.forEach((scenario, thresholds) -> thresholds.forEach((category, minimum) -> {
            var boundary = BigDecimal.valueOf(minimum);
            assertThat(scenario.meetsMinimum(category, boundary)).isTrue();
            assertThat(scenario.meetsMinimum(category, boundary.subtract(new BigDecimal("0.01")))).isFalse();
        }));
        assertThat(RecommendationScenario.OFFICE.requiresGpu()).isFalse();
        assertThat(RecommendationScenario.GAMING.requiresGpu()).isTrue();
        assertThat(RecommendationScenario.CREATION.requiresGpu()).isTrue();
    }

    @Test void closeScoresPreferPriceAndThenNumericProductIdsIndependentOfInputOrder() {
        var highest = plan(1, "1000", "80");
        var cheaperWithinOne = plan(10, "900", "79");
        var samePriceLowerId = plan(2, "900", "79.1");
        var tooWeak = plan(3, "800", "78.999999");
        assertThat(RecommendationSearch.choose(List.of(highest, cheaperWithinOne, tooWeak, samePriceLowerId))).contains(samePriceLowerId);
        assertThat(RecommendationSearch.choose(List.of(samePriceLowerId, tooWeak, cheaperWithinOne, highest))).contains(samePriceLowerId);
        assertThat(RecommendationSearch.choose(List.of())).isEmpty();
    }

    @Test void everyProductIdBreaksTheTieBeforeMemoryQuantity() {
        var oneModuleWithHigherSsdId = tiedPlan(1, 20);
        var twoModulesWithLowerSsdId = tiedPlan(2, 10);
        assertThat(RecommendationSearch.choose(List.of(oneModuleWithHigherSsdId, twoModulesWithLowerSsdId)))
                .contains(twoModulesWithLowerSsdId);
    }

    private RecommendationSearch.Plan tiedPlan(int memoryQuantity, long ssdId) {
        BigDecimal zero = BigDecimal.ZERO;
        var memory = new RecommendationSearch.Candidate(MEMORY, 5, memoryQuantity, zero, zero, null, false, zero);
        var ssd = new RecommendationSearch.Candidate(SSD, ssdId, 1, zero, zero, null, false, zero);
        return new RecommendationSearch.Plan(Map.of(MEMORY, memory, SSD, ssd), new BigDecimal("900"), new BigDecimal("80"));
    }

    private RecommendationSearch.Plan plan(long id, String price, String score) {
        var candidate = new RecommendationSearch.Candidate(HardwareCategory.CPU, id, 1, new BigDecimal(price),
                new BigDecimal(score), null, true, new BigDecimal(score));
        return new RecommendationSearch.Plan(Map.of(HardwareCategory.CPU, candidate), new BigDecimal(price), new BigDecimal(score));
    }

    @Test void largeCandidatePoolsStayBoundedAndKeepTheAffordableLowScorePath() throws Exception {
        var pools = new EnumMap<HardwareCategory, List<RecommendationSearch.Candidate>>(HardwareCategory.class);
        var json = new ObjectMapper();
        var parameters = Map.of(
                HardwareCategory.CPU, fields(json, "{\"socket\":\"AM5\",\"tdp\":100}"),
                HardwareCategory.MOTHERBOARD, fields(json, "{\"socket\":\"AM5\",\"memoryGenerations\":[\"DDR5\"],\"memorySlots\":4,\"formFactor\":\"Micro-ATX\",\"ssdSlots\":[{\"formFactor\":\"M.2 2280\",\"interface\":\"PCIe\",\"protocol\":\"NVMe\"}]}"),
                HardwareCategory.MEMORY, fields(json, "{\"generation\":\"DDR5\",\"stickCount\":1}"),
                HardwareCategory.SSD, fields(json, "{\"formFactor\":\"M.2 2280\",\"interface\":\"PCIe\",\"protocol\":\"NVMe\"}"),
                HardwareCategory.COOLER, fields(json, "{\"supportedSockets\":[\"AM5\"],\"height\":150}"),
                HardwareCategory.CASE, fields(json, "{\"supportedMotherboardFormFactors\":[\"Micro-ATX\"],\"maxCoolerHeight\":160}"),
                HardwareCategory.PSU, fields(json, "{\"ratedPower\":300}"));
        for (var category : parameters.keySet()) {
            var choices = new ArrayList<RecommendationSearch.Candidate>();
            for (int index = 1; index <= 20; index++) choices.add(new RecommendationSearch.Candidate(category,
                    category.ordinal() * 100L + index, 1, new BigDecimal(category == HardwareCategory.CPU ? "1000" : "100"),
                    new BigDecimal(category == HardwareCategory.CPU ? "50" : "0"), parameters.get(category), category == HardwareCategory.CPU, BigDecimal.ZERO));
            pools.put(category, choices);
        }
        pools.get(HardwareCategory.CPU).add(new RecommendationSearch.Candidate(HardwareCategory.CPU, 1000, 1,
                new BigDecimal("100"), new BigDecimal("25"), parameters.get(HardwareCategory.CPU), true, BigDecimal.ZERO));
        var incompleteBoard = fields(json, "{\"socket\":\"AM5\",\"formFactor\":\"Micro-ATX\"}");
        for (int index = 0; index < RecommendationSearch.CANDIDATE_LIMIT; index++) {
            pools.get(MOTHERBOARD).add(new RecommendationSearch.Candidate(MOTHERBOARD, 2000 + index, 1,
                    new BigDecimal("50"), BigDecimal.ZERO, incompleteBoard, false, BigDecimal.ZERO));
        }
        pools.put(HardwareCategory.GPU, List.of(new RecommendationSearch.Candidate(
                HardwareCategory.GPU, 0, 1, BigDecimal.ZERO, BigDecimal.ZERO, null, false, BigDecimal.ZERO)));
        var bounded = RecommendationSearch.search(pools, new BigDecimal("10000"));
        assertThat(bounded).hasSize(64);
        for (var category : HardwareCategory.values()) assertThat(bounded.stream().map(item -> item.parts().get(category)).distinct().count()).isLessThanOrEqualTo(15);
        var affordable = RecommendationSearch.choose(RecommendationSearch.search(pools, new BigDecimal("700"))).orElseThrow();
        assertThat(affordable.total()).isEqualByComparingTo("700");
        assertThat(affordable.parts().get(HardwareCategory.CPU).productId()).isEqualTo(1000);
        assertThat(RecommendationSearch.inspectFailure(pools, new BigDecimal("699.99")))
                .isEqualTo(RecommendationSearch.FailureEvidence.NOT_SEARCHED);
    }

    private JsonNode fields(ObjectMapper json, String values) throws Exception {
        var fields = json.createArrayNode();
        json.readTree(values).fields().forEachRemaining(entry -> {
            var field = fields.addObject().put("code", entry.getKey()).put("sourceUrl", "https://example.com/verified-hardware");
            field.set("value", entry.getValue());
        });
        return fields;
    }
}
