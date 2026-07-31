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
          <div><span class="section-kicker">DATA TREND</span><h3>数据趋势分析</h3></div>
          <el-tag type="success" effect="plain">近 14 日</el-tag>
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
import { computed, markRaw, onMounted, onUnmounted, reactive, ref } from 'vue'
import * as echarts from 'echarts'
import { Coin, Connection, Cpu, Monitor, Refresh, SetUp, Timer, UserFilled } from '@element-plus/icons-vue'
import request from '../utils/request'

const trendChartRef = ref(null)
const healthChartRef = ref(null)
const loading = ref(false)
const lastUpdated = ref('--:--:--')
const deviceStats = reactive({ onlineCount: 0, offlineCount: 0, totalCount: 0, avgCpuLoad: 0, avgMemLoad: 0, avgGpuLoad: 0, avgGpuTemperature: 0, totalPowerWatts: 0 })
const businessStats = reactive({ totalUsers: 0, hasDeviceCount: 0, totalEarnings: 0, todayEarnings: 0 })
const trendData = reactive({ dates: [], earnings: [], users: [], devices: [] })
const taskLogs = ref([])
let trendChart
let healthChart

const kpis = computed(() => [
  { label: '设备总数', value: Number(deviceStats.totalCount || 0).toLocaleString(), unit: '台', note: `${deviceStats.onlineCount || 0} 台当前在线`, icon: markRaw(Monitor), tone: 'green', state: deviceStats.totalCount > 0 ? 'good' : 'neutral' },
  { label: '注册用户', value: Number(businessStats.totalUsers || 0).toLocaleString(), unit: '人', note: `${businessStats.hasDeviceCount || 0} 位用户已绑定设备`, icon: markRaw(UserFilled), tone: 'graphite', state: businessStats.totalUsers > 0 ? 'good' : 'neutral' },
  { label: '累计收益', value: `¥${formatMoney(businessStats.totalEarnings)}`, unit: '', note: `今日收益 ¥${formatMoney(businessStats.todayEarnings)}`, icon: markRaw(Coin), tone: 'amber', state: 'good' },
  { label: '在线设备', value: Number(deviceStats.onlineCount || 0).toLocaleString(), unit: '台', note: deviceStats.totalCount ? `${Math.round(deviceStats.onlineCount / deviceStats.totalCount * 100)}% 在线率` : '暂无设备', icon: markRaw(Connection), tone: 'cyan', state: deviceStats.onlineCount > 0 ? 'good' : 'neutral' },
])

const runtimeBaseline = [
  { label: '节点平台', value: 'Jetson Orin Nano Super', icon: markRaw(Cpu) },
  { label: '系统架构', value: 'ARM64 · 6 Core', icon: markRaw(SetUp) },
  { label: '推理运行时', value: 'CUDA / TensorRT', icon: markRaw(Coin) },
  { label: '心跳窗口', value: '60 seconds', icon: markRaw(Timer) },
]

const formatDateToTime = (value) => value ? String(value).substring(11, 19) : '-'
const formatMoney = (value) => Number(value || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })

const getChartTheme = () => {
  const styles = getComputedStyle(document.documentElement)
  const color = (name, fallback) => styles.getPropertyValue(name).trim() || fallback

  return {
    surface: color('--orin-surface', '#ffffff'),
    surfaceRaised: color('--orin-surface-raised', '#ffffff'),
    border: color('--orin-border', '#d7ddd2'),
    text: color('--orin-text', '#1f2937'),
    muted: color('--orin-muted', '#6b7280'),
    green: color('--orin-green', '#5f9800'),
    cyan: color('--orin-cyan', '#187f7b'),
    amber: color('--orin-amber', '#a66f12'),
  }
}

const renderCharts = () => {
  if (!trendChart || !healthChart) return
  const theme = getChartTheme()

  trendChart.setOption({
    color: [theme.green, theme.cyan, theme.amber],
    grid: { top: 48, right: 52, bottom: 28, left: 58 },
    legend: {
      top: 4,
      right: 0,
      itemWidth: 18,
      itemHeight: 3,
      textStyle: { color: theme.muted, fontSize: 10 },
      data: ['收益', '新增用户', '新增设备'],
    },
    tooltip: {
      trigger: 'axis',
      backgroundColor: theme.surfaceRaised,
      borderColor: theme.border,
      borderWidth: 1,
      textStyle: { color: theme.text },
      axisPointer: { lineStyle: { color: theme.border } },
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: trendData.dates.map(date => String(date).substring(5)),
      axisTick: { show: false },
      axisLine: { lineStyle: { color: theme.border } },
      axisLabel: { color: theme.muted, fontSize: 10 },
    },
    yAxis: [
      {
        type: 'value',
        name: '收益（元）',
        nameTextStyle: { color: theme.muted, fontSize: 10 },
        splitLine: { lineStyle: { color: theme.border, opacity: 0.55 } },
        axisLabel: { color: theme.muted, fontSize: 10, formatter: value => `¥${value}` },
      },
      {
        type: 'value',
        name: '数量',
        minInterval: 1,
        nameTextStyle: { color: theme.muted, fontSize: 10 },
        splitLine: { show: false },
        axisLabel: { color: theme.muted, fontSize: 10 },
      },
    ],
    series: [
      {
        name: '收益',
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 5,
        yAxisIndex: 0,
        data: trendData.earnings,
        lineStyle: { width: 2, color: theme.green },
        itemStyle: { color: theme.green, borderColor: theme.surface, borderWidth: 2 },
        areaStyle: { color: theme.green, opacity: 0.08 },
        tooltip: { valueFormatter: value => `¥${formatMoney(value)}` },
      },
      {
        name: '新增用户',
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 5,
        yAxisIndex: 1,
        data: trendData.users,
        lineStyle: { width: 2, color: theme.cyan },
        itemStyle: { color: theme.cyan, borderColor: theme.surface, borderWidth: 2 },
        tooltip: { valueFormatter: value => `${value} 人` },
      },
      {
        name: '新增设备',
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 5,
        yAxisIndex: 1,
        data: trendData.devices,
        lineStyle: { width: 2, color: theme.amber },
        itemStyle: { color: theme.amber, borderColor: theme.surface, borderWidth: 2 },
        tooltip: { valueFormatter: value => `${value} 台` },
      },
    ],
  })

  healthChart.setOption({
    title: { text: `${deviceStats.totalCount}`, subtext: '总节点', left: 'center', top: '35%', textStyle: { color: theme.text, fontSize: 28, fontWeight: 700 }, subtextStyle: { color: theme.muted, fontSize: 10 } },
    series: [{ type: 'pie', radius: ['67%', '84%'], center: ['50%', '48%'], label: { show: false }, silent: true, data: [
      { value: deviceStats.onlineCount, itemStyle: { color: theme.green, borderColor: theme.surface, borderWidth: 2 } },
      { value: deviceStats.offlineCount, itemStyle: { color: theme.border, borderColor: theme.surface, borderWidth: 2 } },
    ] }],
  })
}

const fetchData = async () => {
  loading.value = true
  try {
    const [statsRes, usersRes, earningsRes, latestRes, trendRes] = await Promise.allSettled([
      request.get('/api/admin/sl/devices/stats', { silent: true }),
      request.get('/api/user/stats', { silent: true }),
      request.get('/api/earnings/stats', { silent: true }),
      request.get('/api/admin/device-tasks/latest', { params: { limit: 6 }, silent: true }),
      request.get('/api/statistics/trend', { params: { days: 14 }, silent: true }),
    ])

    const stats = statsRes.status === 'fulfilled' ? statsRes.value.data : null
    if (stats?.code === 200) Object.assign(deviceStats, { ...stats.data, offlineCount: stats.data.offlineCount ?? Math.max(0, stats.data.totalCount - stats.data.onlineCount) })

    const users = usersRes.status === 'fulfilled' ? usersRes.value.data : null
    if (users?.code === 200) {
      businessStats.totalUsers = Number(users.data.totalUsers || 0)
      businessStats.hasDeviceCount = Number(users.data.hasDeviceCount || 0)
    }

    const earnings = earningsRes.status === 'fulfilled' ? earningsRes.value.data : null
    if (earnings?.code === 200) {
      businessStats.totalEarnings = Number(earnings.data.totalEarnings || 0)
      businessStats.todayEarnings = Number(earnings.data.todayEarnings || 0)
    }

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
      trendData.dates = trend.data.dates || []
      trendData.earnings = trend.data.earnings || []
      trendData.users = trend.data.users || []
      trendData.devices = trend.data.devices || []
    }
    lastUpdated.value = new Date().toLocaleTimeString('zh-CN', { hour12: false })
    renderCharts()
  } finally {
    loading.value = false
  }
}

const resizeCharts = () => { trendChart?.resize(); healthChart?.resize() }

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
.overview-page { max-width: 1600px; margin: 0 auto; color: var(--orin-text); }
.summary-strip { min-height: 72px; padding: 14px 18px; display: flex; align-items: center; gap: 24px; background: var(--orin-surface); border: 1px solid var(--orin-border); border-radius: 6px; }
.summary-copy { min-width: 220px; }
.section-kicker { color: var(--orin-green); font-size: 9px; font-weight: 800; }
.summary-copy h2, .panel-header h3 { margin: 3px 0 0; letter-spacing: 0; }
.summary-copy h2 { font-size: 17px; }
.summary-copy p { margin: 3px 0 0; color: var(--orin-muted); font-size: 10px; }
.baseline-tags { flex: 1; display: flex; gap: 8px; flex-wrap: wrap; }
.baseline-tags span { padding: 5px 8px; color: var(--orin-text-soft); background: var(--orin-surface-soft); border: 1px solid var(--orin-border); border-radius: 4px; font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 10px; }

.metric-grid { margin-top: 14px; display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 14px; }
.metric-card { min-height: 128px; padding: 17px 18px; display: flex; flex-direction: column; border: 1px solid var(--orin-border); border-top-width: 3px; border-radius: 6px; background: var(--orin-surface); }
.metric-card.green { border-top-color: var(--orin-green); }
.metric-card.graphite { border-top-color: var(--orin-text-soft); }
.metric-card.amber { border-top-color: var(--orin-amber); }
.metric-card.cyan { border-top-color: var(--orin-cyan); }
.metric-head { display: flex; justify-content: space-between; color: var(--orin-muted); font-size: 11px; }
.metric-head .el-icon { font-size: 17px; }
.metric-value { margin-top: 13px; display: flex; align-items: baseline; gap: 6px; }
.metric-value strong { font-size: 29px; line-height: 1; }
.metric-value span { color: var(--orin-muted); font-size: 10px; }
.metric-foot { margin-top: auto; display: flex; align-items: center; gap: 7px; color: var(--orin-muted); font-size: 10px; }
.metric-dot { width: 6px; height: 6px; border-radius: 50%; }
.metric-dot.good { background: var(--orin-green); }
.metric-dot.warn { background: var(--orin-amber); }
.metric-dot.neutral { background: var(--orin-muted); }

.content-grid, .lower-grid { margin-top: 14px; display: grid; gap: 14px; }
.content-grid { grid-template-columns: minmax(0, 2fr) minmax(280px, .75fr); }
.lower-grid { grid-template-columns: minmax(300px, .8fr) minmax(0, 1.5fr); }
.panel { padding: 18px; background: var(--orin-surface); border: 1px solid var(--orin-border); border-radius: 6px; }
.panel-header { min-height: 39px; display: flex; justify-content: space-between; align-items: flex-start; }
.panel-header h3 { font-size: 14px; }
.panel-header a { color: var(--orin-green); font-size: 11px; text-decoration: none; }
.trend-chart { width: 100%; height: 290px; }
.health-chart { width: 100%; height: 220px; }
.health-legend { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
.health-legend div { padding: 8px 10px; display: grid; grid-template-columns: auto 1fr auto; align-items: center; gap: 7px; background: var(--orin-surface-soft); border: 1px solid var(--orin-border); border-radius: 4px; color: var(--orin-muted); font-size: 10px; }
.legend-dot { width: 7px; height: 7px; border-radius: 50%; }
.legend-dot.online { background: var(--orin-green); }
.legend-dot.offline { background: var(--orin-border); }
.health-legend strong { color: var(--orin-text); font-size: 12px; }

.runtime-list { margin-top: 10px; }
.runtime-item { min-height: 46px; display: grid; grid-template-columns: 28px 1fr auto; align-items: center; gap: 8px; border-bottom: 1px solid var(--orin-border); }
.runtime-item:last-child { border-bottom: 0; }
.runtime-item .el-icon { color: var(--orin-green); }
.runtime-item span { color: var(--orin-muted); font-size: 10px; }
.runtime-item strong { color: var(--orin-text-soft); font-size: 11px; font-weight: 600; }
.activity-list { margin-top: 9px; }
.activity-row { min-height: 48px; display: grid; grid-template-columns: 9px minmax(0, 1fr) auto; align-items: center; gap: 10px; border-bottom: 1px solid var(--orin-border); }
.activity-row:last-child { border-bottom: 0; }
.activity-status { width: 7px; height: 7px; border-radius: 50%; background: var(--orin-muted); }
.activity-status.success { background: var(--orin-green); }
.activity-status.running { background: var(--orin-cyan); }
.activity-status.pending { background: var(--orin-amber); }
.activity-main, .activity-meta { min-width: 0; display: flex; flex-direction: column; }
.activity-main strong { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 10px; }
.activity-main span { overflow: hidden; color: var(--orin-muted); font-size: 10px; text-overflow: ellipsis; white-space: nowrap; }
.activity-meta { align-items: flex-end; }
.activity-meta strong { color: var(--orin-text-soft); font-size: 9px; }
.activity-meta span { color: var(--orin-muted); font-size: 9px; }

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
