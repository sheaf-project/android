package systems.lupine.sheaf.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Stand between an editor's exits and the work in it.
 *
 * Every editor in the app is a long scroll ending in a Save button, and both
 * ways out - the toolbar arrow and the system back gesture - used to throw the
 * lot away without asking. Back is the easy one to hit by accident: it is a
 * swipe from the screen edge, or a button next to where Save sits on a phone
 * with on-screen navigation, and someone with shaky hands does not get a second
 * chance at it. Reported from the field, about the journal editor, but it was
 * true of every editor except the member one, which is where this pattern
 * started.
 *
 * Installs the back handler, draws the dialog, and hands back the action for
 * the toolbar arrow so both routes out ask the same question. Clean editors are
 * unaffected: with [dirty] false, back is not intercepted at all and leaving
 * stays instant.
 *
 * Three answers, deliberately: save and go, discard and go, or stay. A
 * two-button version has to make "cancel" mean one of the first two, and
 * whichever it means will be wrong for somebody who tapped by mistake.
 *
 * @param dirty whether anything would actually be lost.
 * @param prompt what is unsaved, in the editor's own words.
 * @param canSave false when the form cannot be saved as it stands (an empty
 *   required field), which greys out saving rather than offering a button that
 *   silently does nothing.
 * @param onSave save; the caller's own save-then-navigate handling takes it
 *   from there, so this does not leave by itself.
 * @param onLeave leave without saving.
 */
@Composable
fun rememberUnsavedChangesGuard(
    dirty: Boolean,
    prompt: String,
    canSave: Boolean,
    onSave: () -> Unit,
    onLeave: () -> Unit,
): () -> Unit {
    var asking by remember { mutableStateOf(false) }

    BackHandler(enabled = dirty) { asking = true }

    if (asking) {
        AlertDialog(
            onDismissRequest = { asking = false },
            title = { Text("Unsaved changes") },
            text = { Text(prompt) },
            confirmButton = {
                TextButton(
                    onClick = {
                        asking = false
                        onSave()
                    },
                    enabled = canSave,
                ) { Text("Save and exit") }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            asking = false
                            onLeave()
                        },
                    ) { Text("Discard", color = MaterialTheme.colorScheme.error) }
                    Spacer(Modifier.width(4.dp))
                    TextButton(onClick = { asking = false }) { Text("Cancel") }
                }
            },
        )
    }

    return { if (dirty) asking = true else onLeave() }
}
