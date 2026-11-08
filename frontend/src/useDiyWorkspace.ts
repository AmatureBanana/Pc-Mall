import { computed, onMounted, onUnmounted, ref } from 'vue'
import type { Product } from './catalog'
import { loadCompatibility, loadQuote, restoreWorkspace, saveWorkspace, type HardwareCategory, type Selection } from './diy'
import { useCatalogRequest } from './useCatalogRequest'

export function useDiyWorkspace() {
  const restored = restoreWorkspace()
  const workspace = ref(restored.workspace)
  const storageWarning = ref(restored.warning)
  const quotation = useCatalogRequest(signal => loadQuote(workspace.value, signal), '参考报价')
  const compatibility = useCatalogRequest(signal => loadCompatibility(workspace.value, signal), '兼容检测')
  const count = computed(() => workspace.value.selections.length + (workspace.value.integratedGraphicsCpuId ? 1 : 0))

  function change(selections: Selection[], integratedGraphicsCpuId = workspace.value.integratedGraphicsCpuId) {
    const cpu = selections.find(item => item.category === 'CPU')
    workspace.value = { version: 1, revision: workspace.value.revision + 1, selections,
      ...(integratedGraphicsCpuId && cpu?.productId === integratedGraphicsCpuId && !selections.some(item => item.category === 'GPU')
        ? { integratedGraphicsCpuId } : {}) }
    storageWarning.value = saveWorkspace(workspace.value)
    void quotation.refresh()
    void compatibility.refresh()
  }
  function select(product: Product) {
    if (product.stock < 1 || workspace.value.selections.some(selection => selection.category === product.category && selection.productId === product.id)) return
    change([...workspace.value.selections.filter(selection => selection.category !== product.category),
      { category: product.category, productId: product.id, source: 'DIY' }])
  }
  function remove(category: HardwareCategory) {
    change(workspace.value.selections.filter(selection => selection.category !== category), category === 'GPU' ? 0 : workspace.value.integratedGraphicsCpuId)
  }
  function selectIntegratedGraphics(cpuId: number) {
    if (workspace.value.selections.find(item => item.category === 'CPU')?.productId !== cpuId
      || workspace.value.integratedGraphicsCpuId === cpuId) return
    change(workspace.value.selections.filter(item => item.category !== 'GPU'), cpuId)
  }
  function clear() { if (count.value) change([]) }
  function save() {
    storageWarning.value = saveWorkspace(workspace.value)
    return !storageWarning.value
  }
  const revalidate = () => { void quotation.refresh(); void compatibility.refresh() }
  let refreshTimer: number | undefined
  const onVisible = () => { if (document.visibilityState === 'visible') revalidate() }
  onMounted(() => {
    revalidate()
    window.addEventListener('focus', revalidate)
    document.addEventListener('visibilitychange', onVisible)
    refreshTimer = window.setInterval(onVisible, 30_000)
  })
  onUnmounted(() => {
    window.removeEventListener('focus', revalidate)
    document.removeEventListener('visibilitychange', onVisible)
    window.clearInterval(refreshTimer)
  })
  return { workspace, storageWarning, count, select, selectIntegratedGraphics, remove, clear, save, quotation, compatibility }
}
