<template>
  <div class="team-list-page">
    <!-- 搜索栏 -->
    <el-card class="search-card">
      <el-row :gutter="12" align="middle">
        <el-col :span="6">
          <el-select
            v-model="selectedUserId"
            placeholder="选择用户查看团队"
            filterable
            remote
            :remote-method="searchUsers"
            :loading="searchingUsers"
            style="width: 100%;"
            clearable
            @change="handleUserChange"
          >
            <el-option
              v-for="user in userOptions"
              :key="user.id"
              :value="user.id"
            >
              <div style="display: flex; align-items: center; gap: 8px;">
                <el-avatar :size="24" :src="user.avatarUrl || 'https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png'" />
                <span>{{ user.nickname || '用户' }}</span>
                <span style="color: #9ca3af; font-size: 12px;">ID: {{ user.id }}</span>
                <el-tag size="small" v-if="user.teamCount > 0">{{ user.teamCount }}人</el-tag>
              </div>
            </el-option>
          </el-select>
        </el-col>
        <el-col :span="3">
          <el-button type="primary" @click="fetchTeamList" :loading="loading">查询团队</el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-col>
        <el-col :span="15" style="text-align: right;">
          <span v-if="selectedUser" class="selected-user-info">
            当前查看:
            <el-avatar :size="28" :src="selectedUser.avatarUrl || 'https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png'" />
            <strong>{{ selectedUser.nickname || '用户' + selectedUser.id }}</strong>
            的团队 (共 {{ total }} 人)
          </span>
        </el-col>
      </el-row>
    </el-card>

    <!-- 团队成员列表 -->
    <el-card class="table-card" v-if="selectedUserId">
      <template #header>
        <div class="team-summary-header">
          <div class="summary-title">团队成员总览</div>
          <div class="summary-stats">
            <el-tag type="info" effect="plain" class="stat-tag">成员总数: {{ total }}</el-tag>
            <el-tag type="success" effect="plain" class="stat-tag">合计算力设备: {{ teamTotalDevices }}</el-tag>
          </div>
        </div>
      </template>

      <el-table :data="teamList" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column label="成员信息" min-width="200">
          <template #default="{ row }">
            <div class="user-info-cell">
              <el-avatar :size="36" :src="row.avatarUrl || 'https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png'" />
              <div class="user-detail">
                <div class="user-name">{{ row.nickname || '用户' + row.id }}</div>
                <div class="user-id">ID: {{ row.id }}</div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="phone" label="手机号" width="130">
          <template #default="{ row }">
            {{ maskPhone(row.phone) }}
          </template>
        </el-table-column>
        <el-table-column label="等级" width="100">
          <template #default="{ row }">
            <el-tag :type="getLevelType(row.level)" size="small">
              {{ getLevelName(row.level) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="deviceCount" label="设备数" width="90">
          <template #default="{ row }">
            <span class="device-count">{{ row.deviceCount || 0 }} 台</span>
          </template>
        </el-table-column>
        <el-table-column prop="subTeamCount" label="下级人数" width="100">
          <template #default="{ row }">
            <el-button
              v-if="row.subTeamCount > 0"
              type="primary"
              link
              size="small"
              @click="viewSubTeam(row)"
            >
              {{ row.subTeamCount }} 人
            </el-button>
            <span v-else style="color: #9ca3af;">0 人</span>
          </template>
        </el-table-column>
        <el-table-column prop="balance" label="余额" width="100">
          <template #default="{ row }">
            ¥{{ row.balance || '0.00' }}
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="注册时间" width="170">
          <template #default="{ row }">
            {{ formatTime(row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" link @click="viewSubTeam(row)">
              查看团队
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
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="fetchTeamList"
          @current-change="fetchTeamList"
        />
      </div>
    </el-card>

    <!-- 快捷查看：优秀团队长 -->
    <div class="leaders-overview" v-if="!selectedUserId && !loading && userOptions.length > 0">
      <div class="section-title">
        <span><el-icon><Trophy /></el-icon> 优秀团队长 (按设备持有量排序)</span>
        <el-button type="primary" link @click="loadInitialUsers">刷新推荐</el-button>
      </div>
      <el-row :gutter="20">
        <el-col :xs="24" :sm="12" :lg="6" v-for="user in userOptions.slice(0, 12)" :key="user.id" class="leader-col">
          <el-card class="leader-card" shadow="hover" @click="viewSubTeam(user)">
            <div class="leader-content">
              <div class="leader-avatar-wrapper">
                <el-avatar :size="60" :src="user.avatarUrl || 'https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png'" />
                <el-tag class="leader-rank-tag" type="danger" effect="dark" v-if="user.teamDeviceCount > 100">TOP</el-tag>
              </div>
              <div class="leader-info">
                <div class="leader-name">{{ user.nickname || '用户' + user.id }}</div>
                <div class="leader-id">ID: {{ user.id }}</div>
                <div class="leader-stats-grid">
                  <div class="stat-box">
                    <span class="stat-label">团队设备</span>
                    <span class="stat-value highlight">{{ user.teamDeviceCount || 0 }}</span>
                  </div>
                  <div class="stat-box">
                    <span class="stat-label">团队规模</span>
                    <span class="stat-value">{{ user.teamCount || 0 }}</span>
                  </div>
                </div>
              </div>
            </div>
          </el-card>
        </el-col>
      </el-row>

      <!-- 团队长分页 (新增) -->
      <div class="pagination-wrapper" style="margin-top: 20px;">
        <el-pagination
          v-model:current-page="leaderPage"
          :page-size="12"
          :total="leaderTotal"
          layout="prev, pager, next"
          @current-change="loadInitialUsers"
        />
      </div>
    </div>

    <!-- 空状态 (仅当确实无数据时显示) -->
    <div class="empty-state" v-if="!selectedUserId && !loading && userOptions.length === 0">
        <div class="empty-icon-wrapper">
          <div class="empty-box-icon"><el-icon><Box /></el-icon></div>
        </div>
        <div class="empty-text">暂无数据，请尝试搜索</div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import axios from '../utils/request'
import { ElMessage } from 'element-plus'
import { Box, Trophy } from '@element-plus/icons-vue'

const teamList = ref([])
const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(20)
const total = ref(0)
const teamTotalDevices = ref(0) // 新增：团队总设备数

const selectedUserId = ref(null)
const selectedUser = ref(null)
const userOptions = ref([])
const searchingUsers = ref(false)

// 团队长分页相关
const leaderPage = ref(1)
const leaderTotal = ref(0)

// 等级相关
const inviteLevelOptions = ref([])
const getLevelName = (level) => {
  if (!level) return '普通'
  return inviteLevelOptions.value.find(item => item.index === level)?.name || `等级 ${level}`
}
const getLevelType = (level) => {
  const types = ['info', 'success', 'warning', 'danger', 'primary', '']
  return types[level] || 'info'
}

const loadInviteLevelOptions = async () => {
  try {
    const res = await axios.get('/api/settings/all')
    if (res.data.code === 200) {
      inviteLevelOptions.value = res.data.data.inviteLevels || []
    }
  } catch (e) {
    console.error('加载代理等级失败:', e)
  }
}

const maskPhone = (value) => {
  if (!value) return '-'
  const text = String(value)
  return text.length >= 7 ? `${text.slice(0, 3)}****${text.slice(-4)}` : '****'
}

// 搜索用户
const searchUsers = async (query) => {
  searchingUsers.value = true
  try {
    const res = await axios.get('/api/invite/admin/leaders', {
      params: {
        keyword: query || '',
        page: 1,
        size: 50,
        sortBy: 'team'
      }
    })
    if (res.data.code === 200) {
      userOptions.value = res.data.data.records || []
    }
  } catch (e) {
    console.error(e)
  } finally {
    searchingUsers.value = false
  }
}

// 加载初始用户列表 (优秀团队长)
const loadInitialUsers = async () => {
  searchingUsers.value = true
  try {
    const res = await axios.get('/api/invite/admin/leaders', {
      params: {
        page: leaderPage.value,
        size: 12,
        sortBy: 'team'
      }
    })
    if (res.data.code === 200) {
      userOptions.value = res.data.data.records || []
      leaderTotal.value = res.data.data.total || 0
    }
  } catch (e) {
    console.error(e)
  } finally {
    searchingUsers.value = false
  }
}

// 用户选择变化
const handleUserChange = (userId) => {
  if (userId) {
    const found = userOptions.value.find(u => u.id === userId)
    if (found) {
      selectedUser.value = found
    } else {
      // 如果没找到，至少保留ID
      selectedUser.value = { id: userId }
    }
    currentPage.value = 1
    fetchTeamList()
  } else {
    selectedUser.value = null
    teamList.value = []
    total.value = 0
    teamTotalDevices.value = 0
  }
}

// 获取团队列表
const fetchTeamList = async () => {
  if (!selectedUserId.value) return

  loading.value = true
  try {
    const res = await axios.get('/api/invite/admin/team', {
      params: {
        userId: selectedUserId.value,
        page: currentPage.value,
        size: pageSize.value
      }
    })
    if (res.data.code === 200) {
      const data = res.data.data
      teamList.value = data.records || []
      total.value = data.total || 0

      // 计算本页团队总设备数 (作为近似参考，或后端提供准确总计)
      teamTotalDevices.value = (data.records || []).reduce((sum, item) => sum + (item.deviceCount || 0), 0)

      // 如果后端有返回更准确的 teamDeviceCount，可以更新
      if (selectedUser.value && selectedUser.value.teamDeviceCount) {
          teamTotalDevices.value = selectedUser.value.teamDeviceCount
      }
    }
  } catch (e) {
    console.error(e)
    ElMessage.error('获取团队列表失败')
  } finally {
    loading.value = false
  }
}

// 查看下级团队
const viewSubTeam = (member) => {
  selectedUserId.value = member.id
  selectedUser.value = member
  currentPage.value = 1
  fetchTeamList()
  // 确保用户在下拉框中有选项
  if (!userOptions.value.find(u => u.id === member.id)) {
      userOptions.value = [...userOptions.value, member]
  }
}

// 重置搜索
const resetSearch = () => {
  selectedUserId.value = null
  selectedUser.value = null
  teamList.value = []
  total.value = 0
  teamTotalDevices.value = 0
  leaderPage.value = 1
  loadInitialUsers()
}

// 格式化时间
const formatTime = (timeStr) => {
  if (!timeStr) return '-'
  return timeStr.replace('T', ' ').substring(0, 19)
}

onMounted(() => {
  loadInviteLevelOptions()
  loadInitialUsers()
})
</script>

<style scoped>
.team-list-page {
  padding: 20px;
}

.search-card {
  margin-bottom: 20px;
}
.table-card {
  margin-bottom: 20px;
  border-radius: 12px;
  border: 1px solid #f0f0f0;
}

.team-summary-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.summary-title {
  font-size: 16px;
  font-weight: 600;
  color: #1f2937;
}

.summary-stats {
  display: flex;
  gap: 12px;
}

.stat-tag {
  font-weight: 500;
  border-radius: 6px;
}

.user-info-cell {
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-detail {
  display: flex;
  flex-direction: column;
}

.user-name {
  font-weight: 500;
  color: #1f2937;
}

.user-id {
  font-size: 12px;
  color: #9ca3af;
}

.device-count {
  font-weight: 600;
  color: #10b981;
}

.pagination-wrapper {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}

.leaders-overview {
  margin-top: 30px;
  padding: 0 5px;
}

.section-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  font-size: 18px;
  font-weight: 600;
  color: #374151;
  padding-left: 10px;
  border-left: 4px solid #4f46e5;
}

.leader-col {
  margin-bottom: 20px;
}

.leader-card {
  cursor: pointer;
  transition: all 0.3s ease;
  border-radius: 16px;
  border: 1px solid #f3f4f6;
  background: white;
}

.leader-card:hover {
  transform: translateY(-8px);
  box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 10px 10px -5px rgba(0, 0, 0, 0.04);
  border-color: #e5e7eb;
}

.leader-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  padding: 5px 0;
}

.leader-avatar-wrapper {
  position: relative;
  margin-bottom: 12px;
}

.leader-avatar-wrapper :deep(.el-avatar) {
  border: 2px solid white;
  box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1);
}

.leader-rank-tag {
  position: absolute;
  top: -6px;
  right: -10px;
  padding: 0 10px;
  font-size: 10px;
  height: 20px;
  line-height: 20px;
  font-weight: bold;
}

.leader-info {
  width: 100%;
}

.leader-name {
  font-weight: 600;
  font-size: 16px;
  color: #111827;
  margin-bottom: 4px;
}

.leader-id {
  font-size: 12px;
  color: #9ca3af;
  margin-bottom: 15px;
}

.leader-stats-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  background: #f9fafb;
  padding: 12px;
  border-radius: 12px;
}

.stat-box {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.stat-label {
  font-size: 11px;
  color: #6b7280;
  margin-bottom: 4px;
}

.stat-value {
  font-weight: 700;
  font-size: 15px;
  color: #374151;
}

.stat-value.highlight {
  color: #4f46e5;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 100px 0;
  background: white;
  border-radius: 16px;
  margin-top: 20px;
  border: 1px dashed #e5e7eb;
}

.empty-icon-wrapper {
  width: 100px;
  height: 100px;
  background: #f9fafb;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 20px;
}

.empty-box-icon {
  font-size: 50px;
  opacity: 0.5;
}

.empty-text {
  color: #9ca3af;
  font-size: 16px;
  font-weight: 500;
}

/* 底部缓冲 Padding */
.team-list-page {
  padding-bottom: 80px;
}
</style>
