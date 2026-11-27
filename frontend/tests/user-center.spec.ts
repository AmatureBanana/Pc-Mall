import { expect, test, type Page } from '@playwright/test'

const account = { id: 3101, username: 'lee_jay', nickname: 'Lee Jay', role: 'USER' }
const number = (id: number) => `O${id.toString(16).padStart(32, '0')}`
function item(id: number, name: string, category: string, price: string, quantity = 1, configurationId: number | null = null) {
  return { productId: id, sku: `PREVIEW-${id}`, name, model: name, category, price, quantity,
    amount: (Number(price) * quantity).toFixed(2), configurationId }
}
function sampleOrders() {
  const header = { currency: 'CNY', createdAt: '2026-11-16T06:26:31Z', paymentDeadline: '2026-11-16T06:56:31Z',
    paidAt: null as string | null, cancelledAt: null as string | null, cancellationReason: null as string | null }
  return [
    { ...header, orderNumber: number(1), status: 'PENDING_PAYMENT', total: '799.00', configurations: [],
      items: [item(1, '三星 990 PRO 1TB NVMe SSD', 'SSD', '799.00')] },
    { ...header, orderNumber: number(2), status: 'PAID', paidAt: '2026-11-16T06:29:00Z', total: '5392.00', configurations: [{ id: 7, amount: '5392.00' }],
      items: [item(2, 'Intel Core i5-14600K', 'CPU', '1699.00', 1, 7), item(3, '华硕 TUF B760M-PLUS WIFI D4', 'MOTHERBOARD', '999.00', 1, 7),
        item(4, '金士顿 FURY 16GB DDR5 6000', 'MEMORY', '399.00', 2, 7), item(5, '西数 WD_BLACK SN770 2TB', 'SSD', '899.00', 1, 7),
        item(6, '美商海盗船 RM750e 750W', 'PSU', '399.00', 1, 7), item(7, '侧透 ATX 机箱', 'CASE', '399.00', 1, 7), item(8, '利民 PA120 散热器', 'COOLER', '199.00', 1, 7)] },
    { ...header, orderNumber: number(3), status: 'COMPLETED', paidAt: '2026-11-16T06:29:00Z', total: '2498.00', configurations: [],
      items: [item(9, '芝奇 Trident Z5 RGB 32GB DDR5 6400', 'MEMORY', '999.00'), item(10, 'Intel Core i5-14600KF', 'CPU', '1499.00')] },
  ]
}
async function installPreview(page: Page, extra = false) {
  const orders = sampleOrders()
  if (extra) for (let i = 4; i <= 13; i++) orders.push({ ...orders[0]!, orderNumber: number(i), status: i === 4 ? 'PENDING_PAYMENT' : 'PAID',
    total: '1499.00', items: [item(i + 20, i === 4 ? '备用测试 CPU' : `Intel Core i5 测试 ${i}`, 'CPU', '1499.00')] })
  const requests: URL[] = []
  await page.route('**/api/auth/me', route => route.fulfill({ json: { data: account } }))
  await page.route('**/api/account/profile', route => route.fulfill({ json: { data: account } }))
  await page.route('**/api/auth/csrf', route => route.fulfill({ json: { data: { token: 'preview', headerName: 'X-CSRF-TOKEN' } } }))
  await page.route('**/api/cart', route => route.fulfill({ json: { data: { revision: 0, items: [], configurations: [], total: '0.00', validTotal: '0.00', validCount: 0, lineCount: 0 } } }))
  await page.route('**/api/orders**', async route => {
    const url = new URL(route.request().url())
    if (url.pathname === '/api/orders') {
      requests.push(url)
      const keyword = url.searchParams.get('keyword')?.toLowerCase() ?? ''
      const common = orders.filter(order => order.orderNumber.includes(keyword) || order.items.some(item => item.name.toLowerCase().includes(keyword)))
      const counts: Record<string, number> = { ALL: common.length, PENDING_PAYMENT: 0, PAID: 0, COMPLETED: 0, CANCELLED: 0 }
      for (const order of common) counts[order.status] = (counts[order.status] ?? 0) + 1
      const selected = common.filter(order => !url.searchParams.get('status') || order.status === url.searchParams.get('status'))
      const pageNumber = Number(url.searchParams.get('page') ?? 1), pageSize = Number(url.searchParams.get('pageSize') ?? 20)
      return route.fulfill({ json: { data: { items: selected.slice((pageNumber - 1) * pageSize, pageNumber * pageSize), page: pageNumber, pageSize, total: selected.length, counts } } })
    }
    const selected = orders.find(order => url.pathname.includes(order.orderNumber))!
    if (url.pathname.endsWith('/pay')) { selected.status = 'PAID'; selected.paidAt = '2026-11-16T06:30:00Z' }
    if (url.pathname.endsWith('/cancel')) { selected.status = 'CANCELLED'; selected.cancelledAt = '2026-11-16T06:30:00Z'; selected.cancellationReason = 'USER' }
    return route.fulfill({ json: { data: selected } })
  })
  return { requests, orders }
}

test('four account entries navigate to the shared workspace and orders match the reference layout', async ({ page }) => {
  const errors: string[] = []
  page.on('pageerror', error => errors.push(error.message))
  await installPreview(page)
  await page.goto('/')
  const trigger = page.getByRole('button', { name: '用户菜单', exact: true })
  await trigger.click()
  const menu = page.getByRole('navigation', { name: '用户功能', exact: true })
  await expect(menu.getByRole('link')).toHaveText(['个人中心', '订单管理', '我的购物车', '账号与安全'])
  await page.keyboard.press('Escape')
  await expect(menu).not.toBeVisible()
  await expect(trigger).toBeFocused()
  await trigger.click()
  await menu.getByRole('link', { name: '订单管理', exact: true }).click()
  const history = page.getByRole('main', { name: '我的订单' })
  await expect(history.getByRole('article')).toHaveCount(3)
  const sidebar = page.getByRole('navigation', { name: '用户中心导航' })
  await expect(sidebar.getByRole('link', { name: '订单管理' })).toHaveAttribute('aria-current', 'page')
  await expect(history).toContainText('¥5,392.00')
  await page.screenshot({ path: '../.local/user-center-order-desktop.png', fullPage: true, animations: 'disabled' })
  await history.getByRole('button', { name: /配置组：DIY/ }).click()
  await expect(history.getByText('配置组成交小计', { exact: true })).toBeVisible()
  await sidebar.getByRole('link', { name: '个人中心', exact: true }).click()
  await expect(page.getByRole('main', { name: '个人中心' }).getByLabel('昵称', { exact: true })).toHaveValue('Lee Jay')
  await sidebar.getByRole('link', { name: '账号与安全' }).click()
  await expect(page.getByRole('main', { name: '账号与安全' }).getByLabel('旧密码', { exact: true })).toBeVisible()
  await sidebar.getByRole('link', { name: '我的购物车' }).click()
  await expect(page.getByRole('main', { name: '我的购物车' })).toBeVisible()
  await sidebar.getByRole('link', { name: '订单管理' }).click()
  await expect(history.getByRole('article')).toHaveCount(3)
  await page.setViewportSize({ width: 390, height: 844 })
  await expect(sidebar.getByRole('link')).toHaveCount(4)
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  await page.screenshot({ path: '../.local/user-center-order-mobile.png', fullPage: true, animations: 'disabled' })
  expect(errors).toEqual([])
})

test('order filters reset pagination and card payment and cancellation refresh the result', async ({ page }) => {
  const { requests } = await installPreview(page, true)
  await page.goto('/#orders')
  const history = page.getByRole('main', { name: '我的订单' })
  await expect(history.getByRole('article')).toHaveCount(10)
  await history.getByRole('button', { name: '下一页', exact: true }).click()
  await expect(history.getByRole('article')).toHaveCount(3)
  await history.getByRole('textbox', { name: '搜索订单号或商品名称' }).fill('三星')
  await history.getByRole('textbox', { name: '搜索订单号或商品名称' }).press('Enter')
  await expect(history.getByRole('article')).toHaveCount(1)
  expect(requests.at(-1)?.searchParams.get('page')).toBe('1')
  await history.getByRole('button', { name: '立即支付', exact: true }).click()
  const payment = page.getByRole('dialog', { name: '确认模拟支付' })
  await expect(payment).toContainText('¥799.00')
  await payment.getByRole('button', { name: '确认模拟支付', exact: true }).click()
  await expect(payment).not.toBeVisible()
  await expect(history.getByRole('article')).toContainText('已支付')
  await history.getByRole('textbox', { name: '搜索订单号或商品名称' }).fill('备用')
  await history.getByRole('textbox', { name: '搜索订单号或商品名称' }).press('Enter')
  await expect(history.getByRole('article')).toHaveCount(1)
  await history.getByRole('button', { name: '取消订单', exact: true }).click()
  const cancel = page.getByRole('dialog', { name: '确认取消订单' })
  await cancel.getByRole('button', { name: '确认取消', exact: true }).click()
  await expect(history.getByRole('article')).toContainText('本人主动取消')
  await history.getByLabel('订单状态筛选').selectOption('PENDING_PAYMENT')
  await expect(history).toContainText('没有符合筛选条件的订单')
  expect(requests.at(-1)?.searchParams.get('status')).toBe('PENDING_PAYMENT')
  await history.getByLabel('开始日期', { exact: true }).fill('2026-11-01')
  await history.getByLabel('结束日期', { exact: true }).fill('2026-11-16')
  await expect.poll(() => requests.at(-1)?.searchParams.get('endDate')).toBe('2026-11-16')
})


test('configuration summaries show only key parts by default and expanded parts follow the DIY display order', async ({ page }) => {
  const { orders } = await installPreview(page)
  const integrated = structuredClone(orders[1]!)
  integrated.orderNumber = number(14)
  orders.push(integrated)
  const discrete = orders[1]!
  discrete.items.push(item(11, 'NVIDIA GeForce RTX 4060 8GB', 'GPU', '1999.00', 1, 7))
  discrete.total = '7391.00'
  discrete.configurations[0]!.amount = '7391.00'
  await page.goto('/#orders')
  const history = page.getByRole('main', { name: '我的订单' })
  const card = history.getByRole('article', { name: `订单 ${number(2)}`, exact: true })
  const group = card.getByRole('region', { name: '成交配置组 1', exact: true })
  const toggle = group.getByRole('button', { name: /配置组：DIY/ })
  await expect(toggle).toHaveAttribute('aria-expanded', 'false')
  await expect(group.getByRole('list', { name: '配置组成交明细' })).toHaveCount(0)
  await expect(group.getByRole('term')).toHaveText(['CPU', '内存', '显卡', '硬盘'])
  await expect(group.getByRole('definition')).toHaveText([
    'Intel Core i5-14600K', '金士顿 FURY 16GB DDR5 6000 × 2', 'NVIDIA GeForce RTX 4060 8GB', '西数 WD_BLACK SN770 2TB',
  ])
  await expect(group).not.toContainText('华硕')
  await expect(group).not.toContainText('散热器')
  await card.screenshot({ path: '../.local/order-config-collapsed.png', animations: 'disabled' })
  await toggle.click()
  const parts = group.getByRole('list', { name: '配置组成交明细' }).getByRole('listitem')
  await expect(parts).toHaveText([
    /CPU · Intel Core i5-14600K/, /CPU散热器 · 利民 PA120/, /主板 · 华硕 TUF/, /内存 · 金士顿 FURY/,
    /显卡 · NVIDIA GeForce RTX 4060/, /硬盘 · 西数 WD_BLACK/, /电源 · 美商海盗船/, /机箱 · 侧透 ATX/, /配置组成交小计.*7,391.00/,
  ])
  await card.screenshot({ path: '../.local/order-config-expanded.png', animations: 'disabled' })
  await toggle.click()
  await expect(toggle).toHaveAttribute('aria-expanded', 'false')
  const integratedGroup = history.getByRole('article', { name: `订单 ${number(14)}`, exact: true }).getByRole('region', { name: '成交配置组 1' })
  await expect(integratedGroup.getByRole('definition').nth(2)).toHaveText('核显（随 CPU 提供）')
  await page.setViewportSize({ width: 390, height: 844 })
  await expect(group.getByRole('term')).toHaveText(['CPU', '内存', '显卡', '硬盘'])
  await expect(group.getByRole('definition').last()).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  await page.reload()
  await expect(toggle).toHaveAttribute('aria-expanded', 'false')
  await card.getByRole('link', { name: '查看详情', exact: true }).click()
  const detail = page.getByRole('main', { name: '订单结果' }).getByRole('region', { name: '成交配置组', exact: true })
  await expect(detail.locator('tbody tr')).toHaveText([
    /Intel Core i5-14600K/, /利民 PA120/, /华硕 TUF/, /金士顿 FURY/, /NVIDIA GeForce RTX 4060/,
    /西数 WD_BLACK/, /美商海盗船/, /侧透 ATX/,
  ])
  await expect(detail.locator('tbody tr > td:nth-child(2)')).toHaveText([
    /\/ CPU$/, /\/ CPU散热器$/, /\/ 主板$/, /\/ 内存$/, /\/ 显卡$/, /\/ 硬盘$/, /\/ 电源$/, /\/ 机箱$/,
  ])
})
