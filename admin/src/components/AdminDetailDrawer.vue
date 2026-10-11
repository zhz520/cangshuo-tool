<script setup lang="ts">
import { ElDrawer } from 'element-plus'
withDefaults(defineProps<{ modelValue: boolean; title: string; busy?: boolean; width?: string }>(), { busy: false, width: '580px' })
const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()
</script>

<template>
  <ElDrawer :model-value="modelValue" class="admin-detail-drawer" direction="rtl" :size="`min(${width}, 100vw)`"
    :title="title" :show-close="!busy" :close-on-click-modal="!busy" :close-on-press-escape="!busy"
    :before-close="(done: () => void) => { if (!busy) done() }" @update:model-value="emit('update:modelValue', $event)">
    <slot />
    <template v-if="$slots.footer" #footer><slot name="footer" /></template>
  </ElDrawer>
</template>
