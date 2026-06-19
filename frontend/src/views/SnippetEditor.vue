<template>
  <div class="editor-page">
    <!-- 顶部工具栏 -->
    <div class="editor-toolbar">
      <button class="btn btn-secondary btn-sm" @click="goBack">← 返回收藏夹</button>
      <h2 class="editor-title">{{ snippet?.title || '编辑笔记' }}</h2>
      <div class="toolbar-actions">
        <span v-if="saved" class="save-indicator">✅ 已保存</span>
        <span v-if="dirty" class="dirty-indicator">● 未保存</span>
        <button class="btn btn-primary btn-sm" @click="handleSave" :disabled="saving">
          {{ saving ? '保存中...' : '保存笔记' }}
        </button>
      </div>
    </div>

    <div v-if="loading" class="loading"><div class="spinner"></div> 加载中...</div>

    <template v-else-if="snippet">
      <!-- 三栏布局 -->
      <div class="editor-split">
        <!-- 左栏：代码 -->
        <div class="editor-pane code-pane">
          <div class="pane-header">
            <span class="pane-title">📄 代码</span>
            <span class="tag tag-accent">{{ snippet.language || '未知' }}</span>
            <span v-if="snippet.filePath" class="tag">{{ snippet.filePath }}</span>
          </div>
          <div class="code-container">
            <pre><code>{{ snippet.content }}</code></pre>
          </div>
        </div>

        <!-- 中栏：笔记编辑器 -->
        <div class="editor-pane note-pane">
          <div class="pane-header">
            <span class="pane-title">📝 笔记</span>
            <span v-if="currentVersion" class="tag tag-outline">v{{ currentVersion.versionNumber }}</span>
          </div>
          <!-- 富文本工具栏 -->
          <div class="rich-toolbar">
            <select class="toolbar-select" @change="execCmd('fontName', ($event.target as HTMLSelectElement).value)" title="字体">
              <option value="">字体</option>
              <option value="Arial">Arial</option>
              <option value="Times New Roman">Times New Roman</option>
              <option value="Courier New">Courier New</option>
              <option value="Georgia">Georgia</option>
              <option value="Verdana">Verdana</option>
              <option value="微软雅黑">微软雅黑</option>
              <option value="宋体">宋体</option>
              <option value="黑体">黑体</option>
            </select>
            <select class="toolbar-select toolbar-select-sm" @change="execCmd('fontSize', ($event.target as HTMLSelectElement).value)" title="字号">
              <option value="">字号</option>
              <option value="1">极小</option>
              <option value="2">小</option>
              <option value="3">正常</option>
              <option value="4">中</option>
              <option value="5">大</option>
              <option value="6">很大</option>
              <option value="7">极大</option>
            </select>
            <span class="toolbar-sep"></span>
            <button class="toolbar-btn" title="格式刷（复制格式）" @click="copyFormat" :class="{ active: formatBrushActive }">🖌</button>
            <button class="toolbar-btn" title="应用格式" @click="applyFormat" :disabled="!copiedFormat">📋</button>
            <span class="toolbar-sep"></span>
            <button class="toolbar-btn" title="清除格式" @click="execCmd('removeFormat')">✕</button>
            <span class="toolbar-sep"></span>
            <button class="toolbar-btn" title="加粗" @click="execCmd('bold')"><b>B</b></button>
            <button class="toolbar-btn" title="斜体" @click="execCmd('italic')"><i>I</i></button>
            <button class="toolbar-btn" title="下划线" @click="execCmd('underline')"><u>U</u></button>
            <button class="toolbar-btn" title="删除线" @click="execCmd('strikeThrough')"><s>S</s></button>
            <span class="toolbar-sep"></span>
            <input type="color" class="toolbar-color" @input="execCmd('foreColor', ($event.target as HTMLInputElement).value)" title="文字颜色" value="#000000" />
            <input type="color" class="toolbar-color" @input="execCmd('hiliteColor', ($event.target as HTMLInputElement).value)" title="背景色" value="#ffff00" />
            <span class="toolbar-sep"></span>
            <button class="toolbar-btn" title="左对齐" @click="execCmd('justifyLeft')">⫷</button>
            <button class="toolbar-btn" title="居中" @click="execCmd('justifyCenter')">≣</button>
            <button class="toolbar-btn" title="右对齐" @click="execCmd('justifyRight')">⫸</button>
            <button class="toolbar-btn" title="两端对齐" @click="execCmd('justifyFull')">☰</button>
            <span class="toolbar-sep"></span>
            <button class="toolbar-btn" title="无序列表" @click="execCmd('insertUnorderedList')">•≡</button>
            <button class="toolbar-btn" title="有序列表" @click="execCmd('insertOrderedList')">1.</button>
            <span class="toolbar-sep"></span>
            <button class="toolbar-btn" title="减少缩进" @click="execCmd('outdent')">↤</button>
            <button class="toolbar-btn" title="增加缩进" @click="execCmd('indent')">↦</button>
            <span class="toolbar-sep"></span>
            <select class="toolbar-select" @change="execCmd('formatBlock', ($event.target as HTMLSelectElement).value)" title="段落格式">
              <option value="p">正文</option>
              <option value="h1">标题1</option>
              <option value="h2">标题2</option>
              <option value="h3">标题3</option>
              <option value="h4">标题4</option>
              <option value="pre">代码块</option>
              <option value="blockquote">引用</option>
            </select>
            <span class="toolbar-sep"></span>
            <button class="toolbar-btn" title="撤销" @click="execCmd('undo')">↩</button>
            <button class="toolbar-btn" title="重做" @click="execCmd('redo')">↪</button>
          </div>
          <div
            ref="editorRef"
            class="rich-editor"
            contenteditable="true"
            @input="onEditorInput"
            @paste="onPaste"
            @keydown="onKeyDown"
            placeholder="在这里记录你的学习笔记..."
          ></div>
        </div>

        <!-- 右栏：版本历史列表 -->
        <div class="editor-pane version-pane">
          <div class="pane-header">
            <span class="pane-title">🕐 版本历史</span>
            <span class="tag tag-sm">{{ versions.length }} 个版本</span>
          </div>
          <div class="version-list">
            <!-- 当前编辑版本（未保存） -->
            <div v-if="dirty" class="version-item version-item-dirty">
              <div class="version-header">
                <span class="version-num">未保存</span>
                <span class="version-time">当前编辑</span>
              </div>
              <div class="version-summary">{{ getEditorSummary() }}</div>
            </div>

            <div v-if="versions.length === 0 && !dirty" class="empty-versions">
              暂无保存的版本
            </div>

            <div
              v-for="v in versions"
              :key="v.id"
              class="version-item"
              :class="{ 'version-item-active': viewingVersionId === v.id }"
              @click="viewVersion(v)"
            >
              <div class="version-header">
                <span class="version-num">v{{ v.versionNumber }}</span>
                <span v-if="v.versionLabel" class="version-label">{{ v.versionLabel }}</span>
                <span class="version-time">{{ formatTime(v.createdAt) }}</span>
              </div>
              <div class="version-summary">{{ v.summary || '(空笔记)' }}</div>
              <div class="version-actions">
                <button
                  v-if="viewingVersionId === v.id"
                  class="btn btn-xs btn-primary"
                  @click.stop="restoreToEditor(v)"
                  title="恢复到编辑器"
                >📝 恢复编辑</button>
                <button
                  class="btn btn-xs btn-secondary"
                  @click.stop="handleRollback(v)"
                  title="回滚到此版本"
                >↩ 回滚</button>
                <button
                  class="btn btn-xs btn-danger"
                  @click.stop="handleDeleteVersion(v)"
                  title="删除此版本"
                >✕</button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </template>

    <div v-else class="empty-state">片段不存在或无权访问</div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { snippetApi, noteVersionApi } from '@/api'

const route = useRoute()
const router = useRouter()
const snippetId = Number(route.params.id)

const snippet = ref<any>(null)
const loading = ref(true)
const saving = ref(false)
const saved = ref(false)
const dirty = ref(false)
const editorRef = ref<HTMLDivElement | null>(null)

// 版本历史
const versions = ref<any[]>([])
const currentVersion = ref<any>(null) // 当前正在编辑的版本（最新版本）
const viewingVersionId = ref<number | null>(null) // 正在查看的版本 ID

// 格式刷相关
const formatBrushActive = ref(false)
const copiedFormat = ref<{ html: string; tagName: string } | null>(null)

function goBack() {
  router.push({ name: 'Snippets' })
}

function formatTime(dateStr: string): string {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  const now = new Date()
  const diff = now.getTime() - d.getTime()
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return Math.floor(diff / 60000) + ' 分钟前'
  if (diff < 86400000) return Math.floor(diff / 3600000) + ' 小时前'
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  const hour = String(d.getHours()).padStart(2, '0')
  const min = String(d.getMinutes()).padStart(2, '0')
  return `${month}-${day} ${hour}:${min}`
}

function getEditorSummary(): string {
  if (!editorRef.value) return '(空)'
  const html = editorRef.value.innerHTML
  if (!html || html === '<br>' || html === '<div><br></div>') return '(空)'
  const plain = html.replace(/<[^>]+>/g, '').replace(/\s+/g, ' ').trim()
  return plain.length > 50 ? plain.substring(0, 50) + '...' : plain
}

async function loadSnippet() {
  loading.value = true
  try {
    const res = await snippetApi.getById(snippetId)
    if (res.data.code === 200) {
      snippet.value = res.data.data
      await nextTick()
      if (editorRef.value) {
        if (snippet.value.note) {
          editorRef.value.innerHTML = snippet.value.note
        } else {
          editorRef.value.innerHTML = ''
        }
      }
    }
  } catch {
    snippet.value = null
  } finally {
    loading.value = false
    dirty.value = false
    saved.value = false
  }
}

async function loadVersions() {
  try {
    const res = await noteVersionApi.getVersions(snippetId)
    if (res.data.code === 200) {
      versions.value = res.data.data || []
      if (versions.value.length > 0) {
        currentVersion.value = versions.value[0] // 最新版本
      }
    }
  } catch { /* ignore */ }
}

function execCmd(command: string, value?: string) {
  document.execCommand(command, false, value)
  editorRef.value?.focus()
  onEditorInput()
}

function copyFormat() {
  const sel = window.getSelection()
  if (!sel || sel.rangeCount === 0 || !editorRef.value) return
  const range = sel.getRangeAt(0)
  if (range.collapsed) {
    let node: Node | null = range.startContainer
    if (node.nodeType === 3) node = node.parentElement
    const el = node as HTMLElement | null
    if (el && el !== editorRef.value) {
      copiedFormat.value = { html: '', tagName: el.tagName }
      formatBrushActive.value = true
    }
  } else {
    const fragment = range.cloneContents()
    const div = document.createElement('div')
    div.appendChild(fragment)
    copiedFormat.value = { html: div.innerHTML, tagName: '' }
    formatBrushActive.value = true
  }
}

function applyFormat() {
  if (!copiedFormat.value || !editorRef.value) return
  const sel = window.getSelection()
  if (!sel || sel.rangeCount === 0) return
  const range = sel.getRangeAt(0)
  if (range.collapsed) return
  const fragment = range.extractContents()
  const span = document.createElement('span')
  if (copiedFormat.value.tagName && copiedFormat.value.tagName !== 'DIV') {
    const wrapper = document.createElement(copiedFormat.value.tagName.toLowerCase())
    wrapper.appendChild(fragment)
    range.insertNode(wrapper)
  } else {
    span.innerHTML = copiedFormat.value.html || fragment.textContent || ''
    if (!span.innerHTML) span.appendChild(fragment)
    range.insertNode(span)
  }
  formatBrushActive.value = false
  copiedFormat.value = null
  onEditorInput()
}

function onPaste(e: ClipboardEvent) {
  e.preventDefault()
  const text = e.clipboardData?.getData('text/plain') || ''
  document.execCommand('insertText', false, text)
}

function onEditorInput() {
  saved.value = false
  dirty.value = true
  viewingVersionId.value = null
}

async function handleSave() {
  if (!editorRef.value) return
  saving.value = true
  try {
    const noteHtml = editorRef.value.innerHTML
    const cleanedNote = noteHtml === '<br>' || noteHtml === '<div><br></div>' || noteHtml.trim() === '' ? '' : noteHtml
    const res = await noteVersionApi.save(snippetId, cleanedNote || undefined, snippet.value?.tags || undefined, undefined)
    if (res.data.code === 200) {
      snippet.value.note = cleanedNote
      saved.value = true
      dirty.value = false
      // 刷新版本列表
      await loadVersions()
    }
  } catch (e: any) {
    alert('保存失败: ' + (e.response?.data?.message || e.message))
  } finally {
    saving.value = false
  }
}

// 查看某个版本（只读预览）
function viewVersion(v: any) {
  viewingVersionId.value = v.id
  if (editorRef.value) {
    editorRef.value.innerHTML = v.note || ''
    editorRef.value.setAttribute('contenteditable', 'false')
  }
}

// 恢复到编辑器（可编辑状态）
function restoreToEditor(v: any) {
  viewingVersionId.value = null
  if (editorRef.value) {
    editorRef.value.setAttribute('contenteditable', 'true')
    // 保持当前内容不变，标记为 dirty
    onEditorInput()
  }
}

// 回滚到指定版本
async function handleRollback(v: any) {
  if (!confirm(`确认回滚到 v${v.versionNumber}？当前未保存的编辑将丢失。`)) return
  try {
    const res = await noteVersionApi.rollback(v.id)
    if (res.data.code === 200) {
      if (editorRef.value) {
        editorRef.value.innerHTML = v.note || ''
        editorRef.value.setAttribute('contenteditable', 'true')
      }
      snippet.value.note = v.note
      viewingVersionId.value = null
      dirty.value = false
      saved.value = true
      await loadVersions()
    }
  } catch (e: any) {
    alert('回滚失败: ' + (e.response?.data?.message || e.message))
  }
}

// 删除版本
async function handleDeleteVersion(v: any) {
  if (!confirm(`确认删除 v${v.versionNumber}？此操作不可撤销。`)) return
  try {
    await noteVersionApi.deleteVersion(v.id)
    if (viewingVersionId.value === v.id) {
      viewingVersionId.value = null
      if (editorRef.value) {
        editorRef.value.setAttribute('contenteditable', 'true')
      }
    }
    await loadVersions()
  } catch (e: any) {
    alert('删除失败: ' + (e.response?.data?.message || e.message))
  }
}

function onKeyDown(e: KeyboardEvent) {
  if ((e.ctrlKey || e.metaKey) && e.key === 's') {
    e.preventDefault()
    handleSave()
  }
}

onMounted(async () => {
  await Promise.all([loadSnippet(), loadVersions()])
})

onUnmounted(() => {
  // 清理
})
</script>

<style scoped>
.editor-page {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 56px);
}

.editor-toolbar {
  display: flex;
  align-items: center;
  gap: var(--space-md);
  padding: 10px var(--space-lg);
  border-bottom: 1px solid var(--color-border);
  background: var(--color-bg);
  flex-shrink: 0;
}
.editor-title {
  flex: 1;
  font-size: 15px;
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.toolbar-actions {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}
.save-indicator {
  font-size: 12px;
  color: #22c55e;
}
.dirty-indicator {
  font-size: 12px;
  color: var(--color-warning);
  animation: pulse 1.5s infinite;
}
@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}

/* 三栏布局 */
.editor-split {
  display: flex;
  flex: 1;
  overflow: hidden;
}
.editor-pane {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.code-pane {
  flex: 1;
  border-right: 1px solid var(--color-border);
}
.note-pane {
  flex: 1;
  border-right: 1px solid var(--color-border);
}
.version-pane {
  width: 240px;
  flex-shrink: 0;
}

/* 面板头部 */
.pane-header {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  padding: 8px 14px;
  border-bottom: 1px solid var(--color-border-light);
  background: var(--color-bg-secondary);
  flex-shrink: 0;
  flex-wrap: wrap;
}
.pane-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-secondary);
}

/* 代码区域 */
.code-container {
  flex: 1;
  overflow: auto;
  background: #1a1a2e;
  padding: 16px;
}
.code-container pre {
  margin: 0;
  font-family: var(--font-mono);
  font-size: 13px;
  line-height: 1.6;
  color: #e8e8e8;
  white-space: pre-wrap;
  word-break: break-all;
}

/* ========== 富文本工具栏 ========== */
.rich-toolbar {
  display: flex;
  align-items: center;
  gap: 3px;
  padding: 6px 10px;
  border-bottom: 1px solid var(--color-border-light);
  background: #fafbfc;
  flex-shrink: 0;
  flex-wrap: wrap;
}
.toolbar-btn {
  width: 30px;
  height: 28px;
  border: 1px solid transparent;
  border-radius: 4px;
  background: transparent;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: #555;
  transition: all 0.15s;
  flex-shrink: 0;
}
.toolbar-btn:hover {
  background: #e8ecf1;
  border-color: #ccd5de;
}
.toolbar-btn.active {
  background: var(--color-accent-light);
  border-color: var(--color-accent);
  color: var(--color-accent);
}
.toolbar-btn:disabled {
  opacity: 0.35;
  cursor: not-allowed;
}
.toolbar-sep {
  width: 1px;
  height: 22px;
  background: #dde1e6;
  margin: 0 3px;
  flex-shrink: 0;
}
.toolbar-select {
  font-size: 12px;
  padding: 3px 6px;
  border: 1px solid #dde1e6;
  border-radius: 4px;
  background: #fff;
  color: #555;
  cursor: pointer;
  height: 28px;
  outline: none;
}
.toolbar-select:focus {
  border-color: var(--color-accent);
}
.toolbar-select-sm {
  width: 56px;
}
.toolbar-color {
  width: 24px;
  height: 24px;
  border: 1px solid #dde1e6;
  border-radius: 3px;
  cursor: pointer;
  padding: 1px;
  background: #fff;
  flex-shrink: 0;
}

/* 富文本编辑器 */
.rich-editor {
  flex: 1;
  overflow-y: auto;
  padding: 16px 20px;
  font-size: 14px;
  line-height: 1.8;
  color: var(--color-text-primary);
  outline: none;
  min-height: 200px;
}
.rich-editor:empty::before {
  content: attr(placeholder);
  color: var(--color-text-tertiary);
  pointer-events: none;
}
.rich-editor[contenteditable="false"] {
  background: #f9fafb;
  cursor: default;
}
/* 编辑器内的样式 */
.rich-editor :deep(h1) {
  font-size: 22px;
  font-weight: 700;
  margin: 20px 0 10px;
  padding-bottom: 8px;
  border-bottom: 2px solid var(--color-border);
}
.rich-editor :deep(h2) {
  font-size: 18px;
  font-weight: 700;
  margin: 16px 0 8px;
  padding-bottom: 6px;
  border-bottom: 1px solid var(--color-border-light);
}
.rich-editor :deep(h3) {
  font-size: 16px;
  font-weight: 600;
  margin: 14px 0 6px;
}
.rich-editor :deep(h4) {
  font-size: 14px;
  font-weight: 600;
  margin: 12px 0 4px;
}
.rich-editor :deep(p) {
  margin: 6px 0;
}
.rich-editor :deep(ul), .rich-editor :deep(ol) {
  padding-left: 24px;
  margin: 8px 0;
}
.rich-editor :deep(li) {
  margin: 4px 0;
}
.rich-editor :deep(pre) {
  background: #1a1a2e;
  color: #e8e8e8;
  padding: 12px 16px;
  border-radius: 6px;
  font-family: var(--font-mono);
  font-size: 13px;
  line-height: 1.5;
  overflow-x: auto;
  margin: 8px 0;
}
.rich-editor :deep(code) {
  background: var(--color-bg-secondary);
  padding: 2px 6px;
  border-radius: 3px;
  font-family: var(--font-mono);
  font-size: 13px;
}
.rich-editor :deep(blockquote) {
  border-left: 3px solid var(--color-accent);
  padding: 4px 12px;
  margin: 8px 0;
  color: var(--color-text-secondary);
  background: var(--color-bg-secondary);
  border-radius: 0 4px 4px 0;
}
.rich-editor :deep(a) {
  color: var(--color-accent);
  text-decoration: underline;
}
.rich-editor :deep(table) {
  border-collapse: collapse;
  width: 100%;
  margin: 8px 0;
}
.rich-editor :deep(td), .rich-editor :deep(th) {
  border: 1px solid var(--color-border);
  padding: 6px 10px;
  font-size: 13px;
}
.rich-editor :deep(th) {
  background: var(--color-bg-secondary);
  font-weight: 600;
}
.rich-editor :deep(img) {
  max-width: 100%;
  border-radius: 4px;
}

/* ========== 版本历史列表 ========== */
.version-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}
.version-item {
  padding: 10px 12px;
  border-radius: var(--radius-md);
  border: 1px solid var(--color-border-light);
  margin-bottom: 8px;
  cursor: pointer;
  transition: all 0.15s;
}
.version-item:hover {
  background: var(--color-bg-secondary);
  border-color: var(--color-border);
}
.version-item-active {
  background: var(--color-accent-light) !important;
  border-color: var(--color-accent) !important;
}
.version-item-dirty {
  border-style: dashed;
  border-color: var(--color-warning);
  background: #fffdf5;
}
.version-header {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 4px;
  flex-wrap: wrap;
}
.version-num {
  font-size: 12px;
  font-weight: 700;
  color: var(--color-accent);
  font-family: var(--font-mono);
}
.version-label {
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 3px;
  background: var(--color-bg-secondary);
  color: var(--color-text-secondary);
}
.version-time {
  font-size: 11px;
  color: var(--color-text-tertiary);
  margin-left: auto;
}
.version-summary {
  font-size: 12px;
  color: var(--color-text-secondary);
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.version-actions {
  display: flex;
  gap: 4px;
  margin-top: 6px;
}
.btn-xs {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 3px;
}
.empty-versions {
  text-align: center;
  padding: 24px 12px;
  font-size: 13px;
  color: var(--color-text-tertiary);
}
</style>
