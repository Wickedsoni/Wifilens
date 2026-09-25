# 0003: Room schema export and migration policy

Status: Accepted

## Context
The database is at version 1 and real installs already hold plans. New features (multiple plans,
measurements, scan history) change the schema.

## Decision
- `exportSchema = true`; the generated JSON in `core/database/schemas/` is committed and reviewed with each
  schema change. It feeds `MigrationTestHelper` in the instrumented tests.
- Never use `fallbackToDestructiveMigration`. Every version bump ships an explicit `Migration` that only adds
  or rebuilds tables and preserves data.
- Every migration has a test that opens the previous version's schema, inserts rows the way that version did,
  migrates, and asserts the data survived.
- Rebuilding tables (SQLite cannot alter foreign keys) follows create-new, copy, drop-old, rename, and runs in a
  transaction.
- High-volume tables (scan history) get retention and write throttling from the start.

## Consequences
Schema changes take more effort but cannot silently lose a user's floor plan. `DatabaseMigrationTest` is the
template to extend.
