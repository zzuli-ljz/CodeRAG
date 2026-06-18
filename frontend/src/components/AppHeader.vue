<template>
  <header class="header">
    <div class="header-inner">
      <router-link to="/" class="header-brand">
        <span class="brand-icon">CR</span>
        <span class="brand-text">CodeRAG</span>
      </router-link>

      <nav class="header-nav" v-if="authStore.isLoggedIn">
        <router-link to="/import" class="nav-link">导入仓库</router-link>
        <router-link to="/profile" class="nav-link">我的仓库</router-link>
        <router-link to="/profile" class="nav-link">个人中心</router-link>
        <router-link v-if="authStore.isAdmin" to="/admin" class="nav-link">管理后台</router-link>
      </nav>

      <div class="header-actions">
        <template v-if="authStore.isLoggedIn">
          <span class="user-name">{{ authStore.user?.username }}</span>
          <button class="btn btn-secondary btn-sm" @click="handleLogout">退出</button>
        </template>
        <template v-else>
          <router-link to="/login" class="btn btn-secondary btn-sm">登录</router-link>
          <router-link to="/register" class="btn btn-primary btn-sm">注册</router-link>
        </template>
      </div>
    </div>
  </header>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/store/auth'

const authStore = useAuthStore()
const router = useRouter()

function handleLogout() {
  authStore.logout()
  router.push('/')
}
</script>

<style scoped>
.header {
  height: 56px;
  background: var(--color-bg-secondary);
  border-bottom: none;
  position: sticky;
  top: 0;
  z-index: 100;
}
.header-inner {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 var(--space-xl);
  height: 100%;
  display: flex;
  align-items: center;
  gap: var(--space-lg);
}
.header-brand {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  text-decoration: none;
  color: var(--color-text-primary);
}
.brand-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  background: #1a1a2e;
  color: #e8e8e8;
  font-family: var(--font-mono);
  font-size: 13px;
  font-weight: 700;
  border-radius: var(--radius-sm);
}
.brand-text {
  font-size: 16px;
  font-weight: 600;
  letter-spacing: -0.3px;
}
.header-nav {
  display: flex;
  align-items: center;
  gap: var(--space-xs);
  margin-left: var(--space-lg);
}
.nav-link {
  padding: 6px 12px;
  font-size: 13px;
  color: var(--color-text-secondary);
  text-decoration: none;
  border-radius: var(--radius-md);
  transition: all 0.15s;
}
.nav-link:hover,
.nav-link.router-link-active {
  color: var(--color-accent);
  background: var(--color-accent-light);
}
.header-actions {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}
.user-name {
  font-size: 13px;
  color: var(--color-text-secondary);
  margin-right: var(--space-sm);
}
</style>
