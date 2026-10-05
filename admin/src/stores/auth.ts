import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { ApiRequestError } from '@/api/client'
import { clearAdminToken, readAdminToken, writeAdminToken } from '@/api/token'
import { fetchAdminMe, loginAdmin, logoutAdmin, type AdminProfile } from '@/api/auth'

const ROLE_LABELS: Record<string, string> = { SUPER_ADMIN: '超级管理员', ADMIN: '管理员' }

export const useAuthStore = defineStore('admin-auth', () => {
  const token = ref<string | null>(readAdminToken())
  const profile = ref<AdminProfile | null>(null)
  const loggingIn = ref(false)
  const loggingOut = ref(false)
  const isAuthenticated = computed(() => token.value !== null)
  const displayName = computed(() => profile.value?.nickname || profile.value?.username || '管理员')
  const roleLabel = computed(() => profile.value ? ROLE_LABELS[profile.value.roleCode] ?? profile.value.roleCode : '')

  async function login(username: string, password: string): Promise<void> {
    if (loggingIn.value) return
    loggingIn.value = true
    try {
      const result = await loginAdmin(username, password)
      token.value = result.data.accessToken
      writeAdminToken(result.data.accessToken)
      profile.value = result.data.admin
    } finally {
      loggingIn.value = false
    }
  }

  async function ensureProfile(): Promise<boolean> {
    if (token.value === null) return false
    if (profile.value !== null) return true
    try {
      profile.value = (await fetchAdminMe()).data
      return true
    } catch (error: unknown) {
      if (error instanceof ApiRequestError && error.status === 401) {
        signOut()
        return false
      }
      throw error
    }
  }

  async function logout(): Promise<void> {
    if (loggingOut.value) return
    loggingOut.value = true
    try {
      if (token.value !== null) await logoutAdmin()
    } catch {
      // Local sign-out still completes when the audit call cannot reach the server.
    } finally {
      loggingOut.value = false
      signOut()
    }
  }

  function signOut(): void {
    token.value = null
    profile.value = null
    clearAdminToken()
  }

  return { token, profile, loggingIn, loggingOut, isAuthenticated, displayName, roleLabel, login, ensureProfile, logout, signOut }
})
