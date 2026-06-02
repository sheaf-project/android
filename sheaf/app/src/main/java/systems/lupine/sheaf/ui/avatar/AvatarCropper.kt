package systems.lupine.sheaf.ui.avatar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import kotlin.math.max
import kotlin.math.min

/**
 * Full-screen modal cropper for avatar uploads. The user can pinch to
 * zoom and drag to pan the source image; a circular preview window
 * shows what the final avatar will look like. On confirm, the visible
 * region inside the crop window is exported as a square JPEG of
 * [outputSizePx] x [outputSizePx] and handed back via [onConfirm].
 *
 * Replaces the previous "pick image, upload as-is" flow that left the
 * server (and the display layer) to squash non-square inputs.
 *
 * EXIF orientation is honoured on decode so portrait photos arrive in
 * the cropper right-side-up rather than sideways.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvatarCropDialog(
    sourceUri: Uri,
    onCancel: () -> Unit,
    onConfirm: (jpegBytes: ByteArray) -> Unit,
    outputSizePx: Int = 512,
    jpegQuality: Int = 90,
) {
    val context = LocalContext.current
    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var isExporting by remember { mutableStateOf(false) }

    // Load source bitmap once. We cap at 2048 px on the longest side
    // for the live cropper to keep gesture latency snappy on a phone;
    // we crop from the in-memory bitmap on save rather than re-decoding
    // from the URI at full resolution. 2048 -> 512 output still has
    // 4x headroom in each dim, so the 90%-quality JPEG output is the
    // limiting factor, not source resolution.
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
                    title = { Text("Crop avatar") },
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
                // Triggered by the user pressing "Use photo". A non-null
                // value here causes the LaunchedEffect below to encode
                // the JPEG off the main thread and hand the result back
                // via onConfirm. Doing it that way (rather than launching
                // a coroutine inside the Button's onClick) means the
                // dialog correctly handles being recomposed during the
                // encode and that the heavy work stays off the UI thread.
                var pendingExport by remember { mutableStateOf<CropTransform?>(null) }
                LaunchedEffect(pendingExport, bmp) {
                    val transform = pendingExport ?: return@LaunchedEffect
                    val source = bmp ?: return@LaunchedEffect
                    isExporting = true
                    val bytes = withContext(Dispatchers.Default) {
                        renderToJpeg(
                            source = source,
                            transform = transform,
                            outputSizePx = outputSizePx,
                            quality = jpegQuality,
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

/**
 * State + UI for the pannable/zoomable image inside the crop overlay.
 * The "transform" emitted to [onExport] is enough information for
 * [renderToJpeg] to materialise a cropped Bitmap without re-deriving
 * any of the gesture state.
 */
@Composable
private fun CropCanvas(
    source: Bitmap,
    isExporting: Boolean,
    onExport: (CropTransform) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val viewportW = constraints.maxWidth.toFloat()
        val viewportH = constraints.maxHeight.toFloat()
        // Crop window is a square inscribed in the viewport with a
        // sensible margin so the user can see the parts of the image
        // that won't make it into the avatar.
        val cropPx = min(viewportW, viewportH) * 0.85f

        val imgW = source.width.toFloat()
        val imgH = source.height.toFloat()

        // Minimum scale: image must always cover the crop window in
        // both dimensions, otherwise the user could pan to white space.
        val minScale = remember(imgW, imgH, cropPx) {
            max(cropPx / imgW, cropPx / imgH)
        }
        val maxScale = remember(minScale) { minScale * 6f }

        var scale by remember { mutableFloatStateOf(minScale) }
        var offset by remember { mutableStateOf(Offset.Zero) }

        // Clamp offset so the image always covers the crop window.
        // At any scale, the maximum offset in each axis is half the
        // (scaled-image - crop) overhang in that axis.
        fun clampOffset(target: Offset, s: Float): Offset {
            val maxOffX = max(0f, (imgW * s - cropPx) / 2f)
            val maxOffY = max(0f, (imgH * s - cropPx) / 2f)
            return Offset(
                x = target.x.coerceIn(-maxOffX, maxOffX),
                y = target.y.coerceIn(-maxOffY, maxOffY),
            )
        }

        val imageBitmap: ImageBitmap = remember(source) { source.asImageBitmap() }
        val centerX = viewportW / 2f
        val centerY = viewportH / 2f

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(imgW, imgH, cropPx) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        if (isExporting) return@detectTransformGestures
                        val newScale = (scale * zoom).coerceIn(minScale, maxScale)
                        // When the user pinches, the effective movement
                        // is the gesture pan plus a scale-driven offset
                        // change. We treat zoom as centered on the
                        // viewport for simplicity — the crop overlay
                        // is centered too, so this reads as "zoom
                        // toward what's framed" which is what users
                        // expect for a crop UI.
                        val newOffset = clampOffset(offset + pan, newScale)
                        scale = newScale
                        offset = newOffset
                    }
                },
        ) {
            // Draw the image transformed by the current scale + offset,
            // centered in the viewport.
            translate(left = centerX + offset.x, top = centerY + offset.y) {
                scale(scale, pivot = Offset.Zero) {
                    drawImageCentered(imageBitmap, imgW, imgH)
                }
            }

            // Scrim outside the crop window. We draw the dim layer as
            // a path with an even-odd fill that excludes a circular
            // hole at the crop region — that gives a clean circular
            // preview without any blending tricks.
            drawCropOverlay(
                centerX = centerX,
                centerY = centerY,
                cropPx = cropPx,
                outlineColor = Color.White.copy(alpha = 0.85f),
            )
        }

        // Confirm button anchored at the bottom of the viewport.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Button(
                onClick = {
                    val transform = CropTransform(
                        scale = scale,
                        offset = offset,
                        viewportCenter = Offset(centerX, centerY),
                        cropPx = cropPx,
                    )
                    onExport(transform)
                },
                enabled = !isExporting,
            ) {
                Text(if (isExporting) "Saving…" else "Use photo")
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Pinch to zoom, drag to reposition",
                color = Color.White.copy(alpha = 0.65f),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/**
 * Snapshot of the cropper state at confirm time. Self-contained so
 * [renderToJpeg] doesn't need any of the Composable state.
 */
private data class CropTransform(
    val scale: Float,
    val offset: Offset,
    val viewportCenter: Offset,
    val cropPx: Float,
)

/** Draw image centered on (0,0); the parent translate handles positioning. */
private fun DrawScope.drawImageCentered(
    image: ImageBitmap,
    imgW: Float,
    imgH: Float,
) {
    translate(left = -imgW / 2f, top = -imgH / 2f) {
        drawImage(image)
    }
}

/** Scrim + circle outline for the crop region. */
private fun DrawScope.drawCropOverlay(
    centerX: Float,
    centerY: Float,
    cropPx: Float,
    outlineColor: Color,
) {
    val cropLeft = centerX - cropPx / 2f
    val cropTop = centerY - cropPx / 2f
    val radius = cropPx / 2f

    val fullPath = Path().apply {
        addRect(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height))
    }
    val holePath = Path().apply {
        addOval(
            androidx.compose.ui.geometry.Rect(
                left = cropLeft,
                top = cropTop,
                right = cropLeft + cropPx,
                bottom = cropTop + cropPx,
            )
        )
    }
    val scrim = Path().apply {
        op(fullPath, holePath, PathOperation.Difference)
    }
    drawPath(scrim, color = Color.Black.copy(alpha = 0.65f))

    // Crisp outline ring on the crop edge.
    drawCircle(
        color = outlineColor,
        radius = radius,
        center = Offset(centerX, centerY),
        style = Stroke(width = 2f),
    )
}

/**
 * Decode [uri] into a Bitmap that's already rotated according to its
 * EXIF orientation flag, downsampled so its longest side is at most
 * [maxDim] pixels.
 *
 * `BitmapFactory.decodeStream` only ever rotates the pixel buffer
 * itself, so portrait-mode JPEGs (which store landscape pixels +
 * "rotate 90" in EXIF) come back sideways without this fix-up.
 */
private fun loadOrientedBitmap(context: Context, uri: Uri, maxDim: Int): Bitmap {
    val cr = context.contentResolver
    // First pass: bounds-only decode to read intrinsic dimensions.
    // decodeStream with inJustDecodeBounds=true always returns null —
    // the dimensions land in the Options object as a side effect — so
    // we have to check `openInputStream` itself for null (the actual
    // "couldn't open" signal) rather than the use{} result.
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
 * Materialise the current crop region as a square JPEG of
 * [outputSizePx] x [outputSizePx]. Reverses the gesture math: the
 * crop window in screen coords is translated back into source-image
 * pixel coords, that rect is extracted from the source bitmap, and
 * the result is scaled to the output size.
 *
 * Bounds-clamped because in degenerate cases (e.g. tiny source +
 * extreme scale) floating-point drift can push the computed source
 * rect a pixel past the edge; we'd rather render slightly inside
 * than throw IndexOutOfBoundsException.
 */
private fun renderToJpeg(
    source: Bitmap,
    transform: CropTransform,
    outputSizePx: Int,
    quality: Int,
): ByteArray {
    val imgW = source.width.toFloat()
    val imgH = source.height.toFloat()
    val s = transform.scale
    val crop = transform.cropPx

    // The source-pixel rect that lands inside the crop window.
    // Derivation:
    //   image is drawn centered at (viewportCenter + offset), scaled
    //   by s. The crop window is a square of side `crop` centered at
    //   viewportCenter. So in pre-scale image coords (origin at image
    //   top-left), the crop window's top-left is:
    //       (imgW*s/2 - crop/2 - offset.x) / s   in x
    //       (imgH*s/2 - crop/2 - offset.y) / s   in y
    val srcLeft = ((imgW * s / 2f - crop / 2f - transform.offset.x) / s)
        .coerceIn(0f, imgW - 1f)
    val srcTop = ((imgH * s / 2f - crop / 2f - transform.offset.y) / s)
        .coerceIn(0f, imgH - 1f)
    val srcSize = (crop / s).coerceAtMost(min(imgW - srcLeft, imgH - srcTop))

    val cropped = Bitmap.createBitmap(
        source,
        srcLeft.toInt(),
        srcTop.toInt(),
        srcSize.toInt().coerceAtLeast(1),
        srcSize.toInt().coerceAtLeast(1),
    )
    val scaled = if (cropped.width == outputSizePx && cropped.height == outputSizePx) {
        cropped
    } else {
        Bitmap.createScaledBitmap(cropped, outputSizePx, outputSizePx, true).also {
            if (it !== cropped) cropped.recycle()
        }
    }
    val out = ByteArrayOutputStream(64 * 1024)
    scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
    if (scaled !== source) scaled.recycle()
    return out.toByteArray()
}
