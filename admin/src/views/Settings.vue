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
          <h3>📡 设备通信配置</h3>
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
          <div class="bandwidth-title">📊 带宽估算（基于当前在线设备数）</div>
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
          <h3>⚙️ 设备管理配置</h3>
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

const saving = ref(false)
const onlineCount = ref(0)

const deviceConfig = reactive({
  heartbeatInterval: 60,
  taskPollInterval: 60,
  offlineThreshold: 180,
  heartbeatTimeout: 120,
  autoAssignBusiness: true,
  initialHashrate: 100,
})

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
.page-container { padding: 20px; display: flex; flex-direction: column; gap: 20px; }

.settings-hero {
  background: linear-gradient(135deg, #0c1a30, #15305a 58%, #1a4080);
  color: #eef6ff;
  border-radius: 18px;
  padding: 26px 30px;
}
.settings-hero h1 { margin: 0 0 10px; font-size: 26px; }
.settings-hero p { margin: 0; color: rgba(238, 246, 255, 0.75); line-height: 1.7; max-width: 800px; }

.settings-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 20px; }

.panel {
  background: #fff;
  border-radius: 16px;
  padding: 24px;
  box-shadow: 0 10px 28px rgba(17, 24, 39, 0.06);
}
.panel-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.panel-head h3 { margin: 0; color: #16263a; font-size: 18px; }

.form-section { display: flex; flex-direction: column; gap: 20px; }

.form-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 18px;
  background: #f8fbff;
  border: 1px solid #e8eef5;
  border-radius: 12px;
  gap: 16px;
}
.form-label { flex: 1; }
.form-label strong { display: block; color: #1d2e43; margin-bottom: 4px; font-size: 14px; }
.form-label p { margin: 0; color: #8899aa; font-size: 12px; line-height: 1.5; }
.form-control { display: flex; align-items: center; gap: 8px; flex-shrink: 0; }
.unit { color: #8899aa; font-size: 13px; }

.form-actions { margin-top: 20px; display: flex; gap: 12px; }

.bandwidth-card {
  margin-top: 20px;
  padding: 16px 18px;
  background: linear-gradient(135deg, #f0f7ff, #e8f4fd);
  border: 1px solid #d0e6f6;
  border-radius: 12px;
}
.bandwidth-title { font-size: 14px; font-weight: 600; color: #1d4a7a; margin-bottom: 12px; }
.bandwidth-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; }
.bandwidth-item {
  display: flex; flex-direction: column; gap: 4px;
  padding: 10px; background: rgba(255,255,255,0.8); border-radius: 8px;
}
.bandwidth-item.highlight { background: #1890ff; color: white; }
.bandwidth-item.highlight .bw-label { color: rgba(255,255,255,0.8); }
.bandwidth-item.highlight .bw-value { color: white; }
.bw-label { font-size: 11px; color: #6d8aa0; }
.bw-value { font-size: 16px; font-weight: 700; color: #1d3a5c; }

.config-snapshot {
  margin-top: 20px;
  padding: 16px 18px;
  background: #f9fafb;
  border: 1px solid #ebeef5;
  border-radius: 12px;
}
.snapshot-title { font-size: 14px; font-weight: 600; color: #4a5568; margin-bottom: 12px; }
.snapshot-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 8px; }
.snapshot-item {
  display: flex; justify-content: space-between; align-items: center;
  padding: 8px 12px; background: white; border-radius: 8px; border: 1px solid #edf2f7;
}
.snap-label { font-size: 12px; color: #718096; }
.snap-value { font-size: 13px; font-weight: 600; color: #2d3748; }

@media (max-width: 1100px) { .settings-grid { grid-template-columns: 1fr; } }
</style>
