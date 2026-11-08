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
const saved = ref(false)
const { workspace, storageWarning, count, select, remove, clear, save, quotation, compatibility } = useDiyWorkspace()
const { data: quote, status: quoteStatus, error: quoteError, refresh: refreshQuote } = quotation
const { data: check, status: checkStatus, error: checkError, refresh: refreshCheck } = compatibility
const { data: products, status, error, refresh } = useCatalogRequest(
  signal => loadProducts(signal, activeCategory.value, keyword.value), '候选商品')
const order: HardwareCategory[] = ['CPU', 'COOLER', 'MOTHERBOARD', 'MEMORY', 'GPU', 'SSD', 'PSU', 'CASE']
const displayCategories = order.map(code => hardwareCategories.find(category => category.code === code)!)
const labels: Record<HardwareCategory, string> = {
  CPU: 'CPU', MOTHERBOARD: '主板', GPU: '独立显卡', MEMORY: '内存',
  SSD: 'SSD 固态硬盘', PSU: '电源', CASE: '机箱', COOLER: 'CPU 散热器',
}
const activeLabel = computed(() => labels[activeCategory.value])
const brands = computed(() => [...new Set((products.value ?? []).map(product => product.brand))])
const candidates = computed(() => {
  const items = (products.value ?? []).filter(product => !brand.value || product.brand === brand.value)
  if (sort.value === 'price-asc') items.sort((a, b) => Number(a.price) - Number(b.price) || a.id - b.id)
  if (sort.value === 'price-desc') items.sort((a, b) => Number(b.price) - Number(a.price) || a.id - b.id)
  return items
})
const selectedId = (category: HardwareCategory) => workspace.value.selections.find(item => item.category === category)?.productId
const quoteItem = (category: HardwareCategory) => quote.value?.items.find(item => item.category === category)
const selectionName = (category: HardwareCategory) => quoteItem(category)?.product?.name
  ?? (selectedId(category) ? '商品 #' + selectedId(category) + (quoteStatus.value === 'loading' ? ' · 正在核价' : ' · 引用已保留') : '尚未选择')
const ruleLabels: Record<string, string> = {
  C01: 'CPU 与主板插槽', C02: '内存代际与条数', C03: 'CPU 与散热器插槽',
  C04: '主板板型与机箱', C05: 'SSD 规格、接口与协议',
}
const conflictSolutions: Record<string, string> = {
  C01: '更换为插槽一致的 CPU 或主板，替换后重新检测。',
  C02: '选择主板支持的内存代际，并确保套装实际条数不超过主板插槽数量。',
  C03: '更换为明确支持当前 CPU 插槽的独立散热器，替换后重新检测。',
  C04: '更换为支持当前主板板型的机箱，或选择机箱支持板型内的主板。',
  C05: '选择规格、接口和协议同时匹配主板同一个有效安装组合的 SSD，或更换提供该组合的主板。',
}
const conflicts = computed(() => check.value?.checks.filter(item => item.state === 'FAIL') ?? [])
const storyState = computed(() => {
  if (checkStatus.value === 'loading') return '正在检测'
  if (checkStatus.value === 'error') return '检测暂不可用'
  if (check.value?.checks.some(item => item.state === 'FAIL')) return '发现冲突'
  if (check.value?.missing.length) return '尚未选齐'
  if (check.value?.checks.some(item => item.state === 'UNKNOWN')) return '存在未知参数'
  return '分项已通过'
})
const checkTone = computed(() => {
  if (checkStatus.value !== 'ready') return 'pending'
  if (check.value?.checks.some(item => item.state === 'FAIL')) return 'fail'
  if (check.value?.checks.some(item => item.state === 'UNKNOWN')) return 'unknown'
  return check.value?.checks.some(item => item.state === 'PASS') ? 'pass' : 'pending'
})
const suggestions = computed(() => {
  const invalid = quote.value?.items.find(item => item.state !== 'VALID')
  if (invalid) return [invalid.message, '在左侧替换或移除失效配件，重新获取参考报价。']
  const result: string[] = []
  if (!selectedId('MOTHERBOARD')) result.push(selectedId('CPU')
    ? '优先选择与 CPU 插槽匹配的主板，再核对内存代际与实际条数。'
    : '可以从 CPU 或主板开始，按自己的顺序选择配件。')
  if (checkTone.value === 'unknown') result.unshift('先核实标记为“未知”的参数，再确认配件搭配。')
  if (!result.length) result.push(checkStatus.value === 'ready'
    ? '已检查的项目没有发现冲突；图形输出、功率与安装空间仍需进一步核验。'
    : '请先完成兼容检测，再确认这些配件的搭配；当前配置仍可继续编辑。')
  return result.slice(0, 3)
})
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
function saveConfiguration() { saved.value = save() }
watch(() => workspace.value.revision, () => { saved.value = false })
watch(activeCategory, () => { searchInput.value = keyword.value = brand.value = ''; sort.value = 'default'; void refresh() })
watch(keyword, () => { brand.value = ''; void refresh() })
onMounted(() => { void refresh() })
</script>

<template>
  <main class="diy-workspace page-width">
    <div class="diy-heading">
      <HardwareIcon name="monitor"/><h1>DIY 电脑装机</h1><p>自由选择八类硬件，搭建你的专属电脑配置</p>
      <span class="workspace-badge"><HardwareIcon name="alert"/>当前为装机方案编辑页，可随时修订配置</span>
    </div>
    <p v-if="storageWarning" class="diy-warning" role="alert">{{ storageWarning }}</p>
    <div class="diy-columns">
      <section class="diy-configuration-region" aria-label="当前配置">
        <section class="diy-panel diy-categories">
          <div class="diy-panel-heading"><h2><span>①</span> 硬件分类</h2><p>选择类别浏览商品，已选配件会显示在下方</p></div>
          <nav aria-label="DIY 硬件分类" class="diy-category-list">
            <div v-for="category in displayCategories" :key="category.code" class="diy-category"
              :class="{ active: activeCategory === category.code, chosen: selectedId(category.code), invalid: quoteItem(category.code) && quoteItem(category.code)!.state !== 'VALID' }"
              role="group" :aria-label="category.label + '配置'">
              <button class="diy-category-button" :aria-pressed="activeCategory === category.code"
                :aria-label="(category.code === 'GPU' ? '独立显卡' : category.label) + ' ' + selectionName(category.code)"
                @click="activeCategory = category.code">
                <HardwareIcon :name="category.icon"/>
                <span class="category-copy"><strong>{{ labels[category.code] }}</strong><small :title="selectionName(category.code)">{{ selectionName(category.code) }}</small></span>
                <span class="selection-state"><span>{{ selectedId(category.code) ? '✓' : '○' }}</span>{{ selectedId(category.code) ? '已选择' : '未选择' }}</span>
              </button>
              <div v-if="selectedId(category.code)" class="category-controls">
                <a v-if="quoteItem(category.code)?.product" :href="'#diy/product/' + selectedId(category.code)" :aria-label="quoteItem(category.code)!.product!.name">型号与详情 ↗</a>
                <span v-if="quoteItem(category.code)?.amount" class="slot-price">¥{{ formatPrice(quoteItem(category.code)!.amount!) }}</span>
                <button :aria-label="'替换 ' + category.label" title="替换配件" @click="activeCategory = category.code"><HardwareIcon name="replace"/></button>
                <button :aria-label="'移除 ' + category.label" title="移除配件" @click="remove(category.code)"><HardwareIcon name="trash"/></button>
              </div>
              <small v-if="quoteItem(category.code) && quoteItem(category.code)!.state !== 'VALID'" class="slot-problem">{{ quoteItem(category.code)!.message }}</small>
            </div>
          </nav>
          <p class="diy-category-note">按你的顺序，自由选配</p>
        </section>

        <section class="diy-panel diy-configuration" aria-label="配置修订">
          <div class="diy-panel-heading"><h2><span>③</span> 配置修订</h2><p>管理当前配置的变更、兼容性与修订建议</p></div>
          <div class="diy-configuration-body">
            <div class="diy-total" aria-live="polite">
              <div><strong>当前参考总价：</strong><b v-if="quoteStatus === 'ready' && quote?.total !== null" aria-label="当前参考总价">¥{{ formatPrice(quote!.total!) }}</b><b v-else class="pending-total" aria-label="当前参考总价">{{ quoteStatus === 'loading' ? '正在核价…' : quoteStatus === 'error' ? '报价暂不可用' : '配置存在问题' }}</b></div>
              <p>仅为当前配置的商品合计参考价，<br/>不代表最终成交价</p>
            </div>
            <p v-if="quoteStatus === 'ready' && quote?.total === null" class="diy-warning" role="alert">部分选择不可售，请替换或移除。可售项小计 ¥{{ formatPrice(quote.validSubtotal) }}，不是完整配置报价。</p>
            <p v-if="quoteStatus === 'error'" class="diy-warning" role="alert">{{ quoteError }}</p>
            <div class="diy-quote-meta"><span>已选择 {{ count }} / 8 项 · 修订 {{ workspace.revision }}</span><button :disabled="quoteStatus === 'loading'" @click="refreshQuote">{{ quoteStatus === 'error' ? '重试参考报价' : '刷新价格与库存' }}</button></div>

            <section class="diy-status-note" aria-label="兼容分项诊断" :aria-busy="checkStatus === 'loading'">
              <div class="revision-section-heading"><h3><HardwareIcon name="shield"/>兼容性检查</h3><span :class="'check-' + checkTone"><HardwareIcon :name="checkTone === 'pass' ? 'check' : 'alert'"/>{{ storyState }}</span></div>
              <p v-if="checkStatus === 'loading'" class="diy-check-note">正在读取服务端当前参数…</p>
              <p v-else-if="checkStatus === 'error'" class="diy-warning" role="alert">{{ checkError }}<button @click="refreshCheck">重试兼容检测</button></p>
              <template v-else-if="check">
                <ul v-if="conflicts.length" class="diy-conflicts" aria-label="兼容冲突">
                  <li v-for="item in conflicts" :key="item.rule">
                    <span class="conflict-dot" aria-hidden="true"></span>
                    <strong>{{ ruleLabels[item.rule] ?? '配件兼容冲突' }}</strong>
                  </li>
                </ul>
              </template>
            </section>

            <section class="diy-suggestions" aria-label="配置修订建议">
              <div class="revision-section-heading"><h3><HardwareIcon name="bulb"/>配置修订建议</h3></div>
              <ul v-if="conflicts.length" class="conflict-advice" aria-label="冲突原因与解决建议">
                <li v-for="item in conflicts" :key="item.rule">
                  <strong>{{ ruleLabels[item.rule] ?? '配件兼容冲突' }}</strong>
                  <p><span>原因：</span>{{ item.message }}</p>
                  <p><span>建议：</span>{{ conflictSolutions[item.rule] ?? '替换相关配件，核实参数后重新检测。' }}</p>
                </li>
              </ul>
              <ul v-else><li v-for="suggestion in suggestions" :key="suggestion">{{ suggestion }}</li></ul>
              <p class="verification-note">图形输出、功率与空间等检查尚未完成，当前不能确认整机兼容。</p>
            </section>
            <div class="diy-actions">
              <p class="diy-save-note" role="status">{{ saved ? '已保存到当前标签页，刷新后可继续编辑。' : storageWarning ? '配置尚未保存，当前页面仍可继续编辑；请重试保存。' : '方案保存在当前标签页，刷新后可继续编辑。' }}</p>
              <div><ElButton :disabled="!count" aria-label="清空配置" @click="clearOpen = true"><HardwareIcon name="reset"/>重置配置</ElButton><ElButton type="primary" :disabled="!count" @click="saveConfiguration"><HardwareIcon name="check"/>{{ saved ? '已保存方案' : '保存方案' }}</ElButton></div>
            </div>
          </div>
        </section>
      </section>

      <section class="diy-panel diy-candidates" aria-label="候选商品" :aria-busy="status === 'loading'">
        <div class="diy-panel-heading"><div class="diy-title-row"><h2><span>②</span> 候选商品（{{ activeLabel }}）</h2><span class="candidate-count">{{ status === 'ready' ? '共 ' + candidates.length + ' 件' : '加载中' }}</span></div><p>从下方选择一款，支持任意顺序选择、替换或移除</p></div>
        <div class="diy-filters">
          <div class="diy-brands"><span>品牌：</span><button :class="{ active: !brand }" @click="brand = ''">全部</button><button v-for="item in brands" :key="item" :class="{ active: brand === item }" @click="brand = item">{{ item }}</button></div>
          <div class="diy-search-row"><label>排序：<select v-model="sort" aria-label="候选商品排序"><option value="default">综合排序</option><option value="price-asc">价格从低到高</option><option value="price-desc">价格从高到低</option></select></label><form @submit.prevent="search"><HardwareIcon name="search"/><input v-model="searchInput" aria-label="搜索当前类别商品" placeholder="在当前类别中搜索型号或参数" maxlength="100"/><button type="submit" aria-label="搜索候选商品">搜索</button></form></div>
        </div>
        <div class="diy-candidate-list">
          <ElSkeleton v-if="status === 'loading'" animated :rows="8"/>
          <div v-else-if="status === 'error'" class="diy-empty" role="alert"><HardwareIcon name="alert"/><h3>候选商品加载失败</h3><p>{{ error }}</p><ElButton type="primary" @click="refresh">重新加载候选商品</ElButton></div>
          <ElEmpty v-else-if="!candidates.length" description="没有找到符合条件的商品"><ElButton @click="resetFilters">清除候选筛选</ElButton></ElEmpty>
          <article v-for="product in status === 'ready' ? candidates : []" :key="product.id" class="diy-product" :class="{ selected: selectedId(product.category) === product.id }">
            <a :href="'#diy/product/' + product.id" class="diy-product-image" tabindex="-1" aria-hidden="true"><img :src="product.imageUrl" :alt="product.categoryLabel + '品类示意图'" @error="imageFallback"/></a>
            <div class="diy-product-copy"><h3><a :href="'#diy/product/' + product.id">{{ product.name }}</a></h3><p>{{ product.summary }}</p><ProductPerformanceTags :tags="product.performanceTags" compact/><a :href="'#diy/product/' + product.id" class="diy-spec-link">型号与完整参数 ›</a></div>
            <div class="diy-product-price"><strong>¥{{ formatPrice(product.price) }}</strong><small :class="{ unavailable: !product.stock }">{{ product.stock ? '库存 ' + product.stock + ' 件' : '暂时缺货' }}</small></div>
            <ElButton :type="selectedId(product.category) === product.id ? 'success' : 'primary'" :plain="selectedId(product.category) !== product.id"
              :disabled="product.stock === 0 || selectedId(product.category) === product.id" :aria-label="(selectedId(product.category) === product.id ? '已选择' : '选择') + ' ' + product.name" @click="select(product)">
              {{ selectedId(product.category) === product.id ? '✓ 已选择' : '＋ 选择' }}
            </ElButton>
          </article>
        </div>
        <p class="diy-candidate-note">图片为品类示意，价格与库存为商城实际数据</p>
      </section>
    </div>
    <ElDialog v-model="clearOpen" title="清空当前配置？" width="420px" align-center :close-on-click-modal="false"><p>将移除本标签页的全部已选硬件。取消可继续编辑。</p><template #footer><ElButton @click="clearOpen = false">取消</ElButton><ElButton type="danger" @click="clearConfiguration">确认清空</ElButton></template></ElDialog>
  </main>
</template>

<style scoped>
.diy-workspace { color: #101a35; padding-bottom: 26px; }
.diy-heading { display: flex; align-items: center; gap: 18px; min-height: 78px; }
.diy-heading > svg { width: 38px; height: 38px; color: var(--blue); flex-shrink: 0; }
.diy-heading h1 { font-size: 27px; margin: 0; color: #080f2c; white-space: nowrap; }
.diy-heading > p { border-left: 1px solid #dce4ef; padding-left: 18px; font-size: 14px; color: #344b72; }
.workspace-badge { margin-left: auto; display: flex; align-items: center; gap: 7px; background: #e2ebff; border-radius: 10px; padding: 11px 14px; color: #345582; font-size: 12px; white-space: nowrap; }
.workspace-badge svg { width: 17px; height: 17px; color: var(--blue); }
.diy-columns { display: grid; grid-template-columns: minmax(265px, .72fr) minmax(0, 1.36fr) minmax(0, 1fr); gap: 12px; align-items: stretch; }
/* Group the selected references and revision tools as one accessible configuration region. */
.diy-configuration-region { display: contents; }
.diy-panel { min-width: 0; min-height: max(680px, calc(100vh - 222px)); background: #ffffffdc; border: 1px solid #dce5f5; border-radius: 19px; overflow: hidden; }
.diy-categories { grid-column: 1; grid-row: 1; display: flex; flex-direction: column; }
.diy-candidates { grid-column: 2; grid-row: 1; display: flex; flex-direction: column; }
.diy-configuration { grid-column: 3; grid-row: 1; display: flex; flex-direction: column; }
.diy-panel-heading { padding: 15px 17px 13px; border-bottom: 1px solid #edf1f8; }
.diy-panel-heading h2 { margin: 0; font-size: 21px; line-height: 1.5; color: #0b1025; white-space: nowrap; }
.diy-panel-heading h2 > span { color: var(--blue); margin-right: 3px; }
.diy-panel-heading p { margin: 6px 0 0; font-size: 12px; line-height: 1.7; color: #45567b; }
.diy-title-row { display: flex; justify-content: space-between; align-items: center; gap: 8px; }
.candidate-count { background: #eaf0ff; padding: 3px 10px; border-radius: 20px; font-size: 12px; color: #334c78; white-space: nowrap; }
.diy-category-list { padding: 12px 10px 0; }
.diy-category { border: 1px solid #edf1f9; border-radius: 10px; margin-bottom: 3px; background: #fff; overflow: hidden; }
.diy-category.active, .diy-category.chosen { background: #eaf1ff; border-color: #eaf1ff; }
.diy-category.invalid { background: #fff8ec; border-color: #edcf9b; }
.diy-category-button { display: flex; align-items: center; gap: 13px; width: 100%; min-height: 59px; padding: 9px 12px; border: 0; background: transparent; text-align: left; }
.diy-category-button:hover { background: #e8effc; }
.diy-category.chosen .diy-category-button { min-height: 54px; padding-block: 7px; }
.diy-category-button > svg { width: 31px; height: 31px; flex-shrink: 0; color: #111626; }
.diy-category.active .diy-category-button > svg { color: var(--blue); }
.category-copy { display: flex; flex-direction: column; gap: 4px; min-width: 0; flex: 1; }
.category-copy strong { font-size: 17px; color: #080e20; }
.category-copy small { font-size: 12px; color: #42547b; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.selection-state { display: flex; align-items: center; gap: 4px; font-size: 12px; color: #7e8cab; white-space: nowrap; }
.selection-state > span { font-size: 19px; line-height: 1; }
.chosen .selection-state { color: #079a24; font-weight: 650; }
.category-controls { display: flex; align-items: center; gap: 9px; padding: 0 10px 4px 56px; margin-top: -2px; min-height: 19px; font-size: 10px; color: #4b6a9b; }
.category-controls a:hover { color: var(--blue); }
.category-controls .slot-price { color: #bd4d61; margin-left: auto; font-variant-numeric: tabular-nums; }
.category-controls button { display: flex; padding: 0; background: transparent; border: 0; color: #5c75a0; }
.category-controls button:hover { color: var(--blue); }
.category-controls button:last-child:hover { color: #d6364f; }
.category-controls svg { width: 14px; height: 14px; }
.slot-problem { display: block; padding: 0 12px 8px 56px; font-size: 11px; color: #995a17; line-height: 1.6; }
.diy-category-note { margin: auto 10px 0; padding: 18px 0 24px; font-size: 12px; color: #53658a; text-align: center; line-height: 1.8; }
.diy-filters { padding: 17px 16px 14px; display: grid; gap: 15px; font-size: 13px; }
.diy-brands { display: flex; align-items: center; gap: 7px; flex-wrap: wrap; }
.diy-brands > span { font-weight: 600; }
.diy-brands button { border: 1px solid #dfe6f4; border-radius: 6px; background: #fff; padding: 5px 12px; font-size: 12px; }
.diy-brands button.active { background: #0760ff; border-color: #0760ff; color: #fff; box-shadow: 0 2px 4px #0968fa15; }
.diy-search-row { display: flex; align-items: center; gap: 18px; justify-content: space-between; }
.diy-search-row label { white-space: nowrap; font-weight: 600; font-size: 12px; }
.diy-search-row select { border: 1px solid #dce3f0; border-radius: 6px; color: #1f3152; background: #fff; padding: 7px 9px; max-width: 125px; font-size: 12px; }
.diy-search-row form { display: flex; align-items: center; border: 1px solid #dce3f0; border-radius: 6px; padding: 0 9px; min-width: 0; height: 34px; flex: 1; max-width: 325px; }
.diy-search-row form svg { width: 16px; height: 16px; flex-shrink: 0; color: #60749f; }
.diy-search-row input { border: 0; outline: 0; background: transparent; padding: 4px 6px; width: 100%; min-width: 0; font-size: 12px; }
.diy-search-row form button { border: 0; background: transparent; white-space: nowrap; padding: 3px; color: var(--blue); font-size: 12px; }
.diy-candidate-list { margin: 0 12px; padding: 3px 0; min-height: 320px; max-height: 660px; overflow-y: auto; scrollbar-width: thin; scrollbar-color: #d0daf0 transparent; }
.diy-product { display: grid; grid-template-columns: 78px minmax(0, 1fr) auto 82px; align-items: center; gap: 10px; min-height: 132px; border: 1px solid #dfe6f4; border-radius: 13px; padding: 13px 12px; margin-bottom: 8px; background: #fff9; }
.diy-product.selected { border-color: #90b5fc; background: #f0f5ff; }
.diy-product-image { display: flex; align-items: center; justify-content: center; height: 88px; }
.diy-product-image img { width: 78px; height: 88px; object-fit: contain; }
.diy-product-copy { min-width: 0; }
.diy-product-copy h3 { font-size: 18px; margin: 0 0 6px; color: #0a102c; line-height: 1.45; }
.diy-product-copy h3 a:hover { color: var(--blue); }
.diy-product-copy > p { font-size: 12px; color: #4a5c7e; margin: 0 0 6px; line-height: 1.6; }
.diy-product-copy :deep(.performance-tags) { margin-block: 5px; }
.diy-product-copy :deep(.performance-tag) { font-size: 10px; }
.diy-spec-link { font-size: 10px; color: #62759c; }
.diy-product-price { display: flex; align-items: flex-end; flex-direction: column; gap: 8px; }
.diy-product-price strong { font-size: 20px; color: #ff101c; white-space: nowrap; font-variant-numeric: tabular-nums; }
.diy-product-price small { color: #0b9d29; font-size: 12px; white-space: nowrap; }
.diy-product-price small.unavailable { color: #ac7328; }
.diy-product .el-button { width: 82px; height: 35px; margin: 0; font-weight: 600; }
.diy-product.selected .el-button { background: #e9f7ed; border-color: #b2dfbe; color: #0b9131; opacity: 1; }
.diy-empty { padding: 65px 18px; text-align: center; color: #637693; }
.diy-empty > svg { width: 32px; height: 32px; }
.diy-empty p { font-size: 13px; line-height: 1.7; }
.diy-candidate-note { text-align: center; font-size: 12px; color: #53658a; margin: auto 15px 0; padding: 28px 0 54px; }
.diy-configuration-body { padding: 12px 12px 18px; display: flex; flex-direction: column; gap: 10px; flex: 1; }
.diy-total { padding: 11px 15px; display: flex; align-items: center; justify-content: space-between; gap: 12px; background: linear-gradient(115deg, #eaf1ff, #e2eaff); border-radius: 10px; }
.diy-total > div { display: flex; flex-direction: column; gap: 7px; }
.diy-total strong { font-size: 16px; color: #0c1425; }
.diy-total b { font-size: 34px; font-weight: 750; color: #f31a35; white-space: nowrap; font-variant-numeric: tabular-nums; }
.diy-total .pending-total { font-size: 19px; }
.diy-total p { margin: 0; font-size: 11px; color: #3d5682; line-height: 1.9; max-width: 46%; }
.diy-quote-meta { display: flex; justify-content: space-between; gap: 7px; align-items: center; font-size: 10px; color: #7485a6; margin-top: -5px; }
.diy-quote-meta button { border: 0; background: transparent; color: #4774b4; padding: 0; white-space: nowrap; font-size: inherit; }
.diy-quote-meta button:disabled { color: #8799b6; }
.diy-status-note, .diy-suggestions { border: 1px solid #e0e7f5; border-radius: 11px; overflow: hidden; }
.revision-section-heading { display: flex; justify-content: space-between; align-items: center; gap: 8px; padding: 7px 10px; background: linear-gradient(100deg, #f7faff, #eaf1ff); }
.revision-section-heading h3 { display: flex; align-items: center; gap: 8px; margin: 0; font-size: 18px; color: #0d172c; }
.revision-section-heading h3 svg { width: 23px; height: 23px; color: #0861ff; }
.revision-section-heading > span { display: flex; align-items: center; gap: 4px; font-size: 11px; font-weight: 600; white-space: nowrap; }
.revision-section-heading > span svg { width: 16px; height: 16px; }
.check-pass { color: #0a9e2b; }.check-fail { color: #c42d3c; }.check-unknown { color: #a96e15; }.check-pending { color: #6d83ab; }
.diy-conflicts { list-style: none; margin: 8px 13px 10px; padding: 0; }
.diy-conflicts li { display: flex; align-items: flex-start; gap: 8px; padding: 6px 0; color: #a43138; font-size: 12px; line-height: 1.7; }
.conflict-dot { width: 6px; height: 6px; margin-top: 7px; border-radius: 50%; background: #dc4854; flex-shrink: 0; }
.diy-conflicts strong { font-weight: 600; }
.diy-check-note { font-size: 12px; line-height: 1.8; color: #617394; margin: 12px; }
.diy-suggestions ul { margin: 8px 13px; padding-left: 17px; color: #26354f; font-size: 13px; line-height: 1.7; }
.diy-suggestions li { padding-left: 1px; margin: 3px 0; }
.diy-suggestions .conflict-advice { list-style: none; padding-left: 0; }
.conflict-advice li + li { border-top: 1px solid #edf1f8; padding-top: 9px; margin-top: 9px; }
.conflict-advice strong { color: #a43138; font-size: 12px; }
.conflict-advice p { margin: 4px 0; font-size: 12px; color: #4a5a77; line-height: 1.8; }
.conflict-advice p span { color: #203959; font-weight: 600; }
.verification-note { margin: 8px 12px 10px; font-size: 10px; color: #7e8da6; line-height: 1.65; }
.diy-actions { margin-top: auto; padding-top: 8px; }
.diy-actions > div { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
.diy-actions .el-button { height: 41px; margin: 0; font-size: 14px; font-weight: 650; border-color: #5389ff; color: #0968fa; border-radius: 8px; background: #fff; }
.diy-actions .el-button--primary { background: #075dff; color: #fff; border-color: #075dff; }
.diy-actions .el-button.is-disabled { opacity: .5; }
.diy-actions svg { width: 18px; height: 18px; margin-right: 7px; }
.diy-save-note { text-align: center; font-size: 10px; line-height: 1.7; color: #8593ae; margin: 0 0 9px; }
.diy-warning { color: #9c5d16; border: 1px solid #f0d6aa; background: #fff8ec; padding: 10px 12px; border-radius: 7px; font-size: 12px; line-height: 1.7; margin: 0; }
.diy-warning button { color: #0968fa; border: 0; background: transparent; padding-inline: 5px; }
@media (max-width: 1450px) {
  .diy-columns { grid-template-columns: minmax(240px, .73fr) minmax(0, 1.3fr) minmax(340px, 1fr); }
  .diy-category-button { padding-inline: 9px; gap: 9px; }.category-copy strong { font-size: 15px; }.category-copy small { font-size: 11px; }.selection-state { font-size: 10px; }
  .diy-product { grid-template-columns: 66px minmax(0,1fr) auto; }.diy-product-image img { width: 66px; }
  .diy-product > .el-button { grid-column: 3; grid-row: 2; justify-self: end; }.diy-product-image, .diy-product-copy { grid-row: span 2; }.diy-product-copy h3 { font-size: 15px; }
  .diy-heading { gap: 12px; }.diy-heading > p { font-size: 12px; padding-left: 12px; }.workspace-badge { font-size: 10px; padding: 9px; }
  .diy-total { padding: 12px; }.diy-total b { font-size: 28px; }.diy-total p { font-size: 10px; }.diy-panel-heading h2 { font-size: 18px; }
}
@media (max-width: 1100px) {
  .diy-columns { grid-template-columns: 260px minmax(0, 1fr); }.diy-configuration { grid-column: 1 / -1; grid-row: 2; min-height: 0; }.diy-panel { min-height: 650px; }
  .diy-configuration { min-height: 0; }.diy-configuration-body { display: grid; grid-template-columns: 1fr 1fr; align-items: start; }.diy-status-note { grid-column: 2; grid-row: 1 / 5; }.diy-actions { grid-column: 1 / -1; }
  .diy-heading > p, .workspace-badge { display: none; }.diy-heading h1 { font-size: 24px; }.diy-heading { min-height: 74px; }
}
@media (max-width: 700px) {
  .diy-columns { grid-template-columns: minmax(0, 1fr); }.diy-categories { grid-column: 1; grid-row: 1; min-height: 0; }.diy-candidates { grid-column: 1; grid-row: 2; min-height: 420px; }.diy-configuration { grid-column: 1; grid-row: 3; }
  .diy-category-list { display: grid; grid-template-columns: 1fr 1fr; gap: 4px; }.diy-category-button { min-height: 64px; gap: 8px; }.diy-category-button > svg { width: 24px; height: 24px; }.selection-state { display: none; }
  .category-controls { padding-left: 10px; gap: 7px; }.category-controls .slot-price { margin-left: auto; }.diy-category-note { padding: 12px 0; font-size: 11px; }
  .diy-configuration-body { display: flex; }.diy-candidate-list { min-height: 220px; }.diy-candidate-note { padding: 20px 0; font-size: 11px; }.diy-total b { font-size: 30px; }
  .diy-search-row { gap: 8px; }.diy-search-row select { width: 96px; }.diy-product { padding: 11px 9px; }.diy-product-price strong { font-size: 18px; }
}
</style>
