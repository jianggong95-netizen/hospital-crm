<script setup lang="ts">
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { fetchHomeVisitOrders, type HomeVisitOrderSummary } from '@/api/homevisit'
import { ensureLogin, getUser } from '@/store/session'

type TabKey = 'todo' | 'doing' | 'done' | 'all'

const userName = ref('')
const userId = ref('')
const orders = ref<HomeVisitOrderSummary[]>([])
const loading = ref(false)
const activeTab = ref<TabKey>('todo')

const tabs: Array<{ key: TabKey; label: string }> = [
  { key: 'todo', label: '待接单' },
  { key: 'doing', label: '服务中' },
  { key: 'done', label: '已完成' },
  { key: 'all', label: '全部' }
]

const statusText: Record<string, string> = {
  PENDING: '待审核',
  APPROVED: '待派单',
  REJECTED: '已驳回',
  ASSIGNED: '待接单',
  ACCEPTED: '已接单',
  IN_PROGRESS: '服务中',
  COMPLETED: '已完成',
  CANCELLED: '已取消'
}

const tabCounts = computed(() => ({
  todo: orders.value.filter((o) => o.status === 'ASSIGNED').length,
  doing: orders.value.filter((o) => ['ACCEPTED', 'IN_PROGRESS'].includes(o.status)).length,
  done: orders.value.filter((o) => o.status === 'COMPLETED').length,
  all: orders.value.length
}))

const visibleOrders = computed(() => {
  if (activeTab.value === 'todo') {
    return orders.value.filter((o) => o.status === 'ASSIGNED')
  }
  if (activeTab.value === 'doing') {
    return orders.value.filter((o) => ['ACCEPTED', 'IN_PROGRESS'].includes(o.status))
  }
  if (activeTab.value === 'done') {
    return orders.value.filter((o) => o.status === 'COMPLETED')
  }
  return orders.value
})

onShow(async () => {
  if (!ensureLogin()) {
    return
  }
  const user = getUser()
  userName.value = user?.displayName || user?.username || ''
  userId.value = user?.id || ''
  if (!userId.value) {
    uni.showToast({ title: '登录信息缺失，请重新登录', icon: 'none' })
    return
  }
  await load()
})

async function load() {
  loading.value = true
  try {
    const result = await fetchHomeVisitOrders({ staffUserId: userId.value })
    orders.value = result.items || []
  } catch (error) {
    uni.showToast({ title: (error as Error).message, icon: 'none' })
  } finally {
    loading.value = false
  }
}

function openDetail(item: HomeVisitOrderSummary) {
  uni.navigateTo({ url: `/pages/homevisit/doctor-detail?id=${item.id}` })
}

/** 后端 LocalTime 带秒，列表只展示到分钟 */
function hm(value?: string) {
  return value ? value.slice(0, 5) : ''
}
</script>

<template>
  <view class="page">
    <view class="hero">
      <view class="hero-title">医生上门服务台</view>
      <view class="hero-sub">{{ userName }}，今日任务请及时接单</view>
      <view class="hero-stats">
        <view class="stat">
          <view class="stat-num">{{ tabCounts.todo }}</view>
          <view class="stat-label">待接单</view>
        </view>
        <view class="stat">
          <view class="stat-num">{{ tabCounts.doing }}</view>
          <view class="stat-label">服务中</view>
        </view>
        <view class="stat">
          <view class="stat-num">{{ tabCounts.done }}</view>
          <view class="stat-label">已完成</view>
        </view>
      </view>
    </view>

    <view class="tabs">
      <view
        v-for="tab in tabs"
        :key="tab.key"
        class="tab"
        :class="{ active: activeTab === tab.key }"
        @click="activeTab = tab.key"
      >
        {{ tab.label }}（{{ tabCounts[tab.key] }}）
      </view>
    </view>

    <view v-if="visibleOrders.length === 0" class="card empty-block">
      {{ loading ? '加载中…' : '暂无上门服务任务' }}
    </view>

    <view
      v-for="item in visibleOrders"
      :key="item.id"
      class="card task"
      @click="openDetail(item)"
    >
      <view class="task-head">
        <text class="task-no">{{ item.orderNo }}</text>
        <text class="status" :class="'st-' + item.status">{{ statusText[item.status] || item.status }}</text>
      </view>
      <view class="task-title">{{ item.serviceName || item.serviceCode }}</view>
      <view class="task-body">
        <text>患者：{{ item.customerName || '—' }}</text>
        <text>{{ item.scheduledDate }} {{ hm(item.startTime) }}-{{ hm(item.endTime) }}</text>
      </view>
      <view class="task-foot">
        <text class="muted">派单医生：{{ item.staffName || userName }}</text>
        <text class="link">查看详情 ›</text>
      </view>
    </view>

    <button class="btn-ghost refresh" @click="load">刷新任务列表</button>
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

.hero-title {
  font-size: 36rpx;
  font-weight: 700;
}

.hero-sub {
  font-size: 24rpx;
  opacity: 0.85;
  margin-top: 6rpx;
}

.hero-stats {
  display: flex;
  margin-top: 24rpx;
}

.stat {
  flex: 1;
  text-align: center;
}

.stat-num {
  font-size: 40rpx;
  font-weight: 700;
}

.stat-label {
  font-size: 22rpx;
  opacity: 0.85;
}

.tabs {
  display: flex;
  gap: 12rpx;
  margin-bottom: 20rpx;
}

.tab {
  flex: 1;
  text-align: center;
  font-size: 24rpx;
  padding: 14rpx 0;
  border-radius: 12rpx;
  background: #fff;
  color: #5b6679;
}

.tab.active {
  background: #2d6dad;
  color: #fff;
  font-weight: 600;
}

.task-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.task-no {
  font-size: 24rpx;
  color: #8a94a6;
}

.task-title {
  font-size: 32rpx;
  font-weight: 600;
  margin: 8rpx 0;
}

.task-body {
  display: flex;
  justify-content: space-between;
  color: #4a5568;
  font-size: 26rpx;
}

.task-foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 16rpx;
}

.status {
  font-size: 24rpx;
  padding: 4rpx 14rpx;
  border-radius: 999rpx;
  background: #eef4fc;
  color: #2d6dad;
}

.st-ASSIGNED {
  background: #fff5e6;
  color: #b57b1f;
}

.st-IN_PROGRESS {
  background: #fff0e8;
  color: #c0602e;
}

.st-COMPLETED {
  background: #eef7f1;
  color: #2f8f6b;
}

.st-CANCELLED,
.st-REJECTED {
  background: #fdeeee;
  color: #b5533c;
}

.link {
  color: #2d6dad;
  font-size: 26rpx;
}

.refresh {
  width: 100%;
  margin-top: 10rpx;
}
</style>
