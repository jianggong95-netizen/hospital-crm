<script setup lang="ts">
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import {
  cancelHomeVisitOrder,
  createHomeAddress,
  createHomeVisitOrder,
  fetchHomeAddresses,
  fetchHomeVisitOrders,
  fetchPublishedHomeServices,
  type HomeAddress,
  type HomeServiceItem,
  type HomeVisitOrderSummary
} from '@/api/homevisit'
import { ensureLogin, getPatient, type PatientBrief } from '@/store/session'

const patient = ref<PatientBrief | null>(null)
const services = ref<HomeServiceItem[]>([])
const addresses = ref<HomeAddress[]>([])
const orders = ref<HomeVisitOrderSummary[]>([])
const loading = ref(false)
const submitting = ref(false)

const serviceIndex = ref(0)
const addressIndex = ref(0)
const serviceDate = ref(today())
const startTime = ref('09:00')
const remark = ref('')

const showAddressForm = ref(false)
const newAddress = ref('')
const newContact = ref('')
const newPhone = ref('')
const savingAddress = ref(false)

const statusText: Record<string, string> = {
  PENDING: '待审核',
  APPROVED: '已审核待派单',
  REJECTED: '已驳回',
  ASSIGNED: '已派单',
  ACCEPTED: '医生已接单',
  IN_PROGRESS: '服务中',
  COMPLETED: '已完成',
  CANCELLED: '已取消'
}

const serviceNames = computed(() => services.value.map((s) => s.serviceName))
const addressNames = computed(() =>
  addresses.value.map((a) => `${a.address}（${a.contactName}）`)
)
const currentService = computed(() => services.value[serviceIndex.value])
const currentAddress = computed(() => addresses.value[addressIndex.value])

/** 结束时间按服务时长自动推算，避免患者填错时段 */
const endTime = computed(() => {
  const service = currentService.value
  if (!service || !startTime.value) {
    return ''
  }
  const [hour, minute] = startTime.value.split(':').map(Number)
  const total = hour * 60 + minute + (service.durationMinutes || 30)
  const endHour = Math.min(Math.floor(total / 60), 23)
  const endMinute = total >= 24 * 60 ? 59 : total % 60
  return `${String(endHour).padStart(2, '0')}:${String(endMinute).padStart(2, '0')}`
})

function today() {
  return new Date().toISOString().slice(0, 10)
}

/** 后端 LocalTime 带秒，列表只展示到分钟 */
function hm(value?: string) {
  return value ? value.slice(0, 5) : ''
}

function maxDate() {
  const date = new Date()
  date.setDate(date.getDate() + 14)
  return date.toISOString().slice(0, 10)
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
  await Promise.all([loadServices(), loadAddresses(), loadOrders()])
})

async function loadServices() {
  try {
    const result = await fetchPublishedHomeServices()
    services.value = result.items || []
  } catch (error) {
    uni.showToast({ title: (error as Error).message, icon: 'none' })
  }
}

async function loadAddresses() {
  const customerId = patient.value?.id
  if (!customerId) {
    return
  }
  try {
    const list = await fetchHomeAddresses(customerId)
    addresses.value = list.filter((item) => item.status === 'active')
    const defaultIndex = addresses.value.findIndex((item) => item.isDefault)
    addressIndex.value = defaultIndex >= 0 ? defaultIndex : 0
    showAddressForm.value = addresses.value.length === 0
  } catch (error) {
    uni.showToast({ title: (error as Error).message, icon: 'none' })
  }
}

async function loadOrders() {
  const customerId = patient.value?.id
  if (!customerId) {
    return
  }
  loading.value = true
  try {
    const result = await fetchHomeVisitOrders({ customerId })
    orders.value = result.items || []
  } catch (error) {
    uni.showToast({ title: (error as Error).message, icon: 'none' })
  } finally {
    loading.value = false
  }
}

function onServiceChange(event: { detail: { value: number | string } }) {
  serviceIndex.value = Number(event.detail.value)
}

function onAddressChange(event: { detail: { value: number | string } }) {
  addressIndex.value = Number(event.detail.value)
}

async function saveAddress() {
  if (!newAddress.value.trim() || !newContact.value.trim()) {
    uni.showToast({ title: '请填写地址与联系人', icon: 'none' })
    return
  }
  savingAddress.value = true
  try {
    await createHomeAddress({
      customerId: patient.value!.id,
      address: newAddress.value.trim(),
      contactName: newContact.value.trim(),
      contactPhone: newPhone.value.trim() || undefined,
      isDefault: addresses.value.length === 0
    })
    uni.showToast({ title: '地址已保存', icon: 'success' })
    newAddress.value = ''
    newContact.value = ''
    newPhone.value = ''
    showAddressForm.value = false
    await loadAddresses()
  } catch (error) {
    uni.showToast({ title: (error as Error).message, icon: 'none' })
  } finally {
    savingAddress.value = false
  }
}

async function submit() {
  if (!patient.value) {
    return
  }
  if (!currentService.value) {
    uni.showToast({ title: '暂无可申请的上门服务', icon: 'none' })
    return
  }
  if (!currentAddress.value) {
    uni.showToast({ title: '请先添加服务地址', icon: 'none' })
    return
  }
  if (!endTime.value || endTime.value <= startTime.value) {
    uni.showToast({ title: '服务时段不合法，请调整开始时间', icon: 'none' })
    return
  }
  submitting.value = true
  try {
    await createHomeVisitOrder({
      serviceCode: currentService.value.serviceCode,
      customerId: patient.value.id,
      addressId: currentAddress.value.id,
      scheduledDate: serviceDate.value,
      startTime: startTime.value,
      endTime: endTime.value,
      remark: remark.value.trim() || undefined
    })
    uni.showToast({ title: '申请已提交', icon: 'success' })
    remark.value = ''
    await loadOrders()
  } catch (error) {
    uni.showToast({ title: (error as Error).message, icon: 'none' })
  } finally {
    submitting.value = false
  }
}

function cancel(item: HomeVisitOrderSummary) {
  uni.showModal({
    title: '取消申请',
    content: `确定取消订单 ${item.orderNo} 吗？`,
    success: async (res) => {
      if (!res.confirm) {
        return
      }
      try {
        await cancelHomeVisitOrder(item.id, '患者端取消')
        uni.showToast({ title: '已取消', icon: 'success' })
        await loadOrders()
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
      <view class="card-title">申请上门服务</view>

      <view class="field">
        <text class="field-label">服务项目</text>
        <picker
          v-if="services.length > 0"
          :range="serviceNames"
          :value="serviceIndex"
          @change="onServiceChange"
        >
          <view class="picker-box">{{ serviceNames[serviceIndex] }}</view>
        </picker>
        <view v-else class="picker-box empty">暂无可申请的上门服务</view>
        <view v-if="currentService" class="muted service-hint">
          {{ currentService.category === 'medical' ? '医疗类' : '非医疗类' }} ·
          约 {{ currentService.durationMinutes }} 分钟 · ￥{{ currentService.price }}
        </view>
      </view>

      <view class="field">
        <text class="field-label">服务地址</text>
        <picker
          v-if="addresses.length > 0"
          :range="addressNames"
          :value="addressIndex"
          @change="onAddressChange"
        >
          <view class="picker-box">{{ addressNames[addressIndex] }}</view>
        </picker>
        <view v-else class="picker-box empty">暂无地址，请新增</view>
        <view class="address-actions">
          <text class="link" @click="showAddressForm = !showAddressForm">
            {{ showAddressForm ? '收起新增地址' : '新增服务地址' }}
          </text>
        </view>
      </view>

      <view v-if="showAddressForm" class="sub-card">
        <view class="field">
          <text class="field-label">详细地址</text>
          <input v-model="newAddress" class="input" placeholder="如：和平路 88 号 3 栋 502" />
        </view>
        <view class="field">
          <text class="field-label">联系人</text>
          <input v-model="newContact" class="input" placeholder="上门服务联系人" />
        </view>
        <view class="field">
          <text class="field-label">联系电话</text>
          <input v-model="newPhone" class="input" type="number" placeholder="医生上门前联系用" />
        </view>
        <button class="btn-ghost full" :disabled="savingAddress" @click="saveAddress">
          {{ savingAddress ? '保存中…' : '保存地址' }}
        </button>
      </view>

      <view class="field">
        <text class="field-label">服务日期</text>
        <picker
          mode="date"
          :value="serviceDate"
          :start="today()"
          :end="maxDate()"
          @change="serviceDate = $event.detail.value"
        >
          <view class="picker-box">{{ serviceDate }}</view>
        </picker>
      </view>

      <view class="row">
        <view class="field half">
          <text class="field-label">开始时间</text>
          <picker mode="time" :value="startTime" @change="startTime = $event.detail.value">
            <view class="picker-box">{{ startTime }}</view>
          </picker>
        </view>
        <view class="field half">
          <text class="field-label">预计结束</text>
          <view class="picker-box readonly">{{ endTime || '—' }}</view>
        </view>
      </view>

      <view class="field">
        <text class="field-label">备注（选填）</text>
        <input v-model="remark" class="input" placeholder="如病情说明、上门注意事项" />
      </view>

      <button class="btn-primary" :disabled="submitting" @click="submit">
        {{ submitting ? '提交中…' : '提交上门服务申请' }}
      </button>
    </view>

    <view class="card">
      <view class="card-title">
        <text>我的上门服务</text>
        <text class="muted">{{ orders.length }} 条</text>
      </view>
      <view v-if="orders.length === 0" class="empty-block">
        {{ loading ? '加载中…' : '暂无上门服务申请' }}
      </view>
      <view v-for="item in orders" :key="item.id" class="record">
        <view class="record-head">
          <text class="record-no">{{ item.orderNo }}</text>
          <text class="status" :class="'st-' + item.status">{{ statusText[item.status] || item.status }}</text>
        </view>
        <view class="record-body">
          {{ item.serviceName || item.serviceCode }} · {{ item.scheduledDate }}
          {{ hm(item.startTime) }}-{{ hm(item.endTime) }}
        </view>
        <view class="record-foot">
          <text class="muted">上门人员：{{ item.staffName || '待安排' }}</text>
          <text
            v-if="['PENDING', 'APPROVED', 'ASSIGNED', 'ACCEPTED'].includes(item.status)"
            class="link danger"
            @click="cancel(item)"
          >
            取消
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

.row {
  display: flex;
  gap: 20rpx;
}

.half {
  flex: 1;
}

.service-hint {
  margin-top: 10rpx;
}

.address-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 10rpx;
}

.sub-card {
  background: #f8fafd;
  border: 1rpx solid #e6ecf5;
  border-radius: 14rpx;
  padding: 20rpx;
  margin-bottom: 22rpx;
}

.btn-ghost.full {
  width: 100%;
}

.picker-box.readonly {
  color: #5b6679;
  background: #eef2f8;
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

.st-CANCELLED,
.st-REJECTED {
  background: #fdeeee;
  color: #b5533c;
}

.st-IN_PROGRESS,
.st-ASSIGNED {
  background: #fff5e6;
  color: #b57b1f;
}

.link {
  font-size: 26rpx;
  color: #2d6dad;
}

.link.danger {
  color: #b5533c;
}
</style>
