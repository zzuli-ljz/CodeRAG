<template>
  <div class="page-container">
    <div class="page-header">
      <h1 class="page-title">代码片段收藏夹</h1>
      <div class="header-actions">
        <button class="btn btn-secondary btn-sm" @click="handleExport" :disabled="exporting">
          {{ exporting ? '导出中...' : '导出 Markdown' }}
        </button>
      </div>
    </div>

    <!-- 搜索 & 筛选 -->
    <div class="card" style="margin-bottom:16px">
      <div class="filter-row">
        <input
          v-model="keyword"
          class="input"
          placeholder="搜索标题、内容、标签..."
          style="flex:1;max-width:320px"
          @input="onSearch"
        />
        <select v-model="filterRepoId" class="input" style="width:180px" @change="loadSnippets">
          <option :value="0">全部仓库</option>
          <option v-for="r in repos" :key="r.id" :value="r.id">{{ r.repoName }}</option>
        </select>
        <button class="btn btn-primary btn-sm" @click="showCreate = true">新建片段</button>
      </div>
    </div>

    <!-- 片段列表 -->
    <div v-if="loading" class="loading"><div class="spinner"></div> 加载中...</div>

    <template v-else-if="snippets.length">
      <div v-for="s in snippets" :key="s.id" class="card snippet-card">
        <div class="snippet-header">
          <div class="snippet-title-row">
            <h3 class="snippet-title">{{ s.title || '未命名片段' }}</h3>
            <span class="tag tag-accent">{{ s.language || '未知' }}</span>
            <span v-if="s.repoName" class="tag tag-outline">{{ s.repoName }}</span>
          </div>
          <div class="snippet-actions">
            <button class="btn btn-sm btn-secondary" @click="toggleExpand(s.id)">
              {{ expandedIds.has(s.id) ? '收起' : '展开' }}
            </button>
            <button class="btn btn-sm btn-primary" @click="openEditor(s)">编辑笔记</button>
            <button class="btn btn-sm btn-danger" @click="handleDelete(s.id)">删除</button>
          </div>
        </div>

        <div v-if="s.filePath" class="snippet-meta">
          <span class="meta-text">{{ s.filePath }}</span>
          <span v-if="s.startLine" class="meta-text">行 {{ s.startLine }}{{ s.endLine ? ' - ' + s.endLine : '' }}</span>
        </div>

        <!-- 标签 -->
        <div v-if="s.tags" class="snippet-tags">
          <span v-for="t in parseTags(s.tags)" :key="t" class="tag tag-sm">{{ t }}</span>
        </div>

        <!-- 代码块（可折叠） -->
        <div v-if="expandedIds.has(s.id)" class="snippet-code">
          <pre><code>{{ s.content }}</code></pre>
        </div>
      </div>

      <!-- 分页 -->
      <div class="pagination" v-if="totalPages > 1">
        <button class="btn btn-sm btn-secondary" :disabled="page <= 0" @click="page--; loadSnippets()">上一页</button>
        <span class="page-info">{{ page + 1 }} / {{ totalPages }}</span>
        <button class="btn btn-sm btn-secondary" :disabled="page >= totalPages - 1" @click="page++; loadSnippets()">下一页</button>
      </div>
    </template>

    <div v-else class="empty-state">暂无收藏的代码片段，点击「新建片段」开始</div>

    <!-- 新建片段弹窗 -->
    <div v-if="showCreate" class="modal-overlay" @click.self="closeModal">
      <div class="modal">
        <h3>新建代码片段</h3>
        <div class="form-group">
          <label>标题</label>
          <input v-model="form.title" class="input" placeholder="片段标题" />
        </div>
        <div class="form-group">
          <label>仓库</label>
          <select v-model="form.repoId" class="input">
            <option :value="0">请选择仓库</option>
            <option v-for="r in repos" :key="r.id" :value="r.id">{{ r.repoName }}</option>
          </select>
        </div>
        <div class="form-group">
          <label>文件路径</label>
          <input v-model="form.filePath" class="input" placeholder="如 src/main.js" />
        </div>
        <div class="form-group">
          <label>编程语言</label>
          <input v-model="form.language" class="input" placeholder="如 Java, Python" />
        </div>
        <div class="form-group">
          <label>代码内容</label>
          <textarea v-model="form.content" class="input" rows="8" placeholder="粘贴代码..."></textarea>
        </div>
        <div class="form-row">
          <div class="form-group" style="flex:1">
            <label>起始行</label>
            <input v-model.number="form.startLine" class="input" type="number" />
          </div>
          <div class="form-group" style="flex:1">
            <label>结束行</label>
            <input v-model.number="form.endLine" class="input" type="number" />
          </div>
        </div>
        <div class="form-group">
          <label>标签（逗号分隔）</label>
          <input v-model="form.tags" class="input" placeholder="如 算法, 排序, 核心代码" />
        </div>
        <div class="form-group">
          <label>笔记</label>
          <textarea v-model="form.note" class="input" rows="4" placeholder="记录你的学习笔记..."></textarea>
        </div>
        <div class="modal-actions">
          <button class="btn btn-secondary" @click="closeModal">取消</button>
          <button class="btn btn-primary" @click="handleSave" :disabled="saving">
            {{ saving ? '保存中...' : '保存' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { snippetApi, repoApi } from '@/api'

const router = useRouter()
const snippets = ref<any[]>([])
const repos = ref<any[]>([])
const loading = ref(true)
const keyword = ref('')
const filterRepoId = ref(0)
const page = ref(0)
const totalPages = ref(1)
const expandedIds = ref(new Set<number>())
const exporting = ref(false)
const saving = ref(false)

// 弹窗
const showCreate = ref(false)
const form = ref({
  repoId: 0,
  filePath: '',
  language: '',
  content: '',
  title: '',
  note: '',
  tags: '',
  startLine: null as number | null,
  endLine: null as number | null
})

let searchTimer: any = null

function parseTags(tags: string): string[] {
  return tags.split(',').map(t => t.trim()).filter(Boolean)
}

function toggleExpand(id: number) {
  const s = new Set(expandedIds.value)
  if (s.has(id)) s.delete(id)
  else s.add(id)
  expandedIds.value = s
}

function onSearch() {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    page.value = 0
    loadSnippets()
  }, 400)
}

async function loadSnippets() {
  loading.value = true
  try {
    const res = await snippetApi.list(
      filterRepoId.value || undefined,
      keyword.value || undefined,
      page.value,
      20
    )
    if (res.data.code === 200) {
      const data = res.data.data
      snippets.value = data.content || data || []
      totalPages.value = data.totalPages || 1
    }
  } catch { /* ignore */ }
  finally { loading.value = false }
}

async function loadRepos() {
  try {
    const res = await repoApi.listRepos(0, 50)
    if (res.data.code === 200) {
      repos.value = (Array.isArray(res.data.data) ? res.data.data : res.data.data?.content) || []
    }
  } catch { /* ignore */ }
}

function openEditor(s: any) {
  router.push({ name: 'SnippetEditor', params: { id: s.id } })
}

function closeModal() {
  showCreate.value = false
  form.value = { repoId: 0, filePath: '', language: '', content: '', title: '', note: '', tags: '', startLine: null, endLine: null }
}

async function handleSave() {
  saving.value = true
  try {
    if (!form.value.repoId || !form.value.content) {
      alert('请填写仓库和代码内容')
      saving.value = false
      return
    }
    await snippetApi.save({
      repoId: form.value.repoId,
      filePath: form.value.filePath || undefined,
      language: form.value.language || undefined,
      content: form.value.content,
      title: form.value.title || undefined,
      note: form.value.note || undefined,
      tags: form.value.tags || undefined,
      startLine: form.value.startLine ?? undefined,
      endLine: form.value.endLine ?? undefined
    })
    closeModal()
    await loadSnippets()
  } catch (e: any) {
    alert('保存失败: ' + (e.response?.data?.message || e.message))
  } finally {
    saving.value = false
  }
}

async function handleDelete(id: number) {
  if (!confirm('确认删除该片段？')) return
  try {
    await snippetApi.delete(id)
    snippets.value = snippets.value.filter(s => s.id !== id)
  } catch (e: any) {
    alert('删除失败: ' + (e.response?.data?.message || e.message))
  }
}

async function handleExport() {
  exporting.value = true
  try {
    const res = await snippetApi.exportMarkdown()
    const blob = new Blob([res.data], { type: 'text/markdown' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = 'code-snippets.md'
    a.click()
    URL.revokeObjectURL(url)
  } catch (e: any) {
    alert('导出失败')
  } finally {
    exporting.value = false
  }
}

onMounted(async () => {
  await Promise.all([loadSnippets(), loadRepos()])
})
</script>

<style scoped>
.filter-row {
  display: flex;
  gap: var(--space-sm);
  align-items: center;
  flex-wrap: wrap;
}
.snippet-card {
  margin-bottom: 12px;
  padding: 16px 20px;
}
.snippet-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: var(--space-md);
}
.snippet-title-row {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  flex-wrap: wrap;
}
.snippet-title {
  font-size: 15px;
  font-weight: 600;
}
.snippet-actions {
  display: flex;
  gap: var(--space-xs);
  flex-shrink: 0;
}
.snippet-meta {
  margin-top: 6px;
  display: flex;
  gap: var(--space-md);
}
.meta-text {
  font-size: 12px;
  color: var(--color-text-tertiary);
  font-family: var(--font-mono);
}
.snippet-tags {
  margin-top: 8px;
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}
.tag-sm {
  font-size: 11px;
  padding: 2px 8px;
}
.snippet-code {
  margin-top: 12px;
  background: #1a1a2e;
  border-radius: var(--radius-md);
  padding: 12px 16px;
  overflow-x: auto;
}
.snippet-code pre {
  margin: 0;
  font-family: var(--font-mono);
  font-size: 13px;
  line-height: 1.6;
  color: #e8e8e8;
  white-space: pre-wrap;
  word-break: break-all;
}
.snippet-note {
  margin-top: 12px;
  padding: 12px 16px;
  background: var(--color-bg-secondary);
  border-radius: var(--radius-md);
  border-left: 3px solid var(--color-accent);
}
.note-label {
  font-size: 12px;
  font-weight: 600;
  margin-bottom: 6px;
  color: var(--color-text-secondary);
}
.note-content {
  font-size: 13px;
  line-height: 1.7;
  color: var(--color-text-primary);
  white-space: pre-wrap;
}

/* 弹窗 */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0,0,0,.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 200;
}
.modal {
  background: var(--color-bg);
  border-radius: var(--radius-lg);
  padding: 24px;
  width: 560px;
  max-width: 90vw;
  max-height: 80vh;
  overflow-y: auto;
}
.modal h3 {
  margin-bottom: 16px;
  font-size: 16px;
}
.form-group {
  margin-bottom: 12px;
}
.form-group label {
  display: block;
  font-size: 13px;
  font-weight: 500;
  margin-bottom: 4px;
  color: var(--color-text-secondary);
}
.form-row {
  display: flex;
  gap: var(--space-sm);
}
.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-sm);
  margin-top: 16px;
}
.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-md);
  margin-top: 16px;
}
.page-info {
  font-size: 13px;
  color: var(--color-text-secondary);
}
</style>
