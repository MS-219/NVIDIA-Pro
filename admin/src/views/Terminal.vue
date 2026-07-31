<template>
  <div class="terminal-page">
    <div class="terminal-header">
      <div class="header-left">
        <el-button @click="goBack" circle plain type="info" class="back-btn">
          <el-icon><ArrowLeft /></el-icon>
        </el-button>
        <div class="terminal-info">
          <h3>远程指挥终端 <small>[COMMAND CENTER]</small></h3>
          <p v-if="sn">正在控制节点: <span class="sn-badge">{{ sn }}</span></p>
          <p v-else>尚未选择节点</p>
        </div>
      </div>
      <div class="header-right">
        <div v-if="sn" class="status-indicator" :class="{ connected: isConnected }">
          <span class="pulse-dot"></span>
          {{ isConnected ? '已连接' : '未连接' }}
        </div>
        <el-button v-if="sn" :icon="Delete" @click="clearTerminal">清空屏幕</el-button>
        <el-button v-if="sn && !isConnected" :icon="Refresh" type="danger" plain @click="reconnect">重新连接</el-button>
      </div>
    </div>
    
    <div class="terminal-body" v-loading="loading">
      <div v-show="sn" ref="terminalRef" class="xterm-view"></div>
      <div v-if="!sn" class="terminal-selector" v-loading="deviceLoading">
        <el-icon class="selector-icon"><Monitor /></el-icon>
        <h4>选择在线节点</h4>
        <div class="selector-controls">
          <el-select
            v-model="selectedSn"
            filterable
            remote
            clearable
            :remote-method="loadOnlineDevices"
            :loading="deviceLoading"
            placeholder="请选择在线节点"
            no-data-text="暂无可连接的在线节点"
          >
            <el-option
              v-for="device in deviceOptions"
              :key="device.sn"
              :label="device.name ? `${device.sn} · ${device.name}` : device.sn"
              :value="device.sn"
            />
          </el-select>
          <el-button type="primary" :disabled="!selectedSn" @click="connectSelectedNode">连接</el-button>
        </div>
        <el-empty
          v-if="!deviceLoading && deviceOptions.length === 0"
          description="暂无可连接的在线节点"
          :image-size="72"
        />
      </div>
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
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Terminal } from 'xterm'
import { FitAddon } from 'xterm-addon-fit'
import 'xterm/css/xterm.css'
import { ArrowLeft, Connection, Delete, Monitor, Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import request from '../utils/request'

const route = useRoute()
const router = useRouter()
const routeSn = typeof route.query.sn === 'string' ? route.query.sn : ''
const sn = ref(routeSn)
const selectedSn = ref(routeSn)
const terminalRef = ref(null)
const isConnected = ref(false)
const loading = ref(false)
const deviceLoading = ref(false)
const deviceOptions = ref([])

let term = null
let fitAddon = null
let socket = null
let inputDisposable = null
let resizeDisposable = null
let pasteHandler = null
let connectionAttempt = 0

const sendTerminalInput = (data) => {
  if (socket && socket.readyState === WebSocket.OPEN) {
    socket.send(JSON.stringify({ type: 'input', data }))
  }
}

const sendTerminalSize = () => {
  if (socket && socket.readyState === WebSocket.OPEN && term) {
    socket.send(JSON.stringify({ type: 'resize', cols: term.cols, rows: term.rows }))
  }
}

const normalizePastedText = (text) => text.replace(/\r\n/g, '\r').replace(/\n/g, '\r')

const disposeTerminal = () => {
  connectionAttempt += 1
  if (socket) {
    socket.onopen = null
    socket.onmessage = null
    socket.onclose = null
    socket.onerror = null
    socket.close()
    socket = null
  }
  if (terminalRef.value && pasteHandler) {
    terminalRef.value.removeEventListener('paste', pasteHandler, true)
  }
  pasteHandler = null
  inputDisposable?.dispose()
  inputDisposable = null
  resizeDisposable?.dispose()
  resizeDisposable = null
  term?.dispose()
  term = null
  fitAddon = null
  isConnected.value = false
  loading.value = false
}

const bindTerminalInput = () => {
  if (inputDisposable) {
    inputDisposable.dispose()
  }
  inputDisposable = term.onData(sendTerminalInput)
  resizeDisposable?.dispose()
  resizeDisposable = term.onResize(sendTerminalSize)

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
  if (!sn.value || !terminalRef.value) return

  disposeTerminal()
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

const writeSystemMessage = (message, level = 'info') => {
  const color = level === 'error' ? '31' : level === 'success' ? '32' : '36'
  term?.writeln(`\r\n\x1b[1;${color}m[系统] ${message}\x1b[0m`)
}

const handleSocketMessage = (raw) => {
  let message
  try {
    message = JSON.parse(raw)
  } catch {
    writeSystemMessage('收到无法识别的终端消息', 'error')
    return
  }
  if (message.type === 'output' && typeof message.data === 'string') {
    term?.write(message.data)
  } else if (message.type === 'system') {
    writeSystemMessage(message.message || '终端状态更新', message.level)
  } else if (message.type === 'status') {
    if (message.status === 'ready') {
      isConnected.value = true
      loading.value = false
      writeSystemMessage('维护终端已就绪', 'success')
      sendTerminalSize()
    } else if (message.status === 'closed') {
      isConnected.value = false
      writeSystemMessage('设备端 Shell 已关闭', 'info')
    } else if (message.status === 'error') {
      isConnected.value = false
      writeSystemMessage(message.message || '设备端 Shell 启动失败', 'error')
    }
  }
}

const connectWebSocket = async () => {
  const attempt = ++connectionAttempt
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  const host = window.location.host
  loading.value = true
  isConnected.value = false
  let ticket
  try {
    const response = await request.post(
      `/api/admin/terminal/ticket/${encodeURIComponent(sn.value)}`,
      null,
      { silent: true }
    )
    if (response.data.code !== 200 || !response.data.data?.ticket) {
      throw new Error(response.data.msg || '无法获取终端连接票据')
    }
    ticket = response.data.data.ticket
  } catch (error) {
    if (attempt !== connectionAttempt) return
    loading.value = false
    writeSystemMessage(error.message || '无法获取终端连接票据', 'error')
    return
  }
  if (attempt !== connectionAttempt) return

  const wsUrl = `${protocol}//${host}/ws/admin/terminal/${encodeURIComponent(sn.value)}?ticket=${encodeURIComponent(ticket)}`
  const currentSocket = new WebSocket(wsUrl)
  socket = currentSocket
  
  currentSocket.onopen = () => {
    if (socket !== currentSocket || attempt !== connectionAttempt) return
    writeSystemMessage('安全通道已建立，等待设备 Shell', 'info')
    sendTerminalSize()
  }

  currentSocket.onmessage = (event) => {
    if (socket !== currentSocket) return
    handleSocketMessage(event.data)
  }

  currentSocket.onclose = () => {
    if (socket !== currentSocket) return
    isConnected.value = false
    loading.value = false
    writeSystemMessage('通信链路已断开，连接超时或节点离线', 'error')
  }

  currentSocket.onerror = () => {
    if (socket !== currentSocket) return
    isConnected.value = false
    loading.value = false
    writeSystemMessage('隧道通信发生异常，请检查网络', 'error')
  }

}

const loadOnlineDevices = async (keyword = '') => {
  deviceLoading.value = true
  try {
    const res = await request.get('/api/admin/sl/devices/list', {
      params: {
        page: 1,
        size: 100,
        sn: keyword || undefined,
        status: 1,
        remoteCapable: true
      }
    })
    if (res.data.code === 200) {
      const records = res.data.data?.records
      deviceOptions.value = Array.isArray(records) ? records : []
    } else {
      deviceOptions.value = []
      ElMessage.error(res.data.msg || '获取在线节点失败')
    }
  } catch (e) {
    deviceOptions.value = []
    ElMessage.error('获取在线节点失败')
  } finally {
    deviceLoading.value = false
  }
}

const connectSelectedNode = async () => {
  if (!selectedSn.value) return

  sn.value = selectedSn.value
  await router.replace({
    name: 'Terminal',
    query: { ...route.query, sn: selectedSn.value }
  })
  await nextTick()
  initTerminal()
}

const reconnect = () => {
  if (socket) socket.close()
  term?.writeln('\x1b[1;33m[RETRY]\x1b[0m 正在重新尝试建立连接...')
  connectWebSocket()
}

const clearTerminal = () => term?.clear()
const goBack = () => router.push('/monitor')

const handleResize = () => {
  if (fitAddon) {
    fitAddon.fit()
    sendTerminalSize()
  }
}

onMounted(() => {
  if (sn.value) {
    initTerminal()
  } else {
    loadOnlineDevices()
  }
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  disposeTerminal()
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

.terminal-selector {
  height: 100%;
  min-height: 280px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  gap: 12px;
  color: var(--orin-muted);
}

.selector-icon {
  font-size: 32px;
  color: var(--orin-green-dark);
}

.terminal-selector h4 {
  margin: 0;
  color: var(--orin-text);
  font-size: 16px;
  font-weight: 600;
}

.selector-controls {
  width: min(100%, 520px);
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 10px;
}

.selector-controls :deep(.el-select) {
  width: 100%;
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

  .selector-controls {
    grid-template-columns: 1fr;
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
