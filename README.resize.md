# Resize & Compress

These are tools on the **Bulk Image Processing** editor (top row: **Resize**,
**Aspect ratio**, **Compress**), not a separate home-screen flow. Pick photos,
set the tool, then save — results go to `Pictures/MomiWaterMarker`.

← Back to the [project overview](README.md). For the full edit stack, see
[README.image.md](README.image.md).

## Resize

The same pixel setting is applied to every photo:

| Mode | What it does |
| --- | --- |
| **Scale** | 5%–400% of the original. |
| **Max size** | Cap the longest side; aspect kept. |
| **Exact** | Width × height in pixels. Aspect lock on by default; turn it off to stretch. |

**Aspect ratio** is the neighbouring tool: pad to 1:1 / 16:9 / 9:16 / … without cropping.

## Compress

Re-encode as JPEG / PNG / WebP. For JPEG and WebP:

| Mode | What it does |
| --- | --- |
| **Quality** | Fixed quality slider. Pixels stay the same. |
| **Target size** | Search quality only so the file fits a KB budget (presets plus a custom field). |
| **Fit to size** | Shrink (aspect kept) only if quality-only cannot hit the budget, then compress. Each photo is fitted independently so a 12 MP shot and a small snapshot can share the same 200 KB target. |

## How Fit to size chooses a scale

For each photo, independently:

1. Try quality-only compression at the current pixels.
2. If that already fits at a decent quality (≥ ~70), don't shrink.
3. Otherwise binary-search a scale (aspect kept) at a preferred quality (~75)
   so the encoded file lands on or under the budget.
4. Encode the result with the target-size quality search.

That lives in `ImageStorage` (`suggestSizeFit` / `writeFittedToCache`), used
from the editor export path (`CompressionMode.FIT_TO_SIZE`) and
`FitImagesToSizeUseCase`.
