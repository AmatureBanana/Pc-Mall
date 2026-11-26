<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue'
import { ElButton, ElMessageBox } from 'element-plus'
import ScoreDisplay from './ScoreDisplay.vue'
import { formatPrice } from './catalog'
import { restoreWorkspace } from './diy'
import { recommend, queueRecommendation, recommendationScenarios, scoreComponents, scoreLabels,
  type Recommendation, type Scenario } from './recommendation'

const props = defineProps<{ scenario: Scenario }>()
const emit = defineEmits<{ 'start-diy': [] }>()
const policy = recommendationScenarios[props.scenario]
const budget = ref('')
const busy = ref(false)
const importing = ref(false)
const error = ref('')
const result = ref<Recommendation | null>(null)
const weightedComponents = computed(() => {
  const plan = result.value
  return plan?.status === 'SUCCESS' ? scoreComponents.filter(key => plan.weights[key] > 0) : []
})
const pointsFormat = new Intl.NumberFormat('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
const partScores = computed(() => {
  const plan = result.value
  if (plan?.status !== 'SUCCESS') return {}
  return Object.fromEntries(weightedComponents.value.map(component => {
    const weight = plan.weights[component]
    const points = Number(plan.scores[component]) * weight / 100
    return [component.toUpperCase(), { points: pointsFormat.format(points), weight }]
  }))
})
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
    const response = await recommend(props.scenario, Number(budget.value).toFixed(2), current.signal)
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
  <div class="scenario-recommendation">
    <form class="recommendation-budget" @submit.prevent="generate">
      <label for="recommendation-budget">预算（CNY）<span>¥ <input id="recommendation-budget" v-model="budget" inputmode="decimal" maxlength="13" placeholder="例如 6000.00" :disabled="importing"/></span></label>
      <ElButton type="primary" native-type="submit" :loading="busy" :disabled="importing">生成{{ policy.label }}方案</ElButton>
    </form>
    <p v-if="error" class="recommendation-error" role="alert">{{ error }}</p>
    <p v-if="busy" role="status">正在筛选预算内的商品并复核兼容性…</p>
    <p v-if="result && result.status !== 'SUCCESS'" class="recommendation-feedback" role="status">{{ result.message }}</p>
    <section v-if="result?.status === 'SUCCESS'" class="recommendation-result" :aria-label="`${policy.label}推荐方案`">
      <div class="recommendation-score">
        <ScoreDisplay :label="`${policy.label}评分`" :score="Number(result.score).toFixed(2)"/>
        <ul><li v-for="component in weightedComponents" :key="component">
          {{ scoreLabels[component] }} {{ Number(result.scores[component]).toFixed(2) }} × {{ result.weights[component] }}%
        </li></ul>
      </div>
      <div class="recommendation-totals"><p>参考总价<strong>¥{{ formatPrice(result.total) }}</strong></p><p>预算余额<strong>¥{{ formatPrice(result.remaining) }}</strong></p></div>
      <ul class="recommendation-parts">
        <li v-for="item in result.items" :key="item.category">
          <div>
            <small>{{ item.product?.categoryLabel }} · {{ item.quantity }} 件</small>
            <a :href="`#product/${item.productId}`">{{ item.product?.name }}</a>
            <small>单价 ¥{{ formatPrice(item.product!.price) }}</small>
          </div>
          <ScoreDisplay v-if="partScores[item.category]" class="recommendation-part-score" compact
            :score="partScores[item.category]!.points" :max="partScores[item.category]!.weight"/>
          <span v-else class="recommendation-part-score recommendation-part-unscored">不计分</span>
          <strong class="recommendation-part-amount">¥{{ formatPrice(item.amount!) }}</strong>
        </li>
        <li v-if="result.integratedGraphicsCpuId">
          <div>
            <small>显卡选择</small>
            <strong>使用所选 CPU 核显</strong>
          </div>
          <span class="recommendation-part-score recommendation-part-unscored">不计分</span>
          <strong class="recommendation-part-amount">¥0.00</strong>
        </li>
      </ul>
      <details class="recommendation-reasons" open><summary>推荐理由与检测范围</summary><ul><li v-for="reason in result.reasons" :key="reason">{{ reason }}</li><li>核心检查通过。{{ result.compatibility.unexecutedExtensions.length }} 项扩展检测未执行，BIOS、超频及实机表现未验证。</li></ul></details>
      <ElButton type="primary" class="recommendation-action" :disabled="importing" @click="transfer">转入 DIY 调整</ElButton>
    </section>
  </div>
</template>

<style scoped>
.scenario-recommendation { color: #304b70; }
.recommendation-budget { display: flex; align-items: flex-end; gap: 14px; }
.recommendation-budget label { flex: 1; font-size: 14px; font-weight: 600; }
.recommendation-budget label > span { display: flex; align-items: center; gap: 10px; padding: 0 12px; height: 44px; margin-top: 8px; border: 1px solid #b7cdec; border-radius: 7px; }
.recommendation-budget input { width: 100%; min-width: 0; border: 0; background: transparent; color: #19304f; outline: none; }
.recommendation-budget .el-button { height: 44px; }
.recommendation-error { color: #b52e44; line-height: 1.7; }
.recommendation-feedback { padding: 16px; background: #f1f6ff; border-radius: 8px; line-height: 1.8; }
.recommendation-result { margin-top: 24px; }
.recommendation-score { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 20px; border-radius: 12px; background: #eaf2ff; }
.recommendation-score ul { margin: 0; padding: 0; list-style: none; font-size: 13px; line-height: 1.9; }
.recommendation-totals { display: flex; gap: 36px; padding: 4px 0; }
.recommendation-totals p { line-height: 1.8; font-size: 13px; }.recommendation-totals strong { display: block; color: #19304f; font-size: 20px; }
.recommendation-parts { margin: 0; padding: 0; list-style: none; border-top: 1px solid #e1eafa; }
.recommendation-parts li { display: grid; grid-template-columns: minmax(0, 1fr) minmax(90px, 24%) 104px; align-items: center; gap: 16px; padding: 12px 0; border-bottom: 1px solid #e1eafa; }
.recommendation-part-amount { text-align: right; white-space: nowrap; font-size: 14px; }
.recommendation-parts small { display: block; font-size: 12px; color: #6d809c; margin-block: 3px; }
.recommendation-parts li > div { min-width: 0; overflow-wrap: anywhere; }
.recommendation-parts a { display: block; color: #1a5dad; line-height: 1.7; }.recommendation-parts a:hover { text-decoration: underline; }
.recommendation-part-score { text-align: center; }
.recommendation-part-unscored { color: #536a89; font-size: 13px; }
.recommendation-reasons { margin-block: 20px; font-size: 13px; line-height: 1.9; }.recommendation-reasons summary { cursor: pointer; font-weight: 650; }.recommendation-reasons ul { padding-left: 20px; }
.recommendation-action { width: 100%; height: 44px; }
@media (max-width: 500px) {
  .recommendation-budget { align-items: stretch; flex-direction: column; }
  .recommendation-score { padding: 14px; }
  .recommendation-parts li { grid-template-columns: minmax(0, 1fr) 82px 72px; gap: 8px; }
}
</style>
