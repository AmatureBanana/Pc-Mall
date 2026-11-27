<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElButton } from 'element-plus'
import OrderActionDialog from './OrderActionDialog.vue'
import { cancellationReasons, orderStates, useCart } from './cart'
import { categoryName, formatPrice } from './catalog'
import { signedIn } from './auth'
import { sortHardwareItems } from './diy'

const props = defineProps<{ number: string }>()
const { order, error, loadOrder, paymentBusy, cancellationBusy } = useCart()
const action = ref<'pay' | 'cancel' | null>(null)
watch(() => [props.number, signedIn.value] as const, () => {
  action.value = null
  if (signedIn.value) void loadOrder(props.number)
}, { immediate: true })
const time = (value: string) => new Date(value).toLocaleString('zh-CN', { hour12: false })
const sections = computed(() => order.value ? [
  { id: null, title: '成交单品', amount: null, items: order.value.items.filter(item => item.configurationId === null) },
  ...order.value.configurations.map(group => ({ id: group.id, title: '成交配置组', amount: group.amount,
    items: sortHardwareItems(order.value!.items.filter(item => item.configurationId === group.id)) })),
].filter(section => section.items.length) : [])
</script>

<template>
  <main class="order-result page-width" aria-label="订单结果">
    <h1>订单结果</h1>
    <p v-if="!signedIn">请先登录查看本人订单。<a href="#login">前往登录</a></p>
    <template v-else>
      <p v-if="error && !action" role="alert" class="auth-error">{{ error }}</p>
      <section v-if="order && order.orderNumber === number" aria-label="成交快照">
        <h2>{{ orderStates[order.status] }}</h2>
        <dl><dt>订单号</dt><dd>{{ order.orderNumber }}</dd><dt>创建时间</dt><dd>{{ time(order.createdAt) }}</dd>
          <dt>支付截止</dt><dd>{{ time(order.paymentDeadline) }}</dd>
          <template v-if="order.paidAt"><dt>支付时间</dt><dd>{{ time(order.paidAt) }}</dd></template>
          <template v-if="order.cancelledAt"><dt>取消时间</dt><dd>{{ time(order.cancelledAt) }}</dd></template>
          <template v-if="order.cancellationReason"><dt>取消原因</dt><dd>{{ cancellationReasons[order.cancellationReason] }}</dd></template></dl>
        <section v-for="section in sections" :key="section.id ?? 'singles'" :aria-label="section.title" class="order-group">
          <h3>{{ section.title }}</h3>
          <table><thead><tr><th>商品 / SKU</th><th>型号 / 类别</th><th>单价</th><th>数量</th><th>小计</th></tr></thead>
            <tbody><tr v-for="item in section.items" :key="item.productId"><td>{{ item.name }}<small>{{ item.sku }}</small></td>
              <td>{{ item.model }} / {{ categoryName(item.category) }}</td><td>¥{{ formatPrice(item.price) }}</td><td>{{ item.quantity }}</td><td>¥{{ formatPrice(item.amount) }}</td></tr></tbody>
          </table>
          <p v-if="section.amount">组成交小计：¥{{ formatPrice(section.amount) }}</p>
        </section>
        <p>成交总额（CNY）：<strong aria-label="订单成交总额">¥{{ formatPrice(order.total) }}</strong></p>
        <template v-if="order.status === 'PENDING_PAYMENT'">
          <p>已预占本单库存。仅支付截止前可模拟支付，截止以服务端判定为准。</p>
          <ElButton type="primary" :loading="paymentBusy" :disabled="cancellationBusy" @click="action = 'pay'">模拟支付</ElButton>
          <ElButton :loading="cancellationBusy" :disabled="paymentBusy" @click="action = 'cancel'">取消订单</ElButton>
        </template>
        <p v-if="order.status === 'CANCELLED'">订单已取消，不会恢复购物车。</p>
      </section>
      <p v-else-if="!error" role="status">正在读取订单…</p>
      <ElButton :disabled="paymentBusy || cancellationBusy" @click="loadOrder(number)">重新读取订单</ElButton>
    </template>
    <a href="#orders">返回我的订单</a><a href="#cart">返回购物车</a>
    <OrderActionDialog :number="number" :action="action" @close="action = null"/>
  </main>
</template>

<style scoped>
.order-result { padding-block: 28px; }
section { padding: 24px; margin-block: 20px; border: 1px solid #e0e8f7; border-radius: 15px; background: white; }
.order-group { padding: 16px; }
dl { display: grid; grid-template-columns: auto 1fr; gap: 10px 20px; }
dd { margin: 0; overflow-wrap: anywhere; }
table { width: 100%; border-collapse: collapse; margin-block: 24px; text-align: left; }
th, td { padding: 12px; border-bottom: 1px solid #e0e8f7; }
small { display: block; color: #647796; }
strong { color: #dd1435; font-size: 24px; }
a { display: inline-block; margin: 16px; color: var(--blue); }
</style>
