package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.remote.BulkCsvParseResult
import com.example.data.remote.BulkImportRow
import com.example.data.remote.BulkImportUiState
import com.example.data.remote.BulkUserCsvHelper
import com.example.ui.components.StatusBadge
import com.example.ui.theme.PendingAmber
import com.example.ui.theme.VerifiedGreen

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BulkUserImportDialog(
    existingUsernames: Set<String>,
    selectedYear: String,
    selectedSemester: String,
    importState: BulkImportUiState,
    onStartImport: (List<BulkImportRow>) -> Unit,
    onResetImportState: () -> Unit,
    onShowMessage: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var parseResult by remember { mutableStateOf<BulkCsvParseResult?>(null) }

    val createTemplateLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            BulkUserCsvHelper.writeTemplateToUri(context, uri)
                .onSuccess { onShowMessage("Template saved: ${BulkUserCsvHelper.TEMPLATE_FILE_NAME}") }
                .onFailure { onShowMessage(it.localizedMessage ?: "Failed to save template") }
        }
    }

    val openCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onResetImportState()
            BulkUserCsvHelper.readAndParseCsvFromUri(context, uri, existingUsernames)
                .onSuccess { result -> parseResult = result }
                .onFailure { onShowMessage(it.localizedMessage ?: "Unable to read CSV file") }
        }
    }

    val fallbackTermLabel = "${if (selectedYear == "ALL") "2026 - 2027" else selectedYear} (${if (selectedSemester == "ALL") "Nov" else selectedSemester})"

    Dialog(
        onDismissRequest = { if (!importState.isImporting) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 6.dp,
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .widthIn(max = 580.dp)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Bulk Students & Off-Course CSV",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Add users, Guides (College/External) & enroll Off-Course subjects at once • Fallback term: $fallbackTermLabel",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = { if (!importState.isImporting) onDismiss() },
                        enabled = !importState.isImporting
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close bulk import")
                    }
                }

                HorizontalDivider()

                // Step 1: Template Download & Share
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Step 1 • Download CSV Template",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Supports accounts + Off-Course columns: off_course_subject, exempted_subject, academic_year, semester, assigned_guide_username, guide_type (COLLEGE_FACULTY / EXTERNAL), institution, timetable_slots (e.g. Monday:2;Wednesday:5)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilledTonalButton(
                                onClick = {
                                    createTemplateLauncher.launch(BulkUserCsvHelper.TEMPLATE_FILE_NAME)
                                },
                                enabled = !importState.isImporting,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("bulk_download_template_button")
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Save Template", maxLines = 1)
                            }
                            OutlinedButton(
                                onClick = {
                                    BulkUserCsvHelper.shareTemplateCsv(context)
                                        .onFailure { onShowMessage(it.localizedMessage ?: "Share failed") }
                                },
                                enabled = !importState.isImporting,
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("bulk_share_template_button")
                            ) {
                                Icon(Icons.Default.Share, contentDescription = "Share CSV template", modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Share", maxLines = 1)
                            }
                        }
                    }
                }

                // Step 2: Upload Filled CSV
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Step 2 • Upload Filled CSV File",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Button(
                            onClick = {
                                openCsvLauncher.launch(
                                    arrayOf(
                                        "text/csv",
                                        "text/comma-separated-values",
                                        "application/csv",
                                        "text/plain",
                                        "*/*"
                                    )
                                )
                            },
                            enabled = !importState.isImporting,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("bulk_upload_csv_button")
                        ) {
                            Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(if (parseResult == null) "Select Filled CSV File" else "Choose Different CSV File")
                        }
                    }
                }

                // Step 3: Preview & Validation Summary
                val currentParse = parseResult
                if (currentParse != null) {
                    if (currentParse.fatalError != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    text = "${currentParse.fileName}: ${currentParse.fatalError}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    } else {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            StatusBadge(
                                text = "File: ${currentParse.fileName}",
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            )
                            StatusBadge(
                                text = "New: ${currentParse.newCount}",
                                containerColor = VerifiedGreen.copy(alpha = 0.16f),
                                contentColor = VerifiedGreen
                            )
                            StatusBadge(
                                text = "Update: ${currentParse.updateCount}",
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            if (currentParse.offCourseEnrollCount > 0) {
                                StatusBadge(
                                    text = "Off-Course: ${currentParse.offCourseEnrollCount}",
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                            if (currentParse.invalidRows.isNotEmpty()) {
                                StatusBadge(
                                    text = "Invalid: ${currentParse.invalidRows.size}",
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 220.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(currentParse.rows, key = { "${it.rowNumber}_${it.username}" }) { row ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (row.isValid) {
                                        MaterialTheme.colorScheme.surfaceContainerHigh
                                    } else {
                                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (row.isValid) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                                            contentDescription = null,
                                            tint = if (row.isValid) VerifiedGreen else MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Row ${row.rowNumber} • @${row.username} (${row.displayName.ifBlank { "-" }})",
                                                style = MaterialTheme.typography.labelLarge
                                            )
                                            if (row.isValid) {
                                                val details = listOfNotNull(
                                                    row.role.title,
                                                    row.guideType.takeIf { it.isNotBlank() },
                                                    row.stream.takeIf { it.isNotBlank() },
                                                    row.assignedGuideUsername.takeIf { it.isNotBlank() }?.let { "Guide: @$it" }
                                                ).joinToString(" • ")
                                                Text(
                                                    text = details,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                if (row.hasOffCourseEnrollment) {
                                                    Text(
                                                        text = "Off-Course: ${row.offCourseSubject}${if (row.exemptedSubject.isNotBlank()) " | Exempted: ${row.exemptedSubject}" else ""}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            } else {
                                                Text(
                                                    text = row.validationError.orEmpty(),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }
                                        StatusBadge(
                                            text = when {
                                                !row.isValid -> "SKIP"
                                                row.isExistingUpdate -> "UPDATE"
                                                else -> "NEW"
                                            },
                                            containerColor = when {
                                                !row.isValid -> MaterialTheme.colorScheme.errorContainer
                                                row.isExistingUpdate -> PendingAmber.copy(alpha = 0.18f)
                                                else -> VerifiedGreen.copy(alpha = 0.18f)
                                            },
                                            contentColor = when {
                                                !row.isValid -> MaterialTheme.colorScheme.onErrorContainer
                                                row.isExistingUpdate -> PendingAmber
                                                else -> VerifiedGreen
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Live Progress & Result Banner
                if (importState.isImporting) {
                    val progress = if (importState.totalCount > 0) {
                        importState.processedCount.toFloat() / importState.totalCount.toFloat()
                    } else 0f
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Processing accounts & Off-Course enrollments: ${importState.processedCount} of ${importState.totalCount}…",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else if (importState.completed) {
                    Surface(
                        color = VerifiedGreen.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Done: ${importState.createdCount} Created • ${importState.updatedCount} Updated • ${importState.enrolledRecordsCount} Enrolled • ${importState.failedRows.size} Failed",
                                style = MaterialTheme.typography.labelLarge,
                                color = VerifiedGreen
                            )
                            importState.failedRows.take(5).forEach { (user, reason) ->
                                Text(
                                    text = "• @$user: $reason",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }

                // Footer Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        enabled = !importState.isImporting,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text(if (importState.completed) "Done" else "Close")
                    }
                    Spacer(Modifier.width(8.dp))
                    val validCount = currentParse?.validRows?.size ?: 0
                    Button(
                        onClick = {
                            if (currentParse != null) {
                                onStartImport(currentParse.validRows)
                            }
                        },
                        enabled = !importState.isImporting && !importState.completed && validCount > 0,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("bulk_confirm_import_button")
                    ) {
                        Text("Import $validCount Rows")
                    }
                }
            }
        }
    }
}
