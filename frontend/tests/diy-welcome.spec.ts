import { expect, test } from '@playwright/test'

test('task21: the default page is the DIY recommendation welcome screen', async ({ page }) => {
  const errors: string[] = []
  page.on('pageerror', error => errors.push(error.message))
  await page.goto('/')
  const welcome = page.getByRole('main', { name: 'DIY 装机推荐' })
  await expect(welcome.getByRole('heading', { name: 'DIY 电脑装机', exact: true })).toBeVisible()
  await expect(welcome.getByRole('article')).toHaveCount(3)
  for (const name of ['办公推荐', '游戏推荐', '创作推荐']) {
    await expect(welcome.getByRole('heading', { name, exact: true })).toBeVisible()
  }
  await expect(page.getByRole('navigation', { name: '商城导航' }).getByRole('link', { name: 'DIY 装机' })).toHaveClass('active')
  await expect(page.getByRole('navigation', { name: '硬件分类', exact: true })).toHaveCount(0)
  await page.reload()
  await expect(welcome).toBeVisible()
  await page.screenshot({ path: '../docs/evidence/task21-welcome-desktop.png', fullPage: true, animations: 'disabled' })
  expect(errors).toEqual([])
})

test('task21: manual DIY preserves the current configuration through welcome, history and reload', async ({ page }) => {
  await page.goto('/')
  await page.getByRole('button', { name: '开始手动 DIY', exact: true }).click()
  await expect(page).toHaveURL(/#diy$/)
  const configuration = page.getByRole('region', { name: '当前配置' })
  await page.getByRole('button', { name: '选择 Intel Core i5-14600KF', exact: true }).click()
  await expect(configuration.getByRole('link', { name: 'Intel Core i5-14600KF', exact: true })).toBeVisible()
  const before = await page.evaluate(() => sessionStorage.getItem('pc-mall.diy.workspace.v1'))
  await page.getByRole('navigation', { name: '商城导航' }).getByRole('link', { name: 'DIY 装机' }).click()
  await expect(page.getByRole('main', { name: 'DIY 装机推荐' })).toBeVisible()
  await page.goBack()
  await expect(configuration.getByRole('link', { name: 'Intel Core i5-14600KF', exact: true })).toBeVisible()
  await page.reload()
  await expect(configuration.getByRole('link', { name: 'Intel Core i5-14600KF', exact: true })).toBeVisible()
  expect(await page.evaluate(() => sessionStorage.getItem('pc-mall.diy.workspace.v1'))).toBe(before)
})

test('task21: catalog navigation, product details and global search still reach the real catalog', async ({ page }) => {
  await page.goto('/')
  await page.getByRole('link', { name: '商品浏览', exact: true }).click()
  await expect(page).toHaveURL(/#catalog$/)
  await page.getByRole('link', { name: 'Intel Core i5-14600KF', exact: true }).click()
  const detail = page.getByRole('dialog', { name: '商品详情' })
  await expect(detail).toBeVisible()
  await detail.getByRole('button', { name: '关闭详情' }).click()
  await expect(page).toHaveURL(/#catalog$/)
  await page.getByRole('link', { name: 'DIY 装机', exact: true }).click()
  await page.getByRole('textbox', { name: '搜索商品', exact: true }).fill('14600KF')
  await page.getByRole('button', { name: '搜索', exact: true }).click()
  await expect(page).toHaveURL(/#catalog$/)
  await expect(page.locator('.product-card')).toHaveCount(1)
  await expect(page.getByRole('heading', { name: 'Intel Core i5-14600KF', exact: true })).toBeVisible()
  await page.getByRole('link', { name: '装机猫首页', exact: true }).first().click()
  await expect(page.getByRole('main', { name: 'DIY 装机推荐' })).toBeVisible()
})

for (const name of ['游戏推荐', '创作推荐']) {
  test(`task21: ${name} explains availability and can continue to manual DIY without a fake result`, async ({ page }) => {
    const mutations: string[] = []
    page.on('request', request => {
      if (request.method() !== 'GET' && request.url().includes('/api/')) mutations.push(request.url())
    })
    await page.goto('/')
    const card = page.getByRole('article').filter({ has: page.getByRole('heading', { name, exact: true }) })
    await card.getByRole('button').click()
    const dialog = page.getByRole('dialog', { name, exact: true })
    await expect(dialog.getByRole('status')).toContainText('即将开放')
    await dialog.getByRole('button', { name: '返回选择' }).click()
    await expect(dialog).not.toBeVisible()
    expect(mutations).toEqual([])
    await card.getByRole('button').click()
    await dialog.getByRole('button', { name: '开始手动 DIY', exact: true }).click()
    await expect(page).toHaveURL(/#diy$/)
    await expect(page.getByRole('region', { name: '当前配置' })).toBeVisible()
  })
}

test('task21: the mobile welcome screen and availability dialog fit the viewport', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto('/')
  const welcome = page.getByRole('main', { name: 'DIY 装机推荐' })
  await expect(welcome.getByRole('article')).toHaveCount(3)
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  await page.screenshot({ path: '../docs/evidence/task21-welcome-mobile.png', fullPage: true, animations: 'disabled' })
  await welcome.getByRole('button', { name: '办公推荐 · 开始推荐' }).click()
  const dialog = page.getByRole('dialog', { name: '办公推荐' })
  await expect(dialog).toBeInViewport()
  const bounds = await dialog.boundingBox()
  expect(bounds!.width).toBeLessThanOrEqual(390)
})
