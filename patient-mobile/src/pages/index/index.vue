<script setup lang="ts">
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { fetchHealth } from '@/api/health'
import { ensureLogin, getPatient, getUser, clearSession, setPendingNav, type PatientBrief } from '@/store/session'

type ServiceState = 'checking' | 'up' | 'down'

const serviceState = ref<ServiceState>('checking')
const patient = ref<PatientBrief | null>(null)
const userName = ref('')

const statusText = computed(() => {
  if (serviceState.value === 'checking') return '正在检测服务'
  if (serviceState.value === 'up') return '服务连接正常'
  return '服务暂不可用'
})

const services = [
  {
    url: '/pages/appointment/appointment',
    name: '预约挂号',
    short: '预',
    desc: '门诊与专科预约',
    tint: 'linear-gradient(135deg, #2d6dad, #4c8fd8)',
    needsPatient: true
  },
  {
    url: '/pages/escort/escort',
    name: '陪诊服务',
    short: '陪',
    desc: '院内全程陪同',
    tint: 'linear-gradient(135deg, #2f9e73, #63bf98)',
    needsPatient: true
  },
  {
    url: '/pages/homevisit/apply',
    name: '上门服务',
    short: '门',
    desc: '医护到家服务申请',
    tint: 'linear-gradient(135deg, #1f7a8c, #4aa3b5)',
    needsPatient: true
  },
  {
    url: '/pages/family/family',
    name: '我的家庭',
    short: '家',
    desc: '家庭成员与代办',
    tint: 'linear-gradient(135deg, #7a5cc6, #9b84db)',
    needsPatient: true
  },
  {
    url: '/pages/chat/list',
    name: '在线客服',
    short: '客',
    desc: '智能客服与人工',
    tint: 'linear-gradient(135deg, #c9892b, #e0b061)',
    needsPatient: true
  },
  {
    url: '/pages/homevisit/doctor',
    name: '医生工作台',
    short: '医',
    desc: '接单与服务记录',
    tint: 'linear-gradient(135deg, #98503c, #c47a5f)',
    needsPatient: false
  }
]

onShow(() => {
  if (!ensureLogin()) {
    return
  }
  patient.value = getPatient()
  userName.value = getUser()?.displayName || getUser()?.username || ''
  checkHealth()
})

async function checkHealth() {
  serviceState.value = 'checking'
  try {
    const health = await fetchHealth()
    serviceState.value = health.status === 'UP' ? 'up' : 'down'
  } catch {
    serviceState.value = 'down'
  }
}

function openService(service: { url: string; needsPatient: boolean }) {
  if (!ensureLogin()) {
    return
  }
  if (service.needsPatient && !getPatient()) {
    setPendingNav(service.url)
    uni.navigateTo({ url: '/pages/patient/select' })
    return
  }
  uni.navigateTo({ url: service.url })
}

function selectPatient() {
  uni.navigateTo({ url: '/pages/patient/select' })
}

function logout() {
  uni.showModal({
    title: '退出登录',
    content: '确认退出当前账号吗？',
    success: (res) => {
      if (!res.confirm) {
        return
      }
      clearSession()
      uni.reLaunch({ url: '/pages/login/login' })
    }
  })
}
</script>

<template>
  <view class="page">
    <view class="hero">
      <view class="hero-top">
        <view>
          <view class="hero-title">患者服务中心</view>
          <view class="hero-sub">{{ userName }}，您好</view>
        </view>
        <view class="logout" @click="logout">退出</view>
      </view>
      <view class="hero-status">
        <view class="dot" :class="'dot-' + serviceState"></view>
        <text>{{ statusText }}</text>
        <text class="refresh" @click="checkHealth">刷新</text>
      </view>
    </view>

    <view class="card patient-card" @click="selectPatient">
      <view>
        <view class="muted">当前就诊人</view>
        <view class="patient-name">{{ patient ? patient.name : '未选择，点击选择' }}</view>
        <view v-if="patient" class="muted">{{ patient.customerNo }} · {{ patient.phoneMasked }}</view>
      </view>
      <text class="link">{{ patient ? '切换' : '选择' }}</text>
    </view>

    <view class="grid">
      <view v-for="s in services" :key="s.url" class="service" @click="openService(s)">
        <view class="service-icon" :style="{ background: s.tint }">{{ s.short }}</view>
        <view class="service-name">{{ s.name }}</view>
        <view class="muted">{{ s.desc }}</view>
      </view>
    </view>

    <view class="card notice">
      <view class="card-title">温馨提示</view>
      <view class="muted">预约与陪诊请至少提前一天提交；如需修改就诊人信息，请联系门诊服务台。</view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.hero {
  background: linear-gradient(135deg, #1f4e79, #3576b8);
  border-radius: 20rpx;
  padding: 32rpx 28rpx;
  color: #fff;
  margin-bottom: 24rpx;
}

.hero-top {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}

.hero-title {
  font-size: 36rpx;
  font-weight: 700;
}

.hero-sub {
  font-size: 24rpx;
  opacity: 0.85;
  margin-top: 6rpx;
}

.logout {
  font-size: 24rpx;
  padding: 6rpx 18rpx;
  border: 1rpx solid rgba(255, 255, 255, 0.5);
  border-radius: 999rpx;
}

.hero-status {
  display: flex;
  align-items: center;
  gap: 10rpx;
  margin-top: 24rpx;
  font-size: 24rpx;
  opacity: 0.9;
}

.dot {
  width: 14rpx;
  height: 14rpx;
  border-radius: 50%;
  background: #d9d9d9;
}

.dot-up {
  background: #6fe0a8;
}

.dot-down {
  background: #ff8a80;
}

.refresh {
  margin-left: auto;
  text-decoration: underline;
}

.patient-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.patient-name {
  font-size: 32rpx;
  font-weight: 600;
  margin: 4rpx 0;
}

.grid {
  display: flex;
  flex-wrap: wrap;
  gap: 20rpx;
  margin-bottom: 24rpx;
}

.service {
  width: calc(50% - 10rpx);
  box-sizing: border-box;
  background: #fff;
  border-radius: 20rpx;
  padding: 28rpx 24rpx;
  box-shadow: 0 6rpx 24rpx rgba(31, 78, 121, 0.06);
}

.service-icon {
  width: 76rpx;
  height: 76rpx;
  line-height: 76rpx;
  text-align: center;
  border-radius: 18rpx;
  color: #fff;
  font-size: 32rpx;
  font-weight: 600;
  margin-bottom: 16rpx;
}

.service-name {
  font-size: 30rpx;
  font-weight: 600;
}

.link {
  color: #2d6dad;
  font-size: 26rpx;
}
</style>
