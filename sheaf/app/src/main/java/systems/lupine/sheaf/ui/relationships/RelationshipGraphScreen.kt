@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package systems.lupine.sheaf.ui.relationships

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import systems.lupine.sheaf.data.model.RelationshipGraph
import systems.lupine.sheaf.data.model.RelationshipGraphEdge
import systems.lupine.sheaf.ui.components.ErrorBanner
import systems.lupine.sheaf.ui.components.SheafTopAppBar
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun RelationshipGraphScreen(
    onNavigateUp: () -> Unit,
    viewModel: RelationshipGraphViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
        topBar = {
            SheafTopAppBar(
                title = { Text("Relationship graph") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                val scopes = listOf(GRAPH_SCOPE_MEMBERS to "Members", GRAPH_SCOPE_GROUPS to "Groups")
                scopes.forEachIndexed { index, (value, label) ->
                    SegmentedButton(
                        selected = state.scope == value,
                        onClick = { viewModel.setScope(value) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = scopes.size),
                    ) { Text(label) }
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                val graph = state.graph
                when {
                    state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    state.error != null -> Column(Modifier.align(Alignment.Center).padding(24.dp)) {
                        ErrorBanner(state.error!!)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { viewModel.load() }) { Text("Retry") }
                    }
                    graph == null || graph.nodes.isEmpty() -> Text(
                        if (state.scope == GRAPH_SCOPE_GROUPS) "No group relationships to show yet."
                        else "No member relationships to show yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    )
                    else -> GraphCanvas(graph = graph, scopeKey = state.scope)
                }
            }
        }
    }
}

// Fallback node colours (by index) when a node has no colour of its own.
private val FALLBACK_NODE_COLORS = listOf(
    Color(0xFF8B5CF6), Color(0xFF3B82F6), Color(0xFF10B981), Color(0xFFF59E0B),
    Color(0xFFEF4444), Color(0xFFEC4899), Color(0xFF14B8A6), Color(0xFF6366F1),
)

private fun parseNodeColor(hex: String?, fallback: Color): Color =
    hex?.let {
        runCatching { Color(android.graphics.Color.parseColor(if (it.startsWith("#")) it else "#$it")) }.getOrNull()
    } ?: fallback

@Composable
private fun GraphCanvas(graph: RelationshipGraph, scopeKey: String) {
    val n = graph.nodes.size
    val indexOf = remember(graph) { graph.nodes.withIndex().associate { (i, node) -> node.id to i } }

    // Node positions/velocities in graph space (origin-centred).
    val px = remember(graph) { FloatArray(n) }
    val py = remember(graph) { FloatArray(n) }
    val vx = remember(graph) { FloatArray(n) }
    val vy = remember(graph) { FloatArray(n) }

    var version by remember(graph) { mutableIntStateOf(0) }
    var scale by remember(graph) { mutableFloatStateOf(1f) }
    var offset by remember(graph) { mutableStateOf(Offset.Zero) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    val textMeasurer = rememberTextMeasurer()
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val edgeColor = MaterialTheme.colorScheme.outline
    val labelBg = MaterialTheme.colorScheme.surface

    // Edges resolved to node indices once.
    val edgeIdx = remember(graph) {
        graph.edges.mapNotNull { e ->
            val s = indexOf[e.sourceId]; val t = indexOf[e.targetId]
            if (s != null && t != null && s != t) Triple(s, t, e) else null
        }
    }

    // Fit the current layout into the canvas with padding.
    fun fit() {
        if (n == 0 || canvasSize == IntSize.Zero) return
        var maxR = 1f
        for (i in 0 until n) maxR = max(maxR, sqrt(px[i] * px[i] + py[i] * py[i]))
        val half = min(canvasSize.width, canvasSize.height) / 2f
        scale = ((half - 80f) / (maxR + 1f)).coerceIn(0.2f, 4f)
        offset = Offset.Zero
    }

    // Force-directed layout (Fruchterman-Reingold-ish) with cooling.
    LaunchedEffect(graph) {
        // Seed on a circle so the sim doesn't start degenerate.
        val r0 = 120f + 30f * sqrt(n.toFloat())
        for (i in 0 until n) {
            val a = (2.0 * Math.PI * i / max(1, n)).toFloat()
            px[i] = r0 * cos(a); py[i] = r0 * sin(a)
            vx[i] = 0f; vy[i] = 0f
        }
        val k = 150f                 // ideal edge length
        val kRep = k * k             // repulsion strength
        var alpha = 1f
        // Run until cooled; caps the work for large graphs.
        var steps = 0
        while (alpha > 0.02f && steps < 600) {
            // Repulsion (all pairs).
            for (i in 0 until n) {
                var fxI = 0f; var fyI = 0f
                for (j in 0 until n) {
                    if (i == j) continue
                    var dx = px[i] - px[j]; var dy = py[i] - py[j]
                    var d2 = dx * dx + dy * dy
                    if (d2 < 0.01f) { dx = (i - j) * 0.1f; dy = 0.1f; d2 = dx * dx + dy * dy }
                    val d = sqrt(d2)
                    val f = kRep / d2
                    fxI += dx / d * f; fyI += dy / d * f
                }
                // Centering pull so the graph stays compact.
                fxI -= px[i] * 0.08f; fyI -= py[i] * 0.08f
                vx[i] = (vx[i] + fxI) * 0.85f
                vy[i] = (vy[i] + fyI) * 0.85f
            }
            // Attraction along edges (spring toward ideal length).
            for ((s, t, _) in edgeIdx) {
                val dx = px[t] - px[s]; val dy = py[t] - py[s]
                val d = max(1f, sqrt(dx * dx + dy * dy))
                val f = (d - k) * 0.02f
                val ux = dx / d * f; val uy = dy / d * f
                vx[s] += ux; vy[s] += uy
                vx[t] -= ux; vy[t] -= uy
            }
            // Integrate with a per-step displacement cap scaled by alpha.
            val cap = 40f * alpha
            for (i in 0 until n) {
                val vlen = sqrt(vx[i] * vx[i] + vy[i] * vy[i])
                val s = if (vlen > cap) cap / vlen else 1f
                px[i] += vx[i] * s; py[i] += vy[i] * s
            }
            alpha *= 0.99f
            steps++
            version++
            withFrameNanos { }
        }
        fit()
    }

    LaunchedEffect(canvasSize) { if (canvasSize != IntSize.Zero) fit() }

    Box(Modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { canvasSize = it }
                .pointerInput(graph) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(0.2f, 5f)
                        val f = newScale / scale
                        val center = Offset(size.width / 2f, size.height / 2f)
                        // Zoom about the gesture centroid, then pan.
                        offset = (centroid - center) * (1 - f) + offset * f + pan
                        scale = newScale
                    }
                },
        ) {
            version // read so the sim redraws each tick
            if (n == 0) return@Canvas
            val center = Offset(size.width / 2f, size.height / 2f)
            fun screen(i: Int) = center + offset + Offset(px[i] * scale, py[i] * scale)

            val nodeRadius = 20.dp.toPx()

            // Edges first (under the nodes).
            for ((s, t, e) in edgeIdx) {
                val a = screen(s); val b = screen(t)
                drawLine(edgeColor, a, b, strokeWidth = 2f)
                drawEdgeLabel(textMeasurer, e, a, b, onSurfaceVariant, labelBg)
                if (e.directed) drawArrowhead(a, b, nodeRadius, edgeColor)
            }

            // Nodes.
            graph.nodes.forEachIndexed { i, node ->
                val p = screen(i)
                val color = parseNodeColor(node.color, FALLBACK_NODE_COLORS[i % FALLBACK_NODE_COLORS.size])
                drawCircle(color, radius = nodeRadius, center = p)
                val initial = node.name.trim().firstOrNull()?.uppercase() ?: "?"
                val initialLayout = textMeasurer.measure(
                    initial,
                    style = TextStyle(color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                )
                drawText(
                    initialLayout,
                    topLeft = p - Offset(initialLayout.size.width / 2f, initialLayout.size.height / 2f),
                )
                val nameLayout = textMeasurer.measure(
                    node.name,
                    style = TextStyle(color = onSurface, fontSize = 11.sp),
                )
                drawText(
                    nameLayout,
                    topLeft = Offset(p.x - nameLayout.size.width / 2f, p.y + nodeRadius + 2f),
                )
            }
        }

        // Helper text + recentre.
        Text(
            "Pinch to zoom, drag to pan.",
            style = MaterialTheme.typography.labelSmall,
            color = onSurfaceVariant,
            modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
        )
        FilledTonalIconButton(
            onClick = { fit() },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) {
            Icon(Icons.Filled.CenterFocusStrong, contentDescription = "Recentre")
        }
    }
}

private fun DrawScope.drawEdgeLabel(
    textMeasurer: TextMeasurer,
    edge: RelationshipGraphEdge,
    a: Offset,
    b: Offset,
    textColor: Color,
    bg: Color,
) {
    val mid = Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f)
    val layout = textMeasurer.measure(edge.sourceLabel, style = TextStyle(color = textColor, fontSize = 10.sp))
    val topLeft = Offset(mid.x - layout.size.width / 2f, mid.y - layout.size.height / 2f)
    drawRoundRect(
        color = bg.copy(alpha = 0.85f),
        topLeft = topLeft - Offset(3f, 1f),
        size = androidx.compose.ui.geometry.Size(layout.size.width + 6f, layout.size.height + 2f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f, 3f),
    )
    drawText(layout, topLeft = topLeft)
}

private fun DrawScope.drawArrowhead(a: Offset, b: Offset, nodeRadius: Float, color: Color) {
    val dir = b - a
    val len = sqrt(dir.x * dir.x + dir.y * dir.y)
    if (len < 1f) return
    val ux = dir.x / len; val uy = dir.y / len
    // Tip sits at the target node's boundary.
    val tip = Offset(b.x - ux * nodeRadius, b.y - uy * nodeRadius)
    val size = 12f
    val backX = tip.x - ux * size; val backY = tip.y - uy * size
    val perpX = -uy; val perpY = ux
    val half = size * 0.5f
    val path = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(backX + perpX * half, backY + perpY * half)
        lineTo(backX - perpX * half, backY - perpY * half)
        close()
    }
    drawPath(path, color)
}
