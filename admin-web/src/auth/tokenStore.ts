const STORAGE_KEY = 'crm.auth.tokens'

export interface StoredTokens {
  accessToken: string
  refreshToken: string
}

export function loadTokens(): StoredTokens | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return null
    const parsed = JSON.parse(raw) as StoredTokens
    return parsed?.accessToken ? parsed : null
  } catch {
    return null
  }
}

export function saveTokens(tokens: StoredTokens): void {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(tokens))
}

export function clearTokens(): void {
  localStorage.removeItem(STORAGE_KEY)
}
