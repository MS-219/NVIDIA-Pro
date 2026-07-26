<template>
  <div class="overview-page" v-loading="loading">
    <section class="summary-strip">
      <div class="summary-copy">
        <span class="section-kicker">CLUSTER SNAPSHOT</span>
        <h2>Orin 边缘集群</h2>
        <p>独立运行环境 · 最近刷新 {{ lastUpdated }}</p>
      </div>
      <div class="baseline-tags">
        <span>ARM64</span><span>L4T 36.4.x</span><span>CUDA 12.6</span><span>8GB Unified</span>
      </div>
      <el-button :icon="Refresh" :loading="loading" @click="fetchData">刷新数据</el-button>
    </section>

    <section class="metric-grid">
      <article v-for="item in kpis" :key="item.label" class="metric-card" :class="item.tone">
        <div class="metric-head">
          <span>{{ item.label }}</span>
          <el-icon><component :is="item.icon" /></el-icon>
        </div>
        <div class="metric-value"><strong>{{ item.value }}</strong><span>{{ item.unit }}</span></div>
        <div class="metric-foot"><span :class="['metric-dot', item.state]"></span>{{ item.note }}</div>
      </article>
    </section>

    <section class="content-grid">
      <article class="panel throughput-panel">
        <header class="panel-header">
          <div><span class="section-kicker">TASK THROUGHPUT</span><h3>任务处理趋势</h3></div>
          <el-segmented v-model="trendTimeRange" :options="trendOptions" size="small" />
        </header>
        <div ref="trendChartRef" class="trend-chart"></div>
      </article>

      <article class="panel health-panel">
        <header class="panel-header">
          <div><span class="section-kicker">NODE HEALTH</span><h3>节点在线状态</h3></div>
        </header>
        <div ref="healthChartRef" class="health-chart"></div>
        <div class="health-legend">
          <div><span class="legend-dot online"></span><span>在线</span><strong>{{ deviceStats.onlineCount }}</strong></div>
          <div><span class="legend-dot offline"></span><span>离线</span><strong>{{ deviceStats.offlineCount }}</strong></div>
        </div>
      </article>
    </section>

    <section class="lower-grid">
      <article class="panel runtime-panel">
        <header class="panel-header">
          <div><span class="section-kicker">RUNTIME BASELINE</span><h3>节点运行基线</h3></div>
          <el-tag type="success" effect="plain">标准镜像</el-tag>
        </header>
        <div class="runtime-list">
          <div v-for="item in runtimeBaseline" :key="item.label" class="runtime-item">
            <el-icon><component :is="item.icon" /></el-icon>
            <span>{{ item.label }}</span>
            <strong>{{ item.value }}</strong>
          </div>
        </div>
      </article>

      <article class="panel activity-panel">
        <header class="panel-header">
          <div><span class="section-kicker">RECENT ACTIVITY</span><h3>最近推理任务</h3></div>
          <router-link to="/device-tasks">查看全部</router-link>
        </header>
        <div v-if="taskLogs.length" class="activity-list">
          <div v-for="log in taskLogs" :key="`${log.time}-${log.sn}`" class="activity-row">
            <span class="activity-status" :class="log.status"></span>
            <div class="activity-main"><strong>{{ log.sn }}</strong><span>{{ log.action }}</span></div>
            <div class="activity-meta"><strong>{{ log.statusText }}</strong><span>{{ log.time }}</span></div>
          </div>
        </div>
        <el-empty v-else description="暂无推理任务" :image-size="64" />
      </article>
    </section>
  </div>
</template>

<script setup>
import { computed, markRaw, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import * as echarts from 'echarts'
import { Coin, Cpu, DataLine, Monitor, Odometer, Refresh, SetUp, Timer } from '@element-plus/icons-vue'
import request from '../utils/request'

const trendChartRef = ref(null)
const healthChartRef = ref(null)
const loading = ref(false)
const lastUpdated = ref('--:--:--')
const trendTimeRange = ref('24h')
const trendOptions = [{ label: '24小时', value: '24h' }, { label: '7天', value: '7d' }]
const deviceStats = reactive({ onlineCount: 0, offlineCount: 0, totalCount: 0, avgCpuLoad: 0, avgMemLoad: 0, avgGpuLoad: 0, avgGpuTemperature: 0, totalPowerWatts: 0, totalTokens: 0 })
const taskStats = reactive({ totalTasks: 0, avgLatency: 0 })
const trendData = reactive({ labels: [], values: [] })
const taskLogs = ref([])
let trendChart
let healthChart

const kpis = computed(() => [
  { label: '在线节点', value: deviceStats.onlineCount, unit: `/ ${deviceStats.totalCount}`, note: deviceStats.totalCount ? `${Math.round(deviceStats.onlineCount / deviceStats.totalCount * 100)}% 在线率` : '等待节点注册', icon: markRaw(Cpu), tone: 'green', state: 'good' },
  { label: '累计任务', value: Number(taskStats.totalTasks || 0).toLocaleString(), unit: 'Tasks', note: `${Number(deviceStats.totalTokens || 0).toLocaleString()} Tokens`, icon: markRaw(DataLine), tone: 'graphite', state: 'neutral' },
  { label: '平均 GPU', value: deviceStats.avgGpuLoad || 0, unit: '%', note: deviceStats.avgGpuTemperature ? `平均温度 ${deviceStats.avgGpuTemperature}°C` : '等待 GPU 遥测', icon: markRaw(Odometer), tone: 'amber', state: deviceStats.avgGpuTemperature > 80 ? 'warn' : 'good' },
  { label: '平均内存', value: deviceStats.avgMemLoad || 0, unit: '%', note: deviceStats.totalPowerWatts ? `在线功耗 ${deviceStats.totalPowerWatts} W` : '统一内存水位', icon: markRaw(Monitor), tone: 'cyan', state: deviceStats.avgMemLoad > 85 ? 'warn' : 'good' },
])

const runtimeBaseline = [
  { label: '节点平台', value: 'Jetson Orin Nano Super', icon: markRaw(Cpu) },
  { label: '系统架构', value: 'ARM64 · 6 Core', icon: markRaw(SetUp) },
  { label: '推理运行时', value: 'CUDA / TensorRT', icon: markRaw(Coin) },
  { label: '心跳窗口', value: '60 seconds', icon: markRaw(Timer) },
]

const formatDateToTime = (value) => value ? String(value).substring(11, 19) : '-'

const renderCharts = () => {
  if (!trendChart || !healthChart) return
  trendChart.setOption({
    grid: { top: 28, right: 18, bottom: 28, left: 44 },
    tooltip: { trigger: 'axis', backgroundColor: '#181b16', borderWidth: 0, textStyle: { color: '#fff' } },
    xAxis: { type: 'category', boundaryGap: false, data: trendData.labels, axisTick: { show: false }, axisLine: { lineStyle: { color: '#dfe3da' } }, axisLabel: { color: '#7c8377', fontSize: 10 } },
    yAxis: { type: 'value', splitLine: { lineStyle: { color: '#edf0e9' } }, axisLabel: { color: '#7c8377', fontSize: 10 } },
    series: [{ type: 'line', smooth: true, symbol: 'circle', symbolSize: 5, data: trendData.values, lineStyle: { width: 2, color: '#659f00' }, itemStyle: { color: '#659f00' }, areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [{ offset: 0, color: 'rgba(118,185,0,.24)' }, { offset: 1, color: 'rgba(118,185,0,0)' }]) } }],
  })

  healthChart.setOption({
    title: { text: `${deviceStats.totalCount}`, subtext: '总节点', left: 'center', top: '35%', textStyle: { color: '#1f231d', fontSize: 28, fontWeight: 700 }, subtextStyle: { color: '#8b9186', fontSize: 10 } },
    series: [{ type: 'pie', radius: ['67%', '84%'], center: ['50%', '48%'], label: { show: false }, silent: true, data: [
      { value: deviceStats.onlineCount, itemStyle: { color: '#76b900' } },
      { value: deviceStats.offlineCount, itemStyle: { color: '#e1e4dd' } },
    ] }],
  })
}

const fetchData = async () => {
  loading.value = true
  try {
    const [statsRes, tasksRes, latestRes, trendRes] = await Promise.allSettled([
      request.get('/api/admin/sl/devices/stats', { silent: true }),
      request.get('/api/admin/device-tasks/statistics', { silent: true }),
      request.get('/api/admin/device-tasks/latest', { params: { limit: 6 }, silent: true }),
      request.get('/api/admin/device-tasks/trend', { params: { range: trendTimeRange.value }, silent: true }),
    ])

    const stats = statsRes.status === 'fulfilled' ? statsRes.value.data : null
    if (stats?.code === 200) Object.assign(deviceStats, { ...stats.data, offlineCount: stats.data.offlineCount ?? Math.max(0, stats.data.totalCount - stats.data.onlineCount) })

    const tasks = tasksRes.status === 'fulfilled' ? tasksRes.value.data : null
    if (tasks?.code === 200) Object.assign(taskStats, tasks.data)

    const latest = latestRes.status === 'fulfilled' ? latestRes.value.data : null
    if (latest?.code === 200 && Array.isArray(latest.data)) {
      taskLogs.value = latest.data.map((task) => ({
        time: formatDateToTime(task.createTime),
        sn: task.deviceSn || 'UNASSIGNED',
        action: task.prompt ? (task.prompt.length > 42 ? `${task.prompt.substring(0, 42)}...` : task.prompt) : '边缘推理任务',
        status: task.status === 'completed' ? 'success' : task.status === 'running' ? 'running' : 'pending',
        statusText: task.status === 'completed' ? '完成' : task.status === 'running' ? '运行中' : '排队中',
      }))
    }

    const trend = trendRes.status === 'fulfilled' ? trendRes.value.data : null
    if (trend?.code === 200) {
      trendData.labels = trend.data.labels || []
      trendData.values = trend.data.values || []
    }
    lastUpdated.value = new Date().toLocaleTimeString('zh-CN', { hour12: false })
    renderCharts()
  } finally {
    loading.value = false
  }
}

const resizeCharts = () => { trendChart?.resize(); healthChart?.resize() }
watch(trendTimeRange, fetchData)

onMounted(() => {
  trendChart = echarts.init(trendChartRef.value)
  healthChart = echarts.init(healthChartRef.value)
  fetchData()
  window.addEventListener('resize', resizeCharts)
})

onUnmounted(() => {
  window.removeEventListener('resize', resizeCharts)
  trendChart?.dispose()
  healthChart?.dispose()
})
</script>

<style scoped>
.overview-page { max-width: 1600px; margin: 0 auto; color: #20241e; }
.summary-strip { min-height: 72px; padding: 14px 18px; display: flex; align-items: center; gap: 24px; background: #fff; border: 1px solid #e0e3dc; border-radius: 6px; }
.summary-copy { min-width: 220px; }
.section-kicker { color: #659f00; font-size: 9px; font-weight: 800; }
.summary-copy h2, .panel-header h3 { margin: 3px 0 0; letter-spacing: 0; }
.summary-copy h2 { font-size: 17px; }
.summary-copy p { margin: 3px 0 0; color: #8a9085; font-size: 10px; }
.baseline-tags { flex: 1; display: flex; gap: 8px; flex-wrap: wrap; }
.baseline-tags span { padding: 5px 8px; color: #52584e; background: #f2f4ef; border: 1px solid #e1e4dd; border-radius: 4px; font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 10px; }

.metric-grid { margin-top: 14px; display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 14px; }
.metric-card { min-height: 128px; padding: 17px 18px; display: flex; flex-direction: column; border: 1px solid #e0e3dc; border-top-width: 3px; border-radius: 6px; background: #fff; }
.metric-card.green { border-top-color: #76b900; }
.metric-card.graphite { border-top-color: #343a32; }
.metric-card.amber { border-top-color: #d59b22; }
.metric-card.cyan { border-top-color: #2b8c94; }
.metric-head { display: flex; justify-content: space-between; color: #777e73; font-size: 11px; }
.metric-head .el-icon { font-size: 17px; }
.metric-value { margin-top: 13px; display: flex; align-items: baseline; gap: 6px; }
.metric-value strong { font-size: 29px; line-height: 1; }
.metric-value span { color: #8c9288; font-size: 10px; }
.metric-foot { margin-top: auto; display: flex; align-items: center; gap: 7px; color: #8a9085; font-size: 10px; }
.metric-dot { width: 6px; height: 6px; border-radius: 50%; }
.metric-dot.good { background: #76b900; }
.metric-dot.warn { background: #d59b22; }
.metric-dot.neutral { background: #697066; }

.content-grid, .lower-grid { margin-top: 14px; display: grid; gap: 14px; }
.content-grid { grid-template-columns: minmax(0, 2fr) minmax(280px, .75fr); }
.lower-grid { grid-template-columns: minmax(300px, .8fr) minmax(0, 1.5fr); }
.panel { padding: 18px; background: #fff; border: 1px solid #e0e3dc; border-radius: 6px; }
.panel-header { min-height: 39px; display: flex; justify-content: space-between; align-items: flex-start; }
.panel-header h3 { font-size: 14px; }
.panel-header a { color: #568900; font-size: 11px; text-decoration: none; }
.trend-chart { width: 100%; height: 290px; }
.health-chart { width: 100%; height: 220px; }
.health-legend { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
.health-legend div { padding: 8px 10px; display: grid; grid-template-columns: auto 1fr auto; align-items: center; gap: 7px; background: #f5f6f3; border-radius: 4px; color: #73796f; font-size: 10px; }
.legend-dot { width: 7px; height: 7px; border-radius: 50%; }
.legend-dot.online { background: #76b900; }
.legend-dot.offline { background: #b8bdb4; }
.health-legend strong { color: #252923; font-size: 12px; }

.runtime-list { margin-top: 10px; }
.runtime-item { min-height: 46px; display: grid; grid-template-columns: 28px 1fr auto; align-items: center; gap: 8px; border-bottom: 1px solid #eceee9; }
.runtime-item:last-child { border-bottom: 0; }
.runtime-item .el-icon { color: #659f00; }
.runtime-item span { color: #777d73; font-size: 10px; }
.runtime-item strong { color: #343932; font-size: 11px; font-weight: 600; }
.activity-list { margin-top: 9px; }
.activity-row { min-height: 48px; display: grid; grid-template-columns: 9px minmax(0, 1fr) auto; align-items: center; gap: 10px; border-bottom: 1px solid #eceee9; }
.activity-row:last-child { border-bottom: 0; }
.activity-status { width: 7px; height: 7px; border-radius: 50%; background: #aeb4aa; }
.activity-status.success { background: #76b900; }
.activity-status.running { background: #2b8c94; }
.activity-status.pending { background: #d59b22; }
.activity-main, .activity-meta { min-width: 0; display: flex; flex-direction: column; }
.activity-main strong { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 10px; }
.activity-main span { overflow: hidden; color: #7e847a; font-size: 10px; text-overflow: ellipsis; white-space: nowrap; }
.activity-meta { align-items: flex-end; }
.activity-meta strong { color: #565c52; font-size: 9px; }
.activity-meta span { color: #a0a59c; font-size: 9px; }

@media (max-width: 1100px) {
  .metric-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .content-grid, .lower-grid { grid-template-columns: 1fr; }
}

@media (max-width: 620px) {
  .summary-strip { align-items: flex-start; flex-wrap: wrap; }
  .baseline-tags { order: 3; width: 100%; }
  .metric-grid { grid-template-columns: 1fr; }
  .metric-card { min-height: 112px; }
  .health-panel { min-width: 0; }
}
</style>
