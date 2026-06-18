<template>
  <div class="auth-page">
    <div class="auth-card card">
      <h2 class="auth-title">注册</h2>
      <form @submit.prevent="handleRegister">
        <div class="form-group">
          <label class="form-label">用户名</label>
          <input v-model="form.username" type="text" class="input" placeholder="3-20 个字符" required />
        </div>
        <div class="form-group">
          <label class="form-label">邮箱</label>
          <input v-model="form.email" type="email" class="input" placeholder="your@email.com" required />
        </div>
        <div class="form-group">
          <label class="form-label">密码</label>
          <input v-model="form.password" type="password" class="input" placeholder="6-30 个字符" required />
        </div>
        <div v-if="error" class="form-error">{{ error }}</div>
        <button type="submit" class="btn btn-primary btn-lg" style="width:100%;margin-top:16px" :disabled="loading">
          {{ loading ? '注册中...' : '注册' }}
        </button>
      </form>
      <div class="auth-footer">
        已有账号？<router-link to="/login">立即登录</router-link>
      </div>
      <div class="auth-hint">
        注册后默认为普通用户，如需更高配额请联系管理员分配
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/store/auth'

const authStore = useAuthStore()
const router = useRouter()

const form = reactive({ username: '', email: '', password: '' })
const error = ref('')
const loading = ref(false)

async function handleRegister() {
  error.value = ''
  loading.value = true
  try {
    const res = await authStore.register(form.username, form.email, form.password)
    if (res.code === 200) {
      router.push('/import')
    } else {
      error.value = res.message || '注册失败'
    }
  } catch (e: any) {
    error.value = e.response?.data?.message || '注册失败，请稍后重试'
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
.auth-hint {
  text-align: center;
  margin-top: var(--space-md);
  font-size: 12px;
  color: var(--color-text-tertiary);
  line-height: 1.5;
  padding: 8px 12px;
  background: var(--color-bg-secondary, rgba(0,0,0,0.03));
  border-radius: 6px;
}
</style>
