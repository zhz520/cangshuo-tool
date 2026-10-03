<script setup lang="ts">
import { ElButton, ElEmpty, ElTag } from 'element-plus'
defineProps<{ title: string; description: string }>()
</script>

<template>
  <div><div class="page-heading"><div><p class="eyebrow">MANAGEMENT</p><h1>{{ title }}</h1><p class="page-description">{{ description }}</p></div><ElTag type="info" effect="plain" round>即将开放</ElTag></div>
    <section class="empty-panel"><ElEmpty :image-size="150" :description="`${title}正在准备中`"><p class="empty-description">功能开放后，你可以在这里开始管理。</p><RouterLink to="/dashboard"><ElButton type="primary">返回概览</ElButton></RouterLink></ElEmpty></section>
  </div>
</template>
