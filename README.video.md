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
| **Trim / Cut & Join** | Keep one section of a video (trim), or several sections stitched together in order — **or** turn on **Exclude sections** and mark the parts to cut out; the leftover is joined. Trim is the single-segment keep case. Ranges are chosen with millisecond precision: type start/end in **ms**, or drag the slider; labels and the preview clock use `m:ss.SSS`. Each *kept* section also has its own **playback speed** (0.25×–4×, slow-mo to fast-forward), applied to both audio and video. |
| **Merge** | Concatenate multiple videos into one. Each clip can be **reframed independently** (16:9, 1:1, 9:16, 4:3, or its original ratio) so mismatched sources line up. Clips with differing audio presence are reconciled via `experimentalSetForceAudioTrack`. |
| **Remove Sound** | Strip the audio track (`EditedMediaItem.setRemoveAudio`). |
| **Aspect Ratio** | Reframe to 16:9, 1:1, 9:16, or 4:3 (`Presentation.createForAspectRatio`, scale-to-fit-with-crop). |
| **Color Filter** | Apply a preset look to the whole video: B&W, Invert, Warm, Cool, Bright, Dark, or Punch (see [Color filters](#color-filters)). |
| **Overlay** | Stamp an **image/logo** *or* a line of **text** over every frame. Images can be **cropped** (reusing the photo cropper, shaped masks included) and **resized**; text has a color picker and size. Both choose one of **nine anchor positions** and an opacity (`OverlayEffect` + `BitmapOverlay` with overlay/background frame anchors). |
| **Images → Video (slideshow)** | Turn photos into a video: set **each image's on-screen duration** and pick from **~29 real transitions** at every boundary independently (dissolve, fades, wipes, pushes, covers/reveals, zoom, iris, blinds, checker, diagonal, rotate — see [Slideshow transitions](#slideshow-transitions)), with a shared transition length and an output aspect ratio. |

Every flow ends in **preview-before-save** in the **same top player**: Apply
replaces the original with the result (**Demo**). A toggle above the player
switches back to **Original** to keep editing (controls are locked in Demo).
Save is offered only in Demo; you can type a file name or leave it blank for
an auto name. Editing after switching to Original drops the stale result.

## Slideshow transitions

Media3 1.5.1 has **no native clip-to-clip transition API** (confirmed against
the shipped jars), so there's no overlapping-sequence crossfade to lean on. For
a **slideshow**, though, every neighbour is a *still image* — which makes the
whole problem tractable **without** a GL compositor.

`SlideshowComposer` **pre-renders** the transition: it cover-fits both images to
one output canvas and asks `TransitionRenderer` to draw each in-between frame on
a `Canvas` (blending both images per pixel), then splices those frames in as
short image clips. The result is a **true cross-dissolve** — both images visible
at once — and, because we own every pixel, a large family of effects at the cost
of a `when` branch each rather than a shader.

The timeline is a hybrid: each image is **one** steady clip for its full
duration minus `D/2` at each transitioned edge, followed by `round(D · fps)`
baked transition frames (fps 24, JPEG, ≤ 1280px long edge). `D` is clamped to
`min(transitionMs, ½ of each neighbour)` so a short image is never over-consumed.
Baked frames live in a cache dir that's cleared at the start of every compose.

`SlideTransition` (~29 values) covers: **Dissolve**; **Fade** through black /
white; **Wipe**, **Push**, **Cover**, **Reveal** in all four directions; **Zoom**
in/out; **Iris** open/close; horizontal / vertical **Blinds**; **Checker**;
diagonal wipes; and **Rotate**. Each boundary picks its own, so every one can
differ.

### Video-merge transitions (non-overlapping)

Videos can't be pre-rendered this way, so **merges** still use the
overlap-free approach: composition-wide, time-varying effects keyed on
composition-absolute boundary times (`buildTransitionEffects` in
`VideoTransformer`, driven by `VideoTransition`).

| Transition | Effect | Media3 interface |
| --- | --- | --- |
| **Fade** | dip RGB toward black over the window | `FadeTransitionsMatrix : RgbMatrix` |
| **Flash** | dip toward white (the alpha column adds a constant; frames are opaque) | `FadeTransitionsMatrix` |
| **Slide** | outgoing translates off left, incoming translates in from the right (NDC) | `GeometricTransitionsMatrix : MatrixTransformation` |
| **Zoom** | outgoing scales to a point at the centre, incoming grows back out | `GeometricTransitionsMatrix` |

These read as *motion through black* (no overlap), not a cross-dissolve — a real
cross-dissolve **between videos** would still need an overlapping-sequence
compositor or a two-texture `GlShaderProgram` (FFmpeg `xfade` the last resort).
The slideshow path above sidesteps that entirely by pre-rendering.

## Color filters

Each preset is one Media3 color effect (a shared, output-wide `Effect` applied
to every clip). `VideoColorFilter` is platform-neutral; the transformer maps it
to the GL effect:

| Look | Media3 effect |
| --- | --- |
| **B&W** | `RgbFilter.createGrayscaleFilter()` |
| **Invert** | `RgbFilter.createInvertedFilter()` |
| **Warm** / **Cool** | `RgbAdjustment` (scale red up / blue down, and vice-versa) |
| **Bright** / **Dark** | `Brightness(±)` |
| **Punch** | `Contrast(+)` |

## Speed & per-clip framing

Per-section speed uses `Effects.createExperimentalSpeedChangingEffect`, which
returns a paired `AudioProcessor` + video `Effect` so sound stays in sync with
the picture. Speed, the per-clip reframe (`Presentation`), and any color filter
are attached to each `EditedMediaItem` individually — the composition-wide
transforms (overlay, transitions) are then layered on top of every clip.

## Architecture (video)

The video suite is deliberately **independent** of image processing: it adds
only new files (plus its own `di/VideoModule`) and never touches the
image-processing code. It follows the same Clean Architecture + MVVM + Hilt
layering as the rest of the app. Saves go to `Movies/MomiWaterMarker`.

```
presentation/video/
  VideoEditorScreen.kt     Op picker + per-op flows + preview-before-save,
                           overlay cropper host
  VideoEditorViewModel.kt  StateFlow<VideoEditorUiState> + effects channel
  VideoEditorUiState.kt    VideoOp, AspectRatioOption, OverlayMode, SlideItem,
                           UI state
  VideoComponents.kt       ExoPlayer preview (m:ss.SSS clock) via AndroidView
  Timecode.kt              Millisecond timecode formatting

domain/
  model/                   VideoClip, VideoSegment, VideoEditRequest, TrimRange
                           (with speed), VideoTransition (merge), SlideTransition
                           (slideshow, ~29), VideoColorFilter, OverlayPosition,
                           CropShape/NormalizedRect
  repository/              VideoRepository (abstraction; export + createSlideshow)
  usecase/                 CutAndJoinVideo, MergeVideos, RemoveAudio,
                           ChangeAspectRatio, ApplyVideoFilter, OverlayImage,
                           CreateSlideshow, GetVideoDuration, SaveVideo

data/
  video/                   VideoTransformer (Media3 Transformer wrapper) +
                           FadeTransitionsMatrix, GeometricTransitionsMatrix,
                           ColorFilterKind, ConstantSpeedProvider;
                           SlideshowComposer + TransitionRenderer (pre-rendered
                           slideshow transitions)
  storage/                 VideoStorage (probe, decode, MediaStore, FileProvider,
                           text-overlay render, overlay-bitmap hardening,
                           slideshow-frame baking)
  repository/              VideoRepositoryImpl

di/VideoModule.kt          Binds VideoRepository
```

`VideoTransformer` drives Media3 `Transformer` on the main thread (it requires a
`Looper`) and bridges the async listener into a suspending call, so repositories
stay on plain coroutines. Per-clip transforms (clipping, image duration, speed,
reframe) are set on each `EditedMediaItem`; the shared look (color filter,
overlay, transitions) is layered on top of every clip.

## Design notes / research

The full research log — engine choice (Media3 vs FFmpeg), feature feasibility,
the milestone plan, and the transition implementation write-up — lives in
[`docs/video-editing.md`](docs/video-editing.md).

## Build & test

```bash
./gradlew :app:assembleDebug        # build the debug APK (both flows)
./gradlew :app:installDebug         # install on a connected device
```
