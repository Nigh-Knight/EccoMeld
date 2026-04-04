# Phase 1: WebView Foundation - Context

**Gathered:** 2026-04-04
**Status:** Ready for planning

<domain>
## Phase Boundary

Bundle EccoPath as APK assets served via WebViewAssetLoader from a stable HTTPS origin, verify IndexedDB persistence across app restarts, and rebrand the app from Meld to EccoMeld across all user-visible surfaces. The WebView is headless (invisible) — all user-facing UI remains native Compose.

</domain>

<decisions>
## Implementation Decisions

### WebView Architecture
- **D-01:** WebView is hidden (headless) — all UI is native Compose. EccoPath only performs computation; results come back via JS bridge. No EccoPath web UI is ever shown to the user.
- **D-02:** WebView is created eagerly at app startup so Bridge is instant on first use. No lazy initialization.
- **D-03:** WebView lives as an application-scoped singleton (Hilt `@Singleton`), surviving config changes, tab switches, and the full app lifecycle. Consistent with how `MusicService` and other long-lived components are managed.

### EccoPath Build Pipeline
- **D-04:** A custom Gradle task handles the full EccoPath build pipeline: runs `next build` with static export, copies output to `app/src/main/assets/eccopath/`. Reproducible and automated as part of normal Android build.
- **D-05:** EccoPath keeps its original `next.config.ts` (with `basePath: "/path"`) for web development. The Gradle task uses a separate Android-specific config (e.g., `next.config.android.ts`) with `basePath: ""` and `output: "export"`. No conflict between EccoPath web dev and Android bundling.
- **D-06:** A post-build script strips `crossorigin` attributes from the exported HTML after `next build`. This proactively addresses the Next.js #61210 crossorigin attribute bug that can cause chunk loading failures in WebView.

### Branding
- **D-07:** Full rebrand to "EccoMeld" across all user-visible text: launcher label, app bar, notifications, about screen, crash handler, Discord RPC, and any other user-facing "Meld" or "Metrolist" references.
- **D-08:** App icon changes to the EccoMuse logo. Source file: `~/Projects/EccoMuse-Site/src/assets/images/EccoMuse_new_logo.png`. Must be adapted to Android adaptive icon format (foreground + background layers, multiple density buckets).

### Submodule
- **D-09:** EccoPath added as a git submodule at root-level `eccopath/` directory.
- **D-10:** Submodule tracks EccoPath's main branch (`branch = main` in `.gitmodules`). Both repos evolve together during active development.

### Claude's Discretion
- WebViewAssetLoader URL path structure (how EccoPath routes map to `https://appassets.androidplatform.net/`)
- Specific Gradle task implementation details (exec vs. npm plugin)
- Android adaptive icon layer splitting from the source PNG
- How to handle the `crossorigin` stripping (sed, node script, etc.)

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### EccoPath Source
- `eccopath/lib/bridgeCrawl.ts` — Core bridge algorithm (beam search + fallback BFS). Read to understand what the WebView will execute.
- `eccopath/lib/lastfm.ts` — Last.fm API client with IndexedDB persistent cache. Critical for verifying INFRA-03 (IndexedDB persistence).
- `eccopath/next.config.ts` — Current Next.js config with `basePath: "/path"`. Must create Android-specific variant.

### Existing WebView Patterns
- `app/src/main/kotlin/com/metrolist/music/ui/screens/SpotifyLoginScreen.kt` — Existing WebView usage with `WebViewClient`. Reference for WebView setup patterns in this codebase.
- `app/src/main/kotlin/com/metrolist/music/ui/screens/LoginScreen.kt` — Uses `addJavascriptInterface`. Reference for JS bridge pattern (used in Phase 3).

### Branding
- `app/src/main/res/values/app_name.xml` — Current app name ("Meld"). Change to "EccoMeld".
- `~/Projects/EccoMuse-Site/src/assets/images/EccoMuse_new_logo.png` — Source icon for adaptive icon generation.

### Project Constraints
- `.planning/PROJECT.md` — Constraints section defines WebView-first approach, submodule strategy, and package name policy.
- `.planning/REQUIREMENTS.md` — INFRA-01 through INFRA-04 are the requirements for this phase.
- `.planning/STATE.md` — Documents the crossorigin bug concern (Next.js #61210) and `addJavascriptInterface` decision.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `SpotifyLoginScreen.kt` / `LoginScreen.kt` / `DiscordLoginScreen.kt` — Three existing WebView implementations showing the codebase's WebView patterns (WebViewClient, cookie management, JS interface)
- `App.kt` — Application class with `@HiltAndroidApp`, Timber init, image loader setup. WebView singleton would be initialized here or via Hilt module.
- `di/AppModule.kt` — Hilt singleton providers for database, cache, coroutine scope. WebView provider follows this pattern.

### Established Patterns
- Application-scoped singletons via `@Singleton @Provides` in `AppModule.kt`
- `@PlayerCache`, `@DownloadCache` custom qualifiers — could add `@BridgeWebView` qualifier
- Eager initialization in `App.onCreate()` for YouTube/Spotify/LastFM singletons

### Integration Points
- `App.kt` onCreate — where eager WebView initialization would happen
- `di/AppModule.kt` — where `@Singleton` WebView provider would be registered
- `app/build.gradle.kts` — where custom Gradle task for EccoPath build would be added
- `settings.gradle.kts` — no module addition needed (submodule is not a Gradle module)
- `app/src/main/assets/` — directory doesn't exist yet, needs creation for EccoPath static export

</code_context>

<specifics>
## Specific Ideas

- Icon source is from a separate project (EccoMuse-Site), not designed specifically for Android adaptive icons — will need foreground/background layer adaptation
- EccoPath's current `basePath: "/path"` config means all internal routes and asset references use `/path/` prefix — the Android export config must use `basePath: ""` to serve correctly from WebViewAssetLoader root

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 01-webview-foundation*
*Context gathered: 2026-04-04*
