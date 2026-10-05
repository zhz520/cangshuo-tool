const STORAGE_KEY = 'cangshuo.admin.token'

export function readAdminToken(): string | null {
  try {
    return window.localStorage.getItem(STORAGE_KEY)
  } catch {
    return null
  }
}

export function writeAdminToken(token: string): void {
  try {
    window.localStorage.setItem(STORAGE_KEY, token)
  } catch {
    // Storage can be unavailable in hardened browsers; the session then lasts for this tab only.
  }
}

export function clearAdminToken(): void {
  try {
    window.localStorage.removeItem(STORAGE_KEY)
  } catch {
    // Ignore removal failures; the in-memory store is cleared regardless.
  }
}
