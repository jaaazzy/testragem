# Testragem client

React and TypeScript frontend for the Testragem backtesting research platform.

## Stack

- React, TypeScript and React Router
- Vite and Tailwind CSS
- Local shadcn/ui components and Lucide icons
- Zustand for shared drafts and authentication state
- Spring Boot APIs with session cookies and CSRF protection

## Development

Requires Node.js 22.12+ and npm. Start the backend using the root project instructions, then run from `client/`:

```powershell
npm.cmd ci
npm.cmd run dev
```

Open `http://localhost:5173`. Vite forwards `/api` to `http://localhost:8080`; set `SERVER_PROXY_TARGET` to use another backend address. Use `npm` instead of `npm.cmd` outside Windows.

## Features

- Email registration, verification and session sign-in/out
- Protected workspace routes with loading and error states
- Historical dataset coverage and strategy configuration
- Buy-and-hold, SMA crossover and RSI parameter forms
- Saved results, trade ledger, price/indicator charts, equity and drawdown
- Versioned presets, personal defaults, experiment notes/tags and run comparison
- Light and dark themes, responsive layouts and reduced-motion support

The landing-page chart uses illustrative data. Historical backtest prices and results come from the backend. Simulation, accounting, validation and resource ownership are enforced by Spring Boot.

## Structure

- `src/app/`: application composition, route tree and error pages
- `src/features/auth/`: registration, verification and session state
- `src/features/backtests/`: configuration, results, charts and personal research
- `src/features/workspace/`: authenticated layout and navigation
- `src/components/ui/`: shared shadcn-style components
- `src/styles/globals.css`: theme tokens and shared styling

Routes use hash-based URLs, such as `/#/workspace` and `/#/workspace/backtests`. Component state stays local; shared drafts use Zustand. Persisted settings and results remain authoritative on the server.

## Validation

```powershell
npm.cmd run typecheck
npm.cmd run lint
npm.cmd run build
npm.cmd test
```

Browser checks use an isolated Chrome or Chromium process and mocked API responses. They cover routing, authentication states, forms, charts, comparison, themes, errors and responsive behavior. Real backend integration is tested separately. Screenshots are written to ignored `test-results/` storage.

## Docker

The Dockerfile builds the frontend and serves it through Caddy. Use the root `compose.yaml` to run it with the API and PostgreSQL. See the root README for environment and dataset configuration.

Shared UI components retain their upstream license in `src/components/ui/SHADCN-LICENSE`.
