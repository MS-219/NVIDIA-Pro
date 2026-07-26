<template>
  <div class="upgrade-page">
    <el-row :gutter="20" class="stat-grid">
      <el-col :span="4" v-for="item in statCards" :key="item.key">
        <div class="stat-card" :class="item.type">
          <div class="card-label">{{ item.label }}</div>
          <div class="card-val">{{ item.value }}</div>
          <el-icon class="card-icon"><component :is="item.icon" /></el-icon>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="20" class="top-row">
      <el-col :span="10">
        <div class="panel">
          <div class="panel-title">
            <el-icon><UploadFilled /></el-icon>
            上传升级包
          </div>
          <el-form :model="uploadForm" label-position="top">
            <el-form-item label="版本号">
              <el-input v-model="uploadForm.version" placeholder="例如：V2.6" clearable />
            </el-form-item>
            <el-form-item label="更新说明">
              <el-input
                v-model="uploadForm.releaseNote"
                type="textarea"
                :rows="3"
                placeholder="本次升级修复和新增内容"
              />
            </el-form-item>
            <el-upload
              ref="uploadRef"
              drag
              :auto-upload="false"
              :limit="1"
              :on-change="onFileChange"
              :on-remove="onFileRemove"
              accept=".tar.gz,.tgz"
            >
              <el-icon class="upload-icon"><UploadFilled /></el-icon>
              <div class="el-upload__text">拖入升级包或点击选择</div>
            </el-upload>
            <div class="actions">
              <el-button type="primary" :icon="UploadFilled" :loading="uploading" @click="uploadPackage">
                上传升级包
              </el-button>
              <el-button :icon="Refresh" @click="refreshAll">刷新</el-button>
            </div>
          </el-form>
        </div>
      </el-col>

      <el-col :span="14">
        <div class="panel">
          <div class="panel-title">
            <el-icon><Promotion /></el-icon>
            发起升级
          </div>
          <el-form :model="batchForm" label-position="top">
            <el-row :gutter="16">
              <el-col :span="10">
                <el-form-item label="升级包版本">
                  <el-select v-model="batchForm.packageId" placeholder="选择升级包" style="width: 100%" @change="onPackageChange">
                    <el-option
                      v-for="item in activePackages"
                      :key="item.id"
                      :label="`${item.version} · ${item.fileName}`"
                      :value="item.id"
                    />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="14">
                <el-form-item label="目标范围">
                  <el-radio-group v-model="batchForm.targetScope" class="target-scope">
                    <el-radio-button label="ALL_ONLINE">全部在线</el-radio-button>
                    <el-radio-button label="LOCATION">按地区</el-radio-button>
                    <el-radio-button label="CARRIER">按运营商</el-radio-button>
                  </el-radio-group>
                </el-form-item>
              </el-col>
              <el-col v-if="batchForm.targetScope === 'LOCATION'" :span="10">
                <el-form-item label="地区关键词">
                  <el-input v-model="batchForm.locationKeyword" placeholder="例如：山东省 / 枣庄市" clearable />
                </el-form-item>
              </el-col>
              <el-col v-if="batchForm.targetScope === 'CARRIER'" :span="10">
                <el-form-item label="运营商">
                  <el-select v-model="batchForm.carrier" placeholder="选择运营商" clearable style="width: 100%">
                    <el-option label="中国移动" value="中国移动" />
                    <el-option label="中国联通" value="中国联通" />
                    <el-option label="中国电信" value="中国电信" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="10">
                <el-form-item label="升级策略">
                  <el-checkbox v-model="batchForm.onlyOutdated">只升级低于目标版本或未上报版本的设备</el-checkbox>
                </el-form-item>
              </el-col>
              <el-col :span="14">
                <el-form-item label="备注">
                  <el-input v-model="batchForm.remark" placeholder="例如：山东区域 Agent V2.6 灰度" clearable />
                </el-form-item>
              </el-col>
            </el-row>
            <div class="target-hint">
              <span v-if="targetCountLoading">正在统计目标设备...</span>
              <span v-else>当前范围预计升级 {{ targetCount }} 台设备</span>
            </div>
            <div class="actions">
              <el-button type="primary" :icon="Promotion" :loading="creatingBatch" @click="createBatch">
                开始升级
              </el-button>
            </div>
          </el-form>
        </div>
      </el-col>
    </el-row>

    <div class="panel">
      <div class="panel-head">
        <div class="panel-title">
          <el-icon><Box /></el-icon>
          升级包
        </div>
      </div>
      <el-table :data="packages" border stripe v-loading="packageLoading">
        <el-table-column prop="version" label="版本" width="110" />
        <el-table-column prop="fileName" label="文件" min-width="220" />
        <el-table-column prop="releaseNote" label="更新说明" min-width="220" show-overflow-tooltip />
        <el-table-column label="大小" width="110">
          <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
        </el-table-column>
        <el-table-column label="SHA-256" min-width="260">
          <template #default="{ row }">
            <span class="mono checksum">{{ row.checksum }}</span>
          </template>
        </el-table-column>
        <el-table-column label="上传时间" width="170">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'active' ? 'success' : 'info'" effect="plain">{{ row.status === 'active' ? '可用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div class="panel">
      <div class="panel-head">
        <div class="panel-title">
          <el-icon><List /></el-icon>
          升级批次
        </div>
        <el-button :icon="Refresh" @click="refreshAll">刷新状态</el-button>
      </div>
      <el-table :data="batches" border stripe v-loading="batchLoading" @row-click="selectBatch">
        <el-table-column prop="batchNo" label="批次号" width="170" />
        <el-table-column prop="targetVersion" label="目标版本" width="110" />
        <el-table-column label="范围" width="170">
          <template #default="{ row }">{{ scopeText(row) }}</template>
        </el-table-column>
        <el-table-column label="进度" min-width="260">
          <template #default="{ row }">
            <el-progress :percentage="progress(row)" :stroke-width="8" />
            <div class="muted">
              总数 {{ row.totalCount || 0 }}
              · 待下发 {{ row.pendingCount || 0 }}
              · 已送达 {{ row.deliveredCount || 0 }}
              · 升级中 {{ row.upgradingCount || 0 }}
              · 成功 {{ row.successCount || 0 }}
              · 失败 {{ row.failedCount || 0 }}
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="batchStatusType(row.status)" effect="plain">{{ batchStatusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="开始/完成" width="190">
          <template #default="{ row }">
            <div>{{ formatTime(row.startedAt || row.createTime) }}</div>
            <div class="muted">完成 {{ formatTime(row.finishedAt) }}</div>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div class="panel">
      <div class="panel-head">
        <div class="panel-title">
          <el-icon><Tickets /></el-icon>
          设备升级明细
        </div>
        <div class="filters">
          <el-input v-model="recordFilters.deviceSn" placeholder="搜索 SN" clearable style="width: 220px" @keyup.enter="fetchRecords" @clear="fetchRecords" />
          <el-select v-model="recordFilters.status" placeholder="状态" clearable style="width: 140px" @change="fetchRecords">
            <el-option label="待下发" value="pending" />
            <el-option label="已送达" value="delivered" />
            <el-option label="升级中" value="upgrading" />
            <el-option label="成功" value="success" />
            <el-option label="失败" value="failed" />
            <el-option label="跳过" value="skipped" />
          </el-select>
        </div>
      </div>
      <el-table :data="records" border stripe v-loading="recordLoading">
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="recordStatusType(row.status)" effect="plain">{{ recordStatusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="deviceSn" label="设备 SN" width="180" />
        <el-table-column label="版本" width="170">
          <template #default="{ row }">
            <span class="mono">{{ row.fromVersion || '未知版本' }}</span>
            <span class="muted"> → </span>
            <span class="mono">{{ row.targetVersion }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="commandNo" label="指令编号" width="180" />
        <el-table-column label="结果" min-width="260">
          <template #default="{ row }">
            <div>{{ row.errorMsg || resultSummary(row.resultText) || '-' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="时间" width="190">
          <template #default="{ row }">
            <div>{{ formatTime(row.createTime) }}</div>
            <div class="muted">完成 {{ formatTime(row.finishedAt) }}</div>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination">
        <el-pagination
          v-model:current-page="recordPage"
          :page-size="recordSize"
          :total="recordTotal"
          layout="total, prev, pager, next"
          @current-change="fetchRecords"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import request from '../utils/request'
import { Box, CircleCheck, Clock, List, Promotion, Refresh, Tickets, UploadFilled, Warning } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'

const uploadRef = ref(null)
const selectedFile = ref(null)
const uploading = ref(false)
const creatingBatch = ref(false)
const packageLoading = ref(false)
const batchLoading = ref(false)
const recordLoading = ref(false)
const targetCountLoading = ref(false)
const packages = ref([])
const batches = ref([])
const records = ref([])
const targetCount = ref(0)
const selectedBatchId = ref(null)
const recordPage = ref(1)
const recordSize = ref(10)
const recordTotal = ref(0)

const stats = reactive({
  packages: 0,
  runningBatches: 0,
  upgrading: 0,
  success: 0,
  failed: 0
})

const uploadForm = reactive({
  version: '',
  releaseNote: ''
})

const batchForm = reactive({
  packageId: '',
  targetScope: 'ALL_ONLINE',
  locationKeyword: '',
  carrier: '',
  onlyOutdated: true,
  remark: ''
})

const recordFilters = reactive({
  deviceSn: '',
  status: ''
})

const statCards = computed(() => [
  { key: 'packages', label: '升级包', value: stats.packages || 0, type: 'primary', icon: Box },
  { key: 'runningBatches', label: '进行中批次', value: stats.runningBatches || 0, type: 'warning', icon: Clock },
  { key: 'upgrading', label: '升级中设备', value: stats.upgrading || 0, type: 'indigo', icon: Promotion },
  { key: 'success', label: '成功记录', value: stats.success || 0, type: 'success', icon: CircleCheck },
  { key: 'failed', label: '失败记录', value: stats.failed || 0, type: 'danger', icon: Warning }
])

const selectedPackage = computed(() => packages.value.find(item => item.id === batchForm.packageId))
const activePackages = computed(() => packages.value.filter(item => item.status === 'active'))

const onFileChange = (file) => {
  selectedFile.value = file.raw
}

const onFileRemove = () => {
  selectedFile.value = null
}

const uploadPackage = async () => {
  if (!uploadForm.version.trim()) {
    ElMessage.warning('请填写版本号')
    return
  }
  if (!selectedFile.value) {
    ElMessage.warning('请选择升级包文件')
    return
  }
  uploading.value = true
  try {
    const form = new FormData()
    form.append('file', selectedFile.value)
    form.append('version', uploadForm.version.trim())
    form.append('releaseNote', uploadForm.releaseNote || '')
    const res = await request.post('/api/admin/device-upgrades/packages/upload', form, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 120000
    })
    if (res.data.code === 200) {
      ElMessage.success('升级包已上传')
      uploadForm.version = ''
      uploadForm.releaseNote = ''
      selectedFile.value = null
      uploadRef.value?.clearFiles()
      await fetchPackages()
      batchForm.packageId = activePackages.value[0]?.id || ''
      await refreshAll()
    } else {
      ElMessage.error(res.data.msg || '上传失败')
    }
  } finally {
    uploading.value = false
  }
}

const fetchStats = async () => {
  const res = await request.get('/api/admin/device-upgrades/stats')
  if (res.data.code === 200) {
    Object.assign(stats, res.data.data)
  }
}

const fetchPackages = async () => {
  packageLoading.value = true
  try {
    const res = await request.get('/api/admin/device-upgrades/packages', { params: { page: 1, size: 20 } })
    if (res.data.code === 200) {
      packages.value = res.data.data.records || []
      if (!batchForm.packageId && activePackages.value.length) {
        batchForm.packageId = activePackages.value[0].id
      }
    }
  } finally {
    packageLoading.value = false
  }
}

const fetchTargetCount = async () => {
  targetCountLoading.value = true
  try {
    const res = await request.get('/api/admin/device-upgrades/target-count', {
      params: {
        targetScope: batchForm.targetScope,
        locationKeyword: batchForm.locationKeyword || undefined,
        carrier: batchForm.carrier || undefined,
        targetVersion: selectedPackage.value?.version,
        onlyOutdated: batchForm.onlyOutdated
      }
    })
    targetCount.value = res.data.code === 200 ? (res.data.data.count || 0) : 0
  } finally {
    targetCountLoading.value = false
  }
}

const createBatch = async () => {
  if (!batchForm.packageId) {
    ElMessage.warning('请选择升级包')
    return
  }
  if (batchForm.targetScope === 'LOCATION' && !batchForm.locationKeyword.trim()) {
    ElMessage.warning('请输入地区关键词')
    return
  }
  if (batchForm.targetScope === 'CARRIER' && !batchForm.carrier) {
    ElMessage.warning('请选择运营商')
    return
  }
  await fetchTargetCount()
  if (targetCount.value <= 0) {
    ElMessage.warning('当前范围内没有可升级设备')
    return
  }
  try {
    await ElMessageBox.confirm(`确认向 ${targetCount.value} 台设备下发升级到 ${selectedPackage.value?.version}？`, '发起设备升级', {
      type: 'warning',
      confirmButtonText: '开始升级',
      cancelButtonText: '取消'
    })
  } catch (e) {
    return
  }

  creatingBatch.value = true
  try {
    const res = await request.post('/api/admin/device-upgrades/batches', {
      packageId: batchForm.packageId,
      targetScope: batchForm.targetScope,
      locationKeyword: batchForm.locationKeyword || undefined,
      carrier: batchForm.carrier || undefined,
      onlyOutdated: batchForm.onlyOutdated,
      remark: batchForm.remark || undefined
    })
    if (res.data.code === 200) {
      ElMessage.success('升级批次已创建')
      selectedBatchId.value = res.data.data.id
      batchForm.remark = ''
      await refreshAll()
    } else {
      ElMessage.error(res.data.msg || '创建失败')
    }
  } finally {
    creatingBatch.value = false
  }
}

const fetchBatches = async () => {
  batchLoading.value = true
  try {
    const res = await request.get('/api/admin/device-upgrades/batches', { params: { page: 1, size: 10 } })
    if (res.data.code === 200) {
      batches.value = res.data.data.records || []
      if (!selectedBatchId.value && batches.value.length) {
        selectedBatchId.value = batches.value[0].id
      }
    }
  } finally {
    batchLoading.value = false
  }
}

const fetchRecords = async () => {
  recordLoading.value = true
  try {
    const res = await request.get('/api/admin/device-upgrades/records', {
      params: {
        page: recordPage.value,
        size: recordSize.value,
        batchId: selectedBatchId.value || undefined,
        deviceSn: recordFilters.deviceSn || undefined,
        status: recordFilters.status || undefined
      }
    })
    if (res.data.code === 200) {
      records.value = res.data.data.records || []
      recordTotal.value = res.data.data.total || 0
    }
  } finally {
    recordLoading.value = false
  }
}

const selectBatch = (row) => {
  selectedBatchId.value = row.id
  recordPage.value = 1
  fetchRecords()
}

const refreshAll = async () => {
  await Promise.all([fetchStats(), fetchPackages(), fetchBatches(), fetchTargetCount()])
  await fetchRecords()
}

const onPackageChange = () => {
  fetchTargetCount()
}

const progress = (row) => {
  const total = row.totalCount || 0
  if (!total) return 0
  return Math.round(((row.successCount || 0) + (row.failedCount || 0) + (row.skippedCount || 0)) * 100 / total)
}

const scopeText = (row) => {
  if (row.targetScope === 'LOCATION') return `地区：${row.locationKeyword || '-'}`
  if (row.targetScope === 'CARRIER') return `运营商：${row.carrier || '-'}`
  return '全部在线'
}

const resultSummary = (text) => {
  if (!text) return ''
  const lines = text.split('\n').filter(Boolean)
  return lines.slice(-2).join(' / ')
}

const formatTime = (time) => time ? time.replace('T', ' ').substring(0, 19) : '-'
const formatSize = (size) => {
  const num = Number(size || 0)
  if (num > 1024 * 1024) return `${(num / 1024 / 1024).toFixed(2)} MB`
  if (num > 1024) return `${(num / 1024).toFixed(1)} KB`
  return `${num} B`
}
const batchStatusText = (status) => ({ running: '进行中', completed: '已完成', failed: '失败', partial: '部分成功' }[status] || status || '-')
const batchStatusType = (status) => ({ running: 'warning', completed: 'success', failed: 'danger', partial: 'primary' }[status] || 'info')
const recordStatusText = (status) => ({ pending: '待下发', delivered: '已送达', upgrading: '升级中', success: '成功', failed: '失败', skipped: '跳过', timeout: '超时' }[status] || status || '-')
const recordStatusType = (status) => ({ pending: 'warning', delivered: 'primary', upgrading: 'primary', success: 'success', failed: 'danger', skipped: 'info', timeout: 'danger' }[status] || 'info')

let targetTimer = null
watch(
  () => [batchForm.packageId, batchForm.targetScope, batchForm.locationKeyword, batchForm.carrier, batchForm.onlyOutdated],
  () => {
    clearTimeout(targetTimer)
    targetTimer = setTimeout(fetchTargetCount, 250)
  }
)

onMounted(refreshAll)
</script>

<style scoped>
.upgrade-page {
  width: 100%;
}

.stat-grid,
.top-row {
  margin-bottom: 20px;
}

.stat-card,
.panel {
  background: white;
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0,0,0,0.05);
}

.stat-card {
  height: 108px;
  padding: 20px;
  position: relative;
  overflow: hidden;
}

.stat-card.primary { border-left: 4px solid #1890ff; }
.stat-card.warning { border-left: 4px solid #faad14; }
.stat-card.indigo { border-left: 4px solid #6366f1; }
.stat-card.success { border-left: 4px solid #52c41a; }
.stat-card.danger { border-left: 4px solid #f56c6c; }

.card-label {
  font-size: 14px;
  color: #909399;
  margin-bottom: 8px;
}

.card-val {
  font-size: 30px;
  font-weight: 800;
  color: #303133;
}

.card-icon {
  position: absolute;
  right: 18px;
  bottom: 18px;
  font-size: 44px;
  opacity: 0.1;
}

.panel {
  padding: 22px;
  margin-bottom: 20px;
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.panel-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 800;
  color: #303133;
  margin-bottom: 16px;
}

.panel-head .panel-title {
  margin-bottom: 0;
}

.upload-icon {
  font-size: 42px;
  color: #1890ff;
}

.actions {
  margin-top: 16px;
  display: flex;
  gap: 10px;
  align-items: center;
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
}

.target-hint {
  padding: 10px 12px;
  border-radius: 6px;
  background: #f6f8fa;
  color: #606266;
  font-size: 13px;
}

.filters {
  display: flex;
  gap: 12px;
}

.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", monospace;
}

.checksum {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  vertical-align: bottom;
}

.muted {
  color: #909399;
  font-size: 12px;
}

.pagination {
  margin-top: 18px;
  display: flex;
  justify-content: flex-end;
}
</style>
