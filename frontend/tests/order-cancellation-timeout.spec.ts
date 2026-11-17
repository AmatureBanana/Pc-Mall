import { expect, test, type APIRequestContext, type Page } from '@playwright/test'

async function write(api: APIRequestContext, path: string, data: unknown) {
  const csrf = (await (await api.get('/api/auth/csrf')).json()).data
  return api.post(path, { data, headers: { [csrf.headerName]: csrf.token } })
}
async function signIn(page: Page) {
  const username = `c19${Date.now().toString(36)}${Math.random().toString(36).slice(2, 5)}`
  const password = 'CancellationPassword123!'
  expect((await write(page.request, '/api/auth/register', { username, password, confirmPassword: password })).status()).toBe(201)
  await page.goto('/#login')
  const login = page.getByRole('dialog', { name: '统一登录' })
  await login.getByLabel('用户名', { exact: true }).fill(username)
  await login.getByLabel('密码', { exact: true }).fill(password)
  await login.getByRole('button', { name: '登录', exact: true }).click()
  await expect(login).not.toBeVisible()
}
async function create(api: APIRequestContext, productId: number) {
  const added = await write(api, '/api/cart/items', { productId, quantity: 3, requestKey: `c19-${Date.now()}` })
  expect(added.status()).toBe(200)
  const cart = (await added.json()).data
  const input = { revision: cart.revision, itemIds: [cart.items[0].id], configurationIds: [] }
  const quote = await write(api, '/api/cart/checkout-preview', input)
  expect(quote.status()).toBe(200)
  const response = await write(api, '/api/orders', { ...input, quoteToken: (await quote.json()).data.quoteToken, requestKey: `c19-${Date.now()}` })
  expect(response.status(), await response.text()).toBe(200)
  return (await response.json()).data as { orderNumber: string }
}

test('owner confirms cancellation and safely retries a lost success response', async ({ page, playwright }) => {
  test.setTimeout(90_000)
  page.setDefaultTimeout(10_000)
  const admin = await playwright.request.newContext({ baseURL: process.env.BROWSER_BASE_URL })
  let productPath = ''
  try {
    expect((await write(admin, '/api/auth/login', { username: process.env.BROWSER_ADMIN_USERNAME,
      password: process.env.BROWSER_ADMIN_PASSWORD })).status()).toBe(200)
    const created = await write(admin, '/api/admin/products', {
      sku: `WEB19-${Date.now().toString(36)}`, category: 'PSU', name: '订单取消测试电源', brand: '任务19夹具', model: 'Cancellation-19',
      summary: '订单取消验收', description: '独立测试库专用', imageUrl: '/images/psu.svg', sourceUrl: 'https://example.com/psu', price: '10.01',
      parameters: [{ code: 'ratedPower', value: 750, unit: 'W', sourceUrl: 'https://example.com/psu' }],
    })
    expect(created.status()).toBe(201)
    const id = (await created.json()).data.product.id
    productPath = `/api/admin/products/${id}`
    expect((await write(admin, `${productPath}/status`, { revision: 0, status: 'ON_SALE' })).status()).toBe(200)
    expect((await write(admin, `${productPath}/stock`, { delta: 9, reason: '取消订单隔离夹具' })).status()).toBe(200)
    await signIn(page)
    const first = await create(page.request, id)
    await page.goto(`/#order/${first.orderNumber}`)
    const detail = page.getByRole('main', { name: '订单结果' })
    await detail.getByRole('button', { name: '取消订单', exact: true }).click()
    const dialog = page.getByRole('dialog', { name: '确认取消订单' })
    await expect(dialog).toContainText('不会恢复购物车')
    await dialog.getByRole('button', { name: '返回订单' }).click()
    expect((await (await page.request.get(`/api/orders/${first.orderNumber}`)).json()).data.status).toBe('PENDING_PAYMENT')
    await detail.getByRole('button', { name: '取消订单', exact: true }).click()
    let cancelled: unknown
    await page.route(`**/api/orders/${first.orderNumber}/cancel`, async route => {
      const response = await route.fetch()
      expect(response.status()).toBe(200)
      cancelled = (await response.json()).data
      await route.abort('connectionreset')
    })
    await dialog.getByRole('button', { name: '确认取消', exact: true }).click()
    await expect(dialog.getByRole('alert')).toContainText('取消结果暂未确认')
    await page.unroute(`**/api/orders/${first.orderNumber}/cancel`)
    await dialog.getByRole('button', { name: '确认取消', exact: true }).click()
    await expect(dialog).not.toBeVisible()
    await expect(detail).toContainText('已取消')
    await expect(detail).toContainText('本人主动取消')
    await expect(detail).toContainText('取消时间')
    await expect(detail.getByRole('button', { name: '模拟支付', exact: true })).not.toBeVisible()
    await expect(detail.getByRole('button', { name: '取消订单', exact: true })).not.toBeVisible()
    expect((await (await page.request.get(`/api/orders/${first.orderNumber}`)).json()).data).toEqual(cancelled)
    await page.reload()
    await expect(detail).toContainText('本人主动取消')
    const balance = (await (await admin.get(productPath)).json()).data
    expect(balance.product.stock).toBe(9)
    expect(balance.reservedStock).toBe(0)
    expect((await (await page.request.get('/api/cart')).json()).data.items).toEqual([])
    await page.screenshot({ path: 'test-results/task19-cancelled.png', fullPage: true, animations: 'disabled' })
    await detail.getByRole('link', { name: '返回我的订单' }).click()
    const history = page.getByRole('main', { name: '我的订单' })
    await expect(history).toContainText('本人主动取消')
    await expect(history).toContainText('已取消')
    // Another session pays while this page still holds a pending snapshot.
    const second = await create(page.request, id)
    await page.goto(`/#order/${second.orderNumber}`)
    await detail.getByRole('button', { name: '取消订单', exact: true }).click()
    expect((await write(page.request, `/api/orders/${second.orderNumber}/pay`, {})).status()).toBe(200)
    await dialog.getByRole('button', { name: '确认取消', exact: true }).click()
    await expect(dialog.getByRole('alert')).toContainText('不允许取消')
    await dialog.getByRole('button', { name: '返回订单' }).click()
    await detail.getByRole('button', { name: '重新读取订单' }).click()
    await expect(detail).toContainText('已支付')
    await expect(detail.getByRole('button', { name: '取消订单', exact: true })).not.toBeVisible()
  } finally {
    if (productPath) {
      const current = (await (await admin.get(productPath)).json()).data.product
      if (current.status === 'ON_SALE') await write(admin, `${productPath}/status`, { revision: current.revision, status: 'OFF_SALE' })
    }
    await admin.dispose()
  }
})

const number = `O${'b'.repeat(32)}`
async function mockSession(page: Page, cancelled = false) {
  await page.route('**/api/auth/me', route => route.fulfill({ json: {
    data: { id: 4242, username: 'cancellation_user', nickname: '取消用户', role: 'USER' },
  } }))
  await page.route('**/api/auth/csrf', route => route.fulfill({ json: { data: { token: 'test-token', headerName: 'X-CSRF-TOKEN' } } }))
  await page.route('**/api/cart', route => route.fulfill({ json: { data: {
    revision: 0, items: [], configurations: [], total: '0.00', validTotal: '0.00', validCount: 0, lineCount: 0,
  } } }))
  await page.route(`**/api/orders/${number}`, route => route.fulfill({ json: { data: {
    orderNumber: number, status: cancelled ? 'CANCELLED' : 'PENDING_PAYMENT', currency: 'CNY', total: '10.01',
    createdAt: '2026-11-16T00:00:00Z', paymentDeadline: '2026-11-16T00:30:00Z', paidAt: null,
    cancelledAt: cancelled ? '2026-11-16T00:31:00Z' : null, cancellationReason: cancelled ? 'TIMEOUT' : null,
    items: [{ productId: 7, sku: 'CANCELLATION', name: '取消夹具', model: 'Timeout', category: 'PSU',
      price: '10.01', quantity: 1, amount: '10.01', configurationId: null }], configurations: [],
  } } }))
}

test('timeout cancellation displays its cause and time without payment or cancellation actions', async ({ page }) => {
  // HTTP display contract only; real deadline/scanner behavior is tested against MySQL.
  await mockSession(page, true)
  await page.goto(`/#order/${number}`)
  const detail = page.getByRole('main', { name: '订单结果' })
  await expect(detail).toContainText('已取消')
  await expect(detail).toContainText('支付超时自动取消')
  await expect(detail).toContainText('取消时间')
  await expect(detail.getByRole('button', { name: '模拟支付', exact: true })).not.toBeVisible()
  await expect(detail.getByRole('button', { name: '取消订单', exact: true })).not.toBeVisible()
})

test('cancellation session expiry keeps feedback visible and disables confirmation', async ({ page }) => {
  await mockSession(page)
  await page.route(`**/api/orders/${number}/cancel`, route => route.fulfill({ status: 401,
    json: { code: 'UNAUTHENTICATED', message: '登录已过期，请重新登录。' } }))
  await page.goto(`/#order/${number}`)
  await page.getByRole('button', { name: '取消订单', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '确认取消订单' })
  await dialog.getByRole('button', { name: '确认取消', exact: true }).click()
  await expect(dialog.getByRole('alert')).toContainText('登录已过期')
  await expect(dialog.getByRole('button', { name: '确认取消', exact: true })).toBeDisabled()
  await dialog.getByRole('link', { name: '前往登录' }).click()
  await expect(page.getByRole('dialog', { name: '统一登录' })).toBeVisible()
})
