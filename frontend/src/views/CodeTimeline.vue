<template>
  <div class="page-container">
    <div class="page-header">
      <h1 class="page-title">代码时间线</h1>
      <button class="btn btn-primary" @click="handleAnalyze" :disabled="analyzing">
        {{ analyzing ? '分析中...' : '分析仓库演进' }}
      </button>
    </div>

    <div v-if="loading" class="loading"><div class="spinner"></div> 加载中...</div>

    <template v-else-if="timelineData">
      <!-- 概览卡片 -->
      <div class="card" style="margin-bottom:16px">
        <h3 style="margin-bottom:12px">📊 仓库概览</h3>
        <div class="overview-grid">
          <div class="overview-item">
            <div class="overview-num">{{ timelineData.totalCommits || 0 }}</div>
            <div class="overview-label">总提交数</div>
          </div>
          <div class="overview-item">
            <div class="overview-num">{{ timelineData.totalFiles || 0 }}</div>
            <div class="overview-label">文件数</div>
          </div>
          <div class="overview-item">
            <div class="overview-num">{{ timelineData.activeDays || 0 }}</div>
            <div class="overview-label">活跃天数</div>
          </div>
          <div class="overview-item">
            <div class="overview-num">{{ timelineData.topLanguage || '-' }}</div>
            <div class="overview-label">主要语言</div>
          </div>
        </div>
      </div>

      <!-- 时间线 -->
      <div class="card">
        <h3 style="margin-bottom:16px">🕐 提交时间线</h3>
        <div v-if="timelineData.commits && timelineData.commits.length" class="timeline">
          <div
            v-for="(commit, idx) in timelineData.commits"
            :key="idx"
            class="timeline-item"
          >
            <div class="timeline-dot" :class="dotClass(idx)"></div>
            <div class="timeline-content">
              <div class="commit-header">
                <span class="commit-hash">{{ commit.hash?.slice(0, 7) }}</span>
                <span class="commit-date">{{ formatDate(commit.date) }}</span>
              </div>
              <div class="commit-message">{{ commit.message }}</div>
              <div class="commit-author">{{ commit.author }}</div>
              <div v-if="commit.filesChanged" class="commit-files">
                变更 {{ commit.filesChanged }} 个文件
                <span class="additions">+{{ commit.additions || 0 }}</span>
                <span class="deletions">-{{ commit.deletions || 0 }}</span>
              </div>
            </div>
          </div>
        </div>
        <div v-else class="empty-state">暂无提交记录</div>
      </div>

      <!-- 文件热力图 -->
      <div v-if="timelineData.hotFiles && timelineData.hotFiles.length" class="card" style="margin-top:16px">
        <h3 style="margin-bottom:12px">🔥 文件变更热力图</h3>
        <div class="hotfiles-list">
          <div v-for="(f, idx) in timelineData.hotFiles" :key="idx" class="hotfile-item">
            <div class="hotfile-rank">{{ idx + 1 }}</div>
            <div class="hotfile-info">
              <div class="hotfile-path">{{ f.path }}</div>
              <div class="hotfile-bar">
                <div class="hotfile-fill" :style="{ width: hotPercent(f.changes) + '%' }"></div>
              </div>
            </div>
            <div class="hotfile-num">{{ f.changes }} 次变更</div>
          </div>
        </div>
      </div>
    </template>

    <div v-else class="empty-state">点击「分析仓库演进」查看代码时间线</div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { timelineApi } from '@/api'

const route = useRoute()
const repoId = Number(route.params.repoId)

const loading = ref(false)
const analyzing = ref(false)
const timelineData = ref<any>(null)

function formatDate(d: string) {
  return new Date(d).toLocaleDateString('zh-CN', { year: 'numeric', month: 'short', day: 'numeric' })
}

function dotClass(idx: number): string {
  const colors = ['dot-blue', 'dot-green', 'dot-purple', 'dot-orange', 'dot-teal']
  return colors[idx % colors.length]
}

function hotPercent(changes: number): number {
  if (!timelineData.value?.hotFiles?.length) return 0
  const max = Math.max(...timelineData.value.hotFiles.map((f: any) => f.changes || 0))
  return max > 0 ? (changes / max) * 100 : 0
}

async function handleAnalyze() {
  analyzing.value = true
  try {
    const res = await timelineApi.analyze(repoId)
    if (res.data.code === 200) {
      // 后端返回的是 CodeTimeline 实体，timelineData 字段是 JSON 字符串
      const data = res.data.data
      if (data.timelineData) {
        try {
          timelineData.value = typeof data.timelineData === 'string'
            ? JSON.parse(data.timelineData)
            : data.timelineData
        } catch {
          timelineData.value = data
        }
      } else {
        timelineData.value = data
      }
    }
  } catch (e: any) {
    alert('分析失败: ' + (e.response?.data?.message || e.message))
  } finally {
    analyzing.value = false
  }
}

onMounted(async () => {
  loading.value = true
  try {
    const res = await timelineApi.getLatest(repoId)
    if (res.data.code === 200 && res.data.data) {
      const data = res.data.data
      if (data.timelineData) {
        try {
          timelineData.value = typeof data.timelineData === 'string'
            ? JSON.parse(data.timelineData)
            : data.timelineData
        } catch {
          timelineData.value = data
        }
      } else {
        timelineData.value = data
      }
    }
  } catch { /* ignore */ }
  finally { loading.value = false }
})
</script>

<style scoped>
.overview-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}
.overview-item {
  text-align: center;
  padding: 12px;
  background: var(--color-bg-secondary);
  border-radius: var(--radius-md);
}
.overview-num {
  font-size: 24px;
  font-weight: 700;
  font-family: var(--font-mono);
  color: var(--color-accent);
}
.overview-label {
  font-size: 12px;
  color: var(--color-text-tertiary);
  margin-top: 4px;
}

/* 时间线 */
.timeline {
  position: relative;
  padding-left: 24px;
}
.timeline::before {
  content: '';
  position: absolute;
  left: 7px;
  top: 0;
  bottom: 0;
  width: 2px;
  background: var(--color-border);
}
.timeline-item {
  position: relative;
  margin-bottom: 20px;
}
.timeline-dot {
  position: absolute;
  left: -20px;
  top: 4px;
  width: 12px;
  height: 12px;
  border-radius: 50%;
  border: 2px solid var(--color-bg);
}
.dot-blue { background: #4f46e5; }
.dot-green { background: #22c55e; }
.dot-purple { background: #7c3aed; }
.dot-orange { background: #f59e0b; }
.dot-teal { background: #06b6d4; }
.timeline-content {
  padding: 10px 14px;
  background: var(--color-bg-secondary);
  border-radius: var(--radius-md);
}
.commit-header {
  display: flex;
  gap: var(--space-md);
  align-items: center;
}
.commit-hash {
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--color-accent);
  font-weight: 600;
}
.commit-date {
  font-size: 12px;
  color: var(--color-text-tertiary);
}
.commit-message {
  font-size: 14px;
  font-weight: 500;
  margin-top: 4px;
}
.commit-author {
  font-size: 12px;
  color: var(--color-text-tertiary);
  margin-top: 2px;
}
.commit-files {
  font-size: 12px;
  color: var(--color-text-secondary);
  margin-top: 4px;
}
.additions { color: #22c55e; margin-left: 6px; }
.deletions { color: #ef4444; margin-left: 6px; }

/* 热力图 */
.hotfiles-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.hotfile-item {
  display: flex;
  align-items: center;
  gap: 12px;
}
.hotfile-rank {
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--color-accent-light);
  font-size: 12px;
  font-weight: 700;
  color: var(--color-accent);
  flex-shrink: 0;
}
.hotfile-info {
  flex: 1;
}
.hotfile-path {
  font-size: 13px;
  font-family: var(--font-mono);
  margin-bottom: 4px;
}
.hotfile-bar {
  height: 6px;
  background: var(--color-border-light);
  border-radius: 3px;
  overflow: hidden;
}
.hotfile-fill {
  height: 100%;
  background: linear-gradient(90deg, #f59e0b, #ef4444);
  border-radius: 3px;
  transition: width 0.5s;
}
.hotfile-num {
  font-size: 12px;
  color: var(--color-text-tertiary);
  flex-shrink: 0;
}

@media (max-width: 600px) {
  .overview-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
