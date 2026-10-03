<script setup lang="ts">
import { computed } from 'vue'
import { ElAlert, ElButton, ElIcon, ElMessage } from 'element-plus'
import { ArrowRight, CircleCheck, Clock, Connection, Loading, RefreshRight, Timer, Warning } from '@element-plus/icons-vue'
import { useHealthStore } from '@/stores/health'
import { navigationItems } from '@/router/navigation'

const health = useHealthStore()
const shortcuts = navigationItems.filter(item => ['/tools', '/categories', '/feedback'].includes(item.path))
const heroTitle = computed(() => ({ idle: '准备连接平台服务', loading: '正在检查平台服务', success: '平台服务运行正常', error: '平台服务暂时无法连接' })[health.state])
const heroIcon = computed(() => ({ idle: Connection, loading: Loading, success: CircleCheck, error: Warning })[health.state])
const lastTime = computed(() => health.lastCheckedAt
  ? new Intl.DateTimeFormat('zh-CN', { hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false }).format(health.lastCheckedAt) : '尚未检查')
const lastDate = computed(() => health.lastCheckedAt
  ? new Intl.DateTimeFormat('zh-CN', { year: 'numeric', month: 'long', day: 'numeric' }).format(health.lastCheckedAt) : '刷新后显示检查时间')

async function copyTraceId() {
  if (!health.traceId) return
  try { await navigator.clipboard.writeText(health.traceId); ElMessage.success('请求编号已复制') }
  catch { ElMessage.info('复制未完成，可手动选择请求编号') }
}
</script>

<template>
  <div class="dashboard">
    <div class="page-heading"><div><p class="eyebrow">WORKSPACE</p><h1>概览</h1><p class="page-description">查看平台运行状态，进入你的管理工作。</p></div><span class="heading-note">沧烁工具箱</span></div>

    <section class="health-hero" :class="health.state" aria-label="平台服务状态" aria-live="polite">
      <div class="hero-copy"><span class="hero-status-icon" :class="{ 'is-spinning': health.isChecking }"><ElIcon><component :is="heroIcon" /></ElIcon></span>
        <div><p class="hero-eyebrow">平台状态</p><h2>{{ heroTitle }}</h2><p>服务连接状态与最近一次检查结果，在这里一目了然。</p></div>
      </div>
      <ElButton type="primary" :icon="RefreshRight" :loading="health.isChecking" @click="health.refresh()">{{ health.state === 'error' ? '重新连接' : '刷新状态' }}</ElButton>
    </section>

    <div v-if="health.errorMessage" class="health-error">
      <ElAlert title="服务连接异常" :description="health.errorMessage" type="warning" show-icon :closable="false" />
      <div v-if="health.traceId" class="trace-detail"><span>请求编号</span><code>{{ health.traceId }}</code><ElButton text size="small" @click="copyTraceId">复制编号</ElButton></div>
    </div>

    <section class="metrics-grid" aria-label="连接详情">
      <article class="metric-card"><div class="metric-label">服务连接<ElIcon><Connection /></ElIcon></div><strong class="metric-value" :class="{ positive: health.isConnected }">{{ health.isConnected ? '已连接' : health.isChecking ? '检查中' : health.state === 'error' ? '不可用' : '待检查' }}</strong><p><span class="small-status-dot" :class="health.state"></span>{{ health.isConnected ? '平台服务可以正常响应' : health.isChecking ? '正在获取最新连接状态' : '等待服务响应' }}</p></article>
      <article class="metric-card"><div class="metric-label">请求耗时<ElIcon><Timer /></ElIcon></div><strong class="metric-value">{{ health.elapsedMs ?? '—' }}<small v-if="health.elapsedMs !== null">ms</small></strong><p>最近一次健康检查的请求耗时</p></article>
      <article class="metric-card"><div class="metric-label">最近检查<ElIcon><Clock /></ElIcon></div><strong class="metric-value time-value">{{ lastTime }}</strong><p>{{ lastDate }}</p></article>
    </section>

    <section class="management-section" aria-labelledby="management-title">
      <div class="section-heading"><div><h2 id="management-title">常用管理入口</h2><p>工具、分类与用户反馈，集中管理。</p></div><span class="section-caption">功能将陆续开放</span></div>
      <div class="shortcut-grid">
        <RouterLink v-for="item in shortcuts" :key="item.path" :to="item.path" class="shortcut-card">
          <div class="shortcut-top"><span class="shortcut-icon"><ElIcon><component :is="item.icon" /></ElIcon></span><span class="coming-label">即将开放</span></div>
          <h3>{{ item.title }}</h3><p>{{ item.description }}</p><span class="shortcut-action">查看模块<ElIcon><ArrowRight /></ElIcon></span>
        </RouterLink>
      </div>
    </section>
  </div>
</template>
