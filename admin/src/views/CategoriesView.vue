<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElAlert, ElButton, ElDialog, ElForm, ElFormItem, ElInput, ElInputNumber, ElMessage, ElMessageBox,
  ElSwitch, ElTable, ElTableColumn, ElTag } from 'element-plus'
import { ApiRequestError } from '@/api/client'
import { createAdminCategory, deleteAdminCategory, listAdminCategoryEntries, updateAdminCategory,
  updateAdminCategoryStatus, type AdminCategoryEntry } from '@/api/categories'

const entries = ref<AdminCategoryEntry[]>([])
const loading = ref(false)
const errorMessage = ref<string | null>(null)
const dialogVisible = ref(false)
const editingCode = ref<string | null>(null)
const saving = ref(false)
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
  if (loading.value) return
  loading.value = true
  errorMessage.value = null
  try {
    entries.value = (await listAdminCategoryEntries()).data
  } catch (error: unknown) {
    errorMessage.value = messageFor(error)
  } finally {
    loading.value = false
  }
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
  <section>
    <div class="page-heading">
      <div>
        <p class="eyebrow">CATEGORIES</p>
        <h1>分类管理</h1>
        <p class="page-description">维护分类名称、图标、排序与启用状态；停用分类会同时隐藏其下工具，删除只允许空分类。</p>
      </div>
      <ElButton type="primary" @click="openCreate">新增分类</ElButton>
    </div>

    <ElAlert v-if="errorMessage" class="admin-error" type="error" :closable="false" :title="errorMessage" show-icon />

    <ElTable v-loading="loading" :data="entries" class="admin-table" row-key="code">
      <ElTableColumn prop="code" label="编码" width="140" />
      <ElTableColumn prop="name" label="名称" min-width="140" />
      <ElTableColumn prop="description" label="说明" min-width="220" show-overflow-tooltip />
      <ElTableColumn prop="sortOrder" label="排序" width="90" />
      <ElTableColumn label="工具数" width="90">
        <template #default="scope"><ElTag v-if="scope.row.toolCount > 0" type="info">{{ scope.row.toolCount }}</ElTag><span v-else class="muted">0</span></template>
      </ElTableColumn>
      <ElTableColumn label="启用" width="100">
        <template #default="scope"><ElSwitch :model-value="scope.row.enabled" @change="(value: string | number | boolean) => { scope.row.enabled = Boolean(value); toggleEnabled(asEntry(scope.row)) }" /></template>
      </ElTableColumn>
      <ElTableColumn label="操作" width="170" fixed="right">
        <template #default="scope">
          <ElButton size="small" @click="openEdit(asEntry(scope.row))">编辑</ElButton>
          <ElButton size="small" type="danger" plain @click="remove(asEntry(scope.row))">删除</ElButton>
        </template>
      </ElTableColumn>
    </ElTable>

    <ElDialog v-model="dialogVisible" :title="dialogTitle" width="560px" top="8vh">
      <ElAlert v-if="dialogError" class="admin-error" type="error" :closable="false" :title="dialogError" show-icon />
      <ElForm label-position="top">
        <ElFormItem v-if="!editingCode" label="分类编码">
          <ElInput v-model="form.code" maxlength="32" placeholder="大写字母开头，例如 DEV" />
        </ElFormItem>
        <ElFormItem label="名称"><ElInput v-model="form.name" maxlength="64" /></ElFormItem>
        <ElFormItem label="说明"><ElInput v-model="form.description" type="textarea" :rows="3" maxlength="500" /></ElFormItem>
        <ElFormItem label="图标标识"><ElInput v-model="form.icon" maxlength="128" placeholder="可选，例如 tools" /></ElFormItem>
        <ElFormItem label="排序"><ElInputNumber v-model="form.sortOrder" :min="0" :max="100000" /></ElFormItem>
        <ElFormItem label="启用"><ElSwitch v-model="form.enabled" /></ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="dialogVisible = false">取消</ElButton>
        <ElButton type="primary" :loading="saving" @click="save">保存</ElButton>
      </template>
    </ElDialog>
  </section>
</template>
