import { expect, test, type Page } from '@playwright/test'

const configuration = (page: Page) => page.getByRole('region', { name: '当前配置' })
const diagnostics = (page: Page) => page.getByRole('region', { name: '兼容分项诊断' })
const conflicts = (page: Page) => diagnostics(page).getByRole('list', { name: '兼容冲突' })

const choices = [
  ['CPU', 'Intel Core i5-14600KF'], ['主板', '华硕 TUF B760M-PLUS WIFI'],
  ['内存', '金士顿 FURY Beast 32GB (16G×2)'], ['SSD', '三星 990 PRO 1TB'],
  ['电源', '海盗船 RM750e 全模组电源'], ['机箱', '九州风神 CC560 中塔机箱'],
  ['独立散热器', '九州风神 AK400 风冷散热器'],
]

async function selectAll(page: Page, skip: string[] = []) {
  const categories = page.getByRole('navigation', { name: 'DIY 硬件分类' })
  for (const [category, name] of choices) {
    if (skip.includes(category)) continue
    await categories.getByRole('button', { name: new RegExp(`^${category}`) }).click()
    await page.getByRole('button', { name: `选择 ${name}`, exact: true }).click()
  }
}

test('passing checks show only a summary without rule rows or conflict points', async ({ page }) => {
  await page.goto('/#diy')
  await selectAll(page)

  await expect(diagnostics(page)).toContainText('分项已通过')
  await expect(conflicts(page)).toHaveCount(0)
  await expect(diagnostics(page)).not.toContainText(/C0[1-5]/)
  await expect(page.getByRole('region', { name: '配置修订建议' })).toContainText('当前不能确认整机兼容')
  await expect(page.getByRole('button', { name: /加入购物车|立即购买/ })).toHaveCount(0)
})

test('unfinished selections show a pending summary without rule rows or conflict points', async ({ page }) => {
  await page.goto('/#diy')
  await expect(diagnostics(page)).toContainText('尚未选齐')
  await expect(conflicts(page)).toHaveCount(0)
  await expect(diagnostics(page)).not.toContainText(/C0[1-5]|查看缺件|检测范围、版本与时间/)
  await diagnostics(page).screenshot({ path: '../docs/evidence/diy-ui-compatibility.png', animations: 'disabled' })

  await page.getByRole('navigation', { name: 'DIY 硬件分类' }).getByRole('button', { name: /^CPU/ }).click()
  await page.getByRole('button', { name: '选择 Intel Core i5-14600KF', exact: true }).click()
  await expect(diagnostics(page)).toContainText('尚未选齐')
  await expect(conflicts(page)).toHaveCount(0)
})

test('only confirmed conflicts become bullet points and disappear after a successful recheck', async ({ page }) => {
  await page.goto('/#diy')
  await selectAll(page, ['机箱'])

  // Browser boundary fixture: only the diagnostic payload is reshaped; quotation data is untouched.
  await page.route('**/api/compatibility/check', async route => {
    const response = await route.fetch()
    const body = await response.json()
    body.data.checks = body.data.checks.map((check: { rule: string }) => check.rule === 'C04'
      ? { ...check, state: 'FAIL', message: '主板为 E-ATX，机箱仅支持 ATX、Micro-ATX、Mini-ITX。' }
      : check)
    await route.fulfill({ response, json: body })
  })
  await page.getByRole('navigation', { name: 'DIY 硬件分类' }).getByRole('button', { name: /^机箱/ }).click()
  await page.getByRole('button', { name: '选择 九州风神 CC560 中塔机箱', exact: true }).click()

  await expect(conflicts(page).getByRole('listitem')).toHaveCount(1)
  await expect(conflicts(page)).toContainText('主板板型与机箱')
  await expect(conflicts(page).getByRole('listitem')).toHaveText('主板板型与机箱')
  await expect(diagnostics(page)).not.toContainText(/E-ATX|原因：|建议：/)
  const advice = page.getByRole('region', { name: '配置修订建议' })
  await expect(advice).toContainText('原因：主板为 E-ATX')
  await expect(advice).toContainText('建议：更换为支持当前主板板型的机箱')
  await expect(conflicts(page)).not.toContainText(/C0[1-5]|CPU 与主板插槽|待选择|未知/)
  await expect(diagnostics(page)).toContainText('发现冲突')
  await diagnostics(page).screenshot({ path: '../docs/evidence/diy-ui-conflict.png', animations: 'disabled' })
  await advice.screenshot({ path: '../docs/evidence/diy-ui-revision-advice.png', animations: 'disabled' })
  await page.unroute('**/api/compatibility/check')
  await page.reload()
  await expect(diagnostics(page)).toContainText('分项已通过')
  await expect(conflicts(page)).toHaveCount(0)
  await expect(advice).not.toContainText('E-ATX')
})

test('an unknown parameter is reported as unknown rather than passing', async ({ page }) => {
  await page.goto('/#diy')
  await selectAll(page, ['CPU'])
  await page.route('**/api/compatibility/check', async route => {
    const response = await route.fetch()
    const body = await response.json()
    body.data.checks = body.data.checks.map((check: { rule: string }) => check.rule === 'C02'
      ? { ...check, state: 'UNKNOWN', message: '内存代际、插槽数或套装条数缺少已核验值或来源，无法判断。' }
      : check)
    await route.fulfill({ response, json: body })
  })
  await page.getByRole('navigation', { name: 'DIY 硬件分类' }).getByRole('button', { name: /^CPU/ }).click()
  await page.getByRole('button', { name: '选择 Intel Core i5-14600KF', exact: true }).click()

  await expect(diagnostics(page)).toContainText('存在未知参数')
  await expect(conflicts(page)).toHaveCount(0)
  await expect(diagnostics(page)).not.toContainText('分项已通过')
  await page.unroute('**/api/compatibility/check')
})

test('a failed diagnostic keeps the configuration editable and never shows a stale pass', async ({ page }) => {
  await page.goto('/#diy')
  await selectAll(page)
  await expect(diagnostics(page)).toContainText('分项已通过')

  await page.route('**/api/compatibility/check', route => route.fulfill({ status: 503, json: { code: 'CATALOG_UNAVAILABLE' } }))
  await page.reload()
  await expect(configuration(page)).toContainText('已选择 7 / 8 项')
  await expect(diagnostics(page).getByRole('alert')).toContainText('已保留当前选择')
  await expect(diagnostics(page)).not.toContainText('分项已通过')
  await expect(diagnostics(page)).toContainText('检测暂不可用')
  await expect(page.getByRole('region', { name: '配置修订建议' })).toContainText('请先完成兼容检测')
  await expect(page.getByRole('region', { name: '配置修订建议' })).not.toContainText('已检查的项目没有发现冲突')

  await page.unroute('**/api/compatibility/check')
  await diagnostics(page).getByRole('button', { name: '重试兼容检测' }).click()
  await expect(diagnostics(page)).toContainText('分项已通过')
})

test('a late diagnostic response cannot overwrite the newer configuration', async ({ page }) => {
  let release!: () => void
  let started!: () => void
  const gate = new Promise<void>(resolve => { release = resolve })
  const pending = new Promise<void>(resolve => { started = resolve })
  await page.route('**/api/compatibility/check', async route => {
    const sent = JSON.parse(route.request().postData() ?? '{}')
    if (sent.revision !== 1) { await route.continue(); return }
    const response = await route.fetch()
    const body = await response.json()
    body.data.checks = body.data.checks.map((check: { rule: string }) => check.rule === 'C01'
      ? { ...check, state: 'FAIL', message: '过期配置的插槽冲突' } : check)
    started()
    await gate
    await route.fulfill({ response, json: body })
  })

  await page.goto('/#diy')
  await page.getByRole('button', { name: '选择 Intel Core i5-14600KF', exact: true }).click()
  await pending
  await expect(diagnostics(page)).toContainText('正在读取服务端当前参数')
  await page.getByRole('button', { name: '选择 AMD Ryzen 7 7800X3D', exact: true }).click()
  await release()
  await page.unrouteAll({ behavior: 'wait' })

  // The newer revision's result must still be the one on screen once the stale response lands.
  await expect(configuration(page)).toContainText('修订 2')
  await expect(diagnostics(page)).not.toContainText('过期配置的插槽冲突')
  await expect(conflicts(page)).toHaveCount(0)
  await expect(configuration(page)).toContainText('AMD Ryzen 7 7800X3D')
  await expect(configuration(page)).not.toContainText('Intel Core i5-14600KF')
})
