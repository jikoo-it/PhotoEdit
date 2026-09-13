# Momi Studio — Single Image (layered editor)

**Single Image Processing** on the launch screen opens one **Studio**: a layer
stack for a single photo. Cut-out, portrait look, and background blur share
that stack instead of living as separate destinations.

← Back to the [project overview](README.md). The older processor notes for
[portrait](README.portrait.md) and [cut-out](README.cutout.md) still describe
the ML pieces Studio reuses.

## What you can do

Open a photo. It becomes a **Background** layer. Tools add or adjust layers
above it; the canvas composites bottom → top. Undo/redo snapshots the whole
document (up to 50 steps). Save writes a flatten of the stack (preview ≤1080px
long edge; export ≤2560px). **PNG** when the result still has transparency,
otherwise **JPEG**.

### Cut-out (preview, then confirm)

Auto cut-out does **not** write a layer immediately.

1. **Auto cut-out** runs on-device subject segmentation and traces an outline.
2. The outline is a **preview** on the original photo. Drag handles, the
   outline, or the inside of the shape to refine it.
3. **Use this cut-out** keeps pixels inside the outline as a **Subject** layer.
   **Cancel** discards the preview.

**Trace outline** lets you draw the region by hand; the same review step
follows. Lift your finger, then drag to refine, then confirm.

### Portrait look vs background blur

These are independent.

| Control | Effect |
| --- | --- |
| **Portrait look** | Subject stays in color; everything below is **black and white**. |
| **Background blur** | Subject stays **sharp and in color**; the rest **blurs and stays in color**. |

Use blur alone for a depth look without desaturating. Turn both on for a color
subject over a grayscale, blurred backdrop.

If there is no Subject layer yet, turning on portrait look or committing blur
isolates **all detected people** (ML Kit selfie segmentation) so they stay
sharp. To keep **one** person in a group, cut that person out first (auto +
edit the outline, or trace), then apply portrait look and/or blur.

### Backdrops

| Mode | What it does |
| --- | --- |
| **Original** | Show the photo behind the subject. |
| **Transparent** | Hide opaque backdrops; checkerboard shows alpha. Saved as PNG. |
| **Solid color** | Fill behind the subject. |
| **Replace image** | Pick another photo as the backdrop. |

### Layers

The list is top → bottom on screen (subject on top). Toggle visibility or
delete the selected layer (the Background layer cannot be deleted).

## How compositing works

```
Background (photo) ─┐
Fill / replacement ─┤  (only one opaque backdrop is visible)
Portrait look / blur (adjustment on everything below)
Subject (cut-out, in color, unblurred) ────────────── composite
```

The adjustment layer grayscale/blurs whatever is already drawn; the subject is
painted **after**, so it stays sharp and in color.

## Architecture (Studio)

```
presentation/studio/
  StudioScreen.kt        Canvas, cut-out review overlay, tools, layers, save
  StudioViewModel.kt     LayerDocument + undo; propose outline, path cut-out,
                         extract people, flatten, save
  StudioUiState.kt       Document, preview, tracing/review, blur, busy flags

domain/
  model/                 Layer, LayerContent, LayerDocument, NormalizedPoint
  repository/            StudioRepository (extractPeople, flatten)
                         ImageCutoutRepository (+ proposeSubjectOutline, cutoutPath)
  usecase/               ProposeSubjectOutline, CutoutPath, ExtractPeople,
                         FlattenLayerDocument, SaveImage

data/
  mlkit/                 SubjectSegmenter (auto outline), PersonSegmenter (people)
  rendering/             MaskContour, PathCutout, LayerCompositor, BitmapBlur
                         PortraitEffectProcessor.extractForeground
  repository/            StudioRepositoryImpl, ImageCutoutRepositoryImpl
```

Portrait and cut-out **screens** are unused from navigation; their processors
and repositories remain for Studio.

## Build & test

```bash
./gradlew :app:assembleDebug        # build the debug APK (all flows)
./gradlew :app:testDebugUnitTest    # includes LayerDocument + StudioViewModel tests
./gradlew :app:installDebug         # install on a connected device
```

> **On-device only:** auto cut-out needs Play services (model download on first
> use). Person isolation for portrait look / blur is bundled and works offline.
> Neither ML path runs in a headless unit test.
