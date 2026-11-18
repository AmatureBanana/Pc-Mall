<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { ElButton, ElPagination, ElTable, ElTableColumn } from 'element-plus'
import { AuthError } from './auth'
import { orderStates } from './cart'
import { formatPrice } from './catalog'
import { sortHardwareItems } from './diy'
import { completeOrder, orderDetail, orderPage, type AdminOrder, type AdminOrderPage } from './adminOrders'

const emit = defineEmits<{ expired: [] }>()
const orderNumber = ref(''), username = ref(''), status = ref('')
const page = ref<AdminOrderPage>({ items: [], total: 0, page: 1, pageSize: 10 })
const detail = ref<AdminOrder | null>(null)
const selected = ref<string | null>(null)
const loading = ref(false), busy = ref(false), confirming = ref(false), unauthorized = ref(false)
const error = ref(''), notice = ref('')
let revision = 0
const time = (value: string) => new Date(value).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai', hour12: false })
const sections = computed(() => detail.value ? [
  { id: null, title: '成交单品', amount: null, items: detail.value.order.items.filter(item => item.configurationId === null) },
  ...detail.value.order.configurations.map(group => ({ id: group.id, title: `成交配置组 #${group.id}`, amount: group.amount,
    items: sortHardwareItems(detail.value!.order.items.filter(item => item.configurationId === group.id)) })),
].filter(section => section.items.length) : [])

function feedback(cause: unknown) {
  error.value = cause instanceof Error ? cause.message : '订单服务暂时不可用，请重新载入。'
  if (cause instanceof AuthError && (cause.status === 401 || cause.status === 403)) {
    unauthorized.value = true; detail.value = null; page.value.items = []; confirming.value = false
    if (cause.status === 401) emit('expired')
  }
}
async function loadPage(number = 1) {
  if (busy.value) return
  const current = ++revision
  loading.value = true; error.value = ''; notice.value = ''; confirming.value = false
  selected.value = null; detail.value = null
  try {
    const result = await orderPage(new URLSearchParams({ orderNumber: orderNumber.value.trim(), username: username.value.trim(),
      status: status.value, page: String(number), pageSize: String(page.value.pageSize) }))
    if (current === revision) { page.value = result; unauthorized.value = false }
  } catch (cause) { if (current === revision) { page.value.items = []; feedback(cause) } }
  finally { if (current === revision) loading.value = false }
}
async function open(number: string) {
  if (busy.value) return
  const current = ++revision
  selected.value = number; detail.value = null; confirming.value = false
  loading.value = true; error.value = ''; notice.value = ''
  try {
    const result = await orderDetail(number)
    if (current === revision) { detail.value = result; unauthorized.value = false }
  } catch (cause) { if (current === revision) feedback(cause) }
  finally { if (current === revision) loading.value = false }
}
async function finish() {
  if (!detail.value || busy.value || loading.value || unauthorized.value) return
  const current = ++revision
  busy.value = true; error.value = ''; notice.value = ''
  try {
    const result = await completeOrder(detail.value.order.orderNumber)
    if (current === revision) { detail.value = result; confirming.value = false; notice.value = '交易已完成。' }
  } catch (cause) { if (current === revision) feedback(cause) }
  finally { if (current === revision) busy.value = false }
}
function reload() { selected.value ? void open(selected.value) : void loadPage(page.value.page) }
onMounted(() => void loadPage())
onUnmounted(() => { revision++ })
</script>

<template>
  <div class="catalog-admin order-admin" :aria-busy="loading || busy">
    <div class="admin-section-head"><div><h2>订单管理</h2><p>查询购买记录与成交快照，完成已支付交易</p></div>
      <ElButton v-if="selected" :disabled="busy" @click="loadPage(page.page)">返回订单列表</ElButton></div>
    <p v-if="error" role="alert" class="auth-error">{{ error }} <a v-if="unauthorized" href="#login">前往登录</a></p>
    <p v-if="notice" role="status" class="auth-success">{{ notice }}</p>
    <p v-if="loading" role="status">正在读取订单…</p>
    <ElButton :disabled="loading || busy" @click="reload">重新载入</ElButton>
    <template v-if="!selected">
      <form class="admin-filters" @submit.prevent="loadPage()">
        <label>订单号<input v-model="orderNumber" aria-label="查询订单号" maxlength="40" :disabled="loading"/></label>
        <label>购买用户名<input v-model="username" aria-label="查询购买用户名" maxlength="20" :disabled="loading"/></label>
        <label>订单状态<select v-model="status" aria-label="筛选订单状态" :disabled="loading"><option value="">全部状态</option><option v-for="(label, state) in orderStates" :key="state" :value="state">{{ label }}</option></select></label>
        <label>每页条数<select v-model.number="page.pageSize" aria-label="每页条数" :disabled="loading"><option :value="10">10</option><option :value="20">20</option><option :value="50">50</option></select></label>
        <ElButton native-type="submit" :disabled="loading">查询</ElButton>
      </form>
      <ElTable :data="page.items" :empty-text="error ? '订单数据加载失败，请重新载入' : '没有符合条件的订单'" stripe>
        <ElTableColumn prop="order.orderNumber" label="订单号" min-width="290"/>
        <ElTableColumn prop="buyer.username" label="购买人" min-width="140"/>
        <ElTableColumn label="成交金额" width="120"><template #default="scope">¥{{ formatPrice(scope.row.order.total) }}</template></ElTableColumn>
        <ElTableColumn label="状态" width="100"><template #default="scope">{{ orderStates[scope.row.order.status as keyof typeof orderStates] }}</template></ElTableColumn>
        <ElTableColumn label="操作" width="110"><template #default="scope"><ElButton link type="primary" :disabled="loading || unauthorized" @click="open(scope.row.order.orderNumber)">查看订单</ElButton></template></ElTableColumn>
      </ElTable>
      <ElPagination :current-page="page.page" :page-size="page.pageSize" :total="page.total" layout="total, prev, pager, next" :disabled="loading || unauthorized" @current-change="loadPage"/>
    </template>
    <template v-else-if="detail">
      <dl class="profile-identity">
        <div><dt>订单号</dt><dd>{{ detail.order.orderNumber }}</dd></div>
        <div><dt>购买人</dt><dd>{{ detail.buyer.username }} / {{ detail.buyer.nickname }}（ID {{ detail.buyer.id }}）</dd></div>
        <div><dt>状态</dt><dd>{{ orderStates[detail.order.status] }}</dd></div>
        <div><dt>下单时间</dt><dd>{{ time(detail.order.createdAt) }}</dd></div>
        <div v-if="detail.order.paidAt"><dt>支付时间</dt><dd>{{ time(detail.order.paidAt) }}</dd></div>
        <div v-if="detail.order.completedAt"><dt>完成时间</dt><dd>{{ time(detail.order.completedAt) }}</dd></div>
        <div v-if="detail.order.completedBy"><dt>完成操作者</dt><dd>{{ detail.order.completedBy }}</dd></div>
      </dl>
      <section v-for="section in sections" :key="section.id ?? 'singles'" :aria-label="section.title" class="order-group">
        <h3>{{ section.title }}</h3>
        <table><thead><tr><th>商品 / SKU</th><th>型号 / 类别</th><th>成交单价</th><th>数量</th><th>小计</th></tr></thead>
          <tbody><tr v-for="item in section.items" :key="item.productId"><td>{{ item.name }}<small>{{ item.sku }}</small></td>
            <td>{{ item.model }} / {{ item.category }}</td><td>¥{{ formatPrice(item.price) }}</td><td>{{ item.quantity }}</td><td>¥{{ formatPrice(item.amount) }}</td></tr></tbody></table>
        <p v-if="section.amount">组成交小计：¥{{ formatPrice(section.amount) }}</p>
      </section>
      <p>成交总额（CNY）：<strong aria-label="订单成交总额">¥{{ formatPrice(detail.order.total) }}</strong></p>
      <ElButton v-if="detail.order.status === 'PAID' && !confirming" type="primary" :disabled="loading || unauthorized" @click="confirming = true">完成交易</ElButton>
      <fieldset v-if="confirming" aria-label="确认完成交易" :disabled="busy || loading || unauthorized">
        <legend>确认完成交易</legend><p>确认将订单 {{ detail.order.orderNumber }} 标记为已完成？成交金额 ¥{{ formatPrice(detail.order.total) }}。</p>
        <p>完成后将保留操作记录。</p><ElButton type="primary" :loading="busy" @click="finish">确认完成</ElButton><ElButton :disabled="busy" @click="confirming = false">取消</ElButton>
      </fieldset>
    </template>
  </div>
</template>

<style scoped>
.order-group { margin-block: 20px; padding: 16px; border: 1px solid #e0e8f7; border-radius: 12px; }
table { width: 100%; border-collapse: collapse; text-align: left; }
th, td { padding: 10px; border-bottom: 1px solid #e0e8f7; }
small { display: block; color: #64748b; }
fieldset { margin-top: 20px; padding: 16px; border: 1px solid #e0e8f7; border-radius: 12px; }
</style>
