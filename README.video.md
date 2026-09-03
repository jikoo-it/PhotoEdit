# MomiWaterMarker — Video Processing

The **Video Processing** flow (chosen from the app's launch screen; see the
image side in [README.md](README.md)) is a Media3-powered video editor. Every
operation is a distinct flow that funnels into one export pipeline
(`VideoEditRequest` → `VideoTransformer.ExportSpec` → a Media3 `Composition`),
so adding an operation means building a different request, **not** a new
pipeline. Pick a source, configure the op, **preview the result**, then save to
the gallery.

## Operations

| Op | What it does |
| --- | --- |
| **Trim** | Keep one section of a video. |
| **Cut & Join** | Keep several sections of one video and stitch them together in order. |
| **Merge** | Concatenate multiple videos into one. Clips with differing audio presence are reconciled via `experimentalSetForceAudioTrack`. |
| **Remove Sound** | Strip the audio track (`EditedMediaItem.setRemoveAudio`). |
| **Aspect Ratio** | Reframe to 16:9, 1:1, 9:16, or 4:3 (`Presentation.createForAspectRatio`, scale-to-fit-with-crop). |
| **Image Overlay** | Stamp a logo/image over every frame with adjustable opacity (`OverlayEffect` + `BitmapOverlay`). |
| **Images → Video (slideshow)** | Turn photos into a video: set **each image's on-screen duration** and choose the **transition at every boundary independently** (each can differ), with a shared transition length and an output aspect ratio. |

Every flow ends in **preview-before-save**: the export runs, the result plays in
an ExoPlayer preview, and only then is "Save to gallery" offered. Editing any
control after a preview invalidates the stale result so it can't be saved.

## Transitions

Media3 1.5.1 has **no native clip-to-clip transition API** (confirmed against
the shipped jars). Rather than block on a custom compositor, transitions are
rendered as **composition-wide, time-varying effects** that need no clip
overlap: the effect sees composition-absolute presentation times, and each
boundary's absolute timestamp is computed from the clip layout
(`buildTransitionEffects` in `VideoTransformer`). Each boundary carries its own
`VideoTransition`, so every one can differ.

| Transition | Effect | Media3 interface |
| --- | --- | --- |
| **Fade** | dip RGB toward black over the window | `FadeTransitionsMatrix : RgbMatrix` |
| **Flash** | dip toward white (the alpha column adds a constant; frames are opaque) | `FadeTransitionsMatrix` |
| **Slide** | outgoing translates off left, incoming translates in from the right (NDC) | `GeometricTransitionsMatrix : MatrixTransformation` |
| **Zoom** | outgoing scales to a point at the centre, incoming grows back out | `GeometricTransitionsMatrix` |

Each boundary's half-width is clamped to `min(transitionMs, shorter neighbour)`
so a short image can't be dimmed/moved end-to-end.

**Known limitation:** because there's no overlap, slide/zoom reveal the (black)
background — they read as *motion through black* rather than a true
cross-dissolve. A real cross-dissolve (both clips visible at once) needs an
overlapping-sequence compositor or a two-texture `GlShaderProgram`; FFmpeg
`xfade` is the last-resort fallback. Tracked, not yet built.

## Architecture (video)

The video suite is deliberately **independent** of image processing: it adds
only new files (plus its own `di/VideoModule`) and never touches the
image-processing code. It follows the same Clean Architecture + MVVM + Hilt
layering as the rest of the app.

```
presentation/video/
  VideoEditorScreen.kt     Op picker + per-op flows + preview-before-save
  VideoEditorViewModel.kt  StateFlow<VideoEditorUiState> + effects channel
  VideoEditorUiState.kt    VideoOp, AspectRatioOption, SlideItem, UI state
  VideoComponents.kt       ExoPlayer preview embedded via AndroidView

domain/
  model/                   VideoClip, VideoSegment, VideoEditRequest, TrimRange,
                           VideoTransition
  repository/              VideoRepository (abstraction)
  usecase/                 TrimVideo, CutAndJoinVideo, MergeVideos, RemoveAudio,
                           ChangeAspectRatio, OverlayImage, CreateSlideshow,
                           GetVideoDuration, SaveVideo

data/
  video/                   VideoTransformer (Media3 Transformer wrapper) +
                           FadeTransitionsMatrix, GeometricTransitionsMatrix
  storage/                 VideoStorage (probe, decode, MediaStore, FileProvider)
  repository/              VideoRepositoryImpl

di/VideoModule.kt          Binds VideoRepository
```

`VideoTransformer` drives Media3 `Transformer` on the main thread (it requires a
`Looper`) and bridges the async listener into a suspending call, so repositories
stay on plain coroutines. All output-wide transforms (aspect ratio, overlay,
transitions) are applied as composition video effects; per-clip clipping and
image durations are set on each `EditedMediaItem`.

## Design notes / research

The full research log — engine choice (Media3 vs FFmpeg), feature feasibility,
the milestone plan, and the transition implementation write-up — lives in
[`docs/video-editing.md`](docs/video-editing.md).

## Build & test

```bash
./gradlew :app:assembleDebug        # build the debug APK (both flows)
./gradlew :app:installDebug         # install on a connected device
```
