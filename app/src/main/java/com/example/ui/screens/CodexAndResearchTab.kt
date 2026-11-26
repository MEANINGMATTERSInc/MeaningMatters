package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ai.CitationStyle
import com.example.ai.FormattedCitationResult
import com.example.data.model.Chapter
import com.example.data.model.CodexCategory
import com.example.data.model.CodexOrigin
import com.example.data.model.EvidenceLevel
import com.example.data.model.KnowledgeEntry
import com.example.data.model.ResearchClaim
import com.example.data.model.ResearchSource
import com.example.ui.components.EditorialCard
import com.example.ui.components.EpistemicBadge
import com.example.ui.components.GovernanceNoticeBanner
import com.example.ui.components.StatusPill
import com.example.ui.viewmodel.BookWorkspaceViewModel

@Composable
fun CodexAndResearchTab(
    viewModel: BookWorkspaceViewModel,
    codex: List<KnowledgeEntry>,
    sources: List<ResearchSource>,
    claims: List<ResearchClaim>,
    chapters: List<Chapter>,
    citationResult: FormattedCitationResult?,
    formattedBibliography: String?
) {
    var activeSubTab by remember { mutableIntStateOf(0) } // 0 = Knowledge Codex, 1 = Research & Claims, 2 = Citation Formatter
    var showAddCodexDialog by remember { mutableStateOf(false) }
    var showAddSourceDialog by remember { mutableStateOf(false) }
    var showAddClaimDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = activeSubTab == 0,
                    onClick = { activeSubTab = 0 },
                    label = { Text("Knowledge Codex (${codex.size})") },
                    modifier = Modifier.testTag("subtab_codex")
                )
                FilterChip(
                    selected = activeSubTab == 1,
                    onClick = { activeSubTab = 1 },
                    label = { Text("Sources & Claims (${sources.size})") },
                    modifier = Modifier.testTag("subtab_sources")
                )
                FilterChip(
                    selected = activeSubTab == 2,
                    onClick = { activeSubTab = 2 },
                    label = { Text("Citation Formatter") },
                    leadingIcon = { Icon(Icons.Outlined.FormatQuote, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.testTag("subtab_citations")
                )
            }
        }

        when (activeSubTab) {
            0 -> {
                item {
                    GovernanceNoticeBanner(
                        text = "Persistent Book Memory: Distinguish Author-Approved Facts, Research Findings, Interpretations, and AI Suggestions."
                    )
                }
                item {
                    Button(
                        onClick = { showAddCodexDialog = true },
                        modifier = Modifier.fillMaxWidth().testTag("add_codex_entry_button")
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Add Knowledge Codex Entry")
                    }
                }
                if (codex.isEmpty()) {
                    item {
                        EditorialCard(title = "Empty Knowledge Codex", subtitle = "Store persistent book concepts, people, places, traditions, quotations & terminology") {
                            Text(
                                "Codex entries persist across sessions in Cloud Firestore and are automatically supplied to AI authoring agents.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    items(codex, key = { it.id }) { entry ->
                        CodexEntryCard(
                            entry = entry,
                            onToggleApprove = { viewModel.toggleApproveCodex(entry) },
                            onDelete = { viewModel.deleteCodexEntry(entry.id) }
                        )
                    }
                }
            }

            1 -> {
                item {
                    GovernanceNoticeBanner(text = stringResource(id = R.string.epistemic_notice))
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { showAddSourceDialog = true },
                            modifier = Modifier.weight(1f).testTag("add_source_button")
                        ) {
                            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Register Source")
                        }
                        OutlinedButton(
                            onClick = { showAddClaimDialog = true },
                            modifier = Modifier.weight(1f).testTag("add_claim_button")
                        ) {
                            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Link Claim")
                        }
                    }
                }
                if (sources.isNotEmpty()) {
                    item {
                        Text("Registered Sources (${sources.size})", style = MaterialTheme.typography.titleMedium)
                    }
                    items(sources, key = { it.id }) { src ->
                        SourceCard(
                            source = src,
                            onFormatCitation = {
                                activeSubTab = 2
                                viewModel.formatSourceCitation(src, CitationStyle.APA, "")
                            },
                            onDelete = { viewModel.deleteResearchSource(src.id) }
                        )
                    }
                }
                if (claims.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(4.dp))
                        Text("Source-to-Claim Evidence Map (${claims.size})", style = MaterialTheme.typography.titleMedium)
                    }
                    items(claims, key = { it.id }) { claim ->
                        val linkedSource = sources.firstOrNull { it.id == claim.sourceId }
                        val linkedChapter = chapters.firstOrNull { it.id == claim.chapterId }
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    EpistemicBadge(EvidenceLevel.fromRaw(claim.evidenceLevel))
                                    IconButton(onClick = { viewModel.deleteResearchClaim(claim.id) }) {
                                        Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete claim", modifier = Modifier.size(18.dp))
                                    }
                                }
                                Text(claim.statement, style = MaterialTheme.typography.bodyMedium)
                                if (linkedSource != null) {
                                    Text(
                                        text = "Source: ${linkedSource.title} (${linkedSource.author})",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                if (linkedChapter != null) {
                                    Text(
                                        text = "Chapter: ${linkedChapter.title}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            2 -> {
                item {
                    CitationFormatterSection(
                        sources = sources,
                        citationResult = citationResult,
                        formattedBibliography = formattedBibliography,
                        onFormatSource = { src, style, locator ->
                            viewModel.formatSourceCitation(src, style, locator)
                        },
                        onCompileFullBibliography = { style ->
                            viewModel.generateFullBibliography(style)
                        }
                    )
                }
            }
        }
    }

    if (showAddCodexDialog) {
        AddCodexDialog(
            onDismiss = { showAddCodexDialog = false },
            onAdd = { entry ->
                viewModel.addCodexEntry(entry)
                showAddCodexDialog = false
            }
        )
    }

    if (showAddSourceDialog) {
        AddSourceDialog(
            chapters = chapters,
            onDismiss = { showAddSourceDialog = false },
            onAdd = { src ->
                viewModel.addResearchSource(src)
                showAddSourceDialog = false
            }
        )
    }

    if (showAddClaimDialog) {
        AddClaimDialog(
            sources = sources,
            chapters = chapters,
            onDismiss = { showAddClaimDialog = false },
            onAdd = { claim ->
                viewModel.addResearchClaim(claim)
                showAddClaimDialog = false
            }
        )
    }
}

@Composable
private fun CitationFormatterSection(
    sources: List<ResearchSource>,
    citationResult: FormattedCitationResult?,
    formattedBibliography: String?,
    onFormatSource: (ResearchSource, CitationStyle, String) -> Unit,
    onCompileFullBibliography: (CitationStyle) -> Unit
) {
    val context = LocalContext.current
    var selectedStyle by remember { mutableStateOf(CitationStyle.APA) }
    var selectedSourceId by remember { mutableStateOf<String?>(sources.firstOrNull()?.id) }
    var title by remember { mutableStateOf(sources.firstOrNull()?.title.orEmpty()) }
    var author by remember { mutableStateOf(sources.firstOrNull()?.author.orEmpty()) }
    var year by remember { mutableStateOf(sources.firstOrNull()?.publicationDate.orEmpty()) }
    var publisher by remember { mutableStateOf(sources.firstOrNull()?.publisher.orEmpty()) }
    var url by remember { mutableStateOf(sources.firstOrNull()?.url.orEmpty()) }
    var sourceType by remember { mutableStateOf(sources.firstOrNull()?.sourceType ?: "Book") }
    var locator by remember { mutableStateOf("") }

    fun copyToClipboard(label: String, value: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        cm?.setPrimaryClip(ClipData.newPlainText(label, value))
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GovernanceNoticeBanner(
            text = "Citation Integrity: Gemini formats strictly from the details you supply — missing bibliographic fields are flagged rather than fabricated."
        )

        EditorialCard(
            title = "AI Citation Formatting Tool",
            subtitle = "Generate in-text citations & bibliography entries in APA, MLA, Chicago, Harvard, or IEEE"
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Citation Style", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CitationStyle.entries.forEach { style ->
                        FilterChip(
                            selected = selectedStyle == style,
                            onClick = { selectedStyle = style },
                            label = { Text(style.label) },
                            modifier = Modifier.testTag("citation_style_${style.name}")
                        )
                    }
                }

                if (sources.isNotEmpty()) {
                    Text("Load from Registered Sources", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        sources.forEach { s ->
                            FilterChip(
                                selected = selectedSourceId == s.id,
                                onClick = {
                                    selectedSourceId = s.id
                                    title = s.title
                                    author = s.author
                                    year = s.publicationDate
                                    publisher = s.publisher
                                    url = s.url
                                    sourceType = s.sourceType
                                },
                                label = { Text(s.title.take(24)) }
                            )
                        }
                    }
                }

                OutlinedTextField(value = title, onValueChange = { title = it; selectedSourceId = null }, label = { Text("Source Title *") }, modifier = Modifier.fillMaxWidth().testTag("citation_title_input"))
                OutlinedTextField(value = author, onValueChange = { author = it }, label = { Text("Author(s)") }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = year, onValueChange = { year = it }, label = { Text("Year / Date") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = sourceType, onValueChange = { sourceType = it }, label = { Text("Type (Book, Article)") }, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(value = publisher, onValueChange = { publisher = it }, label = { Text("Publisher / Journal") }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("URL / DOI") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = locator, onValueChange = { locator = it }, label = { Text("Page / Section") }, modifier = Modifier.weight(1f))
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        enabled = title.isNotBlank(),
                        onClick = {
                            val src = ResearchSource(
                                id = selectedSourceId.orEmpty(),
                                title = title,
                                author = author,
                                publicationDate = year,
                                publisher = publisher,
                                url = url,
                                sourceType = sourceType
                            )
                            onFormatSource(src, selectedStyle, locator)
                        },
                        modifier = Modifier.weight(1f).testTag("generate_citation_button")
                    ) {
                        Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Format ${selectedStyle.label}")
                    }
                    OutlinedButton(
                        enabled = sources.isNotEmpty(),
                        onClick = { onCompileFullBibliography(selectedStyle) },
                        modifier = Modifier.testTag("compile_bibliography_button")
                    ) {
                        Text("Full Bib.")
                    }
                }
            }
        }

        if (citationResult != null) {
            EditorialCard(
                title = "Formatted ${citationResult.style.edition}",
                subtitle = citationResult.integrityNote
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CitationOutputRow(
                        label = "In-Text (Parenthetical)",
                        value = citationResult.inTextParenthetical,
                        onCopy = { copyToClipboard("In-Text Citation", citationResult.inTextParenthetical) }
                    )
                    CitationOutputRow(
                        label = "In-Text (Narrative)",
                        value = citationResult.inTextNarrative,
                        onCopy = { copyToClipboard("Narrative Citation", citationResult.inTextNarrative) }
                    )
                    CitationOutputRow(
                        label = "Bibliography Entry",
                        value = citationResult.bibliographyEntry,
                        onCopy = { copyToClipboard("Bibliography Entry", citationResult.bibliographyEntry) }
                    )
                }
            }
        }

        if (!formattedBibliography.isNullOrBlank()) {
            EditorialCard(
                title = "Compiled Book Bibliography",
                trailing = {
                    IconButton(onClick = { copyToClipboard("Full Bibliography", formattedBibliography) }) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy bibliography")
                    }
                }
            ) {
                Text(text = formattedBibliography, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun CitationOutputRow(label: String, value: String, onCopy: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.height(2.dp))
                Text(value, style = MaterialTheme.typography.bodyMedium)
            }
            IconButton(onClick = onCopy) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy $label", modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun CodexEntryCard(
    entry: KnowledgeEntry,
    onToggleApprove: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusPill(text = CodexCategory.fromRaw(entry.category).label)
                    EpistemicBadge(EvidenceLevel.fromRaw(entry.epistemicStatus))
                }
                Row {
                    IconButton(onClick = onToggleApprove) {
                        Icon(
                            Icons.Outlined.CheckCircle,
                            contentDescription = "Toggle author approval",
                            tint = if (entry.approved) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete entry")
                    }
                }
            }
            Text(entry.title, style = MaterialTheme.typography.titleLarge)
            Text(entry.summary, style = MaterialTheme.typography.bodyMedium)
            if (entry.details.isNotBlank()) {
                Text(entry.details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                text = "Origin: ${CodexOrigin.fromRaw(entry.originType).label} • ${if (entry.approved) "Author-Approved" else "Pending Approval"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SourceCard(
    source: ResearchSource,
    onFormatCitation: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    EpistemicBadge(EvidenceLevel.fromRaw(source.evidenceLevel))
                    StatusPill(
                        text = source.sourceType,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row {
                    IconButton(onClick = onFormatCitation) {
                        Icon(Icons.Outlined.FormatQuote, contentDescription = "Format citation")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete source")
                    }
                }
            }
            Text(source.title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = listOf(source.author, source.publicationDate, source.publisher).filter { it.isNotBlank() }.joinToString(" • "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (source.citationText.isNotBlank()) {
                Text(
                    text = "Citation: ${source.citationText}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (source.attachmentName.isNotBlank()) {
                Text(
                    text = "Attached file reference: ${source.attachmentName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            Text(
                text = source.verificationNote,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AddCodexDialog(
    onDismiss: () -> Unit,
    onAdd: (KnowledgeEntry) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var summary by remember { mutableStateOf("") }
    var details by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(CodexCategory.CONCEPT) }
    var evidence by remember { mutableStateOf(EvidenceLevel.INTERPRETATION) }
    var origin by remember { mutableStateOf(CodexOrigin.AUTHOR_APPROVED) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Knowledge Codex Entry") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Entry Title *") }, modifier = Modifier.fillMaxWidth())
                Text("Category", style = MaterialTheme.typography.labelMedium)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CodexCategory.entries.forEach { c ->
                        FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c.label) })
                    }
                }
                Text("Epistemic Classification", style = MaterialTheme.typography.labelMedium)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    EvidenceLevel.entries.forEach { ev ->
                        FilterChip(selected = evidence == ev, onClick = { evidence = ev }, label = { Text(ev.label) })
                    }
                }
                Text("Origin", style = MaterialTheme.typography.labelMedium)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CodexOrigin.entries.forEach { o ->
                        FilterChip(selected = origin == o, onClick = { origin = o }, label = { Text(o.label) })
                    }
                }
                OutlinedTextField(value = summary, onValueChange = { summary = it }, label = { Text("Summary *") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = details, onValueChange = { details = it }, label = { Text("Extended Notes & Context") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                enabled = title.isNotBlank() && summary.isNotBlank(),
                onClick = {
                    onAdd(
                        KnowledgeEntry(
                            title = title,
                            category = category.name,
                            summary = summary,
                            details = details,
                            epistemicStatus = evidence.name,
                            originType = origin.name,
                            approved = origin == CodexOrigin.AUTHOR_APPROVED
                        )
                    )
                }
            ) { Text("Save Entry") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun AddSourceDialog(
    chapters: List<Chapter>,
    onDismiss: () -> Unit,
    onAdd: (ResearchSource) -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("") }
    var pubDate by remember { mutableStateOf("") }
    var publisher by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var sourceType by remember { mutableStateOf("Book") }
    var notes by remember { mutableStateOf("") }
    var evidence by remember { mutableStateOf(EvidenceLevel.UNVERIFIED) }
    var chapterId by remember { mutableStateOf(chapters.firstOrNull()?.id.orEmpty()) }
    var attachmentName by remember { mutableStateOf("") }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) attachmentName = it.getString(idx)
                }
            }
            if (attachmentName.isBlank()) attachmentName = uri.lastPathSegment.orEmpty()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Register Research Source") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Source Title *") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = author, onValueChange = { author = it }, label = { Text("Author(s)") }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = pubDate, onValueChange = { pubDate = it }, label = { Text("Date / Year") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = sourceType, onValueChange = { sourceType = it }, label = { Text("Type") }, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(value = publisher, onValueChange = { publisher = it }, label = { Text("Publisher / Journal") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("URL / DOI") }, modifier = Modifier.fillMaxWidth())
                Text("Evidence Classification", style = MaterialTheme.typography.labelMedium)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    EvidenceLevel.entries.forEach { ev ->
                        FilterChip(selected = evidence == ev, onClick = { evidence = ev }, label = { Text(ev.label) })
                    }
                }
                OutlinedButton(
                    onClick = { filePicker.launch(arrayOf("application/pdf", "text/plain", "text/markdown")) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.AttachFile, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (attachmentName.isBlank()) "Attach Research File (Optional)" else "Attached: $attachmentName")
                }
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Research Notes") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                enabled = title.isNotBlank(),
                onClick = {
                    onAdd(
                        ResearchSource(
                            title = title,
                            author = author,
                            publicationDate = pubDate,
                            publisher = publisher,
                            url = url,
                            sourceType = sourceType,
                            notes = notes,
                            evidenceLevel = evidence.name,
                            chapterId = chapterId,
                            attachmentName = attachmentName
                        )
                    )
                }
            ) { Text("Register") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun AddClaimDialog(
    sources: List<ResearchSource>,
    chapters: List<Chapter>,
    onDismiss: () -> Unit,
    onAdd: (ResearchClaim) -> Unit
) {
    var statement by remember { mutableStateOf("") }
    var selectedSourceId by remember { mutableStateOf(sources.firstOrNull()?.id.orEmpty()) }
    var selectedChapterId by remember { mutableStateOf(chapters.firstOrNull()?.id.orEmpty()) }
    var evidence by remember { mutableStateOf(EvidenceLevel.SUPPORTED) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Link Claim to Evidence") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = statement, onValueChange = { statement = it }, label = { Text("Claim Statement *") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                Text("Evidence Level", style = MaterialTheme.typography.labelMedium)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    EvidenceLevel.entries.forEach { ev ->
                        FilterChip(selected = evidence == ev, onClick = { evidence = ev }, label = { Text(ev.label) })
                    }
                }
                if (sources.isNotEmpty()) {
                    Text("Supporting Source", style = MaterialTheme.typography.labelMedium)
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        sources.forEach { s ->
                            FilterChip(selected = selectedSourceId == s.id, onClick = { selectedSourceId = s.id }, label = { Text(s.title.take(20)) })
                        }
                    }
                }
                if (chapters.isNotEmpty()) {
                    Text("Relevant Chapter", style = MaterialTheme.typography.labelMedium)
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        chapters.forEach { ch ->
                            FilterChip(selected = selectedChapterId == ch.id, onClick = { selectedChapterId = ch.id }, label = { Text(ch.title.take(20)) })
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = statement.isNotBlank(),
                onClick = {
                    val src = sources.firstOrNull { it.id == selectedSourceId }
                    onAdd(
                        ResearchClaim(
                            statement = statement,
                            sourceId = selectedSourceId,
                            chapterId = selectedChapterId,
                            evidenceLevel = evidence.name,
                            citationRef = src?.citationText?.ifBlank { "${src.author} (${src.publicationDate})" }.orEmpty(),
                            verified = evidence == EvidenceLevel.VERIFIED || evidence == EvidenceLevel.SUPPORTED
                        )
                    )
                }
            ) { Text("Link Claim") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
