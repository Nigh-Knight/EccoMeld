# Phase 4: Artist Search + Bridge Trigger - Context

**Gathered:** 2026-04-04
**Status:** Ready for planning

<domain>
## Phase Boundary

Build the interactive Bridge UI on BridgeScreen: two artist search inputs with Last.fm ghost-text autocomplete, a "Find Bridge" button that triggers `BridgeViewModel.startBridge()`, progress feedback during computation (progress bar + text), and clear error display when no path is found. This phase transforms the Phase 2 placeholder stubs into a fully functional user-facing search and trigger experience.

</domain>

<decisions>
## Implementation Decisions

### Autocomplete Data Source
- **D-01:** Add `artist.search` method to the existing Kotlin `lastfm` module (`lastfm/src/main/kotlin/com/metrolist/lastfm/LastFM.kt`). Native Ktor HTTP call — no WebView round-trip. The module already has the Ktor client configured with Last.fm base URL. Returns artist name suggestions for partial queries.
- **D-02:** Autocomplete calls are debounced (300ms like EccoPath's web UI) to avoid hammering Last.fm API.

### Search Input Layout
- **D-03:** Two text fields side by side ("From" and "To") like EccoPath's mobile web layout. "Find Bridge" button below both inputs.
- **D-04:** Reference the EccoPath mobile web UI for layout proportions and spacing (`eccopath/components/search/SeedSearch.tsx` and `BridgeSearch.tsx`).

### Autocomplete Style
- **D-05:** Ghost text inline autocomplete — single suggestion auto-fills as the user types, matching EccoPath's web UI pattern. User presses Enter/confirms to accept the suggestion. Not a dropdown list.

### Progress Display
- **D-06:** Progress bar (linear/horizontal) showing `BridgeProgressInfo.progress` (0.0-1.0) with descriptive text below it. Text shows the phase message from `BridgeProgressInfo.message` (e.g., "Searching from Radiohead...", "Building bridge path..."). Matches EccoPath's web UI progress pattern.
- **D-07:** `BridgeUiState.Searching` already has `foundHops`/`totalHops` fields — these drive the progress bar fill and hop count text.

### Error Display
- **D-08:** When `BridgeUiState.Error`, show an inline error message on the Bridge screen with the error text and a suggestion to try different artists. No dialog/popup — just text in the main content area.

### Bridge Trigger Behavior
- **D-09:** "Find Bridge" button is disabled (greyed out) while `isRunning` is true. The entire input area remains visible but non-interactive during computation. Matches BRDG-06 requirement.
- **D-10:** After a successful bridge, the UI transitions to `PathFound` state. After an error, the inputs remain editable so the user can immediately try different artists.

### Claude's Discretion
- Exact ghost text implementation approach (custom `BasicTextField` with visual layer vs. `TextField` with suffix)
- Debounce implementation details (coroutine-based vs. delay)
- Progress bar styling (Material 3 `LinearProgressIndicator` or custom)
- Exact error message wording for no-path-found case

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### EccoPath Web UI Reference
- `eccopath/components/search/SeedSearch.tsx` — Ghost text autocomplete implementation for seed artist input. Reference for debounce timing, ghost suffix rendering, confirmation behavior.
- `eccopath/components/search/BridgeSearch.tsx` — Bridge target search with same autocomplete UX. Reference for the side-by-side layout and bridge trigger flow.
- `eccopath/lib/lastfm.ts` — `searchArtists()` function with `artist.search` Last.fm API call. Reference for the API parameters and response shape.

### Existing Bridge Infrastructure
- `app/src/main/kotlin/com/metrolist/music/ui/screens/bridge/BridgeScreen.kt` — Current placeholder with `BridgeUiState` sealed class. This file gets the major UI overhaul.
- `app/src/main/kotlin/com/metrolist/music/viewmodels/BridgeViewModel.kt` — `startBridge()` already wired. Phase 4 adds autocomplete state and methods.
- `app/src/main/kotlin/com/metrolist/music/bridge/MeldBridgeInterface.kt` — `onProgress` callback already dispatches `BridgeProgressInfo` JSON to main thread.

### Last.fm Module
- `lastfm/src/main/kotlin/com/metrolist/lastfm/LastFM.kt` — Existing Last.fm API client (scrobbling only). Phase 4 adds `artist.search` method here.

### Existing Search Patterns
- `app/src/main/kotlin/com/metrolist/music/ui/screens/search/OnlineSearchScreen.kt` — Existing search UI with text field patterns. Reference for Compose text input conventions in this codebase.

### Requirements
- `.planning/REQUIREMENTS.md` — BRDG-01 (artist inputs + autocomplete), BRDG-04 (progress feedback), BRDG-06 (error message)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `LastFM` object singleton — already has Ktor client, JSON parser, and Last.fm base URL. Adding `artist.search` is a small addition.
- `BridgeUiState.Searching(foundHops, totalHops)` — already has fields for progress display.
- `BridgeViewModel.startBridge()` — already handles the full invocation chain, just needs to be called from the new UI.
- `OnlineSearchScreen.kt` — shows how Meld does text input + search in Compose.

### Established Patterns
- `MutableStateFlow` for ViewModel state, `collectAsState()` in Composable
- `rememberCoroutineScope()` for UI-triggered operations
- `Dispatchers.IO` for network calls
- Material 3 components throughout

### Integration Points
- `BridgeScreen.kt` — complete rewrite of the composable body (currently placeholder text)
- `BridgeViewModel.kt` — add autocomplete state + methods
- `lastfm/LastFM.kt` — add `artist.search` suspend function
- String resources in `metrolist_strings.xml` for new UI text

</code_context>

<specifics>
## Specific Ideas

- Ghost text autocomplete should match EccoPath's exact behavior: type partial name, single suggestion appears as faded text completing the input, press Enter to confirm
- Progress bar should be a Material 3 `LinearProgressIndicator` with determinate mode when `progress` > 0, indeterminate during `analyzing` phase
- Side-by-side layout should gracefully handle narrow screens — consider `Row` with equal `weight(1f)` and small padding between

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 04-artist-search-bridge-trigger*
*Context gathered: 2026-04-04*
