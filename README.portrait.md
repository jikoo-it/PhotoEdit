# MomiWaterMarker — Portrait Color

The **Portrait Color** tool (under **Single Image Processing** on the launch
screen; see the other flows in [README.md](README.md)) keeps the detected
person(s) in full color while turning the background grayscale, and optionally
Gaussian-blurs the background for a depth-of-field look. Everything runs
**on-device** — pick a portrait, toggle the effect, and save.

## How it works

The person is isolated with **ML Kit Selfie Segmentation**
(`com.google.mlkit:segmentation-selfie`), a **bundled** model (offline, no
download) that returns a soft, per-pixel foreground-confidence mask. This is a
real segmentation mask — not a rectangular crop or a color key — so it works on
arbitrary portraits without any manual masking, and every detected person is
foreground, so **group photos keep everyone in color**.

The mask is requested at the input image's resolution (`enableRawSizeMask()`),
so it aligns pixel-for-pixel with the photo.

### Pipeline

```
person mask (ML Kit) ─┐
source ── grayscale ──┼─ [optional blur] ── background
         (color copy) └─ DST_IN(mask) ───── foreground
background + foreground ─────────────────── composite
```

Compositing is exactly `foreground·mask + background·(1 − mask)`, done with
alpha (`PorterDuff.DST_IN`). The mask's alpha edges are **feathered** with a
small separable box blur so the color/grayscale transition around the person is
gradual — no halos or jaggies around fine hair.

- **Grayscale** — `ColorMatrix` with saturation `0`.
- **Blur** — a dependency-free **stack blur** (Gaussian approximation, `O(w·h)`
  regardless of radius, works on every API level). The blur radius is scaled
  relative to the image's long edge, so a downscaled preview and a full-size
  export look the same.

## Controls

| Control | Effect |
| --- | --- |
| **Selective color** | Keep the person in color, background grayscale. Off = the untouched original. |
| **Background blur** | Also Gaussian-blur the (grayscale) background. |
| **Blur intensity** | Slider (0–100%); re-renders when released. |
| **Hold to compare** | Press and hold to peek at the original. |
| **Save to gallery** | Re-renders at full resolution and saves a JPEG. |

## Performance

All bitmap work runs off the main thread on the IO dispatcher. Previews render
at a bounded resolution (**≤1080px** long edge) for responsiveness; the full
image (**≤2560px**) is only rendered when saving, which also bounds memory on
very large photos. Intermediate bitmaps (mask, grayscale, blurred background,
foreground) are recycled promptly; the caller's source bitmap is never recycled
by the processor. Stale preview renders are cancelled when a newer request
supersedes them, and a result is discarded if the source photo changed while it
was in flight.

### Edge cases

- **Multiple people** — all kept in color (the mask covers every person).
- **No detectable person** — the mask is near-empty, so the whole image reads as
  background (fully grayscale/blurred); nothing crashes.
- **Landscape / very large images** — handled via the bounded working
  resolution.
- **Fine hair / soft edges** — the feathered mask blends them smoothly.

## Architecture (portrait)

Same Clean Architecture + MVVM + Hilt layering as the rest of the app; the
image-processing logic is fully UI-independent and testable behind an interface.

```
presentation/portrait/
  PortraitScreen.kt      Photo picker + toggles + blur slider + compare + save
  PortraitViewModel.kt   StateFlow<PortraitUiState>; preview vs. full-res export
  PortraitUiState.kt     Toggle/slider state → PortraitEffect

domain/
  model/                 PortraitEffect (SelectiveColor | SelectiveColorWithBlur)
  repository/            PortraitEffectRepository (render at a bounded resolution)
  usecase/               ApplyPortraitEffectUseCase (+ shared SaveImageUseCase)

data/
  mlkit/                 PersonSegmenter (ML Kit selfie segmentation → alpha mask)
  rendering/             PortraitEffectProcessor + DefaultPortraitEffectProcessor,
                         BitmapBlur (stack blur)
  repository/            PortraitEffectRepositoryImpl (decode → apply → cache)
```

The requested public component is stable and UI-free:

```kotlin
interface PortraitEffectProcessor {
    suspend fun apply(bitmap: Bitmap, effect: PortraitEffect): Bitmap
}

sealed class PortraitEffect {
    data object SelectiveColor : PortraitEffect()
    data class SelectiveColorWithBlur(val blurRadius: Float) : PortraitEffect()
}
```

## Build & test

```bash
./gradlew :app:assembleDebug        # build the debug APK (all flows)
./gradlew :app:installDebug         # install on a connected device
```

> **On-device only:** selfie segmentation is bundled and runs offline, but still
> needs a real device/emulator to execute — it can't be exercised in a headless
> build.
