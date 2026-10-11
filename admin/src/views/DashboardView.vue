<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElAlert, ElButton, ElEmpty, ElIcon, ElPagination, ElRadioButton, ElRadioGroup, ElTable, ElTableColumn } from 'element-plus'
import { ArrowRight, CircleCheck, Download, RefreshRight, Warning } from '@element-plus/icons-vue'
import AdminPageHeader from '@/components/AdminPageHeader.vue'
import AdminDataPanel from '@/components/AdminDataPanel.vue'
import AdminStatusTag from '@/components/AdminStatusTag.vue'
import { getStatistics, type AdminStatistics, type DailyKey, type SummaryKey } from '@/api/statistics'
import { useAdminRequest } from '@/composables/useAdminRequest'
import { useHealthStore } from '@/stores/health'
import { ApiRequestError } from '@/api/client'

const health = useHealthStore()
const request = useAdminRequest(error => error instanceof ApiRequestError ? error.message : '暂时无法获取统计数据，请稍后重试。')
const days = ref(30), statistics = ref<AdminStatistics | null>(null), metric = ref<DailyKey>('opens')
const dailyPage = ref(1), selectedDate = ref('')
const series: { key: DailyKey; name: string }[] = [
  { key: 'opens', name: '工具打开' }, { key: 'registrations', name: '新增用户' },
  { key: 'activeUsers', name: '活跃用户' }, { key: 'feedback', name: '反馈提交' }, { key: 'submissions', name: '网站投稿' },
]
const mainMetrics: { key: SummaryKey; name: string; note: string; path?: string }[] = [
  { key: 'users', name: '用户总数', note: '当前保留的注册账号', path: '/users' },
  { key: 'newUsers', name: '新增用户', note: '所选时间范围内注册', path: '/users' },
  { key: 'dailyActiveUsers', name: '今日活跃', note: '成功访问 API 的登录用户' },
  { key: 'weeklyActiveUsers', name: '近 7 日活跃', note: '按账号去重，排除管理员' },
  { key: 'allTimeOpens', name: '累计工具打开', note: '包含旧版累计记录', path: '/tools' },
  { key: 'windowOpens', name: '期间工具打开', note: '所选范围内已采集的次数' },
  { key: 'activeMembers', name: '有效会员', note: '启用账号且 PRO 未过期', path: '/users' },
  { key: 'pendingFeedback', name: '待处理反馈', note: '当前待处理，不受日期筛选', path: '/feedback' },
]
const n = (value: number) => new Intl.NumberFormat('zh-CN').format(value)
const timestamp = (value: string) => new Intl.DateTimeFormat('zh-CN', {
  timeZone: 'Asia/Shanghai', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false,
}).format(new Date(value))
const collectedDay = computed(() => statistics.value ? new Intl.DateTimeFormat('sv-SE', {
  timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit',
}).format(new Date(statistics.value.collectionSince)) : '')
const uncollected = (date: string, key: DailyKey) => ['opens', 'activeUsers'].includes(key) && date < collectedDay.value
const chartMax = computed(() => Math.max(1, ...(statistics.value?.daily.map(row => row[metric.value]) ?? [])))
const currentSeries = computed(() => series.find(item => item.key === metric.value)!.name)
const selected = computed(() => statistics.value?.daily.find(row => row.date === selectedDate.value) ?? statistics.value?.daily.at(-1))
const pagedDays = computed(() => [...(statistics.value?.daily ?? [])].reverse().slice((dailyPage.value - 1) * 10, dailyPage.value * 10))
const modeName: Record<string, string> = { LOCAL: '本地工具', SERVER: '服务端工具', HYBRID: '混合工具', WEB: '网页工具' }
const statusName: Record<string, string> = { ENABLED: '已启用', DISABLED: '已停用', MAINTENANCE: '维护中' }
const errorRate = computed(() => {
  const t = statistics.value?.traffic
  return t?.requests ? `${Math.min(100, t.serverErrors / t.requests * 100).toFixed(2)}%` : '—'
})
const bytes = (value: number) => value < 1024 ? `${value} B` : value < 1024 ** 2 ? `${(value / 1024).toFixed(1)} KB`
  : value < 1024 ** 3 ? `${(value / 1024 ** 2).toFixed(1)} MB` : `${(value / 1024 ** 3).toFixed(2)} GB`
async function load() {
  const window = days.value
  await request.run(() => getStatistics(window), result => {
    statistics.value = result.data; dailyPage.value = 1; selectedDate.value = result.data.to
  })
}
function refresh() { void load(); void health.refresh() }
function exportDaily() {
  const data = statistics.value
  if (!data) return
  const rows = [['日期（北京时间）', '新增用户', '工具打开', '活跃用户', '反馈提交', '网站投稿'],
    ...data.daily.map(row => [row.date, row.registrations, uncollected(row.date, 'opens') ? '未采集' : row.opens,
      uncollected(row.date, 'activeUsers') ? '未采集' : row.activeUsers, row.feedback, row.submissions])]
  const url = URL.createObjectURL(new Blob(['\uFEFF' + rows.map(row => row.join(',')).join('\r\n')], { type: 'text/csv;charset=utf-8' }))
  const link = document.createElement('a'); link.href = url; link.download = `toolbox-statistics-${data.from}-${data.to}.csv`
  document.body.appendChild(link); link.click(); link.remove(); setTimeout(() => URL.revokeObjectURL(url), 1000)
}
watch(days, () => { void load() })
onMounted(() => { void load() })
</script>

<template>
  <div class="dashboard statistics-dashboard">
    <AdminPageHeader title="概览" description="用户增长、工具使用和运营待办，查看平台的真实运行情况。">
      <template #actions>
        <ElRadioGroup v-model="days" size="small" aria-label="统计时间范围"><ElRadioButton :value="7">7 天</ElRadioButton><ElRadioButton :value="30">30 天</ElRadioButton><ElRadioButton :value="90">90 天</ElRadioButton></ElRadioGroup>
        <ElButton :icon="RefreshRight" :loading="request.loading.value" @click="refresh">刷新</ElButton>
      </template>
    </AdminPageHeader>
    <div class="statistics-health" role="status">
      <ElIcon><CircleCheck v-if="health.isConnected" /><Warning v-else /></ElIcon>
      <span>{{ health.isConnected ? '平台服务正常' : health.isChecking ? '正在检查服务' : '健康检查尚未通过' }}</span>
      <span v-if="health.elapsedMs !== null">{{ health.elapsedMs }} ms</span>
      <span v-if="statistics" class="statistics-updated">统计更新于 {{ timestamp(statistics.generatedAt) }}</span>
    </div>
    <AdminDataPanel :loading="request.loading.value" :has-content="!!statistics" :error="request.errorMessage.value" :trace-id="request.traceId.value" empty-title="暂无统计数据" @retry="load">
      <template v-if="statistics">
        <p class="statistics-period">{{ statistics.from }} — {{ statistics.to }} · 北京时间 · 当日持续累计。总量与当前状态不受日期筛选影响。</p>
        <section class="statistics-metrics" aria-label="关键指标">
          <article v-for="item in mainMetrics" :key="item.key" class="statistics-metric">
            <RouterLink v-if="item.path" :to="item.path" class="statistics-metric-label">{{ item.name }}<ElIcon><ArrowRight /></ElIcon></RouterLink>
            <span v-else class="statistics-metric-label">{{ item.name }}</span>
            <strong>{{ n(statistics.summary[item.key]) }}</strong><p>{{ item.note }}</p>
          </article>
        </section>
        <section class="statistics-section" aria-labelledby="trend-title">
          <div class="section-heading"><div><h2 id="trend-title">每日趋势</h2><p>工具打开与活跃记录从 {{ timestamp(statistics.collectionSince) }} 开始采集，更早的日期标记为未采集。</p></div></div>
          <ElRadioGroup v-model="metric" size="small" aria-label="趋势指标" class="statistics-series"><ElRadioButton v-for="item in series" :key="item.key" :value="item.key">{{ item.name }}</ElRadioButton></ElRadioGroup>
          <div class="statistics-chart-shell">
            <div class="statistics-chart" :style="{ minWidth: `${statistics.days * 10}px` }" role="group" :aria-label="`${currentSeries}每日趋势，最高 ${chartMax}`">
              <button v-for="row in statistics.daily" :key="row.date" type="button" class="statistics-bar-target" :class="{ selected: row.date === selected?.date, uncollected: uncollected(row.date, metric) }" :aria-pressed="row.date === selected?.date" :aria-label="`${row.date} ${currentSeries}：${uncollected(row.date, metric) ? '未采集' : n(row[metric])}`" :title="`${row.date} · ${uncollected(row.date, metric) ? '未采集' : n(row[metric])}`" @click="selectedDate = row.date">
                <span class="statistics-bar" :style="{ height: `${uncollected(row.date, metric) ? 0 : row[metric] / chartMax * 100}%` }"></span>
              </button>
            </div>
          </div>
          <div class="statistics-chart-labels"><span>{{ statistics.from }}</span><span>最高 {{ n(chartMax === 1 && !statistics.daily.some(row => row[metric]) ? 0 : chartMax) }}</span><span>{{ statistics.to }}</span></div>
          <p v-if="selected" class="statistics-chart-detail" aria-live="polite">{{ selected.date }} · {{ currentSeries }} <strong>{{ uncollected(selected.date, metric) ? '未采集' : n(selected[metric]) }}</strong></p>
          <details class="statistics-daily-details"><summary>查看每日明细</summary>
            <div class="section-heading"><p>采集开始当日可能不足 24 小时。</p><ElButton size="small" :icon="Download" :disabled="request.loading.value" @click="exportDaily">导出日统计 CSV</ElButton></div>
            <ElTable :data="pagedDays" class="admin-table" aria-label="每日统计明细">
              <ElTableColumn prop="date" label="日期" min-width="115" />
              <ElTableColumn label="新增用户" min-width="95"><template #default="{ row }">{{ n(row.registrations) }}</template></ElTableColumn>
              <ElTableColumn label="工具打开" min-width="100"><template #default="{ row }">{{ uncollected(row.date, 'opens') ? '未采集' : n(row.opens) }}</template></ElTableColumn>
              <ElTableColumn label="活跃用户" min-width="100"><template #default="{ row }">{{ uncollected(row.date, 'activeUsers') ? '未采集' : n(row.activeUsers) }}</template></ElTableColumn>
              <ElTableColumn label="反馈提交" min-width="95"><template #default="{ row }">{{ n(row.feedback) }}</template></ElTableColumn>
              <ElTableColumn label="网站投稿" min-width="95"><template #default="{ row }">{{ n(row.submissions) }}</template></ElTableColumn>
            </ElTable>
            <ElPagination v-model:current-page="dailyPage" class="admin-pagination" layout="prev, pager, next, total" :page-size="10" :total="statistics.daily.length" />
          </details>
        </section>
        <section class="statistics-section" aria-labelledby="rank-title">
          <div class="section-heading"><div><h2 id="rank-title">工具使用排行</h2><p>期间使用量优先，累计量用于同分排序；展示前 20 项（含停用和维护工具）。</p></div><RouterLink to="/tools" class="statistics-link">管理工具 →</RouterLink></div>
          <ElTable v-if="statistics.ranking.length" :data="statistics.ranking" class="admin-table">
            <ElTableColumn type="index" label="#" width="50" />
            <ElTableColumn label="工具" min-width="180"><template #default="{ row }"><strong class="statistics-tool-name">{{ row.name }}</strong><span class="statistics-code">{{ row.code }}</span></template></ElTableColumn>
            <ElTableColumn prop="category" label="分类" min-width="110" />
            <ElTableColumn label="状态" min-width="95"><template #default="{ row }"><AdminStatusTag :label="statusName[row.status] ?? row.status" :tone="row.status === 'ENABLED' ? 'success' : row.status === 'MAINTENANCE' ? 'warning' : 'info'" /></template></ElTableColumn>
            <ElTableColumn label="期间打开" min-width="110" align="right" sortable sort-by="opens"><template #default="{ row }">{{ n(row.opens) }}</template></ElTableColumn>
            <ElTableColumn label="累计打开" min-width="110" align="right" sortable sort-by="allTimeOpens"><template #default="{ row }">{{ n(row.allTimeOpens) }}</template></ElTableColumn>
          </ElTable><ElEmpty v-else description="暂无工具" />
        </section>
        <div class="statistics-split">
          <section class="statistics-section"><div class="section-heading"><div><h2>运营待办</h2><p>当前状态统计，不受日期筛选影响。</p></div></div>
            <RouterLink to="/site-links" class="statistics-action-row"><span>网站投稿待审核</span><strong>{{ n(statistics.summary.pendingSites) }}</strong><ElIcon><ArrowRight /></ElIcon></RouterLink>
            <RouterLink to="/feedback" class="statistics-action-row"><span>反馈处理中</span><strong>{{ n(statistics.summary.processingFeedback) }}</strong><ElIcon><ArrowRight /></ElIcon></RouterLink>
            <RouterLink to="/users" class="statistics-action-row"><span>已过期 PRO 账号</span><strong>{{ n(statistics.summary.expiredMembers) }}</strong><ElIcon><ArrowRight /></ElIcon></RouterLink>
            <dl class="statistics-facts"><div><dt>投稿已通过 / 已拒绝</dt><dd>{{ n(statistics.summary.approvedSites) }} / {{ n(statistics.summary.rejectedSites) }}</dd></div><div><dt>反馈已解决 / 总数</dt><dd>{{ n(statistics.summary.resolvedFeedback) }} / {{ n(statistics.summary.feedbackTotal) }}</dd></div><div><dt>用户启用 / 停用</dt><dd>{{ n(statistics.summary.enabledUsers) }} / {{ n(statistics.summary.disabledUsers) }}</dd></div><div><dt>期间活跃账号（去重）</dt><dd>{{ n(statistics.summary.activeUsers) }}</dd></div></dl>
          </section>
          <section class="statistics-section"><div class="section-heading"><div><h2>资源与工具</h2><p>云端逻辑存储用量，不包含备份与对象存储残留。</p></div></div>
            <dl class="statistics-facts"><div><dt>云端文件</dt><dd>{{ n(statistics.summary.storedFiles) }} 个 · {{ bytes(statistics.summary.storedBytes) }}</dd></div><div><dt>已同步收藏 / 最近记录</dt><dd>{{ n(statistics.summary.syncedFavorites) }} / {{ n(statistics.summary.syncedRecents) }}</dd></div><div><dt>工具启用 / 停用 / 维护</dt><dd>{{ n(statistics.summary.enabledTools) }} / {{ n(statistics.summary.disabledTools) }} / {{ n(statistics.summary.maintenanceTools) }}</dd></div><div><dt>AI 尝试次数（UTC 配额日）</dt><dd>{{ n(statistics.summary.aiAttempts) }}</dd></div></dl>
            <p class="statistics-note">AI 按所选日期对应的 UTC 配额日汇总，含失败尝试；当前未接入支付，不展示收入或付费转化。</p>
            <div class="statistics-mode-list"><span v-for="item in statistics.modes" :key="item.code">{{ modeName[item.code] || item.name }} <strong>{{ n(item.count) }}</strong></span></div>
          </section>
        </div>
        <section class="statistics-section"><div class="section-heading"><div><h2>工具分类分布</h2><p>当前未删除工具与其累计打开量。</p></div></div>
          <div class="statistics-category-grid"><RouterLink v-for="item in statistics.categories" :key="item.code" :to="{ path: '/tools', query: { categoryCode: item.code } }" class="statistics-category"><span>{{ item.name }}</span><strong>{{ n(item.count) }} <small>个工具</small></strong><p>{{ n(item.opens) }} 次累计打开</p></RouterLink></div>
        </section>
        <section class="statistics-section"><div class="section-heading"><div><h2>API 请求与错误</h2><p>自 {{ timestamp(statistics.traffic.since) }} 服务启动以来，重启后清零；排除健康检查和 OPTIONS，不受日期筛选影响。</p></div></div>
          <div class="statistics-traffic"><div><span>请求总数</span><strong>{{ n(statistics.traffic.requests) }}</strong></div><div><span>4xx 客户端错误</span><strong>{{ n(statistics.traffic.clientErrors) }}</strong></div><div><span>5xx 服务端错误</span><strong>{{ n(statistics.traffic.serverErrors) }} <small>{{ errorRate }}</small></strong></div><div><span>平均请求耗时</span><strong>{{ statistics.traffic.averageMs.toFixed(2) }} <small>ms</small></strong></div></div>
          <ElAlert v-if="statistics.traffic.activityWriteFailures" type="warning" :closable="false" :title="`活跃统计写入失败 ${n(statistics.traffic.activityWriteFailures)} 次，活跃人数可能偏低，请检查服务日志。`" />
          <p class="statistics-note">请求耗时包含鉴权与接口处理。匿名工具打开次数不是独立访客数，也不表示工具处理成功次数。</p>
        </section>
      </template>
    </AdminDataPanel>
  </div>
</template>
