package cn.zhuangjimao.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class ProductCardSummaryTest {
    private static final String SOURCE = "https://example.com/specifications";
    private final ObjectMapper json = new ObjectMapper();

    @Test void rendersTheSixCategoryTemplatesFromProductSpecificValues() throws Exception {
        assertThat(summary("COOLER","塔式风冷 · 四热管 · 92mm风扇",
                field("fanDiameter",120,"mm"))).isEqualTo("塔式风冷 · 四热管 · 120mm风扇");
        assertThat(summary("MEMORY","原摘要",field("generation","DDR5",""),field("dataRate",6000,"MT/s"),
                field("timingDataRate",6000,"MT/s"),field("casLatency",30,"周期"),field("stickCount",1,"条")))
                .isEqualTo("DDR5 · 6000 MT/s · CL30 · 单根");
        assertThat(summary("GPU","原摘要",field("gpuChipset","Radeon RX 7600 XT",""),
                field("vram",16,"GB"),field("length",281,"mm"))).isEqualTo("Radeon RX 7600 XT · 16 GB · 281 mm");
        assertThat(summary("SSD","原摘要",field("formFactor","M.2 2280",""),field("interface","PCIe",""),
                field("pcieGeneration","4.0",""),field("lanes",4,""),field("originalCapacity",1,"TB")))
                .isEqualTo("M.2 2280 · PCIe 4.0 ×4 · 1 TB");
        assertThat(summary("PSU","旧功率 · 全模组 · 旧板型",field("ratedPower",750,"W"),field("formFactor","ATX","")))
                .isEqualTo("750W · 全模组 · ATX");
        assertThat(summary("CASE","原摘要",field("supportedMotherboardFormFactors",List.of("Mini-ITX","ATX"),""),
                field("maxGpuLength",372,"mm"),field("maxCoolerHeight",170,"mm")))
                .isEqualTo("ATX · 显卡限长 372 mm · 散热器限高 170 mm");
    }

    @Test void omitsUnverifiedOrMismatchedDetailsAndKeepsAnExistingSummaryWhenNoFieldsCanBeShown() throws Exception {
        assertThat(summary("PSU","850 W · ATX 3.1",field("ratedPower",850,"W"),field("formFactor","SFX","")))
                .isEqualTo("850W · 模组待核验 · SFX");
        assertThat(summary("MEMORY","原摘要",field("generation","DDR5",""),field("dataRate",6200,"MT/s"),
                field("timingDataRate",6000,"MT/s"),field("casLatency",30,"周期"),field("stickCount",2,"条")))
                .isEqualTo("DDR5 · 6200 MT/s · 2 根套装");
        assertThat(summary("COOLER","原摘要")).isEqualTo("原摘要");
    }

    @Test void powerSuppliesUseTheSourcedCableTypeAheadOfTheLegacySummary() throws Exception {
        for (var modularity : List.of("全模组","半模组","直出线")) {
            var cableType = field("cableModularity",modularity,"");
            assertThat(HardwareParameters.isValidValue(HardwareCategory.PSU,"cableModularity",cableType.get("value"))).isTrue();
            assertThat(summary("PSU","750W · 全模组 · ATX",
                    field("ratedPower",650,"W"),cableType,field("formFactor","SFX","")))
                    .isEqualTo("650W · "+modularity+" · SFX");
        }
    }

    private JsonNode field(String code,Object value,String unit) {
        return json.createObjectNode().put("code",code).put("unit",unit).put("sourceUrl",SOURCE)
                .set("value",json.valueToTree(value));
    }

    private String summary(String category,String stored,JsonNode... fields) throws Exception {
        var product = new Product(1,"fixture","测试商品",category,category,"测试品牌","测试型号",
                stored,"/images/" + category.toLowerCase(java.util.Locale.ROOT) + ".svg",BigDecimal.TEN,
                1,SOURCE,"演示", "ON_SALE",0);
        var params = json.writeValueAsString(fields);
        var cards = new ProductPerformanceTags(new CardSources(params),
                new PerformanceReferences(null,null,null,json,null),json);
        return cards.decorate(List.of(product)).get(0).product().summary();
    }

    private record CardSources(String parameters) implements ProductMapper {
        @Override public List<TagSource> tagSources(List<Long> ids) {
            assertThat(ids).containsExactly(1L);
            return List.of(new TagSource(1L,parameters,null,null));
        }

        @Override public List<Product> findOnSale(String category,String keyword,String manufacturerKeyword) { throw new UnsupportedOperationException(); }
        @Override public Product findById(long id) { throw new UnsupportedOperationException(); }
        @Override public Specification findSpecification(long id) { throw new UnsupportedOperationException(); }
    }
}
