<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElAlert, ElButton, ElInput, ElOption, ElPagination, ElSelect, ElTable, ElTableColumn, ElTag } from 'element-plus'
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
const loading = ref(false)
const errorMessage = ref<string | null>(null)

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
  if (loading.value) return
  loading.value = true
  errorMessage.value = null
  try {
    const result = await listAdminOperationLogs({ page: page.value, pageSize: pageSize.value,
      module: moduleFilter.value, result: resultFilter.value, keyword: keyword.value.trim() || undefined })
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

onMounted(() => { void load() })
</script>

<template>
  <section>
    <div class="page-heading">
      <div>
        <p class="eyebrow">AUDIT</p>
        <h1>操作日志</h1>
        <p class="page-description">查看管理员写操作审计：模块、操作、请求、来源地址与结果；只读，不提供修改或删除入口。</p>
      </div>
    </div>

    <ElAlert v-if="errorMessage" class="admin-error" type="error" :closable="false" :title="errorMessage" show-icon />

    <div class="admin-filters">
      <ElSelect v-model="moduleFilter" placeholder="全部模块" clearable style="width: 170px" @change="search">
        <ElOption v-for="module in MODULES" :key="module" :label="module" :value="module" />
      </ElSelect>
      <ElSelect v-model="resultFilter" placeholder="全部结果" clearable style="width: 150px" @change="search">
        <ElOption v-for="result in RESULTS" :key="result" :label="result" :value="result" />
      </ElSelect>
      <ElInput v-model="keyword" placeholder="搜索操作或请求路径" clearable style="max-width: 240px"
        @keyup.enter="search" @clear="search" />
      <ElButton @click="search">查询</ElButton>
    </div>

    <ElTable v-loading="loading" :data="entries" class="admin-table" row-key="id">
      <ElTableColumn prop="id" label="ID" width="90" />
      <ElTableColumn label="管理员" width="140">
        <template #default="scope"><span :class="{ muted: !scope.row.adminUsername }">{{ scope.row.adminUsername ?? '已删除' }}</span></template>
      </ElTableColumn>
      <ElTableColumn prop="module" label="模块" width="130" />
      <ElTableColumn prop="operation" label="操作" min-width="150" />
      <ElTableColumn label="请求" min-width="260" show-overflow-tooltip>
        <template #default="scope"><span class="muted">{{ scope.row.requestMethod }} {{ scope.row.requestUri }}</span></template>
      </ElTableColumn>
      <ElTableColumn prop="ip" label="来源" width="150" />
      <ElTableColumn label="结果" width="100">
        <template #default="scope"><ElTag :type="RESULT_TYPES[scope.row.result] ?? 'info'">{{ scope.row.result }}</ElTag></template>
      </ElTableColumn>
      <ElTableColumn label="时间" min-width="190">
        <template #default="scope"><span class="muted">{{ scope.row.createdAt }}</span></template>
      </ElTableColumn>
    </ElTable>

    <ElPagination class="admin-pagination" layout="total, prev, pager, next" :total="total"
      :current-page="page" :page-size="pageSize" @current-change="(value: number) => { page = value; void load() }" />
  </section>
</template>
