<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElButton, ElForm, ElFormItem, ElInput, ElMessage, ElMessageBox, ElOption,
  ElSelect, ElSwitch, ElTable, ElTableColumn } from 'element-plus'
import AdminPageHeader from '@/components/AdminPageHeader.vue'
import AdminDataPanel from '@/components/AdminDataPanel.vue'
import AdminDetailDrawer from '@/components/AdminDetailDrawer.vue'
import AdminFormError from '@/components/AdminFormError.vue'
import AdminRecordCards from '@/components/AdminRecordCards.vue'
import AdminStatusTag from '@/components/AdminStatusTag.vue'
import { useAdminRequest } from '@/composables/useAdminRequest'
import { ApiRequestError } from '@/api/client'
import { ANNOUNCEMENT_LEVELS, createAdminAnnouncement, deleteAdminAnnouncement, listAdminAnnouncements,
  updateAdminAnnouncement, updateAdminAnnouncementStatus, type AdminAnnouncement } from '@/api/announcements'

const LEVEL_LABELS: Record<string, string> = { INFO: '通知', WARNING: '提醒', CRITICAL: '重要' }
const LEVEL_TYPES: Record<string, 'info' | 'warning' | 'danger'> = { INFO: 'info', WARNING: 'warning', CRITICAL: 'danger' }
const entries = ref<AdminAnnouncement[]>([])
const { loading, errorMessage, traceId, run } = useAdminRequest(messageFor)
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const saving = ref(false)
const dialogError = ref<string | null>(null)
const form = reactive({ title: '', body: '', level: 'INFO', enabled: false, startAt: '', endAt: '' })
const dialogTitle = computed(() => editingId.value ? `编辑公告 · #${editingId.value}` : '新增公告')
const asEntry = (row: unknown): AdminAnnouncement => row as AdminAnnouncement

function messageFor(error: unknown): string {
  if (error instanceof ApiRequestError) {
    switch (error.code) {
      case 10006: return '公告不存在或已删除。'
      case 10001: return '请求参数不合法，请检查表单字段。'
      case 10005: return '当前账号没有管理权限。'
      case 10002: return '登录状态已失效，请重新登录。'
      default: return error.message
    }
  }
  return '暂时无法连接平台服务，请稍后重试。'
}

async function load(): Promise<void> {
  await run(() => listAdminAnnouncements(), result => { entries.value = result.data })
}

function openCreate(): void {
  editingId.value = null
  dialogError.value = null
  Object.assign(form, { title: '', body: '', level: 'INFO', enabled: false, startAt: '', endAt: '' })
  dialogVisible.value = true
}

function openEdit(entry: AdminAnnouncement): void {
  editingId.value = entry.id
  dialogError.value = null
  Object.assign(form, { title: entry.title, body: entry.body, level: entry.level, enabled: entry.published,
    startAt: entry.startAt ?? '', endAt: entry.endAt ?? '' })
  dialogVisible.value = true
}

function normalizeInstant(value: string): string | null | undefined {
  const trimmed = value.trim()
  if (!trimmed) return null
  if (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d{1,9})?Z$/.test(trimmed)) return undefined
  return trimmed
}

async function save(): Promise<void> {
  if (saving.value) return
  dialogError.value = null
  if (!form.title.trim() || form.title.trim().length > 128 || /[\n\r]/.test(form.title)) {
    dialogError.value = '标题需为 1-128 字符的单行文本。'
    return
  }
  if (!form.body.trim() || form.body.trim().length > 2000) {
    dialogError.value = '正文需为 1-2000 字符。'
    return
  }
  if (!(ANNOUNCEMENT_LEVELS as readonly string[]).includes(form.level)) {
    dialogError.value = '级别不在允许范围内。'
    return
  }
  const startAt = normalizeInstant(form.startAt)
  const endAt = normalizeInstant(form.endAt)
  if (startAt === undefined || endAt === undefined) {
    dialogError.value = '时间需使用 UTC ISO 格式，例如 2026-10-05T00:00:00Z，可留空。'
    return
  }
  if (startAt && endAt && endAt <= startAt) {
    dialogError.value = '结束时间必须晚于开始时间。'
    return
  }
  const payload = { title: form.title.trim(), body: form.body.trim(), level: form.level,
    enabled: form.enabled, startAt, endAt }
  saving.value = true
  try {
    if (editingId.value) await updateAdminAnnouncement(editingId.value, payload)
    else await createAdminAnnouncement(payload)
    dialogVisible.value = false
    ElMessage.success(editingId.value ? '公告已更新' : '公告已创建')
    await load()
  } catch (error: unknown) {
    dialogError.value = messageFor(error)
  } finally {
    saving.value = false
  }
}

async function togglePublished(entry: AdminAnnouncement): Promise<void> {
  const next = !entry.published
  try {
    await ElMessageBox.confirm(next ? `确认发布「${entry.title}」？` : `确认下线「${entry.title}」？`,
      next ? '发布公告' : '下线公告', { type: 'warning' })
  } catch {
    return
  }
  try {
    await updateAdminAnnouncementStatus(entry.id, next)
    ElMessage.success(next ? '公告已发布' : '公告已下线')
    await load()
  } catch (error: unknown) {
    ElMessage.error(messageFor(error))
  }
}

async function remove(entry: AdminAnnouncement): Promise<void> {
  try {
    await ElMessageBox.confirm(`删除后不可恢复：${entry.title}`, '删除公告',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' })
  } catch {
    return
  }
  try {
    await deleteAdminAnnouncement(entry.id)
    ElMessage.success('公告已删除')
    await load()
  } catch (error: unknown) {
    ElMessage.error(messageFor(error))
  }
}

onMounted(() => { void load() })
</script>

<template>
  <section class="admin-page">
    <AdminPageHeader title="公告管理" description="编辑公告内容，管理发布状态与展示时间。">
      <template #actions>
        <ElButton :loading="loading" @click="load">刷新</ElButton>
        <ElButton type="primary" @click="openCreate">新增公告</ElButton>
      </template>
    </AdminPageHeader>
    <AdminDataPanel :loading="loading" :has-content="entries.length > 0" :error="errorMessage" :trace-id="traceId" empty-title="暂无公告" empty-description="点击右上角开始创建。" @retry="load">
      <ElTable :data="entries" class="admin-table admin-desktop-table" row-key="id">
        <ElTableColumn label="公告" min-width="260">
          <template #default="scope">
            <strong class="admin-record-title">{{ scope.row.title }}</strong>
            <div class="admin-record-subtitle">#{{ scope.row.id }} · 更新 {{ scope.row.updatedAt }}</div>
            <div class="admin-record-description admin-cell-description">{{ scope.row.body }}</div>
          </template>
        </ElTableColumn>
        <ElTableColumn label="级别" width="90">
          <template #default="scope">
            <AdminStatusTag :tone="LEVEL_TYPES[scope.row.level]" :label="LEVEL_LABELS[scope.row.level] ?? scope.row.level" />
          </template>
        </ElTableColumn>
        <ElTableColumn label="状态" width="140">
          <template #default="scope">
            <AdminStatusTag :label="scope.row.active ? '展示中' : scope.row.published ? '已发布未生效' : '草稿'" :tone="scope.row.active ? 'success' : scope.row.published ? 'warning' : 'info'" />
          </template>
        </ElTableColumn>
        <ElTableColumn label="展示时间 · UTC" min-width="180">
          <template #default="scope">
            <div class="admin-record-subtitle">{{ scope.row.startAt ?? '不限开始' }}<br>{{ scope.row.endAt ?? '不限结束' }}</div>
          </template>
        </ElTableColumn>
        <ElTableColumn label="操作" width="230" fixed="right">
          <template #default="scope">
            <div class="admin-row-actions">
              <ElButton size="small" :disabled="loading" @click="openEdit(asEntry(scope.row))">编辑</ElButton>
              <ElButton size="small" :disabled="loading" :type="scope.row.published ? 'warning' : 'primary'" plain @click="togglePublished(asEntry(scope.row))">{{ scope.row.published ? '下线' : '发布' }}</ElButton>
              <ElButton size="small" type="danger" plain :disabled="loading" @click="remove(asEntry(scope.row))">删除</ElButton>
            </div>
          </template>
        </ElTableColumn>
      </ElTable>
      <AdminRecordCards :records="entries" :record-key="record => record.id">
        <template #title="{ record }">
          <strong class="admin-record-title">{{ record.title }}</strong>
          <div class="admin-record-subtitle">#{{ record.id }} · {{ LEVEL_LABELS[record.level] }}</div>
        </template>
        <template #status="{ record }">
          <AdminStatusTag :label="record.active ? '展示中' : record.published ? '已发布未生效' : '草稿'" :tone="record.active ? 'success' : record.published ? 'warning' : 'info'" />
        </template>
        <template #description="{ record }">{{ record.body }}</template>
        <template #meta="{ record }">更新 {{ record.updatedAt }}<br>{{ record.startAt ?? '不限开始' }} → {{ record.endAt ?? '不限结束' }}</template>
        <template #actions="{ record }">
          <ElButton size="small" :disabled="loading" @click="openEdit(record)">编辑</ElButton>
          <ElButton size="small" :disabled="loading" :type="record.published ? 'warning' : 'primary'" plain @click="togglePublished(record)">{{ record.published ? '下线' : '发布' }}</ElButton>
          <ElButton size="small" type="danger" plain :disabled="loading" @click="remove(record)">删除</ElButton>
        </template>
      </AdminRecordCards>
    </AdminDataPanel>
    <div class="admin-list-footer">
      <span class="admin-result-count">共 {{ entries.length }} 条记录</span>
    </div>
    <AdminDetailDrawer v-model="dialogVisible" :title="dialogTitle" :busy="saving" width="600px">
      <AdminFormError v-if="dialogError" :message="dialogError" />
      <ElForm label-position="top" :disabled="saving">
        <section class="admin-form-section">
          <h3>公告内容</h3>
          <ElFormItem label="标题">
            <ElInput v-model="form.title" maxlength="128" />
          </ElFormItem>
          <ElFormItem label="正文">
            <ElInput v-model="form.body" type="textarea" :rows="5" maxlength="2000" />
          </ElFormItem>
        </section>
        <section class="admin-form-section">
          <h3>展示设置</h3>
          <div class="admin-form-grid">
            <ElFormItem label="级别">
              <ElSelect v-model="form.level" style="width: 100%">
                <ElOption v-for="level in ANNOUNCEMENT_LEVELS" :key="level" :label="LEVEL_LABELS[level]" :value="level" />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="发布">
              <ElSwitch v-model="form.enabled" />
            </ElFormItem>
          </div>
          <p class="admin-form-help">时间使用 UTC，留空表示不限制。</p>
          <ElFormItem label="开始时间（UTC，可选）">
            <ElInput v-model="form.startAt" placeholder="2026-10-05T00:00:00Z" />
          </ElFormItem>
          <ElFormItem label="结束时间（UTC，可选）">
            <ElInput v-model="form.endAt" placeholder="2026-11-05T00:00:00Z" />
          </ElFormItem>
        </section>
      </ElForm>
      <template #footer>
        <ElButton :disabled="saving" @click="dialogVisible = false">取消</ElButton>
        <ElButton type="primary" :loading="saving" @click="save">保存公告</ElButton>
      </template>
    </AdminDetailDrawer>
  </section>
</template>
