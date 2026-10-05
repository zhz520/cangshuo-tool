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
2. Read relevant business source and interactions in mature GitHub implementations (e.g. DevToys, CyberChef, IT-Tools, specialist Android libraries) before developing or repairing each tool. Follow `../docs/TOOL_DEVELOPMENT_GUIDE.md`; document source links, review date, adopted ideas, adaptation differences, feature/boundary coverage and actual verification. Naming a repository alone is not evidence of source review.
3. State the implementation plan and file scope.
4. Clean up and delete any unused/temporary files created during implementation.
5. If DB changes, create a Flyway migration.
6. If API changes, update DTOs, tests, and Android models.
7. Implement one task only.
8. Run tests/build/lint.
9. Report exact verification results.
10. Update ROADMAP.md.

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
- Tools may be native or web-hosted under the official site (`/tools/<code>/`). Use web hosting when complexity, dependencies or assets would significantly increase APK size; open it in the app with the system WebView (Chrome kernel). Web tools still register a unique code and ToolRegistry metadata, and need network/loading/offline/error states, HTTPS domain allowlisting and a privacy notice. See PROJECT_SPEC Decision 019 and ../docs/TOOL_DEVELOPMENT_GUIDE.md.

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
