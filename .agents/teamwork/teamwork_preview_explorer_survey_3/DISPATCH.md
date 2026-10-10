# DISPATCH — Explorer Survey 3

## Identity
- Role: Test Automation & CDP Infrastructure Explorer
- Type: teamwork_preview_explorer
- Working directory: D:\Projects\strongwine_2\Wine-website\.agents\teamwork\teamwork_preview_explorer_survey_3

## Authoritative Inputs
- Read `D:\Projects\strongwine_2\Wine-website\.agents\teamwork\ORIGINAL_REQUEST.md`
- Read `D:\Projects\strongwine_2\Wine-website\AGENTS.md`

## Objectives
Investigate the testing environment and Chrome DevTools Protocol (CDP) test execution capabilities:
1. Check available tooling in the environment (Node.js, npm, Python, Chrome/Chromium installation, Playwright, Puppeteer, Selenium, or Java-based Selenium/CDP).
2. Check existing test suites in the repository (`src/test/...`, `package.json`, or test scripts).
3. Investigate how Chrome DevTools Protocol can be driven (e.g. via Puppeteer / Playwright / Python playwright / selenium / custom CDP script) to:
   - Monitor and assert zero console errors / exceptions.
   - Handle authentication flows (cookies / sessions) for anonymous, demo, admin, and shipper.
   - Emulate mobile viewport (390x844) for shipper.
   - Verify HTTP status codes (especially 403 Forbidden redirects or direct responses).
   - Capture screenshots and store visual artifacts.
   - Verify SSE events and real-time DOM updates.
4. Formulate the recommended CDP execution harness and test runner setup.

## Output
Write your findings and evidence to `handoff.md` in your working directory:
`D:\Projects\strongwine_2\Wine-website\.agents\teamwork\teamwork_preview_explorer_survey_3\handoff.md`.
Then send a completion message to the Project Orchestrator.


## 2026-10-10T05:48:09Z
You are Explorer Survey 3. Read D:\Projects\strongwine_2\Wine-website\.agents\teamwork\teamwork_preview_explorer_survey_3\DISPATCH.md, D:\Projects\strongwine_2\Wine-website\.agents\teamwork\ORIGINAL_REQUEST.md, and D:\Projects\strongwine_2\Wine-website\AGENTS.md.
Investigate test automation tools, Chrome DevTools Protocol (CDP) runners, environment binaries (Node, Chrome, Python, etc.), and design the CDP test harness.
Write your complete report to D:\Projects\strongwine_2\Wine-website\.agents\teamwork\teamwork_preview_explorer_survey_3\handoff.md and send a completion message back with your findings.
