package cn.zhuangjimao.catalog;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.Set;

@RestController
class RecommendationController {
    private final OfficeRecommendation office;
    RecommendationController(OfficeRecommendation office) { this.office = office; }

    @PostMapping("/api/recommendations")
    ResponseEntity<Envelope> recommend(@RequestBody(required = false) String body) {
        BigDecimal budget;
        try {
            var mapper = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();
            JsonNode input;
            try (var parser = mapper.createParser(body == null ? "" : body)) {
                input = mapper.readTree(parser);
                if (parser.nextToken() != null) throw new IllegalArgumentException();
            }
            if (input == null || !input.isObject() || input.size() != 2 || !input.path("scenario").asText().equals("OFFICE")) throw new IllegalArgumentException();
            var names = input.fieldNames();
            while (names.hasNext()) if (!Set.of("scenario", "budget").contains(names.next())) throw new IllegalArgumentException();
            var amount = input.path("budget");
            if (!amount.isTextual() || !amount.asText().matches("[0-9]{1,10}(\\.[0-9]{1,2})?")) throw new IllegalArgumentException();
            budget = new BigDecimal(amount.asText()).setScale(2);
            if (budget.signum() <= 0) throw new IllegalArgumentException();
        } catch (Exception invalid) {
            return response(400, OfficeRecommendation.failure("INVALID_INPUT", "请输入大于 0、最多两位小数的 CNY 预算，并选择办公用途。", null));
        }
        try { return response(200, office.recommend(budget)); }
        catch (Exception exception) {
            org.slf4j.LoggerFactory.getLogger(RecommendationController.class).error("Office recommendation failed", exception);
            return response(503, OfficeRecommendation.failure("SYSTEM_ERROR", "推荐服务暂不可用，请稍后重试。", budget));
        }
    }
    private ResponseEntity<Envelope> response(int status, OfficeRecommendation.Result result) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(new Envelope(result));
    }
    record Envelope(OfficeRecommendation.Result data) {}
}
