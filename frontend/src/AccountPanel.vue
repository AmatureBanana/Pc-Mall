<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { ElButton, ElDialog } from 'element-plus'
import { adminModules, AuthError, currentAccount, loginAccount, sessionAccount, signedIn, writeAuth, type Account, type AdminModule } from './auth'
import HardwareIcon from './HardwareIcon.vue'
import UserAvatar from './UserAvatar.vue'
import { accountNavigation } from './accountNavigation'
import CatalogAdministration from './CatalogAdministration.vue'
import UserAdministration from './UserAdministration.vue'
import { useCart } from './cart'
import { beginWorkspaceSignIn, endWorkspaceSignIn, signInOrigin, seenBaseline, restorationPending, workspaceActive } from './workspaceSession'

const user = sessionAccount
const menuOpen = ref(false)
const menuElement = ref<HTMLElement | null>(null)
const menuTrigger = ref<HTMLButtonElement | null>(null)
const route = ref('')
const username = ref('')
const nickname = ref('')
const password = ref('')
const confirmPassword = ref('')
const busy = ref(false)
const error = ref('')
const notice = ref('')
const sessionError = ref('')
const modules = ref<AdminModule[]>([])
const adminLoading = ref(false)
const profileBusy = ref(false)
const { clear: clearCart, refresh: refreshCart } = useCart()
const selectedModule = computed(() => modules.value.find(item => item.code === route.value.split('/')[1]))
const authOpen = computed(() => route.value === 'login' || route.value === 'register')
const adminOpen = computed(() => route.value === 'admin' || route.value.startsWith('admin/'))
let identityRevision = 0
let adminRevision = 0
let authFlowRevision = 0

function navigate(target = '') { window.location.hash = target }
function dismissMenu(event: PointerEvent) {
  if (!menuElement.value?.contains(event.target as Node)) menuOpen.value = false
}
function escapeMenu(event: KeyboardEvent) {
  if (event.key === 'Escape' && menuOpen.value) { menuOpen.value = false; menuTrigger.value?.focus() }
}
/** Remembers that this sign-in started in the workspace and freezes what the user had seen. */
function openAuth(target: 'login' | 'register') {
  authFlowRevision++
  beginWorkspaceSignIn(workspaceActive.value)
  navigate(target)
}
/** Cancelling or failing sends the user back to where they opened the unified login. */
function closeAuth() {
  const returnTo = signInOrigin.value === 'diy' ? 'diy' : ''
  authFlowRevision++
  endWorkspaceSignIn()
  navigate(returnTo)
}
function syncRoute() {
  const next = window.location.hash.slice(1)
  if (authOpen.value && next !== 'login' && next !== 'register') {
    authFlowRevision++
    // Success explicitly transfers the baseline to the workspace before leaving the auth route.
    if (signInOrigin.value !== null) endWorkspaceSignIn()
  }
  route.value = next
}
function clearSecrets() { password.value = confirmPassword.value = '' }
function feedback(cause: unknown) {
  error.value = cause instanceof Error ? cause.message : '操作未完成，请稍后重试。'
  if (cause instanceof AuthError && cause.status === 401) { identityRevision++; user.value = null; signedIn.value = false; clearCart() }
}
async function checkSession() {
  if (busy.value || profileBusy.value) return
  const revision = ++identityRevision
  try {
    const account = await currentAccount()
    if (revision !== identityRevision) return
    if (user.value?.id !== account.id) clearCart()
    user.value = account
    signedIn.value = true
    sessionError.value = ''
    void refreshCart()
  }
  catch (cause) {
    if (revision !== identityRevision) return
    if (cause instanceof AuthError && cause.status === 401) {
      if (user.value) sessionError.value = '登录已过期，请重新登录。'
      user.value = null
      signedIn.value = false
      clearCart()
    } else sessionError.value = '账户状态暂时无法获取，请重试。'
  }
}
async function loadAdmin() {
  const revision = ++adminRevision
  modules.value = []
  error.value = ''
  adminLoading.value = true
  try {
    const result = await adminModules()
    if (revision === adminRevision && adminOpen.value) modules.value = result
  }
  catch (cause) { if (revision === adminRevision && adminOpen.value) feedback(cause) }
  finally { if (revision === adminRevision) adminLoading.value = false }
}
async function submit() {
  if (busy.value) return
  const flow = authFlowRevision
  const origin = signInOrigin.value
  const baseline = seenBaseline.value
  identityRevision++
  busy.value = true
  error.value = ''
  notice.value = ''
  try {
    if (route.value === 'register') {
      if (password.value !== confirmPassword.value) throw new Error('两次输入的密码不一致。')
      await writeAuth('register', { username: username.value, nickname: nickname.value, password: password.value, confirmPassword: confirmPassword.value })
      clearSecrets()
      if (flow !== authFlowRevision || !authOpen.value) return
      notice.value = '注册成功，请登录。'
      navigate('login')
    } else {
      const account = await loginAccount(username.value, password.value)
      if (user.value?.id !== account.id) clearCart()
      user.value = account
      signedIn.value = true
      clearSecrets()
      sessionError.value = ''
      void refreshCart()
      // Closing or navigating away cannot cancel a session already created by the server. Accept
      // that identity, but a stale response must not navigate over the user's current destination.
      if (flow !== authFlowRevision || !authOpen.value) {
        if (origin === 'diy' && user.value.role === 'USER' && workspaceActive.value) {
          seenBaseline.value = baseline
          restorationPending.value = true
        }
        return
      }
      // A sign-in that started in the workspace returns there and revalidates the same references.
      // An administrator is still sent to the administration surface, matching the existing contract.
      if (origin === 'diy' && user.value.role === 'USER') {
        endWorkspaceSignIn(true)
        restorationPending.value = true
        navigate('diy')
      } else if (user.value.role === 'ADMIN') {
        endWorkspaceSignIn()
        navigate('admin')
      } else {
        endWorkspaceSignIn()
        navigate('')
      }
      // The frozen baseline is deliberately kept: the workspace consumes it when it restores.
    }
  } catch (cause) { if (flow === authFlowRevision && authOpen.value) feedback(cause) }
  finally { busy.value = false }
}
async function logout() {
  if (busy.value || profileBusy.value) return
  identityRevision++
  busy.value = true
  sessionError.value = ''
  try { await writeAuth('logout', {}); user.value = null; signedIn.value = false; clearCart(); navigate(); notice.value = '已退出登录。' }
  catch (cause) { sessionError.value = cause instanceof Error ? cause.message : '退出失败，请重试。' }
  finally { busy.value = false }
}
function profileUpdated(account: Account) {
  identityRevision++
  user.value = account
  sessionError.value = ''
}
function profileExpired() { identityRevision++; user.value = null; signedIn.value = false; clearCart() }
function passwordChanged() {
  profileExpired()
  clearSecrets()
  sessionError.value = ''
  navigate('login')
  notice.value = '密码修改成功，旧会话已失效，请重新登录。'
}
watch(route, (next, previous) => {
  menuOpen.value = false
  clearSecrets()
  error.value = ''
  if (!(['register', 'profile', 'security'].includes(previous) && next === 'login')) notice.value = ''
  if (next === 'admin' || next.startsWith('admin/')) void loadAdmin()
  else adminRevision++
})
function setProfileBusy(value: boolean) { profileBusy.value = value }
defineExpose({ profileUpdated, profileExpired, passwordChanged, setProfileBusy })
onMounted(() => { syncRoute(); void checkSession(); window.addEventListener('hashchange', syncRoute); window.addEventListener('focus', checkSession); document.addEventListener('pointerdown', dismissMenu); document.addEventListener('keydown', escapeMenu) })
onUnmounted(() => { window.removeEventListener('hashchange', syncRoute); window.removeEventListener('focus', checkSession); document.removeEventListener('pointerdown', dismissMenu); document.removeEventListener('keydown', escapeMenu) })
</script>

<template>
  <div class="account-bar"><div class="page-width account-bar-inner">
    <span class="account-welcome">欢迎来到装机猫</span>
    <template v-if="!user"><button @click="openAuth('login')">登录</button><button @click="openAuth('register')">注册</button></template>
    <button v-if="user?.role === 'ADMIN'" @click="navigate('admin')">管理后台</button>
    <button v-if="user" :disabled="busy || profileBusy" @click="logout">退出登录</button>
    <span v-if="sessionError" role="alert" class="account-session-error">{{ sessionError }} <button @click="checkSession">重试</button></span>
    <span v-else-if="notice && !authOpen" role="status">{{ notice }}</span>
  </div></div>

  <Teleport to="#header-account" defer>
    <div v-if="user" ref="menuElement" class="user-menu">
      <button ref="menuTrigger" type="button" class="user-menu-trigger" aria-label="用户菜单" :aria-expanded="menuOpen" aria-controls="user-menu-links" @click="menuOpen = !menuOpen">
        <UserAvatar/><span class="account-name" :title="user.nickname">用户 {{ user.nickname || user.username }}</span><HardwareIcon name="chevron-down"/>
      </button>
      <nav v-if="menuOpen" id="user-menu-links" class="user-menu-links" aria-label="用户功能">
        <a v-for="entry in accountNavigation" :key="entry.route" :href="`#${entry.route}`" @click="menuOpen = false"><HardwareIcon :name="entry.icon"/>{{ entry.label }}</a>
      </nav>
    </div>
  </Teleport>

  <ElDialog :model-value="authOpen" :title="route === 'register' ? '注册账户' : '统一登录'" width="440px" class="auth-dialog" :close-on-click-modal="false" @close="authOpen && closeAuth()">
    <p class="auth-intro">{{ route === 'register' ? '创建普通账户，开始你的装机之旅。' : '普通用户与管理员使用同一入口。' }}</p>
    <p v-if="notice" role="status" class="auth-success">{{ notice }}</p>
    <p v-if="error" role="alert" class="auth-error">{{ error }}</p>
    <form class="auth-form" @submit.prevent="submit">
      <label>用户名<input v-model="username" aria-label="用户名" aria-describedby="username-help" name="username" autocomplete="username" required pattern="[A-Za-z0-9_]{4,20}" maxlength="20" :disabled="busy"/><small id="username-help">4～20 位英文字母、数字或下划线，不区分大小写</small></label>
      <label v-if="route === 'register'">昵称（选填）<input v-model="nickname" name="nickname" autocomplete="nickname" :disabled="busy"/></label>
      <label>密码<input v-model="password" aria-label="密码" aria-describedby="password-help" type="password" name="password" :autocomplete="route === 'register' ? 'new-password' : 'current-password'" required :disabled="busy"/><small id="password-help">8～64 个字符，UTF-8 编码不超过 72 字节</small></label>
      <label v-if="route === 'register'">确认密码<input v-model="confirmPassword" type="password" name="confirmPassword" autocomplete="new-password" required :disabled="busy"/></label>
      <ElButton type="primary" native-type="submit" :loading="busy">{{ route === 'register' ? '创建账户' : '登录' }}</ElButton>
    </form>
    <p class="auth-switch"><button :disabled="busy" @click="navigate(route === 'register' ? 'login' : 'register')">{{ route === 'register' ? '已有账户？去登录' : '还没有账户？去注册' }}</button></p>
  </ElDialog>

  <ElDialog :model-value="adminOpen" title="管理后台" :width="selectedModule?.code === 'products' || selectedModule?.code === 'users' ? '1260px' : '900px'" class="admin-dialog" @close="adminOpen && navigate()">
    <p v-if="adminLoading" role="status">正在验证后台权限…</p>
    <div v-else-if="error" class="admin-feedback"><p role="alert" class="auth-error">{{ error }}</p><ElButton v-if="!user" type="primary" @click="navigate('login')">前往登录</ElButton><ElButton @click="loadAdmin">重试</ElButton><ElButton @click="navigate()">返回商城</ElButton></div>
    <div v-else class="admin-layout">
      <nav aria-label="后台模块"><button :class="{ active: !selectedModule }" @click="navigate('admin')">后台概览</button><button v-for="item in modules" :key="item.code" :class="{ active: selectedModule?.code === item.code }" @click="navigate(`admin/${item.code}`)">{{ item.title }}</button></nav>
      <section class="admin-content"><CatalogAdministration v-if="selectedModule?.code === 'products'" @expired="profileExpired"/><UserAdministration v-else-if="selectedModule?.code === 'users'" @expired="profileExpired"/><template v-else-if="selectedModule"><h2>{{ selectedModule.title }}</h2><p>{{ selectedModule.description }}</p><p role="status" class="admin-availability">{{ selectedModule.availability }}</p><ElButton @click="navigate('admin')">返回概览</ElButton></template><template v-else><h2>商城管理中心</h2><p>请选择需要维护的模块。</p><div class="admin-module-grid"><button v-for="item in modules" :key="item.code" @click="navigate(`admin/${item.code}`)"><strong>{{ item.title }}</strong><span>{{ item.description }}</span></button></div></template></section>
    </div>
  </ElDialog>
</template>
