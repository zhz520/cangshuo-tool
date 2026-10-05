<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElAlert, ElButton, ElDialog, ElForm, ElFormItem, ElInput, ElMessage, ElOption, ElPagination,
  ElSelect, ElTable, ElTableColumn, ElTag } from 'element-plus'
import { ApiRequestError } from '@/api/client'
import { FEEDBACK_STATUSES, listAdminFeedback, updateAdminFeedback, type AdminFeedback } from '@/api/feedback'

const STATUS_LABELS: Record<string, string> = { PENDING: '待处理', PROCESSING: '处理中', RESOLVED: '已解决' }
const STATUS_TYPES: Record<string, 'warning' | 'primary' | 'success'> = {
  PENDING: 'warning', PROCESSING: 'primary', RESOLVED: 'success' }
const TYPE_LABELS: Record<string, string> = { BUG: '问题', SUGGESTION: '建议', OTHER: '其他' }

const entries = ref<AdminFeedback[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const keyword = ref('')
const statusFilter = ref<string | undefined>(undefined)
const loading = ref(false)
const errorMessage = ref<string | null>(null)
const dialogVisible = ref(false)
const saving = ref(false)
const dialogError = ref<string | null>(null)
const editing = ref<AdminFeedback | null>(null)
const form = reactive({ status: 'PENDING', reply: '' })
const dialogTitle = computed(() => editing.value ? `处理反馈 · #${editing.value.id}` : '处理反馈')
const asEntry = (row: unknown): AdminFeedback => row as AdminFeedback

function messageFor(error: unknown): string {
  if (error instanceof ApiRequestError) {
    switch (error.code) {
      case 10006: return '反馈不存在。'
      case 10001: return '请求参数不合法，请检查状态与回复内容。'
      case 10005: return '当前账号没有管理权限。'
      case 10002: return '登录状态已失效，请重新登录。'
      default: return error.message
    }
  }
  return '暂时无法连接平台服务，请稍后重试。'
}

async function load(): Promise<void> {
  if (loading.value) return
  loading.value = true
  errorMessage.value = null
  try {
    const result = await listAdminFeedback({ page: page.value, pageSize: pageSize.value,
      keyword: keyword.value.trim() || undefined, status: statusFilter.value })
    entries.value = result.data.records
    total.value = result.data.total
  } catch (error: unknown) {
    errorMessage.value = messageFor(error)
  } finally {
    loading.value = false
  }
}

function search(): void {
  page.value = 1
  void load()
}

function open(entry: AdminFeedback): void {
  editing.value = entry
  dialogError.value = null
  Object.assign(form, { status: entry.status, reply: entry.reply ?? '' })
  dialogVisible.value = true
}

async function save(): Promise<void> {
  if (!editing.value || saving.value) return
  dialogError.value = null
  if (!(FEEDBACK_STATUSES as readonly string[]).includes(form.status)) {
    dialogError.value = '状态不在允许范围内。'
    return
  }
  if (form.reply.trim().length > 2000) {
    dialogError.value = '回复最多 2000 字符。'
    return
  }
  saving.value = true
  try {
    await updateAdminFeedback(editing.value.id, form.status, form.reply.trim() || null)
    dialogVisible.value = false
    ElMessage.success('反馈已更新')
    await load()
  } catch (error: unknown) {
    dialogError.value = messageFor(error)
  } finally {
    saving.value = false
  }
}

onMounted(() => { void load() })
</script>

<template>
  <section>
    <div class="page-heading">
      <div>
        <p class="eyebrow">FEEDBACK</p>
        <h1>用户反馈</h1>
        <p class="page-description">按状态处理用户提交的问题与建议；回复一次后可继续更新状态，回复内容对提交者可见。</p>
      </div>
    </div>

    <ElAlert v-if="errorMessage" class="admin-error" type="error" :closable="false" :title="errorMessage" show-icon />

    <div class="admin-filters">
      <ElInput v-model="keyword" placeholder="搜索内容、联系方式或用户邮箱" clearable style="max-width: 280px"
        @keyup.enter="search" @clear="search" />
      <ElSelect v-model="statusFilter" placeholder="全部状态" clearable style="width: 150px" @change="search">
        <ElOption v-for="status in FEEDBACK_STATUSES" :key="status" :label="STATUS_LABELS[status]" :value="status" />
      </ElSelect>
      <ElButton @click="search">查询</ElButton>
    </div>

    <ElTable v-loading="loading" :data="entries" class="admin-table" row-key="id">
      <ElTableColumn prop="id" label="ID" width="80" />
      <ElTableColumn prop="userEmail" label="用户" min-width="200" />
      <ElTableColumn label="类型" width="90">
        <template #default="scope">{{ TYPE_LABELS[scope.row.type] ?? scope.row.type }}</template>
      </ElTableColumn>
      <ElTableColumn prop="content" label="内容" min-width="260" show-overflow-tooltip />
      <ElTableColumn label="状态" width="100">
        <template #default="scope"><ElTag :type="STATUS_TYPES[scope.row.status]">{{ STATUS_LABELS[scope.row.status] }}</ElTag></template>
      </ElTableColumn>
      <ElTableColumn label="回复" min-width="200" show-overflow-tooltip>
        <template #default="scope"><span class="muted">{{ scope.row.reply ?? '—' }}</span></template>
      </ElTableColumn>
      <ElTableColumn label="提交时间" min-width="180">
        <template #default="scope"><span class="muted">{{ scope.row.createdAt }}</span></template>
      </ElTableColumn>
      <ElTableColumn label="操作" width="110" fixed="right">
        <template #default="scope"><ElButton size="small" @click="open(asEntry(scope.row))">处理</ElButton></template>
      </ElTableColumn>
    </ElTable>

    <ElPagination class="admin-pagination" layout="total, prev, pager, next" :total="total"
      :current-page="page" :page-size="pageSize" @current-change="(value: number) => { page = value; void load() }" />

    <ElDialog v-model="dialogVisible" :title="dialogTitle" width="620px" top="8vh">
      <ElAlert v-if="dialogError" class="admin-error" type="error" :closable="false" :title="dialogError" show-icon />
      <template v-if="editing">
        <p class="muted">{{ editing.userEmail }} · {{ TYPE_LABELS[editing.type] ?? editing.type }} · {{ editing.createdAt }}</p>
        <pre class="feedback-content">{{ editing.content }}</pre>
        <ElForm label-position="top">
          <ElFormItem label="状态">
            <ElSelect v-model="form.status" style="width: 100%">
              <ElOption v-for="status in FEEDBACK_STATUSES" :key="status" :label="STATUS_LABELS[status]" :value="status" />
            </ElSelect>
          </ElFormItem>
          <ElFormItem label="回复（可选，提交者可见）">
            <ElInput v-model="form.reply" type="textarea" :rows="5" maxlength="2000" placeholder="填写处理说明" />
          </ElFormItem>
        </ElForm>
      </template>
      <template #footer>
        <ElButton @click="dialogVisible = false">取消</ElButton>
        <ElButton type="primary" :loading="saving" @click="save">保存</ElButton>
      </template>
    </ElDialog>
  </section>
</template>
