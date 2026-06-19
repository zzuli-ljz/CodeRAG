<template>
  <div class="page-container">
    <div class="page-header">
      <h1 class="page-title">{{ repo?.repoName || '仓库详情' }}</h1>
      <p class="page-desc">{{ repo?.repoUrl }}</p>
    </div>

    <div v-if="loading" class="loading"><div class="spinner"></div> 加载中...</div>

    <template v-else-if="repo">
      <!-- 仓库信息卡片 -->
      <div class="card" style="margin-bottom:16px">
        <div class="repo-meta">
          <span class="tag tag-accent">{{ repo.platform }}</span>
          <span class="tag">{{ repo.language || '未知语言' }}</span>
          <span class="tag" :class="repo.status === 'COMPLETED' ? 'tag-success' : repo.status === 'FAILED' ? 'tag-error' : 'tag-accent'">
            {{ repo.status }}
          </span>
        </div>
        <div class="divider"></div>
        <div class="repo-stats">
          <div class="stat-item">
            <span class="stat-label">文件数</span>
            <span class="stat-value">{{ repo.fileCount || 0 }}</span>
          </div>
          <div class="stat-item">
            <span class="stat-label">代码行数</span>
            <span class="stat-value">{{ repo.codeLineCount || 0 }}</span>
          </div>
          <div class="stat-item">
            <span class="stat-label">默认分支</span>
            <span class="stat-value">{{ repo.defaultBranch }}</span>
          </div>
        </div>
        <div v-if="repo.description" style="margin-top:12px;font-size:13px;color:var(--color-text-secondary)">
          {{ repo.description }}
        </div>
      </div>

      <!-- 功能入口 -->
      <div class="grid grid-3" v-if="repo.status === 'COMPLETED'">
        <router-link :to="`/chat/${repo.id}`" class="card card-hover action-card">
          <h3 class="action-title">AI 代码问答</h3>
          <p class="action-desc">基于整仓代码上下文精准问答</p>
        </router-link>
        <router-link :to="`/architecture/${repo.id}`" class="card card-hover action-card">
          <h3 class="action-title">架构解析</h3>
          <p class="action-desc">AI 自动生成项目架构说明</p>
        </router-link>
        <router-link :to="`/version/${repo.id}`" class="card card-hover action-card">
          <h3 class="action-title">版本对比</h3>
          <p class="action-desc">代码版本差异 AI 解读</p>
        </router-link>
        <router-link :to="`/quiz/${repo.id}`" class="card card-hover action-card">
          <h3 class="action-title">智能刷题</h3>
          <p class="action-desc">代码难点识别与练习</p>
        </router-link>
        <router-link :to="`/translate/${repo.id}`" class="card card-hover action-card">
          <h3 class="action-title">代码翻译</h3>
          <p class="action-desc">多语言代码智能翻译迁移</p>
        </router-link>
        <router-link :to="`/graph/${repo.id}`" class="card card-hover action-card">
          <h3 class="action-title">调用链图谱</h3>
          <p class="action-desc">代码调用关系可视化</p>
        </router-link>
        <router-link :to="`/knowledge/${repo.id}`" class="card card-hover action-card">
          <h3 class="action-title">知识库预览</h3>
          <p class="action-desc">查看已向量化的文档片段</p>
        </router-link>
        <router-link :to="`/learning-path/${repo.id}`" class="card card-hover action-card">
          <h3 class="action-title">🗺️ 学习路径</h3>
          <p class="action-desc">AI 生成渐进式学习路线</p>
        </router-link>
        <router-link :to="`/challenge/${repo.id}`" class="card card-hover action-card">
          <h3 class="action-title">💻 编程挑战</h3>
          <p class="action-desc">基于代码生成编程练习</p>
        </router-link>
        <router-link :to="`/timeline/${repo.id}`" class="card card-hover action-card">
          <h3 class="action-title">🕐 代码时间线</h3>
          <p class="action-desc">仓库提交历史与演进</p>
        </router-link>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { repoApi } from '@/api'

const route = useRoute()
const repo = ref<any>(null)
const loading = ref(true)

onMounted(async () => {
  try {
    const id = Number(route.params.id)
    const res = await repoApi.getRepo(id)
    if (res.data.code === 200) {
      repo.value = res.data.data
    }
  } catch (e: any) {
    alert('获取仓库信息失败')
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.repo-meta {
  display: flex;
  gap: var(--space-sm);
  flex-wrap: wrap;
}
.repo-stats {
  display: flex;
  gap: var(--space-xl);
}
.stat-item {
  display: flex;
  flex-direction: column;
}
.stat-label {
  font-size: 12px;
  color: var(--color-text-tertiary);
}
.stat-value {
  font-size: 16px;
  font-weight: 600;
  font-family: var(--font-mono);
}
.action-card {
  text-decoration: none;
  color: inherit;
}
.action-title {
  font-size: 15px;
  font-weight: 600;
  margin-bottom: var(--space-xs);
}
.action-desc {
  font-size: 13px;
  color: var(--color-text-secondary);
}
</style>
