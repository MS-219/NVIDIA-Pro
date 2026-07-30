<template>
  <div class="user-list-page">
    <!-- 统计卡片 -->
    <el-row :gutter="20" class="stat-cards">
      <el-col :span="6">
        <div class="stat-card users">
          <div class="stat-icon"><el-icon><UserFilled /></el-icon></div>
          <div class="stat-info">
            <div class="stat-value">{{ total }}</div>
            <div class="stat-label">总用户数</div>
          </div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card active">
          <div class="stat-icon"><el-icon><Monitor /></el-icon></div>
          <div class="stat-info">
            <div class="stat-value">{{ stats.hasDeviceCount || 0 }}</div>
            <div class="stat-label">有设备用户</div>
          </div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card balance">
          <div class="stat-icon"><el-icon><Coin /></el-icon></div>
          <div class="stat-info">
            <div class="stat-value">¥{{ stats.totalBalance || '0.00' }}</div>
            <div class="stat-label">总余额</div>
          </div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card quota">
          <div class="stat-icon"><el-icon><Lightning /></el-icon></div>
          <div class="stat-info">
            <div class="stat-value">{{ stats.totalQuota || 0 }}</div>
            <div class="stat-label">总聚芯算力值</div>
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- 搜索栏 -->
    <el-card class="search-card" shadow="never">
      <el-row :gutter="16" align="middle">
        <el-col :span="5">
          <el-input
            v-model="searchKeyword"
            placeholder="搜索昵称/ID/手机号/OpenID"
            prefix-icon="Search"
            clearable
            @clear="handleSearch"
            @keyup.enter="handleSearch"
          />
        </el-col>
        <el-col :span="4">
          <el-select v-model="filterType" placeholder="筛选条件" clearable @change="handleSearch">
            <el-option label="全部用户" value="" />
            <el-option label="有设备" value="hasDevice" />
            <el-option label="有余额" value="hasBalance" />
          </el-select>
        </el-col>
        <el-col :span="4">
          <el-select v-model="userTypeFilter" placeholder="用户类型" clearable @change="handleSearch">
            <el-option label="全部类型" value="" />
            <el-option label="个人用户" value="personal" />
            <el-option label="公司用户" value="company" />
          </el-select>
        </el-col>
        <el-col :span="6">
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="resetSearch">重置</el-button>
          <el-button type="success" @click="refreshData">刷新</el-button>
          <el-button type="warning" @click="refreshLevels">同步等级</el-button>
        </el-col>
        <el-col :span="5" style="text-align: right;">
          <span class="total-count">共 {{ total }} 位用户</span>
        </el-col>
      </el-row>
    </el-card>

    <!-- 用户列表 -->
    <el-card class="table-card" shadow="never">
      <template #header>
        <div class="table-toolbar">
          <div>
            <div class="table-toolbar-title">用户列表</div>
            <div class="table-toolbar-tip">删除的用户会先进入回收站，设备和历史账目暂时保留</div>
          </div>
          <el-button type="danger" plain @click="openRecycleBin">回收站</el-button>
        </div>
      </template>
      <el-table :data="userList" v-loading="loading" stripe>
        <el-table-column label="ID" width="110">
          <template #default="{ row }">
            <code class="table-id-code">{{ String(row.id).padStart(6, '0') }}</code>
          </template>
        </el-table-column>
        <el-table-column label="用户信息" min-width="220">
          <template #default="{ row }">
            <div class="user-info">
              <el-avatar :size="45" :src="row.avatarUrl || ''">
                {{ (row.nickname || '微信用户').charAt(0) }}
              </el-avatar>
              <div class="user-detail">
                <div class="nickname">{{ row.nickname || '微信用户' }}</div>
                <div class="phone" v-if="row.phone"><el-icon><Iphone /></el-icon> {{ maskPhone(row.phone) }}</div>
                <div class="openid">
                  <span>{{ maskIdentifier(row.openid) }}</span>
                </div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="余额" width="100">
          <template #default="{ row }">
            <span class="balance">¥{{ row.balance || '0.00' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="聚芯算力值" width="90">
          <template #default="{ row }">
            <span class="quota-value"><el-icon><Lightning /></el-icon>{{ row.quota ?? 0 }}</span>
          </template>
        </el-table-column>
        <el-table-column label="设备" width="80">
          <template #default="{ row }">
            <el-tag v-if="row.deviceCount > 0" type="success" effect="dark" round size="small">
              <el-icon><Monitor /></el-icon> {{ row.deviceCount }}
            </el-tag>
            <el-tag v-else type="info" effect="plain" round size="small">0</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="签约状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getContractType(row.contractStatus)" size="small" effect="light">
              {{ row.contractStatusText || '待签约' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="用户类型" width="100">
          <template #default="{ row }">
            <el-tag :type="getUserTypeTag(row.userType)" size="small" effect="plain">
              {{ getUserTypeText(row.userType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="等级" width="100">
          <template #default="{ row }">
            <el-tag :type="getLevelTag(row.level)" size="small" effect="dark">
              {{ getLevelText(row.level) }}
            </el-tag>
            <el-tooltip v-if="row.levelManual" content="等级已锁定 (不参与自动晋升)" placement="top">
              <el-icon style="margin-left: 4px; cursor: help;"><Lock /></el-icon>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column label="创作" width="80">
          <template #default="{ row }">
            <el-tag v-if="row.taskCount > 0" type="warning" effect="dark" round size="small">
              <el-icon><Brush /></el-icon> {{ row.taskCount }}
            </el-tag>
            <el-tag v-else type="info" effect="plain" round size="small">0</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="邀请人" width="160">
          <template #default="{ row }">
            <div class="user-info-cell" v-if="row.inviterId">
              <el-avatar :size="24" :src="row.inviterAvatarUrl || 'https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png'" />
              <div class="user-detail">
                <div class="user-name">{{ row.inviterNickname || '未知' }}</div>
                <div class="user-id">ID: {{ row.inviterId }}</div>
              </div>
            </div>
            <span v-else class="text-gray-400" style="font-size: 12px;">无邀请人</span>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="注册时间" width="160">
          <template #default="{ row }">
            <span class="create-time">{{ formatTime(row.createTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="330" fixed="right">
          <template #default="{ row }">
            <div class="user-actions">
              <el-button size="small" type="primary" @click="viewUser(row)">详情</el-button>
              <el-button size="small" @click="editUser(row)">编辑</el-button>
              <el-button size="small" type="warning" @click="rechargeQuota(row)">充值</el-button>
              <el-button
                size="small"
                :type="row.withdrawDisabled ? 'success' : 'danger'"
                @click="toggleWithdraw(row)"
              >
                {{ row.withdrawDisabled ? '解禁' : '禁提' }}
              </el-button>
              <el-button size="small" type="success" @click="openTransferDialog(row)">迁移</el-button>
              <el-button
                size="small"
                type="danger"
                plain
                :loading="deletingUserId === row.id"
                @click="deleteUser(row)"
              >
                删除
              </el-button>
            </div>
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
          @size-change="fetchUsers"
          @current-change="fetchUsers"
        />
      </div>
    </el-card>

    <!-- 用户回收站 -->
    <el-dialog v-model="recycleVisible" title="用户回收站" width="900px" top="7vh" destroy-on-close>
      <div class="recycle-bin">
        <el-alert
          title="回收站中的用户不会登录或继续产生设备收益；恢复后会保留原设备绑定和历史账目。"
          type="warning"
          :closable="false"
          show-icon
        />
        <div class="recycle-toolbar">
          <div class="recycle-search">
            <el-input
              v-model="recycleKeyword"
              placeholder="搜索昵称/ID/手机号/OpenID"
              clearable
              @clear="handleRecycleSearch"
              @keyup.enter="handleRecycleSearch"
            />
            <el-button type="primary" @click="handleRecycleSearch">搜索</el-button>
            <el-button @click="resetRecycleSearch">重置</el-button>
          </div>
          <el-button
            type="danger"
            :disabled="recycleTotal === 0"
            :loading="recycleClearing"
            @click="clearRecycleBin"
          >
            永久清空
          </el-button>
        </div>

        <el-table :data="recycleList" v-loading="recycleLoading" stripe empty-text="回收站暂无用户">
          <el-table-column label="用户" min-width="220">
            <template #default="{ row }">
              <div class="user-info">
                <el-avatar :size="40" :src="row.avatarUrl || ''">
                  {{ (row.nickname || '微信用户').charAt(0) }}
                </el-avatar>
                <div class="user-detail">
                  <div class="nickname">{{ row.nickname || '微信用户' }}</div>
                  <div class="user-id">ID: {{ row.id }}</div>
                  <div class="phone" v-if="row.phone">{{ maskPhone(row.phone) }}</div>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="余额" width="110">
            <template #default="{ row }">
              <span class="balance">¥{{ formatMoney(row.balance) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="保留设备" width="100">
            <template #default="{ row }">{{ row.deviceCount || 0 }} 台</template>
          </el-table-column>
          <el-table-column label="删除时间" width="170">
            <template #default="{ row }">{{ formatTime(row.deletedAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="100" fixed="right">
            <template #default="{ row }">
              <el-button
                size="small"
                type="success"
                :loading="restoringUserId === row.id"
                @click="restoreUser(row)"
              >
                恢复
              </el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="pagination-wrapper recycle-pagination">
          <el-pagination
            v-model:current-page="recyclePage"
            v-model:page-size="recyclePageSize"
            :total="recycleTotal"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next"
            @size-change="handleRecyclePageSizeChange"
            @current-change="fetchRecycleUsers"
          />
        </div>
      </div>
    </el-dialog>

    <!-- 用户详情弹窗 -->
    <el-dialog v-model="detailVisible" title="用户详情" width="92%" top="5vh" class="user-detail-modal">
      <div class="user-detail-dialog" v-if="currentUser" v-loading="detailLoading">
        <!-- 用户基本信息 -->
        <div class="detail-header">
          <el-avatar :size="80" :src="currentUser.avatarUrl || ''">
            {{ (currentUser.nickname || '微信用户').charAt(0) }}
          </el-avatar>
          <div class="header-info">
            <div class="header-name">{{ currentUser.nickname || '微信用户' }}</div>
            <div class="header-id">ID: {{ currentUser.id }} | {{ sensitiveVisible ? (currentUser.phone || '未绑定手机') : maskPhone(currentUser.phone) }}</div>
          </div>
          <el-button class="sensitive-toggle" size="small" @click="sensitiveVisible = !sensitiveVisible">
            {{ sensitiveVisible ? '隐藏敏感信息' : '查看敏感信息' }}
          </el-button>
          <div class="header-stats">
            <div class="stat-item">
              <div class="stat-num balance">¥{{ currentUser.balance || '0.00' }}</div>
              <div class="stat-text">余额</div>
            </div>
            <div class="stat-item">
              <div class="stat-num quota-value"><el-icon><Lightning /></el-icon> {{ currentUser.quota ?? 0 }}</div>
              <div class="stat-text">聚芯算力值</div>
            </div>
            <div class="stat-item">
              <div class="stat-num">¥{{ currentUser.totalEarnings || '0.00' }}</div>
              <div class="stat-text">总收益</div>
            </div>
          </div>
        </div>

        <el-divider />

        <!-- 设备列表 -->
        <div class="detail-section">
          <div class="section-title" style="display: flex; justify-content: space-between; align-items: center;">
            <span><el-icon><Monitor /></el-icon> 绑定的设备 ({{ currentUser.devices?.length || 0 }} 台)</span>
            <el-button
              type="danger"
              size="small"
              plain
              :disabled="selectedDevices.length === 0"
              @click="handleBatchUnbind"
            >
              批量解绑
            </el-button>
          </div>
          <div class="empty-tip" v-if="!currentUser.devices || currentUser.devices.length === 0">
            该用户暂未绑定设备
          </div>
          <el-table
            v-else
            :data="pagedDevices"
            row-key="id"
            size="small"
            stripe
            @selection-change="handleSelectionChange"
          >
            <el-table-column type="selection" width="55" />
            <el-table-column prop="sn" label="设备 SN" width="180">
              <template #default="{ row }">
                <code class="sn-code">{{ row.sn }}</code>
              </template>
            </el-table-column>
            <el-table-column prop="name" label="设备名称" width="100">
              <template #default="{ row }">
                {{ row.name || '未命名' }}
              </template>
            </el-table-column>
            <el-table-column prop="location" label="位置" width="130">
              <template #default="{ row }">
                <span class="location-text" v-if="row.location">{{ row.location }}</span>
                <span v-else style="color: #ccc; font-size: 11px;">未知位置</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="row.status === 1 ? 'success' : 'info'" effect="dark" size="small" round>
                  {{ row.status === 1 ? '在线' : '离线' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="设备收益" width="120">
              <template #default="{ row }">
                <span class="device-earnings">¥{{ row.earnings || '0.00' }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="lastHeartbeatTime" label="最后心跳">
              <template #default="{ row }">
                <span class="time-text">{{ formatTime(row.lastHeartbeatTime) || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="bindTime" label="绑定时间">
              <template #default="{ row }">
                <span class="time-text">{{ formatTime(row.bindTime) || '-' }}</span>
              </template>
            </el-table-column>
          </el-table>
          <div class="detail-pagination" v-if="currentUser.devices?.length">
            <el-pagination
              v-model:current-page="devicePage"
              v-model:page-size="devicePageSize"
              :total="currentUser.devices.length"
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next"
              size="small"
              background
              @size-change="handleDevicePageSizeChange"
              @current-change="handleDevicePageChange"
            />
          </div>
        </div>

        <!-- 提现明细 -->
        <div class="detail-section" v-loading="withdrawDetailLoading">
          <div class="section-title">
            <span>提现明细 ({{ withdrawDetailTotal }} 笔)</span>
          </div>
          <div class="empty-tip" v-if="!withdrawDetailLoading && withdrawDetails.length === 0">
            该用户暂无提现记录
          </div>
          <el-table :data="withdrawDetails" v-else size="small" stripe>
            <el-table-column prop="id" label="记录 ID" width="80" />
            <el-table-column label="提现金额" width="105">
              <template #default="{ row }">
                <span class="withdraw-amount">¥{{ formatMoney(row.amount) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="手续费" width="90">
              <template #default="{ row }">¥{{ formatMoney(row.fee) }}</template>
            </el-table-column>
            <el-table-column label="实际到账" width="105">
              <template #default="{ row }">¥{{ formatMoney(row.actualAmount ?? row.amount) }}</template>
            </el-table-column>
            <el-table-column label="提现方式" width="95">
              <template #default="{ row }">
                <el-tag :type="getWithdrawTypeTag(row.type)" size="small">
                  {{ getWithdrawTypeName(row.type) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="收款账号" min-width="150">
              <template #default="{ row }">
                <span class="withdraw-account">{{ sensitiveVisible ? getWithdrawAccount(row) : maskAccount(getWithdrawAccount(row)) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="95">
              <template #default="{ row }">
                <el-tag :type="getWithdrawStatusTag(row.status)" size="small" effect="dark">
                  {{ getWithdrawStatusName(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="申请时间" width="165">
              <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
            </el-table-column>
            <el-table-column label="处理时间" width="165">
              <template #default="{ row }">
                {{ formatTime(row.processTime || row.auditTime || row.updateTime) }}
              </template>
            </el-table-column>
            <el-table-column label="备注" min-width="140">
              <template #default="{ row }">
                <el-tooltip
                  v-if="row.rejectReason || row.remark"
                  :content="row.rejectReason || row.remark"
                  placement="top"
                >
                  <span class="withdraw-remark">{{ row.rejectReason || row.remark }}</span>
                </el-tooltip>
                <span v-else>-</span>
              </template>
            </el-table-column>
          </el-table>
          <div class="detail-pagination" v-if="withdrawDetailTotal > 0">
            <el-pagination
              v-model:current-page="withdrawDetailPage"
              v-model:page-size="withdrawDetailPageSize"
              :total="withdrawDetailTotal"
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next"
              size="small"
              background
              @size-change="handleWithdrawDetailPageSizeChange"
              @current-change="fetchWithdrawDetails"
            />
          </div>
        </div>

        <!-- 基础信息 -->
        <div class="detail-section">
          <div class="section-title"><el-icon><Memo /></el-icon> 基础信息</div>
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="用户 ID">{{ currentUser.id }}</el-descriptions-item>
            <el-descriptions-item label="昵称">{{ currentUser.nickname || '-' }}</el-descriptions-item>
            <el-descriptions-item label="手机号">{{ sensitiveVisible ? (currentUser.phone || '未绑定') : maskPhone(currentUser.phone) }}</el-descriptions-item>
            <el-descriptions-item label="用户类型">
              <el-tag :type="getUserTypeTag(currentUser.userType)" size="small">
                {{ getUserTypeText(currentUser.userType) }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="备注" :span="2">{{ currentUser.remark || '-' }}</el-descriptions-item>
            <el-descriptions-item label="聚芯算力值"><el-icon><Lightning /></el-icon> {{ currentUser.quota ?? 0 }}</el-descriptions-item>
            <el-descriptions-item label="OpenID" :span="2">
              <code>{{ sensitiveVisible ? currentUser.openid : maskIdentifier(currentUser.openid) }}</code>
            </el-descriptions-item>
            <el-descriptions-item label="注册时间" :span="2">{{ currentUser.createTime }}</el-descriptions-item>
            <el-descriptions-item label="签约状态" :span="1">
              <el-tag :type="getContractType(currentUser.contractStatus)" size="small">
                {{ currentUser.contractStatusText }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="实名信息" :span="1">
              <span v-if="currentUser.contractRealName">
                {{ sensitiveVisible ? currentUser.contractRealName : maskName(currentUser.contractRealName) }}
                ({{ sensitiveVisible ? currentUser.contractMobile : maskPhone(currentUser.contractMobile) }})
              </span>
              <span v-else class="text-gray-300">未同步</span>
            </el-descriptions-item>
            <el-descriptions-item label="身份证" :span="2" v-if="currentUser.contractIdCard">
              <code>{{ sensitiveVisible ? currentUser.contractIdCard : maskIdentity(currentUser.contractIdCard) }}</code>
            </el-descriptions-item>
            <el-descriptions-item label="邀请人" :span="2">
              <div class="user-info-cell" v-if="currentUser.inviterId">
                <el-avatar :size="20" :src="currentUser.inviterAvatarUrl || 'https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png'" />
                <span style="margin-left: 8px;">
                  ID: {{ currentUser.inviterId }}
                  <span v-if="currentUser.inviterNickname">({{ currentUser.inviterNickname }})</span>
                </span>
              </div>
              <span v-else class="text-gray-400">无邀请人</span>
            </el-descriptions-item>
          </el-descriptions>
        </div>
      </div>
    </el-dialog>

    <!-- 编辑弹窗 -->
    <!-- 编辑弹窗 -->
    <el-dialog v-model="editVisible" title="编辑用户信息" width="550px" class="user-edit-dialog">
      <div class="edit-container">
        <el-form :model="editForm" label-width="100px" label-position="left">
          <div class="form-section">
            <div class="section-badge">基本信息</div>
            <el-form-item label="用户昵称">
              <el-input v-model="editForm.nickname" placeholder="请输入用户昵称" prefix-icon="User" />
            </el-form-item>
            <el-form-item label="手机号码">
              <el-input v-model="editForm.phone" placeholder="请输入手机号" prefix-icon="Iphone" />
            </el-form-item>
            <el-form-item label="用户类型">
              <el-radio-group v-model="editForm.userType">
                <el-radio-button value="personal">个人用户</el-radio-button>
                <el-radio-button value="company">公司用户</el-radio-button>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="备注">
              <el-input
                v-model="editForm.remark"
                type="textarea"
                :rows="3"
                maxlength="500"
                show-word-limit
                placeholder="请输入后台备注"
              />
            </el-form-item>
          </div>

          <div class="form-section">
            <div class="section-badge account">账户资产</div>
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="账户余额">
                  <el-input-number v-model="editForm.balance" :precision="2" :min="0" :max="999999" style="width: 100%" />
                  <div class="unit-text">元</div>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="算力配额">
                  <el-input-number v-model="editForm.quota" :min="0" :max="999999" style="width: 100%" disabled />
                  <div class="unit-text">点</div>
                </el-form-item>
              </el-col>
            </el-row>
            <div class="form-tip sync-tip"><el-icon><Opportunity /></el-icon> 保存后算力配额会按账户余额自动同步</div>
          </div>

          <div class="form-section">
            <div class="section-badge level">身份等级</div>
            <el-form-item label="用户等级">
              <el-select v-model="editForm.level" placeholder="请选择等级" :disabled="!editForm.levelManual" style="width: 100%">
                <el-option label="普通用户" :value="0" />
                <el-option
                  v-for="level in inviteLevelOptions"
                  :key="level.index"
                  :label="level.name"
                  :value="level.index"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="自动升级">
              <div class="switch-wrapper">
                <el-switch
                  v-model="editForm.levelManual"
                  :active-value="false"
                  :inactive-value="true"
                  active-text="开启自动晋升"
                  inactive-text="锁定指定等级"
                />
              </div>
            </el-form-item>
          </div>

          <div class="form-section">
            <div class="section-badge invite">分润关系</div>
            <el-form-item label="邀请人">
              <el-select
                v-model="editForm.inviterId"
                placeholder="输入昵称或ID搜索邀请人"
                filterable
                remote
                reserve-keyword
                :remote-method="searchInviter"
                :loading="inviterSearching"
                style="width: 100%;"
                clearable
              >
                <el-option
                  v-for="user in inviterSearchOptions"
                  :key="user.id"
                  :label="`${user.nickname || '用户'} (ID: ${user.id})`"
                  :value="user.id"
                >
                  <div class="inviter-option">
                    <el-avatar :size="24" :src="user.avatarUrl || ''" />
                    <span>{{ user.nickname || '用户' }}</span>
                    <span class="id-tag">ID: {{ user.id }}</span>
                  </div>
                </el-option>
              </el-select>
              <div class="form-tip warning"><el-icon><WarningFilled /></el-icon> 修改后将直接影响所有下级产生的分润归属</div>
            </el-form-item>
          </div>
        </el-form>
      </div>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="editVisible = false" round>取 消</el-button>
          <el-button type="primary" @click="saveUser" :loading="saving" round>保 存 更 改</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 充值聚芯算力值弹窗 -->
    <el-dialog v-model="rechargeVisible" title="充值聚芯算力值" width="400px">
      <el-form :model="rechargeForm" label-width="80px">
        <el-form-item label="当前聚芯算力值">
          <span class="quota-value"><el-icon><Lightning /></el-icon> {{ rechargeForm.currentQuota }}</span>
        </el-form-item>
        <el-form-item label="充值数量">
          <el-input-number v-model="rechargeForm.amount" :min="1" :max="10000" />
          <span style="margin-left: 8px; color: #9ca3af;">点</span>
        </el-form-item>
        <el-form-item label="充值后">
          <span class="quota-value success"><el-icon><Lightning /></el-icon> {{ rechargeForm.currentQuota + rechargeForm.amount }}</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rechargeVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmRecharge" :loading="saving">确认充值</el-button>
      </template>
    </el-dialog>

    <!-- 实名提现账号迁移弹窗 -->
    <el-dialog v-model="transferVisible" title="实名提现账号迁移" width="520px">
      <el-alert
        title="迁移后来源账号将被禁提，目标账号将成为该实名的可提现账号。请确认两个账号属于同一实名。"
        type="warning"
        show-icon
        :closable="false"
        class="transfer-alert"
      />
      <el-form :model="transferForm" label-width="110px">
        <el-form-item label="来源账号ID">
          <el-input v-model="transferForm.sourceUserId" disabled />
        </el-form-item>
        <el-form-item label="来源账号">
          <span>{{ transferForm.sourceLabel }}</span>
        </el-form-item>
        <el-form-item label="目标账号ID" required>
          <el-input
            v-model="transferForm.targetUserId"
            placeholder="填写要接管实名提现的用户ID"
            @keyup.enter="submitTransfer"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="transferVisible = false">取消</el-button>
        <el-button type="primary" :loading="transferSaving" @click="submitTransfer">确认迁移</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, ref, reactive, onMounted } from 'vue'
import axios from '../utils/request'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Brush,
  Coin,
  Iphone,
  Lightning,
  Lock,
  Memo,
  Monitor,
  Opportunity,
  UserFilled,
  WarningFilled
} from '@element-plus/icons-vue'

const userList = ref([])
const loading = ref(false)
const detailLoading = ref(false)
const saving = ref(false)
const searchKeyword = ref('')
const filterType = ref('')
const userTypeFilter = ref('')
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const stats = ref({})
const deletingUserId = ref(null)
const recycleVisible = ref(false)
const recycleLoading = ref(false)
const recycleClearing = ref(false)
const recycleList = ref([])
const recycleKeyword = ref('')
const recyclePage = ref(1)
const recyclePageSize = ref(10)
const recycleTotal = ref(0)
const restoringUserId = ref(null)

const detailVisible = ref(false)
const editVisible = ref(false)
const rechargeVisible = ref(false)
const transferVisible = ref(false)
const transferSaving = ref(false)
const sensitiveVisible = ref(false)
const currentUser = ref(null)
const selectedDevices = ref([])
const devicePage = ref(1)
const devicePageSize = ref(10)
const withdrawDetails = ref([])
const withdrawDetailLoading = ref(false)
const withdrawDetailPage = ref(1)
const withdrawDetailPageSize = ref(10)
const withdrawDetailTotal = ref(0)

const pagedDevices = computed(() => {
  const devices = currentUser.value?.devices || []
  const start = (devicePage.value - 1) * devicePageSize.value
  return devices.slice(start, start + devicePageSize.value)
})

const editForm = reactive({
  id: null,
  nickname: '',
  phone: '',
  balance: 0,
  quota: 100,
  inviterId: null,
  level: 0,
  levelManual: false,
  userType: 'personal',
  remark: ''
})

const inviteLevelOptions = ref([])

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

const rechargeForm = reactive({
  userId: null,
  currentQuota: 0,
  amount: 100
})

const transferForm = reactive({
  sourceUserId: '',
  sourceLabel: '',
  targetUserId: ''
})

// 邀请人搜索相关
const inviterSearchOptions = ref([])
const inviterSearching = ref(false)

const searchInviter = async (query) => {
    if (!query) {
        inviterSearchOptions.value = []
        return
    }
    inviterSearching.value = true
    try {
        const res = await axios.get('/api/user/list', {
            params: {
                page: 1,
                size: 20,
                keyword: query
            }
        })
        if (res.data.code === 200) {
            inviterSearchOptions.value = res.data.data.records || []
        }
    } catch (e) {
        console.error(e)
    } finally {
        inviterSearching.value = false
    }
}

const formatTime = (time) => {
  if (!time) return '-'
  return String(time).replace('T', ' ').substring(0, 19)
}

const formatMoney = (amount) => {
  const value = Number(amount)
  return Number.isFinite(value) ? value.toFixed(2) : '0.00'
}

const maskPhone = (value) => {
  if (!value) return '未绑定'
  const text = String(value)
  return text.length >= 7 ? `${text.slice(0, 3)}****${text.slice(-4)}` : '****'
}

const maskIdentifier = (value) => {
  if (!value) return '-'
  const text = String(value)
  return text.length > 10 ? `${text.slice(0, 6)}****${text.slice(-4)}` : '****'
}

const maskIdentity = (value) => {
  if (!value) return '-'
  const text = String(value)
  return text.length >= 8 ? `${text.slice(0, 4)}**********${text.slice(-4)}` : '****'
}

const maskAccount = (value) => {
  if (!value || value === '-') return '-'
  const text = String(value)
  return text.length > 8 ? `${text.slice(0, 4)} **** ${text.slice(-4)}` : '****'
}

const maskName = (value) => {
  if (!value) return '-'
  const text = String(value)
  return `${text.slice(0, 1)}${'*'.repeat(Math.max(1, text.length - 1))}`
}

const getWithdrawTypeName = (type) => {
  const types = { 1: '微信', 2: '支付宝', 3: '银行卡' }
  return types[type] || '未知'
}

const getWithdrawTypeTag = (type) => {
  const tags = { 1: 'success', 2: 'primary', 3: 'warning' }
  return tags[type] || 'info'
}

const getWithdrawStatusName = (status) => {
  const names = { 0: '待审核', 1: '已通过', 2: '已拒绝', 3: '已打款', 4: '失败' }
  return names[status] || '未知'
}

const getWithdrawStatusTag = (status) => {
  const tags = { 0: 'warning', 1: 'primary', 2: 'danger', 3: 'success', 4: 'danger' }
  return tags[status] || 'info'
}

const getWithdrawAccount = (row) => {
  if (row.type === 2) return row.alipayAccount || row.account || '-'
  if (row.type === 3) return row.bankCardNo || row.account || '-'
  return row.account || '-'
}

const handleDevicePageSizeChange = () => {
  devicePage.value = 1
  selectedDevices.value = []
}

const handleDevicePageChange = () => {
  selectedDevices.value = []
}

const fetchWithdrawDetails = async () => {
  const userId = currentUser.value?.id
  if (!userId) return

  withdrawDetailLoading.value = true
  try {
    const res = await axios.get('/api/withdraw/admin/list', {
      params: {
        userId,
        page: withdrawDetailPage.value,
        size: withdrawDetailPageSize.value
      }
    })
    if (res.data.code === 200 && currentUser.value?.id === userId) {
      withdrawDetails.value = res.data.data.records || []
      withdrawDetailTotal.value = res.data.data.total || 0
    } else if (res.data.code !== 200) {
      ElMessage.error(res.data.msg || '提现明细加载失败')
    }
  } catch (e) {
    console.error(e)
    ElMessage.error('提现明细加载失败')
  } finally {
    if (currentUser.value?.id === userId) {
      withdrawDetailLoading.value = false
    }
  }
}

const handleWithdrawDetailPageSizeChange = () => {
  withdrawDetailPage.value = 1
  fetchWithdrawDetails()
}

const fetchStats = async () => {
  try {
    const res = await axios.get('/api/user/stats')
    if (res.data.code === 200) {
      stats.value = res.data.data
    }
  } catch (e) {
    console.error(e)
  }
}

const fetchUsers = async () => {
  loading.value = true
  try {
    const res = await axios.get('/api/user/list', {
      params: {
        page: currentPage.value,
        size: pageSize.value,
        keyword: searchKeyword.value || undefined,
        filter: filterType.value || undefined,
        userType: userTypeFilter.value || undefined
      }
    })
    if (res.data.code === 200) {
      userList.value = res.data.data.records || []
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
  fetchUsers()
}

const resetSearch = () => {
  searchKeyword.value = ''
  filterType.value = ''
  userTypeFilter.value = ''
  currentPage.value = 1
  fetchUsers()
}

const refreshData = () => {
  fetchStats()
  fetchUsers()
  ElMessage.success('数据已刷新')
}

const refreshLevels = async () => {
  try {
    await ElMessageBox.confirm('确定要全员重新计算等级吗？这会根据当前设备数自动更新所有已开启自动升级的用户的等级。', '提示', {
      type: 'warning'
    })
    const res = await axios.get('/api/user/refresh-levels')
    if (res.data.code === 200) {
      ElMessage.success('等级同步完成')
      fetchUsers()
    } else {
      ElMessage.error(res.data.msg || '同步失败')
    }
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('操作失败')
  }
}

const deleteUser = async (row) => {
  try {
    await ElMessageBox.confirm(
      `确定删除用户“${row.nickname || row.id}”吗？删除后用户会进入回收站，暂时无法登录和产生收益，可在回收站恢复。`,
      '删除用户',
      {
        confirmButtonText: '移入回收站',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    deletingUserId.value = row.id
    const res = await axios.post(`/api/user/delete/${row.id}`)
    if (res.data.code !== 200) {
      ElMessage.error(res.data.msg || '删除失败')
      return
    }

    if (userList.value.length === 1 && currentPage.value > 1) {
      currentPage.value--
    }
    ElMessage.success(res.data.data || '用户已移入回收站')
    await Promise.all([fetchUsers(), fetchStats()])
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') {
      console.error(e)
      ElMessage.error('删除失败')
    }
  } finally {
    deletingUserId.value = null
  }
}

const fetchRecycleUsers = async () => {
  recycleLoading.value = true
  try {
    const res = await axios.get('/api/user/recycle-bin', {
      params: {
        page: recyclePage.value,
        size: recyclePageSize.value,
        keyword: recycleKeyword.value || undefined
      }
    })
    if (res.data.code === 200) {
      recycleList.value = res.data.data.records || []
      recycleTotal.value = res.data.data.total || 0
    } else {
      ElMessage.error(res.data.msg || '回收站加载失败')
    }
  } catch (e) {
    console.error(e)
    ElMessage.error('回收站加载失败')
  } finally {
    recycleLoading.value = false
  }
}

const openRecycleBin = () => {
  recycleVisible.value = true
  recyclePage.value = 1
  recyclePageSize.value = 10
  fetchRecycleUsers()
}

const handleRecycleSearch = () => {
  recyclePage.value = 1
  fetchRecycleUsers()
}

const resetRecycleSearch = () => {
  recycleKeyword.value = ''
  recyclePage.value = 1
  fetchRecycleUsers()
}

const handleRecyclePageSizeChange = () => {
  recyclePage.value = 1
  fetchRecycleUsers()
}

const restoreUser = async (row) => {
  try {
    await ElMessageBox.confirm(
      `确定恢复用户“${row.nickname || row.id}”吗？恢复后该用户可以重新登录，原设备绑定也会恢复生效。`,
      '恢复用户',
      {
        confirmButtonText: '确认恢复',
        cancelButtonText: '取消',
        type: 'info'
      }
    )

    restoringUserId.value = row.id
    const res = await axios.post(`/api/user/restore/${row.id}`)
    if (res.data.code !== 200) {
      ElMessage.error(res.data.msg || '恢复失败')
      return
    }

    if (recycleList.value.length === 1 && recyclePage.value > 1) {
      recyclePage.value--
    }
    ElMessage.success(res.data.data || '用户已恢复')
    await Promise.all([fetchRecycleUsers(), fetchUsers(), fetchStats()])
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') {
      console.error(e)
      ElMessage.error('恢复失败')
    }
  } finally {
    restoringUserId.value = null
  }
}

const clearRecycleBin = async () => {
  try {
    await ElMessageBox.prompt(
      `清空后将永久删除回收站中的 ${recycleTotal.value} 位用户及其提现、收益等关联记录，且无法恢复。请输入“永久删除”确认。`,
      '永久清空回收站',
      {
        confirmButtonText: '永久清空',
        cancelButtonText: '取消',
        type: 'error',
        inputPlaceholder: '请输入：永久删除',
        inputValidator: (value) => value === '永久删除' || '请输入“永久删除”'
      }
    )

    recycleClearing.value = true
    const res = await axios.delete('/api/user/recycle-bin')
    if (res.data.code !== 200) {
      ElMessage.error(res.data.msg || '清空失败')
      return
    }

    recyclePage.value = 1
    ElMessage.success(res.data.data || '回收站已清空')
    await Promise.all([fetchRecycleUsers(), fetchUsers(), fetchStats()])
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') {
      console.error(e)
      ElMessage.error('清空失败')
    }
  } finally {
    recycleClearing.value = false
  }
}

const viewUser = async (row) => {
  detailVisible.value = true
  sensitiveVisible.value = false
  detailLoading.value = true
  currentUser.value = row // 先显示基本信息
  devicePage.value = 1
  devicePageSize.value = 10
  selectedDevices.value = []
  withdrawDetails.value = []
  withdrawDetailPage.value = 1
  withdrawDetailPageSize.value = 10
  withdrawDetailTotal.value = 0
  fetchWithdrawDetails()

  try {
    const res = await axios.get(`/api/user/detail/${row.id}`)
    if (res.data.code === 200 && currentUser.value?.id === row.id) {
      currentUser.value = res.data.data
    }
  } catch (e) {
    console.error(e)
  } finally {
    if (currentUser.value?.id === row.id) {
      detailLoading.value = false
    }
  }
}

const editUser = (row) => {
  editForm.id = row.id
  editForm.nickname = row.nickname || ''
  editForm.phone = row.phone || ''
  editForm.balance = row.balance || 0
  editForm.quota = row.quota ?? 0
  editForm.inviterId = row.inviterId || null
  editForm.level = row.level || 0
  editForm.levelManual = row.levelManual || false
  editForm.userType = row.userType || 'personal'
  editForm.remark = row.remark || ''

  // 初始化邀请人下拉框选项
  if (row.inviterId) {
    inviterSearchOptions.value = [{
      id: row.inviterId,
      nickname: row.inviterNickname || '未知',
      avatarUrl: row.inviterAvatarUrl
    }]
  } else {
    inviterSearchOptions.value = []
  }

  editVisible.value = true
}

const rechargeQuota = (row) => {
  rechargeForm.userId = row.id
  rechargeForm.currentQuota = row.quota ?? 0
  rechargeForm.amount = 100
  rechargeVisible.value = true
}

const openTransferDialog = (row) => {
  transferForm.sourceUserId = String(row.id)
  transferForm.sourceLabel = `${row.nickname || '微信用户'} / ${row.phone || '未绑定手机'}`
  transferForm.targetUserId = ''
  transferVisible.value = true
}

const submitTransfer = async () => {
  const sourceUserId = Number(transferForm.sourceUserId)
  const targetUserId = Number(transferForm.targetUserId)
  if (!sourceUserId || !targetUserId) {
    ElMessage.warning('请填写正确的来源账号和目标账号ID')
    return
  }
  if (sourceUserId === targetUserId) {
    ElMessage.warning('来源账号和目标账号不能相同')
    return
  }

  try {
    await ElMessageBox.confirm(
      `确定把实名提现账号从用户 ${sourceUserId} 迁移到用户 ${targetUserId} 吗？迁移后来源账号将被禁提。`,
      '确认实名迁移',
      {
        type: 'warning',
        confirmButtonText: '确认迁移',
        cancelButtonText: '取消'
      }
    )

    transferSaving.value = true
    const res = await axios.post('/api/user/transfer-contract', {
      sourceUserId,
      targetUserId
    })
    if (res.data.code === 200) {
      ElMessage.success(res.data.data || '实名迁移成功')
      transferVisible.value = false
      fetchUsers()
      fetchStats()
      if (currentUser.value?.id === sourceUserId || currentUser.value?.id === targetUserId) {
        const detailRes = await axios.get(`/api/user/detail/${currentUser.value.id}`)
        if (detailRes.data.code === 200) currentUser.value = detailRes.data.data
      }
    } else {
      ElMessage.error(res.data.msg || '实名迁移失败')
    }
  } catch (e) {
    if (e !== 'cancel') {
      console.error(e)
      ElMessage.error('实名迁移失败')
    }
  } finally {
    transferSaving.value = false
  }
}

const saveUser = async () => {
  saving.value = true
  try {
    // 1. 先保存基本信息（资产字段不在资料编辑中保存，避免覆盖余额）
    const updateRes = await axios.post('/api/user/update', {
      id: editForm.id,
      nickname: editForm.nickname,
      phone: editForm.phone,
      userType: editForm.userType || 'personal',
      remark: editForm.remark || ''
    })

    if (updateRes.data.code !== 200) {
      ElMessage.error(updateRes.data.msg || '保存失败')
      return
    }

    // 2. 单独保存账户资产，后端会按余额同步算力配额
    const assetRes = await axios.post('/api/user/updateAsset', {
      userId: editForm.id,
      balance: editForm.balance
    })

    if (assetRes.data.code !== 200) {
      ElMessage.error(assetRes.data.msg || '资产保存失败')
      return
    }

    // 3. 单独处理等级更新（支持手动解锁自动升级）
    const levelRes = await axios.post('/api/user/updateLevel', {
      userId: editForm.id,
      level: editForm.level,
      levelManual: editForm.levelManual
    })

    if (levelRes.data.code !== 200) {
      ElMessage.warning('基本信息已保存，但等级更新失败: ' + levelRes.data.msg)
    }

    // 4. 单独处理邀请人更新
    const inviterRes = await axios.post('/api/user/updateInviter', {
      userId: editForm.id,
      inviterId: editForm.inviterId
    })

    if (inviterRes.data.code === 200) {
      ElMessage.success('保存成功')
      editVisible.value = false
      fetchUsers()
      fetchStats()
    } else {
      ElMessage.warning('基本信息已保存，但邀请人更新失败: ' + inviterRes.data.msg)
    }
  } catch (e) {
    ElMessage.error('网络错误')
  } finally {
    saving.value = false
  }
}

const getLevelText = (level) => {
  if (!level) return '普通'
  return inviteLevelOptions.value.find(item => item.index === level)?.name || `等级 ${level}`
}

const getLevelTag = (level) => {
  const map = {
    0: 'info',
    1: 'success',
    2: 'warning',
    3: 'danger',
    4: 'primary'
  }
  return map[level] || 'info'
}

const getContractType = (status) => {
  const map = {
    0: 'info',
    1: 'success',
    2: 'danger',
    3: 'warning'
  }
  return map[status] || 'info'
}

const getUserTypeText = (type) => {
  return type === 'company' ? '公司用户' : '个人用户'
}

const getUserTypeTag = (type) => {
  return type === 'company' ? 'warning' : 'info'
}

const confirmRecharge = async () => {
  saving.value = true
  try {
    const res = await axios.post('/api/user/recharge-quota', {
      userId: rechargeForm.userId,
      amount: rechargeForm.amount
    })
    if (res.data.code === 200) {
      ElMessage.success(`成功充值 ${rechargeForm.amount} 聚芯算力值`)
      rechargeVisible.value = false
      fetchUsers()
      fetchStats()
    } else {
      ElMessage.error(res.data.msg || '充值失败')
    }
  } catch (e) {
    ElMessage.error('网络错误')
  } finally {
    saving.value = false
  }
}

const handleSelectionChange = (val) => {
  selectedDevices.value = val
}

const handleBatchUnbind = async () => {
  if (selectedDevices.value.length === 0) return

  try {
    await ElMessageBox.confirm(
      `确定要解绑选中的 ${selectedDevices.value.length} 台设备吗？`,
      '提示',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning',
      }
    )

    // 乐观更新 UI 或开启局部 loading
    const ids = selectedDevices.value.map(d => d.id)

    const res = await axios.post('/api/device/batch-unbind', { ids })
    if (res.data.code === 200) {
      ElMessage.success('批量解绑成功')

      // 刷新详情数据
      if (currentUser.value?.id) {
        const detailRes = await axios.get(`/api/user/detail/${currentUser.value.id}`)
        if (detailRes.data.code === 200) {
          currentUser.value = detailRes.data.data
          const deviceTotal = currentUser.value.devices?.length || 0
          const maxPage = Math.max(1, Math.ceil(deviceTotal / devicePageSize.value))
          devicePage.value = Math.min(devicePage.value, maxPage)
        }
      }
      // 刷新列表统计
      fetchUsers()
      fetchStats()
      // 清空选择
      selectedDevices.value = []
    } else {
      ElMessage.error(res.data.msg || '解绑失败')
    }
  } catch (e) {
    if (e !== 'cancel') {
      console.error(e)
      ElMessage.error('操作失败')
    }
  }
}

const toggleWithdraw = async (row) => {
  const newStatus = !row.withdrawDisabled
  const action = newStatus ? '禁止' : '解禁'

  try {
    await ElMessageBox.confirm(
      `确定要${action}用户 "${row.nickname || row.id}" 的提现权限吗？`,
      '提示',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning',
      }
    )

    const res = await axios.post('/api/user/toggleWithdraw', {
      userId: row.id,
      disabled: newStatus
    })

    if (res.data.code === 200) {
      ElMessage.success(res.data.data || `已${action}该用户提现`)
      row.withdrawDisabled = newStatus
    } else {
      ElMessage.error(res.data.msg || '操作失败')
    }
  } catch (e) {
    if (e !== 'cancel') {
      console.error(e)
      ElMessage.error('操作失败')
    }
  }
}

onMounted(() => {
  loadInviteLevelOptions()
  fetchStats()
  fetchUsers()
})
</script>

<style scoped>
.user-list-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* 统计卡片 - 渐变风格 */
.stat-cards {
  margin-bottom: 8px;
}

.stat-card {
  background: linear-gradient(135deg, #fff 0%, #f8fafc 100%);
  border-radius: 16px;
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
  border: 1px solid #e2e8f0;
  transition: all 0.3s ease;
}

.stat-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.12);
}

.stat-card.users {
  background: linear-gradient(135deg, #dbeafe 0%, #bfdbfe 100%);
  border-color: #93c5fd;
}

.stat-card.active {
  background: linear-gradient(135deg, #d1fae5 0%, #a7f3d0 100%);
  border-color: #6ee7b7;
}

.stat-card.balance {
  background: linear-gradient(135deg, #fef3c7 0%, #fde68a 100%);
  border-color: #fcd34d;
}

.stat-card.quota {
  background: linear-gradient(135deg, #f3e8ff 0%, #e9d5ff 100%);
  border-color: #c4b5fd;
}

.stat-icon {
  font-size: 36px;
}

.stat-value {
  font-size: 32px;
  font-weight: 800;
  color: #1e293b;
}

.stat-label {
  font-size: 14px;
  color: #475569;
  font-weight: 500;
}

/* 搜索栏和表格卡片 */
.search-card {
  border-radius: 12px;
  background: linear-gradient(135deg, #f8fafc 0%, #f1f5f9 100%);
  border: 1px solid #e2e8f0;
}

.table-card {
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1);
}

.user-actions {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-wrap: nowrap;
}

:deep(.user-actions .el-button + .el-button) {
  margin-left: 0;
}

.table-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.table-toolbar-title {
  color: #1e293b;
  font-size: 16px;
  font-weight: 700;
}

.table-toolbar-tip {
  margin-top: 4px;
  color: #94a3b8;
  font-size: 12px;
}

.recycle-bin {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.recycle-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.recycle-search {
  display: flex;
  align-items: center;
  gap: 8px;
  width: min(540px, 100%);
}

.recycle-pagination {
  margin-top: 0;
  padding-bottom: 0;
}

.total-count {
  color: #64748b;
  font-size: 14px;
  background: rgba(124, 58, 237, 0.08);
  padding: 6px 12px;
  border-radius: 8px;
}

/* 用户信息样式 */
.user-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-detail {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.nickname {
  font-weight: 700;
  color: #1e293b;
  font-size: 14px;
}

.phone {
  font-size: 12px;
  color: #059669;
  font-weight: 500;
}

.openid {
  font-size: 11px;
  color: #94a3b8;
}

/* 邀请人信息卡片 */
.user-info-cell {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 8px;
  background: linear-gradient(135deg, #faf5ff 0%, #f3e8ff 100%);
  border-radius: 6px;
  border: 1px solid #e9d5ff;
}

.user-name {
  font-size: 13px;
  font-weight: 600;
  color: #1e293b;
}

.user-id {
  font-size: 11px;
  color: #7c3aed;
  font-weight: 500;
}

/* 数值样式 */
.balance {
  color: #059669;
  font-weight: 700;
  font-size: 15px;
}

.quota-value {
  color: #d97706;
  font-weight: 700;
  font-size: 15px;
}

.quota-value.success {
  color: #059669;
}

.create-time, .time-text {
  font-size: 12px;
  color: #64748b;
}

/* 分页 */
.pagination-wrapper {
  margin-top: 20px;
  padding: 16px;
  display: flex;
  justify-content: flex-end;
  background: #f8fafc;
  border-top: 1px solid #e2e8f0;
}

/* 表格头部样式 */
:deep(.el-table__header-wrapper th) {
  background: linear-gradient(135deg, #f5f3ff 0%, #ede9fe 100%) !important;
  color: #4c1d95 !important;
  font-weight: 600;
  font-size: 13px;
  border-bottom: 2px solid #a78bfa !important;
}

:deep(.el-table__header-wrapper .cell) {
  color: #4c1d95 !important;
}

/* 表格行hover */
:deep(.el-table__row) {
  transition: all 0.2s ease;
}

:deep(.el-table__row:hover) {
  background: linear-gradient(135deg, #faf5ff 0%, #f5f3ff 100%) !important;
}

/* 详情弹窗样式 */
.user-detail-dialog {
  padding: 0 10px;
}

:deep(.user-detail-modal) {
  max-width: 1100px;
}

:deep(.user-detail-modal .el-dialog__body) {
  max-height: calc(90vh - 80px);
  overflow-y: auto;
}

.detail-header {
  display: flex;
  align-items: center;
  gap: 24px;
  padding: 20px;
  background: linear-gradient(135deg, #f5f3ff 0%, #ede9fe 100%);
  border-radius: 12px;
  margin-bottom: 20px;
}

.header-info {
  flex: 1;
}

.form-tip {
  font-size: 12px;
  color: #94a3b8;
  margin-top: 4px;
}

.header-name {
  font-size: 22px;
  font-weight: 800;
  color: #1e293b;
}

.header-id {
  font-size: 13px;
  color: #64748b;
  margin-top: 6px;
}

.header-stats {
  display: flex;
  gap: 28px;
}

.stat-item {
  text-align: center;
  background: rgba(255, 255, 255, 0.8);
  padding: 12px 20px;
  border-radius: 10px;
}

.stat-num {
  font-size: 20px;
  font-weight: 800;
  color: #1e293b;
}

.stat-text {
  font-size: 12px;
  color: #64748b;
  margin-top: 4px;
}

.detail-section {
  margin-top: 24px;
}

.detail-pagination {
  display: flex;
  justify-content: flex-end;
  padding-top: 14px;
  overflow-x: auto;
}

.section-title {
  font-size: 16px;
  font-weight: 700;
  color: #1e293b;
  margin-bottom: 14px;
}

.empty-tip {
  color: #94a3b8;
  font-size: 14px;
  text-align: center;
  padding: 24px;
  background: linear-gradient(135deg, #f8fafc 0%, #f1f5f9 100%);
  border-radius: 10px;
  border: 1px dashed #cbd5e1;
}

.sn-code {
  font-family: 'SF Mono', 'Monaco', monospace;
  font-size: 12px;
  color: #7c3aed;
  background: linear-gradient(135deg, #f5f3ff 0%, #ede9fe 100%);
  padding: 3px 8px;
  border-radius: 4px;
  border: 1px solid #c4b5fd;
}

.device-earnings {
  color: #059669;
  font-weight: 700;
}

.withdraw-amount {
  color: #059669;
  font-weight: 700;
}

.withdraw-account {
  font-family: 'SF Mono', 'Monaco', monospace;
  font-size: 12px;
  word-break: break-all;
}

.withdraw-remark {
  display: block;
  max-width: 150px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  cursor: help;
}

code {
  font-family: 'SF Mono', 'Monaco', monospace;
  font-size: 12px;
  color: #7c3aed;
  background: linear-gradient(135deg, #f5f3ff 0%, #ede9fe 100%);
  padding: 3px 8px;
  border-radius: 4px;
}

.table-id-code {
  display: inline-block;
  white-space: nowrap;
  word-break: keep-all;
}

.user-info-cell {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.user-info-cell .user-detail {
  min-width: 0;
}

.user-info-cell .user-name {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.user-info-cell .user-id {
  white-space: nowrap;
  word-break: keep-all;
}

/* 按钮样式 */
:deep(.el-button--success) {
  background: linear-gradient(135deg, #10b981 0%, #059669 100%);
  border: none;
}

:deep(.el-button--warning) {
  background: linear-gradient(135deg, #f59e0b 0%, #d97706 100%);
  border: none;
}

/* 标签样式增强 */
:deep(.el-tag--success) {
  background: linear-gradient(135deg, #d1fae5 0%, #a7f3d0 100%);
  border-color: #6ee7b7;
  color: #047857;
  font-weight: 600;
}

:deep(.el-tag--warning) {
  background: linear-gradient(135deg, #fef3c7 0%, #fde68a 100%);
  border-color: #fcd34d;
  color: #92400e;
  font-weight: 600;
}

:deep(.el-tag--info) {
  background: linear-gradient(135deg, #f1f5f9 0%, #e2e8f0 100%);
  border-color: #cbd5e1;
  color: #64748b;
}

:deep(.el-tag--danger) {
  background: linear-gradient(135deg, #fee2e2 0%, #fecaca 100%);
  border-color: #fca5a5;
  color: #b91c1c;
  font-weight: 600;
}

:deep(.el-tag--primary) {
  background: linear-gradient(135deg, #dbeafe 0%, #bfdbfe 100%);
  border-color: #93c5fd;
  color: #1d4ed8;
  font-weight: 600;
}
/* ========== 编辑用户弹窗样式 ========== */
.user-edit-dialog :deep(.el-dialog__body) {
  padding: 10px 24px 24px;
}

.edit-container {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.form-section {
  position: relative;
  padding: 24px 16px 16px;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  margin-bottom: 24px;
  background: #fff;
}

.section-badge {
  position: absolute;
  top: -12px;
  left: 16px;
  background: linear-gradient(135deg, #6366f1 0%, #4f46e5 100%);
  color: #fff;
  padding: 2px 12px;
  border-radius: 20px;
  font-size: 12px;
  font-weight: 600;
  box-shadow: 0 2px 4px rgba(79, 70, 229, 0.3);
}

.section-badge.account {
  background: linear-gradient(135deg, #10b981 0%, #059669 100%);
  box-shadow: 0 2px 4px rgba(16, 185, 129, 0.3);
}

.section-badge.level {
  background: linear-gradient(135deg, #f59e0b 0%, #d97706 100%);
  box-shadow: 0 2px 4px rgba(245, 158, 11, 0.3);
}

.section-badge.invite {
  background: linear-gradient(135deg, #ec4899 0%, #db2777 100%);
  box-shadow: 0 2px 4px rgba(236, 72, 153, 0.3);
}

.unit-text {
  position: absolute;
  right: -25px;
  top: 0;
  color: #94a3b8;
  font-size: 13px;
}

.sync-tip {
  margin-top: 8px;
  text-align: center;
  color: #6366f1;
  background: #f5f3ff;
  padding: 4px;
  border-radius: 4px;
  font-size: 12px;
}

.form-tip.warning {
  color: #ef4444;
  background: #fef2f2;
  padding: 6px 10px;
  border-radius: 6px;
  margin-top: 8px;
  line-height: 1.4;
  font-weight: 500;
}

.switch-wrapper {
  background: #f8fafc;
  padding: 8px 16px;
  border-radius: 8px;
  display: inline-block;
}

.inviter-option {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 4px 0;
}

.id-tag {
  margin-left: auto;
  font-size: 11px;
  background: #f1f5f9;
  color: #64748b;
  padding: 1px 6px;
  border-radius: 4px;
}

.dialog-footer {
  display: flex;
  justify-content: center;
  gap: 12px;
  padding-top: 10px;
}

/* 底部缓冲 Padding */
.user-list-page {
  padding-bottom: 80px;
}
</style>
