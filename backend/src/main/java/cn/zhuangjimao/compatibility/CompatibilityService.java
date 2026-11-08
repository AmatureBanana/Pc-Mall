package cn.zhuangjimao.compatibility;

import cn.zhuangjimao.catalog.HardwareCategory;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

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
 * The shared F03 admission boundary. Every invocation re-reads a consistent catalog snapshot;
 * downstream consumers must call it again at their important business transitions.
 */
@Service
public class CompatibilityService {
    static final String RULE_VERSION = "f03-core-graphics-choice-v3";

    private static final List<CompatibilityResult.Unexecuted> EXTENSIONS =
            List.of("E01", "E02", "E03", "E04", "E05", "E06").stream()
                    .map(rule -> new CompatibilityResult.Unexecuted(rule, "P1 扩展检查未执行，相关风险尚未完全验证。"))
                    .toList();

    /** The independent cooler is always required, including CPUs supplied with a stock cooler. */
    private static final Set<HardwareCategory> REQUIRED =
            EnumSet.of(HardwareCategory.CPU, HardwareCategory.MOTHERBOARD, HardwareCategory.MEMORY,
                    HardwareCategory.SSD, HardwareCategory.PSU, HardwareCategory.CASE, HardwareCategory.COOLER);

    private final CompatibilityMapper catalog;
    private final com.fasterxml.jackson.databind.ObjectMapper json;
    private final Clock clock;

    CompatibilityService(CompatibilityMapper catalog, com.fasterxml.jackson.databind.ObjectMapper json, Clock clock) {
        this.catalog = catalog; this.json = json; this.clock = clock;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public CompatibilityResult check(long revision, Map<HardwareCategory, Long> selections) {
        return check(revision, selections, null);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public CompatibilityResult check(long revision, Map<HardwareCategory, Long> selections, Long integratedGraphicsCpuId) {
        if (integratedGraphicsCpuId != null && (!integratedGraphicsCpuId.equals(selections.get(HardwareCategory.CPU))
                || selections.containsKey(HardwareCategory.GPU))) {
            throw new InvalidConfiguration("核显选择须绑定当前 CPU，且不能与独立显卡同时选择。");
        }
        var parts = new EnumMap<HardwareCategory, Part>(HardwareCategory.class);
        var missing = new ArrayList<CompatibilityResult.Missing>();
        var snapshot = new StringBuilder(RULE_VERSION).append(":integratedGraphicsCpuId=").append(integratedGraphicsCpuId);
        for (var category : HardwareCategory.values()) {
            Long id = selections.get(category);
            snapshot.append('\n').append(category).append(':').append(id);
            if (id == null) {
                if (REQUIRED.contains(category)) missing.add(new CompatibilityResult.Missing(category.name(), Labels.of(category),
                        "NOT_SELECTED", "尚未选择" + Labels.of(category) + "。"));
                continue;
            }
            var record = catalog.find(id);
            snapshot.append(':').append(record);
            if (record == null) {
                missing.add(new CompatibilityResult.Missing(category.name(), Labels.of(category), "NOT_FOUND", "商品不存在，请重新选择。"));
                continue;
            }
            if (!record.category().equals(category.name())) {
                throw new InvalidConfiguration("商品类别与所提交的类别不一致。");
            }
            // Off-sale and out-of-stock products still have technical parameters. The quote and
            // transaction services enforce availability independently of this core diagnosis.
            parts.put(category, new Part(category, parameters(record)));
        }
        var checks = CompatibilityRules.check(parts, selections.containsKey(HardwareCategory.GPU));
        // Graphics is a required choice, not an output-path compatibility rule.
        var graphics = new CompatibilityRules.Read(parts,
                CompatibilityRules.Field.of(HardwareCategory.CPU, "integratedGraphics"));
        boolean integratedChosen = integratedGraphicsCpuId != null
                && graphics.usable(HardwareCategory.CPU, "integratedGraphics")
                && graphics.field(HardwareCategory.CPU, "integratedGraphics").booleanValue();
        if (!selections.containsKey(HardwareCategory.GPU) && !integratedChosen) {
            missing.add(new CompatibilityResult.Missing("GPU", "显卡", "NOT_SELECTED", "请选择核显或独立显卡。"));
        }
        boolean complete = missing.isEmpty();
        CompatibilityStatus status = checks.stream().anyMatch(check -> check.state() == RuleState.FAIL)
                ? CompatibilityStatus.INCOMPATIBLE : !complete ? CompatibilityStatus.INCOMPLETE
                : checks.size() == 9 && checks.stream().allMatch(check -> check.state() == RuleState.PASS || check.state() == RuleState.NOT_APPLICABLE)
                ? CompatibilityStatus.CORE_PASSED : CompatibilityStatus.UNVERIFIED;
        return new CompatibilityResult(revision, RULE_VERSION, now(), fingerprint(snapshot.toString()), status,
                complete, status == CompatibilityStatus.CORE_PASSED, checks, List.copyOf(missing), List.of(), EXTENSIONS);
    }

    CompatibilityResult failure(long revision) {
        return new CompatibilityResult(revision, RULE_VERSION, now(), null, CompatibilityStatus.ERROR,
                false, false, List.of(), List.of(), java.util.stream.IntStream.of(1, 2, 3, 4, 5, 6, 8, 9, 10)
                .mapToObj(number -> new CompatibilityResult.Unexecuted("C%02d".formatted(number), "检测异常，未获得可靠结果。"))
                .toList(), EXTENSIONS);
    }

    private String now() { return OffsetDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS).toString(); }

    private static String fingerprint(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private JsonNode parameters(CompatibilityMapper.Record record) {
        String stored = record.parameters();
        if (stored == null) return json.createArrayNode();
        try { return json.readTree(stored); }
        catch (Exception exception) { throw new IllegalStateException("Catalog parameters are unreadable", exception); }
    }
}
