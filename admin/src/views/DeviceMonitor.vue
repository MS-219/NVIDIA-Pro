<template>
  <div class="monitor-page">
    <!-- 核心统计栏 -->
    <el-row :gutter="20" class="stat-grid">
      <el-col :xs="24" :sm="8" :lg="8">
        <div class="pro-card primary">
          <div class="card-label">活跃设备数</div>
          <div class="card-val">{{ stats.onlineCount }} <small>台</small></div>
          <el-icon class="card-icon"><Connection /></el-icon>
        </div>
      </el-col>
      <el-col :xs="24" :sm="8" :lg="8">
        <div class="pro-card warning">
          <div class="card-label">集群平均负载 (GPU)</div>
          <div class="card-val">{{ stats.avgGpuLoad || 60 }}<small>%</small></div>
          <el-icon class="card-icon"><Histogram /></el-icon>
        </div>
      </el-col>
      <el-col :xs="24" :sm="8" :lg="8">
        <div class="pro-card indigo">
          <div class="card-label">内存使用水位</div>
          <div class="card-val">{{ stats.avgMemLoad || 60 }}<small>%</small></div>
          <el-icon class="card-icon"><Monitor /></el-icon>
        </div>
      </el-col>
    </el-row>

    <!-- 过滤器 -->
    <div class="filter-bar">
      <el-input 
        v-model="searchQuery" 
        placeholder="搜索设备编号 (SN)..."
        style="width: 260px" 
        prefix-icon="Search"
        @keyup.enter="fetchDevices"
      />
      <el-cascader
        v-model="locationFilter"
        :options="locationOptions"
        :props="{ checkStrictly: true, expandTrigger: 'hover' }"
        placeholder="选择地区"
        clearable
        style="width: 220px"
        @change="onLocationChange"
      />
      <el-select v-model="deviceTypeFilter" placeholder="设备类型" style="width: 150px" @change="onDeviceTypeChange">
        <el-option label="Orin 设备" :value="2" />
        <el-option label="挂靠设备" :value="1" />
        <el-option label="全部设备" :value="0" />
      </el-select>
      <el-radio-group v-model="statusFilter" @change="fetchDevices">
        <el-radio-button value="">全部设备</el-radio-button>
        <el-radio-button :value="1">在线</el-radio-button>
        <el-radio-button :value="0">离线</el-radio-button>
      </el-radio-group>
      <el-button type="primary" @click="fetchDevices">刷新设备数据</el-button>
      <el-button type="warning" plain :icon="Document" :loading="csvExportLoading" @click="exportDeviceCsv">
        导出设备 CSV
      </el-button>
      <el-button type="info" plain :icon="Clock" @click="openGlobalOfflineRecords">
        全局离线记录
      </el-button>
      <el-button class="affiliate-create-button" type="success" :icon="Plus" @click="openAffiliateDialog">
        新增挂靠设备
      </el-button>
      <el-button type="info" plain :icon="Download" :loading="qrExportLoading" @click="openQrExportDialog">
        导出设备二维码
      </el-button>
    </div>

    <!-- 数据表管理 -->
    <div class="table-container">
      <div v-if="selectedDevices.length > 0" class="batch-toolbar">
        <span>已选择 {{ selectedDevices.length }} 台设备</span>
        <el-button
          type="warning"
          size="small"
          :disabled="selectedBoundDevices.length === 0"
          :loading="batchUnbinding"
          @click="batchUnbindDevices"
        >
          批量解绑（{{ selectedBoundDevices.length }}）
        </el-button>
        <el-button
          type="danger"
          size="small"
          :disabled="selectedDeletableDevices.length === 0"
          :loading="batchDeleting"
          @click="batchDeleteDevices"
        >
          批量删除（{{ selectedDeletableDevices.length }}）
        </el-button>
      </div>
      <el-table :data="devices" border stripe v-loading="loading" @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="48" fixed="left" />
        <el-table-column label="运行状态" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" effect="plain">
              {{ row.status === 1 ? '在线' : '离线' }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="绑定用户" width="190" align="center">
          <template #default="{ row }">
            <div v-if="row.userId != null" class="bound-user">
              <el-avatar :size="34" :src="row.avatarUrl || ''" class="bound-user-avatar">
                {{ (row.nickname || '用户').charAt(0) }}
              </el-avatar>
              <div class="bound-user-meta">
                <span class="bound-user-name">{{ row.nickname || '已绑定用户' }}</span>
                <span class="bound-user-id">ID：{{ row.userId }}</span>
              </div>
            </div>
            <el-tag v-else type="info" effect="plain">未绑定</el-tag>
          </template>
        </el-table-column>
        
        <el-table-column prop="bindCode" label="绑定码" width="220">
          <template #default="{ row }">
            <div>
              <b class="device-bind-code">{{ row.bindCode || '未生成' }}</b>
              <div style="margin-top: 4px; color: #909399; font-size: 12px;">
                {{ row.type === 1 ? '挂靠设备' : `版本 ${row.agentVersion || '待上报'}` }}
              </div>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="位置/运营商" min-width="200">
          <template #default="{ row }">
            <div>
              <el-icon><Location /></el-icon> {{ row.location || '未知区域' }}
              <div class="carrier-info">{{ row.carrier || '未知网络' }}</div>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="Jetson 运行环境" min-width="220">
          <template #default="{ row }">
            <div style="font-size: 12px; color: #666;">
              <div>{{ row.deviceModel || 'Orin 型号待上报' }}</div>
              <div class="runtime-version">{{ row.architecture || 'aarch64' }} · {{ row.l4tVersion || 'L4T 待上报' }} · CUDA {{ row.cudaVersion || '-' }}</div>
              <el-tag size="small" type="info" effect="plain">{{ row.runtimeModel || '未执行任务' }}</el-tag>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="环境状况" width="180">
          <template #default="{ row }">
            <div class="env-cell">
              <el-tag size="small" :type="envTagType(row.envStatus)" effect="plain">
                {{ envStatusText(row.envStatus) }}
              </el-tag>
              <div class="env-summary">{{ row.envSummary || '未检查' }}</div>
              <div v-if="row.envMissingItems" class="env-missing">{{ row.envMissingItems }}</div>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="系统负载" width="160">
          <template #default="{ row }">
            <div style="font-size: 11px;">
              <div style="display: flex; justify-content: space-between; margin-bottom: 2px;">
                <span>CPU</span>
                <span :style="{ color: parseFloat(row.cpuUsage) > 80 ? '#f56c6c' : '#67c23a' }">{{ row.cpuUsage || '0' }}%</span>
              </div>
              <el-progress :percentage="parseFloat(row.cpuUsage) || 0" :show-text="false" :stroke-width="4" />
              
              <div style="display: flex; justify-content: space-between; margin-top: 6px; margin-bottom: 2px;">
                <span>RAM</span>
                <span :style="{ color: parseFloat(row.memoryUsage) > 80 ? '#f56c6c' : '#1890ff' }">{{ row.memoryUsage || '0' }}%</span>
              </div>
              <el-progress :percentage="parseFloat(row.memoryUsage) || 0" :show-text="false" :stroke-width="4" color="#1890ff" />

              <div style="display: flex; justify-content: space-between; margin-top: 6px; margin-bottom: 2px;">
                <span>GPU</span>
                <span>{{ row.gpuUsage || '0' }}%</span>
              </div>
              <el-progress :percentage="parseFloat(row.gpuUsage) || 0" :show-text="false" :stroke-width="4" color="#659f00" />
            </div>
          </template>
        </el-table-column>

        <el-table-column prop="lastHeartbeatTime" label="最后心跳" width="180">
          <template #default="{ row }">
            <span style="color: #666; font-size: 12px;">{{ formatTime(row.lastHeartbeatTime) }}</span>
          </template>
        </el-table-column>

        <el-table-column label="设备操作" width="520" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" size="small" @click="viewDetail(row)">管理</el-button>
            <el-button type="primary" size="small" plain :icon="Edit" @click="openEditDialog(row)">编辑</el-button>
            <el-button type="warning" size="small" plain @click="openOfflineRecords(row)">离线记录</el-button>
            <el-button 
              type="success" 
              size="small" 
              plain 
              @click="openTerminal(row)"
              :disabled="row.status !== 1"
            >终端</el-button>
            <el-button
              v-if="row.userId == null"
              type="success"
              size="small"
              plain
              :icon="Link"
              :loading="bindingId === row.id"
              @click="openBindDialog(row)"
            >绑定</el-button>
            <el-button
              v-if="row.userId != null"
              type="danger"
              size="small"
              plain
              :icon="Unlock"
              :loading="unbindingId === row.id"
              @click="unbindDevice(row)"
            >解绑</el-button>
            <el-button
              v-if="row.userId == null && row.merchantId == null"
              type="danger"
              size="small"
              plain
              :icon="Delete"
              :loading="deletingId === row.id"
              @click="deleteDevice(row)"
            >删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <el-pagination
          v-model:current-page="currentPage"
          :page-size="pageSize"
          :total="total"
          layout="total, prev, pager, next"
          @current-change="fetchDevices"
        />
      </div>
    </div>

    <el-dialog
      v-model="detailVisible"
      title="设备详情"
      width="860px"
      destroy-on-close
      @closed="disposeEarningsChart"
    >
      <div v-loading="detailLoading" class="detail-grid">
        <div class="detail-item">
          <span class="detail-label">SN 序列号</span>
          <span class="detail-value mono">{{ detailData.sn || '-' }}</span>
        </div>
        <div class="detail-item">
          <span class="detail-label">运行状态</span>
          <el-tag :type="detailData.status === 1 ? 'success' : 'danger'" effect="plain">
            {{ detailData.status === 1 ? '在线' : '离线' }}
          </el-tag>
        </div>
        <div class="detail-item">
          <span class="detail-label">绑定用户</span>
          <div v-if="detailData.userId != null" class="bound-user bound-user-detail">
            <el-avatar :size="34" :src="detailData.avatarUrl || ''" class="bound-user-avatar">
              {{ (detailData.nickname || '用户').charAt(0) }}
            </el-avatar>
            <div class="bound-user-meta">
              <span class="bound-user-name">{{ detailData.nickname || '已绑定用户' }}</span>
              <span class="bound-user-id">ID：{{ detailData.userId }}</span>
            </div>
          </div>
          <span v-else class="detail-value">未绑定</span>
        </div>
        <div class="detail-item">
          <span class="detail-label">设备名称</span>
          <span class="detail-value">{{ detailData.name || '未命名' }}</span>
        </div>
        <div class="detail-item">
          <span class="detail-label">设备类型</span>
          <span class="detail-value">{{ formatDeviceType(detailData.type) }}</span>
        </div>
        <div class="detail-item">
          <span class="detail-label">Agent 版本</span>
          <span class="detail-value mono">{{ detailData.agentVersion || '待上报' }}</span>
        </div>
        <div class="detail-item">
          <span class="detail-label">远程环境</span>
          <span class="detail-value">
            <el-tag size="small" :type="envTagType(detailData.envStatus)" effect="plain">
              {{ envStatusText(detailData.envStatus) }}
            </el-tag>
            <span class="env-detail">{{ detailData.envSummary || '未检查' }}</span>
          </span>
        </div>
        <div class="detail-item full" v-if="detailData.envMissingItems">
          <span class="detail-label">缺失环境项</span>
          <span class="detail-value">{{ detailData.envMissingItems }}</span>
        </div>
        <div class="detail-item">
          <span class="detail-label">位置</span>
          <span class="detail-value">{{ detailData.location || '未知区域' }}</span>
        </div>
        <div class="detail-item">
          <span class="detail-label">运营商</span>
          <span class="detail-value">{{ detailData.carrier || '未知网络' }}</span>
        </div>
        <div class="detail-item">
          <span class="detail-label">IP 地址</span>
          <span class="detail-value mono">{{ detailData.ip || '-' }}</span>
        </div>
        <div class="detail-item">
          <span class="detail-label">绑定码</span>
          <span class="detail-value mono">{{ detailData.bindCode || '-' }}</span>
        </div>
        <div class="detail-item">
          <span class="detail-label">CPU 负载</span>
          <span class="detail-value">{{ detailData.cpuUsage || '0' }}%</span>
        </div>
        <div class="detail-item">
          <span class="detail-label">内存负载</span>
          <span class="detail-value">{{ detailData.memoryUsage || '0' }}%</span>
        </div>
        <div class="detail-item">
          <span class="detail-label">GPU 负载</span>
          <span class="detail-value">{{ detailData.gpuUsage || '0' }}%</span>
        </div>
        <div class="detail-item">
          <span class="detail-label">最后心跳</span>
          <span class="detail-value">{{ formatTime(detailData.lastHeartbeatTime) }}</span>
        </div>
        <div class="detail-item full">
          <span class="detail-label">创建时间</span>
          <span class="detail-value">{{ formatTime(detailData.createTime) }}</span>
        </div>
      </div>
      <div class="earnings-chart-panel">
        <div class="earnings-chart-head">
          <div>
            <strong>设备收益趋势</strong>
            <span>最近 7 天</span>
          </div>
          <span class="earnings-chart-total">合计 ¥{{ earningsTotal }}</span>
        </div>
        <div v-loading="earningsChartLoading" ref="earningsChartRef" class="earnings-chart"></div>
      </div>
    </el-dialog>

    <el-dialog v-model="editVisible" title="编辑设备" width="560px" destroy-on-close>
      <el-form :model="editForm" label-position="top">
        <div class="edit-form-grid">
          <el-form-item label="设备名称">
            <el-input v-model="editForm.name" maxlength="100" placeholder="请输入设备名称或备注" />
          </el-form-item>
          <el-form-item label="设备类型">
            <el-select v-model="editForm.type" style="width: 100%">
              <el-option label="实体设备" :value="0" />
              <el-option label="挂靠设备" :value="1" />
              <el-option label="Orin 设备" :value="2" />
            </el-select>
          </el-form-item>
          <el-form-item label="所在位置">
            <el-input v-model="editForm.location" maxlength="100" placeholder="例如：山东省枣庄市" />
          </el-form-item>
          <el-form-item label="运营商">
            <el-input v-model="editForm.carrier" maxlength="50" placeholder="例如：中国移动" />
          </el-form-item>
          <el-form-item label="算力值" class="span-2">
            <el-input-number
              v-model="editForm.hashrate"
              :min="0"
              :max="999999999"
              :precision="0"
              controls-position="right"
              style="width: 100%"
            />
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="editSaving" @click="saveDeviceEdit">保存修改</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="offlineVisible"
      title="设备离线记录"
      width="760px"
      destroy-on-close
    >
      <div class="offline-dialog-head">
        <span class="offline-dialog-sn">{{ offlineDevice.sn || '-' }}</span>
        <span>最近 50 次离线检测结果</span>
      </div>
      <el-table
        v-if="offlineLoading || offlineRecords.length > 0"
        :data="offlineRecords"
        v-loading="offlineLoading"
        border
        stripe
      >
        <el-table-column prop="offlineTime" label="离线时间" min-width="180">
          <template #default="{ row }">{{ formatTime(row.offlineTime) }}</template>
        </el-table-column>
        <el-table-column prop="lastHeartbeatTime" label="最后心跳" min-width="180">
          <template #default="{ row }">{{ formatTime(row.lastHeartbeatTime) }}</template>
        </el-table-column>
        <el-table-column prop="reason" label="离线原因" min-width="220">
          <template #default="{ row }">{{ row.reason || '心跳超时' }}</template>
        </el-table-column>
      </el-table>
      <el-empty v-else description="暂无离线记录" />
    </el-dialog>

    <el-dialog v-model="globalOfflineVisible" title="全局设备离线记录" width="980px" destroy-on-close>
      <div class="global-offline-toolbar">
        <el-input
          v-model="globalOfflineSearch"
          clearable
          placeholder="搜索绑定码或 SN"
          style="width: 280px"
          @keyup.enter="searchGlobalOfflineRecords"
          @clear="searchGlobalOfflineRecords"
        />
        <el-button type="primary" :icon="Search" @click="searchGlobalOfflineRecords">查询</el-button>
      </div>
      <el-table :data="globalOfflineRecords" v-loading="globalOfflineLoading" border stripe>
        <el-table-column prop="bindCode" label="绑定码" width="180">
          <template #default="{ row }">{{ row.bindCode || '-' }}</template>
        </el-table-column>
        <el-table-column prop="sn" label="SN 序列号" min-width="220" show-overflow-tooltip />
        <el-table-column prop="offlineTime" label="离线时间" width="180">
          <template #default="{ row }">{{ formatTime(row.offlineTime) }}</template>
        </el-table-column>
        <el-table-column prop="lastHeartbeatTime" label="最后心跳" width="180">
          <template #default="{ row }">{{ formatTime(row.lastHeartbeatTime) }}</template>
        </el-table-column>
        <el-table-column prop="reason" label="离线原因" min-width="180">
          <template #default="{ row }">{{ row.reason || '心跳超时' }}</template>
        </el-table-column>
      </el-table>
      <div class="pagination">
        <el-pagination
          v-model:current-page="globalOfflinePage"
          :page-size="globalOfflinePageSize"
          :total="globalOfflineTotal"
          layout="total, prev, pager, next"
          @current-change="fetchGlobalOfflineRecords"
        />
      </div>
    </el-dialog>

    <el-dialog v-model="bindVisible" title="绑定设备" width="520px" destroy-on-close>
      <el-form :model="bindForm" label-position="top">
        <el-form-item label="设备">
          <div class="bind-device-summary">
            <strong>{{ bindForm.sn || '-' }}</strong>
            <span>未绑定设备</span>
          </div>
        </el-form-item>
        <el-form-item label="选择用户或输入用户 ID" required>
          <el-select
            v-model="bindForm.userId"
            class="bind-user-select"
            filterable
            remote
            reserve-keyword
            allow-create
            default-first-option
            clearable
            placeholder="输入昵称、手机号或用户 ID"
            :remote-method="searchBindUsers"
            :loading="bindUserLoading"
          >
            <el-option
              v-for="user in bindUserOptions"
              :key="user.id"
              :label="`${user.nickname || '用户'}（ID：${user.id}）`"
              :value="user.id"
            >
              <div class="bind-user-option">
                <el-avatar :size="28" :src="user.avatarUrl || ''">
                  {{ (user.nickname || '用户').charAt(0) }}
                </el-avatar>
                <span>{{ user.nickname || '用户' }}</span>
                <small>ID：{{ user.id }}</small>
              </div>
            </el-option>
          </el-select>
          <div class="bind-tip">可以从搜索结果选择，也可以直接输入用户 ID，保存时会校验用户是否存在。</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="bindVisible = false">取消</el-button>
        <el-button type="primary" :loading="bindingId === bindForm.deviceId" @click="confirmBind">确认绑定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="affiliateVisible" title="新增挂靠设备" width="560px" destroy-on-close>
      <el-form :model="affiliateForm" label-position="top">
        <el-form-item label="创建方式">
          <el-radio-group v-model="affiliateForm.mode">
            <el-radio-button value="single">单个创建</el-radio-button>
            <el-radio-button value="batch">批量创建</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <div class="affiliate-form-grid">
          <el-form-item v-if="affiliateForm.mode === 'batch'" label="创建数量">
            <el-input-number v-model="affiliateForm.count" :min="1" :max="100" :precision="0" controls-position="right" />
          </el-form-item>
          <el-form-item label="设备名称">
            <el-input v-model="affiliateForm.name" maxlength="100" placeholder="选填，例如：华东节点" />
          </el-form-item>
          <el-form-item label="每日算力值">
            <el-input-number v-model="affiliateForm.hashrate" :min="1" :max="999999999" :precision="0" controls-position="right" />
          </el-form-item>
          <el-form-item label="运营商">
            <el-input v-model="affiliateForm.carrier" maxlength="50" placeholder="例如：中国移动" />
          </el-form-item>
          <el-form-item label="位置">
            <el-input v-model="affiliateForm.location" maxlength="100" placeholder="例如：山东省枣庄市" />
          </el-form-item>
          <el-form-item label="绑定用户（可选）">
            <el-select
              v-model="affiliateForm.userId"
              class="bind-user-select"
              filterable
              remote
              reserve-keyword
              allow-create
              default-first-option
              clearable
              placeholder="输入用户昵称或 ID"
              :remote-method="searchAffiliateUsers"
              :loading="affiliateUserLoading"
            >
              <el-option
                v-for="user in affiliateUserOptions"
                :key="user.id"
                :label="`${user.nickname || '用户'}（ID：${user.id}）`"
                :value="user.id"
              >
                <div class="bind-user-option">
                  <el-avatar :size="28" :src="user.avatarUrl || ''">
                    {{ (user.nickname || '用户').charAt(0) }}
                  </el-avatar>
                  <span>{{ user.nickname || '用户' }}</span>
                  <small>ID：{{ user.id }}</small>
                </div>
              </el-option>
            </el-select>
            <div class="bind-tip">输入昵称或用户 ID 后选择用户；也可以直接输入完整用户 ID。</div>
          </el-form-item>
        </div>
        <div class="affiliate-tip">挂靠设备创建后默认在线；填写用户 ID 会在创建时直接绑定并开始收益计算。</div>
      </el-form>
      <template #footer>
        <el-button @click="affiliateVisible = false">取消</el-button>
        <el-button type="primary" :loading="affiliateSaving" @click="saveAffiliateDevices">确认创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="qrExportVisible" title="导出设备二维码" width="min(760px, 94vw)" destroy-on-close>
      <div class="qr-export-toolbar">
        <el-radio-group v-model="qrExportScope" @change="loadQrExportRecords">
          <el-radio-button value="unbound">未绑定设备</el-radio-button>
          <el-radio-button value="all">全部设备</el-radio-button>
        </el-radio-group>
        <span class="qr-export-count">{{ qrExportRecords.length }} 台设备</span>
      </div>
      <el-alert
        title="二维码扫码后会进入绑定流程，ZIP 内每台设备包含一张高清标签和一份设备清单。"
        type="info"
        :closable="false"
        show-icon
      />
      <div v-loading="qrExportRecordsLoading" class="qr-preview-grid">
        <div v-for="record in qrPreviewRecords" :key="record.sn" class="qr-preview-card">
          <img :src="record.cardDataUrl" :alt="`设备 ${record.sn} 二维码`" />
          <span>{{ record.sn }}</span>
        </div>
        <el-empty v-if="!qrExportRecordsLoading && qrExportRecords.length === 0" description="没有可导出的设备" />
      </div>
      <template #footer>
        <el-button @click="qrExportVisible = false">取消</el-button>
        <el-button
          type="primary"
          :icon="Download"
          :loading="qrExportLoading"
          :disabled="qrExportRecords.length === 0"
          @click="downloadQrArchive"
        >
          {{ qrExportLoading ? `正在生成 ${qrExportProgress}%` : '下载二维码 ZIP' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, ref, onMounted, onUnmounted, reactive } from 'vue'
import { useRouter } from 'vue-router'
import request from '../utils/request'
import { Clock, Connection, Delete, Document, Download, Edit, Histogram, Link, Location, Monitor, Plus, Search, Unlock } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as echarts from 'echarts'
import QRCode from 'qrcode'
import JSZip from 'jszip'

const router = useRouter()
const loading = ref(false)
const devices = ref([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)
const searchQuery = ref('')
const statusFilter = ref('')
const deviceTypeFilter = ref(2)
const locationFilter = ref([])
const locationOptions = ref([])
const detailVisible = ref(false)
const detailLoading = ref(false)
const detailData = ref({})
const editVisible = ref(false)
const editSaving = ref(false)
const editForm = reactive({ id: null, name: '', location: '', carrier: '', type: 2, hashrate: 0 })
const deletingId = ref(null)
const selectedDevices = ref([])
const batchUnbinding = ref(false)
const batchDeleting = ref(false)
const csvExportLoading = ref(false)
const unbindingId = ref(null)
const bindingId = ref(null)
const bindVisible = ref(false)
const bindUserLoading = ref(false)
const bindUserOptions = ref([])
const bindForm = reactive({ deviceId: null, userId: null, sn: '' })
const affiliateVisible = ref(false)
const affiliateSaving = ref(false)
const affiliateUserLoading = ref(false)
const affiliateUserOptions = ref([])
let affiliateUserSearchSequence = 0
const affiliateForm = reactive({
  mode: 'batch',
  count: 1,
  name: '',
  hashrate: 100,
  carrier: '',
  location: '',
  userId: ''
})
const qrExportVisible = ref(false)
const qrExportScope = ref('unbound')
const qrExportRecords = ref([])
const qrPreviewRecords = ref([])
const qrExportRecordsLoading = ref(false)
const qrExportLoading = ref(false)
const qrExportProgress = ref(0)
const qrExportLogo = '/nvidia-mark.svg'
const offlineVisible = ref(false)
const offlineLoading = ref(false)
const offlineRecords = ref([])
const offlineDevice = ref({})
const globalOfflineVisible = ref(false)
const globalOfflineLoading = ref(false)
const globalOfflineRecords = ref([])
const globalOfflineSearch = ref('')
const globalOfflinePage = ref(1)
const globalOfflinePageSize = 10
const globalOfflineTotal = ref(0)
const earningsChartRef = ref(null)
const earningsChartLoading = ref(false)
const earningsChartData = reactive({ dates: [], earnings: [] })
let earningsChart = null
let liveTelemetryTimer = null
let liveTelemetryRefreshing = false

const selectedBoundDevices = computed(() => selectedDevices.value.filter(device => device.userId != null))
const selectedDeletableDevices = computed(() => selectedDevices.value.filter(
  device => device.userId == null && device.merchantId == null
))
const earningsTotal = computed(() => (
  earningsChartData.earnings.reduce((sum, amount) => sum + Number(amount || 0), 0)
).toFixed(2))

const stats = reactive({
  onlineCount: 0,
  totalCount: 0,
  avgCpuLoad: 60,
  avgMemLoad: 60,
  avgGpuLoad: 60
})

const normalizeTelemetryPercentage = (value) => {
  const parsed = Number.parseFloat(String(value ?? '').replace('%', ''))
  return Number.isFinite(parsed)
    ? String(Math.round(Math.max(0, Math.min(100, parsed))))
    : '0'
}

const withLiveTelemetry = (device) => {
  if (device.status !== 1) {
    return { ...device, cpuUsage: '0', memoryUsage: '0', gpuUsage: '0' }
  }
  return {
    ...device,
    cpuUsage: normalizeTelemetryPercentage(device.cpuUsage),
    memoryUsage: normalizeTelemetryPercentage(device.memoryUsage),
    gpuUsage: normalizeTelemetryPercentage(device.gpuUsage)
  }
}

const fetchStats = async () => {
  try {
    const res = await request.get('/api/admin/sl/devices/stats')
    if (res.data.code === 200) {
      Object.assign(stats, res.data.data)
    }
  } catch (e) { console.error(e) }
}

const fetchLocations = async () => {
  try {
    const res = await request.get('/api/admin/sl/devices/locations')
    if (res.data.code === 200) {
      const locationData = res.data.data || {}
      const provinces = Array.isArray(locationData.provinces) ? locationData.provinces : []
      const provinceMap = locationData.provinceMap && typeof locationData.provinceMap === 'object'
        ? locationData.provinceMap
        : {}
      locationOptions.value = provinces.map(prov => {
        const cities = provinceMap[prov] || []
        return {
          value: prov,
          label: prov,
          children: cities.length > 0 ? cities.map(city => ({
            value: city,
            label: city
          })) : undefined
        }
      })
    }
  } catch (e) { console.error(e) }
}

const onLocationChange = () => {
  currentPage.value = 1
  fetchDevices()
}

const onDeviceTypeChange = () => {
  currentPage.value = 1
  fetchDevices()
}

const getLocationParam = () => {
  if (!locationFilter.value || locationFilter.value.length === 0) return undefined
  // 如果选了省+市，用省+市组合搜索；如果只选了省，用省搜索
  return locationFilter.value.join('')
}

const fetchDevices = async (options = {}) => {
  const silent = options === true || options?.silent === true
  if (!silent) loading.value = true
  try {
    const res = await request.get('/api/admin/sl/devices/list', {
      params: {
        page: currentPage.value,
        size: pageSize.value,
        sn: searchQuery.value || undefined,
        status: statusFilter.value === '' ? undefined : statusFilter.value,
        location: getLocationParam(),
        type: deviceTypeFilter.value
      }
    })
    if (res.data.code === 200) {
      const pageData = res.data.data || {}
      devices.value = Array.isArray(pageData.records)
        ? pageData.records.map(device => withLiveTelemetry(device))
        : []
      total.value = Number(pageData.total) || 0
      if (detailVisible.value && detailData.value.id != null) {
        const current = devices.value.find(device => device.id === detailData.value.id)
        if (current) detailData.value = { ...detailData.value, ...current }
      }
    }
  } catch (e) {
    if (!silent) ElMessage.error('获取列表失败')
  } finally {
    if (!silent) loading.value = false
  }
}

const formatTime = (time) => time ? time.replace('T', ' ').substring(0, 19) : '-'

const handleSelectionChange = (rows) => {
  selectedDevices.value = Array.isArray(rows) ? rows : []
}

const openEditDialog = (device) => {
  Object.assign(editForm, {
    id: device.id,
    name: device.name || '',
    location: device.location || '',
    carrier: device.carrier || '',
    type: Number.isInteger(Number(device.type)) ? Number(device.type) : 2,
    hashrate: Number(device.hashrate || 0)
  })
  editVisible.value = true
}

const saveDeviceEdit = async () => {
  if (!editForm.id) {
    ElMessage.error('设备信息已失效，请刷新后重试')
    return
  }
  if (!Number.isInteger(Number(editForm.hashrate)) || Number(editForm.hashrate) < 0) {
    ElMessage.error('算力值必须是大于或等于 0 的整数')
    return
  }

  editSaving.value = true
  try {
    const res = await request.post('/api/device/update', {
      id: editForm.id,
      name: String(editForm.name || '').trim() || null,
      location: String(editForm.location || '').trim() || null,
      carrier: String(editForm.carrier || '').trim() || null,
      type: Number(editForm.type),
      hashrate: Number(editForm.hashrate)
    })
    if (res.data.code === 200) {
      ElMessage.success('设备信息已更新')
      editVisible.value = false
      await Promise.all([fetchDevices(), fetchStats(), fetchLocations()])
    } else {
      ElMessage.error(res.data.msg || '设备信息保存失败')
    }
  } catch (error) {
    console.error('保存设备信息失败:', error)
  } finally {
    editSaving.value = false
  }
}

const deleteDevice = async (device) => {
  if (device.userId != null || device.merchantId != null) {
    ElMessage.warning('只能删除未绑定且不属于商户的设备')
    return
  }

  try {
    await ElMessageBox.confirm(
      `确定删除设备 ${device.bindCode || device.sn} 吗？删除后不可恢复。`,
      '删除设备',
      {
        confirmButtonText: '确认删除',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
  } catch (error) {
    return
  }

  deletingId.value = device.id
  try {
    const res = await request.delete(`/api/device/delete/${device.id}`)
    if (res.data.code === 200) {
      ElMessage.success('设备已删除')
      if (devices.value.length === 1 && currentPage.value > 1) currentPage.value -= 1
      await Promise.all([fetchDevices(), fetchStats()])
    } else {
      ElMessage.error(res.data.msg || '删除设备失败')
    }
  } catch (error) {
    console.error('删除设备失败:', error)
  } finally {
    deletingId.value = null
  }
}

const batchUnbindDevices = async () => {
  const targets = selectedBoundDevices.value
  if (targets.length === 0) {
    ElMessage.warning('所选设备中没有已绑定设备')
    return
  }

  try {
    await ElMessageBox.confirm(
      `确定批量解绑所选的 ${targets.length} 台设备吗？`,
      '批量解绑设备',
      {
        confirmButtonText: '确认解绑',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
  } catch (error) {
    return
  }

  batchUnbinding.value = true
  try {
    const res = await request.post('/api/device/batch-unbind', { ids: targets.map(device => device.id) })
    if (res.data.code === 200) {
      ElMessage.success(`已解绑 ${targets.length} 台设备`)
      selectedDevices.value = []
      await Promise.all([fetchDevices(), fetchStats()])
    } else {
      ElMessage.error(res.data.msg || '批量解绑失败')
    }
  } catch (error) {
    console.error('批量解绑失败:', error)
  } finally {
    batchUnbinding.value = false
  }
}

const batchDeleteDevices = async () => {
  const targets = selectedDeletableDevices.value
  if (targets.length === 0) {
    ElMessage.warning('所选设备中没有可删除的未绑定设备')
    return
  }

  try {
    await ElMessageBox.confirm(
      `确定批量删除所选的 ${targets.length} 台未绑定设备吗？删除后不可恢复。`,
      '批量删除设备',
      {
        confirmButtonText: '确认删除',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
  } catch (error) {
    return
  }

  batchDeleting.value = true
  try {
    const res = await request.post('/api/device/batch-delete', { ids: targets.map(device => device.id) })
    if (res.data.code === 200) {
      ElMessage.success(res.data.msg || `已删除 ${targets.length} 台设备`)
      selectedDevices.value = []
      if (targets.length >= devices.value.length && currentPage.value > 1) currentPage.value -= 1
      await Promise.all([fetchDevices(), fetchStats()])
    } else {
      ElMessage.error(res.data.msg || '批量删除失败')
    }
  } catch (error) {
    console.error('批量删除设备失败:', error)
  } finally {
    batchDeleting.value = false
  }
}

const csvCell = (value) => `"${String(value ?? '').replaceAll('"', '""')}"`

const exportDeviceCsv = async () => {
  csvExportLoading.value = true
  try {
    const res = await request.get('/api/device/export-sn', { params: { unboundOnly: false } })
    if (res.data.code !== 200) {
      ElMessage.error(res.data.msg || '获取设备清单失败')
      return
    }

    const records = Array.isArray(res.data.data?.list) ? res.data.data.list : []
    if (records.length === 0) {
      ElMessage.warning('暂无设备可导出')
      return
    }

    const rows = [
      ['序号', '绑定码', 'SN 序列号', '设备名称', '设备类型', '运行状态', '绑定状态', '绑定用户ID', '绑定用户', '位置', '运营商', '算力值', '最后心跳', '创建时间'],
      ...records.map(record => [
        record.index,
        record.bindCode,
        record.sn,
        record.name,
        formatDeviceType(record.type),
        record.status,
        record.bound,
        record.userId,
        record.nickname,
        record.location,
        record.carrier,
        record.hashrate,
        formatTime(record.lastHeartbeatTime),
        formatTime(record.createTime)
      ])
    ]
    const csv = `\uFEFF${rows.map(row => row.map(csvCell).join(',')).join('\r\n')}`
    const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }))
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = `聚芯Orin_设备清单_${new Date().toISOString().slice(0, 10)}.csv`
    document.body.appendChild(anchor)
    anchor.click()
    anchor.remove()
    URL.revokeObjectURL(url)
    ElMessage.success(`已导出 ${records.length} 台设备`)
  } catch (error) {
    console.error('导出设备 CSV 失败:', error)
  } finally {
    csvExportLoading.value = false
  }
}

const fetchGlobalOfflineRecords = async () => {
  globalOfflineLoading.value = true
  try {
    const res = await request.get('/api/admin/device-offline-log/list', {
      params: {
        page: globalOfflinePage.value,
        size: globalOfflinePageSize,
        sn: String(globalOfflineSearch.value || '').trim() || undefined
      }
    })
    if (res.data.code === 200) {
      const pageData = res.data.data || {}
      globalOfflineRecords.value = Array.isArray(pageData.records) ? pageData.records : []
      globalOfflineTotal.value = Number(pageData.total || 0)
    } else {
      ElMessage.error(res.data.msg || '获取全局离线记录失败')
    }
  } catch (error) {
    console.error('获取全局离线记录失败:', error)
  } finally {
    globalOfflineLoading.value = false
  }
}

const openGlobalOfflineRecords = () => {
  globalOfflineVisible.value = true
  globalOfflinePage.value = 1
  fetchGlobalOfflineRecords()
}

const searchGlobalOfflineRecords = () => {
  globalOfflinePage.value = 1
  fetchGlobalOfflineRecords()
}

const renderEarningsChart = () => {
  if (!earningsChartRef.value) return
  earningsChart?.dispose()
  earningsChart = echarts.init(earningsChartRef.value)
  earningsChart.setOption({
    color: ['#659f00'],
    grid: { top: 28, right: 22, bottom: 28, left: 58 },
    tooltip: {
      trigger: 'axis',
      formatter: (items) => {
        const item = items?.[0]
        return item ? `${item.axisValue}<br/>收益：¥${Number(item.value || 0).toFixed(2)}` : ''
      }
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: earningsChartData.dates,
      axisLabel: { color: '#6b7280', fontSize: 11 },
      axisLine: { lineStyle: { color: '#d7ddd2' } }
    },
    yAxis: {
      type: 'value',
      name: '收益（元）',
      nameTextStyle: { color: '#6b7280', fontSize: 11 },
      axisLabel: { color: '#6b7280', formatter: value => `¥${value}` },
      splitLine: { lineStyle: { color: '#edf0eb' } }
    },
    series: [{
      name: '收益',
      type: 'line',
      smooth: true,
      symbolSize: 7,
      data: earningsChartData.earnings,
      areaStyle: { color: 'rgba(101, 159, 0, 0.12)' },
      lineStyle: { width: 3 }
    }]
  })
}

const loadEarningsChart = async (deviceId) => {
  earningsChartLoading.value = true
  earningsChartData.dates = []
  earningsChartData.earnings = []
  try {
    const res = await request.get(`/api/device/chart-data/${deviceId}`)
    if (res.data.code === 200) {
      earningsChartData.dates = Array.isArray(res.data.data?.dates) ? res.data.data.dates : []
      earningsChartData.earnings = Array.isArray(res.data.data?.earnings) ? res.data.data.earnings : []
      await nextTick()
      renderEarningsChart()
    } else {
      ElMessage.error(res.data.msg || '获取设备收益趋势失败')
    }
  } catch (error) {
    console.error('获取设备收益趋势失败:', error)
  } finally {
    earningsChartLoading.value = false
  }
}

const disposeEarningsChart = () => {
  earningsChart?.dispose()
  earningsChart = null
}

const viewDetail = async (device) => {
  detailVisible.value = true
  detailLoading.value = true
  detailData.value = withLiveTelemetry(device)
  await nextTick()
  loadEarningsChart(device.id)

  try {
    const res = await request.get(`/api/device/detail/${device.id}`)
    if (res.data.code === 200) {
      detailData.value = withLiveTelemetry({
        ...device,
        ...res.data.data,
        nickname: res.data.data.nickname || device.nickname,
        avatarUrl: res.data.data.avatarUrl || device.avatarUrl,
      })
    }
  } catch (e) {
    ElMessage.error(`获取设备 ${device.sn} 详情失败`)
  } finally {
    detailLoading.value = false
  }
}

const openOfflineRecords = async (device) => {
  offlineDevice.value = device
  offlineVisible.value = true
  offlineLoading.value = true
  offlineRecords.value = []

  try {
    const res = await request.get(`/api/device/offline-records/${device.id}`, {
      params: { page: 1, size: 50 }
    })
    if (res.data.code === 200) {
      const pageData = res.data.data || {}
      offlineRecords.value = Array.isArray(pageData.records)
        ? pageData.records
        : (Array.isArray(pageData) ? pageData : [])
    } else {
      ElMessage.error(res.data.msg || '获取离线记录失败')
    }
  } catch (error) {
    console.error('获取设备离线记录失败:', error)
    ElMessage.error('获取离线记录失败')
  } finally {
    offlineLoading.value = false
  }
}

const formatDeviceType = (type) => {
  if (type === 2) return '边缘算力节点'
  if (type === 1) return '虚拟设备'
  if (type === 0) return '实体设备'
  return '未标记'
}

const envStatusText = (status) => ({
  ready: '正常',
  warning: '缺依赖',
  checking: '检查中',
  error: '失败',
  unknown: '未检查'
}[status] || '未检查')

const envTagType = (status) => ({
  ready: 'success',
  warning: 'warning',
  checking: 'primary',
  error: 'danger',
  unknown: 'info'
}[status] || 'info')

const openTerminal = (device) => {
  if (device.status !== 1) {
    ElMessage.warning('设备离线，隧道无法建立')
    return
  }
  router.push({
    name: 'Terminal',
    query: { sn: device.sn }
  })
}

const openBindDialog = (device) => {
  if (device.userId != null) {
    ElMessage.warning('该设备已经绑定用户')
    return
  }
  Object.assign(bindForm, { deviceId: device.id, userId: null, sn: device.sn || '' })
  bindUserOptions.value = []
  bindVisible.value = true
}

const searchBindUsers = async (query) => {
  const keyword = String(query || '').trim()
  if (!keyword) {
    bindUserOptions.value = []
    return
  }

  bindUserLoading.value = true
  try {
    const res = await request.get('/api/user/list', {
      params: { page: 1, size: 20, keyword },
      silent: true,
    })
    if (res.data.code === 200) {
      bindUserOptions.value = res.data.data?.records || []
    } else {
      ElMessage.error(res.data.msg || '搜索用户失败')
    }
  } catch (error) {
    console.error('搜索绑定用户失败:', error)
  } finally {
    bindUserLoading.value = false
  }
}

const confirmBind = async () => {
  const userId = Number(bindForm.userId)
  if (!Number.isSafeInteger(userId) || userId <= 0) {
    ElMessage.error('请输入有效的用户 ID，或从搜索结果选择用户')
    return
  }
  if (!bindForm.deviceId) {
    ElMessage.error('设备信息已失效，请刷新后重试')
    return
  }

  bindingId.value = bindForm.deviceId
  try {
    const res = await request.post('/api/device/admin-bind', {
      deviceId: bindForm.deviceId,
      userId,
    })
    if (res.data.code === 200) {
      ElMessage.success('设备绑定成功')
      bindVisible.value = false
      await Promise.all([fetchDevices(), fetchStats()])
    } else {
      ElMessage.error(res.data.msg || '设备绑定失败')
    }
  } catch (error) {
    console.error('绑定设备失败:', error)
  } finally {
    bindingId.value = null
  }
}

const openAffiliateDialog = () => {
  affiliateUserSearchSequence += 1
  affiliateUserOptions.value = []
  affiliateUserLoading.value = false
  Object.assign(affiliateForm, {
    mode: 'batch',
    count: 1,
    name: '',
    hashrate: 100,
    carrier: '',
    location: '',
    userId: ''
  })
  affiliateVisible.value = true
}

const searchAffiliateUsers = async (query) => {
  const keyword = String(query || '').trim()
  const searchSequence = ++affiliateUserSearchSequence
  if (!keyword) {
    affiliateUserOptions.value = []
    affiliateUserLoading.value = false
    return
  }

  affiliateUserLoading.value = true
  try {
    const res = await request.get('/api/user/list', {
      params: { page: 1, size: 20, keyword },
      silent: true
    })
    if (searchSequence !== affiliateUserSearchSequence) return
    if (res.data.code === 200) {
      affiliateUserOptions.value = res.data.data?.records || []
    } else {
      affiliateUserOptions.value = []
      ElMessage.error(res.data.msg || '搜索用户失败')
    }
  } catch (error) {
    if (searchSequence === affiliateUserSearchSequence) {
      affiliateUserOptions.value = []
      console.error('搜索挂靠设备绑定用户失败:', error)
    }
  } finally {
    if (searchSequence === affiliateUserSearchSequence) {
      affiliateUserLoading.value = false
    }
  }
}

const saveAffiliateDevices = async () => {
  const count = affiliateForm.mode === 'single' ? 1 : Number(affiliateForm.count)
  const hashrate = Number(affiliateForm.hashrate)
  const name = String(affiliateForm.name || '').trim()
  const rawUserId = String(affiliateForm.userId || '').trim()
  const userId = rawUserId ? Number(rawUserId) : null

  if (!Number.isInteger(count) || count < 1 || count > 100) {
    ElMessage.error('创建数量必须在 1-100 之间')
    return
  }
  if (!Number.isInteger(hashrate) || hashrate <= 0) {
    ElMessage.error('每日算力值必须是大于 0 的整数')
    return
  }
  if (rawUserId && (!Number.isSafeInteger(userId) || userId <= 0)) {
    ElMessage.error('用户 ID 格式不正确')
    return
  }

  affiliateSaving.value = true
  try {
    const endpoint = affiliateForm.mode === 'single' ? '/api/device/create' : '/api/device/batch-create'
    const res = await request.post(endpoint, {
      count,
      name: name || null,
      hashrate,
      carrier: String(affiliateForm.carrier || '').trim() || null,
      location: String(affiliateForm.location || '').trim() || null,
      userId
    })
    if (res.data.code === 200) {
      const created = affiliateForm.mode === 'single' ? 1 : Number(res.data.data?.created || count)
      ElMessage.success(`已创建 ${created} 台挂靠设备`)
      affiliateVisible.value = false
      deviceTypeFilter.value = 1
      currentPage.value = 1
      await Promise.all([fetchDevices(), fetchStats()])
    } else {
      ElMessage.error(res.data.msg || '创建挂靠设备失败')
    }
  } catch (error) {
    console.error('创建挂靠设备失败:', error)
  } finally {
    affiliateSaving.value = false
  }
}

const deviceBindUrl = (record) => {
  const code = String(record.bindCode || record.sn || '').trim()
  return `https://nvidia.juxinsuanli.cn/bind?code=${encodeURIComponent(code)}`
}

const loadCanvasImage = (src) => new Promise((resolve, reject) => {
  const image = new Image()
  image.onload = () => resolve(image)
  image.onerror = () => reject(new Error(`图片加载失败: ${src}`))
  image.src = src
})

const roundedRect = (context, x, y, width, height, radius) => {
  const safeRadius = Math.min(radius, width / 2, height / 2)
  context.beginPath()
  context.moveTo(x + safeRadius, y)
  context.arcTo(x + width, y, x + width, y + height, safeRadius)
  context.arcTo(x + width, y + height, x, y + height, safeRadius)
  context.arcTo(x, y + height, x, y, safeRadius)
  context.arcTo(x, y, x + width, y, safeRadius)
  context.closePath()
}

const drawQrCard = async (record) => {
  const qrDataUrl = await QRCode.toDataURL(deviceBindUrl(record), {
    width: 720,
    margin: 2,
    errorCorrectionLevel: 'H',
    color: { dark: '#10120f', light: '#ffffff' }
  })
  const [qrImage, nvidiaImage] = await Promise.all([
    loadCanvasImage(qrDataUrl),
    loadCanvasImage(qrExportLogo)
  ])

  const canvas = document.createElement('canvas')
  canvas.width = 1080
  canvas.height = 1420
  const context = canvas.getContext('2d')
  if (!context) throw new Error('当前浏览器不支持二维码标签生成')

  context.fillStyle = '#ffffff'
  context.fillRect(0, 0, canvas.width, canvas.height)
  context.fillStyle = '#76b900'
  context.fillRect(0, 0, canvas.width, 16)

  context.fillStyle = '#10120f'
  context.font = '800 58px "PingFang SC", "Microsoft YaHei", sans-serif'
  context.fillText('聚芯', 72, 118)
  const brandWidth = context.measureText('聚芯').width
  context.fillStyle = '#76b900'
  context.font = '900 62px Arial, sans-serif'
  context.fillText('ORIN', 72 + brandWidth + 18, 118)

  context.fillStyle = '#a9afb6'
  context.font = '500 42px Arial, sans-serif'
  context.fillText('x', 560, 114)
  context.drawImage(nvidiaImage, 650, 54, 82, 82)
  context.fillStyle = '#10120f'
  context.font = '900 52px Arial, sans-serif'
  context.fillText('NVIDIA', 754, 116)

  context.strokeStyle = '#e4e7eb'
  context.lineWidth = 2
  context.beginPath()
  context.moveTo(72, 170)
  context.lineTo(1008, 170)
  context.stroke()

  roundedRect(context, 110, 220, 860, 840, 30)
  context.fillStyle = '#f7f8f6'
  context.fill()
  context.strokeStyle = '#dfe4dc'
  context.stroke()
  context.drawImage(qrImage, 190, 270, 700, 700)

  context.fillStyle = '#000000'
  context.textAlign = 'center'
  context.font = '800 44px "PingFang SC", "Microsoft YaHei", sans-serif'
  context.fillText('微信扫码绑定设备', 540, 1138)
  context.fillStyle = '#000000'
  context.font = '500 26px "PingFang SC", "Microsoft YaHei", sans-serif'
  context.fillText('打开聚芯 Orin 小程序，进入设备页面扫码', 540, 1186)

  const bindCode = String(record.bindCode || '-').trim()
  context.fillStyle = '#000000'
  context.font = '900 46px ui-monospace, SFMono-Regular, Menlo, monospace'
  context.fillText(bindCode, 540, 1266)
  context.fillStyle = '#000000'
  context.font = '500 24px ui-monospace, SFMono-Regular, Menlo, monospace'
  context.fillText(`SN: ${record.sn || '-'}`, 540, 1312)

  context.fillStyle = '#10120f'
  context.fillRect(0, 1360, canvas.width, 60)
  context.fillStyle = '#ffffff'
  context.font = '600 22px "PingFang SC", "Microsoft YaHei", sans-serif'
  context.fillText('聚芯 Orin & NVIDIA 边缘算力设备', 540, 1398)

  return canvas.toDataURL('image/png')
}

const loadQrExportRecords = async () => {
  qrExportRecordsLoading.value = true
  qrPreviewRecords.value = []
  try {
    const res = await request.get('/api/device/export-sn', {
      params: { unboundOnly: qrExportScope.value === 'unbound' },
      silent: true
    })
    if (res.data.code !== 200) {
      ElMessage.error(res.data.msg || '获取二维码设备清单失败')
      qrExportRecords.value = []
      return
    }
    const list = Array.isArray(res.data.data?.list) ? res.data.data.list : []
    qrExportRecords.value = list.filter(record => record.sn && (record.bindCode || record.sn))
    qrPreviewRecords.value = await Promise.all(
      qrExportRecords.value.slice(0, 3).map(async record => ({
        ...record,
        cardDataUrl: await drawQrCard(record)
      }))
    )
  } catch (error) {
    console.error('获取二维码设备清单失败:', error)
    qrExportRecords.value = []
    ElMessage.error('获取二维码设备清单失败')
  } finally {
    qrExportRecordsLoading.value = false
  }
}

const openQrExportDialog = () => {
  qrExportVisible.value = true
  qrExportScope.value = 'unbound'
  qrExportProgress.value = 0
  loadQrExportRecords()
}

const csvValue = (value) => {
  let text = String(value ?? '')
  if (/^[=+\-@]/.test(text)) text = `'${text}`
  return `"${text.replaceAll('"', '""')}"`
}

const safeFilePart = (value) => String(value || 'device')
  .replace(/[^a-zA-Z0-9_-]+/g, '_')
  .slice(0, 80)

const downloadQrArchive = async () => {
  if (qrExportLoading.value || qrExportRecords.value.length === 0) return

  qrExportLoading.value = true
  qrExportProgress.value = 0
  try {
    const zip = new JSZip()
    const records = qrExportRecords.value
    const imageFolder = zip.folder('设备二维码')
    const concurrency = 6

    for (let start = 0; start < records.length; start += concurrency) {
      const batch = records.slice(start, start + concurrency)
      const cards = await Promise.all(batch.map(record => drawQrCard(record)))
      cards.forEach((dataUrl, index) => {
        const record = batch[index]
        const sequence = String(start + index + 1).padStart(4, '0')
        imageFolder.file(`${sequence}_${safeFilePart(record.sn)}.png`, dataUrl.split(',')[1], { base64: true })
      })
      qrExportProgress.value = Math.round(Math.min(90, ((start + batch.length) / records.length) * 90))
    }

    const csvRows = [
      ['序号', 'SN', '绑定码', '绑定链接', '状态', '绑定状态'],
      ...records.map((record, index) => [
        index + 1,
        record.sn,
        record.bindCode || '',
        deviceBindUrl(record),
        record.status || '',
        record.bound || ''
      ])
    ]
    zip.file('设备二维码清单.csv', `\uFEFF${csvRows.map(row => row.map(csvValue).join(',')).join('\r\n')}`)

    const blob = await zip.generateAsync({ type: 'blob' }, metadata => {
      qrExportProgress.value = 90 + Math.round(metadata.percent / 10)
    })
    const url = URL.createObjectURL(blob)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = `聚芯Orin_设备二维码_${new Date().toISOString().slice(0, 10)}.zip`
    document.body.appendChild(anchor)
    anchor.click()
    anchor.remove()
    URL.revokeObjectURL(url)
    qrExportProgress.value = 100
    ElMessage.success(`已导出 ${records.length} 台设备二维码`)
  } catch (error) {
    console.error('导出设备二维码失败:', error)
    ElMessage.error(error.message || '导出设备二维码失败')
  } finally {
    qrExportLoading.value = false
  }
}

const unbindDevice = async (device) => {
  if (device.userId == null) return

  try {
    await ElMessageBox.confirm(
      `确定解绑设备 ${device.sn} 吗？解绑后该设备将不再属于当前用户。`,
      '解绑设备',
      {
        confirmButtonText: '确认解绑',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
  } catch (e) {
    return
  }

  unbindingId.value = device.id
  try {
    const res = await request.post('/api/device/batch-unbind', { ids: [device.id] })
    if (res.data.code === 200) {
      ElMessage.success('设备解绑成功')
      await Promise.all([fetchDevices(), fetchStats()])
    } else {
      ElMessage.error(res.data.msg || '解绑失败')
    }
  } finally {
    unbindingId.value = null
  }
}

onMounted(() => {
  fetchStats()
  fetchLocations()
  fetchDevices()
  liveTelemetryTimer = setInterval(async () => {
    if (liveTelemetryRefreshing) return
    liveTelemetryRefreshing = true
    try {
      await Promise.all([fetchStats(), fetchDevices({ silent: true })])
    } finally {
      liveTelemetryRefreshing = false
    }
  }, 10000)
})

onUnmounted(() => {
  if (liveTelemetryTimer !== null) {
    clearInterval(liveTelemetryTimer)
    liveTelemetryTimer = null
  }
})
</script>

<style scoped>
.monitor-page {
  width: 100%;
  color: var(--orin-text);
}

.stat-grid {
  margin-bottom: 14px;
}

.pro-card {
  position: relative;
  height: 112px;
  overflow: hidden;
  padding: 18px 20px;
  color: var(--orin-text);
  background: var(--orin-surface);
  border: 1px solid var(--orin-border);
  border-left-width: 3px;
  border-radius: 6px;
  box-shadow: none;
}

.pro-card.primary {
  border-left-color: var(--orin-green);
}

.pro-card.success {
  border-left-color: var(--orin-cyan);
}

.pro-card.warning {
  border-left-color: var(--orin-amber);
}

.pro-card.indigo {
  border-left-color: var(--orin-border-strong);
}

.card-label {
  margin-bottom: 8px;
  color: var(--orin-muted);
  font-size: 12px;
}

.card-val {
  color: var(--orin-text);
  font-size: 30px;
  font-weight: 800;
  line-height: 1.1;
}

.card-val small {
  margin-left: 4px;
  color: var(--orin-dim);
  font-size: 12px;
  font-weight: 600;
}

.card-icon {
  position: absolute;
  right: 18px;
  bottom: 16px;
  color: var(--orin-green);
  font-size: 42px;
  opacity: 0.28;
}

.pro-card.success .card-icon {
  color: var(--orin-cyan);
}

.pro-card.warning .card-icon {
  color: var(--orin-amber);
}

.pro-card.indigo .card-icon {
  color: var(--orin-text-soft);
}

.filter-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;
  padding: 14px 16px;
  background: var(--orin-surface);
  border: 1px solid var(--orin-border);
  border-radius: 6px;
  box-shadow: none;
}

.filter-bar :deep(.el-radio-button__inner) {
  color: var(--orin-text-soft);
  background: var(--orin-surface-raised);
  border-color: var(--orin-border);
  box-shadow: none;
}

.filter-bar :deep(.el-radio-button.is-active .el-radio-button__inner) {
  color: #ffffff;
  background: var(--orin-green);
  border-color: var(--orin-green);
  box-shadow: -1px 0 0 0 var(--orin-green);
}

.table-container {
  padding: 16px;
  background: var(--orin-surface);
  border: 1px solid var(--orin-border);
  border-radius: 6px;
  box-shadow: none;
}

.batch-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
  padding: 10px 12px;
  color: var(--orin-text-soft);
  background: var(--orin-surface-soft);
  border: 1px solid var(--orin-border-soft);
  border-radius: 5px;
  font-size: 13px;
}

.batch-toolbar span {
  margin-right: auto;
  font-weight: 700;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px 18px;
}

.detail-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 12px 14px;
  background: var(--orin-surface-soft);
  border: 1px solid var(--orin-border-soft);
  border-radius: 5px;
}

.detail-item.full {
  grid-column: 1 / -1;
}

.detail-label {
  color: var(--orin-muted);
  font-size: 12px;
}

.detail-value {
  color: var(--orin-text-soft);
  font-size: 14px;
  font-weight: 600;
  overflow-wrap: anywhere;
}

.detail-value.mono {
  font-family: monospace;
}

.detail-value.accent {
  color: var(--orin-green-bright);
  font-size: 18px;
  font-weight: 800;
}

.earnings-chart-panel {
  margin-top: 18px;
  padding: 16px;
  background: var(--orin-surface-soft);
  border: 1px solid var(--orin-border-soft);
  border-radius: 6px;
}

.earnings-chart-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.earnings-chart-head > div {
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.earnings-chart-head strong {
  color: var(--orin-text);
  font-size: 15px;
}

.earnings-chart-head span {
  color: var(--orin-muted);
  font-size: 12px;
}

.earnings-chart-total {
  color: var(--orin-green) !important;
  font-size: 15px !important;
  font-weight: 800;
}

.earnings-chart {
  width: 100%;
  height: 280px;
  margin-top: 10px;
}

.edit-form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 16px;
}

.edit-form-grid .span-2 {
  grid-column: 1 / -1;
}

.global-offline-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
}

.runtime-version {
  margin: 4px 0;
  color: var(--orin-muted);
  font-size: 10px;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}

.offline-dialog-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
  color: var(--orin-muted);
  font-size: 12px;
}

.offline-dialog-sn {
  color: var(--orin-text-soft);
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-weight: 700;
}

.carrier-info {
  margin-top: 4px;
  color: var(--orin-muted);
  font-size: 11px;
}

.device-bind-code {
  color: var(--orin-text-soft);
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 14px;
  letter-spacing: 0;
}

.env-cell {
  color: var(--orin-text-soft);
  font-size: 12px;
  line-height: 1.45;
}

.env-summary {
  margin-top: 5px;
}

.env-missing {
  margin-top: 3px;
  color: var(--orin-amber);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.env-detail {
  margin-left: 8px;
}

.bound-user {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-width: 0;
}

.bound-user-detail {
  justify-content: flex-start;
}

.bound-user-avatar {
  flex: 0 0 auto;
  color: var(--orin-green);
  background: var(--orin-surface-soft);
  border: 1px solid var(--orin-border);
}

.bound-user-meta {
  display: flex;
  flex-direction: column;
  min-width: 0;
  text-align: left;
  line-height: 1.35;
}

.bound-user-name {
  max-width: 128px;
  overflow: hidden;
  color: var(--orin-text-soft);
  font-size: 12px;
  font-weight: 650;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.bound-user-id {
  color: var(--orin-muted);
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 11px;
  white-space: nowrap;
}

.bind-device-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  color: var(--orin-text-soft);
  background: var(--orin-surface-soft);
  border: 1px solid var(--orin-border-soft);
  border-radius: 5px;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}

.bind-device-summary span {
  color: var(--orin-muted);
  font-family: inherit;
  font-size: 12px;
}

.bind-user-select {
  width: 100%;
}

.bind-user-option {
  display: flex;
  align-items: center;
  gap: 8px;
}

.bind-user-option small {
  margin-left: auto;
  color: var(--orin-muted);
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}

.bind-tip {
  margin-top: 7px;
  color: var(--orin-muted);
  font-size: 12px;
  line-height: 1.5;
}

.affiliate-form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 16px;
}

.affiliate-form-grid .el-input-number,
.affiliate-form-grid .el-input {
  width: 100%;
}

.affiliate-tip {
  padding: 10px 12px;
  color: var(--orin-muted);
  background: var(--orin-surface-soft);
  border: 1px solid var(--orin-border-soft);
  border-radius: 5px;
  font-size: 12px;
  line-height: 1.5;
}

.qr-export-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}

.qr-export-count {
  color: var(--orin-text-soft);
  font-size: 13px;
  font-weight: 700;
}

.qr-preview-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  min-height: 260px;
  gap: 14px;
  margin-top: 16px;
}

.qr-preview-grid :deep(.el-empty) {
  grid-column: 1 / -1;
}

.qr-preview-card {
  min-width: 0;
  padding: 10px;
  background: var(--orin-surface-soft);
  border: 1px solid var(--orin-border-soft);
  border-radius: 6px;
}

.qr-preview-card img {
  display: block;
  width: 100%;
  aspect-ratio: 1080 / 1420;
  object-fit: contain;
  background: #ffffff;
}

.qr-preview-card span {
  display: block;
  margin-top: 8px;
  overflow: hidden;
  color: var(--orin-muted);
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 11px;
  text-align: center;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 18px;
}

.table-container :deep(.el-progress-bar__outer) {
  background: var(--orin-surface-soft);
}

.table-container :deep(.el-progress-bar__inner) {
  background-color: var(--orin-green) !important;
}

@media (max-width: 760px) {
  .filter-bar {
    align-items: stretch;
    flex-direction: column;
  }

  .filter-bar > * {
    width: 100% !important;
  }

  .affiliate-form-grid,
  .edit-form-grid,
  .qr-preview-grid {
    grid-template-columns: 1fr;
  }

  .edit-form-grid .span-2 {
    grid-column: auto;
  }

  .batch-toolbar,
  .global-offline-toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .batch-toolbar span {
    margin-right: 0;
  }

  .global-offline-toolbar > * {
    width: 100% !important;
  }

  .qr-export-toolbar {
    align-items: flex-start;
    flex-direction: column;
  }
}

.table-container :deep([style*="color: #1890ff"]),
.table-container :deep([style*="color: #67c23a"]) {
  color: var(--orin-green-bright) !important;
}

.table-container :deep([style*="color: #f56c6c"]) {
  color: var(--orin-danger) !important;
}

.table-container :deep([style*="color: #909399"]),
.table-container :deep([style*="color: #666"]),
.table-container :deep([style*="color: #999"]) {
  color: var(--orin-muted) !important;
}

@media (max-width: 900px) {
  .stat-grid {
    row-gap: 12px;
  }

  .pro-card {
    height: 102px;
    padding: 16px;
  }

  .filter-bar {
    align-items: stretch;
    flex-wrap: wrap;
  }

  .filter-bar :deep(.el-input),
  .filter-bar :deep(.el-cascader),
  .filter-bar :deep(.el-select) {
    width: calc(50% - 6px) !important;
  }

  .filter-bar :deep(.el-radio-group) {
    flex: 1 1 100%;
  }

  .filter-bar :deep(.el-radio-button) {
    flex: 1;
  }

  .filter-bar :deep(.el-radio-button__inner) {
    width: 100%;
  }

  .table-container {
    padding: 12px;
  }

  .detail-grid {
    grid-template-columns: 1fr;
  }

  .detail-item.full {
    grid-column: auto;
  }
}

@media (max-width: 520px) {
  .pro-card {
    height: 94px;
    padding: 14px;
  }

  .card-val {
    font-size: 26px;
  }

  .card-icon {
    right: 14px;
    bottom: 14px;
    font-size: 34px;
  }

  .filter-bar {
    padding: 10px;
  }

  .filter-bar :deep(.el-input),
  .filter-bar :deep(.el-cascader),
  .filter-bar :deep(.el-select),
  .filter-bar :deep(.el-button) {
    width: 100% !important;
  }

  .affiliate-form-grid {
    grid-template-columns: 1fr;
  }

  .table-container {
    padding: 8px;
  }

  .pagination {
    justify-content: flex-start;
    overflow-x: auto;
    padding-bottom: 4px;
  }

  .detail-item {
    padding: 10px;
  }
}
</style>
