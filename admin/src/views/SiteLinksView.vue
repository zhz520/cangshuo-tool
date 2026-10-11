<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElButton, ElInput, ElMessage, ElMessageBox, ElPagination, ElTable, ElTableColumn } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'
import AdminPageHeader from '@/components/AdminPageHeader.vue'
import AdminFilterBar from '@/components/AdminFilterBar.vue'
import AdminDataPanel from '@/components/AdminDataPanel.vue'
import AdminStatusTag from '@/components/AdminStatusTag.vue'
import { listAdminSiteLinks, updateAdminSiteLink, type AdminSiteLink } from '@/api/siteLinks'
import { ApiRequestError } from '@/api/client'

const STATUS_LABELS: Record<string, string> = { PENDING: '待审核', APPROVED: '已通过', REJECTED: '已拒绝' }
const STATUS_TONES: Record<string, 'primary' | 'success' | 'warning' | 'danger' | 'info'> =
  { PENDING: 'warning', APPROVED: 'success', REJECTED: 'danger' }
const statusTabs: { value: string | undefined; label: string }[] = [
  { value: undefined, label: '全部' },
  { value: 'PENDING', label: '待审核' },
  { value: 'APPROVED', label: '已通过' },
  { value: 'REJECTED', label: '已拒绝' },
]

const entries = ref<AdminSiteLink[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const keyword = ref('')
const appliedKeyword = ref<string | undefined>(undefined)
const statusFilter = ref<string | undefined>(undefined)
const loading = ref(false)
const saving = ref(false)
const loaded = ref(false)
const errorMessage = ref('')
const errorTraceId = ref('')
const hasFilters = computed(() => Boolean(appliedKeyword.value || statusFilter.value))

async function load(): Promise<void> {
  if (loading.value) return
  loading.value = true
  errorMessage.value = ''
  errorTraceId.value = ''
  try {
    const result = await listAdminSiteLinks({
      page: page.value, pageSize: pageSize.value,
      keyword: appliedKeyword.value, status: statusFilter.value,
    })
    entries.value = result.data.records
    total.value = result.data.total
    loaded.value = true
  } catch (error) {
    errorMessage.value = error instanceof ApiRequestError ? error.message : '加载失败，请稍后重试。'
    errorTraceId.value = error instanceof ApiRequestError ? (error.traceId ?? '') : ''
  } finally {
    loading.value = false
  }
}

function search(): void {
  appliedKeyword.value = keyword.value.trim() || undefined
  page.value = 1
  void load()
}

function reset(): void {
  keyword.value = ''
  appliedKeyword.value = undefined
  statusFilter.value = undefined
  page.value = 1
  void load()
}

function selectStatus(value: string | undefined): void {
  statusFilter.value = value
  page.value = 1
  void load()
}

function changePage(value: number): void {
  page.value = value
  void load()
}

async function review(row: AdminSiteLink, status: 'APPROVED' | 'REJECTED'): Promise<void> {
  if (saving.value) return
  if (status === 'REJECTED') {
    try {
      await ElMessageBox.confirm(`确定拒绝「${row.title}」吗？拒绝后不会展示给用户。`, '拒绝投稿', {
        type: 'warning', confirmButtonText: '拒绝', cancelButtonText: '取消',
      })
    } catch {
      return
    }
  }
  saving.value = true
  try {
    await updateAdminSiteLink(row.id, status)
    ElMessage.success(status === 'APPROVED' ? '已通过，会展示在 App 网站导航中' : '已拒绝')
    await load()
  } catch (error) {
    ElMessage.error(error instanceof ApiRequestError ? error.message : '操作失败，请稍后重试。')
  } finally {
    saving.value = false
  }
}

function displayTime(value: string): string {
  const date = new Date(value)
  if (!Number.isFinite(date.getTime())) return value
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false,
  }).format(date)
}

onMounted(() => {
  void load()
})
</script>

<template>
  <section>
    <AdminPageHeader title="网站导航" description="审核用户投稿的网站，通过的条目会出现在 App 的网站导航中。">
      <template #actions>
        <ElButton :icon="Refresh" :loading="loading" :disabled="saving" @click="load">刷新</ElButton>
      </template>
    </AdminPageHeader>

    <div class="admin-status-tabs" role="group" aria-label="投稿状态筛选">
      <button v-for="tab in statusTabs" :key="tab.value ?? 'ALL'" type="button" class="admin-status-tab"
        :class="{ selected: statusFilter === tab.value }" :aria-pressed="statusFilter === tab.value"
        :disabled="saving" @click="selectStatus(tab.value)">{{ tab.label }}</button>
    </div>

    <AdminFilterBar :busy="saving" @search="search" @reset="reset">
      <ElInput v-model="keyword" aria-label="搜索投稿" placeholder="搜索名称、网址或说明"
        :prefix-icon="Search" maxlength="100" clearable :disabled="saving" @clear="search" />
    </AdminFilterBar>

    <AdminDataPanel :loading="loading" :has-content="entries.length > 0" :error="errorMessage"
      :trace-id="errorTraceId" :empty-title="hasFilters ? '没有匹配的投稿' : '暂无网站投稿'"
      :empty-description="hasFilters ? '调整搜索词或状态后重试。' : '用户在 App 中投稿的网站会显示在这里。'" @retry="load">
      <ElTable :data="entries" class="site-link-desktop-table" row-key="id">
        <ElTableColumn label="网站" min-width="260">
          <template #default="scope">
            <div class="site-link-title">{{ scope.row.title }}</div>
            <p class="site-link-url">{{ scope.row.url }}</p>
            <p v-if="scope.row.description" class="site-link-description">{{ scope.row.description }}</p>
          </template>
        </ElTableColumn>
        <ElTableColumn label="提交人" width="140">
          <template #default="scope">
            {{ scope.row.submittedBy === null ? '已注销账号' : `账号 #${scope.row.submittedBy}` }}
          </template>
        </ElTableColumn>
        <ElTableColumn label="状态" width="110">
          <template #default="scope">
            <AdminStatusTag :label="STATUS_LABELS[scope.row.status] ?? scope.row.status"
              :tone="STATUS_TONES[scope.row.status] ?? 'info'" />
          </template>
        </ElTableColumn>
        <ElTableColumn label="提交时间" width="165">
          <template #default="scope">
            <time :datetime="scope.row.createdAt" :title="scope.row.createdAt">{{ displayTime(scope.row.createdAt) }}</time>
          </template>
        </ElTableColumn>
        <ElTableColumn label="操作" width="170" fixed="right">
          <template #default="scope">
            <ElButton size="small" type="primary" :disabled="saving || scope.row.status === 'APPROVED'"
              @click="review(scope.row as AdminSiteLink, 'APPROVED')">通过</ElButton>
            <ElButton size="small" :disabled="saving || scope.row.status === 'REJECTED'"
              @click="review(scope.row as AdminSiteLink, 'REJECTED')">拒绝</ElButton>
          </template>
        </ElTableColumn>
      </ElTable>

      <div class="site-link-mobile-list" aria-label="网站投稿列表">
        <article v-for="entry in entries" :key="entry.id" class="site-link-mobile-card">
          <div class="site-link-mobile-heading">
            <strong>{{ entry.title }}</strong>
            <AdminStatusTag :label="STATUS_LABELS[entry.status] ?? entry.status"
              :tone="STATUS_TONES[entry.status] ?? 'info'" />
          </div>
          <p class="site-link-url">{{ entry.url }}</p>
          <p v-if="entry.description" class="site-link-description">{{ entry.description }}</p>
          <p class="site-link-mobile-meta">{{ displayTime(entry.createdAt) }}</p>
          <div class="site-link-mobile-actions">
            <ElButton size="small" type="primary" :disabled="saving || entry.status === 'APPROVED'"
              @click="review(entry, 'APPROVED')">通过</ElButton>
            <ElButton size="small" :disabled="saving || entry.status === 'REJECTED'"
              @click="review(entry, 'REJECTED')">拒绝</ElButton>
          </div>
        </article>
      </div>
    </AdminDataPanel>

    <div v-if="loaded" class="admin-list-footer">
      <span class="admin-result-count">{{ loading || errorMessage ? '上次结果' : '当前筛选' }} · 共 {{ total }} 条</span>
      <ElPagination layout="prev, pager, next" :total="total" :current-page="page" :page-size="pageSize"
        :pager-count="5" :disabled="saving" @current-change="changePage" />
    </div>
  </section>
</template>

<style scoped>
.site-link-title { font-weight: 500; color: var(--admin-text); }
.site-link-url { font-size: 12px; color: var(--admin-accent); overflow-wrap: anywhere; }
.site-link-description { font-size: 12px; color: var(--admin-muted); margin-top: 2px; }
.site-link-mobile-list { display: none; }

@media (max-width: 768px) {
  .site-link-desktop-table { display: none; }
  .site-link-mobile-list { display: grid; gap: 12px; }
  .site-link-mobile-card { border: 1px solid var(--admin-border); border-radius: 10px; padding: 14px; display: grid; gap: 6px; }
  .site-link-mobile-heading { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
  .site-link-mobile-meta { font-size: 12px; color: var(--admin-muted); }
  .site-link-mobile-actions { display: flex; gap: 8px; }
}
</style>
