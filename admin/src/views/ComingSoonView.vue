<script setup lang="ts">
import { ElButton, ElEmpty } from 'element-plus'
import AdminPageHeader from '@/components/AdminPageHeader.vue'
import AdminStatusTag from '@/components/AdminStatusTag.vue'
defineProps<{ title: string; description: string }>()
</script>
<template>
  <section class="admin-page">
    <AdminPageHeader :title="title" :description="description">
      <template #actions>
        <AdminStatusTag label="待开放" tone="info" />
      </template>
    </AdminPageHeader>
    <div class="admin-empty-panel">
      <ElEmpty :image-size="120" :description="`${title}正在准备中`">
        <p class="admin-empty-help">功能开放后，可在这里管理相关设置。</p>
        <RouterLink to="/dashboard">
          <ElButton type="primary">返回概览</ElButton>
        </RouterLink>
      </ElEmpty>
    </div>
  </section>
</template>
