<template>
  <main class="login-page">
    <header class="login-header">
      <div class="brand-lockup">
        <span class="brand-mark"><el-icon><Cpu /></el-icon></span>
        <div><strong>ORIN GRID</strong><span>聚芯边缘算力平台</span></div>
      </div>
      <span class="environment">独立生产环境</span>
    </header>

    <section class="login-content">
      <div class="system-brief">
        <span class="eyebrow">JETSON ORIN NODE CONTROL</span>
        <h1>统一管理每一台<br />边缘 GPU 节点</h1>
        <p>面向 ARM64 Orin 集群的独立任务调度、运行监控与版本控制台。</p>

        <dl class="runtime-facts">
          <div><dt>平台架构</dt><dd>ARM64</dd></div>
          <div><dt>系统基线</dt><dd>L4T 36.4.x</dd></div>
          <div><dt>运行状态</dt><dd><span class="online-dot"></span>READY</dd></div>
        </dl>
      </div>

      <div class="login-panel">
        <div class="panel-heading">
          <span class="panel-index">01</span>
          <div><h2>管理员登录</h2><p>使用 Orin 平台专属账号进入控制台</p></div>
        </div>

        <el-form ref="formRef" :model="form" :rules="rules" class="login-form" @submit.prevent>
          <label class="field-label">账号</label>
          <el-form-item prop="username">
            <el-input v-model="form.username" :prefix-icon="User" placeholder="请输入管理员账号" size="large" autocomplete="username" />
          </el-form-item>

          <label class="field-label">密码</label>
          <el-form-item prop="password">
            <el-input v-model="form.password" :prefix-icon="Lock" type="password" placeholder="请输入登录密码" size="large" show-password autocomplete="current-password" @keyup.enter="handleLogin" />
          </el-form-item>

          <div class="form-options">
            <el-checkbox v-model="rememberMe">记住账号</el-checkbox>
            <span>权限由独立后台统一管理</span>
          </div>

          <el-button class="login-button" type="primary" size="large" :loading="loading" @click="handleLogin">
            进入控制台 <el-icon v-if="!loading"><Right /></el-icon>
          </el-button>
        </el-form>

        <div class="security-note"><el-icon><Lock /></el-icon> 本系统与原聚芯平台账号及数据完全隔离</div>
      </div>
    </section>
  </main>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Cpu, Lock, Right, User } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import request from '../utils/request'

const router = useRouter()
const formRef = ref(null)
const loading = ref(false)
const rememberMe = ref(false)
const form = reactive({ username: '', password: '' })
const rules = {
  username: [{ required: true, message: '请输入管理员账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入登录密码', trigger: 'blur' }],
}

const handleLogin = async () => {
  if (loading.value || !formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    localStorage.removeItem('orin_admin_token')
    const res = await request.post('/api/admin/login', form)
    const payload = res?.data || res
    if (Number(payload.code) !== 200 || !payload.data?.token) {
      ElMessage.error(payload.msg || '登录失败')
      return
    }

    localStorage.setItem('orin_admin_token', payload.data.token)
    if (rememberMe.value) localStorage.setItem('orin_admin_remember_user', form.username)
    else localStorage.removeItem('orin_admin_remember_user')
    ElMessage.success('已进入 Orin 控制台')
    router.push('/')
  } catch (error) {
    console.error(error)
    ElMessage.error('无法连接独立后台服务')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  const remembered = localStorage.getItem('orin_admin_remember_user')
  if (remembered) {
    form.username = remembered
    rememberMe.value = true
  }
})
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  color: #f6f7f3;
  background: #10120f;
  position: relative;
  overflow: hidden;
}

.login-page::before {
  content: '';
  position: absolute;
  inset: 0;
  opacity: 0.16;
  background-image: linear-gradient(#686e641f 1px, transparent 1px), linear-gradient(90deg, #686e641f 1px, transparent 1px);
  background-size: 48px 48px;
  pointer-events: none;
}

.login-page::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  top: 0;
  height: 5px;
  background: #76b900;
}

.login-header {
  height: 78px;
  padding: 0 clamp(24px, 5vw, 72px);
  display: flex;
  align-items: center;
  justify-content: space-between;
  position: relative;
  z-index: 1;
  border-bottom: 1px solid #30332e;
}

.brand-lockup { display: flex; align-items: center; gap: 12px; }
.brand-mark { width: 38px; height: 38px; display: grid; place-items: center; color: #11130f; background: #76b900; border-radius: 5px; font-size: 21px; }
.brand-lockup div { display: flex; flex-direction: column; }
.brand-lockup strong { font-size: 14px; }
.brand-lockup span:last-child { color: #8e9489; font-size: 10px; }
.environment { color: #8e9489; font-size: 11px; }

.login-content {
  min-height: calc(100vh - 78px);
  padding: 44px clamp(24px, 8vw, 128px);
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(360px, 430px);
  gap: clamp(48px, 9vw, 144px);
  align-items: center;
  position: relative;
  z-index: 1;
}

.system-brief { max-width: 650px; }
.eyebrow { display: block; margin-bottom: 20px; color: #76b900; font-size: 11px; font-weight: 800; }
.system-brief h1 { margin: 0; font-size: clamp(38px, 5vw, 66px); line-height: 1.12; letter-spacing: 0; }
.system-brief p { max-width: 520px; margin: 22px 0 0; color: #a3a99e; font-size: 15px; line-height: 1.8; }

.runtime-facts { margin: 42px 0 0; display: grid; grid-template-columns: repeat(3, 1fr); border-top: 1px solid #343832; border-bottom: 1px solid #343832; }
.runtime-facts div { padding: 18px 16px 18px 0; }
.runtime-facts div + div { padding-left: 18px; border-left: 1px solid #343832; }
.runtime-facts dt { margin-bottom: 7px; color: #70766c; font-size: 10px; }
.runtime-facts dd { margin: 0; color: #e9ebe6; font-size: 13px; font-weight: 700; }
.online-dot { width: 7px; height: 7px; margin-right: 7px; display: inline-block; border-radius: 50%; background: #76b900; }

.login-panel { padding: 32px; color: #1d201b; background: #f7f8f5; border-radius: 7px; box-shadow: 0 24px 60px rgba(0, 0, 0, 0.28); }
.panel-heading { display: flex; gap: 14px; margin-bottom: 28px; }
.panel-index { width: 35px; height: 35px; display: grid; place-items: center; border: 1px solid #cfd3ca; border-radius: 4px; color: #568800; font-size: 11px; font-weight: 800; }
.panel-heading h2 { margin: 0; font-size: 20px; }
.panel-heading p { margin: 5px 0 0; color: #848a7f; font-size: 11px; }
.field-label { display: block; margin: 0 0 7px; color: #51564d; font-size: 11px; font-weight: 700; }
.login-form :deep(.el-input__wrapper) { min-height: 44px; border-radius: 4px; box-shadow: 0 0 0 1px #d8dcd3 inset; }
.login-form :deep(.el-input__wrapper.is-focus) { box-shadow: 0 0 0 1px #76a900 inset, 0 0 0 3px rgba(118, 185, 0, 0.12); }
.form-options { margin: 2px 0 20px; display: flex; justify-content: space-between; align-items: center; color: #969b92; font-size: 10px; }
.login-button { width: 100%; height: 46px; border-radius: 4px; background: #5f9500; border-color: #5f9500; font-weight: 700; }
.login-button .el-icon { margin-left: 7px; }
.security-note { margin-top: 22px; padding-top: 18px; display: flex; align-items: center; gap: 7px; color: #8b9087; border-top: 1px solid #dfe2db; font-size: 10px; }

@media (max-width: 880px) {
  .system-brief { display: none; }
  .login-content { grid-template-columns: minmax(0, 430px); justify-content: center; padding: 32px 18px; }
  .login-panel { width: 100%; padding: 26px 22px; }
  .environment { display: none; }
}
</style>
