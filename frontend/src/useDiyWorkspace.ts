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
  const count = computed(() => workspace.value.selections.length)

  function change(selections: Selection[]) {
    workspace.value = { version: 1, revision: workspace.value.revision + 1, selections }
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
    change(workspace.value.selections.filter(selection => selection.category !== category))
  }
  function clear() { if (count.value) change([]) }
  function save() {
    storageWarning.value = saveWorkspace(workspace.value)
    return !storageWarning.value
  }
  const revalidate = () => { void quotation.refresh(); void compatibility.refresh() }
  onMounted(() => { revalidate(); window.addEventListener('focus', revalidate) })
  onUnmounted(() => window.removeEventListener('focus', revalidate))
  return { workspace, storageWarning, count, select, remove, clear, save, quotation, compatibility }
}
