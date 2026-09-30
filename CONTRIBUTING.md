# Contributing to WifiLens

Thanks for helping. This page covers how branches, commits and pull requests work here. For the code itself, read
[DEVELOPMENT.md](DEVELOPMENT.md) (module rules and hard constraints) and [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Branches

`main` is always releasable. It is protected: changes land only through a pull request with a green CI run, and
history is linear.

Work on a short-lived branch cut from `main`, named by type:

| Prefix | Use for | Example |
|---|---|---|
| `feat/` | A new feature or visible behaviour | `feat/measured-vs-predicted` |
| `fix/` | A bug fix; put the bug id in the name when there is one | `fix/b-58-widget-refresh` |
| `docs/` | Documentation only | `docs/user-guide-survey` |
| `chore/` | Build, CI, dependencies, tooling | `chore/agp-9-2` |
| `release/` | Version bump and release notes for one release | `release/2.0.3` |

Keep a branch to one topic and merge it within days. GitHub deletes the branch after merge. Don't keep long-running
branches; if a change is big, land it in slices behind the existing UI.

Releases are marked with a tag on `main` (`v2.0.2`), not with a branch.

## Commits

Use [Conventional Commits](https://www.conventionalcommits.org/): `type(scope): summary`, for example
`fix(map): keep room names readable under device pins (B-51)`. Types: `feat`, `fix`, `docs`, `refactor`, `perf`,
`test`, `build`, `ci`, `chore`, `release`. Write the summary in the imperative, under about 72 characters, and use
the body to say *why*.

## Pull requests

1. Branch from an up-to-date `main`.
2. Run the same checks CI runs before you push:
   ```bash
   ./gradlew spotlessApply detekt checkModuleGraph testDebugUnitTest lint
   ```
3. Open the PR with the template filled in: what changed, why, how you tested it, and screenshots for UI changes.
4. CI must pass. PRs are **squash-merged**, so the PR title becomes the commit on `main`; make it a good
   Conventional Commit.

## Bugs

Bugs are tracked in [docs/bug-log.md](docs/bug-log.md) with an id (B-nn), severity, how it was found and how it was
fixed. Log a bug there first; fix it in its own `fix/` branch with a test that fails without the fix.

## Code quality

- `spotlessCheck` (ktlint) checks files changed since `origin/main`; anything you touch must be clean.
- `detekt` runs with a frozen baseline in `config/detekt/baseline.xml`. The baseline may only shrink; never add to it
  to silence new code.
- Room schema changes need a migration and an exported schema; see [ADR 0003](docs/adr/0003-room-migrations.md).
- Decisions that shape the code get an ADR in [docs/adr](docs/adr/README.md).

## Versioning

`versionName` follows `MAJOR.MINOR.PATCH`. `versionCode` in `app/build.gradle.kts` must grow with every upload to
Play (Play rejects a reused code). Every release adds an entry to [CHANGELOG.md](CHANGELOG.md). The full checklist is
in [docs/release/README.md](docs/release/README.md).

## Conduct

Be kind and assume good intent. See [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md).
