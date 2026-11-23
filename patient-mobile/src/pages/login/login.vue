<script setup lang="ts">
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { login } from '@/api/auth'
import { searchCustomers } from '@/api/customer'
import { getPatient, isLoggedIn, saveSession, setPatient } from '@/store/session'

const username = ref('')
const password = ref('')
const loading = ref(false)

onShow(() => {
  if (isLoggedIn()) {
    uni.reLaunch({ url: '/pages/index/index' })
  }
})

/** 登录成功后按显示名精确匹配建档患者，唯一命中则自动绑定就诊人（如 patient01 → 王建国） */
async function autoBindPatient(displayName: string): Promise<void> {
  if (getPatient()) {
    return
  }
  try {
    const result = await searchCustomers(displayName)
    const matched = (result.items || []).filter((c) => c.name === displayName)
    if (matched.length === 1) {
      setPatient({
        id: matched[0].id,
        customerNo: matched[0].customerNo,
        name: matched[0].name,
        gender: matched[0].gender,
        phoneMasked: matched[0].phoneMasked
      })
    }
  } catch {
    // 无检索权限或未建档时跳过，用户仍可手动选择就诊人
  }
}

async function submit() {
  if (!username.value.trim() || !password.value) {
    uni.showToast({ title: '请输入用户名和密码', icon: 'none' })
    return
  }
  loading.value = true
  try {
    const result = await login(username.value.trim(), password.value)
    saveSession(
      { accessToken: result.accessToken, refreshToken: result.refreshToken },
      result.user
    )
    await autoBindPatient(result.user.displayName)
    uni.showToast({ title: '登录成功', icon: 'success' })
    setTimeout(() => uni.reLaunch({ url: '/pages/index/index' }), 300)
  } catch (error) {
    uni.showToast({ title: (error as Error).message, icon: 'none' })
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <view class="page login">
    <view class="brand">
      <view class="brand-mark">+</view>
      <view class="brand-name">Hospital CRM</view>
      <view class="brand-sub">患者服务中心</view>
    </view>

    <view class="card">
      <view class="card-title">账号登录</view>
      <view class="field">
        <text class="field-label">用户名</text>
        <input v-model="username" class="input" placeholder="请输入用户名" />
      </view>
      <view class="field">
        <text class="field-label">密码</text>
        <input v-model="password" class="input" password placeholder="请输入密码" />
      </view>
      <button class="btn-primary" :disabled="loading" @click="submit">
        {{ loading ? '登录中…' : '登录' }}
      </button>
      <view class="tip">账号由医院服务台开通，忘记密码请联系服务中心</view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.login {
  padding-top: 120rpx;
}

.brand {
  text-align: center;
  margin-bottom: 56rpx;
}

.brand-mark {
  width: 112rpx;
  height: 112rpx;
  line-height: 108rpx;
  margin: 0 auto 20rpx;
  border-radius: 28rpx;
  background: linear-gradient(135deg, #2d6dad, #4c8fd8);
  color: #fff;
  font-size: 56rpx;
  font-weight: 600;
  text-align: center;
}

.brand-name {
  font-size: 40rpx;
  font-weight: 700;
  letter-spacing: 1rpx;
}

.brand-sub {
  color: #8a94a6;
  font-size: 26rpx;
  margin-top: 6rpx;
}

.tip {
  margin-top: 24rpx;
  color: #9aa4b5;
  font-size: 24rpx;
  text-align: center;
}
</style>
