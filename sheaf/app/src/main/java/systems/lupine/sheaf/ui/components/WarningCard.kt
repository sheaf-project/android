package systems.lupine.sheaf.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import systems.lupine.sheaf.ui.theme.LocalWarningColors

/**
 * Something is not working, or is about to become visible, and the reader
 * needs to know before they do anything else.
 *
 * Shared rather than per-screen so the warning tone means one thing across the
 * app; the colours come from the palette, so it stays legible in every theme.
 * [title] is optional, for the cases where the first line names the thing and
 * the rest says what to do about it.
 */
@Composable
fun WarningCard(text: String, modifier: Modifier = Modifier, title: String? = null) {
    val warning = LocalWarningColors.current
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = warning.container),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = if (title == null) Alignment.CenterVertically else Alignment.Top,
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = warning.onContainer,
                modifier = Modifier.size(20.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (title != null) {
                    Text(
                        title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = warning.onContainer,
                    )
                }
                Text(
                    text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = warning.onContainer,
                )
            }
        }
    }
}
