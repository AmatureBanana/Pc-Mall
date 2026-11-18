<script setup lang="ts">
import { onUnmounted, ref, watch } from 'vue'
import { ElButton, ElMessageBox } from 'element-plus'
import { formatPrice } from './catalog'
import { restoreWorkspace } from './diy'
import { recommendOffice, queueRecommendation, type Recommendation } from './recommendation'

const emit = defineEmits<{ 'start-diy': [] }>()
const budget = ref('')
const busy = ref(false)
const importing = ref(false)
const error = ref('')
const result = ref<Recommendation | null>(null)
let request: AbortController | null = null
function reset() { request?.abort(); request = null; busy.value = false; error.value = ''; result.value = null }
watch(budget, reset)
onUnmounted(reset)
async function generate() {
  reset()
  if (!/^\d{1,10}(\.\d{1,2})?$/.test(budget.value) || Number(budget.value) <= 0) {
    error.value = '请输入大于 0、最多两位小数的 CNY 预算。'; return
  }
  const current = new AbortController()
  request = current; busy.value = true
  try {
    const response = await recommendOffice(Number(budget.value).toFixed(2), current.signal)
    if (request === current && !current.signal.aborted) result.value = response
  } catch (cause) {
    if (!current.signal.aborted) error.value = cause instanceof Error ? cause.message : '推荐失败，请重试。'
  } finally { if (request === current) busy.value = false }
}
async function transfer() {
  const plan = result.value
  if (plan?.status !== 'SUCCESS' || importing.value) return
  importing.value = true
  try {
    if (restoreWorkspace().workspace.selections.length) {
      await ElMessageBox.confirm('转入此推荐会替换当前标签页的 DIY 配置，是否继续？', '替换当前配置',
        { confirmButtonText: '确认替换', cancelButtonText: '保留原配置', type: 'warning' })
    }
    if (result.value !== plan) return
    queueRecommendation(plan); emit('start-diy')
  } catch { /* Cancel preserves the existing workspace and recommendation. */ }
  finally { importing.value = false }
}
</script>

<template>
  <div class="office-recommendation">
    <p class="office-intro">输入整机预算，为日常办公搭配一套完整配置。</p>
    <form class="office-budget" @submit.prevent="generate">
      <label for="office-budget">预算（CNY）<span>¥ <input id="office-budget" v-model="budget" inputmode="decimal" maxlength="13" placeholder="例如 6000.00" :disabled="importing"/></span></label>
      <ElButton type="primary" native-type="submit" :loading="busy" :disabled="importing">生成办公方案</ElButton>
    </form>
    <p v-if="error" class="office-error" role="alert">{{ error }}</p>
    <p v-if="busy" role="status">正在筛选预算内的商品并复核兼容性…</p>
    <p v-if="result && result.status !== 'SUCCESS'" class="office-feedback" role="status">{{ result.message }}</p>
    <section v-if="result?.status === 'SUCCESS'" class="office-result" aria-label="办公推荐方案">
      <div class="office-score"><div><small>办公评分</small><strong>{{ Number(result.score).toFixed(2) }} <span>/ 100</span></strong></div><p>CPU {{ Number(result.scores.cpu).toFixed(2) }} × 50%<br/>内存 {{ Number(result.scores.memory).toFixed(2) }} × 30%<br/>SSD {{ Number(result.scores.ssd).toFixed(2) }} × 20%</p></div>
      <div class="office-totals"><p>参考总价<strong>¥{{ formatPrice(result.total) }}</strong></p><p>预算余额<strong>¥{{ formatPrice(result.remaining) }}</strong></p></div>
      <ul class="office-parts"><li v-for="item in result.items" :key="item.category"><div><small>{{ item.product?.categoryLabel }} · {{ item.quantity }} 件</small><a :href="`#product/${item.productId}`">{{ item.product?.name }}</a><small>单价 ¥{{ formatPrice(item.product!.price) }}</small></div><strong>¥{{ formatPrice(item.amount!) }}</strong></li><li v-if="result.integratedGraphicsCpuId"><div><small>显卡选择</small><strong>使用所选 CPU 核显</strong></div><strong>¥0.00</strong></li></ul>
      <details class="office-reasons" open><summary>推荐理由与检测范围</summary><ul><li v-for="reason in result.reasons" :key="reason">{{ reason }}</li><li>九项核心检测已通过；{{ result.compatibility.unexecutedExtensions.length }} 项扩展检测未执行，BIOS、超频及实机表现未验证。</li></ul></details>
      <ElButton type="primary" class="office-transfer" :loading="importing" @click="transfer">转入 DIY 调整</ElButton>
    </section>
  </div>
</template>

<style scoped>
.office-recommendation { color: #304b70; }
.office-intro { margin: 0 0 18px; line-height: 1.8; }
.office-budget { display: flex; align-items: flex-end; gap: 14px; }
.office-budget label { flex: 1; font-size: 14px; font-weight: 600; }
.office-budget label > span { display: flex; align-items: center; gap: 10px; padding: 0 12px; height: 44px; margin-top: 8px; border: 1px solid #b7cdec; border-radius: 7px; }
.office-budget input { width: 100%; min-width: 0; border: 0; background: transparent; color: #19304f; outline: none; }
.office-budget .el-button { height: 44px; }
.office-error { color: #b52e44; line-height: 1.7; }
.office-feedback { padding: 16px; background: #f1f6ff; border-radius: 8px; line-height: 1.8; }
.office-result { margin-top: 24px; }
.office-score { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 20px; border-radius: 12px; background: #eaf2ff; }
.office-score small { display: block; margin-bottom: 8px; }
.office-score strong { font-size: 32px; color: #0968fa; }
.office-score strong span { font-size: 16px; font-weight: 500; }
.office-score p { margin: 0; font-size: 13px; line-height: 1.9; }
.office-totals { display: flex; gap: 36px; padding: 4px 0; }
.office-totals p { line-height: 1.8; font-size: 13px; }.office-totals strong { display: block; color: #19304f; font-size: 20px; }
.office-parts { margin: 0; padding: 0; list-style: none; border-top: 1px solid #e1eafa; }
.office-parts li { display: flex; justify-content: space-between; align-items: center; gap: 16px; padding: 12px 0; border-bottom: 1px solid #e1eafa; }
.office-parts li > strong { white-space: nowrap; font-size: 14px; }.office-parts small { display: block; font-size: 12px; color: #6d809c; margin-block: 3px; }
.office-parts li > div { min-width: 0; overflow-wrap: anywhere; }
.office-parts a { display: block; color: #1a5dad; line-height: 1.7; }.office-parts a:hover { text-decoration: underline; }
.office-reasons { margin-block: 20px; font-size: 13px; line-height: 1.9; }.office-reasons summary { cursor: pointer; font-weight: 650; }.office-reasons ul { padding-left: 20px; }
.office-transfer.el-button { width: 100%; height: 44px; }
@media (max-width: 500px) { .office-budget { align-items: stretch; flex-direction: column; }.office-score { padding: 14px; }.office-score strong { font-size: 26px; } }
</style>
