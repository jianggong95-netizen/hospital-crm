<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { fetchCustomers } from '@/api/customers'
import {
  applyInvoice,
  applyRefund,
  approveRefund,
  cancelOrder,
  completeOrder,
  createOrder,
  fetchInvoices,
  fetchOrderDetail,
  fetchOrders,
  fetchPayments,
  fetchReconciliationDiffs,
  fetchReconciliations,
  fetchRefunds,
  initiatePayment,
  issueInvoice,
  rejectRefund,
  runReconciliation,
  voidInvoice
} from '@/api/order'
import type { CustomerSummary } from '@/types/customer'
import type {
  InvoiceItem,
  OrderDetail,
  OrderItemPayload,
  OrderSummary,
  PaymentChannel,
  PaymentItem,
  ReconciliationBatch,
  ReconciliationDetail,
  RefundItem
} from '@/types/order'

const auth = useAuthStore()
const canWrite = computed(() => auth.has('crm:order:write'))
const canManage = computed(() => auth.has('crm:payment:manage'))

const activeTab = ref('orders')
const loading = ref(false)

// ------------------------------------------------------------------
// 标签
// ------------------------------------------------------------------
const orderStatusLabels: Record<string, string> = {
  CREATED: '待支付',
  PAID: '已支付',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
  PARTIAL_REFUNDED: '部分退款',
  REFUNDED: '已退款'
}

const paymentStatusLabels: Record<string, string> = {
  PROCESSING: '处理中',
  SUCCESS: '支付成功',
  FAILED: '支付失败',
  CLOSED: '已关闭'
}

const refundStatusLabels: Record<string, string> = {
  PENDING: '待审核',
  APPROVED: '已审核',
  REJECTED: '已驳回',
  SUCCESS: '退款成功',
  FAILED: '退款失败'
}

const invoiceStatusLabels: Record<string, string> = {
  APPLIED: '待开票',
  ISSUED: '已开票',
  VOID: '已红冲'
}

const reconStatusLabels: Record<string, string> = {
  RUNNING: '执行中',
  MATCHED: '账实相符',
  DIFF: '存在差异',
  FAILED: '执行失败'
}

const diffTypeLabels: Record<string, string> = {
  LOCAL_ONLY: '仅本地有',
  CHANNEL_ONLY: '仅渠道有',
  AMOUNT_MISMATCH: '金额不一致'
}

const channelLabels: Record<string, string> = {
  wechat: '微信支付',
  alipay: '支付宝',
  bank: '银行转账',
  cash: '现金',
  other: '其他'
}

function tagType(status: string) {
  if (['COMPLETED', 'SUCCESS', 'ISSUED', 'MATCHED'].includes(status)) return 'success'
  if (['CANCELLED', 'FAILED', 'REJECTED', 'VOID', 'DIFF'].includes(status)) return 'danger'
  if (['PAID', 'PARTIAL_REFUNDED', 'REFUNDED', 'PROCESSING', 'PENDING', 'APPLIED'].includes(status)) return 'warning'
  return 'info'
}

function money(value?: string) {
  if (value === undefined || value === null || value === '') return '—'
  return `¥${value}`
}

// ------------------------------------------------------------------
// 订单
// ------------------------------------------------------------------
const orderQuery = reactive({ keyword: '', status: '', dateFrom: '', dateTo: '', page: 1, pageSize: 20 })
const orderRows = ref<OrderSummary[]>([])
const orderTotal = ref(0)

async function loadOrders() {
  loading.value = true
  try {
    const result = await fetchOrders({
      keyword: orderQuery.keyword || undefined,
      status: orderQuery.status || undefined,
      dateFrom: orderQuery.dateFrom || undefined,
      dateTo: orderQuery.dateTo || undefined,
      page: orderQuery.page,
      pageSize: orderQuery.pageSize
    })
    orderRows.value = result.items
    orderTotal.value = result.total
  } finally {
    loading.value = false
  }
}

const createDialogVisible = ref(false)
const customerOptions = ref<CustomerSummary[]>([])
const createForm = reactive({
  customerId: '',
  orderType: 'escort',
  discountAmount: '',
  items: [{ itemCode: '', itemName: '', quantity: '1', unitPrice: '' }] as OrderItemPayload[]
})

function addItemLine() {
  createForm.items.push({ itemCode: '', itemName: '', quantity: '1', unitPrice: '' })
}

function removeItemLine(index: number) {
  if (createForm.items.length > 1) createForm.items.splice(index, 1)
}

async function searchCustomers(keyword: string) {
  const result = await fetchCustomers({ keyword: keyword || undefined, page: 1, pageSize: 10 })
  customerOptions.value = result.items
}

function openCreateDialog() {
  createForm.customerId = ''
  createForm.orderType = 'escort'
  createForm.discountAmount = ''
  createForm.items = [{ itemCode: '', itemName: '', quantity: '1', unitPrice: '' }]
  createDialogVisible.value = true
  void searchCustomers('')
}

async function submitCreate() {
  if (!createForm.customerId) {
    ElMessage.warning('请选择客户')
    return
  }
  const invalid = createForm.items.some(item => !item.itemCode || !item.itemName || !item.unitPrice)
  if (invalid) {
    ElMessage.warning('请补全明细编号、名称与单价')
    return
  }
  try {
    await createOrder({
      customerId: createForm.customerId,
      orderType: createForm.orderType,
      discountAmount: createForm.discountAmount || undefined,
      items: createForm.items.map(item => ({ ...item })),
      idempotencyKey: `web-${Date.now()}`
    })
    ElMessage.success('订单已创建')
    createDialogVisible.value = false
    await loadOrders()
  } catch (error) {
    ElMessage.error((error as Error).message || '创建失败')
  }
}

const detailVisible = ref(false)
const detail = ref<OrderDetail | null>(null)

async function openDetail(row: OrderSummary) {
  detail.value = await fetchOrderDetail(row.id)
  detailVisible.value = true
}

async function handleCancel(row: OrderSummary) {
  const { value } = await ElMessageBox.prompt('请输入取消原因', '取消订单', { inputValue: '客户取消' })
  await cancelOrder(row.id, value)
  ElMessage.success('订单已取消')
  await loadOrders()
}

async function handleComplete(row: OrderSummary) {
  await completeOrder(row.id)
  ElMessage.success('订单已完成')
  await loadOrders()
}

// 发起支付
const payDialogVisible = ref(false)
const payForm = reactive({ orderId: '', amount: '', channel: 'wechat' as PaymentChannel })

function openPayDialog(row: OrderSummary) {
  payForm.orderId = row.id
  const outstanding = (Number(row.payableAmount) - Number(row.paidAmount)).toFixed(2)
  payForm.amount = outstanding
  payForm.channel = 'wechat'
  payDialogVisible.value = true
}

async function submitPay() {
  try {
    await initiatePayment(payForm.orderId, {
      channel: payForm.channel,
      amount: payForm.amount,
      idempotencyKey: `web-pay-${payForm.orderId}-${Date.now()}`
    })
    ElMessage.success('支付单已创建，等待渠道回调')
    payDialogVisible.value = false
    await Promise.all([loadOrders(), loadPayments()])
  } catch (error) {
    ElMessage.error((error as Error).message || '发起支付失败')
  }
}

// ------------------------------------------------------------------
// 支付
// ------------------------------------------------------------------
const paymentQuery = reactive({ keyword: '', status: '', channel: '', page: 1, pageSize: 20 })
const paymentRows = ref<PaymentItem[]>([])
const paymentTotal = ref(0)

async function loadPayments() {
  const result = await fetchPayments({
    keyword: paymentQuery.keyword || undefined,
    status: paymentQuery.status || undefined,
    channel: paymentQuery.channel || undefined,
    page: paymentQuery.page,
    pageSize: paymentQuery.pageSize
  })
  paymentRows.value = result.items
  paymentTotal.value = result.total
}

// ------------------------------------------------------------------
// 退款
// ------------------------------------------------------------------
const refundQuery = reactive({ status: '', page: 1, pageSize: 20 })
const refundRows = ref<RefundItem[]>([])
const refundTotal = ref(0)

async function loadRefunds() {
  const result = await fetchRefunds({
    status: refundQuery.status || undefined,
    page: refundQuery.page,
    pageSize: refundQuery.pageSize
  })
  refundRows.value = result.items
  refundTotal.value = result.total
}

const refundDialogVisible = ref(false)
const refundForm = reactive({ paymentId: '', amount: '', reason: '客户申请退款' })

function openRefundDialog(payment?: PaymentItem) {
  refundForm.paymentId = payment ? payment.id : ''
  refundForm.amount = payment ? (Number(payment.amount) - Number(payment.refundedAmount)).toFixed(2) : ''
  refundForm.reason = '客户申请退款'
  refundDialogVisible.value = true
}

async function submitRefund() {
  try {
    await applyRefund(refundForm.paymentId, { amount: refundForm.amount, reason: refundForm.reason })
    ElMessage.success('退款申请已提交')
    refundDialogVisible.value = false
    await loadRefunds()
  } catch (error) {
    ElMessage.error((error as Error).message || '退款申请失败')
  }
}

async function handleApproveRefund(row: RefundItem) {
  await ElMessageBox.confirm(`确认通过 ${row.refundNo} 的退款（${money(row.amount)}）？`, '退款审核')
  await approveRefund(row.id)
  ElMessage.success('退款已执行')
  await Promise.all([loadRefunds(), loadOrders()])
}

async function handleRejectRefund(row: RefundItem) {
  const { value } = await ElMessageBox.prompt('请输入驳回原因', '驳回购退款', { inputValue: '资料不完整' })
  await rejectRefund(row.id, value)
  ElMessage.success('退款已驳回')
  await loadRefunds()
}

// ------------------------------------------------------------------
// 对账
// ------------------------------------------------------------------
const reconQuery = reactive({ channel: '', status: '', page: 1, pageSize: 20 })
const reconRows = ref<ReconciliationBatch[]>([])
const reconTotal = ref(0)
const reconRunForm = reactive({ channel: 'wechat', reconcileDate: new Date().toISOString().slice(0, 10) })

async function loadReconciliations() {
  const result = await fetchReconciliations({
    channel: reconQuery.channel || undefined,
    status: reconQuery.status || undefined,
    page: reconQuery.page,
    pageSize: reconQuery.pageSize
  })
  reconRows.value = result.items
  reconTotal.value = result.total
}

async function handleRunReconciliation() {
  try {
    const result = await runReconciliation({ ...reconRunForm })
    ElMessage.success(
      result.batch.status === 'MATCHED' ? '对账完成，账实相符' : `对账完成，发现 ${result.batch.diffCount} 笔差异`
    )
    await loadReconciliations()
  } catch (error) {
    ElMessage.error((error as Error).message || '对账执行失败')
  }
}

const reconDetailVisible = ref(false)
const reconDetail = ref<ReconciliationDetail | null>(null)

async function openReconDetail(row: ReconciliationBatch) {
  reconDetail.value = await fetchReconciliationDiffs(row.id)
  reconDetailVisible.value = true
}

// ------------------------------------------------------------------
// 发票
// ------------------------------------------------------------------
const invoiceQuery = reactive({ status: '', page: 1, pageSize: 20 })
const invoiceRows = ref<InvoiceItem[]>([])
const invoiceTotal = ref(0)

async function loadInvoices() {
  const result = await fetchInvoices({
    status: invoiceQuery.status || undefined,
    page: invoiceQuery.page,
    pageSize: invoiceQuery.pageSize
  })
  invoiceRows.value = result.items
  invoiceTotal.value = result.total
}

const invoiceDialogVisible = ref(false)
const invoiceForm = reactive({ orderId: '', title: '', taxNo: '', amount: '', invoiceType: 'general' as 'general' | 'special' })

function openInvoiceDialog(row?: OrderSummary) {
  invoiceForm.orderId = row ? row.id : ''
  invoiceForm.title = row && row.customerName ? row.customerName : ''
  invoiceForm.taxNo = ''
  invoiceForm.amount = row ? (Number(row.paidAmount) - Number(row.refundedAmount)).toFixed(2) : ''
  invoiceForm.invoiceType = 'general'
  invoiceDialogVisible.value = true
}

async function submitInvoice() {
  try {
    await applyInvoice(invoiceForm.orderId, {
      title: invoiceForm.title,
      taxNo: invoiceForm.taxNo || undefined,
      amount: invoiceForm.amount,
      invoiceType: invoiceForm.invoiceType
    })
    ElMessage.success('开票申请已提交')
    invoiceDialogVisible.value = false
    await loadInvoices()
  } catch (error) {
    ElMessage.error((error as Error).message || '开票申请失败')
  }
}

async function handleIssueInvoice(row: InvoiceItem) {
  await issueInvoice(row.id)
  ElMessage.success('发票已开具')
  await loadInvoices()
}

async function handleVoidInvoice(row: InvoiceItem) {
  const { value } = await ElMessageBox.prompt('请输入红冲原因', '红冲发票', { inputValue: '抬头填写错误' })
  await voidInvoice(row.id, value)
  ElMessage.success('发票已红冲')
  await loadInvoices()
}

// ------------------------------------------------------------------
// 标签切换与初始化
// ------------------------------------------------------------------
function handleTabChange(name: string) {
  if (name === 'orders') void loadOrders()
  if (name === 'payments') void loadPayments()
  if (name === 'refunds') void loadRefunds()
  if (name === 'reconciliations') void loadReconciliations()
  if (name === 'invoices') void loadInvoices()
}

onMounted(() => {
  void loadOrders()
})
</script>

<template>
  <section class="order-page">
    <header class="page-head">
      <div>
        <h2>收银与订单</h2>
        <p class="page-desc">
          统一订单、支付单、退款、渠道对账与发票流水；金额由服务端按订单项重算，CRM 收费仅在 CRM 主库记账，不写 HIS 收费表。
        </p>
      </div>
    </header>

    <el-tabs v-model="activeTab" @tab-change="handleTabChange">
      <!-- 订单 -->
      <el-tab-pane label="订单" name="orders">
        <div class="toolbar">
          <el-input v-model="orderQuery.keyword" placeholder="订单号" clearable style="width: 180px" />
          <el-select v-model="orderQuery.status" placeholder="全部状态" clearable style="width: 140px">
            <el-option v-for="(label, key) in orderStatusLabels" :key="key" :label="label" :value="key" />
          </el-select>
          <el-date-picker v-model="orderQuery.dateFrom" type="date" placeholder="开始日期" value-format="YYYY-MM-DD" style="width: 150px" />
          <el-date-picker v-model="orderQuery.dateTo" type="date" placeholder="结束日期" value-format="YYYY-MM-DD" style="width: 150px" />
          <el-button type="primary" @click="orderQuery.page = 1; loadOrders()">查询</el-button>
          <el-button v-if="canWrite" @click="openCreateDialog">新建订单</el-button>
        </div>

        <el-table v-loading="loading" :data="orderRows" border stripe>
          <el-table-column prop="orderNo" label="订单号" width="130" />
          <el-table-column prop="customerName" label="客户" min-width="120" />
          <el-table-column prop="orderType" label="类型" width="120" />
          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              <el-tag :type="tagType(row.status)">{{ orderStatusLabels[row.status] || row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="总额" width="110">
            <template #default="{ row }">{{ money(row.totalAmount) }}</template>
          </el-table-column>
          <el-table-column label="应付" width="110">
            <template #default="{ row }">{{ money(row.payableAmount) }}</template>
          </el-table-column>
          <el-table-column label="实付" width="110">
            <template #default="{ row }">{{ money(row.paidAmount) }}</template>
          </el-table-column>
          <el-table-column label="已退" width="110">
            <template #default="{ row }">{{ money(row.refundedAmount) }}</template>
          </el-table-column>
          <el-table-column prop="createdAt" label="创建时间" width="180" />
          <el-table-column label="操作" width="280" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openDetail(row)">详情</el-button>
              <el-button v-if="canWrite && row.status === 'CREATED'" link type="primary" @click="openPayDialog(row)">发起支付</el-button>
              <el-button v-if="canWrite && row.status === 'PAID'" link type="success" @click="handleComplete(row)">完成</el-button>
              <el-button v-if="canWrite && row.status === 'CREATED'" link type="danger" @click="handleCancel(row)">取消</el-button>
              <el-button v-if="canWrite && Number(row.paidAmount) > 0" link type="warning" @click="openInvoiceDialog(row)">开票</el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-pagination
          v-model:current-page="orderQuery.page"
          v-model:page-size="orderQuery.pageSize"
          :total="orderTotal"
          layout="total, prev, pager, next, sizes"
          @current-change="loadOrders"
          @size-change="orderQuery.page = 1; loadOrders()"
        />
      </el-tab-pane>

      <!-- 支付 -->
      <el-tab-pane label="支付" name="payments">
        <div class="toolbar">
          <el-input v-model="paymentQuery.keyword" placeholder="支付单号" clearable style="width: 180px" />
          <el-select v-model="paymentQuery.status" placeholder="全部状态" clearable style="width: 140px">
            <el-option v-for="(label, key) in paymentStatusLabels" :key="key" :label="label" :value="key" />
          </el-select>
          <el-select v-model="paymentQuery.channel" placeholder="全部渠道" clearable style="width: 140px">
            <el-option v-for="(label, key) in channelLabels" :key="key" :label="label" :value="key" />
          </el-select>
          <el-button type="primary" @click="paymentQuery.page = 1; loadPayments()">查询</el-button>
          <el-button v-if="canWrite" @click="openRefundDialog()">申请退款</el-button>
        </div>

        <el-table :data="paymentRows" border stripe>
          <el-table-column prop="paymentNo" label="支付单号" width="140" />
          <el-table-column prop="orderNo" label="订单号" width="130" />
          <el-table-column label="渠道" width="110">
            <template #default="{ row }">{{ channelLabels[row.channel] || row.channel }}</template>
          </el-table-column>
          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              <el-tag :type="tagType(row.status)">{{ paymentStatusLabels[row.status] || row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="金额" width="110">
            <template #default="{ row }">{{ money(row.amount) }}</template>
          </el-table-column>
          <el-table-column label="已退" width="110">
            <template #default="{ row }">{{ money(row.refundedAmount) }}</template>
          </el-table-column>
          <el-table-column prop="channelTradeNo" label="渠道流水号" min-width="160" />
          <el-table-column prop="paidAt" label="支付时间" width="180" />
        </el-table>

        <el-pagination
          v-model:current-page="paymentQuery.page"
          v-model:page-size="paymentQuery.pageSize"
          :total="paymentTotal"
          layout="total, prev, pager, next, sizes"
          @current-change="loadPayments"
          @size-change="paymentQuery.page = 1; loadPayments()"
        />
      </el-tab-pane>

      <!-- 退款 -->
      <el-tab-pane label="退款" name="refunds">
        <div class="toolbar">
          <el-select v-model="refundQuery.status" placeholder="全部状态" clearable style="width: 140px">
            <el-option v-for="(label, key) in refundStatusLabels" :key="key" :label="label" :value="key" />
          </el-select>
          <el-button type="primary" @click="refundQuery.page = 1; loadRefunds()">查询</el-button>
        </div>

        <el-table :data="refundRows" border stripe>
          <el-table-column prop="refundNo" label="退款单号" width="140" />
          <el-table-column prop="orderId" label="订单" min-width="150" />
          <el-table-column label="金额" width="110">
            <template #default="{ row }">{{ money(row.amount) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              <el-tag :type="tagType(row.status)">{{ refundStatusLabels[row.status] || row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="reason" label="原因" min-width="180" />
          <el-table-column prop="reviewedBy" label="审核人" width="120" />
          <el-table-column prop="createdAt" label="申请时间" width="180" />
          <el-table-column label="操作" width="180" fixed="right">
            <template #default="{ row }">
              <template v-if="canManage && row.status === 'PENDING'">
                <el-button link type="success" @click="handleApproveRefund(row)">通过</el-button>
                <el-button link type="danger" @click="handleRejectRefund(row)">驳回</el-button>
              </template>
              <span v-else-if="row.rejectReason">{{ row.rejectReason }}</span>
            </template>
          </el-table-column>
        </el-table>

        <el-pagination
          v-model:current-page="refundQuery.page"
          v-model:page-size="refundQuery.pageSize"
          :total="refundTotal"
          layout="total, prev, pager, next, sizes"
          @current-change="loadRefunds"
          @size-change="refundQuery.page = 1; loadRefunds()"
        />
      </el-tab-pane>

      <!-- 对账 -->
      <el-tab-pane label="对账" name="reconciliations">
        <div class="toolbar">
          <el-select v-model="reconRunForm.channel" style="width: 140px">
            <el-option v-for="(label, key) in channelLabels" :key="key" :label="label" :value="key" />
          </el-select>
          <el-date-picker v-model="reconRunForm.reconcileDate" type="date" value-format="YYYY-MM-DD" style="width: 150px" />
          <el-button v-if="canManage" type="primary" @click="handleRunReconciliation">执行对账</el-button>
          <el-select v-model="reconQuery.status" placeholder="全部状态" clearable style="width: 140px">
            <el-option v-for="(label, key) in reconStatusLabels" :key="key" :label="label" :value="key" />
          </el-select>
          <el-button @click="reconQuery.page = 1; loadReconciliations()">查询</el-button>
        </div>

        <el-table :data="reconRows" border stripe>
          <el-table-column prop="batchNo" label="批次号" width="140" />
          <el-table-column label="渠道" width="110">
            <template #default="{ row }">{{ channelLabels[row.channel] || row.channel }}</template>
          </el-table-column>
          <el-table-column prop="reconcileDate" label="账期" width="130" />
          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              <el-tag :type="tagType(row.status)">{{ reconStatusLabels[row.status] || row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="localCount" label="本地笔数" width="100" />
          <el-table-column prop="channelCount" label="渠道笔数" width="100" />
          <el-table-column label="本地金额" width="120">
            <template #default="{ row }">{{ money(row.localAmount) }}</template>
          </el-table-column>
          <el-table-column label="渠道金额" width="120">
            <template #default="{ row }">{{ money(row.channelAmount) }}</template>
          </el-table-column>
          <el-table-column prop="diffCount" label="差异笔数" width="100" />
          <el-table-column label="操作" width="120" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openReconDetail(row)">差异明细</el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-pagination
          v-model:current-page="reconQuery.page"
          v-model:page-size="reconQuery.pageSize"
          :total="reconTotal"
          layout="total, prev, pager, next, sizes"
          @current-change="loadReconciliations"
          @size-change="reconQuery.page = 1; loadReconciliations()"
        />
      </el-tab-pane>

      <!-- 发票 -->
      <el-tab-pane label="发票" name="invoices">
        <div class="toolbar">
          <el-select v-model="invoiceQuery.status" placeholder="全部状态" clearable style="width: 140px">
            <el-option v-for="(label, key) in invoiceStatusLabels" :key="key" :label="label" :value="key" />
          </el-select>
          <el-button type="primary" @click="invoiceQuery.page = 1; loadInvoices()">查询</el-button>
          <el-button v-if="canWrite" @click="openInvoiceDialog()">申请开票</el-button>
        </div>

        <el-table :data="invoiceRows" border stripe>
          <el-table-column prop="invoiceNo" label="发票号" width="140" />
          <el-table-column prop="orderNo" label="订单号" width="130" />
          <el-table-column prop="title" label="抬头" min-width="180" />
          <el-table-column label="金额" width="110">
            <template #default="{ row }">{{ money(row.amount) }}</template>
          </el-table-column>
          <el-table-column prop="invoiceType" label="类型" width="100" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="tagType(row.status)">{{ invoiceStatusLabels[row.status] || row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="appliedBy" label="申请人" width="120" />
          <el-table-column prop="issuedAt" label="开票时间" width="180" />
          <el-table-column label="操作" width="160" fixed="right">
            <template #default="{ row }">
              <el-button v-if="canManage && row.status === 'APPLIED'" link type="success" @click="handleIssueInvoice(row)">开具</el-button>
              <el-button v-if="canManage && row.status === 'ISSUED'" link type="danger" @click="handleVoidInvoice(row)">红冲</el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-pagination
          v-model:current-page="invoiceQuery.page"
          v-model:page-size="invoiceQuery.pageSize"
          :total="invoiceTotal"
          layout="total, prev, pager, next, sizes"
          @current-change="loadInvoices"
          @size-change="invoiceQuery.page = 1; loadInvoices()"
        />
      </el-tab-pane>
    </el-tabs>

    <!-- 新建订单 -->
    <el-dialog v-model="createDialogVisible" title="新建订单" width="720px">
      <el-form label-width="96px">
        <el-form-item label="客户" required>
          <el-select
            v-model="createForm.customerId"
            filterable
            remote
            :remote-method="searchCustomers"
            placeholder="按姓名或手机号搜索"
            style="width: 100%"
          >
            <el-option
              v-for="customer in customerOptions"
              :key="customer.id"
              :label="`${customer.name || customer.customerNo}（${customer.customerNo}）`"
              :value="customer.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="订单类型" required>
          <el-select v-model="createForm.orderType" style="width: 240px">
            <el-option label="陪诊服务" value="escort" />
            <el-option label="上门服务" value="home_service" />
            <el-option label="会员套餐" value="package" />
            <el-option label="储值充值" value="recharge" />
            <el-option label="其他" value="other" />
          </el-select>
        </el-form-item>
        <el-form-item label="优惠金额">
          <el-input v-model="createForm.discountAmount" placeholder="留空表示无优惠" style="width: 240px" />
        </el-form-item>

        <el-form-item label="订单明细" required>
          <div class="item-lines">
            <div v-for="(item, index) in createForm.items" :key="index" class="item-line">
              <el-input v-model="item.itemCode" placeholder="编号" style="width: 120px" />
              <el-input v-model="item.itemName" placeholder="名称" style="width: 200px" />
              <el-input v-model="item.quantity" placeholder="数量" style="width: 90px" />
              <el-input v-model="item.unitPrice" placeholder="单价" style="width: 110px" />
              <el-button link type="danger" @click="removeItemLine(index)">删除</el-button>
            </div>
            <el-button link type="primary" @click="addItemLine">+ 添加明细</el-button>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>

    <!-- 发起支付 -->
    <el-dialog v-model="payDialogVisible" title="发起支付" width="420px">
      <el-form label-width="80px">
        <el-form-item label="渠道">
          <el-select v-model="payForm.channel" style="width: 100%">
            <el-option v-for="(label, key) in channelLabels" :key="key" :label="label" :value="key" />
          </el-select>
        </el-form-item>
        <el-form-item label="金额" required>
          <el-input v-model="payForm.amount" placeholder="不超过订单未付余额" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="payDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitPay">确认</el-button>
      </template>
    </el-dialog>

    <!-- 订单详情 -->
    <el-drawer v-model="detailVisible" title="订单详情" size="640px">
      <template v-if="detail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="订单号">{{ detail.summary.orderNo }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="tagType(detail.summary.status)">
              {{ orderStatusLabels[detail.summary.status] || detail.summary.status }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="总额">{{ money(detail.summary.totalAmount) }}</el-descriptions-item>
          <el-descriptions-item label="优惠">{{ money(detail.summary.discountAmount) }}</el-descriptions-item>
          <el-descriptions-item label="应付">{{ money(detail.summary.payableAmount) }}</el-descriptions-item>
          <el-descriptions-item label="实付">{{ money(detail.summary.paidAmount) }}</el-descriptions-item>
          <el-descriptions-item label="已退">{{ money(detail.summary.refundedAmount) }}</el-descriptions-item>
          <el-descriptions-item label="取消原因">{{ detail.cancelReason || '—' }}</el-descriptions-item>
        </el-descriptions>

        <h4>订单明细</h4>
        <el-table :data="detail.items" border size="small">
          <el-table-column prop="itemCode" label="编号" width="120" />
          <el-table-column prop="itemName" label="名称" min-width="160" />
          <el-table-column prop="quantity" label="数量" width="90" />
          <el-table-column label="单价" width="100">
            <template #default="{ row }">{{ money(row.unitPrice) }}</template>
          </el-table-column>
          <el-table-column label="金额" width="100">
            <template #default="{ row }">{{ money(row.amount) }}</template>
          </el-table-column>
        </el-table>

        <h4>支付单</h4>
        <el-table :data="detail.payments" border size="small">
          <el-table-column prop="paymentNo" label="支付单号" width="140" />
          <el-table-column label="渠道" width="100">
            <template #default="{ row }">{{ channelLabels[row.channel] || row.channel }}</template>
          </el-table-column>
          <el-table-column label="金额" width="100">
            <template #default="{ row }">{{ money(row.amount) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              <el-tag :type="tagType(row.status)">{{ paymentStatusLabels[row.status] || row.status }}</el-tag>
            </template>
          </el-table-column>
        </el-table>

        <h4>退款单</h4>
        <el-table :data="detail.refunds" border size="small">
          <el-table-column prop="refundNo" label="退款单号" width="140" />
          <el-table-column label="金额" width="100">
            <template #default="{ row }">{{ money(row.amount) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              <el-tag :type="tagType(row.status)">{{ refundStatusLabels[row.status] || row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="reason" label="原因" min-width="140" />
        </el-table>
      </template>
    </el-drawer>

    <!-- 申请退款 -->
    <el-dialog v-model="refundDialogVisible" title="申请退款" width="420px">
      <el-form label-width="80px">
        <el-form-item label="支付单" required>
          <el-input v-model="refundForm.paymentId" placeholder="支付单 UUID" />
        </el-form-item>
        <el-form-item label="金额" required>
          <el-input v-model="refundForm.amount" placeholder="不超过可退余额" />
        </el-form-item>
        <el-form-item label="原因" required>
          <el-input v-model="refundForm.reason" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="refundDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitRefund">提交</el-button>
      </template>
    </el-dialog>

    <!-- 对账差异 -->
    <el-drawer v-model="reconDetailVisible" title="对账差异明细" size="640px">
      <template v-if="reconDetail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="批次号">{{ reconDetail.batch.batchNo }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="tagType(reconDetail.batch.status)">
              {{ reconStatusLabels[reconDetail.batch.status] || reconDetail.batch.status }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="本地金额">{{ money(reconDetail.batch.localAmount) }}</el-descriptions-item>
          <el-descriptions-item label="渠道金额">{{ money(reconDetail.batch.channelAmount) }}</el-descriptions-item>
        </el-descriptions>
        <el-table :data="reconDetail.diffs" border size="small" empty-text="账实相符，无差异">
          <el-table-column label="差异类型" width="120">
            <template #default="{ row }">{{ diffTypeLabels[row.diffType] || row.diffType }}</template>
          </el-table-column>
          <el-table-column prop="paymentNo" label="支付单号" width="140" />
          <el-table-column prop="channelTradeNo" label="渠道流水号" width="160" />
          <el-table-column label="本地金额" width="110">
            <template #default="{ row }">{{ money(row.localAmount) }}</template>
          </el-table-column>
          <el-table-column label="渠道金额" width="110">
            <template #default="{ row }">{{ money(row.channelAmount) }}</template>
          </el-table-column>
        </el-table>
      </template>
    </el-drawer>

    <!-- 申请开票 -->
    <el-dialog v-model="invoiceDialogVisible" title="申请开票" width="460px">
      <el-form label-width="90px">
        <el-form-item label="订单 ID" required>
          <el-input v-model="invoiceForm.orderId" placeholder="订单 UUID" />
        </el-form-item>
        <el-form-item label="抬头" required>
          <el-input v-model="invoiceForm.title" />
        </el-form-item>
        <el-form-item label="税号">
          <el-input v-model="invoiceForm.taxNo" />
        </el-form-item>
        <el-form-item label="金额" required>
          <el-input v-model="invoiceForm.amount" placeholder="不超过实付−已退" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="invoiceForm.invoiceType" style="width: 100%">
            <el-option label="普通发票" value="general" />
            <el-option label="专用发票" value="special" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="invoiceDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitInvoice">提交</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.order-page {
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

.item-lines {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.item-line {
  display: flex;
  align-items: center;
  gap: 8px;
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
