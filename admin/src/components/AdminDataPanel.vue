<script setup lang="ts">
import { ElAlert, ElButton, ElEmpty, ElIcon } from 'element-plus'
import { Loading } from '@element-plus/icons-vue'
defineProps<{ loading: boolean; hasContent: boolean; error?: string | null; traceId?: string | null; emptyTitle: string; emptyDescription?: string }>()
defineEmits<{ retry: [] }>()
</script>

<template>
  <div class="admin-data-panel" :aria-busy="loading">
    <div v-if="error" class="admin-data-error" role="alert">
      <ElAlert type="error" :title="error" :closable="false" show-icon />
      <div class="admin-data-error-actions">
        <span v-if="hasContent">当前内容保留上次结果，尚未更新。</span>
        <span v-if="traceId" class="admin-trace">请求标识：<code>{{ traceId }}</code></span>
        <ElButton size="small" :disabled="loading" @click="$emit('retry')">重试</ElButton>
      </div>
    </div>
    <div v-if="loading" class="admin-data-progress" :class="{ 'initial-loading': !hasContent }" role="status" aria-live="polite">
      <ElIcon class="is-spinning"><Loading /></ElIcon>{{ hasContent ? '正在更新，暂时显示上次结果…' : '正在加载…' }}
    </div>
    <slot v-if="hasContent" />
    <ElEmpty v-else-if="!loading && !error" :description="emptyTitle">
      <p v-if="emptyDescription" class="admin-empty-help">{{ emptyDescription }}</p>
    </ElEmpty>
  </div>
</template>
