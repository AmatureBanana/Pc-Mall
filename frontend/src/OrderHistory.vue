<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElButton, ElEmpty, ElSkeleton } from 'element-plus'
import { signedIn } from './auth'
import { categoryName, formatPrice, imageFallback } from './catalog'
import { cancellationReasons, orderStates, useCart, type Order } from './cart'
import HardwareIcon from './HardwareIcon.vue'
import OrderActionDialog from './OrderActionDialog.vue'
import { hardwareDisplayOrder, sortHardwareItems } from './diy'

const { orderHistory, historyLoading, error, loadOrders, loadOrder } = useCart()
const page = ref(1)
const pageSize = ref(10)
const state = ref<Order['status'] | ''>('')
const keyword = ref('')
const startDate = ref('')
const endDate = ref('')
const action = ref<'pay' | 'cancel' | null>(null)
const actionNumber = ref('')
const expanded = ref(new Set<string>())
const summaryCategories = hardwareDisplayOrder.filter(code => ['CPU', 'GPU', 'MEMORY', 'SSD'].includes(code))
const tabs = [
  { state: '', label: '全部订单', key: 'ALL' },
  { state: 'PENDING_PAYMENT', label: '待支付', key: 'PENDING_PAYMENT' },
  { state: 'PAID', label: '已支付', key: 'PAID' },
  { state: 'COMPLETED', label: '已完成', key: 'COMPLETED' },
  { state: 'CANCELLED', label: '已取消', key: 'CANCELLED' },
] as const
const lastPage = computed(() => Math.max(1, Math.ceil((orderHistory.value?.total ?? 0) / pageSize.value)))
const pageButtons = computed(() => Array.from({ length: Math.min(lastPage.value, 5) }, (_, i) => Math.max(1, Math.min(page.value - 2, lastPage.value - 4)) + i))
const time = (value: string) => new Date(value).toLocaleString('zh-CN', { hour12: false, timeZone: 'Asia/Shanghai' })
const categoryImage = (category: string) => `/images/${category === 'CPU' ? 'intel-cpu' : category === 'MOTHERBOARD' ? 'motherboard' : category.toLowerCase()}.svg`
function refresh() {
  if (signedIn.value) void loadOrders(page.value, { pageSize: pageSize.value, status: state.value,
    keyword: keyword.value.trim(), startDate: startDate.value, endDate: endDate.value })
}
function applyFilters() { if (page.value === 1) refresh(); else page.value = 1 }
watch([signedIn, page], refresh, { immediate: true })
watch([state, pageSize], applyFilters)
function toggleGroup(key: string) {
  const next = new Set(expanded.value)
  if (next.has(key)) next.delete(key)
  else next.add(key)
  expanded.value = next
}
function groupItems(order: Order, id: number) { return sortHardwareItems(order.items.filter(item => item.configurationId === id)) }
function summaryValue(order: Order, id: number, category: string) {
  const item = groupItems(order, id).find(item => item.category === category)
  return item ? `${item.name}${item.quantity > 1 ? ` × ${item.quantity}` : ''}`
    : category === 'GPU' ? '核显（随 CPU 提供）' : '未记录'
}
async function openAction(order: Order, next: 'pay' | 'cancel') {
  actionNumber.value = order.orderNumber
  action.value = next
  await loadOrder(order.orderNumber)
}
</script>

<template>
  <main class="order-history" aria-label="我的订单" :aria-busy="historyLoading">
    <header class="account-section-title"><span><HardwareIcon name="orders"/></span><h1>订单管理</h1><p>查看与管理我的订单</p></header>
    <div v-if="!signedIn" class="orders-panel"><ElEmpty description="请先登录查看本人订单。"><a href="#login" class="login-link">前往登录</a></ElEmpty></div>
    <section v-else class="orders-panel" aria-label="订单管理">
      <div class="order-tabs" role="tablist" aria-label="订单状态">
        <button v-for="tab in tabs" :id="`order-tab-${tab.key}`" :key="tab.key" type="button" role="tab" :aria-selected="state === tab.state"
          :class="{ active: state === tab.state }" @click="state = tab.state">{{ tab.label }}<span>（{{ orderHistory?.counts[tab.key] ?? '–' }}）</span></button>
      </div>
      <form class="order-filters" aria-label="筛选订单" @submit.prevent="applyFilters">
        <div class="order-search"><button type="submit" aria-label="搜索订单"><HardwareIcon name="search"/></button><input v-model="keyword" aria-label="搜索订单号或商品名称" placeholder="搜索订单号或商品名称" maxlength="100" @change="applyFilters"/></div>
        <label class="order-state-select">订单状态：<select v-model="state" aria-label="订单状态筛选"><option value="">全部</option><option v-for="(label, code) in orderStates" :key="code" :value="code">{{ label }}</option></select></label>
        <div class="order-date-filter"><span>下单时间：</span><div class="date-range"><HardwareIcon name="calendar"/><label class="date-field"><span v-if="!startDate" class="date-placeholder">开始日期</span><input v-model="startDate" type="date" aria-label="开始日期" :class="{ empty: !startDate }" :max="endDate || undefined" @change="applyFilters"/></label><span>–</span><label class="date-field"><span v-if="!endDate" class="date-placeholder">结束日期</span><input v-model="endDate" type="date" aria-label="结束日期" :class="{ empty: !endDate }" :min="startDate || undefined" @change="applyFilters"/></label></div></div>
        <ElButton class="orders-refresh" :loading="historyLoading" aria-label="刷新订单" @click="refresh"><HardwareIcon name="reset"/>刷新</ElButton>
      </form>
      <p v-if="error && !action" role="alert" class="auth-error orders-error">{{ error }}</p>
      <div v-if="historyLoading" class="order-loading" role="status"><ElSkeleton :rows="6" animated/></div>
      <div v-else-if="orderHistory" class="order-list" role="tabpanel" :aria-labelledby="`order-tab-${state || 'ALL'}`">
        <ElEmpty v-if="!orderHistory.items.length" :description="keyword || state || startDate || endDate ? '没有符合筛选条件的订单' : '暂无订单'"/>
        <article v-for="order in orderHistory.items" :key="order.orderNumber" class="order-card" :aria-label="`订单 ${order.orderNumber}`">
          <header class="order-card-header"><HardwareIcon name="orders"/><span class="order-number">订单号：<a :href="`#order/${order.orderNumber}`">{{ order.orderNumber }}</a></span><span class="order-created">下单时间：{{ time(order.createdAt) }}</span>
            <span class="order-status" :class="order.status.toLowerCase()"><HardwareIcon :name="order.status === 'PENDING_PAYMENT' ? 'clock' : 'check'"/>{{ orderStates[order.status] }}</span>
          </header>
          <div class="order-card-body">
            <div class="order-lines">
              <div v-for="item in order.items.filter(item => item.configurationId === null)" :key="item.productId" class="order-line">
                <img :src="categoryImage(item.category)" :alt="`${categoryName(item.category)}品类示意图`" @error="imageFallback"/>
                <div class="order-line-info"><h2>{{ item.name }}</h2><p><span class="order-category">{{ categoryName(item.category) }}</span><span>{{ item.model }}</span></p></div>
                <div class="order-line-price">¥{{ formatPrice(item.price) }} <span>× {{ item.quantity }}</span></div>
              </div>
              <section v-for="(group, index) in order.configurations" :key="group.id" class="order-configuration" :aria-label="`成交配置组 ${index + 1}`">
                <div class="order-line"><img src="/images/case.svg" alt="DIY配置组品类示意图"/>
                  <div class="order-line-info"><button type="button" class="order-group-toggle" :aria-expanded="expanded.has(`${order.orderNumber}-${group.id}`)" @click="toggleGroup(`${order.orderNumber}-${group.id}`)">
                    <strong>配置组：DIY 装机方案 {{ index + 1 }}（共 {{ groupItems(order, group.id).reduce((total, item) => total + item.quantity, 0) }} 件）</strong><HardwareIcon name="chevron-down"/>
                  </button><dl v-if="!expanded.has(`${order.orderNumber}-${group.id}`)" class="order-group-summary" aria-label="配置组关键参数">
                    <div v-for="category in summaryCategories" :key="category"><dt>{{ categoryName(category) }}</dt><dd>{{ summaryValue(order, group.id, category) }}</dd></div>
                  </dl></div>
                </div>
                <ul v-if="expanded.has(`${order.orderNumber}-${group.id}`)" class="order-group-parts" aria-label="配置组成交明细"><li v-for="item in groupItems(order, group.id)" :key="item.productId"><span>{{ categoryName(item.category) }} · {{ item.name }}</span><span>¥{{ formatPrice(item.price) }} × {{ item.quantity }}</span></li><li><span>配置组成交小计</span><strong>¥{{ formatPrice(group.amount) }}</strong></li></ul>
              </section>
            </div>
            <div class="order-outcome"><div class="order-amount"><span>{{ order.status === 'PENDING_PAYMENT' ? '应付金额' : order.status === 'CANCELLED' ? '订单金额' : '实付金额' }}：</span><strong>¥{{ formatPrice(order.total) }}</strong></div>
              <div class="order-actions"><template v-if="order.status === 'PENDING_PAYMENT'"><ElButton type="primary" @click="openAction(order, 'pay')">立即支付</ElButton><ElButton @click="openAction(order, 'cancel')">取消订单</ElButton></template>
                <a v-else :href="`#order/${order.orderNumber}`" class="order-detail-link">查看详情</a>
              </div>
            </div>
          </div>
          <p v-if="order.cancellationReason" class="order-cancellation">{{ cancellationReasons[order.cancellationReason] }}<span v-if="order.cancelledAt"> · {{ time(order.cancelledAt) }}</span></p>
        </article>
      </div>
      <nav v-if="orderHistory" class="order-pagination" aria-label="订单分页"><span>共 {{ orderHistory.total }} 条</span>
        <button type="button" aria-label="上一页" :disabled="page <= 1 || historyLoading" @click="page--"><HardwareIcon name="chevron-right"/></button>
        <button v-for="number in pageButtons" :key="number" type="button" :class="{ active: page === number }" :aria-label="`第 ${number} 页`" :aria-current="page === number ? 'page' : undefined" :disabled="historyLoading" @click="page = number">{{ number }}</button>
        <button type="button" aria-label="下一页" :disabled="page >= lastPage || historyLoading" @click="page++"><HardwareIcon name="chevron-right"/></button>
        <select v-model.number="pageSize" aria-label="每页订单数量"><option :value="10">10 条/页</option><option :value="20">20 条/页</option><option :value="50">50 条/页</option></select>
      </nav>
    </section>
    <OrderActionDialog :number="actionNumber" :action="action" @close="action = null" @done="refresh"/>
  </main>
</template>

<style scoped>
.orders-panel { border: 1px solid #eaf0fb; border-radius: 15px; background: #fff; overflow: hidden; min-height: 400px; box-shadow: 0 2px 15px #3867ac04; }
.order-tabs { display: flex; align-items: center; gap: 9px; padding: 9px 3px; border-bottom: 1px solid #edf2fa; overflow-x: auto; }
.order-tabs button { height: 39px; padding: 0 22px; border: 1px solid #dce4f2; border-radius: 7px; background: #fff; color: #151d30; font-size: 14px; font-weight: 650; white-space: nowrap; box-shadow: 0 1px 3px #28487905; }
.order-tabs button.active { color: white; border-color: #0867ff; background: #0867ff; box-shadow: 0 3px 9px #0867ff18; }
.order-tabs button span { font-weight: 500; }
.order-filters { display: flex; flex-wrap: wrap; align-items: center; gap: 16px; padding: 20px 16px; border-bottom: 1px solid #edf2fa; font-size: 13px; }
.order-search { display: flex; align-items: center; width: 290px; height: 38px; border: 1px solid #dae3f1; border-radius: 7px; background: #fff; }
.order-search button { border: 0; background: transparent; padding: 8px 10px; color: #677b9e; display: flex; }
.order-search svg { width: 18px; height: 18px; }
.order-search input { width: 100%; min-width: 0; border: 0; background: none; color: #1d3150; outline: 0; font-size: 13px; }
.order-search input::placeholder { color: #7c8aa4; }
.order-search:focus-within { border-color: #6c9dff; box-shadow: 0 0 0 2px #0968fa0a; }
.order-state-select { display: flex; align-items: center; gap: 7px; white-space: nowrap; }
select { border: 1px solid #dce4f2; border-radius: 7px; padding: 8px 12px; background: white; color: #192640; font-size: 13px; }
.order-state-select select { min-width: 112px; height: 38px; }
.order-date-filter { display: flex; align-items: center; gap: 7px; white-space: nowrap; }
.date-range { display: flex; align-items: center; gap: 9px; border: 1px solid #dce4f2; border-radius: 7px; padding: 7px 10px; height: 38px; color: #7b8caa; }
.date-range > svg { width: 17px; height: 17px; }
.date-range input { border: 0; background: none; font-size: 12px; color: #677b9e; width: 112px; min-width: 0; }
.date-range input::-webkit-calendar-picker-indicator { opacity: .45; cursor: pointer; }
.date-field { position: relative; display: flex; min-width: 0; }
.date-placeholder { position: absolute; left: 3px; top: 2px; font-size: 12px; color: #7b8caa; pointer-events: none; }
.date-field input.empty:not(:focus)::-webkit-datetime-edit { color: transparent; }
.date-field:focus-within .date-placeholder { display: none; }
.orders-refresh { margin-left: auto; height: 38px; color: var(--blue); border-color: #8aafff; font-weight: 600; padding: 0 13px; }
.orders-refresh svg { width: 19px; height: 19px; margin-right: 7px; }
.orders-error { margin: 16px; }
.order-list { padding: 12px 16px 0; }
.order-card { margin-bottom: 17px; border: 1px solid #e4ecfa; border-radius: 12px; overflow: hidden; background: #fff; box-shadow: 0 2px 4px #365fa706; }
.order-card-header { display: flex; align-items: center; gap: 12px; padding: 10px 13px; background: linear-gradient(90deg,#f3f7ff,#f9fbff); min-height: 46px; font-size: 12px; }
.order-card-header > svg { width: 18px; height: 18px; color: #0968fa; flex: none; }
.order-number { color: #111b31; font-weight: 600; overflow-wrap: anywhere; }
.order-number a:hover { color: var(--blue); }
.order-created { margin-left: 14px; color: #344564; }
.order-status { margin-left: auto; display: inline-flex; align-items: center; gap: 7px; padding: 4px 10px; border-radius: 20px; flex: none; font-size: 13px; font-weight: 600; }
.order-status svg { width: 17px; height: 17px; }
.order-status.pending_payment { background: #fff0f2; color: #f02848; }
.order-status.paid { background: #eaf9ed; color: #159745; }
.order-status.completed { background: #eaf1ff; color: #0867ff; }
.order-status.cancelled { background: #edf0f5; color: #7b879d; }
.order-card-body { display: grid; grid-template-columns: minmax(0,1fr) 330px; align-items: stretch; padding: 13px 16px; gap: 18px; }
.order-lines { min-width: 0; }
.order-line { display: flex; align-items: center; gap: 16px; min-height: 68px; padding: 6px 0; }
.order-line > img { width: 111px; height: 66px; object-fit: contain; flex: none; }
.order-line-info { flex: 1; min-width: 0; }
.order-line h2 { color: #121c30; font-size: 16px; line-height: 1.45; font-weight: 650; margin: 0 0 7px; overflow-wrap: anywhere; }
.order-line-info > p { margin: 0; font-size: 12px; line-height: 1.8; color: #4a5b79; display: flex; gap: 15px; flex-wrap: wrap; }
.order-category { border-radius: 4px; background: #eff4ff; padding: 1px 7px; color: #344365; }
.order-line-price { font-weight: 650; color: #101b32; font-size: 14px; flex: none; }
.order-line-price span { margin-left: 7px; font-weight: 400; }
.order-outcome { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 13px; border-left: 1px solid #edf1fa; padding: 10px 0 10px 24px; }
.order-amount { white-space: nowrap; display: flex; align-items: center; gap: 3px; }
.order-amount > span { font-size: 12px; color: #53627f; }
.order-amount strong { color: #fa252f; font-size: 21px; letter-spacing: -.4px; }
.order-actions { display: flex; flex-wrap: wrap; align-items: center; gap: 9px; }
.order-actions .el-button { margin: 0; height: 37px; padding: 0 15px; border-radius: 7px; font-weight: 600; }
.order-detail-link { border: 1px solid #8aafff; color: #0968fa; font-size: 13px; font-weight: 600; border-radius: 6px; padding: 9px 16px; }
.order-detail-link:hover { background: #f1f6ff; }
.order-group-toggle { display: flex; align-items: center; justify-content: space-between; gap: 12px; width: 100%; border: 0; padding: 0; margin-bottom: 5px; background: transparent; text-align: left; color: #121c30; font-size: 15px; }
.order-group-toggle svg { width: 17px; height: 17px; flex: none; transition: transform .15s; }
.order-group-toggle[aria-expanded=true] svg { transform: rotate(180deg); }
.order-group-summary { display: grid; grid-template-columns: repeat(2, minmax(0,1fr)); gap: 6px 16px; margin: 8px 0 0; font-size: 12px; line-height: 1.7; color: #384a6b; }
.order-group-summary > div { display: flex; align-items: baseline; gap: 7px; min-width: 0; }
.order-group-summary dt { color: #7d8da7; flex: none; }
.order-group-summary dd { margin: 0; min-width: 0; overflow-wrap: anywhere; }
.order-group-parts { list-style: none; margin: 8px 0 0; padding: 12px; border-radius: 7px; background: #f6f9ff; font-size: 12px; }
.order-group-parts li { display: flex; justify-content: space-between; gap: 12px; padding: 5px; line-height: 1.6; }
.order-group-parts li > span:first-child { flex: 1; }
.order-group-parts li > span:last-child { flex: none; }
.order-cancellation { margin: 0; padding: 9px 16px; color: #8290a8; background: #fafbfd; font-size: 11px; border-top: 1px solid #eef2f8; }
.order-pagination { display: flex; align-items: center; justify-content: flex-end; flex-wrap: wrap; gap: 7px; padding: 15px 16px; border-top: 1px solid #edf2fa; font-size: 13px; }
.order-pagination > span { margin-right: 10px; }
.order-pagination > button { display: flex; align-items: center; justify-content: center; min-width: 35px; height: 36px; border: 1px solid #e2e9f5; border-radius: 6px; background: white; color: #24324d; padding: 0 9px; }
.order-pagination > button.active { color: white; background: #0968fa; border-color: #0968fa; }
.order-pagination > button:disabled { opacity: .35; cursor: default; }
.order-pagination > button[aria-label=上一页] svg { transform: rotate(180deg); }
.order-pagination svg { width: 17px; height: 17px; }
.order-pagination select { margin-left: 8px; height: 36px; }
.order-loading { padding: 35px 24px; }
.login-link { color: var(--blue); }
@media (max-width: 700px) { .order-group-summary { grid-template-columns: minmax(0,1fr); gap: 4px; } }
@media (min-width: 1450px) { .order-card-body { grid-template-columns: minmax(0,1fr) 435px; } }
@media (max-width: 1250px) { .order-card-body { grid-template-columns: minmax(0,1fr); gap: 9px; } .order-outcome { border-left: 0; border-top: 1px solid #edf1fa; padding: 14px 0 0; } .order-created { margin-left: 0; } .order-card-header { flex-wrap: wrap; gap: 8px; } .order-number { max-width: calc(100% - 110px); } .order-created { order: 4; flex-basis: 100%; padding-left: 26px; } }
@media (max-width: 700px) { .order-tabs { padding: 8px; gap: 7px; } .order-tabs button { padding: 0 13px; height: 36px; font-size: 12px; } .order-filters { padding: 13px 10px; gap: 11px; } .order-search { width: 100%; } .order-state-select { flex: 1; } .order-state-select select { flex: 1; } .order-date-filter { order: 4; width: 100%; flex-wrap: wrap; } .date-range { flex: 1; } .date-range input { width: 100px; flex: 1; } .orders-refresh { margin-left: 0; } .order-list { padding-inline: 9px; } .order-card-body { padding: 10px; } .order-line { gap: 10px; flex-wrap: wrap; } .order-line > img { width: 65px; height: 58px; } .order-line h2 { font-size: 14px; } .order-line-price { padding-left: 75px; width: 100%; font-size: 13px; } .order-line-info > p { gap: 5px 10px; } .order-outcome { gap: 10px; } .order-amount strong { font-size: 19px; } .order-group-toggle { font-size: 13px; } .order-pagination { justify-content: center; gap: 5px; padding-inline: 8px; } .order-pagination > span { flex-basis: 100%; text-align: center; margin: 0 0 5px; } .order-group-parts li { flex-wrap: wrap; } }
</style>
