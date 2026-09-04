<template>
  <div class="page-container">
    <div class="settings-hero">
      <div>
        <h1>系统配置中心</h1>
        <p>管理设备心跳、离线检测、收益结算等核心参数。修改后实时生效，设备将在下次心跳时自动获取最新配置。</p>
      </div>
    </div>

    <div class="settings-grid">
      <!-- 设备通信配置 -->
      <div class="panel">
        <div class="panel-head">
          <h3><el-icon><Connection /></el-icon> 设备通信配置</h3>
          <el-tag effect="plain" type="warning">影响全部在线设备</el-tag>
        </div>
        <div class="form-section">
          <div class="form-item">
            <div class="form-label">
              <strong>心跳上报间隔</strong>
              <p>设备向服务器报告状态的频率。增大可节省带宽，但状态更新变慢。</p>
            </div>
            <div class="form-control">
              <el-input-number
                v-model="deviceConfig.heartbeatInterval"
                :min="10"
                :max="300"
                :step="10"
                controls-position="right"
              />
              <span class="unit">秒</span>
            </div>
          </div>
          <div class="form-item">
            <div class="form-label">
              <strong>任务轮询间隔</strong>
              <p>设备查询待执行任务的频率。未启用任务功能时建议设大以节省带宽。</p>
            </div>
            <div class="form-control">
              <el-input-number
                v-model="deviceConfig.taskPollInterval"
                :min="5"
                :max="300"
                :step="10"
                controls-position="right"
              />
              <span class="unit">秒</span>
            </div>
          </div>
          <div class="form-item">
            <div class="form-label">
              <strong>离线判定阈值</strong>
              <p>超过此时间未收到心跳则标记设备离线。建议设为心跳间隔的 2~3 倍。</p>
            </div>
            <div class="form-control">
              <el-input-number
                v-model="deviceConfig.offlineThreshold"
                :min="30"
                :max="600"
                :step="30"
                controls-position="right"
              />
              <span class="unit">秒</span>
            </div>
          </div>
          <div class="form-item">
            <div class="form-label">
              <strong>收益结算心跳超时</strong>
              <p>收益结算时判定设备活跃的心跳超时阈值。超过此时间无心跳的设备不参与结算。</p>
            </div>
            <div class="form-control">
              <el-input-number
                v-model="deviceConfig.heartbeatTimeout"
                :min="60"
                :max="600"
                :step="30"
                controls-position="right"
              />
              <span class="unit">秒</span>
            </div>
          </div>
        </div>

        <!-- 带宽估算 -->
        <div class="bandwidth-card">
          <div class="bandwidth-title"><el-icon><DataAnalysis /></el-icon> 带宽估算（基于当前在线设备数）</div>
          <div class="bandwidth-grid">
            <div class="bandwidth-item">
              <span class="bw-label">在线设备</span>
              <span class="bw-value">{{ onlineCount }} 台</span>
            </div>
            <div class="bandwidth-item">
              <span class="bw-label">心跳流量</span>
              <span class="bw-value">{{ heartbeatBandwidth }}</span>
            </div>
            <div class="bandwidth-item">
              <span class="bw-label">轮询流量</span>
              <span class="bw-value">{{ taskPollBandwidth }}</span>
            </div>
            <div class="bandwidth-item highlight">
              <span class="bw-label">预估总占用</span>
              <span class="bw-value">{{ totalBandwidth }}</span>
            </div>
          </div>
        </div>

        <div class="form-actions">
          <el-button type="primary" @click="saveDeviceConfig" :loading="saving">保存设备配置</el-button>
          <el-button @click="loadSettings">重置</el-button>
        </div>
      </div>

      <!-- 设备管理配置 -->
      <div class="panel">
        <div class="panel-head">
          <h3><el-icon><Setting /></el-icon> 设备管理配置</h3>
        </div>
        <div class="form-section">
          <div class="form-item">
            <div class="form-label">
              <strong>自动分配业务</strong>
              <p>新设备绑定后自动分配到可用业务组。</p>
            </div>
            <div class="form-control">
              <el-switch v-model="deviceConfig.autoAssignBusiness" />
            </div>
          </div>
          <div class="form-item">
            <div class="form-label">
              <strong>初始算力值</strong>
              <p>新设备绑定时的初始聚芯算力值。</p>
            </div>
            <div class="form-control">
              <el-input-number
                v-model="deviceConfig.initialHashrate"
                :min="0"
                :max="10000"
                :step="100"
                controls-position="right"
              />
            </div>
          </div>
        </div>
        <div class="form-actions">
          <el-button type="primary" @click="saveDeviceConfig" :loading="saving">保存设备配置</el-button>
        </div>

        <!-- 当前配置快照 -->
        <div class="config-snapshot">
          <div class="snapshot-title">当前运行配置</div>
          <div class="snapshot-grid">
            <div class="snapshot-item" v-for="item in configSnapshot" :key="item.label">
              <span class="snap-label">{{ item.label }}</span>
              <span class="snap-value">{{ item.value }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import request from '../utils/request'
import { ElMessage } from 'element-plus'
import { Connection, DataAnalysis, Setting } from '@element-plus/icons-vue'

const saving = ref(false)
const onlineCount = ref(0)

const deviceConfig = reactive({
  heartbeatInterval: 60,
  taskPollInterval: 60,
  offlineThreshold: 180,
  heartbeatTimeout: 120,
  autoAssignBusiness: true,
  initialHashrate: 100,
  powerMode: 'MAXN_SUPER',
})

const powerModeLabels = {
  '15W': '节能 15W',
  '25W': '均衡 25W',
  MAXN_SUPER: '最大性能 MAXN SUPER',
}

// 带宽估算
const calcBandwidth = (intervalSec) => {
  if (!intervalSec || intervalSec <= 0) return '0 kbps'
  const kbps = (onlineCount.value * 1.0 / intervalSec) * 8
  if (kbps > 1000) return (kbps / 1000).toFixed(1) + ' Mbps'
  return kbps.toFixed(0) + ' kbps'
}

const heartbeatBandwidth = computed(() => calcBandwidth(deviceConfig.heartbeatInterval))
const taskPollBandwidth = computed(() => calcBandwidth(deviceConfig.taskPollInterval))
const totalBandwidth = computed(() => {
  const hb = deviceConfig.heartbeatInterval > 0 ? onlineCount.value / deviceConfig.heartbeatInterval : 0
  const tp = deviceConfig.taskPollInterval > 0 ? onlineCount.value / deviceConfig.taskPollInterval : 0
  const kbps = (hb + tp) * 8
  if (kbps > 1000) return (kbps / 1000).toFixed(1) + ' Mbps'
  return kbps.toFixed(0) + ' kbps'
})

const configSnapshot = computed(() => [
  { label: '心跳间隔', value: deviceConfig.heartbeatInterval + ' 秒' },
  { label: '轮询间隔', value: deviceConfig.taskPollInterval + ' 秒' },
  { label: '离线阈值', value: deviceConfig.offlineThreshold + ' 秒' },
  { label: '结算超时', value: deviceConfig.heartbeatTimeout + ' 秒' },
  { label: '自动分配', value: deviceConfig.autoAssignBusiness ? '开启' : '关闭' },
  { label: '初始算力', value: deviceConfig.initialHashrate },
])

const loadSettings = async () => {
  try {
    const res = await request.get('/api/settings/all')
    if (res.data.code === 200) {
      const d = res.data.data.device || {}
      deviceConfig.heartbeatInterval = d.heartbeatInterval ?? 60
      deviceConfig.taskPollInterval = d.taskPollInterval ?? 60
      deviceConfig.offlineThreshold = d.offlineThreshold ?? 180
      deviceConfig.heartbeatTimeout = d.heartbeatTimeout ?? 120
      deviceConfig.autoAssignBusiness = d.autoAssignBusiness ?? true
      deviceConfig.initialHashrate = d.initialHashrate ?? 100
      deviceConfig.powerMode = d.powerMode ?? 'MAXN_SUPER'
    }
  } catch (e) {
    console.error('加载配置失败', e)
  }
}

const fetchOnlineCount = async () => {
  try {
    const res = await request.get('/api/admin/sl/devices/stats')
    if (res.data.code === 200) {
      onlineCount.value = res.data.data.onlineCount || 0
    }
  } catch (e) { /* ignore */ }
}

const saveDeviceConfig = async () => {
  saving.value = true
  try {
    const res = await request.post('/api/settings/device', {
      heartbeatInterval: deviceConfig.heartbeatInterval,
      taskPollInterval: deviceConfig.taskPollInterval,
      offlineThreshold: deviceConfig.offlineThreshold,
      heartbeatTimeout: deviceConfig.heartbeatTimeout,
      autoAssignBusiness: deviceConfig.autoAssignBusiness,
      initialHashrate: deviceConfig.initialHashrate,
      powerMode: deviceConfig.powerMode,
    })
    if (res.data.code === 200) {
      ElMessage.success('设备配置保存成功，设备将在下次心跳时获取新配置')
    } else {
      ElMessage.error(res.data.msg || '保存失败')
    }
  } catch (e) {
    ElMessage.error('保存失败: ' + (e.message || '网络错误'))
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  loadSettings()
  fetchOnlineCount()
})
</script>

<style scoped>
.page-container {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 0 0 24px;
  color: var(--orin-text);
}

.settings-hero {
  padding: 18px 20px;
  color: var(--orin-text);
  background: var(--orin-surface-soft);
  border: 1px solid var(--orin-border);
  border-left: 3px solid var(--orin-green);
  border-radius: 6px;
}

.settings-hero h1 {
  margin: 0 0 7px;
  color: var(--orin-text);
  font-size: 22px;
  line-height: 1.2;
}

.settings-hero p {
  max-width: 880px;
  margin: 0;
  color: var(--orin-muted);
  font-size: 13px;
  line-height: 1.65;
}

.settings-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.panel {
  padding: 16px;
  background: var(--orin-surface);
  border: 1px solid var(--orin-border);
  border-radius: 6px;
  box-shadow: none;
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.panel-head h3 {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0;
  color: var(--orin-text);
  font-size: 15px;
}

.panel-head h3 :deep(.el-icon) {
  color: var(--orin-green);
}

.form-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.form-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  min-height: 68px;
  padding: 12px 14px;
  background: var(--orin-surface-soft);
  border: 1px solid var(--orin-border-soft);
  border-radius: 5px;
}

.form-label {
  flex: 1;
  min-width: 0;
}

.form-label strong {
  display: block;
  margin-bottom: 4px;
  color: var(--orin-text-soft);
  font-size: 13px;
}

.form-label p {
  margin: 0;
  color: var(--orin-muted);
  font-size: 11px;
  line-height: 1.5;
}

.form-control {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  gap: 8px;
}

.power-mode-control :deep(.el-radio-group) {
  display: flex;
  flex-wrap: nowrap;
}

.unit {
  color: var(--orin-muted);
  font-size: 12px;
}

.form-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 14px;
}

.bandwidth-card {
  margin-top: 14px;
  padding: 14px;
  background: var(--orin-surface-soft);
  border: 1px solid var(--orin-border);
  border-radius: 6px;
}

.bandwidth-title {
  display: flex;
  align-items: center;
  gap: 7px;
  margin-bottom: 10px;
  color: var(--orin-text-soft);
  font-size: 13px;
  font-weight: 700;
}

.bandwidth-title :deep(.el-icon) {
  color: var(--orin-green);
}

.bandwidth-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
}

.bandwidth-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
  padding: 9px 10px;
  background: var(--orin-surface-raised);
  border: 1px solid var(--orin-border-soft);
  border-radius: 4px;
}

.bandwidth-item.highlight {
  background: var(--orin-green-soft);
  border-color: var(--orin-green-dark);
}

.bandwidth-item.highlight .bw-label {
  color: var(--orin-muted);
}

.bandwidth-item.highlight .bw-value {
  color: var(--orin-green-bright);
}

.bw-label {
  color: var(--orin-muted);
  font-size: 10px;
}

.bw-value {
  overflow-wrap: anywhere;
  color: var(--orin-text);
  font-size: 15px;
  font-weight: 700;
}

.config-snapshot {
  margin-top: 14px;
  padding: 14px;
  background: var(--orin-surface-soft);
  border: 1px solid var(--orin-border);
  border-radius: 6px;
}

.snapshot-title {
  margin-bottom: 10px;
  color: var(--orin-text-soft);
  font-size: 13px;
  font-weight: 700;
}

.snapshot-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 7px;
}

.snapshot-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  min-width: 0;
  padding: 8px 10px;
  background: var(--orin-surface-raised);
  border: 1px solid var(--orin-border-soft);
  border-radius: 4px;
}

.snap-label {
  color: var(--orin-muted);
  font-size: 11px;
}

.snap-value {
  color: var(--orin-green-bright);
  font-size: 12px;
  font-weight: 700;
  text-align: right;
}

@media (max-width: 900px) {
  .settings-grid {
    grid-template-columns: 1fr;
  }

  .bandwidth-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 520px) {
  .settings-hero,
  .panel {
    padding: 12px;
  }

  .settings-hero h1 {
    font-size: 19px;
  }

  .panel-head {
    align-items: flex-start;
    flex-wrap: wrap;
  }

  .form-item {
    align-items: stretch;
    flex-direction: column;
    min-height: 0;
    padding: 10px;
  }

  .form-control {
    width: 100%;
  }

  .form-control :deep(.el-input-number) {
    flex: 1;
    width: 100%;
  }

  .power-mode-control :deep(.el-radio-group) {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    width: 100%;
  }

  .power-mode-control :deep(.el-radio-button__inner) {
    width: 100%;
    padding-right: 6px;
    padding-left: 6px;
  }

  .form-actions :deep(.el-button) {
    flex: 1;
    margin-left: 0;
  }

  .bandwidth-card,
  .config-snapshot {
    padding: 10px;
  }

  .snapshot-grid {
    grid-template-columns: 1fr;
  }
}
</style>
