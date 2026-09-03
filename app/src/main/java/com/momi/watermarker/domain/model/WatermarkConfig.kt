package com.momi.watermarker.domain.model

/**
 * An immutable description of a watermark to be applied to an image.
 *
 * This is the single source of truth passed from the presentation layer down
 * into the rendering engine. It carries no Android types so it can be unit
 * tested and reused across rendering back-ends.
 *
 * A config describes either a [WatermarkType.TEXT] or [WatermarkType.IMAGE]
 * watermark (selected by [type]); the fields for the inactive type are ignored.
 *
 * @param type        whether the text or image watermark is active.
 * @param text        the watermark text to draw (supports multiple lines via `\n`).
 * @param imageUri    URI of the (cropped) image watermark, or null if none chosen.
 * @param pattern     how/where the watermark is laid out over the image.
 * @param font        the font family used to draw the text.
 * @param colorArgb   the text color as a packed ARGB integer.
 * @param opacity     opacity in `0f..1f` (applied on top of the source alpha).
 * @param textSizeRatio  text size as a fraction of the image's shortest side.
 * @param imageSizeRatio image watermark's longest side as a fraction of the
 *                       image's shortest side (resolution-independent).
 * @param rotationDegrees additional rotation applied to each watermark stamp.
 * @param tileSpacingRatio horizontal gap between repeated watermark items, as a
 *                       fraction of the item's width (tiled/diagonal patterns).
 * @param lineSpacingRatio vertical gap between rows of repeated watermark items,
 *                       as a fraction of the item's height (tiled/diagonal).
 */
data class WatermarkConfig(
    val type: WatermarkType = WatermarkType.DEFAULT,
    val text: String,
    val imageUri: String? = null,
    val pattern: WatermarkPattern = WatermarkPattern.DEFAULT,
    val font: WatermarkFont = WatermarkFont.DEFAULT,
    val colorArgb: Int = DEFAULT_COLOR,
    val opacity: Float = 0.6f,
    val textSizeRatio: Float = 0.06f,
    val imageSizeRatio: Float = 0.25f,
    val rotationDegrees: Float = 0f,
    val tileSpacingRatio: Float = 0.8f,
    val lineSpacingRatio: Float = 0.8f,
) {
    init {
        require(opacity in 0f..1f) { "opacity must be within 0f..1f, was $opacity" }
        require(textSizeRatio > 0f) { "textSizeRatio must be > 0f, was $textSizeRatio" }
        require(imageSizeRatio > 0f) { "imageSizeRatio must be > 0f, was $imageSizeRatio" }
        require(tileSpacingRatio >= 0f) { "tileSpacingRatio must be >= 0f, was $tileSpacingRatio" }
        require(lineSpacingRatio >= 0f) { "lineSpacingRatio must be >= 0f, was $lineSpacingRatio" }
    }

    /** Non-blank on any line — i.e. there is something to draw for a text watermark. */
    val hasText: Boolean get() = text.isNotBlank()

    /** An image watermark has been chosen. */
    val hasImageWatermark: Boolean get() = imageUri != null

    /** Whether the currently-selected [type] has enough to render. */
    val isRenderable: Boolean
        get() = when (type) {
            WatermarkType.TEXT -> hasText
            WatermarkType.IMAGE -> hasImageWatermark
        }

    companion object {
        /** Opaque white. */
        const val DEFAULT_COLOR: Int = 0xFFFFFFFF.toInt()
    }
}
