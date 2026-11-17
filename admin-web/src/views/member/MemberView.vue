<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { fetchCustomers } from '@/api/customers'
import {
  adjustMemberPoints,
  changeMemberPackageStatus,
  consumeMemberBalance,
  consumeMemberCard,
  createMemberPackage,
  createMemberShare,
  fetchMemberAccountDetail,
  fetchMemberAccounts,
  fetchMemberCardDetail,
  fetchMemberCards,
  fetchMemberLedger,
  fetchMemberLevels,
  fetchMemberPackages,
  fetchMemberShares,
  openMemberAccount,
  purchaseMemberPackage,
  rechargeMemberAccount,
  refundMemberCard,
  reverseMemberBenefits,
  revokeMemberShare,
  updateMemberPackage
} from '@/api/member'
import type { CustomerSummary } from '@/types/customer'
import type {
  MemberAccount,
  MemberCard,
  MemberCardDetail,
  MemberLedgerEntry,
  MemberLevel,
  MemberPackage,
  MemberShare,
  PackageType,
  ShareResourceType
} from '@/types/member'

const auth = useAuthStore()
const canWrite = computed(() => auth.has('crm:member:write'))
const canManage = computed(() => auth.has('crm:member:manage'))

const activeTab = ref('accounts')
const loading = ref(false)

const cardStatusLabels: Record<string, string> = {
  ACTIVE: '生效中',
  EXHAUSTED: '已用尽',
  EXPIRED: '已过期',
  VOID: '已作废'
}

const shareStatusLabels: Record<string, string> = {
  ACTIVE: '生效中',
  REVOKED: '已撤销',
  EXPIRED: '已失效'
}

const packageTypeLabels: Record<string, string> = {
  TIMES: '次卡',
  BALANCE: '储值',
  POINTS: '积分'
}

const resourceTypeLabels: Record<string, string> = {
  BALANCE: '余额',
  POINTS: '积分',
  CARD: '次卡'
}

function tagType(status: string) {
  if (['active', 'ACTIVE', 'CONSUMED'].includes(status)) return 'success'
  if (['disabled', 'VOID', 'REVOKED', 'EXPIRED', 'REFUNDED'].includes(status)) return 'danger'
  if (['EXHAUSTED'].includes(status)) return 'warning'
  return 'info'
}

function money(value?: string) {
  if (value === undefined || value === null || value === '') return '—'
  return `¥${value}`
}

const customerOptions = ref<CustomerSummary[]>([])

async function searchCustomers(keyword: string) {
  const result = await fetchCustomers({ keyword: keyword || undefined, page: 1, pageSize: 10 })
  customerOptions.value = result.items
}

function customerLabel(customer?: CustomerSummary) {
  if (!customer) return ''
  return `${customer.name || customer.customerNo}（${customer.customerNo}）`
}

// ------------------------------------------------------------------
// 账户
// ------------------------------------------------------------------
const levelOptions = ref<MemberLevel[]>([])
const accountQuery = reactive({ keyword: '', level: '', status: '', page: 1, pageSize: 20 })
const accountRows = ref<MemberAccount[]>([])
const accountTotal = ref(0)

async function loadLevels() {
  levelOptions.value = await fetchMemberLevels()
}

async function loadAccounts() {
  loading.value = true
  try {
    const result = await fetchMemberAccounts({
      keyword: accountQuery.keyword || undefined,
      level: accountQuery.level || undefined,
      status: accountQuery.status || undefined,
      page: accountQuery.page,
      pageSize: accountQuery.pageSize
    })
    accountRows.value = result.items
    accountTotal.value = result.total
  } finally {
    loading.value = false
  }
}

const openDialogVisible = ref(false)
const openForm = reactive({ customerId: '' })

function openAccountDialog() {
  openForm.customerId = ''
  openDialogVisible.value = true
  void searchCustomers('')
}

async function submitOpenAccount() {
  if (!openForm.customerId) {
    ElMessage.warning('请选择客户')
    return
  }
  try {
    await openMemberAccount({ customerId: openForm.customerId })
    ElMessage.success('会员账户已开通')
    openDialogVisible.value = false
    await loadAccounts()
  } catch (error) {
    ElMessage.error((error as Error).message || '开户失败')
  }
}

const rechargeDialogVisible = ref(false)
const rechargeForm = reactive({ accountId: '', amount: '', reason: '前台充值' })

function openRechargeDialog(row: MemberAccount) {
  rechargeForm.accountId = row.id
  rechargeForm.amount = ''
  rechargeForm.reason = '前台充值'
  rechargeDialogVisible.value = true
}

async function submitRecharge() {
  try {
    await rechargeMemberAccount({
      accountId: rechargeForm.accountId,
      amount: rechargeForm.amount,
      reason: rechargeForm.reason || undefined
    })
    ElMessage.success('充值成功')
    rechargeDialogVisible.value = false
    await loadAccounts()
  } catch (error) {
    ElMessage.error((error as Error).message || '充值失败')
  }
}

const consumeDialogVisible = ref(false)
const consumeForm = reactive({ customerId: '', amount: '', bizNo: '', allowShared: false })

function openConsumeDialog(row: MemberAccount) {
  consumeForm.customerId = row.customerId
  consumeForm.amount = ''
  consumeForm.bizNo = ''
  consumeForm.allowShared = false
  consumeDialogVisible.value = true
}

async function submitConsume() {
  try {
    await consumeMemberBalance({
      customerId: consumeForm.customerId,
      amount: consumeForm.amount,
      bizNo: consumeForm.bizNo || undefined,
      allowShared: consumeForm.allowShared
    })
    ElMessage.success('余额消费成功')
    consumeDialogVisible.value = false
    await loadAccounts()
  } catch (error) {
    ElMessage.error((error as Error).message || '消费失败')
  }
}

const pointsDialogVisible = ref(false)
const pointsForm = reactive({ accountId: '', points: 0, reason: '活动赠送' })

function openPointsDialog(row: MemberAccount) {
  pointsForm.accountId = row.id
  pointsForm.points = 0
  pointsForm.reason = '活动赠送'
  pointsDialogVisible.value = true
}

async function submitPoints() {
  if (!pointsForm.points) {
    ElMessage.warning('积分变动不能为 0')
    return
  }
  try {
    await adjustMemberPoints({
      accountId: pointsForm.accountId,
      points: pointsForm.points,
      reason: pointsForm.reason || undefined
    })
    ElMessage.success('积分已调整')
    pointsDialogVisible.value = false
    await loadAccounts()
  } catch (error) {
    ElMessage.error((error as Error).message || '积分调整失败')
  }
}

// 账本
const ledgerVisible = ref(false)
const ledgerAccount = ref<MemberAccount | null>(null)
const ledgerQuery = reactive({ page: 1, pageSize: 20 })
const ledgerRows = ref<MemberLedgerEntry[]>([])
const ledgerTotal = ref(0)

async function openLedger(row: MemberAccount) {
  ledgerAccount.value = row
  ledgerQuery.page = 1
  ledgerVisible.value = true
  await loadLedger()
}

async function loadLedger() {
  if (!ledgerAccount.value) return
  const result = await fetchMemberLedger(ledgerAccount.value.id, {
    page: ledgerQuery.page,
    pageSize: ledgerQuery.pageSize
  })
  ledgerRows.value = result.items
  ledgerTotal.value = result.total
}

// 账户详情
const detailVisible = ref(false)
const detail = ref<{ account: MemberAccount; cards: MemberCard[] } | null>(null)

async function openAccountDetail(row: MemberAccount) {
  const result = await fetchMemberAccountDetail(row.id)
  detail.value = { account: result.account, cards: result.cards as MemberCard[] }
  detailVisible.value = true
}

// 退款返还
const reversalDialogVisible = ref(false)
const reversalForm = reactive({ orderId: '', refundAmount: '' })

function openReversalDialog() {
  reversalForm.orderId = ''
  reversalForm.refundAmount = ''
  reversalDialogVisible.value = true
}

async function submitReversal() {
  try {
    const result = await reverseMemberBenefits({ ...reversalForm })
    ElMessage.success(
      `权益已返还：扣回余额 ${money(result.balanceReversed)}，扣回积分 ${result.pointsReversed}，作废次卡 ${result.cardsVoided} 张，返还次数 ${result.cardTimesReturned}`
    )
    reversalDialogVisible.value = false
    await loadAccounts()
  } catch (error) {
    ElMessage.error((error as Error).message || '权益返还失败')
  }
}

// ------------------------------------------------------------------
// 次卡
// ------------------------------------------------------------------
const cardQuery = reactive({ status: '', page: 1, pageSize: 20 })
const cardRows = ref<MemberCard[]>([])
const cardTotal = ref(0)

async function loadCards() {
  const result = await fetchMemberCards({
    status: cardQuery.status || undefined,
    page: cardQuery.page,
    pageSize: cardQuery.pageSize
  })
  cardRows.value = result.items
  cardTotal.value = result.total
}

const cardDetailVisible = ref(false)
const cardDetail = ref<MemberCardDetail | null>(null)

async function openCardDetail(row: MemberCard) {
  cardDetail.value = await fetchMemberCardDetail(row.id)
  cardDetailVisible.value = true
}

const cardConsumeVisible = ref(false)
const cardConsumeForm = reactive({ cardId: '', times: 1, bizNo: '', remark: '' })

function openCardConsumeDialog(row: MemberCard) {
  cardConsumeForm.cardId = row.id
  cardConsumeForm.times = 1
  cardConsumeForm.bizNo = ''
  cardConsumeForm.remark = ''
  cardConsumeVisible.value = true
}

async function submitCardConsume() {
  try {
    await consumeMemberCard(cardConsumeForm.cardId, {
      times: cardConsumeForm.times,
      bizNo: cardConsumeForm.bizNo || undefined,
      remark: cardConsumeForm.remark || undefined
    })
    ElMessage.success('扣次成功')
    cardConsumeVisible.value = false
    await loadCards()
  } catch (error) {
    ElMessage.error((error as Error).message || '扣次失败')
  }
}

async function handleCardRefund(row: MemberCard) {
  const { value } = await ElMessageBox.prompt('请输入返还次数', `返还 ${row.cardNo}`, {
    inputValue: '1',
    inputPattern: /^[1-9]\d*$/,
    inputErrorMessage: '请输入正整数'
  })
  try {
    await refundMemberCard(row.id, { times: Number(value), reason: '服务取消' })
    ElMessage.success('次数已返还')
    await loadCards()
  } catch (error) {
    ElMessage.error((error as Error).message || '返还失败')
  }
}

// ------------------------------------------------------------------
// 套餐
// ------------------------------------------------------------------
const packageRows = ref<MemberPackage[]>([])

async function loadPackages() {
  packageRows.value = await fetchMemberPackages()
}

const packageDialogVisible = ref(false)
const packageForm = reactive({
  packageCode: '',
  packageName: '',
  packageType: 'TIMES' as PackageType,
  price: '',
  times: 10,
  balanceAmount: '',
  pointsAmount: 0,
  validDays: 365,
  description: ''
})

function openPackageDialog(row?: MemberPackage) {
  if (row) {
    packageForm.packageCode = row.packageCode
    packageForm.packageName = row.packageName
    packageForm.packageType = row.packageType
    packageForm.price = row.price
    packageForm.times = row.times ?? 10
    packageForm.balanceAmount = row.balanceAmount ?? ''
    packageForm.pointsAmount = row.pointsAmount ?? 0
    packageForm.validDays = row.validDays ?? 365
    packageForm.description = row.description ?? ''
  } else {
    packageForm.packageCode = ''
    packageForm.packageName = ''
    packageForm.packageType = 'TIMES'
    packageForm.price = ''
    packageForm.times = 10
    packageForm.balanceAmount = ''
    packageForm.pointsAmount = 0
    packageForm.validDays = 365
    packageForm.description = ''
  }
  packageDialogVisible.value = true
}

async function submitPackage() {
  const payload = {
    packageCode: packageForm.packageCode,
    packageName: packageForm.packageName,
    packageType: packageForm.packageType,
    price: packageForm.price,
    times: packageForm.packageType === 'TIMES' ? packageForm.times : undefined,
    balanceAmount: packageForm.packageType === 'BALANCE' ? packageForm.balanceAmount : undefined,
    pointsAmount: packageForm.packageType === 'POINTS' ? packageForm.pointsAmount : undefined,
    validDays: packageForm.validDays,
    description: packageForm.description || undefined
  }
  try {
    if (packageForm.packageCode) {
      await updateMemberPackage(packageForm.packageCode, payload)
    } else {
      await createMemberPackage(payload)
    }
    ElMessage.success('套餐已保存')
    packageDialogVisible.value = false
    await loadPackages()
  } catch (error) {
    ElMessage.error((error as Error).message || '保存失败')
  }
}

async function handlePackageStatus(row: MemberPackage) {
  const next = row.status === 'active' ? 'disabled' : 'active'
  await changeMemberPackageStatus(row.packageCode, next)
  ElMessage.success(next === 'active' ? '套餐已启用' : '套餐已停用')
  await loadPackages()
}

const purchaseDialogVisible = ref(false)
const purchaseForm = reactive({ packageCode: '', packageName: '', customerId: '' })

function openPurchaseDialog(row: MemberPackage) {
  purchaseForm.packageCode = row.packageCode
  purchaseForm.packageName = row.packageName
  purchaseForm.customerId = ''
  purchaseDialogVisible.value = true
  void searchCustomers('')
}

async function submitPurchase() {
  if (!purchaseForm.customerId) {
    ElMessage.warning('请选择客户')
    return
  }
  try {
    const result = await purchaseMemberPackage(purchaseForm.packageCode, { customerId: purchaseForm.customerId })
    ElMessage.success(
      result.card
        ? `购买成功，已生成次卡 ${result.card.cardNo}（${result.card.remainingTimes} 次）`
        : `购买成功，余额 +${money(result.balanceAdded)}，积分 +${result.pointsAdded}`
    )
    purchaseDialogVisible.value = false
    await Promise.all([loadPackages(), loadAccounts(), loadCards()])
  } catch (error) {
    ElMessage.error((error as Error).message || '购买失败')
  }
}

// ------------------------------------------------------------------
// 共享授权
// ------------------------------------------------------------------
const shareRows = ref<MemberShare[]>([])
const shareQuery = reactive({ ownerCustomerId: '', beneficiaryCustomerId: '' })

async function loadShares() {
  if (!shareQuery.ownerCustomerId && !shareQuery.beneficiaryCustomerId) {
    shareRows.value = []
    return
  }
  shareRows.value = await fetchMemberShares({
    ownerCustomerId: shareQuery.ownerCustomerId || undefined,
    beneficiaryCustomerId: shareQuery.beneficiaryCustomerId || undefined
  })
}

const shareDialogVisible = ref(false)
const shareForm = reactive({
  ownerCustomerId: '',
  beneficiaryCustomerId: '',
  resourceType: 'BALANCE' as ShareResourceType,
  canConsume: true
})

function openShareDialog() {
  shareForm.ownerCustomerId = ''
  shareForm.beneficiaryCustomerId = ''
  shareForm.resourceType = 'BALANCE'
  shareForm.canConsume = true
  shareDialogVisible.value = true
  void searchCustomers('')
}

async function submitShare() {
  if (!shareForm.ownerCustomerId || !shareForm.beneficiaryCustomerId) {
    ElMessage.warning('请选择授权人与使用人')
    return
  }
  try {
    await createMemberShare({ ...shareForm })
    ElMessage.success('共享授权已创建')
    shareDialogVisible.value = false
    shareQuery.ownerCustomerId = shareForm.ownerCustomerId
    await loadShares()
  } catch (error) {
    ElMessage.error((error as Error).message || '授权失败')
  }
}

async function handleRevokeShare(row: MemberShare) {
  await ElMessageBox.confirm(`确认撤销 ${row.ownerCustomerName || row.ownerCustomerId} 的${resourceTypeLabels[row.resourceType]}授权？`, '撤销授权')
  try {
    await revokeMemberShare(row.id)
    ElMessage.success('授权已撤销')
    await loadShares()
  } catch (error) {
    ElMessage.error((error as Error).message || '撤销失败')
  }
}

// ------------------------------------------------------------------
// 标签切换与初始化
// ------------------------------------------------------------------
function handleTabChange(name: string) {
  if (name === 'accounts') void loadAccounts()
  if (name === 'cards') void loadCards()
  if (name === 'packages') void loadPackages()
  if (name === 'shares') void loadShares()
}

onMounted(() => {
  void loadLevels()
  void loadAccounts()
})
</script>

<template>
  <section class="member-page">
    <header class="page-head">
      <div>
        <h2>会员与权益</h2>
        <p class="page-desc">
          会员等级、储值余额、积分、次卡与套餐；余额与积分每次变动写账本，扣减使用条件更新避免并发超扣，退款按规则返还权益。
        </p>
      </div>
    </header>

    <el-tabs v-model="activeTab" @tab-change="handleTabChange">
      <!-- 账户 -->
      <el-tab-pane label="会员账户" name="accounts">
        <div class="toolbar">
          <el-input v-model="accountQuery.keyword" placeholder="账户号" clearable style="width: 180px" />
          <el-select v-model="accountQuery.level" placeholder="全部等级" clearable style="width: 150px">
            <el-option v-for="level in levelOptions" :key="level.levelCode" :label="level.levelName" :value="level.levelCode" />
          </el-select>
          <el-select v-model="accountQuery.status" placeholder="全部状态" clearable style="width: 130px">
            <el-option label="正常" value="active" />
            <el-option label="停用" value="disabled" />
          </el-select>
          <el-button type="primary" @click="accountQuery.page = 1; loadAccounts()">查询</el-button>
          <el-button v-if="canWrite" @click="openAccountDialog">开通账户</el-button>
          <el-button v-if="canManage" @click="openReversalDialog">退款返还权益</el-button>
        </div>

        <el-table v-loading="loading" :data="accountRows" border stripe>
          <el-table-column prop="accountNo" label="账户号" width="130" />
          <el-table-column prop="customerName" label="客户" min-width="120" />
          <el-table-column label="等级" width="110">
            <template #default="{ row }">
              <el-tag :type="tagType(row.status)">{{ row.levelName || row.memberLevel }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="余额" width="120">
            <template #default="{ row }">{{ money(row.balance) }}</template>
          </el-table-column>
          <el-table-column label="冻结" width="110">
            <template #default="{ row }">{{ money(row.frozenBalance) }}</template>
          </el-table-column>
          <el-table-column prop="points" label="积分" width="100" />
          <el-table-column prop="createdAt" label="开户时间" width="180" />
          <el-table-column label="操作" width="330" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openAccountDetail(row)">详情</el-button>
              <el-button link type="primary" @click="openLedger(row)">账本</el-button>
              <el-button v-if="canWrite" link type="success" @click="openRechargeDialog(row)">充值</el-button>
              <el-button v-if="canWrite" link type="warning" @click="openConsumeDialog(row)">消费</el-button>
              <el-button v-if="canWrite" link type="info" @click="openPointsDialog(row)">积分</el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-pagination
          v-model:current-page="accountQuery.page"
          v-model:page-size="accountQuery.pageSize"
          :total="accountTotal"
          layout="total, prev, pager, next, sizes"
          @current-change="loadAccounts"
          @size-change="accountQuery.page = 1; loadAccounts()"
        />
      </el-tab-pane>

      <!-- 次卡 -->
      <el-tab-pane label="次卡" name="cards">
        <div class="toolbar">
          <el-select v-model="cardQuery.status" placeholder="全部状态" clearable style="width: 140px">
            <el-option v-for="(label, key) in cardStatusLabels" :key="key" :label="label" :value="key" />
          </el-select>
          <el-button type="primary" @click="cardQuery.page = 1; loadCards()">查询</el-button>
        </div>

        <el-table :data="cardRows" border stripe>
          <el-table-column prop="cardNo" label="卡号" width="130" />
          <el-table-column prop="customerName" label="客户" min-width="120" />
          <el-table-column prop="packageName" label="套餐" min-width="140" />
          <el-table-column prop="totalTimes" label="总次数" width="90" />
          <el-table-column prop="usedTimes" label="已用" width="80" />
          <el-table-column prop="remainingTimes" label="剩余" width="80" />
          <el-table-column prop="validTo" label="有效期至" width="120" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="tagType(row.status)">{{ cardStatusLabels[row.status] || row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="220" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openCardDetail(row)">扣次记录</el-button>
              <el-button v-if="canWrite && row.status === 'ACTIVE'" link type="success" @click="openCardConsumeDialog(row)">扣次</el-button>
              <el-button v-if="canWrite && row.usedTimes > 0" link type="warning" @click="handleCardRefund(row)">返还</el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-pagination
          v-model:current-page="cardQuery.page"
          v-model:page-size="cardQuery.pageSize"
          :total="cardTotal"
          layout="total, prev, pager, next, sizes"
          @current-change="loadCards"
          @size-change="cardQuery.page = 1; loadCards()"
        />
      </el-tab-pane>

      <!-- 套餐 -->
      <el-tab-pane label="套餐" name="packages">
        <div class="toolbar">
          <el-button type="primary" @click="loadPackages">刷新</el-button>
          <el-button v-if="canManage" @click="openPackageDialog()">新建套餐</el-button>
        </div>

        <el-table :data="packageRows" border stripe>
          <el-table-column prop="packageCode" label="编码" width="150" />
          <el-table-column prop="packageName" label="名称" min-width="160" />
          <el-table-column label="类型" width="100">
            <template #default="{ row }">{{ packageTypeLabels[row.packageType] || row.packageType }}</template>
          </el-table-column>
          <el-table-column label="价格" width="110">
            <template #default="{ row }">{{ money(row.price) }}</template>
          </el-table-column>
          <el-table-column label="权益" min-width="160">
            <template #default="{ row }">
              <span v-if="row.packageType === 'TIMES'">{{ row.times }} 次 / {{ row.validDays }} 天</span>
              <span v-else-if="row.packageType === 'BALANCE'">余额 {{ money(row.balanceAmount) }}</span>
              <span v-else>积分 {{ row.pointsAmount }}</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="tagType(row.status)">{{ row.status === 'active' ? '已启用' : '已停用' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="230" fixed="right">
            <template #default="{ row }">
              <el-button v-if="canWrite" link type="success" @click="openPurchaseDialog(row)">购买</el-button>
              <el-button v-if="canManage" link type="primary" @click="openPackageDialog(row)">编辑</el-button>
              <el-button v-if="canManage" link type="warning" @click="handlePackageStatus(row)">
                {{ row.status === 'active' ? '停用' : '启用' }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- 共享授权 -->
      <el-tab-pane label="共享授权" name="shares">
        <div class="toolbar">
          <el-select
            v-model="shareQuery.ownerCustomerId"
            filterable
            remote
            clearable
            :remote-method="searchCustomers"
            placeholder="授权人"
            style="width: 220px"
            @visible-change="(visible: boolean) => visible && searchCustomers('')"
          >
            <el-option
              v-for="customer in customerOptions"
              :key="customer.id"
              :label="customerLabel(customer)"
              :value="customer.id"
            />
          </el-select>
          <el-button type="primary" @click="loadShares">查询</el-button>
          <el-button v-if="canWrite" @click="openShareDialog">新建授权</el-button>
        </div>

        <el-table :data="shareRows" border stripe empty-text="请选择授权人后查询">
          <el-table-column prop="ownerCustomerName" label="授权人" min-width="120" />
          <el-table-column prop="beneficiaryCustomerName" label="使用人" min-width="120" />
          <el-table-column label="资源" width="100">
            <template #default="{ row }">{{ resourceTypeLabels[row.resourceType] || row.resourceType }}</template>
          </el-table-column>
          <el-table-column label="可消费" width="90">
            <template #default="{ row }">{{ row.canConsume ? '是' : '否' }}</template>
          </el-table-column>
          <el-table-column prop="validTo" label="失效时间" width="180" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="tagType(row.status)">{{ shareStatusLabels[row.status] || row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="100" fixed="right">
            <template #default="{ row }">
              <el-button v-if="canWrite && row.status === 'ACTIVE'" link type="danger" @click="handleRevokeShare(row)">撤销</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <!-- 开通账户 -->
    <el-dialog v-model="openDialogVisible" title="开通会员账户" width="460px">
      <el-form label-width="80px">
        <el-form-item label="客户" required>
          <el-select
            v-model="openForm.customerId"
            filterable
            remote
            :remote-method="searchCustomers"
            placeholder="按姓名或手机号搜索"
            style="width: 100%"
          >
            <el-option
              v-for="customer in customerOptions"
              :key="customer.id"
              :label="customerLabel(customer)"
              :value="customer.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="openDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitOpenAccount">开通</el-button>
      </template>
    </el-dialog>

    <!-- 充值 -->
    <el-dialog v-model="rechargeDialogVisible" title="会员充值" width="420px">
      <el-form label-width="80px">
        <el-form-item label="金额" required>
          <el-input v-model="rechargeForm.amount" placeholder="大于 0" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="rechargeForm.reason" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rechargeDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitRecharge">确认充值</el-button>
      </template>
    </el-dialog>

    <!-- 余额消费 -->
    <el-dialog v-model="consumeDialogVisible" title="会员余额消费" width="420px">
      <el-form label-width="90px">
        <el-form-item label="金额" required>
          <el-input v-model="consumeForm.amount" placeholder="不超过可用余额" />
        </el-form-item>
        <el-form-item label="业务单号">
          <el-input v-model="consumeForm.bizNo" />
        </el-form-item>
        <el-form-item label="允许代付">
          <el-switch v-model="consumeForm.allowShared" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="consumeDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitConsume">确认扣款</el-button>
      </template>
    </el-dialog>

    <!-- 积分调整 -->
    <el-dialog v-model="pointsDialogVisible" title="积分调整" width="420px">
      <el-form label-width="90px">
        <el-form-item label="积分变动" required>
          <el-input-number v-model="pointsForm.points" :step="100" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="pointsForm.reason" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pointsDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitPoints">确认调整</el-button>
      </template>
    </el-dialog>

    <!-- 退款返还 -->
    <el-dialog v-model="reversalDialogVisible" title="退款返还会员权益" width="460px">
      <el-form label-width="90px">
        <el-form-item label="订单 ID" required>
          <el-input v-model="reversalForm.orderId" placeholder="订单 UUID" />
        </el-form-item>
        <el-form-item label="退款金额" required>
          <el-input v-model="reversalForm.refundAmount" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reversalDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitReversal">确认返还</el-button>
      </template>
    </el-dialog>

    <!-- 扣次 -->
    <el-dialog v-model="cardConsumeVisible" title="次卡扣次" width="420px">
      <el-form label-width="90px">
        <el-form-item label="次数" required>
          <el-input-number v-model="cardConsumeForm.times" :min="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="业务单号">
          <el-input v-model="cardConsumeForm.bizNo" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="cardConsumeForm.remark" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="cardConsumeVisible = false">取消</el-button>
        <el-button type="primary" @click="submitCardConsume">确认扣次</el-button>
      </template>
    </el-dialog>

    <!-- 套餐编辑 -->
    <el-dialog v-model="packageDialogVisible" :title="packageForm.packageCode ? '编辑套餐' : '新建套餐'" width="520px">
      <el-form label-width="100px">
        <el-form-item label="套餐编码" required>
          <el-input v-model="packageForm.packageCode" :disabled="!!packageForm.packageCode" placeholder="字母、数字与下划线" />
        </el-form-item>
        <el-form-item label="套餐名称" required>
          <el-input v-model="packageForm.packageName" />
        </el-form-item>
        <el-form-item label="套餐类型" required>
          <el-select v-model="packageForm.packageType" :disabled="!!packageForm.packageCode" style="width: 100%">
            <el-option label="次卡" value="TIMES" />
            <el-option label="储值" value="BALANCE" />
            <el-option label="积分" value="POINTS" />
          </el-select>
        </el-form-item>
        <el-form-item label="价格" required>
          <el-input v-model="packageForm.price" />
        </el-form-item>
        <el-form-item v-if="packageForm.packageType === 'TIMES'" label="次数">
          <el-input-number v-model="packageForm.times" :min="1" style="width: 100%" />
        </el-form-item>
        <el-form-item v-if="packageForm.packageType === 'BALANCE'" label="储值金额">
          <el-input v-model="packageForm.balanceAmount" />
        </el-form-item>
        <el-form-item v-if="packageForm.packageType === 'POINTS'" label="积分">
          <el-input-number v-model="packageForm.pointsAmount" :min="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="有效天数">
          <el-input-number v-model="packageForm.validDays" :min="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="packageForm.description" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="packageDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitPackage">保存</el-button>
      </template>
    </el-dialog>

    <!-- 购买套餐 -->
    <el-dialog v-model="purchaseDialogVisible" title="购买套餐" width="460px">
      <el-form label-width="90px">
        <el-form-item label="套餐">
          <span>{{ purchaseForm.packageName }}（{{ purchaseForm.packageCode }}）</span>
        </el-form-item>
        <el-form-item label="客户" required>
          <el-select
            v-model="purchaseForm.customerId"
            filterable
            remote
            :remote-method="searchCustomers"
            placeholder="按姓名或手机号搜索"
            style="width: 100%"
          >
            <el-option
              v-for="customer in customerOptions"
              :key="customer.id"
              :label="customerLabel(customer)"
              :value="customer.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="purchaseDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitPurchase">确认购买</el-button>
      </template>
    </el-dialog>

    <!-- 新建共享授权 -->
    <el-dialog v-model="shareDialogVisible" title="新建共享授权" width="520px">
      <el-form label-width="90px">
        <el-form-item label="授权人" required>
          <el-select
            v-model="shareForm.ownerCustomerId"
            filterable
            remote
            :remote-method="searchCustomers"
            placeholder="按姓名或手机号搜索"
            style="width: 100%"
          >
            <el-option
              v-for="customer in customerOptions"
              :key="customer.id"
              :label="customerLabel(customer)"
              :value="customer.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="使用人" required>
          <el-select
            v-model="shareForm.beneficiaryCustomerId"
            filterable
            remote
            :remote-method="searchCustomers"
            placeholder="按姓名或手机号搜索"
            style="width: 100%"
          >
            <el-option
              v-for="customer in customerOptions"
              :key="customer.id"
              :label="customerLabel(customer)"
              :value="customer.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="资源类型" required>
          <el-select v-model="shareForm.resourceType" style="width: 100%">
            <el-option label="余额" value="BALANCE" />
            <el-option label="积分" value="POINTS" />
            <el-option label="次卡" value="CARD" />
          </el-select>
        </el-form-item>
        <el-form-item label="可消费">
          <el-switch v-model="shareForm.canConsume" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="shareDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitShare">授权</el-button>
      </template>
    </el-dialog>

    <!-- 账本 -->
    <el-drawer v-model="ledgerVisible" :title="`账本 · ${ledgerAccount?.accountNo || ''}`" size="680px">
      <el-table :data="ledgerRows" border size="small">
        <el-table-column prop="ledgerType" label="类型" width="170" />
        <el-table-column label="余额变动" width="110">
          <template #default="{ row }">{{ money(row.amount) }}</template>
        </el-table-column>
        <el-table-column prop="pointsDelta" label="积分变动" width="100" />
        <el-table-column label="变动后余额" width="120">
          <template #default="{ row }">{{ money(row.balanceAfter) }}</template>
        </el-table-column>
        <el-table-column prop="pointsAfter" label="变动后积分" width="110" />
        <el-table-column prop="bizNo" label="业务单号" min-width="140" />
        <el-table-column prop="reason" label="备注" min-width="140" />
        <el-table-column prop="createdAt" label="时间" width="180" />
      </el-table>
      <el-pagination
        v-model:current-page="ledgerQuery.page"
        v-model:page-size="ledgerQuery.pageSize"
        :total="ledgerTotal"
        layout="total, prev, pager, next"
        @current-change="loadLedger"
      />
    </el-drawer>

    <!-- 账户详情 -->
    <el-drawer v-model="detailVisible" title="会员账户详情" size="640px">
      <template v-if="detail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="账户号">{{ detail.account.accountNo }}</el-descriptions-item>
          <el-descriptions-item label="客户">{{ detail.account.customerName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="等级">{{ detail.account.levelName || detail.account.memberLevel }}</el-descriptions-item>
          <el-descriptions-item label="折扣率">{{ detail.account.discountRate || '—' }}</el-descriptions-item>
          <el-descriptions-item label="余额">{{ money(detail.account.balance) }}</el-descriptions-item>
          <el-descriptions-item label="积分">{{ detail.account.points }}</el-descriptions-item>
        </el-descriptions>
        <h4>有效次卡</h4>
        <el-table :data="detail.cards" border size="small" empty-text="暂无有效次卡">
          <el-table-column prop="cardNo" label="卡号" width="130" />
          <el-table-column prop="packageName" label="套餐" min-width="140" />
          <el-table-column prop="remainingTimes" label="剩余" width="80" />
          <el-table-column prop="validTo" label="有效期至" width="120" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="tagType(row.status)">{{ cardStatusLabels[row.status] || row.status }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </template>
    </el-drawer>

    <!-- 次卡扣次记录 -->
    <el-drawer v-model="cardDetailVisible" title="次卡扣次记录" size="680px">
      <template v-if="cardDetail">
        <el-descriptions :column="3" border>
          <el-descriptions-item label="卡号">{{ cardDetail.card.cardNo }}</el-descriptions-item>
          <el-descriptions-item label="剩余">{{ cardDetail.card.remainingTimes }}</el-descriptions-item>
          <el-descriptions-item label="有效期至">{{ cardDetail.card.validTo }}</el-descriptions-item>
        </el-descriptions>
        <el-table :data="cardDetail.usages" border size="small" empty-text="暂无扣次记录">
          <el-table-column prop="times" label="次数" width="80" />
          <el-table-column prop="bizNo" label="业务单号" min-width="140" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="tagType(row.status)">{{ row.status === 'CONSUMED' ? '已扣次' : '已返还' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createdAt" label="扣次时间" width="180" />
          <el-table-column prop="refundedAt" label="返还时间" width="180" />
        </el-table>
      </template>
    </el-drawer>
  </section>
</template>

<style scoped>
.member-page {
  padding: 20px 24px 32px;
}

.page-head h2 {
  margin: 0 0 6px;
  font-size: 20px;
}

.page-desc {
  margin: 0 0 16px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.6;
}

.toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
}

.el-pagination {
  margin-top: 14px;
  justify-content: flex-end;
}

h4 {
  margin: 20px 0 10px;
  font-size: 14px;
}
</style>
