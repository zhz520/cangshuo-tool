<script setup lang="ts">
import { nextTick, onMounted, ref, watch } from 'vue'
import { ElAlert } from 'element-plus'
const props = defineProps<{ message: string }>()
const element = ref<HTMLElement | null>(null)
async function focusError(): Promise<void> { await nextTick(); element.value?.focus() }
onMounted(() => { void focusError() })
watch(() => props.message, () => { void focusError() })
</script>

<template>
  <div ref="element" class="admin-error admin-form-error" tabindex="-1" role="alert">
    <ElAlert type="error" :closable="false" :title="message" show-icon />
  </div>
</template>
