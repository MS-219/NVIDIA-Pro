<template>
  <div class="reward-list-page">
    <!-- 筛选栏 -->
    <el-card class="search-card" shadow="never">
      <el-row :gutter="16">
        <el-col :span="6">
          <el-input v-model="filterInviterId" placeholder="搜索邀请人ID" clearable @keyup.enter="handleSearch" />
        </el-col>
        <el-col :span="6">
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-col>
      </el-row>
    </el-card>

    <!-- 奖励列表 -->
    <el-card class="table-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span class="title">邀请分润记录</span>
        </div>
      </template>

      <el-table :data="rewardList" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column label="获奖人 (上级)" min-width="180">
          <template #default="{ row }">
            <div class="user-info-cell">
              <el-avatar :size="28" :src="row.inviterAvatarUrl || 'https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png'" />
              <div class="user-detail">
                <div class="user-name">{{ row.inviterNickname || '未知用户' }}</div>
                <div class="user-id">ID: {{ row.inviterId }}</div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="产生的下级" min-width="180">
          <template #default="{ row }">
            <div class="user-info-cell">
              <el-avatar :size="28" :src="row.inviteeAvatarUrl || 'https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png'" />
              <div class="user-detail">
                <div class="user-name grey">{{ row.inviteeNickname || '未知用户' }}</div>
                <div class="user-id dark">ID: {{ row.inviteeId }}</div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="奖励金额" width="120">
          <template #default="{ row }">
            <span class="amount-text">+¥{{ row.reward }}</span>
          </template>
        </el-table-column>
        <el-table-column label="奖励类型" width="120">
          <template #default="{ row }">
            <el-tag :type="getRewardTypeTag(row.rewardType)" size="small">
              {{ getRewardTypeText(row.rewardType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="150" show-overflow-tooltip />
        <el-table-column prop="createTime" label="时间" width="180">
          <template #default="{ row }">
            {{ formatTime(row.createTime) }}
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next"
          @size-change="fetchRewards"
          @current-change="fetchRewards"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import axios from '../utils/request'
import { ElMessage } from 'element-plus'

const loading = ref(false)
const rewardList = ref([])
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const filterInviterId = ref('')

const fetchRewards = async () => {
  loading.value = true
  try {
    const res = await axios.get('/api/admin/invite-reward/list', {
      params: {
        page: currentPage.value,
        size: pageSize.value,
        inviterId: filterInviterId.value || undefined
      }
    })
    if (res.data.code === 200) {
      rewardList.value = res.data.data.records
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
  fetchRewards()
}

const resetSearch = () => {
  filterInviterId.value = ''
  currentPage.value = 1
  fetchRewards()
}

const getRewardTypeText = (type) => {
  const map = {
    'register': '注册奖励',
    'device': '绑定奖励',
    'earnings': '收益分润'
  }
  return map[type] || type
}

const getRewardTypeTag = (type) => {
  const map = {
    'register': 'info',
    'device': 'success',
    'earnings': 'warning'
  }
  return map[type] || 'info'
}

const formatTime = (time) => {
  if (!time) return '-'
  return String(time).replace('T', ' ').substring(0, 16)
}

onMounted(() => {
  fetchRewards()
})
</script>

<style scoped>
.reward-list-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.search-card, .table-card {
  border-radius: 12px;
}
.title {
  font-size: 16px;
  font-weight: 600;
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
.user-name.grey {
  color: #64748b;
}
.user-id {
  font-size: 11px;
  color: #999;
}
.user-id.dark {
  color: #94a3b8;
}
.amount-text {
  color: #10b981;
  font-weight: 700;
  font-size: 15px;
}
.pagination-wrapper {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

/* 底部缓冲 Padding */
.reward-list-page {
  padding-bottom: 80px;
}
</style>
