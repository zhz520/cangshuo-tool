<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElButton, ElInput, ElOption, ElPagination, ElSelect, ElTable, ElTableColumn } from 'element-plus'
import AdminPageHeader from '@/components/AdminPageHeader.vue'
import AdminFilterBar from '@/components/AdminFilterBar.vue'
import AdminDataPanel from '@/components/AdminDataPanel.vue'
import AdminDetailDrawer from '@/components/AdminDetailDrawer.vue'
import AdminRecordCards from '@/components/AdminRecordCards.vue'
import AdminStatusTag from '@/components/AdminStatusTag.vue'
import { useAdminRequest } from '@/composables/useAdminRequest'
import { ApiRequestError } from '@/api/client'
import { listAdminOperationLogs, type AdminOperationLog } from '@/api/logs'

const MODULES = ['ADMIN', 'TOOL', 'CATEGORY', 'RECOMMENDATION', 'USER', 'ANNOUNCEMENT', 'FEEDBACK'] as const
const RESULTS = ['SUCCESS', 'FAILED', 'UNKNOWN'] as const
const RESULT_TYPES: Record<string, 'success' | 'danger' | 'info'> = {
  SUCCESS: 'success', FAILED: 'danger', UNKNOWN: 'info' }

const entries = ref<AdminOperationLog[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const moduleFilter = ref<string | undefined>(undefined)
const resultFilter = ref<string | undefined>(undefined)
const keyword = ref('')
const { loading, errorMessage, traceId, run } = useAdminRequest(messageFor)
const detailVisible = ref(false)
const detail = ref<AdminOperationLog | null>(null)
const RESULT_LABELS: Record<string, string> = { SUCCESS: '成功', FAILED: '失败', UNKNOWN: '未知' }
const MODULE_LABELS: Record<string, string> = { ADMIN: '管理员', TOOL: '工具', CATEGORY: '分类', RECOMMENDATION: '推荐位', USER: '用户', ANNOUNCEMENT: '公告', FEEDBACK: '反馈' }
function openDetail(entry: AdminOperationLog): void { detail.value = entry; detailVisible.value = true }
const asLog = (row: unknown): AdminOperationLog => row as AdminOperationLog
function resetFilters(): void { keyword.value = ''; moduleFilter.value = undefined; resultFilter.value = undefined; search() }

function messageFor(error: unknown): string {
  if (error instanceof ApiRequestError) {
    switch (error.code) {
      case 10001: return '请求参数不合法，请检查筛选条件。'
      case 10005: return '当前账号没有管理权限。'
      case 10002: return '登录状态已失效，请重新登录。'
      default: return error.message
    }
  }
  return '暂时无法连接平台服务，请稍后重试。'
}

async function load(): Promise<void> {
  await run(() => listAdminOperationLogs({ page: page.value, pageSize: pageSize.value,
    module: moduleFilter.value, result: resultFilter.value, keyword: keyword.value.trim() || undefined }), result => {
    entries.value = result.data.records
    total.value = result.data.total
  })
}

function search(): void {
  page.value = 1
  void load()
}

onMounted(() => { void load() })
</script>

<template>
  <section class="admin-page">
    <AdminPageHeader title="操作日志" description="查看管理员操作记录与请求结果。">
      <template #actions>
        <ElButton :loading="loading" @click="load">刷新</ElButton>
      </template>
    </AdminPageHeader>
    <AdminFilterBar @search="search" @reset="resetFilters">
      <ElInput v-model="keyword" aria-label="搜索日志" placeholder="搜索操作或请求路径" clearable style="width: 300px" @clear="search" />
      <ElSelect v-model="moduleFilter" aria-label="日志模块" placeholder="全部模块" clearable style="width: 170px" @change="search">
        <ElOption v-for="module in MODULES" :key="module" :label="MODULE_LABELS[module]" :value="module" />
      </ElSelect>
      <ElSelect v-model="resultFilter" aria-label="日志结果" placeholder="全部结果" clearable style="width: 150px" @change="search">
        <ElOption v-for="result in RESULTS" :key="result" :label="RESULT_LABELS[result]" :value="result" />
      </ElSelect>
    </AdminFilterBar>
    <AdminDataPanel :loading="loading" :has-content="entries.length > 0" :error="errorMessage" :trace-id="traceId" empty-title="暂无操作日志" empty-description="调整筛选条件后重试。" @retry="load">
      <ElTable :data="entries" class="admin-table admin-desktop-table" row-key="id">
        <ElTableColumn label="操作 / 请求" min-width="280">
          <template #default="scope">
            <strong class="admin-record-title">{{ scope.row.operation }}</strong>
            <div class="admin-record-description admin-cell-description">{{ scope.row.requestMethod }} {{ scope.row.requestUri }}</div>
            <div class="admin-record-subtitle">#{{ scope.row.id }}</div>
          </template>
        </ElTableColumn>
        <ElTableColumn label="管理员 / 来源" min-width="170">
          <template #default="scope">{{ scope.row.adminUsername ?? '已删除' }}<div class="admin-record-subtitle">{{ scope.row.ip }}</div>
          </template>
        </ElTableColumn>
        <ElTableColumn label="模块" width="100">
          <template #default="scope">{{ MODULE_LABELS[scope.row.module] ?? scope.row.module }}</template>
        </ElTableColumn>
        <ElTableColumn label="结果" width="100">
          <template #default="scope">
            <AdminStatusTag :tone="RESULT_TYPES[scope.row.result] ?? 'info'" :label="RESULT_LABELS[scope.row.result] ?? scope.row.result" />
          </template>
        </ElTableColumn>
        <ElTableColumn label="时间" min-width="190">
          <template #default="scope">
            <span class="admin-record-subtitle">{{ scope.row.createdAt }}</span>
          </template>
        </ElTableColumn>
        <ElTableColumn label="操作" width="90" fixed="right">
          <template #default="scope">
            <ElButton size="small" @click="openDetail(asLog(scope.row))">详情</ElButton>
          </template>
        </ElTableColumn>
      </ElTable>
      <AdminRecordCards :records="entries" :record-key="record => record.id">
        <template #title="{ record }">
          <strong class="admin-record-title">{{ record.operation }}</strong>
          <div class="admin-record-subtitle">#{{ record.id }} · {{ MODULE_LABELS[record.module] ?? record.module }}</div>
        </template>
        <template #status="{ record }">
          <AdminStatusTag :tone="RESULT_TYPES[record.result] ?? 'info'" :label="RESULT_LABELS[record.result] ?? record.result" />
        </template>
        <template #description="{ record }">{{ record.requestMethod }} {{ record.requestUri }}</template>
        <template #meta="{ record }">{{ record.adminUsername ?? '已删除' }} · {{ record.ip }}<br>{{ record.createdAt }}</template>
        <template #actions="{ record }">
          <ElButton size="small" @click="openDetail(record)">查看详情</ElButton>
        </template>
      </AdminRecordCards>
    </AdminDataPanel>
    <div class="admin-list-footer">
      <span class="admin-result-count">共 {{ total }} 条操作记录</span>
      <ElPagination class="admin-pagination" layout="prev, pager, next" :pager-count="5" :total="total" :current-page="page" :page-size="pageSize" @current-change="(value: number) => { page = value; void load() }" />
    </div>
    <AdminDetailDrawer v-model="detailVisible" title="日志详情">
      <template v-if="detail">
        <AdminStatusTag :tone="RESULT_TYPES[detail.result] ?? 'info'" :label="RESULT_LABELS[detail.result] ?? detail.result" />
        <dl class="admin-detail-fields">
          <div>
            <dt>记录 ID</dt>
            <dd>{{ detail.id }}</dd>
          </div>
          <div>
            <dt>管理员</dt>
            <dd>{{ detail.adminUsername ?? '已删除' }} · ID {{ detail.adminId ?? '—' }}</dd>
          </div>
          <div>
            <dt>模块</dt>
            <dd>{{ MODULE_LABELS[detail.module] ?? detail.module }} · {{ detail.module }}</dd>
          </div>
          <div>
            <dt>操作</dt>
            <dd>{{ detail.operation }}</dd>
          </div>
          <div>
            <dt>请求方法</dt>
            <dd>{{ detail.requestMethod }}</dd>
          </div>
          <div>
            <dt>请求路径</dt>
            <dd>{{ detail.requestUri }}</dd>
          </div>
          <div>
            <dt>来源地址</dt>
            <dd>{{ detail.ip }}</dd>
          </div>
          <div>
            <dt>记录时间</dt>
            <dd>{{ detail.createdAt }}</dd>
          </div>
        </dl>
      </template>
      <template #footer>
        <ElButton @click="detailVisible = false">关闭详情</ElButton>
      </template>
    </AdminDetailDrawer>
  </section>
</template>
