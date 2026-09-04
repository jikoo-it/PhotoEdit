# Bulk Image Processing

The **Bulk Image Processing** flow applies one edit stack identically to a whole
**batch** of images in a single pass. Pick one or many photos from the
**gallery** or **camera**, stack up edits — crop, transform, resize, filters,
adjustments, pixelate, frame, and watermark — preview the result live, and save
every image back to your gallery.

> **Single Image Processing** — a separate flow focused on editing one image at
> a time (with tools that only make sense on a single photo) is planned and will
> be documented here as it lands.

← Back to the [project overview](README.md). For the video flow, see
[README.video.md](README.video.md).

## Tools

Every edit is a composable **`ImageOp`**; each tool below contributes one op
(or, for Export, the encode settings) to a **pipeline** applied in a fixed,
sensible order to every image in the batch.

| Tool | What it does |
| --- | --- |
| **Crop** | Drag-to-crop with a live overlay. Shapes: rectangle, circle, rounded, squircle. For a non-rectangular shape, the area outside the shape is either **transparent** or **filled** with a color you pick. |
| **Transform** | Rotate by 90° increments and flip horizontally / vertically. |
| **Resize** | Scale by a percentage — **down to 5% or up to 400%** (upscale) — or downscale so the longest side fits a max pixel count (aspect preserved). |
| **Aspect ratio** | Pad the image out to a target ratio (1:1, 16:9, 9:16, 4:3, …) **without cropping** — bars are added on the short sides, either **transparent** or a chosen fill color. Letterbox, not crop. |
| **Filters** | Preset color filters (Mono, Sepia, Noir, Vivid, Cool, Warm, Vintage) **plus a custom RGB color tint** — pick any color (R/G/B 0–255) to wash the image. |
| **Adjust** | Fine-grained brightness, contrast, saturation, and warmth, combined into a single `ColorMatrix`. |
| **Pixelate** | Mosaic effect — averages each *N×N* block into one color. |
| **Frame** | Decorative frames: Solid border, Inset mat, Rounded corners (transparent outside), or a soft drop Shadow — with an option to make the frame background **transparent** instead of filled. |
| **Watermark** | Text watermark with pattern (Center, four corners, Tiled, Diagonal), editable text, color, font, opacity, and size. |
| **Export** | Encode as JPEG / PNG / WebP. Choose a fixed **quality**, or a **target file size** and let the app search for the best quality that fits. |

## How the pipeline composes

Ops are always applied in this order, so results are predictable regardless of
the order tools were touched:

```
Crop → Transform → Resize → Filter → Adjust → Pixelate → Watermark → Aspect ratio → Frame
```

Aspect padding and the frame come **after** the watermark so it stays anchored
to the photo rather than the added bars/border; the frame is last so it wraps
the finished (watermarked, padded) photo.
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
- **Alpha-safe export** — when the result has transparency (a shaped crop, a
  transparent frame background, or transparent aspect-ratio bars), a lossy JPEG
  target is automatically bumped to PNG so the transparency survives.
- **Undo / redo** across the whole edit history.
- **Edge-to-edge UI** that respects the status bar and navigation / gesture
  insets on every screen.

## Sourcing & saving

- Source images from the system **Photo Picker** (no storage permission) or the
  **Camera** (via `FileProvider`, no camera permission required); pick one or
  many at once.
- One-tap **save to gallery** (MediaStore → `Pictures/MomiWaterMarker`), with an
  optional prompt to delete the gallery originals afterward.

## Architecture

Image-specific packages within the app's Clean Architecture + MVVM layering
(presentation → domain ← data), wired with **Hilt**. See the
[project overview](README.md#architecture) for the shared structure.

```
presentation/
  editor/                Image editor
    EditorScreen.kt      Composables; preview pager; Activity Result APIs
    EditorViewModel.kt   StateFlow<EditorUiState> + one-shot effects channel
    EditorUiState.kt     Immutable UI state (assembles the Pipeline) + EditorEffect
    EditorTool.kt        The set of editing tools
    components/          ImageCropper, EditorControls (per-tool panels)

domain/
  model/                 ImageOp (sealed) + Pipeline, CropShape, PhotoFilter,
                         FrameStyle, ResizeMode, AspectRatioPreset,
                         ExportOptions/Format, Watermark*
  usecase/               ApplyPipeline, CropImage, EstimateExportSize,
                         ProcessAndSaveImages, GetImageInfo, …

data/
  rendering/             Per-op processors (Geometry, Color, Effect, Frame,
                         Watermark) + PipelineRenderer that folds the ops,
                         ShapeMask, TypefaceProvider
  storage/               ImageStorage (decode/EXIF, cache, MediaStore, FileProvider)
```

### Design notes

- **SRP** — each processor does one transformation; storage only does I/O; the
  ViewModel only holds UI state and orchestrates use cases.
- **OCP** — a new edit is a new `ImageOp` case plus its processor; the sealed
  hierarchy makes the renderer and UI fail to compile until it is handled
  everywhere. New filters/patterns/fonts are added as enum cases in one `when`.

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
