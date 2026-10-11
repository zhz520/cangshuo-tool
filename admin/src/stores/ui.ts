import { onScopeDispose, ref, watch } from 'vue'
import { defineStore } from 'pinia'

export type AdminTheme = 'dark' | 'light'
const THEME_KEY = 'cangshuo.admin.ui.theme'
const SIDEBAR_KEY = 'cangshuo.admin.ui.sidebar'

function readPreference(key: string): string | null {
  try { return localStorage.getItem(key) } catch { return null }
}

function savePreference(key: string, value: string): void {
  try { localStorage.setItem(key, value) } catch { /* Keep the in-memory preference usable. */ }
}

export const useUiStore = defineStore('ui', () => {
  const initialTheme = document.documentElement.dataset.adminTheme ?? readPreference(THEME_KEY)
  const theme = ref<AdminTheme>(initialTheme === 'dark' ? 'dark' : 'light')
  const sidebarCollapsed = ref(readPreference(SIDEBAR_KEY) === 'collapsed')

  watch(theme, value => {
    const root = document.documentElement
    root.classList.toggle('dark', value === 'dark')
    root.dataset.adminTheme = value
    root.style.colorScheme = value
    root.style.backgroundColor = value === 'dark' ? '#171717' : '#ffffff'
    document.querySelector('meta[name="theme-color"]')?.setAttribute('content', value === 'dark' ? '#171717' : '#ffffff')
  }, { immediate: true, flush: 'sync' })

  function toggleTheme(): void {
    theme.value = theme.value === 'dark' ? 'light' : 'dark'
    savePreference(THEME_KEY, theme.value)
  }

  function toggleSidebar(): void {
    sidebarCollapsed.value = !sidebarCollapsed.value
    savePreference(SIDEBAR_KEY, sidebarCollapsed.value ? 'collapsed' : 'expanded')
  }

  function onStorage(event: StorageEvent): void {
    try {
      if (event.storageArea !== localStorage) return
    } catch { return }
    if (event.key === THEME_KEY || event.key === null) theme.value = event.newValue === 'dark' ? 'dark' : 'light'
    if (event.key === SIDEBAR_KEY || event.key === null) sidebarCollapsed.value = event.newValue === 'collapsed'
  }

  window.addEventListener('storage', onStorage)
  onScopeDispose(() => window.removeEventListener('storage', onStorage))

  return { theme, sidebarCollapsed, toggleTheme, toggleSidebar }
})
