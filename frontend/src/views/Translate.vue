<template>
  <div class="translate-page">
    <!-- 左侧：翻译表单 + 历史 -->
    <div class="translate-sidebar">
      <div class="sidebar-header">
        <h3>代码翻译</h3>
      </div>

      <!-- 模式切换 -->
      <div class="mode-tabs">
        <button class="mode-tab" :class="{ active: mode === 'file' }" @click="mode = 'file'">仓库文件</button>
        <button class="mode-tab" :class="{ active: mode === 'snippet' }" @click="mode = 'snippet'">自由片段</button>
      </div>

      <div class="sidebar-form">
        <!-- 文件模式 -->
        <template v-if="mode === 'file'">
          <div class="form-group">
            <label class="form-label">选择文件</label>
            <select v-model="selectedFile" class="input">
              <option value="">-- 请选择文件 --</option>
              <option v-for="f in fileList" :key="f" :value="f">{{ f }}</option>
            </select>
          </div>
        </template>

        <!-- 片段模式 -->
        <template v-else>
          <div class="form-group">
            <label class="form-label">源代码</label>
            <textarea v-model="snippetCode" class="input code-input" rows="10"
              placeholder="粘贴要翻译的代码..."></textarea>
          </div>
          <div class="form-group">
            <label class="form-label">源语言（留空自动检测）</label>
            <select v-model="snippetSourceLang" class="input">
              <option value="">自动检测</option>
              <option v-for="l in languages" :key="l" :value="l">{{ l }}</option>
            </select>
          </div>
        </template>

        <div class="form-group">
          <label class="form-label">目标语言</label>
          <select v-model="targetLang" class="input">
            <option value="">-- 请选择 --</option>
            <option v-for="l in languages" :key="l" :value="l">{{ l }}</option>
          </select>
        </div>

        <button class="btn btn-primary btn-full" @click="handleTranslate" :disabled="translating">
          {{ translating ? '翻译中...' : '开始翻译' }}
        </button>
      </div>

      <!-- 历史记录 -->
      <div class="sidebar-history" v-if="history.length > 0">
        <h4 class="history-title">翻译历史</h4>
        <div
          v-for="(item, idx) in history"
          :key="item.id"
          class="history-item"
          :class="{ active: selectedId === item.id }"
          @click="selectHistory(item)"
        >
          <div class="history-langs">{{ item.sourceLang }} → {{ item.targetLang }}</div>
          <div class="history-file" v-if="item.sourceFilePath">{{ item.sourceFilePath }}</div>
          <div class="history-time">{{ formatTime(item.createdAt) }}</div>
        </div>
      </div>
    </div>

    <!-- 右侧：翻译结果 -->
    <div class="translate-main" v-if="result">
      <div class="result-card card">
        <div class="result-header">
          <span class="tag tag-accent">{{ result.sourceLang }} → {{ result.targetLang }}</span>
          <span class="result-time">{{ formatTime(result.createdAt) }}</span>
        </div>

        <!-- 翻译代码 -->
        <div class="result-section" v-if="result.translatedCode">
          <h3 class="section-title">翻译代码</h3>
          <pre class="code-block"><code>{{ result.translatedCode }}</code></pre>
          <button class="btn btn-secondary btn-sm copy-btn" @click="copyCode(result.translatedCode)">复制代码</button>
        </div>

        <!-- 差异说明 -->
        <div class="result-section" v-if="result.diffNotes">
          <h3 class="section-title">差异说明</h3>
          <div class="diff-content" v-html="renderMarkdown(result.diffNotes)"></div>
        </div>

        <!-- 注意事项 -->
        <div class="result-section" v-if="result.caveats">
          <h3 class="section-title">注意事项</h3>
          <div class="caveats-content" v-html="renderMarkdown(result.caveats)"></div>
        </div>
      </div>
    </div>

    <!-- 翻译中 -->
    <div v-else-if="translating" class="translate-loading">
      <div class="spinner"></div>
      <p>AI 正在翻译代码...</p>
    </div>

    <!-- 空状态 -->
    <div v-else class="translate-empty empty-state">
      <p>选择左侧的文件或粘贴代码片段开始翻译</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { translateApi, repoApi } from '@/api'
import MarkdownIt from 'markdown-it'

const md = new MarkdownIt({ html: false, breaks: true })
const route = useRoute()
const repoId = Number(route.params.repoId)

const mode = ref<'file' | 'snippet'>('file')
const fileList = ref<string[]>([])
const selectedFile = ref('')
const snippetCode = ref('')
const snippetSourceLang = ref('')
const targetLang = ref('')
const translating = ref(false)
const result = ref<any>(null)
const history = ref<any[]>([])
const selectedId = ref<number | null>(null)

const languages = [
  'Java', 'Python', 'JavaScript', 'TypeScript', 'Go', 'Rust', 'Ruby',
  'PHP', 'Swift', 'Kotlin', 'Scala', 'C', 'C++', 'C#', 'Dart', 'Lua',
  'R', 'Shell', 'SQL'
]

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

function selectHistory(item: any) {
  selectedId.value = item.id
  result.value = item
}

async function copyCode(code: string) {
  try {
    await navigator.clipboard.writeText(code)
    alert('已复制到剪贴板')
  } catch {
    alert('复制失败，请手动复制')
  }
}

async function handleTranslate() {
  if (!targetLang.value) {
    alert('请选择目标语言')
    return
  }

  translating.value = true
  try {
    let res: any
    if (mode.value === 'file') {
      if (!selectedFile.value) {
        alert('请选择要翻译的文件')
        translating.value = false
        return
      }
      res = await translateApi.translateFile(repoId, selectedFile.value, targetLang.value)
    } else {
      if (!snippetCode.value.trim()) {
        alert('请输入源代码')
        translating.value = false
        return
      }
      res = await translateApi.translateSnippet(
        snippetCode.value,
        snippetSourceLang.value || '',
        targetLang.value
      )
    }

    if (res.data.code === 200) {
      const item = res.data.data
      result.value = item
      selectedId.value = item.id
      history.value.unshift(item)
    } else {
      alert('翻译失败: ' + (res.data.message || '未知错误'))
    }
  } catch (e: any) {
    alert('翻译失败: ' + (e.response?.data?.message || e.message))
  } finally {
    translating.value = false
  }
}

onMounted(async () => {
  // 加载文件列表
  try {
    const res = await repoApi.getRepoFiles(repoId)
    if (res.data.code === 200 && Array.isArray(res.data.data)) {
      fileList.value = res.data.data
    }
  } catch {}

  // 加载翻译历史
  try {
    const res = await translateApi.getHistory(repoId)
    if (res.data.code === 200) {
      const pageData = Array.isArray(res.data.data)
        ? res.data.data
        : res.data.data?.content || []
      history.value = pageData
    }
  } catch {}
})
</script>

<style scoped>
.translate-page {
  display: flex;
  height: calc(100vh - 56px);
}
.translate-sidebar {
  width: 300px;
  border-right: 1px solid var(--color-border);
  background: var(--color-bg);
  display: flex;
  flex-direction: column;
  overflow-y: auto;
}
.sidebar-header {
  padding: var(--space-md) var(--space-lg);
  border-bottom: 1px solid var(--color-border-light);
}
.sidebar-header h3 {
  font-size: 14px;
  font-weight: 600;
}
.mode-tabs {
  display: flex;
  border-bottom: 1px solid var(--color-border-light);
}
.mode-tab {
  flex: 1;
  padding: 10px 0;
  font-size: 13px;
  font-weight: 500;
  border: none;
  background: none;
  cursor: pointer;
  color: var(--color-text-secondary);
  transition: all 0.15s;
  border-bottom: 2px solid transparent;
}
.mode-tab.active {
  color: var(--color-accent);
  border-bottom-color: var(--color-accent);
}
.mode-tab:hover:not(.active) {
  color: var(--color-text-primary);
}
.sidebar-form {
  padding: var(--space-md) var(--space-lg);
}
.form-group {
  margin-bottom: var(--space-md);
}
.form-label {
  display: block;
  font-size: 12px;
  font-weight: 500;
  color: var(--color-text-secondary);
  margin-bottom: 4px;
}
.code-input {
  font-family: var(--font-mono);
  font-size: 12px;
  min-height: 120px;
}
.btn-full {
  width: 100%;
}
.sidebar-history {
  flex: 1;
  overflow-y: auto;
  border-top: 1px solid var(--color-border-light);
}
.history-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--color-text-tertiary);
  padding: var(--space-md) var(--space-lg) var(--space-xs);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}
.history-item {
  padding: var(--space-sm) var(--space-lg);
  border-bottom: 1px solid var(--color-border-light);
  cursor: pointer;
  transition: background 0.1s;
}
.history-item:hover { background: var(--color-bg-secondary); }
.history-item.active {
  background: var(--color-accent-light);
  border-left: 3px solid var(--color-accent);
}
.history-langs {
  font-size: 12px;
  font-weight: 500;
}
.history-file {
  font-size: 11px;
  color: var(--color-text-tertiary);
  font-family: var(--font-mono);
  margin-top: 2px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.history-time {
  font-size: 10px;
  color: var(--color-text-tertiary);
  margin-top: 2px;
}
.translate-main {
  flex: 1;
  overflow-y: auto;
  padding: var(--space-lg);
}
.result-card {
  padding: 24px;
}
.result-header {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  margin-bottom: var(--space-lg);
}
.result-time {
  font-size: 12px;
  color: var(--color-text-tertiary);
}
.result-section {
  margin-bottom: var(--space-xl);
}
.section-title {
  font-size: 15px;
  font-weight: 600;
  margin-bottom: var(--space-sm);
  color: var(--color-text-primary);
}
.copy-btn {
  margin-top: var(--space-sm);
}
.diff-content,
.caveats-content {
  font-size: 13px;
  line-height: 1.8;
  color: var(--color-text-secondary);
}
.diff-content :deep(p),
.caveats-content :deep(p) {
  margin-bottom: 6px;
}
.diff-content :deep(ul),
.caveats-content :deep(ul) {
  padding-left: 20px;
  margin-bottom: 8px;
}
.diff-content :deep(code),
.caveats-content :deep(code) {
  background: var(--color-bg-secondary);
  padding: 1px 4px;
  border-radius: 3px;
  font-family: var(--font-mono);
  font-size: 12px;
}
.translate-loading {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
  color: var(--color-text-secondary);
}
.translate-empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-text-tertiary);
}
</style>
