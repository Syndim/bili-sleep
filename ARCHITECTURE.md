# BiliSleep Architecture Documentation

This document provides a comprehensive overview of how the BiliSleep Android application is built, including the technologies, libraries, and architectural patterns used. It's designed to help developers who may not be familiar with Android development understand the structure and components of the app.

## Table of Contents

1. [Overview](#overview)
2. [Technology Stack](#technology-stack)
3. [Project Structure](#project-structure)
4. [Architecture Pattern](#architecture-pattern)
5. [Component Deep Dive](#component-deep-dive)
6. [Data Flow](#data-flow)
7. [Key Features Implementation](#key-features-implementation)
8. [Build System](#build-system)

---

## Overview

BiliSleep is an Android application that plays audio from Bilibili videos, designed for users who want to listen to content while falling asleep. The app features:

- Video search functionality
- Background audio playback
- System media controls (notification, lock screen)
- Sleep timer with fade-out
- Playlist management
- Persistent search history and preferences

---

## Technology Stack

### Core Platform

| Technology | Version | Purpose | Documentation |
|------------|---------|---------|---------------|
| **Kotlin** | 2.0.21 | Primary programming language | [kotlinlang.org](https://kotlinlang.org/) |
| **Android SDK** | 34 (Android 14) | Target platform | [developer.android.com](https://developer.android.com/) |
| **Minimum SDK** | 26 (Android 8.0) | Minimum supported version | - |

### UI Framework

| Library | Purpose | Documentation |
|---------|---------|---------------|
| **Jetpack Compose** | Modern declarative UI toolkit | [Compose Docs](https://developer.android.com/jetpack/compose) |
| **Material Design 3** | UI components and theming | [Material 3](https://m3.material.io/) |
| **Compose Navigation** | Screen navigation | [Navigation Compose](https://developer.android.com/jetpack/compose/navigation) |

### Dependency Injection

| Library | Purpose | Documentation |
|---------|---------|---------------|
| **Hilt** | Dependency injection framework | [Hilt Docs](https://dagger.dev/hilt/) |
| **Dagger** | Underlying DI framework | [Dagger Docs](https://dagger.dev/) |

### Networking

| Library | Purpose | Documentation |
|---------|---------|---------------|
| **Retrofit** | Type-safe HTTP client | [Retrofit](https://square.github.io/retrofit/) |
| **OkHttp** | HTTP client | [OkHttp](https://square.github.io/okhttp/) |
| **Gson** | JSON serialization | [Gson](https://github.com/google/gson) |

### Media Playback

| Library | Purpose | Documentation |
|---------|---------|---------------|
| **Media3 ExoPlayer** | Audio/video playback | [Media3](https://developer.android.com/guide/topics/media/media3) |
| **Media3 Session** | MediaSession for system controls | [Media3 Session](https://developer.android.com/guide/topics/media/media3/getting-started/mediasession) |

### Data Persistence

| Library | Purpose | Documentation |
|---------|---------|---------------|
| **DataStore** | Preferences storage | [DataStore](https://developer.android.com/topic/libraries/architecture/datastore) |

### Image Loading

| Library | Purpose | Documentation |
|---------|---------|---------------|
| **Coil** | Image loading for Compose | [Coil](https://coil-kt.github.io/coil/) |

### Asynchronous Programming

| Library | Purpose | Documentation |
|---------|---------|---------------|
| **Kotlin Coroutines** | Asynchronous programming | [Coroutines](https://kotlinlang.org/docs/coroutines-overview.html) |
| **Kotlin Flow** | Reactive streams | [Flow](https://kotlinlang.org/docs/flow.html) |

---

## Project Structure

```
app/src/main/java/org/syndim/bilisleep/
├── BiliSleepApplication.kt     # Application entry point
├── MainActivity.kt             # Main activity (single activity architecture)
│
├── data/                       # Data layer
│   ├── api/                    # Network API definitions
│   │   └── BiliApiService.kt   # Retrofit API interface
│   ├── model/                  # Data models
│   │   ├── AppModels.kt        # App-specific models
│   │   └── BiliModels.kt       # Bilibili API response models
│   └── repository/             # Data repositories
│       ├── BiliRepository.kt           # Bilibili data operations
│       ├── SearchHistoryRepository.kt  # Search history persistence
│       └── SleepTimerPreferencesRepository.kt  # Timer preferences
│
├── di/                         # Dependency injection
│   └── NetworkModule.kt        # Network-related dependencies
│
├── player/                     # Audio player
│   └── AudioPlayerManager.kt   # Player state and control
│
├── service/                    # Android services
│   └── MediaPlaybackService.kt # Background playback service
│
├── ui/                         # User interface
│   ├── components/             # Reusable UI components
│   │   ├── PlayerComponents.kt     # Mini player
│   │   ├── SleepTimerDialog.kt     # Timer dialog
│   │   └── VideoSearchItemCard.kt  # Search result card
│   ├── navigation/             # Navigation setup
│   │   └── NavHost.kt          # Navigation graph
│   ├── screens/                # Full screens
│   │   ├── PlayerScreen.kt     # Now playing screen
│   │   └── SearchScreen.kt     # Search screen
│   └── theme/                  # Visual theming
│       ├── Color.kt            # Color definitions
│       ├── Theme.kt            # Theme configuration
│       └── Type.kt             # Typography
│
└── viewmodel/                  # ViewModels
    ├── PlayerViewModel.kt      # Player business logic
    └── SearchViewModel.kt      # Search business logic
```

---

## Architecture Pattern

BiliSleep follows the **MVVM (Model-View-ViewModel)** architecture pattern recommended by Google for Android apps, combined with **Clean Architecture** principles.

### Layers

```
┌─────────────────────────────────────────────────────────────┐
│                        UI Layer                             │
│  ┌─────────────────┐  ┌─────────────────┐                  │
│  │    Screens      │  │   Components    │                  │
│  │  (Composables)  │  │  (Composables)  │                  │
│  └────────┬────────┘  └────────┬────────┘                  │
│           │                    │                            │
│           └────────┬───────────┘                            │
│                    ▼                                        │
│           ┌─────────────────┐                              │
│           │   ViewModels    │                              │
│           └────────┬────────┘                              │
└────────────────────┼────────────────────────────────────────┘
                     │
┌────────────────────┼────────────────────────────────────────┐
│                    ▼          Domain Layer                  │
│           ┌─────────────────┐                              │
│           │  Repositories   │                              │
│           └────────┬────────┘                              │
└────────────────────┼────────────────────────────────────────┘
                     │
┌────────────────────┼────────────────────────────────────────┐
│                    ▼          Data Layer                    │
│    ┌──────────────────┐    ┌──────────────────┐            │
│    │   API Service    │    │    DataStore     │            │
│    │   (Network)      │    │   (Local)        │            │
│    └──────────────────┘    └──────────────────┘            │
└─────────────────────────────────────────────────────────────┘
```

### Data Flow

1. **User Interaction** → UI Components detect user actions
2. **ViewModel** → Receives actions, processes business logic
3. **Repository** → Abstracts data sources, provides data
4. **Data Sources** → Network API or local storage
5. **State Flow** → Data flows back up via Kotlin Flow/StateFlow
6. **UI Update** → Compose automatically recomposes on state changes

---

## Component Deep Dive

### 1. Application Entry Point

#### `BiliSleepApplication.kt`
```kotlin
@HiltAndroidApp
class BiliSleepApplication : Application()
```

**Purpose**: Entry point for the Android application.

**Technology**: 
- `@HiltAndroidApp` annotation from [Hilt](https://dagger.dev/hilt/) triggers Hilt's code generation for dependency injection.
- Extends `Application`, which is the base class for maintaining global application state.

---

### 2. Main Activity

#### `MainActivity.kt`

**Purpose**: The single activity that hosts all UI content. Uses the "Single Activity Architecture" pattern where one activity hosts multiple Compose screens.

**Technologies Used**:
- **Jetpack Compose**: `setContent { }` replaces traditional XML layouts
- **Edge-to-Edge**: `enableEdgeToEdge()` allows content to draw behind system bars
- **Navigation**: `rememberNavController()` manages screen navigation
- **Hilt**: `hiltViewModel()` provides ViewModels with injected dependencies
- **Activity Result API**: `registerForActivityResult()` handles permission requests

**Key Responsibilities**:
- Hosts the Compose UI tree
- Requests notification permissions (Android 13+)
- Handles deep links from notification clicks
- Displays the mini player when appropriate

---

### 3. Data Layer

#### `data/api/BiliApiService.kt`

**Purpose**: Defines the HTTP API endpoints for Bilibili.

**Technology**: [Retrofit](https://square.github.io/retrofit/)

```kotlin
interface BiliApiService {
    @GET("x/web-interface/search/type")
    suspend fun searchVideo(
        @Query("keyword") keyword: String,
        // ...
    ): BiliResponse<SearchResult>
}
```

**How It Works**:
- Retrofit generates implementation at compile time
- `suspend` functions integrate with Kotlin Coroutines
- `@GET`, `@Query`, `@Header` annotations define HTTP request structure
- Return types are automatically parsed from JSON using Gson

---

#### `data/model/BiliModels.kt`

**Purpose**: Data classes representing Bilibili API responses.

**Technology**: Kotlin Data Classes + [Gson](https://github.com/google/gson)

```kotlin
data class BiliResponse<T>(
    val code: Int,
    val message: String,
    val data: T?
)
```

**Key Models**:
- `BiliResponse<T>`: Generic wrapper for all API responses
- `SearchResult`: Search results container
- `VideoSearchItem`: Individual video in search results
- `VideoInfo`: Detailed video information
- `PlayUrlResponse`: Audio/video stream URLs

---

#### `data/model/AppModels.kt`

**Purpose**: App-specific data models not tied to API.

**Key Models**:
- `PlaylistItem`: Represents a playable audio item
- `SleepTimerSettings`: Sleep timer configuration
- `PlayerState`: Current state of the audio player
- `SearchUiState`: Sealed class for search screen states

**Sealed Classes**: Used for representing finite states:
```kotlin
sealed class SearchUiState {
    object Initial : SearchUiState()
    object Loading : SearchUiState()
    data class Success(...) : SearchUiState()
    data class Error(...) : SearchUiState()
}
```

---

#### `data/repository/BiliRepository.kt`

**Purpose**: Abstracts data operations, providing a clean API to ViewModels.

**Pattern**: Repository Pattern

```kotlin
class BiliRepository @Inject constructor(
    private val apiService: BiliApiService
) {
    suspend fun searchVideos(query: String, page: Int): Result<SearchResult>
    suspend fun preparePlaylistItem(item: VideoSearchItem): Result<PlaylistItem>
}
```

**Key Concepts**:
- Uses Kotlin `Result<T>` for error handling
- Transforms API models to app models
- Handles network errors gracefully

---

#### `data/repository/SearchHistoryRepository.kt`

**Purpose**: Persists search history locally.

**Technology**: [DataStore Preferences](https://developer.android.com/topic/libraries/architecture/datastore)

```kotlin
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "search_history")
```

**How It Works**:
- DataStore is a modern replacement for SharedPreferences
- Uses Kotlin Flow for reactive data access
- Data persists across app restarts
- Thread-safe and non-blocking

---

#### `data/repository/SleepTimerPreferencesRepository.kt`

**Purpose**: Persists sleep timer settings.

**Features**:
- Remembers last used timer duration
- Persists fade-out preferences
- Loads saved values on app startup

---

### 4. Dependency Injection

#### `di/NetworkModule.kt`

**Purpose**: Provides network-related dependencies.

**Technology**: [Hilt](https://dagger.dev/hilt/) / [Dagger](https://dagger.dev/)

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient { ... }
    
    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit { ... }
    
    @Provides
    @Singleton
    fun provideBiliApiService(retrofit: Retrofit): BiliApiService { ... }
}
```

**Key Concepts**:
- `@Module`: Declares this as a Hilt module
- `@InstallIn(SingletonComponent::class)`: Dependencies live for app lifetime
- `@Provides`: Method provides a dependency
- `@Singleton`: Only one instance is created
- Dependencies are automatically injected where needed

---

### 5. ViewModels

#### `viewmodel/SearchViewModel.kt`

**Purpose**: Manages search screen state and logic.

**Technology**: [ViewModel](https://developer.android.com/topic/libraries/architecture/viewmodel) + [Kotlin Flow](https://kotlinlang.org/docs/flow.html)

```kotlin
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: BiliRepository,
    private val searchHistoryRepository: SearchHistoryRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Initial)
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()
    
    fun search(query: String) {
        viewModelScope.launch {
            // Perform search
        }
    }
}
```

**Key Concepts**:
- `@HiltViewModel`: Enables Hilt injection
- `StateFlow`: Observable state holder
- `viewModelScope`: Coroutine scope tied to ViewModel lifecycle
- Survives configuration changes (screen rotation)

---

#### `viewmodel/PlayerViewModel.kt`

**Purpose**: Manages playback state and controls.

**Key Responsibilities**:
- Starting/stopping playback
- Playlist management
- Sleep timer control
- Coordinating with AudioPlayerManager

---

### 6. Audio Playback

#### `player/AudioPlayerManager.kt`

**Purpose**: Manages audio playback via MediaController.

**Technology**: [Media3 ExoPlayer](https://developer.android.com/guide/topics/media/media3)

```kotlin
@Singleton
class AudioPlayerManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var mediaController: MediaController? = null
    
    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()
}
```

**Key Features**:
- Connects to MediaPlaybackService via MediaController
- Exposes player state as StateFlow
- Manages sleep timer with fade-out
- Handles playlist operations

---

#### `service/MediaPlaybackService.kt`

**Purpose**: Background service for audio playback with system integration.

**Technology**: [Media3 Session](https://developer.android.com/guide/topics/media/media3/getting-started/mediasession)

```kotlin
class MediaPlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null
}
```

**Key Features**:
- Runs in foreground with notification
- Provides media controls in notification/lock screen
- Handles audio focus
- Pauses when headphones disconnected
- Continues playback when app is in background

**System Integration**:
- Notification with play/pause/skip controls
- Lock screen media controls
- Bluetooth/headphone button support
- Google Assistant integration

---

### 7. User Interface

#### UI Components (`ui/components/`)

##### `PlayerComponents.kt` - Mini Player
```kotlin
@Composable
fun MiniPlayer(
    playerState: PlayerState,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    onClick: () -> Unit
)
```
A compact player bar shown at the bottom of the screen.

##### `SleepTimerDialog.kt` - Timer Dialog
```kotlin
@Composable
fun SleepTimerDialog(
    currentSettings: SleepTimerSettings,
    onStartTimer: (Int) -> Unit,
    onDismiss: () -> Unit
)
```
Modal dialog for setting sleep timer duration.

##### `VideoSearchItemCard.kt` - Search Result Card
```kotlin
@Composable
fun VideoSearchItemCard(
    item: VideoSearchItem,
    onClick: () -> Unit
)
```
Card displaying video thumbnail, title, and metadata.

**Technology**: [Jetpack Compose](https://developer.android.com/jetpack/compose)

**Key Compose Concepts**:
- `@Composable`: Marks a function as a UI component
- Declarative UI: Describe what UI should look like, not how to build it
- Recomposition: UI automatically updates when state changes

---

#### Screens (`ui/screens/`)

##### `SearchScreen.kt`
The main search interface with:
- Search bar with keyboard actions
- Search history (when no results)
- Infinite scrolling results list
- Loading and error states

##### `PlayerScreen.kt`
The full-screen player with:
- Album artwork
- Title and artist
- Progress slider
- Playback controls (previous, play/pause, next)
- "Up Next" playlist
- Sleep timer access

---

#### Navigation (`ui/navigation/NavHost.kt`)

**Purpose**: Defines the navigation graph.

**Technology**: [Navigation Compose](https://developer.android.com/jetpack/compose/navigation)

```kotlin
sealed class Screen(val route: String) {
    object Search : Screen("search")
    object Player : Screen("player")
}

@Composable
fun BiliSleepNavHost(
    navController: NavHostController,
    onPlayFromPlaylist: (List<VideoSearchItem>, Int) -> Unit
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Search.route
    ) {
        composable(Screen.Search.route) { SearchScreen(...) }
        composable(Screen.Player.route) { PlayerScreen(...) }
    }
}
```

---

#### Theming (`ui/theme/`)

**Technology**: [Material 3](https://m3.material.io/)

- `Color.kt`: Defines color palette
- `Type.kt`: Typography styles
- `Theme.kt`: Combines colors, typography, and shapes

```kotlin
@Composable
fun BiliSleepTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
```

---

## Data Flow

### Search Flow

```
User types query
       │
       ▼
SearchScreen (Composable)
       │
       ▼ onSearch()
SearchViewModel
       │
       ▼ searchVideos()
BiliRepository
       │
       ▼ HTTP GET
BiliApiService (Retrofit)
       │
       ▼ JSON Response
Parse to SearchResult
       │
       ▼ Update StateFlow
SearchViewModel._uiState
       │
       ▼ Collect StateFlow
SearchScreen recomposes with new data
```

### Playback Flow

```
User clicks video
       │
       ▼
SearchScreen.onPlayFromPlaylist()
       │
       ▼
PlayerViewModel.playPlaylist()
       │
       ▼ preparePlaylistItem()
BiliRepository (fetches audio URL)
       │
       ▼
AudioPlayerManager.setPlaylist()
       │
       ▼ MediaController
MediaPlaybackService
       │
       ▼ ExoPlayer
Audio plays + Notification appears
       │
       ▼ Player.Listener
AudioPlayerManager updates StateFlow
       │
       ▼
PlayerScreen recomposes with playback state
```

---

## Key Features Implementation

### 1. Background Playback

**Implementation**: `MediaPlaybackService` extends `MediaSessionService`

**How it works**:
1. Service runs in foreground with notification
2. ExoPlayer handles actual audio playback
3. MediaSession provides system integration
4. Service survives app being minimized/closed

### 2. Sleep Timer

**Implementation**: `AudioPlayerManager` + `SleepTimerPreferencesRepository`

**How it works**:
1. User selects duration in `SleepTimerDialog`
2. Coroutine counts down remaining time
3. Optional fade-out gradually reduces volume
4. Playback pauses when timer ends
5. Last used duration is persisted

### 3. Notification Click

**Implementation**: `PendingIntent` in `MediaPlaybackService`

**How it works**:
1. Service creates PendingIntent pointing to MainActivity
2. Intent includes `EXTRA_OPEN_PLAYER` flag
3. MainActivity checks for flag in `onCreate`/`onNewIntent`
4. If flag present, navigates to PlayerScreen

### 4. Async Playlist Loading

**Implementation**: `PlayerViewModel.playPlaylist()`

**How it works**:
1. Immediately prepare and play clicked item
2. Launch background coroutine for remaining items
3. Append items to playlist as they're prepared
4. User sees instant playback, playlist builds progressively

---

## Build System

### Gradle Configuration

The project uses **Kotlin DSL** for Gradle build scripts (`.kts` files).

#### `settings.gradle.kts`
- Configures plugin repositories
- Defines project name
- Uses Aliyun Maven mirrors for faster downloads in China

#### `build.gradle.kts` (Project level)
- Defines plugin versions
- Configures Kotlin and Android Gradle plugins

#### `app/build.gradle.kts`
- App-specific configuration
- Dependencies declaration
- Android SDK versions
- Build features (Compose enabled)

### Key Build Features

```kotlin
android {
    compileSdk = 34
    
    defaultConfig {
        minSdk = 26
        targetSdk = 34
    }
    
    buildFeatures {
        compose = true
    }
}
```

### Dependency Categories

1. **Core Android**: `androidx.core`, `androidx.lifecycle`
2. **Compose**: BOM (Bill of Materials) for version alignment
3. **Navigation**: `androidx.navigation:navigation-compose`
4. **DI**: `hilt-android`, `hilt-compiler`
5. **Network**: `retrofit2`, `okhttp3`
6. **Media**: `media3-exoplayer`, `media3-session`
7. **Storage**: `datastore-preferences`
8. **Image**: `coil-compose`

---

## Permissions

Declared in `AndroidManifest.xml`:

| Permission | Purpose |
|------------|---------|
| `INTERNET` | Network access for API calls |
| `FOREGROUND_SERVICE` | Background playback |
| `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Media-specific foreground service (Android 14+) |
| `WAKE_LOCK` | Keep device awake during playback |
| `POST_NOTIFICATIONS` | Show media notification (Android 13+) |

---

## Further Reading

- [Android Developer Documentation](https://developer.android.com/)
- [Jetpack Compose](https://developer.android.com/jetpack/compose)
- [Kotlin Coroutines](https://kotlinlang.org/docs/coroutines-overview.html)
- [Hilt Dependency Injection](https://dagger.dev/hilt/)
- [Media3 ExoPlayer](https://developer.android.com/guide/topics/media/media3)
- [Material Design 3](https://m3.material.io/)
