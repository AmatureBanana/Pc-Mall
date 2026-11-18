import { test, expect, type APIRequestContext } from '@playwright/test'

async function write(api: APIRequestContext, path: string, data: unknown) {
  const csrf = (await (await api.get('/api/auth/csrf')).json()).data
  return api.post(path, { data, headers: { [csrf.headerName]: csrf.token } })
}

test('administrator filters buyer orders, reads snapshots and confirms controlled completion', async ({ page, playwright }) => {
  test.setTimeout(90_000)
  page.setDefaultTimeout(10_000)
  const owner = await playwright.request.newContext({ baseURL: process.env.BROWSER_BASE_URL })
  let productPath = ''
  const username = `a20${Date.now().toString(36)}`, password = 'OrderAdminPassword123!'
  try {
    expect((await write(owner, '/api/auth/register', { username, password, confirmPassword: password })).status()).toBe(201)
    expect((await write(owner, '/api/auth/login', { username, password })).status()).toBe(200)
    expect((await owner.get('/api/admin/orders')).status()).toBe(403)
    await page.goto('/#login')
    const login = page.getByRole('dialog', { name: '统一登录' })
    await login.getByLabel('用户名', { exact: true }).fill(process.env.BROWSER_ADMIN_USERNAME!)
    await login.getByLabel('密码', { exact: true }).fill(process.env.BROWSER_ADMIN_PASSWORD!)
    await login.getByRole('button', { name: '登录', exact: true }).click()
    const admin = page.getByRole('dialog', { name: '管理后台' })
    await expect(admin.getByRole('heading', { name: '商城管理中心' })).toBeVisible()
    const created = await write(page.request, '/api/admin/products', {
      sku: `WEB20-${Date.now().toString(36)}`, category: 'PSU', name: '后台订单快照电源', brand: '任务20', model: 'Admin-20',
      summary: '后台订单验收', description: '隔离测试', imageUrl: '/images/psu.svg', sourceUrl: 'https://example.com/psu', price: '10.01',
      parameters: [{ code: 'ratedPower', value: 750, unit: 'W', sourceUrl: 'https://example.com/psu' }],
    })
    expect(created.status()).toBe(201)
    const id = (await created.json()).data.product.id
    productPath = `/api/admin/products/${id}`
    expect((await write(page.request, `${productPath}/status`, { revision: 0, status: 'ON_SALE' })).status()).toBe(200)
    expect((await write(page.request, `${productPath}/stock`, { delta: 9, reason: '后台订单隔离夹具' })).status()).toBe(200)
    async function createBuyerOrder() {
      const cart = (await (await write(owner, '/api/cart/items', { productId: id, quantity: 3, requestKey: crypto.randomUUID() })).json()).data
      const selection = { revision: cart.revision, itemIds: [cart.items[0].id], configurationIds: [] }
      const quote = (await (await write(owner, '/api/cart/checkout-preview', selection)).json()).data
      const orderResponse = await write(owner, '/api/orders', { ...selection, quoteToken: quote.quoteToken, requestKey: crypto.randomUUID() })
      expect(orderResponse.status()).toBe(200)
      return (await orderResponse.json()).data
    }
    const order = await createBuyerOrder()
    const pending = await createBuyerOrder()
    const cancelled = await createBuyerOrder()
    expect((await write(owner, `/api/orders/${cancelled.orderNumber}/cancel`, {})).status()).toBe(200)
    expect((await write(owner, `/api/orders/${order.orderNumber}/pay`, {})).status()).toBe(200)
    const stock = (await (await page.request.get(productPath)).json()).data
    expect((await write(page.request, `${productPath}/price`, { revision: stock.product.revision, price: '99.99', reason: '验证成交快照' })).status()).toBe(200)
    await page.route('**/api/admin/orders?**', route => route.fulfill({ status: 503, json: { code: 'ORDER_UNAVAILABLE', message: '订单服务暂时不可用，请重试。' } }), { times: 1 })
    await admin.getByRole('navigation').getByRole('button', { name: '订单管理' }).click()
    await expect(admin.getByRole('alert')).toContainText('订单服务暂时不可用')
    await expect(admin.getByText('订单数据加载失败，请重新载入')).toBeVisible()
    await admin.getByRole('button', { name: '重新载入', exact: true }).click()
    await expect(admin.getByRole('alert')).toHaveCount(0)
    await admin.getByLabel('查询购买用户名').fill(username)
    await admin.getByLabel('筛选订单状态').selectOption('PAID')
    await admin.getByRole('button', { name: '查询', exact: true }).click()
    const row = admin.getByRole('row').filter({ hasText: order.orderNumber })
    await expect(row).toContainText(username)
    await expect(row).toContainText('30.03')
    await row.getByRole('button', { name: '查看订单' }).click()
    await expect(admin.getByRole('region', { name: '成交单品' })).toContainText('后台订单快照电源')
    await expect(admin.getByLabel('订单成交总额')).toHaveText('¥30.03')
    await admin.getByRole('button', { name: '完成交易', exact: true }).click()
    const confirmation = admin.getByRole('group', { name: '确认完成交易' })
    await expect(confirmation).toContainText(order.orderNumber)
    await confirmation.getByRole('button', { name: '取消', exact: true }).click()
    await expect(confirmation).not.toBeVisible()
    await admin.getByRole('button', { name: '完成交易', exact: true }).click()
    await page.route(`**/api/admin/orders/${order.orderNumber}/complete`, route => route.fulfill({ status: 503,
      json: { code: 'ORDER_UNAVAILABLE', message: '交易未完成，请重新载入或重试。' } }), { times: 1 })
    await confirmation.getByRole('button', { name: '确认完成', exact: true }).click()
    await expect(admin.getByRole('alert')).toContainText('交易未完成')
    expect((await (await owner.get(`/api/orders/${order.orderNumber}`)).json()).data.status).toBe('PAID')
    expect((await (await page.request.get(`/api/admin/orders/${order.orderNumber}/audit`)).json()).data).toHaveLength(0)
    await page.route(`**/api/admin/orders/${order.orderNumber}/complete`, async route => {
      const response = await route.fetch()
      expect(response.status()).toBe(200)
      await route.abort('connectionreset')
    }, { times: 1 })
    await confirmation.getByRole('button', { name: '确认完成', exact: true }).click()
    await expect(admin.getByRole('alert')).toBeVisible()
    await expect(confirmation).toBeVisible()
    await confirmation.getByRole('button', { name: '确认完成', exact: true }).click()
    await expect(admin.getByRole('status')).toContainText('交易已完成')
    await expect(admin).toContainText('完成时间')
    await expect(admin).toContainText(process.env.BROWSER_ADMIN_USERNAME!)
    await expect(admin.getByRole('button', { name: '完成交易', exact: true })).toHaveCount(0)
    const audit = (await (await page.request.get(`/api/admin/orders/${order.orderNumber}/audit`)).json()).data
    expect(audit).toHaveLength(1)
    expect((await (await owner.get(`/api/orders/${order.orderNumber}`)).json()).data.status).toBe('COMPLETED')
    expect((await (await page.request.get(productPath)).json()).data.product.stock).toBe(stock.product.stock)
    await page.screenshot({ path: 'test-results/task20-completed.png', fullPage: true, animations: 'disabled' })
    await admin.getByRole('button', { name: '返回订单列表' }).click()
    await expect(admin.getByText('没有符合条件的订单')).toBeVisible()
    await admin.getByLabel('筛选订单状态').selectOption('COMPLETED')
    await admin.getByLabel('查询订单号').fill(order.orderNumber)
    await admin.getByRole('button', { name: '查询', exact: true }).click()
    await expect(admin.getByRole('row').filter({ hasText: order.orderNumber })).toContainText('已完成')
    for (const [state, number, label] of [['PENDING_PAYMENT', pending.orderNumber, '待支付'], ['CANCELLED', cancelled.orderNumber, '已取消']]) {
      await admin.getByLabel('筛选订单状态').selectOption(state!)
      await admin.getByLabel('查询订单号').fill(number!)
      await admin.getByRole('button', { name: '查询', exact: true }).click()
      await expect(admin.getByRole('row').filter({ hasText: number! })).toContainText(label!)
      await admin.getByRole('button', { name: '查看订单' }).click()
      await expect(admin.getByRole('button', { name: '完成交易', exact: true })).toHaveCount(0)
      await admin.getByRole('button', { name: '返回订单列表' }).click()
    }
    await admin.getByLabel('筛选订单状态').selectOption('COMPLETED')
    await admin.getByLabel('查询订单号').fill(order.orderNumber)
    await admin.getByRole('button', { name: '查询', exact: true }).click()
    await expect(admin.getByRole('row').filter({ hasText: order.orderNumber })).toContainText('已完成')
    await page.route(`**/api/admin/orders/${order.orderNumber}`, route => route.fulfill({ status: 401,
      json: { code: 'UNAUTHENTICATED', message: '登录已过期，请重新登录。' } }), { times: 1 })
    await admin.getByRole('button', { name: '查看订单' }).click()
    await expect(admin.getByRole('alert')).toContainText('登录已过期')
    await expect(admin.getByRole('link', { name: '前往登录' })).toBeVisible()
    await expect(admin.getByLabel('订单成交总额')).toHaveCount(0)
  } finally {
    if (productPath) {
      const detail = (await (await page.request.get(productPath)).json()).data
      if (detail?.product?.status === 'ON_SALE') await write(page.request, `${productPath}/status`, { revision: detail.product.revision, status: 'OFF_SALE' })
    }
    await owner.dispose()
  }
})
