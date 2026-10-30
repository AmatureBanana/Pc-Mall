package cn.zhuangjimao.catalog;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ProductController {
    private final ProductCatalog products;

    ProductController(ProductCatalog products) {
        this.products = products;
    }

    @GetMapping("/api/products")
    ProductList products(@RequestParam(required = false) HardwareCategory category,
                         @RequestParam(defaultValue = "") String keyword) {
        return new ProductList(products.search(category, keyword));
    }

    @GetMapping("/api/products/{id}")
    ProductDetail detail(@PathVariable long id) throws JsonProcessingException {
        return new ProductDetail(products.detail(id));
    }

    record ProductList(List<Product> data) {}
    record ProductDetail(ProductCatalog.Detail data) {}
}
