# Admin authorization hotfix — 2026-10-23

## Scope

Report: `02-聚芯Orin-GPU面板.md`, sections 2.1 and 2.3.

- Original Orin: `nvidia.juxinsuanli.cn`, `backend/`.
- Forked operations backend: `jd.ldjuxin.yun`, `juxin-node/backend/`.
- Both backend artifact versions: `0.0.2-SNAPSHOT`.

## Root cause and changes

The authentication filter classified raw `getRequestURI()` text, while Spring MVC resolved encoded route characters. `/api/%61dmin` therefore did not enter the `/api/admin` authentication branch.

Both filters now decode the application-relative authorization path once, exclude the servlet context path, and reuse the same path for protected-route classification, login exclusion, and factory-role restrictions. Malformed encodings, residual percent signs, semicolons, backslashes, duplicate slashes, control/space characters, and dot traversal segments are rejected with HTTP 400 before routing. Anonymous requests to the reported encoded management routes now return HTTP 401 before touching business services.

Added 34 regression cases per backend covering device lists, statistics, task dispatch, full/partial encoding, non-admin-prefix management routes, context paths, invalid/user/deleted-admin tokens, factory restrictions, authenticated reads/dispatch, and existing public/device paths. Existing multipart and audit-redaction tests pass.

The fork's existing settings test needed two explicit stubs for hourly-rate lookups; only test setup changed.

## Verification

- Before fix: initial 25-case regression suite had 12 failures and 4 errors; encoded requests reached real controllers.
- After fix: original backend `mvn verify`: 178 tests, 0 failures, 0 errors, 0 skipped.
- After fix: fork backend `mvn verify`: 173 tests, 0 failures, 0 errors, 0 skipped.
- Both builds used local JDK 21 and produced executable Spring Boot JARs.
- `git diff --check` passed.
- Read-only production probes to the report IP with the correct host/SNI returned HTTP 502 for `/api/health` and encoded device statistics. This is not evidence of production remediation.
- No live task dispatch, production writes, push, deployment, or credential changes were performed.

## Build SHA-256

- `backend`: `3a87eeb8bb6d9a111d4cdf6d39927b9e9d36d48208fee860f53319d8b54a981b`
- `juxin-node/backend`: `370e25a5c93914a508c4c6694bc3bd4df6d3c003d54c488259c71359d01891c1`

## Deployment

Review the diff and record current image IDs and health before deploying. From the server repository, rebuild/recreate only each affected backend, preserving environment files, MySQL, and volumes:

```sh
cd /www/wwwroot/NVIDIA-Pro
docker compose --env-file .env up -d --build --no-deps backend
cd /www/wwwroot/NVIDIA-Pro/juxin-node
docker compose --env-file .env up -d --build --no-deps backend
```

Check both local backend health endpoints and both domains. Anonymous plain/encoded device lists, statistics and task management requests must be denied; authenticated administrator operations and factory QR export must continue to work. Validate dispatch denial with a harmless, invalid task body only, without targeting a real device. Preserve previous backend images for rollback.

The user explicitly authorized pushing and deploying this fix. Production verification must be recorded after deployment.

Pre-commit npm audit was run for both admin packages: each reported 6 existing high-severity production dependency findings. Frontend dependencies are unchanged by this backend hotfix; track dependency upgrades separately.
