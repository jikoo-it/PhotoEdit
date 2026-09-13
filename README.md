# Momi Studio

An Android image and video processing app. From a launch screen you pick a flow,
edit your media with a live preview, and save the result back to your gallery.

## Flows

The app is organized into separate flows, each documented in its own README:

- **[Bulk Image Processing](README.image.md)** — apply one edit stack (crop,
  transform, resize, aspect-ratio padding, filters, adjustments, pixelate,
  frame, watermark, compress) identically to a whole batch of images in a
  single pass. Resize, aspect ratio, and compress (quality, target KB, or
  per-image **[fit to file size](README.resize.md)**) live in the editor tool
  row.
- **[Single Image Processing](README.studio.md)** — one photo, one layer stack:
  cut-out (auto outline you can drag before confirming, or trace by hand),
  portrait look (subject in color, rest black and white), background blur
  (subject sharp, rest blurred and still in color), and backdrops (original,
  transparent, color, or another image).
- **[Video Processing](README.video.md)** — trim / cut & join (keep *or* exclude
  sections, with per-section speed), merge (per-clip orientation and framing), remove audio,
  change aspect ratio, color filters, image/text overlays, and an images-to-video
  slideshow with transitions.

Portrait-color and cut-out **processors** are documented in
[README.portrait.md](README.portrait.md) and [README.cutout.md](README.cutout.md);
the user-facing flow is Studio.

## Architecture

Clean Architecture + MVVM, with the dependency rule pointing inward
(presentation → domain ← data). Dependencies are wired with **Hilt**. Each flow
plugs into this shared layering; see the per-flow READMEs above for the
packages specific to each.

```
presentation/            UI (Jetpack Compose) + MVVM
  AppRootScreen.kt       Chooser between bulk-image, single-image, and video
  editor/                Bulk image editor     → README.image.md
  studio/                Layered single-image  → README.studio.md
  portrait/              Portrait ML processor (used by Studio; screen unused)
  cutout/                Cut-out ML processor  (used by Studio; screen unused)
  video/                 Video editor          → README.video.md
  theme/                 Material 3 theme
  settings/              Theme + about

domain/                  Pure Kotlin — no Android types
  model/                 ImageOp (sealed) + Pipeline, LayerDocument / Layer,
                         watermark & export models, CutoutRenderSpec, PortraitEffect,
                         video models
  repository/            Image / Media / Cutout / Portrait / Studio / Video
  usecase/               One use case per action
  util/                  Outcome<T> result type

data/                    Framework implementations
  rendering/             Per-op image processors + PipelineRenderer + LayerCompositor
                         + PathCutout + MaskContour + CutoutComposer
                         + PortraitEffectProcessor + BitmapBlur
  mlkit/                 SubjectSegmenter + PersonSegmenter (ML Kit wrappers)
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
