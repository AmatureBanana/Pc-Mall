package cn.zhuangjimao.catalog;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.List;

import static cn.zhuangjimao.catalog.HardwareCategory.*;
import static cn.zhuangjimao.catalog.HardwareParameters.sourcedField;
import static cn.zhuangjimao.catalog.HardwareParameters.sourcedNumber;
import static cn.zhuangjimao.catalog.HardwareParameters.sourcedSsdCapacity;

@Service
class DiyPerformance {
    private static final BigDecimal OFFICE = BigDecimal.valueOf(40);
    private static final BigDecimal GAMING = BigDecimal.valueOf(70);
    private static final BigDecimal CREATION = BigDecimal.valueOf(90);
    private static final BigDecimal FULL = BigDecimal.valueOf(100);

    private final ProductMapper products;
    private final PerformanceReferences references;
    private final ObjectMapper json;

    DiyPerformance(ProductMapper products, PerformanceReferences references, ObjectMapper json) {
        this.products = products;
        this.references = references;
        this.json = json;
    }

    Assessment assess(List<DiyQuotation.Item> items, boolean integrated) throws Exception {
        if (items.size() != HardwareCategory.values().length - (integrated ? 1 : 0)
                || items.stream().anyMatch(item -> !item.state().equals("VALID"))) return null;
        var parts = new EnumMap<HardwareCategory, DiyQuotation.Item>(HardwareCategory.class);
        items.forEach(item -> parts.put(item.category(), item));
        if (integrated == parts.containsKey(GPU)) return null;

        var cpu = parameters(parts.get(CPU));
        var memory = parameters(parts.get(MEMORY));
        var ssd = parameters(parts.get(SSD));
        var scores = new EnumMap<Metric, BigDecimal>(Metric.class);
        scores.put(Metric.SINGLE, normalize(sourcedNumber(cpu, CPU, "geekbench7SingleCore", "分"), 1800, 1900, 2400, 3200));
        scores.put(Metric.MULTI, normalize(sourcedNumber(cpu, CPU, "geekbench7MultiCore", "分"), 6500, 9000, 15000, 25000));
        if (integrated) {
            var supported = sourcedField(cpu, "integratedGraphics", CPU);
            scores.put(Metric.GPU, supported != null && supported.path("value").asBoolean() ? OFFICE : null);
            scores.put(Metric.VRAM, OFFICE);
        } else {
            var gpu = parts.get(GPU);
            var reference = references.read(gpu.productId());
            var raw = reference != null && reference.state().equals("VALID")
                    ? new BigDecimal(reference.reference().path("rawScore").asText()) : null;
            scores.put(Metric.GPU, normalize(raw, 500, 1400, 4500, 8000));
            scores.put(Metric.VRAM, normalize(sourcedNumber(parameters(gpu), GPU, "vram", "GB"), 0, 8, 12, 24));
        }
        var memorySize = sourcedNumber(memory, MEMORY, "totalCapacity", "GB");
        scores.put(Metric.MEMORY, normalize(memorySize == null ? null
                : memorySize.multiply(BigDecimal.valueOf(parts.get(MEMORY).quantity())), 8, 16, 32, 64));
        scores.put(Metric.STORAGE, normalize(sourcedSsdCapacity(ssd), 256, 512, 1000, 2000));
        scores.put(Metric.READ, normalize(sourcedNumber(ssd, SSD, "sequentialRead", "MB/s"), 400, 1000, 3000, 7000));
        scores.put(Metric.WRITE, normalize(sourcedNumber(ssd, SSD, "sequentialWrite", "MB/s"), 250, 600, 2000, 6000));

        var missing = scores.entrySet().stream().filter(entry -> entry.getValue() == null)
                .map(entry -> entry.getKey().label).toList();
        if (!missing.isEmpty()) return new Assessment(null, null, null, List.of(), missing);

        var score = scores.values().stream().min(BigDecimal::compareTo).orElseThrow();
        var scenario = score.compareTo(CREATION) >= 0 ? "CREATION" : score.compareTo(GAMING) >= 0 ? "GAMING"
                : score.compareTo(OFFICE) >= 0 ? "OFFICE" : "BELOW_OFFICE";
        var bottlenecks = scores.entrySet().stream().filter(entry -> entry.getValue().compareTo(score) == 0)
                .filter(entry -> !integrated || entry.getKey() != Metric.VRAM).map(entry -> entry.getKey().label).toList();
        var memoryPerformance = CapacityPerformance.evaluate(MEMORY.name(), memory).performanceScore();
        return new Assessment(score, weighted(scores, memoryPerformance), scenario, bottlenecks, List.of());
    }

    private JsonNode parameters(DiyQuotation.Item item) throws Exception {
        var spec = products.findSpecification(item.productId());
        return spec == null ? json.createArrayNode() : json.readTree(spec.parameters());
    }

    private static BigDecimal normalize(BigDecimal value, int office, int gaming, int creation, int full) {
        if (value == null) return null;
        int[] inputs = {0, office, gaming, creation, full};
        BigDecimal[] outputs = {office == 0 ? OFFICE : BigDecimal.ZERO, OFFICE, GAMING, CREATION, FULL};
        for (int index = 1; index < inputs.length; index++) {
            if (inputs[index] == inputs[index - 1] || value.compareTo(BigDecimal.valueOf(inputs[index])) > 0) continue;
            return outputs[index - 1].add(value.subtract(BigDecimal.valueOf(inputs[index - 1]))
                    .multiply(outputs[index].subtract(outputs[index - 1]))
                    .divide(BigDecimal.valueOf(inputs[index] - inputs[index - 1]), 6, RoundingMode.DOWN));
        }
        return FULL;
    }

    private static BigDecimal weighted(EnumMap<Metric, BigDecimal> scores, BigDecimal memoryPerformance) {
        if (memoryPerformance == null) return null;
        var memory = weight(scores.get(Metric.MEMORY), "0.75").add(weight(memoryPerformance, "0.25"));
        var ssd = scores.get(Metric.STORAGE).add(scores.get(Metric.READ).min(scores.get(Metric.WRITE)))
                .divide(BigDecimal.valueOf(2));
        return weight(scores.get(Metric.SINGLE), "0.15").add(weight(scores.get(Metric.MULTI), "0.20"))
                .add(weight(scores.get(Metric.GPU), "0.30")).add(weight(memory, "0.15"))
                .add(weight(scores.get(Metric.VRAM), "0.10")).add(weight(ssd, "0.10")).setScale(6, RoundingMode.HALF_UP);
    }

    private static BigDecimal weight(BigDecimal score, String factor) { return score.multiply(new BigDecimal(factor)); }

    private enum Metric {
        SINGLE("CPU 单核"), MULTI("CPU 多核"), GPU("显卡性能"), VRAM("独立显存"), MEMORY("内存容量"),
        STORAGE("SSD 容量"), READ("SSD 顺序读取"), WRITE("SSD 顺序写入");

        final String label;
        Metric(String label) { this.label = label; }
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    record Assessment(@JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal score,
                      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal weightedScore,
                      String scenario, List<String> bottlenecks, List<String> missing) {}
}
