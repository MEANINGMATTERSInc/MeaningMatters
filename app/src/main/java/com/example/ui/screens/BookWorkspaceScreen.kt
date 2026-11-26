package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.Publish
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ai.WritingAssistAction
import com.example.data.prefs.StudioUiPrefs
import com.example.ui.components.AiTaskBanner
import com.example.ui.components.StatusPill
import com.example.ui.viewmodel.BookWorkspaceViewModel

private data class WorkspaceTabItem(
    val label: String,
    val icon: ImageVector,
    val testTag: String
)

private val WorkspaceTabs = listOf(
    WorkspaceTabItem("Purpose", Icons.Outlined.AutoStories, "tab_purpose"),
    WorkspaceTabItem("Architect", Icons.Outlined.AccountTree, "tab_architect"),
    WorkspaceTabItem("Studio", Icons.Outlined.EditNote, "tab_studio"),
    WorkspaceTabItem("Research", Icons.Outlined.LibraryBooks, "tab_research"),
    WorkspaceTabItem("Review", Icons.Outlined.VerifiedUser, "tab_review"),
    WorkspaceTabItem("Publish", Icons.Outlined.Publish, "tab_publish")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookWorkspaceScreen(
    viewModel: BookWorkspaceViewModel,
    uiPrefs: StudioUiPrefs,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val book by viewModel.book.collectAsStateWithLifecycle()
    val purpose by viewModel.purpose.collectAsStateWithLifecycle()
    val voice by viewModel.voice.collectAsStateWithLifecycle()
    val ledger by viewModel.ledger.collectAsStateWithLifecycle()
    val chapters by viewModel.chapters.collectAsStateWithLifecycle()
    val selectedChapterId by viewModel.selectedChapterId.collectAsStateWithLifecycle()
    val chapterVersions by viewModel.chapterVersions.collectAsStateWithLifecycle()
    val codex by viewModel.codex.collectAsStateWithLifecycle()
    val sources by viewModel.sources.collectAsStateWithLifecycle()
    val claims by viewModel.claims.collectAsStateWithLifecycle()
    val reviews by viewModel.reviews.collectAsStateWithLifecycle()
    val aiLogs by viewModel.aiLogs.collectAsStateWithLifecycle()
    val exports by viewModel.exports.collectAsStateWithLifecycle()
    val aiState by viewModel.aiState.collectAsStateWithLifecycle()
    val snackbarMsg by viewModel.snackbar.collectAsStateWithLifecycle()
    val saveIndicator by viewModel.saveIndicator.collectAsStateWithLifecycle()
    val citationResult by viewModel.citationResult.collectAsStateWithLifecycle()
    val formattedBib by viewModel.formattedBibliography.collectAsStateWithLifecycle()
    val lastExport by viewModel.lastExportResult.collectAsStateWithLifecycle()
    val pipelineActive by viewModel.pipelineActive.collectAsStateWithLifecycle()

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var focusMode by remember(uiPrefs.focusModeDefault) { mutableStateOf(uiPrefs.focusModeDefault) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(snackbarMsg) {
        snackbarMsg?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSnackbar()
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 700.dp

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                if (!(selectedTab == 2 && focusMode)) {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background
                        ),
                        navigationIcon = {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier.testTag("workspace_back_button")
                            ) {
                                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back to library")
                            }
                        },
                        title = {
                            Column {
                                Text(
                                    text = book?.title?.ifBlank { "Manuscript Studio" } ?: "Manuscript Studio",
                                    style = MaterialTheme.typography.titleLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${book?.wordCount ?: 0} words • Stage ${book?.pipelineStage ?: 1}/8",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        actions = {
                            StatusPill(
                                text = saveIndicator,
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(end = 12.dp)
                            )
                        }
                    )
                }
            },
            bottomBar = {
                if (!useRail && !(selectedTab == 2 && focusMode)) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.navigationBarsPadding()
                    ) {
                        WorkspaceTabs.forEachIndexed { idx, item ->
                            NavigationBarItem(
                                selected = selectedTab == idx,
                                onClick = { selectedTab = idx },
                                icon = { Icon(item.icon, contentDescription = item.label) },
                                label = {
                                    Text(
                                        text = item.label,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                    }
                }
            }
        ) { padding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                if (useRail && !(selectedTab == 2 && focusMode)) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        WorkspaceTabs.forEachIndexed { idx, item ->
                            NavigationRailItem(
                                selected = selectedTab == idx,
                                onClick = { selectedTab = idx },
                                icon = { Icon(item.icon, contentDescription = item.label) },
                                label = { Text(item.label) },
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 880.dp)
                    ) {
                        AiTaskBanner(
                            aiState = aiState,
                            onDismissError = { viewModel.clearAiError() },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )

                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            when (selectedTab) {
                                0 -> PurposeAndLedgerTab(
                                    viewModel = viewModel,
                                    purpose = purpose,
                                    ledger = ledger,
                                    voice = voice
                                )
                                1 -> ArchitectTab(
                                    viewModel = viewModel,
                                    book = book,
                                    chapters = chapters,
                                    pipelineActive = pipelineActive,
                                    onOpenChapterInStudio = { chapterId ->
                                        viewModel.selectChapter(chapterId)
                                        selectedTab = 2
                                    }
                                )
                                2 -> WritingStudioTab(
                                    viewModel = viewModel,
                                    chapters = chapters,
                                    selectedChapterId = selectedChapterId,
                                    versions = chapterVersions,
                                    aiState = aiState,
                                    editorFontSizeSp = uiPrefs.editorFontSizeSp,
                                    autoSaveEnabled = uiPrefs.autoSaveEnabled,
                                    focusMode = focusMode,
                                    onToggleFocusMode = { focusMode = !focusMode }
                                )
                                3 -> CodexAndResearchTab(
                                    viewModel = viewModel,
                                    codex = codex,
                                    sources = sources,
                                    claims = claims,
                                    chapters = chapters,
                                    citationResult = citationResult,
                                    formattedBibliography = formattedBib
                                )
                                4 -> ReviewAndGovernanceTab(
                                    viewModel = viewModel,
                                    chapters = chapters,
                                    selectedChapterId = selectedChapterId,
                                    reviews = reviews,
                                    aiLogs = aiLogs,
                                    onReviseChapterFromReview = { chId ->
                                        viewModel.selectChapter(chId)
                                        selectedTab = 2
                                        chapters.firstOrNull { it.id == chId }?.let { ch ->
                                            viewModel.runWritingAssist(ch, WritingAssistAction.REVISE_FROM_REVIEW)
                                        }
                                    }
                                )
                                5 -> PublishTab(
                                    viewModel = viewModel,
                                    book = book,
                                    chapters = chapters,
                                    reviews = reviews,
                                    exports = exports,
                                    lastExportResult = lastExport
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
