<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { fetchHealth } from '@/api/health'

type ServiceState = 'checking' | 'up' | 'down'

const serviceState = ref<ServiceState>('checking')

const statusText = computed(() => {
  if (serviceState.value === 'checking') return '正在检测服务'
  if (serviceState.value === 'up') return '服务连接正常'
  return '服务暂不可用'
})

const statusClass = computed(() => `status-${serviceState.value}`)

const services = [
  {
    name: '预约挂号',
    short: '预',
    desc: '门诊与专科预约',
    tint: 'linear-gradient(135deg, #2d6dad, #4c8fd8)'
  },
  {
    name: '陪诊服务',
    short: '陪',
    desc: '院内全程陪同',
    tint: 'linear-gradient(135deg, #2f9e73, #63bf98)'
  },
  {
    name: '我的家庭',
    short: '家',
    desc: '家庭成员与代办',
    tint: 'linear-gradient(135deg, #7a5cc6, #9b84db)'
  },
  {
    name: '在线客服',
    short: '客',
    desc: '智能客服与人工',
    tint: 'linear-gradient(135deg, #c9892b, #e0b061)'
  }
]

const notices = [
  '平台处于 V1.0 建设阶段，服务将在后续版本逐步开放。',
  '请勿在开发与测试环境填写真实患者信息。'
]

function openFeature(name: string) {
  uni.showToast({
    title: `${name}将在后续版本开放`,
    icon: 'none'
  })
}

onMounted(async () => {
  try {
    const health = await fetchHealth()
    serviceState.value = health.status === 'UP' ? 'up' : 'down'
  } catch {
    serviceState.value = 'down'
  }
})
</script>

<template>
  <view class="page">
    <view class="hero">
      <view class="hero-top">
        <view class="brand">
          <view class="brand-mark">CR</view>
          <view class="brand-text">
            <text class="brand-name">Hospital CRM</text>
            <text class="brand-sub">医院智慧服务</text>
          </view>
        </view>
        <view class="status-pill" :class="statusClass">
          <view class="status-dot"></view>
          <text>{{ statusText }}</text>
        </view>
      </view>

      <text class="hero-title">您好，欢迎使用医院服务中心</text>
      <text class="hero-desc">预约、陪诊、家庭账户与在线客服将在后续版本陆续开放。</text>

      <button class="hero-button" @click="openFeature('预约服务')">预约服务</button>
    </view>

    <view class="section">
      <view class="section-head">
        <text class="section-title">便民服务</text>
        <text class="section-more">逐步开放</text>
      </view>

      <view class="service-grid">
        <view v-for="item in services" :key="item.name" class="service-card" @click="openFeature(item.name)">
          <view class="service-icon" :style="{ background: item.tint }">{{ item.short }}</view>
          <text class="service-name">{{ item.name }}</text>
          <text class="service-desc">{{ item.desc }}</text>
          <text class="service-tag">即将开放</text>
        </view>
      </view>
    </view>

    <view class="card progress-card">
      <view class="section-head">
        <text class="section-title">服务进度</text>
        <text class="section-more">暂无进行中的服务</text>
      </view>
      <view class="empty-state">
        <view class="empty-mark"></view>
        <text class="empty-title">还没有服务记录</text>
        <text class="empty-desc">完成预约或陪诊下单后，可在这里查看进度与记录。</text>
      </view>
    </view>

    <view class="card notice-card">
      <view class="section-head">
        <text class="section-title">服务公告</text>
      </view>
      <view v-for="(notice, index) in notices" :key="index" class="notice-item">
        <view class="notice-index">{{ index + 1 }}</view>
        <text class="notice-text">{{ notice }}</text>
      </view>
    </view>

    <view class="footnote">
      <text>Hospital CRM V1.0 · CRM-001 基础骨架</text>
    </view>
  </view>
</template>

<style scoped lang="scss">
.page {
  min-height: 100vh;
  padding: 28rpx 28rpx 48rpx;
  box-sizing: border-box;
}

.hero {
  padding: 36rpx 34rpx 40rpx;
  border-radius: 32rpx;
  background:
    radial-gradient(120% 120% at 100% 0%, rgba(255, 255, 255, 0.22), transparent 55%),
    linear-gradient(135deg, #1f4f86 0%, #2d6dad 58%, #4c8fd8 100%);
  box-shadow: 0 18rpx 46rpx rgba(31, 79, 134, 0.28);
  color: #ffffff;
}

.hero-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.brand {
  display: flex;
  align-items: center;
}

.brand-mark {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 68rpx;
  height: 68rpx;
  margin-right: 16rpx;
  border-radius: 20rpx;
  background: rgba(255, 255, 255, 0.16);
  border: 1rpx solid rgba(255, 255, 255, 0.28);
  font-size: 24rpx;
  font-weight: 700;
  letter-spacing: 0.04em;
}

.brand-text {
  display: flex;
  flex-direction: column;
  line-height: 1.35;
}

.brand-name {
  font-size: 28rpx;
  font-weight: 700;
}

.brand-sub {
  font-size: 21rpx;
  color: rgba(255, 255, 255, 0.68);
}

.status-pill {
  display: flex;
  align-items: center;
  padding: 8rpx 18rpx;
  border-radius: 999rpx;
  background: rgba(255, 255, 255, 0.16);
  font-size: 21rpx;
}

.status-dot {
  width: 10rpx;
  height: 10rpx;
  margin-right: 10rpx;
  border-radius: 50%;
  background: #8fd3b5;
}

.status-up .status-dot {
  background: #8fd3b5;
}

.status-down .status-dot {
  background: #ffb4ae;
}

.status-checking .status-dot {
  background: #f2d49b;
}

.hero-title {
  display: block;
  margin-top: 42rpx;
  font-size: 40rpx;
  font-weight: 700;
  line-height: 1.4;
}

.hero-desc {
  display: block;
  margin-top: 14rpx;
  font-size: 24rpx;
  line-height: 1.75;
  color: rgba(255, 255, 255, 0.78);
}

.hero-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 220rpx;
  height: 78rpx;
  margin: 32rpx 0 0;
  padding: 0;
  border: none;
  border-radius: 999rpx;
  background: #ffffff;
  color: #2d6dad;
  font-size: 27rpx;
  font-weight: 700;
  line-height: 78rpx;
}

.hero-button::after {
  border: none;
}

.section {
  margin-top: 34rpx;
}

.section-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 18rpx;
}

.section-title {
  font-size: 30rpx;
  font-weight: 700;
}

.section-more {
  font-size: 22rpx;
  color: #6b7a90;
}

.service-grid {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
}

.service-card {
  position: relative;
  width: 48%;
  margin-bottom: 20rpx;
  padding: 28rpx 26rpx 24rpx;
  border-radius: 26rpx;
  background: #ffffff;
  border: 1rpx solid #e3eaf3;
  box-shadow: 0 12rpx 30rpx rgba(31, 79, 134, 0.06);
  box-sizing: border-box;
}

.service-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 74rpx;
  height: 74rpx;
  margin-bottom: 18rpx;
  border-radius: 22rpx;
  color: #ffffff;
  font-size: 28rpx;
  font-weight: 700;
}

.service-name {
  display: block;
  font-size: 28rpx;
  font-weight: 700;
}

.service-desc {
  display: block;
  margin-top: 4rpx;
  font-size: 22rpx;
  color: #6b7a90;
}

.service-tag {
  display: inline-block;
  margin-top: 16rpx;
  padding: 4rpx 14rpx;
  border-radius: 999rpx;
  background: #e8f1fb;
  color: #2d6dad;
  font-size: 20rpx;
}

.card {
  margin-top: 20rpx;
  padding: 30rpx 28rpx;
  border-radius: 28rpx;
  background: #ffffff;
  border: 1rpx solid #e3eaf3;
  box-shadow: 0 12rpx 30rpx rgba(31, 79, 134, 0.06);
}

.progress-card {
  margin-top: 14rpx;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 34rpx 20rpx 26rpx;
}

.empty-mark {
  width: 92rpx;
  height: 92rpx;
  border-radius: 30rpx;
  background: linear-gradient(135deg, #e8f1fb, #f4f8fd);
  border: 1rpx dashed #b0c8e4;
}

.empty-title {
  margin-top: 18rpx;
  font-size: 26rpx;
  font-weight: 600;
}

.empty-desc {
  margin-top: 8rpx;
  font-size: 22rpx;
  color: #6b7a90;
  text-align: center;
  line-height: 1.7;
}

.notice-item {
  display: flex;
  align-items: flex-start;
  padding: 16rpx 0;
  border-top: 1rpx solid #eef2f7;
}

.notice-item:first-of-type {
  border-top: none;
}

.notice-index {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36rpx;
  height: 36rpx;
  margin-right: 14rpx;
  flex-shrink: 0;
  border-radius: 10rpx;
  background: #fdf5e6;
  color: #c9892b;
  font-size: 20rpx;
  font-weight: 700;
}

.notice-text {
  flex: 1;
  font-size: 23rpx;
  color: #3f4e66;
  line-height: 1.7;
}

.footnote {
  margin-top: 36rpx;
  text-align: center;
  color: #9aa7b8;
  font-size: 20rpx;
}
</style>
