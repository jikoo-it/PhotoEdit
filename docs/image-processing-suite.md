# Image Processing Suite — Progress & Plan

Status doc for extending **MomiWaterMarker** from a watermark-only app into a full
image-processing app. Branch: `feature/image-processing-suite`.

Last updated during the session that implemented Phase 0.

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
| 0 | Foundations: `ImageOp`/`Pipeline`, `PipelineRenderer`, `ImageProcessingRepository`, dispatcher fix, migrate watermark through the pipeline | **Implemented, not yet compile-verified** (blocked — see below) |
| 1 | Navigation + editor shell (Selection → Editor → Export), tool switcher | Not started |
| 2 | Rotate/Flip, Resize, Compress (export format + quality) | Not started |
| 3 | Filters & Adjustments (shared `ColorMatrix` processor) | Not started |
| 4 | Crop (interactive overlay, aspect lock) | Not started |
| 5 | Polish: undo/redo, reorderable pipeline, per-tool previews, size estimates, perf pass | Not started |

Pipeline op order (when assembled): Crop → Transform → Resize → Adjust → Filter →
Watermark. Compress is an export-stage setting, not an in-pipeline op.

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

## ⚠️ Blocker: concurrent video-editing work in the same tree

Midway through Phase 0, an unrelated **video-editing** feature began appearing in the
same working tree (not created by this image-processing effort):

- `domain/model/VideoClip.kt`, `domain/repository/VideoRepository.kt`,
  `domain/usecase/TrimVideoUseCase.kt`, `domain/usecase/GetVideoDurationUseCase.kt`
- `docs/video-editing.md`
- `app/build.gradle.kts` (adds Media3 deps), `app/src/main/res/xml/file_paths.xml`
- A `VideoRepository` binding added to `di/RepositoryModule.kt` (this merged cleanly
  with the image-processing binding — nothing was lost).

**Problem:** those `.kt` files each have a stray `</content>` tag appended at the end
(a tool artifact leaked into the file body), so the module does not compile. Until
that is fixed, neither feature can build and Phase 0 cannot be compile-verified.

**Decision (per user):** pause and wait — do not modify the video files or otherwise
touch the tree until the concurrency is coordinated.

### Open questions to resolve before resuming
1. Should the video feature and this image work live on the **same branch**, or be
   separated (e.g. a dedicated git worktree) to stop the two efforts colliding?
2. The broken video `.kt` files (stray `</content>`) must be fixed by whoever owns
   that work before either feature builds.

---

## Resuming Phase 0 (once unblocked)
1. Confirm the video files compile (or are removed/isolated).
2. Run `./gradlew :app:compileDebugKotlin testDebugUnitTest` and confirm green.
3. Commit Phase 0 on the feature branch.
4. Proceed to Phase 1 (navigation + editor shell).
