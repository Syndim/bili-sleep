# BiliSleep

An Android app that lets you search Bilibili videos and play them as audio with a sleep timer feature - perfect for falling asleep to your favorite content.

## Features

- **Search Bilibili Videos**: Search for any content on Bilibili and browse results
- **Audio-Only Playback**: Extract and play only the audio track from videos
- **Sleep Timer**: Set a timer to automatically pause playback
  - Preset durations: 15, 30, 45, 60, 90, 120 minutes
  - Gradual fade-out option
  - Add time with a single tap
- **Playlist Support**: Play multiple videos in sequence
- **Auto-Play Next**: When current audio ends before sleep timer, automatically plays the next item
- **Mini Player**: Control playback from any screen

## Architecture

The app follows MVVM architecture with:

- **Jetpack Compose** for UI
- **Hilt** for dependency injection
- **ExoPlayer (Media3)** for audio playback
- **Retrofit** for API calls
- **Coroutines & Flow** for async operations

## Project Structure

```
com.bilisleep/
├── data/
│   ├── api/          # Bilibili API service
│   ├── model/        # Data models
│   └── repository/   # Repository layer
├── di/               # Hilt modules
├── player/           # Audio player manager
├── ui/
│   ├── components/   # Reusable UI components
│   ├── navigation/   # Navigation setup
│   ├── screens/      # Screen composables
│   └── theme/        # Material theme
└── viewmodel/        # ViewModels
```

## Building

1. Open the project in Android Studio
2. Sync Gradle files
3. Build and run on device or emulator

## Requirements

- Android SDK 26+ (Android 8.0)
- Android Studio Hedgehog or newer
- JDK 17

## Note

This app uses the Bilibili public API. Some features may require authentication for full functionality. The app is intended for personal use only.

## License

MIT License
