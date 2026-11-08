package cn.zhuangjimao.catalog;

import org.springframework.http.HttpStatus;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.EnumMap;

@RestController
class DiyController {
    private static final long MAX_SAFE_INTEGER = 9_007_199_254_740_991L;
    private final DiyQuotation quotation;

    DiyController(DiyQuotation quotation) { this.quotation = quotation; }

    @GetMapping("/api/diy/quote")
    ResponseEntity<QuoteResponse> quote(@RequestParam MultiValueMap<String, String> query) {
        var selections = new EnumMap<HardwareCategory, Long>(HardwareCategory.class);
        long revision = -1;
        long memoryQuantity = 1;
        try {
            for (var entry : query.entrySet()) {
                if (entry.getValue().size() != 1 || !entry.getValue().get(0).matches("0|[1-9][0-9]{0,15}")) throw new IllegalArgumentException();
                long value = Long.parseLong(entry.getValue().get(0));
                if (value > MAX_SAFE_INTEGER) throw new IllegalArgumentException();
                if (entry.getKey().equals("revision")) revision = value;
                else if (entry.getKey().equals("memoryQuantity")) memoryQuantity = value;
                else {
                    if (value < 1) throw new IllegalArgumentException();
                    selections.put(HardwareCategory.valueOf(entry.getKey()), value);
                }
            }
            if (revision < 0 || !DiyMemory.validQuantity(memoryQuantity, selections.containsKey(HardwareCategory.MEMORY))) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException exception) {
            throw new CatalogException(HttpStatus.BAD_REQUEST, "INVALID_CONFIGURATION", "配置须含有效修订号，每类最多一个商品引用。所选内存数量须为 1～4 根。");
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new QuoteResponse(quotation.quote(revision, selections, (int) memoryQuantity)));
    }

    record QuoteResponse(DiyQuotation.Quote data) {}
}
