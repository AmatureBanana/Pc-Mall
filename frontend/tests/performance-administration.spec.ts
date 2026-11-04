import { test, expect, type Route } from '@playwright/test'
import { readFileSync } from 'node:fs'

const references = JSON.parse(readFileSync(new URL('../../data/performance-v1/references.json', import.meta.url), 'utf8'))
test('administrator maintains traceable scores without losing product drafts and sees stale reference conflicts', async ({ page }) => {
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
  const candidates = (await (await page.request.get('/api/products?keyword=INTEL-I5-14600K')).json()).data
  const base = candidates.find((p: { sku: string }) => p.sku === 'INTEL-I5-14600K')
  const source = (await (await page.request.get(`/api/products/${base.id}`)).json()).data
  const sku = `WEB08-${Date.now()}`
  const created = await post('/api/admin/products', { sku, category: 'CPU', name: '评分浏览器测试CPU', brand: 'Intel', model: 'i5-14600K',
    summary: '评分维护测试夹具', description: '仅供浏览器自动化测试', imageUrl: '/images/intel-cpu.svg', sourceUrl: base.sourceUrl,
    price: '100.50', parameters: source.parameters })
  expect(created.status()).toBe(201)
  const id = (await created.json()).data.product.id
  const path = `/api/admin/products/${id}`
  expect((await post(`${path}/status`, { revision: 0, status: 'ON_SALE' })).status()).toBe(200)
  try {
    await admin.getByRole('navigation').getByRole('button', { name: '商品管理' }).click()
    await admin.getByLabel('商品关键词').fill(sku)
    await admin.getByRole('button', { name: '查询', exact: true }).click()
    await admin.getByRole('button', { name: '查看编辑' }).click()
    const panel = admin.getByRole('region', { name: '性能评分维护' })
    await expect(panel).toBeVisible()
    await expect(panel).toContainText('尚无经过核验的性能参考')
    await admin.getByLabel('商品名称', { exact: true }).fill('尚未保存的CPU资料')
    const ref = references['INTEL-I5-14600K']
    await panel.getByLabel('评分有效状态').selectOption('true')
    for (const [label, value] of [
      ['原始渲染分', '325'], ['评分来源地址', ref.sourceUrl], ['快照日期', ref.snapshotDate], ['快照SHA-256', ref.snapshotSha256],
      ['基准设备名称', ref.deviceName], ['采样条件', ref.sampleConditions], ['型号与变体匹配依据', ref.matchNote],
      ['完整提交UUID（每行一个）', ref.sampleIds.join('\n')],
    ]) await panel.getByLabel(label!, { exact: true }).fill(value!)
    await panel.getByRole('button', { name: '保存评分', exact: true }).click()
    await expect(panel.getByRole('alert')).toContainText('原因')
    await panel.getByLabel('评分调整原因').fill('核验后维护参考分')
    await panel.getByRole('button', { name: '保存评分', exact: true }).click()
    await expect(panel.getByRole('status')).toContainText('评分与审计已保存')
    await expect(panel).toContainText('当前参考分 62.5 / 100')
    await expect(admin.getByRole('list', { name: '性能标签', exact: true })).toContainText('渲染参考 62.5分')
    await expect(admin.getByLabel('商品名称', { exact: true })).toHaveValue('尚未保存的CPU资料')
    async function delayNextTagRefresh() {
      let release!: () => void, captured!: () => void, delivered!: () => void
      const gate = new Promise<void>(resolve => { release = resolve })
      const pending = new Promise<void>(resolve => { captured = resolve })
      const done = new Promise<void>(resolve => { delivered = resolve })
      let blocked = false
      const pattern = `**/api/admin/products/${id}`
      const handler = async (route: Route) => {
        if (blocked || route.request().method() !== 'GET') { await route.continue(); return }
        blocked = true
        const response = await route.fetch()
        captured()
        await gate
        await route.fulfill({ response })
        delivered()
      }
      await page.route(pattern, handler)
      return { pending, complete: async () => {
        release(); await done; await page.unroute(pattern, handler)
        // Let the fulfilled response and Vue's update reach the next browser paint.
        await page.evaluate(() => new Promise<void>(resolve => requestAnimationFrame(() => requestAnimationFrame(() => resolve()))))
      } }
    }
    const parameterRace = await delayNextTagRefresh()
    await panel.getByLabel('原始渲染分').fill('450')
    await panel.getByLabel('评分调整原因').fill('先保存评分，再更新参数')
    await panel.getByRole('button', { name: '保存评分', exact: true }).click()
    await parameterRace.pending
    await admin.getByLabel('核心数 · 核', { exact: true }).fill('15')
    await admin.getByRole('button', { name: '保存修改', exact: true }).click()
    await expect(admin.getByRole('list', { name: '性能标签', exact: true })).toContainText('15核')
    await parameterRace.complete()
    await expect(admin.getByRole('list', { name: '性能标签', exact: true })).toContainText('15核')
    await panel.getByRole('button', { name: '重新载入评分' }).click()
    await expect(panel.getByLabel('原始渲染分')).toHaveValue('450')
    const scoreRace = await delayNextTagRefresh()
    await panel.getByLabel('原始渲染分').fill('325')
    await panel.getByLabel('评分调整原因').fill('先保存有效分，再立即撤销')
    await panel.getByRole('button', { name: '保存评分', exact: true }).click()
    await scoreRace.pending
    await panel.getByLabel('评分有效状态').selectOption('false')
    await panel.getByLabel('评分调整原因').fill('撤销有效分')
    await panel.getByRole('button', { name: '保存评分', exact: true }).click()
    await expect(admin.getByRole('list', { name: '性能标签', exact: true })).toContainText('渲染评分待核验')
    await scoreRace.complete()
    await expect(admin.getByRole('list', { name: '性能标签', exact: true })).toContainText('渲染评分待核验')
    await panel.getByLabel('评分有效状态').selectOption('true')
    await panel.getByLabel('评分调整原因').fill('重新核验恢复参考')
    await panel.getByRole('button', { name: '保存评分', exact: true }).click()
    await expect(admin.getByRole('list', { name: '性能标签', exact: true })).toContainText('渲染参考 62.5分')
    const latest = (await (await page.request.get(`${path}/performance`)).json()).data
    expect((await post(`${path}/performance`, { ...latest.reference, revision: latest.revision, rawScore: '125.300000', reason: '另一管理员评分更新' })).status()).toBe(200)
    await panel.getByLabel('原始渲染分').fill('450')
    await panel.getByLabel('评分调整原因').fill('旧窗口维护')
    await panel.getByRole('button', { name: '保存评分', exact: true }).click()
    await expect(panel.getByRole('alert')).toContainText('重新载入')
    await expect(panel.getByLabel('原始渲染分')).toHaveValue('450')
    await panel.getByRole('button', { name: '重新载入评分' }).click()
    await expect(panel.getByLabel('原始渲染分')).toHaveValue('125.300000')
    await expect(panel).toContainText('当前参考分 37.6 / 100')
    await panel.getByLabel('评分有效状态').selectOption('false')
    await panel.getByLabel('评分调整原因').fill('来源待复核，撤销有效分')
    await panel.getByRole('button', { name: '保存评分', exact: true }).click()
    await expect(panel.getByRole('status')).toContainText('评分与审计已保存')
    await expect(panel).toContainText('该性能参考已标记为无效')
    await expect(admin.getByRole('list', { name: '性能标签', exact: true })).toContainText('渲染评分待核验')
    await admin.getByRole('button', { name: '保存修改', exact: true }).click()
    await expect(admin.getByRole('alert').filter({ hasText: '重新载入' })).toBeVisible()
    await panel.scrollIntoViewIfNeeded()
    await page.screenshot({ path: '../.local/task-08-performance.png', fullPage: true, animations: 'disabled' })
    const publicDetail = (await (await page.request.get(`/api/products/${id}`)).json()).data
    expect(publicDetail.performance.state).toBe('INVALID')
    expect(publicDetail.performance.score).toBeNull()
  } finally {
    const current = (await (await page.request.get(path)).json()).data
    await post(`${path}/status`, { revision: current.revision, status: 'OFF_SALE' })
  }
})
