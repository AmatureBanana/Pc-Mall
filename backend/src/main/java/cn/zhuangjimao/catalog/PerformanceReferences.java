package cn.zhuangjimao.catalog;

import cn.zhuangjimao.audit.AdministrationAudit;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static cn.zhuangjimao.catalog.CatalogAdministration.*;

/** Authoritative current reference, shared with future recommendation callers. No network calls. */
@Service
public class PerformanceReferences {
    private final ProductMapper products;
    private final CatalogAdministrationMapper admin;
    private final PerformanceMapper store;
    private final ObjectMapper json;
    private final AdministrationAudit audit;
    private static final Set<String> SCENES=Set.of("classroom","junkshop","monster");
    private static final Set<String> INPUT=Set.of("revision","valid","rawScore","sourceUrl","snapshotDate","snapshotSha256",
            "benchmarkVersion","mappingVersion","deviceName","computeType","operatingSystem","scenes","sampleCount","sampleIds","sampleConditions","matchNote","reason");

    public PerformanceReferences(ProductMapper products,CatalogAdministrationMapper admin,PerformanceMapper store,ObjectMapper json,AdministrationAudit audit) {
        this.products=products; this.admin=admin; this.store=store; this.json=json; this.audit=audit;
    }

    @Transactional(readOnly=true)
    public Score read(long id) throws Exception {
        var product=product(id);
        return evaluate(product,parameters(id),store.find(id));
    }

    /** Pure evaluation also used by batched catalog labels, with one consistent source snapshot. */
    Score evaluate(Product product,JsonNode parameters,PerformanceMapper.Stored saved) throws Exception {
        if (CapacityPerformance.supports(product.category())) return CapacityPerformance.evaluate(product.category(),parameters);
        if (!Set.of("CPU","GPU").contains(product.category())) return null;
        if (saved==null) return score("MISSING",null,null,"尚无经过核验的性能参考。");
        var reference=json.readTree(saved.referenceData());
        if (!reference.path("valid").asBoolean()) return score("INVALID",null,reference,"该性能参考已标记为无效。");
        if (!json.readTree(saved.identityData()).equals(identity(product,parameters))) return score("STALE",null,reference,"商品型号或变体参数已变化，需要重新核验评分。");
        List<String> reasons=problems(product,reference,parameters);
        if (!reasons.isEmpty()) return score("INVALID",null,reference,String.join("；",reasons));
        return score("VALID",PerformanceScores.score(product.category(),new BigDecimal(reference.path("rawScore").asText())),reference,
                product.category().equals("GPU") ? "GPU型号级Blender渲染参考，不是该板卡单卡实测或游戏FPS。" : "固定版本Blender渲染参考，不代表游戏FPS或整机性能。");
    }

    @Transactional(readOnly=true)
    public View detail(long id) throws Exception {
        var product=product(id); rendering(product);
        var saved=store.find(id);
        return new View(id,product.revision(),saved==null ? json.createObjectNode() : json.readTree(saved.referenceData()),read(id));
    }

    @Transactional(rollbackFor=Exception.class)
    public View save(long id,JsonNode input,String actor) throws Exception {
        requireFields(input,INPUT);
        var revision=input.get("revision");
        if (revision==null || !revision.isIntegralNumber() || !revision.canConvertToLong() || revision.asLong()<0) bad("请提交有效商品 revision。");
        Long current=admin.lock(id);
        if (current==null) throw new CatalogException(HttpStatus.NOT_FOUND,"PRODUCT_NOT_FOUND","商品不存在。");
        if (current!=revision.asLong()) conflict();
        var product=product(id); rendering(product);
        if (!input.path("valid").isBoolean()) bad("请明确评分是否有效。");
        var reference=((ObjectNode)input).deepCopy(); reference.remove("revision");
        for (String key:List.of("sourceUrl","snapshotDate","snapshotSha256","benchmarkVersion","mappingVersion","deviceName","computeType","operatingSystem","sampleConditions","matchNote","reason")) {
            reference.put(key,text(input,key,key.equals("sampleConditions") || key.equals("matchNote") ? 2000 : key.equals("reason") ? 500 : 1000));
        }
        if (reference.path("reason").asText().isBlank()) bad("请填写评分调整原因。");
        if (reference.path("valid").asBoolean()) {
            var reasons=problems(product,reference,parameters(id));
            if (!reasons.isEmpty()) bad(String.join("；",reasons));
        }
        var before=detail(id);
        store.save(id,json.writeValueAsString(reference),json.writeValueAsString(identity(product)));
        if (store.advance(id,revision.asLong())!=1) conflict();
        var after=detail(id);
        audit.record(actor,"PRODUCT",id,"PERFORMANCE",before,after);
        return after;
    }

    private List<String> problems(Product product,JsonNode r,JsonNode parameters) {
        List<String> reasons=new ArrayList<>();
        if (!r.path("rawScore").isTextual() || !r.path("rawScore").asText().matches("[0-9]{1,16}(\\.[0-9]{1,16})?")) reasons.add("原始分须为非负十进制字符串");
        if (!PerformanceScores.MAPPING_VERSION.equals(r.path("mappingVersion").asText())) reasons.add("映射版本须为performance-v1，不能修改冻结策略");
        if (!PerformanceScores.BLENDER_VERSION.equals(r.path("benchmarkVersion").asText())) reasons.add("基准须固定Blender 4.3.0");
        if (!"Windows".equals(r.path("operatingSystem").asText())) reasons.add("采样系统须为Windows");
        String backend=product.category().equals("CPU") ? "CPU" : product.brand().equalsIgnoreCase("AMD") ? "HIP" : "OPTIX";
        if (!backend.equals(r.path("computeType").asText())) reasons.add("计算后端与固定基准不一致");
        String source=r.path("sourceUrl").asText();
        if (!HardwareParameters.httpUrl(source) || !"opendata.blender.org".equals(java.net.URI.create(source).getHost())) reasons.add("来源须为Blender官方Open Data地址");
        if (!r.path("snapshotSha256").asText().matches("[a-fA-F0-9]{64}")) reasons.add("请提供快照SHA-256校验值");
        try { if (LocalDate.parse(r.path("snapshotDate").asText()).isAfter(LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")))) reasons.add("快照日期不能在未来"); }
        catch (java.time.DateTimeException e) { reasons.add("请提供有效快照日期"); }
        if (!r.path("scenes").isArray() || r.path("scenes").size()!=3 || !texts(r.path("scenes")).equals(SCENES)) reasons.add("须覆盖固定版本classroom、junkshop、monster完整场景");
        var count=r.path("sampleCount"); var ids=r.path("sampleIds");
        if (!count.isIntegralNumber() || !count.canConvertToInt() || count.asInt()<3 || count.asInt()>10000 || !ids.isArray() || ids.size()!=count.asInt() || texts(ids).size()!=ids.size()) reasons.add("须提供至少3条完整独立样本及对应唯一UUID");
        else for (var entry:ids) { try { if (!entry.isTextual() || !UUID.fromString(entry.asText()).toString().equalsIgnoreCase(entry.asText())) throw new IllegalArgumentException(); } catch (IllegalArgumentException e) { reasons.add("样本标识须为UUID"); break; } }
        if (r.path("sampleConditions").asText().isBlank() || r.path("matchNote").asText().isBlank()) reasons.add("请填写采样条件与型号/变体匹配依据");
        String device=r.path("deviceName").asText();
        String model=product.model();
        if (product.category().equals("CPU")) {
            String expected=product.brand().equalsIgnoreCase("Intel") && model.startsWith("i") ? "Intel Core "+model : product.brand()+" "+model;
            var cores=parameter(parameters,"cores");
            boolean amdAlias=product.brand().equalsIgnoreCase("AMD") && cores.path("value").isIntegralNumber()
                    && cores.path("value").asInt()>0 && HardwareParameters.httpUrl(cores.path("sourceUrl").asText())
                    && device.equals(expected+" "+cores.path("value").asText()+"-Core Processor");
            if (!device.equals(expected) && !amdAlias) reasons.add("CPU必须精确匹配商品型号，不能混用K/KF等变体或错误核心数");
        } else {
            String expected=product.brand()+" "+model.replace(" Founders Edition","").replace(" (AMD reference)","");
            if (!device.equals(expected) || identity(product,parameters).path("boardVariant").isNull() || identity(product,parameters).path("vram").isNull()) reasons.add("GPU型号、板卡版本和显存须明确匹配");
        }
        return reasons;
    }
    private Set<String> texts(JsonNode values) { Set<String> result=new HashSet<>(); values.forEach(v -> { if (v.isTextual()) result.add(v.asText()); }); return result; }
    private ObjectNode identity(Product product) {
        return identity(product,parameters(product.id()));
    }
    private ObjectNode identity(Product product,JsonNode parameters) {
        var result=json.createObjectNode().put("category",product.category()).put("brand",product.brand()).put("model",product.model());
        if (product.category().equals("GPU")) { result.set("boardVariant",value(parameters,"boardVariant")); result.set("vram",value(parameters,"vram")); }
        return result;
    }
    private JsonNode parameters(long id) {
        try { var spec=products.findSpecification(id); return spec==null ? json.createArrayNode() : json.readTree(spec.parameters()); }
        catch (Exception e) { throw new IllegalStateException("商品参数无法读取",e); }
    }
    private JsonNode parameter(JsonNode parameters,String code) { for (var p:parameters) if (p.path("code").asText().equals(code)) return p; return json.createObjectNode(); }
    private JsonNode value(JsonNode parameters,String code) { return parameter(parameters,code).path("value").isMissingNode() ? json.nullNode() : parameter(parameters,code).path("value"); }
    private Score score(String state,BigDecimal value,JsonNode reference,String note) { return new Score(state,value,PerformanceScores.MAPPING_VERSION,reference,note,null); }
    private Product product(long id) { var p=products.findById(id); if (p==null) throw new CatalogException(HttpStatus.NOT_FOUND,"PRODUCT_NOT_FOUND","商品不存在。"); return p; }
    private void rendering(Product p) { if (!Set.of("CPU","GPU").contains(p.category())) bad("仅CPU/GPU维护Blender性能参考，内存和SSD评分由商品参数计算。"); }
    private void bad(String message) { throw new CatalogException(HttpStatus.BAD_REQUEST,"INVALID_PERFORMANCE",message); }
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Score(String state,@JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal score,String mappingVersion,JsonNode reference,String note,
                        @JsonInclude(JsonInclude.Include.NON_NULL) @JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal performanceScore) {}
    public record View(long productId,long revision,JsonNode reference,Score performance) {}
}
