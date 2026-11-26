package cn.zhuangjimao.catalog;

import java.util.Locale;
import java.util.Map;

import static java.util.Map.entry;

/** Catalog display names, separate from the manufacturer identities used by the catalog data. */
final class CatalogBrandNames {
    private static final Map<String, String> CHINESE_NAMES = Map.ofEntries(
            entry("ADATA", "威刚"), entry("ARCTIC", "北极"), entry("ASROCK", "华擎"),
            entry("ASUS", "华硕"), entry("BE QUIET!", "德商德静界"), entry("COOLER MASTER", "酷冷至尊"),
            entry("CORSAIR", "海盗船"), entry("CRUCIAL", "英睿达"), entry("DEEPCOOL", "九州风神"),
            entry("FRACTAL DESIGN", "分形工艺"), entry("GIGABYTE", "技嘉"), entry("GREAT WALL", "长城"),
            entry("KINGSTON", "金士顿"), entry("KLEVV", "科赋"), entry("LEXAR", "雷克沙"),
            entry("LIAN LI", "联力"), entry("MONTECH", "君主科技"),
            entry("MSI", "微星"), entry("NOCTUA", "猫头鹰"), entry("NZXT", "恩杰"),
            entry("ONIX", "傲世创科"), entry("PALIT", "同德"), entry("PATRIOT", "博帝"),
            entry("PHANTEKS", "追风者"), entry("PNY", "必恩威"), entry("POWERCOLOR", "撼讯"),
            entry("SABRENT", "萨布伦特"), entry("SAMSUNG", "三星"), entry("SAPPHIRE", "蓝宝石"),
            entry("SCYTHE", "镰刀"), entry("SEASONIC", "海韵"), entry("SK HYNIX", "海力士"),
            entry("SPARKLE", "撼与科技"), entry("TEAMGROUP", "十铨"), entry("THERMALRIGHT", "利民"),
            entry("WESTERN DIGITAL", "西数"),
            entry("XFX", "讯景"), entry("ZOTAC", "索泰"));

    private CatalogBrandNames() {}

    static Product localized(Product product) {
        String chinese = CHINESE_NAMES.get(product.brand().toUpperCase(Locale.ROOT));
        if (chinese == null) return product;
        return new Product(product.id(), product.sku(), localizePrefix(product.name(), product.brand(), chinese),
                product.category(), product.categoryLabel(), chinese, product.model(), product.summary(),
                product.imageUrl(), product.price(), product.stock(), product.sourceUrl(),
                product.priceNote(), product.status(), product.revision());
    }

    static String localizedDescription(Product product, String description) {
        String chinese = CHINESE_NAMES.get(product.brand().toUpperCase(Locale.ROOT));
        return chinese == null ? description : localizePrefix(description, product.brand(), chinese);
    }

    private static String localizePrefix(String text, String brand, String chinese) {
        int length = brand.length();
        if (text.regionMatches(true, 0, brand, 0, length)
                && (text.length() == length || !Character.isLetterOrDigit(text.codePointAt(length)))) {
            return chinese + text.substring(length);
        }
        return text;
    }

    static String manufacturerKeyword(String keyword) {
        for (var name : CHINESE_NAMES.entrySet()) {
            String english = name.getKey();
            if (keyword.regionMatches(true, 0, english, 0, english.length())
                    && (keyword.length() == english.length() || Character.isWhitespace(keyword.charAt(english.length())))) {
                return name.getValue() + keyword.substring(english.length());
            }
            if (keyword.startsWith(name.getValue())) {
                String model = keyword.substring(name.getValue().length()).stripLeading();
                return model.isEmpty() ? english : english + " " + model;
            }
        }
        return null;
    }

    static String normalizeManufacturerSpacing(String keyword) {
        for (String chinese : CHINESE_NAMES.values()) {
            if (keyword.startsWith(chinese) && keyword.length() > chinese.length()) {
                return chinese + " " + keyword.substring(chinese.length()).stripLeading();
            }
        }
        return keyword;
    }
}
