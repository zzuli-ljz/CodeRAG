<template>
  <div class="page-container">
    <div class="page-header">
      <h1 class="page-title">学习打卡 & 成就</h1>
    </div>

    <div v-if="loading" class="loading"><div class="spinner"></div> 加载中...</div>

    <template v-else>
      <!-- 打卡区域 -->
      <div class="card checkin-card">
        <div class="checkin-main">
          <div class="streak-display">
            <div class="streak-flame" :class="{ active: streakData?.todayChecked }">🔥</div>
            <div class="streak-info">
              <div class="streak-num">{{ streakData?.currentStreak || 0 }}</div>
              <div class="streak-label">连续学习天数</div>
            </div>
          </div>
          <div class="checkin-action">
            <button
              class="btn btn-primary btn-lg"
              @click="handleCheckin"
              :disabled="streakData?.todayChecked || checking"
            >
              {{ checking ? '打卡中...' : streakData?.todayChecked ? '今日已打卡 ✅' : '今日打卡' }}
            </button>
            <div class="checkin-hint" v-if="!streakData?.todayChecked">每天打卡，解锁更多成就！</div>
          </div>
        </div>

        <!-- 本周日历 -->
        <div class="week-calendar">
          <div
            v-for="(day, idx) in weekDays"
            :key="idx"
            class="day-cell"
            :class="{
              today: day.isToday,
              checked: day.checked,
              future: day.isFuture
            }"
          >
            <div class="day-name">{{ day.label }}</div>
            <div class="day-dot">{{ day.checked ? '🔥' : day.isFuture ? '·' : '○' }}</div>
            <div class="day-date">{{ day.date }}</div>
          </div>
        </div>
      </div>

      <!-- 成就列表 -->
      <div class="card" style="margin-top:16px">
        <h3 style="margin-bottom:16px">🏆 成就徽章</h3>
        <div class="achievement-grid" v-if="achievements.length">
          <div
            v-for="a in achievements"
            :key="a.key"
            class="achievement-item"
            :class="{ earned: a.earned, locked: !a.earned }"
          >
            <div class="achievement-icon">{{ a.icon || '🏅' }}</div>
            <div class="achievement-info">
              <div class="achievement-name">{{ a.name }}</div>
              <div class="achievement-desc">{{ a.description }}</div>
              <div class="achievement-date" v-if="a.earnedAt">
                {{ formatDate(a.earnedAt) }} 获得
              </div>
            </div>
          </div>
        </div>
        <div v-else class="empty-state">暂无成就，开始学习来解锁吧！</div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { achievementApi } from '@/api'

const loading = ref(true)
const checking = ref(false)
const streakData = ref<any>(null)
const achievements = ref<any[]>([])

// 本周日历
const weekDays = computed(() => {
  const days = []
  const today = new Date()
  const checkedDates: Set<string> = new Set()

  if (streakData.value?.recentDates) {
    for (const d of streakData.value.recentDates) {
      if (d.checked) {
        checkedDates.add(d.date)
      }
    }
  }

  // 本周一
  const monday = new Date(today)
  const dayOfWeek = today.getDay() || 7
  monday.setDate(today.getDate() - dayOfWeek + 1)

  const labels = ['一', '二', '三', '四', '五', '六', '日']
  for (let i = 0; i < 7; i++) {
    const d = new Date(monday)
    d.setDate(monday.getDate() + i)
    const dateStr = d.toISOString().slice(0, 10)
    const isToday = dateStr === today.toISOString().slice(0, 10)
    const isFuture = d > today

    days.push({
      label: labels[i],
      date: d.getDate(),
      checked: checkedDates.has(dateStr),
      isToday,
      isFuture
    })
  }
  return days
})

function formatDate(t: string) {
  return new Date(t).toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' })
}

async function loadData() {
  loading.value = true
  try {
    const [streakRes, achRes] = await Promise.all([
      achievementApi.getStreak(),
      achievementApi.getAchievements()
    ])
    if (streakRes.data.code === 200) streakData.value = streakRes.data.data
    if (achRes.data.code === 200) achievements.value = achRes.data.data || []
  } catch { /* ignore */ }
  finally { loading.value = false }
}

async function handleCheckin() {
  checking.value = true
  try {
    const res = await achievementApi.checkin()
    if (res.data.code === 200) {
      // 后端返回 { streak: {...}, newAchievements: [...] }
      const data = res.data.data
      if (data && data.streak) {
        streakData.value = data.streak
      }
      if (data && data.newAchievements && data.newAchievements.length > 0) {
        const names = data.newAchievements.map((a: any) => a.achievementName || a.name).join('、')
        alert('🎉 恭喜获得新成就：' + names)
      }
      // 打卡成功后重新加载成就列表
      await loadAchievements()
    }
  } catch (e: any) {
    alert('打卡失败: ' + (e.response?.data?.message || e.message))
  } finally {
    checking.value = false
  }
}

async function loadAchievements() {
  try {
    const achRes = await achievementApi.getAchievements()
    if (achRes.data.code === 200) achievements.value = achRes.data.data || []
  } catch { /* ignore */ }
}

onMounted(loadData)
</script>

<style scoped>
.checkin-card {
  padding: 24px;
  text-align: center;
}
.checkin-main {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 48px;
  flex-wrap: wrap;
  margin-bottom: 24px;
}
.streak-display {
  display: flex;
  align-items: center;
  gap: 16px;
}
.streak-flame {
  font-size: 48px;
  opacity: 0.3;
  transition: all 0.5s;
}
.streak-flame.active {
  opacity: 1;
  animation: pulse 1.5s ease-in-out infinite;
}
@keyframes pulse {
  0%, 100% { transform: scale(1); }
  50% { transform: scale(1.15); }
}
.streak-info {
  text-align: left;
}
.streak-num {
  font-size: 36px;
  font-weight: 800;
  font-family: var(--font-mono);
  background: linear-gradient(135deg, #f59e0b, #ef4444);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}
.streak-label {
  font-size: 13px;
  color: var(--color-text-tertiary);
}
.checkin-action {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}
.btn-lg {
  padding: 14px 36px;
  font-size: 16px;
}
.checkin-hint {
  font-size: 12px;
  color: var(--color-text-tertiary);
}

/* 周日历 */
.week-calendar {
  display: flex;
  justify-content: center;
  gap: 8px;
}
.day-cell {
  width: 52px;
  padding: 10px 4px;
  border-radius: var(--radius-md);
  background: var(--color-bg-secondary);
  text-align: center;
  transition: all 0.2s;
}
.day-cell.today {
  background: var(--color-accent-light);
  border: 2px solid var(--color-accent);
}
.day-cell.checked {
  background: #fef3c7;
}
.day-cell.future {
  opacity: 0.4;
}
.day-name {
  font-size: 11px;
  color: var(--color-text-tertiary);
  margin-bottom: 4px;
}
.day-dot {
  font-size: 18px;
}
.day-date {
  font-size: 12px;
  font-weight: 600;
  margin-top: 2px;
}

/* 成就 */
.achievement-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 12px;
}
.achievement-item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 16px;
  border-radius: var(--radius-md);
  border: 1px solid var(--color-border);
  transition: all 0.2s;
}
.achievement-item.earned {
  background: var(--color-bg);
  border-color: #f59e0b;
}
.achievement-item.locked {
  opacity: 0.5;
  filter: grayscale(1);
}
.achievement-icon {
  font-size: 32px;
  flex-shrink: 0;
}
.achievement-name {
  font-size: 14px;
  font-weight: 600;
}
.achievement-desc {
  font-size: 12px;
  color: var(--color-text-tertiary);
  margin-top: 2px;
}
.achievement-date {
  font-size: 11px;
  color: var(--color-accent);
  margin-top: 4px;
}
</style>
