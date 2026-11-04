package cn.zhuangjimao.catalog;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/** Versioned catalog scoring rules. */
public final class PerformanceScores {
    public static final String MAPPING_VERSION = "performance-v1";
    public static final String BLENDER_VERSION = "4.3.0";
    public static final String CAPACITY_VERSION = "capacity-priority-v1";
    private static final int[] MEMORY_TIERS = {8,16,24,32,48,64,96,128};
    private static final int[] SSD_TIERS = {128,240,250,256,480,500,512,960,1000,1024,1920,2000,2048,4000,8000};
    private static final BigDecimal CAPACITY_GUARD = new BigDecimal("0.9");
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private PerformanceScores() {}

    public static BigDecimal score(String category, BigDecimal raw) {
        if (raw == null) return null;
        if (raw.signum() < 0) throw new IllegalArgumentException("性能输入不能为负数");
        int[] inputs;
        int[] outputs;
        switch (category) {
            case "CPU" -> { inputs = new int[]{0,50,200,450,800}; outputs = new int[]{0,25,50,75,100}; }
            case "GPU" -> { inputs = new int[]{0,500,2000,4500,8000}; outputs = new int[]{0,25,50,75,100}; }
            default -> { return null; }
        }
        for (int i=1; i<inputs.length; i++) {
            if (raw.compareTo(BigDecimal.valueOf(inputs[i])) <= 0) {
                return raw.subtract(BigDecimal.valueOf(inputs[i-1])).multiply(BigDecimal.valueOf(outputs[i]-outputs[i-1]))
                        .divide(BigDecimal.valueOf(inputs[i]-inputs[i-1]),6,RoundingMode.HALF_UP)
                        .add(BigDecimal.valueOf(outputs[i-1]));
            }
        }
        return new BigDecimal("100.000000");
    }

    public static CapacityRating memory(BigDecimal capacity, String generation, BigDecimal rate, int cl, int trcd, int trp) {
        int reference = "DDR4".equals(generation) ? 3600 : "DDR5".equals(generation) ? 6400 : 0;
        if (reference == 0 || !positive(rate) || cl <= 0 || trcd <= 0 || trp <= 0) return null;
        BigDecimal weightedCycles = weighted(BigDecimal.valueOf(cl),"0.5")
                .add(weighted(BigDecimal.valueOf(trcd),"0.3")).add(weighted(BigDecimal.valueOf(trp),"0.2"));
        BigDecimal timing = weightedCycles.multiply(BigDecimal.valueOf(2000)).divide(rate,MathContext.DECIMAL128);
        BigDecimal bandwidth = clamp(ratio(rate,reference));
        BigDecimal latency = clamp(ratio(BigDecimal.valueOf(18).subtract(timing),9));
        return capacityRating(capacity,MEMORY_TIERS,weighted(bandwidth,"0.6").add(weighted(latency,"0.4")));
    }

    public static CapacityRating ssd(BigDecimal capacity, BigDecimal read, BigDecimal write) {
        if (!positive(read) || !positive(write)) return null;
        return capacityRating(capacity,SSD_TIERS,weighted(clamp(ratio(read,7000)),"0.55")
                .add(weighted(clamp(ratio(write,6000)),"0.45")));
    }

    private static CapacityRating capacityRating(BigDecimal capacity, int[] tiers, BigDecimal subscore) {
        if (!positive(capacity)) return null;
        for (int rank = 0; rank < tiers.length; rank++) {
            if (capacity.compareTo(BigDecimal.valueOf(tiers[rank])) == 0) {
                BigDecimal score = BigDecimal.valueOf(rank).add(CAPACITY_GUARD.multiply(subscore)).multiply(HUNDRED)
                        .divide(BigDecimal.valueOf(tiers.length-1).add(CAPACITY_GUARD),6,RoundingMode.HALF_UP);
                return new CapacityRating(score,subscore.multiply(HUNDRED).setScale(6,RoundingMode.HALF_UP));
            }
        }
        return null;
    }

    private static boolean positive(BigDecimal value) { return value != null && value.signum() > 0; }
    private static BigDecimal weighted(BigDecimal value, String weight) { return value.multiply(new BigDecimal(weight)); }
    private static BigDecimal ratio(BigDecimal value, int reference) { return value.divide(BigDecimal.valueOf(reference),MathContext.DECIMAL128); }
    private static BigDecimal clamp(BigDecimal value) { return value.max(BigDecimal.ZERO).min(BigDecimal.ONE); }

    public record CapacityRating(BigDecimal score, BigDecimal performanceScore) {}

    public static BigDecimal ssdGigabytes(BigDecimal value, String unit) {
        if (value == null) return null;
        if (value.signum() < 0) throw new IllegalArgumentException("容量不能为负数");
        return value.multiply(switch (unit) {
            case "GB" -> BigDecimal.ONE;
            case "TB" -> new BigDecimal("1000");
            case "GiB" -> new BigDecimal("1.073741824");
            case "TiB" -> new BigDecimal("1099.511627776");
            default -> throw new IllegalArgumentException("容量单位无效");
        });
    }
}
