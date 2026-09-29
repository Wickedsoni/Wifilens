# 0007: Database v2 and multiple floor plans

- **Status:** Accepted (2026-09-29, Sprint 6)
- **Context:** The user approved multiple plans, JSON import/export, walk-survey measurements, signal history and speed-test history. The v1 tables were already plan-scoped (`planId` foreign keys with cascade on cells, rooms and pins). The single-plan limit existed only in `SELECT … FROM grid_plan LIMIT 1`.

## Decision

- **The active plan is database state:** `grid_plan.lastOpenedAt`, where the active plan is the most recently opened one (ties broken by id). It's atomic with the data and can't reference a deleted plan. That's why we chose it over an `activePlanId` in DataStore.
- **Migration v1 → v2 is hand-written and additive** (ADR 0003):
  - It adds timestamps and nullable calibration columns to `grid_plan`, and backfills "now" into existing plans, so the user's plan stays active.
  - It creates `measurement`, `scan_sample`, `channel_congestion` and `speed_test` now, so Sprints 7 and 8 need no migration.
  - Its statements are copied from the exported `2.json`.
- **Saves are addressed by plan id and preserve the name.** Before this, `savePlan` wrote to whichever plan was active at save time and reset the name to "Home". With several plans, a debounced autosave landing after a switch would have overwritten the wrong plan.
- **JSON export format:** versioned (`formatVersion`), validated in pure domain code before anything is written. An import always creates a **new** plan inside one transaction, and never overwrites.

## Consequences

- Deleting the active plan activates the next most recently opened one.
- `DatabaseMigrationTest` covers row preservation, the backfill, the new tables and cascades.
