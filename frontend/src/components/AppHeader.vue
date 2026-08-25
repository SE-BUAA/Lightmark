<template>
  <header class="site-header" :class="{ scrolled: isScrolled }">
    <div class="container header-inner">
      <router-link to="/" class="logo">
        <span class="logo-icon">✦</span>
        <span class="logo-text">拾光旅行</span>
      </router-link>

      <nav class="nav-links" :class="{ open: menuOpen }">
        <router-link
          v-for="item in navItems"
          :key="item.path"
          :to="item.path"
          class="nav-link"
          @click="menuOpen = false"
        >
          {{ item.label }}
        </router-link>
        <div class="nav-actions-mobile">
          <template v-if="authStore.isLoggedIn">
            <router-link
              to="/user-center"
              class="user-mobile-link"
              @click="menuOpen = false"
            >
              <span class="user-avatar-sm">{{
                authStore.nickname.charAt(0) || "?"
              }}</span>
              <span>{{ authStore.nickname || "用户" }}</span>
            </router-link>
            <button class="btn btn-secondary btn-sm" @click="handleLogout">
              退出登录
            </button>
          </template>
          <template v-else>
            <router-link
              to="/login"
              class="btn btn-primary btn-sm"
              @click="menuOpen = false"
            >
              登录
            </router-link>
          </template>
        </div>
      </nav>

      <div class="nav-actions">
        <ThemeToggle />
        <template v-if="authStore.isLoggedIn">
          <router-link to="/user-center" class="user-menu">
            <span class="user-avatar">{{
              authStore.nickname.charAt(0) || "?"
            }}</span>
            <span class="user-name">{{ authStore.nickname || "用户" }}</span>
          </router-link>
          <button class="btn-icon" @click="handleLogout" title="退出登录">
            <svg
              width="18"
              height="18"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
              stroke-linecap="round"
              stroke-linejoin="round"
            >
              <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
              <polyline points="16 17 21 12 16 7" />
              <line x1="21" y1="12" x2="9" y2="12" />
            </svg>
          </button>
        </template>
        <template v-else>
          <router-link to="/login" class="btn btn-primary btn-sm"
            >登录</router-link
          >
        </template>
      </div>

      <button
        class="menu-toggle"
        @click="menuOpen = !menuOpen"
        aria-label="切换菜单"
      >
        <span class="hamburger" :class="{ active: menuOpen }">
          <span></span><span></span><span></span>
        </span>
      </button>
    </div>
  </header>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from "vue";
import { useRouter } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import { useAuthStore } from "@/stores/auth";
import { logoutApi } from "@/api/auth";
import ThemeToggle from "@/components/ThemeToggle.vue";

const router = useRouter();
const authStore = useAuthStore();

const isScrolled = ref(false);
const menuOpen = ref(false);

const navItems = [
  { path: "/", label: "首页" },
  { path: "/flights", label: "机票" },
  { path: "/hotels", label: "酒店" },
  { path: "/trains", label: "火车票" },
  { path: "/vacations", label: "度假" },
  { path: "/itinerary", label: "行程" },
  { path: "/community", label: "社区" },
];

const handleLogout = async () => {
  try {
    await ElMessageBox.confirm("确定要退出登录吗？", "提示", {
      confirmButtonText: "确定",
      cancelButtonText: "取消",
      type: "info",
    });
    await logoutApi();
    authStore.clearSession();
    ElMessage.success("已退出登录");
    router.push("/");
  } catch {
    // 用户取消或 API 失败，不退出
  }
};

let scrollHandler: (() => void) | null = null;

onMounted(() => {
  scrollHandler = () => {
    isScrolled.value = window.scrollY > 60;
  };
  window.addEventListener("scroll", scrollHandler);
});

onUnmounted(() => {
  if (scrollHandler) {
    window.removeEventListener("scroll", scrollHandler);
  }
});
</script>

<style scoped>
.site-header {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 1000;
  padding: 14px 0;
  transition: all var(--duration-normal) var(--ease-out);
  background: transparent;
}

.site-header.scrolled {
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  padding: 8px 0;
  box-shadow: 0 1px 3px rgba(10, 22, 40, 0.06);
}

.header-inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.logo {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 700;
  font-size: 20px;
  color: var(--navy-900);
  z-index: 1001;
}

.logo-icon {
  font-size: 22px;
  color: var(--accent);
}

.logo-text {
  font-family: var(--font-heading);
  letter-spacing: 1px;
}

.nav-links {
  display: flex;
  align-items: center;
  gap: 4px;
}

.nav-link {
  padding: 8px 16px;
  border-radius: var(--radius-sm);
  font-size: 14px;
  font-weight: 500;
  color: var(--slate-700);
  transition: all var(--duration-fast) var(--ease-out);
  position: relative;
}

.nav-link:hover {
  color: var(--accent);
  background: rgba(201, 149, 61, 0.06);
}

.nav-link.router-link-active {
  color: var(--accent);
}

.nav-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.user-menu {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  border-radius: 100px;
  transition: all var(--duration-fast) var(--ease-out);
  cursor: pointer;
}

.user-menu:hover {
  background: rgba(201, 149, 61, 0.08);
}

.user-avatar {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--gold-500), var(--accent-hover));
  color: var(--white);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 700;
  flex-shrink: 0;
}

.user-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--navy-900);
  max-width: 100px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.btn-icon {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--slate-500);
  transition: all var(--duration-fast) var(--ease-out);
  background: transparent;
}

.btn-icon:hover {
  background: var(--cream-100);
  color: var(--cta);
}

.btn-sm {
  padding: 8px 18px;
  font-size: 13px;
}

.menu-toggle {
  display: none;
  z-index: 1001;
  padding: 8px;
}

.hamburger {
  display: flex;
  flex-direction: column;
  gap: 5px;
  width: 22px;
}

.hamburger span {
  display: block;
  height: 2px;
  background: var(--navy-900);
  border-radius: 2px;
  transition: all var(--duration-fast) var(--ease-out);
  transform-origin: center;
}

.hamburger.active span:nth-child(1) {
  transform: translateY(7px) rotate(45deg);
}

.hamburger.active span:nth-child(2) {
  opacity: 0;
}

.hamburger.active span:nth-child(3) {
  transform: translateY(-7px) rotate(-45deg);
}

.nav-actions-mobile {
  display: none;
}

.user-mobile-link {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  color: var(--navy-900);
  font-weight: 600;
  font-size: 15px;
  border-radius: var(--radius-sm);
  transition: background var(--duration-fast) var(--ease-out);
}

.user-mobile-link:hover {
  background: var(--cream-100);
}

.user-avatar-sm {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--gold-500), var(--accent-hover));
  color: var(--white);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 700;
  flex-shrink: 0;
}

@media (max-width: 900px) {
  .menu-toggle {
    display: block;
  }
  .nav-actions {
    display: none;
  }

  .nav-links {
    position: fixed;
    top: 0;
    right: 0;
    width: 280px;
    height: 100vh;
    background: var(--white);
    flex-direction: column;
    align-items: stretch;
    padding: 80px 24px 24px;
    gap: 2px;
    box-shadow: -10px 0 40px rgba(10, 22, 40, 0.1);
    transform: translateX(100%);
    transition: transform var(--duration-normal) var(--ease-out);
  }

  .nav-links.open {
    transform: translateX(0);
  }

  .nav-link {
    padding: 14px 16px;
    font-size: 16px;
    border-radius: var(--radius-sm);
  }

  .nav-link:hover {
    background: var(--cream-100);
  }

  .nav-actions-mobile {
    display: flex;
    margin-top: 20px;
    padding-top: 20px;
    border-top: 1px solid var(--slate-200);
  }

  .nav-actions-mobile .btn {
    width: 100%;
    justify-content: center;
  }
}
</style>
