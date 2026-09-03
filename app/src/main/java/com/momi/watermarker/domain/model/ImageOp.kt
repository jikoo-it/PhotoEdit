package com.momi.watermarker.domain.model

/**
 * A single, self-contained image transformation.
 *
 * An [ImageOp] is a pure, Android-free description of *what* to do to an image;
 * the data layer supplies a processor that knows *how* to do it to a bitmap.
 * Ordered [ImageOp]s form a [Pipeline] that is applied identically to every
 * image in a batch.
 *
 * New operations are added here as the editor grows (crop, filters, ...).
 * Keeping them in one sealed hierarchy means the renderer and the UI both fail
 * to compile until a new op is handled everywhere.
 */
sealed interface ImageOp {

    /** Rotates by a right angle and/or mirrors the image. */
    data class Transform(
        val rotationDegrees: Int = 0,
        val flipHorizontal: Boolean = false,
        val flipVertical: Boolean = false,
    ) : ImageOp {
        init {
            require(rotationDegrees in ROTATIONS) {
                "rotationDegrees must be one of $ROTATIONS, was $rotationDegrees"
            }
        }

        /** True when this transform would leave the image unchanged. */
        val isIdentity: Boolean
            get() = rotationDegrees == 0 && !flipHorizontal && !flipVertical

        /** Returns a copy rotated a further 90° clockwise (wrapping at 360°). */
        fun rotatedClockwise(): Transform =
            copy(rotationDegrees = (rotationDegrees + 90) % 360)

        companion object {
            val ROTATIONS = setOf(0, 90, 180, 270)
        }
    }

    /**
     * Crops the image to a rectangular region, expressed as fractions of the
     * source ([NormalizedRect]) so it is resolution-independent.
     */
    data class Crop(val rect: NormalizedRect = NormalizedRect.FULL) : ImageOp {
        /** True when the crop covers the whole image (nothing is trimmed). */
        val isIdentity: Boolean get() = rect == NormalizedRect.FULL
    }

    /** Scales the image down, either by a percentage or to a maximum dimension. */
    data class Resize(
        val mode: ResizeMode = ResizeMode.PERCENT,
        val percent: Float = 1f,
        val maxDimensionPx: Int = DEFAULT_MAX_DIMENSION,
    ) : ImageOp {
        init {
            require(percent in 0.01f..1f) { "percent must be within 0.01f..1f, was $percent" }
            require(maxDimensionPx > 0) { "maxDimensionPx must be > 0, was $maxDimensionPx" }
        }

        /**
         * True only when this resize can never change any image: a 100% scale.
         * A [ResizeMode.LONGEST_SIDE] resize is not identity here — whether it
         * changes a given image depends on that image's size, decided by the
         * processor at render time.
         */
        val isIdentity: Boolean
            get() = mode == ResizeMode.PERCENT && percent >= 1f

        companion object {
            const val DEFAULT_MAX_DIMENSION = 2048
        }
    }

    /**
     * Fine-grained color adjustments, each normalized to `-1f..1f` where `0f`
     * leaves that channel untouched (positive brightens/increases, negative
     * darkens/decreases). Applied as a single combined `ColorMatrix`.
     */
    data class Adjust(
        val brightness: Float = 0f,
        val contrast: Float = 0f,
        val saturation: Float = 0f,
        val warmth: Float = 0f,
    ) : ImageOp {
        init {
            require(brightness in RANGE) { "brightness must be within $RANGE, was $brightness" }
            require(contrast in RANGE) { "contrast must be within $RANGE, was $contrast" }
            require(saturation in RANGE) { "saturation must be within $RANGE, was $saturation" }
            require(warmth in RANGE) { "warmth must be within $RANGE, was $warmth" }
        }

        val isIdentity: Boolean
            get() = brightness == 0f && contrast == 0f && saturation == 0f && warmth == 0f

        companion object {
            val RANGE = -1f..1f
        }
    }

    /** Applies a named color preset ([PhotoFilter]) to the whole image. */
    data class Filter(val filter: PhotoFilter = PhotoFilter.NONE) : ImageOp {
        val isIdentity: Boolean get() = filter == PhotoFilter.NONE
    }

    /** Draws a watermark ([WatermarkConfig]) over the image. */
    data class Watermark(val config: WatermarkConfig) : ImageOp
}
