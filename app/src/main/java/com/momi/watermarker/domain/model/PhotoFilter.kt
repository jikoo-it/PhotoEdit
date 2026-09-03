package com.momi.watermarker.domain.model

/**
 * A named color preset applied to the whole image. Each value is a pure label;
 * the data layer maps it to a concrete color transformation (a `ColorMatrix`).
 *
 * [NONE] is the identity filter — selecting it contributes no op to the pipeline.
 */
enum class PhotoFilter(val label: String) {
    NONE("Original"),
    // Tonal presets.
    MONO("Mono"),
    SEPIA("Sepia"),
    NOIR("Noir"),
    VIVID("Vivid"),
    COOL("Cool"),
    WARM("Warm"),
    VINTAGE("Vintage"),
    // Color-tint presets (a colored wash over the image).
    RED("Red"),
    ORANGE("Orange"),
    GREEN("Green"),
    TEAL("Teal"),
    BLUE("Blue"),
    VIOLET("Violet");

    companion object {
        val DEFAULT = NONE
    }
}
