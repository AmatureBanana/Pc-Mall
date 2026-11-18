package cn.zhuangjimao.catalog;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Recommendation capacity scores deliberately differ from the catalog's capacity-priority tags. */
public final class RecommendationRules {
    private RecommendationRules() {}

    public static BigDecimal capacity(HardwareCategory category, BigDecimal gb) {
        if (gb == null) return null;
        if (gb.signum() < 0) throw new IllegalArgumentException("容量不能为负数");
        int[] nodes = switch (category) {
            case MEMORY -> new int[]{0, 8, 16, 32};
            case SSD -> new int[]{0, 256, 512, 1000};
            default -> throw new IllegalArgumentException("仅内存与 SSD 使用容量分");
        };
        int[] scores = category == HardwareCategory.MEMORY ? new int[]{0, 50, 80, 100} : new int[]{0, 40, 75, 100};
        for (int i = 1; i < nodes.length; i++) {
            if (gb.compareTo(BigDecimal.valueOf(nodes[i])) <= 0) {
                return gb.subtract(BigDecimal.valueOf(nodes[i - 1])).multiply(BigDecimal.valueOf(scores[i] - scores[i - 1]))
                        .divide(BigDecimal.valueOf(nodes[i] - nodes[i - 1]), 6, RoundingMode.HALF_UP)
                        .add(BigDecimal.valueOf(scores[i - 1]));
            }
        }
        return new BigDecimal("100.000000");
    }

    public static BigDecimal office(BigDecimal cpu, BigDecimal memory, BigDecimal ssd) {
        return cpu.multiply(new BigDecimal("0.50")).add(memory.multiply(new BigDecimal("0.30")))
                .add(ssd.multiply(new BigDecimal("0.20")));
    }
}
