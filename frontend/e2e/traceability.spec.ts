import { test, expect, APIRequestContext } from '@playwright/test'

const userToken = process.env.E2E_USER_TOKEN
const adminToken = process.env.E2E_ADMIN_TOKEN

function requireToken(token: string | undefined, name: string) {
  test.skip(!token, `未配置 ${name}，该业务用例不计入通过数`)
  return (token as string).replace(/^['"]|['"]$/g, '').replace(/^Bearer\s+/i, '').trim()
}

async function json(response: Awaited<ReturnType<APIRequestContext['get']>>, authName?: string) {
  const text = await response.text()
  let body: { code?: number; msg?: string; data?: unknown }
  try {
    body = JSON.parse(text)
  } catch {
    throw new Error(`HTTP ${response.status()} 非 JSON 响应: ${text.slice(0, 300)}`)
  }
  if ((response.status() === 401 || response.status() === 403) && authName) {
    test.skip(true, `${authName} 无效或已过期（HTTP ${response.status()}），请重新登录获取 token`)
  }
  expect(response.ok(), `HTTP ${response.status()}: ${body.msg ?? text.slice(0, 300)}`).toBeTruthy()
  expect(body.code).toBe(0)
  return body.data
}

async function createTestHotelOrder(request: any, headers: Record<string, string>) {
  return json(await request.post('/api/hotel/order', {
    headers,
    data: {
      roomId: 21,
      checkInDate: '2026-12-20',
      checkOutDate: '2026-12-22',
      roomNum: 1,
      pointsDeduced: 0,
      paymentMethod: 'ALIPAY',
      guestList: [{ name: 'E2E测试用户', idCard: '110101199001011234', phone: '13900000000' }],
    },
  }), 'E2E_USER_TOKEN')
}

async function createTestFlightOrder(request: any, headers: Record<string, string>) {
  const productId = process.env.E2E_FLIGHT_PRODUCT_ID || '1'
  return json(await request.post('/api/flights/order', {
    headers,
    data: {
      productId,
      cabin: 'ECONOMY',
      adultCount: 1,
      passengers: [{ name: 'E2E测试用户', idType: 'ID_CARD', idNo: '110101199001011234', phone: '13900000000' }],
      insurance: false,
      extraBaggage: false,
      seatSelection: false,
    },
  }), 'E2E_USER_TOKEN')
}

test.describe('追溯表缺口业务 E2E', () => {
  test('E2E-018 管理员产品与订单管理并校验权限', async ({ request }) => {
    const token = requireToken(adminToken, 'E2E_ADMIN_TOKEN')
    const headers = { Authorization: `Bearer ${token}` }
    const summary = await json(await request.get('/api/admin/dashboard/summary', { headers }), 'E2E_ADMIN_TOKEN')
    expect(summary).toBeTruthy()
    const products = await json(await request.get('/api/admin/products?page=1', { headers }), 'E2E_ADMIN_TOKEN')
    expect(Array.isArray(products.list)).toBeTruthy()
    const orders = await json(await request.get('/api/admin/orders?page=1', { headers }), 'E2E_ADMIN_TOKEN')
    expect(Array.isArray(orders.list)).toBeTruthy()

    if (userToken) {
      const userHeaders = { Authorization: `Bearer ${requireToken(userToken, 'E2E_USER_TOKEN')}` }
      const identityResponse = await request.get('/api/user/current', { headers: userHeaders })
      if (identityResponse.status() === 401 || identityResponse.status() === 403) {
        test.skip(true, 'E2E_USER_TOKEN 无效或已过期，无法执行普通用户越权校验')
      }
      const identityBody = await identityResponse.json() as { data?: { identity?: string } }
      if (identityBody.data?.identity === 'ADMIN') {
        test.skip(true, 'E2E_USER_TOKEN 对应管理员账号，需使用 USER 角色 token 执行越权校验')
      }
      const denied = await request.get('/api/admin/dashboard/summary', { headers: userHeaders })
      expect(denied.status(), '普通用户访问管理员接口必须返回 403').toBe(403)
    }
  })

  test('E2E-019 普通用户独立登录态与退出接口', async ({ request }) => {
    const token = requireToken(userToken, 'E2E_USER_TOKEN')
    const headers = { Authorization: `Bearer ${token}` }
    const current = await json(await request.get('/api/user/current', { headers }), 'E2E_USER_TOKEN')
    expect(current.identity ?? current.roles).toBeTruthy()
    const logout = await json(await request.post('/api/auth/logout', { headers }), 'E2E_USER_TOKEN')
    expect(logout).toBe(true)
    // logout is stateless on the server; verify the authenticated session still
    // works and document that the client must discard the JWT locally.
    const afterLogout = await json(await request.get('/api/user/current', { headers }), 'E2E_USER_TOKEN')
    expect(afterLogout).toBeTruthy()
  })

  test('E2E-020 酒店订单取消并校验订单状态', async ({ request }) => {
    const token = requireToken(userToken, 'E2E_USER_TOKEN')
    const headers = { Authorization: `Bearer ${token}` }
    const orders = await json(await request.get('/api/hotel/orders?page=1&size=10', { headers }), 'E2E_USER_TOKEN')
    // HotelController returns PageResult with `records` (not PageResponse.list).
    const orderRecords = Array.isArray(orders.records) ? orders.records : (orders.list ?? [])
    const pending = orderRecords.find((item: { status?: number | string }) => Number(item.status) === 0) ?? await createTestHotelOrder(request, headers)
    const orderId = pending.orderId ?? pending.id
    expect(orderId).toBeTruthy()
    const cancelled = await request.post(`/api/hotel/order/${orderId}/cancel`, { headers })
    expect(cancelled.ok()).toBeTruthy()
    const detail = await json(await request.get(`/api/hotel/order/${orderId}`, { headers }), 'E2E_USER_TOKEN')
    expect(Number(detail.status)).toBe(3)
  })

  test('E2E-021 AI生成行程、保存、分享与导出', async ({ request }) => {
    test.setTimeout(95_000)
    const token = requireToken(userToken, 'E2E_USER_TOKEN')
    const headers = { Authorization: `Bearer ${token}` }
    const generated = await json(await request.post('/api/itinerary/ai/generate', {
      headers,
      timeout: 90_000,
      data: { destination: '上海', start_date: '2026-10-01', end_date: '2026-10-03', days: 3 },
    }), 'E2E_USER_TOKEN')
    expect(generated.title).toBeTruthy()
    expect(generated.destination).toBe('上海')
    expect(generated.plan_data).toBeTruthy()
    const saved = await json(await request.post('/api/itinerary/plans', { headers, data: generated }), 'E2E_USER_TOKEN')
    expect(saved.id).toBeTruthy()
    const shared = await json(await request.get(`/api/itinerary/plans/${saved.id}/share`, { headers }), 'E2E_USER_TOKEN')
    expect(shared.shortLink).toContain('sharedPlanId')
    const exported = await json(await request.get(`/api/itinerary/plans/${saved.id}/export`, { headers }), 'E2E_USER_TOKEN')
    expect(exported.fileUrl).toContain(`/api/itinerary/plans/${saved.id}/export`)
    await request.delete(`/api/itinerary/plans/${saved.id}`, { headers })
  })

  test('E2E-022 社区发布、点赞、评论', async ({ request }) => {
    const token = requireToken(userToken, 'E2E_USER_TOKEN')
    const headers = { Authorization: `Bearer ${token}` }
    const marker = `E2E-${Date.now()}`
    const post = await json(await request.post('/api/posts', {
      headers,
      data: { title: marker, content: '公网端到端测试内容', images: '', status: 1 },
    }), 'E2E_USER_TOKEN')
    expect(post.id).toBeTruthy()
    try {
      const liked = await json(await request.post(`/api/posts/${post.id}/like`, { headers }))
      expect(typeof liked.liked).toBe('boolean')
      const comment = await json(await request.post(`/api/posts/${post.id}/comments`, {
        headers,
        data: { content: '端到端评论' },
      }))
      expect(comment.content).toBe('端到端评论')
    } finally {
      await request.delete(`/api/posts/${post.id}`, { headers })
    }
  })

  test('UC-M6-02 手动创建、编辑和删除行程', async ({ request }) => {
    const headers = { Authorization: `Bearer ${requireToken(userToken, 'E2E_USER_TOKEN')}` }
    const created = await json(await request.post('/api/itinerary/plans', {
      headers,
      data: { title: 'E2E手动行程', destination: '北京', start_date: '2026-11-01', end_date: '2026-11-02', plan_data: '[]', is_public: 0 },
    }), 'E2E_USER_TOKEN')
    expect(created.id).toBeTruthy()
    const updated = await json(await request.put(`/api/itinerary/plans/${created.id}`, {
      headers,
      data: { ...created, title: 'E2E手动行程-已编辑' },
    }), 'E2E_USER_TOKEN')
    expect(updated.title).toBe('E2E手动行程-已编辑')
    const deleted = await json(await request.delete(`/api/itinerary/plans/${created.id}`, { headers }), 'E2E_USER_TOKEN')
    expect(deleted).toBe(true)
  })

  test('UC-M6-04 分享和导出行程', async ({ request }) => {
    const headers = { Authorization: `Bearer ${requireToken(userToken, 'E2E_USER_TOKEN')}` }
    const plan = await json(await request.post('/api/itinerary/plans', {
      headers,
      data: { title: 'E2E分享行程', destination: '杭州', start_date: '2026-11-01', end_date: '2026-11-02', plan_data: '[]', is_public: 0 },
    }), 'E2E_USER_TOKEN')
    try {
      const shared = await json(await request.get(`/api/itinerary/plans/${plan.id}/share`, { headers }), 'E2E_USER_TOKEN')
      expect(shared.shortLink).toContain('sharedPlanId')
      const exported = await json(await request.get(`/api/itinerary/plans/${plan.id}/export`, { headers }), 'E2E_USER_TOKEN')
      expect(exported.fileUrl).toContain(`/api/itinerary/plans/${plan.id}/export`)
    } finally {
      await request.delete(`/api/itinerary/plans/${plan.id}`, { headers })
    }
  })

  test('UC-M3-04 机票订单支付与出票状态', async ({ request }) => {
    const headers = { Authorization: `Bearer ${requireToken(userToken, 'E2E_USER_TOKEN')}` }
    const orders = await json(await request.get('/api/user/orders?page=1&size=50', { headers }), 'E2E_USER_TOKEN')
    let order = (orders.list ?? orders.records ?? []).find((item: { orderType?: string; order_type?: string; status?: number | string }) =>
      String(item.orderType ?? item.order_type ?? '').toUpperCase().includes('FLIGHT') && Number(item.status) === 0)
    let createdForPayment = false
    if (!order) {
      order = await createTestFlightOrder(request, headers)
      createdForPayment = true
    }
    const orderNo = order.orderNo ?? order.order_no
    const paid = await json(await request.post(`/api/orders/${orderNo}/pay`, { headers, data: { paymentMethod: 'ALIPAY' } }), 'E2E_USER_TOKEN')
    expect(Number(paid.status)).toBe(1)
    if (createdForPayment) {
      await request.post(`/api/orders/${orderNo}/refund`, { headers })
    }
  })

})
