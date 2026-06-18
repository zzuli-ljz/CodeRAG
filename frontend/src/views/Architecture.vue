<template>
  <div class="arch-page">
    <!-- 左侧：历史分析记录 -->
    <div class="arch-sidebar">
      <div class="sidebar-header">
        <h3>分析历史</h3>
        <button class="btn btn-primary btn-sm" @click="handleAnalyze" :disabled="analyzing">
          {{ analyzing ? '分析中...' : '新分析' }}
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
          <div class="history-title">
            第 {{ history.length - idx }} 次分析
          </div>
          <div class="history-time">{{ formatTime(item.createdAt) }}</div>
        </div>
        <div v-if="!history.length && !analyzing" class="empty-state" style="padding:24px">
          暂无分析记录
        </div>
      </div>
    </div>

    <!-- 右侧：分析结果展示 -->
    <div class="arch-main" v-if="result">
      <div class="arch-card card">
        <div class="arch-header-bar">
          <span class="tag tag-success">{{ result.round ? `第 ${result.round} 次` : '分析完成' }}</span>
          <span class="arch-time">{{ formatTime(result.createdAt) }}</span>
        </div>
        <div class="arch-content" v-html="renderMarkdown(result.analysisResult)"></div>
      </div>
    </div>

    <!-- 分析中 -->
    <div v-else-if="analyzing" class="arch-loading">
      <div class="spinner"></div>
      <p>AI 正在分析项目架构...</p>
    </div>

    <!-- 空状态 -->
    <div v-else class="arch-empty empty-state">
      <p>选择左侧的历史记录查看，或点击「新分析」</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { architectureApi } from '@/api'
import MarkdownIt from 'markdown-it'

const md = new MarkdownIt({ html: false, breaks: true })
const route = useRoute()
const repoId = Number(route.params.repoId)

const history = ref<any[]>([])
const selectedId = ref<number | null>(null)
const result = ref<any>(null)
const analyzing = ref(false)

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
}

async function handleAnalyze() {
  analyzing.value = true
  try {
    const res = await architectureApi.analyze(repoId, true)
    if (res.data.code === 200) {
      const newItem = res.data.data
      history.value.unshift(newItem)
      selectItem(newItem)
    }
  } catch (e: any) {
    alert('分析失败: ' + (e.response?.data?.message || e.message))
  } finally {
    analyzing.value = false
  }
}

onMounted(async () => {
  try {
    const res = await architectureApi.getHistory(repoId)
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
  } catch {
    // 也尝试加载最新单条
    try {
      const res = await architectureApi.getLatest(repoId)
      if (res.data.code === 200 && res.data.data) {
        history.value = [res.data.data]
        selectItem(res.data.data)
      }
    } catch {}
  }
})
</script>

<style scoped>
.arch-page {
  display: flex;
  height: calc(100vh - 56px);
}
.arch-sidebar {
  width: 240px;
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
.history-title {
  font-size: 13px;
  font-weight: 500;
}
.history-time {
  font-size: 11px;
  color: var(--color-text-tertiary);
  margin-top: 2px;
}
.arch-main {
  flex: 1;
  overflow-y: auto;
  padding: var(--space-lg);
}
.arch-card {
  padding: 24px;
}
.arch-header-bar {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  margin-bottom: var(--space-md);
}
.arch-time {
  font-size: 12px;
  color: var(--color-text-tertiary);
}
.arch-content {
  font-size: 14px;
  line-height: 1.9;
}
.arch-content :deep(h1), .arch-content :deep(h2), .arch-content :deep(h3) {
  margin-top: 24px;
  margin-bottom: 10px;
  font-weight: 600;
}
.arch-content :deep(h2) { font-size: 18px; }
.arch-content :deep(h3) { font-size: 16px; }
.arch-content :deep(pre) {
  background: #1a1a2e;
  color: #e8e8e8;
  padding: var(--space-md);
  border-radius: var(--radius-md);
  overflow-x: auto;
  margin: var(--space-sm) 0;
}
.arch-content :deep(code) {
  font-family: var(--font-mono);
  font-size: 13px;
}
.arch-content :deep(ul), .arch-content :deep(ol) {
  padding-left: 20px;
  margin-bottom: 8px;
}
.arch-content :deep(p) {
  margin-bottom: 8px;
}
.arch-loading {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
  color: var(--color-text-secondary);
}
.arch-empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-text-tertiary);
}
</style>
