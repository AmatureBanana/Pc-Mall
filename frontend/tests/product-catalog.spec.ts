import { expect, test } from '@playwright/test'

test('SYS-T01 catalog: category and search query the real backend', async ({ page }) => {
  await page.goto('/')
  await expect(page.getByRole('article')).toHaveCount(12)
  const categoryResponse = page.waitForResponse(response => new URL(response.url()).searchParams.get('category') === 'MEMORY')
  await page.getByRole('navigation', { name: '硬件分类' }).getByRole('button', { name: '内存', exact: true }).click()
  expect((await categoryResponse).ok()).toBeTruthy()
  await expect(page.getByRole('article')).toHaveCount(1)
  await page.getByRole('textbox', { name: '搜索商品' }).fill('kf560c36bbek2-32')
  const searchResponse = page.waitForResponse(response => new URL(response.url()).searchParams.get('keyword') === 'kf560c36bbek2-32')
  await page.getByRole('button', { name: '搜索', exact: true }).click()
  expect((await searchResponse).ok()).toBeTruthy()
  await expect(page.getByRole('article')).toHaveCount(1)
  await page.getByRole('textbox', { name: '搜索商品' }).fill('不存在的商品')
  await page.getByRole('button', { name: '搜索', exact: true }).click()
  await expect(page.getByText('没有找到符合条件的商品')).toBeVisible()
  await expect(page.getByRole('heading', { name: '商品加载失败' })).toHaveCount(0)
  await page.getByRole('button', { name: '清除筛选' }).click()
  await expect(page.getByRole('article')).toHaveCount(12)
})

test('SYS-T01 catalog: guest opens memory details and can reload the direct URL', async ({ page }) => {
  await page.goto('/')
  await page.getByRole('link', { name: '金士顿 FURY Beast 32GB (16G×2)', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '商品详情' })
  await expect(dialog.getByRole('heading', { name: '金士顿 FURY Beast 32GB (16G×2)' })).toBeVisible()
  await expect(dialog.getByText('KINGSTON-KF560C36BBEK2-32', { exact: true })).toBeVisible()
  await expect(dialog.getByText('KF560C36BBEK2-32', { exact: true })).toBeVisible()
  await expect(dialog.getByRole('row', { name: /套装总标称容量/ })).toContainText('32 GB')
  await expect(dialog.getByRole('row', { name: /实际内存条数/ })).toContainText('2 条')
  await expect(dialog.getByText('库存 20 件')).toBeVisible()
  await expect(dialog.getByText('/images/memory.svg', { exact: true })).toBeVisible()
  await page.reload()
  await expect(dialog.getByRole('row', { name: /套装总标称容量/ })).toContainText('32 GB')
  await expect(dialog).toBeInViewport()
  await page.screenshot({ path: '../docs/evidence/task-02-memory-detail.png', fullPage: true, animations: 'disabled' })
  await dialog.getByRole('button', { name: '关闭详情' }).click()
  await expect(dialog).toHaveCount(0)
  await expect(page.getByRole('article')).toHaveCount(12)
})

test('all eight categories are reachable through the real catalog', async ({ page }) => {
  await page.goto('/')
  const nav = page.getByRole('navigation', { name: '硬件分类' })
  for (const [label, count] of [['CPU', 2], ['主板', 2], ['独显', 2], ['内存', 1], ['SSD', 2], ['电源', 1], ['机箱', 1], ['散热器', 1]] as const) {
    await nav.getByRole('button', { name: label, exact: true }).click()
    await expect(page.getByRole('article')).toHaveCount(count)
    await expect(nav.getByRole('button', { name: label, exact: true })).toHaveAttribute('aria-pressed', 'true')
  }
})

test('missing and invalid direct product URLs show explicit feedback', async ({ page }) => {
  for (const [id, message] of [['9223372036854775807', '商品不存在'], ['abc', '商品 ID 无效'], ['-1', '商品 ID 无效']]) {
    await page.goto(`/#product/${id}`)
    const dialog = page.getByRole('dialog', { name: '商品详情' })
    await expect(dialog.getByRole('alert')).toContainText(message!)
    await expect(dialog.getByRole('button', { name: '重新加载详情' })).toHaveCount(0)
  }
})

test('an off-sale response is distinct from a missing product', async ({ page }) => {
  // Browser boundary fixture; the real database transition is covered by ProductCatalogTest.
  await page.route('**/api/products/7', route => route.fulfill({ status: 409, json: { code: 'PRODUCT_NOT_SELECTABLE' } }))
  await page.goto('/#product/7')
  const dialog = page.getByRole('dialog', { name: '商品详情' })
  await expect(dialog.getByRole('alert')).toContainText('商品已下架')
  await expect(dialog.getByRole('button', { name: '重新加载详情' })).toHaveCount(0)
})

test('detail request failure clears content and retry reads the real backend', async ({ page }) => {
  let fail = false
  await page.route('**/api/products/7', async route => {
    if (fail) await route.fulfill({ status: 503, json: { code: 'CATALOG_UNAVAILABLE' } })
    else await route.continue()
  })
  await page.goto('/#product/7')
  const dialog = page.getByRole('dialog', { name: '商品详情' })
  await expect(dialog.getByRole('row', { name: /套装总标称容量/ })).toContainText('32 GB')
  fail = true
  await dialog.getByRole('button', { name: '刷新价格与库存' }).click()
  await expect(dialog.getByRole('alert')).toContainText('商品详情暂时不可用')
  await expect(dialog.getByRole('table')).toHaveCount(0)
  fail = false
  await dialog.getByRole('button', { name: '重新加载详情' }).click()
  await expect(dialog.getByRole('row', { name: /实际内存条数/ })).toContainText('2 条')
})

test('category request failure can retry the same selection', async ({ page }) => {
  await page.goto('/')
  await expect(page.getByRole('article')).toHaveCount(12)
  await page.route('**/api/products?category=SSD', route => route.fulfill({ status: 503, json: { code: 'CATALOG_UNAVAILABLE' } }))
  await page.getByRole('navigation', { name: '硬件分类' }).getByRole('button', { name: 'SSD', exact: true }).click()
  await expect(page.getByRole('heading', { name: '商品加载失败' })).toBeVisible()
  await expect(page.getByText('没有找到符合条件的商品')).toHaveCount(0)
  await page.unroute('**/api/products?category=SSD')
  await page.getByRole('button', { name: '重新加载', exact: true }).click()
  await expect(page.getByRole('article')).toHaveCount(2)
  await expect(page.getByRole('heading', { name: '三星 990 PRO 1TB', exact: true })).toBeVisible()
})

test('a late response cannot replace the newer category selection', async ({ page, request }) => {
  const cpuResponse = await request.get('/api/products?category=CPU')
  const cpu = await cpuResponse.json()
  let release!: () => void
  let started!: () => void
  const gate = new Promise<void>(resolve => { release = resolve })
  const pending = new Promise<void>(resolve => { started = resolve })
  await page.route('**/api/products?category=CPU', async route => {
    started()
    await gate
    await route.fulfill({ json: cpu })
  })
  await page.goto('/')
  const nav = page.getByRole('navigation', { name: '硬件分类' })
  await nav.getByRole('button', { name: 'CPU', exact: true }).click()
  await pending
  await nav.getByRole('button', { name: '内存', exact: true }).click()
  await expect(page.getByRole('article')).toHaveCount(1)
  release()
  await page.unrouteAll({ behavior: 'wait' })
  await expect(page.getByRole('heading', { name: '金士顿 FURY Beast 32GB (16G×2)', exact: true })).toBeVisible()
  await expect(page.getByRole('article')).toHaveCount(1)
})
