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
- [ ] Home
- [x] ToolDefinition (Android unified metadata, 13 stable categories, modes/statuses and local executable contract; assembleDebug/lintDebug passed; registry, catalog DTOs and feature integration follow their own tasks)
- [ ] ToolRegistry
- [ ] GET /tools
- [ ] Local calculator
- [ ] Local search
- [ ] Local favorite
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
