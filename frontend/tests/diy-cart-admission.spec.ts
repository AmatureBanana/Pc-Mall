import { expect, test } from '@playwright/test'

test('cart transfer stays disabled until a complete current build passes compatibility', async ({ page }) => {
  await page.goto('/#diy')
  const transfer = page.getByRole('button', { name: '转入购物车', exact: true })
  await expect(transfer).toBeDisabled()
  await expect(transfer).toHaveCSS('background-color', 'rgb(228, 231, 237)')
  const products = (await (await page.request.get('/api/products')).json()).data as { id: number; sku: string }[]
  const memory = products.find(p => p.sku === 'KINGSTON-KF560C36BBE2-16')!.id
  const integratedCpu = products.find(p => p.sku === 'INTEL-I5-14600K')!.id
  const parts = { CPU: 1, MOTHERBOARD: 3, GPU: 5, MEMORY: memory, SSD: 9, PSU: 11, CASE: 12, COOLER: 8 }
  async function expectTransferOrder() {
    await transfer.click()
    const dialog = page.getByRole('dialog', { name: '转入购物车', exact: true })
    await expect(dialog.getByRole('listitem')).toHaveText([
      /^CPU ·/, /^CPU 散热器 ·/, /^主板 ·/, /^内存 ·/, /^显卡 ·/, /^硬盘 ·/, /^电源 ·/, /^机箱 ·/,
    ])
    await dialog.getByRole('button', { name: '取消', exact: true }).click()
  }
  async function load(selections: Record<string, number>, integratedGraphicsCpuId?: number) {
    await page.evaluate(value => sessionStorage.setItem('pc-mall.diy.workspace.v1', JSON.stringify(value)), {
      version: 1, revision: 1, selections: Object.entries(selections).map(([category, productId]) => ({ category, productId, source: 'DIY' })),
      ...(integratedGraphicsCpuId ? { integratedGraphicsCpuId } : {}),
    })
    await page.reload()
  }
  await load({ CPU: 1 })
  await expect(page.getByRole('region', { name: '兼容分项诊断' })).toContainText('尚未选齐')
  await expect(transfer).toBeDisabled()
  await load(parts)
  await expect(transfer).toBeEnabled()
  await expectTransferOrder()
  await load({ ...parts, CPU: 2 })
  await expect(page.getByRole('region', { name: '兼容分项诊断' })).toContainText('发现冲突')
  await expect(transfer).toBeDisabled()
  const { GPU: _gpu, ...base } = parts
  await load({ ...base, CPU: integratedCpu }, integratedCpu)
  await expect(transfer).toBeEnabled()
  await expectTransferOrder()
  await page.route('**/api/compatibility/check', route => route.fulfill({ status: 503, json: { code: 'UNAVAILABLE' } }))
  await page.getByRole('button', { name: '刷新价格与库存', exact: true }).click()
  await expect(page.getByRole('region', { name: '兼容分项诊断' })).toContainText('检测暂不可用')
  await expect(transfer).toBeDisabled()
  await page.unroute('**/api/compatibility/check')
})
