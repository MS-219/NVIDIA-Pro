<template>
  <div class="commands-page">
    <el-row :gutter="20" class="stat-grid">
      <el-col :span="statCards.length > 4 ? 4 : 6" v-for="item in statCards" :key="item.key">
        <div class="stat-card" :class="item.type">
          <div class="card-label">{{ item.label }}</div>
          <div class="card-val">{{ item.value }}</div>
          <el-icon class="card-icon"><component :is="item.icon" /></el-icon>
        </div>
      </el-col>
    </el-row>

    <div class="dispatch-panel">
      <div class="panel-title">
        <el-icon><Promotion /></el-icon>
        远程指令下发
      </div>
      <el-form :model="commandForm" label-position="top">
        <el-row :gutter="18">
          <el-col :span="8">
            <el-form-item label="目标范围">
              <el-radio-group v-model="commandForm.targetScope" class="target-scope">
                <el-radio-button value="SINGLE">单台</el-radio-button>
                <el-radio-button value="ALL_ONLINE">全部可远程</el-radio-button>
                <el-radio-button value="LOCATION">按地区</el-radio-button>
                <el-radio-button value="CARRIER">按运营商</el-radio-button>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col v-if="commandForm.targetScope === 'SINGLE'" :span="6">
            <el-form-item label="目标设备">
              <el-select
                v-model="commandForm.deviceSn"
                placeholder="选择可远程设备"
                filterable
                remote
                reserve-keyword
                :remote-method="searchDevices"
                :loading="deviceLoading"
                style="width: 100%"
              >
                <el-option
                  v-for="device in deviceOptions"
                  :key="device.sn"
                  :label="`${device.sn} · ${device.location || '未知地区'}`"
                  :value="device.sn"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col v-if="commandForm.targetScope === 'LOCATION'" :span="6">
            <el-form-item label="地区关键词">
              <el-input v-model="commandForm.locationKeyword" placeholder="例如：山东省 / 枣庄市" clearable />
            </el-form-item>
          </el-col>
          <el-col v-if="commandForm.targetScope === 'CARRIER'" :span="6">
            <el-form-item label="运营商">
              <el-select v-model="commandForm.carrier" placeholder="选择运营商" clearable style="width: 100%">
                <el-option label="中国移动" value="中国移动" />
                <el-option label="中国联通" value="中国联通" />
                <el-option label="中国电信" value="中国电信" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="5">
            <el-form-item label="指令模板">
              <el-select v-model="commandForm.commandType" style="width: 100%" @change="onCommandTypeChange">
                <el-option label="健康检查" value="HEALTH_CHECK" />
                <el-option label="远程环境检查" value="ENV_CHECK" />
                <el-option label="升级远程 Agent" value="UPGRADE_AGENT" />
                <el-option label="安装远程运维依赖" value="INSTALL_DEPS" />
                <el-option label="建立隧道" value="OPEN_TUNNEL" />
                <el-option label="关闭隧道" value="CLOSE_TUNNEL" />
                <el-option label="重启 Agent" value="RESTART_AGENT" />
                <el-option label="自定义命令" value="CUSTOM" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="7">
            <el-form-item label="备注">
              <el-input v-model="commandForm.remark" placeholder="例如：山东区域节点环境检查" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <div class="dispatch-actions">
              <el-button type="primary" :icon="Promotion" :loading="dispatching" @click="dispatchCommand">
                {{ commandForm.targetScope === 'SINGLE' ? '下发指令' : '批量下发' }}
              </el-button>
              <el-button :icon="Refresh" @click="refreshAll">刷新</el-button>
            </div>
          </el-col>
        </el-row>
        <div class="target-hint" v-if="commandForm.targetScope !== 'SINGLE'">
          <span v-if="targetCountLoading">正在统计可远程设备...</span>
          <span v-else>当前范围将下发给 {{ targetCount }} 台可远程设备</span>
        </div>
        <div class="template-note" v-if="templateNote">{{ templateNote }}</div>
        <el-form-item v-if="needsCommandText" label="命令内容">
          <el-input
            v-model="commandForm.commandText"
            type="textarea"
            :rows="3"
            placeholder="输入要下发给设备 Agent 执行的命令"
          />
        </el-form-item>
      </el-form>
    </div>

    <div class="filter-bar">
      <el-input
        v-model="filters.deviceSn"
        placeholder="搜索 SN"
        style="width: 220px"
        :prefix-icon="Search"
        clearable
        @keyup.enter="fetchCommandGroups"
        @clear="fetchCommandGroups"
      />
      <el-select v-model="filters.commandType" placeholder="指令类型" clearable style="width: 170px" @change="onFilterChange">
        <el-option label="健康检查" value="HEALTH_CHECK" />
        <el-option label="远程环境检查" value="ENV_CHECK" />
        <el-option label="升级远程 Agent" value="UPGRADE_AGENT" />
        <el-option label="安装远程运维依赖" value="INSTALL_DEPS" />
        <el-option label="建立隧道" value="OPEN_TUNNEL" />
        <el-option label="关闭隧道" value="CLOSE_TUNNEL" />
        <el-option label="重启 Agent" value="RESTART_AGENT" />
        <el-option label="自定义命令" value="CUSTOM" />
      </el-select>
      <el-select v-model="filters.status" placeholder="状态" clearable style="width: 140px" @change="onFilterChange">
        <el-option label="待下发" value="pending" />
        <el-option label="已送达" value="delivered" />
        <el-option label="已完成" value="completed" />
        <el-option label="已取消" value="canceled" />
        <el-option label="失败" value="failed" />
      </el-select>
    </div>

    <div class="task-container" v-loading="loading">
      <div class="task-head">
        <div>
          <div class="task-title">指令任务</div>
          <div class="task-subtitle">默认按一次下发聚合，点进任务后再看每台设备的执行明细。</div>
        </div>
        <div class="task-count">共 {{ total }} 个任务</div>
      </div>

      <el-empty v-if="!loading && commandGroups.length === 0" description="暂无指令任务" />

      <div
        v-for="group in commandGroups"
        :key="group.groupKey"
        class="task-card"
        @click="openGroupDetail(group)"
      >
        <div class="task-main">
          <div class="task-topline">
            <el-tag :type="statusType(groupStatus(group))" effect="plain">{{ groupStatusText(group) }}</el-tag>
            <span class="task-name">{{ commandTypeText(group.commandType) }}</span>
            <span class="muted">{{ formatTime(group.createTime) }}</span>
          </div>
          <div class="summary-main">{{ commandSummary(group) }}</div>
          <div class="muted remark" v-if="group.remark">{{ group.remark }}</div>
          <div class="progress-row">
            <el-progress :percentage="groupProgress(group)" :stroke-width="8" :show-text="false" />
            <span class="progress-text">{{ groupProgress(group) }}%</span>
          </div>
          <div class="status-pills">
            <span>总数 {{ group.totalCount || 0 }}</span>
            <span>待下发 {{ group.pendingCount || 0 }}</span>
            <span>已送达 {{ group.deliveredCount || 0 }}</span>
            <span>已完成 {{ group.completedCount || 0 }}</span>
            <span>失败 {{ group.failedCount || 0 }}</span>
            <span>已取消 {{ group.canceledCount || 0 }}</span>
          </div>
        </div>
        <div class="task-actions" @click.stop>
          <el-button link type="primary" @click="openGroupDetail(group)">查看明细</el-button>
          <el-button link @click="viewCommandText(group)">查看命令</el-button>
        </div>
      </div>

      <div class="pagination">
        <el-pagination
          v-model:current-page="page"
          :page-size="size"
          :total="total"
          layout="total, prev, pager, next"
          @current-change="fetchCommandGroups"
        />
      </div>
    </div>

    <el-drawer v-model="detailVisible" :title="detailTitle" size="78%" destroy-on-close>
      <div class="drawer-summary" v-if="currentGroup.groupKey">
        <div>
          <div class="drawer-command">{{ commandSummary(currentGroup) }}</div>
          <div class="muted" v-if="currentGroup.remark">{{ currentGroup.remark }}</div>
        </div>
        <div class="drawer-stats">
          <span>总数 {{ currentGroup.totalCount || 0 }}</span>
          <span>完成 {{ currentGroup.completedCount || 0 }}</span>
          <span>失败 {{ currentGroup.failedCount || 0 }}</span>
          <span>待处理 {{ (currentGroup.pendingCount || 0) + (currentGroup.deliveredCount || 0) }}</span>
        </div>
      </div>

      <div class="detail-filter">
        <el-input
          v-model="detailFilters.deviceSn"
          placeholder="搜索当前任务内 SN"
          style="width: 220px"
          :prefix-icon="Search"
          clearable
          @keyup.enter="fetchGroupRecords"
          @clear="fetchGroupRecords"
        />
        <el-select v-model="detailFilters.status" placeholder="明细状态" clearable style="width: 140px" @change="onDetailFilterChange">
          <el-option label="待下发" value="pending" />
          <el-option label="已送达" value="delivered" />
          <el-option label="已完成" value="completed" />
          <el-option label="已取消" value="canceled" />
          <el-option label="失败" value="failed" />
        </el-select>
        <el-button :icon="Refresh" @click="fetchGroupRecords">刷新明细</el-button>
      </div>

      <el-table :data="commandDetails" border stripe v-loading="detailLoading">
        <el-table-column label="状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" effect="plain">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="指令编号" width="180">
          <template #default="{ row }">
            <span class="mono">{{ row.commandNo }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="deviceSn" label="设备 SN" width="180" />
        <el-table-column label="命令摘要" min-width="260">
          <template #default="{ row }">
            <div class="command-summary">
              <div class="summary-main">{{ commandSummary(row) }}</div>
              <el-button link type="primary" @click="viewResult(row)">完整命令</el-button>
            </div>
            <div class="muted" v-if="row.remark">{{ row.remark }}</div>
          </template>
        </el-table-column>
        <el-table-column label="创建/送达/完成" width="190">
          <template #default="{ row }">
            <div>{{ formatTime(row.createTime) }}</div>
            <div class="muted">送达 {{ formatTime(row.dispatchedAt) }}</div>
            <div class="muted">完成 {{ formatTime(row.finishedAt) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="viewResult(row)">结果</el-button>
            <el-button
              type="danger"
              link
              :disabled="row.status !== 'pending'"
              @click="cancelCommand(row)"
            >取消</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <el-pagination
          v-model:current-page="detailPage"
          :page-size="detailSize"
          :total="detailTotal"
          layout="total, prev, pager, next"
          @current-change="fetchGroupRecords"
        />
      </div>
    </el-drawer>

    <el-dialog v-model="resultVisible" :title="resultTitle" width="680px" destroy-on-close>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="指令编号">{{ currentCommand.commandNo || currentCommand.sampleCommandNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="设备">{{ currentCommand.deviceSn || currentCommand.sampleDeviceSn || '-' }}</el-descriptions-item>
        <el-descriptions-item label="类型">{{ commandTypeText(currentCommand.commandType) }}</el-descriptions-item>
        <el-descriptions-item label="退出码">{{ currentCommand.exitCode ?? '-' }}</el-descriptions-item>
      </el-descriptions>
      <div class="result-title">命令</div>
      <pre class="result-box">{{ currentCommand.commandText || '-' }}</pre>
      <div class="result-title" v-if="currentCommand.id">输出</div>
      <pre v-if="currentCommand.id" class="result-box output">{{ currentCommand.resultText || '暂无回传输出' }}</pre>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import request from '../utils/request'
import { CircleCheck, Clock, CloseBold, Promotion, Refresh, Search, Warning } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'

const loading = ref(false)
const detailLoading = ref(false)
const dispatching = ref(false)
const deviceLoading = ref(false)
const targetCountLoading = ref(false)
const commandGroups = ref([])
const commandDetails = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const detailTotal = ref(0)
const detailPage = ref(1)
const detailSize = ref(10)
const deviceOptions = ref([])
const targetCount = ref(0)
const resultVisible = ref(false)
const detailVisible = ref(false)
const currentCommand = ref({})
const currentGroup = ref({})

const stats = reactive({
  pending: 0,
  delivered: 0,
  completed: 0,
  canceled: 0,
  failed: 0
})

const commandForm = reactive({
  targetScope: 'SINGLE',
  deviceSn: '',
  locationKeyword: '',
  carrier: '',
  commandType: 'HEALTH_CHECK',
  commandText: '',
  remark: ''
})

const filters = reactive({
  deviceSn: '',
  commandType: '',
  status: ''
})

const detailFilters = reactive({
  deviceSn: '',
  status: ''
})

const needsCommandText = computed(() => commandForm.commandType === 'CUSTOM' || commandForm.commandType === 'OPEN_TUNNEL')
const statCards = computed(() => [
  { key: 'pending', label: '待下发', value: stats.pending || 0, type: 'warning', icon: Clock },
  { key: 'delivered', label: '已送达', value: stats.delivered || 0, type: 'primary', icon: Promotion },
  { key: 'completed', label: '已完成', value: stats.completed || 0, type: 'success', icon: CircleCheck },
  { key: 'canceled', label: '已取消', value: stats.canceled || 0, type: 'info', icon: CloseBold },
  { key: 'failed', label: '失败', value: stats.failed || 0, type: 'danger', icon: Warning }
])
const detailTitle = computed(() => currentGroup.value.groupKey ? `${commandTypeText(currentGroup.value.commandType)}任务明细` : '任务明细')
const resultTitle = computed(() => currentCommand.value.id ? '指令执行结果' : '任务命令')

const templateNotes = {
  HEALTH_CHECK: '仅回传系统时间、内核信息，用于确认远程指令链路是否可用。',
  ENV_CHECK: '检查 aarch64、L4T、nvidia-smi、tegrastats、Docker 和 /opt/juxin-orin 节点目录。',
  UPGRADE_AGENT: '在线拉取并安装新版 Orin Agent；系统镜像和模型版本通过独立升级流程管理。',
  INSTALL_DEPS: '安装 curl、wget、python3 和 OpenSSH 客户端，并创建 /opt/juxin-orin/runtime。',
  OPEN_TUNNEL: '执行隧道脚本或自定义命令，需要设备已有 SSH 客户端和隧道密钥。',
  CLOSE_TUNNEL: '执行 /opt/juxin-orin/tunnel-control.sh stop。',
  RESTART_AGENT: '重启 juxin-orin-agent 远程 Agent 服务。',
  CUSTOM: '直接执行自定义命令，请只对可信设备和可信命令使用。'
}
const templateNote = computed(() => templateNotes[commandForm.commandType] || '')

const fetchStats = async () => {
  const res = await request.get('/api/admin/device-commands/stats')
  if (res.data.code === 200) {
    Object.assign(stats, res.data.data)
  }
}

const fetchCommandGroups = async () => {
  loading.value = true
  try {
    const res = await request.get('/api/admin/device-commands/groups', {
      params: {
        page: page.value,
        size: size.value,
        deviceSn: filters.deviceSn || undefined,
        commandType: filters.commandType || undefined,
        status: filters.status || undefined
      }
    })
    if (res.data.code === 200) {
      commandGroups.value = res.data.data.records || []
      total.value = res.data.data.total || 0
    }
  } finally {
    loading.value = false
    fetchStats()
  }
}

const fetchGroupRecords = async () => {
  if (!currentGroup.value.groupKey) return
  detailLoading.value = true
  try {
    const res = await request.get('/api/admin/device-commands/group-records', {
      params: {
        page: detailPage.value,
        size: detailSize.value,
        groupKey: currentGroup.value.groupKey,
        deviceSn: detailFilters.deviceSn || undefined,
        status: detailFilters.status || undefined
      }
    })
    if (res.data.code === 200) {
      commandDetails.value = res.data.data.records || []
      detailTotal.value = res.data.data.total || 0
    }
  } finally {
    detailLoading.value = false
  }
}

const searchDevices = async (keyword = '') => {
  deviceLoading.value = true
  try {
    const res = await request.get('/api/admin/sl/devices/list', {
      params: {
        page: 1,
        size: 100,
        sn: keyword || undefined,
        status: 1,
        remoteCapable: true
      }
    })
    if (res.data.code === 200) {
      deviceOptions.value = res.data.data.records || []
    }
  } finally {
    deviceLoading.value = false
  }
}

const fetchTargetCount = async () => {
  if (commandForm.targetScope === 'SINGLE') {
    targetCount.value = commandForm.deviceSn ? 1 : 0
    return targetCount.value
  }
  targetCountLoading.value = true
  try {
    const res = await request.get('/api/admin/device-commands/target-count', {
      params: {
        targetScope: commandForm.targetScope,
        locationKeyword: commandForm.locationKeyword || undefined,
        carrier: commandForm.carrier || undefined
      }
    })
    targetCount.value = res.data.code === 200 ? (res.data.data.count || 0) : 0
    return targetCount.value
  } finally {
    targetCountLoading.value = false
  }
}

const onCommandTypeChange = () => {
  if (!needsCommandText.value) {
    commandForm.commandText = ''
  }
}

const dispatchCommand = async () => {
  if (commandForm.targetScope === 'SINGLE' && !commandForm.deviceSn) {
    ElMessage.warning('请选择目标设备')
    return
  }
  if (commandForm.targetScope === 'LOCATION' && !commandForm.locationKeyword.trim()) {
    ElMessage.warning('请输入地区关键词')
    return
  }
  if (commandForm.targetScope === 'CARRIER' && !commandForm.carrier) {
    ElMessage.warning('请选择运营商')
    return
  }
  if (needsCommandText.value && !commandForm.commandText.trim()) {
    ElMessage.warning('请输入命令内容')
    return
  }
  const count = await fetchTargetCount()
  if (commandForm.targetScope !== 'SINGLE' && count <= 0) {
    ElMessage.warning('当前范围内没有可远程设备')
    return
  }
  if (commandForm.commandType === 'CUSTOM') {
    try {
      await ElMessageBox.confirm('自定义命令会直接在设备上执行，请确认命令来源可信。', '确认下发', {
        type: 'warning',
        confirmButtonText: '确认下发',
        cancelButtonText: '取消'
      })
    } catch (e) {
      return
    }
  }
  if (commandForm.targetScope !== 'SINGLE') {
    try {
      await ElMessageBox.confirm(`确认向 ${count} 台可远程设备批量下发「${commandTypeText(commandForm.commandType)}」？`, '批量下发确认', {
        type: 'warning',
        confirmButtonText: '批量下发',
        cancelButtonText: '取消'
      })
    } catch (e) {
      return
    }
  }

  dispatching.value = true
  try {
    const url = commandForm.targetScope === 'SINGLE'
      ? '/api/admin/device-commands/dispatch'
      : '/api/admin/device-commands/dispatch-batch'
    const payload = {
      targetScope: commandForm.targetScope,
      deviceSn: commandForm.deviceSn,
      locationKeyword: commandForm.locationKeyword || undefined,
      carrier: commandForm.carrier || undefined,
      commandType: commandForm.commandType,
      commandText: commandForm.commandText || undefined,
      remark: commandForm.remark || undefined
    }
    const res = await request.post(url, payload)
    if (res.data.code === 200) {
      const dispatchedCount = commandForm.targetScope === 'SINGLE' ? 1 : (res.data.data.count || 0)
      ElMessage.success(`已下发 ${dispatchedCount} 条指令`)
      commandForm.commandText = ''
      commandForm.remark = ''
      page.value = 1
      await fetchCommandGroups()
    } else {
      ElMessage.error(res.data.msg || '下发失败')
    }
  } finally {
    dispatching.value = false
  }
}

const cancelCommand = async (row) => {
  try {
    await ElMessageBox.confirm(`确认取消 ${row.commandNo}？`, '取消指令', {
      type: 'warning',
      confirmButtonText: '取消指令',
      cancelButtonText: '返回'
    })
  } catch (e) {
    return
  }
  const res = await request.post('/api/admin/device-commands/cancel', { id: row.id })
  if (res.data.code === 200) {
    ElMessage.success('已取消')
    await Promise.all([fetchCommandGroups(), fetchStats()])
    if (detailVisible.value) {
      await fetchGroupRecords()
    }
  } else {
    ElMessage.error(res.data.msg || '取消失败')
  }
}

const openGroupDetail = async (group) => {
  currentGroup.value = group
  detailFilters.deviceSn = ''
  detailFilters.status = ''
  detailPage.value = 1
  commandDetails.value = []
  detailVisible.value = true
  await fetchGroupRecords()
}

const viewResult = (row) => {
  currentCommand.value = row
  resultVisible.value = true
}

const viewCommandText = (group) => {
  currentCommand.value = group
  resultVisible.value = true
}

const refreshAll = async () => {
  await Promise.all([searchDevices(commandForm.deviceSn), fetchCommandGroups(), fetchStats(), fetchTargetCount()])
  if (detailVisible.value) {
    await fetchGroupRecords()
  }
}

const onFilterChange = () => {
  page.value = 1
  fetchCommandGroups()
}

const onDetailFilterChange = () => {
  detailPage.value = 1
  fetchGroupRecords()
}

const formatTime = (time) => {
  if (!time) return '-'
  return String(time).replace('T', ' ').substring(0, 19)
}
const statusText = (status) => ({ pending: '待下发', delivered: '已送达', completed: '已完成', canceled: '已取消', failed: '失败' }[status] || status || '-')
const statusType = (status) => ({ pending: 'warning', delivered: 'primary', completed: 'success', canceled: 'info', failed: 'danger' }[status] || 'info')
const commandTypeText = (type) => ({
  HEALTH_CHECK: '健康检查',
  ENV_CHECK: '远程环境检查',
  UPGRADE_AGENT: '升级远程 Agent',
  INSTALL_DEPS: '安装远程运维依赖',
  OPEN_TUNNEL: '建立隧道',
  CLOSE_TUNNEL: '关闭隧道',
  RESTART_AGENT: '重启 Agent',
  CUSTOM: '自定义命令'
}[type] || type || '-')
const commandSummary = (row) => {
  if (!row) return '-'
  if (row.commandType === 'UPGRADE_AGENT') {
    const version = (row.commandText || '').match(/VERSION='([^']+)'|VERSION="([^"]+)"/)
    return `在线升级 Agent${version ? ` 到 ${version[1] || version[2]}` : ''}`
  }
  if (row.commandType === 'INSTALL_DEPS') return '安装远程运维基础依赖'
  if (row.commandType === 'ENV_CHECK') return '检查设备远程环境'
  if (row.commandType === 'HEALTH_CHECK') return '确认远程指令链路'
  if (row.commandType === 'OPEN_TUNNEL') return '建立远程隧道'
  if (row.commandType === 'CLOSE_TUNNEL') return '关闭远程隧道'
  if (row.commandType === 'RESTART_AGENT') return '重启远程 Agent 服务'
  const text = row.commandText || ''
  return text.length > 80 ? `${text.slice(0, 80)}...` : (text || '-')
}

const groupProgress = (group) => {
  const totalCount = Number(group.totalCount || 0)
  if (!totalCount) return 0
  const finished = Number(group.completedCount || 0) + Number(group.failedCount || 0) + Number(group.canceledCount || 0)
  return Math.min(100, Math.round((finished / totalCount) * 100))
}

const groupStatus = (group) => {
  if ((group.failedCount || 0) > 0) return 'failed'
  if ((group.pendingCount || 0) > 0) return 'pending'
  if ((group.deliveredCount || 0) > 0) return 'delivered'
  if ((group.canceledCount || 0) > 0 && (group.completedCount || 0) === 0) return 'canceled'
  if ((group.completedCount || 0) > 0) return 'completed'
  return 'pending'
}

const groupStatusText = (group) => {
  if ((group.failedCount || 0) > 0) return '有失败'
  if ((group.pendingCount || 0) > 0) return '待下发'
  if ((group.deliveredCount || 0) > 0) return '执行中'
  if ((group.canceledCount || 0) > 0 && (group.completedCount || 0) === 0) return '已取消'
  if ((group.completedCount || 0) > 0) return '已完成'
  return '待处理'
}

let targetTimer = null
watch(
  () => [commandForm.targetScope, commandForm.locationKeyword, commandForm.carrier, commandForm.deviceSn],
  () => {
    clearTimeout(targetTimer)
    targetTimer = setTimeout(fetchTargetCount, 250)
  }
)

onMounted(() => {
  searchDevices()
  fetchCommandGroups()
  fetchStats()
  fetchTargetCount()
})
</script>

<style scoped>
.commands-page {
  width: 100%;
  color: var(--orin-text);
}

.stat-grid {
  margin-bottom: 14px;
}

.stat-card,
.dispatch-panel,
.filter-bar,
.task-container {
  background: var(--orin-surface);
  border: 1px solid var(--orin-border);
  border-radius: 6px;
  box-shadow: none;
}

.stat-card {
  position: relative;
  height: 104px;
  overflow: hidden;
  padding: 17px 18px;
  border-left-width: 3px;
}

.stat-card.warning { border-left-color: var(--orin-amber); }
.stat-card.primary { border-left-color: var(--orin-green); }
.stat-card.success { border-left-color: var(--orin-green-bright); }
.stat-card.info { border-left-color: var(--orin-border-strong); }
.stat-card.danger { border-left-color: var(--orin-danger); }

.card-label {
  margin-bottom: 8px;
  color: var(--orin-muted);
  font-size: 12px;
}

.card-val {
  color: var(--orin-text);
  font-size: 29px;
  font-weight: 800;
  line-height: 1.1;
}

.card-icon {
  position: absolute;
  right: 16px;
  bottom: 15px;
  color: var(--orin-green);
  font-size: 38px;
  opacity: 0.26;
}

.stat-card.warning .card-icon { color: var(--orin-amber); }
.stat-card.info .card-icon { color: var(--orin-text-soft); }
.stat-card.danger .card-icon { color: var(--orin-danger); }

.dispatch-panel {
  margin-bottom: 14px;
  padding: 18px;
}

.panel-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
  color: var(--orin-text);
  font-weight: 800;
}

.panel-title :deep(.el-icon) {
  color: var(--orin-green);
}

.dispatch-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 68px;
}

.target-scope {
  width: 100%;
  display: flex;
}

.target-scope :deep(.el-radio-button) {
  flex: 1;
}

.target-scope :deep(.el-radio-button__inner) {
  width: 100%;
  padding-left: 10px;
  padding-right: 10px;
  color: var(--orin-text-soft);
  background: var(--orin-surface-raised);
  border-color: var(--orin-border);
  box-shadow: none;
}

.target-scope :deep(.el-radio-button.is-active .el-radio-button__inner) {
  color: #ffffff;
  background: var(--orin-green);
  border-color: var(--orin-green);
  box-shadow: -1px 0 0 0 var(--orin-green);
}

.target-hint {
  margin: -4px 0 16px;
  color: var(--orin-muted);
  font-size: 13px;
}

.template-note {
  margin: -4px 0 16px;
  padding: 10px 12px;
  color: var(--orin-text-soft);
  background: var(--orin-surface-soft);
  border: 1px solid var(--orin-border-soft);
  border-left: 3px solid var(--orin-green-dark);
  border-radius: 6px;
  font-size: 13px;
  line-height: 1.5;
}

.filter-bar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 14px;
  padding: 14px 16px;
}

.task-container {
  padding: 16px;
}

.task-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 20px;
  margin-bottom: 14px;
}

.task-title {
  color: var(--orin-text);
  font-size: 16px;
  font-weight: 800;
}

.task-subtitle,
.task-count {
  margin-top: 4px;
  color: var(--orin-muted);
  font-size: 13px;
}

.task-card {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  padding: 15px 16px;
  background: var(--orin-surface-soft);
  border: 1px solid var(--orin-border-soft);
  border-radius: 6px;
  cursor: pointer;
  transition: background-color 0.16s ease, border-color 0.16s ease;
}

.task-card + .task-card {
  margin-top: 12px;
}

.task-card:hover {
  background: var(--orin-surface-hover);
  border-color: var(--orin-green-dark);
}

.task-main {
  flex: 1;
  min-width: 0;
}

.task-topline {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}

.task-name {
  color: var(--orin-text);
  font-weight: 800;
}

.task-actions {
  width: 138px;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  justify-content: center;
}

.progress-row {
  display: grid;
  grid-template-columns: 1fr 42px;
  gap: 10px;
  align-items: center;
  margin-top: 12px;
}

.progress-text {
  color: var(--orin-muted);
  font-size: 12px;
  text-align: right;
}

.progress-row :deep(.el-progress-bar__outer) {
  background: var(--orin-surface-raised);
}

.progress-row :deep(.el-progress-bar__inner) {
  background-color: var(--orin-green);
}

.status-pills {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.status-pills span,
.drawer-stats span {
  padding: 4px 8px;
  color: var(--orin-text-soft);
  background: var(--orin-surface-raised);
  border: 1px solid var(--orin-border-soft);
  border-radius: 4px;
  font-size: 12px;
}

.drawer-summary {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  margin-bottom: 16px;
  padding: 14px 16px;
  background: var(--orin-surface-soft);
  border: 1px solid var(--orin-border);
  border-left: 3px solid var(--orin-green);
  border-radius: 6px;
}

.drawer-command {
  color: var(--orin-text);
  font-size: 15px;
  font-weight: 800;
}

.drawer-stats,
.detail-filter {
  display: flex;
  gap: 10px;
  align-items: center;
  flex-wrap: wrap;
}

.detail-filter {
  margin-bottom: 16px;
}

.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", monospace;
}

.command-summary {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 28px;
}

.summary-main {
  color: var(--orin-text-soft);
  font-size: 13px;
  line-height: 1.4;
  word-break: break-word;
}

.muted {
  color: var(--orin-muted);
  font-size: 12px;
}

.remark {
  margin-top: 6px;
}

.pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 18px;
}

.result-title {
  margin: 18px 0 8px;
  color: var(--orin-text);
  font-weight: 800;
}

.result-box {
  margin: 0;
  padding: 14px;
  color: var(--orin-text-soft);
  background: var(--orin-surface-soft);
  border: 1px solid var(--orin-border);
  border-radius: 6px;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 260px;
  overflow: auto;
}

.result-box.output {
  color: var(--orin-text);
  background: #0d100d;
  border-left: 3px solid var(--orin-green-dark);
}

@media (max-width: 900px) {
  .stat-grid {
    row-gap: 12px;
  }

  .stat-grid > :deep(.el-col) {
    flex: 0 0 33.3333%;
    max-width: 33.3333%;
  }

  .dispatch-panel,
  .task-container {
    padding: 14px;
  }

  .dispatch-panel :deep(.el-col) {
    flex: 0 0 100%;
    max-width: 100%;
  }

  .target-scope {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .dispatch-actions {
    min-height: 0;
    padding-top: 4px;
  }

  .drawer-summary {
    align-items: flex-start;
    flex-direction: column;
  }

  .detail-filter {
    align-items: stretch;
  }
}

@media (max-width: 520px) {
  .stat-grid > :deep(.el-col) {
    flex: 0 0 50%;
    max-width: 50%;
  }

  .stat-card {
    height: 92px;
    padding: 14px;
  }

  .card-val {
    font-size: 25px;
  }

  .card-icon {
    right: 12px;
    bottom: 12px;
    font-size: 32px;
  }

  .dispatch-panel,
  .filter-bar,
  .task-container {
    padding: 10px;
  }

  .filter-bar :deep(.el-input),
  .filter-bar :deep(.el-select),
  .detail-filter :deep(.el-input),
  .detail-filter :deep(.el-select),
  .detail-filter :deep(.el-button) {
    width: 100% !important;
  }

  .task-head,
  .task-card {
    align-items: stretch;
    flex-direction: column;
  }

  .task-topline {
    align-items: flex-start;
    flex-wrap: wrap;
  }

  .task-actions {
    width: 100%;
    align-items: center;
    flex-direction: row;
    justify-content: flex-start;
  }

  .status-pills,
  .drawer-stats {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    width: 100%;
  }

  .pagination {
    justify-content: flex-start;
    overflow-x: auto;
    padding-bottom: 4px;
  }

  .result-box {
    padding: 10px;
    font-size: 12px;
  }
}
</style>
