<script setup lang="ts">
import { onUnmounted, reactive, ref, watch } from 'vue'
import { ElButton } from 'element-plus'
import AdminField from './AdminField.vue'
import { AuthError } from './auth'
import { formatPerformanceScore, performanceReference, savePerformance, type PerformanceView } from './performance'

const props = defineProps<{ id: number; category: string; brand: string; disabled: boolean }>()
const emit = defineEmits<{ updated: [revision: number]; expired: [] }>()
const data = ref<PerformanceView | null>(null)
const busy = ref(false), error = ref(''), notice = ref(''), unauthorized = ref(false)
const fields = reactive({ valid: 'false', rawScore: '', sourceUrl: '', snapshotDate: '', snapshotSha256: '', deviceName: '',
  sampleConditions: '', matchNote: '', ids: '', reason: '' })
let requestVersion = 0
function feedback(cause: unknown) {
  error.value = cause instanceof Error ? cause.message : '评分维护失败，请重试。'
  if (cause instanceof AuthError && [401, 403].includes(cause.status)) {
    unauthorized.value = true
    if (cause.status === 401) emit('expired')
  }
}
function accept(result: PerformanceView) {
  data.value = result
  const r = result.reference
  fields.valid = String(r.valid === true)
  for (const key of ['rawScore', 'sourceUrl', 'snapshotDate', 'snapshotSha256', 'deviceName', 'sampleConditions', 'matchNote'] as const) {
    fields[key] = typeof r[key] === 'string' ? r[key] as string : ''
  }
  fields.ids = Array.isArray(r.sampleIds) ? r.sampleIds.join('\n') : ''
  fields.reason = ''
}
async function load() {
  if (busy.value) return
  const version = ++requestVersion
  busy.value = true; error.value = ''; notice.value = ''
  try { const result = await performanceReference(props.id); if (version === requestVersion) accept(result) }
  catch (cause) { if (version === requestVersion) feedback(cause) }
  finally { if (version === requestVersion) busy.value = false }
}
async function save() {
  if (!data.value || busy.value || props.disabled || unauthorized.value) return
  const version = ++requestVersion
  busy.value = true; error.value = ''; notice.value = ''
  try {
    const ids = fields.ids.split(/\s+/).filter(Boolean)
    const result = await savePerformance(props.id, { ...fields, ids: undefined, revision: data.value.revision, valid: fields.valid === 'true',
      benchmarkVersion: '4.3.0', mappingVersion: 'performance-v1', computeType: props.category === 'CPU' ? 'CPU' : props.brand.toUpperCase() === 'AMD' ? 'HIP' : 'OPTIX',
      operatingSystem: 'Windows', scenes: ['classroom', 'junkshop', 'monster'], sampleCount: ids.length, sampleIds: ids })
    if (version !== requestVersion) return
    accept(result); emit('updated', result.revision); notice.value = '评分与审计已保存。'
  } catch (cause) { if (version === requestVersion) feedback(cause) }
  finally { if (version === requestVersion) busy.value = false }
}
watch(() => props.id, () => { requestVersion++; busy.value = false; data.value = null; unauthorized.value = false; void load() }, { immediate: true })
onUnmounted(() => { requestVersion++ })
</script>

<template>
  <section class="performance-admin" role="region" aria-label="性能评分维护">
    <h3>性能评分维护</h3>
    <p>固定 Blender 4.3.0 · Windows · {{ category === 'CPU' ? 'CPU' : brand.toUpperCase() === 'AMD' ? 'HIP' : 'OPTIX' }} · performance-v1。内部渲染参考分不代表游戏 FPS。</p>
    <p v-if="data" class="performance-summary"><strong class="performance-score">{{ data.performance.score === null ? '无有效评分' : `当前参考分 ${formatPerformanceScore(data.performance.score)} / 100` }}</strong> · {{ data.performance.note }}</p>
    <p v-if="error" role="alert" class="auth-error">{{ error }}</p>
    <p v-if="notice" role="status" class="auth-success">{{ notice }}</p>
    <p v-if="busy" role="status">正在处理评分数据…</p>
    <fieldset :disabled="busy || disabled || unauthorized || !data">
      <legend>来源与采样条件</legend>
      <div class="admin-base-grid">
        <label class="admin-field">评分有效状态<select v-model="fields.valid" aria-label="评分有效状态"><option value="false">无效 / 待核验</option><option value="true">已核验有效</option></select></label>
        <AdminField v-model="fields.rawScore" label="原始渲染分" :maxlength="33"/>
        <AdminField v-model="fields.deviceName" label="基准设备名称" :maxlength="100"/>
        <AdminField v-model="fields.sourceUrl" label="评分来源地址" :maxlength="1000"/>
        <AdminField v-model="fields.snapshotDate" label="快照日期" :maxlength="10"/>
        <AdminField v-model="fields.snapshotSha256" label="快照SHA-256" :maxlength="64"/>
        <AdminField v-model="fields.sampleConditions" label="采样条件" multiline :maxlength="2000"/>
        <AdminField v-model="fields.matchNote" label="型号与变体匹配依据" multiline :maxlength="2000"/>
        <AdminField v-model="fields.ids" label="完整提交UUID（每行一个）" multiline :maxlength="370000"/>
        <AdminField v-model="fields.reason" label="评分调整原因" multiline :maxlength="500"/>
      </div>
      <p class="admin-form-hint">有效评分须有至少 3 个独立完整提交；每次提交覆盖 classroom、junkshop、monster，再取总分中位数。保存会检查当前商品型号与版本。</p>
      <ElButton type="primary" :disabled="busy || disabled || unauthorized || !data" @click="save">保存评分</ElButton>
    </fieldset>
    <ElButton :disabled="busy || disabled || unauthorized" @click="load">重新载入评分</ElButton>
  </section>
</template>
