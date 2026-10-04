# 沧烁工具箱 Roadmap

## Phase 0 — Foundation
- [x] Monorepo
- [x] API contract documentation
- [x] Android shell (Compose startup screen + Gradle Wrapper; assembleDebug and lintDebug passed; device run pending)
- [x] Spring Boot API (startup shell, health, response/error envelope, traceId, local OpenAPI; package/startup verified)
- [x] Admin shell (Vue + TypeScript layout/navigation, health overview, centralized API client; typecheck/build and local health integration verified; business modules pending)
- [x] Docker Compose (Linux images built; local MySQL, Redis, API, Nginx/Admin and optional source-built MinIO healthy; browser/API/routes/CORS checked; production configuration prepared, DNS/certificates/public deployment pending)
- [x] MySQL + Flyway (JDBC/HikariCP connection, versioned catalog schema and 13 categories; Docker/Windows package/startup, migration validation and health verified; later business tables follow their own tasks)
- [x] Redis (Spring Data Redis/Lettuce authenticated connection, explicit timeouts and aggregated health; Docker/Windows package/startup and live application connections verified; business caches, sessions and rate limits follow their own tasks)
- [x] CI (GitHub Actions workflow and local gates passed; main pushed to zhz520/cangshuo-tool; first hosted run 37128607920 passed all five jobs and uploaded four artifacts; build/lint/configuration gates, automated tests pending)

Phase 0 CI exit criterion was verified by the successful hosted run on 2026-10-03 for commit `08e7dd3`. Automated tests, device execution and release/deployment validation remain separate tasks. See `docs/CI.md`.

## Phase 1 — Walking Skeleton
- [x] Home (Compose → ViewModel → UseCase → Repository, local catalog/category/featured lists, four tabs, empty/error states and registered tool host; original home assembleDebug/lintDebug passed with 0 errors and 10 warnings; calculator, local search and favorites now integrated; device/runtime validation and recent pending; see docs/ANDROID_HOME.md)
- [x] ToolDefinition (Android unified metadata, 13 stable categories, modes/statuses and local executable contract; registry/home integration and catalog DTOs completed; calculator now implemented through this contract; assembleDebug/lintDebug passed; JSON/network integration pending)
- [x] ToolRegistry (Android fixed definition collection, duplicate-code validation, deterministic ordering, code/category/featured queries and status/device lookup results; wired into home through ToolboxAppContainer with calculator registered; assembleDebug/lintDebug passed; runtime checks pending)
- [x] GET /tools (anonymous exact GET /api/v1/tools, validated pagination/category filter, parameterized JDBC, enabled/non-deleted tools and categories, stable sortOrder/code ordering and read-only repeatable-read count/page; Server package, Docker/native startup and Android assembleDebug/lintDebug passed; Android DTOs and API docs synchronized; directory runtime cases, tests and client JSON/network/cache integration pending; see docs/TOOL_CATALOG.md)
- [x] Local calculator (registered calculator/CALC/LOCAL, Compose → ViewModel → UseCase → Repository, bounded decimal arithmetic with precedence/parentheses/percent, editing/copy/reuse and saved state; Chinese/English UI with system default and Chinese fallback; Android build/Lint passed with 0 errors and 11 warnings; V3 catalog metadata migration and native/Docker API startup passed; arithmetic/device/clipboard/locale runtime verification and tests pending; see docs/CALCULATOR.md)
- [x] Local search (independent Compose → ViewModel → UseCase → Repository over bundled enabled metadata; name/code/keywords/description/bilingual category literal matching, NFKC/case/whitespace normalization, all-term queries, category filter and stable relevance sorting; 80-character limit, clear/empty/error/retry and saved query/category; opens registered tools through the existing home flow and returns to search; Chinese/English UI; assembleDebug/lintDebug passed with 0 errors and 11 existing warnings; search examples, ranking/device/IME/navigation/state/locale runtime verification and tests pending; see docs/LOCAL_SEARCH.md)
- [x] Local favorite (application-scoped Room database v1 and exported schema, unique tool-code bookmarks with UTC timestamps, asynchronous Flow/suspend DAO through UseCase/Repository; shared stars on catalog/search/tool header, favorites list and removal, unavailable bookmarks retained, load/empty/error/retry and write failure states; same-tier favorite priority in local search; Chinese/English UI; Room 2.8.5 and KSP 2.3.6 build/code generation and assembleDebug/lintDebug passed with 0 errors and 11 existing warnings; actual persistence/restart/concurrency/failure/navigation/ranking/locale/device verification and tests pending; see docs/LOCAL_FAVORITES.md)
- [ ] Local recent

Exit criteria: Search -> Calculator -> Execute -> Recent works end-to-end.

## Phase 2 — Core Tools
- [ ] Unit converter
- [ ] Timestamp
- [ ] UUID
- [ ] Base64
- [ ] URL codec
- [ ] Hash
- [ ] JSON
- [ ] Text tools
- [ ] QR
- [ ] Image compression

## Phase 3 — Auth & Sync
- [ ] Register/login
- [ ] JWT
- [ ] Refresh token
- [ ] Profile
- [ ] Favorites sync
- [ ] Recent sync
- [ ] Settings sync

## Phase 4 — Device & Network
- [ ] Device info
- [ ] Storage
- [ ] Battery
- [ ] Sensors
- [ ] Compass
- [ ] Level
- [ ] Ping
- [ ] HTTP status

## Phase 5 — Admin
- [ ] Admin auth
- [ ] Tool CRUD
- [ ] Category CRUD
- [ ] Recommendation slots
- [ ] User management
- [ ] Announcements
- [ ] Feedback
- [ ] Operation logs

## Phase 6 — Cloud Tools
- [ ] MinIO
- [ ] OCR
- [ ] PDF
- [ ] AI
- [ ] File security
- [ ] Quotas
- [ ] Rate limits

## Phase 7 — Release
- [ ] Crash monitoring
- [ ] Release CI
- [ ] AAB
- [ ] Privacy policy
- [ ] Permission documentation
- [ ] Data deletion
- [ ] Production backups
- [ ] Staged release
