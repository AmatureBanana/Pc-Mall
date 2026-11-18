package cn.zhuangjimao.catalog;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;

@RestController
class RecommendationController {
    private final RecommendationService recommendations;
    RecommendationController(RecommendationService recommendations) { this.recommendations = recommendations; }

    @PostMapping("/api/recommendations")
    ResponseEntity<Envelope> recommend(@RequestBody(required = false) String body) {
        BigDecimal budget;
        RecommendationScenario scenario = null;
        try {
            var mapper = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();
            JsonNode input;
            try (var parser = mapper.createParser(body == null ? "" : body)) {
                input = mapper.readTree(parser);
                if (parser.nextToken() != null) throw new IllegalArgumentException();
            }
            if (input == null || !input.isObject() || input.size() != 2 || !input.path("scenario").isTextual())
                throw new IllegalArgumentException();
            scenario = RecommendationScenario.valueOf(input.path("scenario").asText());
            var amount = input.path("budget");
            if (!amount.isTextual() || !amount.asText().matches("[0-9]{1,10}(\\.[0-9]{1,2})?")) throw new IllegalArgumentException();
            budget = new BigDecimal(amount.asText()).setScale(2);
            if (budget.signum() <= 0) throw new IllegalArgumentException();
        } catch (Exception invalid) {
            return response(400, RecommendationService.failure("INVALID_INPUT",
                    "请选择办公、游戏或创作用途，并输入大于 0、最多两位小数的 CNY 预算。", scenario, null));
        }
        try { return response(200, recommendations.recommend(scenario, budget)); }
        catch (Exception exception) {
            org.slf4j.LoggerFactory.getLogger(RecommendationController.class).error("Recommendation failed for {}", scenario, exception);
            return response(503, RecommendationService.failure("SYSTEM_ERROR", "推荐服务暂不可用，请稍后重试。", scenario, budget));
        }
    }
    private ResponseEntity<Envelope> response(int status, RecommendationService.Result result) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore())
                .body(status < 400 ? new Envelope(result, null, null) : new Envelope(result, result.status(), result.message()));
    }
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Envelope(RecommendationService.Result data, String code, String message) {}
}
