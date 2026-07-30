<template>
  <div class="terminal-page">
    <div class="terminal-header">
      <div class="header-left">
        <el-button @click="goBack" circle plain type="info" class="back-btn">
          <el-icon><ArrowLeft /></el-icon>
        </el-button>
        <div class="terminal-info">
          <h3>远程指挥终端 <small>[COMMAND CENTER]</small></h3>
          <p>正在控制节点: <span class="sn-badge">{{ sn }}</span></p>
        </div>
      </div>
      <div class="header-right">
        <div class="status-indicator" :class="{ connected: isConnected }">
          <span class="pulse-dot"></span>
          {{ isConnected ? 'TUNNEL ACTIVE' : 'TUNNEL CLOSED' }}
        </div>
        <el-button v-if="sn" :icon="Delete" @click="clearTerminal">清空屏幕</el-button>
        <el-button v-if="sn && !isConnected" :icon="Refresh" type="danger" plain @click="reconnect">重新连接</el-button>
      </div>
    </div>
    
    <div class="terminal-body" v-loading="loading">
      <div ref="terminalRef" class="xterm-view"></div>
    </div>
    
    <div class="terminal-footer">
      <div class="shortcuts">
        <el-tag size="small" type="info">CTRL+C 中断</el-tag>
        <el-tag size="small" type="info">EXIT 退出</el-tag>
        <el-tag size="small" type="info">HELP 帮助</el-tag>
      </div>
      <div class="latency-info" v-if="isConnected">
        <el-icon><Connection /></el-icon> Real-time WebSocket Protocol
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Terminal } from 'xterm'
import { FitAddon } from 'xterm-addon-fit'
import 'xterm/css/xterm.css'
import { ArrowLeft, Connection, Delete, Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

const route = useRoute()
const router = useRouter()
const sn = ref(route.query.sn)
const terminalRef = ref(null)
const isConnected = ref(false)
const loading = ref(false)

let term = null
let fitAddon = null
let socket = null
let inputDisposable = null
let pasteHandler = null

const sendTerminalInput = (data) => {
  if (socket && socket.readyState === WebSocket.OPEN) {
    socket.send(data)
  }
}

const normalizePastedText = (text) => text.replace(/\r\n/g, '\r').replace(/\n/g, '\r')

const bindTerminalInput = () => {
  if (inputDisposable) {
    inputDisposable.dispose()
  }
  inputDisposable = term.onData(sendTerminalInput)

  if (pasteHandler && terminalRef.value) {
    terminalRef.value.removeEventListener('paste', pasteHandler, true)
  }
  pasteHandler = (event) => {
    const text = event.clipboardData?.getData('text/plain')
    if (!text) return
    event.preventDefault()
    event.stopPropagation()
    sendTerminalInput(normalizePastedText(text))
  }
  terminalRef.value?.addEventListener('paste', pasteHandler, true)
}

const initTerminal = () => {
  if (!sn.value) {
    ElMessage.error('缺少节点序列号，无法建立隧道')
    return
  }

  loading.value = true
  
  term = new Terminal({
    cursorBlink: true,
    fontFamily: '"Fira Code", Menlo, Monaco, "Courier New", monospace',
    fontSize: 14,
    theme: {
      background: '#0b0d0c',
      foreground: '#c2c8bd',
      cursor: '#76b900',
      selection: '#34441f',
      black: '#30352f',
      red: '#e06269',
      green: '#76b900',
      yellow: '#d6a33a',
      blue: '#5f94d8',
      magenta: '#a887c6',
      cyan: '#38a9a5',
      white: '#f2f5ef',
    }
  })
  
  fitAddon = new FitAddon()
  term.loadAddon(fitAddon)
  term.open(terminalRef.value)
  fitAddon.fit()
  bindTerminalInput()

  // 启动欢迎语
  term.writeln('\x1b[1;36m[ JX-AI COMMANDER ]\x1b[0m 正在初始化安全外壳通道...')
  term.writeln('\x1b[1;30m--------------------------------------------------\x1b[0m')
  term.writeln('\x1b[1;33m[TARGET]\x1b[0m ' + sn.value)
  term.writeln('\x1b[1;33m[REGION]\x1b[0m Distributed Edge Node')
  term.writeln('\x1b[1;30m--------------------------------------------------\x1b[0m\r\n')

  connectWebSocket()
}

const connectWebSocket = () => {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  const host = window.location.host
  // 根据后端 RemoteTerminalHandler 的监听路径
  const wsUrl = `${protocol}//${host}/ws/admin/terminal/${sn.value}`
  
  socket = new WebSocket(wsUrl)
  
  socket.onopen = () => {
    isConnected.value = true
    loading.value = false
    term.writeln('\x1b[1;32m[SUCCESS]\x1b[0m 通信隧道已打通，控制权限已获取。\r\n')
  }

  socket.onmessage = (event) => {
    term.write(event.data)
  }

  socket.onclose = () => {
    isConnected.value = false
    term.writeln('\r\n\x1b[1;31m[CLOSED] 通信链路已断开，连接超时或节点离线。\x1b[0m')
  }

  socket.onerror = () => {
    isConnected.value = false
    term.writeln('\r\n\x1b[1;31m[ERROR] 隧道通信发生异常，请检查网络。\x1b[0m')
  }

}

const reconnect = () => {
  if (socket) socket.close()
  term.writeln('\x1b[1;33m[RETRY]\x1b[0m 正在重新尝试建立连接...')
  connectWebSocket()
}

const clearTerminal = () => term?.clear()
const goBack = () => router.push('/monitor')

const handleResize = () => {
  if (fitAddon) fitAddon.fit()
}

onMounted(() => {
  initTerminal()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  if (terminalRef.value && pasteHandler) {
    terminalRef.value.removeEventListener('paste', pasteHandler, true)
  }
  if (inputDisposable) inputDisposable.dispose()
  if (socket) socket.close()
  if (term) term.dispose()
  window.removeEventListener('resize', handleResize)
})
</script>

<style scoped>
.terminal-page {
  height: calc(100vh - 128px);
  min-height: 520px;
  display: flex;
  flex-direction: column;
  color: var(--orin-text-soft);
  background: var(--orin-canvas);
  border: 1px solid var(--orin-border);
  border-radius: 6px;
  overflow: hidden;
  box-shadow: none;
}

.terminal-header {
  padding: 12px 20px;
  background: var(--orin-surface);
  border-bottom: 1px solid var(--orin-border);
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 14px;
}

.header-left {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 15px;
}

.terminal-info {
  min-width: 0;
}

.terminal-info h3 {
  margin: 0;
  font-size: 16px;
  color: var(--orin-text);
  font-weight: 600;
}

.terminal-info h3 small {
  color: var(--orin-muted);
  font-size: 10px;
  letter-spacing: 0;
}

.terminal-info p {
  margin: 2px 0 0;
  font-size: 12px;
  color: var(--orin-muted);
}

.sn-badge {
  color: var(--orin-green-bright);
  background: var(--orin-green-soft);
  border: 1px solid var(--orin-green-dark);
  padding: 1px 6px;
  border-radius: 3px;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}

.header-right {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 8px;
}

.status-indicator {
  font-size: 11px;
  color: var(--orin-danger);
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  background: rgba(224, 98, 105, 0.1);
  border: 1px solid rgba(224, 98, 105, 0.3);
  border-radius: 3px;
}

.status-indicator.connected {
  color: var(--orin-green-bright);
  background: var(--orin-green-soft);
  border-color: var(--orin-green-dark);
}

.pulse-dot {
  width: 6px;
  height: 6px;
  background: currentColor;
  border-radius: 50%;
}

.connected .pulse-dot {
  animation: pulse 2s infinite;
}

@keyframes pulse {
  0% { transform: scale(0.95); box-shadow: 0 0 0 0 rgba(118, 185, 0, 0.62); }
  70% { transform: scale(1); box-shadow: 0 0 0 6px rgba(118, 185, 0, 0); }
  100% { transform: scale(0.95); box-shadow: 0 0 0 0 rgba(118, 185, 0, 0); }
}

.terminal-body {
  flex: 1;
  padding: 15px;
  background: var(--orin-canvas);
  overflow: hidden;
}

.xterm-view {
  height: 100%;
}

.terminal-footer {
  padding: 8px 20px;
  background: var(--orin-surface);
  border-top: 1px solid var(--orin-border);
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.shortcuts {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.latency-info {
  font-size: 11px;
  color: var(--orin-muted);
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  display: flex;
  align-items: center;
  gap: 5px;
}

:deep(.xterm-viewport) {
  background: var(--orin-canvas) !important;
}

:deep(.xterm-rows) {
  color: var(--orin-text-soft);
}

@media (max-width: 760px) {
  .terminal-page {
    height: calc(100vh - 98px);
    min-height: 0;
  }

  .terminal-header {
    padding: 11px 12px;
    align-items: stretch;
    flex-direction: column;
  }

  .terminal-info h3 small {
    display: none;
  }

  .header-right {
    justify-content: flex-start;
  }

  .status-indicator {
    min-height: 32px;
  }

  .terminal-body {
    padding: 10px;
  }

  .terminal-footer {
    padding: 8px 12px;
  }

  .latency-info {
    display: none;
  }
}

@media (max-width: 420px) {
  .header-left {
    gap: 10px;
  }

  .header-right .el-button {
    margin-left: 0;
  }

  .shortcuts {
    gap: 5px;
  }
}
</style>
