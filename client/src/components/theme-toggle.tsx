import { Moon, Sun } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { useTheme } from '@/lib/theme'
import { cn } from '@/lib/utils'

export function ThemeToggle({ className }: { className?: string }) {
  const { resolvedTheme, setPreference } = useTheme()
  const dark = resolvedTheme === 'dark'

  return <div role="group" aria-label="Appearance" className={cn('flex shrink-0 items-center gap-1', className)}>
    <Button variant="outline" className="theme-toggle relative h-11 w-16 justify-start rounded-full bg-muted p-1 hover:bg-muted aria-pressed:bg-primary aria-pressed:hover:bg-primary" aria-label="Dark mode" aria-pressed={dark} title={dark ? 'Switch to light mode' : 'Switch to dark mode'} onClick={() => setPreference(dark ? 'light' : 'dark')}>
      <span className="theme-toggle-thumb relative flex size-8 shrink-0 items-center justify-center rounded-full" aria-hidden="true">
        <Sun className="theme-sun absolute size-4" />
        <Moon className="theme-moon absolute size-4" />
      </span>
    </Button>
  </div>
}
