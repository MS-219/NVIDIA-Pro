<template>
  <div class="settings-page">
    <el-row :gutter="24">
      <!-- 个人信息 -->
      <el-col :span="24" style="margin-bottom: 24px;">
        <el-card class="setting-card profile-card" id="profile">
          <template #header>
            <span class="card-title"><el-icon><User /></el-icon> 个人信息</span>
          </template>
          <div class="profile-info">
            <el-avatar :size="80" class="profile-avatar">{{ (adminInfo.username || 'A').charAt(0).toUpperCase() }}</el-avatar>
            <div class="profile-details">
              <div class="profile-row">
                <span class="label">用户名:</span>
                <span class="value">{{ adminInfo.username }}</span>
              </div>
              <div class="profile-row">
                <span class="label">当前角色:</span>
                <el-tag type="success">{{ adminInfo.role === 'super' ? '超级管理员' : '系统管理员' }}</el-tag>
              </div>
              <div class="profile-row">
                <span class="label">最后登录:</span>
                <span class="value">{{ adminInfo.lastLoginTime || '刚刚' }}</span>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>

      <!-- 基础设置 -->
      <el-col :span="12">
        <el-card class="setting-card">
          <template #header>
            <span class="card-title"><el-icon><Coin /></el-icon> 收益设置</span>
          </template>
          <el-form :model="earningsSettings" label-width="140px">
            <el-form-item label="每天收益区间 (￥)">
              <div class="earning-range-row">
                <el-input-number
                  v-model="earningsSettings.dailyMinRate"
                  :min="0"
                  :precision="2"
                  :step="0.1"
                />
                <span class="range-text">至</span>
                <el-input-number
                  v-model="earningsSettings.dailyMaxRate"
                  :min="0"
                  :precision="2"
                  :step="0.1"
                />
                <span class="unit">元 / 天</span>
              </div>
            </el-form-item>
            <el-form-item label="每日累计离线上限">
              <el-input-number
                v-model="earningsSettings.maxDailyOfflineHours"
                :min="0"
                :max="24"
                :precision="1"
                :step="0.5"
              />
              <span class="unit">小时 / 天</span>
              <div class="hint">累计离线超过该时长，当天收益为 0</div>
            </el-form-item>
            <el-form-item label="最小提现额度">
              <el-input-number v-model="earningsSettings.minWithdraw" :min="1" :precision="0" />
              <span class="unit">元</span>
            </el-form-item>
            <el-form-item label="提现手续费">
              <el-input-number v-model="earningsSettings.withdrawFee" :min="0" :max="100" :precision="1" :step="0.5" />
              <span class="unit">%</span>
            </el-form-item>
            <el-form-item label="算力兑换比例">
              <el-input-number v-model="earningsSettings.hashratePerYuan" :min="1" :precision="0" :step="10" />
              <span class="unit">聚芯算力值 = 1元</span>
              <div class="hint">配置多少聚芯算力值兑换1元人民币</div>
            </el-form-item>

            <!-- 动态邀请等级设置 -->
            <el-divider content-position="left">
              <div class="level-section-header">
                <span class="card-title"><el-icon><UserFilled /></el-icon> 代理等级及分润配置</span>
                <el-button type="success" size="small" :icon="Plus" @click="addInviteLevel">新增等级</el-button>
              </div>
            </el-divider>
            <div v-for="(lv, index) in inviteLevels" :key="lv._key" class="level-config-item">
              <el-form-item :label="'等级 ' + (index + 1)">
                <div class="level-inputs">
                  <el-input v-model="lv.name" maxlength="50" placeholder="名称" style="width: 120px" />
                  <span class="range-text">，达标</span>
                  <el-input-number v-model="lv.threshold" :min="0" :precision="0" placeholder="台数" style="width: 110px" />
                  <span class="range-text">台，分润</span>
                  <el-input-number v-model="lv.ratePercent" :min="0" :max="100" :precision="2" :step="1" style="width: 110px" />
                  <span class="unit">%</span>
                  <el-tooltip content="删除该等级" placement="top">
                    <el-button
                      class="remove-level-button"
                      type="danger"
                      plain
                      circle
                      :icon="Delete"
                      :aria-label="`删除等级 ${index + 1}`"
                      @click="removeInviteLevel(index)"
                    />
                  </el-tooltip>
                </div>
              </el-form-item>
            </div>
            <el-empty v-if="inviteLevels.length === 0" description="暂无代理等级" :image-size="72" />

            <el-form-item style="margin-top: 20px;">
              <el-button type="primary" @click="saveEarningsSettings" size="large">保存所有收益与等级设置</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>

      <!-- 提现日期限制设置 -->
      <el-col :span="12">
        <el-card class="setting-card">
          <template #header>
            <span class="card-title"><el-icon><Calendar /></el-icon> 提现日期限制</span>
          </template>
          <el-form label-width="120px">
            <el-form-item label="允许提现日">
              <el-checkbox-group v-model="withdrawAllowedDays">
                <el-checkbox :value="1">周一</el-checkbox>
                <el-checkbox :value="2">周二</el-checkbox>
                <el-checkbox :value="3">周三</el-checkbox>
                <el-checkbox :value="4">周四</el-checkbox>
                <el-checkbox :value="5">周五</el-checkbox>
                <el-checkbox :value="6">周六</el-checkbox>
                <el-checkbox :value="7">周日</el-checkbox>
              </el-checkbox-group>
              <div class="hint">不选择任何日期则表示不限制，每天都可提现</div>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="saveWithdrawDays">保存提现日期设置</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>

      <!-- 系统设置 -->
      <el-col :span="12" style="margin-top: 24px;">
        <el-card class="setting-card">
          <template #header>
            <span class="card-title"><el-icon><Setting /></el-icon> 系统设置</span>
          </template>
          <el-form :model="systemSettings" label-width="120px">
            <el-form-item label="系统名称">
              <el-input v-model="systemSettings.siteName" placeholder="聚芯算力" />
            </el-form-item>
            <el-form-item label="联系邮箱">
              <el-input v-model="systemSettings.contactEmail" placeholder="admin@example.com" />
            </el-form-item>
            <el-form-item label="客服微信">
              <el-input v-model="systemSettings.contactWechat" placeholder="juxinsuanli" />
            </el-form-item>
            <el-form-item label="工作时间">
              <el-input v-model="systemSettings.contactWorkTime" placeholder="9:00-18:00" />
            </el-form-item>
            <el-form-item label="维护模式">
              <el-switch v-model="systemSettings.maintenanceMode" />
              <span class="hint">开启后用户无法使用小程序</span>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="saveSystemSettings">保存设置</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>

      <!-- 首页轮播图 -->
      <el-col :span="12" style="margin-top: 24px;">
        <el-card class="setting-card">
          <template #header>
            <div class="card-header-flex">
              <span class="card-title"><el-icon><Picture /></el-icon> 首页轮播图</span>
              <el-button type="success" size="small" :icon="Plus" @click="addBanner">添加轮播</el-button>
            </div>
          </template>
          <div class="banner-list-admin">
            <div v-for="(banner, index) in banners" :key="index" class="banner-item-admin">
              <div class="banner-img-preview">
                <el-upload
                  action="/api/upload/image"
                  :headers="uploadHeaders"
                  :show-file-list="false"
                  :on-success="(res) => handleBannerUploadSuccess(res, index)"
                >
                  <img v-if="banner.imageUrl" :src="banner.imageUrl" class="preview-img" />
                  <div v-else class="upload-placeholder">
                    <el-icon><Plus /></el-icon>
                    <span>上传图片</span>
                  </div>
                </el-upload>
              </div>
              <div class="banner-info-inputs">
                <el-input v-model="banner.title" placeholder="标题 (例如: 新赛道 新风口)" size="small" />
                <el-input v-model="banner.subtitle" placeholder="副标题 (例如: 流量变现 | 轻松躺赚)" size="small" style="margin-top: 8px;" />
                <div class="banner-ops">
                  <el-button type="danger" size="small" link @click="removeBanner(index)">删除</el-button>
                </div>
              </div>
            </div>
            <div v-if="banners.length === 0" class="empty-text">暂无轮播图，点击上方添加</div>
            <div style="margin-top: 20px; text-align: right;">
              <el-button type="primary" @click="saveBannerSettings">保存轮播图设置</el-button>
            </div>
          </div>
        </el-card>
      </el-col>

      <!-- 安全设置 -->
      <el-col :span="12" style="margin-top: 24px;">
        <el-card class="setting-card" id="security">
          <template #header>
            <span class="card-title"><el-icon><Lock /></el-icon> 安全设置</span>
          </template>
          <el-form :model="securitySettings" label-width="120px">
            <el-form-item label="当前密码">
              <el-input v-model="securitySettings.currentPassword" type="password" show-password />
            </el-form-item>
            <el-form-item label="新密码">
              <el-input v-model="securitySettings.newPassword" type="password" show-password minlength="8" />
            </el-form-item>
            <el-form-item label="确认新密码">
              <el-input v-model="securitySettings.confirmPassword" type="password" show-password minlength="8" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="changePassword">修改密码</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { nextTick, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import axios from '../utils/request'
import { Calendar, Coin, Delete, Lock, Picture, Plus, Setting, User, UserFilled } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'

const route = useRoute()
const adminInfo = ref({})
const uploadHeaders = {
  Authorization: `Bearer ${localStorage.getItem('orin_admin_token') || ''}`
}

const earningsSettings = reactive({
  dailyMinRate: 2.4,
  dailyMaxRate: 2.4,
  maxDailyOfflineHours: 24,
  minWithdraw: 10,
  withdrawFee: 1,
  hashratePerYuan: 100  // 多少聚芯算力值=1元
})

const inviteLevels = reactive([
  { _key: 1, index: 1, name: '初级代理', threshold: 0, ratePercent: 10 },
  { _key: 2, index: 2, name: '中级代理', threshold: 10, ratePercent: 15 },
  { _key: 3, index: 3, name: '高级代理', threshold: 50, ratePercent: 20 },
  { _key: 4, index: 4, name: '金牌代理', threshold: 100, ratePercent: 30 },
  { _key: 5, index: 5, name: '核心合伙人', threshold: 500, ratePercent: 50 }
])
let nextInviteLevelKey = 6

const systemSettings = reactive({
  siteName: '聚芯算力',
  contactEmail: '',
  contactWechat: '',
  contactWorkTime: '',
  maintenanceMode: false
})

const banners = reactive([])

const securitySettings = reactive({
  currentPassword: '',
  newPassword: '',
  confirmPassword: ''
})

// 提现日期限制 (1=周一, 7=周日)
const withdrawAllowedDays = ref([])

// 加载设置
const loadSettings = async () => {
  try {
    // 加载管理员信息
    const infoRes = await axios.get('/api/admin/info')
    if (infoRes.data.code === 200) {
      adminInfo.value = infoRes.data.data
    }

    const res = await axios.get('/api/settings/all')
    if (res.data.code === 200) {
      const data = res.data.data
      // 收益设置
      if (data.earnings) {
        Object.assign(earningsSettings, data.earnings)
      }
      // 系统设置
      if (data.system) {
        Object.assign(systemSettings, data.system)
      }
        // 代理等级 (后端返回的是小数 rate，需要转换为百分比)
      if (data.inviteLevels) {
        const converted = data.inviteLevels.map(lv => ({
          ...lv,
          _key: nextInviteLevelKey++,
          ratePercent: Number(((lv.rate || 0) * 100).toFixed(2))
        }))
        inviteLevels.splice(0, inviteLevels.length, ...converted)
      }
      // 轮播图
      if (data.banners) {
        banners.splice(0, banners.length, ...data.banners)
      }
      // 提现日期限制
      if (data.withdrawAllowedDays) {
        // 将 "1,4" 格式转换为 [1, 4] 数组
        withdrawAllowedDays.value = data.withdrawAllowedDays.split(',').filter(d => d.trim()).map(d => parseInt(d.trim()))
      } else {
        withdrawAllowedDays.value = []
      }
    }
  } catch (e) {
    console.error('加载设置失败:', e)
  }
}

const saveEarningsSettings = async () => {
  const validationError = validateEarningsSettings() || validateInviteLevels()
  if (validationError) {
    ElMessage.error(validationError)
    return
  }

  try {
    // 将百分比转换回小数再发送给后端
    const levelsForBackend = inviteLevels.map((lv, index) => ({
      index: index + 1,
      name: lv.name.trim(),
      threshold: lv.threshold,
      rate: (lv.ratePercent || 0) / 100
    }))
    const payload = {
      ...earningsSettings,
      inviteLevels: levelsForBackend
    }
    const res = await axios.post('/api/settings/earnings', payload)
    if (res.data.code === 200) {
      inviteLevels.forEach((lv, index) => {
        lv.index = index + 1
        lv.name = lv.name.trim()
      })
      ElMessage.success('收益及等级设置已保存')
    } else {
      ElMessage.error(res.data.msg || '保存失败')
    }
  } catch (e) {
    ElMessage.error('网络请求失败')
  }
}

const addInviteLevel = () => {
  const previous = inviteLevels[inviteLevels.length - 1]
  inviteLevels.push({
    _key: nextInviteLevelKey++,
    index: inviteLevels.length + 1,
    name: `等级${inviteLevels.length + 1}`,
    threshold: previous ? Number(previous.threshold || 0) + 1 : 0,
    ratePercent: previous ? Number(previous.ratePercent || 0) : 0
  })
}

const removeInviteLevel = async (index) => {
  try {
    await ElMessageBox.confirm(
      `确定删除“${inviteLevels[index].name || `等级 ${index + 1}`}”吗？保存后后续等级会自动重新编号。`,
      '删除代理等级',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
    inviteLevels.splice(index, 1)
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') {
      ElMessage.error('删除操作失败')
    }
  }
}

const validateInviteLevels = () => {
  let previousThreshold = -1
  for (let index = 0; index < inviteLevels.length; index += 1) {
    const level = inviteLevels[index]
    if (!level.name || !level.name.trim()) return `等级 ${index + 1} 的名称不能为空`
    if (!Number.isInteger(level.threshold) || level.threshold < 0) return `等级 ${index + 1} 的设备门槛必须是非负整数`
    if (level.threshold <= previousThreshold) return '等级设备门槛必须按顺序严格递增'
    if (!Number.isFinite(level.ratePercent) || level.ratePercent < 0 || level.ratePercent > 100) {
      return `等级 ${index + 1} 的分润比例必须在 0% 到 100% 之间`
    }
    previousThreshold = level.threshold
  }
  return ''
}

const validateEarningsSettings = () => {
  const dailyMinRate = Number(earningsSettings.dailyMinRate)
  const dailyMaxRate = Number(earningsSettings.dailyMaxRate)
  const maxDailyOfflineHours = Number(earningsSettings.maxDailyOfflineHours)

  if (!Number.isFinite(dailyMinRate) || dailyMinRate < 0) return '每天收益最低金额不能小于 0'
  if (!Number.isFinite(dailyMaxRate) || dailyMaxRate < 0) return '每天收益最高金额不能小于 0'
  if (dailyMinRate > dailyMaxRate) return '每天收益最低金额不能大于最高金额'
  if (!Number.isFinite(maxDailyOfflineHours)
      || maxDailyOfflineHours < 0
      || maxDailyOfflineHours > 24) {
    return '每日累计离线上限必须在 0 到 24 小时之间'
  }
  return ''
}

const saveSystemSettings = async () => {
  try {
    const res = await axios.post('/api/settings/system', systemSettings)
    if (res.data.code === 200) {
      ElMessage.success('系统设置已保存')
    } else {
      ElMessage.error(res.data.msg || '保存失败')
    }
  } catch (e) {
    ElMessage.error('保存失败')
  }
}

const addBanner = () => {
  banners.push({ imageUrl: '', title: '', subtitle: '' })
}

const removeBanner = (index) => {
  banners.splice(index, 1)
}

const handleBannerUploadSuccess = (res, index) => {
  if (res.code === 200) {
    banners[index].imageUrl = res.data.url
    ElMessage.success('图片上传成功')
  } else {
    ElMessage.error(res.msg || '上传失败')
  }
}

const saveBannerSettings = async () => {
  try {
    const res = await axios.post('/api/settings/banners', banners)
    if (res.data.code === 200) {
      ElMessage.success('轮播图设置已保存')
    } else {
      ElMessage.error(res.data.msg || '保存失败')
    }
  } catch (e) {
    ElMessage.error('网络请求失败')
  }
}

const changePassword = async () => {
  if (securitySettings.newPassword !== securitySettings.confirmPassword) {
    ElMessage.error('两次输入的密码不一致')
    return
  }
  if (!securitySettings.currentPassword || !securitySettings.newPassword) {
    ElMessage.error('请填写完整的密码信息')
    return
  }
  if (securitySettings.newPassword.length < 8) {
    ElMessage.error('新密码长度不能少于8位')
    return
  }

  try {
    const res = await axios.post('/api/admin/changePassword', {
      currentPassword: securitySettings.currentPassword,
      newPassword: securitySettings.newPassword
    })
    if (res.data.code === 200) {
      ElMessage.success('密码修改成功，请重新登录')
      // 清除本地信息并强制跳转到登录页
      localStorage.removeItem('orin_admin_token')

      setTimeout(() => {
        window.location.href = '/login'
      }, 1500)
    } else {
      ElMessage.error(res.data.msg || '密码修改失败')
    }
  } catch (e) {
    ElMessage.error(e.response?.data?.msg || '网络请求失败')
  }
}

// 保存提现日期限制设置
const saveWithdrawDays = async () => {
  try {
    // 将数组 [1, 4] 转换为 "1,4" 格式
    const allowedDays = withdrawAllowedDays.value.sort((a, b) => a - b).join(',')
    const res = await axios.post('/api/settings/withdraw-days', { allowedDays })
    if (res.data.code === 200) {
      ElMessage.success('提现日期限制设置已保存')
    } else {
      ElMessage.error(res.data.msg || '保存失败')
    }
  } catch (e) {
    ElMessage.error('网络请求失败')
  }
}

onMounted(async () => {
  await loadSettings()

  // 处理锚点跳转
  nextTick(() => {
    const tab = route.query.tab
    if (tab) {
      const el = document.getElementById(tab)
      if (el) {
        el.scrollIntoView({ behavior: 'smooth' })
      }
    }
  })
})
</script>

<style scoped>
.settings-page {
  display: flex;
  flex-direction: column;
  gap: 24px;
  padding-bottom: 60px;
}

.setting-card {
  border-radius: 20px;
  border: none;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.05);
  overflow: visible;
  background: #fff;
}

:deep(.el-card__header) {
  padding: 20px 24px;
  border-bottom: 1px solid #f1f5f9;
}

.card-title {
  font-size: 17px;
  font-weight: 700;
  color: #1e293b;
  display: flex;
  align-items: center;
  gap: 10px;
}

/* 个人信息卡片优化 */
.profile-card {
  background: linear-gradient(135deg, #f8fafc 0%, #f1f5f9 100%);
}

.profile-info {
  display: flex;
  align-items: center;
  gap: 40px;
  padding: 10px 0;
}

.profile-avatar {
  background: linear-gradient(135deg, #6366f1 0%, #4f46e5 100%);
  font-size: 36px;
  font-weight: 800;
  box-shadow: 0 10px 20px rgba(99, 102, 241, 0.2);
  border: 4px solid #fff;
}

.profile-details {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.profile-row {
  display: flex;
  align-items: center;
  gap: 16px;
}

.profile-row .label {
  color: #64748b;
  width: 90px;
  font-size: 14px;
  font-weight: 600;
}

.profile-row .value {
  color: #1e293b;
  font-weight: 700;
  font-size: 15px;
}

/* 表单样式增强 */
:deep(.el-form-item__label) {
  font-weight: 600;
  color: #475569;
}

.unit {
  margin-left: 12px;
  color: #64748b;
  font-weight: 600;
  font-size: 14px;
}

.hint {
  margin-top: 6px;
  color: #94a3b8;
  font-size: 12px;
  line-height: 1.4;
}

:deep(.el-input-number) {
  width: 100%;
  max-width: 180px;
}

:deep(.el-input-number .el-input__wrapper) {
  box-shadow: 0 0 0 1px #e2e8f0 inset;
}

:deep(.el-input__wrapper) {
  border-radius: 10px;
  box-shadow: 0 0 0 1px #e2e8f0 inset;
}

/* 代理等级条目 */
.level-section-header {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.level-config-item {
  background: #f8fafc;
  padding: 16px;
  border-radius: 12px;
  margin-bottom: 12px;
  border: 1px solid #f1f5f9;
  transition: all 0.2s;
}

.level-config-item:hover {
  border-color: #c7d2fe;
  background: #f5f7ff;
}

.level-inputs {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.range-text {
  color: #64748b;
  font-size: 13px;
}

.earning-range-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.earning-range-row .unit {
  margin-left: 0;
}

.remove-level-button {
  flex: 0 0 auto;
  margin-left: auto;
}

/* 轮播图管理 */
.card-header-flex {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.banner-item-admin {
  display: flex;
  gap: 20px;
  padding: 20px;
  background: #fff;
  border: 1px solid #f1f5f9;
  border-radius: 16px;
  margin-bottom: 16px;
  transition: all 0.3s;
}

.banner-item-admin:hover {
  box-shadow: 0 5px 15px rgba(0,0,0,0.05);
  border-color: #e2e8f0;
}

.banner-img-preview {
  width: 150px;
  height: 90px;
  flex-shrink: 0;
  border-radius: 12px;
  overflow: hidden;
  border: 2px dashed #e2e8f0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f8fafc;
  cursor: pointer;
  transition: all 0.2s;
}

.banner-img-preview:hover {
  border-color: #6366f1;
  background: #f5f3ff;
}

.preview-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.banner-info-inputs {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 10px;
}

.banner-ops {
  text-align: right;
}

.empty-text {
  text-align: center;
  color: #94a3b8;
  padding: 40px 0;
  background: #f8fafc;
  border-radius: 16px;
  border: 2px dashed #e2e8f0;
}

/* 按钮通用样式提升 */
.el-button--large {
  padding: 12px 30px;
  border-radius: 14px;
  font-weight: 700;
}
</style>
