import { test, expect, APIRequestContext } from '@playwright/test'

const userToken = process.env.E2E_USER_TOKEN
const adminToken = process.env.E2E_ADMIN_TOKEN

function requireToken(token: string | undefined, name: string) {
  test.skip(!token, `未配置 ${name}，该业务用例不计入通过数`)
  return token as string
}

async function json(response: Awaited<ReturnType<APIRequestContext['get']>>) {
  expect(response.ok()).toBeTruthy()
  const body = await response.json()
  expect(body.code).toBe(0)
  return body.data
}

test.describe('追溯表缺口业务 E2E', () => {
  test('E2E-018 管理员产品与订单管理并校验权限', async ({ request }) => {
    const token = requireToken(adminToken, 'E2E_ADMIN_TOKEN')
    const headers = { Authorization: `Bearer ${token}` }
    const summary = await json(await request.get('/api/admin/dashboard/summary', { headers }))
    expect(summary).toBeTruthy()
    const products = await json(await request.get('/api/admin/products?page=1', { headers }))
    expect(Array.isArray(products.list)).toBeTruthy()
    const orders = await json(await request.get('/api/admin/orders?page=1', { headers }))
    expect(Array.isArray(orders.list)).toBeTruthy()

    if (userToken) {
      const denied = await request.get('/api/admin/dashboard/summary', {
        headers: { Authorization: `Bearer ${userToken}` },
      })
      expect(denied.status()).toBe(403)
    }
  })

  test('E2E-019 普通用户独立登录态与退出接口', async ({ request }) => {
    const token = requireToken(userToken, 'E2E_USER_TOKEN')
    const headers = { Authorization: `Bearer ${token}` }
    const current = await json(await request.get('/api/user/current', { headers }))
    expect(current.identity ?? current.roles).toBeTruthy()
    const logout = await json(await request.post('/api/auth/logout', { headers }))
    expect(logout).toBe(true)
    const guest = await request.get('/api/user/current')
    expect([401, 403]).toContain(guest.status())
  })

  test('E2E-020 酒店订单取消并校验订单状态', async ({ request }) => {
    const token = requireToken(userToken, 'E2E_USER_TOKEN')
    const headers = { Authorization: `Bearer ${token}` }
    const orders = await json(await request.get('/api/hotel/orders?page=1&size=10', { headers }))
    const pending = (orders.list ?? []).find((item: { status?: number }) => item.status === 0)
    test.skip(!pending, '没有待支付酒店订单，无法执行取消链路')
    const cancelled = await request.post(`/api/hotel/order/${pending.id}/cancel`, { headers })
    expect(cancelled.ok()).toBeTruthy()
    const detail = await json(await request.get(`/api/hotel/order/${pending.id}`, { headers }))
    expect(detail.status).toBe(3)
  })

  test('E2E-021 AI生成行程、保存、分享与导出', async ({ request }) => {
    test.setTimeout(95_000)
    const token = requireToken(userToken, 'E2E_USER_TOKEN')
    const headers = { Authorization: `Bearer ${token}` }
    const generated = await json(await request.post('/api/itinerary/ai/generate', {
      headers,
      timeout: 90_000,
      data: { destination: '上海', startDate: '2026-10-01', endDate: '2026-10-03', days: 3 },
    }))
    expect(generated.title).toBeTruthy()
    expect(generated.destination).toBe('上海')
    expect(generated.plan_data).toBeTruthy()
    const saved = await json(await request.post('/api/itinerary/plans', { headers, data: generated }))
    expect(saved.id).toBeTruthy()
    const shared = await json(await request.get(`/api/itinerary/plans/${saved.id}/share`, { headers }))
    expect(shared.shortLink).toContain('sharedPlanId')
    const exported = await json(await request.get(`/api/itinerary/plans/${saved.id}/export`, { headers }))
    expect(exported.fileUrl).toContain(`/api/itinerary/plans/${saved.id}/export`)
    await request.delete(`/api/itinerary/plans/${saved.id}`, { headers })
  })

  test('E2E-022 社区发布、点赞、评论与AI情感分析', async ({ request }) => {
    const token = requireToken(userToken, 'E2E_USER_TOKEN')
    const headers = { Authorization: `Bearer ${token}` }
    const marker = `E2E-${Date.now()}`
    const post = await json(await request.post('/api/posts', {
      headers,
      data: { title: marker, content: '公网端到端测试内容', images: '', status: 1 },
    }))
    expect(post.id).toBeTruthy()
    const liked = await json(await request.post(`/api/posts/${post.id}/like`, { headers }))
    expect(typeof liked.liked).toBe('boolean')
    const comment = await json(await request.post(`/api/posts/${post.id}/comments`, {
      headers,
      data: { content: '端到端评论' },
    }))
    expect(comment.content).toBe('端到端评论')
    const sentiment = await json(await request.post('/api/ai/review/sentiment', {
      headers,
      data: { content: comment.content },
    }))
    expect(sentiment).toBeTruthy()
    await request.delete(`/api/posts/${post.id}`, { headers })
  })
})
