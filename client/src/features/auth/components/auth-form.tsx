import { useState, type FormEvent } from 'react'
import { useNavigate, useSearchParams } from 'react-router'
import { ArrowRight, Eye, EyeOff, Info } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Alert, AlertTitle, AlertDescription } from '@/components/ui/alert'
import { authApi, authErrorMessage, AuthError } from '../api'
import { useAuth } from '../store'
import { VerificationPanel } from './verification-panel'
import { safeReturnTo } from '@/app/paths'

export type AuthMode = 'login' | 'signup'
type Fields = { name: string; email: string; password: string; confirmPassword: string }
type FieldErrors = Partial<Record<keyof Fields, string>>

function PasswordField({ id, label, value, onChange, error, hint, autoComplete }: {
  id: 'password' | 'confirmPassword'
  label: string
  value: string
  onChange: (value: string) => void
  error?: string
  hint?: string
  autoComplete: string
}) {
  const [visible, setVisible] = useState(false)
  return (
    <div className="space-y-2.5">
      <Label htmlFor={id}>{label}</Label>
      <div className="relative">
        <Input id={id} name={id} type={visible ? 'text' : 'password'} value={value} onChange={event => onChange(event.target.value)} autoComplete={autoComplete} required aria-invalid={Boolean(error)} aria-describedby={error ? `${id}-error` : hint ? `${id}-hint` : undefined} className="pr-12" />
        <Button type="button" variant="ghost" size="icon" onClick={() => setVisible(current => !current)} aria-label={`${visible ? 'Hide' : 'Show'} ${label.toLowerCase()}`} aria-pressed={visible} className="absolute inset-y-0 right-0 h-11 w-11 text-muted-foreground">
          {visible ? <EyeOff aria-hidden="true" /> : <Eye aria-hidden="true" />}
        </Button>
      </div>
      {error ? <p id={`${id}-error`} className="text-xs leading-5 text-destructive">{error}</p> : hint ? <p id={`${id}-hint`} className="text-xs leading-5 text-muted-foreground">{hint}</p> : null}
    </div>
  )
}

export function AuthForm({ mode }: { mode: AuthMode }) {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const signup = mode === 'signup'
  const [values, setValues] = useState<Fields>({ name: '', email: '', password: '', confirmPassword: '' })
  const [errors, setErrors] = useState<FieldErrors>({})
  const [message, setMessage] = useState('')
  const [pending, setPending] = useState(false)
  const [registered, setRegistered] = useState(false)
  const [needsVerification, setNeedsVerification] = useState(false)

  function update(field: keyof Fields, value: string) {
    setValues(current => ({ ...current, [field]: value }))
    setErrors(current => ({ ...current, [field]: undefined, ...(field === 'password' ? { confirmPassword: undefined } : {}) }))
    setMessage('')
    if (field === 'email') setNeedsVerification(false)
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (pending) return
    const nextErrors: FieldErrors = {}
    if (signup && !values.name.trim()) nextErrors.name = 'Enter your name.'
    const emailInput = event.currentTarget.elements.namedItem('email') as HTMLInputElement
    if (!values.email.trim()) nextErrors.email = 'Enter your email address.'
    else if (!emailInput.validity.valid) nextErrors.email = 'Enter a valid email address, like you@example.com.'
    if (!values.password.trim()) nextErrors.password = 'Enter your password.'
    else if (signup && [...values.password].length < 12) nextErrors.password = 'Use at least 12 characters for your password.'
    else if (signup && new TextEncoder().encode(values.password).length > 72) nextErrors.password = 'This password is too long. Shorten it, especially if it contains symbols.'
    if (signup && !values.confirmPassword) nextErrors.confirmPassword = 'Confirm your password.'
    else if (signup && values.confirmPassword !== values.password) nextErrors.confirmPassword = 'Your passwords do not match.'
    setErrors(nextErrors)
    const firstInvalid = Object.keys(nextErrors)[0]
    if (firstInvalid) {
      requestAnimationFrame(() => document.getElementById(firstInvalid)?.focus())
      return
    }
    setMessage('')
    setPending(true)
    try {
      if (signup) {
        await authApi.register(values.email.trim(), values.name, values.password)
        setRegistered(true)
        setValues(current => ({ ...current, password: '', confirmPassword: '' }))
        return
      }
      await useAuth.getState().signIn(values.email.trim(), values.password)
      setValues({ name: '', email: '', password: '', confirmPassword: '' })
      navigate(safeReturnTo(searchParams.get('next')), { replace: true })
    } catch (error) {
      if (error instanceof AuthError && error.code === 'email_not_verified') setNeedsVerification(true)
      setMessage(authErrorMessage(error))
    } finally {
      setPending(false)
    }
  }

  if (registered || needsVerification) return <div className="space-y-6"><VerificationPanel initialEmail={values.email.trim()} sent={registered} /><Button variant="link" className="px-0" onClick={() => { setRegistered(false); setNeedsVerification(false); setMessage('') }}>Back to {signup ? 'sign up' : 'log in'}</Button></div>

  return (
    <div className="space-y-6">
    <form onSubmit={submit} noValidate className="space-y-5" aria-busy={pending} aria-label={signup ? 'Create an account' : 'Log in'}>
      <fieldset disabled={pending} className="space-y-5">
      {signup && <div className="space-y-2.5"><Label htmlFor="name">Your name</Label><Input id="name" name="name" autoComplete="name" value={values.name} onChange={event => update('name', event.target.value)} required maxLength={80} placeholder="Alex Morgan" aria-invalid={Boolean(errors.name)} aria-describedby={errors.name ? 'name-error' : undefined} />{errors.name && <p id="name-error" className="text-xs leading-5 text-destructive">{errors.name}</p>}</div>}
      <div className="space-y-2.5"><Label htmlFor="email">Email address</Label><Input id="email" name="email" type="email" autoComplete="email" autoCapitalize="none" spellCheck={false} value={values.email} onChange={event => update('email', event.target.value)} required maxLength={254} placeholder="you@example.com" aria-invalid={Boolean(errors.email)} aria-describedby={errors.email ? 'email-error' : undefined} />{errors.email && <p id="email-error" className="text-xs leading-5 text-destructive">{errors.email}</p>}</div>
      <PasswordField id="password" label="Password" value={values.password} onChange={value => update('password', value)} error={errors.password} hint={signup ? 'Use at least 12 characters.' : undefined} autoComplete={signup ? 'new-password' : 'current-password'} />
      {signup && <PasswordField id="confirmPassword" label="Confirm password" value={values.confirmPassword} onChange={value => update('confirmPassword', value)} error={errors.confirmPassword} autoComplete="new-password" />}
      <div className="space-y-4 pt-1">
        <Button type="submit" size="lg" className="w-full" disabled={pending}>{pending ? 'Please wait…' : signup ? 'Create account' : 'Log in'}<ArrowRight aria-hidden="true" /></Button>
        <div role="status" aria-live="polite" aria-atomic="true">
          {message && <Alert className="text-destructive"><Info aria-hidden="true" /><AlertTitle>{needsVerification ? 'Check your email' : 'Unable to continue'}</AlertTitle><AlertDescription>{message}</AlertDescription></Alert>}
        </div>
      </div>
      </fieldset>
    </form>
    </div>
  )
}
