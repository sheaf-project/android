package systems.lupine.sheaf.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import systems.lupine.sheaf.data.api.SheafApiService
import systems.lupine.sheaf.data.model.MemberRead
import systems.lupine.sheaf.ui.theme.SheafTheme
import javax.inject.Inject

@AndroidEntryPoint
class QuickSwitchConfigActivity : ComponentActivity() {

    @Inject lateinit var api: SheafApiService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)

        val widgetId = intent
            .getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setContent {
            SheafTheme {
                ConfigScreen(
                    initialSelection = loadQuickSwitchMembers(this, widgetId).toSet(),
                    loadMembers = { runCatching { api.listMembers() }.getOrDefault(emptyList()) },
                    onSave = { picked ->
                        saveQuickSwitchMembers(this, widgetId, picked.toList())
                        refreshQuickSwitchWidget(widgetId)
                        val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                        setResult(Activity.RESULT_OK, result)
                        finish()
                    },
                    onCancel = {
                        setResult(Activity.RESULT_CANCELED)
                        finish()
                    },
                )
            }
        }
    }

    private fun refreshQuickSwitchWidget(widgetId: Int) {
        lifecycleScope.launch {
            runCatching {
                val glanceManager = androidx.glance.appwidget.GlanceAppWidgetManager(applicationContext)
                val glanceId = glanceManager.getGlanceIdBy(widgetId)
                RefreshQuickSwitchAction().onAction(
                    applicationContext,
                    glanceId,
                    androidx.glance.action.actionParametersOf(),
                )
            }
        }
    }
}

@Composable
private fun ConfigScreen(
    initialSelection: Set<String>,
    loadMembers: suspend () -> List<MemberRead>,
    onSave: (Set<String>) -> Unit,
    onCancel: () -> Unit,
) {
    var members by remember { mutableStateOf<List<MemberRead>?>(null) }
    var selected by remember { mutableStateOf(initialSelection) }

    LaunchedEffect(Unit) {
        members = loadMembers()
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                "Pick quick-switch members",
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Each picked member becomes a tile on the widget. Tap a tile to switch.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))

            val current = members
            when {
                current == null -> Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
                current.isEmpty() -> Text(
                    "No members found. Open Sheaf and load the system, then try adding the widget again.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                else -> LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    items(current, key = { it.id }) { m ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = m.id in selected,
                                onCheckedChange = { want ->
                                    selected = if (want) selected + m.id else selected - m.id
                                },
                            )
                            Spacer(Modifier.width(8.dp))
                            val emoji = m.emoji?.takeIf { it.isNotBlank() }
                            Text(
                                if (emoji != null) "$emoji  ${m.displayNameOrName}" else m.displayNameOrName,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                ) { Text("Cancel") }
                Button(
                    onClick = { onSave(selected) },
                    enabled = selected.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                ) { Text("Save (${selected.size})") }
            }
        }
    }
}
