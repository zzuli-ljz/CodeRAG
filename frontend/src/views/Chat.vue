<template>
  <div class="chat-page">
    <div class="chat-sidebar">
      <div class="sidebar-header">
        <h3>问答历史</h3>
      </div>
      <div class="sidebar-list">
        <div
          v-for="h in history"
          :key="h.id"
          class="history-item"
          @click="selectHistory(h)"
        >
          <div class="history-q">{{ h.question }}</div>
          <div class="history-time">{{ formatTime(h.createdAt) }}</div>
        </div>
        <div v-if="!history.length" class="empty-state" style="padding:24px">
          暂无问答记录
        </div>
      </div>
    </div>

    <div class="chat-main">
      <div class="chat-messages" ref="messagesRef">
        <div v-if="!messages.length" class="empty-state">
          输入问题开始代码问答
        </div>
        <div v-for="(msg, idx) in messages" :key="idx" class="message" :class="msg.role">
          <div class="message-content">
            <div v-if="msg.role === 'user'" class="msg-text">{{ msg.content }}</div>
            <div v-else class="msg-text" v-html="renderMarkdown(msg.content)"></div>
          </div>
          <div v-if="msg.sources" class="msg-sources">
            <span class="tag tag-accent">溯源: {{ msg.sources }}</span>
          </div>
        </div>
        <div v-if="asking" class="message assistant">
          <div class="message-content"><div class="spinner"></div> AI 思考中...</div>
        </div>
      </div>

      <div class="chat-input-area">
        <div class="input-row">
          <textarea
            v-model="question"
            class="input chat-input"
            placeholder="输入代码相关问题，如：这个项目的主入口在哪？路由是如何设计的？"
            @keydown.enter.ctrl="handleAsk"
            rows="2"
          ></textarea>
          <button class="btn btn-primary" @click="handleAsk" :disabled="!question.trim() || asking">
            发送
          </button>
        </div>
        <div class="input-hint">Ctrl + Enter 发送</div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import { chatApi } from '@/api'
import MarkdownIt from 'markdown-it'

const md = new MarkdownIt({ html: false, breaks: true })
const route = useRoute()
const repoId = Number(route.params.repoId)

const question = ref('')
const asking = ref(false)
const messages = ref<any[]>([])
const history = ref<any[]>([])
const messagesRef = ref<HTMLElement>()

function renderMarkdown(text: string) {
  return md.render(text || '')
}

function formatTime(t: string) {
  if (!t) return ''
  return new Date(t).toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}

function selectHistory(h: any) {
  messages.value = [
    { role: 'user', content: h.question },
    { role: 'assistant', content: h.answer, sources: h.sourceSnippets }
  ]
  scrollToBottom()
}

async function handleAsk() {
  if (!question.value.trim() || asking.value) return

  const q = question.value.trim()
  messages.value.push({ role: 'user', content: q })
  question.value = ''
  asking.value = true
  scrollToBottom()

  try {
    const res = await chatApi.ask(repoId, q)
    if (res.data.code === 200) {
      const data = res.data.data
      messages.value.push({
        role: 'assistant',
        content: data.answer,
        sources: data.sourceSnippets
      })
      loadHistory()
    }
  } catch (e: any) {
    const msg = e.response?.data?.message || e.message || '请求失败'
    messages.value.push({
      role: 'assistant',
      content: '❌ ' + msg
    })
  } finally {
    asking.value = false
    scrollToBottom()
  }
}

async function loadHistory() {
  try {
    const res = await chatApi.getHistory(repoId, 0, 50)
    if (res.data.code === 200) {
      history.value = (Array.isArray(res.data.data) ? res.data.data : res.data.data?.content) || []
    }
  } catch { /* ignore */ }
}

function scrollToBottom() {
  nextTick(() => {
    if (messagesRef.value) {
      messagesRef.value.scrollTop = messagesRef.value.scrollHeight
    }
  })
}

onMounted(() => {
  loadHistory()
})
</script>

<style scoped>
.chat-page {
  display: flex;
  height: calc(100vh - 56px);
}
.chat-sidebar {
  width: 260px;
  border-right: 1px solid var(--color-border);
  background: var(--color-bg);
  display: flex;
  flex-direction: column;
}
.sidebar-header {
  padding: var(--space-md) var(--space-lg);
  border-bottom: 1px solid var(--color-border-light);
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
.history-item:hover {
  background: var(--color-bg-secondary);
}
.history-q {
  font-size: 13px;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.history-time {
  font-size: 11px;
  color: var(--color-text-tertiary);
  margin-top: 2px;
}
.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
}
.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: var(--space-lg);
}
.message {
  margin-bottom: var(--space-lg);
}
.message.user .message-content {
  background: var(--color-accent-light);
  padding: var(--space-md);
  border-radius: var(--radius-md);
  display: inline-block;
  max-width: 70%;
}
.message.assistant .message-content {
  background: var(--color-bg);
  border: 1px solid var(--color-border-light);
  padding: var(--space-md);
  border-radius: var(--radius-md);
  max-width: 85%;
}
.msg-text {
  font-size: 14px;
  line-height: 1.7;
}
.msg-text :deep(pre) {
  background: #1a1a2e;
  color: #e8e8e8;
  padding: var(--space-md);
  border-radius: var(--radius-md);
  overflow-x: auto;
  margin: var(--space-sm) 0;
}
.msg-text :deep(code) {
  font-family: var(--font-mono);
  font-size: 13px;
}
.msg-text :deep(p) {
  margin-bottom: var(--space-sm);
}
.msg-sources {
  margin-top: var(--space-xs);
}
.chat-input-area {
  padding: var(--space-md) var(--space-lg);
  border-top: 1px solid var(--color-border);
  background: var(--color-bg);
}
.input-row {
  display: flex;
  gap: var(--space-sm);
  align-items: flex-end;
}
.chat-input {
  flex: 1;
  resize: none;
}
.input-hint {
  font-size: 12px;
  color: var(--color-text-tertiary);
  margin-top: var(--space-xs);
}
</style>
