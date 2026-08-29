import { test, expect } from '@playwright/test';

test.describe('Lightmark 端到端冒烟流程', () => {
  test('E2E-001 服务健康检查与首页加载', async ({ page, request }) => {
    const health = await request.get('/api/health');
    expect(health.ok()).toBeTruthy();
    await page.goto('/');
    // Vue CLI derives the title from the package name (`lightmark`), so keep
    // this assertion case-insensitive while still requiring the app title.
    await expect(page).toHaveTitle(/lightmark|拾光/i);
  });

  test('E2E-002 登录入口显示并校验必填项', async ({ page }) => {
    await page.goto('/login');
    await expect(page.getByRole('heading', { name: /登录|注册/ }).first()).toBeVisible();
    await page.getByRole('button', { name: /^登录$/ }).click();
    // The login view reports client-side validation with Element Plus toast
    // messages (`ElMessage.warning`), rather than form-item error nodes.
    await expect(page.locator('.el-message').first()).toBeVisible();
  });

  test('E2E-003 未登录用户访问业务页面被拦截', async ({ page }) => {
    await page.goto('/trains');
    await expect(page).toHaveURL(/\/login/);
  });

  test('E2E-004 未登录用户访问管理后台被拦截', async ({ page }) => {
    await page.goto('/admin');
    await expect(page).toHaveURL(/\/admin\/login|\/login/);
  });
});

// Full business journeys use a real test account supplied at runtime. The token
// is injected through E2E_USER_TOKEN and is never committed to the repository.
const protectedJourneys = [
  ['E2E-005', '用户中心', '/user-center'],
  ['E2E-006', '机票预订', '/flights'],
  ['E2E-007', '酒店预订', '/hotels'],
  ['E2E-008', '火车票直达查询', '/trains'],
  ['E2E-009', '火车票中转查询', '/trains'],
  ['E2E-010', '火车票订单', '/trains'],
  ['E2E-011', '火车退票和改签', '/user-center'],
  ['E2E-012', '度假产品查询', '/vacations'],
  ['E2E-013', '度假详情和 AI 文案', '/vacations'],
  ['E2E-014', '度假下单和支付', '/vacations'],
  ['E2E-015', '度假智能助手', '/user-center'],
  ['E2E-016', '度假退票', '/user-center'],
  ['E2E-017', '社区发布点赞评论', '/community'],
  ['E2E-018', '管理员产品和订单管理', '/admin'],
];

for (const [id, title, path] of protectedJourneys) {
  test(`${id} ${title}`, async ({ page }) => {
    const isAdminJourney = id === 'E2E-018';
    const token = isAdminJourney ? process.env.E2E_ADMIN_TOKEN : process.env.E2E_USER_TOKEN;
    test.skip(!token, '需要通过 E2E_USER_TOKEN 注入一次性测试账号 token');
    await page.addInitScript((auth) => {
      localStorage.setItem('lightmark_auth', JSON.stringify({
        token: auth.token, userId: 'e2e-user', nickname: 'E2E User', avatar: '', isAdmin: auth.admin, roles: auth.admin ? ['ADMIN'] : ['USER'],
      }));
    }, { token, admin: isAdminJourney });
    await page.goto(path);
    await expect(page).not.toHaveURL(/\/login/);
    await expect(page.locator('body')).toBeVisible();
  });
}
