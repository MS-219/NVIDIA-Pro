<template>
  <div class="page app-updates-page">
    <div class="page-header"><div><h2>APP 自动更新</h2><p>上传并发布 Android APK，APP 启动时会自动检查新版本。</p></div></div>
    <div class="panel upload-panel">
      <el-form label-position="top" :model="form">
        <el-form-item label="版本名称"><el-input v-model="form.version" placeholder="例如 1.0.1" /></el-form-item>
        <el-form-item label="版本号"><el-input-number v-model="form.versionCode" :min="1" /></el-form-item>
        <el-form-item label="更新说明"><el-input v-model="form.releaseNote" type="textarea" :rows="4" placeholder="填写本次更新内容" /></el-form-item>
        <el-form-item label="APK 文件"><el-upload drag :auto-upload="false" :limit="1" accept=".apk" :on-change="onFileChange" :on-remove="onFileRemove"><el-icon><UploadFilled /></el-icon><div class="el-upload__text">拖入 APK 或点击选择</div></el-upload></el-form-item>
        <el-checkbox v-model="form.forceUpdate">强制更新</el-checkbox>
        <div class="actions"><el-button type="primary" :loading="uploading" @click="upload">上传并发布</el-button><el-button @click="load">刷新发布记录</el-button></div>
      </el-form>
    </div>
    <div class="panel"><div class="panel-title">发布记录</div><el-table :data="releases" v-loading="loading"><el-table-column prop="version" label="版本" /><el-table-column prop="version_code" label="版本号" width="90" /><el-table-column prop="file_name" label="文件" /><el-table-column prop="file_size" label="大小" width="110"><template #default="{row}">{{ size(row.file_size) }}</template></el-table-column><el-table-column prop="status" label="状态" width="100" /><el-table-column label="操作" width="100"><template #default="{row}"><el-button v-if="row.status === 'active'" link type="danger" @click="disable(row.id)">下架</el-button></template></el-table-column></el-table></div>
  </div>
</template>
<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import request from '../utils/request'
const releases = ref([]), loading = ref(false), uploading = ref(false), file = ref(null)
const form = reactive({ version: '', versionCode: 1, releaseNote: '', forceUpdate: false })
const onFileChange = (item) => { file.value = item.raw }
const onFileRemove = () => { file.value = null }
const load = async () => { loading.value = true; try { const res = await request.get('/api/admin/app-updates'); releases.value = res.data.data || [] } finally { loading.value = false } }
const upload = async () => { if (!form.version.trim() || !file.value) return ElMessage.warning('请填写版本并选择 APK'); uploading.value = true; try { const body = new FormData(); body.append('file', file.value); body.append('version', form.version.trim()); body.append('versionCode', String(form.versionCode)); body.append('releaseNote', form.releaseNote); body.append('forceUpdate', String(form.forceUpdate)); await request.post('/api/admin/app-updates', body); ElMessage.success('APP 更新已发布'); form.version = ''; form.releaseNote = ''; file.value = null; await load() } finally { uploading.value = false } }
const disable = async (id) => { await ElMessageBox.confirm('确定下架此 APP 版本吗？'); await request.post(`/api/admin/app-updates/${id}/disable`); ElMessage.success('已下架'); await load() }
const size = (v) => v > 1024 * 1024 ? `${(v / 1024 / 1024).toFixed(1)} MB` : `${Math.round(v / 1024)} KB`
onMounted(load)
</script>
