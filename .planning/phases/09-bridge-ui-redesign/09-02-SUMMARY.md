---
phase: 09-bridge-ui-redesign
plan: 02
subsystem: ui
tags: [compose, animation, dropdown, progressive-disclosure, bridge, kotlin]

# Dependency graph
requires:
  - phase: 09-bridge-ui-redesign
    plan: 01
    provides: fromSuggestions/toSuggestions StateFlows, clearFrom/clearTo methods, confirmTo auto-trigger
provides:
  - ArtistSearchInput composable with DropdownMenu autocomplete
  - Progressive disclosure UI: single From input -> chip + animated To input reveal
  - AnimatedContent crossfade between input and searching states
  - AssistChip dismissal for confirmed artists
affects:
  - End-user Bridge tab UX — fully redesigned progressive disclosure flow

# Tech tracking
tech-stack:
  added: []
  patterns:
    - ArtistSearchInput: BasicTextField + DropdownMenu + onGloballyPositioned width-matching
    - AnimatedContent for Idle/Searching crossfade (tween 300ms)
    - AnimatedVisibility expandVertically + fadeIn for To input reveal
    - AssistChip with trailingIcon for confirmed-artist chip dismissal
    - FocusRequester + LaunchedEffect for auto-focus after From confirmation

key-files:
  created: []
  modified:
    - app/src/main/kotlin/com/metrolist/music/ui/screens/bridge/BridgeScreen.kt
    - app/src/main/res/values/metrolist_strings.xml

key-decisions:
  - "GhostTextField fully removed — progressive disclosure replaces side-by-side ghost text layout"
  - "ArtistSearchInput uses onGloballyPositioned width-match for DropdownMenu (Research Pitfall 1)"
  - "No confirm-on-focus-loss in ArtistSearchInput — user must tap a suggestion (Research Pitfall 4)"
  - "AnimatedContent wraps both input and searching states for crossfade — Idle/Searching branches removed from when block"
  - "Seed chips repositioned below From input inside AnimatedContent idle branch (D-09)"
  - "onSeedChipClick simplified to two branches: fromConfirmed.isBlank -> fill From, else fill To"

patterns-established:
  - "ArtistSearchInput: Box wrapping BasicTextField + DropdownMenu, width from onGloballyPositioned"
  - "Progressive disclosure: fromConfirmed.isBlank controls From input vs AssistChip; AnimatedVisibility controls To section"

requirements-completed: [BRDG-01, BRDG-04, BRDG-06]

# Metrics
duration: 4min
completed: 2026-04-05
---

# Phase 9 Plan 02: Bridge UI Redesign — Progressive Disclosure Screen Overhaul Summary

**GhostTextField replaced by ArtistSearchInput with dropdown autocomplete; side-by-side inputs replaced by progressive disclosure with animated reveal, AssistChips, and AnimatedContent crossfade**

## Performance

- **Duration:** 4 min
- **Started:** 2026-04-05T06:26:04Z
- **Completed:** 2026-04-05T06:30:26Z
- **Tasks:** 1 auto + 1 auto-approved checkpoint
- **Files modified:** 2

## Accomplishments

- Removed `GhostTextField` composable (lines 92-203) entirely from BridgeScreen.kt
- Added `ArtistSearchInput` composable: `BasicTextField` with `DropdownMenu`, width-matched via `onGloballyPositioned`, dropdown shows `suggestions` from ViewModel `fromSuggestions`/`toSuggestions`
- Progressive disclosure flow: From input (or chip) always visible; To section reveals via `AnimatedVisibility(expandVertically + fadeIn)` only when From confirmed
- `AssistChip` with trailing X icon for each confirmed artist — `onClick` calls `viewModel.clearFrom()` / `viewModel.clearTo()`
- `AnimatedContent` crossfades (tween 300ms) between input disclosure state and searching progress state
- `LaunchedEffect(fromConfirmed)` auto-focuses To input after From artist confirmed (D-04)
- `onSeedChipClick` simplified to 2 branches (was 5) — no focus tracking vars needed
- Removed `fromHasFocus`, `toHasFocus`, and the Find Bridge `Button`
- Removed `bridge_ghost_confirm_hint` string; added `bridge_clear_from_description` and `bridge_clear_to_description`
- All existing features preserved: PathSheet, queue dialog, seed chips, RandomBridgeFab, error state

## Task Commits

1. **Task 1: Replace GhostTextField with progressive disclosure and dropdown autocomplete** — `38f315ea` (feat)
2. **Task 2: Visual verification** — auto-approved (checkpoint:human-verify in --auto mode)

**Plan metadata:** TBD (docs: complete plan)

## Files Created/Modified

- `app/src/main/kotlin/com/metrolist/music/ui/screens/bridge/BridgeScreen.kt` — Full overhaul: GhostTextField removed, ArtistSearchInput added, progressive disclosure layout, AnimatedContent, AnimatedVisibility, AssistChips, auto-focus LaunchedEffect
- `app/src/main/res/values/metrolist_strings.xml` — Removed `bridge_ghost_confirm_hint`; added `bridge_clear_from_description` and `bridge_clear_to_description`

## Decisions Made

- `ArtistSearchInput` uses `onGloballyPositioned` + `LocalDensity` to width-match the `DropdownMenu` to the input box (Research Pitfall 1 — custom width required, not `fillMaxWidth` inside a menu)
- No confirm-on-focus-loss: `onFocusChanged` only updates `showDropdown` state, never calls `confirmFrom`/`confirmTo` (Research Pitfall 4)
- `AnimatedContent(targetState = uiState is BridgeUiState.Searching)` wraps the entire input+searching section; `when (uiState)` block handles only PathFound, PlaylistReady, and Error
- Seed suggestion chips placed inside the AnimatedContent idle branch, below the From input — consistent with D-09 repositioning

## Deviations from Plan

None — plan executed exactly as written. All acceptance criteria met before compilation; Kotlin compilation succeeded with pre-existing deprecation warnings only (not errors).

## Known Stubs

None — all data sources wired. `fromSuggestions`/`toSuggestions` consume real `StateFlow<List<String>>` from ViewModel, populated by `LastFM.searchArtists()`. No placeholder data flows to UI.

## Self-Check: PASSED
