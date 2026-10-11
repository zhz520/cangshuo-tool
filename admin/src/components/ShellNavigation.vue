<script setup lang="ts">
import { ElIcon } from 'element-plus'
import { useRoute } from 'vue-router'
import { navigationGroups } from '@/router/navigation'

const route = useRoute()
defineProps<{ collapsed?: boolean }>()
const emit = defineEmits<{ navigate: [] }>()
</script>

<template>
  <nav class="side-navigation" aria-label="主导航">
    <section v-for="group in navigationGroups" :key="group.title" class="nav-group">
      <h2>{{ group.title }}</h2>
      <RouterLink v-for="item in group.items" :key="item.path" :to="item.path"
        class="nav-link" :class="{ active: route.path === item.path }"
        :aria-current="route.path === item.path ? 'page' : undefined"
        :aria-label="item.availability === 'planned' ? `${item.title}，未开放` : item.title"
        :title="collapsed ? item.title : undefined" @click="emit('navigate')">
        <ElIcon aria-hidden="true"><component :is="item.icon" /></ElIcon>
        <span class="nav-label">{{ item.title }}</span>
        <span v-if="item.availability === 'planned'" class="nav-unavailable">未开放</span>
      </RouterLink>
    </section>
  </nav>
</template>
