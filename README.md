# MomiWaterMarker

An Android image and video processing app. From a launch screen you pick a flow,
edit your media with a live preview, and save the result back to your gallery.

## Flows

The app is organized into separate flows, each documented in its own README:

- **[Bulk Image Processing](README.image.md)** — apply one edit stack (crop,
  transform, resize, filters, adjustments, pixelate, frame, watermark, export)
  identically to a whole batch of images in a single pass.
- **Single Image Processing** — *forthcoming.* A separate flow focused on
  editing one image at a time, with tools that only make sense on a single
  photo. Will be documented as it lands.
- **[Video Processing](README.video.md)** — trim, cut & join, merge, remove
  audio, change aspect ratio, image overlay, and an images-to-video slideshow
  with transitions.

## Architecture

Clean Architecture + MVVM, with the dependency rule pointing inward
(presentation → domain ← data). Dependencies are wired with **Hilt**. Each flow
plugs into this shared layering; see the per-flow READMEs above for the
packages specific to each.

```
presentation/            UI (Jetpack Compose) + MVVM
  AppRootScreen.kt       Chooser between the image and video flows
  editor/                Image editor          → README.image.md
  video/                 Video editor          → README.video.md
  theme/                 Material 3 theme

domain/                  Pure Kotlin — no Android types
  model/                 ImageOp (sealed) + Pipeline, watermark & export models,
                         video models
  repository/            Image / Media / Video repositories (abstractions)
  usecase/               One use case per action (image + video)
  util/                  Outcome<T> result type

data/                    Framework implementations
  rendering/             Per-op image processors + PipelineRenderer
  storage/               ImageStorage (decode/EXIF, cache, MediaStore, FileProvider)
  video/                 Video processing (see README.video.md)
  repository/            Repository implementations

di/                      Hilt modules + @IoDispatcher qualifier
```

### SOLID highlights

- **SRP** — each processor does one transformation; storage only does I/O; the
  ViewModel only holds UI state and orchestrates use cases.
- **OCP** — a new edit is a new `ImageOp` case plus its processor; the sealed
  hierarchy makes the renderer and UI fail to compile until it is handled
  everywhere. New filters/patterns/fonts are added as enum cases in one `when`.
- **DIP** — use cases depend on repository *interfaces* (in `domain`), bound to
  implementations in `di`.
- **ISP** — image acquisition/persistence, watermarking, and video editing are
  separate, focused repository interfaces.

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
