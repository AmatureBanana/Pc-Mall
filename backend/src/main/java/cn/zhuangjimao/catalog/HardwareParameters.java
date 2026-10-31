package cn.zhuangjimao.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.math.BigDecimal;
import java.util.*;
import static cn.zhuangjimao.catalog.CatalogAdministration.*;

/** One typed vocabulary shared by validation and the administration form. Null means unknown. */
final class HardwareParameters {
    private HardwareParameters() {}
    record Field(String code, String label, String type, String unit, boolean required, List<String> options) {}
    private static final List<String> SOCKETS = List.of("LGA1851","LGA1700","LGA1200","LGA1151","LGA1150","LGA1155","AM5","AM4","sTR5","sTRX4");
    private static final List<String> DDR = List.of("DDR3","DDR4","DDR5");
    private static final List<String> BOARDS = List.of("ATX","Micro-ATX","Mini-ITX","E-ATX");
    private static final List<String> OUTPUTS = List.of("HDMI","DisplayPort","DVI","VGA","USB-C");
    private static final List<String> SSD_FORMS = List.of("M.2 2230","M.2 2242","M.2 2260","M.2 2280","M.2 22110","2.5-inch","U.2","PCIe card");
    private static Field field(String code,String label,String type,String unit,boolean required,List<String> options) {
        return new Field(code,label,type,unit,required,options);
    }
    private static Field number(String code,String label,String unit,boolean required) { return field(code,label,"NUMBER",unit,required,List.of()); }
    private static Field integer(String code,String label,String unit,boolean required) { return field(code,label,"INTEGER",unit,required,List.of()); }
    private static Field choice(String code,String label,boolean required,List<String> options) { return field(code,label,"TEXT","",required,options); }
    private static Field set(String code,String label,boolean required,List<String> options) { return field(code,label,"SET","",required,options); }
    private static Field text(String code,String label) { return choice(code,label,false,List.of()); }
    static List<Field> schema(String category) {
        return switch (HardwareCategory.valueOf(category)) {
            case CPU -> List.of(choice("socket","CPU 插槽",true,SOCKETS), field("integratedGraphics","可用核显","BOOLEAN","",true,List.of()),
                    number("peakPowerEstimate","峰值功耗估算","W",true), set("memoryGenerations","支持内存代际",false,DDR),
                    integer("cores","核心数","核",false),integer("threads","线程数","线程",false),number("basePower","基础功耗","W",false),number("tdp","TDP","W",false));
            case MOTHERBOARD -> List.of(choice("socket","CPU 插槽",true,SOCKETS),choice("formFactor","主板板型",true,BOARDS),
                    set("memoryGenerations","支持内存代际",true,DDR),integer("memorySlots","内存插槽数","个",true),
                    set("displayOutputs","核显视频输出（空集合表示无）",true,OUTPUTS),set("gpuInterfaces","独显安装接口（空集合表示无）",true,List.of("PCIe x16","PCIe x8","PCIe x4")),
                    field("ssdSlots","SSD 有效安装组合","SSD_SLOTS","",true,List.of()),
                    set("ssdFormFactors","SSD 规格集合（旧资料）",false,SSD_FORMS),set("ssdInterfaces","SSD 接口集合（旧资料）",false,List.of("PCIe","SATA")),
                    set("ssdProtocols","SSD 协议集合（旧资料）",false,List.of("NVMe","AHCI")),integer("m2Slots","M.2 插槽数","个",false));
            case GPU -> List.of(text("boardVariant","显卡版本"),number("vram","显存容量","GB",false),
                    choice("installationInterface","安装接口",true,List.of("PCIe x16","PCIe x8","PCIe x4")),text("pcieGeneration","PCIe 代际"),
                    number("boardPowerEstimate","总板卡功耗估算","W",true),number("length","显卡长度","mm",true),set("displayOutputs","有效视频输出",true,OUTPUTS));
            case MEMORY -> List.of(choice("generation","内存代际",true,DDR),number("totalCapacity","套装总标称容量","GB",true),integer("stickCount","实际条数","条",true),
                    number("capacityPerStick","单条容量","GB",false),number("dataRate","数据速率","MT/s",false),number("jedecDataRate","默认数据速率","MT/s",false));
            case SSD -> List.of(number("capacity","十进制规范容量","GB",true),field("originalCapacity","原始容量","NUMBER","CAPACITY",true,List.of("GB","TB","GiB","TiB")),
                    choice("formFactor","物理规格",true,SSD_FORMS),choice("interface","连接接口",true,List.of("PCIe","SATA")),choice("protocol","协议",true,List.of("NVMe","AHCI")),
                    text("pcieGeneration","PCIe 代际"),integer("lanes","PCIe 通道数","",false));
            case PSU -> List.of(number("ratedPower","有效额定功率","W",true),text("manufacturerPartNumber","厂商型号"),choice("formFactor","电源板型",false,List.of("ATX","SFX","SFX-L","TFX")),text("atxVersion","ATX 版本"),number("length","电源长度","mm",false));
            case CASE -> List.of(set("supportedMotherboardFormFactors","支持主板板型",true,BOARDS),number("maxGpuLength","默认安装显卡限长","mm",true),
                    number("maxCoolerHeight","默认安装散热器限高","mm",true),number("maxPsuLength","电源限长","mm",false));
            case COOLER -> List.of(set("supportedSockets","支持 CPU 插槽",true,SOCKETS),number("height","散热器完整高度","mm",true),number("fanDiameter","风扇直径","mm",false));
        };
    }

    static ArrayNode validate(String category, JsonNode input, ObjectMapper json) {
        if (input == null || !input.isArray() || input.size() > 60) invalid("parameters 必须为最多60项的数组。");
        Map<String,Field> fields = new HashMap<>();
        schema(category).forEach(field -> fields.put(field.code(),field));
        Set<String> seen = new HashSet<>();
        var result = json.createArrayNode();
        for (JsonNode entry : input) {
            requireFields(entry,Set.of("code","label","value","unit","sourceUrl","note"));
            String code = CatalogAdministration.text(entry,"code",60);
            Field field = fields.get(code);
            if (field == null || !seen.add(code)) invalid("参数未知、类别不适用或重复：" + code);
            String unit = CatalogAdministration.text(entry,"unit",20);
            if (!(field.unit().equals("CAPACITY") ? field.options().contains(unit) : field.unit().equals(unit))) invalid(code + " 单位无效，应使用 " + field.unit());
            if (!entry.has("value")) invalid(code + " 缺少 value；未知值请显式使用 null。");
            JsonNode value = entry.get("value");
            if (!value.isNull()) {
                switch (field.type()) {
                    case "NUMBER", "INTEGER" -> {
                        if (!value.isNumber() || value.decimalValue().signum() <= 0 || value.decimalValue().compareTo(new BigDecimal("1000000000")) > 0
                                || field.type().equals("INTEGER") && !value.isIntegralNumber()) invalid(code + " 必须为有效正" + (field.type().equals("INTEGER") ? "整数。" : "数。"));
                    }
                    case "BOOLEAN" -> { if (!value.isBoolean()) invalid(code + " 必须为布尔值。"); }
                    case "TEXT" -> { if (!value.isTextual() || value.asText().isBlank() || value.asText().length() > 100
                            || !field.options().isEmpty() && !field.options().contains(value.asText())) invalid(code + " 取值无效。"); }
                    case "SET" -> validateSet(code,value,field.options());
                    case "SSD_SLOTS" -> validateSlots(value);
                    default -> throw new IllegalStateException("Unknown parameter type");
                }
            }
            String source = CatalogAdministration.text(entry,"sourceUrl",1000);
            if (!source.isEmpty() && !httpUrl(source)) invalid(code + " 来源地址必须为 HTTP(S)。");
            result.addObject().put("code",code).put("label",field.label()).set("value",value);
            var normalized = (com.fasterxml.jackson.databind.node.ObjectNode)result.get(result.size()-1);
            normalized.put("unit",unit).put("sourceUrl",source).put("note",CatalogAdministration.text(entry,"note",1000));
        }
        validateCapacities(category,result);
        return result;
    }
    private static void validateSet(String code, JsonNode value, List<String> options) {
        if (!value.isArray() || value.size() > 30) invalid(code + " 必须为集合数组。");
        Set<String> seen = new HashSet<>();
        for (var entry : value) if (!entry.isTextual() || !options.contains(entry.asText()) || !seen.add(entry.asText())) invalid(code + " 集合包含重复或非法值。");
    }
    private static void validateSlots(JsonNode value) {
        if (!value.isArray() || value.size() > 30) invalid("ssdSlots 必须为安装组合数组。");
        Set<String> seen = new HashSet<>();
        for (var slot : value) {
            requireFields(slot,Set.of("formFactor","interface","protocol"));
            String form = CatalogAdministration.text(slot,"formFactor",50), connection = CatalogAdministration.text(slot,"interface",20), protocol = CatalogAdministration.text(slot,"protocol",20);
            if (!SSD_FORMS.contains(form) || !List.of("PCIe","SATA").contains(connection) || !List.of("NVMe","AHCI").contains(protocol)
                    || !seen.add(form + "/" + connection + "/" + protocol)
                    || connection.equals("SATA") && !protocol.equals("AHCI") || connection.equals("PCIe") && !protocol.equals("NVMe")) invalid("SSD 安装组合无效或重复。");
        }
    }
    private static void validateCapacities(String category, ArrayNode parameters) {
        Map<String,JsonNode> values = values(parameters);
        if (category.equals("MEMORY") && known(values,"totalCapacity","stickCount","capacityPerStick")) {
            var capacity = values.get("stickCount").decimalValue().multiply(values.get("capacityPerStick").decimalValue());
            if (capacity.compareTo(values.get("totalCapacity").decimalValue()) != 0) invalid("内存总容量必须等于实际条数乘以单条容量。");
        }
        if (category.equals("SSD") && known(values,"capacity","originalCapacity")) {
            String unit = "";
            for (var p : parameters) if (p.path("code").asText().equals("originalCapacity")) unit = p.path("unit").asText();
            BigDecimal factor = switch (unit) { case "TB" -> new BigDecimal("1000"); case "GiB" -> new BigDecimal("1.073741824"); case "TiB" -> new BigDecimal("1099.511627776"); default -> BigDecimal.ONE; };
            if (values.get("originalCapacity").decimalValue().multiply(factor).compareTo(values.get("capacity").decimalValue()) != 0) invalid("SSD 规范 GB 容量与原始数值、单位换算不一致。");
        }
    }
    private static boolean known(Map<String,JsonNode> values, String... keys) { return Arrays.stream(keys).allMatch(key -> values.containsKey(key) && !values.get(key).isNull()); }
    private static Map<String,JsonNode> values(JsonNode parameters) {
        Map<String,JsonNode> values = new HashMap<>();
        parameters.forEach(p -> values.put(p.path("code").asText(),p.path("value")));
        return values;
    }
    static List<String> missing(String category, JsonNode parameters) {
        List<String> reasons = new ArrayList<>();
        var values = values(parameters);
        for (var field : schema(category)) {
            if (field.required() && (!known(values,field.code()) || values.get(field.code()).isArray() && values.get(field.code()).isEmpty()
                    && !Set.of("displayOutputs","gpuInterfaces").contains(field.code()))) reasons.add(field.code() + "（" + field.label() + "）缺少已核验值");
        }
        for (var p : parameters) if (!p.path("value").isNull() && p.path("sourceUrl").asText().isBlank()) reasons.add(p.path("code").asText() + " 缺少参数来源");
        return reasons;
    }
    static boolean httpUrl(String value) {
        try { var uri = java.net.URI.create(value); return Set.of("http","https").contains(uri.getScheme()) && uri.getHost() != null && uri.getUserInfo() == null; }
        catch (IllegalArgumentException | NullPointerException e) { return false; }
    }
}
