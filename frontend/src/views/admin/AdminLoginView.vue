<template>
  <div class="admin-login">
    <!-- 装饰性背景层 -->
    <div class="bg-layer">
      <div class="bg-grid"></div>
      <div class="bg-orb bg-orb--1"></div>
      <div class="bg-orb bg-orb--2"></div>
      <div class="bg-orb bg-orb--3"></div>
    </div>

    <!-- 登录卡片 -->
    <div class="login-card">
      <div class="card-glow"></div>

      <div class="card-header">
        <div class="brand">
          <span class="brand-icon">✦</span>
          <span class="brand-text">Lightmark</span>
        </div>
        <h1 class="card-title">管理后台</h1>
        <p class="card-desc">与主站共享账号体系，仅管理员可进入</p>
      </div>

      <el-form
        class="login-form"
        :model="form"
        label-position="top"
        @submit.prevent
      >
        <div class="field-group">
          <label class="field-label">账号</label>
          <div class="field-input-wrap">
            <span class="field-icon">📧</span>
            <el-input
              v-model="form.account"
              placeholder="手机号或邮箱"
              :prefix-icon="null"
            />
          </div>
        </div>

        <div class="field-group">
          <label class="field-label">密码</label>
          <div class="field-input-wrap">
            <span class="field-icon">🔒</span>
            <el-input
              v-model="form.password"
              type="password"
              placeholder="输入密码"
              show-password
              :prefix-icon="null"
            />
          </div>
        </div>

        <el-button class="login-btn" :loading="submitting" @click="submit">
          <span class="btn-text">{{
            submitting ? "验证中…" : "进入后台"
          }}</span>
          <span class="btn-arrow">→</span>
        </el-button>
      </el-form>

      <div class="card-footer">
        <router-link to="/login" class="back-link">
          <span class="back-arrow">←</span>
          返回用户登录
        </router-link>
      </div>

      <div class="card-divider"></div>
      <p class="card-version">v2.0 · 拾光旅行管理控制台</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { adminLoginApi } from "@/api/auth";
import { useAuthStore } from "@/stores/auth";

const route = useRoute();
const router = useRouter();
const authStore = useAuthStore();

const submitting = ref(false);
const form = reactive({
  account: "",
  password: "",
});

const submit = async () => {
  if (!form.account || !form.password) {
    ElMessage.warning("请填写账号和密码");
    return;
  }

  submitting.value = true;

  try {
    const loginRes = await adminLoginApi({
      account: form.account,
      password: form.password,
    });

    authStore.setSession(loginRes as unknown as Record<string, unknown>);

    if (!authStore.roles.includes("ADMIN")) {
      authStore.clearSession();
      ElMessage.error("该账号无后台权限");
      return;
    }

    ElMessage.success("后台登录成功");
    const redirect = String(route.query.redirect || "/admin/dashboard");
    await router.replace(redirect);
  } catch {
    // 错误已由请求拦截器统一处理
  } finally {
    submitting.value = false;
  }
};
</script>

<style scoped>
/* ── 基础布局 ── */
.admin-login {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  position: relative;
  background: #070b14;
  overflow: hidden;
  font-family: "DM Sans", "Noto Sans SC", sans-serif;
}

/* ── 背景层 ── */
.bg-layer {
  position: absolute;
  inset: 0;
  pointer-events: none;
  z-index: 0;
}

.bg-grid {
  position: absolute;
  inset: 0;
  background-image: linear-gradient(
      rgba(201, 149, 61, 0.03) 1px,
      transparent 1px
    ),
    linear-gradient(90deg, rgba(201, 149, 61, 0.03) 1px, transparent 1px);
  background-size: 48px 48px;
  mask-image: radial-gradient(
    ellipse 70% 60% at 50% 50%,
    black 30%,
    transparent 70%
  );
  -webkit-mask-image: radial-gradient(
    ellipse 70% 60% at 50% 50%,
    black 30%,
    transparent 70%
  );
}

.bg-orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
  opacity: 0.15;
}

.bg-orb--1 {
  width: 500px;
  height: 500px;
  background: #c9953d;
  top: -120px;
  right: -100px;
  animation: orbFloat 12s ease-in-out infinite;
}

.bg-orb--2 {
  width: 400px;
  height: 400px;
  background: #1e3f66;
  bottom: -80px;
  left: -80px;
  animation: orbFloat 16s ease-in-out infinite reverse;
}

.bg-orb--3 {
  width: 300px;
  height: 300px;
  background: #c45d42;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  animation: orbFloat 20s ease-in-out infinite;
}

@keyframes orbFloat {
  0%,
  100% {
    transform: translate(0, 0) scale(1);
  }
  33% {
    transform: translate(30px, -40px) scale(1.08);
  }
  66% {
    transform: translate(-20px, 30px) scale(0.95);
  }
}

/* ── 登录卡片 ── */
.login-card {
  width: min(420px, 100%);
  position: relative;
  z-index: 1;
  background: rgba(15, 23, 42, 0.75);
  backdrop-filter: blur(24px);
  -webkit-backdrop-filter: blur(24px);
  border-radius: 20px;
  padding: 40px 36px 32px;
  border: 1px solid rgba(255, 255, 255, 0.06);
  box-shadow: 0 24px 80px rgba(0, 0, 0, 0.5),
    inset 0 1px 0 rgba(255, 255, 255, 0.04);
  animation: cardEnter 0.7s cubic-bezier(0.22, 1, 0.36, 1) both;
}

@keyframes cardEnter {
  from {
    opacity: 0;
    transform: translateY(30px) scale(0.97);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

.card-glow {
  position: absolute;
  top: -1px;
  left: 20%;
  right: 20%;
  height: 1px;
  background: linear-gradient(
    90deg,
    transparent,
    rgba(201, 149, 61, 0.4),
    transparent
  );
  filter: blur(2px);
}

/* ── 头部 ── */
.card-header {
  text-align: center;
  margin-bottom: 32px;
}

.brand {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin-bottom: 16px;
}

.brand-icon {
  font-size: 26px;
  color: #c9953d;
  animation: brandPulse 3s ease-in-out infinite;
}

@keyframes brandPulse {
  0%,
  100% {
    opacity: 1;
    transform: scale(1);
  }
  50% {
    opacity: 0.7;
    transform: scale(0.95);
  }
}

.brand-text {
  font-family: "Playfair Display", serif;
  font-size: 24px;
  font-weight: 700;
  color: #ffffff;
  letter-spacing: 1.5px;
}

.card-title {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
  color: #ffffff;
  letter-spacing: -0.3px;
}

.card-desc {
  margin: 6px 0 0;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.35);
  line-height: 1.5;
}

/* ── 表单 ── */
.login-form {
  display: grid;
  gap: 20px;
}

.field-group {
  display: grid;
  gap: 6px;
}

.field-label {
  font-size: 12px;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.5);
  text-transform: uppercase;
  letter-spacing: 0.8px;
  padding-left: 2px;
}

.field-input-wrap {
  position: relative;
  display: flex;
  align-items: center;
}

.field-icon {
  position: absolute;
  left: 14px;
  font-size: 15px;
  z-index: 2;
  pointer-events: none;
  opacity: 0.5;
}

/* Element Plus 输入框覆盖 */
.field-input-wrap :deep(.el-input__wrapper) {
  background: rgba(255, 255, 255, 0.04) !important;
  border: 1px solid rgba(255, 255, 255, 0.08) !important;
  box-shadow: none !important;
  border-radius: 10px !important;
  padding-left: 42px !important;
  height: 48px;
  transition: all 0.3s ease;
}

.field-input-wrap :deep(.el-input__wrapper:hover) {
  border-color: rgba(201, 149, 61, 0.25) !important;
  background: rgba(255, 255, 255, 0.06) !important;
}

.field-input-wrap :deep(.el-input__wrapper.is-focus) {
  border-color: #c9953d !important;
  background: rgba(201, 149, 61, 0.06) !important;
  box-shadow: 0 0 0 3px rgba(201, 149, 61, 0.08) !important;
}

.field-input-wrap :deep(.el-input__inner) {
  color: #ffffff !important;
  font-size: 14px;
  font-weight: 400;
  height: 48px;
}

.field-input-wrap :deep(.el-input__inner::placeholder) {
  color: rgba(255, 255, 255, 0.2) !important;
}

/* ── 登录按钮 ── */
.login-btn {
  width: 100%;
  height: 50px;
  border-radius: 10px !important;
  border: none !important;
  background: linear-gradient(135deg, #c9953d, #b8862e) !important;
  color: #ffffff !important;
  font-size: 15px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  transition: all 0.3s cubic-bezier(0.22, 1, 0.36, 1) !important;
  position: relative;
  overflow: hidden;
  margin-top: 4px;
}

.login-btn::before {
  content: "";
  position: absolute;
  inset: 0;
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.15), transparent);
  opacity: 0;
  transition: opacity 0.3s ease;
}

.login-btn:hover:not(:disabled)::before {
  opacity: 1;
}

.login-btn:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 8px 30px rgba(201, 149, 61, 0.3) !important;
}

.login-btn:active:not(:disabled) {
  transform: translateY(0);
}

.login-btn :deep(.el-loading-spinner) {
  color: #ffffff !important;
}

.btn-text {
  position: relative;
  z-index: 1;
}

.btn-arrow {
  position: relative;
  z-index: 1;
  font-size: 18px;
  transition: transform 0.3s ease;
}

.login-btn:hover .btn-arrow {
  transform: translateX(4px);
}

/* ── 底部 ── */
.card-footer {
  margin-top: 20px;
  text-align: center;
}

.back-link {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: rgba(255, 255, 255, 0.3);
  font-size: 13px;
  transition: all 0.3s ease;
  text-decoration: none;
}

.back-link:hover {
  color: #c9953d;
}

.back-arrow {
  transition: transform 0.3s ease;
}

.back-link:hover .back-arrow {
  transform: translateX(-3px);
}

.card-divider {
  margin: 24px 0 0;
  height: 1px;
  background: linear-gradient(
    90deg,
    transparent,
    rgba(255, 255, 255, 0.06),
    transparent
  );
}

.card-version {
  margin: 12px 0 0;
  text-align: center;
  font-size: 11px;
  color: rgba(255, 255, 255, 0.15);
  letter-spacing: 0.5px;
}

/* ── 动画延迟入场 ── */
.card-header {
  animation: fadeUp 0.6s 0.15s both;
}
.login-form {
  animation: fadeUp 0.6s 0.3s both;
}
.card-footer {
  animation: fadeUp 0.6s 0.45s both;
}
.card-version {
  animation: fadeUp 0.6s 0.55s both;
}

@keyframes fadeUp {
  from {
    opacity: 0;
    transform: translateY(12px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* ── 响应式 ── */
@media (max-width: 480px) {
  .login-card {
    padding: 32px 24px 28px;
  }
  .card-title {
    font-size: 20px;
  }
}
</style>
