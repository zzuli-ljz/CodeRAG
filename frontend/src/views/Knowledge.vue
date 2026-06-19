<template>
  <div class="knowledge-page">
    <!-- 左侧：文件列表 -->
    <div class="knowledge-sidebar">
      <div class="sidebar-header">
        <h3>知识库文件</h3>
        <span class="tag tag-accent">{{ overview?.totalChunks || 0 }} 个片段</span>
      </div>

      <!-- 语言筛选 -->
      <div class="filter-section" v-if="languages.length > 1">
        <select v-model="filterLanguage" @change="onFilterChange" class="input filter-select">
          <option value="">全部语言</option>
          <option v-for="lang in languages" :key="lang" :value="lang">{{ lang }}</option>
        </select>
      </div>

      <!-- 文件树 -->
      <div class="file-tree" v-if="overview?.fileGroups?.length">
        <div
          v-for="file in overview.fileGroups"
          :key="file.filePath"
          class="file-node"
          :class="{ active: selectedFile === file.filePath }"
          @click="selectFile(file.filePath)"
        >
          <div class="file-icon">📄</div>
          <div class="file-info">
            <div class="file-name">{{ getFileName(file.filePath) }}</div>
            <div class="file-path-text">{{ file.filePath }}</div>
            <div class="file-meta">
              <span class="tag">{{ file.chunkCount }} 片段</span>
            </div>
          </div>
        </div>
      </div>
      <div v-else-if="!loading" class="empty-state" style="padding:24px">
        暂无向量化数据
      </div>
    </div>

    <!-- 右侧：片段内容 -->
    <div class="knowledge-main">
      <div v-if="loading" class="loading"><div class="spinner"></div> 加载中...</div>

      <template v-else-if="chunks.length">
        <!-- 概览统计 -->
        <div class="overview-bar" v-if="!selectedFile">
          <div class="stat-card">
            <div class="stat-num">{{ overview?.totalChunks || 0 }}</div>
            <div class="stat-label">向量化片段</div>
          </div>
          <div class="stat-card">
            <div class="stat-num">{{ overview?.totalFiles || 0 }}</div>
            <div class="stat-label">文件数</div>
          </div>
          <div class="stat-card" v-for="(count, lang) in overview?.languageStats || {}" :key="lang">
            <div class="stat-num">{{ count }}</div>
            <div class="stat-label">{{ lang }}</div>
          </div>
        </div>

        <!-- 片段列表 -->
        <div v-for="chunk in chunks" :key="chunk.id" class="chunk-card card">
          <div class="chunk-header">
            <div class="chunk-meta">
              <span class="tag tag-accent">{{ chunk.language || '未知' }}</span>
              <span class="tag">{{ chunk.filePath }}</span>
              <span class="tag">行 {{ chunk.startLine }} - {{ chunk.endLine }}</span>
            </div>
            <button class="btn btn-secondary btn-sm" @click="toggleExpand(chunk.id)">
              {{ expandedChunks.has(chunk.id) ? '收起' : '展开' }}
            </button>
          </div>
          <div v-if="chunk.summary" class="chunk-summary">
            <strong>摘要：</strong>{{ chunk.summary }}
          </div>
          <div v-if="expandedChunks.has(chunk.id)" class="chunk-content">
            <pre><code>{{ chunk.content }}</code></pre>
          </div>
          <div v-else class="chunk-preview" @click="toggleExpand(chunk.id)">
            <pre><code>{{ truncateContent(chunk.content) }}</code></pre>
            <div class="preview-mask" v-if="chunk.content && chunk.content.length > 300">点击展开完整代码...</div>
          </div>
        </div>

        <!-- 分页 -->
        <div class="pagination" v-if="totalPages > 1">
          <button class="btn btn-secondary btn-sm" :disabled="currentPage <= 0" @click="goPage(currentPage - 1)">上一页</button>
          <span class="page-info">{{ currentPage + 1 }} / {{ totalPages }}</span>
          <button class="btn btn-secondary btn-sm" :disabled="currentPage >= totalPages - 1" @click="goPage(currentPage + 1)">下一页</button>
        </div>
      </template>

      <div v-else class="empty-state">
        <div>暂无代码片段数据</div>
        <div style="margin-top:8px;font-size:12px">请先导入仓库并完成代码解析</div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { knowledgeApi } from '@/api'

const route = useRoute()
const repoId = Number(route.params.repoId)

const loading = ref(true)
const overview = ref<any>(null)
const chunks = ref<any[]>([])
const languages = ref<string[]>([])
const selectedFile = ref('')
const filterLanguage = ref('')
const expandedChunks = ref<Set<number>>(new Set())
const currentPage = ref(0)
const totalPages = ref(1)

function getFileName(path: string): string {
  if (!path) return '未知'
  const parts = path.split('/')
  return parts[parts.length - 1] || path
}

function truncateContent(content: string): string {
  if (!content) return ''
  const lines = content.split('\n')
  if (lines.length <= 15) return content
  return lines.slice(0, 15).join('\n') + '\n// ... 点击展开查看完整代码'
}

function toggleExpand(chunkId: number) {
  if (expandedChunks.value.has(chunkId)) {
    expandedChunks.value.delete(chunkId)
  } else {
    expandedChunks.value.add(chunkId)
  }
}

function onFilterChange() {
  loadChunks()
}

function selectFile(filePath: string) {
  if (selectedFile.value === filePath) {
    selectedFile.value = ''
    filterLanguage.value = ''
    loadChunks()
  } else {
    selectedFile.value = filePath
    loadChunks()
  }
}

async function loadChunks(page = 0) {
  try {
    const res = await knowledgeApi.listChunks(
      repoId,
      page,
      20,
      filterLanguage.value || undefined,
      selectedFile.value || undefined
    )
    if (res.data.code === 200) {
      const data = res.data.data
      chunks.value = data.content || []
      currentPage.value = data.number || 0
      totalPages.value = data.totalPages || 1
    }
  } catch {}
}

function goPage(page: number) {
  loadChunks(page)
}

onMounted(async () => {
  try {
    const [overviewRes, langsRes] = await Promise.all([
      knowledgeApi.getOverview(repoId),
      knowledgeApi.listLanguages(repoId)
    ])
    if (overviewRes.data.code === 200) overview.value = overviewRes.data.data
    if (langsRes.data.code === 200) languages.value = langsRes.data.data || []
  } catch {}
  await loadChunks()
  loading.value = false
})
</script>

<style scoped>
.knowledge-page {
  display: flex;
  height: calc(100vh - 56px);
}
.knowledge-sidebar {
  width: 280px;
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
.filter-section {
  padding: var(--space-sm) var(--space-lg);
  border-bottom: 1px solid var(--color-border-light);
}
.filter-select {
  font-size: 13px;
  padding: 6px 10px;
}
.file-tree {
  flex: 1;
  overflow-y: auto;
}
.file-node {
  padding: 10px var(--space-lg);
  border-bottom: 1px solid var(--color-border-light);
  cursor: pointer;
  transition: background 0.1s;
  display: flex;
  gap: 10px;
  align-items: flex-start;
}
.file-node:hover { background: var(--color-bg-secondary); }
.file-node.active {
  background: var(--color-accent-light);
  border-left: 3px solid var(--color-accent);
}
.file-icon {
  font-size: 16px;
  flex-shrink: 0;
  margin-top: 1px;
}
.file-info { flex: 1; min-width: 0; }
.file-name {
  font-size: 13px;
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.file-path-text {
  font-size: 11px;
  color: var(--color-text-tertiary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  margin-top: 2px;
}
.file-meta {
  margin-top: 4px;
}

.knowledge-main {
  flex: 1;
  overflow-y: auto;
  padding: var(--space-lg);
}

/* 概览统计 */
.overview-bar {
  display: flex;
  gap: var(--space-md);
  margin-bottom: var(--space-lg);
  flex-wrap: wrap;
}
.stat-card {
  background: var(--color-bg);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 12px 20px;
  text-align: center;
  min-width: 80px;
}
.stat-num {
  font-size: 22px;
  font-weight: 700;
  color: var(--color-accent);
  font-family: var(--font-mono);
}
.stat-label {
  font-size: 12px;
  color: var(--color-text-tertiary);
  margin-top: 2px;
}

/* 片段卡片 */
.chunk-card {
  margin-bottom: 16px;
  padding: 16px;
}
.chunk-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 10px;
}
.chunk-meta {
  display: flex;
  gap: var(--space-xs);
  flex-wrap: wrap;
}
.chunk-summary {
  font-size: 13px;
  color: var(--color-text-secondary);
  margin-bottom: 10px;
  padding: 8px 12px;
  background: var(--color-bg-secondary);
  border-radius: var(--radius-sm);
  line-height: 1.6;
}
.chunk-content pre,
.chunk-preview pre {
  background: #1a1a2e;
  color: #e8e8e8;
  padding: var(--space-md);
  border-radius: var(--radius-md);
  font-family: var(--font-mono);
  font-size: 13px;
  line-height: 1.6;
  overflow-x: auto;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 500px;
  overflow-y: auto;
}
.chunk-preview {
  position: relative;
  cursor: pointer;
}
.chunk-preview pre {
  max-height: 260px;
  overflow: hidden;
}
.preview-mask {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 20px 16px 12px;
  background: linear-gradient(transparent, rgba(26,26,46,0.9));
  color: #8b8fa3;
  font-size: 13px;
  text-align: center;
  border-radius: 0 0 var(--radius-md) var(--radius-md);
}

/* 分页 */
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
