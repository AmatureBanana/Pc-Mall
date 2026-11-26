package cn.zhuangjimao.compatibility;

import org.junit.jupiter.api.Test;
import java.util.List;
import static cn.zhuangjimao.catalog.HardwareCategory.*;
import static cn.zhuangjimao.compatibility.CompatibilityRulesTest.*;
import static org.assertj.core.api.Assertions.assertThat;

class CompatibilityAdmissionRulesTest {
    @Test
    void newRulesKeepMalformedOrUnsourcedValuesUnknownAndKnownEmptySetsAreFailures() {
        var gpu = part(GPU, "GPU", text("installationInterface", "PCIe x16"));
        assertThat(rule(CompatibilityRules.check(parts(gpu, part(MOTHERBOARD, "无插槽", set("gpuInterfaces", List.of())))), "C06").state()).isEqualTo(RuleState.FAIL);
        for (Object value : List.of("155", true, -1, 0, List.of(155))) {
            assertThat(rule(CompatibilityRules.check(parts(part(COOLER, "散热器", number("height", value)),
                    part(CASE, "机箱", number("maxCoolerHeight", 163)))), "C10").state()).isEqualTo(RuleState.UNKNOWN);
            assertThat(rule(CompatibilityRules.check(parts(part(CPU, "CPU", number("tdp", value)),
                    part(PSU, "电源", number("ratedPower", 750)))), "C08").state()).isEqualTo(RuleState.UNKNOWN);
        }
        assertThat(rule(CompatibilityRules.check(parts(part(GPU, "无来源长度", new Parameter("length", number("length", 244).value(), false)),
                part(CASE, "机箱", number("maxGpuLength", 370)))), "C09").state()).isEqualTo(RuleState.UNKNOWN);
    }

    @Test
    void graphicsOutputAndCpuGraphicsCapabilityAreNotCompatibilityRules() {
        var rules = CompatibilityRules.check(parts(part(CPU, "无核显", number("integratedGraphics", false)),
                part(MOTHERBOARD, "无输出", set("displayOutputs", List.of()))));
        assertThat(rules).extracting(RuleResult::rule).doesNotContain("C07").hasSize(9);
    }

    @Test
    void spatialRulesAllowEqualLimitsAndRejectEvenAFractionAboveThem() {
        for (String code : List.of("C09", "C10")) {
            var category = code.equals("C09") ? GPU : COOLER;
            String size = code.equals("C09") ? "length" : "height";
            String limit = code.equals("C09") ? "maxGpuLength" : "maxCoolerHeight";
            var box = part(CASE, "机箱", number(limit, 155));
            assertThat(rule(CompatibilityRules.check(parts(box, part(category, "部件", number(size, 155)))), code).state()).isEqualTo(RuleState.PASS);
            assertThat(rule(CompatibilityRules.check(parts(box, part(category, "部件", number(size, 155.01)))), code).state()).isEqualTo(RuleState.FAIL);
            assertThat(rule(CompatibilityRules.check(parts(box, part(category, "未知部件"))), code).state()).isEqualTo(RuleState.UNKNOWN);
            assertThat(rule(CompatibilityRules.check(parts(part(category, "部件", number(size, 155)))), code).state()).isEqualTo(RuleState.PENDING);
        }
        assertThat(rule(CompatibilityRules.check(parts()), "C09").state()).isEqualTo(RuleState.NOT_APPLICABLE);
        assertThat(rule(CompatibilityRules.check(parts()), "C10").state()).isEqualTo(RuleState.PENDING);
    }

    @Test
    void c08RequiresTdpPlusFifteenPercentWithoutAnUpperLimit() {
        var cpu = part(CPU, "CPU", number("peakPowerEstimate", 125), number("tdp", 65));
        var gpu = part(GPU, "GPU", number("boardPowerEstimate", 200));
        assertThat(rule(CompatibilityRules.check(parts(cpu, gpu, part(PSU, "电源", number("ratedPower", 397)))), "C08").state()).isEqualTo(RuleState.PASS);
        assertThat(rule(CompatibilityRules.check(parts(cpu, gpu, part(PSU, "电源", number("ratedPower", 396)))), "C08").state()).isEqualTo(RuleState.FAIL);
        assertThat(rule(CompatibilityRules.check(parts(cpu, part(PSU, "电源", number("ratedPower", 167)))), "C08").state()).isEqualTo(RuleState.PASS);
        assertThat(rule(CompatibilityRules.check(parts(cpu, part(PSU, "电源", number("ratedPower", 166)))), "C08").state()).isEqualTo(RuleState.FAIL);
        var cpu120 = part(CPU, "仅 TDP", number("tdp", 120));
        for (int watts : List.of(460, 750)) {
            assertThat(rule(CompatibilityRules.check(parts(cpu120, gpu, part(PSU, "电源", number("ratedPower", watts)))), "C08").state()).isEqualTo(RuleState.PASS);
        }
        var missingCpu = rule(CompatibilityRules.check(parts(part(CPU, "仅峰值", number("peakPowerEstimate", 125)), gpu,
                part(PSU, "电源", number("ratedPower", 750)))), "C08");
        assertThat(missingCpu.state()).isEqualTo(RuleState.UNKNOWN);
        assertThat(missingCpu.message()).contains("CPU TDP").doesNotContain("显卡整卡功耗", "电源额定功率");

        var missingGpuAndPsu = rule(CompatibilityRules.check(parts(cpu, part(GPU, "无功耗参数"),
                part(PSU, "无来源", new Parameter("ratedPower", number("ratedPower", 750).value(), false)))), "C08");
        assertThat(missingGpuAndPsu.state()).isEqualTo(RuleState.UNKNOWN);
        assertThat(missingGpuAndPsu.message()).contains("显卡整卡功耗", "电源额定功率").doesNotContain("CPU 峰值功耗", "TDP");
    }

    @Test
    void c06ChecksTheInstallationInterfaceWithoutRejectingDifferentPcieGenerations() {
        var gpu = part(GPU, "GPU", text("installationInterface", "PCIe x16"), text("pcieGeneration", "4.0"));
        assertThat(rule(CompatibilityRules.check(parts(gpu,
                part(MOTHERBOARD, "主板", set("gpuInterfaces", List.of("PCIe x16")), text("pcieGeneration", "5.0")))), "C06").state())
                .isEqualTo(RuleState.PASS);
        assertThat(rule(CompatibilityRules.check(parts(gpu,
                part(MOTHERBOARD, "主板", set("gpuInterfaces", List.of("PCIe x4"))))), "C06").state())
                .isEqualTo(RuleState.FAIL);
        assertThat(rule(CompatibilityRules.check(parts(gpu)), "C06").state()).isEqualTo(RuleState.PENDING);
        assertThat(rule(CompatibilityRules.check(parts(gpu, part(MOTHERBOARD, "未知主板"))), "C06").state()).isEqualTo(RuleState.UNKNOWN);
        assertThat(rule(CompatibilityRules.check(parts()), "C06").state()).isEqualTo(RuleState.NOT_APPLICABLE);
    }
}
