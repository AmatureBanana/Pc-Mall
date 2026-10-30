<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { ElButton, ElEmpty, ElSkeleton } from 'element-plus'
import HardwareIcon from './HardwareIcon.vue'
import StoreBrand from './StoreBrand.vue'
import ProductDetail from './ProductDetail.vue'
import { categories, formatPrice, imageFallback, loadProducts, type Category } from './catalog'
import { useCatalogRequest } from './useCatalogRequest'

const selectedCategory = ref<Category>('ALL')
const searchInput = ref('')
const keyword = ref('')
const sort = ref('default')
const minInput = ref('')
const maxInput = ref('')
const priceRange = ref({ min: 0, max: Infinity })
const filterError = ref('')
const showDataNote = ref(false)
const detailId = ref<string | null>(null)
const { data, status, error, refresh } = useCatalogRequest(
  signal => loadProducts(signal, selectedCategory.value, keyword.value), '商品')
const products = computed(() => data.value ?? [])
const visibleProducts = computed(() => {
  const items = products.value.filter(product =>
    Number(product.price) >= priceRange.value.min && Number(product.price) <= priceRange.value.max)
  if (sort.value === 'price-asc') items.sort((a, b) => Number(a.price) - Number(b.price) || a.id - b.id)
  if (sort.value === 'price-desc') items.sort((a, b) => Number(b.price) - Number(a.price) || a.id - b.id)
  return items
})
function search() {
  const next = searchInput.value.trim()
  if (keyword.value === next) void refresh()
  else keyword.value = next
}
function applyPrice() {
  const valid = (value: string) => value === '' || /^\d+(\.\d{1,2})?$/.test(value)
  const min = minInput.value === '' ? 0 : Number(minInput.value)
  const max = maxInput.value === '' ? Infinity : Number(maxInput.value)
  if (!valid(minInput.value) || !valid(maxInput.value) || min > max || !Number.isFinite(min) || (maxInput.value !== '' && !Number.isFinite(max))) {
    filterError.value = '请输入有效金额，最低价不能高于最高价。'
    return
  }
  filterError.value = ''
  priceRange.value = { min, max }
}
function resetFilters() {
  selectedCategory.value = 'ALL'
  searchInput.value = keyword.value = minInput.value = maxInput.value = filterError.value = ''
  priceRange.value = { min: 0, max: Infinity }
}
function syncDetail() {
  detailId.value = window.location.hash.startsWith('#product/') ? window.location.hash.slice(9) : null
}
function closeDetail() { window.location.hash = '' }
onMounted(() => { void refresh(); syncDetail(); window.addEventListener('hashchange', syncDetail) })
watch([selectedCategory, keyword], refresh)
onUnmounted(() => window.removeEventListener('hashchange', syncDetail))
</script>

<template>
  <div class="storefront">
    <header class="header"><div class="header-inner page-width">
      <StoreBrand/>
      <p class="tagline">专业 · 可靠 · 用心的电脑硬件商城</p>
      <form class="search" role="search" @submit.prevent="search"><HardwareIcon name="search"/><input v-model="searchInput" aria-label="搜索商品" placeholder="搜索商品名称、型号或关键词（如：RTX 4070、i5、SSD …）" maxlength="100"/><ElButton native-type="submit" type="primary" class="search-button"><HardwareIcon name="search"/>搜索</ElButton></form>
    </div></header>
    <nav class="category-nav" aria-label="硬件分类"><div class="nav-inner page-width">
      <button v-for="category in categories" :key="category.code" :class="{ active: selectedCategory === category.code }" :aria-pressed="selectedCategory === category.code" @click="selectedCategory = category.code"><HardwareIcon :name="category.icon"/>{{ category.code === 'COOLER' ? '散热器' : category.label }}</button>
    </div></nav>
    <main class="main page-width">
      <aside class="sidebar" aria-label="商品筛选">
        <section class="filter-panel category-panel"><h2>商品分类</h2><div class="category-list"><button v-for="category in categories" :key="category.code" :class="{ active: selectedCategory === category.code }" :aria-pressed="selectedCategory === category.code" @click="selectedCategory = category.code"><HardwareIcon :name="category.icon"/><span>{{ category.label }}</span></button></div></section>
        <section class="filter-panel price-panel"><h2>价格区间</h2><form @submit.prevent="applyPrice"><div class="price-inputs"><label><span>¥</span><input v-model="minInput" aria-label="最低价" inputmode="decimal" placeholder="最低价"/></label><span class="range-dash">–</span><label><span>¥</span><input v-model="maxInput" aria-label="最高价" inputmode="decimal" placeholder="最高价"/></label></div><p v-if="filterError" class="filter-error" role="alert">{{ filterError }}</p><ElButton type="primary" native-type="submit">确定</ElButton></form></section>
        <p class="sidebar-note"><HardwareIcon name="check"/>真实型号 · 清晰价格 · 当前库存</p>
      </aside>
      <section class="catalog" aria-label="商品列表" :aria-busy="status === 'loading'">
        <div class="catalog-toolbar"><p aria-live="polite">{{ status === 'loading' ? '正在加载商品…' : status === 'error' ? '商品暂时无法加载' : `共 ${visibleProducts.length} 件商品` }}<span v-if="status === 'ready'" class="demo-label">演示商品</span></p><label class="sort"><span class="sr-only">商品排序</span><select v-model="sort" aria-label="商品排序"><option value="default">综合排序</option><option value="price-asc">价格从低到高</option><option value="price-desc">价格从高到低</option></select></label></div>
        <div v-if="status === 'loading'" class="product-grid skeleton-grid" aria-label="正在加载商品"><div v-for="n in 8" :key="n" class="product-card skeleton-card"><ElSkeleton animated :rows="4"/></div></div>
        <div v-else-if="status === 'error'" class="state-panel error-panel" role="alert"><div class="state-icon"><HardwareIcon name="alert"/></div><h1>商品加载失败</h1><p>{{ error }}</p><ElButton type="primary" @click="refresh">重新加载</ElButton></div>
        <div v-else-if="visibleProducts.length === 0" class="state-panel"><ElEmpty description="没有找到符合条件的商品"><ElButton type="primary" @click="resetFilters">清除筛选</ElButton></ElEmpty></div>
        <div v-else class="product-grid"><article v-for="product in visibleProducts" :key="product.id" class="product-card"><div class="product-image"><a :href="`#product/${product.id}`" tabindex="-1" aria-hidden="true"><img :src="product.imageUrl" :alt="`${product.categoryLabel}品类示意图`" @error="imageFallback"/></a><span class="illustration-label">品类示意</span></div><div class="product-info"><h2 :title="product.name"><a :href="`#product/${product.id}`">{{ product.name }}</a></h2><p class="product-meta"><span>{{ product.categoryLabel }}</span><span class="meta-dot">·</span>{{ product.summary }}</p><div class="product-bottom"><span class="product-price"><span class="currency-symbol">¥</span> {{ formatPrice(product.price) }}</span><span class="stock" :class="{ unavailable: product.stock === 0 }">{{ product.stock === 0 ? '暂时缺货' : `库存 ${product.stock} 件` }}</span></div></div></article></div>
        <p v-if="status === 'ready'" class="catalog-note">价格与库存仅供本机演示，非实时市场报价。<button @click="showDataNote = !showDataNote" :aria-expanded="showDataNote">{{ showDataNote ? '收起数据说明' : '查看数据说明' }} ↗</button></p>
        <div v-if="showDataNote" class="data-note"><h2>关于演示数据</h2><p>商品型号来自品牌公开资料；图片为本站绘制的品类示意图，不代表具体型号外观。价格和库存是数据库中的演示值，刷新页面可获取当前值。</p><ul><li v-for="product in products" :key="product.id"><a :href="product.sourceUrl" target="_blank" rel="noopener noreferrer">{{ product.name }} · 厂商资料</a></li></ul></div>
      </section>
    </main>
    <ProductDetail v-if="detailId !== null" :id="detailId" @close="closeDetail"/>
    <footer class="footer"><div class="page-width footer-inner"><StoreBrand class="footer-brand"/><p>专业 · 可靠 · 用心的电脑硬件商城</p><span class="footer-note">每一件配件，都为你的下一台电脑。</span></div></footer>
  </div>
</template>
