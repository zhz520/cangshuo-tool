<script setup lang="ts">
import { ElTag } from 'element-plus'
withDefaults(defineProps<{ label: string; tone?: 'primary' | 'success' | 'warning' | 'danger' | 'info' }>(), { tone: 'info' })
</script>

<template>
  <ElTag :type="tone" class="admin-status-tag" effect="light"><span aria-hidden="true" class="admin-status-dot"></span>{{ label }}</ElTag>
</template>
