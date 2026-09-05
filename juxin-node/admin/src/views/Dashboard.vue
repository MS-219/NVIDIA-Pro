<template>
  <div class="app-shell">
    <button v-if="mobileMenuOpen" type="button" class="sidebar-scrim" aria-label="关闭导航" @click="mobileMenuOpen = false" />

    <aside id="admin-navigation" class="sidebar" :class="{ open: mobileMenuOpen }" aria-label="主导航">
      <div class="brand">
        <div class="brand-mark"><img src="/favicon.svg" alt="" /></div>
        <div class="brand-copy">
          <strong>聚芯节点</strong>
          <span>边缘设备运营平台</span>
        </div>
      </div>

      <div class="platform-state">
        <span class="state-dot"></span>
        <div>
          <strong>平台服务正常</strong>
          <span>设备与结算数据已同步</span>
        </div>
      </div>

      <el-menu :default-active="activeMenu" class="nav-menu" router @select="mobileMenuOpen = false">
        <el-menu-item index="/overview">
          <el-icon><DataAnalysis /></el-icon>
          <span>运行总览</span>
        </el-menu-item>

        <div class="nav-label">设备与任务</div>
        <el-menu-item index="/monitor">
          <el-icon><Monitor /></el-icon>
          <span>设备管理</span>
        </el-menu-item>
        <el-menu-item index="/device-tasks">
          <el-icon><List /></el-icon>
          <span>推理任务</span>
        </el-menu-item>
        <el-menu-item index="/device-commands">
          <el-icon><Promotion /></el-icon>
          <span>设备指令</span>
        </el-menu-item>

        <div class="nav-label">用户与运营</div>
        <el-menu-item index="/users">
          <el-icon><UserFilled /></el-icon>
          <span>用户管理</span>
        </el-menu-item>
        <el-menu-item index="/teams">
          <el-icon><Avatar /></el-icon>
          <span>团队管理</span>
        </el-menu-item>
        <el-menu-item index="/notices">
          <el-icon><Bell /></el-icon>
          <span>公告管理</span>
        </el-menu-item>
        <el-menu-item index="/feedback">
          <el-icon><ChatDotRound /></el-icon>
          <span>意见反馈</span>
        </el-menu-item>

        <div class="nav-label">财务与结算</div>
        <el-menu-item index="/withdrawals">
          <el-icon><Wallet /></el-icon>
          <span>提现管理</span>
        </el-menu-item>
        <el-menu-item index="/payment-applies">
          <el-icon><DocumentChecked /></el-icon>
          <span>账户变更审核</span>
        </el-menu-item>
        <el-menu-item index="/earnings">
          <el-icon><Money /></el-icon>
          <span>收益管理</span>
        </el-menu-item>
        <el-menu-item index="/rewards">
          <el-icon><Tickets /></el-icon>
          <span>分润流水</span>
        </el-menu-item>
        <el-menu-item index="/exchange">
          <el-icon><Goods /></el-icon>
          <span>设备兑换</span>
        </el-menu-item>

        <div class="nav-label">调度控制</div>
        <el-menu-item index="/automation">
          <el-icon><Connection /></el-icon>
          <span>任务编排</span>
        </el-menu-item>
        <el-menu-item index="/scheduling">
          <el-icon><Operation /></el-icon>
          <span>调度策略</span>
        </el-menu-item>

        <div class="nav-label">平台运维</div>
        <el-menu-item index="/device-upgrades">
          <el-icon><UploadFilled /></el-icon>
          <span>版本升级</span>
        </el-menu-item>
        <el-menu-item index="/app-updates">
          <el-icon><Download /></el-icon>
          <span>APP 更新</span>
        </el-menu-item>
        <el-menu-item index="/terminal">
          <el-icon><Platform /></el-icon>
          <span>终端会话</span>
        </el-menu-item>
        <el-menu-item index="/device-settings">
          <el-icon><Operation /></el-icon>
          <span>设备参数</span>
        </el-menu-item>
        <el-menu-item index="/settings">
          <el-icon><Setting /></el-icon>
          <span>系统设置</span>
        </el-menu-item>
      </el-menu>

      <div class="sidebar-foot">
        <span>生产环境</span>
        <strong><i></i> 在线</strong>
      </div>
    </aside>

    <main class="main-content">
      <header class="topbar">
        <div class="topbar-left">
          <el-button
            class="mobile-menu"
            :icon="Menu"
            circle
            aria-label="打开导航"
            aria-controls="admin-navigation"
            :aria-expanded="mobileMenuOpen"
            @click="mobileMenuOpen = true"
          />
          <div>
            <span class="page-kicker">聚芯节点管理控制台</span>
            <h1>{{ route.meta.title || '运行总览' }}</h1>
          </div>
        </div>
        <div class="topbar-right">
          <div class="sync-state">
            <el-icon><CircleCheck /></el-icon>
            <span>生产环境 · 数据已同步</span>
          </div>
          <el-divider direction="vertical" />
          <el-dropdown @command="handleCommand">
            <button type="button" class="account-button" aria-label="打开管理员菜单">
              <span class="avatar">A</span>
              <span class="account-copy"><strong>管理员</strong><small>平台运维</small></span>
              <el-icon><ArrowDown /></el-icon>
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="logout" :icon="SwitchButton">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <section class="page-body">
        <router-view />
      </section>
    </main>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ArrowDown,
  Avatar,
  Bell,
  ChatDotRound,
  CircleCheck,
  Connection,
  DataAnalysis,
  Download,
  DocumentChecked,
  Goods,
  List,
  Menu,
  Money,
  Monitor,
  Operation,
  Platform,
  Promotion,
  Setting,
  SwitchButton,
  Tickets,
  UploadFilled,
  UserFilled,
  Wallet,
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

const route = useRoute()
const router = useRouter()
const mobileMenuOpen = ref(false)
const activeMenu = computed(() => route.path)

const handleCommand = (command) => {
  if (command === 'logout') {
    localStorage.removeItem('juxin_node_admin_token')
    ElMessage.success('已退出聚芯节点控制台')
    router.push('/login')
  }
}
</script>

<style scoped>
.app-shell {
  display: flex;
  width: 100vw;
  height: 100vh;
  overflow: hidden;
  color: var(--orin-text);
  background: var(--orin-canvas);
}

.sidebar {
  width: 252px;
  flex: 0 0 252px;
  display: flex;
  flex-direction: column;
  color: var(--orin-text-soft);
  background: var(--orin-sidebar);
  border-right: 1px solid var(--orin-border-soft);
  box-shadow: 8px 0 24px rgba(31, 41, 55, 0.06);
  z-index: 20;
}

.brand {
  height: 74px;
  padding: 0 18px;
  display: flex;
  align-items: center;
  gap: 12px;
  border-bottom: 1px solid var(--orin-border-soft);
}

.brand-mark {
  width: 38px;
  height: 38px;
  overflow: hidden;
  border: 1px solid rgba(118, 185, 0, 0.42);
  border-radius: 5px;
  box-shadow: 0 0 18px rgba(118, 185, 0, 0.14);
}

.brand-mark img { width: 100%; height: 100%; display: block; }

.brand-copy {
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.brand-copy strong { color: var(--orin-text); font-size: 15px; line-height: 1.3; }
.brand-copy span { color: var(--orin-muted); font-size: 10px; line-height: 1.5; }

.platform-state {
  margin: 16px 14px 10px;
  padding: 11px 12px;
  display: flex;
  align-items: center;
  gap: 10px;
  background: var(--orin-surface-soft);
  border: 1px solid var(--orin-border-soft);
  border-radius: 5px;
}

.state-dot {
  width: 8px;
  height: 8px;
  flex: 0 0 auto;
  border-radius: 50%;
  background: var(--orin-green);
  box-shadow: 0 0 0 4px rgba(118, 185, 0, 0.12);
}

.platform-state div { display: flex; min-width: 0; flex-direction: column; }
.platform-state strong { color: var(--orin-text-soft); font-size: 11px; font-weight: 600; }
.platform-state span:last-child { margin-top: 3px; color: var(--orin-dim); font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 9px; }

.nav-menu {
  flex: 1;
  overflow-y: auto;
  border-right: 0;
  background: transparent;
  --el-menu-text-color: #475569;
  --el-menu-hover-bg-color: #f2f6ed;
  --el-menu-active-color: #315600;
  --el-menu-bg-color: transparent;
}

.nav-menu :deep(.el-menu-item) {
  height: 42px;
  margin: 2px 10px;
  padding: 0 12px !important;
  border-radius: 4px;
}

.nav-menu :deep(.el-menu-item.is-active) {
  color: #315600 !important;
  background: #eaf3df;
  box-shadow: inset 3px 0 var(--orin-green);
}

.nav-label {
  padding: 17px 22px 6px;
  color: var(--orin-muted);
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 9px;
  font-weight: 700;
  text-transform: uppercase;
}

.sidebar-foot {
  height: 54px;
  padding: 0 18px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-top: 1px solid var(--orin-border-soft);
  color: var(--orin-muted);
  font-size: 10px;
}

.sidebar-foot strong { color: var(--orin-green); font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 9px; }

.main-content {
  min-width: 0;
  flex: 1;
  display: flex;
  flex-direction: column;
}

.topbar {
  height: 74px;
  flex: 0 0 74px;
  padding: 0 28px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  position: relative;
  background: #ffffff;
  border-bottom: 1px solid var(--orin-border-soft);
  box-shadow: 0 6px 18px rgba(31, 41, 55, 0.06);
}

.topbar::before {
  content: '';
  position: absolute;
  inset: 0 auto 0 0;
  width: 3px;
  background: var(--orin-green);
}

.topbar-left,
.topbar-right,
.sync-state,
.account-button { display: flex; align-items: center; }
.topbar-left { gap: 12px; }
.page-kicker { display: block; color: var(--orin-green); font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 9px; font-weight: 800; }
.topbar h1 { margin: 3px 0 0; color: var(--orin-text); font-size: 19px; line-height: 1.2; }
.topbar-right { gap: 12px; }
.sync-state { gap: 7px; color: var(--orin-muted); font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 9px; }
.sync-state .el-icon { color: var(--orin-green); }

.account-button {
  gap: 9px;
  padding: 4px 0;
  border: 0;
  color: var(--orin-text-soft);
  background: transparent;
  cursor: pointer;
}

.account-button:focus-visible {
  outline: 2px solid var(--orin-green);
  outline-offset: 3px;
}

.avatar {
  width: 32px;
  height: 32px;
  display: grid;
  place-items: center;
  border-radius: 4px;
  color: #0a0c09;
  background: var(--orin-green);
  font-size: 12px;
  font-weight: 700;
}

.account-copy { display: flex; flex-direction: column; align-items: flex-start; }
.account-copy strong { font-size: 12px; }
.account-copy small { color: var(--orin-muted); font-size: 10px; }
.mobile-menu { display: none; }

.page-body {
  min-height: 0;
  flex: 1;
  padding: 22px 24px 32px;
  overflow: auto;
  background-color: var(--orin-canvas);
  background-image:
    linear-gradient(rgba(118, 185, 0, 0.018) 1px, transparent 1px),
    linear-gradient(90deg, rgba(118, 185, 0, 0.018) 1px, transparent 1px);
  background-size: 36px 36px;
}

.sidebar-scrim { display: none; }

@media (max-width: 900px) {
  .sidebar {
    position: fixed;
    inset: 0 auto 0 0;
    transform: translateX(-100%);
    transition: transform 180ms ease;
  }
  .sidebar.open { transform: translateX(0); }
  .sidebar-scrim {
    position: fixed;
    inset: 0;
    z-index: 15;
    display: block;
    border: 0;
    background: rgba(15, 23, 42, 0.36);
    backdrop-filter: blur(2px);
  }
  .mobile-menu { display: inline-flex; }
  .sync-state, .account-copy, .topbar-right .el-divider { display: none; }
  .topbar { height: 66px; padding: 0 16px; }
  .page-body { padding: 16px; }
}

/* Premium light workspace */
.app-shell {
  background:
    radial-gradient(circle at 28% 0%, rgba(118, 185, 0, 0.055), transparent 28%),
    var(--orin-canvas);
}

.sidebar {
  width: 268px;
  flex-basis: 268px;
  border-right-color: rgba(55, 76, 60, 0.1);
  box-shadow: 16px 0 52px rgba(34, 54, 39, 0.055);
}

.brand {
  height: 82px;
  padding-inline: 20px;
}

.brand-mark {
  width: 42px;
  height: 42px;
  border: 0;
  border-radius: 12px;
  box-shadow: 0 10px 24px rgba(75, 112, 26, 0.16);
}

.brand-copy strong {
  font-size: 17px;
  letter-spacing: -0.025em;
}

.brand-copy span { margin-top: 2px; font-size: 10px; }

.platform-state {
  margin: 16px 14px 12px;
  padding: 12px 13px;
  border-color: rgba(84, 107, 89, 0.1);
  border-radius: 12px;
  background: linear-gradient(135deg, rgba(118, 185, 0, 0.09), rgba(118, 185, 0, 0.025));
}

.platform-state strong { color: #314036; font-size: 11px; }
.platform-state span:last-child { font-family: inherit; font-size: 9px; }

.nav-menu :deep(.el-menu-item) {
  height: 44px;
  margin: 3px 12px;
  padding: 0 13px !important;
  border-radius: 10px;
  font-size: 13px;
  transition: color 180ms ease, background 180ms ease, transform 180ms ease;
}

.nav-menu :deep(.el-menu-item:hover) { transform: translateX(2px); }
.nav-menu :deep(.el-menu-item .el-icon) { font-size: 17px; }

.nav-menu :deep(.el-menu-item.is-active) {
  color: #477500 !important;
  background: linear-gradient(90deg, rgba(118, 185, 0, 0.14), rgba(118, 185, 0, 0.055));
  box-shadow: inset 3px 0 #76b900;
  font-weight: 650;
}

.nav-label {
  padding: 19px 25px 7px;
  color: #9aa49d;
  font-family: inherit;
  font-size: 9px;
  font-weight: 700;
  letter-spacing: 0.12em;
}

.sidebar-foot {
  height: 62px;
  padding-inline: 20px;
  background: #fbfcfa;
}

.sidebar-foot strong { display: flex; align-items: center; gap: 7px; color: #5b6a5f; font-family: inherit; }
.sidebar-foot strong i { width: 6px; height: 6px; border-radius: 50%; background: #76b900; box-shadow: 0 0 0 4px rgba(118, 185, 0, 0.1); }

.topbar {
  height: 82px;
  padding: 0 28px;
  border-bottom-color: rgba(55, 76, 60, 0.1);
  background: rgba(255, 255, 255, 0.84);
  box-shadow: 0 10px 34px rgba(44, 62, 48, 0.035);
  backdrop-filter: blur(18px);
}

.page-kicker {
  color: #7d8a80;
  font-family: inherit;
  font-size: 9px;
  font-weight: 700;
  letter-spacing: 0.12em;
}

.topbar-left h1 {
  margin-top: 4px;
  color: #18241c;
  font-size: 20px;
  font-weight: 700;
  letter-spacing: -0.03em;
}

.sync-state {
  padding: 8px 11px;
  gap: 7px;
  color: #657269;
  border: 1px solid #e6ebe3;
  border-radius: 9px;
  background: #fafcf9;
  font-family: inherit;
  font-size: 10px;
}

.account-button {
  padding: 5px 7px 5px 5px;
  border-radius: 11px;
  transition: background 180ms ease;
}

.account-button:hover { background: #f2f6ef; }

.avatar {
  width: 34px;
  height: 34px;
  border-radius: 10px;
  color: #ffffff;
  background: linear-gradient(145deg, #74b600, #568c00);
  box-shadow: 0 8px 18px rgba(78, 122, 10, 0.18);
}

.page-body {
  padding: 26px 28px 40px;
  background-image:
    radial-gradient(circle at 92% 4%, rgba(118, 185, 0, 0.045), transparent 20%),
    linear-gradient(rgba(59, 82, 65, 0.018) 1px, transparent 1px),
    linear-gradient(90deg, rgba(59, 82, 65, 0.018) 1px, transparent 1px);
  background-size: auto, 40px 40px, 40px 40px;
}

@media (max-width: 900px) {
  .sidebar { width: min(286px, 86vw); }
  .topbar { height: 70px; padding: 0 16px; }
  .page-body { padding: 18px 16px 28px; }
}
</style>
