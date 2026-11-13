import { expect, test, type Page } from '@playwright/test'

const configuration = (page: Page) => page.getByRole('region', { name: '当前配置' })
const diagnostics = (page: Page) => page.getByRole('region', { name: '兼容分项诊断' })
const conflicts = (page: Page) => diagnostics(page).getByRole('list', { name: '兼容冲突' })

const choices = [
  ['CPU', 'Intel Core i5-14600K'], ['主板', '华硕 TUF B760M-PLUS WIFI'],
  ['内存', '金士顿 FURY Beast 16GB'], ['硬盘', '三星 990 PRO 1TB'],
  ['电源', '海盗船 RM750e 全模组电源'], ['机箱', '九州风神 CC560 中塔机箱'],
  ['CPU散热器', '九州风神 AK400 风冷散热器'],
]

async function selectAll(page: Page, skip: string[] = []) {
  const categories = page.getByRole('navigation', { name: 'DIY 硬件分类' })
  for (const [category, name] of choices) {
    if (skip.includes(category)) continue
    await categories.getByRole('button', { name: new RegExp(`^${category} `) }).click()
    await page.getByRole('button', { name: `选择 ${name}`, exact: true }).click()
    if (category === 'CPU') {
      await categories.getByRole('button', { name: /^显卡 / }).click()
      await page.getByRole('button', { name: '选择 CPU 核显', exact: true }).click()
    }
  }
}

test('core admission shows only a summary without rule rows or conflict points', async ({ page }) => {
  await page.goto('/#diy')
  await selectAll(page)

  await expect(diagnostics(page)).toContainText('核心检测通过')
  await expect(conflicts(page)).toHaveCount(0)
  await expect(diagnostics(page)).not.toContainText(/C0[1-5]/)
  await expect(page.getByRole('region', { name: '配置修订建议' })).not.toContainText('核心通过不代表可售、有库存或全面兼容')
  await expect(page.getByRole('button', { name: /加入购物车|立即购买/ })).toHaveCount(0)
  await page.getByText('检测记录', { exact: true }).click()
  await expect(page.getByLabel('检测记录')).toContainText('f03-core-graphics-choice-v3')
  await page.screenshot({ path: '../docs/evidence/task-11-admission.png', fullPage: true, animations: 'disabled' })
})

test('automatic revalidation clears the old pass and reads changed parameters at the same revision', async ({ page }) => {
  await page.goto('/#diy')
  await selectAll(page)
  await expect(diagnostics(page)).toContainText('核心检测通过')
  let release!: () => void
  let started!: () => void
  const gate = new Promise<void>(resolve => { release = resolve })
  const pending = new Promise<void>(resolve => { started = resolve })
  await page.route('**/api/compatibility/check', async route => {
    const response = await route.fetch()
    const body = await response.json()
    body.data.status = 'INCOMPATIBLE'
    body.data.corePassed = false
    body.data.catalogVersion = 'f'.repeat(64)
    body.data.checks = body.data.checks.map((check: { rule: string }) => check.rule === 'C08'
      ? { ...check, state: 'FAIL', message: '新参数的电源额定功率 526 W，最低需求 527 W。' } : check)
    started()
    await gate
    await route.fulfill({ response, json: body })
  })
  await page.evaluate(() => window.dispatchEvent(new Event('focus')))
  await pending
  await expect(diagnostics(page)).toContainText('正在检测')
  await expect(diagnostics(page)).not.toContainText('核心检测通过')
  release()
  await expect(diagnostics(page)).toContainText('发现冲突')
  await expect(conflicts(page)).toContainText('电源额定功率')
  await expect(page.getByRole('region', { name: '配置修订建议' })).toContainText('526 W')
  await expect(configuration(page)).toContainText('修订 8')
  await page.unrouteAll({ behavior: 'wait' })
})

test('foreground revalidation rejects old rule versions and missing core checks', async ({ page }) => {
  await page.goto('/#diy')
  await selectAll(page)
  await expect(diagnostics(page)).toContainText('核心检测通过')
  for (const invalid of ['version', 'missing-rule', 'false-summary']) {
    await page.route('**/api/compatibility/check', async route => {
      const response = await route.fetch()
      const body = await response.json()
      if (invalid === 'version') body.data.ruleVersion = 'f03-core-c01-c05-v1'
      else if (invalid === 'missing-rule') body.data.checks.pop()
      else body.data.checks[6].state = 'FAIL'
      await route.fulfill({ response, json: body })
    })
    await page.evaluate(() => window.dispatchEvent(new Event('focus')))
    await expect(diagnostics(page).getByRole('alert')).toContainText('兼容检测数据异常')
    await expect(diagnostics(page)).not.toContainText('核心检测通过')
    await page.unroute('**/api/compatibility/check')
  }
})

test('a visible workspace automatically rechecks current parameters', async ({ page }) => {
  await page.clock.install()
  await page.goto('/#diy')
  await selectAll(page)
  await expect(diagnostics(page)).toContainText('核心检测通过')
  await page.route('**/api/compatibility/check', async route => {
    const response = await route.fetch()
    const body = await response.json()
    body.data.status = 'UNVERIFIED'
    body.data.corePassed = false
    body.data.checks[6].state = 'UNKNOWN'
    body.data.checks[6].message = '当前 CPU 峰值功耗来源待核实。'
    await route.fulfill({ response, json: body })
  })
  await page.clock.fastForward(30_000)
  await expect(diagnostics(page)).toContainText('存在未知参数')
  await expect(diagnostics(page)).not.toContainText('核心检测通过')
  await page.unrouteAll({ behavior: 'wait' })
})

test('unfinished selections show a pending summary without rule rows or conflict points', async ({ page }) => {
  await page.goto('/#diy')
  await expect(diagnostics(page)).toContainText('尚未选齐')
  await expect(conflicts(page)).toHaveCount(0)
  await expect(diagnostics(page)).not.toContainText(/C0[1-5]|查看缺件|检测范围、版本与时间/)
  await diagnostics(page).screenshot({ path: '../docs/evidence/task-11-pending.png', animations: 'disabled' })

  await page.getByRole('navigation', { name: 'DIY 硬件分类' }).getByRole('button', { name: /^CPU / }).click()
  await page.getByRole('button', { name: '选择 Intel Core i5-14600K', exact: true }).click()
  await expect(diagnostics(page)).toContainText('尚未选齐')
  const advice = page.getByRole('region', { name: '配置修订建议' })
  await expect(advice).toContainText('优先选择与 CPU 插槽匹配的主板。')
  await expect(advice.getByLabel('缺少配件')).toHaveCount(0)
  await expect(advice).not.toContainText(/再核对内存代际与实际条数|尚未选择主板|尚无经确认有效的核显路径|核心通过不代表可售/)
  await advice.screenshot({ path: '../docs/evidence/diy-simplified-advice.png', animations: 'disabled' })

  await expect(conflicts(page)).toHaveCount(0)
})

test('only confirmed conflicts become bullet points and disappear after a successful recheck', async ({ page }) => {
  await page.goto('/#diy')
  await selectAll(page, ['机箱'])

  // Browser boundary fixture: only the diagnostic payload is reshaped; quotation data is untouched.
  await page.route('**/api/compatibility/check', async route => {
    const response = await route.fetch()
    const body = await response.json()
    body.data.status = 'INCOMPATIBLE'
    body.data.corePassed = false
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
  await diagnostics(page).screenshot({ path: '../docs/evidence/task-11-conflict.png', animations: 'disabled' })
  await advice.screenshot({ path: '../docs/evidence/task-11-revision-advice.png', animations: 'disabled' })
  await page.unroute('**/api/compatibility/check')
  await page.reload()
  await expect(diagnostics(page)).toContainText('核心检测通过')
  await expect(conflicts(page)).toHaveCount(0)
  await expect(advice).not.toContainText('E-ATX')
})

test('an unknown parameter is reported as unknown rather than passing', async ({ page }) => {
  await page.goto('/#diy')
  await selectAll(page, ['CPU'])
  await page.route('**/api/compatibility/check', async route => {
    const response = await route.fetch()
    const body = await response.json()
    body.data.status = 'UNVERIFIED'
    body.data.corePassed = false
    body.data.checks = body.data.checks.map((check: { rule: string }) => check.rule === 'C02'
      ? { ...check, state: 'UNKNOWN', message: '内存代际、插槽数或套装条数缺少已核验值或来源，无法判断。' }
      : check)
    await route.fulfill({ response, json: body })
  })
  await page.getByRole('navigation', { name: 'DIY 硬件分类' }).getByRole('button', { name: /^CPU / }).click()
  await page.getByRole('button', { name: '选择 Intel Core i5-14600K', exact: true }).click()
  await page.getByRole('navigation', { name: 'DIY 硬件分类' }).getByRole('button', { name: /^显卡 / }).click()
  await page.getByRole('button', { name: '选择 CPU 核显', exact: true }).click()

  await expect(diagnostics(page)).toContainText('存在未知参数')
  await expect(conflicts(page)).toHaveCount(0)
  await expect(diagnostics(page)).not.toContainText('核心检测通过')
  await page.unroute('**/api/compatibility/check')
})

test('a failed diagnostic keeps the configuration editable and never shows a stale pass', async ({ page }) => {
  await page.goto('/#diy')
  await selectAll(page)
  await expect(diagnostics(page)).toContainText('核心检测通过')

  await page.route('**/api/compatibility/check', route => route.fulfill({ status: 503, json: { code: 'CATALOG_UNAVAILABLE' } }))
  await page.reload()
  await expect(configuration(page)).toContainText('已选择 8 / 8 项')
  await expect(diagnostics(page).getByRole('alert')).toContainText('已保留当前选择')
  await expect(diagnostics(page)).not.toContainText('核心检测通过')
  await expect(diagnostics(page)).toContainText('检测暂不可用')
  await expect(page.getByRole('region', { name: '配置修订建议' })).toContainText('请先完成兼容检测')
  await expect(page.getByRole('region', { name: '配置修订建议' })).not.toContainText('已检查的项目没有发现冲突')

  await page.unroute('**/api/compatibility/check')
  await diagnostics(page).getByRole('button', { name: '重试兼容检测' }).click()
  await expect(diagnostics(page)).toContainText('核心检测通过')
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
    body.data.status = 'INCOMPATIBLE'
    body.data.corePassed = false
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


test('graphics must be explicitly chosen; integrated or discrete choices satisfy the slot without C07', async ({ page }) => {
  await page.goto('/#diy')
  await selectAll(page)
  await expect(diagnostics(page)).toContainText('核心检测通过')
  await configuration(page).getByRole('button', { name: '移除 显卡', exact: true }).click()
  await expect(diagnostics(page)).toContainText('尚未选齐')
  await expect(diagnostics(page)).not.toContainText('核心检测通过')
  await page.getByRole('navigation', { name: 'DIY 硬件分类' }).getByRole('button', { name: /^显卡 / }).click()
  await page.getByRole('button', { name: '选择 CPU 核显', exact: true }).click()
  await expect(diagnostics(page)).toContainText('核心检测通过')
  await configuration(page).getByRole('button', { name: '替换 CPU', exact: true }).click()
  await page.getByRole('button', { name: '选择 Intel Core i5-14600KF', exact: true }).click()
  await expect(diagnostics(page)).toContainText('尚未选齐')
  await page.getByRole('navigation', { name: 'DIY 硬件分类' }).getByRole('button', { name: /^显卡 / }).click()
  await expect(page.getByRole('button', { name: '选择 CPU 核显', exact: true })).toHaveCount(0)
  await page.getByRole('button', { name: '选择 NVIDIA GeForce RTX 4070 SUPER', exact: true }).click()
  await expect(diagnostics(page)).toContainText('核心检测通过')
})


test('configuration edits automatically update conflicts without a manual recheck button', async ({ page }) => {
  await page.goto('/#diy')
  await selectAll(page)
  const advice = page.getByRole('region', { name: '配置修订建议' })
  await expect(diagnostics(page)).toContainText('核心检测通过')
  await expect(advice).not.toContainText('配置或参数变化后需重检')
  await expect(advice.getByRole('button', { name: '重新检测', exact: true })).toHaveCount(0)
  await configuration(page).getByRole('button', { name: '替换 CPU', exact: true }).click()
  await page.getByRole('button', { name: '选择 AMD Ryzen 7 7800X3D', exact: true }).click()
  await expect(diagnostics(page)).toContainText('发现冲突')
  await expect(conflicts(page)).toContainText('CPU 与主板插槽')
  await expect(advice).toContainText('AM5')
  await expect(advice).toContainText('LGA1700')
  await page.getByRole('button', { name: '选择 Intel Core i5-14600K', exact: true }).click()
  await expect(conflicts(page)).toHaveCount(0)
  await expect(diagnostics(page)).toContainText('尚未选齐')
  await page.getByRole('navigation', { name: 'DIY 硬件分类' }).getByRole('button', { name: /^显卡 / }).click()
  await page.getByRole('button', { name: '选择 CPU 核显', exact: true }).click()
  await expect(diagnostics(page)).toContainText('核心检测通过')
  await configuration(page).getByRole('button', { name: '移除 主板', exact: true }).click()
  await expect(diagnostics(page)).toContainText('尚未选齐')
  await expect(diagnostics(page)).not.toContainText('核心检测通过')
  await expect(conflicts(page)).toHaveCount(0)
  await advice.screenshot({ path: '../docs/evidence/diy-auto-compatibility.png', animations: 'disabled' })
})
