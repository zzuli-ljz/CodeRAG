<template>
  <div class="graph-page">
    <!-- 左侧：图谱列表 -->
    <div class="graph-sidebar">
      <div class="sidebar-header">
        <h3>图谱历史</h3>
        <button class="btn btn-primary btn-sm" @click="handleBuild" :disabled="building">
          {{ building ? '构建中...' : '新图谱' }}
        </button>
      </div>
      <div class="sidebar-list">
        <div
          v-for="(item, idx) in history"
          :key="item.id"
          class="history-item"
          :class="{ active: selectedId === item.id }"
          @click="selectItem(item)"
        >
          <div class="history-title">第 {{ history.length - idx }} 次分析</div>
          <div class="history-time">{{ formatTime(item.createdAt) }}</div>
        </div>
        <div v-if="!history.length && !building" class="empty-state" style="padding:24px">
          暂无图谱记录
        </div>
      </div>
    </div>

    <!-- 右侧：图谱可视化 -->
    <div class="graph-main" v-if="graphData">
      <div class="graph-toolbar">
        <span class="tag tag-accent">{{ graphData.round ? `第 ${graphData.round} 次` : '图谱' }}</span>
        <span class="graph-stats">{{ nodeCount }} 节点 · {{ edgeCount }} 边</span>
        <div class="toolbar-actions">
          <button class="btn btn-secondary btn-sm" @click="zoomIn">+</button>
          <span class="zoom-label">{{ Math.round(scale * 100) }}%</span>
          <button class="btn btn-secondary btn-sm" @click="zoomOut">-</button>
          <button class="btn btn-secondary btn-sm" @click="resetView">重置视图</button>
          <select v-model="filterType" class="input" style="width:auto;padding:4px 8px;font-size:12px">
            <option value="all">全部类型</option>
            <option value="file">文件</option>
            <option value="class">类/结构体</option>
            <option value="function">函数</option>
          </select>
          <select v-model="filterGroup" class="input" style="width:auto;padding:4px 8px;font-size:12px">
            <option value="all">全部模块</option>
            <option v-for="g in groups" :key="g" :value="g">{{ g }}</option>
          </select>
        </div>
      </div>

      <!-- Canvas 图谱 -->
      <div class="graph-canvas" ref="canvasWrapRef"
        @wheel.prevent="handleWheel"
        @mousedown="handleMouseDown"
        @mousemove="handleMouseMove"
        @mouseup="handleMouseUp"
        @mouseleave="handleMouseUp"
        @dblclick="handleDblClick"
      >
        <canvas ref="canvasRef"></canvas>
      </div>

      <!-- 选中节点详情 -->
      <div v-if="selectedNode" class="node-detail">
        <div class="detail-header">
          <span class="tag" :class="nodeTypeTag(selectedNode.type)">{{ nodeTypeLabel(selectedNode.type) }}</span>
          <strong>{{ selectedNode.label }}</strong>
        </div>
        <div class="detail-info" v-if="selectedNode.file">
          <span class="detail-label">文件：</span>
          <code>{{ selectedNode.file }}</code>
        </div>
        <div class="detail-info" v-if="selectedNode.group">
          <span class="detail-label">模块：</span>
          <span>{{ selectedNode.group }}</span>
        </div>
      </div>
    </div>

    <!-- 构建中 -->
    <div v-else-if="building" class="graph-loading">
      <div class="spinner"></div>
      <p>正在构建代码知识图谱...</p>
    </div>

    <!-- 空状态 -->
    <div v-else class="graph-empty empty-state">
      <p>选择左侧的历史记录查看，或点击「新图谱」构建</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { useRoute } from 'vue-router'
import { graphApi } from '@/api'

const route = useRoute()
const repoId = Number(route.params.repoId)

const history = ref<any[]>([])
const selectedId = ref<number | null>(null)
const graphData = ref<any>(null)
const building = ref(false)
const filterType = ref('all')
const filterGroup = ref('all')
const selectedNode = ref<any>(null)

// Canvas
const canvasRef = ref<HTMLCanvasElement | null>(null)
const canvasWrapRef = ref<HTMLDivElement | null>(null)
let ctx: CanvasRenderingContext2D | null = null
let dpr = 1

// 力导向布局数据
interface LayoutNode {
  id: string; label: string; type: string; file?: string; group?: string;
  x: number; y: number; vx: number; vy: number;
}
interface LayoutEdge {
  source: LayoutNode; target: LayoutNode; relation: string;
  color: string; width: number; opacity: number;
}
const layoutNodes = ref<LayoutNode[]>([])
const layoutEdges = ref<LayoutEdge[]>([])

// 缩放与拖拽
const scale = ref(1)
const panX = ref(0)
const panY = ref(0)
const dragging = ref(false)
let dragStartX = 0
let dragStartY = 0
let dragStartPanX = 0
let dragStartPanY = 0

const nodeCount = computed(() => layoutNodes.value.length)
const edgeCount = computed(() => layoutEdges.value.length)

const groups = computed(() => {
  const gs = new Set<string>()
  layoutNodes.value.forEach(n => { if (n.group) gs.add(n.group) })
  return Array.from(gs).sort()
})

const filteredNodes = computed(() => {
  let nodes = layoutNodes.value
  if (filterType.value !== 'all') nodes = nodes.filter(n => n.type === filterType.value)
  if (filterGroup.value !== 'all') nodes = nodes.filter(n => n.group === filterGroup.value)
  return nodes
})

const filteredEdges = computed(() => {
  const nodeIds = new Set(filteredNodes.value.map(n => n.id))
  return layoutEdges.value.filter(e => nodeIds.has(e.source.id) && nodeIds.has(e.target.id))
})

function nodeRadius(node: LayoutNode): number {
  switch (node.type) {
    case 'file': return 14
    case 'class': return 10
    case 'function': return 6
    default: return 8
  }
}

function nodeColor(node: LayoutNode): string {
  const colors: Record<string, string> = {
    file: '#2d3a8c', class: '#2d7d46', function: '#b08a1e',
    interface: '#8b5cf6', struct: '#06b6d4'
  }
  return colors[node.type] || '#8b8fa3'
}

function nodeTypeTag(type: string): string {
  const tags: Record<string, string> = {
    file: 'tag-accent', class: 'tag-success', function: 'tag',
    interface: 'tag-accent', struct: 'tag-accent'
  }
  return tags[type] || 'tag'
}

function nodeTypeLabel(type: string): string {
  const labels: Record<string, string> = {
    file: '文件', class: '类', function: '函数',
    interface: '接口', struct: '结构体'
  }
  return labels[type] || type
}

function truncateLabel(label: string): string {
  return label.length > 18 ? label.substring(0, 16) + '..' : label
}

function formatTime(t: string) {
  if (!t) return ''
  return new Date(t).toLocaleString('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit'
  })
}

// ==================== Canvas 渲染 ====================

function initCanvas() {
  const canvas = canvasRef.value
  const wrap = canvasWrapRef.value
  if (!canvas || !wrap) return
  dpr = window.devicePixelRatio || 1
  const w = wrap.clientWidth
  const h = wrap.clientHeight
  canvas.width = w * dpr
  canvas.height = h * dpr
  canvas.style.width = w + 'px'
  canvas.style.height = h + 'px'
  ctx = canvas.getContext('2d')!
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
}

function worldToScreen(wx: number, wy: number): [number, number] {
  const canvas = canvasRef.value
  if (!canvas) return [0, 0]
  const cx = canvas.clientWidth / 2
  const cy = canvas.clientHeight / 2
  return [cx + (wx + panX.value) * scale.value, cy + (wy + panY.value) * scale.value]
}

function screenToWorld(sx: number, sy: number): [number, number] {
  const canvas = canvasRef.value
  if (!canvas) return [0, 0]
  const cx = canvas.clientWidth / 2
  const cy = canvas.clientHeight / 2
  return [(sx - cx) / scale.value - panX.value, (sy - cy) / scale.value - panY.value]
}

function draw() {
  if (!ctx) return
  const canvas = canvasRef.value
  if (!canvas) return
  const w = canvas.clientWidth
  const h = canvas.clientHeight

  ctx.clearRect(0, 0, w, h)

  // 背景网格
  ctx.strokeStyle = '#e8e8ec'
  ctx.lineWidth = 0.5
  const gridSize = 50 * scale.value
  const [ox, oy] = worldToScreen(0, 0)
  const startX = ((ox % gridSize) + gridSize) % gridSize
  const startY = ((oy % gridSize) + gridSize) % gridSize
  for (let x = startX; x < w; x += gridSize) {
    ctx.beginPath(); ctx.moveTo(x, 0); ctx.lineTo(x, h); ctx.stroke()
  }
  for (let y = startY; y < h; y += gridSize) {
    ctx.beginPath(); ctx.moveTo(0, y); ctx.lineTo(w, y); ctx.stroke()
  }

  const nodes = filteredNodes.value
  const edges = filteredEdges.value

  // 视口裁剪
  const margin = 50
  const [vx1, vy1] = screenToWorld(-margin, -margin)
  const [vx2, vy2] = screenToWorld(w + margin, h + margin)

  const visibleNodeIds = new Set<string>()
  for (const node of nodes) {
    if (node.x >= vx1 && node.x <= vx2 && node.y >= vy1 && node.y <= vy2) {
      visibleNodeIds.add(node.id)
    }
  }

  // 画边
  for (const edge of edges) {
    if (!visibleNodeIds.has(edge.source.id) && !visibleNodeIds.has(edge.target.id)) continue
    const [x1, y1] = worldToScreen(edge.source.x, edge.source.y)
    const [x2, y2] = worldToScreen(edge.target.x, edge.target.y)
    ctx.beginPath()
    ctx.moveTo(x1, y1)
    ctx.lineTo(x2, y2)
    ctx.strokeStyle = edge.color
    ctx.lineWidth = edge.width * scale.value
    ctx.globalAlpha = edge.opacity
    ctx.stroke()
    ctx.globalAlpha = 1
  }

  // 画节点
  const fontSize = Math.max(8, 10 * Math.min(scale.value, 2.5))
  for (const node of nodes) {
    if (!visibleNodeIds.has(node.id)) continue
    const [sx, sy] = worldToScreen(node.x, node.y)
    const r = Math.max(2.5, nodeRadius(node) * Math.min(scale.value, 2.5))

    ctx.beginPath()
    ctx.arc(sx, sy, r, 0, Math.PI * 2)
    ctx.fillStyle = nodeColor(node)
    ctx.fill()
    ctx.strokeStyle = selectedNode.value?.id === node.id ? '#2d3a8c' : '#fff'
    ctx.lineWidth = selectedNode.value?.id === node.id ? 2.5 : 1
    ctx.stroke()

    if (scale.value > 0.35) {
      ctx.font = `${fontSize}px -apple-system, BlinkMacSystemFont, sans-serif`
      ctx.fillStyle = selectedNode.value?.id === node.id ? '#2d3a8c' : '#5a5d6a'
      ctx.textAlign = 'center'
      ctx.textBaseline = 'top'
      ctx.fillText(truncateLabel(node.label), sx, sy + r + 3)
    }
  }
}

// ==================== 交互 ====================

function handleWheel(e: WheelEvent) {
  const rect = canvasRef.value?.getBoundingClientRect()
  if (!rect) return
  const mx = e.clientX - rect.left
  const my = e.clientY - rect.top

  const [wx, wy] = screenToWorld(mx, my)
  const delta = e.deltaY > 0 ? 0.9 : 1.1
  const newScale = Math.max(0.1, Math.min(5, scale.value * delta))

  const cx = rect.width / 2
  const cy = rect.height / 2
  panX.value = (mx - cx) / newScale - wx
  panY.value = (my - cy) / newScale - wy
  scale.value = newScale

  draw()
}

function handleMouseDown(e: MouseEvent) {
  dragging.value = true
  dragStartX = e.clientX
  dragStartY = e.clientY
  dragStartPanX = panX.value
  dragStartPanY = panY.value
}

function handleMouseMove(e: MouseEvent) {
  if (!dragging.value) return
  const dx = e.clientX - dragStartX
  const dy = e.clientY - dragStartY
  panX.value = dragStartPanX + dx / scale.value
  panY.value = dragStartPanY + dy / scale.value
  draw()
}

function handleMouseUp(e: MouseEvent) {
  if (!dragging.value) return
  dragging.value = false
  const dx = e.clientX - dragStartX
  const dy = e.clientY - dragStartY
  if (Math.abs(dx) < 3 && Math.abs(dy) < 3) {
    handleClick(e)
  }
}

function handleClick(e: MouseEvent) {
  const rect = canvasRef.value?.getBoundingClientRect()
  if (!rect) return
  const mx = e.clientX - rect.left
  const my = e.clientY - rect.top
  const [wx, wy] = screenToWorld(mx, my)

  let best: LayoutNode | null = null
  let bestDist = 20 / scale.value
  for (const node of filteredNodes.value) {
    const dx = node.x - wx
    const dy = node.y - wy
    const dist = Math.sqrt(dx * dx + dy * dy)
    if (dist < bestDist) {
      bestDist = dist
      best = node
    }
  }
  selectedNode.value = best
  draw()
}

function handleDblClick(e: MouseEvent) {
  const rect = canvasRef.value?.getBoundingClientRect()
  if (!rect) return
  const mx = e.clientX - rect.left
  const my = e.clientY - rect.top
  const [wx, wy] = screenToWorld(mx, my)

  scale.value = Math.min(3, scale.value * 1.5)
  const cx = rect.width / 2
  const cy = rect.height / 2
  panX.value = (mx - cx) / scale.value - wx
  panY.value = (my - cy) / scale.value - wy
  draw()
}

function zoomIn() {
  scale.value = Math.min(5, scale.value * 1.3)
  draw()
}

function zoomOut() {
  scale.value = Math.max(0.1, scale.value / 1.3)
  draw()
}

function resetView() {
  scale.value = 1
  panX.value = 0
  panY.value = 0
  selectedNode.value = null
  draw()
}

// 窗口大小变化
let resizeTimer: ReturnType<typeof setTimeout> | null = null
function onResize() {
  if (resizeTimer) clearTimeout(resizeTimer)
  resizeTimer = setTimeout(() => {
    initCanvas()
    draw()
  }, 100)
}

// 过滤器变化时重绘
watch([filterType, filterGroup], () => {
  nextTick(() => draw())
})

// ==================== 数据加载 ====================

function selectItem(item: any) {
  selectedId.value = item.id
  selectedNode.value = null
  parseGraphData(item.graphData)
}

async function handleBuild() {
  building.value = true
  try {
    const res = await graphApi.build(repoId, true)
    if (res.data.code === 200) {
      const newItem = res.data.data
      history.value.unshift(newItem)
      selectItem(newItem)
    }
  } catch (e: any) {
    alert('图谱构建失败: ' + (e.response?.data?.message || e.message))
  } finally {
    building.value = false
  }
}

function parseGraphData(jsonStr: string) {
  try {
    const data = JSON.parse(jsonStr)
    graphData.value = data
    const rawNodes: any[] = data.nodes || []
    const rawEdges: any[] = data.edges || []

    const nodeMap = new Map<string, LayoutNode>()
    rawNodes.forEach((n: any) => {
      nodeMap.set(n.id, {
        id: n.id,
        label: n.label || n.id,
        type: n.type || 'unknown',
        file: n.file,
        group: n.group,
        x: (Math.random() - 0.5) * 400,
        y: (Math.random() - 0.5) * 300,
        vx: 0, vy: 0
      })
    })

    const edges: LayoutEdge[] = []
    rawEdges.forEach((e: any) => {
      const source = nodeMap.get(e.source)
      const target = nodeMap.get(e.target)
      if (source && target) {
        edges.push({
          source, target,
          relation: e.relation || 'related',
          color: e.relation === 'imports' ? '#2d3a8c' :
                 e.relation === 'extends' ? '#2d7d46' :
                 e.relation === 'contains' ? '#b08a1e' : '#e2e4e8',
          width: e.relation === 'extends' ? 2 : 1,
          opacity: e.relation === 'contains' ? 0.3 : 0.6
        })
      }
    })

    layoutNodes.value = Array.from(nodeMap.values())
    layoutEdges.value = edges

    nextTick(() => {
      initCanvas()
      draw()
      runForceLayout()
    })
  } catch (e) {
    console.error('图谱数据解析失败:', e)
  }
}

// ==================== 力导向布局（异步） ====================

let layoutAnimFrame: number | null = null

function runForceLayout() {
  if (layoutAnimFrame !== null) {
    cancelAnimationFrame(layoutAnimFrame)
    layoutAnimFrame = null
  }

  const nodes = layoutNodes.value
  const edges = layoutEdges.value
  if (nodes.length === 0) return

  // 大数据量时减少迭代
  const maxIterations = nodes.length < 50 ? 200 : (nodes.length < 200 ? 100 : 40)
  const repulsion = nodes.length > 300 ? 5000 : 3000
  const attraction = 0.005
  const damping = 0.85
  let iter = 0

  function step() {
    // 斥力
    for (let i = 0; i < nodes.length; i++) {
      for (let j = i + 1; j < nodes.length; j++) {
        const dx = nodes[j].x - nodes[i].x
        const dy = nodes[j].y - nodes[i].y
        const dist = Math.sqrt(dx * dx + dy * dy) || 1
        const force = repulsion / (dist * dist)
        const fx = (dx / dist) * force
        const fy = (dy / dist) * force
        nodes[i].vx -= fx
        nodes[i].vy -= fy
        nodes[j].vx += fx
        nodes[j].vy += fy
      }
    }

    // 引力
    for (const edge of edges) {
      const dx = edge.target.x - edge.source.x
      const dy = edge.target.y - edge.source.y
      const dist = Math.sqrt(dx * dx + dy * dy) || 1
      const force = dist * attraction
      const fx = (dx / dist) * force
      const fy = (dy / dist) * force
      edge.source.vx += fx
      edge.source.vy += fy
      edge.target.vx -= fx
      edge.target.vy -= fy
    }

    // 中心引力
    for (const node of nodes) {
      node.vx -= node.x * 0.001
      node.vy -= node.y * 0.001
    }

    // 更新位置
    for (const node of nodes) {
      node.vx *= damping
      node.vy *= damping
      node.x += node.vx
      node.y += node.vy
    }

    // 每 8 帧重绘一次（大数据量时降低重绘频率）
    const drawInterval = nodes.length > 300 ? 8 : 5
    if (iter % drawInterval === 0) {
      draw()
    }

    iter++
    if (iter < maxIterations) {
      layoutAnimFrame = requestAnimationFrame(step)
    } else {
      draw()
      layoutAnimFrame = null
    }
  }

  layoutAnimFrame = requestAnimationFrame(step)
}

// ==================== 生命周期 ====================

onMounted(async () => {
  window.addEventListener('resize', onResize)

  try {
    const res = await graphApi.getHistory(repoId)
    if (res.data.code === 200) {
      const pageData = Array.isArray(res.data.data)
        ? res.data.data
        : res.data.data?.content || []
      history.value = pageData
      if (history.value.length > 0) {
        selectItem(history.value[0])
      }
    }
  } catch {
    try {
      const res = await graphApi.getLatest(repoId)
      if (res.data.code === 200 && res.data.data) {
        history.value = [res.data.data]
        selectItem(res.data.data)
      }
    } catch {}
  }
})

onUnmounted(() => {
  window.removeEventListener('resize', onResize)
  if (layoutAnimFrame !== null) {
    cancelAnimationFrame(layoutAnimFrame)
    layoutAnimFrame = null
  }
  if (resizeTimer) clearTimeout(resizeTimer)
})
</script>

<style scoped>
.graph-page {
  display: flex;
  height: calc(100vh - 56px);
}
.graph-sidebar {
  width: 240px;
  border-right: 1px solid var(--color-border);
  background: var(--color-bg);
  display: flex;
  flex-direction: column;
}
.sidebar-header {
  padding: var(--space-md) var(--space-lg);
  border-bottom: 1px solid var(--color-border-light);
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.sidebar-header h3 {
  font-size: 14px;
  font-weight: 600;
}
.sidebar-list {
  flex: 1;
  overflow-y: auto;
}
.history-item {
  padding: var(--space-md) var(--space-lg);
  border-bottom: 1px solid var(--color-border-light);
  cursor: pointer;
  transition: background 0.1s;
}
.history-item:hover { background: var(--color-bg-secondary); }
.history-item.active {
  background: var(--color-accent-light);
  border-left: 3px solid var(--color-accent);
}
.history-title { font-size: 13px; font-weight: 500; }
.history-time {
  font-size: 11px;
  color: var(--color-text-tertiary);
  margin-top: 2px;
}
.graph-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.graph-toolbar {
  display: flex;
  align-items: center;
  gap: var(--space-md);
  padding: var(--space-sm) var(--space-lg);
  border-bottom: 1px solid var(--color-border-light);
  background: var(--color-bg);
  flex-shrink: 0;
}
.graph-stats {
  font-size: 12px;
  color: var(--color-text-tertiary);
}
.zoom-label {
  font-size: 12px;
  color: var(--color-text-secondary);
  min-width: 40px;
  text-align: center;
  font-variant-numeric: tabular-nums;
}
.toolbar-actions {
  margin-left: auto;
  display: flex;
  gap: var(--space-sm);
  align-items: center;
}
.graph-canvas {
  flex: 1;
  overflow: hidden;
  background: var(--color-bg-secondary);
  cursor: grab;
  position: relative;
}
.graph-canvas:active {
  cursor: grabbing;
}
.graph-canvas canvas {
  display: block;
  width: 100%;
  height: 100%;
}
.node-detail {
  padding: var(--space-md) var(--space-lg);
  border-top: 1px solid var(--color-border);
  background: var(--color-bg);
  flex-shrink: 0;
}
.detail-header {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  margin-bottom: var(--space-xs);
}
.detail-info {
  font-size: 12px;
  color: var(--color-text-secondary);
  margin-top: 4px;
}
.detail-label {
  color: var(--color-text-tertiary);
}
.detail-info code {
  font-family: var(--font-mono);
  font-size: 11px;
  background: var(--color-bg-secondary);
  padding: 1px 4px;
  border-radius: 3px;
}
.graph-loading {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
  color: var(--color-text-secondary);
}
.graph-empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-text-tertiary);
}
</style>
