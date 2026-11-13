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
    <g v-else-if="name === 'ssd'"><path d="M3 9h17v1h2v1h-2v2h2v2H3v-1a2 2 0 0 0 0-4z"/><rect x="7" y="10.5" width="3" height="3" rx=".4"/><rect x="12" y="10.5" width="3" height="3" rx=".4"/><path d="M18 10.5v3"/></g>
    <g v-else-if="name === 'case'"><rect x="5" y="2" width="14" height="20" rx="2"/><path d="M8 5h8M8 8h8"/><circle cx="15.5" cy="18.5" r=".7"/></g>
    <g v-else-if="name === 'psu' || name === 'cooler'"><rect x="3" y="3" width="18" height="18" rx="2"/><circle cx="12" cy="12" r="6"/><circle cx="12" cy="12" r="1.4"/><path d="m12 6 2 4m4 2-4 2m-2 4-2-4m-4-2 4-2"/></g>
    <g v-else-if="name === 'monitor'"><rect x="2" y="3" width="20" height="14" rx="1.5"/><path d="M12 17v4m-5 0h10"/></g>
    <g v-else-if="name === 'trash'"><path d="M3 6h18M9 6V3h6v3M6 6l1 15h10l1-15M10 10v7m4-7v7"/></g>
    <g v-else-if="name === 'replace'"><path d="M4 8h15m-4-4 4 4-4 4M20 16H5m4-4-4 4 4 4"/></g>
    <g v-else-if="name === 'tools'"><path d="m5 3 4 4-2 2-4-4c-2 5 2 8 6 6l9 10 3-3-10-9c2-4-1-8-6-6ZM4 20l5-5m6-9 5-4 2 2-4 5"/></g>
    <g v-else-if="name === 'search'"><circle cx="10.5" cy="10.5" r="6.5"/><path d="m16 16 5 5"/></g>
    <g v-else-if="name === 'shield'"><path d="m12 2 8 3v6c0 5-8 11-8 11S4 16 4 11V5z"/><path d="m8 11 3 3 5-6"/></g>
    <g v-else-if="name === 'bulb'"><path d="M8 17c0-3-4-4-4-8a8 8 0 0 1 16 0c0 4-4 5-4 8M9 20h6m-5 3h4M8 17h8M1 9H0m24 0h-1M4 2 2 0m18 2 2-2"/></g>
    <g v-else-if="name === 'reset'"><path d="M3 10a9 9 0 1 1 1 7M3 4v6h6"/></g>
    <g v-else-if="name === 'check'"><path d="m5 12 4 4L19 6"/></g>
    <g v-else-if="name === 'layers'"><path d="m12 3 9 5-9 5-9-5 9-5ZM3 12l9 5 9-5M3 16l9 5 9-5"/></g>
    <g v-else-if="name === 'cart'"><path d="M2 3h3l3 13h11l3-10H6"/><circle cx="9" cy="20" r="1"/><circle cx="19" cy="20" r="1"/></g>
    <g v-else-if="name === 'card'"><rect x="2" y="4" width="20" height="16" rx="3"/><path d="M2 9h20M6 15h4"/></g>
    <g v-else-if="name === 'chevron-right'"><path d="m9 5 7 7-7 7"/></g>
    <g v-else><circle cx="12" cy="12" r="9"/><path d="M12 7v6m0 4h.01"/></g>
  </svg>
</template>
