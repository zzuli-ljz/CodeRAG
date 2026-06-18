<template>
  <div class="page-container">
    <div class="page-header">
      <h1 class="page-title">管理后台</h1>
      <p class="page-desc">全局用户管理、任务队列、系统配置</p>
    </div>

    <!-- 标签页切换 -->
    <div class="tabs">
      <button class="tab" :class="{ active: activeTab === 'users' }" @click="activeTab = 'users'">用户管理</button>
      <button class="tab" :class="{ active: activeTab === 'tasks' }" @click="activeTab = 'tasks'">任务队列</button>
    </div>

    <!-- ==================== 用户管理 ==================== -->
    <div v-if="activeTab === 'users'" class="card" style="margin-top:16px">
      <div v-if="loadingUsers" class="loading"><div class="spinner"></div> 加载中...</div>
      <div v-else>
        <div class="table-wrapper">
          <table class="table">
            <thead>
              <tr>
                <th>ID</th>
                <th>用户名</th>
                <th>邮箱</th>
                <th>角色</th>
                <th>状态</th>
                <th>导入配额</th>
                <th>问答配额</th>
                <th>注册时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="u in users" :key="u.id" :class="{ 'row-banned': u.banned }">
                <td>{{ u.id }}</td>
                <td>{{ u.username }}</td>
                <td>{{ u.email }}</td>
                <td><span class="tag" :class="roleClass(u.role)">{{ u.role }}</span></td>
                <td>
                  <span v-if="u.banned" class="tag tag-error">已封禁</span>
                  <span v-else class="tag tag-success">正常</span>
                </td>
                <td>
                  <span class="quota-badge">{{ u.customImportLimit != null ? u.customImportLimit : (u.role === 'PREMIUM' || u.role === 'ADMIN' ? '20(默认)' : '3(默认)') }}</span>
                </td>
                <td>
                  <span class="quota-badge">{{ u.customChatLimit != null ? u.customChatLimit : (u.role === 'PREMIUM' || u.role === 'ADMIN' ? '200(默认)' : '20(默认)') }}</span>
                </td>
                <td style="font-size:12px;color:var(--color-text-tertiary)">{{ formatTime(u.createdAt) }}</td>
                <td class="action-cell">
                  <!-- 角色修改 -->
                  <select class="input input-sm" :value="u.role"
                    @change="handleRoleChange(u.id, ($event.target as HTMLSelectElement).value)">
                    <option value="USER">USER</option>
                    <option value="PREMIUM">PREMIUM</option>
                    <option value="ADMIN">ADMIN</option>
                  </select>
                  <!-- 封禁/解封 -->
                  <button class="btn btn-sm" :class="u.banned ? 'btn-success' : 'btn-danger'"
                    @click="handleToggleBan(u.id, !u.banned)">
                    {{ u.banned ? '解封' : '封禁' }}
                  </button>
                  <!-- 设置配额 -->
                  <button class="btn btn-sm btn-outline" @click="openQuotaDialog(u)">配额</button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <!-- 分页 -->
        <div class="pagination" v-if="userTotalPages > 1">
          <button class="btn btn-sm btn-outline" :disabled="userPage <= 0" @click="userPage--; loadUsers()">上一页</button>
          <span class="page-info">第 {{ userPage + 1 }} / {{ userTotalPages }} 页（共 {{ userTotalElements }} 条）</span>
          <button class="btn btn-sm btn-outline" :disabled="userPage >= userTotalPages - 1" @click="userPage++; loadUsers()">下一页</button>
        </div>
      </div>
    </div>

    <!-- ==================== 任务队列 ==================== -->
    <div v-if="activeTab === 'tasks'" class="card" style="margin-top:16px">
      <!-- 筛选栏 -->
      <div class="filter-bar">
        <input class="input input-sm" type="number" v-model="taskFilterUserId" placeholder="用户ID筛选" style="width:140px"
          @keyup.enter="applyTaskFilter" />
        <select class="input input-sm" v-model="taskFilterStatus" style="width:140px">
          <option value="">全部状态</option>
          <option value="PENDING">PENDING</option>
          <option value="RUNNING">RUNNING</option>
          <option value="COMPLETED">COMPLETED</option>
          <option value="FAILED">FAILED</option>
        </select>
        <button class="btn btn-sm btn-primary" @click="applyTaskFilter">筛选</button>
        <button class="btn btn-sm btn-outline" @click="resetTaskFilter">重置</button>
      </div>

      <div v-if="loadingTasks" class="loading"><div class="spinner"></div> 加载中...</div>
      <div v-else>
        <div class="table-wrapper">
          <table class="table">
            <thead>
              <tr>
                <th>ID</th>
                <th>用户ID</th>
                <th>类型</th>
                <th>状态</th>
                <th>进度</th>
                <th>创建时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="t in tasks" :key="t.id">
                <td>{{ t.id }}</td>
                <td>{{ t.userId }}</td>
                <td><span class="tag">{{ t.taskType }}</span></td>
                <td><span class="tag" :class="statusClass(t.status)">{{ t.status }}</span></td>
                <td>
                  <div class="progress-bar" style="width:80px">
                    <div class="progress-bar-fill" :style="{ width: t.progress + '%' }"></div>
                  </div>
                </td>
                <td style="font-size:12px;color:var(--color-text-tertiary)">{{ formatTime(t.createdAt) }}</td>
                <td>
                  <button v-if="t.status === 'RUNNING' || t.status === 'PENDING'"
                    class="btn btn-danger btn-sm"
                    @click="handleFailTask(t.id)">
                    标记失败
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <!-- 分页 -->
        <div class="pagination" v-if="taskTotalPages > 1">
          <button class="btn btn-sm btn-outline" :disabled="taskPage <= 0" @click="taskPage--; loadTasks()">上一页</button>
          <span class="page-info">第 {{ taskPage + 1 }} / {{ taskTotalPages }} 页（共 {{ taskTotalElements }} 条）</span>
          <button class="btn btn-sm btn-outline" :disabled="taskPage >= taskTotalPages - 1" @click="taskPage++; loadTasks()">下一页</button>
        </div>
      </div>
    </div>

    <!-- ==================== 配额设置弹窗 ==================== -->
    <div v-if="quotaDialogVisible" class="modal-overlay" @click.self="quotaDialogVisible = false">
      <div class="modal-card" style="background:#ffffff">
        <h3 class="modal-title">设置配额 - {{ quotaTarget?.username }}</h3>
        <p class="modal-hint">当前角色默认：导入 {{ getRoleDefaultImport(quotaTarget) }} 次/天，AI问答 {{ getRoleDefaultChat(quotaTarget) }} 次/天。留空则使用角色默认值。</p>
        <div class="form-group">
          <label class="form-label">每日导入上限</label>
          <input class="input" type="number" v-model="quotaForm.importLimit"
            :placeholder="'角色默认 ' + getRoleDefaultImport(quotaTarget) + ' 次/天'" min="0" />
        </div>
        <div class="form-group">
          <label class="form-label">每日 AI 问答上限</label>
          <input class="input" type="number" v-model="quotaForm.chatLimit"
            :placeholder="'角色默认 ' + getRoleDefaultChat(quotaTarget) + ' 次/天'" min="0" />
        </div>
        <div class="modal-actions">
          <button class="btn btn-outline" @click="quotaDialogVisible = false">取消</button>
          <button class="btn btn-primary" @click="handleSetQuota">保存</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { adminApi } from '@/api'

const activeTab = ref('users')
const users = ref<any[]>([])
const tasks = ref<any[]>([])
const loadingUsers = ref(false)
const loadingTasks = ref(false)

// 用户分页
const userPage = ref(0)
const userTotalPages = ref(0)
const userTotalElements = ref(0)
const USER_PAGE_SIZE = 20

// 任务分页 + 筛选
const taskPage = ref(0)
const taskTotalPages = ref(0)
const taskTotalElements = ref(0)
const TASK_PAGE_SIZE = 20
const taskFilterUserId = ref<number | null>(null)
const taskFilterStatus = ref('')

// 配额弹窗
const quotaDialogVisible = ref(false)
const quotaTarget = ref<any>(null)
const quotaForm = ref({ importLimit: null as number | null, chatLimit: null as number | null })

function roleClass(role: string) {
  if (role === 'ADMIN') return 'tag-error'
  if (role === 'PREMIUM') return 'tag-accent'
  return ''
}

function statusClass(status: string) {
  if (status === 'COMPLETED') return 'tag-success'
  if (status === 'FAILED') return 'tag-error'
  return 'tag-accent'
}

function formatTime(t: string) {
  if (!t) return ''
  return new Date(t).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai' })
}

// ==================== 用户管理 ====================
async function loadUsers() {
  loadingUsers.value = true
  try {
    const res = await adminApi.listUsers(userPage.value, USER_PAGE_SIZE)
    if (res.data.code === 200) {
      const data = res.data.data
      if (data && data.content) {
        users.value = data.content
        userTotalPages.value = data.totalPages
        userTotalElements.value = data.totalElements
      } else if (Array.isArray(data)) {
        users.value = data
        userTotalPages.value = 1
        userTotalElements.value = data.length
      }
    }
  } catch { /* ignore */ }
  finally { loadingUsers.value = false }
}

async function handleRoleChange(userId: number, role: string) {
  try {
    await adminApi.updateUserRole(userId, role)
    loadUsers()
  } catch (e: any) {
    alert('修改角色失败')
  }
}

async function handleToggleBan(userId: number, banned: boolean) {
  const action = banned ? '封禁' : '解封'
  if (!confirm(`确定要${action}该用户吗？`)) return
  try {
    await adminApi.toggleBan(userId, banned)
    loadUsers()
  } catch (e: any) {
    alert(`${action}失败`)
  }
}

function getRoleDefaultImport(user: any) {
  if (!user) return 3
  return (user.role === 'PREMIUM' || user.role === 'ADMIN') ? 20 : 3
}
function getRoleDefaultChat(user: any) {
  if (!user) return 20
  return (user.role === 'PREMIUM' || user.role === 'ADMIN') ? 200 : 20
}

function openQuotaDialog(user: any) {
  quotaTarget.value = user
  quotaForm.value = {
    importLimit: user.customImportLimit ?? null,
    chatLimit: user.customChatLimit ?? null
  }
  quotaDialogVisible.value = true
}

async function handleSetQuota() {
  if (!quotaTarget.value) return
  try {
    await adminApi.setUserQuota(quotaTarget.value.id, quotaForm.value.importLimit, quotaForm.value.chatLimit)
    quotaDialogVisible.value = false
    loadUsers()
  } catch (e: any) {
    alert('设置配额失败')
  }
}

// ==================== 任务队列 ====================
async function loadTasks() {
  loadingTasks.value = true
  try {
    const res = await adminApi.listTasks(
      taskPage.value, TASK_PAGE_SIZE,
      taskFilterUserId.value || undefined,
      taskFilterStatus.value || undefined
    )
    if (res.data.code === 200) {
      const data = res.data.data
      if (data && data.content) {
        tasks.value = data.content
        taskTotalPages.value = data.totalPages
        taskTotalElements.value = data.totalElements
      } else if (Array.isArray(data)) {
        tasks.value = data
        taskTotalPages.value = 1
        taskTotalElements.value = data.length
      }
    }
  } catch { /* ignore */ }
  finally { loadingTasks.value = false }
}

function applyTaskFilter() {
  taskPage.value = 0
  loadTasks()
}

function resetTaskFilter() {
  taskFilterUserId.value = null
  taskFilterStatus.value = ''
  taskPage.value = 0
  loadTasks()
}

async function handleFailTask(taskId: number) {
  try {
    await adminApi.markTaskFailed(taskId, '管理员手动标记')
    loadTasks()
  } catch (e: any) {
    alert('操作失败')
  }
}

watch(activeTab, (val) => {
  if (val === 'users') loadUsers()
  else loadTasks()
})

onMounted(() => {
  loadUsers()
})
</script>

<style scoped>
.tabs {
  display: flex;
  gap: var(--space-xs);
  border-bottom: 1px solid var(--color-border);
  padding-bottom: -1px;
}
.tab {
  padding: 8px 16px;
  font-size: 14px;
  font-weight: 500;
  color: var(--color-text-secondary);
  background: none;
  border: none;
  border-bottom: 2px solid transparent;
  cursor: pointer;
  transition: all 0.15s;
}
.tab:hover {
  color: var(--color-text-primary);
}
.tab.active {
  color: var(--color-accent);
  border-bottom-color: var(--color-accent);
}

/* 封禁行样式 */
.row-banned {
  opacity: 0.6;
  background: rgba(255, 0, 0, 0.03);
}

/* 操作列 */
.action-cell {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  align-items: center;
}

/* 筛选栏 */
.filter-bar {
  display: flex;
  gap: 8px;
  align-items: center;
  padding: 12px 0;
  border-bottom: 1px solid var(--color-border);
  margin-bottom: 12px;
}

/* 分页 */
.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 16px 0 8px;
}
.page-info {
  font-size: 13px;
  color: var(--color-text-secondary);
}

/* 配额徽标 */
.quota-badge {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 10px;
  background: var(--color-bg-secondary);
  color: var(--color-text-secondary);
}

/* 弹窗 */
.modal-overlay {
  position: fixed;
  top: 0; left: 0; right: 0; bottom: 0;
  background: rgba(0,0,0,0.55);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 9999;
}
.modal-card {
  background: #ffffff !important;
  color: #1a1a2e;
  border-radius: 12px;
  padding: 24px;
  width: 440px;
  max-width: 90vw;
  box-shadow: 0 8px 32px rgba(0,0,0,0.25);
  position: relative;
  z-index: 1001;
}
.modal-title {
  margin: 0 0 8px;
  font-size: 18px;
  font-weight: 600;
  color: var(--color-text-primary);
}
.modal-hint {
  font-size: 12px;
  color: var(--color-text-tertiary);
  margin: 0 0 16px;
  line-height: 1.5;
}
.modal-actions {
  display: flex;
  gap: 12px;
  justify-content: flex-end;
  margin-top: 20px;
}

/* 小号输入框 */
.input-sm {
  padding: 4px 8px;
  font-size: 12px;
}

/* 小号按钮 */
.btn-sm {
  padding: 4px 10px;
  font-size: 12px;
}

/* 表单 */
.form-group {
  margin-bottom: var(--space-md);
}
.form-label {
  display: block;
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text-secondary);
  margin-bottom: 4px;
}
</style>
