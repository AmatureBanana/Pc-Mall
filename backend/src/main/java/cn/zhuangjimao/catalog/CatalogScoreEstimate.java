package cn.zhuangjimao.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/** Display-only estimates from current, sourced specifications. */
final class CatalogScoreEstimate {
    private CatalogScoreEstimate() {}

    static ProductPerformanceTags.Tag tag(String category, Map<String,JsonNode> fields, String referenceNote) {
        return switch (category) {
            case "CPU" -> {
                var multiCore = number(fields,"geekbench7MultiCore","分");
                if (multiCore != null) {
                    yield scaled(multiCore,25000,"CPU 多核估算分","按 Geekbench 7 多核成绩估算，25000 分对应满分。",referenceNote);
                }
                yield scaled(number(fields,"cores","核"),32,"CPU 核心数估算分","仅按标称核心数估算，32 核对应满分。",referenceNote);
            }
            case "GPU" -> scaled(number(fields,"vram","GB"),32,"显存估算分","仅按标称显存容量估算，32 GB 对应满分，未计入图形算力。",referenceNote);
            case "MEMORY" -> capacity(number(fields,"totalCapacity","GB"),true,referenceNote);
            case "SSD" -> capacity(number(fields,"capacity","GB"),false,referenceNote);
            default -> null;
        };
    }

    private static ProductPerformanceTags.Tag scaled(JsonNode field, int fullScale, String label, String note, String referenceNote) {
        if (field == null) return null;
        var score = field.path("value").decimalValue().multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(fullScale),1,RoundingMode.HALF_UP).min(BigDecimal.valueOf(100));
        return estimate(field,score,label,note,referenceNote);
    }

    private static ProductPerformanceTags.Tag capacity(JsonNode field, boolean memory, String referenceNote) {
        if (field == null) return null;
        var score = PerformanceScores.capacityEstimate(field.path("value").decimalValue(),memory);
        return estimate(field,score,"容量估算分", "按标称容量在现有容量档位间估算，未计入"
                + (memory ? "速率和时序。" : "顺序读写速度。"),referenceNote);
    }

    private static ProductPerformanceTags.Tag estimate(JsonNode field, BigDecimal score, String label, String note, String referenceNote) {
        return new ProductPerformanceTags.Tag("score",label+" "+score.setScale(1,RoundingMode.HALF_UP).toPlainString()+"分",
                "ESTIMATE",field.path("sourceUrl").asText(),note+" "+referenceNote);
    }

    private static JsonNode number(Map<String,JsonNode> fields, String code, String unit) {
        var field = fields.get(code);
        return field != null && HardwareParameters.httpUrl(field.path("sourceUrl").asText())
                && unit.equals(field.path("unit").asText()) && field.path("value").isNumber()
                && field.path("value").decimalValue().signum() > 0 ? field : null;
    }
}
