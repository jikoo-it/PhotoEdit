package com.momi.watermarker.domain.model

/**
 * A preset color look applied to a whole video, mapped in the data layer to
 * Media3 color effects. Platform-neutral: the presentation layer offers these
 * by [label] and the transformer turns each into the matching GL effect.
 */
enum class VideoColorFilter(val label: String) {
    NONE("None"),
    GRAYSCALE("B&W"),
    INVERT("Invert"),
    WARM("Warm"),
    COOL("Cool"),
    BRIGHT("Bright"),
    DARK("Dark"),
    HIGH_CONTRAST("Punch");

    companion object {
        val DEFAULT = NONE
    }
}
