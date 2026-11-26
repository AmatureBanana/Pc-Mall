<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{ label?: string; score?: string; max?: number; compact?: boolean; bar?: boolean }>(), { max: 100 })
const stops = [
  { score: 0, rgb: [23, 69, 127] },
  { score: 40, rgb: [9, 104, 250] },
  { score: 70, rgb: [255, 128, 0] },
  { score: 100, rgb: [220, 38, 38] },
]

const scorePercent = computed(() => {
  const value = Number(props.score) / props.max * 100
  return Number.isFinite(value) ? Math.max(0, Math.min(100, value)) : undefined
})
const scoreColor = computed(() => {
  const score = scorePercent.value
  if (score === undefined) return undefined
  const upper = stops.findIndex((stop, index) => index > 0 && score <= stop.score)
  const start = stops[upper - 1]
  const end = stops[upper]
  const fraction = (score - start.score) / (end.score - start.score)
  const rgb = start.rgb.map((channel, index) => Math.round(channel + (end.rgb[index] - channel) * fraction))
  return `rgb(${rgb.join(', ')})`
})
</script>

<template>
  <div class="score-display" :class="{ 'score-display--compact': compact, 'score-display--bar': bar }">
    <small v-if="label">{{ label }}</small>
    <strong v-if="score !== undefined" :style="{ color: scoreColor }">{{ score }} <span>/ {{ max }}{{ compact ? ' 分' : '' }}</span></strong>
    <div v-if="bar && scorePercent !== undefined" class="score-display-track" role="meter" :aria-label="label"
      aria-valuemin="0" :aria-valuemax="max" :aria-valuenow="Number(score)">
      <div class="score-display-fill" :style="{ width: `${scorePercent}%`, backgroundColor: scoreColor }"/>
    </div>
  </div>
</template>

<style scoped>
.score-display small { display: block; margin-bottom: 8px; }
.score-display strong { font-size: 32px; color: #0968fa; white-space: nowrap; }
.score-display strong span { font-size: 16px; font-weight: 500; }
@media (max-width: 500px) { .score-display strong { font-size: 26px; } }
.score-display--compact strong { font-size: 22px; font-variant-numeric: tabular-nums; line-height: 1.2; }
.score-display--compact strong span { font-size: 12px; }
@media (max-width: 500px) { .score-display--compact strong { font-size: 18px; } }
.score-display--bar { display: flex; flex-wrap: wrap; align-items: baseline; justify-content: space-between; gap: 8px; }
.score-display--bar small { margin: 0; font-size: 16px; font-weight: 700; color: #101d36; }
.score-display--bar strong { font-variant-numeric: tabular-nums; line-height: 1.1; }
.score-display--bar strong span { font-size: 13px; color: #536886; }
.score-display-track { flex-basis: 100%; height: 10px; border-radius: 999px; background: #dfe5f0; overflow: hidden; }
.score-display-fill { height: 100%; border-radius: inherit; }
</style>
