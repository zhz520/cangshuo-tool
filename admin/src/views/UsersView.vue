<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElButton, ElInput, ElMessage, ElMessageBox, ElOption, ElPagination, ElSelect, ElTable,
  ElTableColumn } from 'element-plus'
import AdminPageHeader from '@/components/AdminPageHeader.vue'
import AdminFilterBar from '@/components/AdminFilterBar.vue'
import AdminDataPanel from '@/components/AdminDataPanel.vue'
import AdminDetailDrawer from '@/components/AdminDetailDrawer.vue'
import AdminRecordCards from '@/components/AdminRecordCards.vue'
import AdminStatusTag from '@/components/AdminStatusTag.vue'
import { useAdminRequest } from '@/composables/useAdminRequest'
import { ApiRequestError } from '@/api/client'
import { getAdminUser, listAdminUsers, listAdminUserSessions, listAdminUserSync, updateAdminUserStatus,
  type AdminUser, type AdminUserSession, type AdminUserSyncEntry } from '@/api/users'

const users = ref<AdminUser[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const keyword = ref('')
const enabledFilter = ref<string | undefined>(undefined)
const { loading, errorMessage, traceId, run } = useAdminRequest(messageFor)
const detailVisible = ref(false)
const selectedUser = ref<AdminUser | null>(null)
const detailRequest = useAdminRequest(messageFor)
const { loading: detailLoading, errorMessage: detailError, traceId: detailTraceId } = detailRequest
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
  await run(() => listAdminUsers({ page: page.value, pageSize: pageSize.value,
    keyword: keyword.value.trim() || undefined,
    enabled: enabledFilter.value === undefined ? undefined : enabledFilter.value === 'true' }), result => {
    users.value = result.data.records
    total.value = result.data.total
  })
}
function resetFilters(): void { keyword.value = ''; enabledFilter.value = undefined; search() }

function search(): void {
  page.value = 1
  void load()
}

async function openDetail(user: AdminUser): Promise<void> {
  selectedUser.value = user
  detailVisible.value = true
  detail.value = null
  sessions.value = []
  syncEntries.value = []
  await detailRequest.run(() => Promise.all([
    getAdminUser(user.id), listAdminUserSessions(user.id), listAdminUserSync(user.id, 20)]), ([profile, sessionList, syncList]) => {
    detail.value = profile.data
    sessions.value = sessionList.data
    syncEntries.value = syncList.data
  })
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
  <section class="admin-page">
    <AdminPageHeader title="用户管理" description="查看用户账号、登录会话与同步数据规模。">
      <template #actions>
        <ElButton :loading="loading" @click="load">刷新</ElButton>
      </template>
    </AdminPageHeader>
    <AdminFilterBar @search="search" @reset="resetFilters">
      <ElInput v-model="keyword" aria-label="搜索用户" placeholder="搜索邮箱或昵称" clearable style="width: 320px" @clear="search" />
      <ElSelect v-model="enabledFilter" aria-label="用户状态" placeholder="全部状态" clearable style="width: 150px" @change="search">
        <ElOption label="已启用" value="true" />
        <ElOption label="已停用" value="false" />
      </ElSelect>
    </AdminFilterBar>
    <AdminDataPanel :loading="loading" :has-content="users.length > 0" :error="errorMessage" :trace-id="traceId" empty-title="暂无用户" empty-description="尝试调整搜索条件。" @retry="load">
      <ElTable :data="users" class="admin-table admin-desktop-table" row-key="id">
        <ElTableColumn label="用户" min-width="250">
          <template #default="scope">
            <strong class="admin-record-title">{{ scope.row.nickname || '未设置昵称' }}</strong>
            <div class="admin-record-description">{{ scope.row.email }}</div>
            <div class="admin-record-subtitle">#{{ scope.row.id }}</div>
          </template>
        </ElTableColumn>
        <ElTableColumn label="状态" width="110">
          <template #default="scope">
            <AdminStatusTag :tone="scope.row.enabled ? 'success' : 'info'" :label="scope.row.enabled ? '已启用' : '已停用'" />
          </template>
        </ElTableColumn>
        <ElTableColumn label="收藏 / 最近" width="120">
          <template #default="scope">{{ scope.row.favoriteCount }} / {{ scope.row.recentCount }}</template>
        </ElTableColumn>
        <ElTableColumn label="账号时间" min-width="200">
          <template #default="scope">
            <div class="admin-record-subtitle">登录 {{ scope.row.lastLoginAt ?? '尚未登录' }}<br>注册 {{ scope.row.createdAt }}</div>
          </template>
        </ElTableColumn>
        <ElTableColumn label="操作" width="170" fixed="right">
          <template #default="scope">
            <div class="admin-row-actions">
              <ElButton size="small" :disabled="loading" @click="openDetail(asUser(scope.row))">详情</ElButton>
              <ElButton size="small" :disabled="loading" :type="scope.row.enabled ? 'danger' : 'primary'" plain @click="changeStatus(asUser(scope.row))">{{ scope.row.enabled ? '停用' : '启用' }}</ElButton>
            </div>
          </template>
        </ElTableColumn>
      </ElTable>
      <AdminRecordCards :records="users" :record-key="record => record.id">
        <template #title="{ record }">
          <strong class="admin-record-title">{{ record.nickname || '未设置昵称' }}</strong>
          <div class="admin-record-subtitle">#{{ record.id }}</div>
        </template>
        <template #status="{ record }">
          <AdminStatusTag :tone="record.enabled ? 'success' : 'info'" :label="record.enabled ? '已启用' : '已停用'" />
        </template>
        <template #description="{ record }">{{ record.email }}</template>
        <template #meta="{ record }">收藏 {{ record.favoriteCount }} · 最近 {{ record.recentCount }}<br>登录 {{ record.lastLoginAt ?? '尚未登录' }}<br>注册 {{ record.createdAt }}</template>
        <template #actions="{ record }">
          <ElButton size="small" :disabled="loading" @click="openDetail(record)">详情</ElButton>
          <ElButton size="small" :disabled="loading" :type="record.enabled ? 'danger' : 'primary'" plain @click="changeStatus(record)">{{ record.enabled ? '停用' : '启用' }}</ElButton>
        </template>
      </AdminRecordCards>
    </AdminDataPanel>
    <div class="admin-list-footer">
      <span class="admin-result-count">共 {{ total }} 位用户</span>
      <ElPagination class="admin-pagination" layout="prev, pager, next" :pager-count="5" :total="total" :current-page="page" :page-size="pageSize" @current-change="(value: number) => { page = value; void load() }" />
    </div>
    <AdminDetailDrawer v-model="detailVisible" title="用户详情" width="620px">
      <AdminDataPanel :loading="detailLoading" :has-content="detail !== null" :error="detailError" :trace-id="detailTraceId" empty-title="暂无用户详情" @retry="selectedUser && openDetail(selectedUser)">
        <template v-if="detail">
          <section class="admin-detail-section">
            <strong class="admin-record-title">{{ detail.nickname || '未设置昵称' }}</strong>
            <p class="admin-record-description">{{ detail.email }}</p>
            <AdminStatusTag :tone="detail.enabled ? 'success' : 'info'" :label="detail.enabled ? '已启用' : '已停用'" />
            <dl class="admin-detail-fields">
              <div>
                <dt>用户 ID</dt>
                <dd>{{ detail.id }}</dd>
              </div>
              <div>
                <dt>最后登录</dt>
                <dd>{{ detail.lastLoginAt ?? '尚未登录' }}</dd>
              </div>
              <div>
                <dt>注册时间</dt>
                <dd>{{ detail.createdAt }}</dd>
              </div>
              <div>
                <dt>更新时间</dt>
                <dd>{{ detail.updatedAt }}</dd>
              </div>
              <div>
                <dt>同步规模</dt>
                <dd>收藏 {{ detail.favoriteCount }} · 最近 {{ detail.recentCount }}</dd>
              </div>
            </dl>
          </section>
          <section class="admin-detail-section">
            <h3>登录会话 · 最近 20 条</h3>
            <div v-if="sessions.length" class="admin-detail-items">
              <article v-for="session in sessions" :key="session.sessionId" class="admin-detail-item">
                <div class="admin-record-heading">
                  <strong class="admin-record-title">{{ session.sessionCodeMasked }} · #{{ session.sessionId }}</strong>
                  <AdminStatusTag :tone="session.revokedAt ? 'info' : 'success'" :label="session.revokedAt ? '已撤销' : '未撤销'" />
                </div>
                <dl class="admin-detail-fields">
                  <div>
                    <dt>创建</dt>
                    <dd>{{ session.createdAt }}</dd>
                  </div>
                  <div>
                    <dt>到期</dt>
                    <dd>{{ session.expiresAt }}</dd>
                  </div>
                  <div>
                    <dt>最近活跃</dt>
                    <dd>{{ session.lastActiveAt }}</dd>
                  </div>
                  <div v-if="session.revokedAt">
                    <dt>撤销</dt>
                    <dd>{{ session.revokedAt }}</dd>
                  </div>
                </dl>
              </article>
            </div>
            <p v-else class="admin-record-subtitle">暂无登录会话</p>
          </section>
          <section class="admin-detail-section">
            <h3>同步条目 · 最近 20 条</h3>
            <p class="admin-form-help">仅展示键与元数据。</p>
            <div v-if="syncEntries.length" class="admin-detail-items">
              <article v-for="entry in syncEntries" :key="`${entry.entityType}:${entry.entityKey}`" class="admin-detail-item">
                <strong class="admin-record-title">{{ entry.entityKey }}</strong>
                <div class="admin-record-subtitle">{{ entry.entityType }} · {{ entry.deleted ? '已删除' : '未删除' }}<br>更新时间戳 {{ entry.updatedAtMs }}</div>
              </article>
            </div>
            <p v-else class="admin-record-subtitle">暂无同步条目</p>
          </section>
        </template>
      </AdminDataPanel>
      <template #footer>
        <ElButton @click="detailVisible = false">关闭详情</ElButton>
      </template>
    </AdminDetailDrawer>
  </section>
</template>
