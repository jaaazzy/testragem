import { create } from 'zustand'
import { authApi, AuthError, type User } from './api'
import { useBacktestDraftStore } from '../backtests/stores/use-backtest-draft-store'

let authRevision = 0
let draftOwnerId: string | null = null

function adoptIdentity(user: User) {
  if (draftOwnerId !== user.id) useBacktestDraftStore.getState().reset()
  draftOwnerId = user.id
}

// Identity is held in memory; the browser owns the HttpOnly session cookie.
export const useAuth = create<{
  user: User | null
  loading: boolean
  error: string | null
  refresh: () => Promise<void>
  signIn: (email: string, password: string) => Promise<void>
  signOut: () => Promise<void>
}>((set, get) => ({
  user: null,
  loading: true,
  error: null,
  refresh: async () => {
    const revision = authRevision
    set({ error: null, loading: !get().user })
    try {
      const user = await authApi.me()
      if (revision === authRevision) {
        adoptIdentity(user)
        set({ user, loading: false, error: null })
      }
    }
    catch (error) {
      if (revision !== authRevision) return
      if (error instanceof AuthError && error.status === 401) set({ user: null, loading: false, error: null })
      else set({ loading: false, error: 'We could not check your session. Check your connection and try again.' })
    }
  },
  signIn: async (email, password) => {
    const revision = ++authRevision
    const user = await authApi.login(email, password)
    if (revision === authRevision) {
      adoptIdentity(user)
      set({ user, loading: false, error: null })
    }
  },
  signOut: async () => {
    ++authRevision
    await authApi.logout()
    useBacktestDraftStore.getState().reset()
    draftOwnerId = null
    set({ user: null, loading: false, error: null })
  },
}))
