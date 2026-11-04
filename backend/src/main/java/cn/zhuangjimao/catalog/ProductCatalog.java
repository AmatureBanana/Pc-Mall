package cn.zhuangjimao.catalog;

import java.util.List;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ProductCatalog {
    private final ProductMapper products;
    private final ObjectMapper json;
    private final PerformanceReferences performance;
    private final ProductPerformanceTags tags;

    ProductCatalog(ProductMapper products, ObjectMapper json, PerformanceReferences performance,ProductPerformanceTags tags) {
        this.products = products;
        this.json = json;
        this.performance = performance;
        this.tags=tags;
    }

    @Transactional(readOnly=true)
    public List<ProductPerformanceTags.TaggedProduct> search(HardwareCategory category, String keyword) throws Exception {
        if (keyword.length() > 100) {
            throw new CatalogException(HttpStatus.BAD_REQUEST, "INVALID_QUERY", "搜索关键词不能超过 100 个字符。");
        }
        return tags.decorate(products.findOnSale(category == null ? null : category.name(), keyword.strip()));
    }

    @Transactional(readOnly = true)
    public Detail detail(long id) throws JsonProcessingException {
        if (id <= 0) {
            throw new CatalogException(HttpStatus.BAD_REQUEST, "INVALID_PRODUCT_ID", "商品 ID 必须是正整数。");
        }
        Product product = products.findById(id);
        if (product == null) {
            throw new CatalogException(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "商品不存在，请返回列表重新选择。");
        }
        if (!product.status().equals("ON_SALE")) {
            throw new CatalogException(HttpStatus.CONFLICT, "PRODUCT_NOT_SELECTABLE", "商品已下架，请返回列表重新选择。");
        }
        var specification = products.findSpecification(id);
        if (specification == null) {
            throw new CatalogException(HttpStatus.SERVICE_UNAVAILABLE, "PRODUCT_DATA_UNAVAILABLE", "商品参数暂时不可用，请稍后重试。");
        }
        List<Parameter> parameters = json.readValue(specification.parameters(), new TypeReference<>() {});
        try { return new Detail(tags.decorate(List.of(product)).get(0), specification.description(), parameters, specification.dataVersion(),performance.read(id)); }
        catch (JsonProcessingException e) { throw e; }
        catch (Exception e) { throw new IllegalStateException("性能参考无法读取",e); }
    }

    record Parameter(String code, String label, JsonNode value, String unit, String sourceUrl, String note) {}
    record Detail(ProductPerformanceTags.TaggedProduct product, String description, List<Parameter> parameters, String dataVersion, PerformanceReferences.Score performance) {}
}
