<template>
  <div class="page-container">
    <div class="page-header">
      <h1 class="page-title">导入仓库</h1>
      <p class="page-desc">粘贴 GitHub 或 Gitee 公开仓库链接，系统将自动解析、分块、向量化入库</p>
    </div>

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
import { ref, computed } from 'vue'
import { repoApi } from '@/api'
import { useRouter } from 'vue-router'

const router = useRouter()
const repoUrl = ref('')
const branch = ref('')
const importing = ref(false)
const task = ref<any>(null)
let pollTimer: any = null

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
.import-card {
  max-width: 600px;
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
</style>
