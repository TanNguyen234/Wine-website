# Integration Preflight
Core principle: Prove the boundary before running the pipeline. A unit test, mock, cached artifact, successful click, or fallback output is not live-provider evidence.
Evidence levels: unit, integration, sandbox, live, NOT RUN.
Minimal live proof: When authorized, run the smallest representative case first. Preserve command, exit code, provider response.
