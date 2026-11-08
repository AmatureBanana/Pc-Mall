package cn.zhuangjimao.compatibility;

import cn.zhuangjimao.catalog.HardwareCategory;
import com.fasterxml.jackson.databind.JsonNode;

/** A server-side catalog record as the rules see it: only what a rule can actually read. */
record Part(HardwareCategory category, JsonNode parameters) {
}
