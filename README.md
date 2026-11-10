# Testragem

Testragem is a work-in-progress application for exploring trading strategies and preparing backtest experiments. It combines a React interface, a Spring Boot API, PostgreSQL persistence, and an independent Python engine scaffold.

## Current capabilities

| Area | Status |
| --- | --- |
| Authentication | Registration, email verification codes, sign-in, sign-out, and session checks |
| Account | Authenticated profile endpoint and account screen |
| Workspace | Protected navigation, responsive layout, loading and error states |
| Strategy draft | Moving-average crossover, RSI, and buy-and-hold selection; initial-capital input |
| Appearance | Light, dark, and system themes with local preference persistence |
| Dataset import | Planned; no datasets are connected |
| Backtest execution and results | Planned; draft selections do not run simulations |
| Python engine | Health endpoint only; not connected to the Java API |

Drafts live in memory and reset after a reload or sign-out. The public market chart uses generated demonstration values, not an imported dataset or live feed.

## Architecture

```text
Browser -> React client -> /api -> Spring Boot API -> PostgreSQL

Python engine: independent FastAPI service for future simulations
```

| Directory | Stack |
| --- | --- |
| `client/` | React 19, TypeScript, Vite 8, Tailwind CSS 4, React Router, Zustand |
| `server/` | Java 21, Spring Boot 4, Spring Security, Spring JDBC, Flyway |
| `engine/` | Python 3.11+, FastAPI |

Flyway migrations in `server/src/main/resources/db/migration/` define the database schema without bundling user records or market datasets.

## Prerequisites

- Node.js 22.12+ and npm. Browser checks require the global WebSocket API; use a current Node.js 22 release or newer.
- JDK 21 and Maven 3.6.3+ on your PATH. No Maven wrapper is included.
- PostgreSQL and permission to create a database.
- SMTP credentials with STARTTLS support for registration emails.
- Python 3.11+ for the optional engine.
- Chrome or Chromium for frontend browser checks.

## Local setup

Run each service in a separate terminal. These PowerShell examples start from the project root.

### 1. Create an empty database

Run once in your PostgreSQL administration tool:

```sql
CREATE DATABASE testragem;
```

Flyway applies schema migrations when the API starts. No production dump, existing accounts, or market history is needed.

### 2. Configure and start the API

```powershell
cd server
$env:SPRING_PROFILES_ACTIVE = 'local'
$env:DB_PASSWORD = '<your-local-database-password>'
$env:SPRING_MAIL_HOST = '<your-smtp-host>'
$env:SPRING_MAIL_PORT = '587'
$env:SPRING_MAIL_USERNAME = '<your-smtp-username>'
$env:SPRING_MAIL_PASSWORD = '<your-smtp-password>'
$env:MAIL_FROM = '<your-sender-email>'
mvn spring-boot:run
```

Replace placeholders privately. Spring Boot does not automatically load `.env` files. Use shell variables, private IDE settings, or a secret store. In Bash or Zsh, use `export NAME='value'` instead of `$env:NAME = 'value'`.

| Variable | Default or purpose |
| --- | --- |
| `DB_URL` | `jdbc:postgresql://localhost:5432/testragem` |
| `DB_USERNAME` | `postgres` |
| `DB_PASSWORD` | Required; no source-code default |
| `SERVER_PORT` | `8080` |
| `SPRING_PROFILES_ACTIVE` | Set to `local` for HTTP localhost development |
| `AUTH_PUBLIC_URL` | `http://localhost:5173`; frontend address for verification emails |
| `SPRING_MAIL_HOST` | SMTP hostname |
| `SPRING_MAIL_PORT` | Typically `587` for STARTTLS |
| `SPRING_MAIL_USERNAME` | SMTP authentication username |
| `SPRING_MAIL_PASSWORD` | SMTP password or provider app password |
| `MAIL_FROM` | Sender address accepted by your provider |

Registration sends email during account creation. Unavailable SMTP delivery returns `email_delivery_unavailable` and rolls back registration. Verify the emailed six-digit code before signing in; codes expire after ten minutes.

### 3. Start the client

```powershell
cd client
npm ci
npm run dev
```

Open <http://localhost:5173>. Vite forwards `/api` to <http://localhost:8080>. Set `SERVER_PROXY_TARGET` before starting Vite if the API uses another address.

Public routes include `/`, `/about`, `/protocol`, `/login`, `/signup`, and `/verify-email`. After signing in, open `/workspace`. Its sections include datasets, strategies, backtests, and account. Unauthenticated visits redirect to sign-in and retain a safe workspace return destination.

### 4. Start the optional engine

```powershell
cd engine
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -e .
.\.venv\Scripts\python.exe -m uvicorn app.main:app --reload --host 127.0.0.1 --port 8000
```

On macOS or Linux, use `.venv/bin/python` instead of `.\.venv\Scripts\python.exe`.

## API surface

| Method | Path | Purpose |
| --- | --- | --- |
| GET | API `/api/health` | Java service health |
| GET | API `/actuator/health` | Application and database health |
| GET | API `/api/auth/csrf` | Session-bound CSRF token and header name |
| POST | API `/api/auth/register` | Create account and send verification code |
| POST | API `/api/auth/verify-email` | Verify email using its code |
| POST | API `/api/auth/resend-verification` | Request a replacement code, subject to throttling |
| POST | API `/api/auth/login` | Establish session |
| POST | API `/api/auth/logout` | End session |
| GET | API `/api/users/me` | Read authenticated profile |
| GET | Engine `/health` | Python service health |

The API uses port 8080 by default; the engine uses port 8000 in the example above. Authentication uses an HTTP-only session cookie and CSRF protection. Direct API clients must preserve cookies, fetch a CSRF token, and send it using the returned header name on modifying requests. Refresh the token after login or logout. Passwords use BCrypt. Sessions have a 30-minute idle timeout and an eight-hour absolute lifetime.

## Validation

From `client/`:

```powershell
npm run typecheck
npm run lint
npm run build
npm test
```

Browser checks start an isolated Vite server and headless Chrome/Chromium. They cover routes, access states, responsive layouts, and themes with mocked API responses. Set `CHROME_PATH` if the browser is outside standard installation locations. Screenshots go to ignored `client/test-results/`. These checks do not validate live API or SMTP integration.

From `server/`:

```powershell
mvn clean verify
```

Authentication tests use in-memory H2, a schema fixture, and a test email sender. No real mail credentials are required. Validate PostgreSQL migrations and SMTP separately against development services.

For the engine, start the service and request <http://localhost:8000/health>. No dedicated engine test suite is currently included.

## Deployment

- Build the client with `npm run build`; output goes to `client/dist/`.
- Configure SPA fallback to `index.html` for frontend routes such as `/workspace/account`.
- Route `/api` to the Java service on the frontend origin. The Vite proxy is for development only.
- Use HTTPS and disable the `local` profile outside localhost development. Secure session cookies are enabled by default.
- Set `AUTH_PUBLIC_URL` to the HTTPS frontend address. Supply database and SMTP settings through deployment secrets.
- Deploying the engine does not enable backtests; API integration is still planned.

## Repository contents and privacy

The root README is the only included documentation. Local guides, planning documents, editor and agent settings, credentials, environment files, dependency caches, generated screenshots, database dumps, and dataset/export files are excluded through `.gitignore`. Keep actual user data and market datasets outside version control, including formats not covered by ignore rules.

Schema migrations and synthetic test fixtures remain so the project can build and initialize an empty database. Review staged files before future commits: `.gitignore` cannot detect secrets embedded in source code.

Third-party UI attribution remains in `client/src/components/ui/SHADCN-LICENSE`. No project-wide license is currently declared.

## Troubleshooting

| Symptom | Check |
| --- | --- |
| API fails to start | PostgreSQL availability, database existence, and database settings |
| Registration cannot send email | SMTP credentials, STARTTLS, and `MAIL_FROM`; some providers require an app password |
| Login requires verification | Enter the emailed code on `/verify-email`, or request a replacement |
| Local session cookie is missing | Set `SPRING_PROFILES_ACTIVE=local` for HTTP localhost development |
| Client cannot reach API | Start Java service and check `SERVER_PROXY_TARGET` |
| Browser checks cannot find Chrome | Set `CHROME_PATH` to the browser executable |
| Deployed deep link returns 404 | Configure frontend SPA fallback |
| Dataset or backtest actions unavailable | These features are still planned |
