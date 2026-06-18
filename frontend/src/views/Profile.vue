<template>
  <div class="page-container">
    <div class="page-header">
      <h1 class="page-title">个人中心</h1>
    </div>

    <div v-if="loading" class="loading"><div class="spinner"></div> 加载中...</div>

    <template v-else-if="profile">
      <!-- 用户信息 -->
      <div class="card" style="margin-bottom:16px">
        <div class="profile-user">
          <div class="user-avatar">{{ profile.user?.username?.charAt(0).toUpperCase() }}</div>
          <div>
            <div class="user-name">{{ profile.user?.username }}</div>
            <div class="user-role">
              <span class="tag" :class="roleTag">{{ profile.user?.role }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 配额信息 -->
      <div class="card" style="margin-bottom:16px">
        <h3 style="margin-bottom:16px">今日配额使用</h3>
        <div class="grid grid-2">
          <div class="quota-item">
            <div class="quota-label">仓库导入</div>
            <div class="quota-bar">
              <div class="progress-bar">
                <div class="progress-bar-fill" :style="{ width: importPercent + '%' }"></div>
              </div>
            </div>
            <div class="quota-text">
              {{ profile.todayUsage?.importCount || 0 }} / {{ profile.quotaLimit?.dailyImportLimit || 3 }}
            </div>
          </div>
          <div class="quota-item">
            <div class="quota-label">AI 问答</div>
            <div class="quota-bar">
              <div class="progress-bar">
                <div class="progress-bar-fill" :style="{ width: chatPercent + '%' }"></div>
              </div>
            </div>
            <div class="quota-text">
              {{ profile.todayUsage?.chatCount || 0 }} / {{ profile.quotaLimit?.dailyChatLimit || 20 }}
            </div>
          </div>
        </div>
        <div class="divider"></div>
        <div class="quota-item">
          <div class="quota-label">仓库数量</div>
          <div class="quota-text">{{ profile.repoCount || 0 }} / {{ profile.maxRepos || 5 }}</div>
        </div>
      </div>

      <!-- 我的仓库 -->
      <div class="card">
        <h3 style="margin-bottom:16px">我的仓库</h3>
        <div v-if="repos.length">
          <div v-for="repo in repos" :key="repo.id" class="repo-item">
            <div class="repo-info">
              <router-link :to="`/repo/${repo.id}`" class="repo-name">{{ repo.repoName }}</router-link>
              <div class="repo-meta">
                <span class="tag tag-accent">{{ repo.platform }}</span>
                <span class="tag">{{ repo.language || '未知' }}</span>
                <span class="tag" :class="repo.status === 'COMPLETED' ? 'tag-success' : repo.status === 'FAILED' ? 'tag-error' : 'tag-accent'">
                  {{ repo.status }}
                </span>
              </div>
            </div>
            <div class="repo-actions">
              <button class="btn btn-primary btn-sm" @click="handleReImport(repo.id)" :disabled="reimportingId === repo.id">
                {{ reimportingId === repo.id ? '导入中...' : '重新导入' }}
              </button>
              <button class="btn btn-danger btn-sm" @click="handleDelete(repo.id)">删除</button>
            </div>
          </div>
        </div>
        <div v-else class="empty-state">暂无仓库</div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { userApi, repoApi } from '@/api'

const profile = ref<any>(null)
const repos = ref<any[]>([])
const loading = ref(true)
const reimportingId = ref<number | null>(null)

const roleTag = computed(() => {
  const role = profile.value?.user?.role
  if (role === 'ADMIN') return 'tag-error'
  if (role === 'PREMIUM') return 'tag-accent'
  return ''
})

const importPercent = computed(() => {
  if (!profile.value) return 0
  const used = profile.value.todayUsage?.importCount || 0
  const limit = profile.value.quotaLimit?.dailyImportLimit || 3
  return Math.min((used / limit) * 100, 100)
})

const chatPercent = computed(() => {
  if (!profile.value) return 0
  const used = profile.value.todayUsage?.chatCount || 0
  const limit = profile.value.quotaLimit?.dailyChatLimit || 20
  return Math.min((used / limit) * 100, 100)
})

async function handleDelete(repoId: number) {
  if (!confirm('确认删除该仓库？')) return
  try {
    await repoApi.deleteRepo(repoId)
    repos.value = repos.value.filter(r => r.id !== repoId)
  } catch (e: any) {
    alert('删除失败')
  }
}

async function handleReImport(repoId: number) {
  if (!confirm('重新导入将清除旧数据并重新解析仓库，确定继续吗？')) return
  reimportingId.value = repoId
  try {
    const res = await repoApi.reImportRepo(repoId)
    if (res.data.code === 200) {
      const task = res.data.data
      // 轮询任务状态
      pollTask(task.id, () => {
        reimportingId.value = null
        alert('重新导入完成！')
        location.reload()
      })
    } else {
      alert(res.data.msg || '重新导入失败：服务器返回异常')
      reimportingId.value = null
    }
  } catch (e: any) {
    const msg = e.response?.data?.msg || e.message || JSON.stringify(e.response?.data) || '重新导入失败'
    console.error('重新导入错误:', e.response?.status, e.response?.data)
    alert('重新导入失败: ' + msg)
    reimportingId.value = null
  }
}

function pollTask(taskId: number, onSuccess: () => void) {
  const timer = setInterval(async () => {
    try {
      const res = await repoApi.getTaskStatus(taskId)
      const task = res.data.data
      if (task.status === 'COMPLETED') {
        clearInterval(timer)
        onSuccess()
      } else if (task.status === 'FAILED') {
        clearInterval(timer)
        alert('导入失败：' + task.errorMessage)
        reimportingId.value = null
      }
    } catch { /* ignore */ }
  }, 3000)
}

onMounted(async () => {
  try {
    const [profileRes, reposRes] = await Promise.all([
      userApi.getProfile(),
      repoApi.listRepos(0, 50)
    ])
    if (profileRes.data.code === 200) profile.value = profileRes.data.data
    if (reposRes.data.code === 200) repos.value = (Array.isArray(reposRes.data.data) ? reposRes.data.data : reposRes.data.data?.content) || []
  } catch (e) {
    // ignore
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.profile-user {
  display: flex;
  align-items: center;
  gap: var(--space-md);
}
.user-avatar {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background: #1a1a2e;
  color: #e8e8e8;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  font-weight: 600;
  font-family: var(--font-mono);
}
.user-name {
  font-size: 16px;
  font-weight: 600;
}
.user-role {
  margin-top: 4px;
}
.quota-item {
  margin-bottom: var(--space-md);
}
.quota-label {
  font-size: 13px;
  color: var(--color-text-secondary);
  margin-bottom: 6px;
}
.quota-bar {
  margin-bottom: 4px;
}
.quota-text {
  font-size: 13px;
  font-family: var(--font-mono);
  color: var(--color-text-tertiary);
}
.repo-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-md) 0;
  border-bottom: 1px solid var(--color-border-light);
}
.repo-item:last-child {
  border-bottom: none;
}
.repo-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--color-accent);
  text-decoration: none;
}
.repo-name:hover {
  text-decoration: underline;
}
.repo-meta {
  display: flex;
  gap: var(--space-xs);
  margin-top: 4px;
}
.repo-actions {
  display: flex;
  gap: var(--space-xs);
  flex-shrink: 0;
}
</style>
