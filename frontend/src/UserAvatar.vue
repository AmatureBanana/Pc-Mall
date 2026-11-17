<script setup lang="ts">
import { avatarRevision, sessionAccount } from './auth'
import { computed, ref, watch } from 'vue'
import HardwareIcon from './HardwareIcon.vue'
const failed = ref(false)
const source = computed(() => `/api/account/avatar?v=${sessionAccount.value?.id}-${avatarRevision.value}`)
watch(source, () => { failed.value = false })
</script>
<template>
  <span class="user-avatar"><img v-if="sessionAccount && !failed" :src="source" alt="用户头像" @error="failed = true"/><HardwareIcon v-else name="user"/></span>
</template>
<style scoped>
img { width: 100%; height: 100%; object-fit: cover; border-radius: 50%; }
</style>
