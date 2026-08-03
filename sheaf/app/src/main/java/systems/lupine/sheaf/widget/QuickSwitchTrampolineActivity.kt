package systems.lupine.sheaf.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import systems.lupine.sheaf.data.api.SheafApiService
import systems.lupine.sheaf.data.repository.PreferencesRepository
import systems.lupine.sheaf.data.model.FrontCreate
import systems.lupine.sheaf.ui.theme.SheafTheme
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Confirmation surface for the QuickSwitchWidget. The widget tile launches
 * this activity with the picked member id; we show an explicit confirm
 * dialog before firing POST /v1/fronts. A trampoline (vs. a direct fire)
 * is the only safe option from a home screen tap, because widget taps are
 * easy to trigger accidentally and front state has emotional / social
 * weight beyond ordinary UI affordances.
 */
@AndroidEntryPoint
class QuickSwitchTrampolineActivity : ComponentActivity() {

    @Inject lateinit var api: SheafApiService
    @Inject lateinit var prefs: PreferencesRepository

    companion object {
        const val EXTRA_MEMBER_ID = "member_id"
        const val EXTRA_MEMBER_NAME = "member_name"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val memberId = intent.getStringExtra(EXTRA_MEMBER_ID)
        val memberName = intent.getStringExtra(EXTRA_MEMBER_NAME) ?: "member"
        val widgetId = intent
            .getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (memberId.isNullOrBlank()) {
            finish()
            return
        }

        setContent {
            SheafTheme {
                Trampoline(
                    memberName = memberName,
                    onConfirm = { commitSwitch(memberId, widgetId) },
                    onDismiss = { finish() },
                )
            }
        }
    }

    private fun commitSwitch(memberId: String, widgetId: Int) {
        lifecycleScope.launch {
            val result = runCatching {
                // Honour the device's quick-switch preference, same as the
                // carousel on Home: this is the same one-tap action, just from
                // the home screen. Without an override, fall through to the
                // account default by leaving replaceFronts null.
                val override = prefs.quickSwitchReplace.first()
                api.createFront(
                    FrontCreate(memberIds = listOf(memberId), replaceFronts = override),
                )
            }
            if (result.isSuccess) {
                Toast.makeText(this@QuickSwitchTrampolineActivity, "Switched", Toast.LENGTH_SHORT).show()
                if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
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
                setResult(Activity.RESULT_OK)
            } else {
                Toast.makeText(this@QuickSwitchTrampolineActivity, "Switch failed", Toast.LENGTH_SHORT).show()
                setResult(Activity.RESULT_CANCELED)
            }
            finish()
        }
    }
}

@Composable
private fun Trampoline(
    memberName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    var pending by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (pending) {
            CircularProgressIndicator()
        }
    }
    AlertDialog(
        onDismissRequest = { if (!pending) onDismiss() },
        title = { Text("Switch front?") },
        text = {
            Text(
                "Set $memberName as fronting now. Replaces any current fronters " +
                    "(or adds, depending on your system's default).",
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(
                enabled = !pending,
                onClick = {
                    pending = true
                    onConfirm()
                },
            ) { Text("Switch") }
        },
        dismissButton = {
            TextButton(
                enabled = !pending,
                onClick = onDismiss,
            ) { Text("Cancel") }
        },
    )
}
