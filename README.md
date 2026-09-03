# MomiWaterMarker

An Android media studio with two independent flows, chosen from a launch screen:
**Image Processing** (watermarking + photo edits) and **Video Processing** (a
Media3-powered video-editing suite). Both preview the result before saving to
the gallery.

## Image features

- Source images from the system **Photo Picker** (no storage permission) or the
  **Camera** (via `FileProvider`, no camera permission required).
- Predefined watermark **patterns**: Center, four corners, Tiled, and Diagonal.
- Editable watermark **text**, **color** (preset palette), and **font**.
- Adjustable **opacity** and **text size** (resolution-independent).
- Live, pixel-accurate preview rendered by the real engine.
- One-tap **save to gallery** (MediaStore → `Pictures/MomiWaterMarker`).

## Video features

Every operation is a distinct flow that funnels into one export pipeline
(`VideoEditRequest` → Media3 `Composition`). Pick a source, configure the op,
**preview the result**, then save.

- **Trim** — keep one section of a video.
- **Cut & Join** — keep several sections of one video and stitch them in order.
- **Merge** — concatenate multiple videos into one (audio tracks reconciled via
  `experimentalSetForceAudioTrack`).
- **Remove Sound** — strip the audio track.
- **Aspect Ratio** — reframe to 16:9, 1:1, 9:16, or 4:3.
- **Image Overlay** — stamp a logo/image over the video with adjustable opacity.
- **Images → Video (slideshow)** — turn photos into a video: set **each image's
  on-screen duration** and pick the **transition at every boundary
  independently** (each can differ), with a shared transition length and output
  aspect ratio.

### Transitions

Media3 1.5.1 has **no native clip-to-clip transition API**, so transitions are
rendered as composition-wide, time-varying effects keyed to each boundary's
absolute timestamp — no clip overlap needed:

- **Fade** / **Flash** — dip through black / white (`FadeTransitionsMatrix`, an
  `RgbMatrix`).
- **Slide** / **Zoom** — translate or scale the frame (`GeometricTransitionsMatrix`,
  a `MatrixTransformation`).

Because there's no overlap, slide/zoom reveal the background (motion *through*
black) rather than a true cross-dissolve — which, along with the full research
log, is tracked in [`docs/video-editing.md`](docs/video-editing.md).

## Roadmap / TODO

Candidate advanced image-processing features. Each would land as a new
`ImageOp` (or export option) with its own data-layer processor, slotting into
the existing pipeline — so they compose with crop, filters, adjustments, and
watermark.

- [ ] **Edge detection** (Sobel / Canny) — outline extraction.
- [ ] **Cartoonify** — edge-preserving smoothing + quantized colors + edge overlay.
- [ ] **Gaussian blur** — with an adjustable radius (plus box / motion blur).
- [ ] **Sharpen / unsharp mask** — edge enhancement.
- [ ] **Histogram analysis** — live RGB / luminance histogram, plus **auto
      contrast / histogram equalization** and levels.
- [ ] **Denoise** — median / bilateral noise reduction.
- [ ] **Vignette** — darkened / lightened edges.
- [ ] **Auto-enhance** — one-tap brightness / contrast / white-balance correction.
- [ ] **Perspective / skew correction** — straighten documents and buildings.
- [ ] **Background removal / replacement** — subject segmentation.
- [ ] **Grain / noise, duotone, gradient map** — additional stylized looks.

## Architecture

Clean Architecture + MVVM, with the dependency rule pointing inward
(presentation → domain ← data). Dependencies are wired with **Hilt**.

```
presentation/            UI (Jetpack Compose) + MVVM
  AppRootScreen.kt       Launch chooser: Image vs Video flow (AppSection)
  editor/
    EditorScreen.kt      Composables; Activity Result APIs for gallery/camera
    EditorViewModel.kt   StateFlow<EditorUiState> + one-shot effects channel
    EditorUiState.kt     Immutable UI state + EditorEffect
  video/
    VideoEditorScreen.kt   Op picker + per-op flows + preview-before-save
    VideoEditorViewModel.kt StateFlow<VideoEditorUiState> + effects channel
    VideoEditorUiState.kt  VideoOp, AspectRatioOption, SlideItem, UI state
    VideoComponents.kt     ExoPlayer preview (AndroidView)
  theme/                 Material 3 theme

domain/                  Pure Kotlin — no Android types
  model/                 Watermark* + VideoClip, VideoSegment, VideoEditRequest,
                         TrimRange, VideoTransition
  repository/            WatermarkRepository, MediaRepository, VideoRepository
  usecase/               Image: ApplyWatermark, SaveWatermarkedImage, …
                         Video: Trim, CutAndJoin, MergeVideos, RemoveAudio,
                         ChangeAspectRatio, OverlayImage, CreateSlideshow,
                         GetVideoDuration, SaveVideo
  util/                  Outcome<T> result type

data/                    Framework implementations
  rendering/             WatermarkRenderer (Canvas engine), TypefaceProvider
  video/                 VideoTransformer (Media3 export engine) +
                         FadeTransitionsMatrix, GeometricTransitionsMatrix (effects)
  storage/               ImageStorage, VideoStorage (decode, MediaStore, FileProvider)
  repository/            WatermarkRepositoryImpl, MediaRepositoryImpl, VideoRepositoryImpl

di/                      Hilt modules (incl. VideoModule) + @IoDispatcher qualifier
```

The video suite is deliberately **independent** of image processing: it adds
only new files (plus its own `di/VideoModule`) and never touches the
image-processing code.

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
