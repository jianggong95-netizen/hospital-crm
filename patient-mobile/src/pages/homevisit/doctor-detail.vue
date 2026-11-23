<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import {
  acceptHomeVisitOrder,
  addHomeVisitExecution,
  fetchHomeVisitOrder,
  finishHomeVisitOrder,
  rejectHomeVisitOrder,
  reportHomeVisitException,
  startHomeVisitOrder,
  type HomeVisitOrderDetail
} from '@/api/homevisit'
import { ensureLogin } from '@/store/session'

const orderId = ref('')
const order = ref<HomeVisitOrderDetail | null>(null)
const loading = ref(false)
const acting = ref(false)

const rejectMode = ref(false)
const rejectReason = ref('')
const executionContent = ref('')
const finishRemark = ref('')
const exceptionDescription = ref('')
const exceptionLevelIndex = ref(0)
const exceptionCategoryIndex = ref(0)

const levelOptions = [
  { value: 'low', label: '低' },
  { value: 'medium', label: '中' },
  { value: 'high', label: '高' },
  { value: 'critical', label: '紧急' }
]

const categoryOptions = [
  { value: 'vital_sign', label: '生命体征异常' },
  { value: 'equipment', label: '设备故障' },
  { value: 'patient_condition', label: '病情变化' },
  { value: 'staff_safety', label: '人员安全' },
  { value: 'other', label: '其他' }
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

const statusDesc: Record<string, string> = {
  PENDING: '等待医院管理端审核',
  APPROVED: '等待医院管理端派单',
  REJECTED: '申请已被驳回',
  ASSIGNED: '请尽快确认接单',
  ACCEPTED: '请按预约时间上门并开始服务',
  IN_PROGRESS: '请登记服务记录，结束时完成服务',
  COMPLETED: '服务已完成',
  CANCELLED: '订单已取消'
}

const unresolvedExceptions = computed(
  () => (order.value?.exceptions || []).filter((item) => ['OPEN', 'ESCALATED'].includes(item.status)).length
)

onLoad((options) => {
  orderId.value = (options?.id as string) || ''
  if (!orderId.value) {
    uni.showToast({ title: '缺少订单编号', icon: 'none' })
    setTimeout(() => uni.navigateBack(), 800)
  }
})

onShow(() => {
  if (!ensureLogin()) {
    return
  }
  if (orderId.value) {
    load()
  }
})

async function load() {
  if (!orderId.value) {
    return
  }
  loading.value = true
  try {
    order.value = await fetchHomeVisitOrder(orderId.value)
  } catch (error) {
    uni.showToast({ title: (error as Error).message, icon: 'none' })
  } finally {
    loading.value = false
  }
}

async function run(action: () => Promise<unknown>, message: string) {
  acting.value = true
  try {
    await action()
    uni.showToast({ title: message, icon: 'success' })
    await load()
  } catch (error) {
    uni.showToast({ title: (error as Error).message, icon: 'none' })
  } finally {
    acting.value = false
  }
}

function accept() {
  uni.showModal({
    title: '确认接单',
    content: '接单后请按预约时间上门服务',
    success: (res) => {
      if (res.confirm) {
        run(() => acceptHomeVisitOrder(orderId.value), '已接单')
      }
    }
  })
}

function reject() {
  if (!rejectReason.value.trim()) {
    uni.showToast({ title: '请填写拒绝原因', icon: 'none' })
    return
  }
  run(() => rejectHomeVisitOrder(orderId.value, rejectReason.value.trim()), '已拒绝派单')
    .then(() => {
      rejectMode.value = false
      rejectReason.value = ''
    })
}

function start() {
  uni.showModal({
    title: '开始服务',
    content: '确认已到达服务地址并开始本次上门服务？',
    success: (res) => {
      if (res.confirm) {
        run(() => startHomeVisitOrder(orderId.value), '服务已开始')
      }
    }
  })
}

function addExecution() {
  if (!executionContent.value.trim()) {
    uni.showToast({ title: '请填写服务记录', icon: 'none' })
    return
  }
  run(() => addHomeVisitExecution(orderId.value, executionContent.value.trim()), '记录已保存')
    .then(() => {
      executionContent.value = ''
    })
}

function reportException() {
  if (!exceptionDescription.value.trim()) {
    uni.showToast({ title: '请填写异常描述', icon: 'none' })
    return
  }
  run(
    () =>
      reportHomeVisitException(orderId.value, {
        level: levelOptions[exceptionLevelIndex.value].value,
        category: categoryOptions[exceptionCategoryIndex.value].value,
        description: exceptionDescription.value.trim()
      }),
    '异常已上报'
  ).then(() => {
    exceptionDescription.value = ''
  })
}

function finish() {
  if (unresolvedExceptions.value > 0) {
    uni.showToast({ title: '存在未处理的异常，请联系管理端处置', icon: 'none' })
    return
  }
  uni.showModal({
    title: '完成服务',
    content: '确认本次上门服务已全部完成？',
    success: (res) => {
      if (res.confirm) {
        run(
          () => finishHomeVisitOrder(orderId.value, finishRemark.value.trim() || undefined),
          '服务已完成'
        )
      }
    }
  })
}

function formatTime(value?: string) {
  return value ? value.replace('T', ' ').slice(0, 16) : ''
}

/** 后端 LocalTime 带秒，展示只保留到分钟 */
function hm(value?: string) {
  return value ? value.slice(0, 5) : ''
}

function levelLabel(value: string) {
  return levelOptions.find((item) => item.value === value)?.label || value
}

function categoryLabel(value: string) {
  return categoryOptions.find((item) => item.value === value)?.label || value
}
</script>

<template>
  <view class="page">
    <view v-if="!order" class="card empty-block">{{ loading ? '加载中…' : '订单不存在' }}</view>

    <template v-else>
      <view class="card head-card">
        <view class="head-top">
          <text class="order-no">{{ order.orderNo }}</text>
          <text class="status" :class="'st-' + order.status">{{ statusText[order.status] || order.status }}</text>
        </view>
        <view class="service-name">{{ order.serviceName || order.serviceCode }}</view>
        <view class="muted">{{ statusDesc[order.status] || '' }}</view>
        <view v-if="order.price" class="muted">服务费用：￥{{ order.price }}</view>
      </view>

      <view class="card">
        <view class="card-title">服务信息</view>
        <view class="info-row">
          <text class="info-label">患者</text>
          <text>{{ order.customerName || '—' }}</text>
        </view>
        <view class="info-row">
          <text class="info-label">服务时间</text>
          <text>{{ order.scheduledDate }} {{ hm(order.startTime) }}-{{ hm(order.endTime) }}</text>
        </view>
        <view class="info-row">
          <text class="info-label">服务地址</text>
          <text class="info-value">{{ order.address?.address || '—' }}</text>
        </view>
        <view class="info-row">
          <text class="info-label">联系人</text>
          <text>{{ order.address?.contactName || '—' }} {{ order.address?.contactPhoneMasked || '' }}</text>
        </view>
        <view v-if="order.remark" class="info-row">
          <text class="info-label">患者备注</text>
          <text class="info-value">{{ order.remark }}</text>
        </view>
        <view v-if="order.actualStartAt" class="info-row">
          <text class="info-label">实际开始</text>
          <text>{{ formatTime(order.actualStartAt) }}{{ order.durationMinutes ? '（历时 ' + order.durationMinutes + ' 分钟）' : '' }}</text>
        </view>
      </view>

      <view v-if="order.status === 'ASSIGNED'" class="card">
        <view class="card-title">接单处理</view>
        <button class="btn-primary" :disabled="acting" @click="accept">确认接单</button>
        <view class="gap"></view>
        <button v-if="!rejectMode" class="btn-ghost full" @click="rejectMode = true">无法接单，拒绝派单</button>
        <view v-else>
          <view class="field">
            <text class="field-label">拒绝原因</text>
            <input v-model="rejectReason" class="input" placeholder="如：时间冲突、超出服务范围" />
          </view>
          <button class="btn-ghost full danger" :disabled="acting" @click="reject">提交拒绝</button>
        </view>
      </view>

      <view v-else-if="order.status === 'ACCEPTED'" class="card">
        <view class="card-title">开始服务</view>
        <view class="muted">到达服务地址后点击开始，系统将记录服务起止时间。</view>
        <view class="gap"></view>
        <button class="btn-primary" :disabled="acting" @click="start">开始服务</button>
      </view>

      <template v-else-if="order.status === 'IN_PROGRESS'">
        <view class="card">
          <view class="card-title">服务记录</view>
          <view class="field">
            <text class="field-label">本次记录</text>
            <input v-model="executionContent" class="input" placeholder="如：已完成静脉采血，患者无不适" />
          </view>
          <button class="btn-ghost full" :disabled="acting" @click="addExecution">保存服务记录</button>
        </view>

        <view class="card">
          <view class="card-title">
            <text>异常上报</text>
            <text v-if="unresolvedExceptions > 0" class="warn">{{ unresolvedExceptions }} 条待处置</text>
          </view>
          <view class="row">
            <view class="field half">
              <text class="field-label">异常级别</text>
              <picker
                :range="levelOptions.map((item) => item.label)"
                :value="exceptionLevelIndex"
                @change="exceptionLevelIndex = Number($event.detail.value)"
              >
                <view class="picker-box">{{ levelOptions[exceptionLevelIndex].label }}</view>
              </picker>
            </view>
            <view class="field half">
              <text class="field-label">异常类别</text>
              <picker
                :range="categoryOptions.map((item) => item.label)"
                :value="exceptionCategoryIndex"
                @change="exceptionCategoryIndex = Number($event.detail.value)"
              >
                <view class="picker-box">{{ categoryOptions[exceptionCategoryIndex].label }}</view>
              </picker>
            </view>
          </view>
          <view class="field">
            <text class="field-label">异常描述</text>
            <input v-model="exceptionDescription" class="input" placeholder="如：患者血压明显升高，需协助转诊" />
          </view>
          <button class="btn-ghost full danger" :disabled="acting" @click="reportException">上报异常</button>
        </view>

        <view class="card">
          <view class="card-title">完成服务</view>
          <view class="field">
            <text class="field-label">完成备注（选填）</text>
            <input v-model="finishRemark" class="input" placeholder="如：患者生命体征平稳" />
          </view>
          <button class="btn-primary" :disabled="acting" @click="finish">完成服务</button>
        </view>
      </template>

      <view class="card">
        <view class="card-title">
          <text>服务记录</text>
          <text class="muted">{{ order.executions.length }} 条</text>
        </view>
        <view v-if="order.executions.length === 0" class="empty-block small">暂无服务记录</view>
        <view v-for="item in order.executions" :key="item.id" class="timeline-item">
          <view class="timeline-time">{{ formatTime(item.visitAt) }}</view>
          <view class="timeline-content">{{ item.content }}</view>
          <view class="muted">记录人：{{ item.recordedBy || '—' }}</view>
        </view>
      </view>

      <view class="card">
        <view class="card-title">
          <text>异常记录</text>
          <text class="muted">{{ order.exceptions.length }} 条</text>
        </view>
        <view v-if="order.exceptions.length === 0" class="empty-block small">暂无异常上报</view>
        <view v-for="item in order.exceptions" :key="item.id" class="timeline-item">
          <view class="exception-head">
            <text class="level" :class="'lv-' + item.level">{{ levelLabel(item.level) }}</text>
            <text class="muted">{{ categoryLabel(item.category) }}</text>
            <text class="status mini" :class="'st-' + item.status">{{ item.status === 'RESOLVED' ? '已处置' : item.status === 'ESCALATED' ? '已升级' : '待处置' }}</text>
          </view>
          <view class="timeline-content">{{ item.description }}</view>
        </view>
      </view>

      <button class="btn-ghost full" @click="load">刷新订单状态</button>
    </template>
  </view>
</template>

<style lang="scss" scoped>
.head-card {
  border-left: 8rpx solid #2d6dad;
}

.head-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.order-no {
  font-size: 24rpx;
  color: #8a94a6;
}

.service-name {
  font-size: 34rpx;
  font-weight: 700;
  margin: 8rpx 0;
}

.info-row {
  display: flex;
  padding: 10rpx 0;
  font-size: 27rpx;
  border-bottom: 1rpx solid #f2f5fa;
}

.info-row:last-child {
  border-bottom: none;
}

.info-label {
  width: 150rpx;
  color: #8a94a6;
  flex-shrink: 0;
}

.info-value {
  flex: 1;
  word-break: break-all;
}

.gap {
  height: 20rpx;
}

.row {
  display: flex;
  gap: 20rpx;
}

.half {
  flex: 1;
}

.btn-ghost.full {
  width: 100%;
}

.btn-ghost.full.danger {
  color: #b5533c;
  border-color: #e7c6c0;
}

.warn {
  color: #c0602e;
  font-size: 24rpx;
}

.timeline-item {
  padding: 16rpx 0;
  border-bottom: 1rpx solid #f2f5fa;
}

.timeline-item:last-child {
  border-bottom: none;
}

.timeline-time {
  font-size: 24rpx;
  color: #8a94a6;
}

.timeline-content {
  font-size: 27rpx;
  margin: 4rpx 0;
}

.exception-head {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.level {
  font-size: 22rpx;
  padding: 2rpx 12rpx;
  border-radius: 8rpx;
  background: #eef4fc;
  color: #2d6dad;
}

.lv-high,
.lv-critical {
  background: #fdeeee;
  color: #b5533c;
}

.lv-medium {
  background: #fff5e6;
  color: #b57b1f;
}

.status {
  font-size: 24rpx;
  padding: 4rpx 14rpx;
  border-radius: 999rpx;
  background: #eef4fc;
  color: #2d6dad;
}

.status.mini {
  font-size: 22rpx;
  margin-left: auto;
}

.st-ASSIGNED {
  background: #fff5e6;
  color: #b57b1f;
}

.st-IN_PROGRESS {
  background: #fff0e8;
  color: #c0602e;
}

.st-COMPLETED,
.st-RESOLVED {
  background: #eef7f1;
  color: #2f8f6b;
}

.st-ESCALATED,
.st-OPEN {
  background: #fdeeee;
  color: #b5533c;
}

.empty-block.small {
  padding: 24rpx 0;
}
</style>
