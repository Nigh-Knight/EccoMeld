# Phase 4: Artist Search + Bridge Trigger - Research

**Researched:** 2026-04-04
**Domain:** Android Jetpack Compose UI, Last.fm artist.search API, coroutine debounce, ghost-text autocomplete
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **D-01:** Add `artist.search` method to the existing Kotlin `lastfm` module (`lastfm/src/main/kotlin/com/metrolist/lastfm/LastFM.kt`). Native Ktor HTTP call — no WebView round-trip.
- **D-02:** Autocomplete calls are debounced 300ms (matching EccoPath web UI).
- **D-03:** Two text fields side by side ("From" and "To") like EccoPath's mobile web layout. "Find Bridge" button below both inputs.
- **D-04:** Reference EccoPath mobile web UI for layout proportions and spacing (`eccopath/components/search/SeedSearch.tsx` and `BridgeSearch.tsx`).
- **D-05:** Ghost text inline autocomplete — single suggestion auto-fills as the user types, matching EccoPath's web UI pattern. User presses Enter/confirms to accept. Not a dropdown list.
- **D-06:** Progress bar (linear/horizontal) showing `BridgeProgressInfo.progress` (0.0-1.0) with descriptive text below it. Text shows the phase message from `BridgeProgressInfo.message`.
- **D-07:** `BridgeUiState.Searching` already has `foundHops`/`totalHops` fields — these drive the progress bar fill and hop count text.
- **D-08:** When `BridgeUiState.Error`, show an inline error message on the Bridge screen with the error text and a suggestion to try different artists. No dialog/popup — just text in the main content area.
- **D-09:** "Find Bridge" button is disabled (greyed out) while `isRunning` is true. The entire input area remains visible but non-interactive during computation.
- **D-10:** After a successful bridge, the UI transitions to `PathFound` state. After an error, the inputs remain editable so the user can immediately try different artists.

### Claude's Discretion
- Exact ghost text implementation approach (custom `BasicTextField` with visual layer vs. `TextField` with suffix)
- Debounce implementation details (coroutine-based vs. delay)
- Progress bar styling (Material 3 `LinearProgressIndicator` or custom)
- Exact error message wording for no-path-found case

### Deferred Ideas (OUT OF SCOPE)
None — discussion stayed within phase scope.
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| BRDG-01 | User can enter a "From" artist and a "To" artist via search inputs with Last.fm autocomplete | Last.fm `artist.search` API, ghost-text implementation in Compose, ViewModel autocomplete state |
| BRDG-04 | User sees meaningful loading feedback during bridge computation ("Found 3 of 6 hops...") | `BridgeUiState.Searching(foundHops, totalHops)` already dispatched by `MeldBridgeInterface.onProgress()`, `LinearProgressIndicator` usage |
| BRDG-06 | User sees a clear error message when no path is found, with suggestion to try different artists | `BridgeUiState.Error` already dispatched by `MeldBridgeInterface.createPlaylist()` for `found=false`, inline error display pattern |
</phase_requirements>

---

## Summary

Phase 4 builds the interactive face of the Bridge tab: two artist search inputs with ghost-text autocomplete, a trigger button, real-time progress display, and inline error messaging. The infrastructure from Phases 2 and 3 is already complete — `BridgeUiState` sealed class, `BridgeViewModel.startBridge()`, `MeldBridgeInterface.onProgress()` dispatching `Searching(foundHops, totalHops)`, and `MeldBridgeInterface.createPlaylist()` dispatching `PathFound` or `Error`. Phase 4 replaces the placeholder `BridgeScreen.kt` body with real UI and extends `BridgeViewModel` with autocomplete state and methods.

The Last.fm `artist.search` API is a simple GET request returning an artist name list. The existing `LastFM` Kotlin object has the Ktor client configured with the Last.fm base URL and `kotlinx.serialization` JSON parser. Adding `searchArtists()` is a small addition: one new response model (`ArtistSearchResponse`) and one new suspend function using an unauthenticated GET (no API signature needed — `artist.search` is a read-only endpoint). The Last.fm API key used in EccoPath source (`c02db6443f45b41cd57d8166c9f042c9`) appears to be a shared public key; the app already loads its own key from `BuildConfig.LASTFM_API_KEY` which is the correct one to use.

Ghost-text autocomplete in Compose requires a visual overlay over the text input: a transparent layer that renders user-typed text (invisible) followed by the ghost suffix at reduced opacity. The EccoPath web implementation uses a positioned div behind the `<input>`. The Compose equivalent uses `Box` with a `BasicTextField` on top and a `Text` overlay underneath (or within the same `Box` using `drawBehind`). The debounce is best implemented as a coroutine `Job` in the ViewModel — cancel the previous job and `delay(300)` before the API call, mirroring the web `clearTimeout`/`setTimeout` pattern.

**Primary recommendation:** Implement ghost-text via `Box { BasicTextField + Text overlay }`, debounce via `viewModelScope.launch { delay(300); … }` with a cancellable `Job` stored in the ViewModel, and progress via `LinearProgressIndicator` in determinate mode when `foundHops/totalHops > 0`.

---

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Jetpack Compose + Material 3 | 1.10.2 / 1.5.0-alpha09 | All UI components | Already the project UI framework |
| `BasicTextField` (compose.foundation) | 1.10.2 | Ghost-text input (raw text field, no Material decoration) | Allows full visual control without Material theming interference |
| `LinearProgressIndicator` (Material 3) | 1.5.0-alpha09 | Determinate/indeterminate progress bar | Standard M3 component, already used in project |
| Ktor (OkHttp engine) | 3.4.0 | HTTP for `artist.search` | Already configured in `lastfm` module |
| kotlinx.serialization | bundled with Kotlin 2.3.10 | Response deserialization | Already used in `lastfm` module |
| kotlinx.coroutines | bundled | Debounce via `delay()` + `Job` | Already the project coroutine runtime |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `mockito-kotlin` | ~5.x | Unit tests for ViewModel autocomplete state | Already referenced in `BridgeViewModelTest.kt` but not yet declared in `libs.versions.toml` — Wave 0 gap |
| `kotlinx-coroutines-test` | 1.10.2 | `TestCoroutineDispatcher` / `runTest` for debounce testing | Needed for new autocomplete ViewModel tests |

**Installation (new test dependencies only):**
```bash
# No runtime dependencies needed — all build-time already present
# libs.versions.toml additions:
# mockitoKotlin = "5.4.0"
# coroutinesTest = "1.10.2"
# app/build.gradle.kts additions:
# testImplementation(libs.mockito.kotlin)
# testImplementation(libs.coroutines.test)
```

---

## Architecture Patterns

### Recommended Project Structure

New/modified files for Phase 4:

```
lastfm/src/main/kotlin/com/metrolist/lastfm/
├── LastFM.kt                            # ADD: searchArtists() suspend function
└── models/
    └── ArtistSearchResponse.kt          # NEW: @Serializable data classes for artist.search response

app/src/main/kotlin/com/metrolist/music/
├── viewmodels/
│   └── BridgeViewModel.kt               # ADD: autocomplete state + searchArtist() + debounce Job
└── ui/screens/bridge/
    └── BridgeScreen.kt                  # REWRITE: full UI — inputs, button, progress, error

app/src/main/res/values/strings.xml      # ADD: new string resources for Phase 4 UI text
app/src/test/kotlin/com/metrolist/music/bridge/
└── BridgeViewModelTest.kt               # ADD: autocomplete + debounce tests
```

### Pattern 1: Last.fm `artist.search` — unauthenticated GET

The existing `LastFM` object uses POST with form-signed body for authenticated operations (scrobble, love, etc.). The `artist.search` endpoint is read-only and does NOT require authentication or API signature. Use a direct GET with query params appended to the URL.

**Key finding:** The existing `lastfmParams()` private method always adds an `api_sig` (HMAC signature). For `artist.search` this is unnecessary but harmless — however, the `SECRET` var is empty until `initialize()` is called, and the current code crashes with a blank secret on signature computation. **Safer approach:** add a separate `searchArtists()` method that uses the Ktor `get {}` directly with query parameters, bypassing `lastfmParams()`. This matches how the EccoPath TypeScript client calls `artist.search` — no signature, just `api_key` + `format=json`.

```kotlin
// Source: lastfm/src/main/kotlin/com/metrolist/lastfm/LastFM.kt (pattern)
// EccoPath reference: eccopath/lib/lastfm.ts searchArtists()
suspend fun searchArtists(query: String, limit: Int = 1): Result<ArtistSearchResponse> =
    runCatching {
        client.get("https://ws.audioscrobbler.com/2.0/") {
            parameter("method", "artist.search")
            parameter("artist", query)
            parameter("limit", limit.toString())
            parameter("api_key", API_KEY)
            parameter("format", "json")
        }.body<ArtistSearchResponse>()
    }
```

### Pattern 2: Response model for `artist.search`

The Last.fm API response shape (from EccoPath `eccopath/lib/lastfm.ts` `LastfmSearchResponse`):

```kotlin
// NEW: lastfm/src/main/kotlin/com/metrolist/lastfm/models/ArtistSearchResponse.kt
@Serializable
data class ArtistSearchResponse(
    val results: Results
) {
    @Serializable
    data class Results(
        val artistmatches: ArtistMatches
    ) {
        @Serializable
        data class ArtistMatches(
            val artist: List<ArtistMatch> = emptyList()
        )
    }

    @Serializable
    data class ArtistMatch(
        val name: String,
        val mbid: String = "",
        val url: String = "",
        val listeners: String = ""
    )
}
```

**Confidence: HIGH** — verified directly from EccoPath `eccopath/lib/lastfm.ts` `LastfmSearchResponse` interface and the Last.fm API documentation shape.

### Pattern 3: Ghost-text autocomplete in Compose

The web pattern (EccoPath `SeedSearch.tsx`) uses a positioned overlay div with transparent user-typed text followed by the ghost suffix in faded color. The Compose equivalent:

```kotlin
// Source: derived from Compose BasicTextField + Box overlay pattern
@Composable
fun GhostTextField(
    value: String,
    ghostSuffix: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(modifier = modifier) {
        // Ghost text layer (behind the real input)
        if (ghostSuffix.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .matchParentSize()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Invisible spacer that exactly matches user-typed text width
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.Transparent,
                    maxLines = 1,
                )
                // Ghost suffix at reduced opacity
                Text(
                    text = ghostSuffix,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                    maxLines = 1,
                )
            }
        }
        // Real input field on top
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface
            ),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        )
                    }
                    innerTextField()
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}
```

**Critical gotcha:** The ghost text layer must render in the exact same typeface, size, and weight as the `BasicTextField` to ensure the transparent spacer and the visible suffix align perfectly at all screen densities.

### Pattern 4: Debounce via cancellable coroutine Job in ViewModel

Matches the EccoPath `clearTimeout`/`setTimeout(fn, 300)` pattern exactly, using Kotlin coroutines:

```kotlin
// In BridgeViewModel
private var fromSearchJob: Job? = null
private var toSearchJob: Job? = null

private val _fromGhostSuffix = MutableStateFlow("")
private val _toGhostSuffix = MutableStateFlow("")
val fromGhostSuffix: StateFlow<String> = _fromGhostSuffix.asStateFlow()
val toGhostSuffix: StateFlow<String> = _toGhostSuffix.asStateFlow()

fun onFromQueryChanged(query: String) {
    _fromQuery.value = query
    fromSearchJob?.cancel()
    if (query.isBlank()) { _fromGhostSuffix.value = ""; return }
    fromSearchJob = viewModelScope.launch(Dispatchers.IO) {
        delay(300L)
        LastFM.searchArtists(query, 1)
            .onSuccess { response ->
                val suggestion = response.results.artistmatches.artist.firstOrNull()?.name ?: ""
                val suffix = if (suggestion.startsWith(query, ignoreCase = true))
                    suggestion.drop(query.length) else ""
                _fromGhostSuffix.value = suffix
            }
            .onFailure { _fromGhostSuffix.value = "" }
    }
}
```

**Confidence: HIGH** — this coroutine Job cancel/delay pattern is the standard Kotlin equivalent of JS debounce. The project already uses `viewModelScope.launch(Dispatchers.IO)` for network calls and `MutableStateFlow` for ViewModel state.

### Pattern 5: Progress bar with determinate/indeterminate mode

```kotlin
// In BridgeScreen, inside when (uiState is BridgeUiState.Searching)
val progress = if (state.totalHops > 0)
    state.foundHops.toFloat() / state.totalHops.toFloat()
else
    null  // null = indeterminate

if (progress != null) {
    LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
    )
} else {
    LinearProgressIndicator(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
    )
}
Text(
    text = if (state.totalHops > 0)
        "Found ${state.foundHops} of ${state.totalHops} hops…"
    else
        "Searching…",
    style = MaterialTheme.typography.bodySmall
)
```

The `LinearProgressIndicator` determinate overload takes `progress: () -> Float` (lambda) in Material 3 1.5.x. The indeterminate overload takes no `progress` parameter.

**Confidence: HIGH** — verified from Material 3 component signatures in the project.

### Anti-Patterns to Avoid

- **Signing `artist.search` with `lastfmParams()`:** The existing `lastfmParams()` always computes an MD5 API signature. For `artist.search`, the signature is not required, and if `SECRET` is empty at call time, the result is a signature computed over an empty secret which may cause a 403. Use a plain GET instead.
- **Storing confirmed artist names as ghost state:** The ghost suffix is only the trailing portion (e.g., "iohead" for "Rad" → "Radiohead"). Store the full confirmed name separately (`fromConfirmedArtist`, `toConfirmedArtist`) for passing to `startBridge()`.
- **Launching autocomplete on the main thread with `Dispatchers.Main`:** Network calls must use `Dispatchers.IO`. The `delay(300)` debounce can start on any dispatcher since it just suspends.
- **Ghost text using `TextField` with suffix parameter:** Material 3 `TextField` has a `suffix` slot but it appears after the text field, not inline. Use `BasicTextField` with a `Box` overlay for true inline ghost text.
- **Not cancelling the debounce job on `isRunning=true`:** When bridge computation starts, clear ghost text and cancel any pending autocomplete jobs so stale results don't appear during the search.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| HTTP client for `artist.search` | Custom `HttpURLConnection` or OkHttp directly | Existing `LastFM` Ktor client | Module already configured with timeout, OkHttp engine, JSON parsing |
| Debounce logic | `Handler.postDelayed` / custom timer | `delay()` in a cancellable coroutine `Job` | Coroutine Job cancel is cleaner, testable with `TestCoroutineScheduler` |
| Progress percentage | Manual float tracking | `BridgeUiState.Searching.foundHops / totalHops` — already dispatched by `MeldBridgeInterface.onProgress()` | The callback chain is already wired |
| Error state | New error flag | `BridgeUiState.Error` — already dispatched by `MeldBridgeInterface.createPlaylist(found=false)` | No new state needed |
| Concurrent guard | Separate boolean flag | `BridgeViewModel.isRunning` — already computed from `_uiState` | Adding a separate flag risks going out of sync |

**Key insight:** The BridgeUiState machine and its wiring from JavaScript callbacks is complete. Phase 4 is purely about building the UI that reads this state and the ViewModel layer that drives autocomplete.

---

## Common Pitfalls

### Pitfall 1: Ghost text pixel misalignment
**What goes wrong:** The transparent spacer text and the ghost suffix use different text measurement metrics than the actual `BasicTextField`, causing the ghost to appear shifted at certain font scale settings.
**Why it happens:** Compose text measurement varies with `fontScale`, letter spacing, and line height. If the ghost overlay uses slightly different `TextStyle` properties, the width of the invisible spacer does not match the width of the characters rendered by `BasicTextField`.
**How to avoid:** Use the exact same `TextStyle` (including `letterSpacing`, `lineHeight`) in both the ghost overlay and the `BasicTextField`. Pass a shared `val inputTextStyle = MaterialTheme.typography.bodyLarge` variable to both.
**Warning signs:** Ghost suffix appears to jump 1-2 characters left or right after typing a few characters, especially at non-default font scale.

### Pitfall 2: `artist.search` case-sensitivity check
**What goes wrong:** The ghost suffix shows for suggestions that don't actually complete what the user typed, creating confusing UX.
**Why it happens:** Last.fm's `artist.search` returns suggestions sorted by popularity, not by prefix match. A search for "ra" might return "Radiohead" (starts with "ra") OR "Arcade Fire" (contains "ar"). If you show the ghost for any result, you get false completions.
**How to avoid:** Only show the ghost suffix when `suggestion.startsWith(query, ignoreCase = true)`. This is exactly what EccoPath does (`SeedSearch.tsx` line 155).
**Warning signs:** Ghost text appears for suggestions that share no prefix with what the user typed.

### Pitfall 3: Stale ghost suffix after rapid deletion
**What goes wrong:** User types "Radiohead", then deletes back to "R". Ghost shows "adiohead". User then immediately presses Enter — "R" gets confirmed as the artist.
**Why it happens:** The debounce Job from the previous keystroke fires after 300ms and updates the ghost. But if the user presses Enter before the debounce fires, `ghostFull` is still the previous suggestion.
**How to avoid:** When the user presses Enter/confirms, only use `ghostFull` if `ghostFull.startsWith(currentInput, ignoreCase = true)`. Otherwise fall back to `currentInput` raw. This matches the EccoPath behavior in `handleKeyDown`.

### Pitfall 4: `LinearProgressIndicator` progress lambda
**What goes wrong:** Compile error or incorrect progress display with `LinearProgressIndicator(progress = 0.5f)`.
**Why it happens:** Material 3 1.3+ changed the determinate `LinearProgressIndicator` signature from `progress: Float` to `progress: () -> Float` (lambda) to avoid unnecessary recomposition.
**How to avoid:** Use `LinearProgressIndicator(progress = { progressValue })` (lambda form). The indeterminate variant takes no `progress` parameter at all.

### Pitfall 5: `API_KEY` empty in `searchArtists()` if `initialize()` not called
**What goes wrong:** `artist.search` returns a 403 error or empty results.
**Why it happens:** `LastFM.API_KEY` starts empty and is only set by `LastFM.initialize(apiKey, secret)` in `App.onCreate()`. If `searchArtists()` is called before `App.onCreate()` sets it (e.g., in a test), the API key param is blank.
**How to avoid:** In unit tests, call `LastFM.initialize("test_key", "test_secret")` in `@Before`. In the app, `App.onCreate()` sets the key before any ViewModel is created so runtime is safe.

### Pitfall 6: Two `BridgeViewModel` instances in tests
**What goes wrong:** `BridgeViewModelTest` constructs `BridgeViewModel` directly but `MeldBridgeInterface` is not wired to `LastFM`. Tests for autocomplete need to inject a fake `LastFM` or mock `searchArtists`.
**Why it happens:** `LastFM` is a Kotlin `object` singleton — it cannot be replaced via constructor injection. Tests that call `searchArtists()` will make real network calls unless the API key is empty (which causes failures of a different kind).
**How to avoid:** For autocomplete unit tests, expose the search function as a lambda parameter in `BridgeViewModel` (or use a thin wrapper interface). Alternatively, test only the state transitions (ghost suffix state updates) by calling the ViewModel method with a stubbed coroutine dispatcher that never executes the network call body.

---

## Code Examples

### Last.fm `artist.search` GET request
```kotlin
// Source: eccopath/lib/lastfm.ts searchArtists() — ported to Kotlin
// In: lastfm/src/main/kotlin/com/metrolist/lastfm/LastFM.kt
suspend fun searchArtists(query: String, limit: Int = 1): Result<ArtistSearchResponse> =
    runCatching {
        client.get("https://ws.audioscrobbler.com/2.0/") {
            parameter("method", "artist.search")
            parameter("artist", query)
            parameter("limit", limit.toString())
            parameter("api_key", API_KEY)
            parameter("format", "json")
        }.body<ArtistSearchResponse>()
    }
```

### Extracting ghost suffix from search result
```kotlin
// Source: eccopath/components/search/SeedSearch.tsx handleChange() — lines 155-163
val suggestion = response.results.artistmatches.artist.firstOrNull()?.name ?: ""
val suffix = if (suggestion.startsWith(query, ignoreCase = true))
    suggestion.drop(query.length)
else
    ""
_fromGhostSuffix.value = suffix
_fromGhostFull.value = if (suffix.isNotEmpty()) suggestion else ""
```

### Disabling inputs + button during bridge run (D-09)
```kotlin
// In BridgeScreen.kt
val isRunning by viewModel.uiState.collectAsState()
// ...
GhostTextField(
    value = fromQuery,
    enabled = uiState !is BridgeUiState.Searching,
    // ...
)
Button(
    onClick = { viewModel.startBridge(fromConfirmed, toConfirmed) },
    enabled = fromConfirmed.isNotBlank() && toConfirmed.isNotBlank()
            && uiState !is BridgeUiState.Searching
) {
    Text(stringResource(R.string.bridge_find_button))
}
```

### Inline error display (D-08)
```kotlin
// In BridgeScreen.kt — no dialog, just content area text
is BridgeUiState.Error -> Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier.padding(horizontal = 24.dp)
) {
    Text(
        text = state.message,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = stringResource(R.string.bridge_error_try_different),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.Center,
    )
}
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `LinearProgressIndicator(progress = Float)` | `LinearProgressIndicator(progress = () -> Float)` (lambda) | Material 3 ~1.3.0 | Must use lambda form — float overload may be deprecated |
| `TextField` with `trailingIcon`/`suffix` for ghost | `BasicTextField` + `Box` overlay | Compose 1.x → present | `TextField` suffix is not inline; `BasicTextField` gives full layout control |
| Direct `Handler.postDelayed` for debounce | `viewModelScope.launch { delay(300L) }` with `Job.cancel()` | Coroutines became idiomatic ~2021 | Cleaner cancellation, testable |

---

## Open Questions

1. **`LastFM.API_KEY` access from `searchArtists()`**
   - What we know: `API_KEY` is a `private var` on the `LastFM` object, set via `initialize()`. `searchArtists()` would be a public method on the same object, so it has access.
   - What's unclear: If `initialize()` hasn't been called (e.g., during integration tests that don't spin up `App`), the key is blank and Last.fm returns a 403. This is a test setup concern, not a runtime concern.
   - Recommendation: Add a guard in `searchArtists()` — if `API_KEY.isEmpty()` return `Result.failure(IllegalStateException("LastFM not initialized"))`. Tests that need autocomplete should call `LastFM.initialize("key", "secret")` in `@Before`.

2. **Ghost text and IME (input method editor) interaction**
   - What we know: Android's soft keyboard may offer its own autocomplete suggestions. Ghost text and IME suggestions coexist independently — one is in-app UI, the other is the keyboard's own overlay.
   - What's unclear: Whether `autoCorrect = false` / `KeyboardOptions(autoCorrect = false)` is needed to suppress IME suggestions that conflict with ghost text display. EccoPath web sets `autoComplete="off" spellCheck={false}`.
   - Recommendation: Set `KeyboardOptions(imeAction = ImeAction.Done, autoCorrect = false)` on the `BasicTextField`. Use `ImeAction.Done` so pressing the keyboard's checkmark confirms the ghost suggestion (consistent with D-05 Enter-to-confirm).

3. **Side-by-side layout on narrow screens**
   - What we know: D-03 specifies `Row` with equal `weight(1f)` and small padding between. CONTEXT.md specifics suggest this is graceful on narrow screens.
   - What's unclear: Minimum usable width. "From" and "To" labels with 12dp padding on each side in a `Row` with 8dp gap would give roughly `(screenWidth - 32dp) / 2` per field. On a 360dp-wide phone that's ~164dp per field — tight but usable.
   - Recommendation: Use `Row(Modifier.fillMaxWidth()) { Box(Modifier.weight(1f)) { … } + Spacer(8dp) + Box(Modifier.weight(1f)) { … } }`. Test on a 360dp emulator config.

---

## Environment Availability

Step 2.6: SKIPPED — this phase adds code/UI changes with no new external service dependencies. `LastFM` Ktor client is already configured; Last.fm API is a public HTTPS endpoint with no additional local tool requirements.

---

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 4.13.2 |
| Config file | none (default Android Gradle test runner) |
| Quick run command | `./gradlew :app:testFossDebugUnitTest` |
| Full suite command | `./gradlew :app:testFossDebugUnitTest :lastfm:test` |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| BRDG-01 | `onFromQueryChanged("Rad")` after 300ms debounce emits ghost suffix "iohead" | unit | `./gradlew :app:testFossDebugUnitTest --tests "*.BridgeViewModelTest.onFromQueryChanged_emits_ghostSuffix"` | ❌ Wave 0 |
| BRDG-01 | Confirmed artist stored in `fromConfirmedArtist` StateFlow after `confirmFrom()` | unit | `./gradlew :app:testFossDebugUnitTest --tests "*.BridgeViewModelTest.confirmFrom_stores_artist"` | ❌ Wave 0 |
| BRDG-01 | Ghost suffix only shows when suggestion starts with query (startsWith check) | unit | `./gradlew :app:testFossDebugUnitTest --tests "*.BridgeViewModelTest.ghostSuffix_only_shown_on_prefix_match"` | ❌ Wave 0 |
| BRDG-04 | `BridgeUiState.Searching(foundHops=2, totalHops=5)` produces progress = 0.4f | unit | `./gradlew :app:testFossDebugUnitTest --tests "*.BridgeScreenTest.progress_fraction_computed_correctly"` | ❌ Wave 0 |
| BRDG-06 | `BridgeUiState.Error` message is displayed as inline text (no dialog) | manual | visual inspection on device/emulator | — |
| BRDG-06 | After error, inputs remain enabled (not disabled) | unit | `./gradlew :app:testFossDebugUnitTest --tests "*.BridgeViewModelTest.error_state_does_not_set_isRunning"` | ❌ Wave 0 |

### Sampling Rate
- **Per task commit:** `./gradlew :app:testFossDebugUnitTest`
- **Per wave merge:** `./gradlew :app:testFossDebugUnitTest :lastfm:test`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps
- [ ] `app/src/test/kotlin/com/metrolist/music/bridge/BridgeViewModelTest.kt` — add new test methods for autocomplete state (BRDG-01, BRDG-06)
- [ ] `gradle/libs.versions.toml` — add `mockitoKotlin` and `coroutinesTest` version entries
- [ ] `app/build.gradle.kts` — add `testImplementation(libs.mockito.kotlin)` and `testImplementation(libs.coroutines.test)`

**Note:** `BridgeViewModelTest.kt` already exists and imports `org.mockito.kotlin.mock` but the dependency is NOT declared in `libs.versions.toml` or `app/build.gradle.kts`. The test file will fail to compile until Wave 0 adds the dependency. This is the most critical Wave 0 gap.

---

## Project Constraints (from CLAUDE.md)

Directives the planner must verify compliance against:

- Use `MutableStateFlow` for all ViewModel state — no `LiveData`, no `mutableStateOf` in ViewModel
- `Dispatchers.IO` for all network calls (Last.fm `artist.search`)
- `Dispatchers.Main` is NOT appropriate for network calls — do not use it for debounce coroutine body
- Use `runCatching` / `Result<T>` for all API calls — chain `.onSuccess {}` / `.onFailure {}`
- Use `Timber.tag("…").d(…)` for logging, not `Log.d()`
- String resources go in `app/src/main/res/values/strings.xml` — no hardcoded strings in Composables
- Composable functions are PascalCase: `GhostTextField`, `BridgeInputRow`, etc.
- ViewModel class name: `BridgeViewModel` (already exists — add methods, don't rename)
- No Hilt in `lastfm` module — it's a plain Kotlin `object` singleton (no constructor injection)
- `@Immutable` annotation on `BridgeUiState` data classes for Compose stability (already present, preserve)
- GPL-3.0 license header on new Kotlin files: `/** Metrolist Project (C) 2026 / Licensed under GPL-3.0 | See git history for contributors */`

---

## Sources

### Primary (HIGH confidence)
- `eccopath/components/search/SeedSearch.tsx` — authoritative ghost-text pattern, debounce timing, prefix-match logic
- `eccopath/components/search/BridgeSearch.tsx` — autocomplete UX during bridge crawl (disabled state)
- `eccopath/lib/lastfm.ts` — `searchArtists()` API params and response shape
- `lastfm/src/main/kotlin/com/metrolist/lastfm/LastFM.kt` — existing Ktor client, authentication pattern, object structure
- `app/src/main/kotlin/com/metrolist/music/ui/screens/bridge/BridgeScreen.kt` — existing `BridgeUiState` sealed class, current placeholder
- `app/src/main/kotlin/com/metrolist/music/viewmodels/BridgeViewModel.kt` — `startBridge()`, `isRunning`, `MutableStateFlow` pattern
- `app/src/main/kotlin/com/metrolist/music/bridge/MeldBridgeInterface.kt` — `onProgress()` dispatches `Searching(foundHops, totalHops)`
- `gradle/libs.versions.toml` — confirmed library versions (Compose 1.10.2, Material3 1.5.0-alpha09, Ktor 3.4.0)

### Secondary (MEDIUM confidence)
- `app/src/main/kotlin/com/metrolist/music/viewmodels/OnlineSearchSuggestionViewModel.kt` — debounce via `snapshotFlow + debounce(300L)` (alternative pattern to coroutine Job)
- `app/src/main/kotlin/com/metrolist/music/ui/screens/search/OnlineSearchScreen.kt` — `TextFieldValue`, `TextRange`, keyboard controller patterns

### Tertiary (LOW confidence)
- None — all critical claims verified from project source files

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all versions verified from `gradle/libs.versions.toml` and existing source files
- Architecture: HIGH — patterns derived directly from EccoPath canonical references and existing Kotlin codebase
- Pitfalls: HIGH — pitfalls 1-5 derived from code analysis; pitfall 6 is LOW (theoretical test concern)
- Test gaps: HIGH — `BridgeViewModelTest.kt` missing `mockito-kotlin` dep confirmed by inspecting `app/build.gradle.kts`

**Research date:** 2026-04-04
**Valid until:** 2026-05-04 (30 days — stable APIs, no fast-moving dependencies)
