package cn.zhuangjimao.compatibility;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import cn.zhuangjimao.catalog.HardwareCategory;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static cn.zhuangjimao.catalog.HardwareCategory.CASE;
import static cn.zhuangjimao.catalog.HardwareCategory.COOLER;
import static cn.zhuangjimao.catalog.HardwareCategory.CPU;
import static cn.zhuangjimao.catalog.HardwareCategory.MEMORY;
import static cn.zhuangjimao.catalog.HardwareCategory.MOTHERBOARD;
import static cn.zhuangjimao.catalog.HardwareCategory.SSD;
import static org.assertj.core.api.Assertions.assertThat;

/** Deterministic F03 C01–C05 rules; expected values come from the frozen spec, never from the engine. */
class CompatibilityRulesTest {
    private static final ObjectMapper json = new ObjectMapper();

    @Test
    void c01PassesWhenCpuAndMotherboardSocketsMatch() {
        var rules = CompatibilityRules.check(parts(
                part(CPU, "Intel Core i5-14600KF", text("socket", "LGA1700")),
                part(MOTHERBOARD, "华硕 TUF B760M-PLUS WIFI", text("socket", "LGA1700"))));

        RuleResult c01 = rule(rules, "C01");
        assertThat(c01.state()).isEqualTo(RuleState.PASS);
        assertThat(c01.categories()).containsExactly(CPU, MOTHERBOARD);
        assertThat(c01.parameters()).containsExactly("socket");
        assertThat(c01.message()).contains("LGA1700");
    }

    @Test
    void c01FailsOnAKnownMismatchAndReportsBothActualValues() {
        RuleResult c01 = rule(CompatibilityRules.check(parts(
                part(CPU, "Intel Core i5-14600KF", text("socket", "LGA1700")),
                part(MOTHERBOARD, "微星 MAG B650M MORTAR WIFI", text("socket", "AM5")))), "C01");

        assertThat(c01.state()).isEqualTo(RuleState.FAIL);
        assertThat(c01.details()).extracting(RuleResult.RuleDetail::value).containsExactly("LGA1700", "AM5");
    }

    @Test
    void c01IsPendingWithoutBothPartsAndUnknownWithoutAVerifiedSocket() {
        assertThat(rule(CompatibilityRules.check(parts(
                part(CPU, "Intel Core i5-14600KF", text("socket", "LGA1700")))), "C01").state())
                .isEqualTo(RuleState.PENDING);
        assertThat(rule(CompatibilityRules.check(parts(
                part(CPU, "AMD Ryzen 7 7800X3D"),
                part(MOTHERBOARD, "华硕 TUF B760M-PLUS WIFI", text("socket", "LGA1700")))), "C01").state())
                .isEqualTo(RuleState.UNKNOWN);
    }

    @Test
    void c01TreatsAnUnsourcedValueAsUnknownInsteadOfTrustingIt() {
        assertThat(rule(CompatibilityRules.check(parts(
                part(CPU, "Intel Core i5-14600KF", text("socket", "LGA1700", false)),
                part(MOTHERBOARD, "华硕 TUF B760M-PLUS WIFI", text("socket", "LGA1700")))), "C01").state())
                .isEqualTo(RuleState.UNKNOWN);
    }

    @Test
    void c02PassesWhenGenerationIsSupportedAndSticksFitTheSlots() {
        RuleResult c02 = rule(CompatibilityRules.check(parts(
                part(CPU, "Intel Core i5-14600KF", text("socket", "LGA1700")),
                part(MOTHERBOARD, "华硕 TUF B760M-PLUS WIFI", text("socket", "LGA1700"),
                        set("memoryGenerations", List.of("DDR4", "DDR5")), number("memorySlots", 4)),
                part(MEMORY, "金士顿 FURY Beast 32GB (16G×2)", text("generation", "DDR5"), number("stickCount", 2)))), "C02");

        assertThat(c02.state()).isEqualTo(RuleState.PASS);
        assertThat(c02.categories()).containsExactly(MOTHERBOARD, MEMORY);
        assertThat(c02.parameters()).containsExactly("memoryGenerations", "memorySlots", "generation", "stickCount");
    }

    @Test
    void c02FailsWhenTheMotherboardDoesNotListTheMemoryGeneration() {
        RuleResult c02 = rule(CompatibilityRules.check(parts(
                part(MOTHERBOARD, "华硕 TUF B760M-PLUS WIFI", set("memoryGenerations", List.of("DDR5")), number("memorySlots", 4)),
                part(MEMORY, "金士顿 FURY Beast 32GB (16G×2)", text("generation", "DDR4"), number("stickCount", 2)))), "C02");

        assertThat(c02.state()).isEqualTo(RuleState.FAIL);
        assertThat(c02.message()).contains("DDR4", "DDR5");
    }

    @Test
    void c02FailsWhenTheKitSticksExceedThePhysicalSlots() {
        RuleResult c02 = rule(CompatibilityRules.check(parts(
                part(MOTHERBOARD, "华硕 TUF B760M-PLUS WIFI", set("memoryGenerations", List.of("DDR5")), number("memorySlots", 2)),
                part(MEMORY, "金士顿 FURY Beast 32GB (16G×2)", text("generation", "DDR5"), number("stickCount", 4)))), "C02");

        assertThat(c02.state()).isEqualTo(RuleState.FAIL);
        assertThat(c02.message()).contains("4", "2");
        assertThat(rule(CompatibilityRules.check(parts(
                part(MOTHERBOARD, "华硕 TUF B760M-PLUS WIFI", set("memoryGenerations", List.of("DDR5")), number("memorySlots", 2)),
                part(MEMORY, "套装", text("generation", "DDR5"), number("stickCount", 2)))), "C02").state())
                .isEqualTo(RuleState.PASS);
    }

    @Test
    void c02IsPendingWithoutMemoryOrMotherboardAndUnknownWithoutTheKitStickCount() {
        assertThat(rule(CompatibilityRules.check(parts(
                part(MOTHERBOARD, "华硕 TUF B760M-PLUS WIFI", set("memoryGenerations", List.of("DDR5")), number("memorySlots", 4)))), "C02").state())
                .isEqualTo(RuleState.PENDING);
        assertThat(rule(CompatibilityRules.check(parts(
                part(MOTHERBOARD, "华硕 TUF B760M-PLUS WIFI", set("memoryGenerations", List.of("DDR5")), number("memorySlots", 4)),
                part(MEMORY, "内存套装", text("generation", "DDR5")))), "C02").state())
                .isEqualTo(RuleState.UNKNOWN);
    }

    @Test
    void c03PassesWhenTheCoolerListsTheCpuSocketAndFailsWhenItDoesNot() {
        RuleResult pass = rule(CompatibilityRules.check(parts(
                part(CPU, "Intel Core i5-14600KF", text("socket", "LGA1700")),
                part(COOLER, "九州风神 AK400 风冷散热器", set("supportedSockets", List.of("LGA1700", "AM5"))))), "C03");
        assertThat(pass.state()).isEqualTo(RuleState.PASS);
        assertThat(pass.categories()).containsExactly(CPU, COOLER);
        assertThat(pass.parameters()).containsExactly("socket", "supportedSockets");

        RuleResult fail = rule(CompatibilityRules.check(parts(
                part(CPU, "Intel Core i5-14600KF", text("socket", "LGA1700")),
                part(COOLER, "仅 AM5 散热器", set("supportedSockets", List.of("AM5"))))), "C03");
        assertThat(fail.state()).isEqualTo(RuleState.FAIL);
        assertThat(fail.message()).contains("LGA1700", "AM5");
    }

    @Test
    void c03IsPendingWithoutACoolerAndUnknownWithoutACpuSocket() {
        assertThat(rule(CompatibilityRules.check(parts(
                part(CPU, "Intel Core i5-14600KF", text("socket", "LGA1700")))), "C03").state())
                .isEqualTo(RuleState.PENDING);
        assertThat(rule(CompatibilityRules.check(parts(
                part(CPU, "AMD Ryzen 7 7800X3D"),
                part(COOLER, "九州风神 AK400 风冷散热器", set("supportedSockets", List.of("AM5"))))), "C03").state())
                .isEqualTo(RuleState.UNKNOWN);
    }

    @Test
    void c04PassesWhenTheCaseListsTheBoardFormFactorAndFailsWhenItDoesNot() {
        RuleResult pass = rule(CompatibilityRules.check(parts(
                part(MOTHERBOARD, "华硕 TUF B760M-PLUS WIFI", text("formFactor", "Micro-ATX")),
                part(CASE, "九州风神 CC560 中塔机箱",
                        set("supportedMotherboardFormFactors", List.of("ATX", "Micro-ATX", "Mini-ITX"))))), "C04");
        assertThat(pass.state()).isEqualTo(RuleState.PASS);
        assertThat(pass.categories()).containsExactly(MOTHERBOARD, CASE);

        RuleResult fail = rule(CompatibilityRules.check(parts(
                part(MOTHERBOARD, "E-ATX 主板", text("formFactor", "E-ATX")),
                part(CASE, "九州风神 CC560 中塔机箱",
                        set("supportedMotherboardFormFactors", List.of("ATX", "Micro-ATX", "Mini-ITX"))))), "C04");
        assertThat(fail.state()).isEqualTo(RuleState.FAIL);
        assertThat(fail.message()).contains("E-ATX");
    }

    @Test
    void c04IsPendingWithoutACaseAndUnknownWithoutABoardFormFactor() {
        assertThat(rule(CompatibilityRules.check(parts(
                part(MOTHERBOARD, "华硕 TUF B760M-PLUS WIFI", text("formFactor", "Micro-ATX")))), "C04").state())
                .isEqualTo(RuleState.PENDING);
        assertThat(rule(CompatibilityRules.check(parts(
                part(MOTHERBOARD, "未核验板型主板"),
                part(CASE, "九州风神 CC560 中塔机箱",
                        set("supportedMotherboardFormFactors", List.of("ATX"))))), "C04").state())
                .isEqualTo(RuleState.UNKNOWN);
    }

    @Test
    void c05MatchesTheExplicitMotherboardInstallationCombinationNotACartesianProduct() {
        Part board = part(MOTHERBOARD, "华硕 TUF B760M-PLUS WIFI",
                slots(List.<String[]>of(
                        new String[]{"M.2 2242", "PCIe", "NVMe"},
                        new String[]{"M.2 2260", "PCIe", "NVMe"},
                        new String[]{"M.2 2280", "PCIe", "NVMe"})));

        RuleResult pass = rule(CompatibilityRules.check(parts(board,
                part(SSD, "三星 990 PRO 1TB", text("formFactor", "M.2 2280"), text("interface", "PCIe"), text("protocol", "NVMe")))), "C05");
        assertThat(pass.state()).isEqualTo(RuleState.PASS);
        assertThat(pass.categories()).containsExactly(MOTHERBOARD, SSD);

        // 2242 is supported and 2.5-inch is a supported physical form elsewhere; the board never lists it.
        RuleResult fail = rule(CompatibilityRules.check(parts(board,
                part(SSD, "SATA 2.5 英寸盘", text("formFactor", "2.5-inch"), text("interface", "SATA"), text("protocol", "AHCI")))), "C05");
        assertThat(fail.state()).isEqualTo(RuleState.FAIL);
        assertThat(fail.message()).contains("2.5-inch");
    }

    @Test
    void c05RejectsAKnownProtocolMismatchEvenWhenTheFormFactorFits() {
        // The same physical M.2 2280 bay, but only a SATA/AHCI link is verified for this mocked board.
        Part board = part(MOTHERBOARD, "仅 SATA M.2 组合主板", slots(List.<String[]>of(new String[]{"M.2 2280", "SATA", "AHCI"})));

        RuleResult nvme = rule(CompatibilityRules.check(parts(board,
                part(SSD, "三星 990 PRO 1TB", text("formFactor", "M.2 2280"), text("interface", "PCIe"), text("protocol", "NVMe")))), "C05");
        assertThat(nvme.state()).isEqualTo(RuleState.FAIL);
        assertThat(nvme.message()).contains("NVMe");

        RuleResult ahci = rule(CompatibilityRules.check(parts(board,
                part(SSD, "SATA M.2 盘", text("formFactor", "M.2 2280"), text("interface", "SATA"), text("protocol", "AHCI")))), "C05");
        assertThat(ahci.state()).isEqualTo(RuleState.PASS);
    }

    @Test
    void c05IsPendingWithoutAnSsdAndUnknownWithoutAProtocolOrVerifiedSlotList() {
        assertThat(rule(CompatibilityRules.check(parts(
                part(MOTHERBOARD, "华硕 TUF B760M-PLUS WIFI", slots(List.<String[]>of(new String[]{"M.2 2280", "PCIe", "NVMe"}))))), "C05").state())
                .isEqualTo(RuleState.PENDING);
        assertThat(rule(CompatibilityRules.check(parts(
                part(MOTHERBOARD, "华硕 TUF B760M-PLUS WIFI", slots(List.<String[]>of(new String[]{"M.2 2280", "PCIe", "NVMe"}))),
                part(SSD, "未核验协议 SSD", text("formFactor", "M.2 2280"), text("interface", "PCIe")))), "C05").state())
                .isEqualTo(RuleState.UNKNOWN);
        assertThat(rule(CompatibilityRules.check(parts(
                part(MOTHERBOARD, "无 SSD 安装组合记录主板"),
                part(SSD, "三星 990 PRO 1TB", text("formFactor", "M.2 2280"), text("interface", "PCIe"), text("protocol", "NVMe")))), "C05").state())
                .isEqualTo(RuleState.UNKNOWN);
    }

    @Test
    void detailsOnlyListTheParametersEachRuleReallyReads() {
        var rules = CompatibilityRules.check(parts(
                part(CPU, "Intel Core i5-14600KF", text("socket", "LGA1700")),
                part(MOTHERBOARD, "华硕 TUF B760M-PLUS WIFI", set("memoryGenerations", List.of("DDR5")),
                        number("memorySlots", 4), text("formFactor", "Micro-ATX")),
                part(MEMORY, "金士顿 FURY Beast 32GB (16G×2)", text("generation", "DDR5"), number("stickCount", 2))));

        assertThat(rule(rules, "C02").details())
                .extracting(RuleResult.RuleDetail::category, RuleResult.RuleDetail::parameter)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(MOTHERBOARD, "memoryGenerations"),
                        org.assertj.core.groups.Tuple.tuple(MOTHERBOARD, "memorySlots"),
                        org.assertj.core.groups.Tuple.tuple(MEMORY, "generation"),
                        org.assertj.core.groups.Tuple.tuple(MEMORY, "stickCount"));
    }

    @Test
    void installationCombinationsAreReportedAsReadableTextNotRawJson() {
        RuleResult c05 = rule(CompatibilityRules.check(parts(
                part(MOTHERBOARD, "华硕 TUF B760M-PLUS WIFI", slots(List.<String[]>of(new String[]{"M.2 2280", "PCIe", "NVMe"}))),
                part(SSD, "三星 990 PRO 1TB", text("formFactor", "M.2 2280"), text("interface", "PCIe"), text("protocol", "NVMe")))), "C05");

        assertThat(c05.details()).extracting(RuleResult.RuleDetail::value)
                .contains("M.2 2280 · PCIe · NVMe");
        assertThat(c05.details()).extracting(RuleResult.RuleDetail::value)
                .noneSatisfy(value -> assertThat(value).contains("{"));
    }

    @Test
    void aMalformedSetValueDegradesToUnknownInsteadOfThrowing() {
        var rules = CompatibilityRules.check(parts(
                part(MOTHERBOARD, "华硕 TUF B760M-PLUS WIFI", new Parameter("memoryGenerations",
                        json.valueToTree(List.of(5, 7)), true), number("memorySlots", 4)),
                part(MEMORY, "金士顿 FURY Beast 32GB (16G×2)", text("generation", "DDR5"), number("stickCount", 2))));

        assertThat(rule(rules, "C02").state()).isEqualTo(RuleState.UNKNOWN);
        assertThat(rule(rules, "C02").message()).contains("无法判断");
    }

    @Test
    void aMalformedInstallationCombinationDegradesToUnknownInsteadOfThrowing() {
        Part board = part(MOTHERBOARD, "华硕 TUF B760M-PLUS WIFI", new Parameter("ssdSlots",
                json.valueToTree(List.of(java.util.Map.of("formFactor", "M.2 2280"))), true));

        RuleResult c05 = rule(CompatibilityRules.check(parts(board,
                part(SSD, "三星 990 PRO 1TB", text("formFactor", "M.2 2280"), text("interface", "PCIe"), text("protocol", "NVMe")))), "C05");
        assertThat(c05.state()).isEqualTo(RuleState.UNKNOWN);
    }

    @Test
    void pendingNamesCategoriesInReadableFormInsteadOfLeakingEnumCodes() {
        RuleResult c02 = rule(CompatibilityRules.check(parts(
                part(CPU, "Intel Core i5-14600KF", text("socket", "LGA1700")))), "C02");

        assertThat(c02.message()).contains("主板", "内存");
        assertThat(c02.message()).doesNotContain("MOTHERBOARD", "MEMORY");
    }

    @Test
    void everyExecutedRuleIsAlwaysReportedOnceInFrozenOrder() {
        var rules = CompatibilityRules.check(parts(part(CPU, "Intel Core i5-14600KF", text("socket", "LGA1700"))));

        assertThat(rules).extracting(RuleResult::rule).containsExactly("C01", "C02", "C03", "C04", "C05");
        assertThat(rules).extracting(RuleResult::state).containsOnly(RuleState.PENDING);
    }

    @Test
    void malformedSocketScalarsStayUnknownWithoutLosingOtherRules() {
        for (Object value : List.of(List.of("LGA1700"), Map.of(), true, 1700, "", "unverified-socket")) {
            var rules = CompatibilityRules.check(parts(
                    part(CPU, "CPU", new Parameter("socket", json.valueToTree(value), true)),
                    part(MOTHERBOARD, "主板", text("socket", "LGA1700"), text("formFactor", "Micro-ATX")),
                    part(CASE, "机箱", set("supportedMotherboardFormFactors", List.of("Micro-ATX")))));
            assertThat(rule(rules, "C01").state()).as("socket=%s", value).isEqualTo(RuleState.UNKNOWN);
            assertThat(rule(rules, "C04").state()).isEqualTo(RuleState.PASS);
            assertThat(rules).hasSize(5);
        }
    }

    @Test
    void malformedCountsStayUnknownInsteadOfBeingCoercedIntoValidIntegers() {
        for (Object value : List.of("bad", "2", true, 0, -1, 1.5,
                new java.math.BigInteger("9223372036854775808"), List.of(2), Map.of())) {
            var invalidSticks = CompatibilityRules.check(parts(
                    part(MOTHERBOARD, "主板", set("memoryGenerations", List.of("DDR5")), number("memorySlots", 4)),
                    part(MEMORY, "套装", text("generation", "DDR5"), number("stickCount", value))));
            assertThat(rule(invalidSticks, "C02").state()).as("stickCount=%s", value).isEqualTo(RuleState.UNKNOWN);

            var invalidSlots = CompatibilityRules.check(parts(
                    part(MOTHERBOARD, "主板", set("memoryGenerations", List.of("DDR5")), number("memorySlots", value)),
                    part(MEMORY, "套装", text("generation", "DDR5"), number("stickCount", 2))));
            assertThat(rule(invalidSlots, "C02").state()).as("memorySlots=%s", value).isEqualTo(RuleState.UNKNOWN);
        }
    }

    @Test
    void malformedSupportCollectionsAndSsdScalarsStayUnknown() {
        for (Object value : List.of("LGA1700", List.of("unverified-socket"),
                List.of(Map.of("formFactor", "M.2 2280", "interface", "PCIe", "protocol", "NVMe")))) {
            var rules = CompatibilityRules.check(parts(
                    part(CPU, "CPU", text("socket", "LGA1700")),
                    part(COOLER, "散热器", new Parameter("supportedSockets", json.valueToTree(value), true))));
            assertThat(rule(rules, "C03").state()).as("supportedSockets=%s", value).isEqualTo(RuleState.UNKNOWN);
        }
        for (String code : List.of("formFactor", "interface", "protocol")) {
            for (Object value : List.of(List.of("invalid"), Map.of(), true, 1, "", "unverified-value")) {
                Part ssd = part(SSD, "SSD", text("formFactor", "M.2 2280"), text("interface", "PCIe"), text("protocol", "NVMe"));
                for (var parameter : ssd.parameters()) {
                    if (parameter.path("code").asText().equals(code))
                        ((com.fasterxml.jackson.databind.node.ObjectNode) parameter).set("value", json.valueToTree(value));
                }
                var rules = CompatibilityRules.check(parts(
                        part(MOTHERBOARD, "主板", slots(List.<String[]>of(new String[]{"M.2 2280", "PCIe", "NVMe"}))), ssd));
                assertThat(rule(rules, "C05").state()).as("%s=%s", code, value).isEqualTo(RuleState.UNKNOWN);
            }
        }
    }

    @Test
    void malformedInstallationVocabularyCannotDecideAPass() {
        for (String[] combination : List.of(new String[]{"", "PCIe", "NVMe"},
                new String[]{"M.2 2280", "SATA", "NVMe"}, new String[]{"M.2 2280", "unknown", "NVMe"})) {
            var rules = CompatibilityRules.check(parts(
                    part(MOTHERBOARD, "主板", slots(List.<String[]>of(combination))),
                    part(SSD, "SSD", text("formFactor", combination[0]), text("interface", combination[1]), text("protocol", combination[2]))));
            assertThat(rule(rules, "C05").state()).isEqualTo(RuleState.UNKNOWN);
        }
    }

    @Test
    void c02KnownConflictsSurviveUnrelatedUnknownParameters() {
        for (String missing : List.of("memorySlots", "stickCount")) {
            var rules = CompatibilityRules.check(parts(
                    part(MOTHERBOARD, "主板", set("memoryGenerations", List.of("DDR5")),
                            number("memorySlots", missing.equals("memorySlots") ? null : 4)),
                    part(MEMORY, "套装", text("generation", "DDR4"),
                            number("stickCount", missing.equals("stickCount") ? null : 2))));
            assertThat(rule(rules, "C02").state()).as("unknown %s", missing).isEqualTo(RuleState.FAIL);
            assertThat(rule(rules, "C02").message()).contains("DDR4", "DDR5");
        }
        var tooManySticks = CompatibilityRules.check(parts(
                part(MOTHERBOARD, "主板", number("memorySlots", 2)),
                part(MEMORY, "套装", text("generation", null), number("stickCount", 4))));
        assertThat(rule(tooManySticks, "C02").state()).isEqualTo(RuleState.FAIL);
        assertThat(rule(tooManySticks, "C02").message()).contains("4", "2");
    }

    @Test
    void c05KnownConflictsSurviveUnrelatedUnknownParameters() {
        Part board = part(MOTHERBOARD, "主板", slots(List.<String[]>of(new String[]{"M.2 2280", "PCIe", "NVMe"})));
        for (String missing : List.of("interface", "protocol")) {
            var rules = CompatibilityRules.check(parts(board,
                    part(SSD, "SSD", text("formFactor", "2.5-inch"),
                            text("interface", missing.equals("interface") ? null : "SATA"),
                            text("protocol", missing.equals("protocol") ? null : "AHCI"))));
            assertThat(rule(rules, "C05").state()).as("unknown %s", missing).isEqualTo(RuleState.FAIL);
            assertThat(rule(rules, "C05").message()).contains("2.5-inch");
        }
        var unsupportedProtocol = CompatibilityRules.check(parts(board,
                part(SSD, "SSD", text("formFactor", null), text("interface", null), text("protocol", "AHCI"))));
        assertThat(rule(unsupportedProtocol, "C05").state()).isEqualTo(RuleState.FAIL);
        assertThat(rule(unsupportedProtocol, "C05").message()).contains("AHCI");
    }

    @Test
    void c05PartialValuesMustMatchOneCombinationAndCanNeverDecideAPass() {
        Part board = part(MOTHERBOARD, "主板", slots(List.of(
                new String[]{"M.2 2280", "PCIe", "NVMe"}, new String[]{"2.5-inch", "SATA", "AHCI"})));
        var mixedCombination = CompatibilityRules.check(parts(board,
                part(SSD, "SSD", text("formFactor", "M.2 2280"), text("interface", null), text("protocol", "AHCI"))));
        assertThat(rule(mixedCombination, "C05").state()).isEqualTo(RuleState.FAIL);

        var possibleCombination = CompatibilityRules.check(parts(board,
                part(SSD, "SSD", text("formFactor", "M.2 2280"), text("interface", "PCIe"), text("protocol", null))));
        assertThat(rule(possibleCombination, "C05").state()).isEqualTo(RuleState.UNKNOWN);
    }

    // ---- fixtures: a part is the catalog's stored record, never a client claim ----

    static Map<HardwareCategory, Part> parts(Part... parts) {
        var map = new EnumMap<HardwareCategory, Part>(HardwareCategory.class);
        for (Part part : parts) map.put(part.category(), part);
        return map;
    }

    static Part part(HardwareCategory category, String name, Parameter... parameters) {
        ArrayNode list = json.createArrayNode();
        for (Parameter parameter : parameters) {
            var entry = list.addObject().put("code", parameter.code()).put("label", parameter.code());
            entry.set("value", parameter.value());
            entry.put("unit", "").put("sourceUrl", parameter.sourced() ? "https://example.com/source" : "").put("note", "");
        }
        return new Part(category, list);
    }

    static Parameter text(String code, String value) { return text(code, value, true); }

    static Parameter text(String code, String value, boolean sourced) {
        return new Parameter(code, value == null ? json.nullNode() : json.getNodeFactory().textNode(value), sourced);
    }

    static Parameter set(String code, List<String> values) {
        return new Parameter(code, json.valueToTree(values), true);
    }

    static Parameter number(String code, Object value) {
        return new Parameter(code, value == null ? json.nullNode() : json.valueToTree(value), true);
    }

    static Parameter slots(List<String[]> combinations) {
        ArrayNode list = json.createArrayNode();
        for (String[] combination : combinations) {
            list.addObject().put("formFactor", combination[0]).put("interface", combination[1]).put("protocol", combination[2]);
        }
        return new Parameter("ssdSlots", list, true);
    }

    static RuleResult rule(List<RuleResult> rules, String code) {
        return rules.stream().filter(rule -> rule.rule().equals(code)).findFirst().orElseThrow();
    }

    record Parameter(String code, JsonNode value, boolean sourced) {}
}
