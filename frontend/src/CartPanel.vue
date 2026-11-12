<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { ElButton, ElDialog, ElEmpty, ElMessageBox, ElSkeleton } from 'element-plus'
import { categories, formatPrice, imageFallback } from './catalog'
import HardwareIcon from './HardwareIcon.vue'
import { cartQuantity, useCart, type CartItem, type CheckoutPreview } from './cart'

const emit = defineEmits<{ close: [] }>()
const { cart, loading, error, notice, refresh, update, remove, removeConfiguration, startEdit, checkoutPreview,
  createOrder, orderAttempt, orderBusy } = useCart()
const busy = ref(new Set<number>())
const removingConfiguration = ref<number | null>(null)
const pending = ref<Record<number, string>>({})
const selected = ref(new Set<string>())
const collapsed = ref(new Set<number>())
const bulkRemoving = ref(false)
const quote = ref<CheckoutPreview | null>(null)
const quoteOpen = ref(false)
const quoteBusy = ref(false)
const priceChanges = ref<string[]>([])
let quoteAttempt = 0
const itemKey = (id: number) => `item:${id}`
const groupKey = (id: number) => `group:${id}`

// Only unsent edits are drafts. Saved quantities always come from the latest accepted snapshot.
watch(cart, (value, previous) => {
  const ids = new Set(value?.items.map(item => item.id) ?? [])
  pending.value = Object.fromEntries(Object.entries(pending.value).filter(([id]) => ids.has(Number(id))))
  const before = new Set([
    ...(previous?.items ?? []).map(item => itemKey(item.id)),
    ...(previous?.configurations ?? []).map(group => groupKey(group.id)),
  ])
  const next = [
    ...(value?.items ?? []).map(item => ({ key: itemKey(item.id), valid: item.state === 'VALID' })),
    ...(value?.configurations ?? []).map(group => ({ key: groupKey(group.id), valid: group.valid })),
  ]
  selected.value = new Set(next.filter(row => before.has(row.key) ? selected.value.has(row.key) : row.valid)
    .map(row => row.key))
  const groupIds = new Set(value?.configurations.map(group => group.id))
  collapsed.value = new Set([...collapsed.value].filter(id => groupIds.has(id)))
}, { flush: 'sync', immediate: true })

function open() { void refresh() }
onMounted(() => {
  notice.value = ''
  open()
  window.addEventListener('focus', open)
})
onUnmounted(() => window.removeEventListener('focus', open))

const items = computed<CartItem[]>(() => cart.value?.items ?? [])
const groups = computed(() => cart.value?.configurations ?? [])
const itemKeys = computed(() => items.value.map(item => itemKey(item.id)))
const allKeys = computed(() => [...itemKeys.value, ...groups.value.map(group => groupKey(group.id))])
const selectedItems = computed(() => items.value.filter(item => selected.value.has(itemKey(item.id)) && item.state === 'VALID'))
const selectedGroups = computed(() => groups.value.filter(group => selected.value.has(groupKey(group.id)) && group.valid))
const invalidSelected = computed(() => selected.value.size - selectedItems.value.length - selectedGroups.value.length)
const subtotal = (rows: { amount: string }[]) => rows.reduce((sum, row) => sum + BigInt(row.amount.replace('.', '')), 0n)
const itemTotal = computed(() => subtotal(selectedItems.value))
const groupTotal = computed(() => subtotal(selectedGroups.value))
const money = (cents: bigint) => '¥' + formatPrice(`${cents / 100n}.${String(cents % 100n).padStart(2, '0')}`)
const icon = (item: CartItem) => categories.find(category => category.code === item.category)?.icon ?? 'grid'
const groupImage = (members: CartItem[]) => members.find(item => item.category === 'CASE')?.imageUrl ?? members[0]?.imageUrl
const operationBusy = computed(() => bulkRemoving.value || busy.value.size > 0 || removingConfiguration.value !== null)

function allSelected(keys: string[]) { return keys.length > 0 && keys.every(key => selected.value.has(key)) }
function someSelected(keys: string[]) { return !allSelected(keys) && keys.some(key => selected.value.has(key)) }
function choose(keys: string[], event: Event) {
  const checked = (event.target as HTMLInputElement).checked
  keys.forEach(key => checked ? selected.value.add(key) : selected.value.delete(key))
}
watch([cart, () => [...selected.value].sort().join(',')], () => {
  quoteAttempt++
  quote.value = null
  quoteOpen.value = false
  quoteBusy.value = false
})
onUnmounted(() => { quoteAttempt++ })

async function checkQuote(confirm = false) {
  if (quoteBusy.value || !cart.value) return
  const attempt = ++quoteAttempt
  const previous = confirm ? quote.value : null
  const before = previous ?? cart.value
  const seen = new Map([...before.items, ...before.configurations.flatMap(group => group.items)].map(item => [item.productId, item.price]))
  if (quote.value) quote.value = { ...quote.value, status: 'CONFIRMATION_REQUIRED' }
  quoteOpen.value = true
  quoteBusy.value = true
  try {
    const result = await checkoutPreview(items.value.filter(item => selected.value.has(itemKey(item.id))).map(item => item.id),
      groups.value.filter(group => selected.value.has(groupKey(group.id))).map(group => group.id), previous?.quoteToken)
    if (attempt !== quoteAttempt || !quoteOpen.value) return
    if (!result) { quote.value = null; return }
    priceChanges.value = [...new Map([...result.items, ...result.configurations.flatMap(group => group.items)]
      .filter(item => seen.has(item.productId) && seen.get(item.productId) !== item.price)
      .map(item => [item.productId, `${item.name}：¥${seen.get(item.productId)} → ¥${item.price}`])).values()]
    quote.value = result
  } finally { if (attempt === quoteAttempt) quoteBusy.value = false }
}
async function submitOrder() {
  if (quoteBusy.value || orderBusy.value || !quote.value) return
  const current = quote.value
  if (!await createOrder(current) && quote.value === current) quote.value = { ...current, status: 'CONFIRMATION_REQUIRED' }
}
function closeQuote() { quoteAttempt++; quoteOpen.value = false; quote.value = null; quoteBusy.value = false }
function toggleGroup(id: number) {
  if (collapsed.value.has(id)) collapsed.value.delete(id)
  else collapsed.value.add(id)
}

function draft(item: CartItem) { return pending.value[item.id] ?? String(item.quantity) }

/**
 * Only a short-stocked line can still be repaired by lowering its quantity. An off-sale or
 * out-of-stock line has no sellable quantity to lower to, so the server refuses it and the page
 * disables the field instead of inviting a doomed request.
 */
function editable(item: CartItem) {
  return item.state === 'VALID' || item.state === 'INSUFFICIENT_STOCK'
}

/** The field is uncontrolled between edits, so a rejected value can be restored from the server line. */
function change(item: CartItem, event: Event) {
  pending.value = { ...pending.value, [item.id]: (event.target as HTMLInputElement).value }
}
/** Puts the field back to the quantity the server is actually holding for this line. */
function restore(item: CartItem) {
  delete pending.value[item.id]
}
async function apply(item: CartItem, draftValue = draft(item).trim()) {
  if (bulkRemoving.value || busy.value.has(item.id)) return
  if (!/^\d+$/.test(draftValue) || Number(draftValue) < cartQuantity.min || Number(draftValue) > cartQuantity.max) {
    error.value = `数量须为 ${cartQuantity.min}～${cartQuantity.max} 的整数。`
    // A locally refused value must not stay on screen as if the server had accepted it.
    restore(item)
    return
  }
  busy.value.add(item.id)
  try { await update(item.id, Number(draftValue)) }
  finally { restore(item); busy.value.delete(item.id) }
}
async function drop(item: CartItem) {
  if (bulkRemoving.value || busy.value.has(item.id)) return
  busy.value.add(item.id)
  try { await remove(item.id) }
  finally { busy.value.delete(item.id) }
}
async function dropConfiguration(id: number) {
  if (bulkRemoving.value || removingConfiguration.value !== null) return
  removingConfiguration.value = id
  try { await removeConfiguration(id) }
  finally { removingConfiguration.value = null }
}
async function removeRows(all: boolean) {
  if (operationBusy.value) return
  const singles = items.value.filter(item => all || selected.value.has(itemKey(item.id)))
  const configurations = groups.value.filter(group => all || selected.value.has(groupKey(group.id)))
  if (all) {
    try {
      await ElMessageBox.confirm('确定移除购物车内的所有单品和配置组？', '清空购物车', {
        confirmButtonText: '清空', cancelButtonText: '取消', type: 'warning',
      })
    } catch { return }
    if (operationBusy.value || ![...singles.map(item => itemKey(item.id)), ...configurations.map(group => groupKey(group.id))]
      .every(key => allKeys.value.includes(key))) return
  }
  bulkRemoving.value = true
  try {
    for (const item of singles) if (!await remove(item.id)) return
    for (const group of configurations) if (!await removeConfiguration(group.id)) return
  } finally { bulkRemoving.value = false }
}
</script>

<template>
  <main class="cart-page page-width" aria-label="我的购物车">
    <div class="cart-heading">
      <HardwareIcon name="cart"/><h1>购物车</h1><p>管理单品和整套装机方案</p>
      <button type="button" class="cart-text-button cart-back" @click="emit('close')">继续购物</button>
    </div>
    <p v-if="error" role="alert" class="auth-error">{{ error }}</p>
    <p v-else-if="notice" role="status" class="auth-success">{{ notice }}</p>
    <ElButton v-if="orderAttempt?.uncertain" :loading="orderBusy" @click="createOrder()">重试确认上次下单</ElButton>
    <div class="cart-layout">
      <section class="cart-content" aria-label="购物车明细" :aria-busy="loading">
        <div class="cart-toolbar">
          <label class="cart-select"><input type="checkbox" :checked="allSelected(allKeys)"
            :indeterminate="someSelected(allKeys)" :disabled="!allKeys.length || bulkRemoving"
            @change="choose(allKeys, $event)"/>全选 <span>（已选 {{ selected.size }} 项）</span></label>
          <div class="cart-tools">
            <button type="button" class="cart-text-button" :disabled="!selected.size || operationBusy" @click="removeRows(false)">
              <HardwareIcon name="trash"/>删除选中</button>
            <button type="button" class="cart-text-button" :disabled="!allKeys.length || operationBusy" @click="removeRows(true)">
              <HardwareIcon name="trash"/>清空购物车</button>
          </div>
        </div>
        <div v-if="loading" aria-label="正在加载购物车"><ElSkeleton animated :rows="5"/></div>
        <template v-else-if="cart">
          <ElEmpty v-if="!allKeys.length" description="购物车还是空的，去商品详情或 DIY 工作区选购吧。">
            <ElButton type="primary" @click="emit('close')">继续浏览商品</ElButton>
          </ElEmpty>
          <section v-if="items.length" class="cart-section" aria-label="单品商品">
            <div class="cart-section-title">
              <label class="cart-select"><input type="checkbox" :checked="allSelected(itemKeys)"
                :indeterminate="someSelected(itemKeys)" :disabled="bulkRemoving" @change="choose(itemKeys, $event)"/>
                单品商品 <span>（共 {{ items.length }} 项）</span></label>
            </div>
            <table class="cart-table">
              <caption class="sr-only">单品明细</caption>
              <thead class="sr-only"><tr><th scope="col">选择</th><th scope="col">商品</th><th scope="col">库存</th><th scope="col">单价</th><th scope="col">数量</th><th scope="col">小计</th><th scope="col">操作</th></tr></thead>
              <tbody>
                <tr v-for="item in items" :key="item.id">
                  <td class="cart-row-select"><input type="checkbox" :aria-label="'选中 ' + item.name"
                    :checked="selected.has(itemKey(item.id))" :disabled="bulkRemoving" @change="choose([itemKey(item.id)], $event)"/></td>
                  <td><div class="cart-product">
                    <img :src="item.imageUrl" alt="" @error="imageFallback"/>
                    <div><a :href="'#product/' + item.productId" class="cart-name">{{ item.name }}</a>
                      <small class="cart-meta"><span>{{ item.categoryLabel }}</span> {{ item.brand }} · {{ item.model }}</small>
                      <small v-if="item.state !== 'VALID'" class="cart-problem">{{ item.message }}</small>
                    </div>
                  </div></td>
                  <td class="cart-stock">可售 {{ item.stock }} 件</td>
                  <td class="cart-price">¥{{ formatPrice(item.price) }}</td>
                  <td><div class="cart-quantity">
                    <div class="cart-stepper">
                      <button type="button" :aria-label="'减少 ' + item.name + ' 数量'"
                        :disabled="bulkRemoving || busy.has(item.id) || !editable(item) || item.quantity <= cartQuantity.min"
                        @click="apply(item, String(item.quantity - 1))">−</button>
                      <input :value="draft(item)" :aria-label="item.name + ' 数量'" inputmode="numeric"
                        :maxlength="String(cartQuantity.max).length" :disabled="bulkRemoving || busy.has(item.id) || !editable(item)"
                        @input="change(item, $event)" @keydown.enter="apply(item)"/>
                      <button type="button" :aria-label="'增加 ' + item.name + ' 数量'"
                        :disabled="bulkRemoving || busy.has(item.id) || item.state !== 'VALID' || item.quantity >= Math.min(cartQuantity.max, item.stock)"
                        @click="apply(item, String(item.quantity + 1))">+</button>
                    </div>
                    <button type="button" class="cart-text-button" :disabled="bulkRemoving || busy.has(item.id) || !editable(item)" @click="apply(item)">更新</button>
                  </div></td>
                  <td class="cart-price">¥{{ formatPrice(item.amount) }}</td>
                  <td><button type="button" class="cart-text-button" :disabled="bulkRemoving || busy.has(item.id)"
                    :aria-label="'移除 ' + item.name" @click="drop(item)"><HardwareIcon name="trash"/></button></td>
                </tr>
              </tbody>
            </table>
          </section>
          <section v-for="(group, index) in groups" :key="group.id" class="cart-section"
            :aria-label="'装机方案 ' + (index + 1)">
            <div class="cart-section-title cart-group-title">
              <label class="cart-select"><input type="checkbox" :aria-label="'选中装机方案 ' + (index + 1)"
                :checked="selected.has(groupKey(group.id))" :disabled="bulkRemoving" @change="choose([groupKey(group.id)], $event)"/>
                <HardwareIcon name="layers"/>配置组：装机方案 {{ index + 1 }} <span>（共 {{ group.items.reduce((sum, item) => sum + item.quantity, 0) }} 件）</span>
              </label>
              <small class="cart-source">来源：DIY 装机</small>
              <span class="cart-group-subtotal">组小计：<b class="cart-price">¥{{ formatPrice(group.amount) }}</b></span>
              <button type="button" class="cart-text-button cart-group-toggle" :aria-expanded="!collapsed.has(group.id)"
                :aria-label="(collapsed.has(group.id) ? '展开' : '收起') + '装机方案 ' + (index + 1)" @click="toggleGroup(group.id)">
                <HardwareIcon name="chevron-right"/>
              </button>
            </div>
            <div v-show="!collapsed.has(group.id)" class="cart-group-body">
              <img v-if="groupImage(group.items)" :src="groupImage(group.items)" alt="" @error="imageFallback"/>
              <ul class="cart-parts"><li v-for="item in group.items" :key="item.productId">
                <HardwareIcon :name="icon(item)"/><div><b>{{ item.categoryLabel }}：</b>{{ item.name }}
                  <small class="cart-meta cart-sku">{{ item.sku }} · 单价 ¥{{ formatPrice(item.price) }}</small>
                  <small v-if="item.state !== 'VALID'" class="cart-problem">{{ item.message }}</small>
                </div><span>×{{ item.quantity }}</span>
              </li></ul>
              <div><ElButton size="small" :disabled="operationBusy" @click="startEdit(group)">返回 DIY 编辑</ElButton>
              <ElButton size="small" :disabled="bulkRemoving || removingConfiguration !== null" @click="dropConfiguration(group.id)">
                <HardwareIcon name="trash"/>移除整套
              </ElButton></div>
            </div>
            <ul v-if="group.reasons?.length" class="cart-problem"><li v-for="reason in group.reasons" :key="reason">{{ reason }}</li></ul>
          </section>
          <p v-if="groups.length" class="cart-hint"><HardwareIcon name="alert"/>配置组是一套完整装机方案，组内配件不能在购物车中单独修改或删除。</p>
        </template>
      </section>
      <aside class="cart-summary" aria-label="结算信息">
        <h2><HardwareIcon name="card"/>结算信息</h2>
        <dl>
          <div><dt>单品商品（{{ selectedItems.length }} 项）</dt><dd>{{ money(itemTotal) }}</dd></div>
          <div><dt>配置组（{{ selectedGroups.length }} 套）</dt><dd>{{ money(groupTotal) }}</dd></div>
          <div class="cart-summary-total"><dt>选中参考合计</dt><dd aria-label="选中参考合计">{{ money(itemTotal + groupTotal) }}</dd></div>
        </dl>
        <p v-if="invalidSelected" class="cart-problem">{{ invalidSelected }} 项当前不可售，未计入合计。</p>
        <ElButton class="cart-checkout" type="primary" :disabled="!selected.size || loading || operationBusy || quoteBusy" @click="checkQuote()"><HardwareIcon name="card"/>预结算</ElButton>
        <p class="cart-summary-note">按选中项重新核验库存和整组兼容性。参考金额不锁价、不预占库存。</p>
        <button type="button" class="cart-text-button" :disabled="loading || operationBusy" @click="refresh">刷新价格与库存</button>
      </aside>
    </div>
    <ElDialog v-model="quoteOpen" title="确认当前报价" width="min(720px, calc(100vw - 32px))" align-center
      :close-on-click-modal="false" @close="closeQuote">
      <p v-if="error" role="alert" class="auth-error">{{ error }}</p>
      <p v-if="quoteBusy" role="status">正在重新核验选中项…</p>
      <template v-if="quote">
        <p v-if="quote.status === 'RECONFIRMATION_REQUIRED'" role="alert" class="auth-error">报价或商品信息发生变化，请核对最新明细并再次确认。</p>
        <ul v-if="priceChanges.length" class="cart-problem" aria-label="价格变化"><li v-for="change in priceChanges" :key="change">{{ change }}</li></ul>
        <section aria-label="服务端选中明细">
          <p v-for="item in quote.items" :key="item.id">{{ item.name }} · {{ item.sku }} ×{{ item.quantity }} · ¥{{ formatPrice(item.price) }} · 小计 ¥{{ formatPrice(item.amount) }}</p>
          <div v-for="group in quote.configurations" :key="group.id"><h3>完整配置组 #{{ group.id }} · ¥{{ formatPrice(group.amount) }}</h3>
            <p v-for="item in group.items" :key="item.productId">{{ item.categoryLabel }}：{{ item.name }} · {{ item.sku }} ×{{ item.quantity }} · ¥{{ formatPrice(item.price) }}</p>
          </div>
        </section>
        <p>服务端选中总额：<strong aria-label="服务端选中总额" class="cart-price">¥{{ formatPrice(quote.total) }}</strong></p>
        <p v-if="quote.status === 'READY'" role="status" class="auth-success">预检查通过，当前报价已确认。创建订单时仍须重新核验。</p>
      </template>
      <p class="cart-summary-note">重新核验报价不预占库存；点击“创建订单”后才会下单并预占。确认报价不锁价，价格再次变化须重新确认。</p>
      <p v-if="quote?.configurations.length" class="cart-problem">配置组下单尚未开放，请返回购物车仅选择单品。</p>
      <template #footer><ElButton @click="closeQuote">返回购物车</ElButton><ElButton type="primary" :loading="quoteBusy" :disabled="!quote || quoteBusy || orderBusy" @click="checkQuote(true)">确认并重新核验报价</ElButton>
        <ElButton type="primary" :loading="orderBusy" :disabled="!quote || quote.status !== 'READY' || !!quote.configurations.length || quoteBusy || orderBusy || !!orderAttempt" @click="submitOrder">创建订单</ElButton>
        <ElButton v-if="orderAttempt?.uncertain" :loading="orderBusy" @click="createOrder()">重试确认上次下单</ElButton>
      </template>
    </ElDialog>
  </main>
</template>

<style scoped>
.cart-page { padding-block: 24px 40px; color: #16233d; }
.cart-heading { display: flex; align-items: center; flex-wrap: wrap; gap: 12px; margin-bottom: 20px; }
.cart-heading > svg { width: 36px; height: 36px; color: var(--blue); }
.cart-heading h1 { margin: 0; font-size: 26px; }
.cart-heading p { margin: 0; padding-left: 14px; border-left: 1px solid #dbe4f5; color: #687895; font-size: 14px; }
.cart-back { margin-left: auto; }
.cart-layout { display: grid; grid-template-columns: minmax(0, 1fr) minmax(250px, 300px); gap: 18px; align-items: start; }
.cart-content, .cart-summary { border: 1px solid #e0e8f7; border-radius: 15px; background: #fff; box-shadow: 0 3px 12px #173d7610; }
.cart-content { min-width: 0; padding: 16px; }
.cart-toolbar, .cart-tools, .cart-section-title, .cart-select { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.cart-toolbar { justify-content: space-between; margin: 0 0 14px; padding: 0 4px; }
.cart-select { font-size: 14px; font-weight: 700; cursor: pointer; }
.cart-select span { color: #536582; font-weight: 500; }
.cart-page input[type=checkbox] { width: 17px; height: 17px; margin: 0; accent-color: #2867f7; cursor: pointer; }
.cart-text-button { display: inline-flex; align-items: center; justify-content: center; gap: 5px; border: 0; background: transparent; padding: 5px; color: #445675; font-size: 13px; }
.cart-text-button:hover:not(:disabled) { color: var(--blue); background: #edf4ff; border-radius: 5px; }
.cart-text-button:disabled { cursor: not-allowed; opacity: .45; }
.cart-page button svg, .cart-select svg, .cart-hint svg, .cart-parts svg { width: 17px; height: 17px; flex: none; }
.cart-section { border: 1px solid #e1e9f8; border-radius: 11px; margin-bottom: 14px; overflow: hidden; }
.cart-section:last-child { margin-bottom: 0; }
.cart-section-title { background: #eff4fc; min-height: 42px; padding: 8px 12px; }
.cart-section-title svg { color: var(--blue); }
.cart-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.cart-table td { padding: 10px 7px; border-bottom: 1px solid #edf1f8; }
.cart-table tr:last-child td { border-bottom: 0; }
.cart-row-select { width: 32px; text-align: center; }
.cart-product { display: flex; gap: 10px; align-items: center; min-width: 0; }
.cart-product img { width: 60px; height: 52px; flex: none; object-fit: contain; }
.cart-name { font-weight: 700; overflow-wrap: anywhere; }
.cart-name:hover { color: var(--blue); }
.cart-meta { display: block; color: #63718a; line-height: 1.7; margin-top: 4px; }
.cart-sku { overflow-wrap: anywhere; }
.cart-meta span, .cart-source { display: inline-block; border-radius: 5px; padding: 1px 6px; color: #2465e7; background: #e5efff; }
.cart-problem { display: block; color: #9c5d16; font-size: 12px; margin-top: 4px; }
.cart-stock { color: #25823f; white-space: nowrap; }
.cart-price { color: #dd1435; font-weight: 750; white-space: nowrap; font-variant-numeric: tabular-nums; }
.cart-quantity, .cart-stepper { display: flex; align-items: center; gap: 4px; }
.cart-stepper { gap: 0; border: 1px solid #d8e1f0; border-radius: 6px; overflow: hidden; }
.cart-stepper input, .cart-stepper button { width: 24px; padding: 3px 0; text-align: center; border: 0; background: #fff; font: inherit; }
.cart-stepper input { width: 30px; border-inline: 1px solid #edf1f8; }
.cart-stepper button:disabled { opacity: .45; cursor: not-allowed; }
.cart-quantity > button { white-space: nowrap; }
.cart-group-subtotal { margin-left: auto; font-size: 13px; white-space: nowrap; }
.cart-group-toggle[aria-expanded=true] svg { transform: rotate(-90deg); }
.cart-group-toggle[aria-expanded=false] svg { transform: rotate(90deg); }
.cart-group-body { display: grid; grid-template-columns: 90px minmax(0, 1fr) auto; gap: 14px; align-items: center; padding: 14px; }
.cart-group-body > img { width: 90px; height: 110px; object-fit: contain; }
.cart-parts { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px 18px; padding: 0; margin: 0; list-style: none; font-size: 13px; }
.cart-parts li { display: grid; grid-template-columns: auto minmax(0, 1fr) auto; gap: 6px; align-items: start; }
.cart-parts svg { color: #546985; }
.cart-parts span { color: #62728e; }
.cart-hint { display: flex; gap: 7px; align-items: center; margin: 12px 0 0; padding: 11px; border-radius: 7px; background: #eff4fc; color: #4e6387; font-size: 13px; }
.cart-hint svg { color: var(--blue); }
.cart-summary { position: sticky; top: 16px; padding: 16px; }
.cart-summary h2 { display: flex; align-items: center; gap: 8px; margin: 0 -16px 14px; padding: 0 16px 14px; border-bottom: 1px solid #edf1f8; font-size: 18px; }
.cart-summary h2 svg { width: 23px; height: 23px; color: var(--blue); }
.cart-summary dl { margin: 0; }
.cart-summary dl div { display: flex; justify-content: space-between; gap: 8px; margin: 0 0 12px; font-size: 14px; }
.cart-summary dd { margin: 0; font-weight: 700; }
.cart-summary dl .cart-summary-total { display: block; border-top: 1px solid #e7edf7; padding-top: 14px; font-weight: 700; }
.cart-summary-total dd { text-align: right; margin-top: 4px; font-size: 29px; color: #dd1435; overflow-wrap: anywhere; }
.cart-checkout { width: 100%; min-height: 40px; }
.cart-checkout svg { margin-right: 6px; }
.cart-summary-note { color: #647796; line-height: 1.6; font-size: 12px; margin: 10px 0 4px; }
@media (max-width: 1120px) {
  .cart-layout { grid-template-columns: minmax(0, 1fr); }
  .cart-summary { position: static; }
}
@media (max-width: 720px) {
  .cart-content { padding: 10px; }
  .cart-table, .cart-table tbody { display: block; }
  .cart-table tr { display: flex; align-items: center; flex-wrap: wrap; gap: 4px 8px; padding: 8px; border-bottom: 1px solid #edf1f8; }
  .cart-table td { border: 0; padding: 3px; }
  .cart-table td:nth-child(2) { flex: 1 1 calc(100% - 50px); }
  .cart-product img { width: 48px; height: 42px; }
  .cart-parts { grid-template-columns: minmax(0, 1fr); }
  .cart-group-body { grid-template-columns: minmax(0, 1fr); gap: 9px; }
  .cart-group-body > img { display: none; }
  .cart-group-body > button { justify-self: start; }
  .cart-group-subtotal { margin-left: 0; }
}
</style>
