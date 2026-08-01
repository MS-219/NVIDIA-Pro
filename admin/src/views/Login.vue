<template>
  <main class="login-page">
    <div class="page-grid" aria-hidden="true"></div>
    <div class="scan-line" aria-hidden="true"></div>
    <header class="login-header">
      <div class="brand-lockup">
        <span class="brand-mark"><el-icon><Cpu /></el-icon></span>
        <div><strong>聚芯Orin</strong><span>聚芯边缘算力平台</span></div>
      </div>
      <div class="header-status">
        <span class="status-pulse"></span>
        <span>独立生产环境</span>
        <span class="status-code">SECURE / ARM64</span>
      </div>
    </header>

    <section class="login-content">
      <div class="system-brief">
        <div class="brief-meta">
          <span class="eyebrow">JETSON ORIN / CONTROL PLANE</span>
          <span class="meta-code">NODE-FLEET 01</span>
        </div>

        <div class="brief-copy">
          <h1>统一调度每一台<br /><span>边缘算力节点</span></h1>
          <p>面向 ARM64 Orin 集群的独立任务调度、运行监控与版本控制台。</p>
        </div>

        <div class="fleet-schematic" aria-label="聚芯 Orin 边缘节点控制架构">
          <div class="schematic-corner corner-tl"></div>
          <div class="schematic-corner corner-tr"></div>
          <div class="schematic-corner corner-bl"></div>
          <div class="schematic-corner corner-br"></div>
          <span class="schematic-number">CONTROL FABRIC / 01</span>
          <div class="architecture-flow">
            <div class="flow-module">
              <span class="module-index">01</span>
              <div><strong>设备接入</strong><small>DEVICE INGEST</small></div>
            </div>
            <span class="flow-link"><i></i></span>
            <div class="flow-module core-module">
              <img src="/nvidia-mark.svg" alt="NVIDIA" />
              <div><strong>JETSON ORIN</strong><small>CONTROL CORE</small></div>
            </div>
            <span class="flow-link"><i></i></span>
            <div class="flow-module">
              <span class="module-index">03</span>
              <div><strong>任务调度</strong><small>JOB SCHEDULER</small></div>
            </div>
          </div>
          <div class="service-bus">
            <span>运行监控</span>
            <span>版本控制</span>
            <span>收益结算</span>
            <span>设备管理</span>
          </div>
        </div>

        <dl class="runtime-facts">
          <div><dt>平台架构</dt><dd>ARM64</dd><small>JETSON ORIN</small></div>
          <div><dt>系统基线</dt><dd>L4T 36.4.x</dd><small>CUDA READY</small></div>
          <div><dt>链路状态</dt><dd><span class="online-dot"></span>READY</dd><small>CONTROL PLANE</small></div>
        </dl>
      </div>

      <div class="login-panel">
        <div class="panel-topline"><span>ACCESS TERMINAL</span><span>AUTH / 01</span></div>
        <div class="panel-heading">
          <div>
            <span class="panel-kicker">CONTROL PLANE LOGIN</span>
            <h2>管理员登录</h2>
            <p>使用 Orin 平台专属账号进入控制台</p>
          </div>
          <span class="panel-status"><span></span>READY</span>
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

        <div class="security-note"><el-icon><Lock /></el-icon><span>本系统与原聚芯平台账号及数据完全隔离</span><strong>256-BIT</strong></div>
      </div>
    </section>

    <footer class="login-footer">
      <span>JUXIN ORIN INFRASTRUCTURE</span>
      <span>EDGE COMPUTE / PRIVATE CONTROL</span>
    </footer>
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
  position: relative;
  overflow: hidden;
  min-height: 100vh;
  color: #1f2937;
  background: #f5f7f3;
}

.page-grid {
  position: absolute;
  inset: 0;
  opacity: 0.5;
  background-image:
    linear-gradient(#dfe5da 1px, transparent 1px),
    linear-gradient(90deg, #dfe5da 1px, transparent 1px);
  background-size: 52px 52px;
  pointer-events: none;
}

.scan-line {
  position: absolute;
  left: 0;
  right: 0;
  z-index: 1;
  height: 1px;
  background: #76b900;
  box-shadow: 0 0 12px rgba(118, 185, 0, 0.35);
  opacity: 0;
  pointer-events: none;
  animation: page-scan 8s linear infinite;
}

.login-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  position: relative;
  z-index: 3;
  height: 76px;
  padding: 0 clamp(24px, 5vw, 72px);
  background: #ffffff;
  border-bottom: 1px solid #e1e6dd;
}

.login-header::before {
  content: '';
  position: absolute;
  top: 0;
  right: 0;
  left: 0;
  height: 4px;
  background: #76b900;
}

.brand-lockup {
  display: flex;
  align-items: center;
  gap: 12px;
}

.brand-mark {
  display: grid;
  width: 40px;
  height: 40px;
  color: #10120f;
  background: #76b900;
  border: 1px solid #68a400;
  border-radius: 4px;
  font-size: 22px;
  place-items: center;
}

.brand-lockup div {
  display: flex;
  flex-direction: column;
}

.brand-lockup strong {
  color: #18211c;
  font-size: 14px;
}

.brand-lockup span:last-child {
  margin-top: 2px;
  color: #6b7280;
  font-size: 10px;
}

.header-status {
  display: flex;
  align-items: center;
  gap: 9px;
  color: #59635b;
  font-size: 11px;
}

.status-pulse,
.online-dot,
.panel-status span {
  display: inline-block;
  width: 7px;
  height: 7px;
  background: #76b900;
  border-radius: 50%;
  box-shadow: 0 0 0 4px rgba(118, 185, 0, 0.12);
}

.status-code {
  padding-left: 10px;
  color: #788078;
  border-left: 1px solid #d9dfd5;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 10px;
}

.login-content {
  display: grid;
  align-items: center;
  position: relative;
  z-index: 2;
  min-height: calc(100vh - 112px);
  padding: 28px clamp(24px, 7vw, 112px) 58px;
  grid-template-columns: minmax(600px, 1fr) minmax(370px, 430px);
  gap: clamp(46px, 7vw, 116px);
}

.system-brief {
  display: grid;
  width: 100%;
  max-width: 820px;
  grid-template-columns: minmax(330px, 1.05fr) minmax(250px, 0.95fr);
  grid-template-rows: auto auto auto;
  column-gap: clamp(20px, 3vw, 48px);
}

.brief-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  grid-column: 1 / -1;
  padding: 0 0 14px;
  border-bottom: 1px solid #cfd7cc;
}

.eyebrow,
.meta-code,
.panel-kicker,
.panel-topline,
.stage-label,
.runtime-facts small,
.login-footer {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}

.eyebrow {
  color: #578d00;
  font-size: 11px;
  font-weight: 800;
}

.meta-code {
  color: #808a80;
  font-size: 9px;
}

.brief-copy {
  grid-column: 1 / -1;
  grid-row: 2;
  padding: 26px 0 22px;
}

.brief-copy h1 {
  margin: 0;
  color: #1c2732;
  font-size: clamp(38px, 3.5vw, 58px);
  line-height: 1.14;
  letter-spacing: 0;
}

.brief-copy h1 span {
  display: inline-block;
  margin-right: 10px;
  color: #5d9600;
}

.brief-copy p {
  max-width: 620px;
  margin: 18px 0 0;
  color: #64748b;
  font-size: 14px;
  line-height: 1.8;
}

.fleet-schematic {
  position: relative;
  min-height: 228px;
  overflow: hidden;
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid #cfd7cc;
  border-radius: 5px;
  grid-column: 1 / -1;
  grid-row: 3;
}

.fleet-schematic::before,
.fleet-schematic::after {
  content: '';
  position: absolute;
  background: #e5ebe2;
}

.fleet-schematic::before {
  top: 50%;
  right: 24px;
  left: 24px;
  height: 1px;
}

.fleet-schematic::after {
  top: 24px;
  bottom: 24px;
  left: 50%;
  width: 1px;
}

.schematic-corner {
  position: absolute;
  z-index: 4;
  width: 18px;
  height: 18px;
  border-color: #76b900;
}

.corner-tl { top: 12px; left: 12px; border-top: 2px solid; border-left: 2px solid; }
.corner-tr { top: 12px; right: 12px; border-top: 2px solid; border-right: 2px solid; }
.corner-bl { bottom: 12px; left: 12px; border-bottom: 2px solid; border-left: 2px solid; }
.corner-br { right: 12px; bottom: 12px; border-right: 2px solid; border-bottom: 2px solid; }

.schematic-number {
  position: absolute;
  top: 15px;
  right: 24px;
  z-index: 3;
  color: #899389;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 8px;
}

.architecture-flow {
  position: absolute;
  top: 68px;
  right: 36px;
  left: 36px;
  z-index: 2;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
}

.flow-module {
  display: flex;
  align-items: center;
  min-width: 146px;
  min-height: 58px;
  padding: 10px 12px;
  background: #f9fbf8;
  border: 1px solid #cbd5c8;
  border-radius: 4px;
}

.core-module {
  min-width: 192px;
  background: #18211c;
  border-color: #18211c;
  box-shadow: 0 8px 18px rgba(24, 33, 28, 0.16);
}

.module-index {
  display: grid;
  width: 26px;
  height: 26px;
  margin-right: 10px;
  color: #578d00;
  border: 1px solid #b9c8b3;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 9px;
  place-items: center;
}

.flow-module strong,
.flow-module small {
  display: block;
}

.flow-module strong {
  color: #263329;
  font-size: 12px;
}

.flow-module small {
  margin-top: 5px;
  color: #879287;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 8px;
}

.core-module img {
  width: 32px;
  height: 32px;
  margin-right: 10px;
}

.core-module strong { color: #ffffff; }
.core-module small { color: #a9ba9f; }

.flow-link {
  position: relative;
  width: 48px;
  height: 1px;
  flex: 0 0 48px;
  background: #76b900;
}

.flow-link::after {
  content: '';
  position: absolute;
  top: -3px;
  right: -1px;
  width: 7px;
  height: 7px;
  border-top: 1px solid #76b900;
  border-right: 1px solid #76b900;
  transform: rotate(45deg);
}

.service-bus {
  position: absolute;
  right: 42px;
  bottom: 25px;
  left: 42px;
  z-index: 2;
  display: flex;
  justify-content: space-between;
  color: #6f7c6f;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 8px;
}

.service-bus span::before {
  content: '';
  display: inline-block;
  width: 5px;
  height: 5px;
  margin: 0 6px 1px 0;
  background: #76b900;
  border-radius: 50%;
}

.runtime-facts {
  display: grid;
  margin: 18px 0 0;
  border-top: 1px solid #cfd7cc;
  border-bottom: 1px solid #cfd7cc;
  grid-column: 1 / -1;
  grid-template-columns: repeat(3, 1fr);
}

.runtime-facts div {
  padding: 14px 16px 14px 0;
}

.runtime-facts div + div {
  padding-left: 18px;
  border-left: 1px solid #d7ddd2;
}

.runtime-facts dt {
  margin-bottom: 6px;
  color: #6b7280;
  font-size: 10px;
}

.runtime-facts dd {
  margin: 0;
  color: #1f2937;
  font-size: 13px;
  font-weight: 800;
}

.runtime-facts small {
  display: block;
  margin-top: 5px;
  color: #929991;
  font-size: 8px;
}

.online-dot {
  margin-right: 7px;
  box-shadow: none;
}

.login-panel {
  position: relative;
  padding: 0 32px 28px;
  color: #1d201b;
  background: #ffffff;
  border: 1px solid #cfd7cc;
  border-top: 3px solid #76b900;
  border-radius: 6px;
  box-shadow: 0 24px 60px rgba(31, 41, 55, 0.13);
}

.login-panel::after {
  content: '';
  position: absolute;
  right: -1px;
  bottom: -1px;
  width: 34px;
  height: 34px;
  border-right: 2px solid #76b900;
  border-bottom: 2px solid #76b900;
  pointer-events: none;
}

.panel-topline {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 0 -32px;
  padding: 10px 16px;
  color: #6e766d;
  background: #f5f7f3;
  border-bottom: 1px solid #e1e6dd;
  font-size: 8px;
}

.panel-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
  margin: 28px 0 30px;
}

.panel-kicker {
  display: block;
  margin-bottom: 7px;
  color: #619800;
  font-size: 9px;
  font-weight: 800;
}

.panel-heading h2 {
  margin: 0;
  color: #18211c;
  font-size: 24px;
}

.panel-heading p {
  margin: 7px 0 0;
  color: #848a7f;
  font-size: 11px;
}

.panel-status {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 6px 8px;
  color: #578d00;
  background: #f2f7eb;
  border: 1px solid #d5e2c8;
  border-radius: 3px;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 8px;
  font-weight: 800;
}

.panel-status span {
  width: 6px;
  height: 6px;
  box-shadow: none;
}

.field-label {
  display: block;
  margin: 0 0 7px;
  color: #51564d;
  font-size: 11px;
  font-weight: 700;
}

.login-form :deep(.el-input__wrapper) {
  min-height: 48px;
  border-radius: 3px;
  box-shadow: 0 0 0 1px #d8dcd3 inset;
}

.login-form :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px #76a900 inset, 0 0 0 3px rgba(118, 185, 0, 0.12);
}

.form-options {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 4px 0 22px;
  color: #969b92;
  font-size: 10px;
}

.login-button {
  width: 100%;
  height: 48px;
  overflow: hidden;
  border-color: #5f9500;
  border-radius: 3px;
  background: #5f9500;
  font-weight: 800;
}

.login-button:hover {
  box-shadow: 0 8px 18px rgba(95, 149, 0, 0.2);
}

.login-button .el-icon {
  margin-left: 7px;
}

.security-note {
  display: grid;
  align-items: center;
  margin-top: 24px;
  padding-top: 18px;
  color: #8b9087;
  border-top: 1px solid #dfe2db;
  font-size: 10px;
  grid-template-columns: auto 1fr auto;
  gap: 7px;
}

.security-note strong {
  color: #65705f;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 8px;
}

.login-footer {
  position: absolute;
  right: clamp(24px, 5vw, 72px);
  bottom: 16px;
  left: clamp(24px, 5vw, 72px);
  z-index: 2;
  display: flex;
  justify-content: space-between;
  color: #899188;
  font-size: 8px;
}

@keyframes page-scan {
  0% { top: 76px; opacity: 0; }
  8% { opacity: 0.35; }
  60% { opacity: 0.15; }
  100% { top: 100%; opacity: 0; }
}

@media (max-width: 1180px) {
  .login-content {
    grid-template-columns: minmax(480px, 1fr) minmax(350px, 410px);
    gap: 38px;
    padding-right: 44px;
    padding-left: 44px;
  }

  .system-brief {
    grid-template-columns: minmax(280px, 1fr) minmax(190px, 0.8fr);
  }

  .flow-module {
    min-width: 122px;
  }

  .core-module {
    min-width: 164px;
  }

  .flow-link {
    width: 24px;
    flex-basis: 24px;
  }

  .brief-copy h1 {
    font-size: 38px;
  }
}

@media (max-width: 920px) {
  .system-brief {
    display: none;
  }

  .login-content {
    justify-content: center;
    padding: 32px 18px 54px;
    grid-template-columns: minmax(0, 430px);
  }

  .login-panel {
    width: 100%;
  }

  .status-code {
    display: none;
  }
}

@media (max-width: 520px) {
  .login-header {
    height: 68px;
    padding: 0 18px;
  }

  .header-status > span:not(.status-pulse) {
    display: none;
  }

  .login-content {
    min-height: calc(100vh - 68px);
    padding: 22px 14px 48px;
  }

  .login-panel {
    padding: 0 20px 22px;
  }

  .panel-topline {
    margin: 0 -20px;
  }

  .panel-heading {
    margin: 24px 0 26px;
  }

  .panel-status {
    display: none;
  }

  .form-options {
    align-items: flex-start;
    flex-direction: column;
    gap: 8px;
  }

  .security-note {
    grid-template-columns: auto 1fr;
  }

  .security-note strong {
    display: none;
  }

  .login-footer {
    right: 18px;
    left: 18px;
  }

  .login-footer span:last-child {
    display: none;
  }
}

@media (prefers-reduced-motion: reduce) {
  .scan-line {
    animation: none;
  }
}
</style>
