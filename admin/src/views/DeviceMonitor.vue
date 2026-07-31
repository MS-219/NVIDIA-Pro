<template>
  <div class="monitor-page">
    <!-- 核心统计栏 -->
    <el-row :gutter="20" class="stat-grid">
      <el-col :xs="24" :sm="8" :lg="8">
        <div class="pro-card primary">
          <div class="card-label">活跃节点数</div>
          <div class="card-val">{{ stats.onlineCount }} <small>台</small></div>
          <el-icon class="card-icon"><Connection /></el-icon>
        </div>
      </el-col>
      <el-col :xs="24" :sm="8" :lg="8">
        <div class="pro-card warning">
          <div class="card-label">集群平均负载 (GPU)</div>
          <div class="card-val">{{ stats.avgGpuLoad || 0 }}<small>%</small></div>
          <el-icon class="card-icon"><Histogram /></el-icon>
        </div>
      </el-col>
      <el-col :xs="24" :sm="8" :lg="8">
        <div class="pro-card indigo">
          <div class="card-label">内存使用水位</div>
          <div class="card-val">{{ stats.avgMemLoad || 0 }}<small>%</small></div>
          <el-icon class="card-icon"><Monitor /></el-icon>
        </div>
      </el-col>
    </el-row>

    <!-- 过滤器 -->
    <div class="filter-bar">
      <el-input 
        v-model="searchQuery" 
        placeholder="搜索节点编号 (SN)..." 
        style="width: 260px" 
        prefix-icon="Search"
        @keyup.enter="fetchDevices"
      />
      <el-cascader
        v-model="locationFilter"
        :options="locationOptions"
        :props="{ checkStrictly: true, expandTrigger: 'hover' }"
        placeholder="选择地区"
        clearable
        style="width: 220px"
        @change="onLocationChange"
      />
      <el-radio-group v-model="statusFilter" @change="fetchDevices">
        <el-radio-button value="">全部节点</el-radio-button>
        <el-radio-button :value="1">在线</el-radio-button>
        <el-radio-button :value="0">不在线</el-radio-button>
      </el-radio-group>
      <el-button type="primary" @click="fetchDevices">刷新集群数据</el-button>
    </div>

    <!-- 数据表管理 -->
    <div class="table-container">
      <el-table :data="devices" border stripe v-loading="loading">
        <el-table-column label="运行状态" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" effect="plain">
              {{ row.status === 1 ? '在线' : '不在线' }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="绑定状态" width="130" align="center">
          <template #default="{ row }">
            <el-tag :type="row.userId != null ? 'success' : 'info'" effect="plain">
              {{ row.userId != null ? '已绑定' : '未绑定' }}
            </el-tag>
          </template>
        </el-table-column>
        
        <el-table-column prop="sn" label="SN 序列号" width="220">
          <template #default="{ row }">
            <div>
              <b style="font-family: monospace; font-size: 14px;">{{ row.sn }}</b>
              <div style="margin-top: 4px; color: #909399; font-size: 12px;">
                版本 {{ row.agentVersion || '待上报' }}
              </div>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="位置/运营商" min-width="200">
          <template #default="{ row }">
            <div>
              <el-icon><Location /></el-icon> {{ row.location || '未知区域' }}
              <div class="carrier-info">{{ row.carrier || '未知网络' }}</div>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="Jetson 运行环境" min-width="220">
          <template #default="{ row }">
            <div style="font-size: 12px; color: #666;">
              <div>{{ row.deviceModel || 'Orin 型号待上报' }}</div>
              <div class="runtime-version">{{ row.architecture || 'aarch64' }} · {{ row.l4tVersion || 'L4T 待上报' }} · CUDA {{ row.cudaVersion || '-' }}</div>
              <el-tag size="small" type="info" effect="plain">{{ row.runtimeModel || '未执行任务' }}</el-tag>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="环境状况" width="180">
          <template #default="{ row }">
            <div class="env-cell">
              <el-tag size="small" :type="envTagType(row.envStatus)" effect="plain">
                {{ envStatusText(row.envStatus) }}
              </el-tag>
              <div class="env-summary">{{ row.envSummary || '未检查' }}</div>
              <div v-if="row.envMissingItems" class="env-missing">{{ row.envMissingItems }}</div>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="系统负载" width="160">
          <template #default="{ row }">
            <div style="font-size: 11px;">
              <div style="display: flex; justify-content: space-between; margin-bottom: 2px;">
                <span>CPU</span>
                <span :style="{ color: parseFloat(row.cpuUsage) > 80 ? '#f56c6c' : '#67c23a' }">{{ row.cpuUsage || '0' }}%</span>
              </div>
              <el-progress :percentage="parseFloat(row.cpuUsage) || 0" :show-text="false" :stroke-width="4" />
              
              <div style="display: flex; justify-content: space-between; margin-top: 6px; margin-bottom: 2px;">
                <span>RAM</span>
                <span :style="{ color: parseFloat(row.memoryUsage) > 80 ? '#f56c6c' : '#1890ff' }">{{ row.memoryUsage || '0' }}%</span>
              </div>
              <el-progress :percentage="parseFloat(row.memoryUsage) || 0" :show-text="false" :stroke-width="4" color="#1890ff" />

              <div style="display: flex; justify-content: space-between; margin-top: 6px; margin-bottom: 2px;">
                <span>GPU</span>
                <span>{{ row.gpuUsage || '0' }}%</span>
              </div>
              <el-progress :percentage="parseFloat(row.gpuUsage) || 0" :show-text="false" :stroke-width="4" color="#659f00" />
              <div class="telemetry-inline">{{ row.gpuTemperature ?? '-' }}°C · {{ row.powerWatts ?? '-' }}W</div>
            </div>
          </template>
        </el-table-column>

        <el-table-column prop="lastHeartbeatTime" label="最后心跳" width="180">
          <template #default="{ row }">
            <span style="color: #666; font-size: 12px;">{{ formatTime(row.lastHeartbeatTime) }}</span>
          </template>
        </el-table-column>

        <el-table-column label="节点操作" width="280" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" size="small" @click="viewDetail(row)">管理</el-button>
            <el-button 
              type="success" 
              size="small" 
              plain 
              @click="openTerminal(row)"
              :disabled="row.status !== 1"
            >终端</el-button>
            <el-button
              v-if="row.userId != null"
              type="danger"
              size="small"
              plain
              :icon="Unlock"
              :loading="unbindingId === row.id"
              @click="unbindDevice(row)"
            >解绑</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <el-pagination
          v-model:current-page="currentPage"
          :page-size="pageSize"
          :total="total"
          layout="total, prev, pager, next"
          @current-change="fetchDevices"
        />
      </div>
    </div>

    <el-dialog
      v-model="detailVisible"
      title="节点详情"
      width="720px"
      destroy-on-close
    >
      <div v-loading="detailLoading" class="detail-grid">
        <div class="detail-item">
          <span class="detail-label">SN 序列号</span>
          <span class="detail-value mono">{{ detailData.sn || '-' }}</span>
        </div>
        <div class="detail-item">
          <span class="detail-label">运行状态</span>
          <el-tag :type="detailData.status === 1 ? 'success' : 'danger'" effect="plain">
            {{ detailData.status === 1 ? '在线' : '不在线' }}
          </el-tag>
        </div>
        <div class="detail-item">
          <span class="detail-label">绑定状态</span>
          <span class="detail-value">
            {{ detailData.userId != null ? `已绑定（用户 ${detailData.userId}）` : '未绑定' }}
          </span>
        </div>
        <div class="detail-item">
          <span class="detail-label">设备名称</span>
          <span class="detail-value">{{ detailData.name || '未命名' }}</span>
        </div>
        <div class="detail-item">
          <span class="detail-label">设备类型</span>
          <span class="detail-value">{{ formatDeviceType(detailData.type) }}</span>
        </div>
        <div class="detail-item">
          <span class="detail-label">Agent 版本</span>
          <span class="detail-value mono">{{ detailData.agentVersion || '待上报' }}</span>
        </div>
        <div class="detail-item">
          <span class="detail-label">远程环境</span>
          <span class="detail-value">
            <el-tag size="small" :type="envTagType(detailData.envStatus)" effect="plain">
              {{ envStatusText(detailData.envStatus) }}
            </el-tag>
            <span class="env-detail">{{ detailData.envSummary || '未检查' }}</span>
          </span>
        </div>
        <div class="detail-item full" v-if="detailData.envMissingItems">
          <span class="detail-label">缺失环境项</span>
          <span class="detail-value">{{ detailData.envMissingItems }}</span>
        </div>
        <div class="detail-item">
          <span class="detail-label">位置</span>
          <span class="detail-value">{{ detailData.location || '未知区域' }}</span>
        </div>
        <div class="detail-item">
          <span class="detail-label">运营商</span>
          <span class="detail-value">{{ detailData.carrier || '未知网络' }}</span>
        </div>
        <div class="detail-item">
          <span class="detail-label">IP 地址</span>
          <span class="detail-value mono">{{ detailData.ip || '-' }}</span>
        </div>
        <div class="detail-item">
          <span class="detail-label">绑定码</span>
          <span class="detail-value mono">{{ detailData.bindCode || '-' }}</span>
        </div>
        <div class="detail-item">
          <span class="detail-label">CPU 负载</span>
          <span class="detail-value">{{ detailData.cpuUsage || '0' }}%</span>
        </div>
        <div class="detail-item">
          <span class="detail-label">内存负载</span>
          <span class="detail-value">{{ detailData.memoryUsage || '0' }}%</span>
        </div>
        <div class="detail-item">
          <span class="detail-label">GPU 负载</span>
          <span class="detail-value">{{ detailData.gpuUsage || '0' }}%</span>
        </div>
        <div class="detail-item">
          <span class="detail-label">温度 / 功耗</span>
          <span class="detail-value">{{ detailData.gpuTemperature ?? '-' }}°C / {{ detailData.powerWatts ?? '-' }}W</span>
        </div>
        <div class="detail-item">
          <span class="detail-label">最后心跳</span>
          <span class="detail-value">{{ formatTime(detailData.lastHeartbeatTime) }}</span>
        </div>
        <div class="detail-item full">
          <span class="detail-label">创建时间</span>
          <span class="detail-value">{{ formatTime(detailData.createTime) }}</span>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, reactive } from 'vue'
import { useRouter } from 'vue-router'
import request from '../utils/request'
import { Connection, Monitor, Histogram, Location, Search, Unlock } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'

const router = useRouter()
const loading = ref(false)
const devices = ref([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)
const searchQuery = ref('')
const statusFilter = ref('')
const locationFilter = ref([])
const locationOptions = ref([])
const detailVisible = ref(false)
const detailLoading = ref(false)
const detailData = ref({})
const unbindingId = ref(null)

const stats = reactive({
  onlineCount: 0,
  totalCount: 0,
  avgCpuLoad: 0,
  avgMemLoad: 0
})

const fetchStats = async () => {
  try {
    const res = await request.get('/api/admin/sl/devices/stats')
    if (res.data.code === 200) {
      Object.assign(stats, res.data.data)
    }
  } catch (e) { console.error(e) }
}

const fetchLocations = async () => {
  try {
    const res = await request.get('/api/admin/sl/devices/locations')
    if (res.data.code === 200) {
      const locationData = res.data.data || {}
      const provinces = Array.isArray(locationData.provinces) ? locationData.provinces : []
      const provinceMap = locationData.provinceMap && typeof locationData.provinceMap === 'object'
        ? locationData.provinceMap
        : {}
      locationOptions.value = provinces.map(prov => {
        const cities = provinceMap[prov] || []
        return {
          value: prov,
          label: prov,
          children: cities.length > 0 ? cities.map(city => ({
            value: city,
            label: city
          })) : undefined
        }
      })
    }
  } catch (e) { console.error(e) }
}

const onLocationChange = () => {
  currentPage.value = 1
  fetchDevices()
}

const getLocationParam = () => {
  if (!locationFilter.value || locationFilter.value.length === 0) return undefined
  // 如果选了省+市，用省+市组合搜索；如果只选了省，用省搜索
  return locationFilter.value.join('')
}

const fetchDevices = async () => {
  loading.value = true
  try {
    const res = await request.get('/api/admin/sl/devices/list', {
      params: {
        page: currentPage.value,
        size: pageSize.value,
        sn: searchQuery.value || undefined,
        status: statusFilter.value === '' ? undefined : statusFilter.value,
        location: getLocationParam()
      }
    })
    if (res.data.code === 200) {
      const pageData = res.data.data || {}
      devices.value = Array.isArray(pageData.records) ? pageData.records : []
      total.value = Number(pageData.total) || 0
    }
  } catch (e) {
    ElMessage.error('获取列表失败')
  } finally {
    loading.value = false
  }
}

const formatTime = (time) => time ? time.replace('T', ' ').substring(0, 19) : '-'
const viewDetail = async (device) => {
  detailVisible.value = true
  detailLoading.value = true
  detailData.value = { ...device }

  try {
    const res = await request.get(`/api/device/detail/${device.id}`)
    if (res.data.code === 200) {
      detailData.value = {
        ...device,
        ...res.data.data,
      }
    }
  } catch (e) {
    ElMessage.error(`获取节点 ${device.sn} 详情失败`)
  } finally {
    detailLoading.value = false
  }
}

const formatDeviceType = (type) => {
  if (type === 2) return '边缘算力节点'
  if (type === 1) return '虚拟设备'
  if (type === 0) return '实体设备'
  return '未标记'
}

const envStatusText = (status) => ({
  ready: '正常',
  warning: '缺依赖',
  checking: '检查中',
  error: '失败',
  unknown: '未检查'
}[status] || '未检查')

const envTagType = (status) => ({
  ready: 'success',
  warning: 'warning',
  checking: 'primary',
  error: 'danger',
  unknown: 'info'
}[status] || 'info')

const openTerminal = (device) => {
  if (device.status !== 1) {
    ElMessage.warning('节点离线，隧道无法建立')
    return
  }
  router.push({
    name: 'Terminal',
    query: { sn: device.sn }
  })
}

const unbindDevice = async (device) => {
  if (device.userId == null) return

  try {
    await ElMessageBox.confirm(
      `确定解绑节点 ${device.sn} 吗？解绑后该设备将不再属于当前用户。`,
      '解绑设备',
      {
        confirmButtonText: '确认解绑',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
  } catch (e) {
    return
  }

  unbindingId.value = device.id
  try {
    const res = await request.post('/api/device/batch-unbind', { ids: [device.id] })
    if (res.data.code === 200) {
      ElMessage.success('设备解绑成功')
      await Promise.all([fetchDevices(), fetchStats()])
    } else {
      ElMessage.error(res.data.msg || '解绑失败')
    }
  } finally {
    unbindingId.value = null
  }
}

onMounted(() => {
  fetchStats()
  fetchLocations()
  fetchDevices()
})
</script>

<style scoped>
.monitor-page {
  width: 100%;
  color: var(--orin-text);
}

.stat-grid {
  margin-bottom: 14px;
}

.pro-card {
  position: relative;
  height: 112px;
  overflow: hidden;
  padding: 18px 20px;
  color: var(--orin-text);
  background: var(--orin-surface);
  border: 1px solid var(--orin-border);
  border-left-width: 3px;
  border-radius: 6px;
  box-shadow: none;
}

.pro-card.primary {
  border-left-color: var(--orin-green);
}

.pro-card.success {
  border-left-color: var(--orin-cyan);
}

.pro-card.warning {
  border-left-color: var(--orin-amber);
}

.pro-card.indigo {
  border-left-color: var(--orin-border-strong);
}

.card-label {
  margin-bottom: 8px;
  color: var(--orin-muted);
  font-size: 12px;
}

.card-val {
  color: var(--orin-text);
  font-size: 30px;
  font-weight: 800;
  line-height: 1.1;
}

.card-val small {
  margin-left: 4px;
  color: var(--orin-dim);
  font-size: 12px;
  font-weight: 600;
}

.card-icon {
  position: absolute;
  right: 18px;
  bottom: 16px;
  color: var(--orin-green);
  font-size: 42px;
  opacity: 0.28;
}

.pro-card.success .card-icon {
  color: var(--orin-cyan);
}

.pro-card.warning .card-icon {
  color: var(--orin-amber);
}

.pro-card.indigo .card-icon {
  color: var(--orin-text-soft);
}

.filter-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;
  padding: 14px 16px;
  background: var(--orin-surface);
  border: 1px solid var(--orin-border);
  border-radius: 6px;
  box-shadow: none;
}

.filter-bar :deep(.el-radio-button__inner) {
  color: var(--orin-text-soft);
  background: var(--orin-surface-raised);
  border-color: var(--orin-border);
  box-shadow: none;
}

.filter-bar :deep(.el-radio-button.is-active .el-radio-button__inner) {
  color: #ffffff;
  background: var(--orin-green);
  border-color: var(--orin-green);
  box-shadow: -1px 0 0 0 var(--orin-green);
}

.table-container {
  padding: 16px;
  background: var(--orin-surface);
  border: 1px solid var(--orin-border);
  border-radius: 6px;
  box-shadow: none;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px 18px;
}

.detail-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 12px 14px;
  background: var(--orin-surface-soft);
  border: 1px solid var(--orin-border-soft);
  border-radius: 5px;
}

.detail-item.full {
  grid-column: 1 / -1;
}

.detail-label {
  color: var(--orin-muted);
  font-size: 12px;
}

.detail-value {
  color: var(--orin-text-soft);
  font-size: 14px;
  font-weight: 600;
  overflow-wrap: anywhere;
}

.detail-value.mono {
  font-family: monospace;
}

.detail-value.accent {
  color: var(--orin-green-bright);
  font-size: 18px;
  font-weight: 800;
}

.runtime-version,
.telemetry-inline {
  margin: 4px 0;
  color: var(--orin-muted);
  font-size: 10px;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}

.carrier-info {
  margin-top: 4px;
  color: var(--orin-muted);
  font-size: 11px;
}

.env-cell {
  color: var(--orin-text-soft);
  font-size: 12px;
  line-height: 1.45;
}

.env-summary {
  margin-top: 5px;
}

.env-missing {
  margin-top: 3px;
  color: var(--orin-amber);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.env-detail {
  margin-left: 8px;
}

.pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 18px;
}

.table-container :deep(.el-progress-bar__outer) {
  background: var(--orin-surface-soft);
}

.table-container :deep(.el-progress-bar__inner) {
  background-color: var(--orin-green) !important;
}

.table-container :deep([style*="color: #1890ff"]),
.table-container :deep([style*="color: #67c23a"]) {
  color: var(--orin-green-bright) !important;
}

.table-container :deep([style*="color: #f56c6c"]) {
  color: var(--orin-danger) !important;
}

.table-container :deep([style*="color: #909399"]),
.table-container :deep([style*="color: #666"]),
.table-container :deep([style*="color: #999"]) {
  color: var(--orin-muted) !important;
}

@media (max-width: 900px) {
  .stat-grid {
    row-gap: 12px;
  }

  .pro-card {
    height: 102px;
    padding: 16px;
  }

  .filter-bar {
    align-items: stretch;
    flex-wrap: wrap;
  }

  .filter-bar :deep(.el-input),
  .filter-bar :deep(.el-cascader) {
    width: calc(50% - 6px) !important;
  }

  .filter-bar :deep(.el-radio-group) {
    flex: 1 1 100%;
  }

  .filter-bar :deep(.el-radio-button) {
    flex: 1;
  }

  .filter-bar :deep(.el-radio-button__inner) {
    width: 100%;
  }

  .table-container {
    padding: 12px;
  }

  .detail-grid {
    grid-template-columns: 1fr;
  }

  .detail-item.full {
    grid-column: auto;
  }
}

@media (max-width: 520px) {
  .pro-card {
    height: 94px;
    padding: 14px;
  }

  .card-val {
    font-size: 26px;
  }

  .card-icon {
    right: 14px;
    bottom: 14px;
    font-size: 34px;
  }

  .filter-bar {
    padding: 10px;
  }

  .filter-bar :deep(.el-input),
  .filter-bar :deep(.el-cascader),
  .filter-bar :deep(.el-button) {
    width: 100% !important;
  }

  .table-container {
    padding: 8px;
  }

  .pagination {
    justify-content: flex-start;
    overflow-x: auto;
    padding-bottom: 4px;
  }

  .detail-item {
    padding: 10px;
  }
}
</style>
