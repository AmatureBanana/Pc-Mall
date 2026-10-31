package cn.zhuangjimao.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/products")
class CatalogAdministrationController {
    private final CatalogAdministration products;
    CatalogAdministrationController(CatalogAdministration products) { this.products = products; }
    @GetMapping
    Map<String,Object> search(@RequestParam(required=false) HardwareCategory category, @RequestParam(required=false) String status,
            @RequestParam(defaultValue="") String keyword, @RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="10") int size) {
        return Map.of("data", products.search(category,status,keyword,page,size));
    }
    @GetMapping("/{id}")
    Map<String,Object> detail(@PathVariable long id) throws Exception { return Map.of("data", products.detail(id)); }
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    Map<String,Object> create(@RequestBody JsonNode input, Authentication actor) throws Exception {
        return Map.of("data", products.create(input,actor.getName()));
    }
    @GetMapping("/schema")
    Map<String,Object> schema(@RequestParam HardwareCategory category) { return Map.of("data",HardwareParameters.schema(category.name())); }
    @PostMapping("/{id}")
    Map<String,Object> edit(@PathVariable long id, @RequestBody JsonNode input, Authentication actor) throws Exception {
        return Map.of("data",products.edit(id,input,actor.getName()));
    }
    @PostMapping("/{id}/status")
    Map<String,Object> status(@PathVariable long id, @RequestBody JsonNode input, Authentication actor) throws Exception {
        return Map.of("data",products.status(id,input,actor.getName()));
    }
    @GetMapping("/{id}/audit")
    Map<String,Object> audit(@PathVariable long id) throws Exception { return Map.of("data",products.audit(id)); }
}
