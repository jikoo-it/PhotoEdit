# MomiWaterMarker

An Android app for adding text watermarks to photos. Pick an image from the
**gallery** or **camera**, choose a watermark **pattern**, **text**, **color**,
and **font**, preview it live, and save the result to your gallery.

## Features

- Source images from the system **Photo Picker** (no storage permission) or the
  **Camera** (via `FileProvider`, no camera permission required).
- Predefined watermark **patterns**: Center, four corners, Tiled, and Diagonal.
- Editable watermark **text**, **color** (preset palette), and **font**.
- Adjustable **opacity** and **text size** (resolution-independent).
- Live, pixel-accurate preview rendered by the real engine.
- One-tap **save to gallery** (MediaStore → `Pictures/MomiWaterMarker`).

## Architecture

Clean Architecture + MVVM, with the dependency rule pointing inward
(presentation → domain ← data). Dependencies are wired with **Hilt**.

```
presentation/            UI (Jetpack Compose) + MVVM
  editor/
    EditorScreen.kt      Composables; Activity Result APIs for gallery/camera
    EditorViewModel.kt   StateFlow<EditorUiState> + one-shot effects channel
    EditorUiState.kt     Immutable UI state + EditorEffect
  theme/                 Material 3 theme

domain/                  Pure Kotlin — no Android types
  model/                 WatermarkConfig, WatermarkPattern, WatermarkFont, WatermarkImage
  repository/            WatermarkRepository, MediaRepository (abstractions)
  usecase/               ApplyWatermark, SaveWatermarkedImage, CreateCaptureDestination, GetWatermarkOptions
  util/                  Outcome<T> result type

data/                    Framework implementations
  rendering/             WatermarkRenderer (Canvas engine), TypefaceProvider
  storage/               ImageStorage (decode/EXIF, cache, MediaStore, FileProvider)
  repository/            WatermarkRepositoryImpl, MediaRepositoryImpl

di/                      Hilt modules + @IoDispatcher qualifier
```

### SOLID highlights

- **SRP** — the renderer only draws; storage only does I/O; the ViewModel only
  holds UI state and orchestrates use cases.
- **OCP** — new watermark patterns/fonts are added as enum cases handled in one
  `when`; new option sources plug in behind `GetWatermarkOptionsUseCase`.
- **DIP** — use cases depend on `WatermarkRepository` / `MediaRepository`
  interfaces (in `domain`), bound to implementations in `di/RepositoryModule`.
- **ISP** — image *acquisition/persistence* and *watermarking* are separate,
  focused repository interfaces.

## Build & test

```bash
./gradlew :app:assembleDebug        # build the debug APK
./gradlew :app:testDebugUnitTest    # run unit tests
```

## Toolchain notes

- AGP 9.x provides **built-in Kotlin** (bundled Kotlin 2.2.10), so the
  `kotlin-android` plugin is intentionally *not* applied — only the Compose
  compiler plugin (version-matched to Kotlin).
- KSP registers generated sources via the `kotlin.sourceSets` DSL, which
  built-in Kotlin blocks by default; `android.disallowKotlinSourceSets=false`
  in `gradle.properties` re-enables it (needed for Hilt).
