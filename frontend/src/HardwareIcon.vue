<script setup lang="ts">
defineProps<{ name: string }>()
</script>
<template>
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
    <g v-if="name === 'grid'"><rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/><rect x="3" y="14" width="7" height="7" rx="1"/><rect x="14" y="14" width="7" height="7" rx="1"/></g>
    <g v-else-if="name === 'cpu'"><rect x="5" y="5" width="14" height="14" rx="2"/><rect x="8" y="8" width="8" height="8" rx=".6"/><path d="M8 2v3m4-3v3m4-3v3M8 19v3m4-3v3m4-3v3M2 8h3m-3 4h3m-3 4h3m14-8h3m-3 4h3m-3 4h3"/></g>
    <g v-else-if="name === 'board'"><rect x="4" y="2" width="16" height="20" rx="1"/><rect x="7" y="5" width="6" height="6"/><path d="M16 5v8M7 15h10M7 18h10M7 21v-9"/></g>
    <g v-else-if="name === 'gpu'"><path d="M2 4v16m0-3h19V6H2m3 11v3m3-3v2m11-2v2"/><circle cx="8" cy="11.5" r="3"/><circle cx="16" cy="11.5" r="3"/></g>
    <g v-else-if="name === 'memory'"><rect x="2" y="6" width="20" height="11" rx="1"/><path d="M5 9v5m4-5v5m6-5v5m4-5v5M5 17v3m3-3v3m3-3v3m3-3v3m3-3v3m3-3v3"/></g>
    <g v-else-if="name === 'ssd' || name === 'case'"><rect x="5" y="2" width="14" height="20" rx="2"/><path d="M8 5h8M8 8h8"/><circle cx="15.5" cy="18.5" r=".7"/></g>
    <g v-else-if="name === 'psu' || name === 'cooler'"><rect x="3" y="3" width="18" height="18" rx="2"/><circle cx="12" cy="12" r="6"/><circle cx="12" cy="12" r="1.4"/><path d="m12 6 2 4m4 2-4 2m-2 4-2-4m-4-2 4-2"/></g>
    <g v-else-if="name === 'search'"><circle cx="10.5" cy="10.5" r="6.5"/><path d="m16 16 5 5"/></g>
    <g v-else-if="name === 'check'"><path d="m5 12 4 4L19 6"/></g>
    <g v-else><circle cx="12" cy="12" r="9"/><path d="M12 7v6m0 4h.01"/></g>
  </svg>
</template>
