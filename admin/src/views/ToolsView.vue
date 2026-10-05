<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElAlert, ElButton, ElDialog, ElDropdown, ElDropdownItem, ElDropdownMenu, ElForm, ElFormItem,
  ElInput, ElInputNumber, ElMessage, ElMessageBox, ElOption, ElPagination, ElSelect, ElSwitch, ElTable,
  ElTableColumn, ElTag } from 'element-plus'
import { ApiRequestError } from '@/api/client'
import { createAdminTool, deleteAdminTool, listAdminCategories, listAdminTools, updateAdminTool,
  updateAdminToolStatus, TOOL_MODES, TOOL_STATUSES, type AdminCategory, type AdminTool } from '@/api/tools'

const STATUS_LABELS: Record<string, string> = { ENABLED: '已上线', DISABLED: '已下线', MAINTENANCE: '维护中' }
const STATUS_TYPES: Record<string, 'success' | 'info' | 'warning'> = {
  ENABLED: 'success', DISABLED: 'info', MAINTENANCE: 'warning' }

const tools = ref<AdminTool[]>([])
const categories = ref<AdminCategory[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const keyword = ref('')
const categoryFilter = ref<string | undefined>(undefined)
const statusFilter = ref<string | undefined>(undefined)
const loading = ref(false)
const errorMessage = ref<string | null>(null)

const dialogVisible = ref(false)
const editingCode = ref<string | null>(null)
const saving = ref(false)
const dialogError = ref<string | null>(null)
const form = reactive({ code: '', name: '', description: '', categoryCode: '', icon: '', keywords: '',
  mode: 'LOCAL', requiresLogin: false, status: 'ENABLED', version: 1, sortOrder: 0, featured: false,
  configJson: '{}' })
const dialogTitle = computed(() => editingCode.value ? `编辑工具 · ${editingCode.value}` : '新增工具')
const asTool = (row: unknown): AdminTool => row as AdminTool

function messageFor(error: unknown): string {
  if (error instanceof ApiRequestError) {
    switch (error.code) {
      case 30004: return '工具编码已存在，请更换编码。'
      case 30005: return '所选分类不可用，请刷新分类后重试。'
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
    const result = await listAdminTools({ page: page.value, pageSize: pageSize.value,
      keyword: keyword.value.trim() || undefined, categoryCode: categoryFilter.value, status: statusFilter.value })
    tools.value = result.data.records
    total.value = result.data.total
  } catch (error: unknown) {
    errorMessage.value = messageFor(error)
  } finally {
    loading.value = false
  }
}

async function loadCategories(): Promise<void> {
  try {
    categories.value = (await listAdminCategories()).data
  } catch {
    categories.value = []
  }
}

function search(): void {
  page.value = 1
  void load()
}

function resetFilters(): void {
  keyword.value = ''
  categoryFilter.value = undefined
  statusFilter.value = undefined
  page.value = 1
  void load()
}

function openCreate(): void {
  editingCode.value = null
  dialogError.value = null
  Object.assign(form, { code: '', name: '', description: '', categoryCode: categories.value[0]?.code ?? '',
    icon: '', keywords: '', mode: 'LOCAL', requiresLogin: false, status: 'ENABLED', version: 1, sortOrder: 0,
    featured: false, configJson: '{}' })
  dialogVisible.value = true
}

function openEdit(tool: AdminTool): void {
  editingCode.value = tool.code
  dialogError.value = null
  Object.assign(form, { code: tool.code, name: tool.name, description: tool.description,
    categoryCode: tool.categoryCode, icon: tool.icon ?? '', keywords: tool.keywords.join(', '), mode: tool.mode,
    requiresLogin: tool.requiresLogin, status: tool.status, version: tool.version, sortOrder: tool.sortOrder,
    featured: tool.featured, configJson: tool.configJson || '{}' })
  dialogVisible.value = true
}

async function save(): Promise<void> {
  if (saving.value) return
  dialogError.value = null
  if (!editingCode.value && !/^[a-z][a-z0-9_]{0,63}$/.test(form.code.trim())) {
    dialogError.value = '工具编码需以小写字母开头，只能包含小写字母、数字和下划线。'
    return
  }
  if (!form.name.trim() || form.name.trim().length > 128) {
    dialogError.value = '请填写 1-128 字符的工具名称。'
    return
  }
  if (!form.categoryCode) {
    dialogError.value = '请选择分类。'
    return
  }
  if (!(TOOL_MODES as readonly string[]).includes(form.mode) || !(TOOL_STATUSES as readonly string[]).includes(form.status)) {
    dialogError.value = '运行模式或状态不在允许范围内。'
    return
  }
  if (!Number.isInteger(form.version) || form.version < 1 || form.version > 100000
    || !Number.isInteger(form.sortOrder) || form.sortOrder < 0 || form.sortOrder > 100000) {
    dialogError.value = '版本需为 1-100000，排序需为 0-100000。'
    return
  }
  let config: unknown
  try {
    config = JSON.parse(form.configJson.trim() || '{}')
  } catch {
    dialogError.value = '配置 JSON 不是合法 JSON。'
    return
  }
  if (typeof config !== 'object' || config === null || Array.isArray(config)) {
    dialogError.value = '配置 JSON 必须是对象。'
    return
  }
  const keywords = form.keywords.split(/[,，\n]/).map(item => item.trim()).filter(item => item.length > 0)
  if (keywords.length > 20 || keywords.some(item => item.length > 32)) {
    dialogError.value = '关键词最多 20 个，每个不超过 32 字符。'
    return
  }
  const payload = { name: form.name.trim(), description: form.description.trim(),
    categoryCode: form.categoryCode, icon: form.icon.trim() || null, keywords, mode: form.mode,
    requiresLogin: form.requiresLogin, status: form.status, version: form.version, sortOrder: form.sortOrder,
    featured: form.featured, configJson: JSON.stringify(config) }
  saving.value = true
  try {
    if (editingCode.value) await updateAdminTool(editingCode.value, payload)
    else await createAdminTool({ ...payload, code: form.code.trim() })
    dialogVisible.value = false
    ElMessage.success(editingCode.value ? '工具已更新' : '工具已创建')
    await load()
  } catch (error: unknown) {
    dialogError.value = messageFor(error)
  } finally {
    saving.value = false
  }
}

async function changeStatus(tool: AdminTool, status: string): Promise<void> {
  if (tool.status === status) return
  const label = STATUS_LABELS[status] ?? status
  try {
    await ElMessageBox.confirm(`确认将「${tool.name}」设为${label}？`, '状态变更', { type: 'warning' })
  } catch {
    return
  }
  try {
    await updateAdminToolStatus(tool.code, status)
    ElMessage.success(`已设为${label}`)
    await load()
  } catch (error: unknown) {
    ElMessage.error(messageFor(error))
  }
}

async function remove(tool: AdminTool): Promise<void> {
  try {
    await ElMessageBox.prompt(`删除后公开目录不再返回该工具。请输入工具编码 ${tool.code} 确认。`, '删除工具',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消',
        inputValidator: (value: string) => value === tool.code || '输入的编码不匹配' })
  } catch {
    return
  }
  try {
    await deleteAdminTool(tool.code)
    ElMessage.success('工具已删除')
    await load()
  } catch (error: unknown) {
    ElMessage.error(messageFor(error))
  }
}

onMounted(() => { void loadCategories(); void load() })
</script>

<template>
  <section>
    <div class="page-heading">
      <div>
        <p class="eyebrow">CATALOG</p>
        <h1>工具管理</h1>
        <p class="page-description">维护工具名称、分类、运行模式、排序、推荐与上下线状态；删除为软删除，公开目录立即停止返回。</p>
      </div>
      <ElButton type="primary" @click="openCreate">新增工具</ElButton>
    </div>

    <ElAlert v-if="errorMessage" class="admin-error" type="error" :closable="false" :title="errorMessage" show-icon />

    <div class="admin-filters">
      <ElInput v-model="keyword" placeholder="搜索编码、名称或说明" clearable style="max-width: 240px"
        @keyup.enter="search" @clear="search" />
      <ElSelect v-model="categoryFilter" placeholder="全部分类" clearable style="width: 180px" @change="search">
        <ElOption v-for="item in categories" :key="item.code" :label="`${item.name} (${item.code})`" :value="item.code" />
      </ElSelect>
      <ElSelect v-model="statusFilter" placeholder="全部状态" clearable style="width: 150px" @change="search">
        <ElOption v-for="status in TOOL_STATUSES" :key="status" :label="STATUS_LABELS[status]" :value="status" />
      </ElSelect>
      <ElButton @click="search">查询</ElButton>
      <ElButton text @click="resetFilters">重置</ElButton>
    </div>

    <ElTable v-loading="loading" :data="tools" class="admin-table" row-key="code">
      <ElTableColumn prop="code" label="编码" width="150" />
      <ElTableColumn prop="name" label="名称" min-width="150" show-overflow-tooltip />
      <ElTableColumn label="分类" width="120">
        <template #default="scope">{{ scope.row.categoryName }}<span class="muted"> ({{ scope.row.categoryCode }})</span></template>
      </ElTableColumn>
      <ElTableColumn prop="mode" label="模式" width="90" />
      <ElTableColumn label="状态" width="100">
        <template #default="scope"><ElTag :type="STATUS_TYPES[scope.row.status]">{{ STATUS_LABELS[scope.row.status] }}</ElTag></template>
      </ElTableColumn>
      <ElTableColumn prop="sortOrder" label="排序" width="80" />
      <ElTableColumn label="推荐" width="80">
        <template #default="scope"><ElTag v-if="scope.row.featured" type="warning">推荐</ElTag><span v-else class="muted">—</span></template>
      </ElTableColumn>
      <ElTableColumn prop="version" label="版本" width="70" />
      <ElTableColumn label="需登录" width="80">
        <template #default="scope">{{ scope.row.requiresLogin ? '是' : '否' }}</template>
      </ElTableColumn>
      <ElTableColumn label="操作" width="220" fixed="right">
        <template #default="scope">
          <ElButton size="small" @click="openEdit(asTool(scope.row))">编辑</ElButton>
          <ElDropdown trigger="click" @command="(command: string) => changeStatus(asTool(scope.row), command)">
            <ElButton size="small">状态</ElButton>
            <template #dropdown>
              <ElDropdownMenu>
                <ElDropdownItem command="ENABLED" :disabled="scope.row.status === 'ENABLED'">上线</ElDropdownItem>
                <ElDropdownItem command="DISABLED" :disabled="scope.row.status === 'DISABLED'">下线</ElDropdownItem>
                <ElDropdownItem command="MAINTENANCE" :disabled="scope.row.status === 'MAINTENANCE'">维护</ElDropdownItem>
              </ElDropdownMenu>
            </template>
          </ElDropdown>
          <ElButton size="small" type="danger" plain @click="remove(asTool(scope.row))">删除</ElButton>
        </template>
      </ElTableColumn>
    </ElTable>

    <ElPagination class="admin-pagination" layout="total, prev, pager, next" :total="total"
      :current-page="page" :page-size="pageSize" @current-change="(value: number) => { page = value; void load() }" />

    <ElDialog v-model="dialogVisible" :title="dialogTitle" width="620px" top="6vh">
      <ElAlert v-if="dialogError" class="admin-error" type="error" :closable="false" :title="dialogError" show-icon />
      <ElForm label-position="top">
        <ElFormItem v-if="!editingCode" label="工具编码">
          <ElInput v-model="form.code" maxlength="64" placeholder="小写字母开头，例如 image_compress" />
        </ElFormItem>
        <ElFormItem label="名称"><ElInput v-model="form.name" maxlength="128" /></ElFormItem>
        <ElFormItem label="说明"><ElInput v-model="form.description" type="textarea" :rows="2" maxlength="500" /></ElFormItem>
        <ElFormItem label="分类">
          <ElSelect v-model="form.categoryCode" style="width: 100%">
            <ElOption v-for="item in categories" :key="item.code" :label="`${item.name} (${item.code})`" :value="item.code" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="图标标识"><ElInput v-model="form.icon" maxlength="128" placeholder="可选，例如 tools" /></ElFormItem>
        <ElFormItem label="关键词"><ElInput v-model="form.keywords" type="textarea" :rows="2" placeholder="使用逗号或换行分隔，最多 20 个" /></ElFormItem>
        <ElFormItem label="运行模式">
          <ElSelect v-model="form.mode" style="width: 100%">
            <ElOption v-for="mode in TOOL_MODES" :key="mode" :label="mode" :value="mode" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="状态">
          <ElSelect v-model="form.status" style="width: 100%">
            <ElOption v-for="status in TOOL_STATUSES" :key="status" :label="STATUS_LABELS[status]" :value="status" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="版本"><ElInputNumber v-model="form.version" :min="1" :max="100000" /></ElFormItem>
        <ElFormItem label="排序"><ElInputNumber v-model="form.sortOrder" :min="0" :max="100000" /></ElFormItem>
        <ElFormItem label="首页推荐"><ElSwitch v-model="form.featured" /></ElFormItem>
        <ElFormItem label="需要登录"><ElSwitch v-model="form.requiresLogin" /></ElFormItem>
        <ElFormItem label="配置 JSON（对象）">
          <ElInput v-model="form.configJson" type="textarea" :rows="4" placeholder='例如 {"path":"/tools/demo/"}' />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="dialogVisible = false">取消</ElButton>
        <ElButton type="primary" :loading="saving" @click="save">保存</ElButton>
      </template>
    </ElDialog>
  </section>
</template>
