import axios from 'axios'

const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json'
  }
})

// 请求拦截 - 添加 JWT Token
request.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// 响应拦截 - 统一错误处理
request.interceptors.response.use(
  response => response,
  error => {
    if (error.response?.status === 401) {
      localStorage.removeItem('token')
      window.location.href = '/login'
    }
    return Promise.reject(error)
  }
)

// ================================
// Auth API
// ================================
export const authApi = {
  login: (data: { username: string; password: string; role?: string }) =>
    request.post('/auth/login', data),
  register: (data: { username: string; email: string; password: string }) =>
    request.post('/auth/register', data)
}

// ================================
// Repo API
// ================================
export const repoApi = {
  importRepo: (data: { repoUrl: string; branch?: string }) =>
    request.post('/repos/import', data, { timeout: 0 }),
  listRepos: (page = 0, size = 10) =>
    request.get('/repos', { params: { page, size } }),
  getRepo: (id: number) =>
    request.get(`/repos/${id}`),
  deleteRepo: (id: number) =>
    request.delete(`/repos/${id}`),
  reImportRepo: (id: number) =>
    request.post(`/repos/${id}/reimport`, null, { timeout: 0 }),
  getTaskStatus: (taskId: number) =>
    request.get(`/repos/tasks/${taskId}`),
  getUserTasks: () =>
    request.get('/repos/tasks'),
  getRepoFiles: (id: number) =>
    request.get(`/repos/${id}/files`)
}

// ================================
// Chat API
// ================================
export const chatApi = {
  ask: (repoId: number, question: string) =>
    request.post('/chat', { repoId, question }),
  getHistory: (repoId: number, page = 0, size = 20) =>
    request.get(`/chat/history/${repoId}`, { params: { page, size } })
}

// ================================
// Architecture API
// ================================
export const architectureApi = {
  analyze: (repoId: number, force = false) =>
    request.post(`/architecture/analyze/${repoId}`, null, { params: { force } }),
  getLatest: (repoId: number) =>
    request.get(`/architecture/latest/${repoId}`),
  getHistory: (repoId: number, page = 0, size = 10) =>
    request.get(`/architecture/history/${repoId}`, { params: { page, size } })
}

// ================================
// Version Compare API
// ================================
export const versionApi = {
  compare: (repoId: number, sourceRef: string, targetRef: string) =>
    request.post('/version/compare', { repoId, sourceRef, targetRef }),
  getHistory: (repoId: number, page = 0, size = 10) =>
    request.get(`/version/history/${repoId}`, { params: { page, size } })
}

// ================================
// Quiz API
// ================================
export const quizApi = {
  generate: (repoId: number) =>
    request.post(`/quiz/generate/${repoId}`),
  getLatest: (repoId: number) =>
    request.get(`/quiz/latest/${repoId}`),
  listQuizzes: (repoId: number) =>
    request.get(`/quiz/list/${repoId}`),
  submitAnswer: (quizId: number, userAnswer: string) =>
    request.post('/quiz/answer', { quizId, userAnswer }),
  getAttempts: (page = 0, size = 10) =>
    request.get('/quiz/attempts', { params: { page, size } }),
  // 错题本（支持筛选）
  getWrongBook: (page = 0, size = 10, filters?: { difficulty?: string; knowledgePoint?: string; repoId?: number; keyword?: string }) =>
    request.get('/quiz/wrong-book', { params: { page, size, ...filters } }),
  // 错题本筛选选项
  getWrongBookFilters: () =>
    request.get('/quiz/wrong-book/filters'),
  // 收藏列表
  getFavorites: (page = 0, size = 10) =>
    request.get('/quiz/favorites', { params: { page, size } }),
  // 切换状态（错题本/收藏/取消）
  toggleStatus: (attemptId: number, status: string) =>
    request.put(`/quiz/attempt/${attemptId}/status`, null, { params: { status } }),
  // 答题统计
  getStats: () =>
    request.get('/quiz/stats'),
  // 获取用户对某题的最新状态
  getQuizStatus: (quizId: number) =>
    request.get(`/quiz/status/${quizId}`)
}

// ================================
// User API
// ================================
export const userApi = {
  getInfo: () =>
    request.get('/user/info'),
  getProfile: () =>
    request.get('/user/profile')
}

// ================================
// Translate API
// ================================
export const translateApi = {
  translateFile: (repoId: number, filePath: string, targetLang: string) =>
    request.post('/translate/file', { repoId, filePath, targetLang }),
  translateSnippet: (sourceCode: string, sourceLang: string, targetLang: string) =>
    request.post('/translate/snippet', { sourceCode, sourceLang, targetLang }),
  getHistory: (repoId: number, page = 0, size = 10) =>
    request.get(`/translate/history/${repoId}`, { params: { page, size } })
}

// ================================
// Graph API
// ================================
export const graphApi = {
  build: (repoId: number, force = false) =>
    request.post(`/graph/build/${repoId}`, null, { params: { force } }),
  getLatest: (repoId: number) =>
    request.get(`/graph/latest/${repoId}`),
  getHistory: (repoId: number, page = 0, size = 10) =>
    request.get(`/graph/history/${repoId}`, { params: { page, size } })
}

// ================================
// Admin API
// ================================
export const adminApi = {
  listUsers: (page = 0, size = 20) =>
    request.get('/admin/users', { params: { page, size } }),
  updateUserRole: (userId: number, role: string) =>
    request.put(`/admin/users/${userId}/role`, null, { params: { role } }),
  toggleBan: (userId: number, banned: boolean) =>
    request.put(`/admin/users/${userId}/ban`, null, { params: { banned } }),
  setUserQuota: (userId: number, importLimit: number | null, chatLimit: number | null) =>
    request.put(`/admin/users/${userId}/quota`, { importLimit, chatLimit }),
  listTasks: (page = 0, size = 20, userId?: number, status?: string) =>
    request.get('/admin/tasks', { params: { page, size, userId, status } }),
  markTaskFailed: (taskId: number, reason: string) =>
    request.put(`/admin/tasks/${taskId}/fail`, null, { params: { reason } })
}

// ================================
// Knowledge API
// ================================
export const knowledgeApi = {
  listChunks: (repoId: number, page = 0, size = 20, language?: string, filePath?: string) =>
    request.get(`/knowledge/chunks/${repoId}`, { params: { page, size, language, filePath } }),
  getOverview: (repoId: number) =>
    request.get(`/knowledge/overview/${repoId}`),
  getChunkDetail: (chunkId: number) =>
    request.get(`/knowledge/chunk/${chunkId}`),
  listFiles: (repoId: number) =>
    request.get(`/knowledge/files/${repoId}`),
  listLanguages: (repoId: number) =>
    request.get(`/knowledge/languages/${repoId}`)
}

export default request
