package com.example.ui.screens

import android.content.Intent
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.data.model.Book
import com.example.data.model.Chapter
import com.example.data.model.DriveSyncStatus
import com.example.data.model.ExportFormat
import com.example.data.model.ExportJob
import com.example.data.model.ReviewReport
import com.example.export.ExportResult
import com.example.ui.components.EditorialCard
import com.example.ui.components.GovernanceNoticeBanner
import com.example.ui.components.StatusPill
import com.example.ui.viewmodel.BookWorkspaceViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun PublishTab(
    viewModel: BookWorkspaceViewModel,
    book: Book?,
    chapters: List<Chapter>,
    reviews: List<ReviewReport>,
    exports: List<ExportJob>,
    lastExportResult: ExportResult?
) {
    val context = LocalContext.current
    var pendingSaveFile by remember { mutableStateOf<File?>(null) }

    val saveToDeviceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        val src = pendingSaveFile
        if (uri != null && src != null && src.exists()) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    src.inputStream().use { it.copyTo(out) }
                }
            }
        }
        pendingSaveFile = null
    }

    val importDocumentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                var displayName = "Imported Chapter"
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) displayName = cursor.getString(idx).substringBeforeLast(".")
                    }
                }
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (text.isNotBlank()) {
                    viewModel.importChapterFromText(displayName, text)
                }
            }
        }
    }

    fun shareFile(result: ExportResult) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            result.file
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = result.format.mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "${book?.title ?: "Manuscript"} (${result.format.label})")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share or Export to Google Drive / Docs"))
    }

    val draftedCount = chapters.count { it.wordCount > 0L }
    val protocolReadyCount = chapters.count { it.passesFullCompletionProtocol }
    val syncEnum = DriveSyncStatus.fromRaw(book?.driveSyncStatus)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            EditorialCard(
                title = "Stage 8: Final Manuscript Readiness",
                subtitle = "Whole-book assembly checklist before publishing"
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ReadinessRow("Book Purpose Document Approved", book?.purposeApproved == true)
                    ReadinessRow("Canonical Epistemic Ledger Locked", book?.ledgerApproved == true)
                    ReadinessRow("Chapters Drafted ($draftedCount / ${chapters.size})", chapters.isNotEmpty() && draftedCount == chapters.size)
                    ReadinessRow("7-Point Completion Protocol ($protocolReadyCount / ${chapters.size})", chapters.isNotEmpty() && protocolReadyCount == chapters.size)
                    ReadinessRow("Editorial / Muhasabah Reviews Logged (${reviews.size})", reviews.isNotEmpty())
                }
            }
        }

        // Publication Export Engine (Module K)
        item {
            EditorialCard(
                title = "Publication & Export Engine",
                subtitle = "Assembles Title Page, Copyright, TOC, Preface, Chapters, Glossary & Bibliography with integrity validation"
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { viewModel.exportManuscript(ExportFormat.PDF) { shareFile(it) } },
                            modifier = Modifier.weight(1f).testTag("export_pdf_button")
                        ) {
                            Icon(Icons.Outlined.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Export PDF")
                        }
                        Button(
                            onClick = { viewModel.exportManuscript(ExportFormat.DOCX, markDriveExported = true) { shareFile(it) } },
                            modifier = Modifier.weight(1f).testTag("export_docx_button")
                        ) {
                            Icon(Icons.Outlined.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("DOCX / Docs")
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { viewModel.exportManuscript(ExportFormat.EPUB) { shareFile(it) } },
                            modifier = Modifier.weight(1f).testTag("export_epub_button")
                        ) {
                            Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("EPUB 3 eBook")
                        }
                        OutlinedButton(
                            onClick = { viewModel.exportManuscript(ExportFormat.MARKDOWN) { shareFile(it) } },
                            modifier = Modifier.weight(1f).testTag("export_markdown_button")
                        ) {
                            Text("Markdown (.md)")
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            viewModel.exportManuscript(ExportFormat.JSON) { res ->
                                pendingSaveFile = res.file
                                saveToDeviceLauncher.launch(res.file.name)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("export_json_backup_button")
                    ) {
                        Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Save Full Project Backup (JSON) to Device or Google Drive")
                    }
                }
            }
        }

        // Last Validated Export Banner
        if (lastExportResult != null) {
            item {
                EditorialCard(
                    title = "Latest Validated Export",
                    subtitle = lastExportResult.file.name,
                    trailing = {
                        StatusPill(
                            text = if (lastExportResult.validated) "VALIDATED" else "CHECK FAILED",
                            containerColor = if (lastExportResult.validated) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.errorContainer,
                            contentColor = if (lastExportResult.validated) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                ) {
                    Text(lastExportResult.validationNotes, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { shareFile(lastExportResult) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Share / Send")
                        }
                        OutlinedButton(
                            onClick = {
                                pendingSaveFile = lastExportResult.file
                                saveToDeviceLauncher.launch(lastExportResult.file.name)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Outlined.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Save to Drive/Files")
                        }
                    }
                }
            }
        }

        // Module J — Google Docs / Drive Export & Document Import
        item {
            EditorialCard(
                title = "Google Drive & Document Interop",
                subtitle = "Zero-permission Storage Access Framework & DOCX compatibility",
                trailing = { StatusPill(text = syncEnum.label) }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    GovernanceNoticeBanner(
                        text = "Your authoritative manuscript state is synced live in Cloud Firestore. Use 'DOCX / Docs' or 'Save to Drive/Files' to save directly into your Android Google Drive provider, or import external TXT/Markdown chapters below."
                    )
                    OutlinedButton(
                        onClick = {
                            importDocumentLauncher.launch(arrayOf("text/plain", "text/markdown", "text/html"))
                        },
                        modifier = Modifier.fillMaxWidth().testTag("import_document_button")
                    ) {
                        Icon(Icons.Outlined.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Import Manuscript Draft (.txt / .md) into Chapter")
                    }
                }
            }
        }

        // Export History
        if (exports.isNotEmpty()) {
            item {
                Text("Export History (${exports.size})", style = MaterialTheme.typography.titleMedium)
            }
            items(exports, key = { it.id }) { job ->
                val dateFmt = remember { SimpleDateFormat("MMM d, HH:mm", Locale.US) }
                val whenStr = job.createdAt?.toDate()?.let { dateFmt.format(it) } ?: "Just now"
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${job.format} — ${job.fileName}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            StatusPill(text = if (job.validated) "Verified" else job.status)
                        }
                        Text(
                            text = "${job.chapterCount} ch. • ${job.wordCount} words • ${job.byteSize / 1024} KB • $whenStr",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (job.validationNotes.isNotBlank()) {
                            Text(job.validationNotes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadinessRow(label: String, complete: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Outlined.CheckCircle,
            contentDescription = if (complete) "Complete" else "Incomplete",
            tint = if (complete) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (complete) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
