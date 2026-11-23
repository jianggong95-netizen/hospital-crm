<script setup lang="ts">
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import {
  addFamilyMember,
  createFamily,
  fetchFamilies,
  fetchFamilyMembers,
  type FamilyMember,
  type FamilySummary
} from '@/api/family'
import { searchCustomers, type CustomerBrief } from '@/api/customer'
import { ensureLogin, getPatient, type PatientBrief } from '@/store/session'

const patient = ref<PatientBrief | null>(null)
const families = ref<FamilySummary[]>([])
const loading = ref(false)
const creating = ref(false)
const familyName = ref('')

const expandedId = ref('')
const members = ref<FamilyMember[]>([])
const memberLoading = ref(false)

const memberKeyword = ref('')
const candidates = ref<CustomerBrief[]>([])
const relationshipIndex = ref(-1)
const addingMember = ref(false)
const relationships = [
  { code: 'spouse', label: '配偶' },
  { code: 'parent', label: '父母' },
  { code: 'child', label: '子女' },
  { code: 'sibling', label: '兄弟姐妹' }
]
const relationshipLabels = relationships.map((r) => r.label)

onShow(async () => {
  if (!ensureLogin()) {
    return
  }
  patient.value = getPatient()
  if (!patient.value) {
    uni.navigateTo({ url: '/pages/patient/select' })
    return
  }
  await load()
})

async function load() {
  loading.value = true
  try {
    const result = await fetchFamilies()
    families.value = result.items || []
  } catch (error) {
    uni.showToast({ title: (error as Error).message, icon: 'none' })
  } finally {
    loading.value = false
  }
}

async function submitFamily() {
  if (!familyName.value.trim()) {
    uni.showToast({ title: '请填写家庭名称', icon: 'none' })
    return
  }
  creating.value = true
  try {
    await createFamily(familyName.value.trim(), patient.value!.id)
    uni.showToast({ title: '家庭已创建', icon: 'success' })
    familyName.value = ''
    await load()
  } catch (error) {
    uni.showToast({ title: (error as Error).message, icon: 'none' })
  } finally {
    creating.value = false
  }
}

async function toggle(item: FamilySummary) {
  if (expandedId.value === item.id) {
    expandedId.value = ''
    return
  }
  expandedId.value = item.id
  memberLoading.value = true
  try {
    members.value = await fetchFamilyMembers(item.id)
  } catch (error) {
    uni.showToast({ title: (error as Error).message, icon: 'none' })
  } finally {
    memberLoading.value = false
  }
}

async function searchMember() {
  if (!memberKeyword.value.trim()) {
    uni.showToast({ title: '请输入手机号', icon: 'none' })
    return
  }
  try {
    const result = await searchCustomers(memberKeyword.value.trim())
    candidates.value = result.items || []
    if (!candidates.value.length) {
      uni.showToast({ title: '未找到该手机号档案', icon: 'none' })
    }
  } catch (error) {
    uni.showToast({ title: (error as Error).message, icon: 'none' })
  }
}

async function addMember(customer: CustomerBrief) {
  if (relationshipIndex.value < 0) {
    uni.showToast({ title: '请先选择与本人关系', icon: 'none' })
    return
  }
  addingMember.value = true
  try {
    await addFamilyMember(
      expandedId.value,
      customer.id,
      relationships[relationshipIndex.value].code
    )
    uni.showToast({ title: '已加入家庭', icon: 'success' })
    memberKeyword.value = ''
    candidates.value = []
    relationshipIndex.value = -1
    members.value = await fetchFamilyMembers(expandedId.value)
    await load()
  } catch (error) {
    uni.showToast({ title: (error as Error).message, icon: 'none' })
  } finally {
    addingMember.value = false
  }
}
</script>

<template>
  <view class="page">
    <view class="card">
      <view class="muted">当前就诊人</view>
      <view class="patient-name">{{ patient?.name || '未选择' }}</view>
    </view>

    <view class="card">
      <view class="card-title">新建家庭</view>
      <view class="field">
        <input v-model="familyName" class="input" placeholder="如：王建国家庭" />
      </view>
      <button class="btn-primary" :disabled="creating" @click="submitFamily">
        {{ creating ? '创建中…' : '创建家庭' }}
      </button>
    </view>

    <view class="card">
      <view class="card-title">
        <text>家庭列表</text>
        <text class="muted">{{ families.length }} 个</text>
      </view>
      <view v-if="families.length === 0 && !loading" class="empty-block">暂无家庭</view>

      <view v-for="item in families" :key="item.id" class="family">
        <view class="family-head" @click="toggle(item)">
          <view>
            <view class="family-name">{{ item.familyName }}</view>
            <view class="muted">{{ item.familyNo }} · {{ item.memberCount }} 位成员</view>
          </view>
          <text class="link">{{ expandedId === item.id ? '收起' : '成员' }}</text>
        </view>

        <view v-if="expandedId === item.id" class="members">
          <view v-if="memberLoading" class="muted">加载成员…</view>
          <view v-for="m in members" :key="m.id" class="member">
            <text>{{ m.customer?.name || '—' }}</text>
            <text class="muted">
              {{ m.relationshipLabel }}<text v-if="m.admin"> · 管理员</text>
            </text>
          </view>

          <view class="add-box">
            <view class="muted" style="margin-bottom: 12rpx">添加成员</view>
            <view class="inline">
              <input v-model="memberKeyword" class="input flex" type="number" placeholder="成员手机号" />
              <button class="btn-ghost" @click="searchMember">查询</button>
            </view>
            <picker
              :range="relationshipLabels"
              :value="relationshipIndex"
              @change="relationshipIndex = Number($event.detail.value)"
            >
              <view class="picker-box" :class="{ empty: relationshipIndex < 0 }" style="margin-top: 16rpx">
                {{ relationshipIndex >= 0 ? relationshipLabels[relationshipIndex] : '选择与本人关系' }}
              </view>
            </picker>
            <view
              v-for="c in candidates"
              :key="c.id"
              class="candidate"
              @click="addMember(c)"
            >
              <view>
                <view class="family-name">{{ c.name }}</view>
                <view class="muted">{{ c.customerNo }} · {{ c.phoneMasked }}</view>
              </view>
              <text class="link">{{ addingMember ? '添加中…' : '加入' }}</text>
            </view>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.patient-name {
  font-size: 32rpx;
  font-weight: 600;
}

.family {
  border-bottom: 1rpx solid #eef1f6;
  padding: 18rpx 0;
}

.family:last-child {
  border-bottom: none;
}

.family-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.family-name {
  font-size: 30rpx;
  font-weight: 600;
}

.members {
  margin-top: 12rpx;
  padding: 16rpx;
  background: #f7f9fd;
  border-radius: 14rpx;
}

.member {
  display: flex;
  justify-content: space-between;
  padding: 8rpx 0;
  font-size: 26rpx;
}

.add-box {
  margin-top: 18rpx;
  padding-top: 18rpx;
  border-top: 1rpx dashed #dbe3ef;
}

.inline {
  display: flex;
  align-items: center;
  gap: 14rpx;
}

.flex {
  flex: 1;
  height: 72rpx;
  line-height: 72rpx;
}

.candidate {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16rpx 0;
  border-bottom: 1rpx solid #eef1f6;
}

.link {
  color: #2d6dad;
  font-size: 26rpx;
}
</style>
