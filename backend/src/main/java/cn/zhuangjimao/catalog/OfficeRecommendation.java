package cn.zhuangjimao.catalog;

import cn.zhuangjimao.compatibility.CompatibilityResult;
import cn.zhuangjimao.compatibility.CompatibilityService;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;
import static cn.zhuangjimao.catalog.HardwareCategory.*;

@Service
class OfficeRecommendation {
    private final ProductMapper products;
    private final PerformanceReferences references;
    private final CompatibilityService compatibility;
    private final DiyQuotation quotation;
    private final ObjectMapper json;

    OfficeRecommendation(ProductMapper products, PerformanceReferences references, CompatibilityService compatibility,
                         DiyQuotation quotation, ObjectMapper json) {
        this.products = products; this.references = references; this.compatibility = compatibility;
        this.quotation = quotation; this.json = json;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Result recommend(BigDecimal budget) throws Exception {
        var pools = new EnumMap<HardwareCategory, List<RecommendationSearch.Candidate>>(HardwareCategory.class);
        for (var category : HardwareCategory.values()) pools.put(category, new ArrayList<>());
        var scores = new HashMap<Long, BigDecimal>();
        var capacities = new HashMap<Long, BigDecimal>();
        var available = EnumSet.noneOf(HardwareCategory.class);
        for (var product : products.findOnSale(null, "")) {
            if (product.stock() < 1) continue;
            var category = HardwareCategory.valueOf(product.category());
            available.add(category);
            var spec = products.findSpecification(product.id());
            if (spec == null) continue;
            var parameters = json.readTree(spec.parameters());
            if (!parameters.isArray()) continue;
            BigDecimal score = BigDecimal.ZERO;
            BigDecimal capacity = null;
            if (category == CPU) {
                var reference = references.read(product.id());
                if (reference == null || !reference.state().equals("VALID") || reference.score() == null) continue;
                score = reference.score();
                if (score.compareTo(new BigDecimal("25")) < 0) continue;
            } else if (category == MEMORY || category == SSD) {
                var field = field(parameters, category == MEMORY ? "totalCapacity" : "capacity", category);
                if (field == null || !field.path("unit").asText().equals("GB")) continue;
                capacity = field.path("value").decimalValue();
                if (category == SSD) {
                    var original = field(parameters, "originalCapacity", SSD);
                    if (original == null || !Set.of("GB", "TB", "GiB", "TiB").contains(original.path("unit").asText())
                            || PerformanceScores.ssdGigabytes(original.path("value").decimalValue(), original.path("unit").asText()).compareTo(capacity) != 0) continue;
                }
                capacities.put(product.id(), capacity);
            }
            scores.put(product.id(), score);
            var graphics = field(parameters, "integratedGraphics", CPU);
            boolean integrated = category == CPU && graphics != null && graphics.path("value").asBoolean();
            int maximum = category == MEMORY ? Math.min(DiyMemory.MAX_QUANTITY, product.stock()) : 1;
            for (int quantity = 1; quantity <= maximum; quantity++) {
                BigDecimal contribution = category == CPU ? score.multiply(new BigDecimal("0.50")) : BigDecimal.ZERO;
                if (capacity != null) {
                    var totalCapacity = capacity.multiply(BigDecimal.valueOf(quantity));
                    if (totalCapacity.compareTo(BigDecimal.valueOf(category == MEMORY ? 8 : 256)) < 0) continue;
                    contribution = RecommendationRules.capacity(category, totalCapacity).multiply(new BigDecimal(category == MEMORY ? "0.30" : "0.20"));
                }
                pools.get(category).add(new RecommendationSearch.Candidate(category, product.id(), quantity,
                        product.price().multiply(BigDecimal.valueOf(quantity)), contribution, parameters, integrated));
            }
        }
        pools.get(GPU).add(new RecommendationSearch.Candidate(GPU, 0, 1, BigDecimal.ZERO, BigDecimal.ZERO, null, false));
        for (var category : List.of(CPU, MOTHERBOARD, MEMORY, SSD, PSU, CASE, COOLER)) {
            if (!available.contains(category)) return failure("NO_AVAILABLE_PRODUCT", "当前有必需类别无在售库存，请稍后重试。", budget);
            if (pools.get(category).isEmpty()) return failure("INSUFFICIENT_DATA", "当前商品的评分、容量或办公门槛不足以生成可靠方案。", budget);
        }
        var checks = new HashMap<RecommendationSearch.Plan, CompatibilityResult>();
        for (var plan : RecommendationSearch.search(pools, budget)) {
            var check = compatibility.check(0, plan.selections(), plan.integratedGraphicsCpuId(), plan.memoryQuantity());
            if (check.corePassed() && check.complete()) checks.put(plan, check);
        }
        var selected = RecommendationSearch.choose(List.copyOf(checks.keySet()));
        if (selected.isEmpty()) return failure("NO_RESULT_FOUND", "当前搜索未找到预算内的完整办公方案，可调整预算后重试；这不表示全局无解。", budget);
        var plan = selected.get();
        var quote = quotation.quote(0, plan.selections(), plan.memoryQuantity());
        if (quote.total() == null || quote.total().compareTo(budget) > 0)
            return failure("REVALIDATION_REQUIRED", "商品状态已变化，请重新推荐。", budget);
        var cpu = scores.get(plan.parts().get(CPU).productId());
        var memory = RecommendationRules.capacity(MEMORY, capacities.get(plan.parts().get(MEMORY).productId()).multiply(BigDecimal.valueOf(plan.memoryQuantity())));
        var ssd = RecommendationRules.capacity(SSD, capacities.get(plan.parts().get(SSD).productId()));
        return new Result("SUCCESS", "已找到预算内的办公方案。", "OFFICE", "office-v1", budget, quote.total(), budget.subtract(quote.total()),
                RecommendationRules.office(cpu, memory, ssd), new Scores(cpu, memory, ssd), quote.items(),
                plan.integratedGraphicsCpuId(), plan.memoryQuantity(), checks.get(plan), List.of(
                "办公门槛：CPU 参考分至少 25，内存至少 8 GB，SSD 至少 256 GB。",
                "办公总分 = CPU × 50% + 内存容量分 × 30% + SSD 容量分 × 20%，满分 100；与商城综合分口径不同。",
                "在本次有效候选最高分相差 1 分以内优先低价，同价按固定商品 ID 排序。",
                "CPU 为固定 Blender 渲染参考；容量分不代表游戏 FPS、静音表现或整机实测性能。",
                "方案只反映当前报价与库存，不锁价、不预占；转入 DIY 后重新检测，修改后旧推荐解释失效。"));
    }

    private static JsonNode field(JsonNode parameters, String code, HardwareCategory category) {
        JsonNode found = null;
        for (var field : parameters) if (field.path("code").asText().equals(code)) {
            if (found != null) return null;
            found = field;
        }
        return found != null && HardwareParameters.httpUrl(found.path("sourceUrl").asText())
                && HardwareParameters.isValidValue(category, code, found.get("value")) ? found : null;
    }

    static Result failure(String status, String message, BigDecimal budget) {
        return new Result(status, message, "OFFICE", "office-v1", budget, null, null, null, null, List.of(), null, 1, null, List.of());
    }
    record Scores(@JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal cpu,
                  @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal memory,
                  @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal ssd) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    record Result(String status, String message, String scenario, String scoringVersion,
                  @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal budget,
                  @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal total,
                  @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal remaining,
                  @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal score, Scores scores, List<DiyQuotation.Item> items,
                  Long integratedGraphicsCpuId, int memoryQuantity, CompatibilityResult compatibility, List<String> reasons) {}
}
