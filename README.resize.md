# Resize & Compress

A dedicated **bulk** flow (chosen from the launch screen) for three jobs that
don't need the full image editor: change pixel dimensions, compress, or fit
each photo independently to a file-size budget. Pick a batch, configure the
op, then **Process & save** — results go to the gallery
(`Pictures/MomiWaterMarker`) and stay there after uninstall.

← Back to the [project overview](README.md). For the full edit stack (crop,
filters, watermark, …), see [README.image.md](README.image.md).

## Operations

| Op | What it does |
| --- | --- |
| **Resize dimensions** | The same pixel setting is applied to every photo: **Scale** (5%–400%), **Max size** (cap the longest side, aspect kept), or **Exact** width × height. Exact keeps aspect ratio by default; turn the lock off to stretch. |
| **Compress** | Pixels stay the same. Re-encode as JPEG / PNG / WebP at a fixed **quality**, or a **target file size** (presets plus a custom KB field) by searching quality only. If quality-only still can't hit the budget, use Fit to file size. |
| **Fit to file size** | The combination: each photo is **shrunk (aspect kept) only as much as needed**, then compressed to the KB target. A 12 MP shot and a small snapshot can share the same 200 KB budget. JPEG or WebP. Default target is 200 KB; presets and a custom field are offered. |

Progress shows as photos are written. Failures are collected per-image so one
unreadable file doesn't abort the rest of the batch.

## How Fit to file size chooses a scale

For each photo, independently:

1. Try quality-only compression at the current pixels.
2. If that already fits at a decent quality (≥ ~70), don't shrink.
3. Otherwise binary-search a scale (aspect kept) at a preferred quality (~75)
   so the encoded file lands on or under the budget.
4. Encode the result with the existing target-size quality search.

That lives in `ImageStorage` (`suggestSizeFit` / `writeFittedToCache`), driven
by `FitImagesToSizeUseCase`.

## Architecture

Same Clean Architecture + MVVM + Hilt layering as the rest of the app.

```
presentation/batch/
  BatchResizeScreen.kt      Op picker + per-op controls + pick/process/save
  BatchResizeViewModel.kt   StateFlow<BatchResizeUiState> + effects channel
  BatchResizeUiState.kt     BatchResizeOp (DIMENSIONS / COMPRESS / FILE_SIZE)

domain/
  model/                    SizeFitSuggestion, SizeFitPlanner, ImageOp.Resize
                            (PERCENT / LONGEST_SIDE / EXACT), ExportOptions
  usecase/                  ProcessAndSaveImagesUseCase (dimensions + compress),
                            FitImagesToSizeUseCase (per-image fit)
  repository/               ImageProcessingRepository.fitToTargetSize

data/
  storage/                  ImageStorage.analyzeExport / writeFittedToCache
  repository/               ImageProcessingRepositoryImpl
```

Saves use the same MediaStore path as the rest of the image suite
(`Pictures/MomiWaterMarker` on primary shared storage).
