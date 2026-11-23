<script setup lang="ts">
import { ref } from 'vue'
import { searchCustomers, type CustomerBrief } from '@/api/customer'
import { ensureLogin, setPatient, takePendingNav } from '@/store/session'

const keyword = ref('')
const list = ref<CustomerBrief[]>([])
const loading = ref(false)
const searched = ref(false)

async function search() {
  if (!ensureLogin()) {
    return
  }
  if (!keyword.value.trim()) {
    uni.showToast({ title: '请输入建档手机号', icon: 'none' })
    return
  }
  loading.value = true
  try {
    const result = await searchCustomers(keyword.value.trim())
    list.value = result.items || []
    searched.value = true
  } catch (error) {
    uni.showToast({ title: (error as Error).message, icon: 'none' })
  } finally {
    loading.value = false
  }
}

function choose(item: CustomerBrief) {
  setPatient({
    id: item.id,
    customerNo: item.customerNo,
    name: item.name,
    gender: item.gender,
    phoneMasked: item.phoneMasked
  })
  const target = takePendingNav()
  uni.showToast({ title: '已选择就诊人', icon: 'success' })
  setTimeout(() => {
    if (target) {
      uni.redirectTo({ url: target })
    } else {
      uni.navigateBack({ delta: 1 })
    }
  }, 250)
}
</script>

<template>
  <view class="page">
    <view class="card">
      <view class="card-title">选择就诊人</view>
      <view class="muted">输入在医院建档时登记的手机号，匹配您的就诊档案</view>
      <view class="field" style="margin-top: 20rpx">
        <input
          v-model="keyword"
          class="input"
          type="number"
          placeholder="请输入完整手机号"
          @confirm="search"
        />
      </view>
      <button class="btn-primary" :disabled="loading" @click="search">
        {{ loading ? '查询中…' : '查询就诊人' }}
      </button>
    </view>

    <view v-if="searched && list.length === 0" class="card empty-block">
      未找到建档记录，请确认手机号或先到服务台建档
    </view>

    <view v-for="item in list" :key="item.id" class="card person">
      <view class="person-left">
        <view class="person-name">{{ item.name }}</view>
        <view class="muted">{{ item.customerNo }} · {{ item.gender === 'female' ? '女' : '男' }}</view>
      </view>
      <view class="person-right">
        <view class="person-phone">{{ item.phoneMasked || '—' }}</view>
        <button class="btn-ghost" @click="choose(item)">选择</button>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.person {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.person-name {
  font-size: 30rpx;
  font-weight: 600;
}

.person-phone {
  color: #5b6679;
  font-size: 26rpx;
  text-align: right;
  margin-bottom: 8rpx;
}

.person-right {
  text-align: right;
}
</style>
