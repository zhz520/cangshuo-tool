<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { ElAlert, ElButton, ElForm, ElFormItem, ElInput, ElMessage, ElOption, ElPagination, ElSelect, ElTable, ElTableColumn } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'
import AdminPageHeader from '@/components/AdminPageHeader.vue'
import AdminFilterBar from '@/components/AdminFilterBar.vue'
import AdminDataPanel from '@/components/AdminDataPanel.vue'
import AdminStatusTag from '@/components/AdminStatusTag.vue'
import AdminDetailDrawer from '@/components/AdminDetailDrawer.vue'
import { FEEDBACK_STATUSES, type AdminFeedback } from '@/api/feedback'
import { useFeedbackWorkspace, FEEDBACK_STATUS_LABELS, FEEDBACK_STATUS_TONES, FEEDBACK_TYPE_LABELS, type FeedbackStatus } from '@/composables/useFeedbackWorkspace'

const { entries, total, page, pageSize, keyword, statusFilter, loading, loaded, errorMessage, errorTraceId,
  detailVisible, editing, saving, detailError, detailTraceId, form, hasFilters, detailTitle,
  load, search, selectStatus, reset, changePage, open, save } = useFeedbackWorkspace()
const statusTabs: { value: FeedbackStatus | undefined; label: string }[] = [
  { value: undefined, label: '全部' }, ...FEEDBACK_STATUSES.map(value => ({ value, label: FEEDBACK_STATUS_LABELS[value] })),
]
const emptyTitle = computed(() => hasFilters.value ? '没有匹配的反馈' : '暂无用户反馈')
const asEntry = (row: unknown): AdminFeedback => row as AdminFeedback
const detailErrorElement = ref<HTMLElement | null>(null)
watch(detailError, async value => {
  if (!value) return
  await nextTick()
  if (detailVisible.value && detailError.value === value) detailErrorElement.value?.focus()
})
function statusLabel(value: string): string { return FEEDBACK_STATUS_LABELS[value as FeedbackStatus] ?? value }
function statusTone(value: string) { return FEEDBACK_STATUS_TONES[value as FeedbackStatus] ?? 'info' }
function displayTime(value: string | null): string {
  if (!value) return '—'
  const date = new Date(value)
  if (!Number.isFinite(date.getTime())) return value
  return new Intl.DateTimeFormat('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false }).format(date)
}
async function saveFeedback(): Promise<void> {
  if (await save()) ElMessage.success('反馈已更新')
}
onMounted(() => { void load() })
</script>

<template>
  <section class="feedback-workspace">
    <AdminPageHeader title="用户反馈" description="查看问题与建议，跟进处理进度。">
      <template #actions><ElButton :icon="Refresh" :loading="loading" :disabled="saving" @click="load">刷新</ElButton></template>
    </AdminPageHeader>

    <div class="admin-status-tabs" role="group" aria-label="反馈状态筛选">
      <button v-for="tab in statusTabs" :key="tab.value ?? 'ALL'" type="button" class="admin-status-tab"
        :class="{ selected: statusFilter === tab.value }" :aria-pressed="statusFilter === tab.value" :disabled="saving"
        @click="selectStatus(tab.value)">{{ tab.label }}</button>
    </div>

    <AdminFilterBar :busy="saving" @search="search" @reset="reset">
      <ElInput v-model="keyword" class="feedback-search" aria-label="搜索反馈" placeholder="搜索内容、联系方式或用户邮箱"
        :prefix-icon="Search" maxlength="128" clearable :disabled="saving" @clear="search" />
    </AdminFilterBar>

    <AdminDataPanel :loading="loading" :has-content="entries.length > 0" :error="errorMessage" :trace-id="errorTraceId"
      :empty-title="emptyTitle" :empty-description="hasFilters ? '调整搜索词或切换状态后重试。' : '用户提交的问题和建议会显示在这里。'" @retry="load">
      <ElTable :data="entries" class="admin-table feedback-desktop-table" row-key="id">
        <ElTableColumn label="反馈内容" min-width="280">
          <template #default="scope"><div class="feedback-summary">{{ scope.row.content }}</div><p class="feedback-row-meta">#{{ scope.row.id }}<span>·</span>{{ FEEDBACK_TYPE_LABELS[scope.row.type] ?? scope.row.type }}<span v-if="scope.row.reply">· 已回复</span></p></template>
        </ElTableColumn>
        <ElTableColumn label="提交者" min-width="180">
          <template #default="scope"><span class="feedback-email">{{ scope.row.userEmail }}</span></template>
        </ElTableColumn>
        <ElTableColumn label="状态" width="110">
          <template #default="scope"><AdminStatusTag :label="statusLabel(scope.row.status)" :tone="statusTone(scope.row.status)" /></template>
        </ElTableColumn>
        <ElTableColumn label="提交时间" width="165">
          <template #default="scope"><time class="feedback-time" :datetime="scope.row.createdAt" :title="scope.row.createdAt">{{ displayTime(scope.row.createdAt) }}</time></template>
        </ElTableColumn>
        <ElTableColumn label="操作" width="90" fixed="right">
          <template #default="scope"><ElButton size="small" :disabled="saving" :aria-label="`处理反馈 #${scope.row.id}`" @click="open(asEntry(scope.row))">处理</ElButton></template>
        </ElTableColumn>
      </ElTable>

      <div class="feedback-mobile-list" aria-label="反馈列表">
        <article v-for="entry in entries" :key="entry.id" class="feedback-mobile-card">
          <div class="feedback-mobile-heading"><p class="feedback-row-meta">#{{ entry.id }} · {{ FEEDBACK_TYPE_LABELS[entry.type] ?? entry.type }}</p><AdminStatusTag :label="statusLabel(entry.status)" :tone="statusTone(entry.status)" /></div>
          <p class="feedback-summary">{{ entry.content }}</p>
          <p class="feedback-email">{{ entry.userEmail }}</p>
          <div class="feedback-mobile-footer"><time class="feedback-time" :datetime="entry.createdAt">{{ displayTime(entry.createdAt) }}</time><ElButton :disabled="saving" :aria-label="`处理反馈 #${entry.id}`" @click="open(entry)">{{ entry.reply ? '查看与处理 · 已回复' : '查看与处理' }}</ElButton></div>
        </article>
      </div>
    </AdminDataPanel>

    <div v-if="loaded" class="admin-list-footer">
      <span class="admin-result-count">{{ loading || errorMessage ? '上次结果' : '当前筛选' }} · 共 {{ total }} 条</span>
      <ElPagination :layout="'prev, pager, next'" :total="total" :current-page="page" :page-size="pageSize" :pager-count="5" :disabled="saving" @current-change="changePage" />
    </div>

    <AdminDetailDrawer v-model="detailVisible" :title="detailTitle" :busy="saving">
      <template v-if="editing">
        <div v-if="detailError" ref="detailErrorElement" class="feedback-detail-error" tabindex="-1" role="alert">
          <ElAlert class="admin-error" type="error" :closable="false" :title="detailError" show-icon />
          <p v-if="detailTraceId" class="admin-trace">请求标识：<code>{{ detailTraceId }}</code></p>
        </div>
        <div class="feedback-detail-summary"><AdminStatusTag :label="statusLabel(editing.status)" :tone="statusTone(editing.status)" /><span>{{ FEEDBACK_TYPE_LABELS[editing.type] ?? editing.type }}</span></div>
        <dl class="feedback-detail-fields">
          <div><dt>提交者</dt><dd>{{ editing.userEmail }}<span class="feedback-detail-user-id">账号 #{{ editing.userId }}</span></dd></div>
          <div><dt>联系方式</dt><dd>{{ editing.contact || '未填写' }}</dd></div>
          <div><dt>提交时间</dt><dd><time :datetime="editing.createdAt" :title="editing.createdAt">{{ displayTime(editing.createdAt) }}</time></dd></div>
          <div><dt>最近更新</dt><dd><time :datetime="editing.updatedAt" :title="editing.updatedAt">{{ displayTime(editing.updatedAt) }}</time></dd></div>
        </dl>
        <h3 class="feedback-detail-heading">反馈原文</h3>
        <pre class="feedback-content">{{ editing.content }}</pre>
        <section v-if="editing.reply" class="feedback-existing-reply">
          <h3 class="feedback-detail-heading">已有回复</h3>
          <pre class="feedback-content">{{ editing.reply }}</pre>
          <p class="feedback-row-meta">回复时间：<time :datetime="editing.repliedAt ?? undefined" :title="editing.repliedAt ?? undefined">{{ displayTime(editing.repliedAt) }}</time></p>
        </section>
        <ElForm label-position="top" :disabled="saving" @submit.prevent="saveFeedback">
          <ElFormItem label="处理状态"><ElSelect v-model="form.status" aria-label="处理状态" style="width: 100%"><ElOption v-for="status in FEEDBACK_STATUSES" :key="status" :label="FEEDBACK_STATUS_LABELS[status]" :value="status" /></ElSelect></ElFormItem>
          <ElFormItem label="回复（可选，提交者可见）"><ElInput v-model="form.reply" aria-label="回复内容" type="textarea" :rows="5" maxlength="2000" show-word-limit placeholder="填写用户可见的处理说明" /></ElFormItem>
          <p class="feedback-form-help">{{ editing.reply ? '可修改已有回复；已有回复不能清空。' : '仅更新状态时可留空；填写后将对提交者可见。' }}</p>
        </ElForm>
      </template>
      <template #footer><ElButton :disabled="saving" @click="detailVisible = false">取消</ElButton><ElButton type="primary" :loading="saving" @click="saveFeedback">保存更新</ElButton></template>
    </AdminDetailDrawer>
  </section>
</template>
