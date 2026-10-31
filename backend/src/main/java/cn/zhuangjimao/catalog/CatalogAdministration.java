package cn.zhuangjimao.catalog;

import cn.zhuangjimao.audit.AdministrationAudit;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;

@Service
class CatalogAdministration {
    private final CatalogAdministrationMapper admin;
    private final ProductMapper products;
    private final ObjectMapper json;
    private final AdministrationAudit audit;

    CatalogAdministration(CatalogAdministrationMapper admin, ProductMapper products, ObjectMapper json, AdministrationAudit audit) {
        this.admin = admin; this.products = products; this.json = json; this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Page search(HardwareCategory category, String status, String keyword, int page, int size) {
        if (page < 1 || size < 1 || size > 100 || keyword.length() > 100
                || status != null && !Set.of("ON_SALE", "OFF_SALE").contains(status)) invalid("分页或筛选条件无效。");
        String code = category == null ? null : category.name();
        return new Page(admin.search(code, status, keyword.strip(), size, (long)(page - 1) * size),
                admin.count(code, status, keyword.strip()), page, size);
    }

    @Transactional(readOnly = true)
    public Detail detail(long id) throws Exception {
        if (id <= 0) invalid("商品 ID 必须是正整数。");
        var product = products.findById(id);
        if (product == null) throw new CatalogException(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "商品不存在。");
        var spec = products.findSpecification(id);
        return new Detail(product, spec == null ? "" : spec.description(),
                spec == null ? json.createArrayNode() : json.readTree(spec.parameters()),
                spec == null ? "admin-v1" : spec.dataVersion(), admin.revision(id));
    }

    @Transactional(rollbackFor = Exception.class)
    public Detail create(JsonNode input, String actor) throws Exception {
        requireFields(input, Set.of("sku", "category", "name", "brand", "model", "summary", "description", "imageUrl", "sourceUrl", "priceNote", "price", "parameters"));
        String sku = text(input, "sku", 80);
        if (!sku.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,79}")) invalid("SKU 只允许字母、数字、点、下划线和短横线。");
        String category = text(input, "category", 16);
        try { HardwareCategory.valueOf(category); } catch (IllegalArgumentException e) { invalid("请选择八类硬件之一。"); }
        Fields fields = fields(input, sku, category, price(input));
        try { admin.create(fields); }
        catch (DuplicateKeyException e) { throw new CatalogException(HttpStatus.CONFLICT, "DUPLICATE_SKU", "SKU 已存在，请使用唯一销售编码。"); }
        long id = admin.idBySku(sku);
        JsonNode parameters = HardwareParameters.validate(category, input.has("parameters") ? input.get("parameters") : json.createArrayNode(), json);
        admin.specification(id, text(input, "description", 10000), json.writeValueAsString(parameters), "admin-v1");
        var result = detail(id);
        audit.record(actor, "PRODUCT", id, "CREATE", Map.of(), result);
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public Detail edit(long id, JsonNode input, String actor) throws Exception {
        requireFields(input, Set.of("revision", "name", "brand", "model", "summary", "description", "imageUrl", "sourceUrl", "priceNote", "parameters"));
        lock(id,input);
        var before = detail(id);
        var product = before.product();
        var fields = fields(input,product.sku(),product.category(),product.price());
        var parameters = HardwareParameters.validate(product.category(), input.get("parameters"),json);
        String description = text(input,"description",10000);
        if (product.status().equals("ON_SALE")) validateSale(fields,description,parameters);
        admin.edit(id,fields);
        admin.specification(id,description,json.writeValueAsString(parameters),"admin-r" + (before.revision()+1));
        var result = detail(id);
        audit.record(actor,"PRODUCT",id,"EDIT",before,result);
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public Detail status(long id, JsonNode input, String actor) throws Exception {
        requireFields(input,Set.of("revision","status"));
        String status = text(input,"status",16);
        if (!Set.of("ON_SALE","OFF_SALE").contains(status)) invalid("商品状态必须为 ON_SALE 或 OFF_SALE。");
        lock(id,input);
        var before = detail(id);
        if (before.product().status().equals(status)) return before;
        if (status.equals("ON_SALE")) {
            var p = before.product();
            var fields = new Fields(p.sku(),p.category(),p.name(),p.brand(),p.model(),p.summary(),p.imageUrl(),p.sourceUrl(),p.priceNote(),p.price());
            var parameters = HardwareParameters.validate(p.category(),before.parameters(),json);
            validateSale(fields,before.description(),parameters);
        }
        admin.status(id,status);
        var result = detail(id);
        audit.record(actor,"PRODUCT",id,"STATUS",before,result);
        return result;
    }

    @Transactional(readOnly = true)
    public List<AdministrationAudit.Entry> audit(long id) throws Exception {
        detail(id);
        return audit.history("PRODUCT", id);
    }

    private void lock(long id, JsonNode input) {
        var revision = input.get("revision");
        if (revision == null || !revision.isIntegralNumber() || !revision.canConvertToLong() || revision.asLong() < 0) invalid("请提交有效商品 revision。");
        Long current = admin.lock(id);
        if (current == null) throw new CatalogException(HttpStatus.NOT_FOUND,"PRODUCT_NOT_FOUND","商品不存在。");
        if (current != revision.asLong()) throw new CatalogException(HttpStatus.CONFLICT,"PRODUCT_CONFLICT","商品已被修改，请重新载入后再操作。");
    }
    private static void validateSale(Fields fields, String description, JsonNode parameters) {
        List<String> reasons = new ArrayList<>(HardwareParameters.missing(fields.category(),parameters));
        if (fields.name().isBlank()) reasons.add("缺少商品名称");
        if (fields.brand().isBlank()) reasons.add("缺少品牌");
        if (fields.model().isBlank()) reasons.add("缺少型号");
        if (fields.summary().isBlank()) reasons.add("缺少商品摘要");
        if (description.isBlank()) reasons.add("缺少描述");
        if (fields.imageUrl().isBlank()) reasons.add("缺少图片地址");
        if (fields.sourceUrl().isBlank()) reasons.add("缺少商品来源地址");
        if (fields.priceNote().isBlank()) reasons.add("缺少售价说明");
        if (fields.price().signum() <= 0) reasons.add("售价必须大于零");
        if (!reasons.isEmpty()) throw new CatalogException(HttpStatus.BAD_REQUEST,"PRODUCT_NOT_READY","上架条件未满足：" + String.join("；",reasons));
    }

    static Fields fields(JsonNode input, String sku, String category, BigDecimal price) {
        String image = text(input,"imageUrl",500), source = text(input,"sourceUrl",1000);
        if (!image.isEmpty() && !(image.startsWith("/images/") && !image.contains("..") && !image.contains("\\") || HardwareParameters.httpUrl(image))) invalid("图片地址须为本地 /images/ 路径或 HTTP(S) 地址。");
        if (!source.isEmpty() && !HardwareParameters.httpUrl(source)) invalid("商品来源地址须为 HTTP(S)。");
        return new Fields(sku, category, text(input,"name",180), text(input,"brand",60), text(input,"model",100),
                text(input,"summary",240), image, source,
                input.has("priceNote") ? text(input,"priceNote",240) : "管理员录入的 CNY 售价", price);
    }
    static BigDecimal price(JsonNode input) {
        if (!input.has("price")) return BigDecimal.ZERO.setScale(2);
        var value = input.get("price");
        if (!value.isTextual() || !value.asText().matches("[0-9]{1,10}(\\.[0-9]{1,2})?")) invalid("售价须为最多两位小数的人民币金额字符串。");
        var price = new BigDecimal(value.asText());
        if (price.signum() <= 0) invalid("初始售价必须大于零。");
        return price.setScale(2);
    }
    static void requireFields(JsonNode input, Set<String> allowed) {
        if (!input.isObject()) invalid("请提交商品对象。");
        input.fieldNames().forEachRemaining(key -> { if (!allowed.contains(key)) invalid("不允许修改字段：" + key); });
    }
    static String text(JsonNode input, String key, int max) {
        if (!input.has(key)) return "";
        var value = input.get(key);
        if (!value.isTextual() || value.asText().codePointCount(0,value.asText().length()) > max || value.asText().contains("\u0000")) invalid(key + " 类型或长度无效。");
        return value.asText().strip();
    }
    static void invalid(String message) { throw new CatalogException(HttpStatus.BAD_REQUEST, "INVALID_PRODUCT", message); }
    record Fields(String sku, String category, String name, String brand, String model, String summary,
                  String imageUrl, String sourceUrl, String priceNote, BigDecimal price) {}
    record Detail(Product product, String description, JsonNode parameters, String dataVersion, long revision) {}
    record Page(List<Product> items, long total, int page, int size) {}
}
