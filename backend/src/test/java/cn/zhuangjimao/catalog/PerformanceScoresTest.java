package cn.zhuangjimao.catalog;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PerformanceScoresTest {
    @Test void frozenNodesInterpolationAndCapsRemainStable() {
        String[][] cases = {
            {"CPU","0","0"},{"CPU","50","25"},{"CPU","200","50"},{"CPU","450","75"},{"CPU","800","100"},
            {"CPU","25","12.5"},{"CPU","125","37.5"},{"CPU","325","62.5"},{"CPU","625","87.5"},{"CPU","900","100"},
            {"GPU","0","0"},{"GPU","500","25"},{"GPU","2000","50"},{"GPU","4500","75"},{"GPU","8000","100"},
            {"GPU","250","12.5"},{"GPU","1250","37.5"},{"GPU","3250","62.5"},{"GPU","6250","87.5"},{"GPU","9000","100"}
        };
        for (var c : cases) assertThat(PerformanceScores.score(c[0],new BigDecimal(c[1])))
                .as("%s %s",c[0],c[1]).isEqualByComparingTo(c[2]);
        assertThat(PerformanceScores.score("CPU",null)).isNull();
        assertThat(PerformanceScores.score("PSU",new BigDecimal("750"))).isNull();
        assertThatIllegalArgumentException().isThrownBy(() -> PerformanceScores.score("CPU",new BigDecimal("-1")));
    }

    @Test void capacityUnitsUseExactDecimalGigabytes() {
        assertThat(PerformanceScores.ssdGigabytes(BigDecimal.ONE,"TB")).isEqualByComparingTo("1000");
        assertThat(PerformanceScores.ssdGigabytes(BigDecimal.ONE,"GiB")).isEqualByComparingTo("1.073741824");
        assertThat(PerformanceScores.ssdGigabytes(BigDecimal.ONE,"TiB")).isEqualByComparingTo("1099.511627776");
        assertThatIllegalArgumentException().isThrownBy(() -> PerformanceScores.ssdGigabytes(BigDecimal.ONE,"MB"));
    }

    @Test void memoryUsesRatedSpeedAndTimingsButKeepsCapacityStrictlyFirst() {
        var smaller = PerformanceScores.memory(new BigDecimal("16"), "DDR5", new BigDecimal("7200"), 34, 44, 44);
        var larger = PerformanceScores.memory(new BigDecimal("32"), "DDR5", new BigDecimal("4800"), 40, 40, 40);
        var faster = PerformanceScores.memory(new BigDecimal("32"), "DDR5", new BigDecimal("6000"), 36, 38, 38);
        var tighter = PerformanceScores.memory(new BigDecimal("32"), "DDR5", new BigDecimal("6000"), 30, 36, 36);
        var ddr4 = PerformanceScores.memory(new BigDecimal("32"), "DDR4", new BigDecimal("3200"), 16, 18, 18);
        assertThat(smaller.performanceScore()).isEqualByComparingTo("91.851852");
        assertThat(larger.performanceScore()).isEqualByComparingTo("50.925926");
        assertThat(larger.score()).isGreaterThan(smaller.score());
        assertThat(faster.score()).isEqualByComparingTo("47.252110").isGreaterThan(larger.score());
        assertThat(tighter.score()).isGreaterThan(faster.score());
        assertThat(ddr4.performanceScore()).isEqualByComparingTo("86.111111");
        assertThat(PerformanceScores.memory(new BigDecimal("24"), "DDR5", new BigDecimal("4800"), 60, 60, 60).score()).isGreaterThan(smaller.score());
        assertThat(PerformanceScores.memory(new BigDecimal("12"), "DDR5", new BigDecimal("4800"), 40, 40, 40)).isNull();
        assertThat(PerformanceScores.memory(new BigDecimal("32"), "DDR3", new BigDecimal("1600"), 10, 10, 10)).isNull();
    }

    @Test void ssdWeightsReadAndWriteSeparatelyAndDistinguishesAdjacentCapacityTiers() {
        var max500 = PerformanceScores.ssd(new BigDecimal("500"), new BigDecimal("7000"), new BigDecimal("6000"));
        var slow512 = PerformanceScores.ssd(new BigDecimal("512"), BigDecimal.ONE, BigDecimal.ONE);
        assertThat(slow512.score()).isGreaterThan(max500.score());
        var read = PerformanceScores.ssd(new BigDecimal("1000"), new BigDecimal("7000"), new BigDecimal("3000"));
        var write = PerformanceScores.ssd(new BigDecimal("1000"), new BigDecimal("3500"), new BigDecimal("6000"));
        assertThat(read.performanceScore()).isEqualByComparingTo("77.500000");
        assertThat(write.performanceScore()).isEqualByComparingTo("72.500000");
        assertThat(read.score()).isGreaterThan(write.score());
        var capped = PerformanceScores.ssd(new BigDecimal("1000"), new BigDecimal("7450"), new BigDecimal("6900"));
        assertThat(capped.score()).isEqualByComparingTo("59.731544");
        assertThat(capped.performanceScore()).isEqualByComparingTo("100.000000");
        assertThat(PerformanceScores.ssd(new BigDecimal("8000"), new BigDecimal("7000"), new BigDecimal("6000")).score()).isEqualByComparingTo("100");
        assertThat(PerformanceScores.ssd(new BigDecimal("1000"), BigDecimal.ZERO, new BigDecimal("6000"))).isNull();
        assertThat(PerformanceScores.ssd(new BigDecimal("1000"), new BigDecimal("7000"), null)).isNull();
    }
}
