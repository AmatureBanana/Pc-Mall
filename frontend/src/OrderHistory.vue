<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElButton } from 'element-plus'
import { signedIn } from './auth'
import { formatPrice } from './catalog'
import { orderStates, useCart } from './cart'

const { orderHistory, historyLoading, error, loadOrders } = useCart()
const page = ref(1)
watch([signedIn, page], () => { if (signedIn.value) void loadOrders(page.value) }, { immediate: true })
const time = (value: string) => new Date(value).toLocaleString('zh-CN', { hour12: false })
</script>

<template>
  <main class="order-history page-width" aria-label="我的订单" :aria-busy="historyLoading">
    <h1>我的订单</h1>
    <p v-if="!signedIn">请先登录查看本人订单。<a href="#login">前往登录</a></p>
    <template v-else>
      <ElButton :loading="historyLoading" @click="loadOrders(page)">刷新订单</ElButton>
      <p v-if="error" role="alert" class="auth-error">{{ error }}</p>
      <p v-if="historyLoading" role="status">正在读取订单…</p>
      <template v-else-if="orderHistory">
        <p>共 {{ orderHistory.total }} 笔订单</p>
        <p v-if="!orderHistory.items.length">暂无订单</p>
        <div v-else class="history-table"><table><thead><tr><th>订单号</th><th>状态</th><th>成交金额（CNY）</th><th>创建时间</th><th>支付截止 / 支付时间</th></tr></thead>
          <tbody><tr v-for="order in orderHistory.items" :key="order.orderNumber">
            <td><a :href="`#order/${order.orderNumber}`">{{ order.orderNumber }}</a></td><td>{{ orderStates[order.status] }}</td>
            <td>¥{{ formatPrice(order.total) }}</td><td>{{ time(order.createdAt) }}</td>
            <td>{{ time(order.paidAt ?? order.paymentDeadline) }}</td>
          </tr></tbody></table></div>
        <nav aria-label="订单分页"><ElButton :disabled="page <= 1" @click="page--">上一页</ElButton>
          <span>第 {{ page }} 页</span><ElButton :disabled="page * orderHistory.pageSize >= orderHistory.total" @click="page++">下一页</ElButton></nav>
      </template>
    </template>
    <a class="back" href="#">返回商城</a>
  </main>
</template>

<style scoped>
.order-history { padding-block: 28px; }
.history-table { overflow-x: auto; margin-block: 20px; background: white; border: 1px solid #e0e8f7; border-radius: 12px; }
table { width: 100%; border-collapse: collapse; text-align: left; }
th, td { padding: 18px 12px; border-bottom: 1px solid #e0e8f7; }
a { color: var(--blue); overflow-wrap: anywhere; }
nav { display: flex; align-items: center; gap: 16px; }
.back { display: inline-block; margin-block: 24px; }
</style>
