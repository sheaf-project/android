@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package systems.lupine.sheaf.ui.members

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import systems.lupine.sheaf.data.model.ContentRevisionRead
import systems.lupine.sheaf.data.model.MemberRead
import systems.lupine.sheaf.ui.components.*
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

// ── Members list ──────────────────────────────────────────────────────────────

@Composable
fun MembersScreen(
    onMemberClick: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: MembersViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var contextMenuMember by remember { mutableStateOf<MemberRead?>(null) }
    var memberToDelete by remember { mutableStateOf<MemberRead?>(null) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.load()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val textFieldState = rememberTextFieldState()
    val searchBarState = rememberContainedSearchBarState()
    val scope = rememberCoroutineScope()
    val scrollBehavior = SearchBarDefaults.enterAlwaysSearchBarScrollBehavior()

    val query = textFieldState.text.toString()
    val filteredMembers = remember(query, state.members) {
        if (query.isBlank()) state.members
        else state.members.filter {
            it.displayNameOrName.contains(query, ignoreCase = true) ||
                it.pronouns?.contains(query, ignoreCase = true) == true
        }
    }

    val searchContainerColor = MaterialTheme.colorScheme.surfaceVariant
    val searchContentColor = MaterialTheme.colorScheme.onSurfaceVariant
    val appBarColors = SearchBarDefaults.appBarWithSearchColors(
        searchBarColors = SearchBarDefaults.colors(
            containerColor = searchContainerColor,
            inputFieldColors = SearchBarDefaults.inputFieldColors(
                focusedContainerColor = searchContainerColor,
                unfocusedContainerColor = searchContainerColor,
                disabledContainerColor = searchContainerColor,
                focusedTextColor = searchContentColor,
                unfocusedTextColor = searchContentColor,
                focusedLeadingIconColor = searchContentColor,
                unfocusedLeadingIconColor = searchContentColor,
                focusedTrailingIconColor = searchContentColor,
                unfocusedTrailingIconColor = searchContentColor,
                focusedPlaceholderColor = searchContentColor.copy(alpha = 0.6f),
                unfocusedPlaceholderColor = searchContentColor.copy(alpha = 0.6f),
            ),
        ),
        appBarContainerColor = Color.Transparent,
        scrolledAppBarContainerColor = Color.Transparent,
    )

    val inputField = @Composable {
        SearchBarDefaults.InputField(
            textFieldState = textFieldState,
            searchBarState = searchBarState,
            colors = appBarColors.searchBarColors.inputFieldColors,
            onSearch = { scope.launch { searchBarState.animateToCollapsed() } },
            placeholder = { Text("Search members", modifier = Modifier.clearAndSetSemantics {}) },
            leadingIcon = {
                if (searchBarState.currentValue == SearchBarValue.Collapsed) {
                    Icon(Icons.Default.Search, contentDescription = null)
                } else {
                    IconButton(onClick = {
                        scope.launch { searchBarState.animateToCollapsed() }
                        textFieldState.edit { replace(0, length, "") }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close search")
                    }
                }
            },
            trailingIcon = {
                if (textFieldState.text.isNotEmpty()) {
                    IconButton(onClick = { textFieldState.edit { replace(0, length, "") } }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear search")
                    }
                } else if (searchBarState.currentValue == SearchBarValue.Collapsed) {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            },
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            AppBarWithSearch(
                state = searchBarState,
                inputField = inputField,
                scrollBehavior = scrollBehavior,
                colors = appBarColors,
                windowInsets = WindowInsets(0),
            )
            ExpandedFullScreenContainedSearchBar(
                state = searchBarState,
                inputField = inputField,
                colors = appBarColors.searchBarColors,
            ) {
                if (state.isLoading) {
                    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (filteredMembers.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        Text("No members found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(filteredMembers, key = { it.id }) { member ->
                            MemberListItem(
                                member = member,
                                onClick = {
                                    scope.launch { searchBarState.animateToCollapsed() }
                                    onMemberClick(member.id)
                                },
                            )
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onMemberClick("new") }) {
                Icon(Icons.Default.Add, contentDescription = "Add member")
            }
        },
    ) { padding ->
        when {
            state.isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.error != null -> Column(
                Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ErrorBanner(state.error!!)
                Button(onClick = { viewModel.load() }) { Text("Retry") }
            }
            state.members.isEmpty() -> EmptyState(
                icon = Icons.Default.PersonAdd,
                title = "No members yet",
                subtitle = "Tap + to add your first system member.",
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 8.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.members, key = { it.id }) { member ->
                    Box {
                        MemberListItem(
                            member = member,
                            onClick = { onMemberClick(member.id) },
                            onLongClick = { contextMenuMember = member },
                            trailing = {
                                Icon(
                                    Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                        )
                        if (contextMenuMember?.id == member.id) DropdownMenu(
                            expanded = true,
                            onDismissRequest = { contextMenuMember = null },
                        ) {
                            val isFronting = state.currentFronts.any { member.id in it.memberIds }
                            DropdownMenuItem(
                                text = { Text(if (isFronting) "Remove from front" else "Add to front") },
                                leadingIcon = {
                                    Icon(
                                        if (isFronting) Icons.Default.PersonRemove else Icons.Default.PersonAdd,
                                        contentDescription = null,
                                    )
                                },
                                onClick = {
                                    if (isFronting) viewModel.removeFromFront(member.id)
                                    else viewModel.addToFront(member.id)
                                    contextMenuMember = null
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Set ${member.displayNameOrName} as sole fronter") },
                                leadingIcon = { Icon(Icons.Default.SwitchAccount, contentDescription = null) },
                                onClick = {
                                    viewModel.switchSoleFronter(member.id)
                                    contextMenuMember = null
                                },
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Remove member", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    memberToDelete = member
                                    contextMenuMember = null
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    if (memberToDelete != null) {
        val target = memberToDelete!!
        LaunchedEffect(target.id) { viewModel.loadDeleteSafety() }
        MemberDeleteDialog(
            memberLabel = target.displayNameOrName,
            safety = state.deleteSafety,
            isDeleting = state.isDeleting,
            errorMessage = state.deleteError,
            onConfirm = { password, totpCode ->
                viewModel.deleteMember(target.id, password, totpCode)
            },
            onDismiss = { memberToDelete = null; viewModel.clearDeleteError() },
        )
        LaunchedEffect(state.deleteCompleted) {
            if (state.deleteCompleted) {
                memberToDelete = null
                viewModel.clearDeleteCompleted()
            }
        }
    }

}

// ── Member detail / edit / create ─────────────────────────────────────────────

@Composable
fun MemberDetailScreen(
    memberId: String,
    onNavigateUp: () -> Unit,
    viewModel: MemberDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val form  by viewModel.form.collectAsState()
    var showAvatarMenu by remember { mutableStateOf(false) }
    val photoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { viewModel.uploadAndSetAvatar(it) } }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    LaunchedEffect(state.saved) {
        if (state.saved) onNavigateUp()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        modifier = if (!viewModel.isNewMember) Modifier.nestedScroll(scrollBehavior.nestedScrollConnection) else Modifier,
        topBar = {
            if (viewModel.isNewMember) {
                SheafTopAppBar(
                    title = { Text("New Member") },
                    navigationIcon = {
                        IconButton(onClick = onNavigateUp) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                )
            } else {
                SheafLargeFlexibleTopAppBar(
                    title = { Text(form.name.ifBlank { "Member" }) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateUp) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            }
        },
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {

            // Avatar
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box {
                    if (!form.avatarUrl.isNullOrEmpty()) {
                        AsyncImage(
                            model = form.avatarUrl,
                            contentDescription = null,
                            modifier = Modifier.size(80.dp).clip(CircleShape),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                modifier = Modifier.size(44.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .clickable { showAvatarMenu = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit avatar",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }

                    DropdownMenu(
                        expanded = showAvatarMenu,
                        onDismissRequest = { showAvatarMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Choose photo") },
                            leadingIcon = { Icon(Icons.Default.Image, contentDescription = null) },
                            onClick = {
                                showAvatarMenu = false
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                        )
                        if (!form.avatarUrl.isNullOrEmpty()) {
                            DropdownMenuItem(
                                text = { Text("Remove avatar", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = {
                                    Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                },
                                onClick = {
                                    showAvatarMenu = false
                                    viewModel.removeAvatar()
                                },
                            )
                        }
                    }
                }

                if (state.isUploadingAvatar) {
                    CircularProgressIndicator(modifier = Modifier.size(88.dp), strokeWidth = 3.dp)
                }
            }

            if (state.error != null) ErrorBanner(state.error!!)

            OutlinedTextField(
                value = form.name,
                onValueChange = { viewModel.updateForm { copy(name = it) } },
                label = { Text("Name *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = form.displayName,
                onValueChange = { viewModel.updateForm { copy(displayName = it) } },
                label = { Text("Display Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = form.pronouns,
                onValueChange = { viewModel.updateForm { copy(pronouns = it) } },
                label = { Text("Pronouns") },
                placeholder = { Text("e.g. they/them") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = form.description,
                onValueChange = { viewModel.updateForm { copy(description = it) } },
                label = { Text("Description") },
                minLines = 3,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = if (form.avatarUrl?.contains("/v1/files/") == true) "" else form.avatarUrl ?: "",
                onValueChange = { viewModel.updateForm { copy(avatarUrl = it.ifBlank { null }) } },
                label = { Text("Avatar URL") },
                placeholder = { Text("https://…") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            BirthdayPicker(
                value = form.birthday,
                onValueChange = { viewModel.updateForm { copy(birthday = it) } },
                modifier = Modifier.fillMaxWidth(),
            )

            ColorPicker(
                hex = form.color,
                onColorChange = { viewModel.updateForm { copy(color = it) } },
            )

            SectionHeader("Privacy")
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                listOf("public", "friends", "private").forEachIndexed { index, level ->
                    SegmentedButton(
                        selected = form.privacy == level,
                        onClick = { viewModel.updateForm { copy(privacy = level) } },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = 3),
                    ) { Text(level.replaceFirstChar { it.uppercase() }) }
                }
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = { viewModel.save() },
                enabled = !state.isSaving && form.name.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                if (state.isSaving) CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                else Text(if (viewModel.isNewMember) "Add Member" else "Save Changes")
            }
        }
    }

}

// ── Member profile ────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MemberProfileScreen(
    onNavigateUp: () -> Unit,
    onEdit: () -> Unit,
    viewModel: MemberProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }

    // Reload data when returning to this screen (e.g. after editing)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.load()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(state.deleted) {
        if (state.deleted) onNavigateUp()
    }

    val member = state.member
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            SheafLargeFlexibleTopAppBar(
                title = { Text(member?.displayNameOrName ?: "Profile") },
                subtitle = member?.pronouns?.let { { Text(it) } },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleRevisions() }) {
                        Icon(Icons.Default.History, contentDescription = "Bio history")
                    }
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        when {
            state.isLoading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            state.error != null && member == null -> Column(
                Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ErrorBanner(state.error!!)
                Button(onClick = { viewModel.load() }) { Text("Retry") }
            }

            member != null -> {
                val memberColor = member.color?.let { parseColor(it) }
                    ?: MaterialTheme.colorScheme.primaryContainer

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp)
                        .padding(top = 24.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Avatar hero
                    if (member.avatarUrl != null) {
                        AsyncImage(
                            model = member.avatarUrl,
                            contentDescription = null,
                            modifier = Modifier.size(100.dp).clip(CircleShape),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .clip(CircleShape)
                                .background(memberColor),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                member.initials,
                                style = MaterialTheme.typography.headlineMedium,
                                color = Color.White,
                            )
                        }
                    }

                    // Description
                    if (!member.description.isNullOrBlank()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        ) {
                            Text(
                                member.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }

                    // Details card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        val itemColors = ListItemDefaults.colors(containerColor = Color.Transparent)

                        if (member.color != null) {
                            ListItem(
                                headlineContent = { Text("Color") },
                                leadingContent = { ColorSwatch(member.color, size = 24.dp) },
                                colors = itemColors,
                            )
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        }

                        val birthday = member.birthday?.takeIf { it.isNotBlank() }?.let { formatBirthday(it) }
                        if (birthday != null) {
                            ListItem(
                                headlineContent = { Text("Birthday") },
                                trailingContent = { Text(birthday, style = MaterialTheme.typography.bodyMedium) },
                                leadingContent = { Icon(Icons.Default.Cake, contentDescription = null) },
                                colors = itemColors,
                            )
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        }

                        ListItem(
                            headlineContent = { Text("Privacy") },
                            trailingContent = {
                                Text(
                                    member.privacy.replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            },
                            leadingContent = { Icon(Icons.Default.Lock, contentDescription = null) },
                            colors = itemColors,
                        )
                    }

                    // Fronting actions
                    val isFronting = state.currentFronts.any { member.id in it.memberIds }
                    FilledTonalButton(
                        onClick = { if (isFronting) viewModel.removeFromFront() else viewModel.addToFront() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            if (isFronting) Icons.Default.PersonRemove else Icons.Default.PersonAdd,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(if (isFronting) "Remove from Front" else "Add to Front")
                    }

                    OutlinedButton(
                        onClick = { viewModel.switchSoleFronter() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.SwitchAccount, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Set ${member.displayNameOrName} as sole fronter")
                    }

                    if (state.error != null) {
                        ErrorBanner(state.error!!)
                    }

                    Spacer(Modifier.height(8.dp))

                    TextButton(
                        onClick = { showDeleteDialog = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Delete Member")
                    }
                }
            }
        }
    }

    if (showDeleteDialog && member != null) {
        LaunchedEffect(member.id) { viewModel.loadDeleteSafety() }
        MemberDeleteDialog(
            memberLabel = member.displayNameOrName,
            safety = state.deleteSafety,
            isDeleting = state.isDeleting,
            errorMessage = state.deleteError,
            onConfirm = { password, totpCode -> viewModel.delete(password, totpCode) },
            onDismiss = { showDeleteDialog = false; viewModel.clearDeleteError() },
        )
        LaunchedEffect(state.deleted) {
            if (state.deleted) showDeleteDialog = false
        }
    }

    if (state.showRevisions) {
        var openRevision by remember { mutableStateOf<ContentRevisionRead?>(null) }
        ModalBottomSheet(onDismissRequest = { viewModel.toggleRevisions() }) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Bio history (${state.revisions.size})",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(20.dp, 12.dp),
                )
                HorizontalDivider()
                when {
                    state.isLoadingRevisions -> Box(
                        Modifier.fillMaxWidth().padding(40.dp),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }

                    state.revisions.isEmpty() -> Text(
                        "No prior versions of this bio.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(20.dp),
                    )

                    else -> LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                        items(state.revisions, key = { it.id }) { rev ->
                            BioRevisionRow(
                                revision = rev,
                                onClick = { openRevision = rev },
                            )
                            HorizontalDivider()
                        }
                    }
                }
                Spacer(Modifier.navigationBarsPadding())
            }
        }

        val target = openRevision
        if (target != null) {
            BioRevisionDialog(
                revision = target,
                currentBody = member?.description.orEmpty(),
                isRestoring = state.isRestoring,
                onRestore = {
                    viewModel.restoreRevision(target.id)
                    openRevision = null
                },
                onDismiss = { openRevision = null },
            )
        }
    }
}

@Composable
private fun BioRevisionRow(
    revision: ContentRevisionRead,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            formatRevisionDate(revision.createdAt),
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            revision.body.ifBlank { "(empty bio)" },
            style = MaterialTheme.typography.bodySmall,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            color = if (revision.body.isBlank())
                MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onSurface,
        )
        if (revision.editorMemberNames.isNotEmpty()) {
            Text(
                "Edited by ${revision.editorMemberNames.joinToString(", ")}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun BioRevisionDialog(
    revision: ContentRevisionRead,
    currentBody: String,
    isRestoring: Boolean,
    onRestore: () -> Unit,
    onDismiss: () -> Unit,
) {
    var diffMode by remember { mutableStateOf(false) }
    val diff = remember(revision.id, currentBody) {
        if (revision.body == currentBody) emptyList()
        else diffBioLines(currentBody, revision.body)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(formatRevisionDate(revision.createdAt)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PrimaryTabRow(
                    selectedTabIndex = if (diffMode) 1 else 0,
                    containerColor = Color.Transparent,
                ) {
                    Tab(
                        selected = !diffMode,
                        onClick = { diffMode = false },
                        text = { Text("Preview") },
                    )
                    Tab(
                        selected = diffMode,
                        onClick = { diffMode = true },
                        text = { Text("Diff") },
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    if (!diffMode) {
                        Text(
                            revision.body.ifBlank { "(empty bio)" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (revision.body.isBlank())
                                MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurface,
                        )
                    } else if (diff.isEmpty() || diff.none { it.op != BioDiffOp.Equal }) {
                        Text(
                            "No differences from the current bio.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        BioDiffView(diff)
                    }
                }
                if (revision.editorMemberNames.isNotEmpty()) {
                    Text(
                        "Edited by ${revision.editorMemberNames.joinToString(", ")}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onRestore, enabled = !isRestoring) {
                Text(if (isRestoring) "Restoring…" else "Restore this version")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

@Composable
private fun BioDiffView(lines: List<BioDiffLine>) {
    val addedBg = Color(0xFF1B5E20).copy(alpha = 0.25f)
    val removedBg = Color(0xFFB71C1C).copy(alpha = 0.25f)
    Column(modifier = Modifier.fillMaxWidth()) {
        lines.forEach { line ->
            val (bg, prefix) = when (line.op) {
                BioDiffOp.Added -> addedBg to "+ "
                BioDiffOp.Removed -> removedBg to "- "
                BioDiffOp.Equal -> Color.Transparent to "  "
            }
            Text(
                "$prefix${line.text}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(bg)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

private val revisionDateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d, yyyy · HH:mm")

private fun formatRevisionDate(iso: String): String = runCatching {
    OffsetDateTime.parse(iso).toLocalDateTime().format(revisionDateFormatter)
}.getOrDefault(iso)

private fun formatBirthday(value: String): String? {
    val full = Regex("(\\d{4})-(\\d{2})-(\\d{2})").matchEntire(value)
    val yearless = Regex("--(\\d{2})-(\\d{2})").matchEntire(value)
    return when {
        full != null -> {
            val (y, m, d) = full.destructured
            "${birthdayMonthName(m.toInt())} ${d.toInt()}, $y"
        }
        yearless != null -> {
            val (m, d) = yearless.destructured
            "${birthdayMonthName(m.toInt())} ${d.toInt()}"
        }
        else -> null
    }
}

// ── Birthday picker ───────────────────────────────────────────────────────────

@Composable
private fun BirthdayPicker(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDialog by remember { mutableStateOf(false) }

    val fullMatch = remember(value) {
        Regex("(\\d{4})-(\\d{2})-(\\d{2})").matchEntire(value)
    }
    val yearlessMatch = remember(value) {
        Regex("--(\\d{2})-(\\d{2})").matchEntire(value)
    }

    val displayText = when {
        fullMatch != null -> {
            val (y, m, d) = fullMatch.destructured
            "${birthdayMonthName(m.toInt())} ${d.toInt()}, $y"
        }
        yearlessMatch != null -> {
            val (m, d) = yearlessMatch.destructured
            "${birthdayMonthName(m.toInt())} ${d.toInt()}"
        }
        else -> ""
    }

    val interactionSource = remember { MutableInteractionSource() }
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            if (interaction is PressInteraction.Release) showDialog = true
        }
    }

    OutlinedTextField(
        value = displayText,
        onValueChange = {},
        readOnly = true,
        label = { Text("Birthday") },
        placeholder = { Text("Not set") },
        trailingIcon = {
            if (value.isNotBlank()) {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(Icons.Default.Clear, contentDescription = "Clear birthday")
                }
            } else {
                Icon(Icons.Default.Cake, contentDescription = null)
            }
        },
        interactionSource = interactionSource,
        modifier = modifier,
    )

    if (showDialog) {
        BirthdayPickerDialog(
            initialHasYear = fullMatch != null,
            initialYear = fullMatch?.groupValues?.get(1)?.toInt() ?: java.time.LocalDate.now().year,
            initialMonth = (fullMatch?.groupValues?.get(2) ?: yearlessMatch?.groupValues?.get(1))?.toInt() ?: 1,
            initialDay = (fullMatch?.groupValues?.get(3) ?: yearlessMatch?.groupValues?.get(2))?.toInt() ?: 1,
            onDismiss = { showDialog = false },
            onConfirm = { formatted ->
                onValueChange(formatted)
                showDialog = false
            },
        )
    }
}

@Composable
private fun BirthdayPickerDialog(
    initialHasYear: Boolean,
    initialYear: Int,
    initialMonth: Int,
    initialDay: Int,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var includeYear by remember { mutableStateOf(initialHasYear) }
    var year by remember { mutableStateOf(initialYear) }
    var yearText by remember { mutableStateOf(initialYear.toString()) }
    var month by remember { mutableStateOf(initialMonth) }
    var day by remember { mutableStateOf(initialDay.coerceIn(1, birthdayDaysInMonth(initialMonth))) }
    var monthMenuExpanded by remember { mutableStateOf(false) }
    var dayMenuExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Birthday") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ExposedDropdownMenuBox(
                    expanded = monthMenuExpanded,
                    onExpandedChange = { monthMenuExpanded = it },
                ) {
                    OutlinedTextField(
                        value = birthdayMonthName(month),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Month") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = monthMenuExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                    )
                    ExposedDropdownMenu(
                        expanded = monthMenuExpanded,
                        onDismissRequest = { monthMenuExpanded = false },
                    ) {
                        (1..12).forEach { m ->
                            DropdownMenuItem(
                                text = { Text(birthdayMonthName(m)) },
                                onClick = {
                                    month = m
                                    day = day.coerceIn(1, birthdayDaysInMonth(m))
                                    monthMenuExpanded = false
                                },
                            )
                        }
                    }
                }

                ExposedDropdownMenuBox(
                    expanded = dayMenuExpanded,
                    onExpandedChange = { dayMenuExpanded = it },
                ) {
                    OutlinedTextField(
                        value = day.toString(),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Day") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dayMenuExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                    )
                    ExposedDropdownMenu(
                        expanded = dayMenuExpanded,
                        onDismissRequest = { dayMenuExpanded = false },
                    ) {
                        (1..birthdayDaysInMonth(month)).forEach { d ->
                            DropdownMenuItem(
                                text = { Text(d.toString()) },
                                onClick = { day = d; dayMenuExpanded = false },
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "Include year",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = includeYear, onCheckedChange = { includeYear = it })
                }

                AnimatedVisibility(visible = includeYear) {
                    OutlinedTextField(
                        value = yearText,
                        onValueChange = { input ->
                            yearText = input.filter { it.isDigit() }.take(4)
                            yearText.toIntOrNull()?.let { year = it }
                        },
                        label = { Text("Year") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val formatted = if (includeYear && yearText.length == 4) {
                    "%04d-%02d-%02d".format(year, month, day)
                } else {
                    "--%02d-%02d".format(month, day)
                }
                onConfirm(formatted)
            }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private fun birthdayMonthName(month: Int): String =
    java.time.Month.of(month)
        .getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault())

private fun birthdayDaysInMonth(month: Int): Int = when (month) {
    2 -> 29       // allow Feb 29 for year-optional dates
    4, 6, 9, 11 -> 30
    else -> 31
}
