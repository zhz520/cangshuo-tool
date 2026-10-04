# 沧烁工具箱 AI Coding Rules

## Required reading
Before making changes, read:
- PROJECT_SPEC.md
- ROADMAP.md

If present, also read:
- ARCHITECTURE.md
- DATABASE.md
- API.md

For Android UI changes, also read `../docs/ANDROID_UI_SPEC.md`. Use the Stitch references under `../stitch_cangshuo_tool_android_ui_redesign/` and reuse the shared theme/loading components.

## Workflow
1. Inspect existing code first.
2. State the smallest implementation plan.
3. List files to create/modify.
4. If DB changes, create a Flyway migration.
5. If API changes, update DTOs, tests, and Android models.
6. Implement one task only.
7. Run tests/build/lint.
8. Report exact verification results.
9. Update ROADMAP.md.

## Do not
- Perform large unrelated refactors.
- Upgrade all dependencies without a task.
- Duplicate existing services/models.
- Change API fields without updating clients.
- Change DB schema without migration.
- Put API secrets in Android.
- Log passwords, tokens, or raw sensitive user content.
- Claim a task is complete without verification.

## Android
- Compose + ViewModel + UseCase + Repository.
- UI must not call Retrofit directly.
- UI must not access Room directly.
- Every tool has a unique code.
- Register tools in ToolRegistry.
- Handle Loading/Success/Empty/Error/Unsupported where applicable.

## Server
- Controller -> Service -> Mapper/Repository.
- Validate requests.
- Use unified API response/error format.
- Use Flyway for schema changes.
- Keep secrets in environment/configuration management.

## Git
Use small commits:
- feat:
- fix:
- refactor:
- test:
- chore:
