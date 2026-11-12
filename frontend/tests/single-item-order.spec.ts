import { expect, test, type APIRequestContext } from '@playwright/test'

async function write(api: APIRequestContext, path: string, data: unknown) {
  const csrf = (await (await api.get('/api/auth/csrf')).json()).data
  return api.post(path, { data, headers: { [csrf.headerName]: csrf.token } })
}

test('selected single items create a pending order that can be reloaded from its result URL', async ({ page, playwright }) => {
  const admin = await playwright.request.newContext({ baseURL: process.env.BROWSER_BASE_URL })
  let productPath = ''
  try {
    expect((await write(admin, '/api/auth/login', { username: process.env.BROWSER_ADMIN_USERNAME,
      password: process.env.BROWSER_ADMIN_PASSWORD })).status()).toBe(200)
    const created = await write(admin, '/api/admin/products', {
      sku: `WEB16-${Date.now().toString(36)}`, category: 'PSU', name: '单品下单测试电源', brand: '测试品牌', model: 'Order-750',
      summary: '任务16验收夹具', description: '独立测试库专用', imageUrl: '/images/psu.svg', sourceUrl: 'https://example.com/psu', price: '10.01',
      parameters: [{ code: 'ratedPower', value: 750, unit: 'W', sourceUrl: 'https://example.com/psu' }],
    })
    expect(created.status()).toBe(201)
    const id = (await created.json()).data.product.id
    productPath = `/api/admin/products/${id}`
    expect((await write(admin, `${productPath}/status`, { revision: 0, status: 'ON_SALE' })).status()).toBe(200)
    expect((await write(admin, `${productPath}/stock`, { delta: 4, reason: '订单验收备货' })).status()).toBe(200)
    const username = `o${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`
    const password = 'OrderPassword123!'
    expect((await write(page.request, '/api/auth/register', { username, password, confirmPassword: password })).status()).toBe(201)
    await page.goto('/#login')
    const login = page.getByRole('dialog', { name: '统一登录' })
    await login.getByLabel('用户名', { exact: true }).fill(username)
    await login.getByLabel('密码', { exact: true }).fill(password)
    await login.getByRole('button', { name: '登录', exact: true }).click()
    await expect(login).not.toBeVisible()
    expect((await write(page.request, '/api/cart/items', { productId: id, quantity: 3, requestKey: `order-${Date.now()}` })).status()).toBe(200)
    await page.getByRole('button', { name: /^购物车/ }).click()
    await page.getByRole('button', { name: '预结算', exact: true }).click()
    const quote = page.getByRole('dialog', { name: '确认当前报价' })
    await quote.getByRole('button', { name: '确认并重新核验报价' }).click()
    await expect(quote).toContainText('当前报价已确认')
    await quote.getByRole('button', { name: '创建订单', exact: true }).click()
    const result = page.getByRole('main', { name: '订单结果' })
    await expect(result).toContainText('待支付')
    await expect(result.getByLabel('订单成交总额')).toHaveText('¥30.03')
    await expect(result).toContainText('单品下单测试电源')
    await expect(result).toContainText('Order-750')
    await expect(result).toContainText('支付截止')
    await page.reload()
    await expect(result.getByLabel('订单成交总额')).toHaveText('¥30.03')
    expect((await (await page.request.get('/api/cart')).json()).data.items).toHaveLength(0)
    expect((await (await admin.get(productPath)).json()).data.reservedStock).toBe(3)
    await page.screenshot({ path: 'test-results/task16-order.png', fullPage: true, animations: 'disabled' })
    // A second purchase exercises a price change after confirmation, then a lost success response.
    expect((await write(page.request, '/api/cart/items', { productId: id, quantity: 1, requestKey: `retry-${Date.now()}` })).status()).toBe(200)
    await page.goto('/#cart')
    await page.getByRole('button', { name: '预结算', exact: true }).click()
    await quote.getByRole('button', { name: '确认并重新核验报价' }).click()
    await expect(quote).toContainText('当前报价已确认')
    const detail = (await (await admin.get(productPath)).json()).data.product
    expect((await write(admin, `${productPath}/price`, { revision: detail.revision, price: '10.02', reason: '确认后再次变价' })).status()).toBe(200)
    await quote.getByRole('button', { name: '创建订单', exact: true }).click()
    await expect(quote.getByRole('alert')).toContainText('重新核验')
    await expect(quote.getByRole('button', { name: '创建订单', exact: true })).toBeDisabled()
    await quote.getByRole('button', { name: '确认并重新核验报价' }).click()
    await expect(quote).toContainText('请核对最新明细并再次确认')
    await quote.getByRole('button', { name: '确认并重新核验报价' }).click()
    await expect(quote).toContainText('当前报价已确认')
    let committed = ''
    await page.route('**/api/orders', async route => {
      const response = await route.fetch()
      expect(response.status()).toBe(200)
      committed = (await response.json()).data.orderNumber
      await route.abort('connectionreset')
    })
    await quote.getByRole('button', { name: '创建订单', exact: true }).click()
    await expect(quote).toContainText('下单结果暂未确认')
    await page.unroute('**/api/orders')
    await quote.getByRole('button', { name: '返回购物车' }).click()
    await page.getByRole('button', { name: '刷新价格与库存' }).click()
    await expect(page.getByText('购物车还是空的', { exact: false })).toBeVisible()
    await page.getByRole('button', { name: '重试确认上次下单', exact: true }).click()
    await expect(page).toHaveURL(new RegExp(`#order/${committed}$`))
    await expect(result.getByLabel('订单成交总额')).toHaveText('¥10.02')
    expect((await (await admin.get(productPath)).json()).data.reservedStock).toBe(4)
    // If the first request never reached the server, a later domain conflict resolves uncertainty.
    expect((await write(admin, `${productPath}/stock`, { delta: 1, reason: '未知结果未提交恢复验收' })).status()).toBe(200)
    expect((await write(page.request, '/api/cart/items', { productId: id, quantity: 1, requestKey: `uncommitted-${Date.now()}` })).status()).toBe(200)
    await page.goto('/#cart')
    await page.getByRole('button', { name: '预结算', exact: true }).click()
    await quote.getByRole('button', { name: '确认并重新核验报价' }).click()
    await expect(quote).toContainText('当前报价已确认')
    await page.route('**/api/orders', route => route.abort('connectionreset'))
    await quote.getByRole('button', { name: '创建订单', exact: true }).click()
    await expect(quote).toContainText('下单结果暂未确认')
    await page.unroute('**/api/orders')
    await page.route('**/api/orders', async route => {
      const response = await route.fetch({ headers: { ...route.request().headers(), 'x-csrf-token': 'invalid-token' } })
      expect(response.status()).toBe(403)
      await route.fulfill({ response })
    })
    await quote.getByRole('button', { name: '重试确认上次下单', exact: true }).click()
    await expect(quote).toContainText('下单结果暂未确认')
    await expect(quote.getByRole('button', { name: '重试确认上次下单', exact: true })).toBeVisible()
    await page.unroute('**/api/orders')
    const changed = (await (await admin.get(productPath)).json()).data.product
    expect((await write(admin, `${productPath}/price`, { revision: changed.revision, price: '10.03', reason: '未知请求后变价' })).status()).toBe(200)
    await quote.getByRole('button', { name: '重试确认上次下单', exact: true }).click()
    await expect(quote.getByRole('alert')).toContainText('报价或商品信息发生变化')
    await expect(quote.getByRole('button', { name: '重试确认上次下单', exact: true })).not.toBeVisible()
    await quote.getByRole('button', { name: '确认并重新核验报价' }).click()
    await quote.getByRole('button', { name: '确认并重新核验报价' }).click()
    await expect(quote.getByRole('button', { name: '创建订单', exact: true })).toBeEnabled()
    await quote.getByRole('button', { name: '创建订单', exact: true }).click()
    await expect(result.getByLabel('订单成交总额')).toHaveText('¥10.03')
    expect((await (await admin.get(productPath)).json()).data.reservedStock).toBe(5)
  } finally {
    if (productPath) {
      const current = (await (await admin.get(productPath)).json()).data.product
      await write(admin, `${productPath}/status`, { revision: current.revision, status: 'OFF_SALE' })
    }
    await admin.dispose()
  }
})
