import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import type { Product } from './catalog'
import {
  describeRestoration, loadCompatibility, loadQuote, maxDiyMemoryQuantity, readSeen, rememberSeen, restoreWorkspace, saveWorkspace,
  type HardwareCategory, type Restoration, type Selection,
} from './diy'
import { useCatalogRequest } from './useCatalogRequest'
import { workspaceActive, restorationPending, seenBaseline } from './workspaceSession'

export type RestorationState =
  | { phase: 'idle' }
  | { phase: 'running'; revision: number }
  | { phase: 'ready'; revision: number; restoration: Restoration; restored: number }
  | { phase: 'error'; revision: number; error: string }
  | { phase: 'superseded'; revision: number }

export function useDiyWorkspace() {
  const restored = restoreWorkspace()
  const workspace = ref(restored.workspace)
  const storageWarning = ref(restored.warning)
  const quotation = useCatalogRequest(signal => loadQuote(workspace.value, signal), '参考报价')
  const compatibility = useCatalogRequest(signal => loadCompatibility(workspace.value, signal), '兼容检测')
  const restoration = ref<RestorationState>({ phase: 'idle' })
  let restoreAttempt = 0
  const count = computed(() => workspace.value.selections.length + (workspace.value.integratedGraphicsCpuId ? 1 : 0))

  function change(selections: Selection[], integratedGraphicsCpuId = workspace.value.integratedGraphicsCpuId,
    memoryQuantity = workspace.value.memoryQuantity ?? 1) {
    const cpu = selections.find(item => item.category === 'CPU')
    workspace.value = { version: 1, revision: workspace.value.revision + 1, selections,
      ...(memoryQuantity > 1 && selections.some(item => item.category === 'MEMORY') ? { memoryQuantity } : {}),
      ...(integratedGraphicsCpuId && cpu?.productId === integratedGraphicsCpuId && !selections.some(item => item.category === 'GPU')
        ? { integratedGraphicsCpuId } : {}) }
    storageWarning.value = saveWorkspace(workspace.value)
    void quotation.refresh()
    void compatibility.refresh()
  }
  const memoryQuantity = computed(() => workspace.value.memoryQuantity ?? 1)
  const memorySlots = computed(() => compatibility.data.value?.memorySlots ?? null)
  const memoryLimit = computed(() => {
    const memory = quotation.data.value?.items.find(item => item.category === 'MEMORY')
    const board = quotation.data.value?.items.find(item => item.category === 'MOTHERBOARD')
    if (board?.state !== 'VALID' || !memory?.product
      || !['VALID', 'OUT_OF_STOCK'].includes(memory.state)) return 1
    return Math.max(1, Math.min(maxDiyMemoryQuantity, memorySlots.value ?? 1, memory.product.stock))
  })
  function setMemoryQuantity(quantity: number) {
    if (!workspace.value.selections.some(item => item.category === 'MEMORY')
      || !Number.isInteger(quantity) || quantity < 1 || quantity === memoryQuantity.value
      || (quantity > memoryQuantity.value && quantity > memoryLimit.value)) return
    change(workspace.value.selections, workspace.value.integratedGraphicsCpuId, quantity)
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
  // The active restoration already performs both authoritative reads. Background events must not
  // abort its requests; edits still refresh immediately and supersede the old restoration.
  const revalidate = () => {
    if (restoration.value.phase === 'running') return
    void quotation.refresh(); void compatibility.refresh()
  }

  /**
   * Re-reads the current catalog for an existing workspace. Nothing is cached and nothing is added:
   * the accepted answer is whichever the server returns for the revision on screen right now.
   * A frozen baseline taken when the user left for sign-in is used when available, because the
   * workspace keeps revalidating behind the login dialog and would otherwise overwrite it.
   */
  async function restore() {
    const revision = workspace.value.revision
    if (seenBaseline.value === undefined) seenBaseline.value = readSeen()
    const seen = seenBaseline.value
    // Only the newest attempt may publish a result; a superseded attempt must not raise a spurious error.
    const attempt = ++restoreAttempt
    restoration.value = { phase: 'running', revision }
    await Promise.allSettled([quotation.refresh(), compatibility.refresh()])
    // A newer edit while the restoration was in flight owns the screen; an old answer must not win.
    if (attempt !== restoreAttempt || workspace.value.revision !== revision
      || restoration.value.phase !== 'running' || restoration.value.revision !== revision) return
    const failed = quotation.status.value !== 'ready' || compatibility.status.value !== 'ready'
    if (failed || !quotation.data.value || !compatibility.data.value
      || quotation.data.value.revision !== revision || compatibility.data.value.revision !== revision) {
      restoration.value = { phase: 'error', revision,
        error: '登录后重验暂不可用，配置引用仍保留；请重试刷新价格与兼容检测。' }
      return
    }
    restoration.value = { phase: 'ready', revision,
      restoration: describeRestoration(seen, quotation.data.value, compatibility.data.value),
      restored: quotation.data.value.items.length }
    seenBaseline.value = undefined
  }
  function dismissRestoration() { restoration.value = { phase: 'idle' }; seenBaseline.value = undefined }

  watch([quotation.data, compatibility.data], ([nextQuote, nextCheck]) => {
    if (nextQuote && nextCheck && nextQuote.revision === nextCheck.revision && nextQuote.revision === workspace.value.revision) {
      rememberSeen(nextQuote, nextCheck)
    }
  })
  // A restoration summary or error belongs to one revision; a newer edit replaces it with its own state.
  watch(() => workspace.value.revision, (next, previous) => {
    if (next <= previous) return
    seenBaseline.value = undefined
    if (restoration.value.phase === 'running') restoration.value = { phase: 'superseded', revision: restoration.value.revision }
    else if (restoration.value.phase === 'ready' || restoration.value.phase === 'error') restoration.value = { phase: 'idle' }
  })

  let refreshTimer: number | undefined
  const onVisible = () => { if (document.visibilityState === 'visible') revalidate() }
  watch(restorationPending, pending => {
    if (!pending) return
    restorationPending.value = false
    void restore()
  })
  onMounted(() => {
    workspaceActive.value = true
    revalidate()
    if (restorationPending.value) { restorationPending.value = false; void restore() }
    window.addEventListener('focus', revalidate)
    document.addEventListener('visibilitychange', onVisible)
    refreshTimer = window.setInterval(onVisible, 30_000)
  })
  onUnmounted(() => {
    workspaceActive.value = false
    window.removeEventListener('focus', revalidate)
    document.removeEventListener('visibilitychange', onVisible)
    window.clearInterval(refreshTimer)
  })
  return { workspace, storageWarning, count, select, selectIntegratedGraphics, remove, clear,
    memoryQuantity, memorySlots, memoryLimit, setMemoryQuantity,
    quotation, compatibility, restoration, restore, dismissRestoration }
}
