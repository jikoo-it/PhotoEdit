# Video Editing Suite — Research & Progress

> Living document for the video-editing feature set on `feature/image-processing-suite`.
> Captures research, decisions, the milestone plan, risks, and a running progress log.

Last updated: 2026-09-04 (slideshow + transitions)

---

## 1. Goal

Extend MomiWaterMarker from a photo-watermarking app into one that can also edit
video. Requested features: **cut/trim, crop, merge, transitions, shadow removal**,
plus whatever else fits. This doc scopes that into achievable milestones and
records progress.

---

## 2. Research

### 2.1 Engine choice — Media3 `Transformer` vs FFmpeg

| | **Jetpack Media3 `Transformer`** (chosen) | FFmpeg (`ffmpeg-kit` community fork) |
|---|---|---|
| Maintenance | Actively maintained by Google (AndroidX) | Upstream `ffmpeg-kit` retired; only community forks |
| Hardware accel | Yes (MediaCodec) | Software by default; HW is fiddly |
| APK size | Small (a few hundred KB) | Large (tens of MB per ABI) |
| Licensing | Apache 2.0 | GPL/LGPL considerations |
| Trim / crop / scale / rotate / overlay | First-class (`ClippingConfiguration`, `Effect`) | Supported |
| Concatenate (merge) | `Composition` of `EditedMediaItem`s | `concat` filter |
| Clip-to-clip **transitions** | **Not built in** — needs custom GL/overlay compositor | `xfade` filter |
| Per-frame **shadow removal** | N/A (needs ML) | N/A (needs ML) |

**Decision:** Base the suite on **Media3 `Transformer`** (+ `media3-effect` for
GL effects, `media3-exoplayer` + `media3-ui` for preview). Reach for FFmpeg only
if a specific need (exotic codec, `xfade`) can't be met natively. This also lets
us reuse the existing Canvas-based `WatermarkRenderer` as a video `OverlayEffect`.

### 2.2 Feature feasibility

| Feature | Feasibility | Notes |
|---|---|---|
| Trim / cut | Easy | `MediaItem.ClippingConfiguration` (start/end ms). |
| Crop | Easy | `Crop` / `Presentation` effect. Reuses `NormalizedRect`. |
| Merge | Medium | `Composition` of `EditedMediaItem`s; must reconcile differing resolution/fps. |
| Transitions | **Hard** | No native clip-to-clip transition API. Custom GL shader/overlay compositor, or FFmpeg `xfade`. Own mini-project. |
| Shadow removal | **Very hard (ML)** | Not an NLE op — it's deep-learning inpainting. No off-the-shelf Android API. Needs a custom TFLite/MediaPipe model; slow per-frame; quality variable. **Deferred / experimental.** |
| Video watermark/overlay | Easy–Medium | Port `WatermarkRenderer` output to an `OverlayEffect`. Strong fit for this app. |
| Audio (mute/volume/fade/replace) | Easy | Media3 audio processors. |
| Speed / reverse | Easy / Medium | `SpeedChangeEffect`; reverse needs frame buffering. |
| Rotate / flip | Easy | `ScaleAndRotateTransformation`. |
| Color/filters/LUT | Medium | GL matrix / custom shader effects. |
| Frame grab → photo | Easy | `MediaMetadataRetriever` / `ExoPlayer`; ties back to the photo editor. |

### 2.3 Additional feature ideas (backlog)

High value: video watermark/logo overlay, export presets (1080p/720p, per-platform),
audio mute/replace/fade, speed change, reverse, rotate/flip.
Medium: filters & color, frame extraction, text/sticker/emoji overlays with timing,
GIF/boomerang export.
Later/advanced: background music + auto-ducking, PiP/split-screen, chroma key
(green screen — more tractable than shadow removal), auto-captions.

---

## 3. Milestone plan

### Milestone 1 — Core video suite (trim → crop → merge → watermark → export)

Coherent first release that leans on the existing architecture.

**Architecture mapping** (mirrors the photo side; dependency rule points inward):

```
domain/
  model/      VideoClip (platform-neutral uri + durationMs), VideoExportSettings
  repository/ VideoRepository (trim, crop, merge, overlay, export, save)
  usecase/    TrimVideoUseCase, CropVideoUseCase, MergeVideosUseCase,
              ApplyVideoWatermarkUseCase, GetVideoDurationUseCase, SaveVideoUseCase
data/
  video/      VideoTransformer (Media3 Transformer wrapper, suspend + progress)
  storage/    VideoStorage (cache output files, MediaStore.Video, probe metadata)
  repository/ VideoRepositoryImpl
presentation/
  home/       HomeScreen (choose Photo vs Video)
  video/      VideoEditorScreen, VideoEditorViewModel, VideoEditorUiState
```

**Phased delivery:**

- **Phase 0 — Spike (this change):** wire up Media3, trim + export end to end.
  Proves the engine, threading model, storage, and preview.
- **Phase 1 — Trim (productionize):** trim range UI polish, export presets, save
  to gallery, progress + cancel.
- **Phase 2 — Crop / rotate:** `Presentation`/`Crop` + `ScaleAndRotate` effects;
  reuse `NormalizedRect` and the existing cropper UX.
- **Phase 3 — Merge:** multi-clip picking, ordering, resolution/fps reconciliation,
  `Composition` export.
- **Phase 4 — Video watermark:** adapt `WatermarkRenderer` → `OverlayEffect`;
  reuse `WatermarkConfig`.

### Milestone 2 — Motion & audio
Transitions between merged clips (custom compositor / `xfade`), audio (mute/replace/
fade/music), speed & reverse, filters/LUTs, frame grab.

### Milestone 3 — Experimental / ML
Shadow removal (research spike, likely cloud-assisted for photos first), chroma key,
auto-captions. **Not on a release timeline.**

---

## 4. Risks & open questions

- **Transitions** have no native API — the single biggest effort item in M1's neighborhood; kept in M2.
- **Shadow removal** is an ML research problem, not an NLE feature — deliberately deferred.
- **Threading:** `Transformer` must be built/started/cancelled on a thread with a
  `Looper` (main). Wrapper isolates this.
- **Codec/resolution mismatches** on merge require a normalization pass.
- **Large files / long videos:** export time, memory, and cancellation UX matter.
- **Toolchain is forward-dated** (AGP 9.4, compileSdk 37) — Media3 version pinned in the catalog; bump if resolution fails.

---

## 4a. Delivered operations (single export pipeline)

Every operation is expressed as one `VideoEditRequest` (domain) → `VideoTransformer.ExportSpec`
(data) → a Media3 `Composition` of an `EditedMediaItemSequence`. Adding an operation means
building a different request, **not** a new pipeline. Shipped so far:

| Op | How | Status |
|---|---|---|
| Trim / Cut & Join | N clipped segments, same source, concatenated (N=1 is a plain trim — no separate Trim op) | ✅ |
| Merge | N whole segments, different sources, `experimentalSetForceAudioTrack(true)` | ✅ |
| Remove Sound | `EditedMediaItem.setRemoveAudio(true)` | ✅ |
| Aspect Ratio | `Presentation.createForAspectRatio(r, LAYOUT_SCALE_TO_FIT_WITH_CROP)` | ✅ |
| Image Overlay | `OverlayEffect` + `BitmapOverlay` w/ `OverlaySettings.alphaScale` | ✅ |
| Images → video (slideshow) | image `EditedMediaItem` + `setDurationUs`/`setFrameRate`; per-image duration + per-boundary transition (`CreateSlideshowUseCase`) | ✅ |
| Transitions (per boundary) | composition-wide time-varying effects — see §4b | ✅ (fade/flash/slide/zoom) |

UI: a single `VideoEditorActivity` hosts a home op-picker (`VideoOp`) that routes to a
per-op flow; state lives in one `VideoEditorViewModel`/`VideoEditorUiState`. `Composition`
sequence handles differing merge resolutions; `forceAudioTrack` covers differing audio presence.

## 4b. Transitions — how they're built (shipped, non-overlapping approach)

Media3 1.5.1 has **no clip-to-clip transition API** (confirmed against the shipped jars:
no crossfade/xfade on `EditedMediaItemSequence` or `Composition`). Rather than block on a
custom compositor, transitions are rendered by **composition-wide, time-varying effects** that
need no clip overlap — the effect sees composition-absolute presentation times and we know each
boundary's absolute timestamp from the clip layout (`buildTransitionEffects` in
`VideoTransformer`). Each boundary carries its own `VideoTransition`, so every one can differ.

| Transition | Effect | Interface |
|---|---|---|
| Fade | dip RGB toward black over the window | `FadeTransitionsMatrix : RgbMatrix` |
| Flash | dip toward white (alpha column adds constant; frames are opaque) | `FadeTransitionsMatrix` |
| Slide | outgoing translates off left, incoming translates in from right (NDC) | `GeometricTransitionsMatrix : MatrixTransformation` |
| Zoom | outgoing scales to a point at centre, incoming grows back out | `GeometricTransitionsMatrix` |

Each boundary's half-width is clamped to `min(transitionMs, shorter neighbour)` so a short
image can't be dimmed/moved end-to-end. Slides/zooms reveal the black background (no overlap),
so they read as *motion through black* rather than a true cross-dissolve.

**Known limitation / future work:** a real **cross-dissolve** (both clips visible at once)
still needs an overlapping-sequence compositor or a custom `GlShaderProgram` sampling two
textures; FFmpeg `xfade` remains the last-resort fallback. Tracked, not yet built.

## 5. Progress log

- **2026-09-03** — Research complete; engine decision = Media3 Transformer; milestone
  plan drafted (this doc). Starting Phase 0 spike (trim + export).
- **2026-09-03** — Phase 0 domain/data/DI layers landed and compiling
  (`:app:compileDebugKotlin` green). Isolated onto its own worktree/branch
  `feature/video-editing` (off `main`), fully independent of the image-processing
  suite: video adds only new files plus additive build config, and its Hilt
  binding lives in a dedicated `di/VideoModule.kt` so `RepositoryModule.kt` (and
  all other image-processing files) is never touched. Remaining for the spike:
  `VideoEditorScreen` + `HomeScreen` + navigation, then a full `assembleDebug`.
- **2026-09-03** — Spike UI landed (pick → trim → export), installed & running on Pixel 8.
- **2026-09-03** — Generalized the data layer to a single `export(VideoEditRequest)` pipeline
  and shipped a multi-op suite: **Cut & Join, Merge, Remove Sound, Aspect Ratio, Image
  Overlay** (plus existing Trim), each a distinct use case + home-screen flow. Confirmed the
  exact Media3 1.5.1 API surface against the jars before coding (`EditedMediaItemSequence`,
  `Composition.experimentalSetForceAudioTrack`, `Presentation`, `OverlayEffect`/`BitmapOverlay`).
  Compiles clean; installed & verified on Pixel 8 (no crash). Slideshow plumbing added behind
  `VideoSegment.isImage` (no UI). **Transitions** scoped as the next milestone (§4b) — no native
  API, needs a crossfade compositor.
- **2026-09-04** — **Images → video (slideshow)** shipped with a new `VideoOp.SLIDESHOW`:
  pick multiple photos, set each image's on-screen duration, and choose a per-boundary
  transition (every boundary independent) with a shared transition-length slider and an output
  aspect ratio. New `CreateSlideshowUseCase`; UI uses Coil `AsyncImage` thumbnails +
  reorder/remove.
- **2026-09-04** — **Transitions implemented** without waiting on an overlapping compositor
  (§4b rewritten). Verified `RgbMatrix` and `MatrixTransformation` signatures against the jars,
  then added `FadeTransitionsMatrix` (fade/flash) and `GeometricTransitionsMatrix` (slide/zoom)
  as composition-wide effects keyed on absolute boundary times computed in `buildTransitionEffects`.
  `VideoTransition` = {NONE, FADE, FLASH, SLIDE, ZOOM}; mapped in `VideoRepositoryImpl`. Compiles
  clean; installed & launches on Pixel 8. True cross-dissolve left as future work.
- **2026-09-04** — Removed the standalone **Trim** op: it was exactly the single-segment case of
  **Cut & Join**, so Trim now lives inside "Trim / Cut & Join" (which opens with one kept range).
  Deleted `TrimVideoUseCase` and the trim-only UI state/controls.
