<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElButton, ElForm, ElFormItem, ElInput, ElInputNumber, ElMessage, ElMessageBox,
  ElOption, ElSelect, ElSwitch, ElTable, ElTableColumn } from 'element-plus'
import AdminPageHeader from '@/components/AdminPageHeader.vue'
import AdminDataPanel from '@/components/AdminDataPanel.vue'
import AdminDetailDrawer from '@/components/AdminDetailDrawer.vue'
import AdminFormError from '@/components/AdminFormError.vue'
import AdminRecordCards from '@/components/AdminRecordCards.vue'
import AdminStatusTag from '@/components/AdminStatusTag.vue'
import { useAdminRequest } from '@/composables/useAdminRequest'
import { ApiRequestError } from '@/api/client'
import { createAdminRecommendation, deleteAdminRecommendation, listAdminRecommendations,
  updateAdminRecommendation, updateAdminRecommendationStatus, type AdminRecommendation } from '@/api/recommendations'

const entries = ref<AdminRecommendation[]>([])
const { loading, errorMessage, traceId, run } = useAdminRequest(messageFor)
const dialogVisible = ref(false)
const editingCode = ref<string | null>(null)
const saving = ref(false)
const statusBusy = ref(false)
const dialogError = ref<string | null>(null)
const form = reactive({ slotCode: '', title: '', subtitle: '', targetType: 'tool', toolCode: '', linkUrl: '',
  imageUrl: '', sortOrder: 0, enabled: true, startAt: '', endAt: '' })
const dialogTitle = computed(() => editingCode.value ? `编辑推荐位 · ${editingCode.value}` : '新增推荐位')
const asEntry = (row: unknown): AdminRecommendation => row as AdminRecommendation

function messageFor(error: unknown): string {
  if (error instanceof ApiRequestError) {
    switch (error.code) {
      case 30008: return '推荐位编码已存在，请更换编码。'
      case 30009: return '引用的工具不存在或已删除。'
      case 10006: return '推荐位不存在或已删除。'
      case 10001: return '请求参数不合法，请检查表单字段。'
      case 10005: return '当前账号没有管理权限。'
      case 10002: return '登录状态已失效，请重新登录。'
      default: return error.message
    }
  }
  return '暂时无法连接平台服务，请稍后重试。'
}

async function load(): Promise<void> {
  await run(() => listAdminRecommendations(), result => { entries.value = result.data })
}

function openCreate(): void {
  editingCode.value = null
  dialogError.value = null
  Object.assign(form, { slotCode: '', title: '', subtitle: '', targetType: 'tool', toolCode: '', linkUrl: '',
    imageUrl: '', sortOrder: 0, enabled: true, startAt: '', endAt: '' })
  dialogVisible.value = true
}

function openEdit(entry: AdminRecommendation): void {
  editingCode.value = entry.slotCode
  dialogError.value = null
  Object.assign(form, { slotCode: entry.slotCode, title: entry.title, subtitle: entry.subtitle,
    targetType: entry.toolCode ? 'tool' : 'link', toolCode: entry.toolCode ?? '', linkUrl: entry.linkUrl ?? '',
    imageUrl: entry.imageUrl ?? '', sortOrder: entry.sortOrder, enabled: entry.enabled,
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
  if (!editingCode.value && !/^[a-z][a-z0-9_]{0,31}$/.test(form.slotCode.trim())) {
    dialogError.value = '推荐位编码需以小写字母开头，只能包含小写字母、数字和下划线。'
    return
  }
  if (!form.title.trim() || form.title.trim().length > 64) {
    dialogError.value = '请填写 1-64 字符的标题。'
    return
  }
  const toolCode = form.targetType === 'tool' ? form.toolCode.trim() : ''
  const linkUrl = form.targetType === 'link' ? form.linkUrl.trim() : ''
  if (form.targetType === 'tool' && !/^[a-z][a-z0-9_]{0,63}$/.test(toolCode)) {
    dialogError.value = '请选择或填写有效的工具编码。'
    return
  }
  if (form.targetType === 'link' && !/^https:\/\/[^\s]+$/.test(linkUrl)) {
    dialogError.value = '链接必须以 https:// 开头。'
    return
  }
  if (form.imageUrl.trim() && !/^https:\/\/[^\s]+$/.test(form.imageUrl.trim())) {
    dialogError.value = '图片地址必须以 https:// 开头。'
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
  if (!Number.isInteger(form.sortOrder) || form.sortOrder < 0 || form.sortOrder > 100000) {
    dialogError.value = '排序需为 0-100000。'
    return
  }
  const payload = { title: form.title.trim(), subtitle: form.subtitle.trim(),
    toolCode: form.targetType === 'tool' ? toolCode : null, linkUrl: form.targetType === 'link' ? linkUrl : null,
    imageUrl: form.imageUrl.trim() || null, sortOrder: form.sortOrder, enabled: form.enabled, startAt, endAt }
  saving.value = true
  try {
    if (editingCode.value) await updateAdminRecommendation(editingCode.value, payload)
    else await createAdminRecommendation({ ...payload, slotCode: form.slotCode.trim() })
    dialogVisible.value = false
    ElMessage.success(editingCode.value ? '推荐位已更新' : '推荐位已创建')
    await load()
  } catch (error: unknown) {
    dialogError.value = messageFor(error)
  } finally {
    saving.value = false
  }
}

async function toggleEnabled(entry: AdminRecommendation): Promise<void> {
  if (statusBusy.value) return
  statusBusy.value = true
  try {
    const next = entry.enabled
    try {
      await updateAdminRecommendationStatus(entry.slotCode, next)
      ElMessage.success(next ? '推荐位已启用' : '推荐位已停用')
      await load()
    } catch (error: unknown) {
      entry.enabled = !next
      ElMessage.error(messageFor(error))
    }
  } finally { statusBusy.value = false }
}

async function remove(entry: AdminRecommendation): Promise<void> {
  try {
    await ElMessageBox.prompt(`删除后不可恢复。请输入推荐位编码 ${entry.slotCode} 确认。`, '删除推荐位',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消',
        inputValidator: (value: string) => value === entry.slotCode || '输入的编码不匹配' })
  } catch {
    return
  }
  try {
    await deleteAdminRecommendation(entry.slotCode)
    ElMessage.success('推荐位已删除')
    await load()
  } catch (error: unknown) {
    ElMessage.error(messageFor(error))
  }
}

onMounted(() => { void load() })
</script>

<template>
  <section class="admin-page">
    <AdminPageHeader title="推荐位管理" description="管理首页推荐内容、目标链接与展示时间。">
      <template #actions>
        <ElButton :loading="loading" @click="load">刷新</ElButton>
        <ElButton type="primary" @click="openCreate">新增推荐位</ElButton>
      </template>
    </AdminPageHeader>
    <AdminDataPanel :loading="loading" :has-content="entries.length > 0" :error="errorMessage" :trace-id="traceId" empty-title="暂无推荐位" empty-description="点击右上角开始创建。" @retry="load">
      <ElTable :data="entries" class="admin-table admin-desktop-table" row-key="slotCode">
        <ElTableColumn label="推荐内容" min-width="240">
          <template #default="scope">
            <strong class="admin-record-title">{{ scope.row.title }}</strong>
            <div class="admin-record-subtitle">{{ scope.row.slotCode }}</div>
            <div class="admin-record-description admin-cell-description">{{ scope.row.subtitle }}</div>
          </template>
        </ElTableColumn>
        <ElTableColumn label="目标" min-width="170">
          <template #default="scope">
            <span class="admin-record-description">{{ scope.row.toolCode ? `工具 · ${scope.row.toolCode}` : scope.row.linkUrl }}</span>
            <div class="admin-record-subtitle">排序 {{ scope.row.sortOrder }}</div>
          </template>
        </ElTableColumn>
        <ElTableColumn label="生效状态" width="110">
          <template #default="scope">
            <AdminStatusTag :label="scope.row.active ? '生效中' : '未生效'" :tone="scope.row.active ? 'success' : 'info'" />
          </template>
        </ElTableColumn>
        <ElTableColumn label="展示时间 · UTC" min-width="190">
          <template #default="scope">
            <div class="admin-record-subtitle">{{ scope.row.startAt ?? '不限开始' }}<br>{{ scope.row.endAt ?? '不限结束' }}</div>
          </template>
        </ElTableColumn>
        <ElTableColumn label="启用" width="80">
          <template #default="scope">
            <ElSwitch :model-value="scope.row.enabled" :disabled="loading || statusBusy" :aria-label="`${scope.row.title}启用状态`" @change="(value: string | number | boolean) => { scope.row.enabled = Boolean(value); toggleEnabled(asEntry(scope.row)) }" />
          </template>
        </ElTableColumn>
        <ElTableColumn label="操作" width="170" fixed="right">
          <template #default="scope">
            <div class="admin-row-actions">
              <ElButton size="small" :disabled="loading || statusBusy" @click="openEdit(asEntry(scope.row))">编辑</ElButton>
              <ElButton size="small" type="danger" plain :disabled="loading || statusBusy" @click="remove(asEntry(scope.row))">删除</ElButton>
            </div>
          </template>
        </ElTableColumn>
      </ElTable>
      <AdminRecordCards :records="entries" :record-key="record => record.slotCode">
        <template #title="{ record }">
          <strong class="admin-record-title">{{ record.title }}</strong>
          <div class="admin-record-subtitle">{{ record.slotCode }}</div>
        </template>
        <template #status="{ record }">
          <AdminStatusTag :label="record.active ? '生效中' : '未生效'" :tone="record.active ? 'success' : 'info'" />
        </template>
        <template #description="{ record }">{{ record.subtitle }}</template>
        <template #meta="{ record }">{{ record.toolCode ? `工具 · ${record.toolCode}` : record.linkUrl }}<br>排序 {{ record.sortOrder }} · {{ record.startAt ?? '不限开始' }} → {{ record.endAt ?? '不限结束' }}</template>
        <template #actions="{ record }">
          <ElButton size="small" :disabled="loading || statusBusy" @click="openEdit(record)">编辑</ElButton>
          <ElButton size="small" type="danger" plain :disabled="loading || statusBusy" @click="remove(record)">删除</ElButton>
          <ElSwitch :model-value="record.enabled" :disabled="loading || statusBusy" :aria-label="`${record.title}启用状态`" @change="(value: string | number | boolean) => { record.enabled = Boolean(value); toggleEnabled(record) }" />
        </template>
      </AdminRecordCards>
    </AdminDataPanel>
    <div class="admin-list-footer">
      <span class="admin-result-count">共 {{ entries.length }} 条记录</span>
    </div>
    <AdminDetailDrawer v-model="dialogVisible" :title="dialogTitle" :busy="saving" width="600px">
      <p v-if="editingCode" class="admin-form-help">推荐位编码 {{ editingCode }}</p>
      <AdminFormError v-if="dialogError" :message="dialogError" />
      <ElForm label-position="top" :disabled="saving">
        <section class="admin-form-section">
          <h3>推荐内容</h3>
          <ElFormItem v-if="!editingCode" label="推荐位编码">
            <ElInput v-model="form.slotCode" maxlength="32" placeholder="小写字母开头，例如 home_top" />
          </ElFormItem>
          <ElFormItem label="标题">
            <ElInput v-model="form.title" maxlength="64" />
          </ElFormItem>
          <ElFormItem label="副标题">
            <ElInput v-model="form.subtitle" maxlength="128" />
          </ElFormItem>
          <ElFormItem label="目标类型">
            <ElSelect v-model="form.targetType" style="width: 100%">
              <ElOption label="目录工具" value="tool" />
              <ElOption label="https 链接" value="link" />
            </ElSelect>
          </ElFormItem>
          <ElFormItem v-if="form.targetType === 'tool'" label="工具编码">
            <ElInput v-model="form.toolCode" maxlength="64" placeholder="例如 calculator" />
          </ElFormItem>
          <ElFormItem v-else label="链接地址">
            <ElInput v-model="form.linkUrl" maxlength="500" placeholder="https://example.com/activity" />
          </ElFormItem>
          <ElFormItem label="图片地址（可选）">
            <ElInput v-model="form.imageUrl" maxlength="500" placeholder="https://…" />
          </ElFormItem>
        </section>
        <section class="admin-form-section">
          <h3>展示设置</h3>
          <div class="admin-form-grid">
            <ElFormItem label="排序">
              <ElInputNumber v-model="form.sortOrder" :min="0" :max="100000" />
            </ElFormItem>
            <ElFormItem label="启用">
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
        <ElButton type="primary" :loading="saving" @click="save">保存推荐位</ElButton>
      </template>
    </AdminDetailDrawer>
  </section>
</template>
