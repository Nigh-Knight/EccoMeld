---
globs: "app/src/main/kotlin/com/metrolist/music/ui/**/*.kt"
---

# Compose UI Patterns

- Screen composables: `<Feature>Screen(navController: NavController)`
- ViewModels: `hiltViewModel<T>()` inside composable, observe via `collectAsState()`
- Preferences: `rememberPreference(key, default)` returning `MutableState<T>`
- Local state: `remember { mutableStateOf(...) }` and `rememberSaveable { ... }`
- Side effects: `LaunchedEffect`, `DisposableEffect`
- Theme: `MaterialTheme.colorScheme` — default accent `Color(0xFFED5564)`
- Insets: `LocalPlayerAwareWindowInsets` accounts for mini player
- Navigation: routes in `Screens` sealed class, args via `SavedStateHandle`
- Menus: bottom sheets in `ui/menu/` (`SongMenu`, `AlbumMenu`)
- Icons: use `painterResource(R.drawable.*)` — material-icons-extended NOT in deps
- Shimmer: `ShimmerHost` with `showGradient` for loading placeholders
- Long-press: use `combinedClickable` from `foundation` — `SuggestionChip` swallows gestures
