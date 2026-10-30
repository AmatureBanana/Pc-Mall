package cn.zhuangjimao.catalog;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ProductController {
    private final ProductMapper products;

    ProductController(ProductMapper products) {
        this.products = products;
    }

    @GetMapping("/api/products")
    ProductList products() {
        return new ProductList(products.findOnSale());
    }

    record ProductList(List<Product> data) {}
}
