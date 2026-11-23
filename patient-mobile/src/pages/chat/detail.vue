<script setup lang="ts">
import { nextTick, ref } from 'vue'
import { onLoad, onUnload } from '@dcloudio/uni-app'
import {
  fetchMessages,
  requestHandoff,
  sendMessage,
  type ChatMessageView
} from '@/api/chat'
import { ensureLogin } from '@/store/session'

const sessionId = ref('')
const messages = ref<ChatMessageView[]>([])
const input = ref('')
const sending = ref(false)
const loading = ref(false)
const scrollInto = ref('')
let timer: ReturnType<typeof setInterval> | null = null

onLoad((query) => {
  const id = (query?.id as string) || ''
  if (!ensureLogin()) {
    return
  }
  if (!id) {
    uni.showToast({ title: '会话不存在', icon: 'none' })
    uni.navigateBack({ delta: 1 })
    return
  }
  sessionId.value = id
  load()
  timer = setInterval(load, 8000)
})

onUnload(() => {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
})

async function load() {
  if (!sessionId.value) {
    return
  }
  loading.value = true
  try {
    const result = await fetchMessages(sessionId.value)
    const items = result.items || []
    const lastId = messages.value.length ? messages.value[messages.value.length - 1].id : ''
    messages.value = items
    if (items.length && items[items.length - 1].id !== lastId) {
      nextTick(() => {
        scrollInto.value = 'msg-' + items[items.length - 1].id
      })
    }
  } catch (error) {
    uni.showToast({ title: (error as Error).message, icon: 'none' })
  } finally {
    loading.value = false
  }
}

async function send() {
  const content = input.value.trim()
  if (!content) {
    return
  }
  sending.value = true
  try {
    await sendMessage(sessionId.value, content)
    input.value = ''
    await load()
  } catch (error) {
    uni.showToast({ title: (error as Error).message, icon: 'none' })
  } finally {
    sending.value = false
  }
}

function toHuman() {
  uni.showModal({
    title: '转人工服务',
    content: '确认转接人工客服吗？工作时间外可能需要等待。',
    success: async (res) => {
      if (!res.confirm) {
        return
      }
      try {
        const result = await requestHandoff(sessionId.value, 'explicit')
        uni.showToast({
          title: result.accepted ? '已转入人工队列，请稍候' : result.message || '请再次点击转人工确认',
          icon: 'none'
        })
        await load()
      } catch (error) {
        uni.showToast({ title: (error as Error).message, icon: 'none' })
      }
    }
  })
}

function senderLabel(type: string) {
  if (type === 'STAFF') {
    return '人工客服'
  }
  if (type === 'AI') {
    return '智能助手'
  }
  if (type === 'SYSTEM') {
    return '系统'
  }
  return '我'
}
</script>

<template>
  <view class="chat-page">
    <scroll-view class="messages" scroll-y :scroll-into-view="scrollInto">
      <view v-if="messages.length === 0 && !loading" class="empty-block">
        发送第一条消息开始咨询
      </view>
      <view
        v-for="m in messages"
        :key="m.id"
        :id="'msg-' + m.id"
        class="msg-row"
        :class="{ mine: m.senderType === 'CUSTOMER' }"
      >
        <view class="bubble">
          <view class="sender">{{ senderLabel(m.senderType) }}</view>
          <view class="content">{{ m.content }}</view>
        </view>
      </view>
    </scroll-view>

    <view class="composer">
      <button class="btn-ghost" @click="toHuman">转人工</button>
      <input
        v-model="input"
        class="input flex"
        placeholder="请输入问题"
        confirm-type="send"
        @confirm="send"
      />
      <button class="send-btn" :disabled="sending" @click="send">发送</button>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.chat-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: #f4f7fb;
}

.messages {
  flex: 1;
  padding: 24rpx;
  box-sizing: border-box;
}

.msg-row {
  display: flex;
  margin-bottom: 20rpx;
}

.msg-row.mine {
  justify-content: flex-end;
}

.bubble {
  max-width: 76%;
  background: #fff;
  border-radius: 16rpx;
  padding: 16rpx 20rpx;
  box-shadow: 0 4rpx 16rpx rgba(31, 78, 121, 0.06);
}

.mine .bubble {
  background: #2d6dad;
  color: #fff;
}

.sender {
  font-size: 22rpx;
  color: #8a94a6;
  margin-bottom: 6rpx;
}

.mine .sender {
  color: rgba(255, 255, 255, 0.75);
}

.content {
  font-size: 28rpx;
  word-break: break-all;
}

.composer {
  display: flex;
  align-items: center;
  gap: 14rpx;
  padding: 18rpx 20rpx;
  background: #fff;
  border-top: 1rpx solid #e6ebf3;
}

.flex {
  flex: 1;
  height: 76rpx;
  line-height: 76rpx;
}

.send-btn {
  height: 76rpx;
  line-height: 76rpx;
  padding: 0 28rpx;
  border-radius: 12rpx;
  background: #2d6dad;
  color: #fff;
  font-size: 28rpx;
  border: none;
}
</style>
