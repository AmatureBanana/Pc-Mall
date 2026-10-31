<script setup lang="ts">
import { nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { ElButton, ElPagination, ElTable, ElTableColumn, ElTag } from 'element-plus'
import AdminField from './AdminField.vue'
import { AuthError } from './auth'
import { categories, formatPrice } from './catalog'
import { catalogPage, catalogDetail, parameterSchema, saveProduct, changeSaleStatus,
  type AdminDetail, type AdminPage, type AdminParameter, type ParameterField, type Slot } from './adminCatalog'

const emit = defineEmits<{ expired: [] }>()
const page = ref<AdminPage>({ items: [], total: 0, page: 1, size: 10 })
const keyword = ref(''), category = ref(''), status = ref('')
const busy = ref(false), loading = ref(false), editing = ref(false)
const error = ref(''), notice = ref(''), unauthorized = ref(false)
const detail = ref<AdminDetail | null>(null), schema = ref<ParameterField[]>([])
const feedbackElement = ref<HTMLElement | null>(null)
const parameters = ref<AdminParameter[]>([])
const form = reactive({ sku: '', category: 'CPU', name: '', brand: '', model: '', summary: '', description: '', imageUrl: '', sourceUrl: '', priceNote: '管理员录入的 CNY 售价', price: '' })
let revision = 0
function feedback(cause: unknown) {
  error.value = cause instanceof Error ? cause.message : '商品维护失败，请稍后重试。'
  if (cause instanceof AuthError && (cause.status === 401 || cause.status === 403)) {
    unauthorized.value = true
    if (cause.status === 401) emit('expired')
  }
}
async function loadPage(number = 1) {
  const current = ++revision
  loading.value = true; error.value = ''; notice.value = ''
  const query = new URLSearchParams({ keyword: keyword.value, page: String(number), size: '10' })
  if (category.value) query.set('category', category.value)
  if (status.value) query.set('status', status.value)
  try { const result = await catalogPage(query); if (current === revision) page.value = result }
  catch (cause) { if (current === revision) feedback(cause) }
  finally { if (current === revision) loading.value = false }
}
function setParameters(fields: ParameterField[], existing: AdminParameter[] = []) {
  schema.value = fields
  parameters.value = fields.map(field => existing.find(p => p.code === field.code) ?? {
    code: field.code, label: field.label, value: null, unit: field.unit === 'CAPACITY' ? 'TB' : field.unit, sourceUrl: '', note: '',
  })
}
async function chooseCategory() {
  const current = ++revision
  loading.value = true; error.value = ''
  try { const fields = await parameterSchema(form.category); if (current === revision) setParameters(fields) }
  catch (cause) { if (current === revision) feedback(cause) }
  finally { if (current === revision) loading.value = false }
}
async function open(id: number | null) {
  const current = ++revision
  loading.value = true; editing.value = true; error.value = ''; notice.value = ''; detail.value = null
  Object.assign(form, { sku: '', category: 'CPU', name: '', brand: '', model: '', summary: '', description: '', imageUrl: '', sourceUrl: '', priceNote: '管理员录入的 CNY 售价', price: '' })
  parameters.value = []; schema.value = []
  try {
    const result = id === null ? null : await catalogDetail(id)
    const fields = await parameterSchema(result?.product.category ?? 'CPU')
    if (current !== revision) return
    detail.value = result
    if (result) Object.assign(form, { ...result.product, description: result.description })
    setParameters(fields, result?.parameters)
  } catch (cause) { if (current === revision) feedback(cause) }
  finally { if (current === revision) loading.value = false }
}
function payload() {
  const base = { name: form.name, brand: form.brand, model: form.model, summary: form.summary, description: form.description,
    imageUrl: form.imageUrl, sourceUrl: form.sourceUrl, priceNote: form.priceNote, parameters: parameters.value.filter(p =>
      p.value !== null || p.sourceUrl !== '' || p.note !== '' || detail.value?.parameters.some(existing => existing.code === p.code)) }
  return detail.value ? { ...base, revision: detail.value.revision } : {
    ...base, sku: form.sku, category: form.category, ...(form.price ? { price: form.price } : {}),
  }
}
async function save() {
  if (busy.value || loading.value) return
  busy.value = true; error.value = ''; notice.value = ''
  try {
    const result = await saveProduct(detail.value?.product.id ?? null, payload())
    detail.value = result; setParameters(schema.value, result.parameters)
    notice.value = '保存成功，商品资料与审计已一起提交。'
  } catch (cause) { feedback(cause) }
  finally { busy.value = false }
}
async function changeStatus() {
  if (!detail.value || busy.value || loading.value) return
  busy.value = true; error.value = ''; notice.value = ''
  try {
    detail.value = await changeSaleStatus(detail.value)
    notice.value = detail.value.product.status === 'ON_SALE' ? '商品已上架。' : '商品已下架，历史关联保留。'
  } catch (cause) { feedback(cause) }
  finally { busy.value = false }
}
function numeric(parameter: AdminParameter, event: Event) {
  const input = event.target as HTMLInputElement
  parameter.value = input.value === '' ? null : input.valueAsNumber
}
function boolean(parameter: AdminParameter, event: Event) {
  const value = (event.target as HTMLSelectElement).value
  parameter.value = value === '' ? null : value === 'true'
}
function setValue(parameter: AdminParameter, event: Event) {
  parameter.value = Array.from((event.target as HTMLSelectElement).selectedOptions, option => option.value)
}
function slots(parameter: AdminParameter): Slot[] { return Array.isArray(parameter.value) ? parameter.value as Slot[] : [] }
function addSlot(parameter: AdminParameter) { parameter.value = [...slots(parameter), { formFactor: 'M.2 2280', interface: 'PCIe', protocol: 'NVMe' }] }
function back() { editing.value = false; detail.value = null; void loadPage() }
function login() { window.location.hash = 'login' }
onMounted(() => void loadPage())
onUnmounted(() => { revision++ })
watch([error, notice], async () => {
  if (error.value || notice.value) { await nextTick(); feedbackElement.value?.scrollIntoView({ block: 'nearest' }) }
})
</script>

<template>
  <div class="catalog-admin">
    <div class="admin-section-head"><div><h2>商品管理</h2><p>维护八类硬件资料、分类参数与销售状态</p></div><ElButton v-if="!editing" type="primary" :disabled="loading || unauthorized" @click="open(null)">新增商品</ElButton><ElButton v-else :disabled="busy" @click="back">返回商品列表</ElButton></div>
    <div ref="feedbackElement"><p v-if="error" role="alert" class="auth-error">{{ error }} <button v-if="unauthorized" @click="login">前往登录</button></p>
    <p v-if="notice" role="status" class="auth-success">{{ notice }}</p></div>
    <p v-if="loading" role="status">正在加载商品数据…</p>
    <template v-if="!editing">
      <form class="admin-filters" @submit.prevent="loadPage()"><label>关键词<input v-model="keyword" aria-label="商品关键词" placeholder="名称、SKU、品牌或型号" maxlength="100"/></label>
        <label>类别<select v-model="category" aria-label="筛选商品类别"><option value="">全部类别</option><option v-for="item in categories.slice(1)" :key="item.code" :value="item.code">{{ item.label }}</option></select></label>
        <label>状态<select v-model="status" aria-label="筛选销售状态"><option value="">全部状态</option><option value="OFF_SALE">已下架</option><option value="ON_SALE">已上架</option></select></label>
        <ElButton native-type="submit" :disabled="loading">查询</ElButton></form>
      <ElTable :data="page.items" empty-text="没有符合条件的商品" stripe>
        <ElTableColumn prop="sku" label="SKU" min-width="185"/><ElTableColumn prop="name" label="商品" min-width="185"/>
        <ElTableColumn prop="categoryLabel" label="类别" width="82"/><ElTableColumn label="售价 / 库存" width="128"><template #default="scope">¥{{ formatPrice(scope.row.price) }}<small class="admin-stock">可售 {{ scope.row.stock }}</small></template></ElTableColumn>
        <ElTableColumn label="状态" width="90"><template #default="scope"><ElTag :type="scope.row.status === 'ON_SALE' ? 'success' : 'info'">{{ scope.row.status === 'ON_SALE' ? '已上架' : '已下架' }}</ElTag></template></ElTableColumn>
        <ElTableColumn label="操作" width="85"><template #default="scope"><ElButton link type="primary" :disabled="loading" @click="open(scope.row.id)">查看编辑</ElButton></template></ElTableColumn>
      </ElTable>
      <ElPagination :current-page="page.page" :page-size="page.size" :total="page.total" layout="total, prev, pager, next" :disabled="loading" @current-change="loadPage"/>
    </template>
    <form v-else class="admin-product-form" @submit.prevent="save">
      <div v-if="detail" class="admin-product-state"><ElTag :type="detail.product.status === 'ON_SALE' ? 'success' : 'info'">{{ detail.product.status === 'ON_SALE' ? '已上架' : '已下架' }}</ElTag><span>商品 #{{ detail.product.id }} · 当前售价 ¥{{ formatPrice(detail.product.price) }} · 可售库存 {{ detail.product.stock }}</span></div>
      <fieldset :disabled="busy || loading || unauthorized || schema.length === 0">
        <legend>商品资料</legend><div class="admin-base-grid">
          <AdminField v-model="form.sku" label="销售 SKU" :disabled="detail !== null" :maxlength="80" required/>
          <label class="admin-field">商品类别<select v-model="form.category" aria-label="商品类别" :disabled="detail !== null" @change="chooseCategory"><option v-for="item in categories.slice(1)" :key="item.code" :value="item.code">{{ item.label }}</option></select></label>
          <AdminField v-model="form.name" label="商品名称" :maxlength="180"/><AdminField v-model="form.brand" label="品牌" :maxlength="60"/>
          <AdminField v-model="form.model" label="型号" :maxlength="100"/><AdminField v-if="!detail" v-model="form.price" label="初始售价（元）" :maxlength="13"/>
          <AdminField v-model="form.summary" label="摘要" :maxlength="240"/><AdminField v-model="form.imageUrl" label="图片地址" :maxlength="500"/>
          <AdminField v-model="form.sourceUrl" label="商品来源地址" :maxlength="1000"/><AdminField v-model="form.priceNote" label="售价说明" :maxlength="240"/>
          <div class="admin-wide"><AdminField v-model="form.description" label="描述" multiline :maxlength="10000"/></div>
        </div>
        <legend class="admin-parameters-title">{{ categories.find(c => c.code === form.category)?.label }}专属参数</legend>
        <p class="admin-form-hint">空值表示未核验；标记“上架必需”的字段须补齐，并提供来源地址。功率使用 W，尺寸使用 mm；集合可按 Ctrl 多选。</p>
        <div class="admin-parameter-grid">
          <section v-for="(field, index) in schema" :key="field.code" class="admin-parameter"><header><strong>{{ field.label }}</strong><small v-if="field.required">上架必需</small></header>
            <template v-for="p in [parameters[index]!]" :key="p.code">
              <label class="admin-field"><span class="sr-only">{{ field.label }}{{ p.unit && p.unit !== 'CAPACITY' ? ` · ${p.unit}` : '' }}</span>
                <input v-if="field.type === 'NUMBER' || field.type === 'INTEGER'" type="number" :aria-label="`${field.label}${p.unit ? ` · ${p.unit}` : ''}`" :value="p.value" :step="field.type === 'INTEGER' ? '1' : 'any'" min="0" placeholder="未核验" @input="numeric(p, $event)"/>
                <select v-else-if="field.type === 'BOOLEAN'" :aria-label="field.label" :value="p.value === null ? '' : String(p.value)" @change="boolean(p, $event)"><option value="">未核验</option><option value="true">有</option><option value="false">无</option></select>
                <select v-else-if="field.type === 'SET'" :aria-label="field.label" multiple :value="p.value ?? []" @change="setValue(p, $event)"><option v-for="option in field.options" :key="option" :value="option">{{ option }}</option></select>
                <select v-else-if="field.type === 'TEXT' && field.options.length" :aria-label="field.label" :value="p.value ?? ''" @change="p.value = ($event.target as HTMLSelectElement).value || null"><option value="">未核验</option><option v-for="option in field.options" :key="option" :value="option">{{ option }}</option></select>
                <input v-else-if="field.type === 'TEXT'" :aria-label="field.label" :value="p.value" placeholder="未核验" maxlength="100" @input="p.value = ($event.target as HTMLInputElement).value || null"/>
              </label>
              <div v-if="field.type === 'SSD_SLOTS'" class="admin-slots"><div v-for="(slot, slotIndex) in slots(p)" :key="slotIndex"><input v-model="slot.formFactor" :aria-label="`SSD规格${slotIndex+1}`" placeholder="M.2 2280"/><select v-model="slot.interface" :aria-label="`SSD接口${slotIndex+1}`"><option>PCIe</option><option>SATA</option></select><select v-model="slot.protocol" :aria-label="`SSD协议${slotIndex+1}`"><option>NVMe</option><option>AHCI</option></select><button type="button" @click="p.value = slots(p).filter((_, i) => i !== slotIndex)">移除</button></div><ElButton @click="addSlot(p)">添加有效安装组合</ElButton></div>
              <select v-if="field.unit === 'CAPACITY'" v-model="p.unit" aria-label="原始容量单位"><option v-for="unit in field.options" :key="unit">{{ unit }}</option></select>
              <div v-if="field.type === 'SET'" class="admin-set-actions"><button type="button" @click="p.value = []">明确为空集合</button><button type="button" @click="p.value = null">标记未核验</button><small>{{ p.value === null ? '未核验' : Array.isArray(p.value) && p.value.length === 0 ? '已核验：空集合' : '已选择' }}</small></div>
              <AdminField v-model="p.sourceUrl" :label="`${field.label.replace(/（.*）/, '')}来源`" :maxlength="1000"/><AdminField v-model="p.note" :label="`${field.label.replace(/（.*）/, '')}备注`" :maxlength="1000"/>
            </template>
          </section>
        </div>
      </fieldset>
      <div class="admin-form-actions"><ElButton type="primary" native-type="submit" :loading="busy" :disabled="loading || unauthorized || schema.length === 0">{{ detail ? '保存修改' : '保存草稿' }}</ElButton><ElButton v-if="detail" :type="detail.product.status === 'ON_SALE' ? 'warning' : 'success'" :disabled="busy || loading || unauthorized" @click="changeStatus">{{ detail.product.status === 'ON_SALE' ? '下架商品' : '上架商品' }}</ElButton><ElButton v-if="detail" :disabled="busy" @click="open(detail.product.id)">重新载入</ElButton><span>上下架使用最近一次保存的资料</span></div>
    </form>
  </div>
</template>
