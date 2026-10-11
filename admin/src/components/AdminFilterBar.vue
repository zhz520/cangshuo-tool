<script setup lang="ts">
import { ElButton } from 'element-plus'
defineProps<{ busy?: boolean }>()
defineEmits<{ search: []; reset: [] }>()
</script>

<template>
  <form class="admin-filters admin-filter-bar" role="search" aria-label="列表筛选" @submit.prevent="$emit('search')">
    <slot />
    <div class="admin-filter-actions">
      <ElButton native-type="submit" :disabled="busy">查询</ElButton>
      <ElButton native-type="button" :disabled="busy" @click="$emit('reset')">重置</ElButton>
    </div>
  </form>
</template>
