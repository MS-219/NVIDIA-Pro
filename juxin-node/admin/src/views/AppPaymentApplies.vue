<template>
  <div class="page app-payment-page">
    <div class="page-header">
      <div><h2>APP 收款方式</h2><p>APP 用户提交的提现渠道（银行卡或微信 / 支付宝收款码），审核后用于收益提现。</p></div>
    </div>

    <div class="panel">
      <div class="toolbar">
        <el-select v-model="statusFilter" placeholder="状态筛选" clearable style="width: 180px" @change="load">
          <el-option label="全部" value="" />
          <el-option label="待审核" value="pending" />
          <el-option label="已通过" value="approved" />
          <el-option label="已驳回" value="rejected" />
        </el-select>
        <el-button @click="load" :loading="loading">刷新</el-button>
      </div>

      <el-table :data="rows" v-loading="loading" stripe border>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column label="用户" width="180">
          <template #default="{ row }">
            <div class="user-cell">
              <div class="user-name">{{ row.nickname || '未知用户' }}</div>
              <div class="user-sub">{{ row.phone || `ID: ${row.user_id}` }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="方式" width="100">
          <template #default="{ row }">
            <el-tag :type="methodTag(row.method)" effect="dark">{{ methodLabel(row.method) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="收款账户" min-width="220">
          <template #default="{ row }">
            <div v-if="row.method === 'bank_card'" class="account-cell">
              <div class="account-name">{{ row.account_name || '-' }}</div>
              <code class="account-no">{{ row.account_no || '-' }}</code>
            </div>
            <el-image
              v-else-if="row.qr_code_url"
              :src="row.qr_code_url"
              :preview-src-list="[row.qr_code_url]"
              fit="cover"
              style="width: 72px; height: 72px; border-radius: 8px; border: 1px solid #e5e7eb"
              preview-teleported
            >
              <template #error><span class="img-missing">收款码</span></template>
            </el-image>
            <span v-else class="muted">未上传</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)" effect="dark">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="审核备注" min-width="140">
          <template #default="{ row }">{{ row.review_note || '-' }}</template>
        </el-table-column>
        <el-table-column label="申请时间" width="170">
          <template #default="{ row }">{{ fmt(row.created_at) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <template v-if="row.status === 'pending'">
              <el-button size="small" type="success" @click="review(row, 'approve')">通过</el-button>
              <el-button size="small" type="danger" @click="review(row, 'reject')">驳回</el-button>
            </template>
            <span v-else class="muted">已处理</span>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../utils/request'

const rows = ref([])
const loading = ref(false)
const statusFilter = ref('')

const load = async () => {
  loading.value = true
  try {
    const res = await request.get('/api/admin/app/payment-applies', { params: { status: statusFilter.value } })
    rows.value = res.data.data || []
  } finally {
    loading.value = false
  }
}

const review = async (row, action) => {
  const isApprove = action === 'approve'
  let note = ''
  if (!isApprove) {
    try {
      const { value } = await ElMessageBox.prompt('请输入驳回原因', '驳回申请', { inputPlaceholder: '选填', confirmButtonText: '确定驳回', cancelButtonText: '取消' })
      note = (value || '').trim()
    } catch { return }
  }
  try {
    await request.post(`/api/admin/app/payment-applies/${row.id}/${action}`, { note })
    ElMessage.success(isApprove ? '已通过' : '已驳回')
    await load()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '操作失败')
  }
}

const methodLabel = (m) => ({ bank_card: '银行卡', wechat: '微信', alipay: '支付宝' }[m] || m)
const methodTag = (m) => ({ bank_card: 'warning', wechat: 'success', alipay: 'primary' }[m] || 'info')
const statusLabel = (s) => ({ pending: '待审核', approved: '已通过', rejected: '已驳回' }[s] || s)
const statusTag = (s) => ({ pending: 'warning', approved: 'success', rejected: 'danger' }[s] || 'info')
const fmt = (v) => (v ? String(v).replace('T', ' ') : '-')

onMounted(load)
</script>

<style scoped>
.app-payment-page { padding: 0; }
.toolbar { display: flex; gap: 12px; margin-bottom: 16px; }
.user-cell { display: flex; flex-direction: column; }
.user-name { font-weight: 600; color: #303133; }
.user-sub { font-size: 12px; color: #909399; }
.account-cell { display: flex; flex-direction: column; gap: 3px; }
.account-name { font-size: 13px; color: #303133; }
.account-no { color: #b56700; font-weight: 600; }
.muted { color: #909399; font-size: 12px; }
.img-missing { display: flex; width: 100%; height: 100%; align-items: center; justify-content: center; font-size: 11px; color: #909399; background: #f5f7fa; }
</style>
