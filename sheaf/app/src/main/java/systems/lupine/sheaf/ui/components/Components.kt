@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package systems.lupine.sheaf.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import systems.lupine.sheaf.data.model.MemberRead
import systems.lupine.sheaf.ui.theme.PurpleGrey10
import systems.lupine.sheaf.ui.theme.PurpleGrey80
import coil.compose.AsyncImage

// ── Avatar ────────────────────────────────────────────────────────────────────

@Composable
fun MemberAvatar(
    member: MemberRead,
    size: Dp = 48.dp,
    modifier: Modifier = Modifier,
) {
    val color = member.color?.let { parseColor(it) }
        ?: MaterialTheme.colorScheme.primaryContainer

    if (member.avatarUrl != null) {
        AsyncImage(
            model = member.avatarUrl,
            contentDescription = member.displayNameOrName,
            modifier = modifier
                .size(size)
                .clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(color),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = member.initials,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
            )
        }
    }
}

fun parseColor(hex: String): Color? = runCatching {
    Color(android.graphics.Color.parseColor(hex))
}.getOrNull()

// ── Member card ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MemberListItem(
    member: MemberRead,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier,
    ) {
        ListItem(
            headlineContent = {
                Text(
                    member.displayNameOrName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            supportingContent = member.pronouns?.let { pronouns ->
                { Text(pronouns, style = MaterialTheme.typography.bodySmall) }
            },
            leadingContent = { MemberAvatar(member, size = 44.dp) },
            trailingContent = trailing,
            colors = ListItemDefaults.colors(
                containerColor = Color.Transparent,
                headlineColor = MaterialTheme.colorScheme.onSurfaceVariant,
                supportingColor = MaterialTheme.colorScheme.onSurfaceVariant,
                trailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
            modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
        )
    }
}

// ── Empty state ───────────────────────────────────────────────────────────────

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    action: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(52.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        )
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
        action?.invoke()
    }
}

// ── Error banner ──────────────────────────────────────────────────────────────

@Composable
fun ErrorBanner(message: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.errorContainer,
                RoundedCornerShape(12.dp),
            )
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.size(18.dp),
        )
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

// ── Active indicator dot ──────────────────────────────────────────────────────

@Composable
fun ActiveDot(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(10.dp)
            .background(MaterialTheme.colorScheme.tertiary, CircleShape),
    )
}

// ── Section header ────────────────────────────────────────────────────────────

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

// ── Color swatch ─────────────────────────────────────────────────────────────

@Composable
fun ColorSwatch(hex: String, size: Dp = 32.dp, modifier: Modifier = Modifier) {
    val color = parseColor(hex) ?: MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color)
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
    )
}

// ── Color picker ──────────────────────────────────────────────────────────────

@Composable
fun ColorPicker(
    hex: String,
    onColorChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val initialHsv = remember(hex) {
        FloatArray(3).also { hsv ->
            parseColor(hex)?.let { android.graphics.Color.colorToHSV(it.toArgb(), hsv) }
                ?: run { hsv[0] = 0f; hsv[1] = 1f; hsv[2] = 1f }
        }
    }
    var hue        by remember { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember { mutableFloatStateOf(initialHsv[1]) }
    var value      by remember { mutableFloatStateOf(initialHsv[2]) }
    var hexInput   by remember(hex) { mutableStateOf(hex.removePrefix("#").uppercase()) }

    fun commitHsv() {
        val argb   = android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value))
        val newHex = "#%06X".format(argb and 0xFFFFFF)
        hexInput = newHex.removePrefix("#")
        onColorChange(newHex)
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SaturationValueBox(
            hue = hue,
            saturation = saturation,
            value = value,
            onSaturationValueChange = { s, v -> saturation = s; value = v; commitHsv() },
        )
        HueBar(
            hue = hue,
            onHueChange = { h -> hue = h; commitHsv() },
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ColorSwatch(hex = "#$hexInput", size = 40.dp)
            OutlinedTextField(
                value = hexInput,
                onValueChange = { input ->
                    val cleaned = input.filter { it.isLetterOrDigit() }.take(6).uppercase()
                    hexInput = cleaned
                    if (cleaned.length == 6) {
                        runCatching {
                            val color = android.graphics.Color.parseColor("#$cleaned")
                            val hsv = FloatArray(3)
                            android.graphics.Color.colorToHSV(color, hsv)
                            hue = hsv[0]; saturation = hsv[1]; value = hsv[2]
                            onColorChange("#$cleaned")
                        }
                    }
                },
                label = { Text("Hex") },
                prefix = { Text("#") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SaturationValueBox(
    hue: Float,
    saturation: Float,
    value: Float,
    onSaturationValueChange: (Float, Float) -> Unit,
) {
    val hueColor   = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f)))
    val thumbColor = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value)))
    var boxSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(MaterialTheme.shapes.medium)
            .onSizeChanged { boxSize = it }
            .pointerInput(boxSize) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    onSaturationValueChange(
                        (down.position.x / boxSize.width).coerceIn(0f, 1f),
                        1f - (down.position.y / boxSize.height).coerceIn(0f, 1f),
                    )
                    drag(down.id) { change ->
                        change.consume()
                        onSaturationValueChange(
                            (change.position.x / boxSize.width).coerceIn(0f, 1f),
                            1f - (change.position.y / boxSize.height).coerceIn(0f, 1f),
                        )
                    }
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(brush = Brush.horizontalGradient(listOf(Color.White, hueColor)))
            drawRect(brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
            val cx = saturation * size.width
            val cy = (1f - value) * size.height
            drawCircle(Color.White, radius = 12.dp.toPx(), center = Offset(cx, cy), style = Stroke(2.dp.toPx()))
            drawCircle(thumbColor, radius = 10.dp.toPx(), center = Offset(cx, cy))
        }
    }
}

@Composable
private fun HueBar(
    hue: Float,
    onHueChange: (Float) -> Unit,
) {
    val hueColors = remember {
        listOf(0f, 60f, 120f, 180f, 240f, 300f, 360f)
            .map { h -> Color(android.graphics.Color.HSVToColor(floatArrayOf(h, 1f, 1f))) }
    }
    var barSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .clip(CircleShape)
            .onSizeChanged { barSize = it }
            .pointerInput(barSize) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    onHueChange((down.position.x / barSize.width).coerceIn(0f, 1f) * 360f)
                    drag(down.id) { change ->
                        change.consume()
                        onHueChange((change.position.x / barSize.width).coerceIn(0f, 1f) * 360f)
                    }
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(brush = Brush.horizontalGradient(hueColors))
            val cx = (hue / 360f) * size.width
            val cy = size.height / 2f
            drawCircle(Color.White, radius = 13.dp.toPx(), center = Offset(cx, cy), style = Stroke(2.dp.toPx()))
            drawCircle(
                Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f))),
                radius = 11.dp.toPx(),
                center = Offset(cx, cy),
            )
        }
    }
}

// ── Card list container ───────────────────────────────────────────────────────

@Composable
fun CardList(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        content = { Column(content = content) },
    )
}

// ── Top app bar ───────────────────────────────────────────────────────────────

private val sheafAppBarColors
    @Composable get() = TopAppBarDefaults.topAppBarColors(
        containerColor = Color.Transparent,
        scrolledContainerColor = Color.Transparent,
    )

@Composable
fun SheafTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    expandedHeight: Dp = TopAppBarDefaults.TopAppBarExpandedHeight,
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    TopAppBar(
        title = title,
        modifier = modifier,
        navigationIcon = navigationIcon,
        actions = actions,
        expandedHeight = expandedHeight,
        windowInsets = WindowInsets(0),
        scrollBehavior = scrollBehavior,
        colors = sheafAppBarColors,
    )
}

@Composable
fun SheafCenterAlignedTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    CenterAlignedTopAppBar(
        title = title,
        modifier = modifier,
        navigationIcon = navigationIcon,
        actions = actions,
        windowInsets = WindowInsets(0),
        scrollBehavior = scrollBehavior,
        colors = sheafAppBarColors,
    )
}

@Composable
fun SheafLargeFlexibleTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: @Composable (() -> Unit)? = null,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    LargeFlexibleTopAppBar(
        title = title,
        subtitle = subtitle,
        modifier = modifier,
        navigationIcon = navigationIcon,
        actions = actions,
        windowInsets = WindowInsets(0),
        scrollBehavior = scrollBehavior,
        colors = sheafAppBarColors,
    )
}
