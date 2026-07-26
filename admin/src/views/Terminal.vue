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
        <el-button color="#222" @click="clearTerminal" style="color: #666">清空屏幕</el-button>
        <el-button type="danger" plain @click="reconnect" v-if="!isConnected">重新连接</el-button>
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
import { ArrowLeft, Connection } from '@element-plus/icons-vue'
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
      background: '#0d1117',
      foreground: '#c9d1d9',
      cursor: '#58a6ff',
      selection: '#264f78',
      black: '#484f58',
      red: '#ff7b72',
      green: '#3fb950',
      yellow: '#d29922',
      blue: '#58a6ff',
      magenta: '#bc8cff',
      cyan: '#39c5cf',
      white: '#b1bac4',
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

const clearTerminal = () => term.clear()
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
  height: calc(100vh - 40px);
  display: flex;
  flex-direction: column;
  background-color: #0d1117;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 10px 40px rgba(0,0,0,0.5);
  border: 1px solid #30363d;
}

.terminal-header {
  padding: 12px 20px;
  background-color: #161b22;
  border-bottom: 1px solid #30363d;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 15px;
}

.terminal-info h3 {
  margin: 0;
  font-size: 16px;
  color: #f0f6fc;
  font-weight: 600;
}

.terminal-info h3 small {
  color: #8b949e;
  font-size: 10px;
  letter-spacing: 1px;
}

.terminal-info p {
  margin: 2px 0 0;
  font-size: 12px;
  color: #8b949e;
}

.sn-badge {
  background: #23863622;
  color: #3fb950;
  padding: 1px 6px;
  border-radius: 4px;
  font-family: monospace;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 15px;
}

.status-indicator {
  font-size: 11px;
  color: #f85149;
  font-family: monospace;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  background: #f8514911;
  border-radius: 20px;
}

.status-indicator.connected {
  color: #3fb950;
  background: #3fb95011;
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
  0% { transform: scale(0.95); box-shadow: 0 0 0 0 rgba(63, 185, 80, 0.7); }
  70% { transform: scale(1); box-shadow: 0 0 0 6px rgba(63, 185, 80, 0); }
  100% { transform: scale(0.95); box-shadow: 0 0 0 0 rgba(63, 185, 80, 0); }
}

.terminal-body {
  flex: 1;
  padding: 15px;
  background-color: #0d1117;
  overflow: hidden;
}

.xterm-view {
  height: 100%;
}

.terminal-footer {
  padding: 8px 20px;
  background-color: #161b22;
  border-top: 1px solid #30363d;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.shortcuts {
  display: flex;
  gap: 10px;
}

.latency-info {
  font-size: 11px;
  color: #8b949e;
  font-family: monospace;
  display: flex;
  align-items: center;
  gap: 5px;
}

:deep(.xterm-viewport) {
  background-color: #0d1117 !important;
}

:deep(.xterm-rows) {
  color: #c9d1d9;
}
</style>
