package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.Book
import com.example.data.model.BookStatus
import com.example.data.model.DriveSyncStatus
import com.example.ui.components.EditorialMetricTile
import com.example.ui.components.StatusPill
import com.example.ui.viewmodel.DashboardViewModel
import com.example.ui.viewmodel.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onOpenBook: (String) -> Unit,
    onOpenSettings: () -> Unit
) {
    val booksState by viewModel.booksState.collectAsStateWithLifecycle()
    val profile by viewModel.userProfile.collectAsStateWithLifecycle()
    val statusMsg by viewModel.statusMessage.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var showArchived by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var bookToRename by remember { mutableStateOf<Book?>(null) }
    var bookToDelete by remember { mutableStateOf<Book?>(null) }

    LaunchedEffect(statusMsg) {
        statusMsg?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearStatusMessage()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                title = {
                    Column {
                        Text(
                            text = "MEANINGMATTERS",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = "Author Studio",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.testTag("settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = "Settings and security"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreateDialog = true },
                icon = { Icon(Icons.Outlined.Add, contentDescription = "Create book") },
                text = { Text("New Book") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("new_book_fab")
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter
        ) {
            when (val state = booksState) {
                is UiState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is UiState.Error -> {
                    Column(
                        modifier = Modifier
                            .padding(24.dp)
                            .widthIn(max = 520.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Unable to sync your library",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                is UiState.Success -> {
                    val allBooks = state.data
                    val activeBooks = allBooks.filter { !it.archived }
                    val archivedBooks = allBooks.filter { it.archived }
                    val displayed = if (showArchived) archivedBooks else activeBooks

                    val totalWords = activeBooks.sumOf { it.wordCount }
                    val totalChapters = activeBooks.sumOf { it.chapterCount }
                    val completedChapters = activeBooks.sumOf { it.completedChapters }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 840.dp),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Editorial Hero Header
                        item {
                            Card(
                                shape = MaterialTheme.shapes.large,
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Column {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(124.dp)
                                    ) {
                                        Image(
                                            painter = painterResource(id = R.drawable.img_editorial_hero),
                                            contentDescription = "Author desk hero banner",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        Surface(
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.82f),
                                            modifier = Modifier
                                                .align(Alignment.BottomStart)
                                                .padding(12.dp),
                                            shape = MaterialTheme.shapes.small
                                        ) {
                                            Text(
                                                text = "Welcome back, ${profile?.displayName?.ifBlank { "Author" } ?: "Author"}",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                            )
                                        }
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        EditorialMetricTile(
                                            label = "ACTIVE BOOKS",
                                            value = activeBooks.size.toString(),
                                            modifier = Modifier.weight(1f)
                                        )
                                        EditorialMetricTile(
                                            label = "MANUSCRIPT WORDS",
                                            value = totalWords.toString(),
                                            modifier = Modifier.weight(1f)
                                        )
                                        EditorialMetricTile(
                                            label = "APPROVED CH.",
                                            value = "$completedChapters/$totalChapters",
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }

                        // Filter Chips
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FilterChip(
                                    selected = !showArchived,
                                    onClick = { showArchived = false },
                                    label = { Text("My Books (${activeBooks.size})") },
                                    modifier = Modifier.testTag("filter_active_books")
                                )
                                FilterChip(
                                    selected = showArchived,
                                    onClick = { showArchived = true },
                                    label = { Text("Archived (${archivedBooks.size})") },
                                    modifier = Modifier.testTag("filter_archived_books")
                                )
                            }
                        }

                        if (displayed.isEmpty()) {
                            item {
                                Surface(
                                    shape = MaterialTheme.shapes.large,
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(28.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.AutoStories,
                                            contentDescription = "Empty library",
                                            tint = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.size(44.dp)
                                        )
                                        Spacer(Modifier.height(12.dp))
                                        Text(
                                            text = if (showArchived) "No archived manuscripts" else "Begin your first serious work",
                                            style = MaterialTheme.typography.headlineSmall
                                        )
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            text = if (showArchived) {
                                                "Archived book projects will appear here."
                                            } else {
                                                "Define your book's purpose, thesis, and intellectual boundaries first — MeaningMatters guides you from Canonical Ledger to publication-ready PDF, DOCX, and EPUB."
                                            },
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (!showArchived) {
                                            Spacer(Modifier.height(16.dp))
                                            Button(
                                                onClick = { showCreateDialog = true },
                                                modifier = Modifier.testTag("empty_create_book_button")
                                            ) {
                                                Icon(Icons.Outlined.Add, contentDescription = null)
                                                Spacer(Modifier.width(8.dp))
                                                Text("Create First Book")
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            items(displayed, key = { it.id }) { book ->
                                BookDashboardCard(
                                    book = book,
                                    onOpen = { onOpenBook(book.id) },
                                    onRename = { bookToRename = book },
                                    onToggleArchive = { viewModel.toggleArchiveBook(book.id, book.archived) },
                                    onDelete = { bookToDelete = book }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateBookPurposeDialog(
            busy = busy,
            onDismiss = { showCreateDialog = false },
            onCreate = { title, subtitle, genre, audience, purpose, thesis, transformation, values, boundaries, style, targetWords ->
                viewModel.createBook(
                    title = title,
                    subtitle = subtitle,
                    genre = genre,
                    intendedAudience = audience,
                    centralPurpose = purpose,
                    mainThesis = thesis,
                    readerTransformation = transformation,
                    authorValues = values,
                    intellectualBoundaries = boundaries,
                    desiredStyle = style,
                    targetWordCount = targetWords
                ) { createdId ->
                    showCreateDialog = false
                    onOpenBook(createdId)
                }
            }
        )
    }

    bookToRename?.let { target ->
        var newTitle by remember(target.id) { mutableStateOf(target.title) }
        var newSubtitle by remember(target.id) { mutableStateOf(target.subtitle) }
        AlertDialog(
            onDismissRequest = { bookToRename = null },
            title = { Text("Rename Book Project") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newTitle,
                        onValueChange = { newTitle = it },
                        label = { Text("Book Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newSubtitle,
                        onValueChange = { newSubtitle = it },
                        label = { Text("Subtitle") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.renameBook(target.id, newTitle, newSubtitle)
                    bookToRename = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { bookToRename = null }) { Text("Cancel") }
            }
        )
    }

    bookToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { bookToDelete = null },
            title = { Text("Delete '${target.title}'?") },
            text = {
                Text("This permanently deletes the book, all chapters, version checkpoints, Knowledge Codex entries, sources, and review reports from Cloud Firestore.")
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.deleteBook(target.id)
                    bookToDelete = null
                }) { Text("Delete Permanently") }
            },
            dismissButton = {
                TextButton(onClick = { bookToDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun BookDashboardCard(
    book: Book,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onToggleArchive: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val statusEnum = BookStatus.fromRaw(book.status)
    val syncEnum = DriveSyncStatus.fromRaw(book.driveSyncStatus)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .testTag("book_card_${book.id}"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        StatusPill(text = statusEnum.label)
                        StatusPill(
                            text = "Stage ${book.pipelineStage}/8",
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = book.title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (book.subtitle.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = book.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = "Book options")
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                            onClick = { menuExpanded = false; onRename() }
                        )
                        DropdownMenuItem(
                            text = { Text(if (book.archived) "Restore" else "Archive") },
                            leadingIcon = {
                                Icon(
                                    if (book.archived) Icons.Outlined.Unarchive else Icons.Outlined.Archive,
                                    contentDescription = null
                                )
                            },
                            onClick = { menuExpanded = false; onToggleArchive() }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            leadingIcon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null) },
                            onClick = { menuExpanded = false; onDelete() }
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            LinearProgressIndicator(
                progress = { book.progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(MaterialTheme.shapes.extraSmall)
            )

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${book.wordCount} / ${book.targetWordCount} words",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${book.completedChapters}/${book.chapterCount} ch. complete",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusPill(
                    text = if (book.purposeApproved) "Purpose Approved" else "Purpose Draft",
                    containerColor = if (book.purposeApproved) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (book.purposeApproved) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                )
                StatusPill(
                    text = syncEnum.label,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CreateBookPurposeDialog(
    busy: Boolean,
    onDismiss: () -> Unit,
    onCreate: (String, String, String, String, String, String, String, String, String, String, Long) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var subtitle by remember { mutableStateOf("") }
    var genre by remember { mutableStateOf("Philosophy & Non-Fiction") }
    var audience by remember { mutableStateOf("") }
    var centralPurpose by remember { mutableStateOf("") }
    var mainThesis by remember { mutableStateOf("") }
    var transformation by remember { mutableStateOf("") }
    var values by remember { mutableStateOf("Truthfulness, intellectual humility, accountability") }
    var boundaries by remember { mutableStateOf("Never fabricate sources; distinguish verified facts from interpretation") }
    var style by remember { mutableStateOf("Clear, disciplined scholarly editorial prose") }
    var targetWordsText by remember { mutableStateOf("50000") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("New Book Concept", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Meaning before generation — define your book's purpose and thesis.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Book Title *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("create_book_title_input")
                )
                OutlinedTextField(
                    value = subtitle,
                    onValueChange = { subtitle = it },
                    label = { Text("Subtitle") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = genre,
                    onValueChange = { genre = it },
                    label = { Text("Genre") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = audience,
                    onValueChange = { audience = it },
                    label = { Text("Intended Audience") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = centralPurpose,
                    onValueChange = { centralPurpose = it },
                    label = { Text("Central Purpose *") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth().testTag("create_book_purpose_input")
                )
                OutlinedTextField(
                    value = mainThesis,
                    onValueChange = { mainThesis = it },
                    label = { Text("Main Thesis *") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth().testTag("create_book_thesis_input")
                )
                OutlinedTextField(
                    value = transformation,
                    onValueChange = { transformation = it },
                    label = { Text("Reader Transformation") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = values,
                    onValueChange = { values = it },
                    label = { Text("Author's Values") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = boundaries,
                    onValueChange = { boundaries = it },
                    label = { Text("Intellectual Boundaries") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = style,
                    onValueChange = { style = it },
                    label = { Text("Desired Writing Style") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = targetWordsText,
                    onValueChange = { targetWordsText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Target Manuscript Length (Words)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                enabled = !busy && title.isNotBlank(),
                onClick = {
                    val targetWords = targetWordsText.toLongOrNull() ?: 50000L
                    onCreate(
                        title, subtitle, genre, audience, centralPurpose, mainThesis,
                        transformation, values, boundaries, style, targetWords
                    )
                },
                modifier = Modifier.testTag("confirm_create_book_button")
            ) {
                Text(if (busy) "Creating…" else "Create Book")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
