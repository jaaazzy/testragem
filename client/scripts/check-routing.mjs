import { writeFile, mkdir, mkdtemp, access, rm } from 'node:fs/promises'
import { spawn } from 'node:child_process'
import { createServer } from 'node:net'
import { tmpdir } from 'node:os'
import { dirname, join, resolve, sep, basename } from 'node:path'
import { fileURLToPath } from 'node:url'
import assert from 'node:assert/strict'


const clientRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const artifacts = join(clientRoot, 'test-results', 'routing')
const delay = ms => new Promise(resolve => setTimeout(resolve, ms))
let viteProcess, chromeProcess, profileDir
let debugPort, appUrl
let childOutput = ''

async function freePort() {
  const server = createServer()
  await new Promise((resolve, reject) => { server.once('error', reject); server.listen(0, '127.0.0.1', resolve) })
  const port = server.address().port
  await new Promise(resolve => server.close(resolve))
  return port
}

async function ready(url) {
  const deadline = Date.now() + 25000
  while (Date.now() < deadline) {
    try { if ((await fetch(url, { signal: AbortSignal.timeout(1000) })).ok) return }
    catch { /* Wait for the isolated service to start. */ }
    await delay(150)
  }
  throw new Error(`Service did not start: ${url}\n${childOutput.slice(-4000)}`)
}

async function launch() {
  if (typeof WebSocket !== 'function') throw new Error('Routing checks require Node.js 22.12+ and Google Chrome or Chromium.')
  const candidates = [process.env.CHROME_PATH,
    'C:/Program Files/Google/Chrome/Application/chrome.exe',
    'C:/Program Files (x86)/Google/Chrome/Application/chrome.exe',
    process.env.LOCALAPPDATA && join(process.env.LOCALAPPDATA, 'Google/Chrome/Application/chrome.exe'),
    '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
    '/usr/bin/google-chrome', '/usr/bin/chromium', '/usr/bin/chromium-browser'].filter(Boolean)
  let chrome
  for (const candidate of candidates) { try { await access(candidate); chrome = candidate; break } catch { /* Try the next standard install path. */ } }
  if (!chrome) throw new Error('Chrome was not found. Set CHROME_PATH to the Chrome or Chromium executable.')
  await mkdir(artifacts, { recursive: true })
  profileDir = await mkdtemp(join(tmpdir(), 'testragem-routing-'))
  debugPort = await freePort()
  const appPort = await freePort()
  appUrl = `http://127.0.0.1:${appPort}`
  viteProcess = spawn(process.execPath, [join(clientRoot, 'node_modules/vite/bin/vite.js'), '--host', '127.0.0.1', '--port', String(appPort), '--strictPort'], { cwd: clientRoot, windowsHide: true, stdio: ['ignore', 'pipe', 'pipe'] })
  const flags = ['--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check', `--remote-debugging-port=${debugPort}`, `--user-data-dir=${profileDir}`, 'about:blank']
  if (process.env.CHROME_NO_SANDBOX === '1') flags.unshift('--no-sandbox')
  chromeProcess = spawn(chrome, flags, { windowsHide: true, stdio: ['ignore', 'pipe', 'pipe'] })
  for (const child of [viteProcess, chromeProcess]) {
    child.on('error', error => { childOutput += error.message })
    child.stdout.on('data', chunk => { childOutput = (childOutput + chunk).slice(-8000) })
    child.stderr.on('data', chunk => { childOutput = (childOutput + chunk).slice(-8000) })
  }
  await Promise.all([ready(appUrl), ready(`http://127.0.0.1:${debugPort}/json/version`)])
}

async function cleanup() {
  for (const child of [chromeProcess, viteProcess]) {
    if (child && child.exitCode === null) {
      child.kill()
      await Promise.race([new Promise(resolve => child.once('exit', resolve)), delay(2000)])
    }
  }
  // Only remove the unique temporary profile created by this test invocation.
  if (profileDir && resolve(profileDir).startsWith(resolve(tmpdir()) + sep) && basename(profileDir).startsWith('testragem-routing-')) {
    await rm(profileDir, { recursive: true, force: true, maxRetries: 3, retryDelay: 200 }).catch(() => {})
  }
}

async function checkRouting() {
const tab = await (await fetch(`http://127.0.0.1:${debugPort}/json/new?about:blank`, { method: 'PUT' })).json()
const ws = new WebSocket(tab.webSocketDebuggerUrl)
await new Promise((resolve, reject) => { ws.onopen = resolve; ws.onerror = reject })
let id = 0
const pending = new Map()
const errors = []
let mode = 'authenticated'
let failLogout = false
let releaseIdentity
const identityReady = new Promise(resolve => { releaseIdentity = resolve })
const fixture = { id: '8cce43b1-4047-4ded-bb14-ab9442fbcf27', displayName: 'Alex Morgan', email: 'alex@example.com', createdAt: '2026-11-09T00:00:00Z' }
ws.onmessage = async ({ data }) => {
  const message = JSON.parse(data)
  if (message.id) {
    const item = pending.get(message.id)
    if (item) {
      pending.delete(message.id)
      clearTimeout(item.timer)
      if (message.error) item.reject(message.error)
      else item.resolve(message.result)
    }
  } else if (message.method === 'Runtime.exceptionThrown') errors.push(message.params.exceptionDetails.text)
  else if (message.method === 'Fetch.requestPaused') {
    const { request, requestId } = message.params
    const path = new URL(request.url).pathname
    let status = 200
    let body = {}
    if (path === '/api/users/me') {
      await identityReady
      status = mode === 'authenticated' ? 200 : mode === 'offline' ? 503 : 401
      body = status === 200 ? fixture : { error: 'unauthenticated' }
    } else if (path === '/api/auth/csrf') body = { token: 'test-csrf', headerName: 'X-CSRF-TOKEN' }
    else if (path === '/api/auth/login') { mode = 'authenticated'; body = fixture }
    else if (path === '/api/auth/register') { status = 201; body = fixture }
    else if (path === '/api/auth/verify-email') { status = 204 }
    else if (path === '/api/auth/resend-verification') { status = 202; body = { message: 'Check your inbox' } }
    else if (path === '/api/auth/logout') { status = failLogout ? 503 : 204; if (!failLogout) mode = 'guest' }
    else { status = 404; body = { error: 'not_found' } }
    await command('Fetch.fulfillRequest', { requestId, responseCode: status, responseHeaders: [{ name: 'Content-Type', value: 'application/json' }], body: Buffer.from(status === 204 ? '' : JSON.stringify(body)).toString('base64') })
  }
}
function command(method, params = {}) {
  return new Promise((resolve, reject) => {
    const call = ++id
    const timer = setTimeout(() => { pending.delete(call); reject(new Error(`Browser command timed out: ${method}`)) }, 15000)
    pending.set(call, { resolve, reject, timer })
    ws.send(JSON.stringify({ id: call, method, params }))
  })
}
async function evaluate(expression) {
  const response = await command('Runtime.evaluate', { expression: `(async () => (${expression}))()`, returnByValue: true, awaitPromise: true })
  if (response.exceptionDetails) throw new Error(JSON.stringify(response.exceptionDetails))
  return response.result.value
}
async function until(expression) {
  const end = Date.now() + 15000
  while (Date.now() < end) {
    if (await evaluate(`!!(${expression})`)) return
    await new Promise(resolve => setTimeout(resolve, 100))
  }
  throw new Error('Timed out: ' + expression + '\n' + await evaluate('document.body.innerText'))
}
async function fill(selector, value) {
  await evaluate(`document.querySelector(${JSON.stringify(selector)}).focus()`)
  await command('Input.insertText', { text: value })
}
async function screenshot(name) {
  await evaluate(`Promise.all(document.getAnimations().filter(animation => animation.effect?.getComputedTiming().iterations !== Infinity).map(animation => animation.finished.catch(() => {})))`)
  const { data } = await command('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false })
  await writeFile(join(artifacts, name), Buffer.from(data, 'base64'))
}
async function go(path) {
  await evaluate(`location.hash = ${JSON.stringify(path)}`)
}
async function heading(value) { await until(`document.querySelector('main h1')?.textContent === ${JSON.stringify(value)}`) }

try {
  await command('Page.enable'); await command('Runtime.enable')
  await command('Fetch.enable', { patterns: [{ urlPattern: '*/api/*', requestStage: 'Request' }] })
  await command('Emulation.setDeviceMetricsOverride', { width: 1440, height: 1000, deviceScaleFactor: 1, mobile: false })
  await command('Page.navigate', { url: `${appUrl}/#/workspace` })
  await until(`!!document.querySelector('[data-slot=skeleton]')`)
  assert.equal(await evaluate(`!!document.querySelector('[role=status][aria-busy=true]')`), true)
  await screenshot('workspace-loading.png')
  releaseIdentity()
  await heading('Workspace')
  assert.equal(await evaluate(`!!document.querySelector('[data-slot=skeleton]')`), false)
  assert.equal(await evaluate(`getComputedStyle(document.querySelector('main .page-transition')).animationName`), 'page-arrive')
  assert.equal(await evaluate(`document.querySelector('aside a[aria-current=page]')?.textContent`), 'Workspace')
  assert.equal(await evaluate(`document.querySelectorAll('main').length`), 1)
  assert.equal(await evaluate('document.documentElement.scrollWidth > innerWidth'), false)
  await screenshot('workspace-desktop.png')
  await go('#/workspace/strategies'); await heading('Strategies')
  await evaluate(`document.querySelector('button[aria-label="Use RSI strategy"]').click()`)
  await heading('Workspace')
  assert.equal(await evaluate(`document.querySelector('#strategy').value`), 'rsi')
  await evaluate(`[...document.querySelectorAll('button')].find(b => b.textContent === 'Reset draft').click()`)
  assert.equal(await evaluate(`document.querySelector('#strategy').value`), 'ma-crossover')
  await until(`document.querySelector('main [role=status]')?.textContent.includes('Draft reset')`)
  await go('#/workspace/datasets'); await heading('Datasets')
  assert.equal(await evaluate(`document.body.innerText.includes('No datasets are connected yet')`), true)
  await go('#/workspace/backtests'); await heading('Backtests')
  await go('#/workspace/account'); await heading('Your account')
  assert.equal(await evaluate(`document.querySelector('main').innerText.includes('alex@example.com')`), true)
  await screenshot('workspace-account-desktop.png')
  failLogout = true
  await evaluate(`document.querySelector('aside button').click()`)
  await until(`document.querySelector('aside [role=alert]')`)
  assert.equal(await evaluate(`document.querySelector('main h1').textContent`), 'Your account')
  failLogout = false
  await evaluate(`document.querySelector('aside button').click()`)
  await until(`location.hash === '#/login'`)
  await go('#/workspace/strategies')
  await until(`location.hash.startsWith('#/login?next=')`)
  await until(`!!document.querySelector('#password')`)
  await fill('#email', 'alex@example.com'); await fill('#password', 'Correct horse battery staple!')
  await evaluate(`document.querySelector('button[type=submit]').click()`)
  await heading('Strategies')
  assert.equal(await evaluate(`document.body.innerText.includes('Current draft')`), true)
  mode = 'offline'
  await command('Page.reload')
  await heading('We couldn’t open your workspace.')
  assert.equal(await evaluate(`location.hash`), '#/workspace/strategies')
  mode = 'authenticated'
  await evaluate(`[...document.querySelectorAll('button')].find(b => b.textContent === 'Try again').click()`)
  await heading('Strategies')
  await command('Emulation.setDeviceMetricsOverride', { width: 390, height: 844, deviceScaleFactor: 1, mobile: true })
  await go('#/workspace'); await heading('Workspace')
  assert.equal(await evaluate('document.documentElement.scrollWidth > innerWidth'), false)
  assert.equal(await evaluate(`[...document.querySelectorAll('main [data-slot=button], .workspace-toolbar button, .workspace-toolbar select')].every(el => {const box=el.getBoundingClientRect();return box.width>=44 && box.height>=44})`), true, 'Mobile workspace controls need 44px targets')
  await screenshot('workspace-mobile.png')
  await evaluate(`document.querySelector('button[aria-label="Open navigation"]').click()`)
  await until(`document.querySelector('dialog').open`)
  assert.equal(await evaluate(`document.querySelector('dialog').contains(document.activeElement)`), true)
  await screenshot('workspace-menu-mobile.png')
  await command('Input.dispatchKeyEvent', { type: 'keyDown', key: 'Escape', code: 'Escape', windowsVirtualKeyCode: 27 })
  await command('Input.dispatchKeyEvent', { type: 'keyUp', key: 'Escape', code: 'Escape', windowsVirtualKeyCode: 27 })
  await until(`!document.querySelector('dialog').open`)
  assert.equal(await evaluate(`document.activeElement?.getAttribute('aria-label')`), 'Open navigation')
  await evaluate(`document.querySelector('button[aria-label="Open navigation"]').click()`)
  await evaluate(`document.querySelector('dialog a[href="#/workspace/account"]').click()`)
  await heading('Your account')
  assert.equal(await evaluate(`document.querySelector('dialog').open`), false)
  assert.equal(await evaluate('document.documentElement.scrollWidth > innerWidth'), false)
  await screenshot('workspace-account-mobile.png')
  await command('Emulation.setDeviceMetricsOverride', { width: 320, height: 740, deviceScaleFactor: 1, mobile: true })
  for (const [path, title] of [['#/workspace','Workspace'], ['#/workspace/datasets','Datasets'], ['#/workspace/strategies','Strategies'], ['#/workspace/backtests','Backtests'], ['#/workspace/account','Your account']]) {
    await go(path); await heading(title)
    assert.equal(await evaluate('document.documentElement.scrollWidth > innerWidth'), false, `320px overflow: ${path}`)
  }
  await go('#/workspace/strategies'); await heading('Strategies')
  await evaluate(`document.querySelector('button[aria-label="Use RSI strategy"]').click()`)
  await heading('Workspace')
  fixture.id = '91efc2c0-7d7e-4e5f-9a4b-1e5828b561f6'; fixture.displayName = 'Another Researcher'
  await evaluate(`window.dispatchEvent(new Event('focus'))`)
  await until(`document.querySelector('#strategy')?.value === 'ma-crossover'`)
  console.log('PASS: workspace navigation, auth return destination, session retry, mobile layout and focus')

  await command('Emulation.setDeviceMetricsOverride', { width: 1440, height: 1000, deviceScaleFactor: 1, mobile: false })
  await go('#/workspace'); await heading('Workspace')
  await evaluate(`(window.__routingSidebar = document.querySelector('aside'), true)`)
  await evaluate(`document.querySelector('aside a[href="#/workspace/datasets"]').click()`)
  await heading('Datasets')
  await evaluate(`document.querySelector('aside a[href="#/workspace/account"]').click()`)
  await heading('Your account')
  await evaluate(`history.back()`); await heading('Datasets')
  await evaluate(`history.forward()`); await heading('Your account')
  assert.equal(await evaluate(`window.__routingSidebar === document.querySelector('aside')`), true)
  console.log('PASS: browser back/forward preserves the workspace layout')

  await go('#/workspace/not-a-page'); await heading('Page not found')
  assert.equal(await evaluate(`!!document.querySelector('aside nav')`), true)
  assert.equal(await evaluate(`document.title`), 'Page not found | Testragem')
  await screenshot('routing-workspace-404.png')
  await go('#/not-a-page'); await heading('Page not found')
  assert.equal(await evaluate(`!!document.querySelector('aside nav')`), false)
  await evaluate(`document.querySelector('a[href="#/"]').click()`)
  await until(`!!document.querySelector('#home-title')`)
  console.log('PASS: public and authenticated 404 routes')

  mode = 'guest'
  await command('Page.navigate', { url: `${appUrl}/#/workspace/backtests?status=complete` })
  await command('Page.reload')
  await until(`location.hash.startsWith('#/login?next=')`)
  await until(`!!document.querySelector('#password')`)
  assert.equal(await evaluate(`new URLSearchParams(location.hash.split('?')[1]).get('next')`), '/workspace/backtests?status=complete')
  await evaluate(`document.querySelector('.skip-link').click()`)
  assert.equal(await evaluate(`document.activeElement.id`), 'auth-content')
  assert.equal(await evaluate(`location.hash.startsWith('#/login?next=')`), true)
  await evaluate(`document.querySelector('a[href^="#/signup"]').click()`)
  await until(`!!document.querySelector('#confirmPassword')`)
  assert.equal(await evaluate(`new URLSearchParams(location.hash.split('?')[1]).get('next')`), '/workspace/backtests?status=complete')
  await fill('#name', 'Alex Morgan'); await fill('#email', fixture.email)
  await fill('#password', 'Correct horse battery staple!'); await fill('#confirmPassword', 'Correct horse battery staple!')
  await evaluate(`document.querySelector('button[type=submit]').click()`)
  await until(`!!document.querySelector('#verification-code')`)
  await fill('#verification-code', '012345')
  await evaluate(`document.querySelector('button[type=submit]').click()`)
  await until(`document.body.innerText.includes('Your email is verified')`)
  await evaluate(`[...document.querySelectorAll('a')].find(a => a.textContent === 'Continue to log in').click()`)
  await until(`!!document.querySelector('#password')`)
  assert.equal(await evaluate(`new URLSearchParams(location.hash.split('?')[1]).get('next')`), '/workspace/backtests?status=complete')
  await fill('#email', fixture.email); await fill('#password', 'Correct horse battery staple!')
  await evaluate(`document.querySelector('button[type=submit]').click()`)
  await heading('Backtests')
  assert.equal(await evaluate(`location.hash`), '#/workspace/backtests?status=complete')
  await evaluate(`history.back()`)
  await until(`!location.hash.startsWith('#/login')`)
  console.log('PASS: return paths survive signup, verification and login; skip links preserve routing')

  const destinations = [
    ['/workspace/account', '/workspace/account'],
    ['/workspace/backtests?status=complete', '/workspace/backtests?status=complete'],
    ['#/workspace/strategies', '/workspace/strategies'],
    [null, '/workspace'], ['https://evil.example/workspace', '/workspace'],
    ['//evil.example', '/workspace'], ['javascript:alert(1)', '/workspace'],
    ['/workspace/../../login', '/workspace'], ['/workspace\\..\\login', '/workspace'], ['/login', '/workspace'],
  ]
  for (const [value, expected] of destinations) {
    assert.equal(await evaluate(`(await import('/src/app/paths.ts')).safeReturnTo(${JSON.stringify(value)})`), expected)
  }
  await go('#/login?next=https%3A%2F%2Fevil.example')
  await until(`!!document.querySelector('#password')`)
  await fill('#email', fixture.email); await fill('#password', 'Correct horse battery staple!')
  await evaluate(`document.querySelector('button[type=submit]').click()`)
  await heading('Workspace')
  assert.equal(await evaluate(`location.origin`), appUrl)
  console.log('PASS: external and malformed return destinations cannot redirect away from the app')

  async function chooseTheme(value) {
    const current = await evaluate(`document.documentElement.dataset.theme`)
    if (current !== value) await evaluate(`document.querySelector('button[aria-label="Dark mode"]').click()`)
    await until(`localStorage.getItem('testragem.theme') === ${JSON.stringify(value)}`)
    await evaluate(`Promise.all(document.getAnimations().filter(animation => animation.effect?.getComputedTiming().iterations !== Infinity).map(animation => animation.finished.catch(() => {})))`)
  }
  await chooseTheme('dark')
  await until(`document.documentElement.dataset.theme === 'dark'`)
  assert.equal(await evaluate(`localStorage.getItem('testragem.theme')`), 'dark')
  assert.equal(await evaluate(`getComputedStyle(document.documentElement).colorScheme`), 'dark')
  assert.equal(await evaluate(`document.querySelector('button[aria-label="Dark mode"]').getAttribute('aria-pressed')`), 'true')
  const darkToggleBackground = await evaluate(`getComputedStyle(document.querySelector('.theme-toggle')).backgroundColor`)
  await evaluate(`document.querySelector('button[aria-label="Dark mode"]').focus()`)
  await command('Input.dispatchKeyEvent', { type: 'keyDown', key: ' ', code: 'Space', windowsVirtualKeyCode: 32 })
  await command('Input.dispatchKeyEvent', { type: 'keyUp', key: ' ', code: 'Space', windowsVirtualKeyCode: 32 })
  await until(`document.documentElement.dataset.theme === 'light'`)
  assert.equal(await evaluate(`document.activeElement?.getAttribute('aria-label')`), 'Dark mode')
  await chooseTheme('dark')
  await screenshot('theme-workspace-dark.png')
  await command('Page.reload'); await heading('Workspace')
  assert.equal(await evaluate(`document.documentElement.dataset.theme`), 'dark')
  await screenshot('theme-workspace-dark.png')
  await command('Emulation.setEmulatedMedia', { features: [{ name: 'prefers-color-scheme', value: 'light' }] })
  assert.equal(await evaluate(`document.documentElement.dataset.theme`), 'dark')
  assert.equal(await evaluate(`!!document.querySelector('button[aria-label="Use device theme"]')`), false)
  await chooseTheme('light')
  await until(`document.documentElement.dataset.theme === 'light'`)
  await command('Emulation.setEmulatedMedia', { features: [{ name: 'prefers-color-scheme', value: 'dark' }] })
  assert.equal(await evaluate(`document.documentElement.dataset.theme`), 'light', 'Saved light preference survives OS changes')
  await chooseTheme('dark')
  await go('#/'); await until(`!!document.querySelector('#home-title')`)
  const replayTick = await evaluate(`Number(document.querySelector('.trading-replay').dataset.tick)`)
  await until(`Number(document.querySelector('.trading-replay').dataset.tick) > ${replayTick}`)
  const replayFrames = await evaluate(`new Promise(resolve => {
    const frames=[];
    function sample(){const series=document.querySelector('[data-slot="replay-series"]');frames.push({tick:Number(document.querySelector('.trading-replay').dataset.tick),x:series.transform.baseVal.consolidate().matrix.e});if(frames.length===18)resolve(frames);else requestAnimationFrame(sample)}
    requestAnimationFrame(sample);
  })`)
  assert.ok(new Set(replayFrames.map(frame=>frame.x)).size>=12, 'Replay advances continuously between candle updates')
  for (let index=1; index<replayFrames.length; index++) {
    const previous=replayFrames[index-1], current=replayFrames[index]
    if (previous.tick===current.tick) assert.ok(current.x<=previous.x && previous.x-current.x<.6, 'Replay scroll stays continuous without large frame jumps')
  }
  await evaluate(`document.querySelector('button[aria-label="Pause market replay"]').click()`)
  const pausedTick = await evaluate(`document.querySelector('.trading-replay').dataset.tick`)
  const pausedPosition = await evaluate(`document.querySelector('[data-slot="replay-series"]').getAttribute('transform')`)
  await delay(1600)
  assert.equal(await evaluate(`document.querySelector('.trading-replay').dataset.tick`), pausedTick)
  assert.equal(await evaluate(`document.querySelector('[data-slot="replay-series"]').getAttribute('transform')`), pausedPosition, 'Pause freezes the current position')
  await evaluate(`[...document.querySelectorAll('[aria-label="Replay timeframe"] button')].find(button=>button.textContent==='4H').click()`)
  assert.equal(await evaluate(`document.querySelector('[aria-label="Replay timeframe"] button[aria-pressed=true]').textContent`), '4H')
  for (const width of [320,390,768,1024,1440]) {
    await command('Emulation.setDeviceMetricsOverride', { width, height: 1000, deviceScaleFactor: 1, mobile: width<768 })
    assert.equal(await evaluate('document.documentElement.scrollWidth > innerWidth'), false, `Home overflow at ${width}px`)
    await screenshot(`home-replay-${width}.png`)
  }
  console.log('PASS: trading replay advances, pauses, changes timeframe; header and hero fit 320–1440px')
  await screenshot('theme-home-dark.png')
  await go('#/login'); await until(`!!document.querySelector('#password')`)
  await screenshot('theme-login-dark.png')
  await command('Emulation.setDeviceMetricsOverride', { width: 390, height: 844, deviceScaleFactor: 1, mobile: true })
  assert.equal(await evaluate('document.documentElement.scrollWidth > innerWidth'), false)
  await screenshot('theme-login-dark-mobile.png')
  assert.ok(await evaluate(`parseFloat(getComputedStyle(document.querySelector('#password')).fontSize)`) >= 16, 'Mobile auth fields avoid automatic input zoom')
  await go('#/workspace'); await heading('Workspace')
  assert.equal(await evaluate('document.documentElement.scrollWidth > innerWidth'), false)
  await screenshot('theme-workspace-dark-mobile.png')
  const textContrast = () => evaluate(`(() => {
    const css = getComputedStyle(document.documentElement);
    const luminance = token => { const hex = css.getPropertyValue(token).trim().slice(1); const rgb = [0,2,4].map(i => parseInt(hex.slice(i,i+2),16)/255).map(v => v <= .04045 ? v/12.92 : ((v+.055)/1.055)**2.4); return rgb[0]*.2126+rgb[1]*.7152+rgb[2]*.0722 };
    return [['--foreground','--background'],['--muted-foreground','--background'],['--muted-foreground','--card'],['--muted-foreground','--muted'],['--primary-foreground','--primary'],['--secondary-foreground','--secondary'],['--destructive','--card'],['--positive','--card']].map(([fg,bg]) => {const a=luminance(fg),b=luminance(bg); return {fg,bg,ratio:(Math.max(a,b)+.05)/(Math.min(a,b)+.05)}});
  })()`)
  for (const pair of await textContrast()) assert.ok(pair.ratio >= 4.5, `Dark contrast ${pair.fg}/${pair.bg}: ${pair.ratio}`)
  await chooseTheme('light')
  await until(`document.documentElement.dataset.theme === 'light'`)
  assert.notEqual(await evaluate(`getComputedStyle(document.querySelector('.theme-toggle')).backgroundColor`), darkToggleBackground)
  await evaluate(`(window.__nativeThemeTransition = document.startViewTransition, document.startViewTransition = undefined, true)`)
  await chooseTheme('dark'); await chooseTheme('light')
  await evaluate(`document.startViewTransition = window.__nativeThemeTransition`)
  for (const pair of await textContrast()) assert.ok(pair.ratio >= 4.5, `Light contrast ${pair.fg}/${pair.bg}: ${pair.ratio}`)
  await command('Emulation.setDeviceMetricsOverride', { width: 1440, height: 1000, deviceScaleFactor: 1, mobile: false })
  await screenshot('theme-workspace-light.png')
  console.log('PASS: light/dark toggle, no system option, saved preference, OS changes, mobile layout, and dark text contrast')
  console.log('PASS: shadcn skeleton loading, page transitions, keyboard theme toggle and browsers without View Transitions')

  await command('Emulation.setEmulatedMedia', { features: [{name:'prefers-reduced-motion',value:'reduce'},{name:'prefers-reduced-transparency',value:'reduce'},{name:'prefers-contrast',value:'more'}] })
  assert.equal(await evaluate(`getComputedStyle(document.querySelector('.workspace-toolbar')).backdropFilter`), 'none')
  assert.equal(await evaluate(`getComputedStyle(document.querySelector('main [data-slot=button]')).transitionDuration`), '0s')
  assert.equal(await evaluate(`getComputedStyle(document.querySelector('main .page-transition')).animationName`), 'none')
  await chooseTheme('dark')
  assert.equal(await evaluate(`getComputedStyle(document.querySelector('.theme-toggle-thumb')).transitionDuration`), '0s')
  assert.equal(await evaluate(`document.getAnimations().some(animation => animation.effect?.pseudoElement?.includes('view-transition'))`), false)
  await chooseTheme('light')
  await command('Emulation.setDeviceMetricsOverride', { width: 390, height: 844, deviceScaleFactor: 1, mobile: true })
  await evaluate(`document.querySelector('button[aria-label="Open navigation"]').click()`)
  await until(`document.querySelector('dialog').open`)
  assert.equal(await evaluate(`getComputedStyle(document.querySelector('dialog')).transitionDuration`), '0s')
  await evaluate(`document.querySelector('button[aria-label="Close navigation"]').click()`)
  await until(`!document.querySelector('dialog').open`)
  await until(`document.activeElement?.getAttribute('aria-label') === 'Open navigation'`)
  await command('Emulation.setDeviceMetricsOverride', { width: 768, height: 1000, deviceScaleFactor: 1, mobile: false })
  await evaluate(`document.documentElement.style.fontSize = '200%'`)
  assert.equal(await evaluate('document.documentElement.scrollWidth > innerWidth'), false, 'Workspace layout at 200% text size')
  assert.equal(await evaluate(`(() => {const label=document.querySelector('.workspace-toolbar .truncate');return label.scrollWidth<=label.clientWidth})()`), true, 'Current location stays readable at 200% text size')
  await screenshot('workspace-large-text.png')
  await evaluate(`document.documentElement.style.fontSize = ''`)
  await go('#/'); await until(`!!document.querySelector('.trading-replay')`)
  assert.equal(await evaluate(`!!document.querySelector('button[aria-label="Play market replay"]')`), true, 'Reduced-motion replay starts paused')
  const reducedTick = await evaluate(`document.querySelector('.trading-replay').dataset.tick`)
  await delay(1500)
  assert.equal(await evaluate(`document.querySelector('.trading-replay').dataset.tick`), reducedTick)
  await go('#/workspace'); await heading('Workspace')
  await command('Emulation.setEmulatedMedia', { features: [] })
  console.log('PASS: reset feedback, touch targets, reduced motion, increased contrast and opaque toolbar fallback')

  mode = 'guest'
  await evaluate(`window.dispatchEvent(new Event('focus'))`)
  await until(`location.hash.startsWith('#/login?next=')`)
  await evaluate(`history.back()`)
  await until(`location.hash.startsWith('#/login') || !location.hash.startsWith('#/workspace')`)
  assert.equal(await evaluate(`!!document.querySelector('#workspace-content')`), false)
  assert.deepEqual(errors, [])
  console.log('PASS: expired sessions cannot recover workspace content through browser history; no uncaught runtime errors')
  fixture.displayName = null
  mode = 'authenticated'
  // A new document must initialize auth before the protected route can redirect.
  // Hash navigation followed by reload can reload the intervening login route.
  await command('Page.navigate', { url: `${appUrl}/?boundary-check#/workspace/account` })
  await heading('We couldn’t open this page.')
  assert.equal(await evaluate(`document.body.innerText.includes('trim') || document.body.innerText.includes('TypeError')`), false)
  fixture.displayName = 'Alex Morgan'
  await evaluate(`document.querySelector('a[href="#/"]').click()`)
  await until(`!!document.querySelector('#home-title')`)
  console.log('PASS: route error boundary provides recovery without exposing error details')
  mode = 'guest'
  await evaluate(`window.dispatchEvent(new Event('focus'))`)
  await until(`!!document.querySelector('nav[aria-label="Account navigation"] a[href="#/login"]')`)
  for (const width of [320,768,1440]) {
    await command('Emulation.setDeviceMetricsOverride', { width, height: 1000, deviceScaleFactor: 1, mobile: width<768 })
    assert.equal(await evaluate('document.documentElement.scrollWidth > innerWidth'), false, `Guest header overflow at ${width}px`)
    assert.equal(await evaluate(`document.querySelector('.site-navigation').getBoundingClientRect().right <= document.querySelector('.site-account').getBoundingClientRect().left`), true, `Header groups overlap at ${width}px`)
    await screenshot(`home-guest-${width}.png`)
  }
  console.log('PASS: guest header at 320–1440px; reduced-motion replay stays paused')
  console.log('Routing checks passed. Authentication responses were mocked at the browser boundary; no backend or real email is used.')
} finally {
  await command('Page.close').catch(() => {})
  ws.close()
}

}

try {
  await launch()
  await checkRouting()
  console.log(`Screenshots: ${artifacts}`)
} finally { await cleanup() }
