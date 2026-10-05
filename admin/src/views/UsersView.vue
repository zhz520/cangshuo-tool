<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElAlert, ElButton, ElDialog, ElInput, ElMessage, ElMessageBox, ElOption, ElSelect, ElTable,
  ElTableColumn, ElTag } from 'element-plus'
import { ApiRequestError } from '@/api/client'
import { getAdminUser, listAdminUsers, listAdminUserSessions, listAdminUserSync, updateAdminUserStatus,
  type AdminUser, type AdminUserSession, type AdminUserSyncEntry } from '@/api/users'

const users = ref<AdminUser[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const keyword = ref('')
const enabledFilter = ref<string | undefined>(undefined)
const loading = ref(false)
const errorMessage = ref<string | null>(null)
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<AdminUser | null>(null)
const sessions = ref<AdminUserSession[]>([])
const syncEntries = ref<AdminUserSyncEntry[]>([])
const asUser = (row: unknown): AdminUser => row as AdminUser

function messageFor(error: unknown): string {
  if (error instanceof ApiRequestError) {
    switch (error.code) {
      case 10006: return '用户不存在。'
      case 10001: return '请求参数不合法。'
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
    const result = await listAdminUsers({ page: page.value, pageSize: pageSize.value,
      keyword: keyword.value.trim() || undefined,
      enabled: enabledFilter.value === undefined ? undefined : enabledFilter.value === 'true' })
    users.value = result.data.records
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

async function openDetail(user: AdminUser): Promise<void> {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  sessions.value = []
  syncEntries.value = []
  try {
    const [profile, sessionList, syncList] = await Promise.all([
      getAdminUser(user.id), listAdminUserSessions(user.id), listAdminUserSync(user.id, 20)])
    detail.value = profile.data
    sessions.value = sessionList.data
    syncEntries.value = syncList.data
  } catch (error: unknown) {
    ElMessage.error(messageFor(error))
    detailVisible.value = false
  } finally {
    detailLoading.value = false
  }
}

async function changeStatus(user: AdminUser): Promise<void> {
  const next = !user.enabled
  try {
    await ElMessageBox.confirm(next
      ? `确认启用「${user.email}」？`
      : `停用「${user.email}」会立即撤销其全部登录会话，用户需要重新登录且会被拒绝。确认停用？`,
      next ? '启用用户' : '停用用户', { type: 'warning' })
  } catch {
    return
  }
  try {
    await updateAdminUserStatus(user.id, next)
    ElMessage.success(next ? '用户已启用' : '用户已停用')
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
        <p class="eyebrow">USERS</p>
        <h1>用户管理</h1>
        <p class="page-description">搜索账号、查看最近登录与同步数据规模；停用会立即撤销该用户的全部刷新会话。页面只展示同步条目的键，不展示工具输入内容。</p>
      </div>
    </div>

    <ElAlert v-if="errorMessage" class="admin-error" type="error" :closable="false" :title="errorMessage" show-icon />

    <div class="admin-filters">
      <ElInput v-model="keyword" placeholder="搜索邮箱或昵称" clearable style="max-width: 260px"
        @keyup.enter="search" @clear="search" />
      <ElSelect v-model="enabledFilter" placeholder="全部状态" clearable style="width: 150px" @change="search">
        <ElOption label="已启用" value="true" />
        <ElOption label="已停用" value="false" />
      </ElSelect>
      <ElButton @click="search">查询</ElButton>
    </div>

    <ElTable v-loading="loading" :data="users" class="admin-table" row-key="id">
      <ElTableColumn prop="id" label="ID" width="90" />
      <ElTableColumn prop="email" label="邮箱" min-width="220" />
      <ElTableColumn prop="nickname" label="昵称" width="140" />
      <ElTableColumn label="状态" width="100">
        <template #default="scope"><ElTag :type="scope.row.enabled ? 'success' : 'info'">{{ scope.row.enabled ? '已启用' : '已停用' }}</ElTag></template>
      </ElTableColumn>
      <ElTableColumn label="收藏 / 最近" width="120">
        <template #default="scope">{{ scope.row.favoriteCount }} / {{ scope.row.recentCount }}</template>
      </ElTableColumn>
      <ElTableColumn label="最后登录" min-width="180">
        <template #default="scope"><span class="muted">{{ scope.row.lastLoginAt ?? '—' }}</span></template>
      </ElTableColumn>
      <ElTableColumn label="注册时间" min-width="180">
        <template #default="scope"><span class="muted">{{ scope.row.createdAt }}</span></template>
      </ElTableColumn>
      <ElTableColumn label="操作" width="170" fixed="right">
        <template #default="scope">
          <ElButton size="small" @click="openDetail(asUser(scope.row))">详情</ElButton>
          <ElButton size="small" :type="scope.row.enabled ? 'danger' : 'primary'" plain @click="changeStatus(asUser(scope.row))">
            {{ scope.row.enabled ? '停用' : '启用' }}
          </ElButton>
        </template>
      </ElTableColumn>
    </ElTable>

    <ElDialog v-model="detailVisible" title="用户详情" width="720px" top="6vh">
      <div v-loading="detailLoading">
        <template v-if="detail">
          <p><strong>{{ detail.email }}</strong> · {{ detail.nickname }} · {{ detail.enabled ? '已启用' : '已停用' }}</p>
          <p class="muted">最后登录：{{ detail.lastLoginAt ?? '—' }} · 注册：{{ detail.createdAt }} · 收藏 {{ detail.favoriteCount }} · 最近 {{ detail.recentCount }}</p>
          <h3>登录会话（最多 20 条）</h3>
          <ElTable :data="sessions" size="small">
            <ElTableColumn prop="sessionId" label="ID" width="80" />
            <ElTableColumn prop="sessionCodeMasked" label="会话" width="120" />
            <ElTableColumn prop="createdAt" label="创建" min-width="170" />
            <ElTableColumn prop="expiresAt" label="到期" min-width="170" />
            <ElTableColumn label="状态" width="100">
              <template #default="scope"><ElTag :type="scope.row.revokedAt ? 'info' : 'success'">{{ scope.row.revokedAt ? '已撤销' : '有效' }}</ElTag></template>
            </ElTableColumn>
          </ElTable>
          <h3>同步条目（键与元数据）</h3>
          <ElTable :data="syncEntries" size="small">
            <ElTableColumn prop="entityType" label="类型" width="120" />
            <ElTableColumn prop="entityKey" label="键" min-width="160" />
            <ElTableColumn prop="updatedAtMs" label="更新时间戳" min-width="160" />
            <ElTableColumn label="删除" width="90">
              <template #default="scope">{{ scope.row.deleted ? '是' : '否' }}</template>
            </ElTableColumn>
          </ElTable>
        </template>
      </div>
    </ElDialog>
  </section>
</template>
