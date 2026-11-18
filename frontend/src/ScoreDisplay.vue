<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{ label: string; score?: string }>()
const stops = [
  { score: 0, rgb: [23, 69, 127] },
  { score: 40, rgb: [9, 104, 250] },
  { score: 70, rgb: [255, 128, 0] },
  { score: 100, rgb: [220, 38, 38] },
]

const scoreColor = computed(() => {
  const value = Number(props.score)
  if (!Number.isFinite(value)) return undefined
  const score = Math.max(0, Math.min(100, value))
  const upper = stops.findIndex((stop, index) => index > 0 && score <= stop.score)
  const start = stops[upper - 1]
  const end = stops[upper]
  const fraction = (score - start.score) / (end.score - start.score)
  const rgb = start.rgb.map((channel, index) => Math.round(channel + (end.rgb[index] - channel) * fraction))
  return `rgb(${rgb.join(', ')})`
})
</script>

<template>
  <div class="score-display">
    <small>{{ label }}</small>
    <strong v-if="score !== undefined" :style="{ color: scoreColor }">{{ score }} <span>/ 100</span></strong>
  </div>
</template>

<style scoped>
.score-display small { display: block; margin-bottom: 8px; }
.score-display strong { font-size: 32px; color: #0968fa; white-space: nowrap; }
.score-display strong span { font-size: 16px; font-weight: 500; }
@media (max-width: 500px) { .score-display strong { font-size: 26px; } }
</style>
