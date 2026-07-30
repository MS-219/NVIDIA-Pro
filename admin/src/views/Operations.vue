<template>
  <div class="page-container">
    <div class="ops-hero">
      <div>
        <h1>集群远程运维</h1>
        <p>把升级、巡检、命令下发和日志追踪聚合到一个页面。当前先提供可执行的运维工作台样式，后续可直接接真实接口与批量控制能力。</p>
      </div>
      <div class="ops-badges">
        <div class="badge-card">
          <strong>{{ fleetStats.online }}</strong>
          <span>在线节点</span>
        </div>
        <div class="badge-card">
          <strong>{{ fleetStats.pendingUpgrade }}</strong>
          <span>待升级节点</span>
        </div>
        <div class="badge-card">
          <strong>{{ fleetStats.alerts }}</strong>
          <span>待处理告警</span>
        </div>
      </div>
    </div>

    <div class="ops-grid">
      <div class="panel">
        <div class="panel-head">
          <h3>批量运维动作</h3>
          <el-tag type="danger" effect="plain">需人工确认</el-tag>
        </div>
        <div class="action-grid">
          <button class="action-card" v-for="action in actions" :key="action.title">
            <strong>{{ action.title }}</strong>
            <p>{{ action.detail }}</p>
            <span>{{ action.scope }}</span>
          </button>
        </div>
      </div>

      <div class="panel">
        <div class="panel-head">
          <h3>升级发布窗口</h3>
          <span class="subtle">版本节奏与覆盖率</span>
        </div>
        <div class="release-card">
          <div class="release-top">
            <div>
              <strong>{{ release.version }}</strong>
              <p>{{ release.note }}</p>
            </div>
            <el-tag type="success">{{ release.channel }}</el-tag>
          </div>
          <el-progress :percentage="release.coverage" :stroke-width="10" />
          <div class="release-meta">
            <span>覆盖率 {{ release.coverage }}%</span>
            <span>预计窗口 {{ release.window }}</span>
          </div>
          <div class="release-actions">
            <el-button type="primary">推送到待升级节点</el-button>
            <el-button plain>导出升级清单</el-button>
          </div>
        </div>
      </div>
    </div>

    <div class="bottom-grid">
      <div class="panel">
        <div class="panel-head">
          <h3>巡检节点列表</h3>
          <span class="subtle">按风险程度排序</span>
        </div>
        <div class="node-list">
          <div class="node-item" v-for="node in nodes" :key="node.sn">
            <div class="node-main">
              <strong>{{ node.sn }}</strong>
              <p>{{ node.location }} · {{ node.statusText }}</p>
            </div>
            <div class="node-side">
              <el-tag :type="node.levelType" effect="plain">{{ node.level }}</el-tag>
              <span>{{ node.issue }}</span>
            </div>
          </div>
        </div>
      </div>

      <div class="panel">
        <div class="panel-head">
          <h3>运维审计流</h3>
          <span class="subtle">最近动作与结果</span>
        </div>
        <div class="audit-list">
          <div class="audit-item" v-for="audit in audits" :key="audit.id">
            <div class="audit-time">{{ audit.time }}</div>
            <div class="audit-content">
              <strong>{{ audit.title }}</strong>
              <p>{{ audit.detail }}</p>
            </div>
            <el-tag :type="audit.type" effect="plain">{{ audit.status }}</el-tag>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
const fleetStats = {
  online: 12,
  pendingUpgrade: 4,
  alerts: 3
}

const release = {
  version: 'edge-agent v2.1.4',
  note: '包含真实 CPU 上报、任务链路修复与终端隧道增强。',
  channel: 'STABLE',
  coverage: 68,
  window: '今晚 23:00 - 23:30'
}

const actions = [
  { title: '批量升级 Agent', detail: '向所有待升级节点推送最新安装包并重启服务。', scope: '影响: 指定节点组' },
  { title: '同步配置模板', detail: '下发统一环境变量、模型地址和调度参数。', scope: '影响: 全部在线节点' },
  { title: '强制重连终端隧道', detail: '针对隧道异常节点重启 terminal agent。', scope: '影响: 终端异常节点' },
  { title: '执行巡检脚本', detail: '批量校验磁盘、内存、模型服务和 systemd 状态。', scope: '影响: 当前筛选节点' }
]

const nodes = [
  { sn: 'JX-974E009861F9', location: '浙江省杭州市', statusText: '在线，CPU 正常', level: '低风险', levelType: 'success', issue: '终端与任务链路均已恢复' },
  { sn: 'JX-EDGE-QC-01', location: '江苏省苏州市', statusText: '在线，模型服务偶发超时', level: '中风险', levelType: 'warning', issue: '建议检查本地 Ollama 响应时间' },
  { sn: 'JX-EDGE-CRAWL-02', location: '广东省深圳市', statusText: '离线 19 分钟', level: '高风险', levelType: 'danger', issue: '心跳中断，需要人工确认网络' }
]

const audits = [
  { id: 1, time: '02:18:24', title: '推送新版 edge-agent', detail: '4 台节点完成升级，1 台等待维护窗口。', status: '成功', type: 'success' },
  { id: 2, time: '02:09:03', title: '重载 Nginx WebSocket 代理', detail: '终端隧道恢复连接，监控后台可正常建立 shell。', status: '成功', type: 'success' },
  { id: 3, time: '01:56:41', title: '巡检高负载节点', detail: '发现 1 台节点内存占用超过 90%，已标记预警。', status: '告警', type: 'warning' },
  { id: 4, time: '01:40:12', title: '批量下发测试任务', detail: '其中 1 台节点执行失败，原因待排查。', status: '失败', type: 'danger' }
]
</script>

<style scoped>
.page-container {
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.ops-hero {
  min-height: 132px;
  padding: 22px 24px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 22px;
  color: var(--orin-text);
  background: var(--orin-surface);
  border: 1px solid var(--orin-border);
  border-left: 3px solid var(--orin-green);
  border-radius: 6px;
}

.ops-hero h1 {
  margin: 0 0 8px;
  font-size: 22px;
  letter-spacing: 0;
}

.ops-hero p {
  max-width: 780px;
  margin: 0;
  color: var(--orin-muted);
  font-size: 13px;
  line-height: 1.7;
}

.ops-badges {
  display: flex;
  gap: 10px;
}

.badge-card {
  min-width: 112px;
  padding: 12px 14px;
  background: var(--orin-surface-soft);
  border: 1px solid var(--orin-border);
  border-radius: 4px;
}

.badge-card strong {
  display: block;
  color: var(--orin-text);
  font-size: 24px;
}

.badge-card span {
  color: var(--orin-muted);
  font-size: 11px;
}

.ops-grid,
.bottom-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
}

.panel {
  min-width: 0;
  padding: 18px;
  color: var(--orin-text-soft);
  background: var(--orin-surface);
  border: 1px solid var(--orin-border);
  border-radius: 6px;
}

.panel-head {
  margin-bottom: 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.panel-head h3 {
  margin: 0;
  color: var(--orin-text);
  font-size: 16px;
}

.subtle {
  color: var(--orin-muted);
  font-size: 11px;
}

.action-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.action-card {
  padding: 14px;
  color: var(--orin-text-soft);
  text-align: left;
  background: var(--orin-surface-soft);
  border: 1px solid var(--orin-border-soft);
  border-radius: 5px;
  cursor: pointer;
}

.action-card:hover {
  background: var(--orin-surface-hover);
  border-color: var(--orin-green-dark);
}

.action-card:focus-visible {
  outline: 2px solid var(--orin-green);
  outline-offset: 2px;
}

.action-card strong {
  display: block;
  margin-bottom: 8px;
  color: var(--orin-text);
  font-size: 13px;
}

.action-card p {
  margin: 0 0 10px;
  color: var(--orin-muted);
  font-size: 12px;
  line-height: 1.6;
}

.action-card span {
  color: var(--orin-dim);
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 10px;
}

.release-card {
  padding: 16px;
  background: var(--orin-surface-soft);
  border: 1px solid var(--orin-border-soft);
  border-radius: 5px;
}

.release-top {
  margin-bottom: 14px;
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.release-top strong {
  color: var(--orin-text);
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 16px;
}

.release-top p {
  margin: 6px 0 0;
  color: var(--orin-muted);
  font-size: 12px;
  line-height: 1.6;
}

.release-meta {
  margin-top: 12px;
  display: flex;
  justify-content: space-between;
  color: var(--orin-muted);
  font-size: 11px;
}

.release-actions {
  margin-top: 16px;
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.node-list,
.audit-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.node-item,
.audit-item {
  min-width: 0;
  padding: 13px 14px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  background: var(--orin-surface-soft);
  border: 1px solid var(--orin-border-soft);
  border-radius: 5px;
}

.node-main,
.audit-content {
  min-width: 0;
}

.node-main strong,
.audit-content strong {
  color: var(--orin-text);
  font-size: 12px;
}

.node-main p,
.audit-content p {
  margin: 4px 0 0;
  color: var(--orin-muted);
  font-size: 11px;
  line-height: 1.55;
}

.node-side {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 8px;
  color: var(--orin-muted);
  font-size: 11px;
  text-align: right;
}

.audit-time {
  min-width: 72px;
  color: var(--orin-dim);
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 10px;
}

.audit-content {
  flex: 1;
}

@media (max-width: 1100px) {
  .ops-grid,
  .bottom-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .ops-hero {
    padding: 18px;
    align-items: stretch;
    flex-direction: column;
  }

  .ops-badges {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .badge-card {
    min-width: 0;
  }

  .action-grid {
    grid-template-columns: 1fr;
  }

  .node-item,
  .audit-item {
    align-items: flex-start;
  }
}

@media (max-width: 520px) {
  .ops-badges {
    grid-template-columns: 1fr;
  }

  .panel {
    padding: 14px;
  }

  .node-item,
  .audit-item {
    flex-direction: column;
  }

  .node-side {
    align-items: flex-start;
    text-align: left;
  }

  .release-top,
  .release-meta {
    flex-direction: column;
  }
}
</style>
