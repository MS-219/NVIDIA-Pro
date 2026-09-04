<template>
  <div class="earnings-page">
    <!-- 收益统计卡片 -->
    <el-row :gutter="20" class="stat-row">
      <el-col :span="6">
        <div class="stat-card income-total">
          <div class="stat-icon-wrapper"><el-icon><Coin /></el-icon></div>
          <div class="stat-info">
            <div class="stat-value">¥{{ stats.totalEarnings || '0.00' }}</div>
            <div class="stat-label">累计收益</div>
          </div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card income-today">
          <div class="stat-icon-wrapper"><el-icon><Calendar /></el-icon></div>
          <div class="stat-info">
            <div class="stat-value">¥{{ stats.todayEarnings || '0.00' }}</div>
            <div class="stat-label">今日收益</div>
          </div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card income-month">
          <div class="stat-icon-wrapper"><el-icon><TrendCharts /></el-icon></div>
          <div class="stat-info">
            <div class="stat-value">¥{{ stats.monthEarnings || '0.00' }}</div>
            <div class="stat-label">本月收益</div>
          </div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card runtime">
          <div class="stat-icon-wrapper"><el-icon><Timer /></el-icon></div>
          <div class="stat-info">
            <div class="stat-value">{{ stats.totalSettlements || 0 }}次</div>
            <div class="stat-label">累计结算</div>
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- 选项卡与数据表格 -->
    <el-card class="data-card">
      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <!-- 收益明细 -->
        <el-tab-pane label="收益明细" name="records">
          <div class="tab-header">
            <el-date-picker
              v-model="dateRange"
              type="daterange"
              range-separator="至"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              size="default"
              value-format="YYYY-MM-DD"
              @change="fetchEarnings"
            />
            <el-button type="primary" @click="fetchEarnings">刷新明细</el-button>
          </div>

          <el-table :data="earningsList" v-loading="loading" stripe border>
            <el-table-column prop="id" label="ID" width="80" />
            <el-table-column prop="deviceSn" label="设备SN" width="180" />
            <el-table-column label="所属用户" width="180">
              <template #default="{ row }">
                <div class="user-info-cell">
                  <el-avatar :size="30" :src="row.avatarUrl || 'https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png'" />
                  <div class="user-detail">
                    <div class="user-name">{{ row.nickname || '用户' + row.userId }}</div>
                    <div class="user-id">ID: {{ row.userId }}</div>
                  </div>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="结算收益" width="120">
              <template #default="{ row }">
                <span class="amount-text">¥{{ row.amount }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="createTime" label="结算时间" width="180">
                <template #default="{ row }">
                    {{ row.createTime?.replace('T', ' ') || '-' }}
                </template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small" effect="plain">
                  {{ row.status === 1 ? '已入账' : '待处理' }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- 用户汇总 -->
        <el-tab-pane label="用户汇总" name="userSummary">
          <div class="tab-header">
            <el-input
              v-model="searchKeyword"
              placeholder="搜索用户ID/昵称"
              style="width: 260px"
              clearable
              @keyup.enter="fetchSummary"
            >
              <template #prefix><el-icon><Search /></el-icon></template>
            </el-input>
            <el-button type="primary" @click="fetchSummary">查询</el-button>
          </div>

          <el-table :data="summaryList" v-loading="loading" stripe border>
            <el-table-column label="用户" min-width="200">
              <template #default="{ row }">
                <div class="user-info-cell">
                  <el-avatar :size="32" :src="row.avatarUrl || 'https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png'" />
                  <div class="user-detail">
                    <div class="user-name">{{ row.nickname || '用户' + row.user_id }}</div>
                    <div class="user-id">ID: {{ row.user_id }}</div>
                  </div>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="totalAmount" label="累计收益" width="150" sortable>
              <template #default="{ row }">
                <span class="sum-amount">¥{{ Number(row.totalAmount || 0).toFixed(2) }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="recordCount" label="结算笔数" width="120" align="center" />
            <el-table-column label="最后结算" width="180">
              <template #default="{ row }">
                {{ row.lastTime?.replace('T', ' ') || '-' }}
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- 设备汇总 -->
        <el-tab-pane label="设备汇总" name="deviceSummary">
          <div class="tab-header">
            <el-input
              v-model="searchKeyword"
              placeholder="搜索设备SN/绑定码"
              style="width: 260px"
              clearable
              @keyup.enter="fetchSummary"
            >
              <template #prefix><el-icon><Cpu /></el-icon></template>
            </el-input>
            <el-button type="primary" @click="fetchSummary">查询</el-button>
          </div>

          <el-table :data="summaryList" v-loading="loading" stripe border>
            <el-table-column prop="sn" label="设备SN" width="200" />
            <el-table-column prop="bindCode" label="绑定位" width="120" />
            <el-table-column prop="totalAmount" label="累计产出" width="150" sortable>
              <template #default="{ row }">
                <span class="sum-amount">¥{{ Number(row.totalAmount || 0).toFixed(2) }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="recordCount" label="结算笔数" width="130" align="center" />
            <el-table-column label="最后产出时间" min-width="180">
              <template #default="{ row }">
                {{ row.lastTime?.replace('T', ' ') || '-' }}
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- 补偿收益 -->
        <el-tab-pane label="补偿收益" name="compensate">
          <div class="compensate-panel">
            <div class="compensate-header">
              <div class="compensate-icon"><el-icon><Tools /></el-icon></div>
              <div class="compensate-title">
                <h3>收益补偿工具</h3>
                <p>用于服务器宕机等异常情况，为所有已绑定且在线的设备批量补发收益</p>
              </div>
            </div>

            <el-alert
              title="注意：补偿操作会为所有已绑定且在线的设备补发收益、更新余额、增加算力值，并触发邀请人分润，请谨慎操作！"
              type="warning"
              :closable="false"
              show-icon
              class="compensate-alert"
            />

            <div class="compensate-form">
              <div class="form-item">
                <label class="form-label">补偿天数</label>
                <div class="form-control">
                  <el-input-number
                    v-model="compensateDays"
                    :min="1"
                    :max="30"
                    :step="1"
                    size="large"
                    controls-position="right"
                    style="width: 200px"
                  />
                  <span class="form-hint">天（1-30天）</span>
                </div>
              </div>

              <div class="form-item">
                <label class="form-label">操作说明</label>
                <div class="form-desc">
                  <p><el-icon><CircleCheck /></el-icon> 将为 <strong>所有已绑定且在线的设备</strong> 补发 <strong>{{ compensateDays }}</strong> 天的收益</p>
                  <p><el-icon><CircleCheck /></el-icon> 每台设备每天收益 = 基础收益 × 用户等级费率</p>
                  <p><el-icon><CircleCheck /></el-icon> 同时会增加对应的算力值（每天 +100）</p>
                  <p><el-icon><CircleCheck /></el-icon> 邀请人的级差分润也会同步计算</p>
                </div>
              </div>

              <div class="form-actions">
                <el-button
                  type="danger"
                  size="large"
                  :loading="compensateLoading"
                  :icon="Lightning"
                  @click="handleCompensate"
                  class="compensate-btn"
                >
                  {{ compensateLoading ? '正在补偿中...' : '执行补偿' }}
                </el-button>
              </div>
            </div>

            <!-- 补偿结果 -->
            <div v-if="compensateResult" class="compensate-result">
              <div class="result-header">
                <el-icon class="result-icon"><CircleCheckFilled /></el-icon>
                <span class="result-title">补偿完成</span>
              </div>
              <el-row :gutter="16" class="result-stats">
                <el-col :span="6">
                  <div class="result-stat-item">
                    <div class="result-stat-value">{{ compensateResult.totalDevices }}</div>
                    <div class="result-stat-label">总设备数</div>
                  </div>
                </el-col>
                <el-col :span="6">
                  <div class="result-stat-item success">
                    <div class="result-stat-value">{{ compensateResult.successCount }}</div>
                    <div class="result-stat-label">成功</div>
                  </div>
                </el-col>
                <el-col :span="6">
                  <div class="result-stat-item danger" v-if="compensateResult.failCount > 0">
                    <div class="result-stat-value">{{ compensateResult.failCount }}</div>
                    <div class="result-stat-label">失败</div>
                  </div>
                  <div class="result-stat-item success" v-else>
                    <div class="result-stat-value">0</div>
                    <div class="result-stat-label">失败</div>
                  </div>
                </el-col>
                <el-col :span="6">
                  <div class="result-stat-item highlight">
                    <div class="result-stat-value">¥{{ compensateResult.totalAmount }}</div>
                    <div class="result-stat-label">补偿总金额</div>
                  </div>
                </el-col>
              </el-row>
              <div class="result-detail">
                <span>补偿天数: {{ compensateResult.days }} 天</span>
                <span>总记录数: {{ compensateResult.totalRecords }} 条</span>
              </div>
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>

      <!-- 公用分页 -->
      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handlePageChange"
        />
      </div>
    </el-card>

  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import axios from '../utils/request'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Calendar,
  CircleCheck,
  CircleCheckFilled,
  Coin,
  Cpu,
  Lightning,
  Search,
  Timer,
  Tools,
  TrendCharts
} from '@element-plus/icons-vue'

const loading = ref(false)
const earningsList = ref([])
const summaryList = ref([])
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const dateRange = ref([])
const stats = ref({})
const activeTab = ref('records')
const compensateDays = ref(1)
const compensateLoading = ref(false)
const compensateResult = ref(null)
const searchKeyword = ref('')

// 获取统计
const fetchStats = async () => {
  try {
    const res = await axios.get('/api/earnings/stats')
    if (res.data.code === 200) {
      stats.value = res.data.data
    }
  } catch (e) {
    console.error(e)
  }
}

// 获取收益明细
const fetchEarnings = async () => {
  loading.value = true
  try {
    const res = await axios.get('/api/earnings/admin/list', {
      params: {
        page: page.value,
        size: pageSize.value,
        startDate: dateRange.value?.[0],
        endDate: dateRange.value?.[1]
      }
    })
    if (res.data.code === 200) {
      earningsList.value = res.data.data.records || []
      total.value = res.data.data.total || 0
    }
  } catch (e) {
    console.error(e)
    ElMessage.error('获取收益列表失败')
  } finally {
    loading.value = false
  }
}

// 获取汇总数据 (用户或设备)
const fetchSummary = async () => {
  loading.value = true
  const apiMap = {
    userSummary: '/api/earnings/admin/user-summary',
    deviceSummary: '/api/earnings/admin/device-summary'
  }

  try {
    const res = await axios.get(apiMap[activeTab.value], {
      params: {
        page: page.value,
        size: pageSize.value,
        keyword: searchKeyword.value
      }
    })
    if (res.data.code === 200) {
      summaryList.value = res.data.data.records || []
      total.value = res.data.data.total || 0
    }
  } catch (e) {
    console.error(e)
    ElMessage.error('获取汇总数据失败')
  } finally {
    loading.value = false
  }
}

// 统一数据加载入口
const loadData = () => {
  if (activeTab.value === 'records') {
    fetchEarnings()
  } else if (activeTab.value === 'userSummary' || activeTab.value === 'deviceSummary') {
    fetchSummary()
  }
}

// 标签切换
const handleTabChange = () => {
  page.value = 1
  searchKeyword.value = ''
  loadData()
}

// 分页处理
const handlePageChange = (val) => {
  page.value = val
  loadData()
}

const handleSizeChange = (val) => {
  pageSize.value = val
  page.value = 1
  loadData()
}


// 执行补偿收益
const handleCompensate = async () => {
  try {
    await ElMessageBox.confirm(
      `确认要为所有在线设备补偿 ${compensateDays.value} 天的收益吗？\n\n此操作将为所有已绑定且在线的设备补发收益、更新余额和算力值。`,
      '确认补偿操作',
      {
        confirmButtonText: '确认补偿',
        cancelButtonText: '取消',
        type: 'warning',
        dangerouslyUseHTMLString: false
      }
    )
  } catch {
    return // 用户取消
  }

  compensateLoading.value = true
  compensateResult.value = null

  try {
    const res = await axios.post('/api/earnings/admin/compensate', {
      days: compensateDays.value
    })
    if (res.data.code === 200) {
      compensateResult.value = res.data.data
      ElMessage.success(`补偿完成！成功 ${res.data.data.successCount} 台设备`)
      // 刷新统计数据
      fetchStats()
    } else {
      ElMessage.error(res.data.msg || '补偿失败')
    }
  } catch (e) {
    console.error(e)
    ElMessage.error('补偿请求失败，请检查网络')
  } finally {
    compensateLoading.value = false
  }
}

onMounted(() => {
  fetchStats()
  loadData()
})
</script>

<style scoped>
.earnings-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* 收益统计卡片 */
.stat-row {
  margin-bottom: 8px;
}

.stat-card {
  border-radius: 16px;
  padding: 24px;
  display: flex;
  align-items: center;
  gap: 20px;
  transition: all 0.3s ease;
  border: none;
  color: #fff;
}

.stat-card:hover {
  transform: translateY(-4px);
}

.stat-card.income-total {
  background: linear-gradient(135deg, #6366f1 0%, #4f46e5 100%);
  box-shadow: 0 8px 20px rgba(99, 102, 241, 0.25);
}

.stat-card.income-today {
  background: linear-gradient(135deg, #10b981 0%, #059669 100%);
  box-shadow: 0 8px 20px rgba(16, 185, 129, 0.25);
}

.stat-card.income-month {
  background: linear-gradient(135deg, #f59e0b 0%, #d97706 100%);
  box-shadow: 0 8px 20px rgba(245, 158, 11, 0.25);
}

.stat-card.runtime {
  background: linear-gradient(135deg, #06b6d4 0%, #0891b2 100%);
  box-shadow: 0 8px 20px rgba(6, 182, 212, 0.25);
}

.stat-icon-wrapper {
  width: 60px;
  height: 60px;
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 30px;
  background: rgba(255, 255, 255, 0.2);
  backdrop-filter: blur(4px);
}

.stat-value {
  font-size: 28px;
  font-weight: 800;
  color: #fff;
  letter-spacing: 0;
}

.stat-label {
  font-size: 14px;
  color: rgba(255, 255, 255, 0.85);
  margin-top: 4px;
  font-weight: 500;
}

/* 数据卡片 */
.data-card {
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1);
  border: none;
}

.tab-header {
  margin-bottom: 20px;
  display: flex;
  gap: 12px;
  padding: 0 4px;
}

.amount-text {
  color: #10b981;
  font-weight: 700;
  font-size: 15px;
}

.sum-amount {
  color: #4f46e5;
  font-weight: 700;
  font-size: 15px;
}

.user-info-cell {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 4px 8px;
  background: linear-gradient(135deg, #faf5ff 0%, #f3e8ff 100%);
  border-radius: 8px;
  border: 1px solid #e9d5ff;
}

.user-detail {
  display: flex;
  flex-direction: column;
  line-height: 1.3;
}

.user-name {
  font-weight: 600;
  color: #1e293b;
  font-size: 13px;
}

.user-id {
  font-size: 11px;
  color: #7c3aed;
  font-weight: 500;
}

.pagination-wrapper {
  margin-top: 20px;
  padding: 16px;
  display: flex;
  justify-content: flex-end;
  background: #f8fafc;
  border-top: 1px solid #e2e8f0;
}

/* 标签页与表格头部统一风格 */
:deep(.el-tabs__header) {
  margin-bottom: 20px;
}

:deep(.el-tabs__nav-wrap) {
  padding: 0 20px;
  background: #fff;
}

:deep(.el-tabs__item) {
  height: 56px;
  font-size: 15px;
  font-weight: 500;
}

:deep(.el-tabs__item.is-active) {
  font-weight: 700;
}

:deep(.el-table__header-wrapper th) {
  background: linear-gradient(135deg, #f5f3ff 0%, #ede9fe 100%) !important;
  color: #4c1d95 !important;
  font-weight: 600;
  font-size: 13px;
  border-bottom: 2px solid #a78bfa !important;
}

:deep(.el-table__row) {
  transition: all 0.2s ease;
}

:deep(.el-table__row:hover) {
  background: linear-gradient(135deg, #faf5ff 0%, #f5f3ff 100%) !important;
}

:deep(.el-tag--success) {
  background: linear-gradient(135deg, #d1fae5 0%, #a7f3d0 100%);
  border-color: #6ee7b7;
  color: #047857;
  font-weight: 600;
}

:deep(.el-tag--info) {
  background: linear-gradient(135deg, #f1f5f9 0%, #e2e8f0 100%);
  border-color: #cbd5e1;
  color: #64748b;
}

code {
  font-family: 'SF Mono', 'Monaco', monospace;
  font-size: 12px;
  color: #7c3aed;
  background: linear-gradient(135deg, #f5f3ff 0%, #ede9fe 100%);
  padding: 2px 6px;
  border-radius: 4px;
}


/* 底部缓冲 Padding */
.earnings-page {
  padding-bottom: 80px;
}

/* ========== 补偿收益面板 ========== */
.compensate-panel {
  padding: 10px 0;
}

.compensate-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 24px;
}

.compensate-icon {
  width: 56px;
  height: 56px;
  border-radius: 16px;
  background: linear-gradient(135deg, #f59e0b 0%, #d97706 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
  box-shadow: 0 8px 16px rgba(245, 158, 11, 0.25);
}

.compensate-title h3 {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  color: #1e293b;
}

.compensate-title p {
  margin: 4px 0 0;
  font-size: 14px;
  color: #64748b;
}

.compensate-alert {
  margin-bottom: 28px;
  border-radius: 12px;
}

.compensate-form {
  background: linear-gradient(135deg, #fefce8 0%, #fef9c3 100%);
  border: 1px solid #fde68a;
  border-radius: 16px;
  padding: 32px;
  margin-bottom: 24px;
}

.form-item {
  margin-bottom: 24px;
}

.form-label {
  display: block;
  font-size: 15px;
  font-weight: 700;
  color: #1e293b;
  margin-bottom: 10px;
}

.form-control {
  display: flex;
  align-items: center;
  gap: 14px;
}

.form-hint {
  font-size: 13px;
  color: #92400e;
  font-weight: 500;
}

.form-desc {
  background: rgba(255, 255, 255, 0.7);
  border-radius: 10px;
  padding: 16px 20px;
  border: 1px solid #fde68a;
}

.form-desc p {
  margin: 6px 0;
  font-size: 14px;
  color: #44403c;
  line-height: 1.6;
}

.form-actions {
  margin-top: 8px;
}

.compensate-btn {
  height: 48px;
  padding: 0 40px;
  font-size: 16px;
  font-weight: 700;
  border-radius: 12px;
  letter-spacing: 1px;
}

/* 补偿结果 */
.compensate-result {
  background: linear-gradient(135deg, #ecfdf5 0%, #d1fae5 100%);
  border: 1px solid #6ee7b7;
  border-radius: 16px;
  padding: 28px;
  animation: slideIn 0.4s ease;
}

@keyframes slideIn {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.result-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 20px;
}

.result-icon {
  font-size: 24px;
}

.result-title {
  font-size: 18px;
  font-weight: 700;
  color: #047857;
}

.result-stats {
  margin-bottom: 16px;
}

.result-stat-item {
  background: rgba(255, 255, 255, 0.8);
  border-radius: 12px;
  padding: 16px;
  text-align: center;
  border: 1px solid rgba(110, 231, 183, 0.2);
}

.result-stat-item.success {
  border-color: #10b981;
}

.result-stat-item.danger {
  border-color: #ef4444;
  background: rgba(254, 226, 226, 0.5);
}

.result-stat-item.highlight {
  border-color: #8b5cf6;
  background: rgba(237, 233, 254, 0.5);
}

.result-stat-value {
  font-size: 24px;
  font-weight: 800;
  color: #1e293b;
  margin-bottom: 4px;
}

.result-stat-item.success .result-stat-value {
  color: #047857;
}

.result-stat-item.danger .result-stat-value {
  color: #dc2626;
}

.result-stat-item.highlight .result-stat-value {
  color: #7c3aed;
}

.result-stat-label {
  font-size: 13px;
  color: #64748b;
  font-weight: 500;
}

.result-detail {
  display: flex;
  gap: 24px;
  font-size: 14px;
  color: #065f46;
  font-weight: 500;
  padding-top: 12px;
  border-top: 1px solid rgba(110, 231, 183, 0.3);
}
</style>
