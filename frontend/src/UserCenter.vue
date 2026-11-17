<script setup lang="ts">
import HardwareIcon from './HardwareIcon.vue'
import UserAvatar from './UserAvatar.vue'
import { sessionAccount } from './auth'
import { accountNavigation } from './accountNavigation'

defineProps<{ section: string }>()
</script>

<template>
  <div class="user-center">
    <aside class="user-center-sidebar" aria-label="用户中心">
      <div class="user-center-identity"><UserAvatar/>
        <div><strong :title="sessionAccount?.nickname">{{ sessionAccount ? `用户 ${sessionAccount.nickname || sessionAccount.username}` : '欢迎来到装机猫' }}</strong>
          <small>{{ sessionAccount?.role === 'ADMIN' ? '管理员' : sessionAccount ? '普通用户' : '登录后管理你的账户' }}</small></div>
      </div>
      <nav aria-label="用户中心导航"><a v-for="entry in accountNavigation" :key="entry.route" :href="`#${entry.route}`"
        :class="{ active: section === entry.route }" :aria-current="section === entry.route ? 'page' : undefined">
        <HardwareIcon :name="entry.icon"/>{{ entry.label }}
      </a></nav>
      <a href="#" class="user-center-back"><HardwareIcon name="chevron-right"/>返回商城</a>
    </aside>
    <div class="user-center-content"><slot/></div>
  </div>
</template>

<style scoped>
.user-center { display: grid; grid-template-columns: 225px minmax(0, 1fr); gap: 17px; width: min(1680px, calc(100% - 44px)); margin: 20px auto 24px; flex: 1; align-items: stretch; min-height: calc(100vh - 150px); }
.user-center-sidebar { display: flex; flex-direction: column; padding: 23px 14px; background: #fff; border: 1px solid #edf2fc; border-radius: 17px; box-shadow: 0 2px 12px #265bb006; }
.user-center-identity { display: flex; gap: 13px; align-items: center; padding: 0 8px 26px; min-width: 0; }
.user-center-identity > div { min-width: 0; }
strong { display: block; max-width: 130px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 16px; color: #111b31; }
small { display: block; color: #75839d; font-size: 12px; margin-top: 5px; }
nav { display: grid; gap: 8px; }
nav a { display: flex; align-items: center; gap: 16px; padding: 14px 15px; border-radius: 9px; position: relative; font-weight: 550; font-size: 15px; color: #172444; }
nav svg { width: 24px; height: 24px; flex: none; }
nav a:hover { background: #f5f8ff; }
nav a.active { background: linear-gradient(100deg, #e4eeff, #eaf1ff); color: #0867ff; font-weight: 700; }
nav a.active::before { content: ''; position: absolute; left: 0; width: 2px; top: 6px; bottom: 6px; border-radius: 3px; background: #1767ff; }
.user-center-back { display: flex; gap: 9px; align-items: center; margin: auto 12px 0; padding-top: 36px; font-size: 12px; color: #8090aa; }
.user-center-back svg { width: 15px; height: 15px; transform: rotate(180deg); }
.user-center-content { min-width: 0; }
@media (max-width: 1000px) { .user-center { grid-template-columns: 185px minmax(0,1fr); width: calc(100% - 32px); gap: 14px; } .user-center-sidebar { padding-inline: 10px; } .user-center-identity { gap: 9px; padding-inline: 4px; } strong { max-width: 105px; font-size: 14px; } nav a { padding-inline: 10px; gap: 10px; font-size: 14px; } }
@media (max-width: 700px) { .user-center { display: block; width: calc(100% - 24px); margin-top: 12px; } .user-center-sidebar { padding: 10px; margin-bottom: 18px; } .user-center-identity, .user-center-back { display: none; } nav { grid-template-columns: repeat(4,1fr); gap: 3px; } nav a { flex-direction: column; justify-content: center; gap: 6px; padding: 10px 2px; font-size: 11px; } nav a.active::before { display: none; } nav svg { width: 20px; height: 20px; } }
</style>
