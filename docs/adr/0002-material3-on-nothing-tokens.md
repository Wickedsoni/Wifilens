# 0002: Material 3 structure on Nothing design tokens

Status: Accepted

## Context
The prototype uses hand-built components in a Nothing-inspired look (dot-matrix display font, monochrome
palette). Only a partial Material 3 colour scheme is set, so real Material components fall back to default
purple roles, and there is no Scaffold, NavigationBar, Snackbar or predictive back.

## Decision
Keep the visual identity, adopt Material 3 for structure and behaviour:
- `NothingColorTokens` stays the single source of truth. A complete `ColorScheme` (every role, including
  containers and `surfaceContainer*`), `Typography` and `Shapes` are generated from them in `Theme.kt`.
- Use Material components (`Scaffold`, `NavigationBar`, `Switch`, `FilterChip`, `SegmentedButton`,
  `SnackbarHost`) restyled through the theme, not per-call overrides. Thin `Nothing*` wrappers stay only where
  the look is genuinely custom.
- Dynamic colour is an opt-in setting (off by default, API 31+). Signal and heat-map colours are fixed and
  never follow the wallpaper, so "green means good" always holds.
- Text colours must reach WCAG AA (4.5:1); `ContrastTest` enforces it for every scheme.

## Consequences
Standard components bring accessibility semantics, motion and back handling for free. Some instrumented
tests need updated selectors, so tests should prefer stable test tags over text.
