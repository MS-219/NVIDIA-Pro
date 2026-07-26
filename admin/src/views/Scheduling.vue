<template>
  <div class="page-container">
    <div class="hero-band">
      <div>
        <h1>智能调度策略</h1>
        <p>按任务目标、节点负载与心跳质量切换调度模式。这里先把策略设计、命中规则与运行偏好整理成可操作面板，便于后续直接接后端持久化。</p>
      </div>
      <div class="hero-side">
        <div class="hero-chip">
          <span class="hero-num">{{ currentPolicy.name }}</span>
          <span class="hero-label">当前策略</span>
        </div>
        <div class="hero-chip">
          <span class="hero-num">{{ currentPolicy.maxConcurrency }}</span>
          <span class="hero-label">单节点并发</span>
        </div>
      </div>
    </div>

    <div class="policy-grid">
      <button
        v-for="policy in policies"
        :key="policy.id"
        class="policy-card"
        :class="{ active: currentPolicyId === policy.id }"
        @click="currentPolicyId = policy.id"
      >
        <div class="policy-card-top">
          <h3>{{ policy.name }}</h3>
          <el-tag :type="currentPolicyId === policy.id ? 'success' : 'info'" effect="plain">
            {{ currentPolicyId === policy.id ? 'ACTIVE' : 'STANDBY' }}
          </el-tag>
        </div>
        <p>{{ policy.description }}</p>
        <div class="policy-kpis">
          <span>CPU <b>{{ policy.cpuLimit }}%</b></span>
          <span>MEM <b>{{ policy.memLimit }}%</b></span>
          <span>重试 <b>{{ policy.retryCount }}</b></span>
        </div>
      </button>
    </div>

    <div class="main-grid">
      <div class="panel">
        <div class="panel-head">
          <h3>调度阈值</h3>
          <el-tag type="warning" effect="plain">前端草案</el-tag>
        </div>

        <el-form label-position="top">
          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item label="CPU 负载上限">
                <el-slider v-model="currentPolicy.cpuLimit" :min="20" :max="95" show-input />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="内存占用上限">
                <el-slider v-model="currentPolicy.memLimit" :min="20" :max="95" show-input />
              </el-form-item>
            </el-col>
          </el-row>

          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item label="单节点最大并发">
                <el-input-number v-model="currentPolicy.maxConcurrency" :min="1" :max="32" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="失败重试次数">
                <el-input-number v-model="currentPolicy.retryCount" :min="0" :max="10" style="width: 100%" />
              </el-form-item>
            </el-col>
          </el-row>

          <el-form-item label="策略说明">
            <el-input v-model="currentPolicy.note" type="textarea" :rows="3" />
          </el-form-item>
        </el-form>
      </div>

      <div class="panel">
        <div class="panel-head">
          <h3>策略开关</h3>
          <el-tag type="info" effect="plain">实时预览</el-tag>
        </div>

        <div class="toggle-list">
          <div class="toggle-item">
            <div>
              <strong>优先低负载节点</strong>
              <p>先分发给 CPU / MEM 更低的节点。</p>
            </div>
            <el-switch v-model="currentPolicy.preferLowLoad" />
          </div>
          <div class="toggle-item">
            <div>
              <strong>优先最新心跳节点</strong>
              <p>提升在线状态最新节点的排序权重。</p>
            </div>
            <el-switch v-model="currentPolicy.preferFreshHeartbeat" />
          </div>
          <div class="toggle-item">
            <div>
              <strong>阻断高热节点</strong>
              <p>达到阈值后，节点立即停止接收新任务。</p>
            </div>
            <el-switch v-model="currentPolicy.blockHotNode" />
          </div>
          <div class="toggle-item">
            <div>
              <strong>失败任务自动转移</strong>
              <p>执行失败后自动切换到下一台可用节点。</p>
            </div>
            <el-switch v-model="currentPolicy.autoRetry" />
          </div>
        </div>
      </div>
    </div>

    <div class="bottom-grid">
      <div class="panel">
        <div class="panel-head">
          <h3>命中规则矩阵</h3>
          <span class="subtle">当前策略将如何筛选节点</span>
        </div>
        <div class="rule-grid">
          <div class="rule-card" v-for="rule in currentPolicy.rules" :key="rule.title">
            <h4>{{ rule.title }}</h4>
            <p>{{ rule.detail }}</p>
          </div>
        </div>
      </div>

      <div class="panel">
        <div class="panel-head">
          <h3>场景模板</h3>
          <span class="subtle">用于快速切换运行模式</span>
        </div>
        <div class="scene-list">
          <div class="scene-item" v-for="scene in scenes" :key="scene.name">
            <div>
              <strong>{{ scene.name }}</strong>
              <p>{{ scene.detail }}</p>
            </div>
            <el-button size="small" @click="applyScene(scene)">应用</el-button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'

const policies = ref([
  {
    id: 1,
    name: '均衡负载',
    description: '默认模式，优先保证集群整体稳定与节点寿命。',
    cpuLimit: 80,
    memLimit: 85,
    maxConcurrency: 4,
    retryCount: 2,
    preferLowLoad: true,
    preferFreshHeartbeat: true,
    blockHotNode: true,
    autoRetry: true,
    note: '适合常规生产，保持任务均匀分布。',
    rules: [
      { title: '负载过滤', detail: 'CPU 或内存超过阈值的节点不参与派发。' },
      { title: '心跳排序', detail: '最近 60 秒内上报的节点排位更高。' },
      { title: '失败切换', detail: '节点失败后自动切换候补节点继续执行。' }
    ]
  },
  {
    id: 2,
    name: '低延迟优先',
    description: '优先将任务压向最快节点，适合交互式推理。',
    cpuLimit: 90,
    memLimit: 92,
    maxConcurrency: 6,
    retryCount: 1,
    preferLowLoad: false,
    preferFreshHeartbeat: true,
    blockHotNode: false,
    autoRetry: true,
    note: '适合实时问答与短文本任务。',
    rules: [
      { title: '性能倾斜', detail: '允许更高并发，优先使用最近响应快的节点。' },
      { title: '心跳门槛', detail: '心跳超时节点直接降权。' },
      { title: '单次回退', detail: '只做一次自动重试，避免堆积。' }
    ]
  },
  {
    id: 3,
    name: '夜间压缩',
    description: '夜间低峰模式，严格控制接单节点数量。',
    cpuLimit: 70,
    memLimit: 75,
    maxConcurrency: 3,
    retryCount: 0,
    preferLowLoad: true,
    preferFreshHeartbeat: false,
    blockHotNode: true,
    autoRetry: false,
    note: '适合夜间批处理与低成本托管运行。',
    rules: [
      { title: '紧缩接单', detail: '仅保留核心节点参与任务执行。' },
      { title: '严格阈值', detail: '达到阈值立即阻断任务派发。' },
      { title: '人工复核', detail: '失败任务保留待人工处理。' }
    ]
  }
])

const scenes = [
  { name: '实时客服', detail: '提高并发、降低延迟阈值，优先交互性能。', patch: { cpuLimit: 88, memLimit: 90, maxConcurrency: 6 } },
  { name: '内容批处理', detail: '降低并发，强调稳定和失败重试。', patch: { cpuLimit: 78, memLimit: 82, maxConcurrency: 3, retryCount: 3 } },
  { name: '夜间巡检', detail: '减少活跃节点数量，降低资源占用。', patch: { cpuLimit: 68, memLimit: 72, maxConcurrency: 2, retryCount: 0 } }
]

const currentPolicyId = ref(1)
const currentPolicy = computed(() => policies.value.find(item => item.id === currentPolicyId.value) || policies.value[0])

const applyScene = (scene) => {
  Object.assign(currentPolicy.value, scene.patch)
}
</script>

<style scoped>
.page-container { padding: 20px; display: flex; flex-direction: column; gap: 20px; }
.hero-band {
  background: linear-gradient(135deg, #fff7ed, #ffe3bf 55%, #ffd7a6);
  border-radius: 18px;
  padding: 24px 26px;
  display: flex;
  justify-content: space-between;
  gap: 20px;
}
.hero-band h1 { margin: 0 0 10px; font-size: 28px; color: #8a3d00; }
.hero-band p { margin: 0; max-width: 780px; color: #9e5b2a; line-height: 1.7; }
.hero-side { display: flex; gap: 14px; }
.hero-chip {
  min-width: 130px;
  background: rgba(255,255,255,0.62);
  border-radius: 14px;
  padding: 12px 14px;
  backdrop-filter: blur(8px);
}
.hero-num { display: block; font-size: 22px; font-weight: 800; color: #5f2800; }
.hero-label { font-size: 12px; color: #9a642f; }
.policy-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 16px; }
.policy-card {
  border: 1px solid #f0e0ce;
  background: #fff;
  border-radius: 16px;
  padding: 18px;
  text-align: left;
  cursor: pointer;
  box-shadow: 0 10px 24px rgba(114, 77, 31, 0.06);
}
.policy-card.active { border-color: #f59e0b; box-shadow: 0 0 0 3px rgba(245, 158, 11, 0.12); }
.policy-card-top { display: flex; justify-content: space-between; gap: 10px; align-items: center; margin-bottom: 10px; }
.policy-card h3 { margin: 0; color: #7a3412; }
.policy-card p { margin: 0 0 12px; color: #7c6858; line-height: 1.6; }
.policy-kpis { display: flex; gap: 12px; flex-wrap: wrap; font-size: 12px; color: #9d6c40; }
.main-grid, .bottom-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 20px; }
.panel {
  background: #fff;
  border-radius: 16px;
  padding: 20px;
  box-shadow: 0 10px 26px rgba(15, 23, 42, 0.06);
}
.panel-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; gap: 12px; }
.panel-head h3 { margin: 0; color: #1e293b; font-size: 18px; }
.subtle { font-size: 12px; color: #94a3b8; }
.toggle-list { display: flex; flex-direction: column; gap: 14px; }
.toggle-item {
  display: flex;
  justify-content: space-between;
  gap: 14px;
  align-items: center;
  padding: 14px 0;
  border-bottom: 1px solid #eef2f6;
}
.toggle-item:last-child { border-bottom: none; }
.toggle-item strong { color: #243447; }
.toggle-item p { margin: 4px 0 0; color: #7f8ea3; font-size: 13px; }
.rule-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 14px; }
.rule-card {
  border: 1px solid #ecf0f5;
  background: #f8fafc;
  border-radius: 14px;
  padding: 16px;
}
.rule-card h4 { margin: 0 0 8px; color: #223045; }
.rule-card p { margin: 0; color: #6e7e92; line-height: 1.6; font-size: 13px; }
.scene-list { display: flex; flex-direction: column; gap: 12px; }
.scene-item {
  display: flex;
  justify-content: space-between;
  gap: 14px;
  align-items: center;
  padding: 14px 16px;
  border: 1px solid #edf2f7;
  border-radius: 14px;
  background: #fbfdff;
}
.scene-item strong { color: #223045; }
.scene-item p { margin: 4px 0 0; font-size: 13px; color: #72839a; }
@media (max-width: 1100px) {
  .policy-grid, .main-grid, .bottom-grid, .rule-grid { grid-template-columns: 1fr; }
  .hero-band { flex-direction: column; }
}
</style>
