<script setup lang="ts">
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { createSession, fetchMySessions, type ChatSessionView } from '@/api/chat'
import { ensureLogin, getPatient, type PatientBrief } from '@/store/session'

const patient = ref<PatientBrief | null>(null)
const sessions = ref<ChatSessionView[]>([])
const loading = ref(false)
const creating = ref(false)

const statusText: Record<string, string> = {
  AI: '智能接待',
  QUEUED: '排队中',
  ACTIVE: '人工服务',
  CLOSED: '已结束'
}

onShow(async () => {
  if (!ensureLogin()) {
    return
  }
  patient.value = getPatient()
  if (!patient.value) {
    uni.navigateTo({ url: '/pages/patient/select' })
    return
  }
  await load()
})

async function load() {
  loading.value = true
  try {
    const result = await fetchMySessions(patient.value!.id)
    sessions.value = (result.items || []).slice().sort((a, b) => {
      return (b.lastMessageAt || '').localeCompare(a.lastMessageAt || '')
    })
  } catch (error) {
    uni.showToast({ title: (error as Error).message, icon: 'none' })
  } finally {
    loading.value = false
  }
}

async function startChat() {
  creating.value = true
  try {
    const detail = await createSession(patient.value!.id, 'h5')
    uni.navigateTo({ url: `/pages/chat/detail?id=${detail.session.id}` })
  } catch (error) {
    uni.showToast({ title: (error as Error).message, icon: 'none' })
  } finally {
    creating.value = false
  }
}

function open(item: ChatSessionView) {
  uni.navigateTo({ url: `/pages/chat/detail?id=${item.id}` })
}

function fmtTime(value?: string) {
  return value ? value.replace('T', ' ').slice(0, 16) : ''
}
</script>

<template>
  <view class="page">
    <view class="card">
      <view class="muted">当前就诊人</view>
      <view class="patient-name">{{ patient?.name || '未选择' }}</view>
    </view>

    <view class="card">
      <view class="card-title">在线客服</view>
      <view class="muted">默认由智能助手接待，可随时转人工</view>
      <button class="btn-primary" style="margin-top: 20rpx" :disabled="creating" @click="startChat">
        {{ creating ? '创建中…' : '发起咨询' }}
      </button>
    </view>

    <view class="card">
      <view class="card-title">
        <text>我的会话</text>
        <text class="muted">{{ sessions.length }} 个</text>
      </view>
      <view v-if="sessions.length === 0 && !loading" class="empty-block">暂无会话记录</view>
      <view v-for="item in sessions" :key="item.id" class="session" @click="open(item)">
        <view class="session-head">
          <text class="session-no">{{ item.sessionNo }}</text>
          <text class="status" :class="'st-' + item.status">{{ statusText[item.status] || item.status }}</text>
        </view>
        <view class="session-preview">{{ item.lastMessagePreview || '暂无消息' }}</view>
        <view class="muted">{{ fmtTime(item.lastMessageAt) }}</view>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.patient-name {
  font-size: 32rpx;
  font-weight: 600;
}

.session {
  padding: 20rpx 0;
  border-bottom: 1rpx solid #eef1f6;
}

.session:last-child {
  border-bottom: none;
}

.session-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.session-no {
  font-weight: 600;
}

.session-preview {
  color: #4a5568;
  font-size: 26rpx;
  margin: 6rpx 0;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.status {
  font-size: 24rpx;
  padding: 4rpx 14rpx;
  border-radius: 999rpx;
  background: #eef4fc;
  color: #2d6dad;
}

.st-QUEUED {
  background: #fff6e8;
  color: #b5852f;
}

.st-ACTIVE {
  background: #eef7f1;
  color: #2f8f6b;
}

.st-CLOSED {
  background: #f2f3f5;
  color: #8a94a6;
}
</style>
