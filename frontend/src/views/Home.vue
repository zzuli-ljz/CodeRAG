<template>
  <div class="home">
    <!-- Hero 区域 -->
    <section class="hero">
      <div class="hero-bg">
        <div class="grid-pattern"></div>
        <div class="glow orb-1"></div>
        <div class="glow orb-2"></div>
      </div>
      <div class="hero-inner">
        <h1 class="hero-title">
          <span class="title-accent">Code</span>RAG
        </h1>
        <p class="hero-subtitle">多平台代码智能学习平台</p>
        <p class="hero-desc">
          导入 GitHub / Gitee 公开仓库，通过 RAG 向量检索 + 大模型，
          实现代码读懂、解析、答疑、架构分析、版本对比、刷题学习。
        </p>
        <div class="hero-actions">
          <router-link to="/import" class="btn btn-primary">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="M5 12h14M12 5l7 7-7 7"/></svg>
            开始使用
          </router-link>
        </div>
      </div>
    </section>

    <!-- 核心功能 -->
    <section class="features">
      <div class="page-container">
        <h2 class="section-title">核心功能</h2>
        <div class="feature-grid">
          <div class="feature-card" v-for="(f, i) in features" :key="f.title"
               :style="{ '--delay': i * 80 + 'ms' }">
            <div class="icon-wrap" :class="'color-' + i">
              <component :is="'span'" v-html="f.svg" />
            </div>
            <h3 class="feature-title">{{ f.title }}</h3>
            <p class="feature-desc">{{ f.desc }}</p>
          </div>
        </div>
      </div>
    </section>

    <!-- 数据展示条 -->
    <section class="stats-bar">
      <div class="page-container">
        <div class="stats-row">
          <div class="stat-item"><span class="stat-num">RAG</span><span>向量检索引擎</span></div>
          <div class="stat-divider"></div>
          <div class="stat-item"><span class="stat-num">AI</span><span>通义大模型驱动</span></div>
          <div class="stat-divider"></div>
          <div class="stat-item"><span class="stat-num">SPA</span><span>前后端一体化部署</span></div>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
const features = [
  {
    svg: '<svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>',
    title: '双平台仓库导入',
    desc: '自动识别 GitHub/Gitee 链接，适配器模式兼容双平台，异步解析入库'
  },
  {
    svg: '<svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15a2 2 0 01-2 2H7l-4 4V5a2 2 0 012-2h14a2 2 0 012 2z"/><path d="M8 10h.01M12 10h.01M16 10h.01"/></svg>',
    title: 'RAG 智能代码问答',
    desc: '基于整仓代码上下文精准问答，附带代码片段溯源'
  },
  {
    svg: '<svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="3" width="18" height="18" rx="2" ry="2"/><line x1="3" y1="9" x2="21" y2="9"/><line x1="9" y1="21" x2="9" y2="9"/></svg>',
    title: '自动架构解析',
    desc: 'AI 自动生成项目架构说明，梳理模块关系与调用链'
  },
  {
    svg: '<svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><circle cx="18" cy="18" r="3"/><circle cx="6" cy="6" r="3"/><path d="M15.5 15.5L7.5 7.5"/><line x1="6" y1="18" x2="18" y2="18" opacity=".4"/><line x1="18" y1="6" x2="18" y2="18" opacity=".4"/></svg>',
    title: '代码版本对比',
    desc: '两分支/Commit 差异解析，AI 解读改动逻辑与风险'
  },
  {
    svg: '<svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/><polyline points="10 9 9 9 8 9"/></svg>',
    title: '智能刷题学习',
    desc: '自动识别代码难点，生成练习题，AI 批改讲解'
  },
  {
    svg: '<svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M12 2L2 7l10 5 10-5-10-5z"/><path d="M2 17l10 5 10-5"/><path d="M2 12l10 5 10-5"/></svg>',
    title: '多语言代码翻译',
    desc: '智能翻译代码到目标语言，适配项目风格'
  },
  {
    svg: '<svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="5" r="2"/><circle cx="5" cy="12" r="2"/><circle cx="19" cy="12" r="2"/><circle cx="7" cy="19" r="2"/><circle cx="17" cy="19" r="2"/><line x1="11" y1="6.7" x2="6" y2="10.7"/><line x1="13" y1="6.7" x2="18" y2="10.7"/><line x1="6" y1="13.3" x2="8" y2="17.7"/><line x1="18" y1="13.3" x2="16" y2="17.7"/></svg>',
    title: '调用链可视化',
    desc: '代码函数/类调用关系图谱，直观理解项目结构'
  },

]
</script>

<style scoped>
/* ========== Hero ========== */
.hero {
  position: relative;
  overflow: hidden;
  padding: 100px 0 90px;
}
.hero-bg {
  position: absolute;
  inset: 0;
  z-index: 0;
}
.grid-pattern {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(79,70,229,.04) 1px, transparent 1px),
    linear-gradient(90deg, rgba(79,70,229,.04) 1px, transparent 1px);
  background-size: 40px 40px;
  mask-image: radial-gradient(ellipse at center, black 30%, transparent 70%);
  -webkit-mask-image: radial-gradient(ellipse at center, black 30%, transparent 70%);
}
.glow {
  position: absolute;
  border-radius: 50%;
  filter: blur(100px);
  animation: float 8s ease-in-out infinite;
}
.orb-1 {
  width: 400px; height: 400px;
  background: rgba(99,102,241,.15);
  top: -120px; right: 5%;
}
.orb-2 {
  width: 300px; height: 300px;
  background: rgba(16,185,129,.12);
  bottom: -80px; left: 10%;
  animation-delay: -4s;
}
@keyframes float {
  0%, 100% { transform: translate(0, 0); }
  50% { transform: translate(-20px, 20px); }
}

.hero-inner {
  position: relative;
  z-index: 1;
  max-width: 700px;
  margin: 0 auto;
  text-align: center;
  padding: 0 24px;
}
.hero-title {
  font-size: 52px;
  font-weight: 800;
  letter-spacing: -2px;
  line-height: 1.1;
  margin-bottom: 12px;
  color: var(--color-text-primary);
}
.title-accent {
  background: linear-gradient(135deg, #4f46e5, #06b6d4);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}
.hero-subtitle {
  font-size: 18px;
  color: var(--color-text-secondary);
  margin-bottom: 16px;
  font-weight: 500;
  letter-spacing: 2px;
}
.hero-desc {
  font-size: 15px;
  color: var(--color-text-tertiary);
  line-height: 1.8;
  margin-bottom: 36px;
  max-width: 520px;
  margin-left: auto;
  margin-right: auto;
}
.hero-actions {
  display: flex;
  gap: 14px;
  justify-content: center;
  flex-wrap: wrap;
}

/* ========== 按钮 ========== */
.btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 12px 28px;
  border-radius: 10px;
  font-size: 15px;
  font-weight: 600;
  text-decoration: none;
  transition: all .25s cubic-bezier(.4,0,.2,1);
  cursor: pointer;
  position: relative;
  overflow: hidden;
}
.btn-primary {
  background: linear-gradient(135deg, #4f46e5, #6366f1);
  color: #fff;
  box-shadow: 0 4px 15px rgba(79,70,229,.35), 0 1px 3px rgba(0,0,0,.08);
}
.btn-primary:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 25px rgba(79,70,229,.45), 0 2px 6px rgba(0,0,0,.1);
}
.btn-primary:active { transform: translateY(0); }
.btn-secondary {
  background: var(--color-bg-secondary);
  color: var(--color-text-primary);
  border: 1px solid var(--color-border);
}
.btn-secondary:hover {
  border-color: #4f46e5;
  color: #4f46e5;
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0,0,0,.06);
}

/* ========== 功能卡片 ========== */
.features {
  padding: 80px 0;
}
.section-title {
  font-size: 26px;
  font-weight: 700;
  text-align: center;
  margin-bottom: 48px;
  position: relative;
}
.section-title::after {
  content: '';
  display: block;
  width: 40px; height: 3px;
  background: linear-gradient(90deg, #4f46e5, #06b6d4);
  margin: 14px auto 0;
  border-radius: 2px;
}

.feature-grid {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 24px;
}
.feature-grid .feature-card {
  flex: 0 0 calc((100% - 48px) / 3);
  min-width: 280px;
}
.feature-card {
  background: var(--color-bg);
  border: 1px solid var(--color-border);
  border-radius: 16px;
  padding: 32px 24px;
  text-align: center;
  transition: all .35s cubic-bezier(.4,0,.2,1);
  cursor: default;
  position: relative;
  overflow: hidden;
  animation: cardIn .5s ease-out both;
  animation-delay: var(--delay);
}
@keyframes cardIn {
  from { opacity: 0; transform: translateY(24px); }
  to   { opacity: 1; transform: translateY(0); }
}
.feature-card::before {
  content: '';
  position: absolute;
  top: 0; left: 0; right: 0;
  height: 3px;
  background: linear-gradient(90deg, transparent, var(--card-accent, #4f46e5), transparent);
  opacity: 0;
  transition: opacity .3s;
}
.feature-card:hover::before { opacity: 1; }

.feature-card:hover {
  transform: translateY(-6px);
  box-shadow: 0 20px 40px rgba(0,0,0,.08), 0 0 0 1px var(--card-accent, #4f46e5);
  border-color: transparent;
}

.icon-wrap {
  width: 64px; height: 64px;
  border-radius: 16px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 18px;
  transition: transform .35s cubic-bezier(.34,1.56,.64,1);
  position: relative;
}
.icon-wrap > span {
  display: flex;
  align-items: center;
  justify-content: center;
}
.color-0 { --card-accent: #4f46e5; background: rgba(79,70,229,.08); color: #4f46e5; }
.color-1 { --card-accent: #0891b2; background: rgba(8,145,178,.08); color: #0891b2; }
.color-2 { --card-accent: #7c3aed; background: rgba(124,58,237,.08); color: #7c3aed; }
.color-3 { --card-accent: #db2777; background: rgba(219,39,119,.08); color: #db2777; }
.color-4 { --card-accent: #059669; background: rgba(5,150,105,.08); color: #059669; }
.color-5 { --card-accent: #d97706; background: rgba(217,119,6,.08); color: #d97706; }

.feature-card:hover .icon-wrap {
  transform: scale(1.12) rotate(-3deg);
}
.feature-title {
  font-size: 16px;
  font-weight: 650;
  margin-bottom: 8px;
  color: var(--color-text-primary);
}
.feature-desc {
  font-size: 13px;
  color: var(--color-text-secondary);
  line-height: 1.65;
}

/* ========== 统计条 ========== */
.stats-bar {
  padding: 32px 0 72px;
}
.stats-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 32px;
  flex-wrap: wrap;
}
.stat-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--color-text-tertiary);
}
.stat-num {
  font-weight: 750;
  font-size: 15px;
  background: linear-gradient(135deg, #4f46e5, #06b6d4);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  letter-spacing: .5px;
}
.stat-divider {
  width: 1px; height: 20px;
  background: var(--color-border);
}

/* ========== 响应式 ========== */
@media (max-width: 768px) {
  .hero-title { font-size: 36px; }
  .feature-grid { grid-template-columns: repeat(2, 1fr); gap: 16px; }
  .feature-card { padding: 24px 16px; }
  .icon-wrap { width: 52px; height: 52px; }
  .icon-wrap svg { width: 24px; height: 24px; }
}
@media (max-width: 480px) {
  .feature-grid { grid-template-columns: 1fr; max-width: 320px; margin: 0 auto; }
  .stats-row { gap: 16px; }
}
</style>
