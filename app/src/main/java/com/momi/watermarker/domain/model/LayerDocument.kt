package com.momi.watermarker.domain.model

/**
 * A single-photo layer stack. [layers] are stored bottom → top. This is the
 * Studio document model — not a bulk [Pipeline].
 */
data class LayerDocument(
    val sourceUri: String,
    val canvasWidth: Int,
    val canvasHeight: Int,
    val layers: List<Layer>,
    val selectedLayerId: String? = layers.lastOrNull()?.id,
) {
    val selectedLayer: Layer? get() = layers.find { it.id == selectedLayerId }

    fun layer(id: String): Layer? = layers.find { it.id == id }

    fun hasSubject(): Boolean = layer(LayerIds.SUBJECT) != null

    fun portraitLookEnabled(): Boolean {
        val adj = layer(LayerIds.ADJUSTMENT)
        val content = adj?.content as? LayerContent.Adjustment ?: return false
        return adj.visible && content.grayscale
    }

    fun blurStrength(): Float =
        (layer(LayerIds.ADJUSTMENT)?.content as? LayerContent.Adjustment)?.blurStrength ?: 0f

    fun fillColorArgb(): Int =
        (layer(LayerIds.FILL)?.content as? LayerContent.Fill)?.colorArgb ?: DEFAULT_FILL

    /**
     * True when flattening should keep an alpha channel (no opaque backdrop
     * covering the canvas).
     */
    fun producesTransparency(): Boolean {
        val visible = layers.filter { it.visible }
        return visible.none { layer ->
            when (layer.content) {
                is LayerContent.Fill -> true
                is LayerContent.Raster -> layer.id != LayerIds.SUBJECT
                is LayerContent.Adjustment -> false
            }
        }
    }

    fun selecting(id: String): LayerDocument = copy(selectedLayerId = id)

    fun togglingVisibility(id: String): LayerDocument = copy(
        layers = layers.map { if (it.id == id) it.copy(visible = !it.visible) else it },
    )

    fun removing(id: String): LayerDocument {
        if (id == LayerIds.BACKGROUND) return this
        val next = layers.filterNot { it.id == id }
        val selected = if (selectedLayerId == id) next.lastOrNull()?.id else selectedLayerId
        return copy(layers = next, selectedLayerId = selected)
    }

    fun withSubject(uri: String): LayerDocument =
        upsert(Layer(LayerIds.SUBJECT, "Subject", LayerContent.Raster(uri)))

    fun withPortraitLook(enabled: Boolean, blurStrength: Float = blurStrength()): LayerDocument {
        val blur = blurStrength.coerceIn(0f, 1f)
        return if (enabled) {
            upsert(
                Layer(
                    id = LayerIds.ADJUSTMENT,
                    name = "Portrait look",
                    content = LayerContent.Adjustment(grayscale = true, blurStrength = blur),
                    visible = true,
                ),
            ).showingOriginalBackdrop()
        } else {
            if (blur <= 0.01f) {
                removing(LayerIds.ADJUSTMENT)
            } else {
                upsert(
                    Layer(
                        id = LayerIds.ADJUSTMENT,
                        name = "Background blur",
                        content = LayerContent.Adjustment(grayscale = false, blurStrength = blur),
                        visible = true,
                    ),
                )
            }
        }
    }

    fun withBlur(strength: Float): LayerDocument {
        val s = strength.coerceIn(0f, 1f)
        val existing = layer(LayerIds.ADJUSTMENT)?.content as? LayerContent.Adjustment
        val grayscale = existing?.grayscale ?: false
        return if (!grayscale && s <= 0.01f) {
            removing(LayerIds.ADJUSTMENT)
        } else {
            upsert(
                Layer(
                    id = LayerIds.ADJUSTMENT,
                    name = if (grayscale) "Portrait look" else "Background blur",
                    content = LayerContent.Adjustment(grayscale = grayscale, blurStrength = s),
                    visible = true,
                ),
            )
        }
    }

    fun showingOriginalBackdrop(): LayerDocument = copy(
        layers = layers.map { layer ->
            when (layer.id) {
                LayerIds.BACKGROUND -> layer.copy(visible = true)
                LayerIds.FILL, LayerIds.REPLACEMENT -> layer.copy(visible = false)
                else -> layer
            }
        },
    )

    fun showingTransparentBackdrop(): LayerDocument {
        // Portrait look needs the original photo as the grayscale backdrop.
        if (portraitLookEnabled()) return showingOriginalBackdrop()
        return copy(
            layers = layers.map { layer ->
                when (layer.id) {
                    LayerIds.BACKGROUND, LayerIds.FILL, LayerIds.REPLACEMENT -> layer.copy(visible = false)
                    else -> layer
                }
            },
        )
    }

    fun showingColorFill(argb: Int = fillColorArgb()): LayerDocument {
        val withFill = upsert(Layer(LayerIds.FILL, "Fill", LayerContent.Fill(argb), visible = true))
        return withFill.copy(
            layers = withFill.layers.map { layer ->
                when (layer.id) {
                    LayerIds.BACKGROUND, LayerIds.REPLACEMENT -> layer.copy(visible = false)
                    else -> layer
                }
            },
            selectedLayerId = LayerIds.FILL,
        )
    }

    fun showingReplacement(uri: String): LayerDocument =
        upsert(Layer(LayerIds.REPLACEMENT, "Background image", LayerContent.Raster(uri), visible = true)).let { doc ->
            doc.copy(
                layers = doc.layers.map { layer ->
                    when (layer.id) {
                        LayerIds.BACKGROUND, LayerIds.FILL -> layer.copy(visible = false)
                        else -> layer
                    }
                },
            )
        }

    fun upsert(layer: Layer): LayerDocument {
        val merged = layers.filterNot { it.id == layer.id } + layer
        val sorted = merged.sortedBy { zIndex(it.id) }
        return copy(layers = sorted, selectedLayerId = layer.id)
    }

    companion object {
        const val DEFAULT_FILL = 0xFFFFFFFF.toInt()

        fun fromPhoto(sourceUri: String, width: Int, height: Int): LayerDocument = LayerDocument(
            sourceUri = sourceUri,
            canvasWidth = width.coerceAtLeast(1),
            canvasHeight = height.coerceAtLeast(1),
            layers = listOf(
                Layer(LayerIds.BACKGROUND, "Background", LayerContent.Raster(sourceUri)),
            ),
            selectedLayerId = LayerIds.BACKGROUND,
        )

        private fun zIndex(id: String): Int {
            val index = LayerIds.Z_ORDER.indexOf(id)
            return if (index >= 0) index else LayerIds.Z_ORDER.size
        }
    }
}

/** Which opaque backdrop the UI chips should show as selected. */
enum class StudioBackdrop {
    ORIGINAL,
    TRANSPARENT,
    COLOR,
    IMAGE,
}

fun LayerDocument.backdrop(): StudioBackdrop {
    val fill = layer(LayerIds.FILL)
    val replacement = layer(LayerIds.REPLACEMENT)
    val background = layer(LayerIds.BACKGROUND)
    return when {
        fill?.visible == true -> StudioBackdrop.COLOR
        replacement?.visible == true -> StudioBackdrop.IMAGE
        background?.visible == true -> StudioBackdrop.ORIGINAL
        else -> StudioBackdrop.TRANSPARENT
    }
}
