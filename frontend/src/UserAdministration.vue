<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { ElButton, ElPagination, ElTable, ElTableColumn, ElTag } from 'element-plus'
import { AuthError } from './auth'
import { changeUserStatus, userDetail, userPage, type AdminUser, type UserPage, type UserStatus } from './adminUsers'

const emit = defineEmits<{ expired: [] }>()
const username = ref('')
const status = ref('')
const page = ref<UserPage>({ items: [], total: 0, page: 1, size: 10 })
const detail = ref<AdminUser | null>(null)
const editingId = ref<number | null>(null)
const pendingStatus = ref<UserStatus | null>(null)
const loading = ref(false)
const busy = ref(false)
const unauthorized = ref(false)
const error = ref('')
const notice = ref('')
let revision = 0

function feedback(cause: unknown) {
  error.value = cause instanceof Error ? cause.message : '用户数据暂时无法获取，请重试。'
  if (cause instanceof AuthError && (cause.status === 401 || cause.status === 403)) {
    unauthorized.value = true
    if (cause.status === 401) emit('expired')
  }
}
async function loadPage(number = 1) {
  if (busy.value) return
  const current = ++revision
  loading.value = true; error.value = ''; notice.value = ''; pendingStatus.value = null
  editingId.value = null; detail.value = null
  try {
    const query = new URLSearchParams({ username: username.value.trim(), page: String(number), size: '10' })
    if (status.value) query.set('status', status.value)
    const result = await userPage(query)
    if (current === revision) { page.value = result; unauthorized.value = false }
  } catch (cause) {
    if (current === revision) { page.value.items = []; feedback(cause) }
  } finally { if (current === revision) loading.value = false }
}
async function open(id: number) {
  if (busy.value) return
  const current = ++revision
  editingId.value = id; detail.value = null; pendingStatus.value = null
  loading.value = true; error.value = ''; notice.value = ''
  try {
    const result = await userDetail(id)
    if (current === revision) { detail.value = result; unauthorized.value = false }
  } catch (cause) { if (current === revision) feedback(cause) }
  finally { if (current === revision) loading.value = false }
}
async function changeStatus() {
  if (!detail.value || !pendingStatus.value || busy.value || loading.value || unauthorized.value) return
  const current = ++revision
  const target = pendingStatus.value
  busy.value = true; error.value = ''; notice.value = ''
  try {
    const result = await changeUserStatus(detail.value, target)
    if (current !== revision) return
    detail.value = result
    notice.value = target === 'DISABLED' ? '账户已禁用，旧会话已失效。' : '账户已启用，请用户重新登录。'
  } catch (cause) { if (current === revision) feedback(cause) }
  finally { if (current === revision) { busy.value = false; pendingStatus.value = null } }
}
function reload() { editingId.value === null ? void loadPage(page.value.page) : void open(editingId.value) }
function login() { window.location.hash = 'login' }
function label(value: UserStatus) { return value === 'ACTIVE' ? '已启用' : '已禁用' }
onMounted(() => void loadPage())
onUnmounted(() => { revision++ })
</script>

<template>
  <div class="catalog-admin user-admin" :aria-busy="loading || busy">
    <div class="admin-section-head"><div><h2>用户管理</h2><p>查询普通用户，管理账户启用与禁用状态</p></div>
      <ElButton v-if="editingId !== null" :disabled="busy" @click="loadPage()">返回用户列表</ElButton></div>
    <p v-if="error" role="alert" class="auth-error">{{ error }} <button v-if="unauthorized" @click="login">前往登录</button></p>
    <p v-if="notice" role="status" class="auth-success">{{ notice }}</p>
    <p v-if="loading" role="status">正在加载用户数据…</p>
    <template v-if="editingId === null">
      <form class="admin-filters" @submit.prevent="loadPage()">
        <label>用户名<input v-model="username" aria-label="查询用户名" placeholder="按用户名搜索" maxlength="20" :disabled="loading"/></label>
        <label>账户状态<select v-model="status" aria-label="筛选账户状态" :disabled="loading"><option value="">全部状态</option><option value="ACTIVE">已启用</option><option value="DISABLED">已禁用</option></select></label>
        <ElButton native-type="submit" :disabled="loading">查询</ElButton><ElButton :disabled="loading" @click="reload">重新载入</ElButton>
      </form>
      <ElTable :data="page.items" :empty-text="error ? '用户数据加载失败，请重新载入' : '没有符合条件的普通用户'" stripe>
        <ElTableColumn prop="id" label="ID" width="80"/><ElTableColumn prop="username" label="用户名" min-width="180"/>
        <ElTableColumn prop="nickname" label="昵称" min-width="150"/>
        <ElTableColumn label="状态" width="100"><template #default="scope"><ElTag :type="scope.row.status === 'ACTIVE' ? 'success' : 'info'">{{ label(scope.row.status) }}</ElTag></template></ElTableColumn>
        <ElTableColumn label="操作" width="100"><template #default="scope"><ElButton link type="primary" :disabled="loading || unauthorized" @click="open(scope.row.id)">查看用户</ElButton></template></ElTableColumn>
      </ElTable>
      <ElPagination :current-page="page.page" :page-size="page.size" :total="page.total" layout="total, prev, pager, next" :disabled="loading || unauthorized" @current-change="loadPage"/>
    </template>
    <template v-else>
      <template v-if="detail">
        <dl class="profile-identity user-identity">
          <div><dt>用户 ID</dt><dd>{{ detail.id }}</dd></div><div><dt>用户名</dt><dd>{{ detail.username }}</dd></div>
          <div><dt>昵称</dt><dd>{{ detail.nickname }}</dd></div><div><dt>角色</dt><dd>普通用户 USER</dd></div>
          <div><dt>账户状态</dt><dd><ElTag :type="detail.status === 'ACTIVE' ? 'success' : 'info'">{{ label(detail.status) }}</ElTag></dd></div>
          <div><dt>注册时间</dt><dd>{{ detail.createdAt.replace('T', ' ') }}</dd></div><div><dt>更新时间</dt><dd>{{ detail.updatedAt.replace('T', ' ') }}</dd></div>
        </dl>
        <p class="admin-form-hint">启禁保留账户资料和历史记录。禁用后将拒绝登录及受保护请求，重新启用需要用户重新登录。</p>
        <div v-if="pendingStatus" role="group" aria-label="确认账户状态变更" class="user-status-confirmation">
          <p>确认{{ pendingStatus === 'DISABLED' ? '禁用' : '启用' }} {{ detail.username }}？{{ pendingStatus === 'DISABLED' ? '该用户的既有会话将失效。' : '该用户可重新登录。' }}</p>
          <ElButton :type="pendingStatus === 'DISABLED' ? 'warning' : 'success'" :loading="busy" :disabled="loading || unauthorized" @click="changeStatus">{{ pendingStatus === 'DISABLED' ? '确认禁用' : '确认启用' }}</ElButton>
          <ElButton :disabled="busy" @click="pendingStatus = null">取消</ElButton>
        </div>
        <ElButton v-else :type="detail.status === 'ACTIVE' ? 'warning' : 'success'" :disabled="loading || busy || unauthorized" @click="pendingStatus = detail.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'">{{ detail.status === 'ACTIVE' ? '禁用账户' : '启用账户' }}</ElButton>
      </template>
      <ElButton :disabled="busy || loading" @click="reload">重新载入</ElButton>
    </template>
  </div>
</template>
