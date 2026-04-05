# Phase 9: Bridge UI Redesign - Context

**Gathered:** 2026-04-05
**Status:** Ready for planning

<domain>
## Phase Boundary

Replace the current side-by-side ghost text inputs with a progressive disclosure search UX. Phase 9 changes ONLY the input/trigger flow and state transitions — the path result view (bottom sheet with genre tags, listener counts, now-playing highlight) from Phase 6 stays unchanged. The existing `BridgeUiState` sealed class, `BridgeViewModel`, and `BridgeAlgorithm` (Phase 8 native Kotlin) remain the same — only the Compose UI layer in `BridgeScreen.kt` is redesigned.

</domain>

<decisions>
## Implementation Decisions

### Autocomplete Style (replacing ghost text)
- **D-01:** Standard dropdown list below the search input replaces inline ghost text. 5 results max from Last.fm `artist.search`. Dropdown dismisses on outside tap or back press (standard Material 3 behavior).
- **D-02:** Ghost text components (`GhostTextField`, ghost suffix rendering, "Tab to confirm" hint) are removed entirely — not just hidden.

### Progressive Disclosure
- **D-03:** Single search input visible initially ("From" artist). After user selects a suggestion from the dropdown, the "From" artist is confirmed as a chip/label, and a second input slides down with fade-in animation for "To" artist.
- **D-04:** Second input auto-focuses immediately after appearing.
- **D-05:** Both artists confirmed triggers bridge search automatically — no manual "Find Bridge" button tap needed (matches ROADMAP Success Criteria #4).

### State Transition Animations
- **D-06:** Idle → Searching: crossfade transition — inputs collapse/fade, progress indicator takes center stage.
- **D-07:** Searching → PathFound/PlaylistReady: bottom sheet slides up with spring animation (consistent with Phase 6 existing pattern).
- **D-08:** All state transitions use `AnimatedContent` or `AnimatedVisibility` with Compose animation APIs.

### Seed Suggestions Placement
- **D-09:** Spotify seed suggestion chips appear below the single search input, visible in Idle state. Same `SeedSuggestionsRow` component, just repositioned.
- **D-10:** Random Bridge FAB remains in current position (bottom-right).

### What Stays Unchanged
- `BridgeUiState` sealed class — same states, same fields
- `BridgeViewModel` — same public API (`onFromQueryChanged`, `confirmFrom`, `onToQueryChanged`, `confirmTo`, `findBridge`, etc.)
- `BridgeAlgorithm` (Phase 8) — untouched
- Path result bottom sheet and `PathSheet` composable — untouched
- `SeedSuggestionsRow` component — reused, just repositioned

### Claude's Discretion
- Dropdown implementation approach (Material 3 `ExposedDropdownMenu` vs custom `DropdownMenu` vs `LazyColumn` overlay)
- Exact animation durations and easing curves
- Confirmed artist chip styling (InputChip, AssistChip, or custom)
- How "From" label transitions from input to confirmed chip
- Whether to use `AnimatedContent` or `Crossfade` for state transitions
- Search input styling (Material 3 `SearchBar` vs `OutlinedTextField` vs `TextField`)

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Current Bridge UI (being redesigned)
- `app/src/main/kotlin/com/metrolist/music/ui/screens/bridge/BridgeScreen.kt` — Current implementation with `GhostTextField`, `SeedSuggestionsRow`, `PathSheet`, and all state rendering. The main file being overhauled.
- `app/src/main/kotlin/com/metrolist/music/viewmodels/BridgeViewModel.kt` — Public API that the new UI must call (same methods: `onFromQueryChanged`, `confirmFrom`, `onToQueryChanged`, `confirmTo`, `findBridge`).

### Phase 4 Context (original input design — being replaced)
- `.planning/phases/04-artist-search-bridge-trigger/04-CONTEXT.md` — Original ghost text decisions. Phase 9 replaces D-03, D-04, D-05 with dropdown + progressive disclosure.

### Phase 6 Context (path view — unchanged)
- `.planning/phases/06-linear-path-result-view/06-CONTEXT.md` — Bottom sheet, artist node content, now-playing indicator. All kept as-is.

### Phase 8 (algorithm — unchanged)
- `app/src/main/kotlin/com/metrolist/music/bridge/BridgeAlgorithm.kt` — Native Kotlin beam search. Not modified in this phase.

### Existing Compose Patterns
- `app/src/main/kotlin/com/metrolist/music/ui/screens/search/OnlineSearchScreen.kt` — Existing search screen with dropdown suggestions. Reference for autocomplete patterns in this codebase.
- `app/src/main/kotlin/com/metrolist/music/ui/component/` — Reusable UI components available.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `SeedSuggestionsRow` — Already built, just needs repositioning
- `PathSheet` composable — Stays unchanged, renders bottom sheet for path results
- `BridgeUiState` sealed class — All states already defined
- `AnimatedVisibility` — Already used in BridgeScreen for seed suggestions

### Established Patterns
- `BasicTextField` with custom `decorationBox` — current input pattern (being replaced)
- `BottomSheet`/`rememberBottomSheetState` — custom bottom sheet pattern used for path view
- `hiltViewModel<BridgeViewModel>()` — standard ViewModel access pattern
- `collectAsState()` — standard state observation pattern

### Integration Points
- `BridgeViewModel` public API — UI calls same methods, just in different order (from-confirm → show-to-input → to-confirm → auto-trigger)
- `LocalPlayerConnection` — still used for playback controls in path view
- `LocalPlayerAwareWindowInsets` — still used for inset handling

</code_context>

<specifics>
## Specific Ideas

- Progressive disclosure pattern: single input → confirmed chip + second input sliding in. This is the core UX change.
- EccoPath web UI no longer the primary reference — Phase 9 diverges to a native Android-optimized UX.
- Auto-trigger on both confirmations eliminates the "Find Bridge" button from the flow (button may remain as fallback but is not the primary path).

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 09-bridge-ui-redesign*
*Context gathered: 2026-04-05*
