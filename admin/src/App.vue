<script setup lang="ts">
import { ElConfigProvider } from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import { useUiStore } from '@/stores/ui'

useUiStore()
</script>

<template>
  <ElConfigProvider :locale="zhCn"><RouterView /></ElConfigProvider>
</template>
