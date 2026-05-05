package systems.lupine.sheaf.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import systems.lupine.sheaf.data.model.FileUsage

// Shared storage quota card. Used on the Files management screen as a header
// and on the Data settings detail screen as a summary surface. Bar turns
// yellow at >= 80% and red at >= 95% so users get a visual nudge before they
// fill the quota.
@Composable
fun StorageQuotaCard(usage: FileUsage?, modifier: Modifier = Modifier) {
    val used = usage?.usedBytes ?: 0L
    val quota = usage?.quotaBytes ?: 0L
    val pct = if (quota > 0) (used.toFloat() / quota.toFloat()).coerceIn(0f, 1f) else 0f
    val barColor = when {
        pct >= 0.95f -> MaterialTheme.colorScheme.error
        pct >= 0.80f -> Color(0xFFEAB308)
        else -> MaterialTheme.colorScheme.primary
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Storage", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                if (usage == null) "..."
                else "${formatBytesShort(used)} of ${formatBytesShort(quota)} used  ·  ${usage.fileCount} file${if (usage.fileCount == 1) "" else "s"}",
                style = MaterialTheme.typography.bodyMedium,
            )
            LinearProgressIndicator(
                progress = { pct },
                color = barColor,
                trackColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            )
        }
    }
}

internal fun formatBytesShort(bytes: Long): String {
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
