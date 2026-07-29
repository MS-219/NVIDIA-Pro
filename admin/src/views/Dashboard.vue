<template>
  <div class="app-shell">
    <button v-if="mobileMenuOpen" class="sidebar-scrim" aria-label="关闭导航" @click="mobileMenuOpen = false" />

    <aside class="sidebar" :class="{ open: mobileMenuOpen }">
      <div class="brand">
        <div class="brand-mark"><el-icon><Cpu /></el-icon></div>
        <div class="brand-copy">
          <strong>ORIN GRID</strong>
          <span>聚芯边缘算力平台</span>
        </div>
      </div>

      <div class="platform-state">
        <span class="state-dot"></span>
        <div>
          <strong>调度服务运行中</strong>
          <span>ARM64 · L4T 36.4.x</span>
        </div>
      </div>

      <el-menu :default-active="activeMenu" class="nav-menu" router @select="mobileMenuOpen = false">
        <el-menu-item index="/overview">
          <el-icon><DataAnalysis /></el-icon>
          <span>运行总览</span>
        </el-menu-item>

        <div class="nav-label">节点与任务</div>
        <el-menu-item index="/monitor">
          <el-icon><Monitor /></el-icon>
          <span>Orin 节点</span>
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
        <el-menu-item index="/operations">
          <el-icon><Odometer /></el-icon>
          <span>远程运维</span>
        </el-menu-item>
        <el-menu-item index="/device-upgrades">
          <el-icon><UploadFilled /></el-icon>
          <span>版本升级</span>
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
        <span>独立环境</span>
        <strong>ORIN-PROD</strong>
      </div>
    </aside>

    <main class="main-content">
      <header class="topbar">
        <div class="topbar-left">
          <el-button class="mobile-menu" :icon="Menu" circle aria-label="打开导航" @click="mobileMenuOpen = true" />
          <div>
            <span class="page-kicker">ORIN CONTROL</span>
            <h1>{{ route.meta.title || '运行总览' }}</h1>
          </div>
        </div>
        <div class="topbar-right">
          <div class="sync-state">
            <el-icon><CircleCheck /></el-icon>
            <span>独立环境</span>
          </div>
          <el-divider direction="vertical" />
          <el-dropdown @command="handleCommand">
            <button class="account-button">
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
  Cpu,
  DataAnalysis,
  DocumentChecked,
  List,
  Menu,
  Money,
  Monitor,
  Odometer,
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
    localStorage.removeItem('orin_admin_token')
    ElMessage.success('已退出 Orin 控制台')
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
  background: #f4f5f2;
}

.sidebar {
  width: 248px;
  flex: 0 0 248px;
  display: flex;
  flex-direction: column;
  color: #f7f8f5;
  background: #111310;
  border-right: 1px solid #292c27;
  z-index: 20;
}

.brand {
  height: 74px;
  padding: 0 18px;
  display: flex;
  align-items: center;
  gap: 12px;
  border-bottom: 1px solid #292c27;
}

.brand-mark {
  width: 38px;
  height: 38px;
  display: grid;
  place-items: center;
  color: #10130e;
  background: #76b900;
  border-radius: 6px;
  font-size: 21px;
}

.brand-copy {
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.brand-copy strong { font-size: 15px; line-height: 1.3; }
.brand-copy span { color: #93998d; font-size: 11px; line-height: 1.5; }

.platform-state {
  margin: 16px 14px 10px;
  padding: 11px 12px;
  display: flex;
  align-items: center;
  gap: 10px;
  background: #1a1d18;
  border: 1px solid #2c3029;
  border-radius: 6px;
}

.state-dot {
  width: 8px;
  height: 8px;
  flex: 0 0 auto;
  border-radius: 50%;
  background: #76b900;
  box-shadow: 0 0 0 4px rgba(118, 185, 0, 0.12);
}

.platform-state div { display: flex; min-width: 0; flex-direction: column; }
.platform-state strong { font-size: 12px; font-weight: 600; }
.platform-state span:last-child { margin-top: 2px; color: #858b80; font-size: 10px; }

.nav-menu {
  flex: 1;
  overflow-y: auto;
  border-right: 0;
  background: transparent;
  --el-menu-text-color: #aeb3aa;
  --el-menu-hover-bg-color: #20231e;
  --el-menu-active-color: #ffffff;
  --el-menu-bg-color: transparent;
}

.nav-menu :deep(.el-menu-item) {
  height: 42px;
  margin: 2px 10px;
  padding: 0 12px !important;
  border-radius: 5px;
}

.nav-menu :deep(.el-menu-item.is-active) {
  color: #fff;
  background: #28301f;
  box-shadow: inset 3px 0 #76b900;
}

.nav-label {
  padding: 17px 22px 6px;
  color: #656b61;
  font-size: 10px;
  font-weight: 700;
  text-transform: uppercase;
}

.sidebar-foot {
  height: 54px;
  padding: 0 18px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-top: 1px solid #292c27;
  color: #777d73;
  font-size: 10px;
}

.sidebar-foot strong { color: #b4baaf; font-size: 10px; }

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
  background: #fff;
  border-bottom: 1px solid #e3e5e0;
}

.topbar-left,
.topbar-right,
.sync-state,
.account-button { display: flex; align-items: center; }
.topbar-left { gap: 12px; }
.page-kicker { display: block; color: #76a900; font-size: 9px; font-weight: 800; }
.topbar h1 { margin: 2px 0 0; color: #171a16; font-size: 19px; line-height: 1.2; }
.topbar-right { gap: 12px; }
.sync-state { gap: 6px; color: #4d5448; font-size: 12px; }
.sync-state .el-icon { color: #659f00; }

.account-button {
  gap: 9px;
  padding: 4px 0;
  border: 0;
  color: #252923;
  background: transparent;
  cursor: pointer;
}

.avatar {
  width: 32px;
  height: 32px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  color: #fff;
  background: #252923;
  font-size: 12px;
  font-weight: 700;
}

.account-copy { display: flex; flex-direction: column; align-items: flex-start; }
.account-copy strong { font-size: 12px; }
.account-copy small { color: #8a9085; font-size: 10px; }
.mobile-menu { display: none; }

.page-body {
  min-height: 0;
  flex: 1;
  padding: 22px 24px 32px;
  overflow: auto;
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
    background: rgba(10, 12, 9, 0.52);
  }
  .mobile-menu { display: inline-flex; }
  .sync-state, .account-copy, .topbar-right .el-divider { display: none; }
  .topbar { height: 66px; padding: 0 16px; }
  .page-body { padding: 16px; }
}
</style>
