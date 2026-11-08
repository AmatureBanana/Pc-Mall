package cn.zhuangjimao.compatibility;

import cn.zhuangjimao.catalog.HardwareCategory;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/** One executed core check, with the categories and parameters it actually read. */
@JsonInclude(JsonInclude.Include.ALWAYS)
record RuleResult(String rule, RuleState state, List<HardwareCategory> categories, List<String> parameters,
                  String message, List<RuleDetail> details) {
    /** One parameter read by a rule, including a null value so "unknown" is never silent. */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    record RuleDetail(HardwareCategory category, String parameter, String value) {}
}
