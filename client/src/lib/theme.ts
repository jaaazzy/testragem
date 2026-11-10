import { create } from 'zustand'

export type ThemePreference = 'light' | 'dark' | 'system'
type ResolvedTheme = Exclude<ThemePreference, 'system'>
const storageKey = 'testragem.theme'
const systemTheme = window.matchMedia('(prefers-color-scheme: dark)')
function preference(value: string | null): ThemePreference { return value === 'light' || value === 'dark' ? value : 'system' }
function storedPreference(): ThemePreference {
  try { return preference(localStorage.getItem(storageKey)) }
  catch { return 'system' }
}
function resolveTheme(value: ThemePreference): ResolvedTheme { return value === 'system' ? systemTheme.matches ? 'dark' : 'light' : value }
function applyTheme(value: ThemePreference) {
  const resolved = resolveTheme(value)
  document.documentElement.dataset.theme = resolved
  document.documentElement.style.colorScheme = resolved
  document.querySelector('meta[name="theme-color"]')?.setAttribute('content', resolved === 'dark' ? '#11141e' : '#ffffff')
  return resolved
}
const initialPreference = storedPreference()
let themeTransition: ViewTransition | undefined
export const useTheme = create<{ preference: ThemePreference; resolvedTheme: ResolvedTheme; setPreference: (value: ThemePreference) => void }>(set => ({
  preference: initialPreference,
  resolvedTheme: resolveTheme(initialPreference),
  setPreference: value => {
    const update = () => {
      const resolvedTheme = applyTheme(value)
      set({ preference: value, resolvedTheme })
      try { localStorage.setItem(storageKey, value) }
      catch { /* The setting still works in memory when storage is unavailable. */ }
    }
    themeTransition?.skipTransition()
    if (document.startViewTransition && !window.matchMedia('(prefers-reduced-motion: reduce)').matches && resolveTheme(value) !== useTheme.getState().resolvedTheme) {
      themeTransition = document.startViewTransition(update)
    } else update()
  },
}))
export function initializeTheme() {
  useTheme.setState({ resolvedTheme: applyTheme(useTheme.getState().preference) })
  systemTheme.addEventListener('change', () => { if (useTheme.getState().preference === 'system') useTheme.setState({ resolvedTheme: applyTheme('system') }) })
  window.addEventListener('storage', event => {
    if (event.key === storageKey || event.key === null) {
      const value = preference(event.newValue)
      useTheme.setState({ preference: value, resolvedTheme: applyTheme(value) })
    }
  })
}
