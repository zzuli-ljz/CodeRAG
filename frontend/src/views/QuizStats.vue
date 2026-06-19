<template>
  <div class="page-container">
    <div class="page-header">
      <h1 class="page-title">答题记录</h1>
      <p class="page-desc">错题本、收藏题目与答题统计</p>
    </div>

    <!-- 统计概览 -->
    <div class="card stats-overview" v-if="stats">
      <div class="grid grid-4">
        <div class="stat-item">
          <div class="stat-num">{{ stats.totalAttempts || 0 }}</div>
          <div class="stat-label">总答题数</div>
        </div>
        <div class="stat-item">
          <div class="stat-num correct">{{ stats.correctAttempts || 0 }}</div>
          <div class="stat-label">正确</div>
        </div>
        <div class="stat-item">
          <div class="stat-num wrong">{{ stats.wrongAttempts || 0 }}</div>
          <div class="stat-label">错误</div>
        </div>
        <div class="stat-item">
          <div class="stat-num" :class="accuracyClass">{{ stats.accuracyRate || 0 }}%</div>
          <div class="stat-label">正确率</div>
        </div>
      </div>
    </div>

    <!-- 标签切换 -->
    <div class="tabs">
      <button
        class="tab-btn"
        :class="{ active: activeTab === 'wrong' }"
        @click="switchTab('wrong')"
      >
        错题本 ({{ stats?.wrongBookCount || 0 }})
      </button>
      <button
        class="tab-btn"
        :class="{ active: activeTab === 'favorite' }"
        @click="switchTab('favorite')"
      >
        收藏题目 ({{ stats?.favoriteCount || 0 }})
      </button>
      <button
        class="tab-btn"
        :class="{ active: activeTab === 'history' }"
        @click="switchTab('history')"
      >
        全部记录
      </button>
    </div>

    <!-- 筛选栏（仅错题本 tab 显示） -->
    <div class="card filter-bar" v-if="activeTab === 'wrong'">
      <div class="filter-row">
        <div class="filter-item">
          <label class="filter-label">难度</label>
          <select v-model="filters.difficulty" class="filter-select" @change="applyFilters">
            <option value="">全部</option>
            <option v-for="d in filterOptions.difficulties" :key="d" :value="d">{{ d }}</option>
          </select>
        </div>
        <div class="filter-item">
          <label class="filter-label">知识点</label>
          <select v-model="filters.knowledgePoint" class="filter-select" @change="applyFilters">
            <option value="">全部</option>
            <option v-for="k in filterOptions.knowledgePoints" :key="k" :value="k">{{ k }}</option>
          </select>
        </div>
        <div class="filter-item">
          <label class="filter-label">来源仓库</label>
          <select v-model="filters.repoId" class="filter-select" @change="applyFilters">
            <option :value="undefined">全部</option>
            <option v-for="r in filterOptions.repos" :key="r.id" :value="r.id">{{ r.name }}</option>
          </select>
        </div>
        <div class="filter-item filter-search">
          <label class="filter-label">搜索</label>
          <input
            v-model="filters.keyword"
            class="filter-input"
            placeholder="搜索题目内容..."
            @keyup.enter="applyFilters"
          />
          <button class="btn btn-sm btn-primary" @click="applyFilters">搜索</button>
        </div>
        <div class="filter-item filter-reset">
          <button class="btn btn-sm btn-secondary" @click="resetFilters">重置</button>
        </div>
      </div>
    </div>

    <!-- 列表 -->
    <div v-if="loading" class="loading"><div class="spinner"></div> 加载中...</div>

    <template v-else-if="records.length">
      <div v-for="record in records" :key="record.id" class="card record-card">
        <div class="record-header">
          <div class="record-meta">
            <span class="tag" :class="record.isCorrect ? 'tag-success' : 'tag-error'">
              {{ record.isCorrect ? '正确' : '错误' }}
            </span>
            <span class="tag tag-accent" v-if="record.status === 'WRONG_BOOK'">错题本</span>
            <span class="tag tag-accent" v-if="record.status === 'FAVORITE'">⭐ 收藏</span>
            <span class="tag tag-info" v-if="record.difficulty">{{ record.difficulty }}</span>
            <span class="tag tag-info" v-if="record.knowledgePoint">{{ record.knowledgePoint }}</span>
            <span class="tag tag-repo" v-if="record.repoName">{{ record.repoName }}</span>
            <span class="record-time">{{ formatTime(record.createdAt) }}</span>
          </div>
          <div class="record-actions">
            <button
              class="btn btn-sm"
              :class="record.status === 'WRONG_BOOK' ? 'btn-primary' : 'btn-secondary'"
              @click="toggleStatus(record, 'WRONG_BOOK')"
            >
              {{ record.status === 'WRONG_BOOK' ? '取消错题' : '加入错题本' }}
            </button>
            <button
              class="btn btn-sm"
              :class="record.status === 'FAVORITE' ? 'btn-primary' : 'btn-secondary'"
              @click="toggleStatus(record, 'FAVORITE')"
            >
              {{ record.status === 'FAVORITE' ? '取消收藏' : '收藏' }}
            </button>
          </div>
        </div>

        <!-- 题目内容（可折叠） -->
        <div class="record-question" v-if="record.question">
          <div class="collapsible-header" @click="toggleCollapse(record, 'question')">
            <span class="collapse-icon">{{ isCollapsed(record, 'question') ? '▶' : '▼' }}</span>
            <span class="collapse-title">题目内容</span>
            <span class="collapse-hint" v-if="isCollapsed(record, 'question')">点击展开</span>
          </div>
          <div class="collapsible-body" v-show="!isCollapsed(record, 'question')">
            <div class="question-text" v-html="renderMarkdown(record.question)"></div>
            <div class="question-options" v-if="record.options">
              <div v-for="(opt, idx) in parseOptions(record.options)" :key="idx" class="option-item"
                   :class="{ 'option-correct': record.correctAnswer && opt.startsWith(record.correctAnswer + '.') }">
                {{ opt }}
              </div>
            </div>
          </div>
        </div>

        <!-- 代码片段（可折叠，默认折叠） -->
        <div class="record-code" v-if="record.codeSnippet">
          <div class="collapsible-header" @click="toggleCollapse(record, 'code')">
            <span class="collapse-icon">{{ isCollapsed(record, 'code') ? '▶' : '▼' }}</span>
            <span class="collapse-title">参考代码</span>
            <span class="collapse-hint" v-if="isCollapsed(record, 'code')">点击展开</span>
          </div>
          <div class="collapsible-body" v-show="!isCollapsed(record, 'code')">
            <pre><code>{{ record.codeSnippet }}</code></pre>
          </div>
        </div>

        <div class="record-answer">
          <div class="answer-row">
            <span class="answer-label">你的答案：</span>
            <span class="answer-value" :class="{ 'answer-wrong': !record.isCorrect }">{{ record.userAnswer }}</span>
          </div>
          <div class="answer-row" v-if="record.correctAnswer && !record.isCorrect">
            <span class="answer-label">正确答案：</span>
            <span class="answer-value answer-correct">{{ record.correctAnswer }}</span>
          </div>
        </div>
        <div v-if="record.aiFeedback" class="record-feedback" v-html="renderMarkdown(record.aiFeedback)"></div>
      </div>

      <!-- 分页 -->
      <div class="pagination" v-if="totalPages > 1">
        <button class="btn btn-secondary btn-sm" :disabled="currentPage <= 0" @click="goPage(currentPage - 1)">上一页</button>
        <span class="page-info">{{ currentPage + 1 }} / {{ totalPages }}</span>
        <button class="btn btn-secondary btn-sm" :disabled="currentPage >= totalPages - 1" @click="goPage(currentPage + 1)">下一页</button>
      </div>
    </template>

    <div v-else class="empty-state">
      <div>暂无记录</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { quizApi } from '@/api'
import MarkdownIt from 'markdown-it'

const md = new MarkdownIt({ html: false, breaks: true })

const stats = ref<any>(null)
const records = ref<any[]>([])
const activeTab = ref('wrong')
const loading = ref(false)
const currentPage = ref(0)
const totalPages = ref(1)

// 筛选条件
const filters = reactive({
  difficulty: '',
  knowledgePoint: '',
  repoId: undefined as number | undefined,
  keyword: ''
})

// 筛选选项
const filterOptions = reactive({
  difficulties: [] as string[],
  knowledgePoints: [] as string[],
  repos: [] as { id: number; name: string }[]
})

// 折叠状态管理（record.id -> { question?: boolean, code?: boolean }）
const collapseState = reactive<Record<number, { question: boolean; code: boolean }>>({})

function isCollapsed(record: any, key: 'question' | 'code'): boolean {
  const state = collapseState[record.id]
  if (!state) {
    // 默认：题目展开，代码折叠
    collapseState[record.id] = { question: false, code: true }
    return key === 'code'
  }
  return state[key]
}

function toggleCollapse(record: any, key: 'question' | 'code') {
  if (!collapseState[record.id]) {
    collapseState[record.id] = { question: false, code: true }
  }
  collapseState[record.id][key] = !collapseState[record.id][key]
}

const accuracyClass = computed(() => {
  if (!stats.value) return ''
  const rate = stats.value.accuracyRate || 0
  if (rate >= 80) return 'correct'
  if (rate >= 50) return ''
  return 'wrong'
})

function renderMarkdown(text: string) {
  return md.render(text || '')
}

function parseOptions(options: string): string[] {
  if (!options) return []
  return options.split('\n').filter(o => o.trim())
}

function formatTime(t: string | Date) {
  return new Date(t).toLocaleString('zh-CN')
}

async function loadStats() {
  try {
    const res = await quizApi.getStats()
    if (res.data.code === 200) stats.value = res.data.data
  } catch {}
}

async function loadFilterOptions() {
  try {
    const res = await quizApi.getWrongBookFilters()
    if (res.data.code === 200) {
      filterOptions.difficulties = res.data.data.difficulties || []
      filterOptions.knowledgePoints = res.data.data.knowledgePoints || []
      filterOptions.repos = res.data.data.repos || []
    }
  } catch {}
}

async function loadRecords(page = 0) {
  loading.value = true
  try {
    let res
    if (activeTab.value === 'wrong') {
      const params: any = {}
      if (filters.difficulty) params.difficulty = filters.difficulty
      if (filters.knowledgePoint) params.knowledgePoint = filters.knowledgePoint
      if (filters.repoId) params.repoId = filters.repoId
      if (filters.keyword) params.keyword = filters.keyword
      res = await quizApi.getWrongBook(page, 10, params)
    } else if (activeTab.value === 'favorite') {
      res = await quizApi.getFavorites(page, 10)
    } else {
      res = await quizApi.getAttempts(page, 10)
    }
    if (res.data.code === 200) {
      const data = res.data.data
      records.value = data.content || []
      currentPage.value = data.number || 0
      totalPages.value = data.totalPages || 1
    }
  } catch {} finally {
    loading.value = false
  }
}

function applyFilters() {
  loadRecords(0)
}

function resetFilters() {
  filters.difficulty = ''
  filters.knowledgePoint = ''
  filters.repoId = undefined
  filters.keyword = ''
  loadRecords(0)
}

async function toggleStatus(record: any, status: string) {
  try {
    const res = await quizApi.toggleStatus(record.id, status)
    if (res.data.code === 200) {
      record.status = res.data.data.status
      await loadStats()
    }
  } catch (e: any) {
    alert('操作失败: ' + (e.response?.data?.message || e.message))
  }
}

function switchTab(tab: string) {
  activeTab.value = tab
  loadRecords(0)
}

function goPage(page: number) {
  loadRecords(page)
}

onMounted(async () => {
  await loadStats()
  await loadFilterOptions()
  await loadRecords()
})
</script>

<style scoped>
.stats-overview {
  margin-bottom: var(--space-lg);
}
.stats-overview .stat-item {
  text-align: center;
}
.stats-overview .stat-num {
  font-size: 28px;
  font-weight: 700;
  font-family: var(--font-mono);
  color: var(--color-text-primary);
}
.stats-overview .stat-num.correct { color: var(--color-success); }
.stats-overview .stat-num.wrong { color: var(--color-error); }
.stats-overview .stat-label {
  font-size: 12px;
  color: var(--color-text-tertiary);
  margin-top: 4px;
}

.tabs {
  display: flex;
  gap: var(--space-sm);
  margin-bottom: var(--space-lg);
}
.tab-btn {
  padding: 8px 20px;
  font-size: 14px;
  font-weight: 500;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-bg);
  color: var(--color-text-secondary);
  cursor: pointer;
  transition: all 0.15s;
  font-family: var(--font-sans);
}
.tab-btn:hover {
  border-color: var(--color-accent);
  color: var(--color-accent);
}
.tab-btn.active {
  background: var(--color-accent);
  color: #fff;
  border-color: var(--color-accent);
}

/* 筛选栏 */
.filter-bar {
  margin-bottom: var(--space-lg);
  padding: 16px;
}
.filter-row {
  display: flex;
  gap: var(--space-md);
  align-items: flex-end;
  flex-wrap: wrap;
}
.filter-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.filter-label {
  font-size: 12px;
  color: var(--color-text-tertiary);
  font-weight: 500;
}
.filter-select,
.filter-input {
  padding: 6px 10px;
  font-size: 13px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-bg);
  color: var(--color-text-primary);
  font-family: var(--font-sans);
  min-width: 120px;
}
.filter-select:focus,
.filter-input:focus {
  outline: none;
  border-color: var(--color-accent);
}
.filter-input {
  min-width: 200px;
}
.filter-search {
  flex-direction: row;
  align-items: flex-end;
  gap: var(--space-xs);
}
.filter-search .filter-label {
  align-self: flex-start;
  margin-bottom: 6px;
}
.filter-reset {
  justify-content: flex-end;
}

/* 折叠组件 */
.collapsible-header {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  user-select: none;
  padding: 4px 0;
  margin-bottom: 8px;
}
.collapsible-header:hover .collapse-title {
  color: var(--color-accent);
}
.collapse-icon {
  font-size: 10px;
  color: var(--color-text-tertiary);
  transition: transform 0.15s;
  width: 12px;
}
.collapse-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-secondary);
  transition: color 0.15s;
}
.collapse-hint {
  font-size: 11px;
  color: var(--color-text-tertiary);
  margin-left: auto;
}
.collapsible-body {
  padding-top: 4px;
}

.record-card {
  margin-bottom: 12px;
  padding: 16px;
}
.record-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 10px;
  flex-wrap: wrap;
  gap: var(--space-sm);
}
.record-meta {
  display: flex;
  gap: var(--space-sm);
  align-items: center;
  flex-wrap: wrap;
}
.record-time {
  font-size: 12px;
  color: var(--color-text-tertiary);
}
.record-actions {
  display: flex;
  gap: var(--space-xs);
}
.record-answer {
  margin-bottom: 8px;
}
.answer-row {
  font-size: 14px;
  margin-bottom: 4px;
}
.answer-label {
  color: var(--color-text-secondary);
}
.answer-value {
  font-family: var(--font-mono);
  font-weight: 500;
}
.answer-wrong {
  color: var(--color-error);
}
.answer-correct {
  color: var(--color-success);
}

.record-question {
  margin-bottom: 12px;
  padding: 12px 16px;
  background: var(--color-bg-secondary);
  border-radius: var(--radius-md);
  border-left: 3px solid var(--color-accent);
}
.question-text {
  font-size: 14px;
  font-weight: 600;
  line-height: 1.6;
  margin-bottom: 8px;
  color: var(--color-text-primary);
}
.question-options {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.option-item {
  font-size: 13px;
  font-family: var(--font-mono);
  padding: 4px 8px;
  border-radius: var(--radius-sm);
  background: var(--color-bg);
  color: var(--color-text-secondary);
}
.option-correct {
  background: #e6f7ed;
  color: var(--color-success);
  font-weight: 600;
}

.record-code {
  margin-bottom: 12px;
  padding: 12px 16px;
  background: #1a1a2e;
  border-radius: var(--radius-md);
}
.record-code .collapsible-header {
  margin-bottom: 0;
}
.record-code .collapse-title {
  color: #a0a0b0;
}
.record-code .collapse-icon {
  color: #a0a0b0;
}
.record-code .collapse-hint {
  color: #707080;
}
.record-code pre {
  margin: 0;
  overflow-x: auto;
}
.record-code code {
  font-size: 12px;
  color: #e8e8e8;
  font-family: var(--font-mono);
  line-height: 1.5;
}
.record-feedback {
  margin-top: 10px;
  padding: 12px 16px;
  background: var(--color-bg-secondary);
  border-radius: var(--radius-md);
  font-size: 13px;
  line-height: 1.7;
}
.record-feedback :deep(pre) {
  background: #1a1a2e;
  color: #e8e8e8;
  padding: var(--space-sm);
  border-radius: var(--radius-sm);
  overflow-x: auto;
}

.tag-repo {
  background: #e8f0fe;
  color: #1a73e8;
}

.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-md);
  padding: var(--space-md) 0;
}
.page-info {
  font-size: 13px;
  color: var(--color-text-secondary);
}
</style>
