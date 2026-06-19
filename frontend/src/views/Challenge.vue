<template>
  <div class="page-container">
    <div class="page-header">
      <h1 class="page-title">编程挑战</h1>
      <button class="btn btn-primary" @click="handleGenerate" :disabled="generating">
        {{ generating ? 'AI 生成中...' : '生成新挑战' }}
      </button>
    </div>

    <div v-if="loading" class="loading"><div class="spinner"></div> 加载中...</div>

    <template v-else-if="challenges.length">
      <div v-for="c in challenges" :key="c.id" class="card challenge-card">
        <div class="challenge-header">
          <div>
            <span class="tag tag-accent">{{ c.language || '未知' }}</span>
            <span class="tag" :class="difficultyClass(c.difficulty)">{{ difficultyLabel(c.difficulty) }}</span>
            <span v-if="c.filePath" class="tag tag-outline">{{ c.filePath }}</span>
          </div>
          <button class="btn btn-sm btn-secondary" @click="toggleExpand(c.id)">
            {{ expandedIds.has(c.id) ? '收起' : '查看详情' }}
          </button>
        </div>

        <div v-if="expandedIds.has(c.id)">
          <div class="challenge-desc" v-html="renderMarkdown(c.challengeDescription)"></div>

          <div class="code-template">
            <div class="template-label">📋 代码模板（请补全实现）</div>
            <pre><code>{{ c.codeTemplate }}</code></pre>
          </div>

          <!-- 用户提交区 -->
          <div class="submit-area">
            <textarea
              v-model="answers[c.id]"
              class="input code-input"
              rows="10"
              placeholder="在此编写你的代码..."
            ></textarea>
            <button
              class="btn btn-primary"
              @click="handleSubmit(c.id)"
              :disabled="!answers[c.id] || submittingId === c.id"
            >
              {{ submittingId === c.id ? 'AI 批改中...' : '提交代码' }}
            </button>
          </div>

          <!-- 批改结果 -->
          <div v-if="feedbacks[c.id]" class="feedback" :class="feedbackClass(c.id)">
            <div class="feedback-header">
              <span v-if="scores[c.id] != null" class="score">得分: {{ scores[c.id] }}/100</span>
            </div>
            <div class="feedback-body" v-html="renderMarkdown(feedbacks[c.id])"></div>
          </div>

          <!-- 参考答案 -->
          <div v-if="feedbacks[c.id]" class="reference-code">
            <div class="template-label">📖 参考答案</div>
            <pre><code>{{ c.referenceCode }}</code></pre>
          </div>
        </div>
      </div>

      <div class="pagination" v-if="totalPages > 1">
        <button class="btn btn-sm btn-secondary" :disabled="page <= 0" @click="page--; loadChallenges()">上一页</button>
        <span class="page-info">{{ page + 1 }} / {{ totalPages }}</span>
        <button class="btn btn-sm btn-secondary" :disabled="page >= totalPages - 1" @click="page++; loadChallenges()">下一页</button>
      </div>
    </template>

    <div v-else class="empty-state">暂无编程挑战，点击「生成新挑战」开始</div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { challengeApi } from '@/api'
import MarkdownIt from 'markdown-it'

const md = new MarkdownIt({ html: false, breaks: true })
const route = useRoute()
const repoId = Number(route.params.repoId)

const challenges = ref<any[]>([])
const loading = ref(true)
const generating = ref(false)
const submittingId = ref<number | null>(null)
const page = ref(0)
const totalPages = ref(1)
const expandedIds = ref(new Set<number>())
const answers = reactive<Record<number, string>>({})
const feedbacks = reactive<Record<number, string>>({})
const scores = reactive<Record<number, number>>({})

function renderMarkdown(text: string) { return md.render(text || '') }

function difficultyLabel(d?: string): string {
  switch (d?.toUpperCase()) {
    case 'EASY': return '简单'
    case 'MEDIUM': return '中等'
    case 'HARD': return '困难'
    default: return d || '中等'
  }
}

function difficultyClass(d?: string): string {
  switch (d?.toUpperCase()) {
    case 'EASY': return 'tag-success'
    case 'HARD': return 'tag-error'
    default: return ''
  }
}

function feedbackClass(id: number): string {
  return (scores[id] != null && scores[id] >= 60) ? 'correct' : 'wrong'
}

function toggleExpand(id: number) {
  const s = new Set(expandedIds.value)
  if (s.has(id)) s.delete(id)
  else s.add(id)
  expandedIds.value = s
}

async function loadChallenges() {
  loading.value = true
  try {
    const res = await challengeApi.list(repoId, page.value, 10)
    if (res.data.code === 200) {
      const data = res.data.data
      challenges.value = data.content || data || []
      totalPages.value = data.totalPages || 1
    }
  } catch { /* ignore */ }
  finally { loading.value = false }
}

async function handleGenerate() {
  generating.value = true
  try {
    const res = await challengeApi.generate(repoId)
    if (res.data.code === 200) {
      await loadChallenges()
    }
  } catch (e: any) {
    alert('生成失败: ' + (e.response?.data?.message || e.message))
  } finally {
    generating.value = false
  }
}

async function handleSubmit(challengeId: number) {
  if (!answers[challengeId]) return
  submittingId.value = challengeId
  try {
    const res = await challengeApi.submit(challengeId, answers[challengeId])
    if (res.data.code === 200) {
      const data = res.data.data
      feedbacks[challengeId] = data.feedback
      scores[challengeId] = data.score
    }
  } catch (e: any) {
    alert('提交失败: ' + (e.response?.data?.message || e.message))
  } finally {
    submittingId.value = null
  }
}

onMounted(loadChallenges)
</script>

<style scoped>
.challenge-card {
  margin-bottom: 16px;
  padding: 18px 20px;
}
.challenge-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--space-sm);
}
.challenge-desc {
  margin-top: 14px;
  font-size: 14px;
  line-height: 1.8;
}
.challenge-desc :deep(pre) {
  background: #1a1a2e;
  color: #e8e8e8;
  padding: 10px 14px;
  border-radius: var(--radius-md);
  overflow-x: auto;
  font-size: 13px;
}
.code-template {
  margin-top: 14px;
  background: #1a1a2e;
  border-radius: var(--radius-md);
  padding: 12px 16px;
  overflow-x: auto;
}
.code-template pre {
  margin: 8px 0 0;
  font-family: var(--font-mono);
  font-size: 13px;
  line-height: 1.6;
  color: #e8e8e8;
  white-space: pre-wrap;
}
.template-label {
  font-size: 12px;
  font-weight: 600;
  color: #a0a0b0;
}
.submit-area {
  margin-top: 14px;
}
.code-input {
  font-family: var(--font-mono);
  font-size: 13px;
  line-height: 1.6;
  margin-bottom: 10px;
}
.feedback {
  margin-top: 14px;
  padding: 14px 18px;
  border-radius: var(--radius-md);
  border-left: 4px solid;
}
.feedback.correct {
  background: #f0fdf4;
  border-color: #22c55e;
}
.feedback.wrong {
  background: #fef2f2;
  border-color: #ef4444;
}
.feedback-header {
  margin-bottom: 8px;
}
.score {
  font-weight: 700;
  font-size: 16px;
  font-family: var(--font-mono);
}
.feedback-body {
  font-size: 13px;
  line-height: 1.7;
}
.feedback-body :deep(pre) {
  background: #1a1a2e;
  color: #e8e8e8;
  padding: 8px 12px;
  border-radius: var(--radius-sm);
  overflow-x: auto;
  font-size: 12px;
}
.reference-code {
  margin-top: 14px;
  background: #1a1a2e;
  border-radius: var(--radius-md);
  padding: 12px 16px;
  overflow-x: auto;
}
.reference-code pre {
  margin: 8px 0 0;
  font-family: var(--font-mono);
  font-size: 13px;
  line-height: 1.6;
  color: #e8e8e8;
  white-space: pre-wrap;
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
