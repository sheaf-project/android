package systems.lupine.sheaf.wear.tile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import androidx.wear.protolayout.ResourceBuilders
import okhttp3.OkHttpClient
import okhttp3.Request
import systems.lupine.sheaf.wear.data.WearMember
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit

/**
 * Tile avatar pipeline. Wear OS tiles run in a separate process from the
 * app and consume images through ProtoLayout's [ResourceBuilders.Resources]
 * graph, so they can't reach Coil's in-memory cache. We render avatars
 * (URL-backed or fallback) into 80x80 PNGs on the wear app's `filesDir`
 * after each [systems.lupine.sheaf.wear.data.WearStore.loadAll], then the
 * tile services decode them lazily on each tile request.
 *
 * 80px is the on-screen size of a small avatar at watch density without
 * scaling artefacts; bumping further is wasted resource budget for a
 * complication-sized image.
 */

private const val AVATAR_PX = 80
private const val AVATAR_DIR = "tile_avatars"

internal fun tileAvatarDir(context: Context): File =
    File(context.filesDir, AVATAR_DIR).also { it.mkdirs() }

internal fun tileAvatarFile(context: Context, memberId: String): File =
    File(tileAvatarDir(context), "$memberId.png")

/**
 * Render and persist 80x80 avatar PNGs for [members]. Files for members
 * not in the input list are deleted, so the on-disk set always matches
 * the live roster (no orphaned avatars after a member is removed).
 *
 * A member with an avatar URL whose fetch fails keeps any previously
 * cached image rather than being overwritten with the initials fallback,
 * so a transient network blip (common right after a re-pair, before the
 * watch's network has settled) doesn't degrade a good avatar to initials.
 * The next refresh re-tries the fetch.
 */
internal fun renderTileAvatars(context: Context, members: List<WearMember>) {
    val dir = tileAvatarDir(context)
    val live = members.map { "${it.id}.png" }.toSet()
    dir.listFiles()?.forEach { if (it.name !in live) it.delete() }

    if (members.isEmpty()) return

    val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    for (m in members) {
        val file = tileAvatarFile(context, m.id)
        val hasUrl = !m.avatarUrl.isNullOrBlank()
        val fetched = if (hasUrl) fetchAvatarBitmap(m, http) else null
        val bm = when {
            fetched != null -> cropCircle(scaleSquare(fetched, AVATAR_PX))
            // URL avatar that failed to fetch but is already cached: keep
            // the existing file instead of clobbering it with initials.
            hasUrl && file.exists() -> continue
            // No URL (or first fetch failed with nothing cached): draw the
            // coloured-initials fallback so the member is never invisible.
            else -> drawFallback(m)
        }
        runCatching {
            FileOutputStream(file).use { out ->
                bm.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        }
        bm.recycle()
    }
}

/** Download and decode a member's avatar bitmap, or null on any failure. */
private fun fetchAvatarBitmap(member: WearMember, http: OkHttpClient): Bitmap? {
    val url = member.avatarUrl?.takeIf { it.isNotBlank() } ?: return null
    return runCatching {
        http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (!resp.isSuccessful) return@runCatching null
            resp.body?.byteStream()?.let(BitmapFactory::decodeStream)
        }
    }.getOrNull()
}

/**
 * A short signature of the on-disk avatar cache (file names + sizes),
 * folded into the tile resources version so the system re-fetches the
 * resource bundle whenever avatars are (re)rendered: after a re-pair
 * repopulates the cache, or a previously-failed download finally
 * succeeds. Without it the resources version only rotates on fronter-set
 * / tile-config changes, so refreshed avatars stayed invisible until the
 * tile was deleted and recreated. Uses file length, not mtime, so
 * re-rendering an identical avatar doesn't needlessly churn the version.
 */
internal fun tileAvatarsSignature(context: Context): String {
    val files = tileAvatarDir(context).listFiles()?.sortedBy { it.name } ?: return "0"
    var acc = 1
    for (f in files) {
        acc = 31 * acc + f.name.hashCode()
        acc = 31 * acc + f.length().toInt()
    }
    return acc.toString()
}

private fun drawFallback(member: WearMember): Bitmap {
    val bg = parseColor(member.color) ?: Color.parseColor("#7F6CFF")
    val fg = if (luminance(bg) > 0.5f) Color.BLACK else Color.WHITE
    val bm = Bitmap.createBitmap(AVATAR_PX, AVATAR_PX, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bm)
    val paint = Paint().apply { isAntiAlias = true; color = bg }
    canvas.drawCircle(AVATAR_PX / 2f, AVATAR_PX / 2f, AVATAR_PX / 2f, paint)

    val text = member.emoji?.takeIf { it.isNotBlank() } ?: member.initials
    val textPaint = Paint().apply {
        isAntiAlias = true
        color = fg
        textSize = AVATAR_PX * 0.5f
        textAlign = Paint.Align.CENTER
    }
    val r = Rect()
    textPaint.getTextBounds(text, 0, text.length, r)
    canvas.drawText(text, AVATAR_PX / 2f, AVATAR_PX / 2f - r.exactCenterY(), textPaint)
    return bm
}

private fun scaleSquare(src: Bitmap, size: Int): Bitmap {
    val srcSize = minOf(src.width, src.height)
    val left = (src.width - srcSize) / 2
    val top = (src.height - srcSize) / 2
    val cropped = Bitmap.createBitmap(src, left, top, srcSize, srcSize)
    return if (cropped.width == size) cropped
    else Bitmap.createScaledBitmap(cropped, size, size, true)
}

private fun cropCircle(src: Bitmap): Bitmap {
    val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    val paint = Paint().apply { isAntiAlias = true; color = Color.WHITE }
    canvas.drawCircle(src.width / 2f, src.height / 2f, src.width / 2f, paint)
    paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
    canvas.drawBitmap(src, 0f, 0f, paint)
    return out
}

private fun parseColor(hex: String?): Int? = hex?.let {
    runCatching { Color.parseColor(if (it.startsWith("#")) it else "#$it") }.getOrNull()
}

private fun luminance(color: Int): Float {
    val r = Color.red(color) / 255f
    val g = Color.green(color) / 255f
    val b = Color.blue(color) / 255f
    return 0.2126f * lin(r) + 0.7152f * lin(g) + 0.0722f * lin(b)
}

private fun lin(c: Float): Float =
    if (c <= 0.03928f) c / 12.92f
    else Math.pow(((c + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()

/**
 * Build a ProtoLayout [ResourceBuilders.ImageResource] from the cached
 * avatar PNG for [memberId], or null if no PNG exists yet (member loaded
 * after last refresh, or refresh never ran).
 */
internal fun tileAvatarResource(
    context: Context,
    memberId: String,
): ResourceBuilders.ImageResource? {
    val file = tileAvatarFile(context, memberId)
    if (!file.exists()) return null
    val bm = BitmapFactory.decodeFile(file.absolutePath) ?: return null
    val buffer = ByteBuffer.allocate(bm.byteCount)
    bm.copyPixelsToBuffer(buffer)
    return ResourceBuilders.ImageResource.Builder()
        .setInlineResource(
            ResourceBuilders.InlineImageResource.Builder()
                .setData(buffer.array())
                .setWidthPx(bm.width)
                .setHeightPx(bm.height)
                .setFormat(ResourceBuilders.IMAGE_FORMAT_ARGB_8888)
                .build()
        )
        .build()
}

/** Stable resource id used inside a tile layout to reference a member avatar. */
internal fun tileAvatarResourceId(memberId: String): String = "member:$memberId"
