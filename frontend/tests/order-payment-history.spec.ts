import { expect, test, type APIRequestContext, type Page } from '@playwright/test'

async function write(api: APIRequestContext, path: string, data: unknown) {
  const csrf = (await (await api.get('/api/auth/csrf')).json()).data
  return api.post(path, { data, headers: { [csrf.headerName]: csrf.token } })
}
async function signIn(page: Page) {
  const username = `p18${Date.now().toString(36)}${Math.random().toString(36).slice(2, 5)}`
  const password = 'PaymentPassword123!'
  expect((await write(page.request, '/api/auth/register', { username, password, confirmPassword: password })).status()).toBe(201)
  await page.goto('/#login')
  const login = page.getByRole('dialog', { name: '统一登录' })
  await login.getByLabel('用户名', { exact: true }).fill(username)
  await login.getByLabel('密码', { exact: true }).fill(password)
  await login.getByRole('button', { name: '登录', exact: true }).click()
  await expect(login).not.toBeVisible()
}
async function create(api: APIRequestContext, productId: number) {
  const added = await write(api, '/api/cart/items', { productId, quantity: 3, requestKey: `p18-${Date.now()}` })
  expect(added.status()).toBe(200)
  const cart = (await added.json()).data
  const input = { revision: cart.revision, itemIds: [cart.items[0].id], configurationIds: [] }
  const quote = await write(api, '/api/cart/checkout-preview', input)
  expect(quote.status()).toBe(200)
  const response = await write(api, '/api/orders', { ...input, quoteToken: (await quote.json()).data.quoteToken, requestKey: `p18-${Date.now()}` })
  expect(response.status(), await response.text()).toBe(200)
  return (await response.json()).data as { orderNumber: string }
}

test('owner browses history, confirms simulated payment and safely retries a lost success response', async ({ page, playwright }) => {
  test.setTimeout(90_000)
  page.setDefaultTimeout(10_000)
  const admin = await playwright.request.newContext({ baseURL: process.env.BROWSER_BASE_URL })
  let productPath = ''
  try {
    expect((await write(admin, '/api/auth/login', { username: process.env.BROWSER_ADMIN_USERNAME,
      password: process.env.BROWSER_ADMIN_PASSWORD })).status()).toBe(200)
    const created = await write(admin, '/api/admin/products', {
      sku: `WEB18-${Date.now().toString(36)}`, category: 'PSU', name: '模拟支付测试电源', brand: '任务18夹具', model: 'Payment-18',
      summary: '订单支付验收', description: '独立测试库专用', imageUrl: '/images/psu.svg', sourceUrl: 'https://example.com/psu', price: '10.01',
      parameters: [{ code: 'ratedPower', value: 750, unit: 'W', sourceUrl: 'https://example.com/psu' }],
    })
    expect(created.status()).toBe(201)
    const id = (await created.json()).data.product.id
    productPath = `/api/admin/products/${id}`
    expect((await write(admin, `${productPath}/status`, { revision: 0, status: 'ON_SALE' })).status()).toBe(200)
    expect((await write(admin, `${productPath}/stock`, { delta: 9, reason: '模拟支付隔离夹具' })).status()).toBe(200)
    await signIn(page)
    await page.getByRole('link', { name: 'DIY 装机', exact: true }).click()
    await page.getByRole('button', { name: '我的订单', exact: true }).click()
    const history = page.getByRole('main', { name: '我的订单' })
    await expect(history).toContainText('暂无订单')
    await expect(page.getByRole('navigation', { name: '商城导航' }).locator('.active')).toHaveCount(0)
    const first = await create(page.request, id)
    const second = await create(page.request, id)
    await history.getByRole('button', { name: '刷新订单' }).click()
    await expect(history.getByRole('link', { name: first.orderNumber })).toBeVisible()
    await expect(history.getByRole('link', { name: second.orderNumber })).toBeVisible()
    await page.screenshot({ path: 'test-results/task18-history.png', fullPage: true, animations: 'disabled' })
    await history.getByRole('link', { name: first.orderNumber }).click()
    const detail = page.getByRole('main', { name: '订单结果' })
    await expect(detail.getByLabel('订单成交总额')).toHaveText('¥30.03')
    await expect(page.getByRole('navigation', { name: '商城导航' }).locator('.active')).toHaveCount(0)
    const stock = (await (await admin.get(productPath)).json()).data
    expect((await write(admin, `${productPath}/price`, { revision: stock.product.revision, price: '99.99', reason: '成交后价格变化' })).status()).toBe(200)
    await detail.getByRole('button', { name: '模拟支付', exact: true }).click()
    const dialog = page.getByRole('dialog', { name: '确认模拟支付' })
    await expect(dialog).toContainText('¥30.03')
    await expect(dialog).toContainText('站内模拟')
    let paid: unknown
    await page.route(`**/api/orders/${first.orderNumber}/pay`, async route => {
      const response = await route.fetch()
      expect(response.status()).toBe(200)
      paid = (await response.json()).data
      await route.abort('connectionreset')
    })
    await dialog.getByRole('button', { name: '确认模拟支付', exact: true }).click()
    await expect(dialog.getByRole('alert')).toContainText('支付结果暂未确认')
    await page.unroute(`**/api/orders/${first.orderNumber}/pay`)
    await dialog.getByRole('button', { name: '确认模拟支付', exact: true }).click()
    await expect(dialog).not.toBeVisible()
    await expect(detail).toContainText('已支付')
    await expect(detail).toContainText('支付时间')
    await expect(detail.getByRole('button', { name: '模拟支付', exact: true })).not.toBeVisible()
    expect((await (await page.request.get(`/api/orders/${first.orderNumber}`)).json()).data).toEqual(paid)
    await page.reload()
    await expect(detail).toContainText('已支付')
    expect((await (await admin.get(productPath)).json()).data.reservedStock).toBe(stock.reservedStock)
    expect((await (await admin.get(productPath)).json()).data.product.stock).toBe(stock.product.stock)
    await page.screenshot({ path: 'test-results/task18-paid.png', fullPage: true, animations: 'disabled' })
    // The real Clock/MySQL cutoff is covered by backend tests; this is the browser error contract.
    await page.goto(`/#order/${second.orderNumber}`)
    await detail.getByRole('button', { name: '模拟支付', exact: true }).click()
    await page.route(`**/api/orders/${second.orderNumber}/pay`, route => route.fulfill({ status: 409,
      json: { code: 'ORDER_PAYMENT_EXPIRED', message: '订单已到支付截止时间，无法支付。' } }))
    await dialog.getByRole('button', { name: '确认模拟支付', exact: true }).click()
    await expect(dialog.getByRole('alert')).toContainText('已到支付截止时间')
    await expect(detail).toContainText('待支付')
    await page.unroute(`**/api/orders/${second.orderNumber}/pay`)
    await dialog.getByRole('button', { name: '返回订单' }).click()
    await page.getByRole('button', { name: '退出登录', exact: true }).click()
    await signIn(page)
    await page.getByRole('button', { name: '我的订单', exact: true }).click()
    await expect(history).toContainText('暂无订单')
    await expect(history).not.toContainText(first.orderNumber)
    await page.goto(`/#order/${first.orderNumber}`)
    await expect(detail.getByRole('alert')).toContainText('订单不存在')
    await expect(detail.getByLabel('订单成交总额')).not.toBeVisible()
    expect((await write(page.request, `/api/orders/${first.orderNumber}/pay`, {})).status()).toBe(404)
  } finally {
    if (productPath) {
      const current = (await (await admin.get(productPath)).json()).data.product
      if (current.status === 'ON_SALE') await write(admin, `${productPath}/status`, { revision: current.revision, status: 'OFF_SALE' })
    }
    await admin.dispose()
  }
})

test('payment session expiry keeps login feedback visible and disables confirmation', async ({ page }) => {
  const number = `O${'a'.repeat(32)}`
  // Browser contract at the HTTP boundary; no private service or real account is substituted.
  await page.route('**/api/auth/me', route => route.fulfill({ json: {
    data: { id: 4242, username: 'payment_expiry', nickname: '支付用户', role: 'USER' },
  } }))
  await page.route('**/api/auth/csrf', route => route.fulfill({ json: { data: { token: 'test-token', headerName: 'X-CSRF-TOKEN' } } }))
  await page.route('**/api/cart', route => route.fulfill({ json: { data: {
    revision: 0, items: [], configurations: [], total: '0.00', validTotal: '0.00', validCount: 0, lineCount: 0,
  } } }))
  await page.route(`**/api/orders/${number}`, route => route.fulfill({ json: { data: {
    orderNumber: number, status: 'PENDING_PAYMENT', currency: 'CNY', total: '10.01',
    createdAt: '2026-11-13T00:00:00Z', paymentDeadline: '2026-11-13T00:30:00Z', paidAt: null,
    items: [{ productId: 7, sku: 'PAYMENT-EXPIRY', name: '支付夹具', model: 'Expiry', category: 'PSU',
      price: '10.01', quantity: 1, amount: '10.01', configurationId: null }], configurations: [],
  } } }))
  await page.route(`**/api/orders/${number}/pay`, route => route.fulfill({ status: 401,
    json: { code: 'UNAUTHENTICATED', message: '登录已过期，请重新登录。' } }))
  await page.goto(`/#order/${number}`)
  await page.getByRole('button', { name: '模拟支付', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '确认模拟支付' })
  await dialog.getByRole('button', { name: '确认模拟支付', exact: true }).click()
  await expect(dialog.getByRole('alert')).toContainText('登录已过期')
  await expect(dialog.getByRole('button', { name: '确认模拟支付', exact: true })).toBeDisabled()
  await dialog.getByRole('link', { name: '前往登录' }).click()
  await expect(page.getByRole('dialog', { name: '统一登录' })).toBeVisible()
})
