<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { ElButton, ElEmpty, ElSkeleton } from 'element-plus'
import HardwareIcon from './HardwareIcon.vue'
import StoreBrand from './StoreBrand.vue'
import ProductDetail from './ProductDetail.vue'
import ProductPerformanceTags from './ProductPerformanceTags.vue'
import AccountPanel from './AccountPanel.vue'
import DiyWorkspace from './DiyWorkspace.vue'
import DiyWelcome from './DiyWelcome.vue'
import CartPanel from './CartPanel.vue'
import OrderPanel from './OrderPanel.vue'
import OrderHistory from './OrderHistory.vue'
import UserCenter from './UserCenter.vue'
import ProfilePanel from './ProfilePanel.vue'
import { categories, formatPrice, imageFallback, loadProducts, motherboardPlatform, type Category } from './catalog'
import { useCatalogRequest } from './useCatalogRequest'
import { signedIn } from './auth'
import { useCart } from './cart'

const { count: cartCount, leaveEdit } = useCart()
const accountPanel = ref<InstanceType<typeof AccountPanel> | null>(null)
function openCart() { window.location.hash = 'cart' }
function openDiy() { leaveEdit(); window.location.hash = 'diy' }
function openDiyWelcome() { leaveEdit(); window.location.hash = '' }

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
const orderNumber = ref<string | null>(null)
const ordersOpen = ref(window.location.hash === '#orders')
const diyOpen = ref(window.location.hash.startsWith('#diy'))
const welcomeOpen = ref(!window.location.hash || window.location.hash === '#')
const diySection = computed(() => diyOpen.value || welcomeOpen.value)
const cartOpen = ref(window.location.hash === '#cart')
const accountRoute = ref(window.location.hash.slice(1))
const userCenterOpen = computed(() => ordersOpen.value || !!orderNumber.value || cartOpen.value || ['profile', 'security'].includes(accountRoute.value))
const accountSection = computed(() => ordersOpen.value || orderNumber.value ? 'orders' : cartOpen.value ? 'cart' : accountRoute.value)
const { data, status, error, refresh } = useCatalogRequest(
  signal => loadProducts(signal, selectedCategory.value, keyword.value), '商品')
const products = computed(() => data.value ?? [])
const showSummaryCategory = (category: Category) => category === 'CPU' || category === 'MOTHERBOARD'
const visibleProducts = computed(() => {
  const items = products.value.filter(product =>
    Number(product.price) >= priceRange.value.min && Number(product.price) <= priceRange.value.max)
  if (sort.value === 'price-asc') items.sort((a, b) => Number(a.price) - Number(b.price) || a.id - b.id)
  if (sort.value === 'price-desc') items.sort((a, b) => Number(b.price) - Number(a.price) || a.id - b.id)
  return items
})
function search() {
  window.location.hash = 'catalog'
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
  const hash = window.location.hash
  const previousRoute = accountRoute.value
  accountRoute.value = hash.slice(1)
  orderNumber.value = hash.startsWith('#order/') ? hash.slice(7) : null
  ordersOpen.value = hash === '#orders'
  cartOpen.value = hash === '#cart'
  if (hash.startsWith('#diy')) { diyOpen.value = true; welcomeOpen.value = false }
  else if (hash === '' || hash === '#') { diyOpen.value = false; welcomeOpen.value = true }
  else if (hash === '#catalog' || hash.startsWith('#product/')) { diyOpen.value = false; welcomeOpen.value = false }
  if (hash === '#catalog' && previousRoute !== 'catalog') void refresh()
  detailId.value = hash.startsWith('#product/') ? hash.slice(9) : hash.startsWith('#diy/product/') ? hash.slice(13) : null
}
function closeDetail() { window.location.hash = diyOpen.value ? 'diy' : 'catalog' }
/** The unified login is the only sign-in surface, so the detail dialog hands over to it. */
function openDetailSignIn() { window.location.hash = 'login' }
onMounted(() => {
  void refresh(); syncDetail()
  window.addEventListener('hashchange', syncDetail)
})
watch([selectedCategory, keyword], refresh)
onUnmounted(() => window.removeEventListener('hashchange', syncDetail))
</script>

<template>
  <div class="storefront" :class="{ 'storefront--diy': diySection && !userCenterOpen, 'storefront--cart': cartOpen, 'storefront--account': userCenterOpen }">
    <AccountPanel ref="accountPanel"/>
    <header class="header"><div class="header-inner page-width">
      <StoreBrand/>
      <p v-if="!diySection && !userCenterOpen" class="tagline">专业 · 可靠 · 用心的电脑硬件商城</p>
      <nav class="workspace-nav" aria-label="商城导航"><a href="#catalog" :class="{ active: !diySection && !userCenterOpen }"><HardwareIcon name="grid"/>商品浏览</a><a href="#" :class="{ active: diySection && !userCenterOpen }" @click.prevent="openDiyWelcome"><HardwareIcon name="tools"/>DIY 装机</a></nav>
      <form class="search" role="search" @submit.prevent="search"><HardwareIcon name="search"/><input v-model="searchInput" aria-label="搜索商品" placeholder="搜索商品名称、型号或关键词（如：RTX 4070、i5、SSD …）" maxlength="100"/><ElButton native-type="submit" type="primary" class="search-button"><HardwareIcon name="search"/>搜索</ElButton></form>
      <button v-if="signedIn" type="button" class="header-cart" :aria-current="cartOpen ? 'page' : undefined" @click="openCart"><HardwareIcon name="cart"/>购物车<span v-if="cartCount" class="cart-count" :aria-label="`购物车共 ${cartCount} 件`">{{ cartCount }}</span><HardwareIcon name="chevron-right"/></button>
      <div id="header-account" class="header-account"/>
    </div></header>
    <nav v-if="!diySection && !userCenterOpen" class="category-nav" aria-label="硬件分类"><div class="nav-inner page-width">
      <button v-for="category in categories" :key="category.code" :class="{ active: selectedCategory === category.code }" :aria-pressed="selectedCategory === category.code" @click="selectedCategory = category.code"><HardwareIcon :name="category.icon"/>{{ category.label }}</button>
    </div></nav>
    <DiyWelcome v-if="welcomeOpen && !userCenterOpen" @start-diy="openDiy"/>
    <DiyWorkspace v-if="diyOpen" v-show="!userCenterOpen" @session-expired="accountPanel?.profileExpired()"/>
    <UserCenter v-if="userCenterOpen" :section="accountSection">
      <OrderPanel v-if="orderNumber" :number="orderNumber"/>
      <OrderHistory v-else-if="ordersOpen"/>
      <CartPanel v-else-if="cartOpen && signedIn" @close="closeDetail"/>
      <main v-else-if="cartOpen" class="page-width" aria-label="我的购物车"><ElEmpty description="请先登录查看购物车。"><ElButton type="primary" @click="openDetailSignIn">前往登录</ElButton></ElEmpty></main>
      <ProfilePanel v-else :key="accountSection" :mode="accountSection === 'security' ? 'security' : 'profile'"
        @updated="accountPanel?.profileUpdated($event)" @expired="accountPanel?.profileExpired()"
        @password-changed="accountPanel?.passwordChanged()" @busy="accountPanel?.setProfileBusy($event)"/>
    </UserCenter>
    <main v-else-if="!diySection" class="main page-width">
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
        <div v-else class="product-grid"><article v-for="product in visibleProducts" :key="product.id" class="product-card"><div class="product-image"><a :data-motherboard-platform="motherboardPlatform(product)" :href="`#product/${product.id}`" tabindex="-1" aria-hidden="true"><img :src="product.imageUrl" :alt="`${product.categoryLabel}品类示意图`" @error="imageFallback"/></a><span class="illustration-label">品类示意</span></div><div class="product-info"><h2 :title="product.name"><a :href="`#product/${product.id}`">{{ product.name }}</a></h2><p class="product-meta" :class="{ 'product-meta--specs': !showSummaryCategory(product.category) }"><template v-if="showSummaryCategory(product.category)"><span>{{ product.categoryLabel }}</span><span class="meta-dot">·</span></template>{{ product.summary }}</p><ProductPerformanceTags :tags="product.performanceTags" compact/><div class="product-bottom"><span class="product-price"><span class="currency-symbol">¥</span> {{ formatPrice(product.price) }}</span><span class="stock" :class="{ unavailable: product.stock === 0 }">{{ product.stock === 0 ? '暂时缺货' : `库存 ${product.stock} 件` }}</span></div></div></article></div>
        <p v-if="status === 'ready'" class="catalog-note">价格与库存仅供本机演示，非实时市场报价。<button @click="showDataNote = !showDataNote" :aria-expanded="showDataNote">{{ showDataNote ? '收起数据说明' : '查看数据说明' }} ↗</button></p>
        <div v-if="showDataNote" class="data-note"><h2>关于演示数据</h2><p>商品型号来自公开资料。图片为本站绘制的品类示意图。价格和库存采用本地演示数据。</p><ul><li v-for="product in products" :key="product.id"><a :href="product.sourceUrl" target="_blank" rel="noopener noreferrer">{{ product.name }} · 型号资料</a></li></ul></div>
      </section>
    </main>
    <ProductDetail v-if="detailId !== null" :id="detailId" :signed-in="signedIn" @close="closeDetail" @sign-in="openDetailSignIn"/>
    <footer v-if="!diySection && !userCenterOpen" class="footer"><div class="page-width footer-inner"><StoreBrand class="footer-brand"/><p>专业 · 可靠 · 用心的电脑硬件商城</p><span class="footer-note">每一件配件，都为你的下一台电脑。</span></div></footer>
  </div>
</template>
