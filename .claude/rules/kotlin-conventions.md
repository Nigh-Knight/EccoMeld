---
globs: "**/*.kt"
---

# Kotlin Conventions

- PascalCase for files, classes, composables. camelCase for functions, variables
- Extension files: `*Ext.kt`. Entity files: `*Entity.kt`
- API clients are `object` singletons returning `Result<T>` with `.onSuccess{}.onFailure{}` chaining
- `@Volatile` for thread-safe mutable properties on singletons
- Coroutines: `Dispatchers.IO` for network/DB, `Main` for UI, `Default` for CPU. Always explicit
- `MutableStateFlow` for ViewModel state. `stateIn()` for DB Flow→StateFlow
- Conversion functions: `to<Target>()` pattern (`toSongEntity()`, `toMediaMetadata()`)
- Prefix `local` for operations that skip remote sync (`localToggleLike()`)
- Preference keys: PascalCase + `Key` suffix as top-level vals
- Constants: UPPER_SNAKE_CASE in companion objects
- JSON: `kotlinx.serialization` with `Json { ignoreUnknownKeys = true }`
- Logging: `Timber.tag("Tag").d()` — never `Log.d()` or `println()`
