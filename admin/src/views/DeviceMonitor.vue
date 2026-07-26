<template>
  <div class="monitor-page">
    <!-- 核心统计栏 -->
    <el-row :gutter="20" class="stat-grid">
      <el-col :xs="24" :sm="12" :lg="6">
        <div class="pro-card primary">
          <div class="card-label">活跃节点数</div>
          <div class="card-val">{{ stats.onlineCount }} <small>Nodes</small></div>
          <el-icon class="card-icon"><Connection /></el-icon>
        </div>
      </el-col>
      <el-col :xs="24" :sm="12" :lg="6">
        <div class="pro-card success">
          <div class="card-label">集群总 Token</div>
          <div class="card-val">{{ Number(stats.totalTokens || 0).toLocaleString() }} <small>Tokens</small></div>
          <el-icon class="card-icon"><Cpu /></el-icon>
        </div>
      </el-col>
      <el-col :xs="24" :sm="12" :lg="6">
        <div class="pro-card warning">
          <div class="card-label">集群平均负载 (GPU)</div>
          <div class="card-val">{{ stats.avgGpuLoad || 0 }}<small>%</small></div>
          <el-icon class="card-icon"><Histogram /></el-icon>
        </div>
      </el-col>
      <el-col :xs="24" :sm="12" :lg="6">
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
        <el-radio-button label="">全部节点</el-radio-button>
        <el-radio-button :label="1">在线</el-radio-button>
        <el-radio-button :label="0">离线</el-radio-button>
      </el-radio-group>
      <el-button type="primary" @click="fetchDevices">刷新集群数据</el-button>
    </div>

    <!-- 数据表管理 -->
    <div class="table-container">
      <el-table :data="devices" border stripe v-loading="loading">
        <el-table-column label="运行状态" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" effect="plain">
              {{ row.status === 1 ? 'ONLINE' : 'OFFLINE' }}
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

        <el-table-column label="贡献 Token" width="160" align="right">
          <template #default="{ row }">
            <span style="font-weight: 800; color: #1890ff; font-size: 16px;">{{ Number(row.totalTokens || 0).toLocaleString() }}</span>
            <span style="color: #999; margin-left: 4px;">Tokens</span>
          </template>
        </el-table-column>

        <el-table-column prop="lastHeartbeatTime" label="最后心跳" width="180">
          <template #default="{ row }">
            <span style="color: #666; font-size: 12px;">{{ formatTime(row.lastHeartbeatTime) }}</span>
          </template>
        </el-table-column>

        <el-table-column label="节点操作" width="200" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" size="small" @click="viewDetail(row)">管理</el-button>
            <el-button 
              type="success" 
              size="small" 
              plain 
              @click="openTerminal(row)"
              :disabled="row.status !== 1"
            >终端</el-button>
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
            {{ detailData.status === 1 ? 'ONLINE' : 'OFFLINE' }}
          </el-tag>
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
          <span class="detail-label">累计 Token</span>
          <span class="detail-value accent">{{ Number(detailData.totalTokens || 0).toLocaleString() }}</span>
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
import { Connection, Cpu, Monitor, Histogram, Location, Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

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

const stats = reactive({
  onlineCount: 0,
  totalTokens: 0,
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
      const { provinces, provinceMap } = res.data.data
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
      devices.value = res.data.data.records
      total.value = res.data.data.total
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

onMounted(() => {
  fetchStats()
  fetchLocations()
  fetchDevices()
})
</script>

<style scoped>
.monitor-page {
  width: 100%;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px 18px;
}

.detail-item {
  padding: 12px 14px;
  background: #f7f9fc;
  border: 1px solid #ebeef5;
  border-radius: 5px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.detail-item.full {
  grid-column: 1 / -1;
}

.detail-label {
  font-size: 12px;
  color: #909399;
}

.detail-value {
  font-size: 14px;
  color: #303133;
  font-weight: 600;
}

.detail-value.mono {
  font-family: monospace;
}

.detail-value.accent {
  color: #1890ff;
  font-size: 18px;
  font-weight: 800;
}

.stat-grid {
  margin-bottom: 14px;
}

.pro-card {
  height: 120px;
  background: white;
  border: 1px solid #e0e3dc;
  border-radius: 6px;
  padding: 20px;
  position: relative;
  overflow: hidden;
  box-shadow: none;
}

.card-label {
  font-size: 14px;
  color: #909399;
  margin-bottom: 8px;
}

.card-val {
  font-size: 32px;
  font-weight: 800;
  color: #303133;
}

.card-val small {
  font-size: 14px;
  color: #999;
  margin-left: 4px;
}

.card-icon {
  position: absolute;
  right: 20px;
  bottom: 20px;
  font-size: 48px;
  opacity: 0.1;
}

.primary { border-top: 3px solid #2b8c94; }
.success { border-top: 3px solid #659f00; }
.warning { border-top: 3px solid #d59b22; }
.indigo { border-top: 3px solid #343a32; }

.filter-bar {
  background: white;
  padding: 20px;
  border-radius: 6px;
  margin-bottom: 14px;
  display: flex;
  gap: 20px;
  align-items: center;
  border: 1px solid #e0e3dc;
  box-shadow: none;
}

.table-container {
  background: white;
  padding: 24px;
  border: 1px solid #e0e3dc;
  border-radius: 6px;
  box-shadow: none;
}

.runtime-version,
.telemetry-inline {
  margin: 4px 0;
  color: #858b80;
  font-size: 10px;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}

.carrier-info {
  font-size: 11px;
  color: #999;
  margin-top: 4px;
}

.env-cell {
  font-size: 12px;
  color: #606266;
  line-height: 1.45;
}

.env-summary {
  margin-top: 5px;
}

.env-missing {
  margin-top: 3px;
  color: #e6a23c;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.env-detail {
  margin-left: 8px;
}

.pagination {
  margin-top: 24px;
  display: flex;
  justify-content: flex-end;
}

@media (max-width: 900px) {
  .stat-grid { row-gap: 12px; }
  .filter-bar { align-items: stretch; flex-direction: column; }
  .filter-bar :deep(.el-input),
  .filter-bar :deep(.el-cascader) { width: 100% !important; }
  .table-container { padding: 12px; overflow-x: auto; }
  .detail-grid { grid-template-columns: 1fr; }
  .detail-item.full { grid-column: auto; }
}
</style>
