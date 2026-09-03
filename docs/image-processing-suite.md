# Image Processing Suite — Progress & Plan

Status doc for extending **MomiWaterMarker** from a watermark-only app into a full
image-processing app. Branch: `feature/image-processing-suite`.

Last updated after implementing Phases 0–5 (all phases complete; build + unit
tests green). Ready to merge to `main`.

---

## Goal

Turn the app into a pipeline-based image editor: select one or a batch of images →
build an ordered list of actions (crop, resize, compress, filters, adjustments,
watermark) → apply that **same pipeline identically to every selected image** →
export.

### Decisions locked in
- **Batch model:** one shared action pipeline, applied identically to all selected
  images (not per-image editing).
- **Scope:** full architecture, phased roadmap.
- **Watermark:** reworked into the pipeline as one action (`ImageOp.Watermark`),
  not a separate flow.

### Central abstraction
`ImageOp` (sealed, pure/Android-free) describes *what* to do; a data-layer processor
knows *how* to do it to a `Bitmap`. Ordered `ImageOp`s form a `Pipeline`.
`PipelineRenderer` folds the ops over a bitmap in order. `WatermarkRenderer` becomes
one processor among siblings.

---

## Phase roadmap

| Phase | Scope | State |
|-------|-------|-------|
| 0 | Foundations: `ImageOp`/`Pipeline`, `PipelineRenderer`, `ImageProcessingRepository`, dispatcher fix, migrate watermark through the pipeline | ✅ Done |
| 1 | Editor shell with tool switcher (global save pulled out of watermark panel) | ✅ Done |
| 2 | Transform (rotate/flip), Resize, Export/Compress (format + quality) | ✅ Done |
| 3 | Filters (`PhotoFilter` presets) & Adjustments (brightness/contrast/saturation/warmth) via a shared `ColorProcessor` | ✅ Done |
| 4 | Crop (rectangular, reuses the `ImageCropperScreen` overlay) | ✅ Done |
| 5 | Polish: undo/redo (tag-coalesced), global reset-all | ✅ Done |

Pipeline op order (when assembled): **Crop → Transform → Resize → Filter → Adjust →
Watermark**. Compress is an export-stage setting (`ExportOptions`), not an in-pipeline
op — an empty pipeline still saves (re-encode / format conversion).

### Phase 5 scoping notes
- **Undo/redo** snapshots the editable settings (`EditSnapshot`) before each edit.
  A continuous run of same-`tag` edits (one slider drag) coalesces into a single
  undo step; discrete actions (`tag = null`) each get their own step. History is
  capped at 50 entries. `onResetAllEdits` clears everything as one undoable step.
- **Reorderable pipeline** was intentionally *not* built: the batch model is
  "same fixed pipeline for all images", assembled in a fixed, sensible order from
  tool settings — there is no arbitrary op list for the user to reorder.
- **Compress size estimates** deferred: an accurate estimate requires a full
  render+encode per image, too costly for a live preview. Revisit if desired.

---

## Phase 0 — what was done

All on branch `feature/image-processing-suite`.

### New files
- `domain/model/ImageOp.kt` — sealed `ImageOp`; currently only `Watermark(config)`.
  New op cases are added here per phase so every `when` stays exhaustive.
- `domain/model/Pipeline.kt` — `Pipeline(ops: List<ImageOp>)` with `isEmpty`/`isNotEmpty`
  and `Pipeline.EMPTY`.
- `domain/repository/ImageProcessingRepository.kt` — `applyPipeline(source, pipeline)`.
- `data/rendering/PipelineRenderer.kt` — folds ops over a bitmap; delegates the
  `Watermark` op to the existing `WatermarkRenderer`. Owns intermediate-bitmap
  lifecycle; never mutates/recycles the caller's source; always returns a distinct
  bitmap.
- `data/repository/ImageProcessingRepositoryImpl.kt` — decodes source, decodes any
  image-watermark bitmaps up front, runs `PipelineRenderer`, writes result to cache,
  recycles everything. Runs on `@DefaultDispatcher`.
- `domain/usecase/ApplyPipelineUseCase.kt` — single-image (preview); fails on an
  empty pipeline.
- `domain/usecase/ProcessAndSaveImagesUseCase.kt` — batch; applies the pipeline to
  every source and saves each, collecting per-image failures. Carries
  `BatchSaveResult` (moved here from the old `SaveWatermarkedImagesUseCase`).
- `domain/usecase/SaveImageUseCase.kt` — generalized gallery save (renamed from
  `SaveWatermarkedImageUseCase`).
- `test/domain/ApplyPipelineUseCaseTest.kt` — empty-pipeline guard + delegation.

### Modified files
- `di/Dispatchers.kt` — added `@DefaultDispatcher` qualifier alongside `@IoDispatcher`.
- `di/AppModule.kt` — `@IoDispatcher` now provides real `Dispatchers.IO` (was
  `Dispatchers.Default`); added `@DefaultDispatcher` → `Dispatchers.Default`.
- `di/RepositoryModule.kt` — bind `ImageProcessingRepository`; dropped the old
  `WatermarkRepository` binding.
- `presentation/editor/EditorUiState.kt` — added a computed `pipeline` that assembles
  ops from the tool settings (today: watermark only); `canSave` now keys off
  `pipeline.isNotEmpty`.
- `presentation/editor/EditorViewModel.kt` — uses `ApplyPipelineUseCase` +
  `ProcessAndSaveImagesUseCase`; preview and save both run through the pipeline.
- `test/presentation/EditorViewModelTest.kt` — updated to the new use cases.

### Removed files
- `domain/repository/WatermarkRepository.kt`
- `data/repository/WatermarkRepositoryImpl.kt`
- `domain/usecase/ApplyWatermarkUseCase.kt`
- `domain/usecase/SaveWatermarkedImageUseCase.kt`
- `domain/usecase/SaveWatermarkedImagesUseCase.kt`
- `test/domain/ApplyWatermarkUseCaseTest.kt`

### Verification
- **Baseline before changes:** `./gradlew :app:compileDebugKotlin testDebugUnitTest`
  passed.
- **After Phase 0 changes:** compile **failed** — but only because of unrelated,
  concurrently-added video files that are syntactically broken (see below). The
  Phase 0 image code has not yet been independently compile-verified.

---

## Key files (final)

### Domain
- `domain/model/ImageOp.kt` — sealed ops: `Crop`, `Transform`, `Resize`, `Filter`,
  `Adjust`, `Watermark`. Each carries an `isIdentity` so no-op settings are excluded
  from the assembled pipeline.
- `domain/model/Pipeline.kt`, `ResizeMode.kt`, `ExportFormat.kt`, `ExportOptions.kt`,
  `PhotoFilter.kt`.
- `domain/repository/ImageProcessingRepository.kt` — `applyPipeline(source, pipeline,
  export)`; empty pipeline is valid (re-encode).
- `domain/usecase/` — `ApplyPipelineUseCase` (preview), `ProcessAndSaveImagesUseCase`
  (batch save, threads `ExportOptions`), `SaveImageUseCase`, `CropImageUseCase`.

### Data / rendering
- `data/rendering/PipelineRenderer.kt` — folds ops over a bitmap; owns intermediate
  lifecycle; never recycles the caller's source; returns a distinct bitmap.
- `data/rendering/GeometryProcessor.kt` — `crop`, `transform`, `resize`.
- `data/rendering/ColorProcessor.kt` — `filter` (presets) + `adjust` (ColorMatrix).
- `data/rendering/WatermarkRenderer.kt` — the original Canvas engine, now one processor.
- `data/repository/ImageProcessingRepositoryImpl.kt`, `data/storage/ImageStorage.kt`
  (format-aware `writeToCache`/`saveToGallery`).

### Presentation
- `presentation/editor/EditorTool.kt` — Crop, Transform, Resize, Filters, Adjust,
  Watermark, Export.
- `presentation/editor/EditorUiState.kt` — one shared setting per tool + `pipeline`
  assembly + `canSave`/`hasAnyEdits`/`canUndo`/`canRedo`.
- `presentation/editor/EditorViewModel.kt` — per-tool events, tag-coalesced undo/redo,
  reset-all, debounced preview, batch save.
- `presentation/editor/EditSnapshot.kt` — undo/redo snapshot + `snapshot()`/`restore()`.
- `presentation/editor/EditorScreen.kt` — tool switcher + per-tool control panels;
  undo/redo/reset actions; reuses `components/ImageCropper.kt` (shape-less mode for
  the main photo crop).

### Verification
`./gradlew :app:compileDebugKotlin :app:testDebugUnitTest` — green.

> Note: the earlier Phase-0 blocker (an unrelated concurrent video-editing feature
> with broken `.kt` files in the same tree) was resolved by the user stashing all
> video changes. No video files are touched by this work.

---

## Possible follow-ups
- Compress size estimates (needs a background render+encode).
- Aspect-ratio lock / preset ratios in the crop overlay.
- Persisting the pipeline as a reusable preset across sessions.
