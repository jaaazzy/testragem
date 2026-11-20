export type User = { id: string; email: string; displayName: string; createdAt: string }

export class AuthError extends Error {
  constructor(public status: number, public code: string) {
    super(code)
  }
}

async function request<T>(path: string, body?: object): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' }
  if (body !== undefined) {
    // Get a fresh session-bound token, including after login/logout or expiry.
    const csrf = await request<{ token: string; headerName: string }>('/api/auth/csrf')
    headers[csrf.headerName] = csrf.token
    headers['Content-Type'] = 'application/json'
  }
  const response = await fetch(path, {
    method: body === undefined ? 'GET' : 'POST',
    credentials: 'same-origin',
    cache: 'no-store',
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
    signal: AbortSignal.timeout(15_000),
  })
  if (!response.ok) {
    const error = await response.json().catch(() => ({})) as { error?: string }
    throw new AuthError(response.status, error.error ?? 'request_failed')
  }
  return response.status === 204 ? undefined as T : response.json() as Promise<T>
}

export const authApi = {
  register: (email: string, displayName: string, password: string) =>
    request<User>('/api/auth/register', { email, displayName, password }),
  login: (email: string, password: string) => request<User>('/api/auth/login', { email, password }),
  me: () => request<User>('/api/users/me'),
  logout: () => request<void>('/api/auth/logout', {}),
  verifyEmail: (email: string, code: string) => request<void>('/api/auth/verify-email', { email, code }),
  resendVerification: (email: string) => request<{ message: string }>('/api/auth/resend-verification', { email }),
}

export function authErrorMessage(error: unknown): string {
  if (error instanceof DOMException && error.name === 'TimeoutError') return 'The server took too long to respond. Try again shortly.'
  if (!(error instanceof AuthError)) return 'Cannot reach the server. Check your connection and try again.'
  if (error.code === 'email_already_registered') return 'This email already has an account. Log in instead.'
  if (error.code === 'invalid_credentials') return 'Your email or password is incorrect. Check both and try again.'
  if (error.code === 'email_not_verified') return 'Verify your email before logging in. Enter the code from your inbox or request a new one.'
  if (error.code === 'invalid_or_expired_verification') return 'This code is incorrect, expired, or no longer available. Check the latest email or request a new code.'
  if (error.code === 'email_delivery_unavailable') return 'We could not send the email. Please try again later.'
  if (error.status === 429) return 'Too many attempts. Wait 15 minutes before trying again.'
  if (error.status === 403) return 'Your session changed or expired. Please try again.'
  if (error.status === 400) return 'Check your email and name. Use a password with at least 12 characters; very long passwords may need shortening.'
  if (error.status >= 500) return 'The service is temporarily unavailable. Please try again shortly.'
  return 'The request could not be completed. Please try again.'
}
