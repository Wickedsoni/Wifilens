# Sprint Log

Scrum-lite log for WifiLens 2.0 (plan: [ROADMAP.md](ROADMAP.md), rules: [ANDROID_GUIDELINES.md](../engineering/ANDROID_GUIDELINES.md)).
Token figures are the remaining-context counter the agent sees (the only measure available to it).

## Definition of Done (every sprint)
- `./gradlew spotlessCheck detekt testDebugUnitTest lintDebug assembleRelease` green
- detekt baseline did not grow; `graphify update .` run
- dead files removed, docs/ADRs updated
- device gate passed on the physical phone (user-approved), merged to `main`

## Backlog status

| Sprint | Goal | Status |
|---|---|---|
| 0 | Knowledge base + project setup | Done |
| 1 | Toolchain, Hilt, `:core:common`, lifecycle-aware state | In progress |
| 2 | Pure M3 Expressive design system | To do |
| 3 | Logo, splash, motion | To do |
| 4 | Permission handling | To do |
| 5 | Strings + accessibility | To do |
| 6 | DB v2, multiple plans, JSON import/export | To do |
| 7 | Walk survey + calibration | To do |
| 8 | Signal history + charts | To do |
| 9 | PNG/PDF report | To do |
| 10 | Performance (baseline profile, benchmarks) | To do |
| 11 | Release (AAB, policy, listing) | To do |

## Sprint 0: Knowledge base
- **Tokens:** start 14,900,504, end 14,867,700 (~33k used)
- **Done:** shallow-cloned the 6 reference repos into the scratchpad; wrote `docs/engineering/ANDROID_GUIDELINES.md`; saved the roadmap; linked the guide from `CLAUDE.md`; deleted the clones.
- **Findings:** stable toolchain is AGP 9.4.1 / Kotlin 2.4.20 / BOM 2026.09.00. ai-samples is cloud/Gemini-based, so it isn't adopted (privacy-first). Physical-device baseline-profile generation needs API 33+ or root.
- **Session 2:** installed Android CLI 1.0 (`--no-metrics`) and `skills add --all --agent=claude-code`, which put 26 skills in `~/.claude/skills`. `android init` also dropped an `android-cli` skill into the .gemini, .codex and .junie folders. Studied the Kotlin 2.4.20 notes (guidelines §1a). KMP was rejected and the app stays pure Android (§1b). The test device is the Moto Edge 40.
- **Retro:** WebFetch can't render JS pages (the KMP get-started page); fall back to sibling doc pages. Kept every clone outside the repo, so there was nothing to clean up.
