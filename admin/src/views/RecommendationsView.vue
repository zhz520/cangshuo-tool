<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElAlert, ElButton, ElDialog, ElForm, ElFormItem, ElInput, ElInputNumber, ElMessage, ElMessageBox,
  ElOption, ElSelect, ElSwitch, ElTable, ElTableColumn, ElTag } from 'element-plus'
import { ApiRequestError } from '@/api/client'
import { createAdminRecommendation, deleteAdminRecommendation, listAdminRecommendations,
  updateAdminRecommendation, updateAdminRecommendationStatus, type AdminRecommendation } from '@/api/recommendations'

const entries = ref<AdminRecommendation[]>([])
const loading = ref(false)
const errorMessage = ref<string | null>(null)
const dialogVisible = ref(false)
const editingCode = ref<string | null>(null)
const saving = ref(false)
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
  if (loading.value) return
  loading.value = true
  errorMessage.value = null
  try {
    entries.value = (await listAdminRecommendations()).data
  } catch (error: unknown) {
    errorMessage.value = messageFor(error)
  } finally {
    loading.value = false
  }
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
  const next = entry.enabled
  try {
    await updateAdminRecommendationStatus(entry.slotCode, next)
    ElMessage.success(next ? '推荐位已启用' : '推荐位已停用')
    await load()
  } catch (error: unknown) {
    entry.enabled = !next
    ElMessage.error(messageFor(error))
  }
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
  <section>
    <div class="page-heading">
      <div>
        <p class="eyebrow">RECOMMENDATIONS</p>
        <h1>推荐位管理</h1>
        <p class="page-description">维护首页推荐位：可指向目录工具或 https 链接，支持排序、启停与生效时间窗；公开接口只返回当前生效的推荐位。</p>
      </div>
      <ElButton type="primary" @click="openCreate">新增推荐位</ElButton>
    </div>

    <ElAlert v-if="errorMessage" class="admin-error" type="error" :closable="false" :title="errorMessage" show-icon />

    <ElTable v-loading="loading" :data="entries" class="admin-table" row-key="slotCode">
      <ElTableColumn prop="slotCode" label="编码" width="140" />
      <ElTableColumn prop="title" label="标题" min-width="140" />
      <ElTableColumn label="目标" min-width="220" show-overflow-tooltip>
        <template #default="scope"><span v-if="scope.row.toolCode">工具：{{ scope.row.toolCode }}</span><span v-else>{{ scope.row.linkUrl }}</span></template>
      </ElTableColumn>
      <ElTableColumn prop="sortOrder" label="排序" width="80" />
      <ElTableColumn label="生效" width="110">
        <template #default="scope">
          <ElTag v-if="scope.row.active" type="success">生效中</ElTag>
          <ElTag v-else type="info">未生效</ElTag>
        </template>
      </ElTableColumn>
      <ElTableColumn label="时间窗" min-width="190">
        <template #default="scope"><span class="muted">{{ scope.row.startAt ?? '不限' }} → {{ scope.row.endAt ?? '不限' }}</span></template>
      </ElTableColumn>
      <ElTableColumn label="启用" width="90">
        <template #default="scope"><ElSwitch :model-value="scope.row.enabled" @change="(value: string | number | boolean) => { scope.row.enabled = Boolean(value); toggleEnabled(asEntry(scope.row)) }" /></template>
      </ElTableColumn>
      <ElTableColumn label="操作" width="170" fixed="right">
        <template #default="scope">
          <ElButton size="small" @click="openEdit(asEntry(scope.row))">编辑</ElButton>
          <ElButton size="small" type="danger" plain @click="remove(asEntry(scope.row))">删除</ElButton>
        </template>
      </ElTableColumn>
    </ElTable>

    <ElDialog v-model="dialogVisible" :title="dialogTitle" width="600px" top="6vh">
      <ElAlert v-if="dialogError" class="admin-error" type="error" :closable="false" :title="dialogError" show-icon />
      <ElForm label-position="top">
        <ElFormItem v-if="!editingCode" label="推荐位编码">
          <ElInput v-model="form.slotCode" maxlength="32" placeholder="小写字母开头，例如 home_top" />
        </ElFormItem>
        <ElFormItem label="标题"><ElInput v-model="form.title" maxlength="64" /></ElFormItem>
        <ElFormItem label="副标题"><ElInput v-model="form.subtitle" maxlength="128" /></ElFormItem>
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
        <ElFormItem label="图片地址（可选）"><ElInput v-model="form.imageUrl" maxlength="500" placeholder="https://…" /></ElFormItem>
        <ElFormItem label="排序"><ElInputNumber v-model="form.sortOrder" :min="0" :max="100000" /></ElFormItem>
        <ElFormItem label="启用"><ElSwitch v-model="form.enabled" /></ElFormItem>
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
