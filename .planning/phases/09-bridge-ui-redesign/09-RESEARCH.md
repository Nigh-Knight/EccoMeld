# Phase 9: Bridge UI Redesign - Research

**Researched:** 2026-04-05
**Domain:** Jetpack Compose UI — progressive disclosure search, dropdown autocomplete, animated state transitions
**Confidence:** HIGH

## Summary

Phase 9 replaces the side-by-side `GhostTextField` pair in `BridgeScreen.kt` with a progressive disclosure pattern: a single search input with a dropdown suggestion list, followed by a confirmed-artist chip and an animated second input that slides in. The `BridgeViewModel` public API is unchanged — only the Compose UI layer is overhauled.

The redesign is self-contained to one file (`BridgeScreen.kt`) plus string resources (`metrolist_strings.xml`). The `GhostTextField` composable, `fromGhostSuffix`/`toGhostSuffix` StateFlows, and the `bridge_ghost_confirm_hint` string are the only items being deleted. Everything else — `BridgeViewModel`, `BridgeUiState`, `PathSheet`, `SeedSuggestionsRow`, `RandomBridgeFab`, animation patterns — is reused or repositioned.

The codebase already contains all required primitives: `AnimatedContent` is used in `RecognitionScreen.kt`, `FocusRequester.requestFocus()` is used across five playlist screens, `DropdownMenu`/`DropdownMenuItem` are used in `SpotifyPlaylistScreen.kt`, and `FilterChip`/`AssistChip` are used across library screens and `ChipsRow.kt`. No new dependencies are required.

**Primary recommendation:** Use `DropdownMenu` + `LazyColumn` overlay for autocomplete (custom, not `ExposedDropdownMenuBox`) because the input field needs to remain a plain `BasicTextField` with `surfaceVariant` background — not an `OutlinedTextField` or `TextField`. Use `AnimatedVisibility` with `expandVertically() + fadeIn()` for the second input reveal. Use `AssistChip` (already in `ChipsRow.kt`) for the confirmed-artist label.

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **D-01:** Standard dropdown list below the search input replaces inline ghost text. 5 results max from Last.fm `artist.search`. Dropdown dismisses on outside tap or back press (standard Material 3 behavior).
- **D-02:** Ghost text components (`GhostTextField`, ghost suffix rendering, "Tab to confirm" hint) are removed entirely — not just hidden.
- **D-03:** Single search input visible initially ("From" artist). After user selects a suggestion from the dropdown, the "From" artist is confirmed as a chip/label, and a second input slides down with fade-in animation for "To" artist.
- **D-04:** Second input auto-focuses immediately after appearing.
- **D-05:** Both artists confirmed triggers bridge search automatically — no manual "Find Bridge" button tap needed.
- **D-06:** Idle → Searching: crossfade transition — inputs collapse/fade, progress indicator takes center stage.
- **D-07:** Searching → PathFound/PlaylistReady: bottom sheet slides up with spring animation (consistent with Phase 6 existing pattern).
- **D-08:** All state transitions use `AnimatedContent` or `AnimatedVisibility` with Compose animation APIs.
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

### Deferred Ideas (OUT OF SCOPE)
None — discussion stayed within phase scope
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| BRDG-01 | User can enter a "From" artist and a "To" artist via search inputs with Last.fm autocomplete | Dropdown list replaces ghost text; same `onFromQueryChanged`/`onToQueryChanged` ViewModel API; `searchArtists(query, 5)` — increase limit from 1 to 5 |
| BRDG-04 | User sees meaningful loading feedback during bridge computation | Searching state still shows `LinearProgressIndicator` + message text; D-06 crossfade brings it into focus center-stage |
| BRDG-06 | User sees a clear error message when no path is found, with suggestion to try different artists | Error state display unchanged; new layout gives it more vertical space |
</phase_requirements>

---

## Standard Stack

### Core (no new dependencies — all already in project)
| Component | Source | Purpose | Status |
|-----------|--------|---------|--------|
| `AnimatedContent` | `androidx.compose.animation` | D-06 crossfade for Idle→Searching transition | Already imported in `RecognitionScreen.kt` |
| `AnimatedVisibility` | `androidx.compose.animation` | D-03 second input slide-in reveal; D-08 transitions | Already used in `BridgeScreen.kt` |
| `DropdownMenu` / `DropdownMenuItem` | `androidx.compose.material3` | Autocomplete dropdown overlay | Already used in `SpotifyPlaylistScreen.kt` |
| `FocusRequester` + `focusRequester()` | `androidx.compose.ui.focus` | D-04 second input auto-focus | Already used in 5 playlist screens |
| `AssistChip` | `androidx.compose.material3` | Confirmed artist chip label | Already imported in `ChipsRow.kt` |
| `FilterChip` | `androidx.compose.material3` | Alternative chip — already in LibraryAlbumsScreen | Available if AssistChip doesn't fit visually |
| `BasicTextField` | `androidx.compose.foundation.text` | Input field (keep existing styling pattern) | Already used in `BridgeScreen.kt`, `SearchBar.kt` |

### No New Dependencies Required
All Compose animation APIs, focus utilities, dropdown menus, and chip variants are already in the project's dependency tree via `Jetpack Compose 1.10.2` + `Material 3 1.5.0-alpha09`.

**Installation:** None required.

---

## Architecture Patterns

### Recommended Project Structure

No new files needed. Changes are confined to:

```
app/src/main/kotlin/com/metrolist/music/ui/screens/bridge/
└── BridgeScreen.kt          ← single file being overhauled

app/src/main/res/values/
└── metrolist_strings.xml    ← add new strings, remove bridge_ghost_confirm_hint

app/src/test/kotlin/com/metrolist/music/bridge/
└── BridgeViewModelTest.kt   ← new tests for autocomplete list state
```

### Pattern 1: DropdownMenu for Autocomplete Overlay

**What:** A `Box` wrapping the `BasicTextField` with `DropdownMenu` anchored to the bottom. The menu is shown when the input is focused and suggestions exist.

**Why custom over `ExposedDropdownMenuBox`:** `ExposedDropdownMenuBox` (used in `ContentSettings.kt`) is designed for read-only selection pickers with `OutlinedTextField`. The artist input needs to remain editable, styled with `surfaceVariant` background and `RoundedCornerShape(8.dp)` — the same as the current `GhostTextField`. Using a bare `DropdownMenu` with `expanded` controlled by a `var showDropdown by remember { mutableStateOf(false) }` avoids `ExposedDropdownMenuBox` boilerplate and matches the codebase's existing dropdown pattern in `SpotifyPlaylistScreen.kt`.

**What the ViewModel provides:** The ViewModel needs a new `fromSuggestions: StateFlow<List<String>>` and `toSuggestions: StateFlow<List<String>>` (replacing `fromGhostSuffix`/`toGhostSuffix`). The `onFromQueryChanged` function already debounces 300ms and calls `LastFM.searchArtists(query, 1)` — the limit needs to be raised to 5 and results stored in a list instead of a single ghost suffix.

**Example pattern** (based on `SpotifyPlaylistScreen.kt` line 615):
```kotlin
// Source: app/src/main/kotlin/com/metrolist/music/ui/screens/playlist/SpotifyPlaylistScreen.kt
Box {
    BasicTextField(
        value = fromQuery,
        onValueChange = { viewModel.onFromQueryChanged(it) },
        modifier = Modifier.onFocusChanged { showFromDropdown = it.isFocused && fromSuggestions.isNotEmpty() },
        // ... existing decoration box pattern
    )
    DropdownMenu(
        expanded = showFromDropdown && fromSuggestions.isNotEmpty(),
        onDismissRequest = { showFromDropdown = false },
        modifier = Modifier.fillMaxWidth(),
    ) {
        fromSuggestions.forEach { suggestion ->
            DropdownMenuItem(
                text = { Text(suggestion) },
                onClick = {
                    viewModel.onFromQueryChanged(suggestion)
                    viewModel.confirmFrom()
                    showFromDropdown = false
                }
            )
        }
    }
}
```

### Pattern 2: Progressive Disclosure — Second Input Reveal

**What:** When `fromConfirmedArtist` becomes non-empty, the "From" input collapses to a chip label, and the "To" input animates into view below it using `AnimatedVisibility`.

**State driver:** `fromConfirmed: String` (already `viewModel.fromConfirmedArtist.collectAsState()`). When non-empty: show chip. When empty: show input.

**Second input auto-focus (D-04):**
```kotlin
// Source: app/src/main/kotlin/com/metrolist/music/ui/screens/playlist/AutoPlaylistScreen.kt
val toFocusRequester = remember { FocusRequester() }

AnimatedVisibility(
    visible = fromConfirmed.isNotEmpty(),
    enter = expandVertically() + fadeIn(),
    exit = shrinkVertically() + fadeOut(),
) {
    Column {
        // "To" input with focusRequester modifier
        BasicTextField(
            modifier = Modifier.focusRequester(toFocusRequester),
            // ...
        )
    }
    LaunchedEffect(fromConfirmed) {
        if (fromConfirmed.isNotEmpty()) {
            toFocusRequester.requestFocus()
        }
    }
}
```

**Critical detail:** `LaunchedEffect(fromConfirmed)` runs after recomposition, which means the node exists in the tree before `requestFocus()` is called. This is the correct pattern — calling `requestFocus()` inside the `AnimatedVisibility` content before the node is fully composed throws `IllegalStateException`. The `LaunchedEffect` pattern used in `AutoPlaylistScreen.kt`, `HistoryScreen.kt`, and others is safe.

### Pattern 3: AnimatedContent for State Transition (D-06)

**What:** Wrap the input section + progress section in `AnimatedContent` keyed on whether `uiState is Searching`. On Idle→Searching crossfade, the inputs fade out and the progress indicator fades in with center-stage vertical space.

**Example** (based on `RecognitionScreen.kt` line 221):
```kotlin
// Source: app/src/main/kotlin/com/metrolist/music/ui/screens/recognition/RecognitionScreen.kt
AnimatedContent(
    targetState = uiState is BridgeUiState.Searching,
    transitionSpec = {
        fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
    },
    label = "bridge_input_vs_searching",
) { isSearching ->
    if (isSearching) {
        SearchingContent(state = uiState as BridgeUiState.Searching)
    } else {
        InputContent(/* ... */)
    }
}
```

### Pattern 4: AssistChip for Confirmed Artist Label

**What:** Replace the active "From" input with an `AssistChip` showing the confirmed artist name + an "x" close icon that clears confirmation and re-shows the input.

**Why AssistChip:** Already imported in `ChipsRow.kt` (line 34). Has standard height (~32dp) consistent with `SuggestionChip` already used in `SeedSuggestionsRow`. Supports `leadingIcon` and `trailingIcon` for the close button.

```kotlin
// Source: app/src/main/kotlin/com/metrolist/music/ui/component/ChipsRow.kt
AssistChip(
    onClick = { viewModel.clearFrom() },  // new ViewModel method
    label = { Text(fromConfirmed, maxLines = 1, overflow = TextOverflow.Ellipsis) },
    trailingIcon = {
        Icon(
            painter = painterResource(R.drawable.close),
            contentDescription = "Clear from artist",
            modifier = Modifier.size(AssistChipDefaults.IconSize),
        )
    }
)
```

**New ViewModel method needed:** `clearFrom()` — resets `_fromConfirmedArtist`, `_fromQuery`, `_fromSuggestions` to empty. Similarly `clearTo()`.

### Anti-Patterns to Avoid

- **Calling `requestFocus()` directly in composition:** Will throw `IllegalStateException` if the node hasn't been attached yet. Always use `LaunchedEffect` to defer the call.
- **Using `ExposedDropdownMenuBox` for editable autocomplete:** Designed for read-only selection pickers. The `menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)` variant exists in Material3 1.3+, but the project already uses plain `DropdownMenu` for editable contexts (`SpotifyPlaylistScreen.kt`) — maintain consistency.
- **Storing `showDropdown` state in ViewModel:** Dropdown visibility is ephemeral UI state, not business state. Keep it in `remember { mutableStateOf(false) }` in the composable.
- **Animating the `LazyColumn` height inside `DropdownMenu`:** `DropdownMenu` handles its own surface and shadow. Do not wrap its content in `AnimatedVisibility` — the menu handles enter/exit itself via `expanded`.

---

## ViewModel Changes Required

This section is critical — the planner must know exactly what ViewModel state changes are needed to support the dropdown UX.

### Current ghost state being removed
```
fromGhostSuffix: StateFlow<String>     ← REMOVE
toGhostSuffix: StateFlow<String>       ← REMOVE
_fromGhostFull: String (private)       ← REMOVE
_toGhostFull: String (private)         ← REMOVE
```

### New suggestion list state to add
```
fromSuggestions: StateFlow<List<String>>    ← ADD (replaces ghost suffix)
toSuggestions: StateFlow<List<String>>      ← ADD
```

### Methods to update
- `onFromQueryChanged()`: change `LastFM.searchArtists(query, 1)` to `searchArtists(query, 5)`, store results as `List<String>` instead of single ghost suffix
- `onToQueryChanged()`: same change
- `confirmFrom()`: simplified — no ghost full name to resolve, just uses current `_fromQuery.value`
- `confirmTo()`: same simplification

### New methods to add
- `clearFrom()`: resets `_fromQuery`, `_fromConfirmedArtist`, `_fromSuggestions` to empty
- `clearTo()`: resets `_toQuery`, `_toConfirmedArtist`, `_toSuggestions` to empty

### Auto-trigger bridge (D-05)
After `confirmTo()` is called (either from dropdown selection or seed chip click), check if both `_fromConfirmedArtist.value.isNotBlank()` and `_toConfirmedArtist.value.isNotBlank()` — if so, call `findBridge()` automatically. This replaces the explicit "Find Bridge" button press.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Dropdown dismiss on outside tap | Custom `Modifier.pointerInput` gesture detector | `DropdownMenu(onDismissRequest = { ... })` | Material 3 `DropdownMenu` handles outside-tap and back-press dismissal natively |
| Animated height for second input reveal | Manual `height()` animation | `AnimatedVisibility(enter = expandVertically())` | `expandVertically` correctly handles intrinsic height measurement, which is unknown for a `Column` containing a `BasicTextField` |
| Chip close button icon size | Custom dp value | `AssistChipDefaults.IconSize` | Matches Material 3 touch target spec automatically |
| Crossfade transition | Manual `alpha` animation + coroutine | `AnimatedContent` with `fadeIn() togetherWith fadeOut()` | Handles simultaneous enter/exit of two content states without z-order issues |
| Focus after animation | `Thread.sleep()` or `delay()` before `requestFocus()` | `LaunchedEffect(key)` | Coroutine launches after composition; node is attached when effect runs |

**Key insight:** Compose animation primitives (`AnimatedVisibility`, `AnimatedContent`) handle layout measurement, z-order, and timing automatically. Manual alpha/height animation via `Animatable` or coroutines is significantly more code and misses edge cases (back-press mid-animation, configuration change during animation).

---

## Common Pitfalls

### Pitfall 1: DropdownMenu Width Mismatch

**What goes wrong:** `DropdownMenu` by default sizes to its content width, not the anchor width. The dropdown appears narrower than the search input.

**Why it happens:** `DropdownMenu` is a popup with its own layout scope. It doesn't inherit the parent `Box` width automatically.

**How to avoid:** Add `modifier = Modifier.fillMaxWidth()` to `DropdownMenu` — but this fills the screen width. The correct approach is to constrain to the input width via `onGloballyPositioned`:

```kotlin
var dropdownWidth by remember { mutableStateOf(0) }
Box(
    modifier = Modifier.onGloballyPositioned { dropdownWidth = it.size.width }
) {
    BasicTextField(...)
    DropdownMenu(..., modifier = Modifier.width(with(LocalDensity.current) { dropdownWidth.toDp() }))
}
```

**Simpler alternative (used in SpotifyPlaylistScreen pattern):** Wrap the `DropdownMenu` in the same `Column` as the input, letting it naturally expand to the parent column width. Test on device — the popup may still mismatch.

**Warning signs:** Dropdown items are too narrow or clipped on long artist names.

### Pitfall 2: `requestFocus()` on Non-Attached Node

**What goes wrong:** `IllegalStateException: FocusRequester is not initialized` when calling `toFocusRequester.requestFocus()` too early.

**Why it happens:** The second input's `AnimatedVisibility` content begins composing when `visible = true`, but the focus node is not attached to the focus tree until after the first composition pass completes.

**How to avoid:** Always use `LaunchedEffect(key) { focusRequester.requestFocus() }` where `key` is the value that triggers visibility (e.g., `fromConfirmed`). The effect runs after the composition frame where the node was attached.

**Warning signs:** Crash on first artist selection, or keyboard not appearing after second input animates in.

### Pitfall 3: Auto-Trigger Loop with Seed Chips

**What goes wrong:** Clicking a seed chip fills both artists and auto-triggers `findBridge()` twice — once when `confirmFrom()` sets the from artist, again when `confirmTo()` sets the to artist.

**Why it happens:** The seed chip `onChipClick` logic in `BridgeScreen.kt` calls `confirmFrom()` + `confirmTo()` sequentially for the case where both are empty. If auto-trigger fires after `confirmFrom()`, the `_toConfirmedArtist` is still blank, so the first trigger is a no-op. But if the chip click sequence fills both in rapid succession, `findBridge()` guard (`from.isBlank() || to.isBlank()`) prevents double-trigger only if the second call completes before state propagates. This is safe in practice on the main thread.

**How to avoid:** The auto-trigger in `confirmTo()` must check `_fromConfirmedArtist.value.isNotBlank()` at call time. The auto-trigger in `confirmFrom()` must NOT trigger — only `confirmTo()` triggers. This is the natural model: "To" artist is always confirmed last in the progressive disclosure flow.

**Warning signs:** Bridge search fires before both artists are shown as chips.

### Pitfall 4: `DropdownMenu` Blocks Input Focus Change

**What goes wrong:** Tapping an autocomplete suggestion causes the `BasicTextField` to lose focus (triggering `onFocusChanged { if (!isFocused) confirmFrom() }`), which confirms the current raw query instead of the selected suggestion.

**Why it happens:** The original `GhostTextField` had `onFocusChanged = { if (!focused) viewModel.confirmFrom() }` — this pattern must be removed entirely. The dropdown UX confirms via `onClick`, not focus loss.

**How to avoid:** Remove all `onFocusChanged` confirm logic. Confirmation happens only when user taps a dropdown item or a seed chip. Do NOT add `confirmFrom()` to the focus-lost handler.

**Warning signs:** The "From" chip shows the partially-typed text instead of the selected suggestion name.

### Pitfall 5: "Find Bridge" Button Still Rendered

**What goes wrong:** The `Button` for "Find Bridge" (lines 480–501 of current `BridgeScreen.kt`) is left in place. With D-05 (auto-trigger), it becomes redundant and occupies screen space.

**How to avoid:** Remove the `Button` from the UI. A text fallback link could remain for users who confirm via typing (not dropdown tap) — but the button is not needed as the primary flow. The `findBridge()` method still exists in the ViewModel as a named action; auto-trigger just calls it programmatically.

**Warning signs:** Two ways to trigger bridge search create inconsistent UX.

### Pitfall 6: Ghost Suffix StateFlows Still Read by Screen

**What goes wrong:** `fromGhostSuffix`/`toGhostSuffix` are removed from ViewModel but the old screen still collects them. This is a compile error, so it will be caught immediately — but the planner must list removal of all four `collectAsState()` calls as explicit tasks.

**Lines to remove from BridgeScreen.kt:**
- Line 298: `val fromGhostSuffix by viewModel.fromGhostSuffix.collectAsState()`
- Line 299: `val toGhostSuffix by viewModel.toGhostSuffix.collectAsState()`
- Line 314: `var fromHasFocus by remember { mutableStateOf(false) }`
- Line 315: `var toHasFocus by remember { mutableStateOf(false) }`
- The entire `GhostTextField` composable (lines 92–203)
- `ghostSuffix` parameter usage in `GhostTextField` calls

---

## Code Examples

### Autocomplete Input with Dropdown

```kotlin
// Recommended pattern for ArtistSearchInput composable
// Source pattern: SpotifyPlaylistScreen.kt DropdownMenu usage (line 615)
@Composable
private fun ArtistSearchInput(
    query: String,
    suggestions: List<String>,
    onQueryChange: (String) -> Unit,
    onSuggestionSelected: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val textStyle = MaterialTheme.typography.bodyLarge.copy(
        color = MaterialTheme.colorScheme.onSurface
    )
    var showDropdown by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        BasicTextField(
            value = query,
            onValueChange = {
                onQueryChange(it)
                showDropdown = it.isNotBlank()
            },
            enabled = enabled,
            singleLine = true,
            textStyle = textStyle,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, autoCorrect = false),
            decorationBox = { /* existing Box+surfaceVariant pattern */ },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { showDropdown = it.isFocused && query.isNotBlank() && suggestions.isNotEmpty() },
        )
        DropdownMenu(
            expanded = showDropdown && suggestions.isNotEmpty(),
            onDismissRequest = { showDropdown = false },
            modifier = Modifier.fillMaxWidth(),
        ) {
            suggestions.forEach { suggestion ->
                DropdownMenuItem(
                    text = { Text(suggestion, style = MaterialTheme.typography.bodyLarge) },
                    onClick = {
                        onSuggestionSelected(suggestion)
                        showDropdown = false
                    },
                )
            }
        }
    }
}
```

### Progressive Disclosure Layout

```kotlin
// From-confirmed chip → "To" input appears
// Source pattern: AutoPlaylistScreen.kt FocusRequester usage (line 144)
val toFocusRequester = remember { FocusRequester() }
val fromConfirmed by viewModel.fromConfirmedArtist.collectAsState()
val toConfirmed by viewModel.toConfirmedArtist.collectAsState()

Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
    // "From" input or confirmed chip
    if (fromConfirmed.isBlank()) {
        ArtistSearchInput(/* From input */)
    } else {
        AssistChip(
            onClick = { viewModel.clearFrom() },
            label = { Text(fromConfirmed) },
            trailingIcon = {
                Icon(painterResource(R.drawable.close), contentDescription = null,
                    modifier = Modifier.size(AssistChipDefaults.IconSize))
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }

    // "To" input — animated reveal
    AnimatedVisibility(
        visible = fromConfirmed.isNotEmpty(),
        enter = expandVertically(animationSpec = spring()) + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        Spacer(Modifier.height(8.dp))
        ArtistSearchInput(
            modifier = Modifier.focusRequester(toFocusRequester),
            /* To input params */
        )
    }
}

// Auto-focus second input after it appears
LaunchedEffect(fromConfirmed) {
    if (fromConfirmed.isNotEmpty() && toConfirmed.isBlank()) {
        try { toFocusRequester.requestFocus() } catch (_: Exception) { /* not yet attached */ }
    }
}
```

### AnimatedContent for Idle/Searching Crossfade

```kotlin
// Source pattern: RecognitionScreen.kt AnimatedContent usage (line 221)
AnimatedContent(
    targetState = uiState is BridgeUiState.Searching || uiState is BridgeUiState.Error,
    transitionSpec = {
        fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
    },
    label = "bridge_input_state",
) { isActiveSearch ->
    if (isActiveSearch) {
        // Progress / error display — takes full vertical space
        SearchingOrErrorContent(uiState = uiState)
    } else {
        // Progressive disclosure inputs + seed chips
        InputDisclosureContent(
            fromConfirmed = fromConfirmed,
            toConfirmed = toConfirmed,
            /* ... */
        )
    }
}
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Ghost text inline autocomplete | Dropdown list autocomplete | Phase 9 | More results visible, less input confusion |
| Side-by-side From/To inputs | Progressive disclosure single-then-second | Phase 9 | Reduces cognitive load, matches Android search patterns |
| Manual "Find Bridge" button | Auto-trigger on both-confirmed | Phase 9 | Removes an unnecessary tap in the primary flow |

**Deprecated/outdated (items to remove):**
- `GhostTextField` composable: replaced by `ArtistSearchInput` with `DropdownMenu`
- `fromGhostSuffix` / `toGhostSuffix` StateFlows: replaced by `fromSuggestions` / `toSuggestions`
- `_fromGhostFull` / `_toGhostFull` private vars: no longer needed
- `bridge_ghost_confirm_hint` string resource: remove
- `onFocusChanged { if (!focused) viewModel.confirmFrom() }` pattern: remove entirely
- `fromHasFocus` / `toHasFocus` UI state vars: remove (no longer needed for chip fill priority)

---

## Open Questions

1. **"Find Bridge" button retention**
   - What we know: D-05 makes auto-trigger the primary path. The button remains as fallback for edge cases (user types directly without selecting from dropdown).
   - What's unclear: Should the button be removed entirely or kept as a secondary fallback (e.g., as a `TextButton` instead of filled `Button`)?
   - Recommendation: Keep as a `TextButton` (less prominent) so keyboard-only users can explicitly trigger. Remove the filled `Button`. Place it below the inputs, visible only when both queries are non-blank.

2. **Chip fill priority logic after progressive disclosure**
   - What we know: The existing `onSeedChipClick` logic in `BridgeScreen.kt` uses `fromHasFocus`/`toHasFocus` tracking to prioritize which field gets filled. Both focus vars are being removed.
   - What's unclear: With progressive disclosure, a seed chip tap when "From" is already confirmed should fill "To". The logic simplifies: if `fromConfirmed.isBlank()`, fill From; else fill To. The focus-tracking fallback case is no longer needed.
   - Recommendation: Simplify `onSeedChipClick` to two cases: from-empty → fill from + confirm; else → fill to + confirm (auto-triggers bridge if to was the last to confirm).

3. **DropdownMenu width alignment on screen**
   - What we know: `DropdownMenu` does not automatically match the width of its anchor `Box`. In `SpotifyPlaylistScreen.kt` the dropdown is used inside a fixed-width container.
   - What's unclear: Whether `Modifier.fillMaxWidth()` on `DropdownMenu` correctly inherits the input column width or fills the screen.
   - Recommendation: Use `onGloballyPositioned` to capture input width and pass to `DropdownMenu` width. Test on device. LOW confidence on which is cleaner.

---

## Environment Availability

Step 2.6: SKIPPED — Phase 9 is a pure Compose UI change with no external tool, CLI, or service dependencies beyond what is already in the project.

---

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 4 (JUnit 4.13.2) |
| Config file | `app/build.gradle.kts` (testOptions, no separate config file) |
| Quick run command | `./gradlew :app:testUniversalFossDebugUnitTest --tests "com.metrolist.music.bridge.BridgeViewModelTest" -x lint` |
| Full suite command | `./gradlew :app:testUniversalFossDebugUnitTest -x lint` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| BRDG-01 | `onFromQueryChanged` stores suggestions in `fromSuggestions` list (not ghost suffix) | unit | `./gradlew :app:testUniversalFossDebugUnitTest --tests "com.metrolist.music.bridge.BridgeViewModelTest.fromSuggestions_*" -x lint` | ❌ Wave 0 |
| BRDG-01 | `confirmFrom()` simplified — uses raw query directly when no ghost | unit | already covered by `confirmFrom_stores_confirmed_artist` | ✅ |
| BRDG-01 | `clearFrom()` resets from query, confirmed, suggestions | unit | `./gradlew :app:testUniversalFossDebugUnitTest --tests "com.metrolist.music.bridge.BridgeViewModelTest.clearFrom_*" -x lint` | ❌ Wave 0 |
| BRDG-04 | Searching state renders progress indicator — visual test | manual | manual UI test | — |
| BRDG-06 | Error state message displayed after no-path result | unit | already covered by `error_state_does_not_set_isRunning` | ✅ |
| D-05 | Auto-trigger: `confirmTo()` calls `findBridge()` when both confirmed | unit | `./gradlew :app:testUniversalFossDebugUnitTest --tests "com.metrolist.music.bridge.BridgeViewModelTest.confirmTo_autoTriggers_when_both_confirmed" -x lint` | ❌ Wave 0 |
| D-05 | Auto-trigger: `confirmFrom()` does NOT trigger bridge alone | unit | `./gradlew :app:testUniversalFossDebugUnitTest --tests "com.metrolist.music.bridge.BridgeViewModelTest.confirmFrom_does_not_trigger_bridge_alone" -x lint` | ❌ Wave 0 |

### Sampling Rate
- **Per task commit:** `./gradlew :app:testUniversalFossDebugUnitTest --tests "com.metrolist.music.bridge.BridgeViewModelTest" -x lint`
- **Per wave merge:** `./gradlew :app:testUniversalFossDebugUnitTest -x lint`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps

- [ ] `BridgeViewModelTest.kt` — add `fromSuggestions_populated_on_query_change` test
- [ ] `BridgeViewModelTest.kt` — add `fromSuggestions_cleared_on_blank_query` test
- [ ] `BridgeViewModelTest.kt` — add `clearFrom_resets_all_from_state` test
- [ ] `BridgeViewModelTest.kt` — add `clearTo_resets_all_to_state` test
- [ ] `BridgeViewModelTest.kt` — add `confirmTo_autoTriggers_when_both_confirmed` test
- [ ] `BridgeViewModelTest.kt` — add `confirmFrom_does_not_trigger_bridge_alone` test

*(All tests extend existing `BridgeViewModelTest.kt` — no new file needed)*

---

## Project Constraints (from CLAUDE.md)

The following directives apply to this phase:

| Constraint | Impact on Phase 9 |
|------------|------------------|
| **No material-icons-extended dependency** | Use `painterResource(R.drawable.close)` for chip close button (not `Icons.Default.Close`). Use `painterResource(R.drawable.search)` for input leading icon. Pattern established in Phase 4 (R.drawable.error), Phase 6 (R.drawable.volume_up). |
| **Kotlin Compose Compiler — PascalCase composable functions** | New composable `ArtistSearchInput` (PascalCase). New composable `ConfirmedArtistChip` if extracted. |
| **`BasicTextField` with custom `decorationBox`** — existing input pattern | Keep `BasicTextField` + `surfaceVariant` background + `RoundedCornerShape(8.dp)` for the new `ArtistSearchInput`. Do not switch to `OutlinedTextField` or `TextField`. |
| **`Dispatchers.IO` for network** | `onFromQueryChanged()` / `onToQueryChanged()` network calls on `Dispatchers.IO` (already the case, unchanged). |
| **`Timber.d()` for logging** | Add `Timber.tag("BridgeUI").d(...)` for suggestion fetch results if debug logging is added. |
| **GPL-3.0 file header** | `BridgeScreen.kt` file header stays unchanged. |
| **No ktlint / detekt** | No linting constraints. Android Lint only via CI. |
| **GSD workflow enforcement** | Work via `/gsd:execute-phase` — no direct edits outside workflow. |

---

## Sources

### Primary (HIGH confidence)
- Direct code inspection — `BridgeScreen.kt`, `BridgeViewModel.kt` (current implementation, full read)
- Direct code inspection — `SearchBar.kt`, `OnlineSearchScreen.kt` (search patterns in codebase)
- Direct code inspection — `ContentSettings.kt` lines 173–204 (ExposedDropdownMenuBox usage)
- Direct code inspection — `SpotifyPlaylistScreen.kt` lines 615–626 (DropdownMenu usage)
- Direct code inspection — `AutoPlaylistScreen.kt`, `HistoryScreen.kt` (FocusRequester.requestFocus() pattern)
- Direct code inspection — `RecognitionScreen.kt` lines 221–227 (AnimatedContent + fadeIn/fadeOut pattern)
- Direct code inspection — `ChipsRow.kt` lines 34–35 (AssistChip import), LibraryAlbumsScreen.kt (FilterChip)
- Direct code inspection — `BridgeViewModelTest.kt` (existing test patterns, mocking setup)
- Direct code inspection — `metrolist_strings.xml` lines 958–985 (existing bridge string resources)
- `CONTEXT.md` (phase 9) — locked decisions D-01 through D-10

### Secondary (MEDIUM confidence)
- Compose `AnimatedVisibility` `expandVertically()` + `AnimatedContent` crossfade — well-established API, confirmed used in codebase
- `FocusRequester.requestFocus()` inside `LaunchedEffect` safety pattern — confirmed by 5 instances in codebase

### Tertiary (LOW confidence)
- `DropdownMenu` width matching anchor via `onGloballyPositioned` — common pattern, not verified against current Material3 1.5.0-alpha09 specifically. Recommend testing on device.

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all libraries already in project, confirmed by direct import search
- Architecture patterns: HIGH — each pattern confirmed by existing codebase usage
- ViewModel changes: HIGH — exact fields and methods identified from full source read
- Pitfalls: HIGH — most derived from actual code patterns being modified (ghost suffix focus handlers, dropdown dismiss)
- Animation specifics (durations/easing): MEDIUM — spring() and tween(300) are reasonable defaults; exact values are Claude's discretion per CONTEXT.md

**Research date:** 2026-04-05
**Valid until:** 2026-05-05 (stable Compose APIs; Material3 alpha may ship breaking changes)
