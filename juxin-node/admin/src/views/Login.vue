<template>
  <main class="login-page">
    <div class="ambient ambient-one" aria-hidden="true"></div>
    <div class="ambient ambient-two" aria-hidden="true"></div>

    <header class="login-header">
      <div class="brand-lockup">
        <span class="brand-mark"><img src="/favicon.svg" alt="聚芯节点" /></span>
        <div class="brand-copy">
          <strong>聚芯节点</strong>
          <span>边缘设备运营平台</span>
        </div>
      </div>
      <div class="header-state"><span></span>管理服务正常</div>
    </header>

    <section class="login-layout">
      <div class="visual-column">
        <div class="hero-copy">
          <span class="eyebrow"><i></i> JUXIN NODE CONTROL</span>
          <h1>让每一台边缘设备<br /><em>清晰、稳定、可控</em></h1>
          <p>统一管理设备、用户、收益与任务调度，实时掌握聚芯节点的运行状态。</p>
        </div>

        <div class="compute-map" aria-label="聚芯节点管理架构示意">
          <div class="map-glow" aria-hidden="true"></div>
          <div class="map-grid" aria-hidden="true"></div>

          <div class="core-node">
            <span class="core-logo">JX</span>
            <div><small>核心平台</small><strong>聚芯节点</strong></div>
            <span class="core-state">运行中</span>
          </div>

          <div class="satellite node-device"><el-icon><Monitor /></el-icon><span>设备管理</span><small>节点状态与绑定</small></div>
          <div class="satellite node-user"><el-icon><UserFilled /></el-icon><span>用户运营</span><small>账户与团队</small></div>
          <div class="satellite node-income"><el-icon><Coin /></el-icon><span>收益结算</span><small>每日收益与分润</small></div>
          <div class="satellite node-task"><el-icon><Connection /></el-icon><span>任务调度</span><small>推理任务与策略</small></div>

          <span class="connector connector-a" aria-hidden="true"></span>
          <span class="connector connector-b" aria-hidden="true"></span>
          <span class="connector connector-c" aria-hidden="true"></span>
          <span class="connector connector-d" aria-hidden="true"></span>
        </div>

        <div class="platform-facts">
          <div><strong>ARM64</strong><span>平台架构</span></div>
          <div><strong>RK3588</strong><span>节点平台</span></div>
          <div><strong>实时同步</strong><span>设备状态</span></div>
        </div>
      </div>

      <aside class="login-card">
        <div class="card-heading">
          <span class="card-icon"><el-icon><Lock /></el-icon></span>
          <div>
            <span class="card-kicker">管理员入口</span>
            <h2>欢迎回来</h2>
            <p>登录聚芯节点管理控制台</p>
          </div>
        </div>

        <el-form ref="formRef" :model="form" :rules="rules" class="login-form" label-position="top" @submit.prevent>
          <el-form-item label="管理员账号" prop="username">
            <el-input
              v-model="form.username"
              :prefix-icon="User"
              placeholder="请输入管理员账号"
              size="large"
              autocomplete="username"
            />
          </el-form-item>

          <el-form-item label="登录密码" prop="password">
            <el-input
              v-model="form.password"
              :prefix-icon="Lock"
              type="password"
              placeholder="请输入登录密码"
              size="large"
              show-password
              autocomplete="current-password"
              @keyup.enter="handleLogin"
            />
          </el-form-item>

          <div class="form-options">
            <el-checkbox v-model="rememberMe">记住账号</el-checkbox>
            <span><i></i>安全连接</span>
          </div>

          <el-button class="login-button" type="primary" size="large" :loading="loading" @click="handleLogin">
            进入管理后台 <el-icon v-if="!loading"><Right /></el-icon>
          </el-button>
        </el-form>

        <div class="security-note">
          <el-icon><CircleCheck /></el-icon>
          <span>账号和数据由独立生产环境保护</span>
        </div>
      </aside>
    </section>

    <footer class="login-footer">
      <span>© 2026 聚芯节点</span>
      <span>边缘算力 · 独立部署 · 安全管理</span>
    </footer>
  </main>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { CircleCheck, Coin, Connection, Lock, Monitor, Right, User, UserFilled } from '@element-plus/icons-vue'
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
    localStorage.removeItem('juxin_node_admin_token')
    const res = await request.post('/api/admin/login', form)
    const payload = res?.data || res
    if (Number(payload.code) !== 200 || !payload.data?.token) {
      ElMessage.error(payload.msg || '登录失败')
      return
    }

    localStorage.setItem('juxin_node_admin_token', payload.data.token)
    if (rememberMe.value) localStorage.setItem('juxin_node_admin_remember_user', form.username)
    else localStorage.removeItem('juxin_node_admin_remember_user')
    ElMessage.success('已进入聚芯节点管理后台')
    router.push('/')
  } catch (error) {
    console.error(error)
    ElMessage.error('无法连接后台服务，请稍后重试')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  const remembered = localStorage.getItem('juxin_node_admin_remember_user')
  if (remembered) {
    form.username = remembered
    rememberMe.value = true
  }
})
</script>

<style scoped>
.login-page {
  position: relative;
  min-height: 100dvh;
  overflow: hidden auto;
  color: #172119;
  background:
    radial-gradient(circle at 14% 12%, rgba(118, 185, 0, 0.1), transparent 28%),
    linear-gradient(145deg, #f7faf5 0%, #ffffff 44%, #f4f7f2 100%);
}

.login-page::before {
  position: absolute;
  inset: 0;
  content: '';
  pointer-events: none;
  background-image:
    linear-gradient(rgba(35, 55, 41, 0.035) 1px, transparent 1px),
    linear-gradient(90deg, rgba(35, 55, 41, 0.035) 1px, transparent 1px);
  background-size: 56px 56px;
  mask-image: linear-gradient(to bottom, rgba(0, 0, 0, 0.72), transparent 78%);
}

.ambient {
  position: absolute;
  border-radius: 50%;
  filter: blur(6px);
  pointer-events: none;
}

.ambient-one { top: -190px; left: -130px; width: 460px; height: 460px; border: 1px solid rgba(118, 185, 0, 0.18); }
.ambient-two { right: -180px; bottom: -230px; width: 560px; height: 560px; background: rgba(118, 185, 0, 0.045); }

.login-header,
.login-footer,
.login-layout {
  position: relative;
  z-index: 2;
  width: min(1440px, calc(100% - 64px));
  margin: 0 auto;
}

.login-header {
  height: 92px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.brand-lockup { display: flex; align-items: center; gap: 13px; }
.brand-mark { width: 44px; height: 44px; overflow: hidden; border-radius: 12px; box-shadow: 0 10px 24px rgba(75, 112, 26, 0.18); }
.brand-mark img { width: 100%; height: 100%; display: block; }
.brand-copy { display: flex; flex-direction: column; }
.brand-copy strong { font-size: 17px; font-weight: 700; letter-spacing: -0.02em; }
.brand-copy span { margin-top: 2px; color: #748078; font-size: 11px; }
.header-state { display: flex; align-items: center; gap: 9px; color: #68736b; font-size: 12px; font-weight: 600; }
.header-state span { width: 8px; height: 8px; border-radius: 50%; background: #76b900; box-shadow: 0 0 0 5px rgba(118, 185, 0, 0.11); }

.login-layout {
  min-height: calc(100dvh - 156px);
  padding: 34px 0 56px;
  display: grid;
  align-items: center;
  grid-template-columns: minmax(0, 1.25fr) minmax(380px, 460px);
  gap: clamp(64px, 8vw, 140px);
}

.visual-column { min-width: 0; }
.hero-copy { max-width: 700px; }
.eyebrow { display: inline-flex; align-items: center; gap: 9px; color: #5d8f0c; font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 11px; font-weight: 700; letter-spacing: 0.12em; }
.eyebrow i { width: 28px; height: 1px; background: #76b900; }
.hero-copy h1 { margin: 22px 0 18px; color: #162019; font-size: clamp(42px, 4.7vw, 70px); line-height: 1.08; letter-spacing: -0.055em; text-wrap: balance; }
.hero-copy h1 em { color: #5b9000; font-style: normal; }
.hero-copy p { max-width: 590px; margin: 0; color: #657168; font-size: 16px; line-height: 1.8; text-wrap: pretty; }

.compute-map {
  position: relative;
  width: min(720px, 100%);
  height: 280px;
  margin-top: 38px;
  overflow: hidden;
  border: 1px solid rgba(74, 92, 78, 0.12);
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.72);
  box-shadow: 0 30px 80px rgba(41, 60, 45, 0.09), inset 0 1px rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(18px);
}

.map-grid { position: absolute; inset: 0; opacity: 0.38; background-image: radial-gradient(circle, rgba(64, 83, 68, 0.22) 1px, transparent 1px); background-size: 20px 20px; mask-image: radial-gradient(circle at center, #000, transparent 78%); }
.map-glow { position: absolute; top: 50%; left: 50%; width: 300px; height: 300px; transform: translate(-50%, -50%); border-radius: 50%; background: radial-gradient(circle, rgba(118, 185, 0, 0.17), transparent 67%); }

.core-node,
.satellite { position: absolute; z-index: 2; display: flex; align-items: center; background: rgba(255, 255, 255, 0.94); border: 1px solid rgba(68, 84, 72, 0.14); box-shadow: 0 12px 32px rgba(43, 61, 47, 0.08); }
.core-node { top: 50%; left: 50%; width: 250px; min-height: 82px; padding: 14px 16px; gap: 13px; transform: translate(-50%, -50%); border-radius: 16px; }
.core-logo { width: 46px; height: 46px; display: grid; flex: 0 0 auto; place-items: center; border-radius: 12px; background: #111711; }
.core-logo img { width: 28px; max-height: 28px; filter: brightness(0) invert(1); }
.core-node div { min-width: 0; display: flex; flex: 1; flex-direction: column; }
.core-node small { color: #839087; font-size: 10px; }
.core-node strong { margin-top: 3px; color: #1c281f; font-size: 13px; letter-spacing: 0.04em; }
.core-state { padding: 5px 7px; color: #507e0b; border-radius: 6px; background: rgba(118, 185, 0, 0.1); font-size: 9px; font-weight: 700; }

.satellite { width: 152px; min-height: 58px; padding: 10px 12px; display: grid; grid-template-columns: 30px 1fr; grid-template-rows: auto auto; column-gap: 9px; border-radius: 13px; }
.satellite .el-icon { grid-row: 1 / 3; color: #5f930a; font-size: 20px; }
.satellite span { color: #263229; font-size: 11px; font-weight: 700; }
.satellite small { color: #89928b; font-size: 9px; }
.node-device { top: 28px; left: 28px; }
.node-user { right: 28px; top: 28px; }
.node-income { bottom: 28px; left: 28px; }
.node-task { right: 28px; bottom: 28px; }

.connector { position: absolute; z-index: 1; width: 112px; height: 1px; background: linear-gradient(90deg, rgba(118, 185, 0, 0.1), rgba(118, 185, 0, 0.66)); transform-origin: center; }
.connector::after { position: absolute; right: 0; top: -2px; width: 5px; height: 5px; content: ''; border-radius: 50%; background: #76b900; box-shadow: 0 0 0 4px rgba(118, 185, 0, 0.1); }
.connector-a { top: 91px; left: 173px; transform: rotate(19deg); }
.connector-b { top: 91px; right: 173px; transform: rotate(161deg); }
.connector-c { bottom: 91px; left: 173px; transform: rotate(-19deg); }
.connector-d { right: 173px; bottom: 91px; transform: rotate(199deg); }

.platform-facts { width: min(720px, 100%); margin-top: 18px; display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px; }
.platform-facts div { padding: 13px 16px; display: flex; align-items: baseline; justify-content: space-between; gap: 10px; border-bottom: 1px solid #dfe5df; }
.platform-facts strong { color: #27342b; font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 11px; }
.platform-facts span { color: #8a958d; font-size: 10px; }

.login-card {
  padding: 38px;
  border: 1px solid rgba(66, 83, 71, 0.13);
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.9);
  box-shadow: 0 32px 90px rgba(34, 53, 39, 0.13), inset 0 1px rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(24px);
}

.card-heading { display: flex; align-items: flex-start; gap: 14px; }
.card-icon { width: 46px; height: 46px; display: grid; flex: 0 0 auto; place-items: center; color: #4f7f05; border-radius: 13px; background: rgba(118, 185, 0, 0.11); font-size: 19px; }
.card-heading > div { min-width: 0; }
.card-kicker { color: #6d786f; font-size: 11px; font-weight: 600; }
.card-heading h2 { margin: 4px 0 5px; color: #18231b; font-size: 30px; line-height: 1.2; letter-spacing: -0.04em; }
.card-heading p { margin: 0; color: #89918b; font-size: 12px; }
.login-form { margin-top: 30px; }
.login-form :deep(.el-form-item) { margin-bottom: 22px; }
.login-form :deep(.el-form-item__label) { padding-bottom: 8px; color: #4c584f; font-size: 12px; font-weight: 600; line-height: 1.2; }
.login-form :deep(.el-input__wrapper) { min-height: 50px; padding: 0 15px; border-radius: 11px !important; background: #f8faf7 !important; box-shadow: 0 0 0 1px #dfe5dd inset !important; transition: box-shadow 180ms ease, background 180ms ease; }
.login-form :deep(.el-input__wrapper:hover) { background: #ffffff !important; box-shadow: 0 0 0 1px #b9c6b6 inset !important; }
.login-form :deep(.el-input__wrapper.is-focus) { background: #ffffff !important; box-shadow: 0 0 0 1px #76b900 inset, 0 0 0 4px rgba(118, 185, 0, 0.1) !important; }
.login-form :deep(.el-input__prefix) { color: #7d8a80; }
.form-options { margin: -2px 0 20px; display: flex; align-items: center; justify-content: space-between; }
.form-options > span { display: flex; align-items: center; gap: 7px; color: #8a958d; font-size: 10px; }
.form-options > span i { width: 6px; height: 6px; border-radius: 50%; background: #76b900; }
.login-button { width: 100%; min-height: 50px; border-radius: 11px; font-weight: 700; box-shadow: 0 14px 28px rgba(88, 137, 12, 0.2); transition: transform 180ms ease, box-shadow 180ms ease; }
.login-button:hover { transform: translateY(-1px); box-shadow: 0 18px 34px rgba(88, 137, 12, 0.25); }
.login-button:active { transform: translateY(1px) scale(0.995); }
.login-button .el-icon { margin-left: 7px; }
.security-note { margin-top: 22px; padding-top: 18px; display: flex; align-items: center; justify-content: center; gap: 8px; color: #89928b; border-top: 1px solid #edf0eb; font-size: 10px; }
.security-note .el-icon { color: #639b0a; }

.login-footer { min-height: 64px; display: flex; align-items: center; justify-content: space-between; color: #8a938c; border-top: 1px solid rgba(91, 107, 95, 0.1); font-size: 10px; }

@media (max-width: 1120px) {
  .login-layout { grid-template-columns: minmax(0, 1fr) minmax(360px, 420px); gap: 42px; }
  .compute-map { height: 250px; }
  .satellite { width: 136px; }
  .node-device, .node-income { left: 18px; }
  .node-user, .node-task { right: 18px; }
  .connector { display: none; }
}

@media (max-width: 860px) {
  .login-page { overflow-y: auto; }
  .login-header, .login-footer, .login-layout { width: min(100% - 32px, 620px); }
  .login-layout { padding-top: 28px; grid-template-columns: 1fr; gap: 32px; }
  .hero-copy { text-align: center; }
  .eyebrow { justify-content: center; }
  .hero-copy p { margin-inline: auto; }
  .compute-map, .platform-facts { display: none; }
  .login-card { width: min(100%, 460px); margin: 0 auto; }
}

@media (max-width: 520px) {
  .login-header { height: 78px; }
  .header-state { display: none; }
  .login-layout { min-height: auto; padding: 34px 0 48px; }
  .hero-copy h1 { margin-top: 16px; font-size: 38px; }
  .hero-copy p { font-size: 14px; }
  .login-card { padding: 28px 22px; border-radius: 20px; }
  .card-heading h2 { font-size: 26px; }
  .login-footer { min-height: 58px; justify-content: center; }
  .login-footer span:last-child { display: none; }
}

@media (prefers-reduced-motion: reduce) {
  .login-button { transition: none; }
}
</style>
