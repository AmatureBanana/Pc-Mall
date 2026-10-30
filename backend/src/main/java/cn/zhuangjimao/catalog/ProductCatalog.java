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

    ProductCatalog(ProductMapper products, ObjectMapper json) {
        this.products = products;
        this.json = json;
    }

    List<Product> search(HardwareCategory category, String keyword) {
        if (keyword.length() > 100) {
            throw new CatalogException(HttpStatus.BAD_REQUEST, "INVALID_QUERY", "搜索关键词不能超过 100 个字符。");
        }
        return products.findOnSale(category == null ? null : category.name(), keyword.strip());
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
        return new Detail(product, specification.description(), parameters, specification.dataVersion());
    }

    record Parameter(String code, String label, JsonNode value, String unit, String sourceUrl, String note) {}
    record Detail(Product product, String description, List<Parameter> parameters, String dataVersion) {}
}
