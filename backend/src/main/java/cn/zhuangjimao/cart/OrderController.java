package cn.zhuangjimao.cart;

import cn.zhuangjimao.auth.AccountPrincipal;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/orders")
class OrderController {
    private static final ObjectMapper STRICT = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();
    private final SingleItemOrders orders;
    OrderController(SingleItemOrders orders) { this.orders = orders; }

    @GetMapping
    ResponseEntity<HistoryResponse> history(@AuthenticationPrincipal AccountPrincipal principal, @RequestParam(defaultValue="1") int page,
            @RequestParam(defaultValue="20") int pageSize, @RequestParam(required=false) String status,
            @RequestParam(required=false) String keyword,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new HistoryResponse(orders.history(principal.ownerId(),
                new SingleItemOrders.HistoryQuery(page, pageSize, status, keyword, startDate, endDate))));
    }

    @PostMapping
    ResponseEntity<Response> create(@AuthenticationPrincipal AccountPrincipal principal, @RequestBody String body) {
        JsonNode input;
        try (var parser = STRICT.createParser(body)) {
            input = STRICT.readTree(parser);
            if (parser.nextToken() != null) invalid();
        } catch (java.io.IOException error) { invalid(); return null; }
        if (input == null || !input.isObject()) invalid();
        var allowed = Set.of("revision", "itemIds", "configurationIds", "quoteToken", "requestKey");
        input.fieldNames().forEachRemaining(field -> { if (!allowed.contains(field)) invalid(); });
        var revision = input.get("revision");
        if (revision == null || !revision.isIntegralNumber() || !revision.canConvertToLong() || revision.asLong() < 0) invalid();
        return answer(orders.create(principal.ownerId(), revision.asLong(), ids(input, "itemIds"), ids(input, "configurationIds"),
                text(input, "quoteToken"), text(input, "requestKey")));
    }

    @GetMapping("/{number}")
    ResponseEntity<Response> view(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable String number) {
        return answer(orders.view(principal.ownerId(), number));
    }

    @PostMapping("/{number}/pay")
    ResponseEntity<Response> pay(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable String number,
                                 @RequestBody(required = false) String body) {
        if (!emptyObject(body)) invalidPayment();
        return answer(orders.pay(principal.ownerId(), number));
    }

    private static void invalidPayment() { throw new CartException(HttpStatus.BAD_REQUEST, "INVALID_PAYMENT_INPUT", "模拟支付不接受客户端金额、状态、时间或用户字段。"); }

    @PostMapping("/{number}/cancel")
    ResponseEntity<Response> cancel(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable String number,
                                    @RequestBody(required = false) String body) {
        if (!emptyObject(body)) invalidCancellation();
        return answer(orders.cancel(principal.ownerId(), number));
    }

    private static void invalidCancellation() { throw new CartException(HttpStatus.BAD_REQUEST, "INVALID_CANCELLATION_INPUT", "取消订单不接受客户端状态、时间、原因或用户字段。"); }

    private static boolean emptyObject(String body) {
        if (body == null || body.isBlank()) return true;
        try (var parser = STRICT.createParser(body)) {
            JsonNode input = STRICT.readTree(parser);
            return input != null && input.isObject() && input.isEmpty() && parser.nextToken() == null;
        } catch (java.io.IOException error) { return false; }
    }

    private static Set<Long> ids(JsonNode input, String name) {
        var value = input.get(name);
        if (value == null || !value.isArray()) invalid();
        var ids = new HashSet<Long>();
        for (var entry : value) {
            if (!entry.isIntegralNumber() || !entry.canConvertToLong() || entry.asLong() <= 0 || !ids.add(entry.asLong())) invalid();
        }
        return Set.copyOf(ids);
    }
    private static String text(JsonNode input, String name) {
        var value = input.get(name);
        if (value == null || !value.isTextual()) invalid();
        return value.asText();
    }
    private static void invalid() { throw new CartException(HttpStatus.BAD_REQUEST, "INVALID_ORDER_INPUT", "下单只接受购物项标识、修订、报价指纹和请求标识，字段或JSON格式无效。"); }
    private static ResponseEntity<Response> answer(SingleItemOrders.View order) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new Response(order));
    }
    record Response(SingleItemOrders.View data) {}
    record HistoryResponse(SingleItemOrders.History data) {}
}
