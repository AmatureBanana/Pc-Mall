package cn.zhuangjimao.compatibility;

import cn.zhuangjimao.catalog.HardwareCategory;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/** The diagnostic answer for one configuration revision. */
@JsonInclude(JsonInclude.Include.ALWAYS)
record CompatibilityResult(long revision, String ruleVersion, String checkedAt, boolean corePassed,
                           List<RuleResult> checks, List<Missing> missing, List<Unexecuted> unexecutedRules) {
    /** A required category with no usable selection, kept separate from a rule's own state. */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    record Missing(String category, String label, String state, String message) {}

    /** A frozen rule this slice deliberately did not execute. */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    record Unexecuted(String rule, String reason) {}
}
