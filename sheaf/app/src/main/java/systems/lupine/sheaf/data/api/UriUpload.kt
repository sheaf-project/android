package systems.lupine.sheaf.data.api

import android.content.ContentResolver
import android.net.Uri
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import java.io.FileNotFoundException

/**
 * A request body that streams a content [Uri] straight to the socket instead of
 * buffering the whole file into a ByteArray first. An import can be large (a
 * complete Sheaf backup runs up to 100MB with images), so buffering it cost a
 * heap allocation the size of the file; streaming keeps memory flat.
 *
 * Not one-shot on purpose: the Uri is re-opened on each [writeTo], so OkHttp can
 * replay the body (the token authenticator re-sends the request after a 401
 * refresh). The Uri therefore has to stay readable for the life of the upload,
 * which holds for an OPEN_DOCUMENT grant within the same session. (In a debug
 * build the body-logging interceptor still buffers the body to log it; release
 * builds log nothing, so the streaming is real there.)
 */
private class UriRequestBody(
    private val resolver: ContentResolver,
    private val uri: Uri,
    private val mediaType: MediaType,
) : RequestBody() {

    override fun contentType(): MediaType = mediaType

    override fun contentLength(): Long =
        runCatching {
            resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
        }.getOrDefault(-1L)

    override fun writeTo(sink: BufferedSink) {
        val input = resolver.openInputStream(uri)
            ?: throw FileNotFoundException("Cannot open $uri")
        input.use { sink.writeAll(it.source()) }
    }
}

/**
 * Multipart `file` part that streams from [uri] rather than reading it into
 * memory. Drop-in replacement for the per-importer `ByteArray.toPart` helpers.
 */
fun streamingFilePart(
    resolver: ContentResolver,
    uri: Uri,
    fileName: String,
    contentType: String = "application/octet-stream",
): MultipartBody.Part =
    MultipartBody.Part.createFormData(
        "file",
        fileName,
        UriRequestBody(resolver, uri, contentType.toMediaType()),
    )
