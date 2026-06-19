import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/store/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'Home',
      component: () => import('@/views/Home.vue')
    },
    {
      path: '/login',
      name: 'Login',
      component: () => import('@/views/Login.vue')
    },
    {
      path: '/register',
      name: 'Register',
      component: () => import('@/views/Register.vue')
    },
    {
      path: '/import',
      name: 'RepoImport',
      component: () => import('@/views/RepoImport.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/repo/:id',
      name: 'RepoPreview',
      component: () => import('@/views/RepoPreview.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/chat/:repoId',
      name: 'Chat',
      component: () => import('@/views/Chat.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/architecture/:repoId',
      name: 'Architecture',
      component: () => import('@/views/Architecture.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/version/:repoId',
      name: 'VersionCompare',
      component: () => import('@/views/VersionCompare.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/quiz/:repoId',
      name: 'Quiz',
      component: () => import('@/views/Quiz.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/translate/:repoId',
      name: 'Translate',
      component: () => import('@/views/Translate.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/graph/:repoId',
      name: 'Graph',
      component: () => import('@/views/Graph.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/knowledge/:repoId',
      name: 'Knowledge',
      component: () => import('@/views/Knowledge.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/profile',
      name: 'Profile',
      component: () => import('@/views/Profile.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/quiz-stats',
      name: 'QuizStats',
      component: () => import('@/views/QuizStats.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/admin',
      name: 'Admin',
      component: () => import('@/views/Admin.vue'),
      meta: { requiresAuth: true, requiresAdmin: true }
    }
  ]
})

router.beforeEach((to, _from, next) => {
  const authStore = useAuthStore()

  if (to.meta.requiresAuth && !authStore.isLoggedIn) {
    next({ name: 'Login', query: { redirect: to.fullPath } })
    return
  }

  if (to.meta.requiresAdmin && authStore.user?.role !== 'ADMIN') {
    next({ name: 'Home' })
    return
  }

  next()
})

export default router
