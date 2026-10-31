<script setup lang="ts">
defineProps<{ label: string; multiline?: boolean; disabled?: boolean; maxlength?: number; required?: boolean }>()
const value = defineModel<string>({ required: true })
</script>
<template>
  <label class="admin-field"><span>{{ label }}</span>
    <textarea v-if="multiline" v-model="value" :aria-label="label" :disabled="disabled" :maxlength="maxlength" :required="required" rows="3"/>
    <input v-else v-model="value" :aria-label="label" :disabled="disabled" :maxlength="maxlength" :required="required"/>
  </label>
</template>
