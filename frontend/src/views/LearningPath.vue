<template>
  <div class="page-container">
    <div class="page-header">
      <h1 class="page-title">代码学习路径</h1>
      <div class="header-actions">
        <button class="btn btn-primary" @click="handleGenerate" :disabled="generating || !repoId">
          {{ generating ? 'AI 分析中...' : '生成学习路径' }}
        </button>
      </div>
    </div>

    <!-- 仓库选择 -->
    <div class="card" style="margin-bottom:16px" v-if="!repoId">
      <h3 style="margin-bottom:12px">选择仓库</h3>
      <div class="repo-grid">
        <div
          v-for="r in repos"
          :key="r.id"
          class="repo-card"
          @click="selectRepo(r)"
        >
          <div class="repo-name">{{ r.repoName }}</div>
          <div class="repo-meta">{{ r.platform }} · {{ r.language || '未知' }}</div>
        </div>
      </div>
      <div v-if="!repos.length" class="empty-state">暂无仓库，请先导入</div>
    </div>

    <!-- 主内容区：左侧历史 + 右侧内容 -->
    <template v-if="repoId">
      <div class="repo-bar card" style="margin-bottom:16px">
        <span class="repo-label">{{ selectedRepo?.repoName }}</span>
        <button class="btn btn-sm btn-secondary" @click="repoId = 0; selectedRepo = null; pathContent = ''; history = []">切换仓库</button>
      </div>

      <div v-if="loading" class="loading"><div class="spinner"></div> 加载中...</div>

      <div v-else class="path-layout">
        <!-- 左侧：历史记录 -->
        <div class="path-sidebar">
          <div class="sidebar-header">📋 历史学习路径</div>
          <div class="sidebar-list" v-if="history.length">
            <div
              v-for="h in history"
              :key="h.id"
              class="sidebar-item"
              :class="{ active: h.id === currentPathId }"
              @click="loadPath(h)"
            >
              <div class="sidebar-round">第 {{ h.round }} 轮</div>
              <div class="sidebar-time">{{ formatTime(h.createdAt) }}</div>
            </div>
          </div>
          <div v-else class="sidebar-empty">暂无历史记录</div>
        </div>

        <!-- 右侧：学习路径内容（可折叠） -->
        <div class="path-main">
          <div v-if="pathContent" class="card path-card">
            <div class="path-header">
              <span class="tag tag-accent">第 {{ currentRound }} 轮学习路径</span>
              <span class="path-time">{{ formatTime(pathTime) }}</span>
            </div>
            <div class="path-body">
              <div v-for="(section, si) in parsedSections" :key="si" class="path-section">
                <div
                  class="section-header"
                  :class="{ collapsed: !section.expanded }"
                  @click="toggleSection(si)"
                >
                  <span class="section-arrow">{{ section.expanded ? '▾' : '▸' }}</span>
                  <span class="section-title">{{ section.title }}</span>
                </div>
                <div v-show="section.expanded" class="section-content" v-html="renderMarkdown(section.content)"></div>
              </div>
            </div>
          </div>
          <div v-else class="empty-state">点击「生成学习路径」，AI 将分析仓库代码结构并生成渐进式学习路线</div>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute } from 'vue-router'
import { learningPathApi, repoApi } from '@/api'
import MarkdownIt from 'markdown-it'

const md = new MarkdownIt({ html: false, breaks: true })
const route = useRoute()

const repoId = ref(Number(route.params.repoId) || 0)
const selectedRepo = ref<any>(null)
const repos = ref<any[]>([])
const generating = ref(false)
const loading = ref(false)
const pathContent = ref('')
const pathTime = ref('')
const currentRound = ref(1)
const currentPathId = ref<number | null>(null)
const history = ref<any[]>([])

// 可折叠章节
interface Section {
  title: string
  content: string
  expanded: boolean
}
const parsedSections = ref<Section[]>([])

function parseSections(markdown: string): Section[] {
  const sections: Section[] = []
  const lines = markdown.split('\n')
  let currentTitle = '概述'
  let currentContent: string[] = []
  let firstContent = true

  for (const line of lines) {
    // 匹配 ## 标题（二级标题作为章节分隔）
    if (/^##\s+/.test(line)) {
      if (firstContent && currentContent.length === 0 && sections.length === 0) {
        // 第一个 ## 之前的内容作为概述
        currentTitle = line.replace(/^##\s+/, '')
        firstContent = false
        continue
      }
      // 保存上一个章节
      if (currentContent.length > 0 || sections.length > 0) {
        sections.push({
          title: currentTitle,
          content: currentContent.join('\n').trim(),
          expanded: sections.length === 0 // 第一个章节默认展开
        })
      }
      currentTitle = line.replace(/^##\s+/, '')
      currentContent = []
      firstContent = false
    } else {
      currentContent.push(line)
    }
  }
  // 最后一个章节
  if (currentContent.length > 0 || currentTitle) {
    sections.push({
      title: currentTitle,
      content: currentContent.join('\n').trim(),
      expanded: sections.length === 0
    })
  }

  // 如果没有找到 ## 标题，整个内容作为一个章节
  if (sections.length === 0 && markdown.trim()) {
    sections.push({
      title: '学习路径',
      content: markdown.trim(),
      expanded: true
    })
  }

  return sections
}

function toggleSection(idx: number) {
  parsedSections.value[idx].expanded = !parsedSections.value[idx].expanded
}

function renderMarkdown(text: string) {
  return md.render(text || '')
}

function formatTime(t: string) {
  return new Date(t).toLocaleString('zh-CN')
}

function selectRepo(r: any) {
  repoId.value = r.id
  selectedRepo.value = r
  loadLatest()
}

async function loadRepos() {
  try {
    const res = await repoApi.listRepos(0, 50)
    if (res.data.code === 200) {
      repos.value = (Array.isArray(res.data.data) ? res.data.data : res.data.data?.content) || []
    }
  } catch { /* ignore */ }
}

async function loadLatest() {
  if (!repoId.value) return
  loading.value = true
  try {
    const [latestRes, histRes] = await Promise.all([
      learningPathApi.getLatest(repoId.value),
      learningPathApi.getHistory(repoId.value)
    ])
    if (latestRes.data.code === 200 && latestRes.data.data) {
      const d = latestRes.data.data
      pathContent.value = d.pathContent
      pathTime.value = d.createdAt
      currentRound.value = d.round
      currentPathId.value = d.id
      parsedSections.value = parseSections(d.pathContent || '')
    }
    if (histRes.data.code === 200) {
      history.value = histRes.data.data || []
    }
  } catch { /* ignore */ }
  finally { loading.value = false }
}

function loadPath(h: any) {
  pathContent.value = h.pathContent
  pathTime.value = h.createdAt
  currentRound.value = h.round
  currentPathId.value = h.id
  parsedSections.value = parseSections(h.pathContent || '')
}

async function handleGenerate() {
  if (!repoId.value) return
  generating.value = true
  try {
    const res = await learningPathApi.generate(repoId.value, selectedRepo.value?.repoName || '')
    if (res.data.code === 200) {
      await loadLatest()
    }
  } catch (e: any) {
    alert('生成失败: ' + (e.response?.data?.message || e.message))
  } finally {
    generating.value = false
  }
}

onMounted(async () => {
  await loadRepos()
  if (repoId.value) {
    const r = repos.value.find(r => r.id === repoId.value)
    if (r) selectedRepo.value = r
    await loadLatest()
  }
})
</script>

<style scoped>
.repo-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 10px;
}
.repo-card {
  padding: 14px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: all 0.2s;
}
.repo-card:hover {
  border-color: var(--color-accent);
  background: var(--color-accent-light);
}
.repo-name {
  font-size: 14px;
  font-weight: 600;
}
.repo-meta {
  font-size: 12px;
  color: var(--color-text-tertiary);
  margin-top: 4px;
}
.repo-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 20px;
}
.repo-label {
  font-weight: 600;
  font-size: 15px;
}

/* 左右布局 */
.path-layout {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}

/* 左侧历史列表 */
.path-sidebar {
  width: 220px;
  flex-shrink: 0;
  background: var(--color-bg);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  overflow: hidden;
  position: sticky;
  top: 80px;
}
.sidebar-header {
  padding: 14px 16px;
  font-size: 14px;
  font-weight: 600;
  border-bottom: 1px solid var(--color-border-light);
  background: var(--color-bg-secondary);
}
.sidebar-list {
  max-height: 60vh;
  overflow-y: auto;
}
.sidebar-item {
  padding: 12px 16px;
  cursor: pointer;
  border-bottom: 1px solid var(--color-border-light);
  transition: background 0.15s;
}
.sidebar-item:hover {
  background: var(--color-bg-secondary);
}
.sidebar-item.active {
  background: var(--color-accent-light);
  border-left: 3px solid var(--color-accent);
}
.sidebar-round {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-accent);
}
.sidebar-time {
  font-size: 11px;
  color: var(--color-text-tertiary);
  margin-top: 2px;
}
.sidebar-empty {
  padding: 24px 16px;
  text-align: center;
  font-size: 13px;
  color: var(--color-text-tertiary);
}

/* 右侧内容 */
.path-main {
  flex: 1;
  min-width: 0;
}
.path-card {
  padding: 20px 24px;
}
.path-header {
  display: flex;
  align-items: center;
  gap: var(--space-md);
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--color-border-light);
}
.path-time {
  font-size: 12px;
  color: var(--color-text-tertiary);
}

/* 可折叠章节 */
.path-section {
  margin-bottom: 4px;
}
.section-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  cursor: pointer;
  border-radius: var(--radius-md);
  transition: background 0.15s;
  user-select: none;
}
.section-header:hover {
  background: var(--color-bg-secondary);
}
.section-header.collapsed {
  opacity: 0.7;
}
.section-arrow {
  font-size: 12px;
  color: var(--color-text-tertiary);
  width: 16px;
  text-align: center;
  flex-shrink: 0;
}
.section-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-accent);
}
.section-content {
  padding: 8px 12px 16px 36px;
  font-size: 14px;
  line-height: 1.9;
}
.section-content :deep(h3) {
  font-size: 14px;
  margin: 12px 0 6px;
  color: var(--color-text-primary);
}
.section-content :deep(pre) {
  background: #1a1a2e;
  color: #e8e8e8;
  padding: 12px 16px;
  border-radius: var(--radius-md);
  overflow-x: auto;
  font-size: 13px;
  line-height: 1.6;
}
.section-content :deep(code) {
  font-family: var(--font-mono);
  font-size: 13px;
}
.section-content :deep(ul), .section-content :deep(ol) {
  padding-left: 20px;
  margin: 8px 0;
}
.section-content :deep(li) {
  margin: 4px 0;
}
.section-content :deep(blockquote) {
  border-left: 3px solid var(--color-accent);
  padding-left: 12px;
  color: var(--color-text-secondary);
  margin: 10px 0;
}
.section-content :deep(p) {
  margin: 6px 0;
}

@media (max-width: 768px) {
  .path-layout {
    flex-direction: column;
  }
  .path-sidebar {
    width: 100%;
    position: static;
  }
  .sidebar-list {
    max-height: 200px;
  }
}
</style>
