package cn.zhuangjimao.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Evaluates the current, sourced catalog parameters for a capacity-priority score. */
final class CapacityPerformance {
    private static final Map<String, Set<String>> PROFILE_FIELDS = Map.of(
            "MEMORY",Set.of("dataRate","timingDataRate","casLatency","trcd","trp"),
            "SSD",Set.of("sequentialRead","sequentialWrite"));

    private CapacityPerformance() {}

    static boolean supports(String category) { return PROFILE_FIELDS.containsKey(category); }

    static boolean hasProfileValues(String category, JsonNode parameters) {
        var codes = PROFILE_FIELDS.get(category);
        if (codes == null) return false;
        for (var field : parameters) {
            if (codes.contains(field.path("code").asText()) && field.hasNonNull("value")) return true;
        }
        return false;
    }

    static void removeProfileFields(String category, ArrayNode parameters) {
        var codes = PROFILE_FIELDS.get(category);
        if (codes == null) return;
        for (int i = parameters.size() - 1; i >= 0; i--) {
            if (codes.contains(parameters.get(i).path("code").asText())) parameters.remove(i);
        }
    }

    static PerformanceReferences.Score evaluate(String category, JsonNode parameters) {
        Map<String, JsonNode> fields = new HashMap<>();
        parameters.forEach(field -> fields.put(field.path("code").asText(),field));
        return switch (category) {
            case "MEMORY" -> memory(fields);
            case "SSD" -> ssd(fields);
            default -> throw new IllegalArgumentException("不支持容量优先评分的类别：" + category);
        };
    }

    private static PerformanceReferences.Score memory(Map<String, JsonNode> fields) {
        var capacity = number(fields,"totalCapacity","GB");
        var generation = fields.get("generation");
        var rate = number(fields,"dataRate","MT/s");
        var timingRate = number(fields,"timingDataRate","MT/s");
        var cl = cycles(fields,"casLatency");
        var trcd = cycles(fields,"trcd");
        var trp = cycles(fields,"trp");
        if (capacity == null || !sourced(generation) || !generation.path("value").isTextual()
                || rate == null || timingRate == null || cl == null || trcd == null || trp == null) {
            return unavailable("MISSING","待核验内存容量、DDR代际、标称速率或 CL、tRCD、tRP 时序及其来源。");
        }
        if (rate.path("value").decimalValue().compareTo(timingRate.path("value").decimalValue()) != 0) {
            return unavailable("INVALID","时序对应的速率与标称速率不同，请核验同一档位的参数。");
        }
        var result = PerformanceScores.memory(capacity.path("value").decimalValue(),generation.path("value").asText(),
                rate.path("value").decimalValue(),cl.path("value").intValue(),trcd.path("value").intValue(),trp.path("value").intValue());
        if (result == null) return unavailable("INVALID","当前评分版本不支持此内存容量或DDR代际。");
        return valid(result, "按商品标称内存容量优先，同容量比较标称速率和时序。标称档位的评分不代表整机实测速率。",
                rate,capacity,generation,timingRate,cl,trcd,trp);
    }

    private static PerformanceReferences.Score ssd(Map<String, JsonNode> fields) {
        var capacity = number(fields,"capacity","GB");
        var original = fields.get("originalCapacity");
        var read = number(fields,"sequentialRead","MB/s");
        var write = number(fields,"sequentialWrite","MB/s");
        if (capacity == null || !positiveNumber(original) || read == null || write == null) {
            return unavailable("MISSING","待核验SSD容量、原始容量单位或顺序读写速度及其来源。");
        }
        var gb = capacity.path("value").decimalValue();
        try {
            if (PerformanceScores.ssdGigabytes(original.path("value").decimalValue(),original.path("unit").asText()).compareTo(gb) != 0) {
                return unavailable("INVALID","SSD原始容量与规范GB换算不一致。");
            }
        } catch (IllegalArgumentException e) {
            return unavailable("INVALID","SSD原始容量单位无效。");
        }
        var result = PerformanceScores.ssd(gb,read.path("value").decimalValue(),write.path("value").decimalValue());
        if (result == null) return unavailable("INVALID","当前评分版本不支持此SSD容量。");
        return valid(result,"按十进制标称容量优先，同容量比较厂商标称顺序读写。随机读写和持续写入未计入。",
                read,capacity,original,write);
    }

    private static JsonNode cycles(Map<String, JsonNode> fields, String code) {
        var field = number(fields,code,"周期");
        return field != null && field.path("value").isIntegralNumber() && field.path("value").canConvertToInt() ? field : null;
    }

    private static JsonNode number(Map<String, JsonNode> fields, String code, String unit) {
        var field = fields.get(code);
        return positiveNumber(field) && field.path("unit").asText().equals(unit) ? field : null;
    }

    private static boolean positiveNumber(JsonNode field) {
        return sourced(field) && field.path("value").isNumber() && field.path("value").decimalValue().signum() > 0;
    }

    private static boolean sourced(JsonNode field) {
        return field != null && HardwareParameters.httpUrl(field.path("sourceUrl").asText());
    }

    private static PerformanceReferences.Score valid(PerformanceScores.CapacityRating result, String note, JsonNode primary, JsonNode... fields) {
        var reference = JsonNodeFactory.instance.objectNode().put("sourceUrl",primary.path("sourceUrl").asText());
        var inputs = reference.putArray("inputs").add(primary);
        for (var field : fields) inputs.add(field);
        return new PerformanceReferences.Score("VALID",result.score(),PerformanceScores.CAPACITY_VERSION,reference,note,result.performanceScore());
    }

    private static PerformanceReferences.Score unavailable(String state, String note) {
        return new PerformanceReferences.Score(state,null,PerformanceScores.CAPACITY_VERSION,null,note,null);
    }
}
