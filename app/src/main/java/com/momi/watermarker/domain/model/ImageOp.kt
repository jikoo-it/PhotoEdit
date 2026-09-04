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
     * Crops the image to a region ([rect], fractions of the source so it is
     * resolution-independent) and optionally masks it to a non-rectangular
     * [shape]. Pixels outside the shape are made transparent when
     * [backgroundArgb] is null (the default), or filled with that opaque color
     * otherwise — since the exported file is always rectangular, this lets the
     * user choose between a see-through cut-out and a solid backdrop.
     */
    data class Crop(
        val rect: NormalizedRect = NormalizedRect.FULL,
        val shape: CropShape = CropShape.RECTANGLE,
        val backgroundArgb: Int? = null,
    ) : ImageOp {
        /** True only when nothing is trimmed and no shape mask is applied. */
        val isIdentity: Boolean
            get() = rect == NormalizedRect.FULL && shape == CropShape.RECTANGLE

        /**
         * Whether the crop introduces transparency (needs an alpha-capable
         * format): a non-rectangular shape whose masked area is left see-through.
         */
        val hasTransparency: Boolean get() = shape != CropShape.RECTANGLE && backgroundArgb == null
    }

    /**
     * Scales the image by a percentage (up or down) or caps its longest side.
     * A [percent] above 1f enlarges the image; below 1f shrinks it.
     */
    data class Resize(
        val mode: ResizeMode = ResizeMode.PERCENT,
        val percent: Float = 1f,
        val maxDimensionPx: Int = DEFAULT_MAX_DIMENSION,
    ) : ImageOp {
        init {
            require(percent in MIN_PERCENT..MAX_PERCENT) {
                "percent must be within $MIN_PERCENT..$MAX_PERCENT, was $percent"
            }
            require(maxDimensionPx > 0) { "maxDimensionPx must be > 0, was $maxDimensionPx" }
        }

        /**
         * True only when this resize can never change any image: an exact 100%
         * scale. A [ResizeMode.LONGEST_SIDE] resize is not identity here — whether
         * it changes a given image depends on that image's size, decided by the
         * processor at render time.
         */
        val isIdentity: Boolean
            get() = mode == ResizeMode.PERCENT && percent == 1f

        companion object {
            const val DEFAULT_MAX_DIMENSION = 2048

            /** Scale bounds: down to 5% and up to 400% of the original. */
            const val MIN_PERCENT = 0.05f
            const val MAX_PERCENT = 4f
        }
    }

    /**
     * Pads the image out to a target aspect ratio *without cropping* any pixels:
     * bars are added on the two shorter sides so the whole image still fits. The
     * bars are transparent when [fillArgb] is null (the default), or that opaque
     * color otherwise. [AspectRatioPreset.ORIGINAL] is the identity (no padding).
     */
    data class AspectPad(
        val preset: AspectRatioPreset = AspectRatioPreset.ORIGINAL,
        val fillArgb: Int? = null,
    ) : ImageOp {
        val isIdentity: Boolean get() = preset.ratio == null

        /** Transparent bars need an alpha-capable export format. */
        val hasTransparency: Boolean get() = !isIdentity && fillArgb == null
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

    /**
     * A color filter over the whole image: either a named [PhotoFilter] preset
     * or a user-picked [customTintArgb] color wash. A non-null [customTintArgb]
     * takes precedence over [filter] (they are mutually exclusive in the UI).
     */
    data class Filter(
        val filter: PhotoFilter = PhotoFilter.NONE,
        val customTintArgb: Int? = null,
    ) : ImageOp {
        val isIdentity: Boolean get() = filter == PhotoFilter.NONE && customTintArgb == null

        /** Whether a user-picked custom color tint is active. */
        val hasCustomTint: Boolean get() = customTintArgb != null
    }

    /**
     * Mosaic / pixelate effect: replaces each [blockSizePx] × [blockSizePx]
     * square with its average color. A block of 1px is the identity.
     */
    data class Pixelate(val blockSizePx: Int = 1) : ImageOp {
        init { require(blockSizePx >= 1) { "blockSizePx must be >= 1, was $blockSizePx" } }

        val isIdentity: Boolean get() = blockSizePx <= 1
    }

    /**
     * A decorative frame around the image. [widthRatio] is the frame thickness
     * (or, for [FrameStyle.SHADOW], the padding + blur) as a fraction of the
     * image's shorter side; [cornerRadiusRatio] is the corner rounding as a
     * fraction of the shorter side (used by [FrameStyle.ROUNDED]).
     */
    data class Frame(
        val style: FrameStyle = FrameStyle.NONE,
        val widthRatio: Float = DEFAULT_WIDTH_RATIO,
        val colorArgb: Int = DEFAULT_COLOR,
        val cornerRadiusRatio: Float = DEFAULT_CORNER_RADIUS_RATIO,
        val transparentBackground: Boolean = false,
    ) : ImageOp {
        val isIdentity: Boolean get() = style == FrameStyle.NONE

        /**
         * True when the frame reveals a see-through background (needs an
         * alpha-capable export format): a rounded frame always does, and any
         * other style does when the fill is set to transparent.
         */
        val hasTransparency: Boolean
            get() = style == FrameStyle.ROUNDED || (transparentBackground && style != FrameStyle.NONE)

        companion object {
            const val DEFAULT_WIDTH_RATIO = 0.05f
            const val DEFAULT_COLOR = 0xFFFFFFFF.toInt()
            const val DEFAULT_CORNER_RADIUS_RATIO = 0.08f
        }
    }

    /** Draws a watermark ([WatermarkConfig]) over the image. */
    data class Watermark(val config: WatermarkConfig) : ImageOp
}
