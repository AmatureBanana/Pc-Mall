package cn.zhuangjimao.compatibility;

import cn.zhuangjimao.catalog.HardwareCategory;
import cn.zhuangjimao.catalog.HardwareParameters;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.math.BigDecimal;
import java.math.RoundingMode;
import static cn.zhuangjimao.catalog.HardwareCategory.PSU;

import static cn.zhuangjimao.catalog.HardwareCategory.CASE;
import static cn.zhuangjimao.catalog.HardwareCategory.COOLER;
import static cn.zhuangjimao.catalog.HardwareCategory.CPU;
import static cn.zhuangjimao.catalog.HardwareCategory.MEMORY;
import static cn.zhuangjimao.catalog.HardwareCategory.MOTHERBOARD;
import static cn.zhuangjimao.catalog.HardwareCategory.SSD;
import static cn.zhuangjimao.catalog.HardwareCategory.GPU;

/**
 * The nine active core rules; C07 graphics-output validation was removed by product decision. Every input is a server-side catalog record: a client never supplies
 * parameters, categories or a pass flag. A part that is absent or unavailable is PENDING, a
 * parameter without a verified value or source is UNKNOWN, and only two known values can decide
 * PASS or FAIL. Each rule reports its own result, so one failure never clears the others.
 */
public final class CompatibilityRules {
    /** One catalog field a rule reads: the part category plus the parameter code. */
    record Field(HardwareCategory category, String code) {
        static Field of(HardwareCategory category, String code) { return new Field(category, code); }
    }

    private CompatibilityRules() {}

    static List<RuleResult> check(Map<HardwareCategory, Part> parts) {
        return check(parts, parts.containsKey(GPU));
    }

    static List<RuleResult> check(Map<HardwareCategory, Part> parts, boolean gpuSelected) {
        var connection = graphicsInterface(parts, gpuSelected);
        var length = space(parts, "C09", GPU, "length", "maxGpuLength", gpuSelected);
        return List.of(socket(parts), memory(parts), cooler(parts), boardFormFactor(parts), storage(parts), connection,
                power(parts, gpuSelected), length,
                space(parts, "C10", COOLER, "height", "maxCoolerHeight", gpuSelected));
    }

    private static RuleResult space(Map<HardwareCategory, Part> parts, String rule, HardwareCategory category, String size, String limit, boolean gpuSelected) {
        var read = new Read(parts, Field.of(category, size), Field.of(CASE, limit));
        if (category == GPU && !gpuSelected) return read.result(rule, RuleState.NOT_APPLICABLE, "未选择独显，显卡长度检查不适用。");
        if (!read.allPresent()) return read.pending(rule);
        if (!read.allUsable()) return read.unknown(rule, Labels.of(category) + "尺寸或机箱有效限制缺少已核验值或来源，无法判断。");
        BigDecimal actual = read.decimal(category, size), maximum = read.decimal(CASE, limit);
        return read.result(rule, actual.compareTo(maximum) <= 0 ? RuleState.PASS : RuleState.FAIL,
                Labels.of(category) + "尺寸 " + actual.toPlainString() + " mm，机箱默认安装有效限制 " + maximum.toPlainString() + " mm；尺寸不超过限制才通过。");
    }

    private static RuleResult power(Map<HardwareCategory, Part> parts, boolean gpuSelected) {
        var fields = new ArrayList<Field>(List.of(Field.of(CPU, "peakPowerEstimate"), Field.of(PSU, "ratedPower")));
        if (gpuSelected) fields.add(Field.of(GPU, "boardPowerEstimate"));
        var read = new Read(parts, fields.toArray(Field[]::new));
        if (!read.allPresent()) return read.pending("C08");
        if (!read.allUsable()) return read.unknown("C08", "CPU 峰值功耗、显卡总板功耗或电源额定功率缺少已核验值或来源，不能以 TDP 代替峰值。");
        BigDecimal cpu = read.decimal(CPU, "peakPowerEstimate");
        BigDecimal gpu = parts.containsKey(GPU) ? read.decimal(GPU, "boardPowerEstimate") : BigDecimal.ZERO;
        BigDecimal required = cpu.add(gpu).add(new BigDecimal("80")).multiply(new BigDecimal("1.30")).setScale(0, RoundingMode.CEILING);
        BigDecimal rated = read.decimal(PSU, "ratedPower");
        return read.result("C08", rated.compareTo(required) >= 0 ? RuleState.PASS : RuleState.FAIL,
                "最低需求 ceil(1.30 × (" + cpu.toPlainString() + " + " + gpu.toPlainString() + " + 80)) = "
                        + required.toPlainString() + " W；电源额定 " + rated.toPlainString() + " W，额定功率大于或等于需求才通过。");
    }

    private static RuleResult graphicsInterface(Map<HardwareCategory, Part> parts, boolean gpuSelected) {
        var read = new Read(parts, Field.of(GPU, "installationInterface"), Field.of(MOTHERBOARD, "gpuInterfaces"));
        if (!gpuSelected) return read.result("C06", RuleState.NOT_APPLICABLE, "未选择独显，安装接口检查不适用。");
        if (!read.allPresent()) return read.pending("C06");
        if (!read.allUsable()) return read.unknown("C06", "显卡安装接口或主板可用插槽缺少已核验值或来源，无法判断。");
        String connection = read.value(GPU, "installationInterface");
        return read.result("C06", read.values(MOTHERBOARD, "gpuInterfaces").contains(connection) ? RuleState.PASS : RuleState.FAIL,
                "显卡安装接口为 " + connection + "，主板支持：" + String.join("、", read.values(MOTHERBOARD, "gpuInterfaces")) + "；仅比较安装接口，不以 PCIe 代际差异判定冲突。");
    }

    /** C01: the CPU socket must equal the motherboard socket. */
    private static RuleResult socket(Map<HardwareCategory, Part> parts) {
        var read = new Read(parts, Field.of(CPU, "socket"), Field.of(MOTHERBOARD, "socket"));
        if (!read.allPresent()) return read.pending("C01");
        if (!read.allUsable()) return read.unknown("C01", "CPU 或主板的插槽缺少已核验值或来源，无法判断。");
        String cpu = read.value(CPU, "socket"), board = read.value(MOTHERBOARD, "socket");
        if (cpu.equals(board)) return read.result("C01", RuleState.PASS, "CPU 与主板插槽一致：" + cpu + "。");
        return read.result("C01", RuleState.FAIL, "CPU 插槽为 " + cpu + "，主板插槽为 " + board + "，无法安装。");
    }

    /** C02: the memory generation must be supported and the installed sticks must fit the slots. */
    private static RuleResult memory(Map<HardwareCategory, Part> parts) {
        var read = new Read(parts,
                Field.of(MOTHERBOARD, "memoryGenerations"), Field.of(MOTHERBOARD, "memorySlots"),
                Field.of(MEMORY, "generation"), Field.of(MEMORY, "stickCount"));
        if (!read.allPresent()) return read.pending("C02");
        String generation = read.value(MEMORY, "generation");
        List<String> generations = read.values(MOTHERBOARD, "memoryGenerations");
        if (read.usable(MEMORY, "generation") && read.usable(MOTHERBOARD, "memoryGenerations")
                && !generations.contains(generation)) {
            return read.result("C02", RuleState.FAIL,
                    "内存为 " + generation + "，主板支持 " + String.join("、", generations) + "，不能安装。");
        }
        if (read.usable(MOTHERBOARD, "memorySlots") && read.usable(MEMORY, "stickCount")) {
            long slots = read.integer(MOTHERBOARD, "memorySlots");
            long sticks = read.integer(MEMORY, "stickCount") * parts.get(MEMORY).quantity();
            if (sticks > slots) return read.result("C02", RuleState.FAIL,
                        "已选内存共 " + sticks + " 条，主板仅有 " + slots + " 个内存插槽，无法全部安装。");
        }
        if (!read.allUsable()) return read.unknown("C02", "内存代际、插槽数或内存条数缺少已核验值或来源，无法判断。");
        long slots = read.integer(MOTHERBOARD, "memorySlots");
        long sticks = read.integer(MEMORY, "stickCount") * parts.get(MEMORY).quantity();
        return read.result("C02", RuleState.PASS,
                generation + " 内存共 " + sticks + " 条，可装入 " + slots + " 个插槽。");
    }

    /** C03: the CPU socket must appear in the cooler's verified support list. */
    private static RuleResult cooler(Map<HardwareCategory, Part> parts) {
        var read = new Read(parts, Field.of(CPU, "socket"), Field.of(COOLER, "supportedSockets"));
        if (!read.allPresent()) return read.pending("C03");
        if (!read.allUsable()) return read.unknown("C03", "CPU 插槽或散热器支持清单缺少已核验值或来源，无法判断。");
        String socket = read.value(CPU, "socket");
        List<String> supported = read.values(COOLER, "supportedSockets");
        if (supported.contains(socket)) return read.result("C03", RuleState.PASS, "散热器支持 " + socket + " 插槽。");
        return read.result("C03", RuleState.FAIL,
                "CPU 插槽为 " + socket + "，散热器仅支持 " + String.join("、", supported) + "。");
    }

    /** C04: the case's supported-board set must contain the motherboard form factor. */
    private static RuleResult boardFormFactor(Map<HardwareCategory, Part> parts) {
        var read = new Read(parts, Field.of(MOTHERBOARD, "formFactor"), Field.of(CASE, "supportedMotherboardFormFactors"));
        if (!read.allPresent()) return read.pending("C04");
        if (!read.allUsable()) return read.unknown("C04", "主板板型或机箱支持板型集合缺少已核验值或来源，无法判断。");
        String formFactor = read.value(MOTHERBOARD, "formFactor");
        List<String> supported = read.values(CASE, "supportedMotherboardFormFactors");
        if (supported.contains(formFactor)) return read.result("C04", RuleState.PASS, "机箱支持 " + formFactor + " 板型。");
        return read.result("C04", RuleState.FAIL,
                "主板为 " + formFactor + "，机箱仅支持 " + String.join("、", supported) + "。");
    }

    /**
     * C05: one listed motherboard installation combination must match the SSD's form factor,
     * interface and protocol together. Never a cartesian product of the three legacy sets.
     */
    private static RuleResult storage(Map<HardwareCategory, Part> parts) {
        var read = new Read(parts,
                Field.of(MOTHERBOARD, "ssdSlots"),
                Field.of(SSD, "formFactor"), Field.of(SSD, "interface"), Field.of(SSD, "protocol"));
        if (!read.allPresent()) return read.pending("C05");
        if (!read.usable(MOTHERBOARD, "ssdSlots")) return read.unknown("C05", "主板的 SSD 安装组合缺少已核验值或来源，无法判断。");
        boolean knownForm = read.usable(SSD, "formFactor");
        boolean knownConnection = read.usable(SSD, "interface");
        boolean knownProtocol = read.usable(SSD, "protocol");
        String formFactor = read.value(SSD, "formFactor");
        String connection = read.value(SSD, "interface");
        String protocol = read.value(SSD, "protocol");
        var slots = read.installationCombinations(MOTHERBOARD, "ssdSlots");
        if (slots == null) return read.unknown("C05", "主板的 SSD 安装组合格式无效，无法判断。");
        boolean formListed = false, possibleCombination = false;
        for (var slot : slots) {
            if (knownForm && !slot.formFactor().equals(formFactor)) continue;
            formListed = true;
            if ((!knownConnection || slot.interfaceName().equals(connection))
                    && (!knownProtocol || slot.protocol().equals(protocol))) possibleCombination = true;
        }
        if (!formListed) return read.result("C05", RuleState.FAIL,
                "主板未提供 " + formFactor + " 安装位，可安装组合为：" + describeSlots(slots) + "。");
        if (!possibleCombination) {
            var known = new ArrayList<String>();
            if (knownForm) known.add(formFactor);
            if (knownConnection) known.add(connection);
            if (knownProtocol) known.add(protocol);
            return read.result("C05", RuleState.FAIL,
                    "主板未提供同时满足 SSD 已核验条件（" + String.join(" · ", known)
                            + "）的安装组合，可安装组合为：" + describeSlots(slots) + "。");
        }
        if (!read.allUsable()) return read.unknown("C05", "SSD 安装组合或硬盘的规格、接口、协议缺少已核验值或来源，无法判断。");
        return read.result("C05", RuleState.PASS,
                "主板提供 " + formFactor + " · " + connection + " · " + protocol + " 的有效安装组合。");
    }

    private static String describeSlots(List<Installation> slots) {
        var described = new ArrayList<String>();
        for (var slot : slots) described.add(slot.formFactor() + " · " + slot.interfaceName() + " · " + slot.protocol());
        return String.join("、", described) + "。";
    }

    /** One verified motherboard SSD installation combination from the catalog's ssdSlots array. */
    record Installation(String formFactor, String interfaceName, String protocol) {}

    /**
     * The explicit set of catalog fields one rule reads, so reported details never invent a
     * combination the catalog does not store.
     */
    static final class Read {
        private final Map<HardwareCategory, Part> parts;
        private final List<Field> fields;
        private final List<String> codes;

        Read(Map<HardwareCategory, Part> parts, Field... fields) {
            this.parts = parts;
            this.fields = List.of(fields);
            var distinct = new LinkedHashSet<String>();
            for (Field field : fields) distinct.add(field.code());
            this.codes = List.copyOf(distinct);
        }

        boolean allPresent() { return categories().stream().allMatch(parts::containsKey); }

        boolean allUsable() { return fields.stream().allMatch(field -> usable(field.category(), field.code())); }

        /**
         * A value counts only when it is present, well-formed and carries a verified source. A
         * malformed array or a non-integral number is unusable rather than an exception, so bad
         * catalog data degrades to UNKNOWN instead of a server error.
         */
        boolean usable(HardwareCategory category, String code) {
            JsonNode value = field(category, code);
            JsonNode parameter = parameter(category, code);
            if (!HardwareParameters.isValidValue(category, code, value) || value.isArray() && value.isEmpty()
                    && !List.of("displayOutputs", "gpuInterfaces").contains(code)) return false;
            return parameter != null && HardwareParameters.httpUrl(parameter.path("sourceUrl").asText());
        }

        String value(HardwareCategory category, String code) {
            JsonNode value = field(category, code);
            return value != null && value.isValueNode() && !value.isNull() ? value.asText() : null;
        }

        List<String> values(HardwareCategory category, String code) {
            JsonNode value = field(category, code);
            if (value == null || !value.isArray()) return List.of();
            var list = new ArrayList<String>();
            for (JsonNode entry : value) if (entry.isTextual()) list.add(entry.asText());
            return list;
        }

        /** Parsed ssdSlots combinations, or null when any entry is malformed. */
        List<Installation> installationCombinations(HardwareCategory category, String code) {
            JsonNode value = field(category, code);
            if (value == null || !value.isArray()) return null;
            var list = new ArrayList<Installation>();
            for (JsonNode entry : value) {
                if (!isInstallation(entry)) return null;
                list.add(new Installation(entry.path("formFactor").asText(), entry.path("interface").asText(),
                        entry.path("protocol").asText()));
            }
            return list;
        }

        long integer(HardwareCategory category, String code) { return field(category, code).asLong(); }
        BigDecimal decimal(HardwareCategory category, String code) { return field(category, code).decimalValue(); }

        JsonNode field(HardwareCategory category, String code) {
            JsonNode parameter = parameter(category, code);
            return parameter == null ? null : parameter.get("value");
        }

        JsonNode parameter(HardwareCategory category, String code) {
            Part part = parts.get(category);
            if (part == null || part.parameters() == null || !part.parameters().isArray()) return null;
            for (JsonNode parameter : part.parameters()) if (parameter.path("code").asText().equals(code)) return parameter;
            return null;
        }

        List<HardwareCategory> categories() {
            return fields.stream().map(Field::category).distinct().toList();
        }

        /**
         * A part this rule needs is absent — either never selected or no longer usable. The
         * caller's missing list already states which, so the message names the categories in
         * readable form rather than leaking enum codes.
         */
        RuleResult pending(String rule) {
            String unavailable = categories().stream()
                    .filter(category -> !parts.containsKey(category))
                    .map(Labels::of).collect(Collectors.joining("、"));
            return result(rule, RuleState.PENDING, "尚未选择或当前不可用：" + unavailable + "，无法完成本次检查。");
        }

        RuleResult unknown(String rule, String message) { return result(rule, RuleState.UNKNOWN, message); }

        RuleResult result(String rule, RuleState state, String message) {
            var details = new ArrayList<RuleResult.RuleDetail>();
            for (Field field : fields) {
                if (!parts.containsKey(field.category())) continue;
                details.add(new RuleResult.RuleDetail(field.category(), field.code(),
                        display(field(field.category(), field.code()))));
            }
            return new RuleResult(rule, state, categories(), codes, message, List.copyOf(details));
        }

        private static boolean isInstallation(JsonNode entry) {
            return entry.path("formFactor").isTextual() && entry.path("interface").isTextual()
                    && entry.path("protocol").isTextual();
        }

        private String display(JsonNode value) {
            if (value == null || value.isNull()) return null;
            if (value.isArray()) {
                var list = new ArrayList<String>();
                for (JsonNode entry : value) {
                    if (entry.isTextual()) list.add(entry.asText());
                    else if (isInstallation(entry)) list.add(entry.path("formFactor").asText() + " · "
                            + entry.path("interface").asText() + " · " + entry.path("protocol").asText());
                }
                return String.join("、", list);
            }
            return value.asText();
        }
    }
}
