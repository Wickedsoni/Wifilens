# 0004: Navigation approach

Status: Accepted (implementation planned with the Material 3 shell)

## Context
Navigation is a saved string route switched by `when` in `WifiLensApp`. There is no back stack, screens are
not addressable, and predictive back cannot work. Sub-screens under "More" are managed by their own local
state.

## Decision
Adopt Navigation Compose with type-safe routes: one top-level destination per bottom-bar tab and a nested
graph per feature. Set `enableOnBackInvokedCallback` for predictive back. Route arguments are small ids; large
state stays in Room or ViewModels. Cross-feature jumps (Map -> Diagnose) go through the app-level nav host, so
features stay independent.

## Consequences
State restoration after rotation and process death has to be tested for each destination. Tab switching gets
proper back-stack behaviour and motion.
