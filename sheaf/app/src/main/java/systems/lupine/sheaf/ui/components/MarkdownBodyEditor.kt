@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package systems.lupine.sheaf.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import systems.lupine.sheaf.data.model.FileRead
import systems.lupine.sheaf.data.model.UserRead

// Bundle of state + callbacks the editor needs to power its image picker. The
// owning ViewModel manages all of it; the editor just renders + invokes.
data class MarkdownImagePicker(
    val user: UserRead?,
    val availableImages: List<FileRead>,
    val isLoadingImages: Boolean,
    val isUploadingImage: Boolean,
    val pendingImageMarkdown: String?,
    val onLoadAvailableImages: () -> Unit,
    val onUploadImage: (Uri) -> Unit,
    val onPickExistingFile: (FileRead) -> Unit,
    val onPickExternalUrl: (String) -> Unit,
    val onConsumeImage: () -> Unit,
)

// Markdown text editor with formatting toolbar, image picker, and a panel
// listing detected image references with hosted/external badges below the
// body. Used for journal entries, member bios, system descriptions, and group
// descriptions. Pass `imagePicker = null` to drop image features (toolbar
// loses the image button, no references panel).
@Composable
fun MarkdownBodyEditor(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Body",
    minLines: Int = 6,
    maxLines: Int = Int.MAX_VALUE,
    required: Boolean = false,
    imagePicker: MarkdownImagePicker? = null,
) {
    var bodyValue by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    LaunchedEffect(value) {
        if (value != bodyValue.text) {
            bodyValue = TextFieldValue(value, TextRange(value.length))
        }
    }
    var showImagePicker by remember { mutableStateOf(false) }
    var showFormattingHelp by remember { mutableStateOf(false) }

    // Insert any pending image markdown into the body at the current cursor,
    // then clear it so the next upload retriggers cleanly.
    LaunchedEffect(imagePicker?.pendingImageMarkdown) {
        val md = imagePicker?.pendingImageMarkdown ?: return@LaunchedEffect
        bodyValue = bodyValue.insertAtCursor(md, surroundWithBlankLines = true)
        onValueChange(bodyValue.text)
        imagePicker.onConsumeImage()
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FormatToolbar(
            showImageButton = imagePicker != null,
            isUploadingImage = imagePicker?.isUploadingImage == true,
            onAction = { transform ->
                bodyValue = transform(bodyValue)
                onValueChange(bodyValue.text)
            },
            onPickImage = {
                showImagePicker = true
                imagePicker?.onLoadAvailableImages?.invoke()
            },
            onHelp = { showFormattingHelp = true },
        )

        OutlinedTextField(
            value = bodyValue,
            onValueChange = { newValue ->
                bodyValue = newValue
                if (newValue.text != value) onValueChange(newValue.text)
            },
            label = { Text(if (required) "$label *" else label) },
            minLines = minLines,
            maxLines = maxLines,
            modifier = Modifier.fillMaxWidth(),
        )

        if (imagePicker != null) {
            ImageReferencesPanel(markdown = value)
        }
    }

    if (showFormattingHelp) {
        MarkdownHelpDialog(onDismiss = { showFormattingHelp = false })
    }

    if (showImagePicker && imagePicker != null) {
        val activityImagePicker = rememberLauncherForActivityResult(
            ActivityResultContracts.PickVisualMedia()
        ) { uri -> if (uri != null) imagePicker.onUploadImage(uri) }
        ImagePickerSheet(
            user = imagePicker.user,
            isLoadingImages = imagePicker.isLoadingImages,
            availableImages = imagePicker.availableImages,
            isUploading = imagePicker.isUploadingImage,
            onUploadNew = {
                showImagePicker = false
                activityImagePicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onPickExisting = {
                imagePicker.onPickExistingFile(it)
                showImagePicker = false
            },
            onPickExternal = {
                imagePicker.onPickExternalUrl(it)
                showImagePicker = false
            },
            onDismiss = { showImagePicker = false },
        )
    }
}

@Composable
private fun FormatToolbar(
    showImageButton: Boolean,
    isUploadingImage: Boolean,
    onAction: ((TextFieldValue) -> TextFieldValue) -> Unit,
    onPickImage: () -> Unit,
    onHelp: () -> Unit,
) {
    val scrollState = rememberScrollState()
    val btnSize = 38.dp
    val iconSize = 20.dp
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onAction { it.wrapSelection("**") } }, modifier = Modifier.size(btnSize)) {
            Icon(Icons.Default.FormatBold, contentDescription = "Bold", modifier = Modifier.size(iconSize))
        }
        IconButton(onClick = { onAction { it.wrapSelection("*") } }, modifier = Modifier.size(btnSize)) {
            Icon(Icons.Default.FormatItalic, contentDescription = "Italic", modifier = Modifier.size(iconSize))
        }
        IconButton(onClick = { onAction { it.cycleHeading() } }, modifier = Modifier.size(btnSize)) {
            Icon(Icons.Default.Title, contentDescription = "Heading", modifier = Modifier.size(iconSize))
        }
        IconButton(
            onClick = { onAction { it.toggleLinePrefix("- ", Regex("^- ")) } },
            modifier = Modifier.size(btnSize),
        ) {
            Icon(Icons.AutoMirrored.Filled.FormatListBulleted, contentDescription = "Bulleted list", modifier = Modifier.size(iconSize))
        }
        IconButton(
            onClick = { onAction { it.toggleLinePrefix("1. ", Regex("^\\d+\\. ")) } },
            modifier = Modifier.size(btnSize),
        ) {
            Icon(Icons.Default.FormatListNumbered, contentDescription = "Numbered list", modifier = Modifier.size(iconSize))
        }
        IconButton(onClick = { onAction { it.wrapSelection("[", "](url)") } }, modifier = Modifier.size(btnSize)) {
            Icon(Icons.Default.Link, contentDescription = "Link", modifier = Modifier.size(iconSize))
        }
        IconButton(onClick = { onAction { it.wrapSelection("`") } }, modifier = Modifier.size(btnSize)) {
            Icon(Icons.Default.Code, contentDescription = "Inline code", modifier = Modifier.size(iconSize))
        }
        IconButton(
            onClick = { onAction { it.wrapSelection("\n```\n", "\n```\n") } },
            modifier = Modifier.size(btnSize),
        ) {
            Icon(Icons.Default.DataObject, contentDescription = "Code block", modifier = Modifier.size(iconSize))
        }
        if (showImageButton) {
            IconButton(onClick = onPickImage, enabled = !isUploadingImage, modifier = Modifier.size(btnSize)) {
                if (isUploadingImage) {
                    CircularProgressIndicator(modifier = Modifier.size(iconSize), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = "Insert image", modifier = Modifier.size(iconSize))
                }
            }
        }
        IconButton(onClick = onHelp, modifier = Modifier.size(btnSize)) {
            Icon(Icons.Default.HelpOutline, contentDescription = "Formatting help", modifier = Modifier.size(iconSize))
        }
    }
}

// Quick reference for the markdown the renderer supports. Triggered from the
// editor toolbar. The line-break note is the headline item: people expect a
// single newline to break a line, but markdown needs a blank line for a new
// paragraph and two trailing spaces (or a backslash) for a soft break.
@Composable
private fun MarkdownHelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Got it") } },
        title = { Text("Formatting") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MarkdownHelpRow("**bold**", "Bold")
                MarkdownHelpRow("*italic*", "Italic")
                MarkdownHelpRow("# Heading", "Heading (more # = smaller)")
                MarkdownHelpRow("- item", "Bulleted list")
                MarkdownHelpRow("1. item", "Numbered list")
                MarkdownHelpRow("> quote", "Quote")
                MarkdownHelpRow("[text](https://…)", "Link")
                MarkdownHelpRow("`code`", "Inline code")
                MarkdownHelpRow("``` … ```", "Code block")
                HorizontalDivider()
                Text("Line breaks", style = MaterialTheme.typography.labelLarge)
                Text(
                    "Leave a blank line between paragraphs. For a single line break " +
                        "without starting a new paragraph, end the line with two spaces, " +
                        "or a backslash (\\).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

@Composable
private fun MarkdownHelpRow(syntax: String, label: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            syntax,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
            modifier = Modifier.weight(1f),
        )
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
    }
}

// ── Image picker (upload / existing / external) ───────────────────────────────

@Composable
private fun ImagePickerSheet(
    user: UserRead?,
    isLoadingImages: Boolean,
    availableImages: List<FileRead>,
    isUploading: Boolean,
    onUploadNew: () -> Unit,
    onPickExisting: (FileRead) -> Unit,
    onPickExternal: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val uploadsAllowed = user?.uploadsAllowed ?: true
    val externalAllowed = user?.externalImagesAllowed ?: true
    val initialTab = if (uploadsAllowed) 0 else 1
    var tabIndex by remember(uploadsAllowed) { mutableStateOf(initialTab) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                "Add image",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(20.dp, 12.dp),
            )

            val tabs = buildList {
                if (uploadsAllowed) add("Upload" to Icons.Default.Upload)
                add("Existing" to Icons.Default.PhotoLibrary)
                if (externalAllowed) add("External" to Icons.Default.Link)
            }
            PrimaryTabRow(selectedTabIndex = tabIndex, containerColor = Color.Transparent) {
                tabs.forEachIndexed { i, (label, icon) ->
                    Tab(
                        selected = tabIndex == i,
                        onClick = { tabIndex = i },
                        text = { Text(label) },
                        icon = { Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    )
                }
            }

            Box(modifier = Modifier.padding(16.dp).heightIn(min = 240.dp)) {
                when (tabs.getOrNull(tabIndex)?.first) {
                    "Upload" -> ImagePickerUploadTab(isUploading = isUploading, onUploadNew = onUploadNew)
                    "Existing" -> ImagePickerExistingTab(
                        isLoading = isLoadingImages,
                        files = availableImages,
                        onPick = onPickExisting,
                    )
                    "External" -> ImagePickerExternalTab(onSubmit = onPickExternal)
                }
            }
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}

@Composable
private fun ImagePickerUploadTab(isUploading: Boolean, onUploadNew: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().clickable(enabled = !isUploading) { onUploadNew() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 160.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    MaterialTheme.shapes.medium,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (isUploading) {
                    CircularProgressIndicator()
                    Text("Uploading…", style = MaterialTheme.typography.bodyMedium)
                } else {
                    Icon(
                        Icons.Default.Upload,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text("Tap to choose an image", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "JPEG, PNG, GIF, or WebP",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ImagePickerExistingTab(
    isLoading: Boolean,
    files: List<FileRead>,
    onPick: (FileRead) -> Unit,
) {
    when {
        isLoading -> Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        files.isEmpty() -> Text(
            "No uploaded images yet.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        )
        else -> LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
        ) {
            items(files, key = { it.id }) { file ->
                Box(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onPick(file) },
                ) {
                    AsyncImage(
                        model = file.url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Text(
                        formatBytes(file.sizeBytes),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .background(Color.Black.copy(alpha = 0.55f))
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ImagePickerExternalTab(onSubmit: (String) -> Unit) {
    var url by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = url,
            onValueChange = { url = it; error = null },
            label = { Text("Image URL") },
            placeholder = { Text("https://example.com/image.png") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            isError = error != null,
        )
        if (error != null) {
            Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Text(
            "Must be HTTPS. External images may be blocked by server policy.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = {
                val trimmed = url.trim()
                if (!trimmed.startsWith("https://")) {
                    error = "URL must start with https://"
                    return@Button
                }
                onSubmit(trimmed)
            },
            enabled = url.trim().isNotEmpty(),
        ) { Text("Insert") }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    var v = bytes.toDouble()
    var i = 0
    while (v >= 1024.0 && i < units.lastIndex) {
        v /= 1024.0
        i++
    }
    return "%.${if (i == 0) 0 else 1}f %s".format(v, units[i])
}
