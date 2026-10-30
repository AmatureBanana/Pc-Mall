import { expect, test } from '@playwright/test'

test('SYS-T01 subset: guest sees products from the real backend', async ({ page, request }) => {
  const response = await request.get('/api/products')
  expect(response.ok()).toBeTruthy()
  const { data } = await response.json()
  expect(data).toHaveLength(12)
  await page.goto('/')
  await expect(page.getByRole('heading', { name: 'Intel Core i5-14600KF', exact: true })).toBeVisible()
  const card = page.getByRole('article').filter({ has: page.getByRole('heading', { name: data[0].name, exact: true }) })
  await expect(card.getByText('CPU', { exact: true })).toBeVisible()
  await expect(card.getByText('1,499.00', { exact: false })).toBeVisible()
  await expect(card.getByText('库存 12 件')).toBeVisible()
  await expect(page.getByRole('article')).toHaveCount(12)
  await page.screenshot({ path: '../docs/evidence/task-01-storefront.png', fullPage: true })
})

test('request failure is explicit and retry recovers from the real backend', async ({ page }) => {
  let failed = false
  await page.route('**/api/products', async route => {
    if (!failed) {
      failed = true
      await route.fulfill({ status: 503, contentType: 'application/json', body: '{"code":"CATALOG_UNAVAILABLE"}' })
    } else { await route.continue() }
  })
  await page.goto('/')
  await expect(page.getByRole('heading', { name: '商品加载失败' })).toBeVisible()
  await expect(page.getByText('没有找到符合条件的商品')).toHaveCount(0)
  await expect(page.getByRole('article')).toHaveCount(0)
  await page.getByRole('button', { name: '重新加载' }).click()
  await expect(page.getByRole('article')).toHaveCount(12)
})

test('a successful empty response is different from a failed request', async ({ page }) => {
  await page.route('**/api/products', route => route.fulfill({ json: { data: [] } }))
  await page.goto('/')
  await expect(page.getByText('没有找到符合条件的商品')).toBeVisible()
  await expect(page.getByRole('heading', { name: '商品加载失败' })).toHaveCount(0)
})

test('mobile layout keeps database products readable without horizontal page overflow', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto('/')
  await expect(page.getByRole('article')).toHaveCount(12)
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy()
  await page.screenshot({ path: '../docs/evidence/task-01-mobile.png', fullPage: true })
})
