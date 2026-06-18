<template>
  <div class="compare-page">
    <!-- 左侧：历史对比记录 -->
    <div class="compare-sidebar">
      <div class="sidebar-header">
        <h3>对比历史</h3>
        <button class="btn btn-primary btn-sm" @click="startNewCompare" :disabled="comparing">
          新对比
        </button>
      </div>
      <div class="sidebar-list">
        <div
          v-for="(item, idx) in history"
          :key="item.id"
          class="history-item"
          :class="{ active: selectedId === item.id }"
          @click="selectItem(item)"
        >
          <div class="history-refs">
            <span class="tag tag-sm">{{ item.sourceRef }}</span>
            <span class="arrow">→</span>
            <span class="tag tag-sm">{{ item.targetRef }}</span>
          </div>
          <div class="history-time">{{ formatTime(item.createdAt) }}</div>
        </div>
        <div v-if="!history.length && !comparing" class="empty-state" style="padding:24px">
          暂无对比记录
        </div>
      </div>
    </div>

    <!-- 右侧：对比表单 / 结果展示 -->
    <div class="compare-main">
      <!-- 新建对比模式 -->
      <div v-if="mode === 'new'" class="compare-new">
        <div class="card compare-form">
          <h3 style="margin-bottom:16px">新建版本对比</h3>
          <p class="form-desc">选择两个分支或 Commit，AI 解读代码改动逻辑</p>
          <div class="grid grid-2">
            <div class="form-group">
              <label class="form-label">源分支 / Commit</label>
              <input v-model="sourceRef" type="text" class="input" placeholder="如: main / abc123" />
            </div>
            <div class="form-group">
              <label class="form-label">目标分支 / Commit</label>
              <input v-model="targetRef" type="text" class="input" placeholder="如: develop / def456" />
            </div>
          </div>
          <button class="btn btn-primary" @click="handleCompare" :disabled="!sourceRef || !targetRef || comparing">
            {{ comparing ? '对比中...' : '开始对比' }}
          </button>
        </div>

        <div v-if="comparing" class="compare-loading">
          <div class="spinner"></div>
          <p>AI 正在分析代码差异...</p>
        </div>
      </div>

      <!-- 查看历史结果模式 -->
      <div v-else-if="result" class="compare-result">
        <div class="card">
          <div class="result-header">
            <div class="result-refs">
              <span class="tag tag-accent">{{ result.sourceRef }}</span>
              <span class="arrow">→</span>
              <span class="tag tag-accent">{{ result.targetRef }}</span>
            </div>
            <span class="result-time">{{ formatTime(result.createdAt) }}</span>
          </div>
          <div class="compare-content" v-html="renderMarkdown(result.analysisResult)"></div>

          <div class="divider"></div>
          <details>
            <summary style="cursor:pointer;font-size:13px;color:var(--color-accent)">
              查看原始 Diff
              <span v-if="isDiffTruncated" style="color:var(--color-warning);font-size:11px">（已截断，仅显示前 {{ diffDisplayLimit }} 字符）</span>
            </summary>
            <pre class="code-block" style="margin-top:12px;max-height:400px;overflow:auto">{{ truncatedDiff }}</pre>
          </details>
        </div>
      </div>

      <!-- 空状态 -->
      <div v-else class="compare-empty empty-state">
        <p>选择左侧的历史记录查看，或点击「新对比」</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { versionApi } from '@/api'
import MarkdownIt from 'markdown-it'

const md = new MarkdownIt({ html: false, breaks: true })
const route = useRoute()
const repoId = Number(route.params.repoId)

const sourceRef = ref('')
const targetRef = ref('')
const comparing = ref(false)
const result = ref<any>(null)
const history = ref<any[]>([])
const selectedId = ref<number | null>(null)
const mode = ref<'new' | 'view'>('view')

/** 前端 diff 显示最大字符数（防止几十万字符渲染卡死浏览器） */
const diffDisplayLimit = 50_000
const isDiffTruncated = computed(() => {
  const raw = result.value?.diffContent || ''
  return raw.length > diffDisplayLimit
})
const truncatedDiff = computed(() => {
  const raw = result.value?.diffContent || ''
  if (raw.length <= diffDisplayLimit) return raw
  return raw.substring(0, diffDisplayLimit) + '\n\n... (原始 diff 共 ' + raw.length.toLocaleString() + ' 字符，已截断，完整内容请通过 Git 命令查看)'
})

function renderMarkdown(text: string) {
  return md.render(text || '')
}

function formatTime(t: string) {
  if (!t) return ''
  return new Date(t).toLocaleString('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit'
  })
}

function selectItem(item: any) {
  selectedId.value = item.id
  result.value = item
  mode.value = 'view'
}

function startNewCompare() {
  mode.value = 'new'
  selectedId.value = null
  result.value = null
  sourceRef.value = ''
  targetRef.value = ''
}

async function handleCompare() {
  if (!sourceRef.value || !targetRef.value) return
  comparing.value = true
  try {
    const res = await versionApi.compare(repoId, sourceRef.value, targetRef.value)
    if (res.data.code === 200) {
      const newItem = res.data.data
      history.value.unshift(newItem)
      selectItem(newItem)
    }
  } catch (e: any) {
    alert('对比失败: ' + (e.response?.data?.message || e.message))
  } finally {
    comparing.value = false
  }
}

async function loadHistory() {
  try {
    const res = await versionApi.getHistory(repoId, 0, 50)
    if (res.data.code === 200) {
      const pageData = Array.isArray(res.data.data)
        ? res.data.data
        : res.data.data?.content || []
      history.value = pageData
      // 默认选中最新的
      if (history.value.length > 0) {
        selectItem(history.value[0])
      }
    }
  } catch { /* ignore */ }
}

onMounted(() => {
  loadHistory()
})
</script>

<style scoped>
.compare-page {
  display: flex;
  height: calc(100vh - 56px);
}
.compare-sidebar {
  width: 260px;
  border-right: 1px solid var(--color-border);
  background: var(--color-bg);
  display: flex;
  flex-direction: column;
}
.sidebar-header {
  padding: var(--space-md) var(--space-lg);
  border-bottom: 1px solid var(--color-border-light);
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.sidebar-header h3 {
  font-size: 14px;
  font-weight: 600;
}
.sidebar-list {
  flex: 1;
  overflow-y: auto;
}
.history-item {
  padding: var(--space-md) var(--space-lg);
  border-bottom: 1px solid var(--color-border-light);
  cursor: pointer;
  transition: background 0.1s;
}
.history-item:hover { background: var(--color-bg-secondary); }
.history-item.active {
  background: var(--color-accent-light);
  border-left: 3px solid var(--color-accent);
}
.history-refs {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
}
.history-refs .arrow {
  color: var(--color-text-tertiary);
  font-size: 11px;
}
.history-time {
  font-size: 11px;
  color: var(--color-text-tertiary);
  margin-top: 4px;
}
.compare-main {
  flex: 1;
  overflow-y: auto;
  padding: var(--space-lg);
}
.compare-new {
  max-width: 640px;
}
.compare-form {
  padding: 24px;
}
.form-desc {
  font-size: 13px;
  color: var(--color-text-tertiary);
  margin-bottom: var(--space-md);
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
.compare-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 0;
  gap: 16px;
  color: var(--color-text-secondary);
}
.compare-result {
  max-width: 900px;
}
.compare-result .card {
  padding: 24px;
}
.result-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--space-md);
  padding-bottom: var(--space-md);
  border-bottom: 1px solid var(--color-border-light);
}
.result-refs {
  display: flex;
  align-items: center;
  gap: 8px;
}
.result-refs .arrow {
  color: var(--color-text-tertiary);
  font-size: 13px;
}
.result-time {
  font-size: 12px;
  color: var(--color-text-tertiary);
}
.compare-content {
  font-size: 14px;
  line-height: 1.9;
}
.compare-content :deep(h1), .compare-content :deep(h2), .compare-content :deep(h3) {
  margin-top: 24px;
  margin-bottom: 10px;
  font-weight: 600;
}
.compare-content :deep(h2) { font-size: 18px; }
.compare-content :deep(h3) { font-size: 16px; }
.compare-content :deep(pre) {
  background: #1a1a2e;
  color: #e8e8e8;
  padding: var(--space-md);
  border-radius: var(--radius-md);
  overflow-x: auto;
  margin: var(--space-sm) 0;
}
.compare-content :deep(code) {
  font-family: var(--font-mono);
  font-size: 13px;
}
.compare-content :deep(ul), .compare-content :deep(ol) {
  padding-left: 20px;
  margin-bottom: 8px;
}
.compare-content :deep(p) {
  margin-bottom: 8px;
}
.compare-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: var(--color-text-tertiary);
}
</style>
