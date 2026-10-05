<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElButton, ElDrawer, ElDropdown, ElDropdownItem, ElDropdownMenu, ElIcon, ElTag } from 'element-plus'
import { Menu, User, Grid } from '@element-plus/icons-vue'
import ShellNavigation from '@/components/ShellNavigation.vue'
import { useHealthStore } from '@/stores/health'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const health = useHealthStore()
const auth = useAuthStore()
const drawerOpen = ref(false)
const title = computed(() => String(route.meta.title ?? '管理控制台'))
onMounted(() => { void health.refresh(); void auth.ensureProfile().catch(() => undefined) })

async function onCommand(command: string): Promise<void> {
  if (command !== 'logout') return
  await auth.logout()
  await router.replace('/login')
}
</script>

<template>
  <div class="admin-shell">
    <aside class="sidebar">
      <RouterLink to="/dashboard" class="brand">
        <span class="brand-mark"><ElIcon><Grid /></ElIcon></span>
        <span><strong>沧烁工具箱</strong><small>管理控制台</small></span>
      </RouterLink>
      <ShellNavigation />
      <div class="sidebar-footer"><span class="workspace-mark"></span><div>本地工作区<small>小工具，大效率。</small></div></div>
    </aside>

    <ElDrawer v-model="drawerOpen" class="navigation-drawer" direction="ltr" size="250px" :with-header="false">
      <div class="drawer-brand">沧烁工具箱<span>管理控制台</span></div>
      <ShellNavigation @navigate="drawerOpen = false" />
    </ElDrawer>

    <div class="main-area">
      <header class="topbar">
        <div class="topbar-left">
          <ElButton class="mobile-menu" :icon="Menu" aria-label="打开导航" @click="drawerOpen = true" />
          <div class="breadcrumb"><RouterLink to="/dashboard">工作台</RouterLink><span>/</span><strong>{{ title }}</strong></div>
        </div>
        <div class="topbar-right">
          <span class="connection-pill" :class="health.state" role="status" aria-live="polite"><i></i>{{ health.label }}</span>
          <span class="topbar-divider"></span>
          <ElDropdown trigger="click" @command="onCommand">
            <span class="topbar-user" role="button" tabindex="0">
              <ElIcon><User /></ElIcon>{{ auth.displayName }}
              <ElTag v-if="auth.roleLabel" size="small">{{ auth.roleLabel }}</ElTag>
            </span>
            <template #dropdown>
              <ElDropdownMenu><ElDropdownItem command="logout" :disabled="auth.loggingOut">退出登录</ElDropdownItem></ElDropdownMenu>
            </template>
          </ElDropdown>
        </div>
      </header>
      <main id="main-content" class="page-container"><RouterView /></main>
      <footer class="page-footer">沧烁工具箱 · 管理控制台</footer>
    </div>
  </div>
</template>
