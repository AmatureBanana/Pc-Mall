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

class RecommendationRulesTest {
    @Test void officeUsesCapacityNodesAndHundredPointWeightedScore() {
        assertThat(RecommendationRules.capacity(HardwareCategory.MEMORY, new BigDecimal("8"))).isEqualByComparingTo("50");
        assertThat(RecommendationRules.capacity(HardwareCategory.MEMORY, new BigDecimal("24"))).isEqualByComparingTo("90");
        assertThat(RecommendationRules.capacity(HardwareCategory.SSD, new BigDecimal("384"))).isEqualByComparingTo("57.5");
        assertThat(RecommendationRules.capacity(HardwareCategory.SSD, new BigDecimal("2000"))).isEqualByComparingTo("100");
        assertThat(RecommendationRules.office(new BigDecimal("60"), new BigDecimal("80"), new BigDecimal("100")))
                .isEqualByComparingTo("74");
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

    private RecommendationSearch.Plan plan(long id, String price, String score) {
        var candidate = new RecommendationSearch.Candidate(HardwareCategory.CPU, id, 1, new BigDecimal(price), new BigDecimal(score), null, true);
        return new RecommendationSearch.Plan(Map.of(HardwareCategory.CPU, candidate), new BigDecimal(price), new BigDecimal(score));
    }

    @Test void largeCandidatePoolsStayBoundedAndKeepTheAffordableLowScorePath() throws Exception {
        var pools = new EnumMap<HardwareCategory, List<RecommendationSearch.Candidate>>(HardwareCategory.class);
        var json = new ObjectMapper();
        var parameters = Map.of(
                HardwareCategory.CPU, fields(json, "{\"socket\":\"AM5\",\"peakPowerEstimate\":100}"),
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
                    new BigDecimal(category == HardwareCategory.CPU ? "50" : "10"), parameters.get(category), category == HardwareCategory.CPU));
            pools.put(category, choices);
        }
        pools.get(HardwareCategory.CPU).add(new RecommendationSearch.Candidate(HardwareCategory.CPU, 1000, 1,
                new BigDecimal("100"), new BigDecimal("25"), parameters.get(HardwareCategory.CPU), true));
        pools.put(HardwareCategory.GPU, List.of(new RecommendationSearch.Candidate(HardwareCategory.GPU, 0, 1, BigDecimal.ZERO, BigDecimal.ZERO, null, false)));
        var bounded = RecommendationSearch.search(pools, new BigDecimal("10000"));
        assertThat(bounded).hasSize(64);
        for (var category : HardwareCategory.values()) assertThat(bounded.stream().map(item -> item.parts().get(category)).distinct().count()).isLessThanOrEqualTo(15);
        var affordable = RecommendationSearch.choose(RecommendationSearch.search(pools, new BigDecimal("700"))).orElseThrow();
        assertThat(affordable.total()).isEqualByComparingTo("700");
        assertThat(affordable.parts().get(HardwareCategory.CPU).productId()).isEqualTo(1000);
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
