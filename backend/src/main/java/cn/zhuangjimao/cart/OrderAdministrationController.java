package cn.zhuangjimao.cart;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import org.springframework.security.core.Authentication;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/admin/orders")
class OrderAdministrationController {
    private final OrderAdministration orders;
    OrderAdministrationController(OrderAdministration orders) { this.orders = orders; }

    @GetMapping
    ResponseEntity<?> search(@RequestParam(defaultValue = "") String orderNumber, @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "") String username, @RequestParam(required = false) Long accountId,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int pageSize) {
        return answer(orders.search(new OrderAdministration.Query(orderNumber, status, username, accountId, page, pageSize)));
    }

    @GetMapping("/{number}")
    ResponseEntity<?> detail(@PathVariable String number) { return answer(orders.detail(number)); }

    @PostMapping("/{number}/complete")
    ResponseEntity<?> complete(@PathVariable String number, @RequestBody(required = false) String body, Authentication actor) throws Exception {
        if (body != null && !body.isBlank()) {
            try (var parser = new ObjectMapper().createParser(body)) {
                JsonNode input = parser.readValueAsTree();
                if (input == null || !input.isObject() || !input.isEmpty() || parser.nextToken() != null) invalid();
            } catch (java.io.IOException error) { invalid(); }
        }
        return answer(orders.complete(number, actor.getName()));
    }

    @GetMapping("/{number}/audit")
    ResponseEntity<?> audit(@PathVariable String number) throws Exception { return answer(orders.history(number)); }

    private static void invalid() {
        throw new CartException(org.springframework.http.HttpStatus.BAD_REQUEST, "INVALID_COMPLETION_INPUT", "完成交易不接受客户端金额、明细、状态、时间或操作者字段。");
    }

    private static ResponseEntity<?> answer(Object data) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("data", data));
    }
}
