package cn.zhuangjimao.compatibility;

import cn.zhuangjimao.catalog.DiyMemory;
import cn.zhuangjimao.catalog.HardwareCategory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.EnumMap;

/** Public, CSRF-protected diagnostic entry point; the client may only send product references. */
@RestController
class CompatibilityController {
    private static final long MAX_SAFE_INTEGER = 9_007_199_254_740_991L;

    private final CompatibilityService compatibility;

    CompatibilityController(CompatibilityService compatibility) { this.compatibility = compatibility; }

    @PostMapping("/api/compatibility/check")
    ResponseEntity<CheckResponse> check(@RequestBody(required = false) String body) {
        JsonNode input = parse(body);
        if (input == null || !input.isObject()) throw invalid();
        var selections = new EnumMap<HardwareCategory, Long>(HardwareCategory.class);
        long revision = -1;
        long memoryQuantity = 1;
        Long integratedGraphicsCpuId = null;
        var fields = input.fields();
        while (fields.hasNext()) {
            var field = fields.next();
            if (field.getKey().equals("revision")) {
                if (!safe(field.getValue())) throw invalid();
                revision = field.getValue().asLong();
                continue;
            }
            if (field.getKey().equals("integratedGraphicsCpuId")) {
                if (!safe(field.getValue()) || field.getValue().asLong() < 1) throw invalid();
                integratedGraphicsCpuId = field.getValue().asLong();
                continue;
            }
            if (field.getKey().equals("memoryQuantity")) {
                if (!safe(field.getValue())) throw invalid();
                memoryQuantity = field.getValue().asLong();
                continue;
            }
            HardwareCategory category;
            try { category = HardwareCategory.valueOf(field.getKey()); }
            catch (IllegalArgumentException exception) { throw invalid(); }
            if (!safe(field.getValue()) || field.getValue().asLong() < 1) throw invalid();
            selections.put(category, field.getValue().asLong());
        }
        if (revision < 0 || !DiyMemory.validQuantity(memoryQuantity, selections.containsKey(HardwareCategory.MEMORY))) throw invalid();
        try {
            return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                    .body(new CheckResponse(compatibility.check(revision, selections, integratedGraphicsCpuId, (int) memoryQuantity)));
        } catch (InvalidConfiguration exception) {
            throw exception;
        } catch (RuntimeException exception) {
            org.slf4j.LoggerFactory.getLogger(CompatibilityController.class).error("Compatibility check failed", exception);
            return ResponseEntity.status(503).cacheControl(CacheControl.noStore())
                    .body(new CheckResponse(compatibility.failure(revision)));
        }
    }

    private static boolean safe(JsonNode value) {
        return value != null && value.isIntegralNumber() && value.canConvertToLong()
                && value.asLong() >= 0 && value.asLong() <= MAX_SAFE_INTEGER;
    }

    /**
     * Duplicate JSON keys would silently collapse "CPU:1, CPU:2" into one reference, so the raw
     * body is parsed with strict duplicate detection rather than a lenient tree binding.
     */
    private static JsonNode parse(String body) {
        if (body == null || body.isBlank()) return null;
        try (var parser = STRICT.createParser(body)) {
            JsonNode node = STRICT.readTree(parser);
            return parser.nextToken() == null ? node : null;
        } catch (Exception exception) { return null; }
    }

    private static final ObjectMapper STRICT = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();

    private static InvalidConfiguration invalid() {
        return new InvalidConfiguration("配置须含有效修订号，每类最多一个商品引用，所选内存数量须为 1～4 根。");
    }

    record CheckResponse(CompatibilityResult data) {}
}
