<template>
  <div class="payment-apply-list">
    <el-card class="filter-card" style="margin-bottom: 20px;">
      <el-row :gutter="16" align="middle">
        <el-col :span="6">
          <el-select v-model="statusFilter" placeholder="状态筛选" clearable @change="handleSearch">
            <el-option label="全部" value="" />
            <el-option label="待审核" :value="0" />
            <el-option label="已通过" :value="1" />
            <el-option label="已驳回" :value="2" />
          </el-select>
        </el-col>
        <el-col :span="4">
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-col>
        <el-col :span="14" style="text-align: right;">
          <el-button @click="showSensitive = !showSensitive">{{ showSensitive ? '隐藏敏感账号' : '查看敏感账号' }}</el-button>
          <el-button type="success" @click="fetchData" :loading="loading">刷新</el-button>
        </el-col>
      </el-row>
    </el-card>

    <el-card>
      <el-table :data="applyList" v-loading="loading" stripe border>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column label="用户" width="160">
          <template #default="{ row }">
            <div class="user-info-cell">
              <div class="user-detail">
                <div class="user-name">{{ row.nickname || '未知用户' }}</div>
                <div class="user-id">ID: {{ row.userId }}</div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="100">
          <template #default="{ row }">
            <el-tag :type="row.paymentType === 0 ? 'warning' : 'primary'">
              {{ row.paymentType === 0 ? '银行卡' : '支付宝' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="旧账号 (原账号)" min-width="160">
          <template #default="{ row }"><code>{{ showSensitive ? (row.oldCardNo || '-') : maskAccount(row.oldCardNo) }}</code></template>
        </el-table-column>
        <el-table-column label="新账号 (申请变更)" min-width="160">
          <template #default="{ row }">
            <code style="color: #b56700; font-weight: bold;">{{ showSensitive ? (row.newCardNo || '-') : maskAccount(row.newCardNo) }}</code>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusTag(row.status)" effect="dark">
              {{ getStatusName(row.status) }}
            </el-tag>
            <el-tooltip v-if="row.status === 2 && row.rejectReason" :content="row.rejectReason" placement="top">
              <div style="margin-top: 4px; font-size: 11px; color: #f56c6c; cursor: pointer;">
                查看原因
              </div>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="申请时间" width="170" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <template v-if="row.status === 0">
              <el-button size="small" type="success" @click="handleApprove(row)">通过</el-button>
              <el-button size="small" type="danger" @click="handleReject(row)">驳回</el-button>
            </template>
            <template v-else>
              <el-button size="small" disabled>已处理</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrapper" style="margin-top: 20px; text-align: right;">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="fetchData"
          @current-change="fetchData"
        />
      </div>
    </el-card>

    <el-dialog v-model="rejectVisible" title="驳回申请" width="400px">
      <el-input v-model="rejectRemark" type="textarea" :rows="3" placeholder="请输入驳回原因（必填）" />
      <template #footer>
        <el-button @click="rejectVisible = false">取消</el-button>
        <el-button type="danger" @click="confirmReject" :loading="submitting">确认驳回</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import axios from '../utils/request'
import { ElMessage, ElMessageBox } from 'element-plus'

const applyList = ref([])
const loading = ref(false)
const submitting = ref(false)
const statusFilter = ref(0) // Default to showing pending applies
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const showSensitive = ref(false)

const rejectVisible = ref(false)
const rejectRemark = ref('')
const currentApply = ref(null)

const fetchData = async () => {
  loading.value = true
  try {
    const params = {
      page: currentPage.value,
      size: pageSize.value,
      status: statusFilter.value !== '' ? statusFilter.value : undefined
    }
    const res = await axios.get('/api/admin/payment-apply/list', { params })
    if (res.data.code === 200) {
      applyList.value = res.data.data.records || []
      total.value = res.data.data.total || 0
    }
  } catch (e) {
    console.error(e)
    ElMessage.error('获取列表失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  currentPage.value = 1
  fetchData()
}

const resetSearch = () => {
  statusFilter.value = 0
  currentPage.value = 1
  fetchData()
}

const getStatusName = (status) => {
  const names = { 0: '待审核', 1: '已通过', 2: '已驳回' }
  return names[status] || '未知'
}

const getStatusTag = (status) => {
  const tags = { 0: 'warning', 1: 'success', 2: 'danger' }
  return tags[status] || 'info'
}

const maskAccount = (value) => {
  if (!value) return '-'
  const text = String(value)
  return text.length > 8 ? `${text.slice(0, 4)} **** ${text.slice(-4)}` : '****'
}

const handleApprove = async (row) => {
  try {
    await ElMessageBox.confirm('确认审核通过？通过后将覆盖用户之前的收款账号。', '审核通过', {
      type: 'warning'
    })

    submitting.value = true
    const res = await axios.post(`/api/admin/payment-apply/approve/${row.id}`)
    if (res.data.code === 200) {
      ElMessage.success('审核通过成功')
      fetchData()
    } else {
      ElMessage.error(res.data.msg || '操作失败')
    }
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('操作失败')
  } finally {
    submitting.value = false
  }
}

const handleReject = (row) => {
  currentApply.value = row
  rejectRemark.value = ''
  rejectVisible.value = true
}

const confirmReject = async () => {
  if (!rejectRemark.value.trim()) {
    ElMessage.warning('请填写驳回原因')
    return
  }

  submitting.value = true
  try {
    const res = await axios.post(`/api/admin/payment-apply/reject/${currentApply.value.id}`, {
      reason: rejectRemark.value
    })
    if (res.data.code === 200) {
      ElMessage.success('已驳回')
      rejectVisible.value = false
      fetchData()
    } else {
      ElMessage.error(res.data.msg || '操作失败')
    }
  } catch (e) {
    ElMessage.error('操作失败')
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  fetchData()
})
</script>

<style scoped>
.payment-apply-list {
  padding: 0;
}
.user-info-cell {
  display: flex;
  align-items: center;
}
.user-detail {
  display: flex;
  flex-direction: column;
}
.user-name {
  font-weight: 500;
  color: #303133;
}
.user-id {
  font-size: 12px;
  color: #909399;
}
</style>
