<template>
  <div class="task-page">
    <!-- 指令下发中心 -->
    <div class="command-hub">
      <div class="hub-header">
        <el-icon><VideoPlay /></el-icon> 算力切片分发指挥部 (Task Orchestrator)
      </div>
      <el-form :model="newTask" label-position="top">
        <el-row :gutter="20">
          <el-col :span="6">
            <el-form-item label="任务类型">
              <el-select v-model="newTask.taskType" placeholder="选择任务类型" style="width: 100%">
                <el-option label="AI 推理 (Ollama)" value="ollama" />
                <el-option label="网络爬虫 (Crawler)" value="spider" />
                <el-option label="代码注入 (Python)" value="python_script" />
              </el-select>
            </el-form-item>
          </el-col>
          
          <el-col :span="12">
            <!-- 根据类型切换输入框 -->
            <el-form-item v-if="newTask.taskType === 'ollama'" label="推理 Prompt">
              <el-input v-model="newTask.prompt" type="textarea" :rows="3" placeholder="输入 AI 提示词..." />
            </el-form-item>
            
            <el-form-item v-else-if="newTask.taskType === 'spider'" label="采集 URL">
              <el-input v-model="newTask.spiderUrl" placeholder="输入要采集的目标网址 (http://...)" />
            </el-form-item>
            
            <el-form-item v-else-if="newTask.taskType === 'python_script'" label="注入代码">
              <el-input v-model="newTask.taskParams" type="textarea" :rows="3" placeholder="输入要远程执行的 Python 代码..." />
            </el-form-item>
          </el-col>

          <el-col :span="6">
            <el-form-item label="核心引擎" v-if="newTask.taskType === 'ollama'">
              <el-select v-model="newTask.model" placeholder="选择推理引擎" style="width: 100%">
                <el-option label="Qwen 2.5 7B" value="qwen2.5:7b" />
                <el-option label="Llama 3.1 8B" value="llama3.1:8b" />
              </el-select>
            </el-form-item>
            <div style="margin-top: 32px">
              <el-button type="primary" style="width: 100%" @click="handleDispatch" :loading="loading">立即下发切片任务</el-button>
            </div>
          </el-col>
        </el-row>
      </el-form>
    </div>

    <!-- 简易统计 -->
    <el-row :gutter="20" style="margin-bottom: 24px">
      <el-col :span="6" v-for="s in miniStats" :key="s.label">
        <div class="mini-card">
          <div class="label">{{ s.label }}</div>
          <div class="val">{{ s.value }}</div>
        </div>
      </el-col>
    </el-row>

    <!-- 任务流水表格 -->
    <div class="table-container">
      <div class="table-header">
        <div class="title">实时推理流水线路</div>
        <el-button size="small" @click="fetchData">刷新数据</el-button>
      </div>
      
	      <el-table :data="taskList" border v-loading="loading">
        <el-table-column label="任务指纹" width="160">
          <template #default="{ row }">
            <code class="code-id">#{{ row.taskId ? row.taskId.substring(0,8) : 'NONE' }}</code>
          </template>
        </el-table-column>
        <el-table-column label="执行节点/SN" width="180">
          <template #default="{ row }">
            <b>{{ row.deviceSn }}</b>
          </template>
        </el-table-column>
	        <el-table-column prop="modelName" label="核心引擎" width="140" />
	        <el-table-column label="任务类型" width="140" align="center">
	          <template #default="{ row }">
	            <el-tag :type="taskTypeMeta(row.taskType).tagType" effect="plain">
	              {{ taskTypeMeta(row.taskType).label }}
	            </el-tag>
	          </template>
	        </el-table-column>
	        <el-table-column label="状态" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="产出 Token" width="120" prop="generateTokens" align="right" />
        <el-table-column label="耗时 (s)" width="100" align="right">
          <template #default="{ row }">
            {{ (row.durationMs / 1000).toFixed(1) }}
          </template>
        </el-table-column>
        <el-table-column label="奖励算力" width="120" align="right">
          <template #default="{ row }">
            <span style="color: #67c23a; font-weight: bold;">+{{ row.rewardHashrate }}⚡</span>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="执行时间" min-width="180">
          <template #default="{ row }">
            {{ formatTime(row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="viewDetails(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 任务详情弹窗 -->
      <el-dialog v-model="detailsVisible" title="任务执行指纹详情" width="60%">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="任务 ID">{{ currentTask.taskId }}</el-descriptions-item>
	          <el-descriptions-item label="执行设备">{{ currentTask.deviceSn }}</el-descriptions-item>
	          <el-descriptions-item label="核心引擎">{{ currentTask.modelName }}</el-descriptions-item>
	          <el-descriptions-item label="任务类型">
	            <el-tag :type="taskTypeMeta(currentTask.taskType).tagType" effect="plain">
	              {{ taskTypeMeta(currentTask.taskType).label }}
	            </el-tag>
	          </el-descriptions-item>
	          <el-descriptions-item label="执行状态">
	            <el-tag :type="statusType(currentTask.status)">{{ statusText(currentTask.status) }}</el-tag>
	          </el-descriptions-item>
        </el-descriptions>
        
        <div style="margin-top: 20px;">
          <h4 style="margin-bottom: 8px;">输入指令 (Prompt)</h4>
          <div class="code-box">{{ currentTask.prompt }}</div>
        </div>
        
        <div style="margin-top: 20px;">
          <h4 style="margin-bottom: 8px;">回传结果 (Response)</h4>
          <div class="code-box success">{{ currentTask.responseText || '暂无返回内容' }}</div>
        </div>

        <div v-if="currentTask.errorMsg" style="margin-top: 20px;">
          <h4 style="margin-bottom: 8px; color: #f56c6c;">异常信息 (Error)</h4>
          <div class="code-box error">{{ currentTask.errorMsg }}</div>
        </div>
      </el-dialog>

      <div class="pagination">
        <el-pagination
          background
          layout="total, prev, pager, next"
          :total="total"
          @current-change="(p) => { page = p; fetchData(); }"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, reactive } from 'vue'
import { VideoPlay } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import request from '../utils/request'

const loading = ref(false)
const taskList = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)

const newTask = reactive({ 
  prompt: '', 
  model: 'qwen2.5:7b',
  taskType: 'ollama',
  spiderUrl: '',
  taskParams: ''
})

const miniStats = ref([
  { label: '总计产出 Tokens', value: '0' },
  { label: '平均推理速度', value: '0 T/s' },
  { label: '峰值并发量', value: '0' },
  { label: '平均响应耗时', value: '0ms' }
])

const statusType = (s) => s === 'completed' ? 'success' : s === 'running' ? 'primary' : 'info'
const statusText = (s) => s === 'completed' ? '执行成功' : s === 'running' ? '正在执行' : '等待中'
const formatTime = (t) => t ? t.replace('T', ' ').substring(0, 19) : '-'
const taskTypeMeta = (type) => {
  const map = {
    ollama: { label: '模型推理', tagType: 'primary' },
    spider: { label: '数据采集', tagType: 'warning' },
    python_script: { label: '脚本执行', tagType: 'danger' }
  }
  return map[type] || { label: type || '未知任务', tagType: 'info' }
}

const fetchStats = async () => {
  try {
    const res = await request.get('/api/admin/device-tasks/statistics')
    if (res.data.code === 200) {
      const data = res.data.data
      miniStats.value[0].value = data.totalTokens >= 1000000 
        ? (data.totalTokens / 1000000).toFixed(2) + 'M' 
        : (data.totalTokens || 0).toLocaleString()
      miniStats.value[1].value = (data.avgInferenceRate || 0) + ' T/s'
      miniStats.value[2].value = data.peakConcurrency || 0
      miniStats.value[3].value = (data.avgLatency || 0) + 'ms'
    }
  } catch (e) {
    console.error('获取统计失败', e)
  }
}

const fetchData = async () => {
  loading.value = true
  try {
    const res = await request.get('/api/admin/device-tasks/list', { 
      params: { page: page.value, size: size.value }
    })
    if (res.data.code === 200) {
      taskList.value = res.data.data.records
      total.value = res.data.data.total
    }
  } finally { 
    loading.value = false 
    fetchStats()
  }
}

const handleDispatch = async () => {
  let payload = {
    taskType: newTask.taskType,
    status: 'pending'
  }

  // 根据类型组装数据
  if (newTask.taskType === 'ollama') {
    if (!newTask.prompt) { ElMessage.warning('请输入 Prompt'); return; }
    payload.prompt = newTask.prompt
    payload.modelName = newTask.model
  } else if (newTask.taskType === 'spider') {
    if (!newTask.spiderUrl) { ElMessage.warning('请输入采集 URL'); return; }
    payload.taskParams = JSON.stringify({ url: newTask.spiderUrl })
    payload.prompt = 'Web Scraping Task: ' + newTask.spiderUrl
  } else if (newTask.taskType === 'python_script') {
    if (!newTask.taskParams) { ElMessage.warning('请输入 Python 代码'); return; }
    payload.taskParams = newTask.taskParams
    payload.prompt = 'Remote Python Script Execution'
  }
  
  loading.value = true
  try {
    const res = await request.post('/api/admin/device-tasks/dispatch', payload)
    if (res.data.code === 200) {
      ElMessage.success('切片任务已提交至全网调度...')
      // 重置输入
      newTask.prompt = ''
      newTask.spiderUrl = ''
      newTask.taskParams = ''
      fetchData() 
    }
  } catch (e) {
    ElMessage.error('下发失败')
  } finally {
    loading.value = false
  }
}


const detailsVisible = ref(false)
const currentTask = ref({})

const viewDetails = (row) => {
  currentTask.value = row
  detailsVisible.value = true
}

onMounted(fetchData)

</script>

<style scoped>
.task-page { width: 100%; }

.command-hub {
  background: white;
  padding: 24px;
  border-radius: 12px;
  margin-bottom: 24px;
  box-shadow: 0 4px 12px rgba(0,0,0,0.05);
  border-left: 4px solid #1890ff;
}

.hub-header {
  font-size: 16px;
  font-weight: bold;
  margin-bottom: 20px;
  color: #333;
  display: flex;
  align-items: center;
  gap: 8px;
}

.mini-card {
  background: white;
  padding: 16px;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.04);
}

.mini-card .label { font-size: 12px; color: #999; margin-bottom: 4px; }
.mini-card .val { font-size: 20px; font-weight: 800; color: #333; }

.table-container {
  background: white;
  padding: 24px;
  border-radius: 12px;
  box-shadow: 0 4px 12px rgba(0,0,0,0.05);
}

.table-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.table-header .title { font-size: 15px; font-weight: bold; }

.code-id {
  background: #f4f4f5;
  color: #909399;
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 12px;
}

.code-box {
  background: #282c34;
  color: #abb2bf;
  padding: 16px;
  border-radius: 6px;
  font-family: 'Fira Code', monospace;
  font-size: 13px;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 300px;
  overflow-y: auto;
}
.code-box.success {
  border-left: 4px solid #67c23a;
  color: #e8f5e9;
}
.code-box.error {
  border-left: 4px solid #f56c6c;
  background: #fff5f5;
  color: #c0392b;
}

.pagination { margin-top: 24px; display: flex; justify-content: flex-end; }
</style>
