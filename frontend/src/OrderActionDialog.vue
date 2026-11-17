<script setup lang="ts">
import { computed } from 'vue'
import { ElButton, ElDialog } from 'element-plus'
import { useCart } from './cart'
import { formatPrice } from './catalog'

const props = defineProps<{ number: string; action: 'pay' | 'cancel' | null }>()
const emit = defineEmits<{ close: []; done: [] }>()
const { order, error, paymentBusy, cancellationBusy, payOrder, cancelOrder } = useCart()
const busy = computed(() => paymentBusy.value || cancellationBusy.value)
async function confirm() {
  const { number, action } = props
  if (!action) return
  const success = action === 'pay' ? await payOrder(number) : await cancelOrder(number)
  if (success && props.number === number && props.action === action) { emit('close'); emit('done') }
}
</script>

<template>
  <ElDialog :model-value="action !== null" :title="action === 'pay' ? '确认模拟支付' : '确认取消订单'" width="min(480px, 94vw)"
    :close-on-click-modal="!busy" :close-on-press-escape="!busy" :show-close="!busy" @close="emit('close')">
    <template v-if="action === 'pay' && order && order.orderNumber === number">
      <p>本次为站内模拟支付。</p><p>订单：{{ number }}</p>
      <p>成交金额（CNY）：<strong>¥{{ formatPrice(order.total) }}</strong></p>
    </template>
    <template v-else-if="action === 'cancel'"><p>确认取消此待支付订单？成功后释放本单预占库存，不会恢复购物车。</p><p>订单：{{ number }}</p></template>
    <p v-if="!order && !error" role="status">正在核对订单…</p>
    <p v-if="error" role="alert" class="auth-error">{{ error }}</p>
    <a v-if="!order && error" href="#login" @click="emit('close')">前往登录</a>
    <template #footer><ElButton :disabled="busy" @click="emit('close')">返回订单</ElButton>
      <ElButton :type="action === 'pay' ? 'primary' : 'danger'" :loading="busy" :disabled="!order || order.orderNumber !== number || order.status !== 'PENDING_PAYMENT'" @click="confirm">{{ action === 'pay' ? '确认模拟支付' : '确认取消' }}</ElButton></template>
  </ElDialog>
</template>

<style scoped>
strong { color: #ed263c; font-size: 24px; }
a { color: var(--blue); }
</style>
