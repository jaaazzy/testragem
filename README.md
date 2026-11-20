# Testragem

A crypto backtesting research application using React, Spring Boot and PostgreSQL. Bounded Java simulations run synchronously inside the API. The optional Python AI service is separate from simulation; there is no broker, job-attempt system or live-trading integration.

## Current implementation

- Email registration/verification, session sign-in/out, CSRF protection and protected routing.
- Immutable historical CSV snapshots, SHA-256 fingerprints, UTC coverage and missing-hour reporting.
- A long-only accounting engine: next-open execution, allocation, fees, slippage, trade ledger, equity and drawdown.
- Owned configuration/result persistence, paginated history and JSON export.
- Account-owned, versioned presets; experiment names, notes and tags; reusable financial defaults; and comparisons of two to four saved runs.
- Strategy settings, dataset browser, run submission and saved-result screens using local shadcn components.
- Docker Compose packaging and CI configuration.

**Configuration validation and all three strategies are implemented.** Moving-average crossover is available for production runs, with defaults `fastPeriod=10` and `slowPeriod=30`. It uses completed candle closes, waits for two full SMA observations before detecting crossings, and executes signals at the next candle open. Its actual SMA values feed the result charts. Wilder RSI is available with defaults `period=14`, `lowerThreshold=30`, `upperThreshold=70`: buy on recovery above the lower threshold and sell on decline below the upper threshold. It seeds after 14 changes (15 closes), emits no signal on its first observation, and supplies the same RSI values to charts. Buy-and-hold is implemented and enabled: enter at the first open, then hold and value the position at the final close without inventing a sale. Integration tests use test-only strategies to exercise the surrounding application; unit tests also exercise the production SMA and RSI strategies through the execution engine.

The animated hero is generated sample data, independent of historical backtest datasets.

## Backend architecture

1. **Configuration validation:** `DefaultConfigurationValidator` applies financial, date, dataset, parameter and warm-up rules before simulation. Validation failures return field errors for the configuration form.
2. **Strategy execution:** `DefaultStrategyFactory` creates a fresh buy-and-hold, SMA crossover or Wilder RSI instance for each run. Strategies emit buy/sell/hold signals from completed candles and provide the same indicator observations to result charts.

The execution engine handles quantities, balances and costs; strategies never trade directly. Candle and configuration records are immutable. Unit tests cover indicator calculations, signal timing and accounting. Custom code execution and a rule-language interpreter are outside this release.

## Development

Requirements: Java 21+, Node.js 22.12+ and PostgreSQL. The backend includes Maven Wrapper scripts; no global Maven installation is required.

For the backend, create an empty development database and set the environment in your terminal or private IntelliJ run configuration:

```powershell
$env:DB_URL='jdbc:postgresql://localhost:5432/testragem'
$env:DB_USERNAME='postgres'
$env:DB_PASSWORD='your-local-password'
$env:SPRING_PROFILES_ACTIVE='local'
$env:AUTH_PUBLIC_URL='http://localhost:5173'
cd server
.\mvnw.cmd clean spring-boot:run
```

Verify the wrapper with `.\mvnw.cmd -v` from `server`. It pins Maven 3.10.0 and downloads it on first use if not already cached; a JDK must be available through `JAVA_HOME` or `PATH`. On Linux/macOS use `sh ./mvnw` in place of `.\mvnw.cmd`.

Spring Boot does not automatically load `.env` files. The local profile permits HTTP cookies on localhost; use HTTPS with the default secure-cookie configuration for public deployment. Supply SMTP host/port/username/password and `MAIL_FROM` to register and verify real accounts. Keep these values out of frontend configuration and source control.

For the client:

```powershell
cd client
npm ci
npm run dev
```

The Vite `/api` proxy targets Spring Boot on port 8080. Client routes use hash routing, including `#/workspace/backtests/{id}`. Public deployment serves frontend assets and `/api` from the same origin.

### Project structure

```text
client/                 React UI and routing
server/                 Spring Boot API, persistence and simulation
  .../auth/             Registration, verification and sign-in
  .../security/         Session and route protections
  .../user/             User identity
  .../dataset/          Controller, importer, reader, service, repository and models
  .../strategy/         Catalog controller/service, factory and trading rules
  .../backtest/         Controller, service, repository, validation, engine, DTOs and models
  .../common/           Health and consistent API errors
ai-service/             Python AI research and future inference (FastAPI)
  src/testragem_ai/     Application factory, API routes and environment settings
  tests/                Service contract and configuration tests
```

Java owns simulations, execution costs and balances. Python is reserved for market-regime classification and future AI-assisted strategy selection/evaluation. It does not run a second simulator. The frontend continues to talk only to Spring Boot. AI integration, training, leakage-safe preprocessing, versioned model artifacts and held-out comparisons remain future work.

### Python AI service setup

Python 3.11+ is required; Python 3.12 is used in CI and Docker. From the repository root:

```powershell
cd ai-service
py -3.12 -m venv .venv
.\.venv\Scripts\python.exe -m pip install -e ".[dev]"
.\.venv\Scripts\python.exe -m testragem_ai
```

If the `py` launcher is unavailable, use `python -m venv .venv` with a supported Python version. Calling the virtual environment's executable directly avoids changing PowerShell activation policy.

Open `http://127.0.0.1:8001/docs` for the internal API. `GET /health` and `/health/live` return 200 when the process runs. `GET /health/ready` intentionally returns 503 with `ai_model_not_implemented`: no model has been trained or wired in, and there are no prediction endpoints yet. This readiness response does not mean installation failed.

Run tests with `.\.venv\Scripts\python.exe -m unittest discover -s tests -v`. Configure `AI_HOST`, `AI_PORT` and `AI_LOG_LEVEL` in the process environment; defaults are `127.0.0.1`, `8001` and `info`. No `.env` file loads automatically. Keep secrets and model/dataset artifacts outside Git.

This is an optional internal service. Local startup binds to loopback; no browser CORS access or public proxy is configured. Public exposure requires service authentication and deployment controls. Spring Boot does not depend on it yet, so the regular app runs without Python. The optional Compose profile starts it with `docker compose --env-file .env --profile ai up --build -d`; only host loopback port 8001 is published. Container health checks use liveness while model readiness remains unavailable.

### Personal research workspace

Save a named preset above the workspace configuration form. Loading it restores the strategy, parameters, historical range and financial assumptions. Saving a new version appends an immutable revision; earlier versions remain selectable. A preset can also be saved as a separate preset. The list shows the latest 200 presets for the signed-in account. Presets use the existing three supported strategy types; a custom rule interpreter is not implemented.

Account > Research defaults stores capital, fees, slippage, allocation and comparison focus. Apply financial defaults explicitly with **Use my defaults** so loading preferences never silently changes an edited draft. Result pages support **Reuse settings** and personal names, notes and up to eight tags. These annotations are separate from immutable simulation results. Names and tags appear in history, where two to four experiments can be selected for comparison. Different datasets, ranges, capital, costs, allocation or engine versions produce a comparability notice; selecting a focus highlights a metric rather than declaring a winning strategy.

V12 adds the workspace storage. Restart the backend to apply it through Flyway. All routes require the existing session; POST/PUT requests require CSRF. No request accepts an owner ID. Unknown and other-account preset/run IDs return the same 404 response. Annotation batch reads omit inaccessible IDs.

| Method | Route | Purpose |
| --- | --- | --- |
| GET / POST | `/api/workspace/presets` | List latest presets / create a preset |
| GET / POST | `/api/workspace/presets/{id}/versions` | Read history / append a revision |
| GET / PUT | `/api/workspace/preferences` | Read / save account defaults |
| GET | `/api/workspace/annotations?ids=...` | Read metadata for up to 50 owned runs |
| PUT | `/api/workspace/annotations/{id}` | Save an experiment name, notes and tags |
| GET | `/api/backtests/compare?ids=...` | Read two to four owned results |

`WorkspaceIntegrationTest` verifies real PostgreSQL migrations, ownership, CSRF, preset revision retention, financial defaults, annotations and comparisons. It uses the same dedicated `BACKTEST_TEST_DB_*` variables as `BacktestIntegrationTest`; never point these tests at the development or production database.

### Migration safety

V1-V8 preserve the existing identity/catalog/configuration foundation. The uncommitted V9/V10 drafts were invalid and referenced the deferred job design. Their original contents are preserved locally; V9 now defines synchronous results and V10 defines displayed trade/equity detail.

Use a clean build after renaming migration files, so stale files in `target/classes` cannot introduce duplicate versions. If an environment already applied a different V9/V10, Flyway validation must stop startup. Do not repair checksums, drop tables, or reset data to bypass that mismatch; it needs a reviewed forward-migration path.

Existing configuration rows remain nullable for compatibility. New saves require an authenticated owner and dataset, enforced by the application. Configurations/results have no edit/delete endpoints; rerunning creates a new immutable pair. Original authentication migrations are unchanged.

### Historical data

Set `BACKTEST_DATASET_CSV` to a local, operator-provided **BTCUSDT hourly OHLCV CSV**, then restart the backend. Set `BACKTEST_DATASET_SOURCE` to its provenance. The importer does not infer asset identity or redistribution rights; verify those before a public release.

For persistent local settings, copy `server/config/application.example.properties` to `server/config/application.local.properties`, fill in the CSV path, and use `SPRING_PROFILES_ACTIVE=local`. This private file is ignored by Git and Docker. It is loaded when starting from either `server/` or the repository root; environment overrides remain supported. Startup logs explicitly report the loaded candle count and coverage, or warn when no path is configured.

Required columns are `timestamp_utc` (or `timestamp`), `open`, `high`, `low`, `close`, `volume`. Timestamps must include an offset, align to UTC hours and increase strictly. Additional research/label columns are ignored and never exposed to strategies. Negative/invalid OHLCV, duplicates and unordered rows are rejected.

The loader captures one immutable in-memory snapshot at startup and registers its metadata in PostgreSQL. Files are limited to 80 MB / 100,000 candles. Missing hours are counted without interpolation; ranges crossing them are rejected. A changed file has a different fingerprint and dataset version. Only the currently loaded snapshot is offered for new runs; saved results retain the original fingerprint and settings.

There is no public file-upload endpoint and no automatic exchange download. With no CSV configured, the API starts normally and returns an empty dataset catalogue. Actual datasets remain outside Git; the three-row test CSV is explicitly synthetic.

### Execution rules

- Buy-and-hold may enter at the first selected open through `onStart()`. Close-based signals execute at the next candle's open; final-close signals cannot execute.
- One long position, no leverage, no pyramiding. Allocation is a percentage of available cash, including entry fees.
- Adverse slippage changes execution prices; it is reported separately, not debited twice. Fees apply on each executed side.
- Quantity rounds down to 12 decimals, money to eight decimals. Remaining holdings are marked to the final close without inventing a closing sale.
- Return and drawdown values are fractions. A value of `0.25` means 25%.
- Maximum 10,000 candles, one concurrent run per API instance, 20 submissions per user per 15 minutes, and a cooperative five-second calculation budget. These limits target trusted developer-supplied strategies, not arbitrary untrusted code.

## API

All research endpoints require an authenticated session. Modifying requests also require a fresh CSRF token from `GET /api/auth/csrf`.

| Method | Endpoint | Purpose |
| --- | --- | --- |
| GET | `/api/datasets` | Loaded dataset, coverage, fingerprint and quality |
| GET | `/api/datasets/{id}` | Inspect loaded version |
| GET | `/api/datasets/{id}/candles?start=...&end=...` | Inclusive UTC candle window; maximum 10,000 candles |
| GET | `/api/strategies` | Supported strategy keys, defaults and run availability |
| POST | `/api/backtests` | Return 201 with a completed saved result, or an explicit error |
| GET | `/api/backtests?page=0&size=10` | Current user's history; maximum page size 50 |
| GET | `/api/backtests/{id}` | Owned settings, summary, fills, equity and recorded indicators |
| GET | `/api/backtests/{id}/chart` | Owned run's exact historical snapshot and bounded candle window |

POST accepts `datasetId`, `strategy`, inclusive `rangeStart` / `rangeEnd` UTC instants, `initialCapital`, `feeBps`, `slippageBps`, `allocationPercent` and `parameters`. Ownership is derived from the session, never the request. Unknown/non-owned run IDs both return 404. Pending modules return 501; a busy execution slot returns 503. Failed runs do not persist partial results.

### Result charts

Saved results include a historical candlestick chart with executed buy/sell markers, shared candle inspection, zoom and window navigation, equity and drawdown charts, and trade-to-candle selection. Charts support light/dark appearance, keyboard inspection and responsive mobile plots with a shared time window. The ledger is paginated in groups of 20 trades.

SMA overlays and RSI panels consume recorded `indicatorPoints` from the simulation. After `onCandleClose`, the engine snapshots `TradingStrategy.indicatorValues()` with the candle timestamp. Supported keys are `fastSma`, `slowSma`, and `rsi`; omit keys during warm-up. These must be the values already used for the decision. The frontend does not calculate replacement indicators. Buy-and-hold uses the default empty map.

V11 adds an indicator JSONB column with an empty default for older results. Missing traces show an explicit empty state. The chart endpoint checks run ownership before accessing data and verifies both dataset ID and fingerprint. If the original snapshot is not loaded, it returns 409 `dataset_snapshot_unavailable`; saved equity, drawdown and trades remain readable. Load the original CSV to restore price charts. Archived CSV retrieval is not implemented.

## Verification

```powershell
# server directory
.\mvnw.cmd clean verify

# Optional real PostgreSQL integration checks: use a DEDICATED TEST database.
$env:BACKTEST_TEST_DB_URL='jdbc:postgresql://localhost:5432/testragem_test'
$env:BACKTEST_TEST_DB_USERNAME='postgres'
$env:BACKTEST_TEST_DB_PASSWORD='your-test-password'
.\mvnw.cmd verify

# client directory
npm run build
npm run lint
npm test
```

Backend tests cover hand-calculated accounting, next-open timing, costs, allocation, CSV quality, authentication and pending-module behavior. PostgreSQL integration checks run only when the test URL is supplied and exercise real migrations, transactions, CSRF and cross-user isolation. The authentication suite uses a separate H2 identity fixture limited to V8.

Browser checks launch isolated Vite and Chrome/Chromium processes. They test routing, loading/error states, appearance, mobile layouts and fixture-based dataset/submission/history/results flows. Set `CHROME_PATH` if needed. Browser API responses are mocked; they do not validate real SMTP or production strategy implementations. Screenshots go to ignored `client/test-results/`.

## Docker and public release

Copy `deployment.env.example` to a PRIVATE `.env`, replace the database password, and configure SMTP:

```powershell
docker compose --env-file .env up --build -d
docker compose ps
```

Local default: `http://localhost:8088`. To connect data, mount your dataset directory via `DATASET_DIRECTORY` and set `BACKTEST_DATASET_CSV=/datasets/your-file.csv`.

The API container checks `/actuator/health/readiness`: application readiness, PostgreSQL connectivity and disk space. Optional integration failures (such as SMTP) still affect aggregate `/actuator/health`, but do not prevent the web container from starting. Both endpoints hide health details. SMTP must still be configured for registration and verification emails; delivery failures remain explicit application errors. After changing the Dockerfile or backend, rebuild with `docker compose up --build -d --wait`.

For a public Linux host, set `SITE_ADDRESS` to your hostname, `AUTH_PUBLIC_URL` to its HTTPS URL, ports to 80/443, and use the default Spring profile instead of `local`. Caddy terminates HTTPS and proxies `/api`; database/API ports are not published. Preserve named database/Caddy volumes, configure backups, and verify restore on a disposable environment. A hosting account, DNS and SMTP credentials must be supplied separately. No hosting purchase or public deployment has been performed.

CI checks Java tests against PostgreSQL, Python service tests, and client build/lint/browser checks. Docker runtime verification requires Docker; packaging alone is not a claim that the public release or strategy module is complete.

## Privacy

Keep credentials, environment files, real datasets and exports outside source control. Only the tiny synthetic test CSV is permitted by the dataset ignore rules. This README documents setup, architecture and implementation status.
