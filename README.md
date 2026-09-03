# MomiWaterMarker

An Android image (and video) processing app. Pick one or many photos from the
**gallery** or **camera**, apply a stack of edits — crop, transform, resize,
filters, adjustments, pixelate, frame, and watermark — preview the result live,
and save it back to your gallery. The same edits can be applied identically to
a whole **batch** of images in one pass.

There are two flows, chosen from a launch screen: **Image Processing** (below)
and **Video Processing** — documented separately in
**[README.video.md](README.video.md)**.

## Image processing

Every edit is a composable **`ImageOp`**. The tools below each contribute one op
(or, for Export, the encode settings) to a **pipeline** that is applied in a
fixed, sensible order to every image in the batch.

| Tool | What it does |
| --- | --- |
| **Crop** | Drag-to-crop with a live overlay. Shapes: rectangle, circle, rounded, squircle. Non-rectangular shapes mask to **transparent** pixels outside the shape. |
| **Transform** | Rotate by 90° increments and flip horizontally / vertically. |
| **Resize** | Scale by a percentage, or downscale so the longest side fits a max pixel count (aspect preserved). |
| **Filters** | Preset color filters (Mono, Sepia, Noir, Vivid, Cool, Warm, Vintage) **plus a custom RGB color tint** — pick any color (R/G/B 0–255) to wash the image. |
| **Adjust** | Fine-grained brightness, contrast, saturation, and warmth, combined into a single `ColorMatrix`. |
| **Pixelate** | Mosaic effect — averages each *N×N* block into one color. |
| **Frame** | Decorative frames: Solid border, Inset mat, Rounded corners (transparent outside), or a soft drop Shadow. |
| **Watermark** | Text watermark with pattern (Center, four corners, Tiled, Diagonal), editable text, color, font, opacity, and size. |
| **Export** | Encode as JPEG / PNG / WebP. Choose a fixed **quality**, or a **target file size** and let the app search for the best quality that fits. |

### How the pipeline composes

Ops are always applied in this order, so results are predictable regardless of
the order tools were touched:

```
Crop → Transform → Resize → Filter → Adjust → Pixelate → Watermark → Frame
```

The frame is applied last so it wraps the finished (watermarked) photo.
**Compression is not a pipeline op** — it changes how the final pixels are
*encoded*, not the pixels themselves, so it is applied once at the write stage.

- **Live preview** rendered by the same engine that writes the final file, with
  a debounced re-render as you adjust controls.
- **Batch model** — one edit stack applied identically to all selected images.
  Single-image-only tools (Crop) are hidden while multiple images are selected.
- **Swipeable preview** — the large preview is a pager; swipe between images, or
  tap to view full-screen (also swipeable). A trailing "+" page adds more images.
- **Accurate size badge** — shows the real on-disk size when unedited, otherwise
  the estimated size of the file that will be written with the current export
  settings.
- **Alpha-safe export** — when the result has transparency (a shaped crop or
  rounded frame), a lossy JPEG target is automatically bumped to PNG so the
  transparency survives.
- **Undo / redo** across the whole edit history.
- **Edge-to-edge UI** that respects the status bar and navigation / gesture
  insets on every screen.

## Sourcing & saving

- Source images from the system **Photo Picker** (no storage permission) or the
  **Camera** (via `FileProvider`, no camera permission required); pick one or
  many at once.
- One-tap **save to gallery** (MediaStore → `Pictures/MomiWaterMarker`), with an
  optional prompt to delete the gallery originals afterward.

## Video processing

A separate **Video Processing** flow offers **trim**, **cut & join**, **merge**,
**remove audio**, **aspect-ratio change**, **image overlay**, and an
**images-to-video slideshow with transitions** — each ending in the same
preview → save-to-gallery step. See **[README.video.md](README.video.md)** for
the full feature set, the transition implementation, and video architecture.

## Roadmap / TODO

Candidate advanced image-processing features. Each would land as a new
`ImageOp` (or export option) with its own data-layer processor, slotting into
the existing pipeline — so they compose with the tools above.

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
  AppRootScreen.kt       Chooser between the image and video flows
  editor/                Image editor
    EditorScreen.kt      Composables; preview pager; Activity Result APIs
    EditorViewModel.kt   StateFlow<EditorUiState> + one-shot effects channel
    EditorUiState.kt     Immutable UI state (assembles the Pipeline) + EditorEffect
    EditorTool.kt        The set of editing tools
    components/          ImageCropper, EditorControls (per-tool panels)
  video/                 Video editor screen + view model
  theme/                 Material 3 theme

domain/                  Pure Kotlin — no Android types
  model/                 ImageOp (sealed) + Pipeline, CropShape, PhotoFilter,
                         FrameStyle, ResizeMode, ExportOptions/Format, Watermark*
  repository/            Image / Media / Video repositories (abstractions)
  usecase/               ApplyPipeline, CropImage, EstimateExportSize,
                         ProcessAndSaveImages, GetImageInfo, video use cases, …
  util/                  Outcome<T> result type

data/                    Framework implementations
  rendering/             Per-op processors (Geometry, Color, Effect, Frame,
                         Watermark) + PipelineRenderer that folds the ops,
                         ShapeMask, TypefaceProvider
  storage/               ImageStorage (decode/EXIF, cache, MediaStore, FileProvider)
  repository/            Repository implementations
```

Video-specific packages (`presentation/video`, `data/video`, video use cases,
`di/VideoModule`) are documented in [README.video.md](README.video.md).

```
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
