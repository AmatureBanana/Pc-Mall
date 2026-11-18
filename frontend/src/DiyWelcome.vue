<script setup lang="ts">
import { ref } from 'vue'
import { ElButton, ElDialog } from 'element-plus'
import HardwareIcon from './HardwareIcon.vue'
import DiyWelcomeArt from './DiyWelcomeArt.vue'
import OfficeRecommendation from './OfficeRecommendation.vue'

const emit = defineEmits<{ 'start-diy': [] }>()
const scenarios = [
  { code: 'office', name: '办公推荐', description: '适合日常办公、学习和轻度应用，稳定高效，安静省心。', tags: ['稳定安静', '性价比', '日常多任务'], action: '开始推荐' },
  { code: 'gaming', name: '游戏推荐', description: '面向游戏与电竞体验，搭配适合你的处理器、显卡与散热。', tags: ['游戏体验', '独立显卡', '散热搭配'], action: '开始推荐' },
  { code: 'creation', name: '创作推荐', description: '面向视频剪辑、3D 渲染、设计创作等高负载工作。', tags: ['多核性能', '大内存', '高速存储'], action: '查看方案' },
] as const
const selectedScenario = ref<(typeof scenarios)[number] | null>(null)
function startDiy() { selectedScenario.value = null; emit('start-diy') }
</script>

<template>
  <main class="diy-welcome page-width" aria-label="DIY 装机推荐">
    <div class="welcome-heading">
      <HardwareIcon name="monitor"/><h1>DIY 电脑装机</h1>
      <p>欢迎来到装机工作区，选择推荐类型快速开始，或手动自定义你的电脑配置</p>
    </div>

    <div class="welcome-panel">
      <section class="welcome-hero" aria-labelledby="welcome-title">
        <div class="welcome-hero-copy">
          <h2 id="welcome-title">你好，欢迎开始你的专属装机之旅！</h2>
          <p>你可以选择适合自己需求的推荐方案（办公、游戏、创作等），也可以手动自由配置，打造理想的电脑。</p>
        </div>
        <DiyWelcomeArt kind="hero" class="welcome-hero-art"/>
      </section>

      <section class="welcome-scenarios" aria-label="选择推荐类型">
        <article v-for="scenario in scenarios" :key="scenario.code" class="welcome-scenario" :aria-labelledby="`scenario-${scenario.code}`">
          <DiyWelcomeArt :kind="scenario.code" class="welcome-scenario-art"/>
          <div class="welcome-scenario-copy">
            <h2 :id="`scenario-${scenario.code}`">{{ scenario.name }}</h2>
            <p>{{ scenario.description }}</p>
            <ul class="welcome-tags" aria-label="用途特点"><li v-for="tag in scenario.tags" :key="tag">{{ tag }}</li></ul>
            <ElButton type="primary" class="welcome-action" :aria-label="scenario.name + ' · ' + scenario.action" @click="selectedScenario = scenario">
              {{ scenario.action }}<span aria-hidden="true">→</span>
            </ElButton>
          </div>
        </article>
      </section>

      <section class="welcome-manual" aria-labelledby="manual-title">
        <div class="welcome-manual-icon"><HardwareIcon name="layers"/></div>
        <div class="welcome-manual-copy"><h2 id="manual-title">想要自己配？</h2><p>按你的需求自由选择 CPU、主板、显卡、内存等硬件。</p></div>
        <ElButton type="primary" plain class="welcome-manual-action" @click="startDiy">开始手动 DIY<span aria-hidden="true">→</span></ElButton>
      </section>

      <p class="welcome-tip"><HardwareIcon name="info"/><span><strong>提示：</strong>推荐方案仅作起点，你仍可在后续步骤中调整配置。</span></p>
    </div>

    <ElDialog :model-value="selectedScenario?.code === 'office'" title="办公推荐" width="680px" class="welcome-office-dialog" @close="selectedScenario = null" destroy-on-close>
      <OfficeRecommendation v-if="selectedScenario?.code === 'office'" @start-diy="startDiy"/>
    </ElDialog>
    <ElDialog :model-value="selectedScenario !== null && selectedScenario.code !== 'office'" :title="selectedScenario?.name" width="460px" class="welcome-dialog" @close="selectedScenario = null">
      <p class="welcome-availability" role="status">{{ selectedScenario?.name }}即将开放，你可以先进入 DIY 工作区自由搭配硬件。</p>
      <template #footer><ElButton @click="selectedScenario = null">返回选择</ElButton><ElButton type="primary" @click="startDiy">开始手动 DIY</ElButton></template>
    </ElDialog>
  </main>
</template>

<style scoped>
.diy-welcome { padding-bottom: 36px; color: #101d36; }
.welcome-heading { min-height: 78px; display: flex; align-items: center; gap: 20px; }
.welcome-heading > svg { width: 40px; height: 40px; color: var(--blue); flex-shrink: 0; margin-left: 14px; }
.welcome-heading h1 { margin: 0; font-size: 28px; font-weight: 750; white-space: nowrap; }
.welcome-heading p { padding-left: 24px; margin: 0 0 0 4px; border-left: 1px solid #c5d7f3; font-size: 16px; line-height: 1.7; color: #455977; }
.welcome-panel { padding: 28px 24px 24px; border: 1px solid #dce7f9; border-radius: 20px; background: #ffffffbf; box-shadow: 0 3px 16px #214f9310; }
.welcome-hero { min-height: 200px; border-radius: 20px; background: linear-gradient(110deg, #eaf2ff 0%, #e5efff 52%, #cfe0ff 100%); position: relative; overflow: hidden; display: flex; align-items: center; padding: 34px 60px; isolation: isolate; }
.welcome-hero::after { content: ''; position: absolute; inset: auto -5% -150px 28%; height: 200px; border-radius: 50%; background: #ffffff45; transform: rotate(-10deg); z-index: -1; }
.welcome-hero-copy { position: relative; z-index: 1; max-width: calc(100% - 260px); }
.welcome-hero h2 { font-size: clamp(26px, 2.35vw, 36px); font-weight: 800; line-height: 1.45; margin: 0 0 12px; letter-spacing: .3px; color: #0b1730; }
.welcome-hero p { margin: 0; font-size: 18px; line-height: 1.9; color: #3d5173; }
.welcome-hero-art { position: absolute; width: 350px; height: 230px; right: 35px; bottom: -5px; }
.welcome-scenarios { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 18px; margin-top: 24px; }
.welcome-scenario { display: grid; grid-template-columns: 42% minmax(0, 1fr); align-items: center; min-height: 264px; padding: 26px 20px 26px 8px; border: 1px solid #dae5f8; border-radius: 16px; background: linear-gradient(145deg, #fff, #f9fbff); transition: border-color .18s, box-shadow .18s; }
.welcome-scenario:hover { border-color: #9cbff7; box-shadow: 0 5px 18px #2367bf0c; }
.welcome-scenario-art { width: 100%; min-width: 0; height: 206px; }
.welcome-scenario-copy { min-width: 0; }
.welcome-scenario h2 { font-size: 26px; margin: 0 0 9px; font-weight: 750; line-height: 1.4; }
.welcome-scenario p { margin: 0; color: #425473; font-size: 15px; line-height: 1.65; min-height: 50px; }
.welcome-tags { display: flex; gap: 5px; flex-wrap: wrap; padding: 0; margin: 18px 0 22px; list-style: none; }
.welcome-tags li { background: #eaf1ff; border: 1px solid #e5edfc; border-radius: 10px; color: #415a82; padding: 5px 8px; font-size: 13px; white-space: nowrap; }
.welcome-action.el-button { width: min(100%, 212px); height: 44px; border-radius: 9px; font-size: 17px; font-weight: 650; }
.welcome-action span, .welcome-manual-action span { margin-left: 18px; font-size: 27px; line-height: 1; font-weight: 400; }
.welcome-manual { display: flex; align-items: center; min-height: 132px; margin-top: 18px; padding: 24px 40px; gap: 32px; border: 1px solid #dce7f9; border-radius: 16px; background: linear-gradient(120deg, #fff, #f8faff); }
.welcome-manual-icon { flex-shrink: 0; display: grid; place-items: center; width: 94px; height: 78px; border-radius: 16px; background: #eaf2ff; color: var(--blue); }
.welcome-manual-icon svg { width: 50px; height: 50px; }
.welcome-manual-copy { border-left: 1px solid #d7e3f8; padding-left: 32px; }
.welcome-manual h2 { margin: 0 0 6px; font-size: 28px; font-weight: 750; }
.welcome-manual p { margin: 0; font-size: 18px; line-height: 1.7; color: #445574; }
.welcome-manual-action.el-button { margin-left: auto; flex-shrink: 0; height: 56px; padding-inline: 36px; border-radius: 9px; background: #f5f9ff; font-size: 20px; font-weight: 650; }
.welcome-manual-action.el-button:hover { background: var(--blue); }
.welcome-tip { display: flex; align-items: center; gap: 12px; min-height: 50px; padding: 12px 24px; border-radius: 15px; margin: 20px 0 0; background: linear-gradient(100deg, #eff5ff, #e5eeff); color: #445574; font-size: 15px; line-height: 1.8; }
.welcome-tip svg { width: 21px; height: 21px; color: var(--blue); flex-shrink: 0; }
.welcome-tip strong { margin-right: 5px; }
.welcome-availability { margin: 4px 0 12px; line-height: 1.9; color: #455977; }
@media (max-width: 1350px) {
  .welcome-hero { padding-inline: 36px; }
  .welcome-hero-art { right: 0; width: 300px; }
  .welcome-hero p { font-size: 16px; }
  .welcome-scenario { grid-template-columns: 35% minmax(0, 1fr); padding-right: 16px; }
  .welcome-scenario h2 { font-size: 23px; }
  .welcome-tags li { padding-inline: 6px; font-size: 12px; }
  .welcome-manual { padding-inline: 28px; gap: 22px; }
  .welcome-manual-copy { padding-left: 22px; }
  .welcome-manual p { font-size: 16px; }
}
@media (max-width: 1050px) {
  .welcome-heading { flex-wrap: wrap; padding-block: 18px; gap: 12px; }
  .welcome-heading p { flex-basis: 100%; border: 0; padding: 0; margin: 0 0 0 14px; font-size: 14px; }
  .welcome-scenario { display: flex; flex-direction: column; padding: 16px 20px 24px; }
  .welcome-scenario-art { height: 150px; }
  .welcome-scenario-copy { width: 100%; }
  .welcome-tags { margin-block: 14px 18px; }
  .welcome-action.el-button { width: 100%; }
  .welcome-manual-icon { width: 70px; height: 70px; }
  .welcome-manual h2 { font-size: 24px; }
  .welcome-manual-action.el-button { padding-inline: 20px; font-size: 17px; }
}
@media (max-width: 700px) {
  .diy-welcome { padding-bottom: 20px; }
  .welcome-heading > svg { width: 30px; height: 30px; margin-left: 4px; }
  .welcome-heading h1 { font-size: 23px; }
  .welcome-heading p { margin-left: 4px; }
  .welcome-panel { padding: 14px; border-radius: 15px; }
  .welcome-hero { padding: 24px; min-height: 235px; border-radius: 12px; align-items: flex-start; }
  .welcome-hero-copy { max-width: 100%; }
  .welcome-hero h2 { font-size: 24px; margin-bottom: 8px; }
  .welcome-hero p { font-size: 14px; padding-right: 38px; line-height: 1.8; }
  .welcome-hero-art { width: 140px; height: 98px; right: -10px; bottom: -4px; opacity: .6; }
  .welcome-scenarios { grid-template-columns: 1fr; gap: 12px; margin-top: 14px; }
  .welcome-scenario { display: grid; grid-template-columns: 35% minmax(0, 1fr); min-height: 225px; padding: 20px 16px 20px 0; }
  .welcome-scenario-art { height: 165px; }
  .welcome-scenario h2 { font-size: 23px; }
  .welcome-scenario p { font-size: 14px; }
  .welcome-tags { gap: 4px; margin-block: 12px 14px; }
  .welcome-tags li { font-size: 11px; padding: 4px 5px; border-radius: 7px; }
  .welcome-action.el-button { font-size: 15px; height: 40px; }
  .welcome-manual { flex-wrap: wrap; gap: 14px; padding: 20px 16px; margin-top: 14px; }
  .welcome-manual-icon { width: 54px; height: 58px; border-radius: 12px; }
  .welcome-manual-icon svg { width: 36px; height: 36px; }
  .welcome-manual-copy { flex: 1; border: 0; padding: 0; }
  .welcome-manual h2 { font-size: 23px; }
  .welcome-manual p { font-size: 14px; }
  .welcome-manual-action.el-button { width: 100%; height: 46px; }
  .welcome-tip { padding: 12px 14px; margin-top: 14px; font-size: 13px; align-items: flex-start; border-radius: 10px; }
  .welcome-tip svg { margin-top: 2px; }
}
@media (prefers-reduced-motion: reduce) { .welcome-scenario { transition: none; } }
</style>

<style>
/* Element Plus teleports dialogs outside this component's scoped DOM. */
.welcome-office-dialog.el-dialog { width: min(680px, calc(100% - 24px)); margin-block: 5vh; }
.welcome-office-dialog .el-dialog__body { max-height: 75vh; overflow-y: auto; padding-right: 8px; }
</style>
