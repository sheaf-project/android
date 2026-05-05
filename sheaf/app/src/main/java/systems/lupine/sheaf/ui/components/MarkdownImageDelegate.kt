package systems.lupine.sheaf.ui.components

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import systems.lupine.sheaf.data.api.SheafApiService
import systems.lupine.sheaf.data.model.FileRead
import systems.lupine.sheaf.data.model.UserRead
import systems.lupine.sheaf.util.toUserMessage
import javax.inject.Inject

// State + behaviour for the Markdown editor's image picker, shared across any
// ViewModel that hosts a MarkdownBodyEditor (journals, member bios, system
// description, group descriptions). Hilt creates a fresh instance per VM, so
// each editor surface owns its own pendingImageMarkdown / availableImages
// without cross-talk.
data class MarkdownImageDelegateState(
    val user: UserRead? = null,
    val availableImages: List<FileRead> = emptyList(),
    val isLoadingImages: Boolean = false,
    val isUploadingImage: Boolean = false,
    val pendingImageMarkdown: String? = null,
    val error: String? = null,
)

class MarkdownImageDelegate @Inject constructor(
    private val api: SheafApiService,
    @ApplicationContext private val context: Context,
) {
    private val _state = MutableStateFlow(MarkdownImageDelegateState())
    val state: StateFlow<MarkdownImageDelegateState> = _state.asStateFlow()

    // Loads /me to populate the user.uploadsAllowed / externalImagesAllowed
    // flags the picker uses to gate its tabs. Callers should invoke once on
    // VM init.
    fun loadUser(scope: CoroutineScope) {
        scope.launch {
            runCatching { api.getMe() }
                .onSuccess { u -> _state.update { it.copy(user = u) } }
        }
    }

    fun loadAvailableImages(scope: CoroutineScope) {
        if (_state.value.isLoadingImages) return
        scope.launch {
            _state.update { it.copy(isLoadingImages = true) }
            runCatching { api.listFiles() }
                .onSuccess { files ->
                    val images = files.filter { it.contentType.startsWith("image/") }
                    _state.update { it.copy(availableImages = images, isLoadingImages = false) }
                }
                .onFailure {
                    _state.update { it.copy(isLoadingImages = false) }
                }
        }
    }

    fun uploadImage(scope: CoroutineScope, uri: Uri) {
        scope.launch {
            _state.update { it.copy(isUploadingImage = true, error = null) }
            runCatching {
                val resolver = context.contentResolver
                val mimeType = resolver.getType(uri) ?: "image/jpeg"
                val bytes = resolver.openInputStream(uri)!!.use { it.readBytes() }
                val ext = mimeType.substringAfter("/").let { if (it == "jpeg") "jpg" else it }
                val body = bytes.toRequestBody(mimeType.toMediaTypeOrNull())
                val part = MultipartBody.Part.createFormData("file", "image.$ext", body)
                api.uploadFile(part)
            }
                .onSuccess { resp ->
                    _state.update {
                        it.copy(
                            isUploadingImage = false,
                            pendingImageMarkdown = "![image](${resp.url})",
                        )
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            isUploadingImage = false,
                            error = "Failed to upload image: ${e.toUserMessage()}",
                        )
                    }
                }
        }
    }

    fun pickExistingFile(file: FileRead) {
        _state.update { it.copy(pendingImageMarkdown = "![image](/v1/files/${file.key})") }
    }

    fun pickExternalUrl(url: String) {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return
        _state.update { it.copy(pendingImageMarkdown = "![image]($trimmed)") }
    }

    fun consumePendingImageMarkdown() {
        _state.update { it.copy(pendingImageMarkdown = null) }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}

// @Composable helper that observes the delegate's state and returns a
// MarkdownImagePicker wired to the given scope. Pass the VM's viewModelScope
// so uploads keep running across recompositions / configuration changes.
@androidx.compose.runtime.Composable
fun rememberMarkdownImagePicker(
    delegate: MarkdownImageDelegate,
    scope: CoroutineScope,
): MarkdownImagePicker {
    val s by delegate.state.collectAsState()
    return MarkdownImagePicker(
        user = s.user,
        availableImages = s.availableImages,
        isLoadingImages = s.isLoadingImages,
        isUploadingImage = s.isUploadingImage,
        pendingImageMarkdown = s.pendingImageMarkdown,
        onLoadAvailableImages = { delegate.loadAvailableImages(scope) },
        onUploadImage = { uri -> delegate.uploadImage(scope, uri) },
        onPickExistingFile = { delegate.pickExistingFile(it) },
        onPickExternalUrl = { delegate.pickExternalUrl(it) },
        onConsumeImage = { delegate.consumePendingImageMarkdown() },
    )
}
