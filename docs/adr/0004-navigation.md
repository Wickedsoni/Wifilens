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

## Implementation status (Phase 3)
Navigation Compose 2.10 is in with a real back stack: four tab destinations, Back from any tab returns to
Analyze, then leaves the app; `enableOnBackInvokedCallback` is set. Routes are plain strings for now: type-safe
routes need the kotlinx.serialization compiler plugin and only pay off once destinations take arguments
(Phase 6 multi-plan). Sub-screens under More are still local state and move onto the graph with that phase.
