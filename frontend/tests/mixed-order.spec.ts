import { expect, test, type APIRequestContext } from '@playwright/test'

async function write(api: APIRequestContext, path: string, data: unknown) {
  const csrf = (await (await api.get('/api/auth/csrf')).json()).data
  return api.post(path, { data, headers: { [csrf.headerName]: csrf.token } })
}

test('mixed checkout keeps whole groups, rejects aggregate shortage and recovers the same grouped snapshot', async ({ page, playwright }) => {
  test.setTimeout(90_000)
  const admin = await playwright.request.newContext({ baseURL: process.env.BROWSER_BASE_URL })
  const privateIds: number[] = []
  try {
    expect((await write(admin, '/api/auth/login', { username: process.env.BROWSER_ADMIN_USERNAME,
      password: process.env.BROWSER_ADMIN_PASSWORD })).status()).toBe(200)
    const catalog = (await (await admin.get('/api/products')).json()).data as { id: number; sku: string }[]
    const memory = catalog.find(product => product.sku === 'KINGSTON-KF560C36BBE2-16')!.id
    const selections: { category: string; productId: number }[] = []
    const fixture = Date.now().toString(36)
    for (const templateId of [1, 3, 5, memory, 9, 11, 12, 8]) {
      const detail = (await (await admin.get(`/api/admin/products/${templateId}`)).json()).data
      const p = detail.product
      const created = await write(admin, '/api/admin/products', {
        sku: `WEB17-${fixture}-${p.category}`, category: p.category, name: `混合订单-${p.category}`,
        brand: '任务17夹具', model: `Mixed17-${p.category}`, summary: p.summary, description: detail.description,
        imageUrl: p.imageUrl, sourceUrl: p.sourceUrl, priceNote: p.priceNote, price: p.price, parameters: detail.parameters,
      })
      expect(created.status(), await created.text()).toBe(201)
      const id = (await created.json()).data.product.id
      privateIds.push(id)
      selections.push({ category: p.category, productId: id })
      expect((await write(admin, `/api/admin/products/${id}/status`, { revision: 0, status: 'ON_SALE' })).status()).toBe(200)
      expect((await write(admin, `/api/admin/products/${id}/stock`, { delta: 101, reason: '混合订单隔离夹具' })).status()).toBe(200)
    }
    const psu = selections.find(part => part.category === 'PSU')!.productId
    const username = `o17${fixture}${Math.random().toString(36).slice(2, 5)}`
    const password = 'OrderPassword123!'
    expect((await write(page.request, '/api/auth/register', { username, password, confirmPassword: password })).status()).toBe(201)
    await page.goto('/#login')
    const login = page.getByRole('dialog', { name: '统一登录' })
    await login.getByLabel('用户名', { exact: true }).fill(username)
    await login.getByLabel('密码', { exact: true }).fill(password)
    await login.getByRole('button', { name: '登录', exact: true }).click()
    await expect(login).not.toBeVisible()
    for (const memoryQuantity of [1, 2]) {
      expect((await write(page.request, '/api/cart/configurations', { selections, memoryQuantity })).status()).toBe(200)
    }
    const added = await write(page.request, '/api/cart/items', { productId: psu, quantity: 99, requestKey: `mixed-${fixture}` })
    expect(added.status()).toBe(200)
    const original = (await added.json()).data
    await page.getByRole('button', { name: /^购物车/ }).click()
    const cart = page.getByRole('main', { name: '我的购物车' })
    await expect(cart.getByRole('region', { name: '装机方案 1', exact: true }).getByRole('checkbox')).toHaveCount(1)
    await cart.getByRole('button', { name: '预结算', exact: true }).click()
    const quote = page.getByRole('dialog', { name: '确认当前报价' })
    await quote.getByRole('button', { name: '确认并重新核验报价' }).click()
    await expect(quote).toContainText('当前报价已确认')
    await expect(quote.getByRole('button', { name: '创建订单', exact: true })).toBeEnabled()
    // Each group alone fits. The selected aggregate of 99 + 1 + 1 must still fit at creation.
    expect((await write(admin, `/api/admin/products/${psu}/stock`, { delta: -1, reason: '聚合缺货验收' })).status()).toBe(200)
    await quote.getByRole('button', { name: '创建订单', exact: true }).click()
    await expect(quote.getByRole('alert')).toContainText('选中项缺货')
    const retained = (await (await page.request.get('/api/cart')).json()).data
    expect(retained.items).toHaveLength(1)
    expect(retained.configurations).toHaveLength(2)
    expect((await (await admin.get(`/api/admin/products/${psu}`)).json()).data.reservedStock).toBe(0)
    expect((await write(admin, `/api/admin/products/${psu}/stock`, { delta: 1, reason: '恢复混合成交库存' })).status()).toBe(200)
    await quote.getByRole('button', { name: '确认并重新核验报价' }).click()
    await quote.getByRole('button', { name: '确认并重新核验报价' }).click()
    await expect(quote).toContainText('当前报价已确认')
    const forged = await write(page.request, '/api/orders', { revision: original.revision, itemIds: [],
      configurationIds: [original.configurations[0].id], quoteToken: '0'.repeat(64), requestKey: `split-${fixture}`,
      configurationItems: [selections[0]] })
    expect(forged.status()).toBe(400)
    // A refused server response preserves the cart; the exact request can safely be retried.
    await page.route('**/api/orders', route => route.fulfill({ status: 503, contentType: 'application/json',
      body: JSON.stringify({ code: 'CART_UNAVAILABLE', message: '数据库暂不可用' }) }))
    await quote.getByRole('button', { name: '创建订单', exact: true }).click()
    await expect(quote).toContainText('下单结果暂未确认')
    expect((await (await page.request.get('/api/cart')).json()).data.configurations).toHaveLength(2)
    await page.unroute('**/api/orders')
    // Commit once but return a malformed grouped response. Never display it as a partial success.
    let committed: { orderNumber: string; total: string; configurations: { id: number }[] } | undefined
    await page.route('**/api/orders', async route => {
      const response = await route.fetch()
      expect(response.status()).toBe(200)
      const body = await response.json()
      committed = body.data
      body.data.configurations = []
      await route.fulfill({ response, json: body })
    })
    await quote.getByRole('button', { name: '重试确认上次下单', exact: true }).click()
    await expect(quote).toContainText('下单结果暂未确认')
    await page.unroute('**/api/orders')
    await quote.getByRole('button', { name: '重试确认上次下单', exact: true }).click()
    await expect(page).toHaveURL(new RegExp(`#order/${committed!.orderNumber}$`))
    const result = page.getByRole('main', { name: '订单结果' })
    await expect(result.getByLabel('订单成交总额')).toHaveText('¥79,955.50')
    await expect(result.getByRole('region', { name: '成交单品', exact: true }).locator('tbody tr')).toHaveCount(1)
    for (const group of original.configurations) {
      await expect(result.getByRole('region', { name: `成交配置组 #${group.id}`, exact: true }).locator('tbody tr')).toHaveCount(8)
    }
    const stock = (await (await admin.get(`/api/admin/products/${psu}`)).json()).data
    expect(stock.reservedStock).toBe(101)
    expect(stock.product.stock).toBe(0)
    const remaining = (await (await page.request.get('/api/cart')).json()).data
    expect(remaining.items).toHaveLength(0)
    expect(remaining.configurations).toHaveLength(0)
    const snapshot = (await (await page.request.get(`/api/orders/${committed!.orderNumber}`)).json()).data
    expect((await write(admin, `/api/admin/products/${psu}/price`, { revision: stock.product.revision,
      price: '999.99', reason: '验证历史快照稳定' })).status()).toBe(200)
    const updated = (await (await admin.get(`/api/admin/products/${psu}`)).json()).data.product
    expect((await write(admin, `/api/admin/products/${psu}/status`, { revision: updated.revision, status: 'OFF_SALE' })).status()).toBe(200)
    await page.reload()
    await expect(result.getByLabel('订单成交总额')).toHaveText('¥79,955.50')
    expect((await (await page.request.get(`/api/orders/${committed!.orderNumber}`)).json()).data).toEqual(snapshot)
    await page.screenshot({ path: 'test-results/task17-mixed-order.png', fullPage: true, animations: 'disabled' })
  } finally {
    await page.unroute('**/api/orders')
    for (const id of privateIds) {
      const current = (await (await admin.get(`/api/admin/products/${id}`)).json()).data.product
      if (current.status === 'ON_SALE') await write(admin, `/api/admin/products/${id}/status`, { revision: current.revision, status: 'OFF_SALE' })
    }
    await admin.dispose()
  }
})
