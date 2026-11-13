import { expect, test, type APIResponse, type Page } from '@playwright/test'

const configuration = (page: Page) => page.getByRole('region', { name: '当前配置' })
const diagnostics = (page: Page) => page.getByRole('region', { name: '兼容分项诊断' })
const restoration = (page: Page) => page.getByRole('region', { name: '登录恢复' })
const categories = (page: Page) => page.getByRole('navigation', { name: 'DIY 硬件分类' })
const PASSWORD = 'RestorePassword123!'
const WORKSPACE = 'pc-mall.diy.workspace.v1'
const SEEN = 'pc-mall.diy.seen.v1'

const choices = [
  ['CPU', 'Intel Core i5-14600K'], ['主板', '华硕 TUF B760M-PLUS WIFI'],
  ['内存', '金士顿 FURY Beast 16GB'], ['硬盘', '三星 990 PRO 1TB'],
  ['电源', '海盗船 RM750e 全模组电源'], ['机箱', '九州风神 CC560 中塔机箱'],
  ['CPU散热器', '九州风神 AK400 风冷散热器'],
]

const cartPattern = /^\/api\/cart(\/|$)/
const orderPattern = /^\/api\/orders(\/|$)/

/**
 * Records any cart or order write so a restoration can prove it has no purchase side effect.
 * The task 13 header badge legitimately reads GET /api/cart after sign-in; a read is not a purchase
 * side effect, so only writes and every order call are counted as traffic here.
 */
function watchPurchaseTraffic(page: Page) {
  const traffic: string[] = []
  page.on('request', event => {
    const path = new URL(event.url()).pathname
    const write = !['GET', 'HEAD'].includes(event.method())
    if (orderPattern.test(path) || cartPattern.test(path) && write) traffic.push(event.url())
  })
  return traffic
}

/** Records every cart call, reads included, for the separate "no cart row was created" evidence. */
function watchCartTraffic(page: Page) {
  const traffic: string[] = []
  page.on('request', event => {
    if (cartPattern.test(new URL(event.url()).pathname)) traffic.push(event.url())
  })
  return traffic
}

async function csrfToken(api: import('@playwright/test').APIRequestContext) {
  const body = await (await api.get('/api/auth/csrf')).json()
  return body.data as { token: string; headerName: string }
}

async function registerUser(page: Page) {
  const { request } = await import('@playwright/test')
  const api = await request.newContext({ baseURL: new URL(page.url()).origin })
  try {
    const token = await csrfToken(api)
    const username = `rs${Date.now().toString(36)}`
    const nickname = `恢复用户${username.slice(-4)}`
    const response = await api.post('/api/auth/register', {
      data: { username, password: PASSWORD, confirmPassword: PASSWORD, nickname },
      headers: { [token.headerName]: token.token },
    })
    expect(response.status()).toBe(201)
    return { username, nickname, password: PASSWORD }
  } finally { await api.dispose() }
}

async function openSignIn(page: Page) {
  await page.getByRole('button', { name: '登录', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '统一登录' })
  await expect(dialog).toBeVisible()
  return dialog
}

async function signIn(page: Page, credentials: { username: string; password: string }) {
  const dialog = await openSignIn(page)
  await dialog.getByLabel('用户名', { exact: true }).fill(credentials.username)
  await dialog.getByLabel('密码', { exact: true }).fill(credentials.password)
  await dialog.getByRole('button', { name: '登录', exact: true }).click()
}

async function workshopSnapshot(page: Page) {
  return page.evaluate(key => sessionStorage.getItem(key), WORKSPACE)
}

/** Waits for the last-seen diagnostic snapshot written after both quote and check succeeded. */
async function waitForSeenSnapshot(page: Page, revision: number) {
  const handle = await page.waitForFunction(
    ([key, expected]) => {
      const raw = sessionStorage.getItem(key as string)
      if (raw === null) return null
      try { return JSON.parse(raw).revision === expected ? raw : null } catch { return null }
    },
    [SEEN, revision] as [string, number],
  )
  return JSON.parse((await handle.jsonValue()) as string)
}

async function adminApi(page: Page) {
  const { request } = await import('@playwright/test')
  const username = process.env.BROWSER_ADMIN_USERNAME
  const password = process.env.BROWSER_ADMIN_PASSWORD
  if (!username || !password) throw new Error('Use scripts/test.ps1 to start an isolated server with ephemeral administrator credentials.')
  const api = await request.newContext({ baseURL: new URL(page.url()).origin })
  const token = await csrfToken(api)
  const login = await api.post('/api/auth/login', { data: { username, password }, headers: { [token.headerName]: token.token } })
  expect(login.status()).toBe(200)
  return {
    api,
    async post(path: string, data: unknown): Promise<APIResponse> {
      const fresh = await csrfToken(api)
      return api.post(path, { data, headers: { [fresh.headerName]: fresh.token } })
    },
    async detail(path: string) {
      return (await (await api.get(path)).json()).data
    },
    async dispose() { await api.dispose() },
  }
}

/** A real, selectable PSU fixture: restoration diagnostics need a product the admin can change. */
async function createPowerSupply(admin: Awaited<ReturnType<typeof adminApi>>) {
  const suffix = `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 5)}`
  const sku = `WEB12-${suffix}`
  const name = `登录恢复测试电源 ${suffix}`
  const response = await admin.post('/api/admin/products', {
    sku, category: 'PSU', name, brand: '测试品牌', model: 'Restore-750',
    summary: '750W 登录恢复夹具', description: '游客登录恢复自动化专用', imageUrl: '/images/psu.svg',
    sourceUrl: 'https://example.com/psu', price: '100.00',
    parameters: [{ code: 'ratedPower', value: 750, unit: 'W', sourceUrl: 'https://example.com/psu' }],
  })
  expect(response.status()).toBe(201)
  const id: number = (await response.json()).data.product.id
  const path = `/api/admin/products/${id}`
  expect((await admin.post(`${path}/status`, { revision: 0, status: 'ON_SALE' })).status()).toBe(200)
  expect((await admin.post(`${path}/stock`, { delta: 5, reason: '登录恢复夹具备货' })).status()).toBe(200)
  return { id, name, path }
}

/** Always returns the fixture to OFF_SALE so storefront counts and later specs stay stable. */
async function retire(admin: Awaited<ReturnType<typeof adminApi>>, path: string) {
  try {
    const current = await admin.detail(path)
    if (current.product.status === 'ON_SALE') {
      await admin.post(`${path}/status`, { revision: current.revision, status: 'OFF_SALE' })
    }
  } finally { await admin.dispose() }
}

async function selectAll(page: Page) {
  for (const [category, name] of choices) {
    await categories(page).getByRole('button', { name: new RegExp(`^${category} `) }).click()
    await page.getByRole('button', { name: `选择 ${name}`, exact: true }).click()
    if (category === 'CPU') {
      await categories(page).getByRole('button', { name: /^显卡 / }).click()
      await page.getByRole('button', { name: '选择 CPU 核显', exact: true }).click()
    }
  }
}

test('a guest configuration returns after sign-in with authoritative revalidation and no cart side effect', async ({ page }) => {
  const purchases = watchPurchaseTraffic(page)
  const cartCalls = watchCartTraffic(page)
  await page.goto('/#diy')
  await selectAll(page)
  await expect(diagnostics(page)).toContainText('核心检测通过')
  const total = await configuration(page).getByLabel('当前参考总价').innerText()
  await waitForSeenSnapshot(page, 8)
  const saved = await workshopSnapshot(page)

  const credentials = await registerUser(page)
  await signIn(page, credentials)

  await expect(page).toHaveURL(/#diy$/)
  await expect(page.getByText(credentials.nickname)).toBeVisible()
  const banner = restoration(page)
  await expect(banner).toBeVisible()
  await expect(banner).toContainText('已恢复登录前的工作区')
  await expect(banner).toContainText('商品、价格、库存与参数均为服务端当前数据')
  await expect(banner).toContainText('本次恢复不会自动加入购物车或下单')
  await expect(banner).toContainText('当前数据与登录前所见一致')

  await expect(configuration(page)).toContainText('已选择 8 / 8 项')
  await expect(diagnostics(page)).toContainText('核心检测通过')
  await expect(configuration(page).getByLabel('当前参考总价')).toHaveText(total)
  // The restored references, their DIY source and the revision are exactly the guest's own workspace.
  expect(await workshopSnapshot(page)).toBe(saved)
  // Only the header badge's read is allowed: restoration itself never writes or creates a cart line.
  expect(purchases).toEqual([])
  expect(cartCalls).not.toEqual([])
  expect(cartCalls.every(url => url.endsWith('/api/cart'))).toBeTruthy()
})

test('a failed or cancelled sign-in keeps the guest configuration and adds nothing', async ({ page }) => {
  const purchases = watchPurchaseTraffic(page)
  await page.goto('/#diy')
  await page.getByRole('button', { name: '选择 Intel Core i5-14600KF', exact: true }).click()
  await expect(configuration(page).getByLabel('当前参考总价')).toHaveText('¥1,499.00')
  await waitForSeenSnapshot(page, 1)
  const saved = await workshopSnapshot(page)
  const credentials = await registerUser(page)

  const dialog = await openSignIn(page)
  await dialog.getByLabel('用户名', { exact: true }).fill(credentials.username)
  await dialog.getByLabel('密码', { exact: true }).fill('WrongRestorePassword1!')
  await dialog.getByRole('button', { name: '登录', exact: true }).click()
  await expect(dialog.getByRole('alert')).toContainText('用户名或密码错误')
  await expect(restoration(page)).toHaveCount(0)
  await expect(configuration(page)).toContainText('已选择 1 / 8 项')
  await expect(configuration(page).getByLabel('当前参考总价')).toHaveText('¥1,499.00')
  expect(await workshopSnapshot(page)).toBe(saved)

  await dialog.getByRole('button', { name: 'Close this dialog' }).click()
  await expect(dialog).toBeHidden()
  // Cancelling the unified login returns to the workspace it was opened from.
  await expect(page).toHaveURL(/#diy$/)
  await expect(configuration(page)).toContainText('已选择 1 / 8 项')
  await expect(configuration(page).getByLabel('当前参考总价')).toHaveText('¥1,499.00')
  await expect(restoration(page)).toHaveCount(0)
  await expect(page.getByRole('button', { name: '登录', exact: true })).toBeVisible()
  expect(purchases).toEqual([])
})

test('price and parameter changes are diagnosed after restoration and the references stay editable', async ({ page }) => {
  const purchases = watchPurchaseTraffic(page)
  await page.goto('/#diy')
  const admin = await adminApi(page)
  const fixture = await createPowerSupply(admin)
  try {
    await categories(page).getByRole('button', { name: /^电源/ }).click()
    await page.getByRole('button', { name: `选择 ${fixture.name}`, exact: true }).click()
    await expect(configuration(page).getByLabel('当前参考总价')).toHaveText('¥100.00')
    await waitForSeenSnapshot(page, 1)

    const priced = await admin.detail(fixture.path)
    expect((await admin.post(`${fixture.path}/price`, { revision: priced.revision, price: '120.50', reason: '登录恢复变价验证' })).status()).toBe(200)
    const edited = await admin.detail(fixture.path)
    const parameters = edited.parameters.map((entry: { code: string }) =>
      entry.code === 'ratedPower' ? { ...entry, value: 650 } : entry)
    expect((await admin.post(fixture.path, {
      revision: edited.revision, name: edited.product.name, brand: edited.product.brand, model: edited.product.model,
      summary: edited.product.summary, description: edited.description, imageUrl: edited.product.imageUrl,
      sourceUrl: edited.product.sourceUrl, priceNote: edited.product.priceNote, parameters,
    })).status()).toBe(200)

    const credentials = await registerUser(page)
    await signIn(page, credentials)

    const banner = restoration(page)
    await expect(banner).toContainText('已恢复登录前的工作区')
    await expect(banner).toContainText('电源参考价由 ¥100.00 变为 ¥120.50')
    // The fingerprint moved, so a parameter or sale change is also honestly reported.
    await expect(banner).toContainText('服务端参数指纹与登录前所见不同')
    await expect(configuration(page)).toContainText('已选择 1 / 8 项')
    await expect(configuration(page)).toContainText(fixture.name)
    await expect(configuration(page).getByRole('button', { name: '移除 电源', exact: true })).toBeVisible()
    expect(purchases).toEqual([])
  } finally { await retire(admin, fixture.path) }
})

test('an off-sale or out-of-stock reference is reported after restoration instead of being dropped', async ({ page }) => {
  await page.goto('/#diy')
  const admin = await adminApi(page)
  const fixture = await createPowerSupply(admin)
  try {
    await categories(page).getByRole('button', { name: /^电源/ }).click()
    await page.getByRole('button', { name: `选择 ${fixture.name}`, exact: true }).click()
    await expect(configuration(page).getByLabel('当前参考总价')).toHaveText('¥100.00')
    await waitForSeenSnapshot(page, 1)
    expect((await admin.post(`${fixture.path}/status`, { revision: (await admin.detail(fixture.path)).revision, status: 'OFF_SALE' })).status()).toBe(200)
    const credentials = await registerUser(page)
    await signIn(page, credentials)

    await expect(restoration(page)).toContainText('电源：商品已下架，请替换。')
    await expect(configuration(page)).toContainText('已选择 1 / 8 项')
    await expect(configuration(page)).toContainText(fixture.name)
    await expect(configuration(page)).toContainText('配置存在问题')

    // The same workspace keeps the reference and now reports the stock change.
    await page.getByRole('button', { name: '退出登录' }).click()
    await expect(page.getByRole('button', { name: '登录', exact: true })).toBeVisible()
    expect((await admin.post(`${fixture.path}/status`, { revision: (await admin.detail(fixture.path)).revision, status: 'ON_SALE' })).status()).toBe(200)
    expect((await admin.post(`${fixture.path}/stock`, { delta: -5, reason: '登录恢复缺货验证' })).status()).toBe(200)
    await page.goto('/#diy')
    await expect(configuration(page)).toContainText('已选择 1 / 8 项')
    await signIn(page, credentials)
    await expect(restoration(page)).toContainText('电源：商品暂时缺货，请替换。')
    await expect(configuration(page)).toContainText(fixture.name)
  } finally { await retire(admin, fixture.path) }
})

test('an invalid restored reference keeps the user able to replace it', async ({ page }) => {
  await page.addInitScript(key => sessionStorage.setItem(key, JSON.stringify({
    version: 1, revision: 4, selections: [{ category: 'CPU', productId: 9007199254740991, source: 'DIY' }],
  })), WORKSPACE)
  await page.goto('/#diy')
  await expect(configuration(page)).toContainText('商品不存在')
  await expect(configuration(page)).toContainText('已选择 1 / 8 项')
  const credentials = await registerUser(page)
  await signIn(page, credentials)
  await expect(restoration(page)).toContainText('CPU：商品不存在，请移除或替换。')
  await expect(configuration(page)).toContainText('已选择 1 / 8 项')
  await expect(configuration(page).getByRole('button', { name: '移除 CPU', exact: true })).toBeVisible()
  await page.getByRole('button', { name: '移除 CPU', exact: true }).click()
  await expect(configuration(page)).toContainText('已选择 0 / 8 项')
})

test('an administrator signing in from the workspace still reaches the administration surface', async ({ page }) => {
  const username = process.env.BROWSER_ADMIN_USERNAME
  const password = process.env.BROWSER_ADMIN_PASSWORD
  if (!username || !password) throw new Error('Use scripts/test.ps1 to start an isolated server with ephemeral administrator credentials.')
  await page.goto('/#diy')
  await page.getByRole('button', { name: '选择 Intel Core i5-14600KF', exact: true }).click()
  await expect(configuration(page).getByLabel('当前参考总价')).toHaveText('¥1,499.00')
  await waitForSeenSnapshot(page, 1)
  await signIn(page, { username, password })
  // The existing administration contract wins over restoration for administrators.
  await expect(page.getByRole('dialog', { name: '管理后台' })).toBeVisible()
  await page.goto('/#diy')
  await expect(configuration(page)).toContainText('已选择 1 / 8 项')
  await expect(configuration(page)).toContainText('Intel Core i5-14600KF')
  await expect(restoration(page)).toHaveCount(0)
})

test('the comparison baseline is frozen when the user leaves for sign-in', async ({ page }) => {
  await page.goto('/#diy')
  const admin = await adminApi(page)
  const fixture = await createPowerSupply(admin)
  try {
    await categories(page).getByRole('button', { name: /^电源/ }).click()
    await page.getByRole('button', { name: `选择 ${fixture.name}`, exact: true }).click()
    await expect(configuration(page).getByLabel('当前参考总价')).toHaveText('¥100.00')
    await waitForSeenSnapshot(page, 1)

    const credentials = await registerUser(page)
    const dialog = await openSignIn(page)
    // While the dialog is open the workspace stays mounted and keeps revalidating, so the snapshot moves.
    expect((await admin.post(`${fixture.path}/price`, { revision: (await admin.detail(fixture.path)).revision,
      price: '130.00', reason: '登录框打开期间变价' })).status()).toBe(200)
    await page.evaluate(() => window.dispatchEvent(new Event('focus')))
    await expect.poll(async () => {
      const raw = await page.evaluate(key => sessionStorage.getItem(key), SEEN)
      return (JSON.parse(raw ?? '{}').items?.[0]?.amount) ?? null
    }).toBe('130.00')

    await dialog.getByLabel('用户名', { exact: true }).fill(credentials.username)
    await dialog.getByLabel('密码', { exact: true }).fill(credentials.password)
    await dialog.getByRole('button', { name: '登录', exact: true }).click()

    // The honest "before" is what the user saw before opening the dialog, not the frozen-behind value.
    await expect(restoration(page)).toContainText('电源参考价由 ¥100.00 变为 ¥130.00')
    await expect(configuration(page)).toContainText(fixture.name)
  } finally { await retire(admin, fixture.path) }
})

test('an edit made while restoration is in flight wins over the older restoration result', async ({ page }) => {
  await page.goto('/#diy')
  await page.getByRole('button', { name: '选择 Intel Core i5-14600KF', exact: true }).click()
  await waitForSeenSnapshot(page, 1)
  const credentials = await registerUser(page)

  let release!: () => void
  let started!: () => void
  const gate = new Promise<void>(resolve => { release = resolve })
  const pending = new Promise<void>(resolve => { started = resolve })
  await page.route('**/api/compatibility/check', async route => {
    const sent = JSON.parse(route.request().postData() ?? '{}')
    if (sent.revision !== 1) { await route.continue(); return }
    const response = await route.fetch()
    const body = await response.json()
    body.data.status = 'INCOMPATIBLE'
    body.data.corePassed = false
    body.data.checks = body.data.checks.map((check: { rule: string }) =>
      check.rule === 'C01' ? { ...check, state: 'FAIL', message: '旧恢复结果的插槽冲突' } : check)
    started()
    await gate
    await route.fulfill({ response, json: body })
  })

  await signIn(page, credentials)
  await pending
  await expect(restoration(page)).toContainText('正在按服务端当前数据')
  await page.getByRole('button', { name: '选择 AMD Ryzen 7 7800X3D', exact: true }).click()
  release()
  await page.unrouteAll({ behavior: 'wait' })

  await expect(configuration(page)).toContainText('修订 2')
  await expect(configuration(page)).toContainText('AMD Ryzen 7 7800X3D')
  await expect(restoration(page)).toContainText('恢复期间配置已变更')
  await expect(restoration(page)).not.toContainText('旧恢复结果的插槽冲突')
  await expect(page.getByRole('region', { name: '配置修订建议' })).not.toContainText('旧恢复结果的插槽冲突')
})

for (const damaged of ['rule', 'references', 'duplicates', 'absent'] as const) {
  test(`restoration never claims consistency with a ${damaged} baseline`, async ({ page }) => {
    await page.goto('/#diy')
    await page.getByRole('button', { name: '选择 Intel Core i5-14600KF', exact: true }).click()
    await waitForSeenSnapshot(page, 1)
    const credentials = await registerUser(page)
    await page.evaluate(([key, kind]) => {
      const seen = JSON.parse(sessionStorage.getItem(key)!)
      if (kind === 'absent') sessionStorage.removeItem(key)
      else {
        if (kind === 'rule') seen.ruleVersion = 'older-rule-version'
        if (kind === 'references') seen.items[0].productId = 9007199254740991
        if (kind === 'duplicates') seen.items.push(seen.items[0])
        sessionStorage.setItem(key, JSON.stringify(seen))
      }
    }, [SEEN, damaged])
    const dialog = await openSignIn(page)
    // A successful background refresh after opening must not turn a frozen absence into a baseline.
    await page.evaluate(() => window.dispatchEvent(new Event('focus')))
    await waitForSeenSnapshot(page, 1)
    await dialog.getByLabel('用户名', { exact: true }).fill(credentials.username)
    await dialog.getByLabel('密码', { exact: true }).fill(credentials.password)
    await dialog.getByRole('button', { name: '登录', exact: true }).click()
    await expect(restoration(page)).toContainText('已恢复登录前的工作区')
    await expect(restoration(page)).not.toContainText('当前数据与登录前所见一致')
  })
}

test('background revalidation cannot interrupt an in-flight restoration', async ({ page }) => {
  await page.goto('/#diy')
  await page.getByRole('button', { name: '选择 Intel Core i5-14600KF', exact: true }).click()
  await waitForSeenSnapshot(page, 1)
  const credentials = await registerUser(page)
  let release!: () => void
  let started!: () => void
  const gate = new Promise<void>(resolve => { release = resolve })
  const pending = new Promise<void>(resolve => { started = resolve })
  await page.route('**/api/compatibility/check', async route => {
    const response = await route.fetch()
    started()
    await gate
    await route.fulfill({ response })
  })
  await signIn(page, credentials)
  await pending
  // Quote has completed while compatibility remains gated: manual refresh must not abort the pair.
  await expect(configuration(page).getByLabel('当前参考总价')).toHaveText('¥1,499.00')
  await expect(configuration(page).getByRole('button', { name: '刷新价格与库存', exact: true })).toBeDisabled()
  await page.evaluate(() => {
    window.dispatchEvent(new Event('focus'))
    document.dispatchEvent(new Event('visibilitychange'))
  })
  release()
  await expect(restoration(page)).toContainText('已恢复登录前的工作区')
  await expect(restoration(page)).not.toContainText('重验暂不可用')
})

test('a login flow reload preserves its workspace origin', async ({ page }) => {
  await page.goto('/#diy')
  await page.getByRole('button', { name: '选择 Intel Core i5-14600KF', exact: true }).click()
  await waitForSeenSnapshot(page, 1)
  const credentials = await registerUser(page)
  await openSignIn(page)
  await page.reload()
  const dialog = page.getByRole('dialog', { name: '统一登录' })
  await dialog.getByLabel('用户名', { exact: true }).fill(credentials.username)
  await dialog.getByLabel('密码', { exact: true }).fill(credentials.password)
  await dialog.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page).toHaveURL(/#diy$/)
  await expect(restoration(page)).toContainText('已恢复登录前的工作区')
})

test('a restoration retry retains the original before-price after background refresh', async ({ page }) => {
  await page.goto('/#diy')
  await page.getByRole('button', { name: '选择 Intel Core i5-14600KF', exact: true }).click()
  await waitForSeenSnapshot(page, 1)
  const credentials = await registerUser(page)
  await page.route('**/api/diy/quote?*', async route => {
    const response = await route.fetch()
    const body = await response.json()
    body.data.items[0].amount = '1600.00'
    body.data.total = body.data.validSubtotal = '1600.00'
    await route.fulfill({ response, json: body })
  })
  let fail = true
  await page.route('**/api/compatibility/check', async route => {
    if (fail) { fail = false; await route.fulfill({ status: 503, json: {} }) }
    else await route.continue()
  })
  await signIn(page, credentials)
  await expect(restoration(page)).toContainText('重验暂不可用')
  await page.evaluate(() => window.dispatchEvent(new Event('focus')))
  await expect.poll(async () => JSON.parse((await page.evaluate(key => sessionStorage.getItem(key), SEEN))!).items[0].amount)
    .toBe('1600.00')
  await restoration(page).getByRole('button', { name: '重试登录后重验' }).click()
  await expect(restoration(page)).toContainText('CPU参考价由 ¥1,499.00 变为 ¥1,600.00')
})

test('leaving login through history clears its origin before a storefront login', async ({ page }) => {
  await page.goto('/#diy')
  const credentials = await registerUser(page)
  await openSignIn(page)
  await page.goBack()
  await expect(page).toHaveURL(/#diy$/)
  await page.getByRole('link', { name: '商品浏览', exact: true }).click()
  await page.evaluate(() => { window.location.hash = 'login' })
  const dialog = page.getByRole('dialog', { name: '统一登录' })
  await dialog.getByLabel('用户名', { exact: true }).fill(credentials.username)
  await dialog.getByLabel('密码', { exact: true }).fill(credentials.password)
  await dialog.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page.getByText(credentials.nickname)).toBeVisible()
  await expect(page).not.toHaveURL(/#diy$/)
  await expect(restoration(page)).toHaveCount(0)
})

test('closing an in-flight login keeps the workspace when the server later succeeds', async ({ page }) => {
  await page.goto('/#diy')
  await page.getByRole('button', { name: '选择 Intel Core i5-14600KF', exact: true }).click()
  await waitForSeenSnapshot(page, 1)
  const credentials = await registerUser(page)
  let release!: () => void
  let started!: () => void
  const gate = new Promise<void>(resolve => { release = resolve })
  const pending = new Promise<void>(resolve => { started = resolve })
  await page.route('**/api/auth/login', async route => {
    const response = await route.fetch()
    started()
    await gate
    await route.fulfill({ response })
  })
  await signIn(page, credentials)
  await pending
  await page.getByRole('dialog', { name: '统一登录' }).getByRole('button', { name: 'Close this dialog' }).click()
  await expect(page).toHaveURL(/#diy$/)
  release()
  await expect(page.getByText(credentials.nickname)).toBeVisible()
  await expect(page).toHaveURL(/#diy$/)
  await expect(restoration(page)).toContainText('已恢复登录前的工作区')
})

test('registering from the workspace preserves the same flow through login', async ({ page }) => {
  await page.goto('/#diy')
  await page.getByRole('button', { name: '选择 Intel Core i5-14600KF', exact: true }).click()
  await waitForSeenSnapshot(page, 1)
  await page.getByRole('button', { name: '注册', exact: true }).click()
  const registration = page.getByRole('dialog', { name: '注册账户' })
  const username = `ui${Date.now().toString(36)}`
  await registration.getByLabel('用户名', { exact: true }).fill(username)
  await registration.getByLabel('密码', { exact: true }).fill(PASSWORD)
  await registration.locator('input[name="confirmPassword"]').fill(PASSWORD)
  await registration.getByRole('button', { name: '创建账户', exact: true }).click()
  const login = page.getByRole('dialog', { name: '统一登录' })
  await expect(login).toContainText('注册成功，请登录。')
  await login.getByLabel('密码', { exact: true }).fill(PASSWORD)
  await login.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page).toHaveURL(/#diy$/)
  await expect(restoration(page)).toContainText('已恢复登录前的工作区')
  await expect(configuration(page)).toContainText('已选择 1 / 8 项')
})

test('restocked references report their recovered availability without claiming consistency', async ({ page }) => {
  await page.goto('/#diy')
  const admin = await adminApi(page)
  const fixture = await createPowerSupply(admin)
  try {
    await categories(page).getByRole('button', { name: /^电源/ }).click()
    await page.getByRole('button', { name: `选择 ${fixture.name}`, exact: true }).click()
    await expect(configuration(page).getByLabel('当前参考总价')).toHaveText('¥100.00')
    await waitForSeenSnapshot(page, 1)
    expect((await admin.post(`${fixture.path}/stock`, { delta: -5, reason: '登录恢复缺货基线' })).status()).toBe(200)
    await page.evaluate(() => window.dispatchEvent(new Event('focus')))
    await expect.poll(async () => JSON.parse((await page.evaluate(key => sessionStorage.getItem(key), SEEN))!).items[0].state)
      .toBe('OUT_OF_STOCK')
    const before = await waitForSeenSnapshot(page, 1)
    expect(before.items[0].amount).toBeNull()

    const credentials = await registerUser(page)
    const dialog = await openSignIn(page)
    expect((await admin.post(`${fixture.path}/stock`, { delta: 5, reason: '登录期间补货' })).status()).toBe(200)
    await dialog.getByLabel('用户名', { exact: true }).fill(credentials.username)
    await dialog.getByLabel('密码', { exact: true }).fill(credentials.password)
    await dialog.getByRole('button', { name: '登录', exact: true }).click()
    await expect(restoration(page)).toContainText('已恢复登录前的工作区')
    await expect(configuration(page).getByLabel('当前参考总价')).toHaveText('¥100.00')
    const after = await waitForSeenSnapshot(page, 1)
    expect(after.items[0].state).toBe('VALID')
    // Stock availability is deliberately outside the compatibility parameter fingerprint.
    expect(after.catalogVersion).toBe(before.catalogVersion)
    await expect(restoration(page)).toContainText('电源：商品状态由「暂时缺货」变为「可售」')
    await expect(restoration(page)).not.toContainText('当前数据与登录前所见一致')
  } finally { await retire(admin, fixture.path) }
})
