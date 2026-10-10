# DISPATCH — Explorer Survey 1

## Identity
- Role: Backend & Runtime Architecture Explorer
- Type: teamwork_preview_explorer
- Working directory: D:\Projects\strongwine_2\Wine-website\.agents\teamwork\teamwork_preview_explorer_survey_1

## Authoritative Inputs
- Read `D:\Projects\strongwine_2\Wine-website\.agents\teamwork\ORIGINAL_REQUEST.md`
- Read `D:\Projects\strongwine_2\Wine-website\AGENTS.md`

## Objectives
Investigate the backend architecture, security rules, and active runtime configuration of the StrongWine application:
1. Examine Spring Boot application configuration (`application.properties` / `application.yml`, server port, database settings, context path).
2. Check Spring Security configuration (`SecurityFilterChain`, RBAC roles `ROLE_ADMIN`, `ROLE_USER`, `ROLE_SHIPPER`, public routes vs protected routes, redirect vs 403 handling).
3. Check seed users and credentials (`demo`, `admin`, `shipper.ops.hcm` passwords, authorities).
4. Check controllers and routes:
   - Anonymous routes: `/home`, `/wines`, `/wines/{id}`, `/cart`, `/login`, `/register`, `/forgot-password`.
   - Customer routes: `/orders`, `/orders/{id}`, order authorization / privacy check logic.
   - Admin routes: `/admin`, `/admin/wines/import`, inventory export endpoints.
   - Shipper routes: `/shipper/dashboard`, OTP endpoints.
   - SSE streaming endpoint(s) for orders and live updates.
5. Check if the server is already running (e.g. check open ports like 8080 or running Java processes) or commands needed to run it (`./mvnw spring-boot:run` or similar).

## Output
Write your findings and evidence to `handoff.md` in your working directory:
`D:\Projects\strongwine_2\Wine-website\.agents\teamwork\teamwork_preview_explorer_survey_1\handoff.md`.
Then send a completion message to the Project Orchestrator.
