<template>
  <div class="feedback-page">
    <!-- 搜索栏 -->
    <el-card class="search-card" shadow="never">
      <el-row :gutter="16" align="middle">
        <el-col :span="4">
          <el-select v-model="filterStatus" placeholder="处理状态" clearable @change="handleSearch">
            <el-option label="待处理" :value="0" />
            <el-option label="处理中" :value="1" />
            <el-option label="已处理" :value="2" />
          </el-select>
        </el-col>
        <el-col :span="6">
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="resetSearch">重置</el-button>
          <el-button type="success" @click="refreshData">刷新</el-button>
        </el-col>
      </el-row>
    </el-card>

    <!-- 反馈列表 -->
    <el-card class="table-card" shadow="never">
      <el-table :data="feedbackList" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column label="反馈用户" width="160">
          <template #default="{ row }">
            <div class="user-info-cell">
              <el-avatar :size="28" :src="row.avatarUrl || 'https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png'" />
              <div class="user-detail">
                <div class="user-name">{{ row.nickname || '未知用户' }}</div>
                <div class="user-id">ID: {{ row.userId }}</div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="120">
          <template #default="{ row }">
            <el-tag :type="getTypeTag(row.type)">{{ getTypeText(row.type) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="content" label="反馈内容" min-width="250" show-overflow-tooltip />
        <el-table-column label="联系方式" width="150">
          <template #default="{ row }">{{ maskContact(row.contact) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-badge :is-dot="row.status === 0" class="status-badge">
              <el-tag :type="getStatusTag(row.status)">{{ getStatusText(row.status) }}</el-tag>
            </el-badge>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="时间" width="170">
          <template #default="{ row }">
            {{ formatTime(row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" @click="handleDetail(row)">
              {{ row.status === 0 ? '处理' : '查看' }}
            </el-button>
            <el-button size="small" type="danger" plain @click="handleDelete(row)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @size-change="fetchFeedbacks"
          @current-change="fetchFeedbacks"
        />
      </div>
    </el-card>

    <!-- 详情/处理弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="initialStatus === 0 ? '处理反馈' : '反馈详情'"
      width="600px"
    >
      <el-form :model="detailForm" label-width="80px">
        <el-form-item label="反馈用户">
          <span>ID: {{ detailForm.userId }}</span>
        </el-form-item>
        <el-form-item label="类型">
          <el-tag :type="getTypeTag(detailForm.type)">{{ getTypeText(detailForm.type) }}</el-tag>
        </el-form-item>
        <el-form-item label="内容">
          <div class="detail-content">{{ detailForm.content }}</div>
        </el-form-item>
        <el-form-item label="附件图片" v-if="detailForm.images">
          <div class="image-list">
            <el-image
              v-for="(img, index) in detailForm.images.split(',')"
              :key="index"
              :src="img"
              :preview-src-list="detailForm.images.split(',')"
              fit="cover"
              class="feedback-img"
            />
          </div>
        </el-form-item>
        <el-form-item label="联系方式">
          <div class="contact-detail">
            <span>{{ contactVisible ? (detailForm.contact || '-') : maskContact(detailForm.contact) }}</span>
            <el-button
              v-if="detailForm.contact"
              type="primary"
              size="small"
              link
              @click="contactVisible = !contactVisible"
            >
              {{ contactVisible ? '隐藏' : '查看完整信息' }}
            </el-button>
          </div>
        </el-form-item>
        <el-divider v-if="initialStatus === 0">回复建议</el-divider>
        <el-form-item label="回复内容" v-if="initialStatus === 0 || detailForm.reply">
          <el-input
            v-model="detailForm.reply"
            type="textarea"
            :rows="3"
            placeholder="请输入给用户的回复或内部备注..."
            :disabled="initialStatus !== 0"
          />
        </el-form-item>
        <el-form-item label="处理结果" v-if="initialStatus === 0">
          <el-radio-group v-model="detailForm.status">
            <el-radio :value="1">处理中</el-radio>
            <el-radio :value="2">已完成</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="状态" v-else>
          <el-tag :type="getStatusTag(detailForm.status)">{{ getStatusText(detailForm.status) }}</el-tag>
        </el-form-item>
      </el-form>
      <template #footer v-if="initialStatus === 0">
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitProcess" :loading="saving">确认处理</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import axios from '../utils/request'
import { ElMessage, ElMessageBox } from 'element-plus'

const feedbackList = ref([])
const loading = ref(false)
const saving = ref(false)
const filterStatus = ref(null)
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

const dialogVisible = ref(false)
const contactVisible = ref(false)
const detailForm = reactive({
  id: null,
  userId: null,
  type: '',
  content: '',
  images: '',
  contact: '',
  status: 0,
  reply: ''
})
const initialStatus = ref(0)

const fetchFeedbacks = async () => {
  loading.value = true
  try {
    const res = await axios.get('/api/feedback/list', {
      params: {
        page: currentPage.value,
        size: pageSize.value,
        status: filterStatus.value !== null ? filterStatus.value : undefined
      }
    })
    if (res.data.code === 200) {
      feedbackList.value = res.data.data.records
      total.value = res.data.data.total
    }
  } catch (e) {
    ElMessage.error('获取列表失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  currentPage.value = 1
  fetchFeedbacks()
}

const resetSearch = () => {
  filterStatus.value = null
  currentPage.value = 1
  fetchFeedbacks()
}

const refreshData = () => {
  fetchFeedbacks()
  ElMessage.success('刷新成功')
}

const handleDetail = (row) => {
  Object.assign(detailForm, row)
  initialStatus.value = row.status
  contactVisible.value = false
  dialogVisible.value = true
}

const submitProcess = async () => {
  if (initialStatus.value === 0 && detailForm.status === 0) {
    ElMessage.warning('请选择处理结果')
    return
  }

  saving.value = true
  try {
    const res = await axios.post('/api/feedback/process', {
      id: detailForm.id,
      status: detailForm.status,
      reply: detailForm.reply
    })
    if (res.data.code === 200) {
      ElMessage.success('处理成功')
      dialogVisible.value = false
      fetchFeedbacks()
    } else {
      ElMessage.error(res.data.msg || '提交失败')
    }
  } catch (error) {
    ElMessage.error('网络错误')
  } finally {
    saving.value = false
  }
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(`确认删除反馈 #${row.id}？删除后不可恢复。`, '删除反馈', {
      type: 'warning',
      confirmButtonText: '确认删除',
      cancelButtonText: '取消'
    })

    const res = await axios.post(`/api/feedback/delete/${row.id}`)
    if (res.data.code === 200) {
      ElMessage.success('删除成功')
      fetchFeedbacks()
    } else {
      ElMessage.error(res.data.msg || '删除失败')
    }
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

const getTypeText = (type) => {
  const map = {
    'suggestion': '功能建议',
    'bug': '问题反馈',
    'complaint': '投诉建议',
    'other': '其他'
  }
  return map[type] || type
}

const getTypeTag = (type) => {
  const map = {
    'suggestion': 'success',
    'bug': 'danger',
    'complaint': 'warning',
    'other': 'info'
  }
  return map[type] || 'info'
}

const getStatusText = (status) => {
  const map = {
    0: '待处理',
    1: '处理中',
    2: '已处理'
  }
  return map[status] || '未知'
}

const getStatusTag = (status) => {
  const map = {
    0: 'info',
    1: 'warning',
    2: 'success'
  }
  return map[status] || 'info'
}

const formatTime = (time) => {
  if (!time) return '-'
  return String(time).replace('T', ' ').substring(0, 16)
}

const maskContact = (value) => {
  if (!value) return '-'
  const text = String(value)
  if (text.length >= 7) {
    return `${text.slice(0, 3)}****${text.slice(-4)}`
  }
  return `${text.slice(0, 1)}***`
}

onMounted(() => {
  fetchFeedbacks()
})
</script>

<style scoped>
.feedback-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.search-card, .table-card {
  border-radius: 12px;
}
.detail-content {
  background: #f8fafc;
  padding: 12px;
  border-radius: 8px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
}
.contact-detail {
  display: flex;
  align-items: center;
  gap: 8px;
}
.image-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.feedback-img {
  width: 100px;
  height: 100px;
  border-radius: 8px;
  cursor: pointer;
  border: 1px solid #eee;
}
.pagination-wrapper {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
.status-badge :deep(.el-badge__content.is-fixed.is-dot) {
  right: 5px;
  top: 5px;
}
code {
  font-size: 12px;
  color: #6366f1;
  background: rgba(99, 102, 241, 0.1);
  padding: 2px 6px;
  border-radius: 4px;
}

.user-info-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}
.user-detail {
  display: flex;
  flex-direction: column;
  line-height: 1.2;
}
.user-name {
  font-size: 13px;
  font-weight: 500;
  color: #333;
}
.user-id {
  font-size: 11px;
  color: #999;
}
</style>
