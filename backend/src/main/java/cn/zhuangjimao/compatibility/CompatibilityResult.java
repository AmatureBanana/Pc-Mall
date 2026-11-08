package cn.zhuangjimao.compatibility;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/** The diagnostic answer for one configuration revision. */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record CompatibilityResult(long revision, String ruleVersion, String checkedAt, String catalogVersion,
                           CompatibilityStatus status, boolean complete, boolean corePassed,
                           List<RuleResult> checks, List<Missing> missing, List<Unexecuted> unexecutedRules,
                           List<Unexecuted> unexecutedExtensions) {
    /** A required category with no usable selection, kept separate from a rule's own state. */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Missing(String category, String label, String state, String message) {}

    /** A frozen rule this slice deliberately did not execute. */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Unexecuted(String rule, String reason) {}
}
