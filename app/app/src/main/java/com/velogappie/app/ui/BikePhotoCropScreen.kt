package com.velogappie.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import com.velogappie.app.R
import kotlin.math.min
import kotlin.math.roundToInt

private const val CROP_ASPECT = 16f / 9f

private enum class CropDragMode { MOVE, RESIZE_TL, RESIZE_TR, RESIZE_BL, RESIZE_BR }

@Composable
fun BikePhotoCropScreen(
    bitmap: Bitmap,
    onCropConfirmed: (Bitmap) -> Unit,
    onCancel: () -> Unit,
) {
    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
    var canvasSize by remember { mutableStateOf(Size.Zero) }

    val imageRect = remember(canvasSize, bitmap) {
        if (canvasSize == Size.Zero) return@remember Rect.Zero
        val imgAspect = bitmap.width.toFloat() / bitmap.height
        val canvasAspect = canvasSize.width / canvasSize.height
        if (imgAspect > canvasAspect) {
            val h = canvasSize.width / imgAspect
            val top = (canvasSize.height - h) / 2f
            Rect(0f, top, canvasSize.width, top + h)
        } else {
            val w = canvasSize.height * imgAspect
            val left = (canvasSize.width - w) / 2f
            Rect(left, 0f, left + w, canvasSize.height)
        }
    }

    var cropOffset by remember { mutableStateOf(Offset.Zero) }
    var cropWidth by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(imageRect) {
        if (imageRect != Rect.Zero) {
            val maxW = imageRect.width * 0.9f
            val maxH = imageRect.height * 0.9f
            val w = min(maxW, maxH * CROP_ASPECT)
            cropWidth = w
            val h = w / CROP_ASPECT
            cropOffset = Offset(
                imageRect.left + (imageRect.width - w) / 2f,
                imageRect.top + (imageRect.height - h) / 2f,
            )
        }
    }

    val cropHeight = cropWidth / CROP_ASPECT

    Column(
        Modifier.fillMaxSize().background(Color.Black),
    ) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .onSizeChanged { canvasSize = it.toSize() }
                .pointerInput(imageRect) {
                    if (imageRect == Rect.Zero) return@pointerInput
                    val handlePx = 48.dp.toPx()
                    val minW = imageRect.width * 0.15f

                    fun near(pos: Offset, corner: Offset) =
                        (pos - corner).getDistance() < handlePx

                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val cr = Rect(
                            cropOffset.x, cropOffset.y,
                            cropOffset.x + cropWidth, cropOffset.y + cropWidth / CROP_ASPECT,
                        )
                        val mode = when {
                            near(down.position, cr.topLeft) -> CropDragMode.RESIZE_TL
                            near(down.position, Offset(cr.right, cr.top)) -> CropDragMode.RESIZE_TR
                            near(down.position, Offset(cr.left, cr.bottom)) -> CropDragMode.RESIZE_BL
                            near(down.position, Offset(cr.right, cr.bottom)) -> CropDragMode.RESIZE_BR
                            else -> CropDragMode.MOVE
                        }
                        down.consume()

                        var prevSpread = 0f

                        while (true) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.isEmpty()) break

                            if (pressed.size >= 2) {
                                val centroid = Offset(
                                    pressed.map { it.position.x }.average().toFloat(),
                                    pressed.map { it.position.y }.average().toFloat(),
                                )
                                val spread = pressed
                                    .map { (it.position - centroid).getDistance() }
                                    .average().toFloat()
                                if (prevSpread > 0f && spread > 0f) {
                                    val zoom = spread / prevSpread
                                    val newW = (cropWidth * zoom).coerceIn(minW, imageRect.width)
                                    val newH = newW / CROP_ASPECT
                                    if (newH <= imageRect.height) {
                                        val cx = cropOffset.x + cropWidth / 2f
                                        val cy = cropOffset.y + cropHeight / 2f
                                        cropWidth = newW
                                        cropOffset = Offset(
                                            (cx - newW / 2f).coerceIn(imageRect.left, imageRect.right - newW),
                                            (cy - newW / CROP_ASPECT / 2f).coerceIn(imageRect.top, imageRect.bottom - newW / CROP_ASPECT),
                                        )
                                    }
                                }
                                prevSpread = spread
                            } else {
                                prevSpread = 0f
                                val change = pressed.first()
                                val delta = change.position - change.previousPosition

                                when (mode) {
                                    CropDragMode.MOVE -> {
                                        val h = cropWidth / CROP_ASPECT
                                        cropOffset = Offset(
                                            (cropOffset.x + delta.x).coerceIn(imageRect.left, imageRect.right - cropWidth),
                                            (cropOffset.y + delta.y).coerceIn(imageRect.top, imageRect.bottom - h),
                                        )
                                    }
                                    CropDragMode.RESIZE_BR -> {
                                        val newW = (cropWidth + delta.x).coerceIn(minW, imageRect.right - cropOffset.x)
                                        val newH = newW / CROP_ASPECT
                                        if (cropOffset.y + newH <= imageRect.bottom) cropWidth = newW
                                    }
                                    CropDragMode.RESIZE_TL -> {
                                        val anchorR = cropOffset.x + cropWidth
                                        val anchorB = cropOffset.y + cropWidth / CROP_ASPECT
                                        val newW = (cropWidth - delta.x).coerceIn(minW, anchorR - imageRect.left)
                                        val newH = newW / CROP_ASPECT
                                        val newL = anchorR - newW
                                        val newT = anchorB - newH
                                        if (newL >= imageRect.left && newT >= imageRect.top) {
                                            cropWidth = newW
                                            cropOffset = Offset(newL, newT)
                                        }
                                    }
                                    CropDragMode.RESIZE_TR -> {
                                        val anchorB = cropOffset.y + cropWidth / CROP_ASPECT
                                        val newW = (cropWidth + delta.x).coerceIn(minW, imageRect.right - cropOffset.x)
                                        val newH = newW / CROP_ASPECT
                                        val newT = anchorB - newH
                                        if (newT >= imageRect.top) {
                                            cropWidth = newW
                                            cropOffset = Offset(cropOffset.x, newT)
                                        }
                                    }
                                    CropDragMode.RESIZE_BL -> {
                                        val anchorR = cropOffset.x + cropWidth
                                        val newW = (cropWidth - delta.x).coerceIn(minW, anchorR - imageRect.left)
                                        val newH = newW / CROP_ASPECT
                                        val newL = anchorR - newW
                                        if (newL >= imageRect.left && cropOffset.y + newH <= imageRect.bottom) {
                                            cropWidth = newW
                                            cropOffset = Offset(newL, cropOffset.y)
                                        }
                                    }
                                }
                            }

                            event.changes.forEach { it.consume() }
                        }
                    }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawImage(
                    image = imageBitmap,
                    dstOffset = IntOffset(imageRect.left.roundToInt(), imageRect.top.roundToInt()),
                    dstSize = IntSize(imageRect.width.roundToInt(), imageRect.height.roundToInt()),
                )

                val crop = Rect(cropOffset.x, cropOffset.y, cropOffset.x + cropWidth, cropOffset.y + cropHeight)
                val scrim = Color.Black.copy(alpha = 0.6f)
                drawRect(scrim, Offset.Zero, Size(size.width, crop.top))
                drawRect(scrim, Offset(0f, crop.bottom), Size(size.width, size.height - crop.bottom))
                drawRect(scrim, Offset(0f, crop.top), Size(crop.left, crop.height))
                drawRect(scrim, Offset(crop.right, crop.top), Size(size.width - crop.right, crop.height))

                drawRect(Color.White, crop.topLeft, crop.size, style = Stroke(2.dp.toPx()))

                val bracketLen = 24.dp.toPx()
                val bracketStroke = Stroke(4.dp.toPx())
                val corners = listOf(
                    crop.topLeft to listOf(Offset(bracketLen, 0f), Offset(0f, bracketLen)),
                    Offset(crop.right, crop.top) to listOf(Offset(-bracketLen, 0f), Offset(0f, bracketLen)),
                    Offset(crop.left, crop.bottom) to listOf(Offset(bracketLen, 0f), Offset(0f, -bracketLen)),
                    Offset(crop.right, crop.bottom) to listOf(Offset(-bracketLen, 0f), Offset(0f, -bracketLen)),
                )
                for ((corner, dirs) in corners) {
                    for (dir in dirs) {
                        drawLine(Color.White, corner, corner + dir, bracketStroke.width)
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.photo_crop_cancel), color = Color.White)
            }
            Button(onClick = {
                if (imageRect == Rect.Zero || cropWidth <= 0f) return@Button
                val scaleX = bitmap.width.toFloat() / imageRect.width
                val scaleY = bitmap.height.toFloat() / imageRect.height
                val srcX = ((cropOffset.x - imageRect.left) * scaleX).roundToInt().coerceIn(0, bitmap.width - 1)
                val srcY = ((cropOffset.y - imageRect.top) * scaleY).roundToInt().coerceIn(0, bitmap.height - 1)
                val srcW = (cropWidth * scaleX).roundToInt().coerceAtMost(bitmap.width - srcX)
                val srcH = (cropHeight * scaleY).roundToInt().coerceAtMost(bitmap.height - srcY)
                if (srcW > 0 && srcH > 0) {
                    val cropped = Bitmap.createBitmap(bitmap, srcX, srcY, srcW, srcH)
                    val scaled = Bitmap.createScaledBitmap(cropped, 1280, (1280 / CROP_ASPECT).roundToInt(), true)
                    if (cropped !== scaled) cropped.recycle()
                    onCropConfirmed(scaled)
                }
            }) {
                Text(stringResource(R.string.photo_crop_save))
            }
        }
    }
}
