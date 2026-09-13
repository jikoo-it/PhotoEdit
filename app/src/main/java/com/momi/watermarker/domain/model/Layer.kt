package com.momi.watermarker.domain.model

/** How a layer is blended onto the composite below. Phase 1 supports Normal only. */
enum class LayerBlend {
    NORMAL,
}

/**
 * What a layer paints. Rasters are bitmap URIs; fills are solid color;
 * adjustments process everything already drawn below.
 */
sealed class LayerContent {
    data class Raster(val uri: String) : LayerContent()
    data class Fill(val colorArgb: Int) : LayerContent()
    data class Adjustment(
        val grayscale: Boolean = false,
        val blurStrength: Float = 0f,
    ) : LayerContent()
}

/**
 * One node in a [LayerDocument] stack. [id] is stable across edits so undo and
 * the UI can track the same layer; [name] is a display fallback.
 */
data class Layer(
    val id: String,
    val name: String,
    val content: LayerContent,
    val visible: Boolean = true,
    val opacity: Float = 1f,
    val blend: LayerBlend = LayerBlend.NORMAL,
)

/** Well-known layer ids used by the Studio tools. */
object LayerIds {
    const val BACKGROUND = "background"
    const val FILL = "fill"
    const val REPLACEMENT = "replacement"
    const val ADJUSTMENT = "adjustment"
    const val SUBJECT = "subject"

    /** Bottom → top. Unknown ids are painted above these. */
    val Z_ORDER = listOf(BACKGROUND, FILL, REPLACEMENT, ADJUSTMENT, SUBJECT)
}

/** Flattened composite ready to preview or save. */
data class FlattenedImage(
    val uri: String,
    val hasTransparency: Boolean,
)
