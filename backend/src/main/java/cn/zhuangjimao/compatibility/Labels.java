package cn.zhuangjimao.compatibility;

import cn.zhuangjimao.catalog.HardwareCategory;

import java.util.Map;

/** Category names exactly as the catalog stores them in product_categories.label. */
final class Labels {
    private Labels() {}

    private static final Map<HardwareCategory, String> NAMES = Map.of(
            HardwareCategory.CPU, "CPU",
            HardwareCategory.MOTHERBOARD, "主板",
            HardwareCategory.GPU, "显卡",
            HardwareCategory.MEMORY, "内存",
            HardwareCategory.SSD, "SSD",
            HardwareCategory.PSU, "电源",
            HardwareCategory.CASE, "机箱",
            HardwareCategory.COOLER, "独立散热器");

    static String of(HardwareCategory category) { return NAMES.get(category); }
}
