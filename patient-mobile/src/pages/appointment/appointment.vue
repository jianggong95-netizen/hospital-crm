<script setup lang="ts">
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import {
  cancelAppointment,
  createAppointment,
  fetchAppointmentTypes,
  fetchMyAppointments,
  fetchSlots,
  type AppointmentSummary,
  type AppointmentType,
  type ServiceSlot
} from '@/api/appointment'
import { ensureLogin, getPatient, type PatientBrief } from '@/store/session'

const patient = ref<PatientBrief | null>(null)
const types = ref<AppointmentType[]>([])
const slots = ref<ServiceSlot[]>([])
const appointments = ref<AppointmentSummary[]>([])
const loading = ref(false)
const submitting = ref(false)

const typeIndex = ref(-1)
const slotIndex = ref(-1)
const contactPhone = ref('')

const typeLabels = computed(() => types.value.map((t) => t.typeName))
const slotLabels = computed(() =>
  slots.value.map((s) => `${s.slotDate} ${s.startTime}-${s.endTime}（剩余 ${s.remaining}）`)
)
const selectedType = computed(() => (typeIndex.value >= 0 ? types.value[typeIndex.value] : null))
const selectedSlot = computed(() => (slotIndex.value >= 0 ? slots.value[slotIndex.value] : null))

const statusText: Record<string, string> = {
  BOOKED: '已预约',
  CONFIRMED: '已确认',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
  NO_SHOW: '爽约'
}

function today(offset = 0) {
  const d = new Date()
  d.setDate(d.getDate() + offset)
  return d.toISOString().slice(0, 10)
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
  await loadAll()
})

async function loadAll() {
  loading.value = true
  try {
    const [typeList, mine] = await Promise.all([
      fetchAppointmentTypes(),
      fetchMyAppointments(patient.value!.id)
    ])
    types.value = typeList || []
    appointments.value = mine.items || []
    if (types.value.length && typeIndex.value < 0) {
      typeIndex.value = 0
      await loadSlots()
    }
  } catch (error) {
    uni.showToast({ title: (error as Error).message, icon: 'none' })
  } finally {
    loading.value = false
  }
}

async function loadSlots() {
  const type = selectedType.value
  if (!type) {
    return
  }
  try {
    const result = await fetchSlots({
      typeCode: type.typeCode,
      dateFrom: today(),
      dateTo: today(14),
      pageSize: 50
    })
    slots.value = (result.items || []).filter((s) => s.remaining > 0 && s.status === 'OPEN')
    slotIndex.value = slots.value.length ? 0 : -1
  } catch (error) {
    uni.showToast({ title: (error as Error).message, icon: 'none' })
  }
}

async function changeType(e: { detail: { value: number } }) {
  typeIndex.value = Number(e.detail.value)
  await loadSlots()
}

async function submit() {
  if (!selectedType.value) {
    uni.showToast({ title: '请选择预约类型', icon: 'none' })
    return
  }
  if (!selectedSlot.value) {
    uni.showToast({ title: '请选择服务时段', icon: 'none' })
    return
  }
  submitting.value = true
  try {
    await createAppointment({
      customerId: patient.value!.id,
      typeCode: selectedType.value.typeCode,
      slotId: selectedSlot.value.id,
      contactPhone: contactPhone.value || undefined
    })
    uni.showToast({ title: '预约成功', icon: 'success' })
    slotIndex.value = -1
    contactPhone.value = ''
    await loadAll()
    await loadSlots()
  } catch (error) {
    uni.showToast({ title: (error as Error).message, icon: 'none' })
  } finally {
    submitting.value = false
  }
}

function cancel(item: AppointmentSummary) {
  uni.showModal({
    title: '取消预约',
    content: `确定取消预约 ${item.appointmentNo} 吗？`,
    success: async (res) => {
      if (!res.confirm) {
        return
      }
      try {
        await cancelAppointment(item.id, '患者端取消')
        uni.showToast({ title: '已取消', icon: 'success' })
        await loadAll()
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
      <view class="card-title">新建预约</view>
      <view class="field">
        <text class="field-label">预约类型</text>
        <picker :range="typeLabels" :value="typeIndex" @change="changeType">
          <view class="picker-box" :class="{ empty: !selectedType }">
            {{ selectedType ? selectedType.typeName : '请选择预约类型' }}
          </view>
        </picker>
      </view>
      <view class="field">
        <text class="field-label">服务时段</text>
        <picker
          :range="slotLabels"
          :value="slotIndex"
          @change="slotIndex = Number($event.detail.value)"
        >
          <view class="picker-box" :class="{ empty: !selectedSlot }">
            {{ selectedSlot ? slotLabels[slotIndex] : slots.length ? '请选择服务时段' : '该类型暂无可约时段' }}
          </view>
        </picker>
      </view>
      <view class="field">
        <text class="field-label">联系电话（选填）</text>
        <input v-model="contactPhone" class="input" type="number" placeholder="用于接收预约提醒" />
      </view>
      <button class="btn-primary" :disabled="submitting || loading" @click="submit">
        {{ submitting ? '提交中…' : '提交预约' }}
      </button>
    </view>

    <view class="card">
      <view class="card-title">
        <text>我的预约</text>
        <text class="muted">{{ appointments.length }} 条</text>
      </view>
      <view v-if="appointments.length === 0" class="empty-block">暂无预约记录</view>
      <view v-for="item in appointments" :key="item.id" class="record">
        <view class="record-head">
          <text class="record-no">{{ item.appointmentNo }}</text>
          <text class="status" :class="'st-' + item.status">{{ statusText[item.status] || item.status }}</text>
        </view>
        <view class="record-body">
          {{ item.typeName }} · {{ item.slotDate }} {{ item.startTime }}-{{ item.endTime }}
        </view>
        <view class="record-foot">
          <text class="muted">联系电话 {{ item.contactPhoneMasked || '—' }}</text>
          <text
            v-if="item.status === 'BOOKED' || item.status === 'CONFIRMED'"
            class="link danger"
            @click="cancel(item)"
          >
            取消预约
          </text>
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

.st-CANCELLED,
.st-NO_SHOW {
  background: #fdeeee;
  color: #b5533c;
}

.st-COMPLETED {
  background: #eef7f1;
  color: #2f8f6b;
}

.link {
  font-size: 26rpx;
  color: #2d6dad;
  padding: 6rpx 0 6rpx 20rpx;
}

.link.danger {
  color: #b5533c;
}
</style>
