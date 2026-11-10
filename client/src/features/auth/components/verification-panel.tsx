import { Link, createSearchParams, useSearchParams } from 'react-router'
import { safeReturnTo } from '@/app/paths'
import { useRef, useState, type FormEvent } from 'react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { authApi, authErrorMessage, AuthError } from '../api'

export function VerificationPanel({ initialEmail = '', sent = false }: { initialEmail?: string; sent?: boolean }) {
  const [searchParams] = useSearchParams()
  const loginSearch = searchParams.has('next') ? `?${createSearchParams({ next: safeReturnTo(searchParams.get('next')) })}` : ''
  const [email, setEmail] = useState(initialEmail)
  const [code, setCode] = useState('')
  const [pending, setPending] = useState<'verify' | 'resend' | null>(null)
  const [verified, setVerified] = useState(false)
  const [emailError, setEmailError] = useState('')
  const [codeError, setCodeError] = useState('')
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')
  const emailInput = useRef<HTMLInputElement>(null)
  const codeInput = useRef<HTMLInputElement>(null)

  function validEmail(): boolean {
    if (!email.trim() || !emailInput.current?.validity.valid) {
      setEmailError('Enter the email address you used to sign up.')
      emailInput.current?.focus()
      return false
    }
    setEmailError('')
    return true
  }

  async function verify(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (pending || !validEmail()) return
    setError(''); setCodeError(''); setMessage('')
    if (!/^[0-9]{6}$/.test(code)) {
      setCodeError('Enter the six-digit code from your email.')
      codeInput.current?.focus()
      return
    }
    setPending('verify')
    try {
      await authApi.verifyEmail(email.trim(), code)
      setVerified(true); setCode('')
    } catch (failure) {
      if (failure instanceof AuthError && failure.code === 'invalid_or_expired_verification') {
        setCodeError(authErrorMessage(failure))
        requestAnimationFrame(() => { codeInput.current?.focus(); codeInput.current?.select() })
      } else setError(authErrorMessage(failure))
    } finally { setPending(null) }
  }

  async function resend() {
    if (pending || !validEmail()) return
    setPending('resend'); setError(''); setMessage(''); setCodeError('')
    try {
      await authApi.resendVerification(email.trim())
      setCode('')
      setMessage('If this account needs verification, a new code is on its way. Check your inbox and spam folder, then use the latest code.')
      requestAnimationFrame(() => codeInput.current?.focus())
    } catch (failure) { setError(authErrorMessage(failure)) }
    finally { setPending(null) }
  }

  if (verified) return <div className="space-y-5"><p role="status" className="text-sm leading-6">Your email is verified. You can now log in to your account.</p><Button className="w-full" size="lg" asChild><Link to={`/login${loginSearch}`}>Continue to log in</Link></Button></div>
  return <div className="space-y-5">
    <p className="text-sm leading-6 text-muted-foreground">{sent ? <>We sent a six-digit code to <strong className="break-words font-medium text-foreground">{initialEmail}</strong>. </> : 'Enter the six-digit code from your verification email. '}Codes expire after 10 minutes.</p>
    <form onSubmit={verify} noValidate className="space-y-5" aria-label="Verify your email" aria-busy={pending !== null}>
      <div className="space-y-2.5">
        <Label htmlFor="verification-email">Email address</Label>
        <Input ref={emailInput} id="verification-email" type="email" autoComplete="email" autoCapitalize="none" spellCheck={false} required maxLength={254} value={email} onChange={event => { setEmail(event.target.value); setEmailError(''); setError(''); setMessage(''); setCode(''); setCodeError('') }} disabled={pending !== null} aria-invalid={Boolean(emailError)} aria-describedby={emailError ? 'verification-email-error' : undefined} />
        {emailError && <p id="verification-email-error" className="text-sm leading-5 text-destructive">{emailError}</p>}
      </div>
      <div className="space-y-2.5">
        <Label htmlFor="verification-code">Verification code</Label>
        <Input ref={codeInput} id="verification-code" name="code" type="text" inputMode="numeric" autoComplete="one-time-code" pattern="[0-9]{6}" maxLength={6} required value={code} onChange={event => { setCode(event.target.value.replace(/\D/g, '').slice(0, 6)); setCodeError(''); setError('') }} placeholder="000000" disabled={pending !== null} aria-invalid={Boolean(codeError)} aria-describedby={codeError ? 'verification-code-error' : 'verification-code-hint'} className="h-14 pl-6 text-center text-2xl font-semibold tracking-[.45em] tabular-nums" />
        <p id="verification-code-hint" className="text-xs leading-5 text-muted-foreground">Use the latest code. You can paste all six digits at once.</p>
        {codeError && <p id="verification-code-error" role="alert" className="text-sm leading-6 text-destructive">{codeError}</p>}
      </div>
      <Button type="submit" className="w-full" size="lg" disabled={pending !== null}>{pending === 'verify' ? 'Verifying…' : 'Verify email'}</Button>
    </form>
    <div role="status" aria-live="polite" className="text-sm leading-6">
      {error && <p className="text-destructive">{error}</p>}
      {message && <p>{message}</p>}
    </div>
    <div className="flex flex-wrap items-center justify-between gap-2 border-t pt-4"><p className="text-sm text-muted-foreground">Didn’t get the code?</p><Button variant="link" className="px-0" onClick={resend} disabled={pending !== null}>{pending === 'resend' ? 'Sending…' : 'Send a new code'}</Button></div>
  </div>
}
