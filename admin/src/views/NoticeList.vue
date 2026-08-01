<template>
  <div class="notice-list-page">
    <!-- 顶部操作栏 -->
    <el-card class="search-card">
      <el-row :gutter="16" align="middle">
        <el-col :span="6">
          <el-input
            v-model="searchKeyword"
            placeholder="搜索公告标题"
            prefix-icon="Search"
            clearable
            @clear="handleSearch"
            @keyup.enter="handleSearch"
          />
        </el-col>
        <el-col :span="4">
          <el-select v-model="statusFilter" placeholder="状态筛选" clearable @change="handleSearch">
            <el-option label="全部" value="" />
            <el-option label="已发布" :value="1" />
            <el-option label="草稿" :value="0" />
          </el-select>
        </el-col>
        <el-col :span="4">
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-col>
        <el-col :span="10" style="text-align: right;">
          <el-button type="success" @click="openAddDialog">
            <el-icon><Plus /></el-icon> 新增公告
          </el-button>
        </el-col>
      </el-row>
    </el-card>

    <!-- 公告列表 -->
    <el-card class="table-card">
      <el-table :data="noticeList" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="title" label="标题" min-width="200">
          <template #default="{ row }">
            <div class="title-cell">
              <el-tag v-if="row.type === 1" size="small" type="info">系统</el-tag>
              <el-tag v-else-if="row.type === 2" size="small" type="warning">活动</el-tag>
              <el-tag v-else-if="row.type === 3" size="small" type="danger">维护</el-tag>
              <span class="title-text">{{ row.title }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" effect="dark" round>
              {{ row.status === 1 ? '已发布' : '草稿' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sort" label="排序" width="80" />
        <el-table-column prop="publishTime" label="发布时间" width="170" />
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="viewNotice(row)">查看</el-button>
            <el-button size="small" type="primary" @click="editNotice(row)">编辑</el-button>
            <el-button
              size="small"
              :type="row.status === 1 ? 'warning' : 'success'"
              @click="togglePublish(row)"
            >
              {{ row.status === 1 ? '下架' : '发布' }}
            </el-button>
            <el-button size="small" type="danger" @click="deleteNotice(row)">删除</el-button>
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
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="fetchNotices"
          @current-change="fetchNotices"
        />
      </div>
    </el-card>

    <!-- 查看详情弹窗 -->
    <el-dialog v-model="detailVisible" title="公告详情" width="600px">
      <div v-if="currentNotice" class="notice-detail">
        <h2>{{ currentNotice.title }}</h2>
        <div class="meta">
          <el-tag v-if="currentNotice.type === 1" size="small" type="info">系统通知</el-tag>
          <el-tag v-else-if="currentNotice.type === 2" size="small" type="warning">活动公告</el-tag>
          <el-tag v-else-if="currentNotice.type === 3" size="small" type="danger">维护公告</el-tag>
          <span class="time">{{ currentNotice.publishTime || currentNotice.createTime }}</span>
        </div>
        <div class="content">{{ currentNotice.content }}</div>
      </div>
    </el-dialog>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="editVisible" :title="editForm.id ? '编辑公告' : '新增公告'" width="700px">
      <el-form :model="editForm" :rules="rules" ref="formRef" label-width="80px">
        <el-form-item label="标题" prop="title">
          <el-input v-model="editForm.title" placeholder="请输入公告标题" />
        </el-form-item>
        <el-form-item label="类型" prop="type">
          <el-radio-group v-model="editForm.type">
            <el-radio :value="1">系统通知</el-radio>
            <el-radio :value="2">活动公告</el-radio>
            <el-radio :value="3">维护公告</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="editForm.sort" :min="0" :max="999" />
          <span class="form-tip">数值越大越靠前</span>
        </el-form-item>
        <el-form-item label="封面图">
          <el-upload
            class="avatar-uploader"
            :show-file-list="false"
            accept=".jpg,.jpeg,.png,.gif,.webp,.bmp"
            :http-request="uploadNoticeImage"
            :before-upload="beforeAvatarUpload"
            :disabled="imageUploading"
            name="file"
          >
            <img v-if="editForm.imageUrl" :src="editForm.imageUrl" class="avatar" />
            <div v-else-if="imageUploading" class="avatar-uploading">
              <el-icon class="is-loading"><Loading /></el-icon>
              <span>上传中</span>
            </div>
            <div v-else class="avatar-upload-empty">
              <el-icon><Plus /></el-icon>
              <span>上传封面</span>
            </div>
          </el-upload>
        </el-form-item>
        <el-form-item label="内容" prop="content">
          <el-input
            v-model="editForm.content"
            type="textarea"
            :rows="6"
            placeholder="请输入公告内容"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="info" @click="saveNotice(0)" :loading="saving">保存草稿</el-button>
        <el-button type="primary" @click="saveNotice(1)" :loading="saving">保存并发布</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { Loading, Plus } from '@element-plus/icons-vue'
import axios from '../utils/request'
import { ElMessage, ElMessageBox } from 'element-plus'

const noticeList = ref([])
const loading = ref(false)
const saving = ref(false)
const searchKeyword = ref('')
const statusFilter = ref('')
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

const detailVisible = ref(false)
const editVisible = ref(false)
const currentNotice = ref(null)
const formRef = ref(null)
const imageUploading = ref(false)

const editForm = reactive({
  id: null,
  title: '',
  content: '',
  type: 1,
  sort: 0,
  imageUrl: ''
})

const rules = {
  title: [{ required: true, message: '请输入公告标题', trigger: 'blur' }],
  content: [{ required: true, message: '请输入公告内容', trigger: 'blur' }],
  type: [{ required: true, message: '请选择公告类型', trigger: 'change' }]
}

const fetchNotices = async () => {
  loading.value = true
  try {
    const res = await axios.get('/api/notice/admin/list', {
      params: {
        page: currentPage.value,
        size: pageSize.value,
        keyword: searchKeyword.value || undefined,
        status: statusFilter.value !== '' ? statusFilter.value : undefined
      }
    })
    if (res.data.code === 200) {
      noticeList.value = res.data.data.records || []
      total.value = res.data.data.total || 0
    }
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  currentPage.value = 1
  fetchNotices()
}

const resetSearch = () => {
  searchKeyword.value = ''
  statusFilter.value = ''
  currentPage.value = 1
  fetchNotices()
}

const viewNotice = (row) => {
  currentNotice.value = row
  detailVisible.value = true
}

const openAddDialog = () => {
  editForm.id = null
  editForm.title = ''
  editForm.content = ''
  editForm.type = 1
  editForm.sort = 0
  editForm.imageUrl = ''
  editVisible.value = true
}

const editNotice = (row) => {
  editForm.id = row.id
  editForm.title = row.title
  editForm.content = row.content || ''
  editForm.type = row.type || 1
  editForm.sort = row.sort || 0
  editForm.imageUrl = row.imageUrl || ''
  editVisible.value = true
}

const saveNotice = async (publishStatus) => {
  if (!formRef.value) return

  await formRef.value.validate(async (valid) => {
    if (!valid) return

    saving.value = true
    try {
      const url = editForm.id ? '/api/notice/admin/update' : '/api/notice/admin/add'
      const data = {
        ...editForm,
        status: publishStatus
      }

      const res = await axios.post(url, data)
      if (res.data.code === 200) {
        ElMessage.success(res.data.data || '操作成功')
        editVisible.value = false
        fetchNotices()
      } else {
        ElMessage.error(res.data.msg || '操作失败')
      }
    } catch (e) {
      ElMessage.error('网络错误')
    } finally {
      saving.value = false
    }
  })
}

const togglePublish = async (row) => {
  const action = row.status === 1 ? 'unpublish' : 'publish'
  const actionText = row.status === 1 ? '下架' : '发布'

  try {
    const res = await axios.post(`/api/notice/admin/${action}/${row.id}`)
    if (res.data.code === 200) {
      ElMessage.success(`${actionText}成功`)
      fetchNotices()
    } else {
      ElMessage.error(res.data.msg || `${actionText}失败`)
    }
  } catch (e) {
    ElMessage.error('网络错误')
  }
}

const deleteNotice = async (row) => {
  try {
    await ElMessageBox.confirm('确定要删除该公告吗？删除后无法恢复。', '确认删除', {
      type: 'warning'
    })

    const res = await axios.post(`/api/notice/admin/delete/${row.id}`)
    if (res.data.code === 200) {
      ElMessage.success('删除成功')
      fetchNotices()
    } else {
      ElMessage.error(res.data.msg || '删除失败')
    }
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error('操作失败')
    }
  }
}

const uploadNoticeImage = async ({ file, onSuccess, onError }) => {
  imageUploading.value = true
  try {
    const formData = new FormData()
    formData.append('file', file)
    const res = await axios.post('/api/upload/image', formData, { silent: true })
    if (res.data.code !== 200 || !res.data.data?.url) {
      throw new Error(res.data.msg || '服务器未返回图片地址')
    }

    editForm.imageUrl = res.data.data.url
    onSuccess?.(res.data)
    ElMessage.success('封面上传成功')
  } catch (error) {
    const message = error?.response?.data?.msg || error?.message || '封面上传失败'
    ElMessage.error(message)
    onError?.(error)
  } finally {
    imageUploading.value = false
  }
}

const beforeAvatarUpload = (rawFile) => {
  const allowedTypes = ['image/jpeg', 'image/png', 'image/gif', 'image/webp', 'image/bmp']
  if (!allowedTypes.includes(rawFile.type)) {
    ElMessage.error('仅支持 JPG、PNG、GIF、WEBP、BMP 图片')
    return false
  }
  if (rawFile.size > 5 * 1024 * 1024) {
    ElMessage.error('图片大小不能超过 5MB')
    return false
  }
  return true
}

onMounted(() => {
  fetchNotices()
})
</script>

<style scoped>
.notice-list-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.search-card, .table-card {
  border-radius: 12px;
}

.title-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.title-text {
  font-weight: 500;
}

.pagination-wrapper {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

.notice-detail h2 {
  margin: 0 0 12px 0;
  font-size: 20px;
  color: #1a1a2e;
}

.notice-detail .meta {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 20px;
  padding-bottom: 16px;
  border-bottom: 1px solid #e5e7eb;
}

.notice-detail .time {
  color: #9ca3af;
  font-size: 14px;
}

.notice-detail .content {
  color: #374151;
  line-height: 1.8;
  white-space: pre-wrap;
}

.form-tip {
  margin-left: 12px;
  color: #9ca3af;
  font-size: 12px;
}

.avatar-uploader .avatar {
  width: 178px;
  height: 178px;
  display: block;
  object-fit: cover;
}

.avatar-uploader :deep(.el-upload) {
  border: 1px dashed var(--el-border-color);
  border-radius: 6px;
  cursor: pointer;
  position: relative;
  overflow: hidden;
  transition: var(--el-transition-duration-fast);
}

.avatar-uploader :deep(.el-upload:hover) {
  border-color: var(--el-color-primary);
}

.avatar-upload-empty,
.avatar-uploading {
  color: #8c939d;
  width: 178px;
  height: 178px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  font-size: 13px;
}

.avatar-upload-empty .el-icon,
.avatar-uploading .el-icon {
  font-size: 28px;
}
</style>
