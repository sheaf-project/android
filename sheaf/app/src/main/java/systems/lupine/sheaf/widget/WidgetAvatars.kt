package systems.lupine.sheaf.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import kotlinx.coroutines.flow.firstOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import systems.lupine.sheaf.data.model.MemberRead
import systems.lupine.sheaf.data.repository.PreferencesRepository
import java.io.File
import java.io.FileOutputStream

// Phone home-screen widgets can't share Coil's in-memory cache reliably
// (Glance composition is driven from a separate process by AppWidgetHost),
// so we render avatars to PNGs on the app's filesDir during each refresh
// and decode them lazily when the widget renders. 96px is enough headroom
// for the small circle previews shown next to names.
private const val AVATAR_PX = 96
private const val AVATAR_DIR = "widget_avatars"

internal fun widgetAvatarDir(context: Context): File =
    File(context.filesDir, AVATAR_DIR).also { it.mkdirs() }

internal fun widgetAvatarFile(context: Context, memberId: String): File =
    File(widgetAvatarDir(context), "$memberId.png")

internal fun loadWidgetAvatar(context: Context, memberId: String): Bitmap? {
    val file = widgetAvatarFile(context, memberId)
    if (!file.exists()) return null
    return runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
}

internal suspend fun renderWidgetAvatars(
    context: Context,
    prefs: PreferencesRepository,
    http: OkHttpClient,
    members: List<MemberRead>,
) {
    // No file pruning here on purpose. Multiple widgets share this
    // cache directory and each widget refreshes independently with
    // its own member list — an earlier "delete files not in `members`"
    // step here meant each refresh wiped the avatars other widgets
    // needed, leaving them with letter-fallback circles. UUIDs as
    // file names means there's no name collision risk; size is ~9 KB
    // each, so leaving stale files is cheap. A separate
    // app-lifecycle-scope pass can sweep orphans later if it ever
    // matters.
    widgetAvatarDir(context)  // ensure exists
    if (members.isEmpty()) return

    val cdnBase = prefs.fileCdnBase.firstOrNull()?.trimEnd('/')?.ifBlank { null }
    val baseUrl = prefs.baseUrl.firstOrNull()?.trimEnd('/')
    val preferredBase = cdnBase ?: baseUrl

    for (m in members) {
        val resolved = resolveAvatarUrl(m.avatarUrl, baseUrl, cdnBase, preferredBase)
        val bm = renderOne(m, http, resolved)
        runCatching {
            FileOutputStream(widgetAvatarFile(context, m.id)).use { out ->
                bm.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        }
        bm.recycle()
    }
}

// Mirrors RelativeUrlInterceptor for non-Coil downloaders.
private fun resolveAvatarUrl(
    raw: String?,
    baseUrl: String?,
    cdnBase: String?,
    preferredBase: String?,
): String? {
    if (raw.isNullOrBlank()) return null
    return when {
        cdnBase != null && baseUrl != null && raw.startsWith("$baseUrl/") ->
            cdnBase + raw.removePrefix(baseUrl)
        raw.startsWith("/") -> preferredBase?.let { "$it$raw" }
        !raw.contains("://") -> preferredBase?.let { "$it/$raw" }
        else -> raw
    }
}

private fun renderOne(member: MemberRead, http: OkHttpClient, url: String?): Bitmap {
    val fetched = url?.let { fetchSquareBitmap(it, http) }
    return if (fetched != null) cropCircle(scaleSquare(fetched, AVATAR_PX))
    else drawFallback(member)
}

private fun fetchSquareBitmap(url: String, http: OkHttpClient): Bitmap? = runCatching {
    val resp = http.newCall(Request.Builder().url(url).build()).execute()
    resp.use {
        if (!it.isSuccessful) return@runCatching null
        it.body?.byteStream()?.let(BitmapFactory::decodeStream)
    }
}.getOrNull()

private fun drawFallback(member: MemberRead): Bitmap {
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
