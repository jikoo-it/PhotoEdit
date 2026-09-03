package com.momi.watermarker.presentation.editor.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import com.momi.watermarker.domain.model.CropShape
import com.momi.watermarker.domain.model.NormalizedRect
import com.momi.watermarker.domain.model.squircleUnitPoints
import kotlin.math.min

/**
 * Full-screen crop UI: the picked image is shown fit-to-screen with a draggable,
 * resizable crop rectangle over it. A shape can be chosen (rectangle, circle,
 * rounded, squircle); the crop rect is its bounding box. Confirming reports the
 * crop as a [NormalizedRect] (fractions of the image) plus the [CropShape],
 * which the caller applies to the full-resolution bitmap.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageCropperScreen(
    imageUri: String,
    onConfirm: (NormalizedRect, CropShape) -> Unit,
    onCancel: () -> Unit,
    title: String = "Crop watermark",
    /**
     * When false, the shape picker is hidden and the crop is always a plain
     * rectangle — used for cropping the main photo, where a masked (transparent)
     * shape wouldn't make sense.
     */
    showShapeSelector: Boolean = true,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
        val painter = rememberAsyncImagePainter(model = imageUri)
        val state = painter.state

        // The displayed image rect and current crop rect, both in container
        // pixels. Updated from effects/gestures (never written during layout).
        var imageRect by remember { mutableStateOf<Rect?>(null) }
        var cropRect by remember { mutableStateOf<Rect?>(null) }
        var shape by remember { mutableStateOf(CropShape.DEFAULT) }

        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text(title, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Filled.Close, contentDescription = "Cancel", tint = Color.White)
                    }
                },
                actions = {
                    TextButton(
                        enabled = cropRect != null && imageRect != null,
                        onClick = {
                            val crop = cropRect
                            val image = imageRect
                            if (crop != null && image != null && image.width > 0f && image.height > 0f) {
                                onConfirm(toNormalized(crop, image), shape)
                            }
                        },
                    ) {
                        Text("Done", color = Color.White)
                    }
                },
            )

            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                val density = LocalDensity.current
                val containerW = with(density) { maxWidth.toPx() }
                val containerH = with(density) { maxHeight.toPx() }
                val handleTouchPx = with(density) { 28.dp.toPx() }
                val minCropPx = with(density) { 48.dp.toPx() }

                // Always compose the image so Coil actually starts (and finishes)
                // the async load — its painter only leaves the Loading state once
                // it is drawn.
                Image(
                    painter = painter,
                    contentDescription = "Image to crop",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )

                val success = state as? AsyncImagePainter.State.Success
                val drawable = success?.result?.drawable
                val iw = (drawable?.intrinsicWidth ?: 0).toFloat()
                val ih = (drawable?.intrinsicHeight ?: 0).toFloat()

                when {
                    success != null && iw > 0f && ih > 0f -> {
                        val scale = min(containerW / iw, containerH / ih)
                        val dispW = iw * scale
                        val dispH = ih * scale
                        val offX = (containerW - dispW) / 2f
                        val offY = (containerH - dispH) / 2f
                        val image = Rect(offX, offY, offX + dispW, offY + dispH)

                        // Publish the image rect and (re)seed the crop to an 85%
                        // inset whenever the layout or image changes.
                        LaunchedEffect(image) {
                            imageRect = image
                            val inset = min(dispW, dispH) * 0.075f
                            cropRect = Rect(
                                image.left + inset,
                                image.top + inset,
                                image.right - inset,
                                image.bottom - inset,
                            )
                        }

                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(containerW, containerH, iw, ih) {
                                    var handle = CropHandle.NONE
                                    detectDragGestures(
                                        onDragStart = { pos ->
                                            handle = cropRect?.let { handleAt(pos, it, handleTouchPx) }
                                                ?: CropHandle.NONE
                                        },
                                        onDragEnd = { handle = CropHandle.NONE },
                                        onDragCancel = { handle = CropHandle.NONE },
                                    ) { change, drag ->
                                        change.consume()
                                        val crop = cropRect
                                        val img = imageRect
                                        if (crop != null && img != null && handle != CropHandle.NONE) {
                                            cropRect = resize(crop, img, handle, drag, minCropPx)
                                        }
                                    }
                                },
                        ) {
                            val crop = cropRect ?: return@Canvas
                            val outline = cropOutline(shape, crop)

                            // Dim everything outside the crop shape.
                            val dim = Color.Black.copy(alpha = 0.55f)
                            clipPath(outline, clipOp = ClipOp.Difference) {
                                drawRect(dim, topLeft = Offset(0f, 0f), size = size)
                            }

                            // Shape border + corner handles (at the bounding box).
                            drawPath(outline, color = Color.White, style = Stroke(width = 2.dp.toPx()))
                            val r = 8.dp.toPx()
                            listOf(
                                Offset(crop.left, crop.top),
                                Offset(crop.right, crop.top),
                                Offset(crop.left, crop.bottom),
                                Offset(crop.right, crop.bottom),
                            ).forEach { corner -> drawCircle(Color.White, radius = r, center = corner) }
                        }
                    }

                    state is AsyncImagePainter.State.Error -> Text(
                        "Couldn't load image.",
                        color = Color.White,
                        modifier = Modifier.align(Alignment.Center),
                    )

                    else -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.White)
                    }
                }
            }

            if (showShapeSelector) {
                ShapeSelector(selected = shape, onSelect = { shape = it })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShapeSelector(selected: CropShape, onSelect: (CropShape) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CropShape.entries.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(option.displayName) },
            )
        }
    }
}

private enum class CropHandle { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, BODY, NONE }

/** The [shape] outline inscribed in the crop bounding box [crop]. */
private fun cropOutline(shape: CropShape, crop: Rect): Path = Path().apply {
    when (shape) {
        CropShape.RECTANGLE -> addRect(crop)
        CropShape.CIRCLE -> addOval(crop)
        CropShape.ROUNDED -> {
            val radius = min(crop.width, crop.height) * CropShape.ROUNDED_CORNER_FRACTION
            addRoundRect(RoundRect(crop, CornerRadius(radius, radius)))
        }
        CropShape.SQUIRCLE -> {
            squircleUnitPoints().forEachIndexed { i, (ux, uy) ->
                val px = crop.left + ux * crop.width
                val py = crop.top + uy * crop.height
                if (i == 0) moveTo(px, py) else lineTo(px, py)
            }
            close()
        }
    }
}

/** Which handle (if any) the pointer grabbed at [pos]. */
private fun handleAt(pos: Offset, crop: Rect, touch: Float): CropHandle {
    fun near(corner: Offset) = (pos - corner).getDistance() <= touch
    return when {
        near(Offset(crop.left, crop.top)) -> CropHandle.TOP_LEFT
        near(Offset(crop.right, crop.top)) -> CropHandle.TOP_RIGHT
        near(Offset(crop.left, crop.bottom)) -> CropHandle.BOTTOM_LEFT
        near(Offset(crop.right, crop.bottom)) -> CropHandle.BOTTOM_RIGHT
        crop.contains(pos) -> CropHandle.BODY
        else -> CropHandle.NONE
    }
}

/** Applies a drag to the crop rect, clamped to [image] with a minimum size. */
private fun resize(crop: Rect, image: Rect, handle: CropHandle, drag: Offset, minSize: Float): Rect {
    var l = crop.left
    var t = crop.top
    var r = crop.right
    var b = crop.bottom
    when (handle) {
        CropHandle.TOP_LEFT -> {
            l = (l + drag.x).coerceIn(image.left, r - minSize)
            t = (t + drag.y).coerceIn(image.top, b - minSize)
        }
        CropHandle.TOP_RIGHT -> {
            r = (r + drag.x).coerceIn(l + minSize, image.right)
            t = (t + drag.y).coerceIn(image.top, b - minSize)
        }
        CropHandle.BOTTOM_LEFT -> {
            l = (l + drag.x).coerceIn(image.left, r - minSize)
            b = (b + drag.y).coerceIn(t + minSize, image.bottom)
        }
        CropHandle.BOTTOM_RIGHT -> {
            r = (r + drag.x).coerceIn(l + minSize, image.right)
            b = (b + drag.y).coerceIn(t + minSize, image.bottom)
        }
        CropHandle.BODY -> {
            val dx = drag.x.coerceIn(image.left - l, image.right - r)
            val dy = drag.y.coerceIn(image.top - t, image.bottom - b)
            l += dx; r += dx; t += dy; b += dy
        }
        CropHandle.NONE -> Unit
    }
    return Rect(l, t, r, b)
}

private fun toNormalized(crop: Rect, image: Rect): NormalizedRect {
    fun frac(value: Float, origin: Float, span: Float) = ((value - origin) / span).coerceIn(0f, 1f)
    val left = frac(crop.left, image.left, image.width)
    val top = frac(crop.top, image.top, image.height)
    val right = frac(crop.right, image.left, image.width)
    val bottom = frac(crop.bottom, image.top, image.height)
    return NormalizedRect(
        left = left,
        top = top,
        // Guard against a zero-area rect from rounding at the edges.
        right = right.coerceAtLeast(left + 0.001f).coerceAtMost(1f),
        bottom = bottom.coerceAtLeast(top + 0.001f).coerceAtMost(1f),
    )
}
