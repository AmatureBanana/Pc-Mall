import { expect, test, type APIRequestContext } from '@playwright/test'
import type { Product } from '../src/catalog'

async function catalogSize(request: APIRequestContext, query = '') {
  const response = await request.get(`/api/products${query}`)
  expect(response.ok()).toBeTruthy()
  const { data } = await response.json()
  expect(data.length).toBeGreaterThan(0)
  return data.length
}

for (const view of [
  { path: '/#catalog', nav: '硬件分类', region: '商品列表' },
  { path: '/#diy', nav: 'DIY 硬件分类', region: '候选商品' },
]) {
  test(`${view.nav} groups power supplies by their rated watts`, async ({ page }) => {
    await page.route('**/api/products?category=PSU', async route => {
      const response = await route.fetch()
      const body: { data: Product[] } = await response.json()
      const psu = body.data.find(product => product.sku === 'CORSAIR-CP-9020295')!
      body.data.push(
        { ...psu, id: 100001, brand: '测试品牌', name: '甲型号', ratedPowerWatts: 1000 },
        { ...psu, id: 100002, brand: '测试品牌', name: '乙型号', ratedPowerWatts: 650 },
        { ...psu, id: 100003, brand: '测试品牌', name: '丙型号', ratedPowerWatts: 750 },
        { ...psu, id: 100004, brand: '测试品牌', name: '功率待核验型号', ratedPowerWatts: undefined },
      )
      await route.fulfill({ response, json: body })
    })
    await page.goto(view.path)
    const nav = page.getByRole('navigation', { name: view.nav, exact: true })
    const region = page.getByRole('region', { name: view.region, exact: true })
    const ratedPower = region.getByRole('group', { name: '额定功率', exact: true })
    const products = region.getByRole('article').getByRole('heading')
    await expect(ratedPower).toHaveCount(0)
    await nav.getByRole('button', { name: /^电源/ }).click()
    await expect(ratedPower.getByRole('button')).toHaveText(['全部', '650W', '750W', '1000W'])
    await expect(products).toHaveCount(5)
    await ratedPower.getByRole('button', { name: '750W', exact: true }).click()
    await expect(products).toHaveText(['海盗船 RM750e 全模组电源', '丙型号'])
    await region.getByRole('group', { name: '品牌', exact: true }).getByRole('button', { name: '测试品牌' }).click()
    await expect(products).toHaveText(['丙型号'])
    await ratedPower.getByRole('button', { name: '650W', exact: true }).click()
    await expect(products).toHaveText(['乙型号'])
    await ratedPower.getByRole('button', { name: '1000W', exact: true }).click()
    await expect(products).toHaveText(['甲型号'])
    await nav.getByRole('button', { name: /^内存/ }).click()
    await expect(ratedPower).toHaveCount(0)
    await nav.getByRole('button', { name: /^电源/ }).click()
    await expect(ratedPower.getByRole('button', { name: '全部', exact: true })).toHaveAttribute('aria-pressed', 'true')
    await expect(products).toHaveCount(5)
  })

  test(`${view.nav} filters memory and SSD by their capacity group`, async ({ page }) => {
    await page.route('**/api/products?category=SSD', async route => {
      const response = await route.fetch()
      const body: { data: Product[] } = await response.json()
      const ssd = body.data[0]
      body.data.push(
        { ...ssd, id: 100001, name: '512 GB 测试硬盘', capacityGb: 512 },
        { ...ssd, id: 100002, name: '1024 GB 测试硬盘', capacityGb: 1024 },
      )
      await route.fulfill({ response, json: body })
    })
    await page.goto(view.path)
    const nav = page.getByRole('navigation', { name: view.nav, exact: true })
    const region = page.getByRole('region', { name: view.region, exact: true })
    const capacities = region.getByRole('group', { name: '容量', exact: true })
    const products = region.getByRole('article').getByRole('heading')
    await expect(capacities).toHaveCount(0)
    await nav.getByRole('button', { name: /^内存/ }).click()
    await expect(capacities.getByRole('button')).toHaveText(['全部', '8G', '16G', '24G', '32G'])
    await capacities.getByRole('button', { name: '8G', exact: true }).click()
    await expect(products).toHaveCount(0)
    await capacities.getByRole('button', { name: '16G', exact: true }).click()
    await expect(products).toHaveText(['金士顿 FURY Beast 16GB'])

    await nav.getByRole('button', { name: /^硬盘/ }).click()
    await expect(capacities.getByRole('button')).toHaveText(['全部', '500G', '1T', '2T', '4T', '8T'])
    await expect(capacities.getByRole('button', { name: '全部', exact: true })).toHaveAttribute('aria-pressed', 'true')
    await capacities.getByRole('button', { name: '500G', exact: true }).click()
    await expect(products).toHaveText(['512 GB 测试硬盘'])
    await capacities.getByRole('button', { name: '1T', exact: true }).click()
    await expect(products).toHaveCount(3)
    await expect(products.filter({ hasText: '1024 GB 测试硬盘' })).toBeVisible()
    await capacities.getByRole('button', { name: '8T', exact: true }).click()
    await expect(products).toHaveCount(0)
    await capacities.getByRole('button', { name: '全部', exact: true }).click()
    await expect(products).toHaveCount(4)
  })
}

test('SYS-T01 catalog: category and search query the real backend', async ({ page, request }) => {
  const allProducts = await catalogSize(request)
  const memoryProducts = await catalogSize(request, '?category=MEMORY')
  expect(memoryProducts).toBeLessThan(allProducts)
  await page.goto('/#catalog')
  await expect(page.getByRole('article')).toHaveCount(allProducts)
  const categoryResponse = page.waitForResponse(response => new URL(response.url()).searchParams.get('category') === 'MEMORY')
  await page.getByRole('navigation', { name: '硬件分类' }).getByRole('button', { name: '内存', exact: true }).click()
  expect((await categoryResponse).ok()).toBeTruthy()
  await expect(page.getByRole('article')).toHaveCount(memoryProducts)
  await page.getByRole('textbox', { name: '搜索商品' }).fill('kf560c36bbe2-16')
  const searchResponse = page.waitForResponse(response => new URL(response.url()).searchParams.get('keyword') === 'kf560c36bbe2-16')
  await page.getByRole('button', { name: '搜索', exact: true }).click()
  expect((await searchResponse).ok()).toBeTruthy()
  await expect(page.getByRole('article')).toHaveCount(1)
  await page.getByRole('textbox', { name: '搜索商品' }).fill('不存在的商品')
  await page.getByRole('button', { name: '搜索', exact: true }).click()
  await expect(page.getByText('没有找到符合条件的商品')).toBeVisible()
  await expect(page.getByRole('heading', { name: '商品加载失败' })).toHaveCount(0)
  await page.getByRole('button', { name: '清除筛选' }).click()
  await expect(page.getByRole('article')).toHaveCount(allProducts)
})

test('the catalog search above filters matches models and detailed parameters in the selected category', async ({ page }) => {
  await page.goto('/#catalog')
  const category = page.getByRole('navigation', { name: '硬件分类', exact: true })
  await category.getByRole('button', { name: '内存', exact: true }).click()
  const catalog = page.getByRole('region', { name: '商品列表', exact: true })
  const capacity = catalog.getByRole('group', { name: '容量', exact: true }).getByRole('button', { name: '16G', exact: true })
  await capacity.click()
  const search = catalog.getByRole('search', { name: '商品列表搜索', exact: true })
  const sort = catalog.getByRole('combobox', { name: '商品排序', exact: true })
  const searchBox = (await search.boundingBox())!
  const brandsBox = (await catalog.getByRole('group', { name: '品牌', exact: true }).boundingBox())!
  const sortBox = (await sort.boundingBox())!
  expect(searchBox.y + searchBox.height).toBeLessThanOrEqual(brandsBox.y)
  expect(searchBox.x + searchBox.width).toBeCloseTo(sortBox.x + sortBox.width, 0)

  const query = search.getByRole('textbox')
  await query.fill('4800 mt/s')
  await search.getByRole('button').click()
  await expect(catalog.getByRole('article').getByRole('heading')).toHaveText(['金士顿 FURY Beast 16GB'])
  await expect(capacity).toHaveAttribute('aria-pressed', 'true')
  await query.fill('kf560c36bbe2-16')
  await query.press('Enter')
  await expect(catalog.getByRole('article').getByRole('heading')).toHaveText(['金士顿 FURY Beast 16GB'])
  await query.fill('14600KF')
  await query.press('Enter')
  await expect(catalog.getByText('没有找到符合条件的商品')).toBeVisible()
  await expect(category.getByRole('button', { name: '内存', exact: true })).toHaveAttribute('aria-pressed', 'true')
})

test('catalog brand and platform filters combine with price, sorting and search', async ({ page, request }, testInfo) => {
  const allProducts = await catalogSize(request)
  await page.goto('/#catalog')
  const nav = page.getByRole('navigation', { name: '硬件分类' })
  const brands = page.getByRole('group', { name: '品牌', exact: true })
  const platforms = page.getByRole('group', { name: '平台', exact: true })
  const headings = page.getByRole('article').getByRole('heading')
  const sort = page.getByLabel('商品排序', { exact: true })
  await expect(headings).toHaveCount(allProducts)
  await expect(brands).toHaveCount(0)
  await expect(platforms).toHaveCount(0)

  await nav.getByRole('button', { name: '主板', exact: true }).click()
  await expect(platforms.getByRole('button')).toHaveText(['全部', 'Intel', 'AMD'])
  await sort.selectOption('price-asc')
  await expect(headings).toHaveText(['微星 MAG B650M MORTAR WIFI', '华硕 TUF B760M-PLUS WIFI'])
  const brandGroup = (await brands.boundingBox())!
  const count = (await page.getByText('共 2 件商品', { exact: false }).boundingBox())!
  expect(brandGroup.y + brandGroup.height).toBeLessThanOrEqual(count.y)
  await page.screenshot({ path: testInfo.outputPath('catalog-filters-desktop.png'), animations: 'disabled' })
  await brands.getByRole('button', { name: '华硕', exact: true }).click()
  await expect(headings).toHaveText(['华硕 TUF B760M-PLUS WIFI'])
  await platforms.getByRole('button', { name: 'AMD', exact: true }).click()
  await expect(brands.getByRole('button')).toHaveText(['全部', '微星'])
  await expect(brands.getByRole('button', { name: '全部', exact: true })).toHaveAttribute('aria-pressed', 'true')
  await expect(headings).toHaveText(['微星 MAG B650M MORTAR WIFI'])
  await brands.getByRole('button', { name: '微星', exact: true }).click()

  const prices = page.getByRole('complementary', { name: '商品筛选' })
  await prices.getByLabel('最高价').fill('1')
  await prices.getByRole('button', { name: '确定', exact: true }).click()
  await expect(headings).toHaveCount(0)
  await expect(brands.getByRole('button', { name: '微星', exact: true })).toHaveAttribute('aria-pressed', 'true')
  await prices.getByLabel('最高价').clear()
  await prices.getByRole('button', { name: '确定', exact: true }).click()
  await expect(headings).toHaveText(['微星 MAG B650M MORTAR WIFI'])

  await page.getByRole('textbox', { name: '搜索商品' }).fill('B760M')
  await page.getByRole('button', { name: '搜索', exact: true }).click()
  await expect(page.getByText('没有找到符合条件的商品')).toBeVisible()
  await expect(brands.getByRole('button', { name: '微星', exact: true })).toHaveAttribute('aria-pressed', 'true')
  await expect(platforms.getByRole('button', { name: 'AMD', exact: true })).toHaveAttribute('aria-pressed', 'true')
  await page.getByRole('button', { name: '清除筛选' }).click()
  await expect(headings).toHaveCount(allProducts)
  await expect(brands).toHaveCount(0)

  await page.setViewportSize({ width: 390, height: 844 })
  await page.route('**/api/products?category=GPU', async route => {
    const response = await route.fetch()
    const body = await response.json()
    const amd = body.data.find((product: { platform: string }) => product.platform === 'AMD')
    amd.brand = '移动端用于验证品牌筛选按钮自动换行的显卡品牌名称'
    await route.fulfill({ response, json: body })
  })
  await nav.getByRole('button', { name: '显卡', exact: true }).click()
  await expect(platforms.getByRole('button')).toHaveText(['全部', 'NVIDIA', 'AMD', 'Intel'])
  await expect(brands.getByRole('button')).toHaveCount(3)
  const brandButtons = brands.getByRole('button')
  const firstBrand = (await brandButtons.first().boundingBox())!
  const lastBrand = (await brandButtons.last().boundingBox())!
  expect(lastBrand.y).toBeGreaterThan(firstBrand.y)
  expect(lastBrand.x + lastBrand.width).toBeLessThanOrEqual(390)
  await platforms.getByRole('button', { name: 'NVIDIA', exact: true }).click()
  await expect(headings).toHaveText(['NVIDIA GeForce RTX 4070 SUPER'])
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  await page.screenshot({ path: testInfo.outputPath('catalog-filters-mobile.png'), animations: 'disabled' })
  await nav.getByRole('button', { name: '全部商品', exact: true }).click()
  await expect(headings).toHaveCount(allProducts)
  await expect(brands).toHaveCount(0)
})

test('SYS-T01 catalog: guest opens memory details and can reload the direct URL', async ({ page, request }) => {
  const allProducts = await catalogSize(request)
  await page.goto('/#catalog')
  await page.getByRole('link', { name: '金士顿 FURY Beast 16GB', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '商品详情' })
  await expect(dialog.getByRole('heading', { name: '金士顿 FURY Beast 16GB' })).toBeVisible()
  await expect(dialog.getByText('KINGSTON-KF560C36BBE2-16', { exact: true })).toBeVisible()
  await expect(dialog.getByText('KF560C36BBE2-16', { exact: true })).toBeVisible()
  await expect(dialog.getByRole('row', { name: /^内存容量/ })).toContainText('16 GB')
  await expect(dialog.getByRole('row', { name: /实际内存条数/ })).toContainText('1 条')
  await expect(dialog.getByText('库存 40 件')).toBeVisible()
  await expect(dialog.getByText('/images/memory.svg', { exact: true })).toBeVisible()
  await page.reload()
  await expect(dialog.getByRole('row', { name: /^内存容量/ })).toContainText('16 GB')
  await expect(dialog).toBeInViewport()
  await page.screenshot({ path: '../docs/evidence/task-02-memory-detail.png', fullPage: true, animations: 'disabled' })
  await dialog.getByRole('button', { name: '关闭详情' }).click()
  await expect(dialog).toHaveCount(0)
  await expect(page.getByRole('article')).toHaveCount(allProducts)
})

test('all eight categories are reachable through the real catalog', async ({ page }) => {
  await page.goto('/#catalog')
  const nav = page.getByRole('navigation', { name: '硬件分类' })
  for (const [label, count] of [['CPU', 3], ['主板', 2], ['显卡', 2], ['内存', 1], ['硬盘', 2], ['电源', 1], ['机箱', 1], ['CPU散热器', 1]] as const) {
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

test('detail request failure clears content and retry reads the real backend', async ({ page, request }) => {
  const memory = (await (await request.get('/api/products?category=MEMORY&keyword=KF560C36BBE2-16')).json()).data[0]
  let fail = false
  await page.route('**/api/products/' + memory.id, async route => {
    if (fail) await route.fulfill({ status: 503, json: { code: 'CATALOG_UNAVAILABLE' } })
    else await route.continue()
  })
  await page.goto('/#product/' + memory.id)
  const dialog = page.getByRole('dialog', { name: '商品详情' })
  await expect(dialog.getByRole('row', { name: /^内存容量/ })).toContainText('16 GB')
  fail = true
  await dialog.getByRole('button', { name: '刷新价格与库存' }).click()
  await expect(dialog.getByRole('alert')).toContainText('商品详情暂时不可用')
  await expect(dialog.getByRole('table')).toHaveCount(0)
  fail = false
  await dialog.getByRole('button', { name: '重新加载详情' }).click()
  await expect(dialog.getByRole('row', { name: /实际内存条数/ })).toContainText('1 条')
})

test('category request failure can retry the same selection', async ({ page }) => {
  await page.goto('/#catalog')
  await expect(page.getByRole('article')).toHaveCount(13)
  await page.route('**/api/products?category=SSD', route => route.fulfill({ status: 503, json: { code: 'CATALOG_UNAVAILABLE' } }))
  await page.getByRole('navigation', { name: '硬件分类' }).getByRole('button', { name: '硬盘', exact: true }).click()
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
  await page.goto('/#catalog')
  const nav = page.getByRole('navigation', { name: '硬件分类' })
  await nav.getByRole('button', { name: 'CPU', exact: true }).click()
  await pending
  await nav.getByRole('button', { name: '内存', exact: true }).click()
  await expect(page.getByRole('article')).toHaveCount(1)
  release()
  await page.unrouteAll({ behavior: 'wait' })
  await expect(page.getByRole('heading', { name: '金士顿 FURY Beast 16GB', exact: true })).toBeVisible()
  await expect(page.getByRole('article')).toHaveCount(1)
})
