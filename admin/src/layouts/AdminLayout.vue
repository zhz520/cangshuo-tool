<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElButton, ElDrawer, ElDropdown, ElDropdownItem, ElDropdownMenu, ElIcon, ElTag } from 'element-plus'
import { Menu, User, Fold, Expand, Sunny, Moon, ArrowDown } from '@element-plus/icons-vue'
import ShellNavigation from '@/components/ShellNavigation.vue'
import { useHealthStore } from '@/stores/health'
import { useAuthStore } from '@/stores/auth'
import { useUiStore } from '@/stores/ui'

const route = useRoute()
const router = useRouter()
const health = useHealthStore()
const auth = useAuthStore()
const ui = useUiStore()
const drawerOpen = ref(false)
const mainScroll = ref<HTMLElement | null>(null)
const title = computed(() => String(route.meta.title ?? '管理控制台'))
const desktopViewport = window.matchMedia('(min-width: 960px)')
function closeMobileDrawer(event: MediaQueryListEvent): void {
  if (event.matches) drawerOpen.value = false
}
onMounted(() => {
  void health.refresh()
  void auth.ensureProfile().catch(() => undefined)
  desktopViewport.addEventListener('change', closeMobileDrawer)
})
onBeforeUnmount(() => desktopViewport.removeEventListener('change', closeMobileDrawer))
watch(() => route.path, () => {
  drawerOpen.value = false
  mainScroll.value?.scrollTo({ top: 0 })
})

async function onCommand(command: string): Promise<void> {
  if (command !== 'logout') return
  await auth.logout()
  await router.replace('/login')
}
</script>

<template>
  <div class="admin-shell" :class="{ 'sidebar-collapsed': ui.sidebarCollapsed }">
    <a class="skip-link" href="#main-content">跳转到内容</a>
    <aside class="sidebar">
      <RouterLink to="/dashboard" class="brand" aria-label="沧烁工具箱，返回概览">
        <span class="brand-mark" aria-hidden="true">CS</span>
        <span class="brand-content"><strong>沧烁工具箱</strong></span>
      </RouterLink>
      <div class="workspace-context"><span>当前工作区</span><strong>管理控制台</strong></div>
      <ShellNavigation :collapsed="ui.sidebarCollapsed" />
      <div class="sidebar-footer" :title="auth.displayName"><ElIcon><User /></ElIcon><div class="sidebar-account">{{ auth.displayName }}<small>{{ auth.roleLabel || '管理账户' }}</small></div></div>
    </aside>

    <ElDrawer v-model="drawerOpen" class="navigation-drawer" direction="ltr" size="min(300px, 86vw)" title="沧烁工具箱 · 导航">
      <ShellNavigation @navigate="drawerOpen = false" />
    </ElDrawer>

    <div class="main-area">
      <header class="topbar">
        <div class="topbar-left">
          <ElButton class="shell-icon-button mobile-menu" :icon="Menu" aria-label="打开导航" :aria-expanded="drawerOpen" @click="drawerOpen = true" />
          <ElButton class="shell-icon-button desktop-menu" :icon="ui.sidebarCollapsed ? Expand : Fold" :aria-label="ui.sidebarCollapsed ? '展开侧栏' : '收起侧栏'" :aria-expanded="!ui.sidebarCollapsed" @click="ui.toggleSidebar" />
          <div class="breadcrumb"><RouterLink to="/dashboard">工作台</RouterLink><span>/</span><strong>{{ title }}</strong></div>
        </div>
        <div class="topbar-right">
          <ElButton class="shell-icon-button" :icon="ui.theme === 'dark' ? Sunny : Moon" :aria-label="ui.theme === 'dark' ? '切换为浅色主题' : '切换为深色主题'" :title="ui.theme === 'dark' ? '切换为浅色主题' : '切换为深色主题'" @click="ui.toggleTheme" />
          <span class="connection-pill" :class="health.state" role="status" aria-live="polite"><i></i>{{ health.label }}</span>
          <span class="topbar-divider"></span>
          <ElDropdown trigger="click" @command="onCommand">
            <button class="topbar-user" type="button" aria-label="账户菜单">
              <ElIcon><User /></ElIcon><span class="topbar-user-name">{{ auth.displayName }}</span>
              <ElTag v-if="auth.roleLabel" size="small">{{ auth.roleLabel }}</ElTag>
              <ElIcon><ArrowDown /></ElIcon>
            </button>
            <template #dropdown>
              <ElDropdownMenu><ElDropdownItem command="logout" :disabled="auth.loggingOut">退出登录</ElDropdownItem></ElDropdownMenu>
            </template>
          </ElDropdown>
        </div>
      </header>
      <main id="main-content" ref="mainScroll" class="content-scroll" tabindex="-1"><div class="page-container"><RouterView /></div></main>
    </div>
  </div>
</template>
