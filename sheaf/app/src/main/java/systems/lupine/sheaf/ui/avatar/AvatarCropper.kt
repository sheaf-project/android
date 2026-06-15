package systems.lupine.sheaf.ui.avatar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Canvas as AndroidCanvas
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Rotate90DegreesCcw
import androidx.compose.material.icons.filled.Rotate90DegreesCw
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.exifinterface.media.ExifInterface
import androidx.compose.foundation.Canvas
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Shape of the crop preview window. Square avatars get a circular mask
 *  (the stored image is still the square crop; the circle is display
 *  framing); wide banners get a rounded rectangle. */
enum class CropShape { Circle, Rectangle }

/**
 * Full-screen modal image cropper. The user can pinch to zoom, drag to
 * pan, and rotate (two-finger twist, quarter-turn buttons, or the fine
 * slider) the source image inside a crop window of [aspectRatio]
 * (width / height). On confirm, the region inside the crop window is
 * exported as a PNG no larger than [outputLongestPx] on its longest edge
 * and handed back via [onConfirm].
 *
 * Mirrors the web cropper: zoom is "unlocked" below cover so a whole
 * image can be framed edge-to-edge (any area the image doesn't reach
 * renders transparent in the PNG), rather than forcing the source to
 * fill the frame. EXIF orientation is honoured on decode so portrait
 * photos arrive right-side-up.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageCropDialog(
    sourceUri: Uri,
    aspectRatio: Float,
    cropShape: CropShape,
    title: String,
    onCancel: () -> Unit,
    onConfirm: (pngBytes: ByteArray) -> Unit,
    outputLongestPx: Int = 1024,
) {
    val context = LocalContext.current
    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var isExporting by remember { mutableStateOf(false) }

    // Load source bitmap once, capped at 2048 px on the longest side so
    // gestures stay snappy. We crop from this in-memory bitmap on save
    // rather than re-decoding at full resolution; 2048 to 1024 output
    // still leaves the PNG re-encode as the limiting factor.
    LaunchedEffect(sourceUri) {
        withContext(Dispatchers.IO) {
            try {
                val bmp = loadOrientedBitmap(context, sourceUri, maxDim = 2048)
                withContext(Dispatchers.Main) { sourceBitmap = bmp }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    loadError = e.message ?: "Couldn't load image"
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = !isExporting,
            dismissOnClickOutside = false,
        ),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = {
                        IconButton(onClick = onCancel, enabled = !isExporting) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Black,
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White,
                    ),
                )

                val bmp = sourceBitmap
                // Set by "Use photo"; the LaunchedEffect encodes the PNG
                // off the main thread and hands the result back, so the
                // dialog survives recomposition during the encode and the
                // heavy work stays off the UI thread.
                var pendingExport by remember { mutableStateOf<CropTransform?>(null) }
                LaunchedEffect(pendingExport, bmp) {
                    val transform = pendingExport ?: return@LaunchedEffect
                    val source = bmp ?: return@LaunchedEffect
                    isExporting = true
                    val bytes = withContext(Dispatchers.Default) {
                        renderToPng(
                            source = source,
                            transform = transform,
                            outputLongestPx = outputLongestPx,
                        )
                    }
                    onConfirm(bytes)
                }
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        bmp != null -> CropCanvas(
                            source = bmp,
                            aspectRatio = aspectRatio,
                            cropShape = cropShape,
                            isExporting = isExporting,
                            onExport = { transform -> pendingExport = transform },
                        )
                        loadError != null -> Text(
                            loadError!!,
                            color = Color.White,
                            modifier = Modifier.padding(24.dp),
                        )
                        else -> CircularProgressIndicator(color = Color.White)
                    }
                }
            }
        }
    }
}

/** Avatar crop: square crop with a circular preview mask. */
@Composable
fun AvatarCropDialog(
    sourceUri: Uri,
    onCancel: () -> Unit,
    onConfirm: (pngBytes: ByteArray) -> Unit,
) = ImageCropDialog(
    sourceUri = sourceUri,
    aspectRatio = 1f,
    cropShape = CropShape.Circle,
    title = "Crop avatar",
    onCancel = onCancel,
    onConfirm = onConfirm,
)

/** Banner crop: wide 3:1 crop, matching the web banner aspect. */
@Composable
fun BannerCropDialog(
    sourceUri: Uri,
    onCancel: () -> Unit,
    onConfirm: (pngBytes: ByteArray) -> Unit,
) = ImageCropDialog(
    sourceUri = sourceUri,
    aspectRatio = 3f,
    cropShape = CropShape.Rectangle,
    title = "Crop banner",
    onCancel = onCancel,
    onConfirm = onConfirm,
)

/**
 * State + UI for the pannable / zoomable / rotatable image inside the
 * crop overlay. The [CropTransform] emitted to [onExport] is enough for
 * [renderToPng] to materialise the crop without re-deriving gesture
 * state.
 */
@Composable
private fun CropCanvas(
    source: Bitmap,
    aspectRatio: Float,
    cropShape: CropShape,
    isExporting: Boolean,
    onExport: (CropTransform) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val viewportW = constraints.maxWidth.toFloat()
        val viewportH = constraints.maxHeight.toFloat()

        // Inscribe the crop window in the viewport: cap its width at 92%
        // and its height at 70% (the lower band holds the rotate + confirm
        // controls), then fit the requested aspect inside that box.
        val maxCropW = viewportW * 0.92f
        val maxCropH = viewportH * 0.70f
        val cropW: Float
        val cropH: Float
        if (maxCropW / aspectRatio <= maxCropH) {
            cropW = maxCropW
            cropH = maxCropW / aspectRatio
        } else {
            cropH = maxCropH
            cropW = maxCropH * aspectRatio
        }

        val imgW = source.width.toFloat()
        val imgH = source.height.toFloat()

        // "cover" fills the frame; "contain" fits the whole image inside
        // it. We start at cover (so the default framing fills the window,
        // matching the old avatar behaviour) but allow zooming out below
        // contain so the user can letterbox a whole image into the frame.
        val coverScale = remember(imgW, imgH, cropW, cropH) { max(cropW / imgW, cropH / imgH) }
        val containScale = remember(imgW, imgH, cropW, cropH) { min(cropW / imgW, cropH / imgH) }
        val minScale = remember(containScale) { containScale * 0.5f }
        val maxScale = remember(coverScale) { coverScale * 6f }

        var scale by remember(coverScale) { mutableFloatStateOf(coverScale) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        var rotationDeg by remember { mutableFloatStateOf(0f) }

        // Clamp the pan so the crop centre always stays over the image
        // (its rotated bounding box): the user can push the image edge to
        // the centre of the frame, but can't lose it off-screen entirely.
        fun clampOffset(target: Offset, s: Float, deg: Float): Offset {
            val rad = Math.toRadians(deg.toDouble())
            val c = abs(cos(rad)).toFloat()
            val sn = abs(sin(rad)).toFloat()
            val halfW = (c * imgW + sn * imgH) / 2f * s
            val halfH = (sn * imgW + c * imgH) / 2f * s
            return Offset(
                x = target.x.coerceIn(-halfW, halfW),
                y = target.y.coerceIn(-halfH, halfH),
            )
        }

        val imageBitmap: ImageBitmap = remember(source) { source.asImageBitmap() }
        val centerX = viewportW / 2f
        val centerY = viewportH / 2f

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(imgW, imgH, cropW, cropH) {
                    detectTransformGestures { _, pan, zoom, rotationChange ->
                        if (isExporting) return@detectTransformGestures
                        val newScale = (scale * zoom).coerceIn(minScale, maxScale)
                        val newRotation = normalizeDeg(rotationDeg + rotationChange)
                        val newOffset = clampOffset(offset + pan, newScale, newRotation)
                        scale = newScale
                        rotationDeg = newRotation
                        offset = newOffset
                    }
                },
        ) {
            // Draw the image scaled, then rotated, then translated about
            // its own centre, the same order renderToPng reverses into a
            // matrix, so what's framed is exactly what's exported.
            translate(left = centerX + offset.x, top = centerY + offset.y) {
                rotate(degrees = rotationDeg, pivot = Offset.Zero) {
                    scale(scale, pivot = Offset.Zero) {
                        drawImageCentered(imageBitmap, imgW, imgH)
                    }
                }
            }

            drawCropOverlay(
                centerX = centerX,
                centerY = centerY,
                cropW = cropW,
                cropH = cropH,
                cropShape = cropShape,
                outlineColor = Color.White.copy(alpha = 0.85f),
            )
        }

        // Rotate + confirm controls anchored at the bottom of the viewport.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                IconButton(
                    onClick = { rotationDeg = normalizeDeg(prevQuarter(rotationDeg)) },
                    enabled = !isExporting,
                ) {
                    Icon(
                        Icons.Default.Rotate90DegreesCcw,
                        contentDescription = "Rotate left",
                        tint = Color.White,
                    )
                }
                Slider(
                    value = rotationDeg,
                    onValueChange = { if (!isExporting) rotationDeg = it },
                    valueRange = -180f..180f,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
                IconButton(
                    onClick = { rotationDeg = normalizeDeg(nextQuarter(rotationDeg)) },
                    enabled = !isExporting,
                ) {
                    Icon(
                        Icons.Default.Rotate90DegreesCw,
                        contentDescription = "Rotate right",
                        tint = Color.White,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = {
                    onExport(
                        CropTransform(
                            scale = scale,
                            offset = offset,
                            rotationDeg = rotationDeg,
                            cropW = cropW,
                            cropH = cropH,
                        )
                    )
                },
                enabled = !isExporting,
            ) {
                Text(if (isExporting) "Saving…" else "Use photo")
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Pinch to zoom, drag to reposition, twist or use the slider to rotate",
                color = Color.White.copy(alpha = 0.65f),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
    }
}

/**
 * Snapshot of the cropper state at confirm time. Self-contained so
 * [renderToPng] doesn't need any Composable state.
 */
private data class CropTransform(
    val scale: Float,
    val offset: Offset,
    val rotationDeg: Float,
    val cropW: Float,
    val cropH: Float,
)

/** Normalise a rotation into (-180, 180] for a tidy slider reading. */
private fun normalizeDeg(deg: Float): Float {
    var d = deg % 360f
    if (d > 180f) d -= 360f
    if (d <= -180f) d += 360f
    return d
}

/** Snap to the next 90 degree step. The +1 nudge means landing exactly on
 *  a multiple advances to the next one rather than sticking (matches web). */
private fun nextQuarter(deg: Float): Float = (kotlin.math.ceil((deg + 1f) / 90f)) * 90f

/** Snap to the previous 90 degree step (mirror of [nextQuarter]). */
private fun prevQuarter(deg: Float): Float = (kotlin.math.floor((deg - 1f) / 90f)) * 90f

/** Draw image centered on (0,0); the parent transform handles placement. */
private fun DrawScope.drawImageCentered(
    image: ImageBitmap,
    imgW: Float,
    imgH: Float,
) {
    translate(left = -imgW / 2f, top = -imgH / 2f) {
        drawImage(image)
    }
}

/** Scrim + outline for the crop region (circular or rounded-rect). */
private fun DrawScope.drawCropOverlay(
    centerX: Float,
    centerY: Float,
    cropW: Float,
    cropH: Float,
    cropShape: CropShape,
    outlineColor: Color,
) {
    val cropLeft = centerX - cropW / 2f
    val cropTop = centerY - cropH / 2f
    val rect = androidx.compose.ui.geometry.Rect(
        left = cropLeft,
        top = cropTop,
        right = cropLeft + cropW,
        bottom = cropTop + cropH,
    )
    val corner = if (cropShape == CropShape.Circle) cropW / 2f else 16f

    val fullPath = Path().apply {
        addRect(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height))
    }
    val holePath = Path().apply {
        if (cropShape == CropShape.Circle) {
            addOval(rect)
        } else {
            addRoundRect(
                androidx.compose.ui.geometry.RoundRect(
                    rect = rect,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner, corner),
                )
            )
        }
    }
    val scrim = Path().apply { op(fullPath, holePath, PathOperation.Difference) }
    drawPath(scrim, color = Color.Black.copy(alpha = 0.65f))

    // Crisp outline on the crop edge.
    if (cropShape == CropShape.Circle) {
        drawCircle(
            color = outlineColor,
            radius = cropW / 2f,
            center = Offset(centerX, centerY),
            style = Stroke(width = 2f),
        )
    } else {
        drawPath(holePath, color = outlineColor, style = Stroke(width = 2f))
    }
}

/**
 * Decode [uri] into a Bitmap already rotated per its EXIF orientation
 * flag, downsampled so its longest side is at most [maxDim] pixels.
 *
 * `BitmapFactory.decodeStream` only rotates the pixel buffer itself, so
 * portrait JPEGs (landscape pixels + "rotate 90" in EXIF) come back
 * sideways without this fix-up.
 */
private fun loadOrientedBitmap(context: Context, uri: Uri, maxDim: Int): Bitmap {
    val cr = context.contentResolver
    // First pass: bounds-only decode to read intrinsic dimensions.
    // decodeStream with inJustDecodeBounds=true always returns null; the
    // dimensions land in the Options object as a side effect, so we check
    // openInputStream itself for null (the real "couldn't open" signal).
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    val boundsStream = cr.openInputStream(uri) ?: error("Couldn't open image")
    boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
    val longest = max(bounds.outWidth, bounds.outHeight).takeIf { it > 0 }
        ?: error("Couldn't read image dimensions")
    var sampleSize = 1
    while (longest / sampleSize > maxDim) sampleSize *= 2

    val decodeStream = cr.openInputStream(uri) ?: error("Couldn't open image")
    val decoded = decodeStream.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inMutable = false
        })
    } ?: error("Couldn't decode image")

    val exifStream = cr.openInputStream(uri)
    val orientation = exifStream?.use {
        ExifInterface(it).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL,
        )
    } ?: ExifInterface.ORIENTATION_NORMAL

    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.postRotate(90f); matrix.preScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.postRotate(270f); matrix.preScale(-1f, 1f)
        }
        else -> return decoded
    }
    return Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        .also { if (it !== decoded) decoded.recycle() }
}

/**
 * Materialise the crop region as a PNG no larger than [outputLongestPx]
 * on its longest edge. Builds the same transform the cropper showed on
 * screen (scale, then rotate, then translate about the image centre),
 * then maps the crop window onto the output bitmap. Areas the image
 * doesn't reach stay transparent (the output is ARGB_8888 and we encode
 * PNG), which is how a whole image zoomed out below "cover" keeps its
 * letterbox.
 */
private fun renderToPng(
    source: Bitmap,
    transform: CropTransform,
    outputLongestPx: Int,
): ByteArray {
    val aspect = transform.cropW / transform.cropH
    val outW: Int
    val outH: Int
    if (aspect >= 1f) {
        outW = outputLongestPx
        outH = (outputLongestPx / aspect).roundToInt().coerceAtLeast(1)
    } else {
        outH = outputLongestPx
        outW = (outputLongestPx * aspect).roundToInt().coerceAtLeast(1)
    }

    // screen px -> output px. cropW maps to outW (cropH maps to outH at the
    // same ratio since the output keeps the crop aspect).
    val k = outW / transform.cropW

    val matrix = Matrix().apply {
        // image local -> centred at origin
        postTranslate(-source.width / 2f, -source.height / 2f)
        postScale(transform.scale, transform.scale)
        postRotate(transform.rotationDeg)
        // place relative to the crop window's top-left (the viewport-centre
        // and crop-centre terms cancel to cropW/2 + offset)
        postTranslate(transform.cropW / 2f + transform.offset.x, transform.cropH / 2f + transform.offset.y)
        postScale(k, k)
    }

    val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(out)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
    canvas.drawBitmap(source, matrix, paint)

    val stream = ByteArrayOutputStream(128 * 1024)
    out.compress(Bitmap.CompressFormat.PNG, 100, stream)
    out.recycle()
    return stream.toByteArray()
}
