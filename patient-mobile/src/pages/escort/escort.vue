<script setup lang="ts">
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import {
  cancelEscortOrder,
  createEscortOrder,
  fetchMyEscortOrders,
  type EscortOrderSummary
} from '@/api/escort'
import { ensureLogin, getPatient, type PatientBrief } from '@/store/session'

const patient = ref<PatientBrief | null>(null)
const orders = ref<EscortOrderSummary[]>([])
const loading = ref(false)
const submitting = ref(false)

const serviceDate = ref('')
const startTime = ref('')
const endTime = ref('')
const area = ref('')
const contactPhone = ref('')
const remark = ref('')

const statusText: Record<string, string> = {
  PENDING: '待派单',
  ASSIGNED: '已派单',
  ACCEPTED: '陪诊师已接单',
  IN_PROGRESS: '服务中',
  COMPLETED: '已完成',
  CANCELLED: '已取消'
}

function today() {
  return new Date().toISOString().slice(0, 10)
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
  if (!serviceDate.value) {
    serviceDate.value = today()
  }
  if (!startTime.value) {
    startTime.value = '09:00'
  }
  if (!endTime.value) {
    endTime.value = '11:00'
  }
  await load()
})

async function load() {
  loading.value = true
  try {
    const result = await fetchMyEscortOrders(patient.value!.id)
    orders.value = result.items || []
  } catch (error) {
    uni.showToast({ title: (error as Error).message, icon: 'none' })
  } finally {
    loading.value = false
  }
}

async function submit() {
  if (!area.value.trim()) {
    uni.showToast({ title: '请填写服务区域', icon: 'none' })
    return
  }
  if (!startTime.value || !endTime.value) {
    uni.showToast({ title: '请选择服务时段', icon: 'none' })
    return
  }
  if (startTime.value >= endTime.value) {
    uni.showToast({ title: '结束时间需晚于开始时间', icon: 'none' })
    return
  }
  submitting.value = true
  try {
    await createEscortOrder({
      customerId: patient.value!.id,
      serviceDate: serviceDate.value,
      startTime: startTime.value,
      endTime: endTime.value,
      area: area.value.trim(),
      contactPhone: contactPhone.value || undefined,
      remark: remark.value || undefined
    })
    uni.showToast({ title: '申请已提交', icon: 'success' })
    area.value = ''
    startTime.value = ''
    endTime.value = ''
    remark.value = ''
    await load()
  } catch (error) {
    uni.showToast({ title: (error as Error).message, icon: 'none' })
  } finally {
    submitting.value = false
  }
}

function cancel(item: EscortOrderSummary) {
  uni.showModal({
    title: '取消陪诊',
    content: `确定取消订单 ${item.orderNo} 吗？`,
    success: async (res) => {
      if (!res.confirm) {
        return
      }
      try {
        await cancelEscortOrder(item.id, '患者端取消')
        uni.showToast({ title: '已取消', icon: 'success' })
        await load()
      } catch (error) {
        uni.showToast({ title: (error as Error).message, icon: 'none' })
      }
    }
  })
}

function switchPatient() {
  uni.navigateTo({ url: '/pages/patient/select' })
}
</script>

<template>
  <view class="page">
    <view class="card patient-bar" @click="switchPatient">
      <view>
        <view class="muted">当前就诊人</view>
        <view class="patient-name">{{ patient?.name || '未选择' }}</view>
      </view>
      <view class="avatar-chip">切换</view>
    </view>

    <view class="card">
      <view class="card-title">申请陪诊</view>
      <view class="field">
        <text class="field-label">服务日期</text>
        <picker mode="date" :value="serviceDate" :start="today()" @change="serviceDate = $event.detail.value">
          <view class="picker-box">{{ serviceDate }}</view>
        </picker>
      </view>
      <view class="row">
        <view class="field half">
          <text class="field-label">开始时间</text>
          <picker mode="time" :value="startTime" @change="startTime = $event.detail.value">
            <view class="picker-box" :class="{ empty: !startTime }">{{ startTime || '选择时间' }}</view>
          </picker>
        </view>
        <view class="field half">
          <text class="field-label">结束时间</text>
          <picker mode="time" :value="endTime" @change="endTime = $event.detail.value">
            <view class="picker-box" :class="{ empty: !endTime }">{{ endTime || '选择时间' }}</view>
          </picker>
        </view>
      </view>
      <view class="field">
        <text class="field-label">服务区域</text>
        <input v-model="area" class="input" placeholder="如：门诊楼 3 层、住院部 12 楼" />
      </view>
      <view class="field">
        <text class="field-label">联系电话（选填）</text>
        <input v-model="contactPhone" class="input" type="number" placeholder="陪诊师联系用" />
      </view>
      <view class="field">
        <text class="field-label">备注（选填）</text>
        <input v-model="remark" class="input" placeholder="如行动不便、需轮椅等" />
      </view>
      <button class="btn-primary" :disabled="submitting" @click="submit">
        {{ submitting ? '提交中…' : '提交陪诊申请' }}
      </button>
    </view>

    <view class="card">
      <view class="card-title">
        <text>我的陪诊</text>
        <text class="muted">{{ orders.length }} 条</text>
      </view>
      <view v-if="orders.length === 0" class="empty-block">暂无陪诊订单</view>
      <view v-for="item in orders" :key="item.id" class="record">
        <view class="record-head">
          <text class="record-no">{{ item.orderNo }}</text>
          <text class="status" :class="'st-' + item.status">{{ statusText[item.status] || item.status }}</text>
        </view>
        <view class="record-body">
          {{ item.serviceDate }} {{ item.startTime }}-{{ item.endTime }} · {{ item.area }}
        </view>
        <view class="record-foot">
          <text class="muted">陪诊师：{{ item.escortName || '待派单' }}</text>
          <text v-if="item.status === 'PENDING'" class="link danger" @click="cancel(item)">取消</text>
        </view>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.patient-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.patient-name {
  font-size: 32rpx;
  font-weight: 600;
}

.row {
  display: flex;
  gap: 20rpx;
}

.half {
  flex: 1;
}

.record {
  padding: 20rpx 0;
  border-bottom: 1rpx solid #eef1f6;
}

.record:last-child {
  border-bottom: none;
}

.record-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.record-no {
  font-weight: 600;
  font-size: 28rpx;
}

.record-body {
  color: #4a5568;
  font-size: 26rpx;
  margin: 6rpx 0;
}

.record-foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.status {
  font-size: 24rpx;
  padding: 4rpx 14rpx;
  border-radius: 999rpx;
  background: #eef4fc;
  color: #2d6dad;
}

.st-COMPLETED {
  background: #eef7f1;
  color: #2f8f6b;
}

.st-CANCELLED {
  background: #fdeeee;
  color: #b5533c;
}

.link {
  font-size: 26rpx;
  color: #2d6dad;
}

.link.danger {
  color: #b5533c;
}
</style>
