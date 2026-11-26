package cn.zhuangjimao.catalog;

import cn.zhuangjimao.compatibility.CompatibilityResult;
import cn.zhuangjimao.compatibility.CompatibilityService;
import cn.zhuangjimao.compatibility.CompatibilityStatus;
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
import static cn.zhuangjimao.catalog.HardwareParameters.sourcedField;
import static cn.zhuangjimao.catalog.HardwareParameters.sourcedNumber;
import static cn.zhuangjimao.catalog.HardwareParameters.sourcedSsdCapacity;

@Service
class RecommendationService {
    private final ProductMapper products;
    private final PerformanceReferences references;
    private final CompatibilityService compatibility;
    private final DiyQuotation quotation;
    private final ObjectMapper json;

    RecommendationService(ProductMapper products, PerformanceReferences references, CompatibilityService compatibility,
                          DiyQuotation quotation, ObjectMapper json) {
        this.products = products;
        this.references = references;
        this.compatibility = compatibility;
        this.quotation = quotation;
        this.json = json;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Result recommend(RecommendationScenario scenario, BigDecimal budget) throws Exception {
        var pools = new EnumMap<HardwareCategory, List<RecommendationSearch.Candidate>>(HardwareCategory.class);
        for (var category : HardwareCategory.values()) pools.put(category, new ArrayList<>());
        var lowestStockedPrices = new EnumMap<HardwareCategory, BigDecimal>(HardwareCategory.class);
        var required = EnumSet.allOf(HardwareCategory.class);
        if (!scenario.requiresGpu()) required.remove(GPU);

        boolean missingData = false;
        boolean unknownOfficeGraphics = false;
        for (var product : products.findOnSale(null, "", null)) {
            if (product.stock() < 1) continue;
            var category = HardwareCategory.valueOf(product.category());
            lowestStockedPrices.merge(category, product.price(), BigDecimal::min);
            var spec = products.findSpecification(product.id());
            var parameters = spec == null ? null : json.readTree(spec.parameters());
            if (parameters == null || !parameters.isArray()) {
                missingData = true;
                if (category == CPU && !scenario.requiresGpu()) unknownOfficeGraphics = true;
                continue;
            }

            boolean officeCpu = category == CPU && !scenario.requiresGpu();
            var graphics = officeCpu ? sourcedField(parameters, "integratedGraphics", CPU) : null;
            boolean integrated = graphics != null && graphics.path("value").asBoolean();
            var value = scenarioValue(scenario, product, category, parameters);
            if (value == null) {
                missingData = true;
                if (officeCpu && (graphics == null || integrated)) unknownOfficeGraphics = true;
                continue;
            }
            int maximum = category == MEMORY ? Math.min(DiyMemory.MAX_QUANTITY, product.stock()) : 1;
            for (int quantity = 1; quantity <= maximum; quantity++) {
                var units = BigDecimal.valueOf(quantity);
                var totalValue = value.multiply(units);
                if (!scenario.meetsMinimum(category, totalValue)) continue;
                if (officeCpu && graphics == null) unknownOfficeGraphics = true;
                var score = category == MEMORY || category == SSD ? RecommendationRules.capacity(category, totalValue) : totalValue;
                pools.get(category).add(new RecommendationSearch.Candidate(category, product.id(), quantity,
                        product.price().multiply(units), scenario.contribution(category, score), parameters, integrated, score));
            }
        }

        if (!lowestStockedPrices.keySet().containsAll(required))
            return failure("NO_AVAILABLE_PRODUCT", "当前有必需类别无在售库存，请稍后重试。", scenario, budget);
        if (required.stream().anyMatch(category -> pools.get(category).isEmpty()))
            return insufficientData(scenario, budget);

        if (!scenario.requiresGpu()) {
            if (!lowestStockedPrices.containsKey(GPU) && pools.get(CPU).stream().noneMatch(RecommendationSearch.Candidate::integrated)) {
                if (unknownOfficeGraphics) return insufficientData(scenario, budget);
                return failure("NO_AVAILABLE_PRODUCT", "当前配置候选无可用核显，独显缺货，请稍后重试。", scenario, budget);
            }
            pools.get(GPU).add(new RecommendationSearch.Candidate(
                    GPU, 0, 1, BigDecimal.ZERO, BigDecimal.ZERO, null, false, BigDecimal.ZERO));
        }
        var priceLowerBound = required.stream().map(lowestStockedPrices::get).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (priceLowerBound.compareTo(budget) > 0)
            return failure("BUDGET_INSUFFICIENT", "当前预算低于必需配件的价格下界，请提高预算。", scenario, budget);

        var checks = new HashMap<RecommendationSearch.Plan, CompatibilityResult>();
        for (var plan : RecommendationSearch.search(pools, budget)) {
            var check = compatibility.check(0, plan.selections(), plan.integratedGraphicsCpuId(), plan.memoryQuantity());
            if (check.status() == CompatibilityStatus.ERROR) throw new IllegalStateException("Recommendation compatibility check failed");
            if (!check.corePassed() || !check.complete()) return revalidation(scenario, budget);
            checks.put(plan, check);
        }
        var selected = RecommendationSearch.choose(List.copyOf(checks.keySet()));
        if (selected.isEmpty()) return explainFailure(scenario, budget, pools, missingData || unknownOfficeGraphics);

        var plan = selected.get();
        var quote = quotation.quote(0, plan.selections(), plan.memoryQuantity());
        if (quote.total() == null || quote.total().compareTo(plan.total()) != 0)
            return revalidation(scenario, budget);
        var scores = new Scores(plan.parts().get(CPU).score(), scenario.requiresGpu() ? plan.parts().get(GPU).score() : null,
                plan.parts().get(MEMORY).score(), plan.parts().get(SSD).score());
        return new Result("SUCCESS", "已找到预算内的" + scenario.label + "方案。", scenario, scenario.version,
                budget, quote.total(), budget.subtract(quote.total()), plan.utility(), scores, scenario.weights(), quote.items(),
                plan.integratedGraphicsCpuId(), plan.memoryQuantity(), checks.get(plan), List.of(
                scenario.minimumExplanation(), scenario.weightExplanation(),
                "在本次有效候选最高分相差 1 分以内优先低价，同价按固定商品 ID 排序。",
                "CPU 和独显参考分采用固定 Blender 渲染基准；容量分用于预算推荐，分数不代表游戏 FPS 或整机实测性能。",
                "方案只反映当前报价与库存，不锁价、不预占；转入 DIY 后重新检测，修改后旧推荐解释失效。"));
    }

    private BigDecimal scenarioValue(RecommendationScenario scenario, Product product, HardwareCategory category,
                                     JsonNode parameters) throws Exception {
        if (category == CPU || category == GPU && scenario.requiresGpu()) {
            var reference = references.read(product.id());
            return reference != null && reference.state().equals("VALID") ? reference.score() : null;
        }
        return switch (category) {
            case MEMORY -> sourcedNumber(parameters, MEMORY, "totalCapacity", "GB");
            case SSD -> sourcedSsdCapacity(parameters);
            default -> BigDecimal.ZERO;
        };
    }

    private static Result explainFailure(RecommendationScenario scenario, BigDecimal budget,
                                         Map<HardwareCategory, List<RecommendationSearch.Candidate>> pools, boolean missingData) {
        return switch (RecommendationSearch.inspectFailure(pools, budget)) {
            case ABOVE_BUDGET -> missingData ? insufficientData(scenario, budget)
                    : failure("BUDGET_INSUFFICIENT", "已核验的兼容方案均超出当前预算，请提高预算。", scenario, budget);
            case INCOMPATIBLE -> missingData ? insufficientData(scenario, budget)
                    : failure("NO_COMPATIBLE_COMBINATION", "当前达到用途门槛的候选范围内无兼容组合。", scenario, budget);
            case UNVERIFIED -> insufficientData(scenario, budget);
            case WITHIN_BUDGET, NOT_SEARCHED -> failure("NO_RESULT_FOUND",
                    "当前搜索未找到预算内的完整" + scenario.label + "方案，可调整预算后重试。", scenario, budget);
        };
    }

    private static Result insufficientData(RecommendationScenario scenario, BigDecimal budget) {
        return failure("INSUFFICIENT_DATA", "当前商品缺少已核验的评分或参数，或可核验的商品未达到" + scenario.label + "门槛。", scenario, budget);
    }

    private static Result revalidation(RecommendationScenario scenario, BigDecimal budget) {
        return failure("REVALIDATION_REQUIRED", "商品状态或兼容检查结果已变化，请重新推荐。", scenario, budget);
    }

    static Result failure(String status, String message, RecommendationScenario scenario, BigDecimal budget) {
        return new Result(status, message, scenario, scenario == null ? null : scenario.version,
                budget, null, null, null, null, null, List.of(), null, 1, null, List.of());
    }

    record Scores(@JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal cpu,
                  @JsonInclude(JsonInclude.Include.NON_NULL) @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal gpu,
                  @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal memory,
                  @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal ssd) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    record Result(String status, String message, RecommendationScenario scenario, String scoringVersion,
                  @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal budget,
                  @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal total,
                  @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal remaining,
                  @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal score, Scores scores, RecommendationScenario.Weights weights,
                  List<DiyQuotation.Item> items,
                  Long integratedGraphicsCpuId, int memoryQuantity, CompatibilityResult compatibility, List<String> reasons) {}
}
