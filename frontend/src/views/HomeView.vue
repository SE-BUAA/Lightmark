<template>
  <div class="home">
    <!-- ── Hero ── -->
    <section class="hero">
      <div class="hero-bg">
        <div class="hero-pattern"></div>
        <div class="hero-glow"></div>
      </div>
      <div class="container hero-content">
        <div class="hero-text">
          <span class="hero-chip">探索世界 · 拾光而行</span>
          <h1 class="hero-title">
            让每一次出发<br />
            <span class="hero-highlight">都成为光影</span>
          </h1>
          <p class="hero-desc">
            机票 · 酒店 · 火车票 · 度假 · 智能行程 — 一站式旅行规划平台
          </p>
          <div class="hero-actions">
            <router-link to="/flights" class="btn btn-primary btn-hero">
              开始预订
              <span class="btn-arrow">→</span>
            </router-link>
            <router-link
              v-if="showGuestActions"
              to="/login"
              class="btn btn-secondary btn-hero"
            >
              登录 / 注册
            </router-link>
          </div>
        </div>
        <div class="hero-visual">
          <div class="hero-card-stack">
            <div class="stack-card c1">
              <span class="card-icon">✈</span>
              <span>北京 → 上海</span>
              <strong>&#165;680</strong>
            </div>
            <div class="stack-card c2">
              <span class="card-icon">🏨</span>
              <span>上海外滩酒店</span>
              <strong>&#165;850起</strong>
            </div>
            <div class="stack-card c3">
              <span class="card-icon">🚄</span>
              <span>G1 北京南→上海</span>
              <strong>&#165;553</strong>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- ── Quick Search ── -->
    <section class="quick-search">
      <div class="container">
        <div class="search-card">
          <div class="search-tabs">
            <button
              v-for="tab in searchTabs"
              :key="tab.key"
              :class="['search-tab', { active: activeTab === tab.key }]"
              @click="activeTab = tab.key"
            >
              <span class="tab-icon">{{ tab.icon }}</span>
              {{ tab.label }}
            </button>
          </div>
          <div class="search-form">
            <div class="search-field">
              <label>出发地</label>
              <input type="text" placeholder="城市或机场" />
            </div>
            <div class="search-field">
              <label>目的地</label>
              <input type="text" placeholder="城市或机场" />
            </div>
            <div class="search-field">
              <label>出发日期</label>
              <input type="date" />
            </div>
            <button class="btn btn-cta search-btn">搜索</button>
          </div>
        </div>
      </div>
    </section>

    <!-- ── Features ── -->
    <section class="features">
      <div class="container">
        <div class="section-header">
          <h2 class="section-title">一站式旅行服务</h2>
          <p class="section-subtitle">
            从预订到出行，从规划到回忆，我们陪伴你的每一段旅程
          </p>
        </div>
        <div class="feature-grid">
          <router-link
            v-for="f in features"
            :key="f.title"
            :to="f.path"
            class="feature-card"
          >
            <div class="feature-icon-wrap">
              <span class="feature-icon">{{ f.icon }}</span>
            </div>
            <h3>{{ f.title }}</h3>
            <p>{{ f.desc }}</p>
          </router-link>
        </div>
      </div>
    </section>

    <!-- ── Destinations ── -->
    <section class="destinations">
      <div class="container">
        <div class="section-header">
          <h2 class="section-title">热门目的地</h2>
          <p class="section-subtitle">发现下一个值得出发的地方</p>
        </div>
        <div class="dest-grid">
          <div
            v-for="(d, i) in destinations"
            :key="d.name"
            :class="['dest-card', `dest-${i + 1}`]"
          >
            <div class="dest-image-wrap">
              <img class="dest-image" :src="d.img" :alt="`${d.name}目的地图片`" />
              <div class="dest-overlay">
                <h3>{{ d.name }}</h3>
                <span class="dest-tag">{{ d.tag }}</span>
                <p class="dest-desc">{{ d.desc }}</p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- ── CTA ── -->
    <section class="cta-section">
      <div class="container cta-inner">
        <h2>准备好开启你的下一段旅程了吗？</h2>
        <p>注册即享会员专享价，积分抵扣更优惠</p>
        <router-link v-if="showGuestActions" to="/login" class="btn btn-primary btn-lg">
          立即注册
          <span class="btn-arrow">→</span>
        </router-link>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import { useAuthStore } from "@/stores/auth";

const activeTab = ref("flights");
const authStore = useAuthStore();
const showGuestActions = computed(() => !authStore.isLoggedIn);

const searchTabs = [
  { key: "flights", label: "机票", icon: "✈" },
  { key: "hotels", label: "酒店", icon: "🏨" },
  { key: "trains", label: "火车票", icon: "🚄" },
  { key: "vacations", label: "度假", icon: "🏖" },
];

const features = [
  {
    icon: "✈",
    title: "机票预订",
    desc: "国内国际航班，比价预订一站搞定",
    path: "/flights",
  },
  {
    icon: "🏨",
    title: "酒店住宿",
    desc: "海量酒店，真实住客评价参考",
    path: "/hotels",
  },
  {
    icon: "🚄",
    title: "火车出行",
    desc: "高铁动车普速，在线选座更便捷",
    path: "/trains",
  },
  {
    icon: "🏖",
    title: "旅游度假",
    desc: "跟团自由行当地玩乐，满足所有想象",
    path: "/vacations",
  },
  {
    icon: "🎫",
    title: "售后保障",
    desc: "订单查询、退改签说明与客服支持更透明",
    path: "/about",
  },
  {
    icon: "🗺",
    title: "智能行程",
    desc: "AI 规划行程，省心省力",
    path: "/itinerary",
  },
  {
    icon: "💬",
    title: "旅行社区",
    desc: "游记攻略，问答分享，一起探索世界",
    path: "/community",
  },
];

const destinations = [
  {
    name: "上海",
    tag: "摩登都市",
    desc: "外滩夜景、海派美食与商圈购物，一城解锁都市旅行与周边度假。",
    img: "https://6a2a86f788751837051fd603.imgix.net/city/physion-building-5525415_1920.jpg?auto=format&fit=fill&w=900&q=80",
  },
  {
    name: "北京",
    tag: "古都风华",
    desc: "故宫、长城与胡同文化串联历史纵深，适合亲子与文化深度游。",
    img: "https://6a2a86f788751837051fd603.imgix.net/city/jplenio-temple-of-heaven-3675835_1920.jpg?auto=format&fit=fill&w=900&q=80",
  },
  {
    name: "三亚",
    tag: "热带海滨",
    desc: "海岛度假、酒店套餐与亲子项目丰富，是放松型旅行热门首选。",
    img: "https://6a2a86f788751837051fd603.imgix.net/city/meisheng63-scenery-2320473_1920.jpg?auto=format&fit=fill&w=900&q=80",
  },
  {
    name: "成都",
    tag: "美食之都",
    desc: "慢节奏街巷、川味餐馆和周边熊猫基地，适合周末与长线休闲游。",
    img: "https://6a2a86f788751837051fd603.imgix.net/city/airtank-xue-tao-6910634_1920.jpg?auto=format&fit=fill&w=900&q=80",
  },
  {
    name: "杭州",
    tag: "江南水乡",
    desc: "西湖风景、茶文化与精品民宿结合，适合轻度假和情侣出行。",
    img: "https://6a2a86f788751837051fd603.imgix.net/city/xiaohhei-nature-7075743_1920.jpg?auto=format&fit=fill&w=900&q=80",
  },
  {
    name: "西安",
    tag: "千年古都",
    desc: "历史遗址与城市夜游兼具，火车与度假产品都适合串联多日线路。",
    img: "https://6a2a86f788751837051fd603.imgix.net/city/zhou-you-zhong-guo-daming-palace-8268910_1920.jpg?auto=format&fit=fill&w=900&q=80",
  },
];
</script>

<style scoped>
/* ── Hero ── */
.hero {
  position: relative;
  min-height: 90vh;
  display: flex;
  align-items: center;
  overflow: hidden;
  background: linear-gradient(
    135deg,
    var(--navy-900) 0%,
    var(--navy-700) 60%,
    var(--navy-600) 100%
  );
}

.hero-bg {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.hero-pattern {
  position: absolute;
  inset: 0;
  background-image: radial-gradient(
      circle at 25% 40%,
      rgba(201, 149, 61, 0.08) 0%,
      transparent 50%
    ),
    radial-gradient(
      circle at 75% 60%,
      rgba(212, 122, 98, 0.06) 0%,
      transparent 50%
    );
}

.hero-glow {
  position: absolute;
  top: -30%;
  right: -10%;
  width: 60%;
  height: 80%;
  background: radial-gradient(
    ellipse,
    rgba(201, 149, 61, 0.08) 0%,
    transparent 60%
  );
  animation: heroPulse 6s ease-in-out infinite alternate;
}

@keyframes heroPulse {
  0% {
    opacity: 0.5;
    transform: scale(1);
  }
  100% {
    opacity: 1;
    transform: scale(1.1);
  }
}

.hero-content {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 60px;
  align-items: center;
  padding: 80px 24px;
  position: relative;
  z-index: 1;
}

.hero-chip {
  display: inline-block;
  padding: 6px 18px;
  border-radius: 100px;
  font-size: 13px;
  font-weight: 500;
  color: var(--gold-400);
  background: rgba(201, 149, 61, 0.12);
  border: 1px solid rgba(201, 149, 61, 0.2);
  margin-bottom: 24px;
  letter-spacing: 1px;
}

.hero-title {
  font-family: var(--font-heading);
  font-size: clamp(40px, 5.5vw, 68px);
  font-weight: 700;
  color: var(--white);
  line-height: 1.1;
  letter-spacing: -0.5px;
}

.hero-highlight {
  background: linear-gradient(to right, var(--gold-400), var(--gold-500));
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.hero-desc {
  font-size: clamp(16px, 1.2vw, 18px);
  color: rgba(255, 255, 255, 0.6);
  margin-top: 20px;
  line-height: 1.7;
  max-width: 460px;
}

.hero-actions {
  display: flex;
  gap: 14px;
  margin-top: 32px;
  flex-wrap: wrap;
}

.btn-hero {
  padding: 14px 32px;
  font-size: 15px;
}

.btn-hero.btn-secondary {
  color: rgba(255, 255, 255, 0.8);
  border-color: rgba(255, 255, 255, 0.15);
}

.btn-hero.btn-secondary:hover {
  border-color: var(--gold-500);
  color: var(--gold-400);
}

.btn-arrow {
  transition: transform var(--duration-fast) var(--ease-out);
}

.btn:hover .btn-arrow {
  transform: translateX(4px);
}

/* Hero visual cards */
.hero-visual {
  position: relative;
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 400px;
}

.hero-card-stack {
  position: relative;
  width: 300px;
  height: 360px;
}

.stack-card {
  position: absolute;
  width: 260px;
  padding: 20px;
  background: rgba(255, 255, 255, 0.06);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: var(--radius-lg);
  color: var(--white);
  display: flex;
  flex-direction: column;
  gap: 8px;
  transition: transform var(--duration-slow) var(--ease-out);
}

.stack-card .card-icon {
  font-size: 28px;
}
.stack-card span {
  font-size: 14px;
  color: rgba(255, 255, 255, 0.6);
}
.stack-card strong {
  font-size: 20px;
  color: var(--gold-400);
  font-weight: 700;
}

.c1 {
  top: 20px;
  right: 20px;
  transform: rotate(-4deg);
}
.c2 {
  top: 100px;
  left: 0;
  transform: rotate(3deg);
}
.c3 {
  top: 190px;
  right: 30px;
  transform: rotate(-2deg);
}

.stack-card:hover {
  transform: translateY(-8px) rotate(0deg);
  background: rgba(255, 255, 255, 0.1);
}

/* ── Quick Search ── */
.quick-search {
  margin-top: -50px;
  position: relative;
  z-index: 2;
}

.search-card {
  background: var(--white);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-lg);
  overflow: hidden;
}

.search-tabs {
  display: flex;
  border-bottom: 1px solid var(--slate-200);
  padding: 4px;
  gap: 4px;
}

.search-tab {
  flex: 1;
  padding: 16px;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-secondary);
  border-radius: var(--radius-sm);
  transition: all var(--duration-fast) var(--ease-out);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
}

.search-tab.active {
  background: var(--navy-900);
  color: var(--white);
}

.search-tab:not(.active):hover {
  background: var(--cream-100);
}

.tab-icon {
  font-size: 16px;
}

.search-form {
  display: flex;
  gap: 14px;
  padding: 24px;
  align-items: flex-end;
  flex-wrap: wrap;
}

.search-field {
  flex: 1;
  min-width: 160px;
}

.search-field label {
  display: block;
  font-size: 12px;
  font-weight: 600;
  color: var(--text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-bottom: 6px;
}

.search-field input {
  width: 100%;
  padding: 10px 14px;
  border: 1.5px solid var(--slate-200);
  border-radius: var(--radius-sm);
  font-size: 14px;
  font-family: var(--font-body);
  transition: border-color var(--duration-fast) var(--ease-out);
  background: var(--cream-50);
  outline: none;
}

.search-field input:focus {
  border-color: var(--accent);
  box-shadow: 0 0 0 3px rgba(201, 149, 61, 0.1);
}

.search-btn {
  padding: 10px 32px;
  min-width: 100px;
}

/* ── Features ── */
.features {
  padding: 100px 0 60px;
}

.section-header {
  text-align: center;
  margin-bottom: 48px;
}

.section-header .section-subtitle {
  margin: 10px auto 0;
}

.feature-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 24px;
}

.feature-card {
  background: var(--white);
  border-radius: var(--radius-md);
  padding: 32px 24px;
  border: 1px solid var(--slate-100);
  transition: all var(--duration-normal) var(--ease-out);
  cursor: pointer;
}

.feature-card:hover {
  transform: translateY(-6px);
  box-shadow: var(--shadow-md);
  border-color: var(--gold-200);
}

.feature-icon-wrap {
  width: 56px;
  height: 56px;
  border-radius: var(--radius-sm);
  background: linear-gradient(135deg, var(--gold-200), var(--cream-200));
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 18px;
}

.feature-icon {
  font-size: 24px;
}

.feature-card h3 {
  font-size: 18px;
  font-weight: 700;
  color: var(--navy-900);
  margin-bottom: 8px;
}

.feature-card p {
  font-size: 14px;
  color: var(--text-secondary);
  line-height: 1.6;
}

/* ── Destinations ── */
.destinations {
  padding: 60px 0 80px;
  background: var(--cream-100);
}

.dest-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
}

.dest-card {
  border-radius: var(--radius-md);
  overflow: hidden;
  position: relative;
  aspect-ratio: 4 / 3;
  cursor: pointer;
}

.dest-image-wrap {
  width: 100%;
  height: 100%;
}

.dest-1 {
  grid-row: span 2;
  aspect-ratio: auto;
}

.dest-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
  transition: transform var(--duration-slow) var(--ease-out);
}

.dest-card:hover .dest-image {
  transform: scale(1.05);
}

.dest-overlay {
  position: absolute;
  inset: 0;
  background: linear-gradient(
    180deg,
    rgba(10, 22, 40, 0.08) 0%,
    rgba(10, 22, 40, 0.58) 58%,
    rgba(10, 22, 40, 0.88) 100%
  );
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  gap: 10px;
  padding: 24px;
}

.dest-overlay h3 {
  color: var(--white);
  font-size: 22px;
  font-family: var(--font-heading);
  font-weight: 600;
  margin: 0;
}

.dest-tag {
  display: inline-block;
  align-self: flex-start;
  font-size: 12px;
  color: var(--white);
  background: rgba(255, 255, 255, 0.16);
  padding: 5px 12px;
  border-radius: 999px;
  letter-spacing: 0.5px;
}

.dest-desc {
  margin: 0;
  color: rgba(255, 255, 255, 0.82);
  line-height: 1.7;
  font-size: 14px;
}

/* ── CTA ── */
.cta-section {
  padding: 80px 0;
  background: var(--navy-900);
  position: relative;
  overflow: hidden;
}

.cta-section::before {
  content: "";
  position: absolute;
  inset: 0;
  background: radial-gradient(
    ellipse at 50% 0%,
    rgba(201, 149, 61, 0.08) 0%,
    transparent 60%
  );
}

.cta-inner {
  text-align: center;
  position: relative;
  z-index: 1;
}

.cta-inner h2 {
  font-family: var(--font-heading);
  font-size: clamp(30px, 4vw, 44px);
  color: var(--white);
  font-weight: 600;
}

.cta-inner p {
  color: rgba(255, 255, 255, 0.5);
  margin-top: 12px;
  font-size: 16px;
}

.cta-inner .btn {
  margin-top: 28px;
  font-size: 16px;
  padding: 14px 36px;
}

.btn-lg .btn-arrow {
  display: inline-block;
  margin-left: 4px;
}

/* ── Responsive ── */
@media (max-width: 1024px) {
  .hero-content {
    grid-template-columns: 1fr;
    text-align: center;
    gap: 40px;
  }

  .hero-desc {
    margin: 20px auto 0;
  }

  .hero-actions {
    justify-content: center;
  }

  .hero-visual {
    display: none;
  }

  .feature-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .dest-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .dest-1 {
    grid-row: auto;
    aspect-ratio: 4/3;
  }
}

@media (max-width: 640px) {
  .feature-grid {
    grid-template-columns: 1fr;
  }

  .dest-grid {
    grid-template-columns: 1fr;
  }

  .search-form {
    flex-direction: column;
  }

  .search-field {
    min-width: 100%;
  }
}
</style>
