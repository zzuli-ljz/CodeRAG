<template>
  <div class="auth-page">
    <div class="auth-card card">
      <h2 class="auth-title">登录</h2>

      <!-- 身份选择 -->
      <div v-if="!identitySelected" class="identity-selection">
        <p class="identity-hint">请选择你的身份</p>
        <div class="identity-cards">
          <div
            v-for="item in identities"
            :key="item.role"
            class="identity-card"
            @click="selectIdentity(item)"
          >
            <span class="identity-icon">{{ item.icon }}</span>
            <span class="identity-label">{{ item.label }}</span>
          </div>
        </div>
      </div>

      <!-- 登录表单 -->
      <form v-else @submit.prevent="handleLogin">
        <!-- 已选身份标签 -->
        <div class="selected-identity" @click="resetIdentity">
          <span class="selected-badge" :class="'badge-' + selectedRole.toLowerCase()">
            {{ selectedIcon }} {{ selectedLabel }}
          </span>
          <span class="change-hint">切换身份</span>
        </div>

        <div class="form-group">
          <label class="form-label">用户名</label>
          <input v-model="form.username" type="text" class="input" placeholder="请输入用户名" required />
        </div>
        <div class="form-group">
          <label class="form-label">密码</label>
          <input v-model="form.password" type="password" class="input" placeholder="请输入密码" required />
        </div>
        <div v-if="error" class="form-error">{{ error }}</div>
        <button type="submit" class="btn btn-primary btn-lg" style="width:100%;margin-top:16px" :disabled="loading">
          {{ loading ? '登录中...' : '登录' }}
        </button>
      </form>

      <div class="auth-footer">
        还没有账号？<router-link to="/register">立即注册</router-link>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/store/auth'

const authStore = useAuthStore()
const router = useRouter()
const route = useRoute()

const identities = [
  { role: 'USER', label: '普通用户', icon: '👤' },
  { role: 'PREMIUM', label: '高级用户', icon: '⭐' },
  { role: 'ADMIN', label: '管理员', icon: '🛡️' }
]

const identitySelected = ref(false)
const selectedRole = ref('')
const selectedLabel = ref('')
const selectedIcon = ref('')

function selectIdentity(item: any) {
  selectedRole.value = item.role
  selectedLabel.value = item.label
  selectedIcon.value = item.icon
  identitySelected.value = true
}

function resetIdentity() {
  identitySelected.value = false
  selectedRole.value = ''
  form.username = ''
  form.password = ''
}

const form = reactive({ username: '', password: '' })
const error = ref('')
const loading = ref(false)

async function handleLogin() {
  error.value = ''
  loading.value = true
  try {
    const res = await authStore.login(form.username, form.password, selectedRole.value)
    if (res.code === 200) {
      const redirect = (route.query.redirect as string)
        || (selectedRole.value === 'ADMIN' ? '/admin' : '/import')
      router.push(redirect)
    } else {
      error.value = res.message || '登录失败'
    }
  } catch (e: any) {
    error.value = e.response?.data?.message || '登录失败，请稍后重试'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.auth-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: calc(100vh - 56px);
  padding: var(--space-xl);
}
.auth-card {
  width: 100%;
  max-width: 420px;
}
.auth-title {
  font-size: 20px;
  font-weight: 600;
  margin-bottom: var(--space-lg);
  text-align: center;
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
.form-error {
  color: var(--color-error);
  font-size: 13px;
  margin-top: var(--space-sm);
}
.auth-footer {
  text-align: center;
  margin-top: var(--space-lg);
  font-size: 13px;
  color: var(--color-text-tertiary);
}

/* 身份选择 */
.identity-selection {
  text-align: center;
}
.identity-hint {
  font-size: 13px;
  color: var(--color-text-secondary);
  margin-bottom: var(--space-md);
}
.identity-cards {
  display: flex;
  gap: 12px;
  justify-content: center;
}
.identity-card {
  flex: 1;
  padding: 18px 12px;
  border: 2px solid var(--color-border);
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.2s;
  text-align: center;
  background: var(--color-bg);
}
.identity-card:hover {
  border-color: var(--color-primary);
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0,0,0,0.08);
}
.identity-icon {
  font-size: 28px;
  display: block;
  margin-bottom: 6px;
}
.identity-label {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text-primary);
}

/* 已选身份 */
.selected-identity {
  text-align: center;
  margin-bottom: var(--space-md);
  cursor: pointer;
}
.selected-badge {
  display: inline-block;
  padding: 4px 14px;
  border-radius: 20px;
  font-size: 13px;
  font-weight: 600;
}
.badge-user { background: rgba(107,114,128,0.1); color: #4b5563; }
.badge-premium { background: rgba(245,158,11,0.1); color: #d97706; }
.badge-admin { background: rgba(239,68,68,0.1); color: #dc2626; }
.change-hint {
  display: block;
  font-size: 11px;
  color: var(--color-text-tertiary);
  margin-top: 4px;
}
.change-hint:hover {
  color: var(--color-primary);
}

@media (max-width: 480px) {
  .identity-cards {
    flex-direction: column;
  }
}
</style>
