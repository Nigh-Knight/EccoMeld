# Phase 2: Bridge Tab + State Model - Research

**Researched:** 2026-04-04
**Domain:** Jetpack Compose Navigation, ViewModel state modeling, Android resource integration
**Confidence:** HIGH

## Summary

Phase 2 is a focused wiring exercise. The existing navigation infrastructure (`Screens.kt`, `AppNavigation.kt`, `NavigationBuilder.kt`, `MainActivity.kt`) is clean and well-understood from reading the source. Adding a Bridge tab follows an exact pattern already established four times (Home, Search, ListenTogether, Library). The most complex element of this phase is defining `BridgeUiState` as a sealed class — this is pure Kotlin with no library dependencies.

The main risk is touching too many files without understanding side-effects: `topLevelScreens`, `currentTitleRes`, `navigationItems`, `shouldShowNavigationBar`, and the `NavigationTab` enum in `AppearanceSettings.kt` are all places that reference the full list of tabs and must be kept in sync. The planner must address all five touch points, not just `Screens.kt`.

**Primary recommendation:** Copy the `ListenTogether` tab pattern exactly. Add `Bridge` to `Screens.MainScreens`, register its composable route in `NavigationBuilder`, create a minimal `BridgeScreen` composable, define `BridgeUiState` sealed class, and create a `BridgeViewModel` with a `StateFlow<BridgeUiState>`. Touch the five supporting locations in `MainActivity.kt` and `AppearanceSettings.kt` as documented below.

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| BRDG-07 | Bridge tab appears in bottom navigation alongside existing Meld tabs | `Screens.MainScreens` list drives `AppNavigationBar`; add `Bridge` object to that list. Navigation wiring via `NavigationBuilder.kt` and `NavHost` start destination logic in `MainActivity`. |
</phase_requirements>

## Project Constraints (from CLAUDE.md)

- **Platform**: Android only (SDK 26+, targeting SDK 36)
- **Language**: Kotlin 2.3.10, JVM target 21
- **UI**: Jetpack Compose 1.10.2 + Material 3 1.5.0-alpha09
- **DI**: Dagger Hilt 2.59.1 — ViewModels use `@HiltViewModel` + `@Inject constructor`
- **Navigation**: Jetpack Navigation Compose — NavHost + route strings, composables registered in `NavigationBuilder.kt`
- **Screens**: PascalCase composable functions, `NavController` parameter, screens in `ui/screens/`
- **ViewModels**: PascalCase + `ViewModel` suffix, in `viewmodels/` package
- **State**: `MutableStateFlow` in ViewModel, `collectAsState()` in Compose
- **String resources**: New strings go in `app/src/main/res/values/metrolist_strings.xml` (not `strings.xml`) — confirmed by how `R.string.together` is defined there
- **Drawables**: Vector XML in `app/src/main/res/drawable/` — outlined/filled pair for tab icon (see `home_outlined.xml` / `home_filled.xml` pattern)
- **No package rename**: Keep `com.metrolist.music`
- **Logging**: Timber throughout
- **GSD workflow**: All file changes through GSD execute-phase

## Standard Stack

No new libraries needed. This phase uses only what is already in the project:

### Core (already in project)
| Library | Version | Purpose |
|---------|---------|---------|
| Jetpack Navigation Compose | bundled with Compose | NavHost routing |
| Jetpack Compose | 1.10.2 | UI |
| Material 3 | 1.5.0-alpha09 | `NavigationBar`, `NavigationBarItem` |
| Dagger Hilt | 2.59.1 | ViewModel injection |
| kotlinx.coroutines | 1.10.2 | StateFlow for UI state |

**No new dependencies required for Phase 2.**

## Architecture Patterns

### How the Existing Tab System Works (HIGH confidence, verified by reading source)

The tab system has four layers that must stay in sync:

**Layer 1 — `Screens.kt`**
`Screens` is a sealed class. Each tab is an `object` subclass with:
- `titleId: Int` — `@StringRes` for the nav label
- `iconIdInactive: Int` — `@DrawableRes` for unselected state
- `iconIdActive: Int` — `@DrawableRes` for selected state
- `route: String` — navigation route string

The `companion object` holds `MainScreens: List<Screens>` — this is the canonical list of bottom-nav tabs.

**Layer 2 — `NavigationBuilder.kt`**
`NavGraphBuilder.navigationBuilder()` extension registers a `composable()` for every route. The screen composable is called here. This is where `BridgeScreen(navController)` gets registered.

**Layer 3 — `MainActivity.kt` (five touch points)**

1. **`navigationItems`** (line ~547): Filters `Screens.MainScreens` for the `ListenTogetherInTopBar` preference. Bridge needs to appear here — either always, or conditionally like ListenTogether. Since Bridge has no analogous setting, it should always appear.

2. **`topLevelScreens`** (line ~567): A `remember` list of routes that qualify for `shouldShowTopBar`. Bridge should be added to this list so the TopAppBar appears when Bridge is active.

3. **`currentTitleRes`** (line ~775): A `when` expression mapping `navBackStackEntry?.destination?.route` to a string resource ID for the TopAppBar title. Add `Screens.Bridge.route -> R.string.bridge`.

4. **`shouldShowNavigationBar`** (line ~607): Derived from `navigationItemRoutes.contains(currentRoute)`. This is automatic once Bridge is in `navigationItems` — no explicit change needed here.

5. **`NavHost` start destination** (line ~1050): The `when` expression for `defaultOpenTab` maps `NavigationTab` enum values to screen routes. A `BRIDGE` entry is optional for Phase 2 (it's a settings feature, deferred). No change strictly required here.

**Layer 4 — `AppearanceSettings.kt`**
`NavigationTab` enum (line 1633) is `HOME`, `SEARCH`, `LIBRARY`. The "default open tab" settings dialog maps these to screen routes. This enum does not need to include `BRIDGE` for Phase 2 (Bridge is always available; defaulting to it is a future enhancement). No change strictly required.

### Recommended File Structure for Phase 2

```
app/src/main/kotlin/com/metrolist/music/
├── ui/
│   └── screens/
│       ├── Screens.kt                        # Add Bridge object + add to MainScreens
│       ├── NavigationBuilder.kt              # Register composable("bridge") { BridgeScreen(...) }
│       └── bridge/
│           └── BridgeScreen.kt              # New: BridgeScreen composable + BridgeUiState
├── viewmodels/
│   └── BridgeViewModel.kt                   # New: HiltViewModel holding StateFlow<BridgeUiState>
└── MainActivity.kt                           # 3 touch points: topLevelScreens, currentTitleRes, navigationItems
app/src/main/res/
├── drawable/
│   ├── bridge_outlined.xml                  # New: icon for unselected state
│   └── bridge_filled.xml                    # New: icon for selected state
└── values/
    └── metrolist_strings.xml                # Add: <string name="bridge">Bridge</string>
```

### Pattern: Adding a Tab (replicate ListenTogether)

**`Screens.kt` — add object:**
```kotlin
// Source: verified from app/src/main/kotlin/com/metrolist/music/ui/screens/Screens.kt
object Bridge : Screens(
    titleId = R.string.bridge,
    iconIdInactive = R.drawable.bridge_outlined,
    iconIdActive = R.drawable.bridge_filled,
    route = "bridge"
)

companion object {
    val MainScreens = listOf(Home, Search, ListenTogether, Library, Bridge)
}
```

**`NavigationBuilder.kt` — register route:**
```kotlin
// Source: verified pattern from existing composable() registrations
composable(Screens.Bridge.route) {
    BridgeScreen(navController = navController)
}
```

**`MainActivity.kt` — `topLevelScreens`:**
```kotlin
val topLevelScreens = remember {
    listOf(
        Screens.Home.route,
        Screens.Library.route,
        Screens.ListenTogether.route,
        Screens.Bridge.route,   // add this
        "settings",
    )
}
```

**`MainActivity.kt` — `currentTitleRes`:**
```kotlin
val currentTitleRes = remember(navBackStackEntry) {
    when (navBackStackEntry?.destination?.route) {
        Screens.Home.route -> R.string.home
        Screens.Search.route -> R.string.search
        Screens.Library.route -> R.string.filter_library
        Screens.ListenTogether.route -> R.string.together
        Screens.Bridge.route -> R.string.bridge   // add this
        else -> null
    }
}
```

### Pattern: BridgeUiState Sealed Class

All states needed by the Bridge feature (across all phases) should be defined now so later phases can just emit new states without modifying the sealed hierarchy. The Phase 2 screen only needs to respond to each — placeholder content is fine.

```kotlin
// Source: designed from requirements BRDG-02..BRDG-06 in REQUIREMENTS.md
// Location: app/src/main/kotlin/com/metrolist/music/ui/screens/bridge/BridgeScreen.kt
// or a separate BridgeUiState.kt if preferred

sealed class BridgeUiState {
    /** No bridge running, initial/reset state */
    object Idle : BridgeUiState()

    /** Bridge computation in progress; hop count feedback for BRDG-04 */
    data class Searching(
        val foundHops: Int = 0,
        val totalHops: Int = 0,
    ) : BridgeUiState()

    /** EccoPath returned a path; BRDG-05 data available */
    data class PathFound(
        val path: List<String>,      // artist names — detail type refined in Phase 5/6
    ) : BridgeUiState()

    /** Playlist built and playing; PLAY-01 state */
    data class PlaylistReady(
        val path: List<String>,
        val nowPlayingIndex: Int = 0,
    ) : BridgeUiState()

    /** No path found or computation failed; BRDG-06 */
    data class Error(
        val message: String,
    ) : BridgeUiState()
}
```

### Pattern: BridgeViewModel

```kotlin
// Source: verified HiltViewModel pattern from existing viewmodels/
@HiltViewModel
class BridgeViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow<BridgeUiState>(BridgeUiState.Idle)
    val uiState: StateFlow<BridgeUiState> = _uiState.asStateFlow()
}
```

Note: Later phases will inject `MusicDatabase` and other dependencies into `BridgeViewModel`. For Phase 2, no dependencies are needed — the ViewModel just holds state.

### Pattern: BridgeScreen (placeholder implementation)

```kotlin
// Source: verified from ui/screens/HomeScreen.kt and ListenTogetherScreen.kt patterns
@Composable
fun BridgeScreen(
    navController: NavController,
) {
    val viewModel: BridgeViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsState()

    when (val state = uiState) {
        is BridgeUiState.Idle -> { /* placeholder */ }
        is BridgeUiState.Searching -> { /* placeholder with state.foundHops */ }
        is BridgeUiState.PathFound -> { /* placeholder with state.path */ }
        is BridgeUiState.PlaylistReady -> { /* placeholder with state.nowPlayingIndex */ }
        is BridgeUiState.Error -> { /* placeholder with state.message */ }
    }
}
```

### Pattern: Vector Drawable Icons

The icon pair for a nav tab always consists of:
- `<name>_outlined.xml` — unselected state (outline only)
- `<name>_filled.xml` — selected state (filled/solid)

For a Bridge icon, `explore_outlined.xml` (already present) could serve as the outlined icon — it represents discovery/exploration which aligns with bridge discovery semantics. A filled variant (`explore_filled.xml` or a dedicated `bridge_outlined.xml`/`bridge_filled.xml`) will need to be created or the `explore_outlined.xml` used for both with the distinction omitted for MVP.

**Recommendation:** Use `explore_outlined.xml` for both active and inactive states in Phase 2. Creating a proper outlined/filled icon pair is cosmetic polish, not a blocker. The planner can assign this as one small task and note that a real designer icon is deferred.

Alternatively, create minimal `bridge_outlined.xml` and `bridge_filled.xml` using the Material Symbols "merge" or "route" path data if desired. This is a one-time vector XML creation with no Kotlin work.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead |
|---------|-------------|-------------|
| Back-stack isolation between tabs | Custom back stack management | `navController.navigate { popUpTo(startDestination) { saveState = true }; launchSingleTop = true; restoreState = true }` (already in `MainActivity.kt` `onNavItemClick`) |
| State persistence across recomposition | Manual `rememberSaveable` hacks in BridgeScreen | `StateFlow` in `BridgeViewModel` — survives recomposition, config changes, screen rotation |
| Tab selected state detection | String comparison in composable | `isRouteSelected()` already in `AppNavigation.kt` — works automatically once route is registered |

## Common Pitfalls

### Pitfall 1: Forgetting `MainScreens` companion object
**What goes wrong:** Adding `Bridge` object to `Screens.kt` but not to `Screens.MainScreens` — Bridge won't appear in the nav bar because `AppNavigationBar` iterates `navigationItems` which comes from `MainScreens`.
**How to avoid:** `MainScreens` update is the single most important change in `Screens.kt`.

### Pitfall 2: Missing `topLevelScreens` entry in MainActivity
**What goes wrong:** Bridge tab appears in nav bar but the TopAppBar disappears when Bridge is selected, and `shouldShowTopBar` is false.
**How to avoid:** Add `Screens.Bridge.route` to the `topLevelScreens` list.

### Pitfall 3: Missing `currentTitleRes` mapping
**What goes wrong:** TopAppBar shows blank title on Bridge screen (no crash, just UX regression).
**How to avoid:** Add `Screens.Bridge.route -> R.string.bridge` to the `when` expression.

### Pitfall 4: Sealed class not `@Immutable`
**What goes wrong:** Compose stability warnings; potential unnecessary recomposition.
**How to avoid:** Annotate `BridgeUiState` subclasses with `@Immutable` following the codebase convention (`@Immutable` on data classes in model layer is standard here).

### Pitfall 5: Using `object` for stateful states
**What goes wrong:** `Searching`, `PathFound`, `PlaylistReady`, and `Error` carry data — they must be `data class`, not `object`. Only `Idle` is truly a singleton state.
**How to avoid:** Use `object` only for `Idle`; all others are `data class`.

### Pitfall 6: Route string collision
**What goes wrong:** Using `"bridge"` as a route that accidentally matches a prefix of an existing route (`"browse/{browseId}"` starts with `b` but won't collide). No existing route starts with `bridge`.
**How to avoid:** Confirmed by reviewing `NavigationBuilder.kt` — `"bridge"` is safe.

### Pitfall 7: Icon drawable reference that doesn't exist
**What goes wrong:** Build fails at resource resolution if `R.drawable.bridge_outlined` or `R.drawable.bridge_filled` are referenced but XML files not created.
**How to avoid:** Create both drawable XMLs before or in the same task as `Screens.kt` modification.

## Runtime State Inventory

Step 2.5 SKIPPED: This is a greenfield addition phase (new tab + new state model). No renames, refactors, or migrations involved.

## Environment Availability

Step 2.6 SKIPPED: Phase 2 is pure code/config changes — new Kotlin files and resource XMLs. No external tools, databases, or services beyond the existing Android build toolchain (JDK 21, Android SDK 36, Gradle 9.3.1) which are already verified from Phase 1.

## Validation Architecture

nyquist_validation is enabled in `.planning/config.json`.

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 4.13.2 (present in library modules per CLAUDE.md) |
| Config file | None detected in `app/` module — no `src/test/` directory found |
| Quick run command | `./gradlew :app:assembleDebug` (build verification — no unit test infra in app module) |
| Full suite command | `./gradlew :app:assembleDebug :app:assembleRelease` |

**Note:** The `app` module has no configured unit test infrastructure. JUnit is declared in library modules only. For Phase 2, validation is build-level (does it compile?) and manual (does the tab appear?). The planner should assign a build + smoke test task, not attempt to write JUnit tests for UI navigation in Wave 0.

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| BRDG-07 | Bridge tab appears in bottom nav and is tappable | smoke (build + manual) | `./gradlew :app:assembleDebug` | N/A — build check |
| BRDG-07 | Tapping Bridge navigates without affecting other tabs | manual | Manual device/emulator test | N/A |
| BRDG-07 | All BridgeUiState cases exist in code | build (compile check) | `./gradlew :app:assembleDebug` | N/A |

### Sampling Rate
- **Per task commit:** `./gradlew :app:assembleDebug`
- **Per wave merge:** `./gradlew :app:assembleDebug`
- **Phase gate:** Full debug build green before `/gsd:verify-work`

### Wave 0 Gaps
None — no test infrastructure setup needed. Build verification suffices for this structural phase. Manual UAT covers the tab navigation behavior.

## State of the Art

| Old Approach | Current Approach | Impact |
|--------------|------------------|--------|
| XML-based Activity navigation with tabs | Jetpack Navigation Compose with NavHost | Already in use — BridgeScreen is just another composable destination |
| Manual ViewModel state with `MutableLiveData` | `MutableStateFlow` + `collectAsState()` | Already in use — follow same pattern |

## Open Questions

1. **Icon choice for Bridge tab**
   - What we know: `explore_outlined.xml` exists and semantically fits. No `bridge_outlined.xml` or `bridge_filled.xml` exists.
   - What's unclear: Whether product wants a custom icon or is fine with the explore/merge icon.
   - Recommendation: Use `explore_outlined.xml` for both active and inactive in Phase 2. Create a real icon pair as cosmetic polish later or as a separate task.

2. **`R.string.together` location**
   - What we know: `R.string.together` is defined in `metrolist_strings.xml`, not `strings.xml`. New strings like `R.string.bridge` must go in `metrolist_strings.xml` to match the pattern.
   - What's unclear: Nothing — this is confirmed.
   - Recommendation: Add `<string name="bridge">Bridge</string>` to `metrolist_strings.xml`.

3. **`BridgeUiState` location — same file as `BridgeScreen` or separate?**
   - What we know: The codebase doesn't have a rigid convention; sealed classes are co-located with their screen in some cases and separate in others.
   - Recommendation: Co-locate `BridgeUiState` in `BridgeScreen.kt` for Phase 2. If it grows complex, extract later. This minimizes file count for a phase that is intentionally minimal.

## Sources

### Primary (HIGH confidence)
- Direct source read: `app/src/main/kotlin/com/metrolist/music/ui/screens/Screens.kt` — full tab definition pattern
- Direct source read: `app/src/main/kotlin/com/metrolist/music/ui/component/AppNavigation.kt` — `AppNavigationBar` and `AppNavigationRail` implementations
- Direct source read: `app/src/main/kotlin/com/metrolist/music/ui/screens/NavigationBuilder.kt` — full route registration pattern
- Direct source read: `app/src/main/kotlin/com/metrolist/music/MainActivity.kt` — all five touch points identified and line numbers confirmed
- Direct source read: `app/src/main/kotlin/com/metrolist/music/ui/screens/settings/AppearanceSettings.kt` — `NavigationTab` enum (line 1633)
- Direct source read: `app/src/main/res/values/metrolist_strings.xml` — confirmed `R.string.together` location
- Direct source read: `app/src/main/res/drawable/` listing — confirmed available icons, no existing bridge icons

### Secondary (MEDIUM confidence)
- CLAUDE.md: conventions for `@HiltViewModel`, `@Immutable`, `MutableStateFlow`, screen naming
- REQUIREMENTS.md: BridgeUiState states derived from BRDG-01..BRDG-06 requirements

## Metadata

**Confidence breakdown:**
- Tab wiring (Screens.kt, NavigationBuilder, AppNavigation): HIGH — exact pattern read from source
- MainActivity touch points: HIGH — all five identified by reading the file with line numbers
- BridgeUiState design: HIGH — states directly map to REQUIREMENTS.md, no novel design decisions
- Icon approach: MEDIUM — decision to reuse explore_outlined.xml is pragmatic but aesthetic judgment
- Test infrastructure: HIGH — confirmed no unit test infra in app module by checking structure

**Research date:** 2026-04-04
**Valid until:** 2026-05-04 (stable — these are internal codebase patterns, no external library churn)
