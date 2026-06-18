import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { authApi } from '@/api'

interface User {
  id: number
  username: string
  email: string
  role: string
  avatar?: string
}

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(localStorage.getItem('token'))
  const user = ref<User | null>(null)

  const isLoggedIn = computed(() => !!token.value)
  const isAdmin = computed(() => user.value?.role === 'ADMIN')
  const isPremium = computed(() => user.value?.role === 'PREMIUM' || user.value?.role === 'ADMIN')

  async function login(username: string, password: string, role?: string) {
    const res = await authApi.login({ username, password, role })
    if (res.data.code === 200) {
      token.value = res.data.data.token
      user.value = {
        id: res.data.data.userId,
        username: res.data.data.username,
        email: '',
        role: res.data.data.role
      }
      localStorage.setItem('token', token.value!)
    }
    return res.data
  }

  async function register(username: string, email: string, password: string) {
    const res = await authApi.register({ username, email, password })
    if (res.data.code === 200) {
      token.value = res.data.data.token
      user.value = {
        id: res.data.data.userId,
        username: res.data.data.username,
        email,
        role: res.data.data.role
      }
      localStorage.setItem('token', token.value!)
    }
    return res.data
  }

  function logout() {
    token.value = null
    user.value = null
    localStorage.removeItem('token')
  }

  return { token, user, isLoggedIn, isAdmin, isPremium, login, register, logout }
})
