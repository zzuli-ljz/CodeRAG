<template>
  <div class="quiz-page">
    <!-- 左侧：生成历史 -->
    <div class="quiz-sidebar">
      <div class="sidebar-header">
        <h3>刷题历史</h3>
        <button class="btn btn-primary btn-sm" @click="handleGenerate" :disabled="generating">
          {{ generating ? '生成中...' : '生成新题' }}
        </button>
      </div>
      <div class="sidebar-list">
        <div
          v-for="(batch, idx) in batches"
          :key="'b'+idx"
          class="batch-item"
          :class="{ active: currentBatchIndex === idx }"
          @click="selectBatch(idx)"
        >
          <div class="batch-title">第 {{ idx + 1 }} 次生成</div>
          <div class="batch-meta">{{ batch.count }} 道题 · {{ formatTime(batch.time) }}</div>
        </div>
        <div v-if="!batches.length" class="empty-state" style="padding:24px">
          暂无题目，点击上方按钮生成
        </div>
      </div>
    </div>

    <!-- 右侧：题目内容 -->
    <div class="quiz-main" v-if="quizzes.length">
      <div v-for="(quiz, qIdx) in quizzes" :key="quiz.id" class="quiz-card card">
        <!-- 题目头部 -->
        <div class="quiz-header">
          <span class="tag tag-accent">第 {{ qIdx + 1 }} 题</span>
          <span class="tag" :class="difficultyClass(quiz.difficulty)">{{ difficultyLabel(quiz.difficulty) }}</span>
          <span v-if="quiz.knowledgePoint" class="tag tag-outline">{{ quiz.knowledgePoint }}</span>
        </div>

        <!-- 题目文字 -->
        <div class="quiz-question">{{ quiz.question }}</div>

        <!-- 选择题选项（有 options 字段时显示为按钮组） -->
        <div v-if="quiz.options && parseOptions(quiz.options).length" class="quiz-options">
          <button
            v-for="(opt, oIdx) in parseOptions(quiz.options)"
            :key="oIdx"
            class="option-btn"
            :class="{
              'selected': getAnswer(quiz.id) === opt.key,
              'correct': isSubmitted(quiz.id) && isCorrect(quiz.id) && getAnswer(quiz.id) === opt.key,
              'wrong': isSubmitted(quiz.id) && !isCorrect(quiz.id) && getAnswer(quiz.id) === opt.key && opt.key === quiz.answer,
              'show-correct': isSubmitted(quiz.id) && !isCorrect(quiz.id) && opt.key === quiz.answer,
              'disabled': isSubmitted(quiz.id)
            }"
            @click="selectOption(quiz.id, opt.key)"
            :disabled="isSubmitted(quiz.id)"
          >
            <span class="opt-label">{{ opt.label }}</span>
            <span class="opt-text">{{ opt.text }}</span>
            <span v-if="isSubmitted(quiz.id) && opt.key === quiz.answer" class="opt-icon correct-icon">&#10003;</span>
            <span v-if="isSubmitted(quiz.id) && getAnswer(quiz.id) === opt.key && opt.key !== quiz.answer" class="opt-icon wrong-icon">&#10007;</span>
          </button>
        </div>

        <!-- 简答题输入框（无 options 时） -->
        <div v-else class="quiz-answer-area">
          <textarea
            :value="getAnswer(quiz.id)"
            @input="(e: Event) => setAnswer(quiz.id, (e.target as HTMLTextAreaElement).value)"
            class="input"
            placeholder="请输入你的答案..."
            rows="3"
            :disabled="isSubmitted(quiz.id)"
          ></textarea>
        </div>

        <!-- 提交按钮 -->
        <div v-if="!isSubmitted(quiz.id)" class="quiz-submit-row">
          <button
            class="btn btn-primary btn-sm"
            @click="handleSubmit(quiz)"
            :disabled="!getAnswer(quiz.id) || submittingId === quiz.id"
          >
            {{ submittingId === quiz.id ? '提交中...' : '提交答案' }}
          </button>
        </div>

        <!-- 批改结果 -->
        <div v-if="getFeedback(quiz.id)" class="quiz-feedback" :class="{ correct: isCorrect(quiz.id) }">
          <div class="feedback-title">
            <span>{{ isCorrect(quiz.id) ? '&#9989; 回答正确' : '&#10007; 回答错误' }}</span>
          </div>
          <div class="feedback-body" v-html="renderMarkdown(getFeedback(quiz.id) || '')"></div>
        </div>
      </div>
    </div>

    <!-- 空状态 -->
    <div v-else class="quiz-empty empty-state">
      <div>暂无题目，请在左侧点击「生成新题」开始练习</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { quizApi } from '@/api'
import MarkdownIt from 'markdown-it'

const md = new MarkdownIt({ html: false, breaks: true })
const route = useRoute()
const repoId = Number(route.params.repoId)

// 数据
const batches = ref<any[]>([])
const currentBatchIndex = ref(-1)
const generating = ref(false)
const submittingId = ref<number | null>(null)

// 独立的状态追踪（不依赖 computed，避免每次重新计算覆盖用户选择）
// key: quizId -> value: { userAnswer, submitted, feedback, isCorrect }
const quizState = ref<Map<number, { userAnswer: string; submitted: boolean; feedback: string | null; isCorrect: boolean }>>(new Map())

// 当前显示的题目列表（当前批次）
function getCurrentQuizzes(): any[] {
  if (currentBatchIndex.value < 0) return []
  const batch = batches.value[currentBatchIndex.value]
  return batch ? batch.items : []
}

// 用 computed 但不创建新对象，直接返回原始 items
const quizzes = ref<any[]>([])

function renderMarkdown(text: string) {
  return md.render(text || '')
}

function formatTime(t: string | Date) {
  return new Date(t).toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}

function difficultyLabel(d?: string): string {
  if (!d) return '中等'
  switch (d.toUpperCase()) {
    case 'EASY': return '简单'
    case 'MEDIUM': return '中等'
    case 'HARD': return '困难'
    default: return d
  }
}

function difficultyClass(d?: string): string {
  if (!d) return ''
  switch (d?.toUpperCase()) {
    case 'EASY': return 'tag-success'
    case 'HARD': return 'tag-error'
    default: return ''
  }
}

/** 解析选项字符串 "A. xxx\nB. xxx\nC. xxx\nD. xxx" 或挤在一行的格式 */
function parseOptions(optionsStr: string): { key: string; label: string; text: string }[] {
  if (!optionsStr) return []

  // 策略1：按换行符拆分（标准格式）
  let lines = optionsStr.split('\n').filter(l => l.trim())

  // 策略2：如果拆分后不足2项，说明选项可能挤在一行里，用正则全局匹配 A/B/C/D 前缀
  if (lines.length < 2) {
    const matches = [...optionsStr.matchAll(/(?:^|(?<=[\n；;]))\s*([A-D])[\.\、\s](.+?)(?=\s*[A-D][\.\、\s]|$)/gs)]
    if (matches.length >= 2) {
      return matches.map(m => ({ key: m[1].toUpperCase(), label: m[1].toUpperCase(), text: m[2].trim() }))
    }
  }

  const result: any[] = []
  for (const line of lines) {
    // 正则匹配 "A." / "A、" / "A " 开头的选项
    const match = line.trim().match(/^([A-D])[\.\、\s](.+)$/)
    if (match) {
      result.push({ key: match[1].toUpperCase(), label: match[1].toUpperCase(), text: match[2].trim() })
    }
  }
  return result
}

// --- 状态管理方法 ---

function getState(quizId: number) {
  if (!quizState.value.has(quizId)) {
    quizState.value.set(quizId, { userAnswer: '', submitted: false, feedback: null, isCorrect: false })
  }
  return quizState.value.get(quizId)!
}

function getAnswer(quizId: number): string {
  return getState(quizId).userAnswer
}

function setAnswer(quizId: number, val: string) {
  const s = getState(quizId)
  s.userAnswer = val
}

function selectOption(quizId: number, key: string) {
  setAnswer(quizId, key)
}

function isSubmitted(quizId: number): boolean {
  return getState(quizId).submitted
}

function isCorrect(quizId: number): boolean {
  return getState(quizId).isCorrect
}

function getFeedback(quizId: number): string | null {
  return getState(quizId).feedback
}

async function handleGenerate() {
  generating.value = true
  try {
    const res = await quizApi.generate(repoId)
    if (res.data.code === 200) {
      await loadBatches()
      // 自动选中最新一批
      if (batches.value.length > 0) selectBatch(batches.value.length - 1)
    }
  } catch (e: any) {
    const msg = e.response?.data?.message || e.message || '生成失败'
    alert(msg)
  } finally {
    generating.value = false
  }
}

async function handleSubmit(quiz: any) {
  if (!getAnswer(quiz.id)) return
  submittingId.value = quiz.id
  try {
    const res = await quizApi.submitAnswer(quiz.id, getAnswer(quiz.id))
    if (res.data.code === 200) {
      const data = res.data.data
      const s = getState(quiz.id)
      s.submitted = true
      s.feedback = data.aiFeedback
      s.isCorrect = data.isCorrect
    }
  } catch (e: any) {
    alert('提交失败: ' + (e.response?.data?.message || e.message))
  } finally {
    submittingId.value = null
  }
}

function selectBatch(idx: number) {
  currentBatchIndex.value = idx
  quizzes.value = [...getCurrentQuizzes()]
}

/** 加载所有题目并按时间分批 */
async function loadBatches() {
  try {
    const res = await quizApi.listQuizzes(repoId)
    if (res.data.code === 200) {
      const list: any[] = res.data.data || []
      // 按 createdAt 分批
      const batchList: any[] = []
      let currentBatch: any[] = []
      let lastTime = 0

      for (const q of list) {
        const t = new Date(q.createdAt).getTime()
        if (currentBatch.length > 0 && Math.abs(t - lastTime) > 60000) {
          if (currentBatch.length > 0) {
            batchList.push({
              items: [...currentBatch],
              count: currentBatch.length,
              time: currentBatch[0].createdAt
            })
          }
          currentBatch = []
        }
        currentBatch.push(q)
        lastTime = t
      }
      if (currentBatch.length > 0) {
        batchList.push({
          items: [...currentBatch],
          count: currentBatch.length,
          time: currentBatch[0].createdAt
        })
      }

      batches.value = batchList.reverse()

      // 如果没有选中的批次且已有批次，自动选中第一个
      if (currentBatchIndex.value < 0 && batchList.length > 0) {
        selectBatch(0)
      }
    }
  } catch {}
}

onMounted(async () => {
  await loadBatches()
})
</script>

<style scoped>
.quiz-page {
  display: flex;
  height: calc(100vh - 56px);
}
.quiz-sidebar {
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
.batch-item {
  padding: var(--space-md) var(--space-lg);
  border-bottom: 1px solid var(--color-border-light);
  cursor: pointer;
  transition: background 0.1s;
}
.batch-item:hover { background: var(--color-bg-secondary); }
.batch-item.active {
  background: var(--color-accent-light);
  border-left: 3px solid var(--color-accent);
}
.batch-title {
  font-size: 13px;
  font-weight: 500;
}
.batch-meta {
  font-size: 11px;
  color: var(--color-text-tertiary);
  margin-top: 2px;
}
.quiz-main {
  flex: 1;
  overflow-y: auto;
  padding: var(--space-lg);
}
.quiz-card {
  margin-bottom: 20px;
  padding: 20px;
}
.quiz-header {
  display: flex;
  gap: var(--space-sm);
  margin-bottom: var(--space-md);
  flex-wrap: wrap;
}
.quiz-question {
  font-size: 15px;
  line-height: 1.8;
  margin-bottom: var(--space-md);
  font-weight: 500;
}
/* 选项按钮 */
.quiz-options {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: var(--space-md);
}
.option-btn {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  background: var(--color-bg-secondary);
  border: 2px solid transparent;
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: all 0.15s;
  text-align: left;
  font-family: inherit;
  font-size: 14px;
}
.option-btn:hover:not(.disabled) {
  border-color: var(--color-accent-light);
  background: var(--color-bg);
}
.option-btn.selected {
  border-color: var(--color-accent);
  background: var(--color-accent-light);
}
.option-btn.correct {
  border-color: #22c55e;
  background: #f0fdf4;
}
.option-btn.wrong {
  border-color: #ef4444;
  background: #fef2f2;
}
.option-btn.show-correct:not(.wrong) {
  border-color: #22c55e;
  background: #f0fdf4;
}
.option-btn.disabled {
  cursor: default;
  opacity: 0.85;
}
.opt-label {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--color-border);
  color: var(--color-text);
  font-weight: 600;
  font-size: 13px;
  flex-shrink: 0;
}
.option-btn.selected .opt-label {
  background: var(--color-accent);
  color: white;
}
.option-btn.correct .opt-label { background: #22c55e; color: white; }
.option-btn.wrong .opt-label { background: #ef4444; color: white; }
.option-btn.show-correct:not(.wrong) .opt-label { background: #22c55e; color: white; }
.opt-text { flex: 1; line-height: 1.6; }
.opt-icon {
  font-weight: 700;
  font-size: 16px;
}
.correct-icon { color: #22c55e; }
.wrong-icon { color: #ef4444; }
/* 简答题 */
.quiz-answer-area { margin-bottom: var(--space-md); }
.quiz-submit-row {
  margin-top: 8px;
}
/* 反馈 */
.quiz-feedback {
  margin-top: 16px;
  padding: 14px 18px;
  border-radius: var(--radius-md);
  background: #fef2f2;
  border-left: 4px solid #ef4444;
}
.quiz-feedback.correct {
  background: #f0fdf4;
  border-left-color: #22c55e;
}
.feedback-title {
  font-weight: 600;
  font-size: 15px;
  margin-bottom: 8px;
}
.feedback-body {
  font-size: 13px;
  line-height: 1.7;
}
.feedback-body :deep(pre) {
  background: #1a1a2e;
  color: #e8e8e8;
  padding: var(--space-sm);
  border-radius: var(--radius-sm);
  overflow-x: auto;
}
/* 空状态 */
.quiz-empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-text-tertiary);
}
</style>
