<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElButton, ElDialog, ElEmpty, ElSkeleton } from 'element-plus'
import HardwareIcon from './HardwareIcon.vue'
import ProductPerformanceTags from './ProductPerformanceTags.vue'
import { formatPrice, imageFallback, loadProducts } from './catalog'
import { hardwareCategories, type HardwareCategory } from './diy'
import { useCatalogRequest } from './useCatalogRequest'
import { useDiyWorkspace } from './useDiyWorkspace'

const activeCategory = ref<HardwareCategory>('CPU')
const searchInput = ref('')
const keyword = ref('')
const brand = ref('')
const sort = ref('default')
const clearOpen = ref(false)
const { workspace, storageWarning, count, select, remove, clear, quotation } = useDiyWorkspace()
const { data: quote, status: quoteStatus, error: quoteError, refresh: refreshQuote } = quotation
const { data: products, status, error, refresh } = useCatalogRequest(
  signal => loadProducts(signal, activeCategory.value, keyword.value), '候选商品')
const descriptions: Record<HardwareCategory, string> = {
  CPU: '决定计算性能与核心算力', MOTHERBOARD: '连接和扩展各类硬件', GPU: '图形处理与游戏性能',
  MEMORY: '系统运行的工作内存', SSD: '提供存储空间与读写速度', PSU: '为整机提供稳定电力',
  CASE: '整机安装与散热空间', COOLER: '为 CPU 提供散热能力',
}
const labels: Record<HardwareCategory, string> = {
  CPU: 'CPU 处理器', MOTHERBOARD: '主板', GPU: '独立显卡', MEMORY: '内存',
  SSD: 'SSD 固态硬盘', PSU: '电源', CASE: '机箱', COOLER: '独立散热器',
}
const activeLabel = computed(() => hardwareCategories.find(category => category.code === activeCategory.value)!.label)
const brands = computed(() => [...new Set((products.value ?? []).map(product => product.brand))])
const candidates = computed(() => {
  const items = (products.value ?? []).filter(product => !brand.value || product.brand === brand.value)
  if (sort.value === 'price-asc') items.sort((a, b) => Number(a.price) - Number(b.price) || a.id - b.id)
  if (sort.value === 'price-desc') items.sort((a, b) => Number(b.price) - Number(a.price) || a.id - b.id)
  return items
})
const selectedId = (category: HardwareCategory) => workspace.value.selections.find(item => item.category === category)?.productId
const quoteItem = (category: HardwareCategory) => quote.value?.items.find(item => item.category === category)
function search() {
  const next = searchInput.value.trim()
  if (keyword.value === next) void refresh()
  else keyword.value = next
}
function resetFilters() { searchInput.value = keyword.value = brand.value = ''; void refresh() }
function clearConfiguration() {
  clear()
  clearOpen.value = false
  activeCategory.value = 'CPU'
  sort.value = 'default'
  resetFilters()
}
watch(activeCategory, () => { searchInput.value = keyword.value = brand.value = ''; sort.value = 'default'; void refresh() })
watch(keyword, () => { brand.value = ''; void refresh() })
onMounted(() => { void refresh() })
</script>

<template>
  <main class="diy-workspace page-width">
    <div class="diy-heading"><HardwareIcon name="monitor"/><h1>DIY 电脑装机</h1><p>自由选择八类硬件，搭建你的专属电脑配置</p><span class="workspace-badge"><HardwareIcon name="alert"/>当前标签页工作区 · 演示</span></div>
    <p v-if="storageWarning" class="diy-warning" role="alert">{{ storageWarning }}</p>
    <div class="diy-columns">
      <section class="diy-panel diy-categories">
        <div class="diy-panel-heading"><h2><span>①</span> 硬件分类</h2><p>选择类别浏览商品，已选配件会显示在右侧</p></div>
        <nav aria-label="DIY 硬件分类" class="diy-category-list">
          <button v-for="category in hardwareCategories" :key="category.code" :aria-pressed="activeCategory === category.code"
            :class="{ active: activeCategory === category.code }" @click="activeCategory = category.code">
            <HardwareIcon :name="category.icon"/><span class="category-copy"><strong>{{ labels[category.code] }}</strong><small>{{ descriptions[category.code] }}</small></span>
            <span class="selection-state" :class="{ chosen: selectedId(category.code) }"><span>{{ selectedId(category.code) ? '✓' : '○' }}</span>{{ selectedId(category.code) ? '已选择' : '未选择' }}</span>
          </button>
        </nav>
        <p class="diy-category-note">按你的顺序，自由选配<br/>每类一个 SKU · 一个销售单位</p>
      </section>

      <section class="diy-panel diy-candidates" aria-label="候选商品" :aria-busy="status === 'loading'">
        <div class="diy-panel-heading"><div class="diy-title-row"><h2><span>②</span> 候选商品（{{ activeLabel }}）</h2><span class="candidate-count">{{ status === 'ready' ? `共 ${candidates.length} 件` : '加载中' }}</span></div><p>从下方选择一款，支持任意顺序选择、替换或移除</p></div>
        <div class="diy-filters">
          <div class="diy-brands"><span>品牌：</span><button :class="{ active: !brand }" @click="brand = ''">全部</button><button v-for="item in brands" :key="item" :class="{ active: brand === item }" @click="brand = item">{{ item }}</button></div>
          <div class="diy-search-row"><label>排序：<select v-model="sort" aria-label="候选商品排序"><option value="default">综合排序</option><option value="price-asc">价格从低到高</option><option value="price-desc">价格从高到低</option></select></label><form @submit.prevent="search"><HardwareIcon name="search"/><input v-model="searchInput" aria-label="搜索当前类别商品" placeholder="在当前类别中搜索型号或参数" maxlength="100"/><button type="submit" aria-label="搜索候选商品">搜索</button></form></div>
        </div>
        <div class="diy-candidate-list">
          <ElSkeleton v-if="status === 'loading'" animated :rows="8"/>
          <div v-else-if="status === 'error'" class="diy-empty" role="alert"><HardwareIcon name="alert"/><h3>候选商品加载失败</h3><p>{{ error }}</p><ElButton type="primary" @click="refresh">重新加载候选商品</ElButton></div>
          <ElEmpty v-else-if="!candidates.length" description="没有找到符合条件的商品"><ElButton @click="resetFilters">清除候选筛选</ElButton></ElEmpty>
          <article v-for="product in status === 'ready' ? candidates : []" :key="product.id" class="diy-product" :class="{ selected: selectedId(product.category) === product.id }">
            <a :href="`#diy/product/${product.id}`" class="diy-product-image" tabindex="-1" aria-hidden="true"><img :src="product.imageUrl" :alt="`${product.categoryLabel}品类示意图`" @error="imageFallback"/></a>
            <div class="diy-product-copy"><h3><a :href="`#diy/product/${product.id}`">{{ product.name }}</a></h3><p>{{ product.summary }}</p><ProductPerformanceTags :tags="product.performanceTags" compact/><a :href="`#diy/product/${product.id}`" class="diy-spec-link">型号与完整参数 ↗</a></div>
            <div class="diy-product-price"><strong>¥{{ formatPrice(product.price) }}</strong><small :class="{ unavailable: !product.stock }">{{ product.stock ? `库存 ${product.stock} 件` : '暂时缺货' }}</small></div>
            <ElButton :type="selectedId(product.category) === product.id ? 'success' : 'primary'" :plain="selectedId(product.category) !== product.id"
              :disabled="product.stock === 0 || selectedId(product.category) === product.id" :aria-label="`${selectedId(product.category) === product.id ? '已选择' : '选择'} ${product.name}`" @click="select(product)">
              {{ selectedId(product.category) === product.id ? '✓ 已选择' : '＋ 选择' }}
            </ElButton>
          </article>
        </div>
        <p class="diy-candidate-note">图片为品类示意 · 价格与库存为商城演示数据</p>
      </section>

      <section class="diy-panel diy-configuration" aria-label="当前配置" :aria-busy="quoteStatus === 'loading'">
        <div class="diy-panel-heading"><div class="diy-title-row"><h2><span>③</span> 当前配置（8 类硬件）</h2><ElButton :disabled="!count" @click="clearOpen = true"><HardwareIcon name="trash"/>清空配置</ElButton></div><p>已选择 <b>{{ count }} / 8</b> 项，可继续选择其他配件</p></div>
        <div class="diy-configuration-body">
          <div v-for="category in hardwareCategories" :key="category.code" class="diy-slot" :class="{ 'slot-selected': selectedId(category.code), 'slot-invalid': quoteItem(category.code) && quoteItem(category.code)!.state !== 'VALID' }" role="group" :aria-label="`${category.label}配置`">
            <img v-if="quoteItem(category.code)?.product" :src="quoteItem(category.code)!.product!.imageUrl" alt="" @error="imageFallback"/><HardwareIcon v-else :name="category.icon"/>
            <div class="diy-slot-copy"><strong>{{ category.label }}</strong>
              <template v-if="selectedId(category.code)"><a v-if="quoteItem(category.code)?.product" :href="`#diy/product/${selectedId(category.code)}`">{{ quoteItem(category.code)!.product!.name }}</a><span v-else>商品 #{{ selectedId(category.code) }} · {{ quoteStatus === 'loading' ? '正在核价' : '引用已保留' }}</span>
                <b v-if="quoteItem(category.code)?.amount" class="slot-price">¥{{ formatPrice(quoteItem(category.code)!.amount!) }}</b>
                <small v-if="quoteItem(category.code) && quoteItem(category.code)!.state !== 'VALID'" class="slot-problem">{{ quoteItem(category.code)!.message }}</small>
                <small v-else-if="category.code === 'MEMORY'">1 个销售单位（套装条数不计购买数量）</small>
              </template><span v-else class="slot-placeholder">尚未选择</span>
            </div>
            <template v-if="selectedId(category.code)"><span v-if="quoteItem(category.code)?.state === 'VALID'" class="slot-chosen">✓ 已选择</span><button class="slot-action" :aria-label="`替换 ${category.label}`" title="替换" @click="activeCategory = category.code"><HardwareIcon name="replace"/></button><button class="slot-action" :aria-label="`移除 ${category.label}`" title="移除" @click="remove(category.code)"><HardwareIcon name="trash"/></button></template>
            <ElButton v-else type="primary" plain :aria-label="`选择 ${category.label}类别`" @click="activeCategory = category.code">＋ 选择</ElButton>
          </div>
          <div class="diy-total" aria-live="polite"><div><strong>当前参考总价：</strong><b v-if="quoteStatus === 'ready' && quote?.total !== null" aria-label="当前参考总价">¥{{ formatPrice(quote!.total!) }}</b><b v-else class="pending-total" aria-label="当前参考总价">{{ quoteStatus === 'loading' ? '正在核价…' : quoteStatus === 'error' ? '报价暂不可用' : '配置存在问题' }}</b></div><p>仅为当前配置的商品合计参考价，<br/>不代表最终成交价</p></div>
          <p v-if="quoteStatus === 'ready' && quote?.total === null" class="diy-warning" role="alert">部分选择不可售，请替换或移除。可售项小计 ¥{{ formatPrice(quote.validSubtotal) }}，不是完整配置报价。</p>
          <p v-if="quoteStatus === 'error'" class="diy-warning" role="alert">{{ quoteError }}</p>
          <div class="diy-quote-meta"><span>配置修订 {{ workspace.revision }} · 每类 1 个销售单位</span><button :disabled="quoteStatus === 'loading'" @click="refreshQuote">{{ quoteStatus === 'error' ? '重试参考报价' : '刷新价格与库存' }}</button></div>
          <div class="diy-status-note"><HardwareIcon name="alert"/><p><strong>兼容状态：尚未检测</strong><br/>当前支持商品选择与参考计价。兼容性检测将在后续接入，尚不可进行整机交易。</p></div>
        </div>
      </section>
    </div>
    <ElDialog v-model="clearOpen" title="清空当前配置？" width="420px" align-center :close-on-click-modal="false"><p>将移除本标签页的全部已选硬件。取消可继续编辑。</p><template #footer><ElButton @click="clearOpen = false">取消</ElButton><ElButton type="danger" @click="clearConfiguration">确认清空</ElButton></template></ElDialog>
  </main>
</template>

<style scoped>
.diy-workspace { color: #16243d; padding-bottom: 26px; }
.diy-heading { display: flex; align-items: center; gap: 18px; min-height: 76px; }
.diy-heading > svg { width: 38px; height: 38px; color: var(--blue); }
.diy-heading h1 { font-size: 27px; margin: 0; color: #0b1730; }
.diy-heading > p { border-left: 1px solid #dce4ef; padding-left: 18px; font-size: 15px; color: #4b5f7c; }
.workspace-badge { margin-left: auto; display: flex; align-items: center; gap: 8px; background: #e3eeff; border-radius: 10px; padding: 11px 14px; color: #23477f; font-size: 13px; white-space: nowrap; }
.workspace-badge svg { width: 18px; height: 18px; color: var(--blue); }
.diy-columns { display: grid; grid-template-columns: minmax(250px, .72fr) minmax(480px, 1.46fr) minmax(390px, 1.1fr); gap: 12px; align-items: stretch; }
.diy-panel { min-width: 0; background: #ffffffed; border: 1px solid #e2e8f2; border-radius: 15px; overflow: hidden; box-shadow: 0 2px 8px #1a3d6e03; }
.diy-panel-heading { padding: 18px 18px 14px; border-bottom: 1px solid #edf1f7; }
.diy-panel-heading h2 { margin: 0; font-size: 20px; line-height: 1.5; color: #0d1830; white-space: nowrap; }
.diy-panel-heading h2 > span { color: var(--blue); }
.diy-panel-heading p { margin: 6px 0 0; font-size: 13px; line-height: 1.6; color: #51617c; }
.diy-panel-heading p b { color: var(--blue); }
.diy-title-row { display: flex; justify-content: space-between; align-items: center; gap: 8px; }
.candidate-count { background: #e9f1ff; padding: 4px 10px; border-radius: 20px; font-size: 12px; color: #36629d; white-space: nowrap; }
.diy-category-list { padding: 12px 10px; }
.diy-category-list > button { display: flex; align-items: center; gap: 16px; width: 100%; padding: 12px 14px; min-height: 72px; background: white; border: 1px solid #f0f3f8; border-radius: 9px; margin-bottom: 3px; text-align: left; }
.diy-category-list > button:hover { background: #f5f8ff; }
.diy-category-list > button.active { background: #eaf2ff; border-color: #eaf2ff; }
.diy-category-list > button > svg { width: 32px; height: 32px; flex-shrink: 0; }
.diy-category-list > button.active > svg { color: var(--blue); }
.category-copy { display: flex; flex-direction: column; gap: 5px; min-width: 0; }
.category-copy strong { font-size: 17px; color: #0c1930; }
.category-copy small { font-size: 12px; color: #56657b; white-space: nowrap; }
.selection-state { display: flex; gap: 5px; margin-left: auto; align-items: center; white-space: nowrap; font-size: 12px; color: #7b879a; }
.selection-state > span { font-size: 18px; }
.selection-state.chosen { color: #0a9d45; font-weight: 600; }
.diy-category-note { margin: 2px 0 12px; font-size: 11px; color: #8a96a8; text-align: center; line-height: 1.6; }
.diy-filters { padding: 16px 18px; display: grid; gap: 18px; font-size: 13px; }
.diy-brands { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.diy-brands button { border: 1px solid #dce3ef; border-radius: 5px; background: white; padding: 5px 14px; }
.diy-brands button.active { background: var(--blue); border-color: var(--blue); color: white; }
.diy-search-row { display: flex; align-items: center; gap: 12px; justify-content: space-between; }
.diy-search-row label { white-space: nowrap; }
.diy-search-row select { border: 1px solid #d8e1ee; border-radius: 6px; color: #334968; background: white; padding: 7px 9px; max-width: 130px; }
.diy-search-row form { display: flex; align-items: center; border: 1px solid #d8e1ee; border-radius: 6px; padding: 0 9px; min-width: 0; height: 34px; flex: 1; max-width: 340px; }
.diy-search-row form svg { width: 17px; height: 17px; flex-shrink: 0; color: #6a7e9a; }
.diy-search-row input { border: 0; outline: 0; padding: 4px 7px; width: 100%; min-width: 0; font-size: 12px; }
.diy-search-row form button { border: 0; background: transparent; white-space: nowrap; padding: 3px; color: var(--blue); font-size: 12px; }
.diy-candidate-list { margin: 0 10px; min-height: 490px; max-height: 550px; overflow-y: auto; padding: 3px 6px 3px 2px; scrollbar-width: thin; scrollbar-color: #c6cfdb #f5f7fa; }
.diy-product { display: grid; grid-template-columns: 85px minmax(0, 1fr) auto 88px; align-items: center; gap: 12px; min-height: 134px; border: 1px solid #dfe6f0; border-radius: 12px; padding: 13px 12px; margin-bottom: 7px; }
.diy-product.selected { border: 2px solid #237dff; padding: 12px 11px; background: linear-gradient(110deg, #f9fcff, #eaf2ff); }
.diy-product-image { display: flex; height: 88px; align-items: center; justify-content: center; }
.diy-product-image img { width: 85px; height: 88px; object-fit: cover; }
.diy-product-copy { min-width: 0; }
.diy-product-copy h3 { font-size: 18px; margin: 0 0 6px; color: #101c32; line-height: 1.4; }
.diy-product-copy h3 a:hover { color: var(--blue); }
.diy-product-copy > p { font-size: 13px; color: #53637d; margin: 0 0 6px; line-height: 1.5; }
.diy-product-copy :deep(.performance-tags) { margin-block: 5px; }
.diy-spec-link { font-size: 11px; color: #6d81a2; }
.diy-product-price { display: flex; align-items: flex-end; flex-direction: column; gap: 8px; }
.diy-product-price strong { font-size: 20px; color: #f32236; white-space: nowrap; font-variant-numeric: tabular-nums; }
.diy-product-price small { color: #158144; font-size: 12px; }
.diy-product-price small.unavailable { color: #b37c30; }
.diy-product .el-button { width: 88px; height: 35px; margin: 0; font-weight: 600; }
.diy-product.selected .el-button { background: #0eac51; border-color: #0eac51; color: white; opacity: 1; }
.diy-empty { padding: 65px 18px; text-align: center; color: #637693; }
.diy-empty > svg { width: 32px; height: 32px; }
.diy-empty p { font-size: 13px; line-height: 1.7; }
.diy-candidate-note { text-align: center; font-size: 11px; color: #8a98ac; margin: 12px; }
.diy-configuration .diy-panel-heading { padding-inline: 16px; }
.diy-title-row .el-button { padding-inline: 10px; }
.diy-title-row .el-button svg { width: 16px; height: 16px; margin-right: 5px; }
.diy-configuration-body { padding: 8px 14px 18px; }
.diy-slot { display: flex; align-items: center; gap: 10px; border: 1px solid #e0e7f1; border-radius: 9px; margin-bottom: 5px; min-height: 48px; padding: 5px 10px; }
.diy-slot > svg, .diy-slot > img { flex-shrink: 0; width: 51px; height: 37px; object-fit: cover; color: #8c99ac; }
.diy-slot.slot-selected { min-height: 72px; }
.diy-slot.slot-invalid { background: #fff9ef; border-color: #e9cb95; }
.diy-slot-copy { flex: 1; min-width: 0; display: flex; flex-direction: column; line-height: 1.4; }
.diy-slot-copy > strong { font-size: 15px; color: #111e36; }
.diy-slot-copy > a { font-size: 13px; overflow-wrap: anywhere; }
.diy-slot-copy > span, .diy-slot-copy > small { font-size: 11px; color: #7f8da2; }
.diy-slot-copy .slot-price { color: #f32236; font-size: 15px; }
.diy-slot-copy .slot-problem { color: #ad6519; }
.slot-chosen { font-size: 12px; color: #0a9b42; white-space: nowrap; }
.slot-action { border: 0; background: transparent; padding: 4px; display: flex; align-items: center; }
.slot-action svg { width: 18px; height: 18px; }
.diy-slot .el-button { height: 30px; padding: 0 12px; }
.diy-total { margin-top: 16px; padding: 16px 18px; display: flex; align-items: center; justify-content: space-between; gap: 12px; background: linear-gradient(110deg, #eaf3ff, #e5edff); border-radius: 10px; }
.diy-total > div { display: flex; flex-direction: column; gap: 5px; }
.diy-total strong { font-size: 15px; }
.diy-total b { font-size: 29px; color: #ef2334; white-space: nowrap; font-variant-numeric: tabular-nums; }
.diy-total .pending-total { font-size: 20px; }
.diy-total p { margin: 0; font-size: 11px; color: #536888; line-height: 1.8; }
.diy-quote-meta { display: flex; justify-content: space-between; gap: 8px; align-items: center; margin: 12px 1px; font-size: 10px; color: #7d8da5; }
.diy-quote-meta button { border: 0; background: transparent; color: var(--blue); padding: 0; white-space: nowrap; }
.diy-quote-meta button:disabled { color: #8799b6; }
.diy-status-note { padding: 11px; background: #edf4ff; border: 1px solid #dceaff; border-radius: 9px; display: flex; gap: 8px; align-items: start; }
.diy-status-note svg { width: 18px; height: 18px; color: var(--blue); flex-shrink: 0; margin-top: 2px; }
.diy-status-note p { font-size: 11px; line-height: 1.8; margin: 0; color: #526887; }
.diy-status-note strong { font-size: 12px; color: #28466e; }
.diy-warning { color: #9c5d16; border: 1px solid #f0d6aa; background: #fff8ec; padding: 10px 12px; border-radius: 7px; font-size: 12px; line-height: 1.7; }
@media (max-width: 1400px) { .diy-columns { grid-template-columns: 242px minmax(420px, 1.4fr) minmax(350px, 1fr); } .diy-category-list > button { gap: 10px; padding-inline: 10px; } .category-copy small { font-size: 10px; } .selection-state { font-size: 10px; gap: 2px; } .diy-product { grid-template-columns: 65px minmax(0,1fr) auto; gap: 9px; } .diy-product-image img { width: 65px; } .diy-product > .el-button { grid-column: 3; grid-row: 2; justify-self: end; } .diy-product-image, .diy-product-copy { grid-row: span 2; } .diy-product-price { justify-content: end; } .diy-product-copy h3 { font-size: 14px; } .diy-product-price strong { font-size: 18px; } .diy-slot { gap: 6px; padding-inline: 7px; } .diy-slot > img, .diy-slot > svg { width: 36px; } .slot-chosen { display: none; } .diy-panel-heading h2 { font-size: 17px; } .diy-total { padding: 14px 12px; } .diy-total b { font-size: 25px; } }
@media (max-width: 1100px) { .diy-columns { grid-template-columns: 210px minmax(0, 1fr); } .diy-configuration { grid-column: 1 / -1; } .diy-heading > p, .category-copy small { display: none; } .diy-category-list > button { gap: 8px; } .category-copy strong { font-size: 13px; } .diy-category-list > button > svg { width: 25px; } .diy-candidate-list { min-height: 450px; } }
@media (max-width: 640px) { .diy-columns { grid-template-columns: minmax(0,1fr); } .diy-category-list { display: grid; grid-template-columns: 1fr 1fr; } .diy-category-list > button { min-height: 55px; } .diy-category-note { display: none; } .diy-heading { flex-wrap: wrap; gap: 10px; padding-block: 15px; } .diy-heading h1 { font-size: 24px; } .workspace-badge { margin-left: 0; } .diy-candidate-list { max-height: 580px; min-height: 220px; } .diy-search-row { flex-direction: column; align-items: stretch; } .diy-search-row form { max-width: none; } }
</style>
