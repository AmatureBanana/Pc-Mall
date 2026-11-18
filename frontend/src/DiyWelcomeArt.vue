<script setup lang="ts">
import { useId } from 'vue'
defineProps<{ kind: 'hero' | 'office' | 'gaming' | 'creation' }>()
// Several illustrations share the page, so SVG paint servers need instance-specific IDs.
const id = useId()
const paint = (name: string) => `url(#${id}-${name})`
</script>

<template>
  <svg v-if="kind === 'hero'" viewBox="0 0 350 230" fill="none" aria-hidden="true">
    <defs>
      <linearGradient :id="`${id}-glass`" x1="90" y1="10" x2="270" y2="180" gradientUnits="userSpaceOnUse"><stop stop-color="#fff" stop-opacity=".95"/><stop offset=".42" stop-color="#a6d5ff" stop-opacity=".72"/><stop offset="1" stop-color="#4d9aff" stop-opacity=".8"/></linearGradient>
      <linearGradient :id="`${id}-base`" x1="90" y1="150" x2="260" y2="220" gradientUnits="userSpaceOnUse"><stop stop-color="#d4e8ff"/><stop offset=".5" stop-color="#9fc7ff"/><stop offset="1" stop-color="#4c8fff"/></linearGradient>
      <linearGradient :id="`${id}-side`"><stop stop-color="#e8f6ff" stop-opacity=".7"/><stop offset="1" stop-color="#a4d3ff" stop-opacity=".15"/></linearGradient>
      <filter :id="`${id}-shadow`" x="-40%" y="-40%" width="180%" height="200%"><feDropShadow dx="0" dy="14" stdDeviation="10" flood-color="#4678db" flood-opacity=".2"/></filter>
    </defs>
    <ellipse cx="194" cy="214" rx="130" ry="10" fill="#a4c4fc" opacity=".4"/>
    <g :filter="paint('shadow')">
      <path d="m59 185 110-30 116 25-2 27-112 22-112-24z" :fill="paint('base')"/>
      <path d="m59 183 110-28 116 25-114 26z" fill="#eff7ff"/>
      <path d="m59 183 112 23 112-26" stroke="white" stroke-width="2"/>
      <path d="m116 39 60-22a14 14 0 0 1 10 0l59 22a12 12 0 0 1 8 12l-3 84a12 12 0 0 1-7 10l-57 27a13 13 0 0 1-11 0l-58-26a12 12 0 0 1-7-11l-2-83a13 13 0 0 1 8-13z" :fill="paint('glass')" stroke="#f2fbff" stroke-width="2"/>
      <path d="m111 45 68 25 71-25M179 70v100" stroke="#fff" stroke-opacity=".65" stroke-width="2"/>
      <path d="m111 45 68 25v100l-62-26z" :fill="paint('side')"/>
      <g transform="translate(135 78) scale(1.24)"><path fill="#0670ff" d="M24 1 45 13v23L24 48 3 36V13z"/><path fill="#e3f4ff" d="m24 9 13 7-13 7-13-7zm-13 13 10 6v13l-10-6zm16 6 10-6v13l-10 6z"/></g>
    </g>
    <path d="M248 59c69-11 65 14 8 41-64 30-148 42-159 29-5-6 2-13 13-20" stroke="white" stroke-width="3" stroke-linecap="round" opacity=".9"/>
    <path d="m267 43 2-6 2 6 6 2-6 2-2 6-2-6-6-2z" fill="white" opacity=".8"/>
  </svg>

  <svg v-else-if="kind === 'gaming'" viewBox="75 0 155 185" aria-hidden="true">
    <image href="/images/case.svg" width="300" height="180"/>
  </svg>

  <svg v-else viewBox="0 0 220 220" fill="none" aria-hidden="true">
    <defs>
      <linearGradient :id="`${id}-body`" x1="30" y1="25" x2="190" y2="170" gradientUnits="userSpaceOnUse"><stop stop-color="#5d6d85"/><stop offset=".5" stop-color="#1c2a3e"/><stop offset="1" stop-color="#52637b"/></linearGradient>
      <linearGradient :id="`${id}-screen`" x1="38" y1="40" x2="169" y2="142" gradientUnits="userSpaceOnUse"><stop stop-color="#c4eaff"/><stop offset=".48" stop-color="#6db7f3"/><stop offset="1" stop-color="#1c65cd"/></linearGradient>
      <linearGradient :id="`${id}-ribbon`" x1="66" y1="50" x2="152" y2="129" gradientUnits="userSpaceOnUse"><stop stop-color="#b6edff"/><stop offset=".45" stop-color="#1690e6"/><stop offset="1" stop-color="#0849bb"/></linearGradient>
      <linearGradient :id="`${id}-metal`" x1="75" y1="145" x2="138" y2="196" gradientUnits="userSpaceOnUse"><stop stop-color="#dbe6f5"/><stop offset=".5" stop-color="#a9bace"/><stop offset="1" stop-color="#eaf3ff"/></linearGradient>
      <filter :id="`${id}-shadow`" x="-30%" y="-30%" width="160%" height="170%"><feDropShadow dx="0" dy="6" stdDeviation="4" flood-color="#3b5682" flood-opacity=".18"/></filter>
    </defs>
    <ellipse cx="111" cy="195" rx="95" ry="11" fill="#d9e7fc" opacity=".5"/>
    <g :filter="paint('shadow')">
      <path d="m102 145 18 1 2 29 22 9-38 9-27-9 24-9z" :fill="paint('metal')" stroke="#a9bbd3"/>
      <path d="m31 29 153 11a5 5 0 0 1 5 5v109a5 5 0 0 1-5 5L30 147a6 6 0 0 1-5-6V35a6 6 0 0 1 6-6z" :fill="paint('body')"/>
      <path d="m32 35 151 11v102L32 137z" :fill="paint('screen')"/>
      <g v-if="kind === 'office'">
        <path d="M66 119c-15-21 19-28 33-40 17-15 9-27 29-28 18 0 31 20 17 34-15 15-33 12-43 25-7 10 17 7 23 18-25 4-43 6-59-9z" :fill="paint('ribbon')"/>
        <path d="M71 130c16-24 80-23 86-47 4-17-5-26-17-29 24 7 34 25 27 43-9 23-45 24-40 39z" fill="#236ccf" opacity=".85"/>
        <path d="M58 103c13-4 33-15 40-28 7-11 10-26 24-25" stroke="#d7f6ff" stroke-width="2" opacity=".65"/>
      </g>
      <g v-else>
        <path d="m32 35 151 11v102L32 137z" fill="#162338"/>
        <path d="m40 47 128 9v59l-128-9z" fill="#86bfdc"/>
        <path d="m40 90 36-25 24 21 19-14 49 34v9l-128-9z" fill="#416d8b"/>
        <path d="m76 65 10 9-10-2-12 5z" fill="#d6edff"/>
        <path d="m40 97 39-12 30 11 29-6 30 19v6l-128-9z" fill="#213d56"/>
        <path d="m81 92 12 6-8 9 12 3 9-12" stroke="#88c8e8" stroke-width="3"/>
        <path d="m40 113 128 9v19l-128-9z" fill="#24344c"/>
        <path d="m43 117 38 3m3 0 33 3m3 0 45 3" stroke="#c08af3" stroke-width="3"/>
        <path d="m43 122 17 1m3 1 45 3m3 0 54 4" stroke="#58b3d7" stroke-width="3"/>
        <path d="m43 127 56 4m3 0 28 2m3 0 32 2" stroke="#66c3ad" stroke-width="3"/>
        <path d="m172 56 7 1m-7 6 7 1m-7 6 7 1m-7 6 7 1m-7 6 7 1" stroke="#8c9db9" stroke-width="2"/>
        <circle cx="176" cy="99" r="5" stroke="#9d8cfa" stroke-width="2"/>
      </g>
      <path d="m28 142 158 12" stroke="#7e92af"/>
    </g>
    <g v-if="kind === 'office'">
      <path d="m50 184 74-7 35 12-75 13z" fill="#e9f0fa" stroke="#a6bad1"/>
      <path d="m60 186 62-6m-53 10 65-7m-55 11 64-8" stroke="#a6bad1" stroke-width="2" stroke-dasharray="4 2"/>
      <path d="m167 177 12-1c9 0 17 9 12 13l-13 3c-6-1-19-11-11-15z" fill="#dbe7f7" stroke="#afc1d7"/>
      <path d="m178 177 2 5" stroke="#93aac9"/>
      <path d="m20 167 22 2-1 27-19-2z" fill="#a6cef8"/>
      <path d="m26 172-5-26m11 27 2-29m4 29 6-22" stroke="#2785de" stroke-width="3" stroke-linecap="round"/>
    </g>
  </svg>
</template>
