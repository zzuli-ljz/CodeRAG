<template>
  <div class="page-container">
    <div class="page-header">
      <h1 class="page-title">导入仓库</h1>
      <p class="page-desc">粘贴 GitHub 或 Gitee 公开仓库链接，系统将自动解析、分块、向量化入库</p>
    </div>

    <div class="import-layout">
      <!-- 左侧：导入表单 -->
      <div class="card import-card">
        <div class="form-group">
          <label class="form-label">仓库链接</label>
          <input
            v-model="repoUrl"
            type="text"
            class="input"
            placeholder="https://github.com/owner/repo 或 https://gitee.com/owner/repo"
          />
        </div>
        <div class="form-group">
          <label class="form-label">分支（可选，默认为主分支）</label>
          <input v-model="branch" type="text" class="input" placeholder="main / master" />
        </div>
        <div class="platform-hint">
          <span class="tag" :class="platformTag">{{ platformLabel }}</span>
        </div>
        <button
          class="btn btn-primary btn-lg"
          style="margin-top:16px"
          @click="handleImport"
          :disabled="!repoUrl || importing"
        >
          {{ importing ? '提交中...' : '开始导入' }}
        </button>
      </div>

      <!-- 右侧：热门仓库推荐 + 搜索 -->
      <div class="trending-panel">
        <div class="trending-tabs">
          <button
            :class="['trending-tab', { active: trendingTab === 'github' }]"
            @click="switchTrending('github')"
          >
            <svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor"><path d="M12 0C5.37 0 0 5.37 0 12c0 5.31 3.435 9.795 8.205 11.385.6.105.825-.255.825-.57 0-.285-.015-1.23-.015-2.235-3.015.555-3.795-.735-4.035-1.41-.135-.345-.72-1.41-1.23-1.695-.42-.225-1.02-.78-.015-.795.945-.015 1.62.87 1.845 1.23 1.08 1.815 2.805 1.305 3.495.99.105-.78.42-1.305.765-1.605-2.67-.3-5.46-1.335-5.46-5.925 0-1.305.465-2.385 1.23-3.225-.12-.3-.54-1.53.12-3.18 0 0 1.005-.315 3.3 1.23.96-.27 1.98-.405 3-.405s2.04.135 3 .405c2.295-1.56 3.3-1.23 3.3-1.23.66 1.65.24 2.88.12 3.18.765.84 1.23 1.905 1.23 3.225 0 4.605-2.805 5.625-5.475 5.925.435.375.81 1.095.81 2.22 0 1.605-.015 2.895-.015 3.3 0 .315.225.69.825.57A12.02 12.02 0 0 0 24 12c0-6.63-5.37-12-12-12z"/></svg>
            GitHub 热门
          </button>
          <button
            :class="['trending-tab', { active: trendingTab === 'gitee' }]"
            @click="switchTrending('gitee')"
          >
            <svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor"><circle cx="12" cy="12" r="10" fill="none" stroke="currentColor" stroke-width="2"/><text x="12" y="16" text-anchor="middle" font-size="10" font-weight="bold" fill="currentColor">G</text></svg>
            Gitee 热门
          </button>
        </div>

        <!-- 搜索框 -->
        <div class="search-box">
          <svg class="search-icon" viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
          <input
            v-model="searchKeyword"
            type="text"
            class="search-input"
            :placeholder="trendingTab === 'github' ? '搜索 GitHub 仓库...' : '搜索 Gitee 仓库...'"
            @keyup.enter="doSearch"
          />
          <button class="search-btn" @click="doSearch" :disabled="searchLoading || !searchKeyword.trim()">
            搜索
          </button>
        </div>

        <!-- 搜索模式：结果列表 -->
        <div class="trending-list" v-if="isSearchMode && !searchLoading && searchResults.length > 0">
          <div class="search-result-header">
            <span>搜索 "{{ lastSearchKeyword }}" 共 {{ searchTotal }} 个结果</span>
            <button class="btn-text" @click="clearSearch">✕ 清除</button>
          </div>
          <div
            v-for="repo in searchResults"
            :key="repo.id"
            class="trending-item"
            @click="previewRepo(repo)"
          >
            <div class="trending-repo-name">
              <span class="trending-owner">{{ repo.owner }}/</span>
              <span class="trending-name">{{ repo.name }}</span>
            </div>
            <p class="trending-desc">{{ repo.description || '暂无描述' }}</p>
            <div class="trending-meta">
              <span class="trending-stars">⭐ {{ formatNumber(repo.stars) }}</span>
              <span v-if="repo.language" class="trending-lang">{{ repo.language }}</span>
            </div>
          </div>
        </div>

        <!-- 热门模式：推荐列表 -->
        <div class="trending-list" v-if="!isSearchMode && !trendingLoading && trendingRepos.length > 0">
          <div
            v-for="repo in trendingRepos"
            :key="repo.id"
            class="trending-item"
            @click="previewRepo(repo)"
          >
            <div class="trending-repo-name">
              <span class="trending-owner">{{ repo.owner }}/</span>
              <span class="trending-name">{{ repo.name }}</span>
            </div>
            <p class="trending-desc">{{ repo.description || '暂无描述' }}</p>
            <div class="trending-meta">
              <span class="trending-stars">⭐ {{ formatNumber(repo.stars) }}</span>
              <span v-if="repo.language" class="trending-lang">{{ repo.language }}</span>
            </div>
          </div>
        </div>

        <!-- 搜索无结果 -->
        <div class="trending-empty" v-if="isSearchMode && !searchLoading && searchResults.length === 0 && lastSearchKeyword">
          <p>未找到与 "{{ lastSearchKeyword }}" 相关的仓库</p>
          <p class="empty-hint">请尝试其他关键词</p>
        </div>

        <div class="trending-loading" v-if="trendingLoading || searchLoading">
          <span class="spinner"></span>
          <span>{{ searchLoading ? '搜索中...' : '加载热门仓库...' }}</span>
        </div>

        <div class="trending-error" v-if="trendingError && !isSearchMode">
          <p>{{ trendingError }}</p>
          <button class="btn btn-secondary btn-sm" @click="fetchTrending">重试</button>
        </div>
      </div>

      <!-- 仓库预览弹窗 -->
      <div class="modal-overlay" v-if="previewRepoData" @click.self="closePreview">
        <div class="modal-card repo-preview-card">
          <div class="modal-header">
            <h3>仓库详情</h3>
            <button class="modal-close" @click="closePreview">✕</button>
          </div>
          <div class="modal-body">
            <div class="preview-info">
              <div class="preview-name">
                <span class="preview-platform-tag" :class="previewRepoData.platform">
                  {{ previewRepoData.platform === 'github' ? 'GitHub' : 'Gitee' }}
                </span>
                <strong>{{ previewRepoData.fullName }}</strong>
              </div>
              <p class="preview-desc">{{ previewRepoData.description || '暂无描述' }}</p>
              <div class="preview-stats">
                <span class="preview-stat">⭐ {{ formatNumber(previewRepoData.stars) }} Stars</span>
                <span v-if="previewRepoData.language" class="preview-stat">📄 {{ previewRepoData.language }}</span>
                <a :href="previewRepoData.htmlUrl" target="_blank" class="preview-link">
                  🔗 在 {{ previewRepoData.platform === 'github' ? 'GitHub' : 'Gitee' }} 上查看
                </a>
              </div>
            </div>
          </div>
          <div class="modal-footer">
            <button class="btn btn-secondary" @click="closePreview">取消</button>
            <button class="btn btn-primary" @click="importFromPreview">
              导入此仓库
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- 进度展示 -->
    <div v-if="task" class="card" style="margin-top:16px">
      <h3 style="margin-bottom:16px">导入进度</h3>

      <!-- 步骤指示器 -->
      <div class="import-steps">
        <div
          v-for="(step, idx) in steps"
          :key="idx"
          class="step-item"
          :class="{
            'step-active': currentStep === idx,
            'step-done': currentStep > idx,
            'step-error': task.status === 'FAILED' && currentStep === idx
          }"
        >
          <div class="step-icon">
            <svg v-if="currentStep > idx" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="3"><polyline points="20 6 9 17 4 12"/></svg>
            <svg v-else-if="task.status === 'FAILED' && currentStep === idx" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="3"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
            <span v-else class="step-number">{{ idx + 1 }}</span>
          </div>
          <div class="step-info">
            <div class="step-label">{{ step.label }}</div>
            <div v-if="currentStep === idx && task.status !== 'FAILED'" class="step-detail">
              <span class="spinner"></span>
              {{ step.runningText }}
            </div>
            <div v-else-if="currentStep > idx" class="step-detail step-done-text">
              {{ step.doneText }}
            </div>
          </div>
        </div>
      </div>

      <!-- 进度条 -->
      <div class="progress-bar" style="margin-top:20px">
        <div
          class="progress-bar-fill"
          :class="{ 'progress-error': task.status === 'FAILED' }"
          :style="{ width: task.progress + '%' }"
        ></div>
      </div>
      <div style="margin-top:6px;display:flex;justify-content:space-between;font-size:12px;color:var(--color-text-secondary)">
        <span>{{ task.statusMessage }}</span>
        <span>{{ task.progress }}%</span>
      </div>

      <!-- 失败详情 -->
      <div v-if="task.status === 'FAILED'" class="error-detail">
        <div class="error-header">
          <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>
          <strong>导入失败</strong>
        </div>
        <div class="error-message">{{ task.statusMessage }}</div>
        <div v-if="task.errorMessage && task.errorMessage !== task.statusMessage" class="error-technical">
          <details>
            <summary>技术详情</summary>
            <pre>{{ task.errorMessage }}</pre>
          </details>
        </div>
        <button class="btn btn-primary" style="margin-top:12px" @click="handleRetry">
          重新导入
        </button>
      </div>

      <!-- 完成提示 -->
      <div v-if="task.status === 'COMPLETED'" class="success-detail">
        <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/></svg>
        <span>导入完成！{{ task.statusMessage }}</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { repoApi } from '@/api'
import { useRouter } from 'vue-router'
import axios from 'axios'

const router = useRouter()
const repoUrl = ref('')
const branch = ref('')
const importing = ref(false)
const task = ref<any>(null)
let pollTimer: any = null

// ========== 热门仓库 + 搜索 ==========
interface TrendingRepo {
  id: number
  owner: string
  name: string
  fullName: string
  htmlUrl: string
  description: string
  stars: number
  language: string
  platform: 'github' | 'gitee'
}

const trendingTab = ref<'github' | 'gitee'>('github')
const trendingRepos = ref<TrendingRepo[]>([])
const trendingLoading = ref(false)
const trendingError = ref('')

// 搜索相关
const searchKeyword = ref('')
const lastSearchKeyword = ref('')
const searchResults = ref<TrendingRepo[]>([])
const searchLoading = ref(false)
const searchTotal = ref(0)
const isSearchMode = ref(false)

// 预览弹窗
const previewRepoData = ref<TrendingRepo | null>(null)

function formatNumber(n: number): string {
  if (n >= 1000) return (n / 1000).toFixed(1) + 'k'
  return String(n)
}

// ---- GitHub API ----
async function fetchGitHubTrending(): Promise<TrendingRepo[]> {
  const { data } = await axios.get('https://api.github.com/search/repositories', {
    params: {
      q: 'stars:>5000 created:>2024-01-01',
      sort: 'stars',
      order: 'desc',
      per_page: 6
    },
    headers: { Accept: 'application/vnd.github.v3+json' },
    timeout: 10000
  })
  return (data.items || []).map(mapGitHubRepo)
}

async function searchGitHub(keyword: string): Promise<{ repos: TrendingRepo[]; total: number }> {
  const { data } = await axios.get('https://api.github.com/search/repositories', {
    params: {
      q: keyword,
      sort: 'stars',
      order: 'desc',
      per_page: 10
    },
    headers: { Accept: 'application/vnd.github.v3+json' },
    timeout: 10000
  })
  return {
    repos: (data.items || []).map(mapGitHubRepo),
    total: data.total_count || 0
  }
}

function mapGitHubRepo(item: any): TrendingRepo {
  return {
    id: item.id,
    owner: item.owner?.login || '',
    name: item.name,
    fullName: item.full_name,
    htmlUrl: item.html_url,
    description: item.description,
    stars: item.stargazers_count,
    language: item.language,
    platform: 'github' as const
  }
}

// ---- Gitee API ----
async function fetchGiteeTrending(): Promise<TrendingRepo[]> {
  // Gitee 搜索 API 不支持 stars:>xxx 语法，使用热门关键词搜索
  const { data } = await axios.get('https://gitee.com/api/v5/search/repositories', {
    params: {
      q: 'AI',
      sort: 'stars',
      order: 'desc',
      per_page: 6
    },
    timeout: 10000
  })
  return (data || []).map(mapGiteeRepo)
}

async function searchGitee(keyword: string): Promise<{ repos: TrendingRepo[]; total: number }> {
  const { data } = await axios.get('https://gitee.com/api/v5/search/repositories', {
    params: {
      q: keyword,
      sort: 'stars',
      order: 'desc',
      per_page: 10
    },
    timeout: 10000
  })
  const repos = (data || []).map(mapGiteeRepo)
  return { repos, total: repos.length }
}

function mapGiteeRepo(item: any): TrendingRepo {
  return {
    id: item.id,
    owner: item.owner?.login || item.namespace?.path || '',
    name: item.name,
    fullName: item.full_name,
    htmlUrl: item.html_url,
    description: item.description,
    stars: item.stargazers_count || item.stars_count || 0,
    language: item.language,
    platform: 'gitee' as const
  }
}

// ---- 统一操作 ----
async function fetchTrending() {
  trendingLoading.value = true
  trendingError.value = ''
  trendingRepos.value = []
  isSearchMode.value = false
  try {
    if (trendingTab.value === 'github') {
      trendingRepos.value = await fetchGitHubTrending()
    } else {
      trendingRepos.value = await fetchGiteeTrending()
    }
  } catch (e: any) {
    if (e.response?.status === 403) {
      trendingError.value = 'API 请求频率限制，请稍后重试'
    } else if (e.code === 'ECONNABORTED') {
      trendingError.value = '请求超时，请检查网络连接'
    } else {
      trendingError.value = '加载失败，请检查网络或稍后重试'
    }
  } finally {
    trendingLoading.value = false
  }
}

function switchTrending(platform: 'github' | 'gitee') {
  trendingTab.value = platform
  clearSearch()
  fetchTrending()
}

// ---- 搜索 ----
async function doSearch() {
  const kw = searchKeyword.value.trim()
  if (!kw || searchLoading.value) return

  searchLoading.value = true
  searchResults.value = []
  lastSearchKeyword.value = kw
  isSearchMode.value = true
  trendingError.value = ''

  try {
    if (trendingTab.value === 'github') {
      const result = await searchGitHub(kw)
      searchResults.value = result.repos
      searchTotal.value = result.total
    } else {
      const result = await searchGitee(kw)
      searchResults.value = result.repos
      searchTotal.value = result.total
    }
  } catch (e: any) {
    if (e.response?.status === 403) {
      trendingError.value = 'API 请求频率限制，请稍后重试'
    } else if (e.code === 'ECONNABORTED') {
      trendingError.value = '请求超时，请检查网络连接'
    } else {
      trendingError.value = '搜索失败，请稍后重试'
    }
    isSearchMode.value = false
  } finally {
    searchLoading.value = false
  }
}

function clearSearch() {
  searchKeyword.value = ''
  lastSearchKeyword.value = ''
  searchResults.value = []
  searchTotal.value = 0
  isSearchMode.value = false
  trendingError.value = ''
}

// ---- 预览 & 导入 ----
function previewRepo(repo: TrendingRepo) {
  previewRepoData.value = repo
}

function closePreview() {
  previewRepoData.value = null
}

function importFromPreview() {
  if (previewRepoData.value) {
    repoUrl.value = previewRepoData.value.htmlUrl
    branch.value = ''
    closePreview()
    // 滚动到顶部让用户看到表单
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }
}

onMounted(() => {
  fetchTrending()
})

const steps = [
  { label: '获取文件列表', runningText: '正在获取仓库文件...', doneText: '文件列表获取完成' },
  { label: '代码分块', runningText: '正在解析和分块代码...', doneText: '代码分块完成' },
  { label: '向量化入库', runningText: '正在向量化并存储...', doneText: '向量化入库完成' },
  { label: '完成', runningText: '正在完成...', doneText: '导入完成' }
]

const currentStep = computed(() => {
  if (!task.value) return -1
  const p = task.value.progress
  const s = task.value.status
  if (s === 'FAILED') {
    if (p < 35) return 0
    if (p < 55) return 1
    if (p < 95) return 2
    return 3
  }
  if (s === 'COMPLETED') return 4
  if (p < 35) return 0
  if (p < 55) return 1
  if (p < 95) return 2
  return 3
})

const platformLabel = computed(() => {
  if (repoUrl.value.includes('github.com')) return 'GitHub'
  if (repoUrl.value.includes('gitee.com')) return 'Gitee'
  return '未识别'
})

const platformTag = computed(() => {
  if (repoUrl.value.includes('github.com') || repoUrl.value.includes('gitee.com')) return 'tag-accent'
  return ''
})

async function handleImport() {
  if (!repoUrl.value) return
  importing.value = true
  try {
    const res = await repoApi.importRepo({ repoUrl: repoUrl.value, branch: branch.value || undefined })
    if (res.data.code === 200) {
      task.value = res.data.data
      startPolling()
    }
  } catch (e: any) {
    const msg = e.response?.data?.message || e.message || '导入请求失败'
    alert(msg)
    // 同时在进度区域显示错误
    task.value = { status: 'FAILED', progress: 0, statusMessage: '导入失败', errorMessage: msg }
  } finally {
    importing.value = false
  }
}

function handleRetry() {
  task.value = null
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
  handleImport()
}

function startPolling() {
  if (pollTimer) clearInterval(pollTimer)
  pollTimer = setInterval(async () => {
    if (!task.value) return
    try {
      const res = await repoApi.getTaskStatus(task.value.id)
      if (res.data.code === 200) {
        task.value = res.data.data
        if (task.value.status === 'COMPLETED' || task.value.status === 'FAILED') {
          clearInterval(pollTimer)
          pollTimer = null
        }
      }
    } catch { /* ignore */ }
  }, 2000)
}
</script>

<style scoped>
/* 左右布局 */
.import-layout {
  display: flex;
  gap: 24px;
  align-items: flex-start;
}
.import-card {
  flex: 1;
  max-width: 520px;
}

/* 热门仓库面板 */
.trending-panel {
  flex: 1;
  max-width: 400px;
  background: var(--color-bg);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  overflow: hidden;
}
.trending-tabs {
  display: flex;
  border-bottom: 1px solid var(--color-border);
}
.trending-tab {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 12px 8px;
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text-secondary);
  background: transparent;
  border: none;
  cursor: pointer;
  transition: all 0.15s;
}
.trending-tab:hover {
  color: var(--color-text-primary);
  background: var(--color-bg-tertiary);
}
.trending-tab.active {
  color: var(--color-primary);
  border-bottom: 2px solid var(--color-primary);
  margin-bottom: -1px;
}

/* 热门列表 */
.trending-list {
  padding: 8px;
  max-height: 420px;
  overflow-y: auto;
}
.trending-item {
  padding: 12px;
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: background 0.12s;
  border: 1px solid transparent;
}
.trending-item:hover {
  background: var(--color-accent-light);
  border-color: var(--color-accent);
}
.trending-item + .trending-item {
  margin-top: 4px;
}
.trending-repo-name {
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 4px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.trending-owner {
  color: var(--color-text-secondary);
  font-weight: 400;
}
.trending-name {
  color: var(--color-accent);
}
.trending-desc {
  font-size: 12px;
  color: var(--color-text-secondary);
  line-height: 1.4;
  margin-bottom: 6px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.trending-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 12px;
}
.trending-stars {
  color: #e3b341;
}
.trending-lang {
  color: var(--color-text-secondary);
  padding: 1px 8px;
  background: var(--color-bg-tertiary);
  border-radius: 10px;
  font-size: 11px;
}

/* 搜索框 */
.search-box {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 12px;
  border-bottom: 1px solid var(--color-border);
  background: var(--color-bg-secondary, rgba(0,0,0,0.02));
}
.search-icon {
  flex-shrink: 0;
  color: var(--color-text-secondary);
}
.search-input {
  flex: 1;
  border: none;
  background: transparent;
  font-size: 13px;
  color: var(--color-text-primary);
  outline: none;
  padding: 4px 0;
}
.search-input::placeholder {
  color: var(--color-text-tertiary, #999);
}
.search-btn {
  flex-shrink: 0;
  padding: 4px 12px;
  font-size: 12px;
  font-weight: 500;
  color: #fff;
  background: var(--color-primary);
  border: none;
  border-radius: 4px;
  cursor: pointer;
  transition: opacity 0.15s;
}
.search-btn:hover {
  opacity: 0.85;
}
.search-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.search-result-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  font-size: 12px;
  color: var(--color-text-secondary);
  border-bottom: 1px solid var(--color-border);
}
.btn-text {
  background: none;
  border: none;
  color: var(--color-primary);
  font-size: 12px;
  cursor: pointer;
  padding: 2px 6px;
}
.btn-text:hover {
  text-decoration: underline;
}

/* 空状态 */
.trending-empty {
  padding: 32px 16px;
  text-align: center;
  font-size: 13px;
  color: var(--color-text-secondary);
}
.trending-empty .empty-hint {
  font-size: 12px;
  color: var(--color-text-tertiary, #999);
  margin-top: 4px;
}

/* 加载/错误状态 */
.trending-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 40px 16px;
  font-size: 13px;
  color: var(--color-text-secondary);
}
.trending-error {
  padding: 24px 16px;
  text-align: center;
  font-size: 13px;
  color: var(--color-text-secondary);
}
.trending-error p {
  margin-bottom: 10px;
}
.form-group {
  margin-bottom: var(--space-md);
}
.form-label {
  display: block;
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text-secondary);
  margin-bottom: var(--space-xs);
}
.platform-hint {
  margin-top: var(--space-sm);
}

/* 步骤指示器 */
.import-steps {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.step-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  opacity: 0.45;
  transition: opacity 0.3s;
}
.step-item.step-active,
.step-item.step-done,
.step-item.step-error {
  opacity: 1;
}
.step-icon {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  background: var(--color-bg-tertiary);
  color: var(--color-text-secondary);
  font-size: 14px;
  font-weight: 600;
  transition: all 0.3s;
}
.step-active .step-icon {
  background: var(--color-primary);
  color: #fff;
}
.step-done .step-icon {
  background: var(--color-success, #10b981);
  color: #fff;
}
.step-error .step-icon {
  background: var(--color-error, #ef4444);
  color: #fff;
}
.step-info {
  padding-top: 5px;
}
.step-label {
  font-weight: 500;
  font-size: 14px;
}
.step-detail {
  font-size: 12px;
  color: var(--color-text-secondary);
  margin-top: 2px;
  display: flex;
  align-items: center;
  gap: 6px;
}
.step-done-text {
  color: var(--color-success, #10b981);
}

/* 加载动画 */
.spinner {
  display: inline-block;
  width: 12px;
  height: 12px;
  border: 2px solid var(--color-primary);
  border-top-color: transparent;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}
@keyframes spin {
  to { transform: rotate(360deg); }
}

/* 进度条错误态 */
.progress-bar-fill.progress-error {
  background: var(--color-error, #ef4444);
}

/* 错误详情 */
.error-detail {
  margin-top: 16px;
  padding: 16px;
  background: var(--color-error-bg, rgba(239, 68, 68, 0.08));
  border: 1px solid var(--color-error-border, rgba(239, 68, 68, 0.2));
  border-radius: 8px;
}
.error-header {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--color-error, #ef4444);
  font-size: 15px;
}
.error-message {
  margin-top: 8px;
  font-size: 14px;
  color: var(--color-text-primary);
  line-height: 1.5;
}
.error-technical {
  margin-top: 8px;
}
.error-technical summary {
  font-size: 12px;
  color: var(--color-text-secondary);
  cursor: pointer;
  user-select: none;
}
.error-technical pre {
  margin-top: 6px;
  padding: 8px;
  background: var(--color-bg-tertiary);
  border-radius: 4px;
  font-size: 12px;
  overflow-x: auto;
  max-height: 150px;
  overflow-y: auto;
  white-space: pre-wrap;
  word-break: break-all;
}

/* 成功提示 */
.success-detail {
  margin-top: 16px;
  padding: 12px 16px;
  background: var(--color-success-bg, rgba(16, 185, 129, 0.08));
  border: 1px solid var(--color-success-border, rgba(16, 185, 129, 0.2));
  border-radius: 8px;
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--color-success, #10b981);
  font-size: 14px;
}

/* 预览弹窗 */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  animation: fadeIn 0.15s ease;
}
@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}
.modal-card {
  background: var(--color-bg);
  border-radius: var(--radius-lg);
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
  width: 90%;
  max-width: 480px;
  animation: slideUp 0.2s ease;
}
@keyframes slideUp {
  from { transform: translateY(20px); opacity: 0; }
  to { transform: translateY(0); opacity: 1; }
}
.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid var(--color-border);
}
.modal-header h3 {
  font-size: 16px;
  font-weight: 600;
  margin: 0;
}
.modal-close {
  background: none;
  border: none;
  font-size: 18px;
  color: var(--color-text-secondary);
  cursor: pointer;
  padding: 4px;
  border-radius: 4px;
  transition: all 0.15s;
}
.modal-close:hover {
  color: var(--color-text-primary);
  background: var(--color-bg-tertiary);
}
.modal-body {
  padding: 20px;
}
.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding: 14px 20px;
  border-top: 1px solid var(--color-border);
}
.preview-name {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
  font-size: 15px;
  word-break: break-all;
}
.preview-platform-tag {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 600;
  flex-shrink: 0;
}
.preview-platform-tag.github {
  background: #24292e;
  color: #fff;
}
.preview-platform-tag.gitee {
  background: #c71d23;
  color: #fff;
}
.preview-desc {
  font-size: 13px;
  color: var(--color-text-secondary);
  line-height: 1.5;
  margin-bottom: 12px;
}
.preview-stats {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 14px;
  font-size: 13px;
}
.preview-stat {
  color: var(--color-text-secondary);
}
.preview-link {
  color: var(--color-primary);
  text-decoration: none;
  font-size: 13px;
}
.preview-link:hover {
  text-decoration: underline;
}
</style>
