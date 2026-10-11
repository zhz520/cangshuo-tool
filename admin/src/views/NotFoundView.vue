<script setup lang="ts">
import { ElButton, ElResult } from 'element-plus'
</script>
<template>
  <section class="admin-empty-panel">
    <ElResult icon="warning" title="页面不存在" sub-title="这个页面可能已移动，请返回概览。">
      <template #extra>
        <RouterLink to="/dashboard">
          <ElButton type="primary">返回概览</ElButton>
        </RouterLink>
      </template>
    </ElResult>
  </section>
</template>
