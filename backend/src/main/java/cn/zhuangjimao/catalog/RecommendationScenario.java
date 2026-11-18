package cn.zhuangjimao.catalog;

import java.math.BigDecimal;
import java.util.Map;

import static cn.zhuangjimao.catalog.HardwareCategory.*;

enum RecommendationScenario {
    OFFICE("办公", "office-v1", 25, 0, 8, 256, 50, 0, 30, 20),
    GAMING("游戏", "gaming-v1", 35, 40, 16, 512, 30, 55, 10, 5),
    CREATION("创作", "creation-v1", 35, 35, 16, 512, 35, 35, 20, 10);

    final String label;
    final String version;
    private final Map<HardwareCategory, Integer> minimums;
    private final Map<HardwareCategory, Integer> weights;

    RecommendationScenario(String label, String version, int cpu, int gpu, int memory, int ssd,
                           int cpuWeight, int gpuWeight, int memoryWeight, int ssdWeight) {
        this.label = label;
        this.version = version;
        minimums = Map.of(CPU, cpu, GPU, gpu, MEMORY, memory, SSD, ssd);
        weights = Map.of(CPU, cpuWeight, GPU, gpuWeight, MEMORY, memoryWeight, SSD, ssdWeight);
    }

    boolean requiresGpu() { return minimums.get(GPU) > 0; }

    boolean meetsMinimum(HardwareCategory category, BigDecimal value) {
        return value.compareTo(BigDecimal.valueOf(minimums.getOrDefault(category, 0))) >= 0;
    }

    BigDecimal contribution(HardwareCategory category, BigDecimal score) {
        return score.multiply(BigDecimal.valueOf(weights.getOrDefault(category, 0), 2));
    }

    Weights weights() {
        return new Weights(weights.get(CPU), weights.get(GPU), weights.get(MEMORY), weights.get(SSD));
    }

    String minimumExplanation() {
        String graphics = requiresGpu() ? "，独显参考分至少 " + minimums.get(GPU) : "";
        return "%s门槛：CPU 参考分至少 %d%s，内存至少 %d GB，SSD 至少 %d GB。"
                .formatted(label, minimums.get(CPU), graphics, minimums.get(MEMORY), minimums.get(SSD));
    }

    String weightExplanation() {
        String graphics = requiresGpu() ? " + 独显 × " + weights.get(GPU) + "%" : "";
        return "%s总分 = CPU × %d%%%s + 内存容量分 × %d%% + SSD 容量分 × %d%%，满分 100。"
                .formatted(label, weights.get(CPU), graphics, weights.get(MEMORY), weights.get(SSD));
    }

    record Weights(int cpu, int gpu, int memory, int ssd) {}
}
