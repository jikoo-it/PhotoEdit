# MomiWaterMarker — Cut-out Studio

The **Cut-out Studio** tool (under **Single Image Processing** on the launch
screen; see the other flows in [README.md](README.md)) extracts the subject of one photo
**entirely on-device** and lets you choose what sits behind it. Pick a photo,
the subject is segmented once, then swap between backgrounds with a live
preview and save.

## How it works

Subject extraction uses **ML Kit Subject Segmentation**
(`play-services-mlkit-subject-segmentation`), which returns a foreground bitmap
with everything but the salient subject made transparent. The model is
downloaded on demand by Google Play services — the manifest asks for it at
install time (`com.google.mlkit.vision.DEPENDENCIES = subject_segment`), so the
first cut-out normally doesn't stall on a download. No server round-trip and no
custom TensorFlow model.

Segmentation (the expensive step) runs **once** per photo and is cached as a
transparent PNG. Changing the background just re-composites that cut-out, so
previews are fast.

## Backgrounds

| Mode | What it does |
| --- | --- |
| **Transparent** | Keep only the subject; the background is fully transparent. Saved as **PNG** (alpha preserved). |
| **Solid color** | Fill the background with a chosen color from a swatch row. |
| **Blur original** | Keep the original photo behind the subject, blurred — a portrait / depth look. A slider controls how heavy the blur is. |
| **Replace image** | Pick another photo and cover-fit it behind the subject. |

Opaque results (color / blur / replacement) are flattened to **JPEG**; the
transparent mode is the only one that needs PNG.

The preview sits on a checkerboard so transparent regions read clearly.

## Rendering notes

Compositing is plain `Canvas` work (`CutoutComposer`): the background is drawn
first, then the subject stamped on top. The working resolution is capped at a
2048px long edge to bound memory and segmentation time. The blur is a cheap,
encoder-friendly downscale-then-upscale (bilinear averaging) rather than a
per-pixel Gaussian, so it stays fast even on large photos.

## Architecture (cut-out)

Follows the same Clean Architecture + MVVM + Hilt layering as the rest of the
app; segmentation is isolated behind a repository interface so the ML engine
can be swapped without touching the UI.

```
presentation/cutout/
  CutoutScreen.kt        Photo picker + background controls + preview + save
  CutoutViewModel.kt     StateFlow<CutoutUiState>; segments once, re-renders on change
  CutoutUiState.kt       BackgroundMode selection + progress/result state

domain/
  model/                 BackgroundMode, CutoutRenderSpec
  repository/            ImageCutoutRepository (cutoutSubject + renderResult)
  usecase/               CutoutSubjectUseCase, RenderCutoutUseCase (+ shared SaveImageUseCase)

data/
  mlkit/                 SubjectSegmenter (ML Kit wrapper, Task → suspend)
  rendering/             CutoutComposer (background compositing + blur)
  repository/            ImageCutoutRepositoryImpl (decode → segment → composite → cache)
```

The subject PNG and composited results are written to the shared image cache
(`ImageStorage`) and saved to the gallery through the same `MediaRepository`
path as the other image flows.

## Build & test

```bash
./gradlew :app:assembleDebug        # build the debug APK (all flows)
./gradlew :app:installDebug         # install on a connected device
```

> **On-device only:** subject segmentation runs through Google Play services and
> needs a real device/emulator with Play services (and a network connection on
> first use to fetch the model). It can't be exercised in a headless build.
