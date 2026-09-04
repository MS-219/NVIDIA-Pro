<template>
  <div class="page-container">
    <div class="hero-panel">
      <div>
        <h1>任务编排自动化</h1>
        <p>把抓取、推理、脚本执行和结果回传编成稳定流水线。当前先提供可用的策略模板与节点配置，不再展示空白占位页。</p>
      </div>
      <div class="hero-stats">
        <div class="hero-stat">
          <span class="num">{{ flows.filter(f => f.enabled).length }}</span>
          <span class="label">运行中流程</span>
        </div>
        <div class="hero-stat">
          <span class="num">{{ totalSteps }}</span>
          <span class="label">自动化步骤</span>
        </div>
      </div>
    </div>

    <div class="content-grid">
      <div class="panel">
        <div class="panel-head">
          <h3>流程清单</h3>
          <el-button type="primary" @click="createDraft">新建草稿</el-button>
        </div>

        <div class="flow-list">
          <button
            v-for="flow in flows"
            :key="flow.id"
            class="flow-card"
            :class="{ active: flow.id === activeFlowId }"
            @click="activeFlowId = flow.id"
          >
            <div class="flow-top">
              <strong>{{ flow.name }}</strong>
              <el-switch
                v-model="flow.enabled"
                @click.stop
              />
            </div>
            <div class="flow-desc">{{ flow.description }}</div>
            <div class="flow-meta">
              <span>{{ flow.trigger }}</span>
              <span>{{ flow.steps.length }} 步</span>
            </div>
          </button>
        </div>
      </div>

      <div class="panel detail-panel" v-if="activeFlow">
        <div class="panel-head">
          <div>
            <h3>{{ activeFlow.name }}</h3>
            <p>{{ activeFlow.description }}</p>
          </div>
          <el-tag :type="activeFlow.enabled ? 'success' : 'info'">
            {{ activeFlow.enabled ? 'ACTIVE' : 'DRAFT' }}
          </el-tag>
        </div>

        <el-form label-position="top" class="flow-form">
          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item label="触发方式">
                <el-select v-model="activeFlow.trigger">
                  <el-option label="定时轮询" value="定时轮询" />
                  <el-option label="节点空闲触发" value="节点空闲触发" />
                  <el-option label="人工触发" value="人工触发" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="执行策略">
                <el-select v-model="activeFlow.policy">
                  <el-option label="串行执行" value="串行执行" />
                  <el-option label="并行执行" value="并行执行" />
                  <el-option label="失败自动跳过" value="失败自动跳过" />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>

          <el-form-item label="说明">
            <el-input v-model="activeFlow.description" type="textarea" :rows="2" />
          </el-form-item>
        </el-form>

        <div class="steps-board">
          <div class="steps-head">
            <h4>流程步骤</h4>
            <el-button link type="primary" @click="appendStep">添加步骤</el-button>
          </div>
          <div class="step-line" v-for="(step, index) in activeFlow.steps" :key="step.id">
            <div class="step-index">{{ index + 1 }}</div>
            <div class="step-main">
              <div class="step-top">
                <strong>{{ step.name }}</strong>
                <el-tag size="small" effect="plain">{{ step.type }}</el-tag>
              </div>
              <div class="step-desc">{{ step.detail }}</div>
            </div>
          </div>
        </div>

        <div class="dispatch-matrix">
          <h4>节点分工</h4>
          <div class="matrix-row" v-for="node in activeFlow.nodes" :key="node.sn">
            <div class="node-id">{{ node.sn }}</div>
            <div class="node-role">{{ node.role }}</div>
            <el-progress :percentage="node.load" :show-text="false" :stroke-width="6" />
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'

const flows = ref([
  {
    id: 1,
    name: '热点采集转摘要',
    description: '采集新闻源后自动生成摘要，并投递到运营内容池。',
    trigger: '定时轮询',
    policy: '串行执行',
    enabled: true,
    steps: [
      { id: 11, name: '抓取资讯列表', type: '数据采集', detail: '从 3 个新闻源抓取最新内容。' },
      { id: 12, name: '提炼摘要', type: '模型推理', detail: '使用 Qwen 对正文进行 120 字摘要。' },
      { id: 13, name: '写入结果库', type: '脚本执行', detail: '将摘要写入待审核内容表。' }
    ],
    nodes: [
      { sn: 'JX-974E009861F9', role: '摘要推理', load: 44 },
      { sn: 'JX-EDGE-CRAWL-02', role: '数据采集', load: 31 }
    ]
  },
  {
    id: 2,
    name: '商品页巡检',
    description: '对投放商品页进行抓取与文案合规检查。',
    trigger: '节点空闲触发',
    policy: '并行执行',
    enabled: false,
    steps: [
      { id: 21, name: '拉取商品页', type: '数据采集', detail: '抓取标题、价格、主图及规格。' },
      { id: 22, name: '违规词检查', type: '模型推理', detail: '检查是否包含高风险营销词。' }
    ],
    nodes: [
      { sn: 'JX-EDGE-QC-01', role: '质检审查', load: 18 }
    ]
  }
])

const activeFlowId = ref(1)
const activeFlow = computed(() => flows.value.find(item => item.id === activeFlowId.value))
const totalSteps = computed(() => flows.value.reduce((sum, flow) => sum + flow.steps.length, 0))

const createDraft = () => {
  const id = Date.now()
  flows.value.unshift({
    id,
    name: '新建自动化草稿',
    description: '待补充流程目标与节点分工。',
    trigger: '人工触发',
    policy: '串行执行',
    enabled: false,
    steps: [
      { id: id + 1, name: '定义输入', type: '准备阶段', detail: '补充任务来源、输入结构与目标结果。' }
    ],
    nodes: []
  })
  activeFlowId.value = id
}

const appendStep = () => {
  if (!activeFlow.value) return
  activeFlow.value.steps.push({
    id: Date.now(),
    name: '新增步骤',
    type: '待配置',
    detail: '补充该步骤的执行内容与输出目标。'
  })
}
</script>

<style scoped>
.page-container {
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.hero-panel {
  min-height: 128px;
  padding: 22px 24px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  color: var(--orin-text);
  background: var(--orin-surface);
  border: 1px solid var(--orin-border);
  border-left: 3px solid var(--orin-green);
  border-radius: 6px;
}

.hero-panel h1 {
  margin: 0 0 8px;
  font-size: 22px;
  letter-spacing: 0;
}

.hero-panel p {
  max-width: 760px;
  margin: 0;
  color: var(--orin-muted);
  font-size: 13px;
  line-height: 1.7;
}

.hero-stats {
  display: flex;
  gap: 10px;
}

.hero-stat {
  min-width: 122px;
  padding: 12px 14px;
  background: var(--orin-surface-soft);
  border: 1px solid var(--orin-border);
  border-radius: 4px;
}

.hero-stat .num {
  display: block;
  color: var(--orin-text);
  font-size: 24px;
  font-weight: 800;
}

.hero-stat .label {
  color: var(--orin-muted);
  font-size: 11px;
}

.content-grid {
  display: grid;
  grid-template-columns: minmax(280px, 340px) minmax(0, 1fr);
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
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.panel-head h3 {
  margin: 0;
  color: var(--orin-text);
  font-size: 16px;
}

.panel-head p {
  margin: 4px 0 0;
  color: var(--orin-muted);
  font-size: 12px;
}

.flow-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.flow-card {
  width: 100%;
  padding: 13px 14px;
  color: var(--orin-text-soft);
  text-align: left;
  background: var(--orin-surface-soft);
  border: 1px solid var(--orin-border-soft);
  border-radius: 5px;
  cursor: pointer;
}

.flow-card:hover {
  background: var(--orin-surface-hover);
  border-color: var(--orin-border-strong);
}

.flow-card:focus-visible {
  outline: 2px solid var(--orin-green);
  outline-offset: 2px;
}

.flow-card.active {
  background: var(--orin-green-soft);
  border-color: var(--orin-green-dark);
  box-shadow: inset 3px 0 var(--orin-green);
}

.flow-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 8px;
}

.flow-top strong {
  color: var(--orin-text);
}

.flow-desc {
  margin-bottom: 10px;
  color: var(--orin-muted);
  font-size: 12px;
  line-height: 1.6;
}

.flow-meta {
  display: flex;
  justify-content: space-between;
  color: var(--orin-dim);
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 10px;
}

.detail-panel {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.flow-form :deep(.el-form-item) {
  margin-bottom: 12px;
}

.steps-board,
.dispatch-matrix {
  padding: 15px;
  color: var(--orin-text-soft);
  background: var(--orin-surface-soft);
  border: 1px solid var(--orin-border-soft);
  border-radius: 5px;
}

.steps-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}

.steps-head h4,
.dispatch-matrix h4 {
  margin: 0;
  color: var(--orin-text);
  font-size: 14px;
}

.step-line {
  padding: 12px 0;
  display: flex;
  align-items: flex-start;
  gap: 12px;
  border-top: 1px solid var(--orin-border-soft);
}

.step-line:first-of-type {
  padding-top: 0;
  border-top: 0;
}

.step-index {
  width: 28px;
  height: 28px;
  display: grid;
  flex: 0 0 28px;
  place-items: center;
  color: #0b0d0c;
  background: var(--orin-green);
  border-radius: 3px;
  font-size: 12px;
  font-weight: 800;
}

.step-main {
  min-width: 0;
  flex: 1;
}

.step-top {
  margin-bottom: 6px;
  display: flex;
  align-items: center;
  gap: 10px;
}

.step-top strong {
  color: var(--orin-text);
}

.step-desc {
  color: var(--orin-muted);
  font-size: 12px;
  line-height: 1.6;
}

.matrix-row {
  padding: 10px 0;
  display: grid;
  grid-template-columns: minmax(140px, 180px) 120px minmax(100px, 1fr);
  align-items: center;
  gap: 12px;
  border-top: 1px solid var(--orin-border-soft);
}

.matrix-row:first-of-type {
  padding-top: 0;
  border-top: 0;
}

.node-id {
  overflow: hidden;
  color: var(--orin-text);
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 11px;
  font-weight: 700;
  text-overflow: ellipsis;
}

.node-role {
  color: var(--orin-muted);
  font-size: 12px;
}

@media (max-width: 1100px) {
  .content-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .hero-panel {
    padding: 18px;
    align-items: stretch;
    flex-direction: column;
  }

  .hero-stats {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .hero-stat {
    min-width: 0;
  }

  .flow-form :deep(.el-col) {
    max-width: 100%;
    flex: 0 0 100%;
  }

  .matrix-row {
    grid-template-columns: 1fr;
    gap: 6px;
  }
}

@media (max-width: 460px) {
  .hero-stats {
    grid-template-columns: 1fr;
  }

  .panel,
  .steps-board,
  .dispatch-matrix {
    padding: 14px;
  }
}
</style>
