package cn.zhuangjimao.catalog;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.RoundingMode;
import java.util.*;
import java.util.regex.Pattern;

/** Public catalog summaries, platform filters and labels from current product data. */
@Service
class ProductPerformanceTags {
    private static final Pattern NVIDIA_GPU = Pattern.compile("\\b(?:GEFORCE|NVIDIA|QUADRO)\\b");
    private static final Pattern AMD_GPU = Pattern.compile("\\b(?:RADEON|AMD)\\b");
    private static final Pattern INTEL_GPU = Pattern.compile("\\b(?:ARC|INTEL)\\b");
    private static final Pattern HEAT_PIPES = Pattern.compile("(?:\\d+|[一二三四五六七八九十]+)热管");
    private static final Set<String> COOLER_TYPES = Set.of("塔式风冷","单塔风冷","双塔风冷","下压式风冷","风冷散热器");
    private static final Set<String> PSU_MODULARITY = Set.of("全模组","半模组","非模组","直出线");
    private static final List<String> CASE_FORM_FACTORS = List.of("E-ATX","ATX","Micro-ATX","Mini-ITX");
    private final ProductMapper products;
    private final PerformanceReferences performance;
    private final ObjectMapper json;
    ProductPerformanceTags(ProductMapper products,PerformanceReferences performance,ObjectMapper json) {
        this.products=products; this.performance=performance; this.json=json;
    }

    @Transactional(readOnly=true)
    public List<TaggedProduct> decorate(List<Product> items) throws Exception {
        if (items.isEmpty()) return List.of();
        Map<Long,ProductMapper.TagSource> sources=new HashMap<>();
        products.tagSources(items.stream().map(Product::id).toList()).forEach(source -> sources.put(source.productId(),source));
        var result=new ArrayList<TaggedProduct>();
        for (var product:items) {
            var source=sources.get(product.id());
            JsonNode parameters=source==null || source.parameters()==null ? json.createArrayNode() : json.readTree(source.parameters());
            var reference=source==null || source.referenceData()==null ? null : new PerformanceMapper.Stored(source.referenceData(),source.identityData());
            Map<String,JsonNode> fields=new HashMap<>();
            parameters.forEach(field -> fields.put(field.path("code").asText(),field));
            var platform=platform(product,fields);
            var displayProduct=withDisplaySummary(product,fields,platform);
            result.add(new TaggedProduct(displayProduct,labels(product,fields,performance.evaluate(product,parameters,reference)),platform));
        }
        return result;
    }

    List<Tag> forProduct(Product product) throws Exception { return decorate(List.of(product)).get(0).performanceTags(); }

    private String platform(Product product,Map<String,JsonNode> fields) {
        if (product.category().equals("MOTHERBOARD")) {
            return HardwareParameters.socketPlatform(textValue(fields,"socket",""));
        }
        if (!product.category().equals("GPU")) return null;
        var chipset=known(fields,"gpuChipset");
        // Legacy GPU products store the chipset family in the model name.
        String model=(chipset==null ? product.model() : chipset.path("value").asText()).toUpperCase(Locale.ROOT);
        if (NVIDIA_GPU.matcher(model).find()) return "NVIDIA";
        if (AMD_GPU.matcher(model).find()) return "AMD";
        if (INTEL_GPU.matcher(model).find()) return "Intel";
        return null;
    }

    private Product withDisplaySummary(Product product,Map<String,JsonNode> fields,String platform) {
        return switch (product.category()) {
            case "CPU" -> {
                String socket=textValue(fields,"socket","插槽待核验");
                String label=HardwareParameters.socketLabel(socket);
                yield product.withSummary(label.equals(socket) ? product.brand()+" · "+socket : label);
            }
            case "MOTHERBOARD" -> {
                var field=known(fields,"memorySlots");
                var slots=field==null ? null : field.path("value");
                var slotLabel=slots!=null && slots.isIntegralNumber() && slots.canConvertToInt() && slots.intValue()>0
                        ? slots.intValue()+"个内存插槽" : "内存插槽数待核验";
                yield product.withSummary(String.join(" · ",platform==null ? "平台待核验" : platform+"平台",
                        textValue(fields,"formFactor","板型待核验"),slotLabel));
            }
            case "COOLER" -> {
                var tokens=summaryTokens(product);
                var type=tokens.stream().filter(COOLER_TYPES::contains).findFirst().orElse("");
                var pipes=tokens.stream().filter(token -> HEAT_PIPES.matcher(token).matches()).findFirst().orElse("");
                yield summarize(product,type,pipes,summaryNumber(fields,"fanDiameter","mm","","mm风扇"));
            }
            case "MEMORY" -> {
                var speed=summaryNumber(fields,"dataRate","MT/s","","");
                var timingSpeed=summaryNumber(fields,"timingDataRate","MT/s","","");
                var latency=speed.isEmpty() || speed.equals(timingSpeed)
                        ? summaryNumber(fields,"casLatency","周期","CL","") : "";
                var sticks=summaryNumber(fields,"stickCount","条","","");
                yield summarize(product,textValue(fields,"generation",""),speed.isEmpty() ? "" : speed+" MT/s",
                        latency,sticks.isEmpty() ? "" : sticks.equals("1") ? "单根" : sticks+" 根套装");
            }
            case "GPU" -> summarize(product,
                    textValue(fields,"gpuChipset",product.model().replace(" Founders Edition","").replace(" (AMD reference)","")),
                    summaryNumber(fields,"vram","GB",""," GB"),
                    summaryNumber(fields,"length","mm",""," mm"));
            case "SSD" -> {
                var connection=textValue(fields,"interface","");
                var generation=textValue(fields,"pcieGeneration","");
                var lanes=summaryNumber(fields,"lanes",""," ×","");
                if (connection.equals("PCIe")) connection+=(generation.isEmpty() ? "" : " "+generation)+lanes;
                var original=known(fields,"originalCapacity");
                var unit=original==null ? "" : original.path("unit").asText();
                var capacity=Set.of("GB","TB","GiB","TiB").contains(unit)
                        ? summaryNumber(fields,"originalCapacity",unit,""," "+unit) : "";
                if (capacity.isEmpty()) capacity=summaryNumber(fields,"capacity","GB",""," GB");
                yield summarize(product,textValue(fields,"formFactor",""),connection,capacity);
            }
            case "PSU" -> {
                var modularity=textValue(fields,"cableModularity","");
                if (modularity.isEmpty()) {
                    modularity=summaryTokens(product).stream().filter(PSU_MODULARITY::contains).findFirst().orElse("模组待核验");
                }
                if (modularity.equals("非模组")) modularity="直出线";
                yield summarize(product,summaryNumber(fields,"ratedPower","W","","W"),modularity,textValue(fields,"formFactor",""));
            }
            case "CASE" -> {
                var supported=known(fields,"supportedMotherboardFormFactors");
                var boards=new HashSet<String>();
                if (supported!=null && supported.path("value").isArray()) {
                    supported.path("value").forEach(board -> boards.add(board.asText()));
                }
                var formFactor=CASE_FORM_FACTORS.stream().filter(boards::contains).findFirst().orElse("");
                yield summarize(product,formFactor,summaryNumber(fields,"maxGpuLength","mm","显卡限长 "," mm"),
                        summaryNumber(fields,"maxCoolerHeight","mm","散热器限高 "," mm"));
            }
            default -> product;
        };
    }

    private List<String> summaryTokens(Product product) {
        return Arrays.stream(product.summary().split("·")).map(String::strip).toList();
    }

    private Product summarize(Product product,String... parts) {
        var summary=String.join(" · ",Arrays.stream(parts).filter(part -> !part.isBlank()).toList());
        return summary.isEmpty() ? product : product.withSummary(summary);
    }

    private String summaryNumber(Map<String,JsonNode> fields,String code,String unit,String prefix,String suffix) {
        var field=known(fields,code);
        if (field==null || !unit.equals(field.path("unit").asText())) return "";
        var value=field.path("value");
        return value.isNumber() && value.decimalValue().signum()>0
                ? prefix+value.decimalValue().stripTrailingZeros().toPlainString()+suffix : "";
    }

    private List<Tag> labels(Product product,Map<String,JsonNode> fields,PerformanceReferences.Score score) {
        var tags=new ArrayList<Tag>();
        if (score!=null) {
            boolean capacity=CapacityPerformance.supports(product.category());
            if (score.state().equals("VALID")) tags.add(new Tag("score",(capacity ? "综合评分 " : "渲染参考 ")+score.score().setScale(1,RoundingMode.HALF_UP).toPlainString()+"分",
                    "SCORE",score.reference().path("sourceUrl").asText(),score.note()));
            else {
                var estimate=CatalogScoreEstimate.tag(product.category(),fields,score.note());
                tags.add(estimate!=null ? estimate : new Tag("score",capacity ? "综合评分待核验" : "渲染评分待核验","UNVERIFIED","",score.note()));
            }
        }
        switch (product.category()) {
            case "CPU" -> {
                number(tags,fields,"cores","","核","核","");
                number(tags,fields,"threads","","线程","线程","");
            }
            case "GPU" -> {
                number(tags,fields,"vram","","GB显存","GB","");
                number(tags,fields,"boardPowerEstimate","板卡功耗","W","W","厂商总板卡功耗，不是整机功耗。");
            }
            case "MEMORY" -> {
                number(tags,fields,"totalCapacity","","GB内存","GB","按当前商品的标称内存容量计算。");
                text(tags,fields,"generation","","代际规格不代表超频或整机运行速度已验证。");
            }
            case "SSD" -> {
                number(tags,fields,"capacity","","GB SSD","GB","十进制标称容量，1TB=1000GB；原始单位见分类参数。");
                text(tags,fields,"protocol","","协议规格，实际安装能力须由兼容检查确认。");
            }
            case "MOTHERBOARD" -> {
                set(tags,fields,"memoryGenerations","","支持的内存代际，不代表整机兼容已通过。");
                number(tags,fields,"memorySlots","","个内存槽","个","");
                number(tags,fields,"m2Slots","标称","个M.2槽","个","厂商标称槽数；实际可用性受CPU型号、共享资源和安装条件影响。");
            }
            case "PSU" -> {
                number(tags,fields,"ratedPower","额定","W","W","额定功率，不是整机功耗或能效评分。");
                text(tags,fields,"atxVersion","ATX ","电源标准版本，不是主板板型。");
            }
            case "CASE" -> {
                number(tags,fields,"maxGpuLength","显卡限长","mm","mm","默认安装条件；前置冷排等额外占位需要另行核验。");
                number(tags,fields,"maxCoolerHeight","散热限高","mm","mm","默认安装条件下的散热器高度限制。");
            }
            case "COOLER" -> {
                number(tags,fields,"height","高度","mm","mm","完整散热器高度，不是裸塔高度，不代表散热能力已验证。");
                set(tags,fields,"supportedSockets","支持","插槽支持清单，不是完整装机兼容结论。");
            }
            default -> { }
        }
        if (tags.isEmpty()) tags.add(new Tag("missing","能力资料待核验","UNVERIFIED","","缺少有来源的结构化参数。"));
        return List.copyOf(tags);
    }

    private JsonNode known(Map<String,JsonNode> fields,String code) {
        JsonNode field=fields.get(code);
        return field!=null && !field.path("value").isNull() && !field.path("value").isMissingNode() && HardwareParameters.httpUrl(field.path("sourceUrl").asText()) ? field : null;
    }

    private String textValue(Map<String,JsonNode> fields,String code,String fallback) {
        var field=known(fields,code);
        if (field==null) return fallback;
        var value=field.path("value").textValue();
        return value==null || value.isBlank() ? fallback : value;
    }
    private void number(List<Tag> tags,Map<String,JsonNode> fields,String code,String prefix,String suffix,String unit,String note) {
        var field=known(fields,code);
        if (field==null || !field.path("value").isNumber() || field.path("value").decimalValue().signum()<=0 || !field.path("unit").asText().equals(unit)) return;
        add(tags,code,prefix+field.path("value").decimalValue().stripTrailingZeros().toPlainString()+suffix,field,note);
    }
    private void text(List<Tag> tags,Map<String,JsonNode> fields,String code,String prefix,String note) {
        var field=known(fields,code);
        if (field==null || !field.path("value").isTextual() || field.path("value").asText().isBlank()) return;
        add(tags,code,prefix+field.path("value").asText(),field,note);
    }
    private void set(List<Tag> tags,Map<String,JsonNode> fields,String code,String prefix,String note) {
        var field=known(fields,code);
        if (field==null || !field.path("value").isArray() || field.path("value").isEmpty()) return;
        var values=new ArrayList<String>();
        for (var value:field.path("value")) { if (!value.isTextual() || value.asText().isBlank()) return; values.add(value.asText()); }
        String label;
        if (code.equals("supportedSockets")) {
            var preferred=values.stream().filter(v -> Set.of("LGA1700","AM5").contains(v)).toList();
            label=String.join(" / ",preferred.isEmpty() ? values.subList(0,Math.min(2,values.size())) : preferred);
            note+=" 完整清单："+String.join("、",values);
        } else label=String.join(" / ",values);
        add(tags,code,prefix+label,field,note);
    }
    private void add(List<Tag> tags,String code,String label,JsonNode field,String note) {
        String sourceNote=field.path("note").asText();
        tags.add(new Tag(code,label,"CAPABILITY",field.path("sourceUrl").asText(),note+(sourceNote.isBlank() ? "" : " "+sourceNote)));
    }
    record Tag(String code,String label,String kind,String sourceUrl,String note) {}
    record TaggedProduct(@JsonUnwrapped Product product,List<Tag> performanceTags,String platform) {}
}
