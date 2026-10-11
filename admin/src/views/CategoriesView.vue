<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElButton, ElForm, ElFormItem, ElInput, ElInputNumber, ElMessage, ElMessageBox,
  ElSwitch, ElTable, ElTableColumn } from 'element-plus'
import AdminPageHeader from '@/components/AdminPageHeader.vue'
import AdminFilterBar from '@/components/AdminFilterBar.vue'
import AdminDataPanel from '@/components/AdminDataPanel.vue'
import AdminDetailDrawer from '@/components/AdminDetailDrawer.vue'
import AdminFormError from '@/components/AdminFormError.vue'
import AdminRecordCards from '@/components/AdminRecordCards.vue'
import AdminStatusTag from '@/components/AdminStatusTag.vue'
import { useAdminRequest } from '@/composables/useAdminRequest'
import { ApiRequestError } from '@/api/client'
import { createAdminCategory, deleteAdminCategory, listAdminCategoryEntries, updateAdminCategory,
  updateAdminCategoryStatus, type AdminCategoryEntry } from '@/api/categories'

const entries = ref<AdminCategoryEntry[]>([])
const keyword = ref('')
const appliedKeyword = ref('')
const visibleEntries = computed(() => entries.value.filter(entry =>
  `${entry.code} ${entry.name} ${entry.description}`.toLowerCase().includes(appliedKeyword.value)))
function search(): void { appliedKeyword.value = keyword.value.trim().toLowerCase() }
function resetFilters(): void { keyword.value = ''; appliedKeyword.value = '' }
const { loading, errorMessage, traceId, run } = useAdminRequest(messageFor)
const dialogVisible = ref(false)
const editingCode = ref<string | null>(null)
const saving = ref(false)
const statusBusy = ref(false)
const dialogError = ref<string | null>(null)
const form = reactive({ code: '', name: '', description: '', icon: '', sortOrder: 0, enabled: true })
const dialogTitle = computed(() => editingCode.value ? `编辑分类 · ${editingCode.value}` : '新增分类')
const asEntry = (row: unknown): AdminCategoryEntry => row as AdminCategoryEntry

function messageFor(error: unknown): string {
  if (error instanceof ApiRequestError) {
    switch (error.code) {
      case 30006: return '分类编码已存在，请更换编码。'
      case 30007: return '该分类下仍有工具，请先移动或删除这些工具。'
      case 10006: return '分类不存在或已删除。'
      case 10001: return '请求参数不合法，请检查表单字段。'
      case 10005: return '当前账号没有管理权限。'
      case 10002: return '登录状态已失效，请重新登录。'
      default: return error.message
    }
  }
  return '暂时无法连接平台服务，请稍后重试。'
}

async function load(): Promise<void> {
  await run(() => listAdminCategoryEntries(), result => { entries.value = result.data })
}

function openCreate(): void {
  editingCode.value = null
  dialogError.value = null
  Object.assign(form, { code: '', name: '', description: '', icon: '', sortOrder: 0, enabled: true })
  dialogVisible.value = true
}

function openEdit(entry: AdminCategoryEntry): void {
  editingCode.value = entry.code
  dialogError.value = null
  Object.assign(form, { code: entry.code, name: entry.name, description: entry.description,
    icon: entry.icon ?? '', sortOrder: entry.sortOrder, enabled: entry.enabled })
  dialogVisible.value = true
}

async function save(): Promise<void> {
  if (saving.value) return
  dialogError.value = null
  if (!editingCode.value && !/^[A-Z][A-Z0-9_]{0,31}$/.test(form.code.trim().toUpperCase())) {
    dialogError.value = '分类编码需以大写字母开头，只能包含大写字母、数字和下划线。'
    return
  }
  if (!form.name.trim() || form.name.trim().length > 64) {
    dialogError.value = '请填写 1-64 字符的分类名称。'
    return
  }
  if (!Number.isInteger(form.sortOrder) || form.sortOrder < 0 || form.sortOrder > 100000) {
    dialogError.value = '排序需为 0-100000。'
    return
  }
  if (form.icon.trim() && !/^[a-z][a-z0-9_]{0,127}$/.test(form.icon.trim())) {
    dialogError.value = '图标标识需以小写字母开头，只能包含小写字母、数字和下划线。'
    return
  }
  const payload = { name: form.name.trim(), description: form.description.trim(),
    icon: form.icon.trim() || null, sortOrder: form.sortOrder, enabled: form.enabled }
  saving.value = true
  try {
    if (editingCode.value) await updateAdminCategory(editingCode.value, payload)
    else await createAdminCategory({ ...payload, code: form.code.trim().toUpperCase() })
    dialogVisible.value = false
    ElMessage.success(editingCode.value ? '分类已更新' : '分类已创建')
    await load()
  } catch (error: unknown) {
    dialogError.value = messageFor(error)
  } finally {
    saving.value = false
  }
}

async function toggleEnabled(entry: AdminCategoryEntry): Promise<void> {
  if (statusBusy.value) return
  statusBusy.value = true
  try {
    const next = entry.enabled
    if (!next) {
      try {
        await ElMessageBox.confirm(`停用「${entry.name}」后，其下 ${entry.toolCount} 个工具会从公开目录隐藏。确认停用？`,
          '停用分类', { type: 'warning' })
      } catch {
        entry.enabled = true
        return
      }
    }
    try {
      await updateAdminCategoryStatus(entry.code, next)
      ElMessage.success(next ? '分类已启用' : '分类已停用')
      await load()
    } catch (error: unknown) {
      entry.enabled = !next
      ElMessage.error(messageFor(error))
    }
  } finally { statusBusy.value = false }
}

async function remove(entry: AdminCategoryEntry): Promise<void> {
  if (entry.toolCount > 0) {
    ElMessage.warning('该分类下仍有工具，请先移动或删除这些工具。')
    return
  }
  try {
    await ElMessageBox.prompt(`删除后不可恢复。请输入分类编码 ${entry.code} 确认。`, '删除分类',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消',
        inputValidator: (value: string) => value === entry.code || '输入的编码不匹配' })
  } catch {
    return
  }
  try {
    await deleteAdminCategory(entry.code)
    ElMessage.success('分类已删除')
    await load()
  } catch (error: unknown) {
    ElMessage.error(messageFor(error))
  }
}

onMounted(() => { void load() })
</script>

<template>
  <section class="admin-page">
    <AdminPageHeader title="分类管理" description="整理工具分类、显示顺序与启用状态。">
      <template #actions>
        <ElButton :loading="loading" @click="load">刷新</ElButton>
        <ElButton type="primary" @click="openCreate">新增分类</ElButton>
      </template>
    </AdminPageHeader>
    <AdminFilterBar @search="search" @reset="resetFilters">
      <ElInput v-model="keyword" aria-label="搜索分类" placeholder="搜索分类名称、编码或说明" clearable style="width: 360px" @clear="search" />
    </AdminFilterBar>
    <AdminDataPanel :loading="loading" :has-content="visibleEntries.length > 0" :error="errorMessage" :trace-id="traceId" empty-title="暂无分类" empty-description="调整搜索条件，或新增一个分类。" @retry="load">
      <ElTable :data="visibleEntries" class="admin-table admin-desktop-table" row-key="code">
        <ElTableColumn label="分类" min-width="240">
          <template #default="scope">
            <strong class="admin-record-title">{{ scope.row.name }}</strong>
            <div class="admin-record-subtitle">{{ scope.row.code }}</div>
            <div class="admin-record-description admin-cell-description">{{ scope.row.description }}</div>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="toolCount" label="工具数" width="110" />
        <ElTableColumn prop="sortOrder" label="排序" width="100" />
        <ElTableColumn label="启用" width="120">
          <template #default="scope">
            <ElSwitch :model-value="scope.row.enabled" :disabled="loading || statusBusy" :aria-label="`${scope.row.name}启用状态`" @change="(value: string | number | boolean) => { scope.row.enabled = Boolean(value); toggleEnabled(asEntry(scope.row)) }" />
          </template>
        </ElTableColumn>
        <ElTableColumn label="操作" width="170" fixed="right">
          <template #default="scope">
            <div class="admin-row-actions">
              <ElButton size="small" :disabled="loading || statusBusy" @click="openEdit(asEntry(scope.row))">编辑</ElButton>
              <ElButton size="small" type="danger" plain :disabled="loading || statusBusy || scope.row.toolCount > 0" @click="remove(asEntry(scope.row))">删除</ElButton>
            </div>
          </template>
        </ElTableColumn>
      </ElTable>
      <AdminRecordCards :records="visibleEntries" :record-key="record => record.code">
        <template #title="{ record }">
          <strong class="admin-record-title">{{ record.name }}</strong>
          <div class="admin-record-subtitle">{{ record.code }}</div>
        </template>
        <template #status="{ record }">
          <AdminStatusTag :label="record.enabled ? '已启用' : '已停用'" :tone="record.enabled ? 'success' : 'info'" />
        </template>
        <template #description="{ record }">{{ record.description }}</template>
        <template #meta="{ record }">{{ record.toolCount }} 个工具 · 排序 {{ record.sortOrder }}</template>
        <template #actions="{ record }">
          <ElButton size="small" :disabled="loading || statusBusy" @click="openEdit(record)">编辑</ElButton>
          <ElButton size="small" type="danger" plain :disabled="loading || statusBusy || record.toolCount > 0" @click="remove(record)">删除</ElButton>
          <ElSwitch :model-value="record.enabled" :disabled="loading || statusBusy" :aria-label="`${record.name}启用状态`" @change="(value: string | number | boolean) => { record.enabled = Boolean(value); toggleEnabled(record) }" />
        </template>
      </AdminRecordCards>
    </AdminDataPanel>
    <div class="admin-list-footer">
      <span class="admin-result-count">显示 {{ visibleEntries.length }} / {{ entries.length }} 个分类 · 空分类可删除</span>
    </div>
    <AdminDetailDrawer v-model="dialogVisible" :title="dialogTitle" :busy="saving">
      <p v-if="editingCode" class="admin-form-help">分类编码 {{ editingCode }}</p>
      <AdminFormError v-if="dialogError" :message="dialogError" />
      <ElForm label-position="top" :disabled="saving">
        <section class="admin-form-section">
          <h3>分类信息</h3>
          <ElFormItem v-if="!editingCode" label="分类编码">
            <ElInput v-model="form.code" maxlength="32" placeholder="大写字母开头，例如 DEV" />
          </ElFormItem>
          <ElFormItem label="名称">
            <ElInput v-model="form.name" maxlength="64" />
          </ElFormItem>
          <ElFormItem label="说明">
            <ElInput v-model="form.description" type="textarea" :rows="3" maxlength="500" />
          </ElFormItem>
          <ElFormItem label="图标标识">
            <ElInput v-model="form.icon" maxlength="128" placeholder="可选，例如 tools" />
          </ElFormItem>
        </section>
        <section class="admin-form-section">
          <h3>目录展示</h3>
          <div class="admin-form-grid">
            <ElFormItem label="排序">
              <ElInputNumber v-model="form.sortOrder" :min="0" :max="100000" />
            </ElFormItem>
            <ElFormItem label="启用">
              <ElSwitch v-model="form.enabled" />
            </ElFormItem>
          </div>
        </section>
      </ElForm>
      <p class="admin-form-help">停用后，该分类下的工具会从公开目录隐藏。</p>
      <template #footer>
        <ElButton :disabled="saving" @click="dialogVisible = false">取消</ElButton>
        <ElButton type="primary" :loading="saving" @click="save">保存分类</ElButton>
      </template>
    </AdminDetailDrawer>
  </section>
</template>
