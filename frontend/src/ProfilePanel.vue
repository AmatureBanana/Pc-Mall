<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { ElButton, ElDialog } from 'element-plus'
import { AuthError, changePassword, currentProfile, updateNickname, type Account } from './auth'

const emit = defineEmits<{
  updated: [account: Account]
  expired: []
  passwordChanged: []
  busy: [value: boolean]
}>()
const profile = ref<Account | null>(null)
const nickname = ref('')
const oldPassword = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
const loading = ref(true)
const working = ref('')
const error = ref('')
const notice = ref('')
let active = true

function navigate(target = '') { window.location.hash = target }
function clearSecrets() { oldPassword.value = newPassword.value = confirmPassword.value = '' }
function feedback(cause: unknown) {
  error.value = cause instanceof Error ? cause.message : '操作未完成，请稍后重试。'
  if (cause instanceof AuthError && cause.status === 401) {
    profile.value = null
    clearSecrets()
    emit('expired')
  }
}
async function load() {
  loading.value = true
  error.value = ''
  try {
    const value = await currentProfile()
    if (!active) return
    profile.value = value
    nickname.value = value.nickname
    emit('updated', value)
  } catch (cause) { if (active) feedback(cause) }
  finally { if (active) loading.value = false }
}
function begin(action: string) {
  if (working.value) return false
  working.value = action
  error.value = notice.value = ''
  emit('busy', true)
  return true
}
function finish() { working.value = ''; emit('busy', false) }
async function saveNickname() {
  if (!begin('nickname')) return
  try {
    if (Array.from(nickname.value).length > 30) throw new Error('昵称最多 30 个字符。')
    const value = await updateNickname(nickname.value)
    if (!active) return
    profile.value = value
    nickname.value = value.nickname
    notice.value = '昵称已保存。'
    emit('updated', value)
  } catch (cause) { if (active) feedback(cause) }
  finally { if (active) finish() }
}
async function savePassword() {
  if (!begin('password')) return
  try {
    if (newPassword.value !== confirmPassword.value) throw new Error('两次输入的新密码不一致。')
    await changePassword(oldPassword.value, newPassword.value, confirmPassword.value)
    clearSecrets()
    if (active) emit('passwordChanged')
  } catch (cause) { if (active) feedback(cause) }
  finally { if (active) finish() }
}
onMounted(load)
onUnmounted(() => { active = false; clearSecrets(); emit('busy', false) })
</script>

<template>
  <ElDialog :model-value="true" title="个人资料与账户安全" width="760px" class="profile-dialog" :close-on-click-modal="false" :show-close="!working" :close-on-press-escape="!working" @close="navigate()">
    <p class="auth-intro">管理你的显示昵称与登录密码。</p>
    <p v-if="loading" role="status">正在读取个人资料…</p>
    <p v-if="error" role="alert" class="auth-error">{{ error }}</p>
    <p v-if="notice" role="status" class="auth-success">{{ notice }}</p>
    <template v-if="profile && !loading">
      <dl class="profile-identity"><div><dt>账户 ID</dt><dd>{{ profile.id }}</dd></div><div><dt>用户名</dt><dd>{{ profile.username }}</dd></div><div><dt>账户角色</dt><dd>{{ profile.role === 'ADMIN' ? '管理员' : '普通用户' }}</dd></div></dl>
      <div class="profile-sections">
        <section><h2>个人昵称</h2><p>昵称会显示在商城顶部，留空时使用用户名。</p>
          <form class="auth-form" @submit.prevent="saveNickname"><fieldset :disabled="!!working">
            <label>昵称<input v-model="nickname" aria-label="昵称" aria-describedby="nickname-help" name="nickname" autocomplete="nickname"/><small id="nickname-help">最多 30 个字符 · 当前 {{ Array.from(nickname).length }}/30</small></label>
            <ElButton type="primary" native-type="submit" :loading="working === 'nickname'">保存昵称</ElButton>
          </fieldset></form>
        </section>
        <section><h2>修改密码</h2><p>修改成功后，所有旧会话将失效，需要重新登录。</p>
          <form class="auth-form" @submit.prevent="savePassword"><fieldset :disabled="!!working">
            <label>旧密码<input v-model="oldPassword" aria-label="旧密码" type="password" autocomplete="current-password" required/></label>
            <label>新密码<input v-model="newPassword" aria-label="新密码" aria-describedby="new-password-help" type="password" autocomplete="new-password" required/><small id="new-password-help">8～64 个字符，UTF-8 编码不超过 72 字节</small></label>
            <label>确认新密码<input v-model="confirmPassword" aria-label="确认新密码" type="password" autocomplete="new-password" required/></label>
            <ElButton type="primary" native-type="submit" :loading="working === 'password'">修改密码并重新登录</ElButton>
          </fieldset></form>
        </section>
      </div>
    </template>
    <div v-if="!profile && !loading" class="profile-actions"><ElButton type="primary" @click="navigate('login')">前往登录</ElButton><ElButton @click="load">重试</ElButton></div>
  </ElDialog>
</template>
