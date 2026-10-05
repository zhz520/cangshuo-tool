<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElAlert, ElButton, ElDialog, ElForm, ElFormItem, ElInput, ElMessage, ElMessageBox, ElOption,
  ElSelect, ElSwitch, ElTable, ElTableColumn, ElTag } from 'element-plus'
import { ApiRequestError } from '@/api/client'
import { ANNOUNCEMENT_LEVELS, createAdminAnnouncement, deleteAdminAnnouncement, listAdminAnnouncements,
  updateAdminAnnouncement, updateAdminAnnouncementStatus, type AdminAnnouncement } from '@/api/announcements'

const LEVEL_LABELS: Record<string, string> = { INFO: '通知', WARNING: '提醒', CRITICAL: '重要' }
const LEVEL_TYPES: Record<string, 'info' | 'warning' | 'danger'> = { INFO: 'info', WARNING: 'warning', CRITICAL: 'danger' }
const entries = ref<AdminAnnouncement[]>([])
const loading = ref(false)
const errorMessage = ref<string | null>(null)
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
  if (loading.value) return
  loading.value = true
  errorMessage.value = null
  try {
    entries.value = (await listAdminAnnouncements()).data
  } catch (error: unknown) {
    errorMessage.value = messageFor(error)
  } finally {
    loading.value = false
  }
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
  <section>
    <div class="page-heading">
      <div>
        <p class="eyebrow">ANNOUNCEMENTS</p>
        <h1>公告管理</h1>
        <p class="page-description">创建公告草稿、发布或下线，并可用 UTC 时间窗定时展示；公开接口只返回当前可见的公告。</p>
      </div>
      <ElButton type="primary" @click="openCreate">新增公告</ElButton>
    </div>

    <ElAlert v-if="errorMessage" class="admin-error" type="error" :closable="false" :title="errorMessage" show-icon />

    <ElTable v-loading="loading" :data="entries" class="admin-table" row-key="id">
      <ElTableColumn prop="id" label="ID" width="80" />
      <ElTableColumn prop="title" label="标题" min-width="180" />
      <ElTableColumn label="级别" width="100">
        <template #default="scope"><ElTag :type="LEVEL_TYPES[scope.row.level]">{{ LEVEL_LABELS[scope.row.level] }}</ElTag></template>
      </ElTableColumn>
      <ElTableColumn label="状态" width="110">
        <template #default="scope">
          <ElTag v-if="scope.row.active" type="success">展示中</ElTag>
          <ElTag v-else-if="scope.row.published" type="warning">已发布未生效</ElTag>
          <ElTag v-else type="info">草稿</ElTag>
        </template>
      </ElTableColumn>
      <ElTableColumn label="时间窗" min-width="190">
        <template #default="scope"><span class="muted">{{ scope.row.startAt ?? '不限' }} → {{ scope.row.endAt ?? '不限' }}</span></template>
      </ElTableColumn>
      <ElTableColumn label="更新时间" min-width="190">
        <template #default="scope"><span class="muted">{{ scope.row.updatedAt }}</span></template>
      </ElTableColumn>
      <ElTableColumn label="操作" width="230" fixed="right">
        <template #default="scope">
          <ElButton size="small" @click="openEdit(asEntry(scope.row))">编辑</ElButton>
          <ElButton size="small" :type="scope.row.published ? 'warning' : 'primary'" plain @click="togglePublished(asEntry(scope.row))">
            {{ scope.row.published ? '下线' : '发布' }}
          </ElButton>
          <ElButton size="small" type="danger" plain @click="remove(asEntry(scope.row))">删除</ElButton>
        </template>
      </ElTableColumn>
    </ElTable>

    <ElDialog v-model="dialogVisible" :title="dialogTitle" width="600px" top="8vh">
      <ElAlert v-if="dialogError" class="admin-error" type="error" :closable="false" :title="dialogError" show-icon />
      <ElForm label-position="top">
        <ElFormItem label="标题"><ElInput v-model="form.title" maxlength="128" /></ElFormItem>
        <ElFormItem label="正文"><ElInput v-model="form.body" type="textarea" :rows="5" maxlength="2000" /></ElFormItem>
        <ElFormItem label="级别">
          <ElSelect v-model="form.level" style="width: 100%">
            <ElOption v-for="level in ANNOUNCEMENT_LEVELS" :key="level" :label="LEVEL_LABELS[level]" :value="level" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="发布"><ElSwitch v-model="form.enabled" /></ElFormItem>
        <ElFormItem label="开始时间（UTC，可选）"><ElInput v-model="form.startAt" placeholder="2026-10-05T00:00:00Z" /></ElFormItem>
        <ElFormItem label="结束时间（UTC，可选）"><ElInput v-model="form.endAt" placeholder="2026-11-05T00:00:00Z" /></ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="dialogVisible = false">取消</ElButton>
        <ElButton type="primary" :loading="saving" @click="save">保存</ElButton>
      </template>
    </ElDialog>
  </section>
</template>
