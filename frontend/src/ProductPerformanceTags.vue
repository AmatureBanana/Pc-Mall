<script setup lang="ts">
import { formatPerformanceScore, type PerformanceTag } from './performance'
defineProps<{ tags: PerformanceTag[]; compact?: boolean }>()

function displayLabel(tag: PerformanceTag): string {
  if (tag.kind !== 'SCORE') return tag.label
  // A running backend may still return score labels with two decimal places.
  return tag.label.replace(/ (\d+(?:\.\d+)?)分$/, (_, score: string) => ` ${formatPerformanceScore(score)}分`)
}
</script>

<template>
  <ul class="performance-tags" :class="{ 'performance-tags--compact': compact }" aria-label="性能标签">
    <li v-for="tag in tags" :key="tag.code">
      <a v-if="!compact && tag.sourceUrl && tag.kind !== 'UNVERIFIED'" :class="['performance-tag', tag.kind.toLowerCase()]"
        :href="tag.sourceUrl" :title="tag.note" target="_blank" rel="noopener noreferrer">{{ displayLabel(tag) }}</a>
      <span v-else :class="['performance-tag', tag.kind.toLowerCase()]" :title="tag.note">{{ displayLabel(tag) }}</span>
    </li>
  </ul>
</template>
