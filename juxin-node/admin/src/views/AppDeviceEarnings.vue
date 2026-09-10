<template>
  <div class="page app-earnings-page">
    <div class="page-header">
      <div><h2>APP 设备收益</h2><p>APP 独立账本中各节点的收益情况（与旧小程序余额相互独立）。</p></div>
    </div>

    <el-row :gutter="20" class="stat-row">
      <el-col :span="6"><div class="stat-card"><div class="stat-value">{{ nodes.length }}</div><div class="stat-label">节点总数</div></div></el-col>
      <el-col :span="6"><div class="stat-card"><div class="stat-value accent">{{ onlineCount }}</div><div class="stat-label">在线节点</div></div></el-col>
      <el-col :span="6"><div class="stat-card"><div class="stat-value">¥{{ fmtMoney(dailyTotal) }}</div><div class="stat-label">今日收益合计</div></div></el-col>
      <el-col :span="6"><div class="stat-card"><div class="stat-value">¥{{ fmtMoney(totalEarnings) }}</div><div class="stat-label">累计收益合计</div></div></el-col>
    </el-row>

    <div class="panel">
      <div class="toolbar"><el-button type="primary" @click="load" :loading="loading">刷新</el-button></div>
      <el-table :data="nodes" v-loading="loading" stripe border>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column label="节点" min-width="160">
          <template #default="{ row }">
            <div class="node-cell">
              <div class="node-name">{{ row.name || '聚芯节点' }}</div>
              <div class="node-sub">{{ row.binding_code }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="所属用户" width="180">
          <template #default="{ row }">
            <div class="user-cell">
              <div class="user-name">{{ row.nickname || '未绑定' }}</div>
              <div class="user-sub">{{ row.phone || (row.owner_user_id ? `ID: ${row.owner_user_id}` : '-') }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="110">
          <template #default="{ row }">
            <el-tag :type="row.device_type === 1 ? 'info' : 'success'" effect="plain">{{ row.device_type === 1 ? '挂靠' : '实体' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'online' ? 'success' : 'info'" effect="dark">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="hashrate" label="算力" width="120">
          <template #default="{ row }">{{ Number(row.hashrate || 0).toFixed(3) }}</template>
        </el-table-column>
        <el-table-column label="今日收益" width="130">
          <template #default="{ row }"><span class="money">¥{{ fmtMoney(row.daily_earnings) }}</span></template>
        </el-table-column>
        <el-table-column label="累计收益" width="140">
          <template #default="{ row }"><span class="money strong">¥{{ fmtMoney(row.total_earnings) }}</span></template>
        </el-table-column>
        <el-table-column label="最后上报" width="170">
          <template #default="{ row }">{{ fmt(row.last_reported_at) }}</template>
        </el-table-column>
        <el-table-column label="绑定时间" width="170">
          <template #default="{ row }">{{ fmt(row.bound_at) }}</template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import request from '../utils/request'

const nodes = ref([])
const loading = ref(false)

const load = async () => {
  loading.value = true
  try {
    const res = await request.get('/api/admin/app/device-earnings')
    nodes.value = res.data.data || []
  } finally {
    loading.value = false
  }
}

const onlineCount = computed(() => nodes.value.filter((n) => n.status === 'online').length)
const dailyTotal = computed(() => nodes.value.reduce((sum, n) => sum + Number(n.daily_earnings || 0), 0))
const totalEarnings = computed(() => nodes.value.reduce((sum, n) => sum + Number(n.total_earnings || 0), 0))
const fmtMoney = (v) => Number(v || 0).toFixed(2)
const fmt = (v) => (v ? String(v).replace('T', ' ') : '-')

onMounted(load)
</script>

<style scoped>
.app-earnings-page { padding: 0; }
.stat-row { margin-bottom: 20px; }
.stat-card { background: #fff; border: 1px solid #e5e7eb; border-radius: 12px; padding: 20px; }
.stat-value { font-size: 26px; font-weight: 800; color: #1e293b; }
.stat-value.accent { color: #047857; }
.stat-label { font-size: 13px; color: #64748b; margin-top: 6px; }
.toolbar { display: flex; justify-content: flex-end; margin-bottom: 16px; }
.node-cell, .user-cell { display: flex; flex-direction: column; }
.node-name, .user-name { font-weight: 600; color: #303133; }
.node-sub, .user-sub { font-size: 12px; color: #909399; }
.money { color: #b56700; }
.money.strong { color: #047857; font-weight: 700; }
</style>
