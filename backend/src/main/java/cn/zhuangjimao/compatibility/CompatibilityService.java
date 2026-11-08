package cn.zhuangjimao.compatibility;

import cn.zhuangjimao.catalog.HardwareCategory;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * F03 C01–C05 diagnostics over the current catalog snapshot. Unselected parts stay PENDING, and
 * because C06–C10 have not run this service never claims a whole-machine core pass.
 */
@Service
class CompatibilityService {
    static final String RULE_VERSION = "f03-core-c01-c05-v1";

    /** The frozen rules this slice does not execute; task 11 owns them. */
    private static final List<String> UNEXECUTED = List.of("C06", "C07", "C08", "C09", "C10");

    /** The seven base parts are required; a discrete GPU is conditional and owned by task 11. */
    private static final Set<HardwareCategory> REQUIRED =
            EnumSet.of(HardwareCategory.CPU, HardwareCategory.MOTHERBOARD, HardwareCategory.MEMORY,
                    HardwareCategory.SSD, HardwareCategory.PSU, HardwareCategory.CASE, HardwareCategory.COOLER);

    private final CompatibilityMapper catalog;
    private final com.fasterxml.jackson.databind.ObjectMapper json;
    private final Clock clock;

    CompatibilityService(CompatibilityMapper catalog, com.fasterxml.jackson.databind.ObjectMapper json, Clock clock) {
        this.catalog = catalog; this.json = json; this.clock = clock;
    }

    @Transactional(readOnly = true)
    public CompatibilityResult check(long revision, Map<HardwareCategory, Long> selections) {
        var parts = new EnumMap<HardwareCategory, Part>(HardwareCategory.class);
        var missing = new ArrayList<CompatibilityResult.Missing>();
        for (var category : HardwareCategory.values()) {
            Long id = selections.get(category);
            if (id == null) {
                if (REQUIRED.contains(category)) missing.add(new CompatibilityResult.Missing(category.name(), Labels.of(category),
                        "NOT_SELECTED", "尚未选择" + Labels.of(category) + "。"));
                continue;
            }
            var record = catalog.find(id);
            if (record == null) {
                missing.add(new CompatibilityResult.Missing(category.name(), Labels.of(category), "NOT_FOUND", "商品不存在，请重新选择。"));
                continue;
            }
            if (!record.category().equals(category.name())) {
                throw new InvalidConfiguration("商品类别与所提交的类别不一致。");
            }
            if (!record.status().equals("ON_SALE")) {
                missing.add(new CompatibilityResult.Missing(category.name(), Labels.of(category), "OFF_SALE", "商品已下架，请重新选择。"));
                continue;
            }
            parts.put(category, new Part(category, parameters(record)));
        }
        var checks = CompatibilityRules.check(parts);
        return new CompatibilityResult(revision, RULE_VERSION,
                OffsetDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS).toString(), false,
                checks, List.copyOf(missing),
                UNEXECUTED.stream().map(rule -> new CompatibilityResult.Unexecuted(rule, "任务11接入")).toList());
    }

    private JsonNode parameters(CompatibilityMapper.Record record) {
        String stored = record.parameters();
        if (stored == null) return json.createArrayNode();
        try { return json.readTree(stored); }
        catch (Exception exception) { throw new IllegalStateException("Catalog parameters are unreadable", exception); }
    }
}
