import { test, expect } from '@playwright/test'

test('administrator adjusts price and available stock with reasons, sees conflicts, and shoppers see current values', async ({ page }) => {
  test.setTimeout(60000)
  await page.goto('/#login')
  const login = page.getByRole('dialog', { name: '统一登录' })
  await login.getByLabel('用户名', { exact: true }).fill(process.env.BROWSER_ADMIN_USERNAME!)
  await login.getByLabel('密码', { exact: true }).fill(process.env.BROWSER_ADMIN_PASSWORD!)
  await login.getByRole('button', { name: '登录', exact: true }).click()
  const admin = page.getByRole('dialog', { name: '管理后台' })
  await expect(admin).toBeVisible()
  async function post(path: string, data: unknown) {
    const csrf = (await (await page.request.get('/api/auth/csrf')).json()).data
    return page.request.post(path, { data, headers: { [csrf.headerName]: csrf.token } })
  }
  const sku = `WEB07-${Date.now()}`
  const created = await post('/api/admin/products', {
    sku, category: 'PSU', name: '价格库存浏览器测试电源', brand: '测试品牌', model: 'Demo-750',
    summary: '750W 测试夹具', description: '价格库存浏览器自动化专用', imageUrl: '/images/psu.svg',
    sourceUrl: 'https://example.com/psu', price: '100.50',
    parameters: [{ code: 'ratedPower', value: 750, unit: 'W', sourceUrl: 'https://example.com/psu' }],
  })
  expect(created.status()).toBe(201)
  const id = (await created.json()).data.product.id
  const path = `/api/admin/products/${id}`
  expect((await post(`${path}/status`, { revision: 0, status: 'ON_SALE' })).status()).toBe(200)
  try {
    await admin.getByRole('navigation').getByRole('button', { name: '商品管理' }).click()
    await admin.getByLabel('商品关键词').fill(sku)
    await admin.getByRole('button', { name: '查询', exact: true }).click()
    await admin.getByRole('button', { name: '查看编辑' }).click()
    await expect(admin.getByLabel('新售价（元）')).toBeVisible()
    await admin.getByLabel('商品名称', { exact: true }).fill('尚未保存的资料')
    await admin.getByLabel('新售价（元）').fill('1.234')
    await admin.getByLabel('调价原因').fill('供应商报价更新')
    await admin.getByRole('button', { name: '确认调价', exact: true }).click()
    await expect(admin.getByRole('alert')).toContainText('两位小数')
    await admin.getByLabel('新售价（元）').fill('321.09')
    await admin.getByRole('button', { name: '确认调价', exact: true }).click()
    await expect(admin.getByRole('status')).toContainText('售价已更新')
    await expect(admin.getByLabel('商品名称', { exact: true })).toHaveValue('尚未保存的资料')
    await admin.getByLabel('库存增减量').fill('5')
    await admin.getByRole('button', { name: '确认调整库存' }).click()
    await expect(admin.getByRole('alert')).toContainText('原因')
    await admin.getByLabel('库存调整原因').fill('到货入库')
    await admin.getByRole('button', { name: '确认调整库存' }).click()
    await expect(admin.getByRole('status')).toContainText('可售库存已更新')
    await expect(admin.locator('.admin-product-state')).toContainText('可售库存 5')
    await admin.getByLabel('库存增减量').fill('-6')
    await admin.getByLabel('库存调整原因').fill('盘点减少')
    await admin.getByRole('button', { name: '确认调整库存' }).click()
    await expect(admin.getByRole('alert')).toContainText('可售库存不足')
    const latest = (await (await page.request.get(path)).json()).data
    expect((await post(`${path}/price`, { revision: latest.revision, price: '322.10', reason: '另一窗口调价' })).status()).toBe(200)
    await admin.getByLabel('库存增减量').fill('1')
    await admin.getByLabel('库存调整原因').fill('其他窗口调价后的补货')
    await admin.getByRole('button', { name: '确认调整库存' }).click()
    await expect(admin.getByRole('status')).toContainText('可售库存已更新')
    await admin.getByLabel('新售价（元）').fill('1.00')
    await admin.getByLabel('调价原因').fill('旧页面调价')
    await admin.getByRole('button', { name: '确认调价', exact: true }).click()
    await expect(admin.getByRole('alert')).toContainText('重新载入')
    await admin.getByRole('button', { name: '重新载入', exact: true }).click()
    await expect(admin.locator('.admin-product-state')).toContainText('322.10')
    const saved = (await (await page.request.get(path)).json()).data
    const { name, brand, model, summary, imageUrl, sourceUrl, priceNote } = saved.product
    expect((await post(path, { name, brand, model, summary: `${summary}，其他管理员已核验`, imageUrl, sourceUrl, priceNote,
      revision: saved.revision, description: saved.description, parameters: saved.parameters })).status()).toBe(200)
    await admin.getByLabel('库存增减量').fill('1')
    await admin.getByLabel('库存调整原因').fill('并发资料变更后的补货')
    await admin.getByRole('button', { name: '确认调整库存' }).click()
    await expect(admin.getByRole('status')).toContainText('商品资料已有其他变更')
    await admin.getByRole('button', { name: '保存修改', exact: true }).click()
    await expect(admin.getByRole('alert')).toContainText('重新载入')
    await admin.getByRole('button', { name: '重新载入', exact: true }).click()
    await expect(admin.getByLabel('摘要', { exact: true })).toHaveValue(`${summary}，其他管理员已核验`)
    await admin.locator('.admin-section-head').scrollIntoViewIfNeeded()
    await page.screenshot({ path: '../.local/task-07-price-stock.png', fullPage: true, animations: 'disabled' })
    await admin.getByRole('button', { name: '返回商品列表' }).click()
    await expect(admin.getByRole('row').filter({ hasText: sku })).toContainText('322.10')
    await expect(admin.getByRole('row').filter({ hasText: sku })).toContainText('可售 7')
    await page.goto('/')
    const card = page.locator('.product-card').filter({ hasText: '价格库存浏览器测试电源' })
    await expect(card).toContainText('322.10')
    await expect(card).toContainText('库存 7 件')
    await card.getByRole('heading').getByRole('link').click()
    await expect(page.getByRole('dialog', { name: '商品详情' })).toContainText('322.10')
  } finally {
    const current = (await (await page.request.get(path)).json()).data
    await post(`${path}/status`, { revision: current.revision, status: 'OFF_SALE' })
  }
})
