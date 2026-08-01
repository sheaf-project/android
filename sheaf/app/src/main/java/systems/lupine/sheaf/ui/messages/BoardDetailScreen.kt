package systems.lupine.sheaf.ui.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import systems.lupine.sheaf.data.model.MessageRead
import systems.lupine.sheaf.ui.components.ErrorBanner
import systems.lupine.sheaf.ui.components.SheafTopAppBar
import androidx.compose.ui.draw.alpha
import systems.lupine.sheaf.ui.components.PENDING_DELETE_ALPHA
import systems.lupine.sheaf.ui.components.PendingDeleteBadge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardDetailScreen(
    onNavigateUp: () -> Unit,
    viewModel: BoardDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()

    // Scroll to bottom when new messages arrive — messages come oldest-first
    // after we flip the list below.
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            SheafTopAppBar(
                title = { Text(state.boardTitle.ifEmpty { "Board" }) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        bottomBar = { Composer(state, viewModel) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            state.error?.let { msg ->
                ErrorBanner(msg, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
            when {
                state.isLoading -> Box(
                    Modifier.fillMaxWidth().height(120.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
                state.messages.isEmpty() -> EmptyState()
                else -> {
                    // Backend returns most-recent first; we want most-recent
                    // at the bottom so the composer is adjacent. Reverse here
                    // so the LazyColumn is in chronological order.
                    val ordered = state.messages.asReversed()
                    // Lookup table for jump-to-parent. Built once per
                    // message-list change so tapping a reply's quoted
                    // parent card can animateScrollToItem cheaply.
                    val indexById by remember(ordered) {
                        derivedStateOf {
                            ordered.withIndex().associate { (i, m) -> m.id to i }
                        }
                    }
                    val scope = rememberCoroutineScope()
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 16.dp,
                            vertical = 8.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(ordered, key = { it.id }) { msg ->
                            MessageBubble(
                                message = msg,
                                onReply = { viewModel.setReplyTo(msg) },
                                onJumpToParent = { parentId ->
                                    indexById[parentId]?.let { idx ->
                                        scope.launch { listState.animateScrollToItem(idx) }
                                    }
                                },
                                parentIsOnPage = msg.parentMessageId?.let { it in indexById } == true,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Composer(state: BoardDetailUiState, vm: BoardDetailViewModel) {
    Surface(tonalElevation = 4.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            state.replyTo?.let { reply ->
                ReplyingToBanner(
                    reply = reply,
                    onClear = { vm.setReplyTo(null) },
                )
                Spacer(Modifier.height(8.dp))
            }
            AuthorDropdown(state, vm)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                OutlinedTextField(
                    value = state.draft,
                    onValueChange = { vm.setDraft(it) },
                    label = {
                        Text(if (state.replyTo != null) "Write a reply…" else "Write a message…")
                    },
                    minLines = 1,
                    maxLines = 5,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = { vm.submit() },
                    enabled = !state.isPosting && state.draft.isNotBlank() && state.authorMemberId != null,
                ) {
                    if (state.isPosting) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(20.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = if (state.draft.isNotBlank())
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Composer pre-banner shown when [BoardDetailUiState.replyTo] is set.
 * Mirrors the web client's `Replying to X: <snippet>` row so the user
 * has both a clear visual indicator that the next send goes under a
 * specific parent and a one-tap way out.
 */
@Composable
private fun ReplyingToBanner(
    reply: systems.lupine.sheaf.data.model.MessageRead,
    onClear: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Replying to ${reply.authorMemberName ?: "deleted member"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    reply.body,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onClear) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Cancel reply",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthorDropdown(state: BoardDetailUiState, vm: BoardDetailViewModel) {
    var expanded by remember { mutableStateOf(false) }
    val name = state.members.firstOrNull { it.id == state.authorMemberId }?.displayNameOrName
        ?: "Pick a member"
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
    ) {
        OutlinedTextField(
            readOnly = true,
            value = name,
            onValueChange = {},
            label = { Text("Posting as") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            state.members.forEach { m ->
                DropdownMenuItem(
                    text = { Text(m.displayNameOrName) },
                    onClick = {
                        vm.setAuthor(m.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: MessageRead,
    onReply: () -> Unit,
    onJumpToParent: (parentId: String) -> Unit,
    parentIsOnPage: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (message.pendingDeleteAt != null) PENDING_DELETE_ALPHA else 1f),
    ) {
        PendingDeleteBadge(message.pendingDeleteAt)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                message.authorMemberName ?: "[deleted member]",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                message.createdAt.take(16).replace('T', ' '),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            // Compact reply trigger. Surface as a small icon button at
            // the row's trailing edge — matches the chat-app convention
            // of "lightweight per-message action" without dropping a
            // full button below every message.
            IconButton(
                onClick = onReply,
                modifier = Modifier.heightIn(max = 28.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Reply,
                    contentDescription = "Reply to this message",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.height(18.dp),
                )
            }
        }
        if (message.parentMessageId != null && message.parentPreview != null) {
            // Quoted-parent card. When the parent is in the loaded
            // page, the whole card acts as a jump-to-parent affordance;
            // tapping animates the LazyColumn up to that message. When
            // the parent is off-page (older than the current batch),
            // we still show the quote but the tap is a no-op rather
            // than a broken-looking scroll. Pagination of older
            // messages is a follow-up.
            val parentId = message.parentMessageId
            val cardModifier = if (parentIsOnPage) {
                Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 2.dp)
                    .clickable { onJumpToParent(parentId) }
            } else {
                Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 2.dp)
            }
            Surface(
                tonalElevation = 1.dp,
                shape = RoundedCornerShape(8.dp),
                modifier = cardModifier,
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(
                        "Reply to ${message.parentAuthorMemberName ?: "deleted member"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        message.parentPreview,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Text(
                message.body,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "No messages yet — be the first to post.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
