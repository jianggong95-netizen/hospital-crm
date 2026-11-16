<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { fetchCustomers } from '@/api/customers'
import { fetchPatient360 } from '@/api/patient360'
import type { CustomerSummary } from '@/types/customer'
import type { Patient360DataSource, Patient360View, SectionStatus } from '@/types/patient360'

const route = useRoute()

const genderLabels: Record<string, string> = { unknown: '未知', male: '男', female: '女' }
const sourceLabels: Record<string, string> = {
  manual: '手工建档',
  outpatient: '门诊',
  inpatient: '住院',
  physical: '体检',
  online: '线上渠道',
  referral: '转介绍',
  activity: '活动'
}
const sectionLabels: Record<string, string> = {
  profile: '客户主数据',
  family: '家庭账户',
  his: 'HIS 诊疗',
  visits: '就诊记录',
  admissions: '住院记录',
  appointments: '预约',
  serviceOrders: '服务订单',
  followups: '随访',
  memberships: '会员权益',
  complaints: '投诉'
}
const statusLabels: Record<SectionStatus, string> = {
  LIVE: '实时',
  STALE: '已过期缓存',
  UNAVAILABLE: '暂不可用',
  NOT_LINKED: '未绑定',
  RESTRICTED: '无权限',
  NOT_FOUND: '无数据',
  NOT_REQUESTED: '未请求'
}

const customerLoading = ref(false)
const customerRows = ref<CustomerSummary[]>([])
const customerTotal = ref(0)
const customerPage = ref(1)
const customerPageSize = 10
const keyword = ref('')

const selectedId = ref<string>()
const loading = ref(false)
const view = ref<Patient360View>()
const visitPage = ref(1)
const visitPageSize = ref(10)
const admissionPage = ref(1)
const admissionPageSize = ref(10)

const hisStatus = computed<SectionStatus | undefined>(() => view.value?.his.dataStatus)
const hasHisData = computed(() => hisStatus.value === 'LIVE' || hisStatus.value === 'STALE')

async function searchCustomers() {
  customerLoading.value = true
  try {
    const result = await fetchCustomers({
      keyword: keyword.value || undefined,
      page: customerPage.value,
      pageSize: customerPageSize
    })
    customerRows.value = result.items
    customerTotal.value = result.total
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载客户列表失败')
  } finally {
    customerLoading.value = false
  }
}

function changeCustomerPage(page: number) {
  customerPage.value = page
  searchCustomers()
}

function resetPages() {
  visitPage.value = 1
  admissionPage.value = 1
}

async function selectCustomer(row: CustomerSummary) {
  if (selectedId.value === row.id && view.value) return
  selectedId.value = row.id
  view.value = undefined
  resetPages()
  await load(false)
}

async function load(refresh: boolean) {
  if (!selectedId.value) return
  loading.value = true
  try {
    view.value = await fetchPatient360(selectedId.value, {
      visitPage: visitPage.value,
      visitPageSize: visitPageSize.value,
      admissionPage: admissionPage.value,
      admissionPageSize: admissionPageSize.value,
      refresh
    })
    if (view.value.his.visits) {
      visitPage.value = view.value.his.visits.page
      visitPageSize.value = view.value.his.visits.pageSize
    }
    if (view.value.his.admissions) {
      admissionPage.value = view.value.his.admissions.page
      admissionPageSize.value = view.value.his.admissions.pageSize
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载患者 360 失败')
  } finally {
    loading.value = false
  }
}

function changeVisitPage(page: number) {
  visitPage.value = page
  load(false)
}

function changeAdmissionPage(page: number) {
  admissionPage.value = page
  load(false)
}

function refresh() {
  load(true)
}

function statusLabel(status?: SectionStatus) {
  return status ? statusLabels[status] ?? status : '—'
}

function statusBadge(status?: SectionStatus) {
  switch (status) {
    case 'LIVE':
      return 'badge-success'
    case 'STALE':
      return 'badge-warning'
    case 'UNAVAILABLE':
      return 'badge-danger'
    default:
      return 'badge-neutral'
  }
}

function sectionLabel(section: string) {
  return sectionLabels[section] ?? section
}

function formatDateTime(value?: string) {
  if (!value) return '—'
  return new Date(value).toLocaleString('zh-CN', { hour12: false })
}

function sourceLabel(code?: string) {
  return code ? sourceLabels[code] ?? code : '—'
}

function hasCached(source: Patient360DataSource) {
  return source.cached === true
}

onMounted(async () => {
  await searchCustomers()
  const queryId = route.query.customerId
  const initialId = typeof queryId === 'string' && queryId ? queryId : customerRows.value[0]?.id
  if (initialId) {
    selectedId.value = initialId
    await load(false)
  }
})

watch(
  () => route.query.customerId,
  async (value) => {
    if (typeof value !== 'string' || !value || value === selectedId.value) return
    selectedId.value = value
    view.value = undefined
    resetPages()
    await load(false)
  }
)
</script>

<template>
  <section class="patient360">
    <aside class="card search-panel">
      <div class="section-head">
        <div>
          <h3 class="section-title">客户检索</h3>
          <p class="section-subtitle">按姓名、客户号、手机号或证件号定位客户</p>
        </div>
      </div>
      <el-input
        v-model="keyword"
        placeholder="姓名 / 客户号 / 手机号 / 证件号"
        clearable
        @keyup.enter="searchCustomers"
      />
      <el-button class="search-button" type="primary" plain @click="searchCustomers">查询</el-button>

      <el-table
        v-loading="customerLoading"
        :data="customerRows"
        row-key="id"
        highlight-current-row
        class="customer-table"
        @row-click="selectCustomer"
      >
        <el-table-column prop="name" label="姓名" min-width="88" />
        <el-table-column label="客户号" min-width="110">
          <template #default="{ row }">
            <span class="cell-mono">{{ row.customerNo }}</span>
          </template>
        </el-table-column>
        <el-table-column label="手机号" min-width="112">
          <template #default="{ row }">
            <span class="cell-mono">{{ row.phoneMasked || '—' }}</span>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-if="customerTotal > customerPageSize"
        class="search-pagination"
        :current-page="customerPage"
        :page-size="customerPageSize"
        :total="customerTotal"
        layout="prev, pager, next"
        small
        @current-change="changeCustomerPage"
      />
    </aside>

    <div class="detail-panel">
      <el-empty v-if="!selectedId" description="从左侧选择客户查看患者 360 视图" />

      <div v-else v-loading="loading" class="detail-stack">
        <template v-if="view">
          <article class="card profile-card">
            <div class="profile-head">
              <div class="profile-identity">
                <div>
                  <h2>{{ view.customer.name }}</h2>
                  <span class="cell-mono">{{ view.customer.customerNo }}</span>
                </div>
                <div class="profile-tags">
                  <span class="badge" :class="view.customer.status === 'active' ? 'badge-success' : 'badge-danger'">
                    {{ view.customer.status === 'active' ? '启用' : '停用' }}
                  </span>
                  <el-tag v-for="tag in view.customer.tags" :key="tag.id" size="small" effect="plain">
                    {{ tag.tagName }}
                  </el-tag>
                </div>
              </div>
              <el-button type="primary" plain @click="refresh">刷新 360</el-button>
            </div>

            <el-descriptions :column="3" border class="profile-grid">
              <el-descriptions-item label="性别">
                {{ genderLabels[view.customer.gender] || view.customer.gender }}
              </el-descriptions-item>
              <el-descriptions-item label="出生日期">{{ view.customer.birthDate || '—' }}</el-descriptions-item>
              <el-descriptions-item label="来源">{{ sourceLabel(view.customer.sourceCode) }}</el-descriptions-item>
              <el-descriptions-item label="手机号">
                <span class="cell-mono">
                  {{ view.sensitiveVisible ? (view.customer.phone || '—') : (view.customer.phoneMasked || '—') }}
                </span>
              </el-descriptions-item>
              <el-descriptions-item label="证件号">
                <span class="cell-mono">
                  {{ view.sensitiveVisible ? (view.customer.idNo || '—') : (view.customer.idNoMasked || '—') }}
                </span>
              </el-descriptions-item>
              <el-descriptions-item label="建档时间">{{ formatDateTime(view.customer.createdAt) }}</el-descriptions-item>
            </el-descriptions>

            <el-alert
              v-if="!view.sensitiveVisible"
              class="section-alert"
              title="当前角色无敏感信息查看权限，手机号与证件号仅展示脱敏值"
              type="info"
              :closable="false"
              show-icon
            />
            <el-alert
              v-if="view.unavailableSections.length"
              class="section-alert"
              :title="`以下聚合分区尚未在本版本交付：${view.unavailableSections.map(sectionLabel).join('、')}`"
              type="info"
              :closable="false"
              show-icon
            />
          </article>

          <el-alert
            v-if="hisStatus === 'UNAVAILABLE'"
            class="section-alert"
            title="HIS 暂不可用：未展示任何诊疗数据，也不会以推测值填充。请稍后重试或联系 HIS 管理员。"
            type="error"
            :closable="false"
            show-icon
          />
          <el-alert
            v-else-if="hisStatus === 'STALE'"
            class="section-alert"
            title="HIS 暂不可用：当前展示的是有效期内缓存快照，数据已标记过期，请以 HIS 实时数据为准。"
            type="warning"
            :closable="false"
            show-icon
          />
          <el-alert
            v-else-if="hisStatus === 'NOT_LINKED'"
            class="section-alert"
            title="该客户尚未绑定 HIS 患者主索引，暂无可展示的诊疗数据。请先在 HIS 集成中完成人工核验绑定。"
            type="info"
            :closable="false"
            show-icon
          />
          <el-alert
            v-else-if="hisStatus === 'RESTRICTED'"
            class="section-alert"
            title="当前角色无 HIS 患者数据查看权限，诊疗分区已隐藏。"
            type="info"
            :closable="false"
            show-icon
          />

          <article class="card section-card">
            <div class="section-head">
              <div>
                <h3 class="section-title">HIS 诊疗摘要</h3>
                <p class="section-subtitle">
                  来源 {{ view.his.sourceSystem || 'HIS' }} · 获取于 {{ formatDateTime(view.his.fetchedAt) }}
                  <template v-if="view.his.validUntil"> · 有效期至 {{ formatDateTime(view.his.validUntil) }}</template>
                </p>
              </div>
              <div class="status-group">
                <el-tag v-if="view.his.cached" size="small" type="info" effect="plain">命中缓存</el-tag>
                <span class="badge" :class="statusBadge(hisStatus)">{{ statusLabel(hisStatus) }}</span>
              </div>
            </div>

            <template v-if="hasHisData">
              <el-descriptions :column="3" border>
                <el-descriptions-item label="患者姓名">{{ view.his.patientNameMasked || '—' }}</el-descriptions-item>
                <el-descriptions-item label="病案号">{{ view.his.mrn || '—' }}</el-descriptions-item>
                <el-descriptions-item label="患者主索引">
                  <span class="cell-mono">{{ view.his.externalPatientIdMasked || '—' }}</span>
                </el-descriptions-item>
                <el-descriptions-item label="性别">
                  {{ view.his.gender ? genderLabels[view.his.gender] || view.his.gender : '—' }}
                </el-descriptions-item>
                <el-descriptions-item label="出生日期">{{ view.his.birthDate || '—' }}</el-descriptions-item>
                <el-descriptions-item label="手机号">
                  <span class="cell-mono">{{ view.his.mobileMasked || '—' }}</span>
                </el-descriptions-item>
              </el-descriptions>

              <div v-if="view.his.requiresManualVerification" class="verify-tip">
                该患者匹配结果需人工核验；临床数据仅供授权人员查看，不随家庭关系自动共享。
              </div>

              <h4 class="sub-title">就诊记录</h4>
              <el-table :data="view.his.visits?.items ?? []" row-key="visitId" stripe>
                <el-table-column prop="visitId" label="就诊号" min-width="130" />
                <el-table-column label="就诊时间" min-width="170">
                  <template #default="{ row }">{{ formatDateTime(row.visitTime) }}</template>
                </el-table-column>
                <el-table-column prop="department" label="科室" min-width="120" />
                <el-table-column prop="doctor" label="医生" min-width="100" />
                <el-table-column prop="diagnosis" label="诊断" min-width="180" show-overflow-tooltip />
              </el-table>
              <el-pagination
                v-if="(view.his.visits?.total ?? 0) > visitPageSize"
                class="table-pagination"
                :current-page="visitPage"
                :page-size="visitPageSize"
                :total="view.his.visits?.total ?? 0"
                layout="total, prev, pager, next"
                @current-change="changeVisitPage"
              />

              <h4 class="sub-title">住院记录</h4>
              <el-table :data="view.his.admissions?.items ?? []" row-key="admissionId" stripe>
                <el-table-column prop="admissionId" label="住院号" min-width="130" />
                <el-table-column label="入院时间" min-width="170">
                  <template #default="{ row }">{{ formatDateTime(row.admittedAt) }}</template>
                </el-table-column>
                <el-table-column label="出院时间" min-width="170">
                  <template #default="{ row }">{{ formatDateTime(row.dischargedAt) }}</template>
                </el-table-column>
                <el-table-column prop="ward" label="病区" min-width="110" />
                <el-table-column prop="diagnosis" label="诊断" min-width="180" show-overflow-tooltip />
              </el-table>
              <el-pagination
                v-if="(view.his.admissions?.total ?? 0) > admissionPageSize"
                class="table-pagination"
                :current-page="admissionPage"
                :page-size="admissionPageSize"
                :total="view.his.admissions?.total ?? 0"
                layout="total, prev, pager, next"
                @current-change="changeAdmissionPage"
              />
            </template>
            <el-empty v-else :description="view.his.message || '暂无可展示的 HIS 诊疗数据'" />
          </article>

          <article class="card section-card">
            <div class="section-head">
              <div>
                <h3 class="section-title">家庭账户</h3>
                <p class="section-subtitle">家庭关系仅表达归属，不授予病历或临床数据访问权限</p>
              </div>
              <span class="badge" :class="statusBadge(view.family.dataStatus)">
                {{ statusLabel(view.family.dataStatus) }}
              </span>
            </div>

            <el-descriptions v-if="view.family.family" :column="3" border>
              <el-descriptions-item label="家庭编号">
                <span class="cell-mono">{{ view.family.family.familyNo }}</span>
              </el-descriptions-item>
              <el-descriptions-item label="家庭名称">{{ view.family.family.familyName }}</el-descriptions-item>
              <el-descriptions-item label="家庭状态">
                {{ view.family.family.familyStatus === 'active' ? '启用' : '停用' }}
              </el-descriptions-item>
              <el-descriptions-item label="家庭关系">{{ view.family.family.relationshipLabel }}</el-descriptions-item>
              <el-descriptions-item label="家庭管理员">
                {{ view.family.family.admin ? '是' : '否' }}
              </el-descriptions-item>
              <el-descriptions-item label="有效成员">{{ view.family.family.memberCount }}</el-descriptions-item>
            </el-descriptions>
            <el-empty v-else :description="view.family.message || '暂无家庭账户信息'" />
          </article>

          <article class="card section-card">
            <div class="section-head">
              <div>
                <h3 class="section-title">数据来源与更新时间</h3>
                <p class="section-subtitle">
                  每个分区独立标注来源、状态、获取时间与有效期；HIS 不可用时不会补齐或推测数据
                </p>
              </div>
            </div>
            <el-table :data="view.dataSources" row-key="section" stripe>
              <el-table-column label="分区" min-width="120">
                <template #default="{ row }">{{ sectionLabel(row.section) }}</template>
              </el-table-column>
              <el-table-column prop="sourceSystem" label="来源系统" min-width="100" />
              <el-table-column label="状态" min-width="120">
                <template #default="{ row }">
                  <span class="badge" :class="statusBadge(row.dataStatus)">{{ statusLabel(row.dataStatus) }}</span>
                </template>
              </el-table-column>
              <el-table-column label="获取时间" min-width="170">
                <template #default="{ row }">{{ formatDateTime(row.fetchedAt) }}</template>
              </el-table-column>
              <el-table-column label="有效期至" min-width="170">
                <template #default="{ row }">{{ formatDateTime(row.validUntil) }}</template>
              </el-table-column>
              <el-table-column label="缓存" width="80">
                <template #default="{ row }">{{ hasCached(row) ? '是' : '否' }}</template>
              </el-table-column>
              <el-table-column prop="message" label="说明" min-width="220" show-overflow-tooltip />
            </el-table>
          </article>
        </template>
      </div>
    </div>
  </section>
</template>

<style scoped lang="scss">
.patient360 {
  display: flex;
  align-items: flex-start;
  gap: 18px;
}

.search-panel {
  width: 328px;
  flex-shrink: 0;
  padding: 20px;
}

.search-button {
  width: 100%;
  margin-top: 10px;
}

.customer-table {
  margin-top: 14px;
  cursor: pointer;
}

.search-pagination {
  margin-top: 12px;
  justify-content: flex-end;
}

.detail-panel {
  flex: 1;
  min-width: 0;
}

.detail-stack {
  display: flex;
  flex-direction: column;
  gap: 18px;
  min-height: 220px;
}

.profile-card,
.section-card {
  padding: 22px 24px;
}

.profile-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--crm-border);
}

.profile-identity {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.profile-identity h2 {
  font-size: 22px;
  letter-spacing: 0.01em;
}

.profile-tags {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}

.profile-grid {
  margin-top: 18px;
}

.section-alert {
  margin-top: 12px;
}

.status-group {
  display: flex;
  align-items: center;
  gap: 8px;
}

.sub-title {
  margin: 22px 0 10px;
  font-size: 14px;
  font-weight: 700;
}

.verify-tip {
  margin-top: 12px;
  padding: 10px 14px;
  border-radius: 10px;
  background: var(--crm-warning-soft);
  color: var(--crm-warning);
  font-size: 12.5px;
}

@media (max-width: 1100px) {
  .patient360 {
    flex-direction: column;
  }

  .search-panel {
    width: 100%;
  }
}
</style>
