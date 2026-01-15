# AGENTS.md - Coding Agent Guidelines for BiliSleep

This file provides guidelines for AI coding agents working on the BiliSleep Android project.

## Build Commands

```bash
# Full debug build
./gradlew assembleDebug

# Full release build
./gradlew assembleRelease

# Clean build
./gradlew clean assembleDebug

# Check for compilation errors without building APK
./gradlew compileDebugKotlin

# Install on connected device
./gradlew installDebug
```

## Test Commands

```bash
# Run all unit tests
./gradlew test

# Run all unit tests for debug variant
./gradlew testDebugUnitTest

# Run a single test class
./gradlew testDebugUnitTest --tests "org.syndim.bilisleep.ExampleUnitTest"

# Run a single test method
./gradlew testDebugUnitTest --tests "org.syndim.bilisleep.ExampleUnitTest.testMethod"

# Run instrumented tests (requires device/emulator)
./gradlew connectedDebugAndroidTest

# Run single instrumented test
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=org.syndim.bilisleep.ExampleInstrumentedTest
```

## Lint Commands

```bash
# Run Android lint
./gradlew lintDebug

# Run lint and generate HTML report
./gradlew lintDebug --continue
# Report at: app/build/reports/lint-results-debug.html
```

## Project Structure

```
app/src/main/java/org/syndim/bilisleep/
├── data/           # Data layer (API, models, repositories)
├── di/             # Hilt dependency injection modules
├── player/         # Audio player management
├── service/        # Android services (MediaPlaybackService)
├── ui/             # Jetpack Compose UI (components, screens, theme, navigation)
└── viewmodel/      # ViewModels for business logic
```

## Code Style Guidelines

### Kotlin Conventions

- **Language version**: Kotlin 2.0+
- **JVM target**: Java 17
- **Null safety**: Leverage Kotlin's null safety; avoid `!!` operator
- **Immutability**: Prefer `val` over `var`; use immutable collections

### Naming Conventions

| Element | Convention | Example |
|---------|------------|---------|
| Classes | PascalCase | `SearchViewModel`, `BiliRepository` |
| Functions | camelCase | `searchVideos()`, `playNext()` |
| Properties | camelCase | `playerState`, `currentIndex` |
| Constants | SCREAMING_SNAKE_CASE | `BASE_URL`, `DEFAULT_USER_AGENT` |
| Composables | PascalCase | `PlayerScreen`, `MiniPlayer` |
| Private backing fields | Prefix with `_` | `_uiState`, `_playerState` |
| StateFlow exposure | Use `.asStateFlow()` | `val uiState = _uiState.asStateFlow()` |

### Import Organization

```kotlin
// 1. Android/AndroidX imports
import android.content.Context
import androidx.compose.runtime.*

// 2. Project imports
import org.syndim.bilisleep.data.model.PlayerState
import org.syndim.bilisleep.viewmodel.PlayerViewModel

// 3. Third-party imports
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow

// 4. Java imports
import javax.inject.Inject
```

- Use wildcard imports for Compose: `import androidx.compose.foundation.layout.*`
- Avoid wildcard imports for project classes

### Compose UI Guidelines

```kotlin
@Composable
fun MyComponent(
    state: SomeState,              // Required params first
    onAction: () -> Unit,          // Callbacks
    modifier: Modifier = Modifier  // Modifier always last with default
) {
    // Implementation
}
```

- Always accept `Modifier` as last parameter with default value
- Use `remember` for expensive computations
- Use `LaunchedEffect` for side effects
- Prefer `collectAsState()` for StateFlow in Compose

### Error Handling

```kotlin
// Use Kotlin Result<T> for repository methods
suspend fun fetchData(): Result<Data> = withContext(Dispatchers.IO) {
    try {
        val response = api.getData()
        if (response.code == 0 && response.data != null) {
            Result.success(response.data)
        } else {
            Result.failure(Exception(response.message))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
}

// Handle with fold()
result.fold(
    onSuccess = { data -> /* use data */ },
    onFailure = { error -> /* handle error */ }
)
```

### Dependency Injection (Hilt)

```kotlin
// ViewModels
@HiltViewModel
class MyViewModel @Inject constructor(
    private val repository: MyRepository
) : ViewModel()

// Repositories/Singletons
@Singleton
class MyRepository @Inject constructor(
    private val apiService: ApiService
)

// Modules
@Module
@InstallIn(SingletonComponent::class)
object MyModule {
    @Provides
    @Singleton
    fun provideService(): MyService = ...
}
```

### Coroutines & Flow

- Use `viewModelScope` in ViewModels
- Use `withContext(Dispatchers.IO)` for IO operations in repositories
- Expose state as `StateFlow`, not `MutableStateFlow`
- Use `SharingStarted.WhileSubscribed(5000)` for shared flows

### Media3/ExoPlayer

- Use `@OptIn(UnstableApi::class)` for unstable Media3 APIs
- Always include Referer header for Bilibili CDN requests
- Handle audio focus and becoming noisy events

## File Templates

### New ViewModel
```kotlin
package org.syndim.bilisleep.viewmodel

@HiltViewModel
class NewViewModel @Inject constructor(
    private val repository: SomeRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow<UiState>(UiState.Initial)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()
}
```

### New Composable Screen
```kotlin
package org.syndim.bilisleep.ui.screens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewScreen(
    onNavigateBack: () -> Unit,
    viewModel: NewViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    Scaffold(
        topBar = { TopAppBar(title = { Text("Title") }) }
    ) { paddingValues ->
        // Content
    }
}
```

### New Repository
```kotlin
package org.syndim.bilisleep.data.repository

@Singleton
class NewRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun doSomething(): Result<Data> = withContext(Dispatchers.IO) {
        // Implementation
    }
}
```

## Common Patterns

### Sealed Classes for UI State
```kotlin
sealed class UiState {
    object Initial : UiState()
    object Loading : UiState()
    data class Success(val data: Data) : UiState()
    data class Error(val message: String) : UiState()
}
```

### DataStore for Preferences
```kotlin
private val Context.dataStore by preferencesDataStore(name = "prefs")

val setting: Flow<String> = context.dataStore.data.map { prefs ->
    prefs[KEY] ?: "default"
}

suspend fun saveSetting(value: String) {
    context.dataStore.edit { prefs -> prefs[KEY] = value }
}
```

## Important Notes

- Package: `org.syndim.bilisleep`
- Min SDK: 26 (Android 8.0)
- Target SDK: 34 (Android 14)
- Compose BOM: 2024.02.00
- Always run `./gradlew assembleDebug` after changes to verify compilation
