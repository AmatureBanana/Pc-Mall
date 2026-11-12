<script setup lang="ts">
import { watch } from 'vue'
import { ElButton } from 'element-plus'
import { useCart } from './cart'
import { formatPrice } from './catalog'
import { signedIn } from './auth'

const props = defineProps<{ number: string }>()
const { order, error, loadOrder } = useCart()
watch(() => [props.number, signedIn.value] as const, () => { if (signedIn.value) void loadOrder(props.number) }, { immediate: true })
const time = (value: string) => new Date(value).toLocaleString('zh-CN', { hour12: false })
const states = { PENDING_PAYMENT: '待支付', PAID: '已支付', CANCELLED: '已取消', COMPLETED: '已完成' }
</script>

<template>
  <main class="order-result page-width" aria-label="订单结果">
    <h1>订单结果</h1>
    <p v-if="!signedIn">请先登录查看本人订单。<a href="#login">前往登录</a></p>
    <template v-else>
      <p v-if="error" role="alert" class="auth-error">{{ error }}</p>
      <section v-if="order && order.orderNumber === number" aria-label="成交快照">
        <h2>{{ states[order.status] }}</h2>
        <dl><dt>订单号</dt><dd>{{ order.orderNumber }}</dd><dt>创建时间</dt><dd>{{ time(order.createdAt) }}</dd>
          <dt>支付截止</dt><dd>{{ time(order.paymentDeadline) }}</dd></dl>
        <table><thead><tr><th>商品 / SKU</th><th>型号 / 类别</th><th>单价</th><th>数量</th><th>小计</th></tr></thead>
          <tbody><tr v-for="item in order.items" :key="item.productId"><td>{{ item.name }}<small>{{ item.sku }}</small></td>
            <td>{{ item.model }} / {{ item.category }}</td><td>¥{{ formatPrice(item.price) }}</td><td>{{ item.quantity }}</td><td>¥{{ formatPrice(item.amount) }}</td></tr></tbody>
        </table>
        <p>成交总额（CNY）：<strong aria-label="订单成交总额">¥{{ formatPrice(order.total) }}</strong></p>
        <p>已预占本单库存。模拟支付功能尚未开放。</p>
      </section>
      <p v-else-if="!error" role="status">正在读取订单…</p>
      <ElButton @click="loadOrder(number)">重新读取订单</ElButton>
    </template>
    <a href="#cart">返回购物车</a>
  </main>
</template>

<style scoped>
.order-result { padding-block: 28px; }
section { padding: 24px; margin-block: 20px; border: 1px solid #e0e8f7; border-radius: 15px; background: white; }
dl { display: grid; grid-template-columns: auto 1fr; gap: 10px 20px; }
dd { margin: 0; overflow-wrap: anywhere; }
table { width: 100%; border-collapse: collapse; margin-block: 24px; text-align: left; }
th, td { padding: 12px; border-bottom: 1px solid #e0e8f7; }
small { display: block; color: #647796; }
strong { color: #dd1435; font-size: 24px; }
a { display: inline-block; margin: 16px; color: var(--blue); }
</style>
