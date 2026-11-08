import { ref } from 'vue'
import { parseSeen, readSeen, type SeenSnapshot } from './diy'

/**
 * Whether the DIY workspace is on screen. The unified sign-in lives in the account bar, so the
 * workspace publishes this so a successful sign-in can come back to the configuration it left.
 */
export const workspaceActive = ref(false)

/**
 * Where the unified sign-in was opened from. Opening it from the workspace must return there on
 * success or cancel instead of dropping the user on the storefront.
 */
export const signInOrigin = ref<'diy' | null>(null)

/**
 * What the user saw when they left the workspace to sign in. The workspace stays mounted behind the
 * login dialog and keeps revalidating, so its diagnostic snapshot is overwritten while the dialog is
 * open; this frozen copy is the only honest "before" for the restoration comparison.
 */
// undefined means not frozen; null means the user had no comparable snapshot when sign-in opened.
export const seenBaseline = ref<SeenSnapshot | null | undefined>(undefined)

const signInKey = 'pc-mall.diy.sign-in.v1'

/** Only navigation and diagnostic data; storage failure leaves the in-memory flow usable. */
export function beginWorkspaceSignIn(fromWorkspace: boolean) {
  endWorkspaceSignIn()
  if (!fromWorkspace) return
  signInOrigin.value = 'diy'
  seenBaseline.value = readSeen()
  try {
    sessionStorage.setItem(signInKey, JSON.stringify({ version: 1, origin: 'diy', baseline: seenBaseline.value }))
  } catch { /* The active page can still complete the flow. */ }
}

export function endWorkspaceSignIn(keepBaseline = false) {
  signInOrigin.value = null
  if (!keepBaseline) seenBaseline.value = undefined
  try { sessionStorage.removeItem(signInKey) } catch { /* Navigation must remain usable. */ }
}

// A reload inside the same auth flow keeps the return target; other routes discard stale context.
try {
  const raw = sessionStorage.getItem(signInKey)
  if (window.location.hash === '#login' || window.location.hash === '#register') {
    const stored = raw === null ? null : JSON.parse(raw)
    if (stored?.version === 1 && stored.origin === 'diy' && Object.hasOwn(stored, 'baseline')) {
      signInOrigin.value = 'diy'
      seenBaseline.value = parseSeen(JSON.stringify(stored.baseline))
    } else endWorkspaceSignIn()
  } else endWorkspaceSignIn()
} catch { endWorkspaceSignIn() }

/** Set when a sign-in that started from the workspace succeeded; the workspace consumes it. */
export const restorationPending = ref(false)
