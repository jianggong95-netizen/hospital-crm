<script setup lang="ts">
import { computed } from 'vue'
import { phaseStatusLabels, phases } from '@/data/roadmap'

const taskCount = computed(() => phases.reduce((total, phase) => total + phase.tasks.length, 0))
</script>

<template>
  <div class="roadmap">
    <section class="intro card">
      <div>
        <span class="badge badge-primary">交付原则</span>
        <h2>按任务编号逐项交付，每个任务独立验收</h2>
        <p>
          先建数据模型与 API 契约，再写业务代码与页面。每个任务必须包含单元测试、集成测试、权限测试与迁移脚本，
          由用户确认后进入下一任务。
        </p>
      </div>
      <div class="intro-stats">
        <div>
          <strong>{{ phases.length }}</strong>
          <span>实施阶段</span>
        </div>
        <div>
          <strong>{{ taskCount }}</strong>
          <span>开发任务</span>
        </div>
        <div>
          <strong>1</strong>
          <span>当前已完成</span>
        </div>
      </div>
    </section>

    <section class="timeline">
      <article v-for="phase in phases" :key="phase.key" class="card phase-card">
        <div class="phase-head">
          <span class="phase-key" :class="{ active: phase.status === 'active' }">{{ phase.key }}</span>
          <div class="phase-title">
            <h3>{{ phase.name }}</h3>
            <p>{{ phase.focus }}</p>
          </div>
          <span
            class="badge"
            :class="phase.status === 'active' ? 'badge-success' : phase.status === 'done' ? 'badge-primary' : 'badge-neutral'"
          >
            {{ phaseStatusLabels[phase.status] }}
          </span>
        </div>
        <ul class="task-list">
          <li v-for="task in phase.tasks" :key="task">
            <span class="task-dot" :class="{ active: phase.status === 'active' }"></span>
            <span>{{ task }}</span>
          </li>
        </ul>
      </article>
    </section>

    <section class="card guardrails">
      <h3>不可逾越的实施边界</h3>
      <div class="guardrail-grid">
        <div>
          <strong>HIS 只读</strong>
          <p>HIS 连接账号默认只读，不提供任意 SQL 执行端点，不写 HIS 收费表。</p>
        </div>
        <div>
          <strong>敏感数据</strong>
          <p>证件、手机号、地址加密或脱敏；测试仅使用合成或脱敏数据。</p>
        </div>
        <div>
          <strong>家庭授权</strong>
          <p>家庭关系不等于病历访问授权，代办、代付、共享权益分别校验。</p>
        </div>
        <div>
          <strong>资金流水</strong>
          <p>订单、支付、退款、余额和次卡使用独立流水与幂等机制。</p>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped lang="scss">
.roadmap {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.intro {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 32px;
  padding: 30px 32px;
  background:
    radial-gradient(120% 160% at 100% 0%, rgba(76, 143, 216, 0.14), transparent 60%),
    #fff;
}

.intro h2 {
  margin: 12px 0 8px;
  font-size: 24px;
}

.intro p {
  max-width: 720px;
  color: var(--crm-ink-soft);
  font-size: 13.5px;
  line-height: 1.85;
}

.intro-stats {
  display: flex;
  gap: 28px;
  flex-shrink: 0;
}

.intro-stats div {
  text-align: center;
}

.intro-stats strong {
  display: block;
  font-size: 30px;
  color: var(--crm-primary);
}

.intro-stats span {
  color: var(--crm-muted);
  font-size: 12px;
}

.timeline {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.phase-card {
  padding: 22px 24px;
}

.phase-head {
  display: flex;
  align-items: center;
  gap: 16px;
}

.phase-key {
  display: grid;
  place-items: center;
  width: 46px;
  height: 46px;
  flex-shrink: 0;
  border-radius: 13px;
  background: #eef2f7;
  color: var(--crm-muted);
  font-weight: 700;
}

.phase-key.active {
  background: linear-gradient(135deg, #2d6dad, #4c8fd8);
  color: #fff;
  box-shadow: 0 8px 18px rgba(45, 109, 173, 0.24);
}

.phase-title {
  flex: 1;
  min-width: 0;
}

.phase-title h3 {
  font-size: 16px;
}

.phase-title p {
  color: var(--crm-muted);
  font-size: 12.5px;
}

.task-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px 24px;
  margin: 16px 0 0 62px;
  padding: 0;
  list-style: none;
}

.task-list li {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 7px 10px;
  border-radius: 10px;
  background: #f8fafc;
  color: var(--crm-ink-soft);
  font-size: 13px;
}

.task-dot {
  width: 7px;
  height: 7px;
  flex-shrink: 0;
  border-radius: 50%;
  background: #c6d0dd;
}

.task-dot.active {
  background: var(--crm-success);
  box-shadow: 0 0 0 4px var(--crm-success-soft);
}

.guardrails {
  padding: 24px 28px;
}

.guardrails h3 {
  font-size: 17px;
  margin-bottom: 16px;
}

.guardrail-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.guardrail-grid div {
  padding: 16px;
  border-radius: var(--crm-radius-md);
  background: #f8fafc;
  border: 1px solid var(--crm-border);
}

.guardrail-grid strong {
  color: var(--crm-primary);
  font-size: 13px;
}

.guardrail-grid p {
  margin-top: 6px;
  color: var(--crm-muted);
  font-size: 12.5px;
  line-height: 1.7;
}

@media (max-width: 1180px) {
  .intro {
    flex-direction: column;
    align-items: flex-start;
  }

  .guardrail-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .task-list {
    grid-template-columns: 1fr;
    margin-left: 0;
  }

  .intro-stats {
    width: 100%;
    justify-content: space-between;
  }

  .guardrail-grid {
    grid-template-columns: 1fr;
  }
}
</style>
