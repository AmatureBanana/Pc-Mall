package cn.zhuangjimao.catalog;

import cn.zhuangjimao.compatibility.CompatibilityRules;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.*;

/** Deterministic, bounded search. Its result is a candidate set, never a proof of global optimality. */
public final class RecommendationSearch {
    public static final int CANDIDATE_LIMIT = 15;
    public static final int BEAM_WIDTH = 64;
    private static final List<HardwareCategory> ORDER = List.of(HardwareCategory.CPU, HardwareCategory.MOTHERBOARD,
            HardwareCategory.MEMORY, HardwareCategory.SSD, HardwareCategory.COOLER, HardwareCategory.CASE,
            HardwareCategory.GPU, HardwareCategory.PSU);

    private RecommendationSearch() {}

    public record Candidate(HardwareCategory category, long productId, int quantity, BigDecimal price,
                            BigDecimal contribution, JsonNode parameters, boolean integrated) {}
    public record Plan(Map<HardwareCategory, Candidate> parts, BigDecimal total, BigDecimal utility) {
        public int memoryQuantity() { return parts.containsKey(HardwareCategory.MEMORY) ? parts.get(HardwareCategory.MEMORY).quantity() : 1; }
        public Map<HardwareCategory, Long> selections() {
            var ids = new EnumMap<HardwareCategory, Long>(HardwareCategory.class);
            parts.forEach((category, candidate) -> { if (candidate.productId() > 0) ids.put(category, candidate.productId()); });
            return ids;
        }
        public Long integratedGraphicsCpuId() {
            var cpu = parts.get(HardwareCategory.CPU);
            return !selections().containsKey(HardwareCategory.GPU) && cpu.integrated() ? cpu.productId() : null;
        }
    }

    private static int identities(Plan a, Plan b) {
        for (var category : HardwareCategory.values()) {
            var left = a.parts().get(category); var right = b.parts().get(category);
            int compared = Long.compare(left == null ? 0 : left.productId(), right == null ? 0 : right.productId());
            if (compared != 0) return compared;
            compared = Integer.compare(left == null ? 0 : left.quantity(), right == null ? 0 : right.quantity());
            if (compared != 0) return compared;
        }
        return 0;
    }
    private static final Comparator<Plan> CHEAP = Comparator.comparing(Plan::total).thenComparing(RecommendationSearch::identities);
    private static final Comparator<Plan> STRONG = Comparator.comparing(Plan::utility).reversed().thenComparing(CHEAP);

    public static Optional<Plan> choose(List<Plan> valid) {
        var maximum = valid.stream().map(Plan::utility).max(BigDecimal::compareTo);
        return maximum.flatMap(max -> valid.stream().filter(plan -> plan.utility().compareTo(max.subtract(BigDecimal.ONE)) >= 0).min(CHEAP));
    }

    public static List<Plan> search(Map<HardwareCategory, List<Candidate>> candidates, BigDecimal budget) {
        var pools = new EnumMap<HardwareCategory, List<Candidate>>(HardwareCategory.class);
        for (var category : ORDER) {
            var pool = candidates.getOrDefault(category, List.of());
            if (pool.isEmpty()) return List.of();
            var cheap = Comparator.comparing(Candidate::price).thenComparingLong(Candidate::productId).thenComparingInt(Candidate::quantity);
            var limited = new LinkedHashSet<Candidate>();
            pool.stream().sorted(cheap).limit(8).forEach(limited::add);
            pool.stream().sorted(Comparator.comparing(Candidate::contribution).reversed().thenComparing(cheap))
                    .filter(item -> !limited.contains(item)).limit(CANDIDATE_LIMIT - limited.size()).forEach(limited::add);
            pools.put(category, List.copyOf(limited));
        }
        List<Plan> beam = List.of(new Plan(Map.of(), BigDecimal.ZERO, BigDecimal.ZERO));
        for (int step = 0; step < ORDER.size(); step++) {
            var category = ORDER.get(step);
            BigDecimal remainingMinimum = BigDecimal.ZERO;
            for (int next = step + 1; next < ORDER.size(); next++) {
                remainingMinimum = remainingMinimum.add(pools.get(ORDER.get(next)).stream().map(Candidate::price).min(BigDecimal::compareTo).orElseThrow());
            }
            var expanded = new ArrayList<Plan>();
            for (var plan : beam) for (var candidate : pools.get(category)) {
                var total = plan.total().add(candidate.price());
                if (total.add(remainingMinimum).compareTo(budget) > 0) continue;
                var parts = new EnumMap<HardwareCategory, Candidate>(HardwareCategory.class);
                parts.putAll(plan.parts()); parts.put(category, candidate);
                var next = new Plan(Collections.unmodifiableMap(parts), total, plan.utility().add(candidate.contribution()));
                if (category == HardwareCategory.GPU && candidate.productId() == 0 && next.integratedGraphicsCpuId() == null) continue;
                var parameters = new EnumMap<HardwareCategory, JsonNode>(HardwareCategory.class);
                parts.forEach((code, item) -> { if (item.productId() > 0) parameters.put(code, item.parameters()); });
                if (CompatibilityRules.canComplete(parameters, next.memoryQuantity())) expanded.add(next);
            }
            var retained = new LinkedHashSet<Plan>();
            expanded.stream().sorted(CHEAP).limit(BEAM_WIDTH / 2).forEach(retained::add);
            expanded.stream().sorted(STRONG).filter(plan -> !retained.contains(plan))
                    .limit(BEAM_WIDTH - retained.size()).forEach(retained::add);
            beam = List.copyOf(retained);
        }
        return beam;
    }
}
