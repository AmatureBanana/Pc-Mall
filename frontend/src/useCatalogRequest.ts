import { onUnmounted, ref, shallowRef } from 'vue'
import { ProductRequestError } from './catalog'

/** Shared catalog request policy: timeout, cancellation and stale-response protection. */
export function useCatalogRequest<T>(load: (signal: AbortSignal) => Promise<T>, subject: string) {
  const data = shallowRef<T>()
  const status = ref<'loading' | 'ready' | 'error'>('loading')
  const error = ref('')
  const retryable = ref(true)
  let activeRequest: AbortController | undefined

  async function refresh() {
    activeRequest?.abort()
    const controller = new AbortController()
    activeRequest = controller
    data.value = undefined
    status.value = 'loading'
    const timeout = window.setTimeout(() => controller.abort(), 8000)
    try {
      const result = await load(controller.signal)
      if (controller !== activeRequest) return
      data.value = result
      status.value = 'ready'
    } catch (cause) {
      if (controller !== activeRequest) return
      error.value = cause instanceof DOMException && cause.name === 'AbortError'
        ? `${subject}连接超时，请检查连接后重试。`
        : cause instanceof Error ? cause.message : `${subject}加载失败，请稍后重试。`
      retryable.value = !(cause instanceof ProductRequestError) || cause.retryable
      status.value = 'error'
    } finally { window.clearTimeout(timeout) }
  }
  onUnmounted(() => { activeRequest?.abort(); activeRequest = undefined })
  return { data, status, error, retryable, refresh }
}
