import { expect, test } from '@playwright/test'

test('editing a configuration automatically saves only this tab references and survives reload', async ({ page }) => {
  await page.goto('/#diy')
  await page.getByRole('button', { name: '选择 Intel Core i5-14600KF', exact: true }).click()
  const configuration = page.getByRole('region', { name: '当前配置' })
  await expect(configuration.getByRole('button', { name: '转入购物车', exact: true })).toBeEnabled()
  expect(await page.evaluate(() => JSON.parse(sessionStorage.getItem('pc-mall.diy.workspace.v1')!)))
    .toEqual({ version: 1, revision: 1, selections: [{ category: 'CPU', productId: 1, source: 'DIY' }] })
  await page.reload()
  await expect(configuration.getByRole('link', { name: 'Intel Core i5-14600KF', exact: true })).toBeVisible()
  await expect(configuration.getByLabel('当前参考总价')).toHaveText('¥1,499.00')
})

test('changing to a two-slot motherboard keeps the old memory quantity visible until reduced', async ({ page }) => {
  await page.route('**/api/compatibility/check', async route => {
    const response = await route.fetch()
    if (route.request().postDataJSON().MOTHERBOARD !== 4 || !response.ok()) {
      await route.fulfill({ response })
      return
    }
    const body = await response.json()
    body.data.memorySlots = 2
    await route.fulfill({ response, json: body })
  })
  await page.goto('/#diy')
  const categories = page.getByRole('navigation', { name: 'DIY 硬件分类' })
  const memoryCategory = categories.getByRole('button', { name: /^内存 / })
  const boardCategory = categories.getByRole('button', { name: /^主板 / })
  const counter = page.getByRole('group', { name: '内存数量' })
  const quantity = counter.getByLabel('当前内存根数')
  const increase = counter.getByRole('button', { name: '增加内存数量' })
  const decrease = counter.getByRole('button', { name: '减少内存数量' })
  await memoryCategory.click()
  await page.getByRole('button', { name: '选择 金士顿 FURY Beast 16GB', exact: true }).click()
  await expect(quantity).toHaveText('1')
  await expect(increase).toBeDisabled()
  await expect(decrease).toBeDisabled()
  await boardCategory.click()
  await page.getByRole('button', { name: /^选择 华硕 TUF B760M/ }).click()
  await expect(page.getByText('主板 4 槽 · 当前最多 4 根')).toBeVisible()
  await memoryCategory.click()
  for (const count of [2, 3, 4]) {
    await increase.click()
    await expect(quantity).toHaveText(String(count))
  }
  await expect(increase).toBeDisabled()
  await boardCategory.click()
  await page.getByRole('button', { name: /^选择 微星 MAG B650M/ }).click()
  await expect(page.getByText('主板 2 槽 · 当前最多 2 根')).toBeVisible()
  await memoryCategory.click()
  await expect(quantity).toHaveText('4')
  await expect(increase).toBeDisabled()
  await expect(page.getByText('当前选择 4 根，请调低数量或更换主板。')).toBeVisible()
  await decrease.click()
  await expect(quantity).toHaveText('3')
  await expect(page.getByText('当前选择 3 根，请调低数量或更换主板。')).toBeVisible()
  await decrease.click()
  await expect(quantity).toHaveText('2')
  await expect(page.getByText('当前选择 2 根，请调低数量或更换主板。')).not.toBeVisible()
})

test('an automatic save failure keeps editing available and never claims a successful save', async ({ page }) => {
  await page.addInitScript(() => {
    Storage.prototype.setItem = () => { throw new DOMException('Storage unavailable', 'QuotaExceededError') }
  })
  await page.goto('/#diy')
  await page.getByRole('button', { name: '选择 Intel Core i5-14600KF', exact: true }).click()
  const configuration = page.getByRole('region', { name: '当前配置' })
  await expect(configuration.getByRole('status')).toContainText('配置尚未保存')
  await expect(configuration.getByRole('status')).not.toContainText('已保存到当前标签页')
  await expect(configuration.getByRole('link', { name: 'Intel Core i5-14600KF', exact: true })).toBeVisible()
  await configuration.getByRole('button', { name: '移除 CPU', exact: true }).click()
  await expect(configuration).toContainText('已选择 0 / 8 项')
})

test('F02 workspace: arbitrary order, replacement, removal, cancelled clear and tab refresh', async ({ page }) => {
  await page.goto('/')
  await page.getByRole('link', { name: 'DIY 装机', exact: true }).click()
  const categories = page.getByRole('navigation', { name: 'DIY 硬件分类' })
  const configuration = page.getByRole('region', { name: '当前配置' })
  await expect(categories.getByRole('button')).toHaveCount(8)
  await categories.getByRole('button', { name: /内存/ }).click()
  await page.getByRole('button', { name: '选择 金士顿 FURY Beast 16GB', exact: true }).click()
  await expect(configuration).toContainText('¥449.50')
  await categories.getByRole('button', { name: /^CPU / }).click()
  await page.getByRole('button', { name: '选择 Intel Core i5-14600KF', exact: true }).click()
  await expect(configuration.getByLabel('当前参考总价')).toHaveText('¥1,948.50')
  await page.getByRole('button', { name: '选择 AMD Ryzen 7 7800X3D', exact: true }).click()
  await expect(configuration.getByLabel('当前参考总价')).toHaveText('¥2,848.50')
  await expect(configuration).not.toContainText('Intel Core i5-14600KF')
  await configuration.getByRole('button', { name: '移除 内存', exact: true }).click()
  await expect(configuration.getByLabel('当前参考总价')).toHaveText('¥2,399.00')
  await configuration.getByRole('button', { name: '清空配置', exact: true }).click()
  const confirm = page.getByRole('dialog', { name: '清空当前配置？' })
  await confirm.getByRole('button', { name: '取消', exact: true }).click()
  await expect(configuration).toContainText('AMD Ryzen 7 7800X3D')
  await page.reload()
  await expect(configuration).toContainText('AMD Ryzen 7 7800X3D')
  await expect(configuration.getByLabel('当前参考总价')).toHaveText('¥2,399.00')
  await expect(page.getByRole('region', { name: '兼容分项诊断' })).toContainText('尚未选齐')
  await page.getByLabel('候选商品排序').selectOption('price-desc')
  await configuration.getByRole('button', { name: '清空配置', exact: true }).click()
  await confirm.getByRole('button', { name: '确认清空', exact: true }).click()
  await expect(configuration.getByLabel('当前参考总价')).toHaveText('¥0.00')
  await expect(page.getByLabel('候选商品排序')).toHaveValue('default')
  await page.reload()
  await expect(configuration).toContainText('已选择 0 / 8 项')
})

test('quotation failure retains references through reload and retry, without showing an old total', async ({ page }) => {
  await page.goto('/#diy')
  const configuration = page.getByRole('region', { name: '当前配置' })
  await page.getByRole('button', { name: '选择 Intel Core i5-14600KF', exact: true }).click()
  await expect(configuration.getByLabel('当前参考总价')).toHaveText('¥1,499.00')
  await page.route('**/api/diy/quote?*', route => route.fulfill({ status: 503, json: { code: 'CATALOG_UNAVAILABLE' } }))
  await configuration.getByRole('button', { name: '刷新价格与库存' }).click()
  await expect(configuration.getByRole('alert')).toContainText('已保留当前选择')
  await expect(configuration.getByLabel('当前参考总价')).toHaveText('报价暂不可用')
  await page.reload()
  await expect(configuration).toContainText('已选择 1 / 8 项')
  await expect(configuration.getByRole('alert')).toContainText('已保留当前选择')
  await page.unroute('**/api/diy/quote?*')
  await configuration.getByRole('button', { name: '重试参考报价' }).click()
  await expect(configuration.getByLabel('当前参考总价')).toHaveText('¥1,499.00')
  const saved = await page.evaluate(() => JSON.parse(sessionStorage.getItem('pc-mall.diy.workspace.v1')!))
  expect(saved).toEqual({ version: 1, revision: 1, selections: [{ category: 'CPU', productId: 1, source: 'DIY' }] })
})

test('a late quotation cannot overwrite a replacement configuration', async ({ page, request }) => {
  const oldQuote = await (await request.get('/api/diy/quote?revision=1&CPU=1')).json()
  let release!: () => void
  let started!: () => void
  const gate = new Promise<void>(resolve => { release = resolve })
  const pending = new Promise<void>(resolve => { started = resolve })
  await page.route('**/api/diy/quote?*', async route => {
    if (new URL(route.request().url()).searchParams.get('revision') !== '1') { await route.continue(); return }
    started()
    await gate
    await route.fulfill({ json: oldQuote })
  })
  await page.goto('/#diy')
  const configuration = page.getByRole('region', { name: '当前配置' })
  await page.getByRole('button', { name: '选择 Intel Core i5-14600KF', exact: true }).click()
  await pending
  await expect(configuration.getByLabel('当前参考总价')).toHaveText('正在核价…')
  await page.getByRole('button', { name: '选择 AMD Ryzen 7 7800X3D', exact: true }).click()
  await expect(configuration.getByLabel('当前参考总价')).toHaveText('¥2,399.00')
  release()
  await page.unrouteAll({ behavior: 'wait' })
  await expect(configuration.getByLabel('当前参考总价')).toHaveText('¥2,399.00')
  await expect(configuration).toContainText('修订 2')
  await expect(configuration).not.toContainText('Intel Core i5-14600KF')
})

test('all eight sales units remain editable with complete core compatibility and catalog details', async ({ page }) => {
  await page.goto('/#diy')
  const categories = page.getByRole('navigation', { name: 'DIY 硬件分类' })
  const configuration = page.getByRole('region', { name: '当前配置' })
  const choices = [
    ['CPU', 'Intel Core i5-14600KF'], ['主板', '华硕 TUF B760M-PLUS WIFI'],
    ['显卡', 'NVIDIA GeForce RTX 4070 SUPER'], ['内存', '金士顿 FURY Beast 16GB'],
    ['硬盘', '三星 990 PRO 1TB'], ['电源', '海盗船 RM750e 全模组电源'],
    ['机箱', '九州风神 CC560 中塔机箱'], ['CPU散热器', '九州风神 AK400 风冷散热器'],
  ]
  for (const [category, name] of choices) {
    await categories.getByRole('button', { name: new RegExp(`^${category} `) }).click()
    await page.getByRole('button', { name: `选择 ${name}`, exact: true }).click()
    await expect(configuration.getByRole('link', { name, exact: true })).toBeVisible()
    if (category === '主板') {
      await configuration.getByRole('button', { name: '替换 CPU', exact: true }).click()
      await page.screenshot({ path: '../docs/evidence/task-11-workspace.png', fullPage: true, animations: 'disabled' })
    }
  }
  await expect(configuration).toContainText('已选择 8 / 8 项')
  await expect(configuration.getByLabel('当前参考总价')).toHaveText('¥10,102.50')
  await expect(page.getByRole('region', { name: '兼容分项诊断' })).toContainText('核心检测通过')
  await expect(configuration).not.toContainText('核心通过不代表可售、有库存或全面兼容')
  await configuration.getByRole('link', { name: '金士顿 FURY Beast 16GB', exact: true }).click()
  const detail = page.getByRole('dialog', { name: '商品详情' })
  await expect(detail.getByRole('row', { name: /实际内存条数/ })).toContainText('1 条')
  await detail.getByRole('button', { name: '关闭详情' }).click()
  await expect(page).toHaveURL(/#diy$/)
  await expect(configuration).toContainText('已选择 8 / 8 项')
})

test('off-sale and out-of-stock quotation feedback retains the selected SKU and excludes its amount', async ({ page, request }) => {
  const response = await (await request.get('/api/diy/quote?revision=1&CPU=1')).json()
  await page.goto('/#diy')
  const configuration = page.getByRole('region', { name: '当前配置' })
  await page.getByRole('button', { name: '选择 Intel Core i5-14600KF', exact: true }).click()
  await expect(configuration.getByLabel('当前参考总价')).toHaveText('¥1,499.00')
  // Browser boundary fixtures; real MySQL state transitions are covered by DiyWorkspaceTest.
  for (const [state, message] of [['OFF_SALE', '商品已下架，请替换。'], ['OUT_OF_STOCK', '商品暂时缺货，请替换。']]) {
    await page.route('**/api/diy/quote?*', route => route.fulfill({ json: { data: {
      ...response.data, total: null, validSubtotal: '0.00', items: response.data.items.map((item: object) =>
        ({ ...item, state, message, amount: null })),
    } } }))
    await configuration.getByRole('button', { name: '刷新价格与库存' }).click()
    await expect(configuration).toContainText(message!)
    await expect(configuration.getByLabel('当前参考总价')).toHaveText('配置存在问题')
    await expect(configuration).toContainText('不是完整配置报价')
    await expect(configuration).toContainText('Intel Core i5-14600KF')
    await page.unroute('**/api/diy/quote?*')
  }
})

test('an authenticated account can enter the same public workspace', async ({ page }) => {
  await page.goto('/#login')
  const login = page.getByRole('dialog', { name: '统一登录' })
  await login.getByLabel('用户名', { exact: true }).fill(process.env.BROWSER_ADMIN_USERNAME!)
  await login.getByLabel('密码', { exact: true }).fill(process.env.BROWSER_ADMIN_PASSWORD!)
  await login.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page.getByRole('dialog', { name: '管理后台' })).toBeVisible()
  await page.goto('/#diy')
  await page.getByRole('button', { name: '选择 Intel Core i5-14600KF', exact: true }).click()
  await expect(page.getByRole('region', { name: '当前配置' }).getByLabel('当前参考总价')).toHaveText('¥1,499.00')
})

test('damaged tab storage is reported without deleting it and a new edit can recover', async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem('pc-mall.diy.workspace.v1', '{broken'))
  await page.goto('/#diy')
  await expect(page.getByRole('alert')).toContainText('原存储未删除')
  expect(await page.evaluate(() => sessionStorage.getItem('pc-mall.diy.workspace.v1'))).toBe('{broken')
  await page.getByRole('button', { name: '选择 Intel Core i5-14600KF', exact: true }).click()
  await expect(page.getByRole('region', { name: '当前配置' }).getByLabel('当前参考总价')).toHaveText('¥1,499.00')
  await expect(page.getByText('原存储未删除', { exact: false })).toHaveCount(0)
})

test('unavailable session storage does not prevent editing or clear the in-memory configuration', async ({ page }) => {
  await page.addInitScript(() => {
    Storage.prototype.setItem = () => { throw new DOMException('Quota exceeded', 'QuotaExceededError') }
  })
  await page.goto('/#diy')
  await page.getByRole('button', { name: '选择 Intel Core i5-14600KF', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('当前选择仍可编辑')
  await expect(page.getByRole('region', { name: '当前配置' }).getByLabel('当前参考总价')).toHaveText('¥1,499.00')
})
