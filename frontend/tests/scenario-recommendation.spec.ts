import { expect, test, type Page } from '@playwright/test'

async function openScenario(page: Page, label: string) {
  await page.goto('/')
  await page.getByRole('button', { name: new RegExp(`^${label}推荐 ·`) }).click()
  return page.getByRole('dialog', { name: `${label}推荐`, exact: true })
}

test('recommendation scores interpolate between blue, orange and red', async ({ page }) => {
  let score = '0'
  await page.route('**/api/recommendations', async route => {
    const response = await route.fetch()
    const body = await response.json()
    expect(body.data.status).toBe('SUCCESS')
    body.data.score = score
    body.data.scores = { cpu: '59.95', gpu: '86.16', memory: '100.00', ssd: '100.00' }
    await route.fulfill({ response, json: body })
  })
  const dialog = await openScenario(page, '游戏')
  await dialog.getByLabel('预算（CNY）').fill('20000')
  const displayed = dialog.locator('.recommendation-score .score-display strong')
  for (const [value, color] of [
    ['0', 'rgb(23, 69, 127)'],
    ['20', 'rgb(16, 87, 189)'],
    ['40', 'rgb(9, 104, 250)'],
    ['55', 'rgb(132, 116, 125)'],
    ['70', 'rgb(255, 128, 0)'],
    ['85', 'rgb(238, 83, 19)'],
    ['100', 'rgb(220, 38, 38)'],
  ]) {
    score = value
    await dialog.getByRole('button', { name: '生成游戏方案' }).click()
    await expect(displayed).toHaveText(`${value}.00 / 100`)
    await expect(displayed).toHaveCSS('color', color)
  }
  const parts = dialog.getByRole('region', { name: '游戏推荐方案' }).locator('.recommendation-parts')
  const cpuRow = parts.getByRole('listitem').filter({ hasText: /^CPU ·/ })
  const cpuScore = cpuRow.locator('.recommendation-part-score strong')
  await expect(cpuScore).toHaveText('17.99 / 30 分')
  await expect(cpuScore).toHaveCSS('color', 'rgb(173, 120, 84)')
  await expect(cpuScore).toHaveCSS('font-size', '22px')
  await expect(cpuRow.locator('.recommendation-part-amount')).toHaveCSS('font-size', '14px')
  const [product, scoreColumn, amount] = await cpuRow.evaluate(row => Array.from(row.children, element => {
    const { left, right } = element.getBoundingClientRect()
    return { left, right }
  }))
  expect(product.right).toBeLessThan(scoreColumn.left)
  expect(scoreColumn.right).toBeLessThan(amount.left)
  await expect(parts.getByRole('listitem').filter({ hasText: /^显卡 ·/ })).toContainText('47.39 / 55 分')
  await expect(parts.getByRole('listitem').filter({ hasText: /^内存 ·/ })).toContainText('10.00 / 10 分')
  const ssdScore = parts.getByRole('listitem').filter({ hasText: /^SSD ·/ }).locator('.recommendation-part-score strong')
  await expect(ssdScore).toHaveText('5.00 / 5 分')
  await expect(ssdScore).toHaveCSS('color', 'rgb(220, 38, 38)')
  await expect(parts.getByRole('listitem').filter({ hasText: /^主板 ·/ })).toContainText('不计分')
})

for (const [label, gpuWeight] of [['游戏', 55], ['创作', 35]] as const) {
  test(`task22: ${label} recommends a real dedicated-GPU plan and keeps its score until a DIY edit`, async ({ page }) => {
    if (label === '创作') await page.setViewportSize({ width: 390, height: 844 })
    const dialog = await openScenario(page, label)
    await dialog.getByLabel('预算（CNY）').fill('20000')
    await dialog.getByRole('button', { name: `生成${label}方案` }).click()
    const plan = dialog.getByRole('region', { name: `${label}推荐方案` })
    await expect(plan).toContainText(`${label}评分`)
    await expect(plan).toContainText(new RegExp(`独显 [0-9.]+ × ${gpuWeight}%`))
    await expect(plan).toContainText('6 项扩展检测未执行')
    await expect(plan.getByRole('link')).toHaveCount(8)
    if (label === '创作') {
      await expect(plan.locator('.recommendation-part-score strong').first()).toHaveCSS('font-size', '18px')
      expect(await plan.locator('.recommendation-parts').evaluate(list => list.scrollWidth <= list.clientWidth)).toBe(true)
    }
    const recommendationScore = plan.locator('.recommendation-score .score-display strong')
    const scoreText = await recommendationScore.innerText()
    const color = await recommendationScore.evaluate(element => getComputedStyle(element).color)
    await plan.getByRole('button', { name: '转入 DIY 调整' }).click()
    await expect(page).toHaveURL(/#diy$/)
    const assessment = page.getByRole('region', { name: '性能评分', exact: true })
    await expect(assessment).not.toContainText(`${label}评分`)
    await expect(page.getByRole('region', { name: '配置修订' }).locator('.diy-quote-meta + section'))
      .toHaveAttribute('aria-label', '性能评分')
    await expect(assessment.locator('.score-display strong')).toHaveText(scoreText)
    await expect(assessment.locator('.score-display strong')).toHaveCSS('color', color)
    const meter = assessment.getByRole('meter', { name: '性能评分' })
    const points = Number(scoreText.split('/')[0])
    await expect(meter).toHaveAttribute('aria-valuenow', String(points))
    await expect(meter.locator('.score-display-fill')).toHaveCSS('background-color', color)
    const fillPercent = await meter.evaluate(track => track.firstElementChild!.getBoundingClientRect().width / track.getBoundingClientRect().width * 100)
    expect(fillPercent).toBeCloseTo(points, 1)
    await expect(page.getByRole('region', { name: '兼容分项诊断' })).toContainText('核心检测通过')
    const saved = await page.evaluate(() => JSON.parse(sessionStorage.getItem('pc-mall.diy.workspace.v1')!))
    expect(saved.selections).toHaveLength(8)
    expect(saved.selections.every((item: { source: string }) => item.source === 'RECOMMENDATION')).toBe(true)
    expect(saved.integratedGraphicsCpuId).toBeUndefined()
    await page.getByRole('button', { name: '移除 显卡', exact: true }).click()
    await expect(meter).toHaveCount(0)
    await expect(assessment).toContainText('选齐配件后计算')
  })
}

for (const [scenario, label] of [['OFFICE', '办公'], ['GAMING', '游戏'], ['CREATION', '创作']] as const) {
  test(`task22: ${label} reports validation, budget and service failures without a plan`, async ({ page }) => {
    const dialog = await openScenario(page, label)
    const budget = dialog.getByLabel('预算（CNY）')
    const generate = dialog.getByRole('button', { name: `生成${label}方案` })
    const transfer = dialog.getByRole('button', { name: '转入 DIY 调整' })
    await budget.fill('0')
    await generate.click()
    await expect(dialog.getByRole('alert')).toContainText('请输入')
    await budget.fill('1')
    await generate.click()
    await expect(dialog.getByRole('status')).toContainText('价格下界')
    await expect(transfer).toHaveCount(0)

    // The real API covers classification. Inject its response contract here to exercise the same UI without changing the shared database.
    const failures = [
      ['NO_AVAILABLE_PRODUCT', '当前有必需类别无在售库存，请稍后重试。'],
      ['INSUFFICIENT_DATA', '当前商品缺少已核验的评分或参数。'],
      ['NO_COMPATIBLE_COMBINATION', '当前达到用途门槛的候选范围内无兼容组合。'],
      ['NO_RESULT_FOUND', '当前搜索未找到预算内的完整方案。'],
      ['REVALIDATION_REQUIRED', '商品状态或兼容检查结果已变化，请重新推荐。'],
      ['SYSTEM_ERROR', '推荐服务暂不可用，请稍后重试。'],
    ] as const
    await budget.fill('20000')
    for (const [status, message] of failures) {
      const systemError = status === 'SYSTEM_ERROR'
      await page.route('**/api/recommendations', route => route.fulfill({
        status: systemError ? 503 : 200,
        json: { data: { scenario, status, message }, ...(systemError ? { code: status, message } : {}) },
      }), { times: 1 })
      await generate.click()
      await expect(dialog.getByRole(systemError ? 'alert' : 'status')).toHaveText(message)
      await expect(transfer).toHaveCount(0)
    }
  })

  test(`task22: ${label} refuses to import a success payload with untrustworthy F03 results`, async ({ page }) => {
    await page.route('**/api/recommendations', async route => {
      const response = await route.fetch()
      const body = await response.json()
      expect(body.data.status).toBe('SUCCESS')
      body.data.compatibility.checks[0].state = 'UNKNOWN'
      await route.fulfill({ response, json: body })
    })
    const dialog = await openScenario(page, label)
    await dialog.getByLabel('预算（CNY）').fill('20000')
    await dialog.getByRole('button', { name: `生成${label}方案` }).click()
    await expect(dialog.getByRole('alert')).toContainText('兼容检测未通过验证')
    await expect(dialog.getByRole('button', { name: '转入 DIY 调整' })).toHaveCount(0)
  })
}
