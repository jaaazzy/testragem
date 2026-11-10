export const paths = {
  home: '/',
  login: '/login',
  signup: '/signup',
  verifyEmail: '/verify-email',
  workspace: '/workspace',
} as const

/** Accept only local workspace destinations, including old hash-style next links. */
export function safeReturnTo(value: string | null): string {
  const candidate = value?.startsWith('#/') ? value.slice(1) : value
  if (!candidate?.startsWith('/') || candidate.startsWith('//') || [...candidate].some(character => character === '\\' || character.charCodeAt(0) <= 32 || character.charCodeAt(0) === 127)) return paths.workspace
  try {
    const url = new URL(candidate, 'https://testragem.invalid')
    if (url.origin !== 'https://testragem.invalid' || (url.pathname !== paths.workspace && !url.pathname.startsWith('/workspace/'))) return paths.workspace
    return url.pathname + url.search + url.hash
  } catch { return paths.workspace }
}
