package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.AcademicTerm
import com.example.data.model.GuideType
import com.example.data.model.OffCourseRecord
import com.example.data.model.RecordStatus
import com.example.data.model.SignatureStatus
import com.example.data.model.UiState
import com.example.data.model.WeekVerificationStatus
import com.example.data.model.WeeklyScheduleHelper
import com.example.data.model.WeeklyStudyReport
import com.example.notifications.PeriodNotificationScheduler
import com.example.ui.components.AcademicTermFilterBar
import com.example.ui.components.PeriodLogsBreakdown
import com.example.ui.components.StatusBadge
import com.example.ui.components.signatureStatusColors
import com.example.ui.components.weekStatusColors
import com.example.ui.theme.PendingAmber
import com.example.ui.theme.VerifiedGreen
import java.util.Calendar

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StudentHomeScreen(
    recordsState: UiState<List<OffCourseRecord>>,
    inlineReportsState: UiState<List<WeeklyStudyReport>>,
    terms: List<AcademicTerm>,
    selectedYear: String,
    selectedSemester: String,
    onSelectYear: (String) -> Unit,
    onSelectSemester: (String) -> Unit,
    onOpenRecord: (String) -> Unit,
    onSaveInlineWeeklyReport: (OffCourseRecord, WeeklyStudyReport) -> Unit,
    onShowAllTerms: () -> Unit = {},
    onCreateNewRecord: () -> Unit,
    onExportPdf: (OffCourseRecord, List<WeeklyStudyReport>) -> Unit,
    modifier: Modifier = Modifier
) {
    var editingWeekForRecord by remember { mutableStateOf<Pair<OffCourseRecord, Int>?>(null) }

    when (recordsState) {
        is UiState.Loading -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is UiState.Error -> {
            Box(modifier = modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = recordsState.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
        is UiState.Success -> {
            val allRecords = recordsState.data
            val filtered = allRecords.filter { rec ->
                (selectedYear == "ALL" || rec.academicYear == selectedYear) &&
                    (selectedSemester == "ALL" || rec.semester == selectedSemester)
            }
            val primaryRecord = filtered.firstOrNull { it.recordStatus != RecordStatus.ARCHIVED.name }
                ?: filtered.firstOrNull()
            val inlineReports = (inlineReportsState as? UiState.Success)?.data.orEmpty()
                .filter { primaryRecord != null && it.recordId == primaryRecord.id }
                .sortedBy { it.weekNumber }
            val lockState = remember(inlineReports) {
                WeeklyScheduleHelper.resolveWeeklyLockState(inlineReports)
            }

            val todayName = PeriodNotificationScheduler.calendarDayToName(
                Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
            )
            val todaySlots = filtered.filter { it.recordStatus != RecordStatus.ARCHIVED.name }
                .flatMap { rec ->
                    rec.timetableSlots.filter { it.startsWith("$todayName:") }
                        .map { "${rec.subjectTitle} (P${it.substringAfter(":")})" }
                }

            LazyColumn(
                modifier = modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Year & Semester Selector
                item {
                    AcademicTermFilterBar(
                        terms = terms,
                        records = allRecords,
                        selectedYear = selectedYear,
                        selectedSemester = selectedSemester,
                        onSelectYear = onSelectYear,
                        onSelectSemester = onSelectSemester
                    )
                }

                // Today's scheduled period alert
                if (todaySlots.isNotEmpty()) {
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.NotificationsActive,
                                    contentDescription = "Today's period alert",
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Off-Course Periods Today ($todayName)",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                    Text(
                                        text = todaySlots.joinToString(" • "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                }
                            }
                        }
                    }
                }

                if (primaryRecord == null) {
                    item {
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoStories,
                                    contentDescription = null,
                                    modifier = Modifier.size(44.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = if (allRecords.isEmpty()) "No Off-Course Enrolled Yet"
                                    else "No Off-Course in $selectedYear ($selectedSemester)",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = if (allRecords.isEmpty()) {
                                        "Your Admin can enroll you directly, or tap below to set up your KKHM Off-Course Record."
                                    } else {
                                        "You have ${allRecords.size} record(s) in other Academic Years or Semesters."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (allRecords.isNotEmpty()) {
                                    FilledTonalButton(
                                        onClick = onShowAllTerms,
                                        modifier = Modifier.testTag("student_show_all_terms_button")
                                    ) { Text("Show All Years & Semesters") }
                                }
                                Button(
                                    onClick = onCreateNewRecord,
                                    modifier = Modifier.testTag("empty_create_record_button")
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Create Off-Course Record")
                                }
                            }
                        }
                    }
                } else {
                    // 1. COMPACT OFF-COURSE DETAILS BOX AT TOP
                    item {
                        val (gBg, gFg) = signatureStatusColors(primaryRecord.guideSignatureStatus)
                        val guideTypeShort = if (primaryRecord.guideType == GuideType.EXTERNAL.name) "External Guide" else "College Teacher"
                        ElevatedCard(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.elevatedCardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                            ),
                            modifier = Modifier.fillMaxWidth().testTag("student_compact_offcourse_box")
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            text = "MY OFF-COURSE DETAILS • ${primaryRecord.academicYear} (${primaryRecord.semester})",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = primaryRecord.subjectTitle,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (primaryRecord.exemptedSubject.isNotBlank()) {
                                            Text(
                                                text = "Exempted Subject: ${primaryRecord.exemptedSubject}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Surface(
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = "${primaryRecord.utilizationPercent}%",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimary
                                            )
                                            Text(
                                                text = "${primaryRecord.verifiedWeeksCount}/${primaryRecord.completedWeeksCount} Wks",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
                                            )
                                        }
                                    }
                                }

                                Text(
                                    text = "Guide: ${primaryRecord.guideName} ($guideTypeShort)${if (primaryRecord.guidePhone.isNotBlank()) " • ${primaryRecord.guidePhone}" else ""}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                // Scheduled Periods per Week pill strip
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    StatusBadge(
                                        text = "${primaryRecord.periodsPerWeek} Periods / Week",
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    primaryRecord.timetableSlots.take(5).forEach { slot ->
                                        val d = slot.substringBefore(":").take(3)
                                        val p = slot.substringAfter(":")
                                        StatusBadge(
                                            text = "$d P$p",
                                            containerColor = MaterialTheme.colorScheme.surface,
                                            contentColor = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    StatusBadge(
                                        text = "Guide: ${SignatureStatus.fromRaw(primaryRecord.guideSignatureStatus).label}",
                                        containerColor = gBg,
                                        contentColor = gFg
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { onOpenRecord(primaryRecord.id) },
                                        modifier = Modifier.weight(1f).testTag("student_open_full_book_button")
                                    ) {
                                        Icon(Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Record Book")
                                    }
                                    OutlinedButton(
                                        onClick = { onExportPdf(primaryRecord, inlineReports) },
                                        modifier = Modifier.testTag("student_export_pdf_button")
                                    ) {
                                        Icon(Icons.Default.PictureAsPdf, contentDescription = "Export PDF", modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("PDF")
                                    }
                                }
                            }
                        }
                    }

                    // 2. WEEKLY REPORT SECTION RIGHT BELOW THE COMPACT BOX (Periods/Week + Strict Guide Lock)
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Default.Schedule,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Weekly Study Reports (${primaryRecord.periodsPerWeek} Per./Wk)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = "${inlineReports.size}/18 Wks",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Guide Approval Lock Card OR Unlocked Next Week CTA
                    if (primaryRecord.recordStatus != RecordStatus.ARCHIVED.name) {
                        item {
                            val blocking = lockState.blockingUnverifiedReport
                            when {
                                blocking != null -> {
                                    Card(
                                        shape = RoundedCornerShape(16.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.errorContainer
                                        ),
                                        modifier = Modifier.fillMaxWidth().testTag("student_home_guide_lock_banner")
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(14.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Lock,
                                                    contentDescription = "Locked",
                                                    tint = MaterialTheme.colorScheme.error
                                                )
                                                Text(
                                                    text = "Week ${blocking.weekNumber} Awaiting Guide Approval",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onErrorContainer
                                                )
                                            }
                                            Text(
                                                text = "Please approach your Guide (${primaryRecord.guideName}) to inspect & approve Week ${blocking.weekNumber} before adding Week ${blocking.weekNumber + 1}.\nഗൈഡിൻ്റെ അപ്രൂവൽ ലഭിക്കാതെ അടുത്ത ആഴ്ചയിലെ റെക്കോർഡ് ചേർക്കാൻ കഴിയില്ല — അപ്രൂവലിനായി ഗൈഡിനെ സമീപിക്കുക.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                OutlinedButton(
                                                    onClick = { editingWeekForRecord = primaryRecord to blocking.weekNumber.toInt() },
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Text("Edit Week ${blocking.weekNumber}")
                                                }
                                                Button(
                                                    onClick = {},
                                                    enabled = false,
                                                    modifier = Modifier.weight(1f).testTag("student_add_week_locked_button")
                                                ) {
                                                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(Modifier.width(4.dp))
                                                    Text("Add Week ${blocking.weekNumber + 1}")
                                                }
                                            }
                                        }
                                    }
                                }
                                lockState.nextUnlockedWeek != null -> {
                                    val nextWk = lockState.nextUnlockedWeek
                                    Button(
                                        onClick = { editingWeekForRecord = primaryRecord to nextWk },
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(52.dp)
                                            .testTag("student_add_unlocked_week_button")
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Add Week $nextWk Period Activities (${primaryRecord.periodsPerWeek} Periods)")
                                    }
                                }
                                lockState.isAllWeeksCompletedAndVerified -> {
                                    Surface(
                                        color = VerifiedGreen.copy(alpha = 0.14f),
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = VerifiedGreen)
                                            Text(
                                                text = "All 18 weeks completed and approved by your Guide!",
                                                style = MaterialTheme.typography.titleSmall,
                                                color = VerifiedGreen
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Inline Weekly Reports List
                    if (inlineReportsState is UiState.Loading) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                    } else if (inlineReports.isEmpty()) {
                        item {
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "No weekly reports logged yet. Tap 'Add Week 1 Period Activities' above to record what you studied in each scheduled Off-Course period.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    } else {
                        items(inlineReports.sortedByDescending { it.weekNumber }, key = { it.id }) { rep ->
                            val (wBg, wFg) = weekStatusColors(rep.verificationStatus)
                            val isVerified = rep.verificationStatus == WeekVerificationStatus.VERIFIED.name
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = "Week ${rep.weekNumber}",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            if (rep.dateFrom.isNotBlank() || rep.dateTo.isNotBlank()) {
                                                Text(
                                                    text = "${rep.dateFrom} → ${rep.dateTo} • ${rep.utilizedPeriods}/${rep.allocatedPeriods} periods (${rep.utilizationPercent}%)",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        StatusBadge(
                                            text = WeekVerificationStatus.fromRaw(rep.verificationStatus).label,
                                            containerColor = wBg,
                                            contentColor = wFg
                                        )
                                    }

                                    PeriodLogsBreakdown(periodLogs = rep.periodLogs)

                                    if (rep.mentorFeedback.isNotBlank()) {
                                        Surface(
                                            color = PendingAmber.copy(alpha = 0.14f),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = "Guide Feedback: ${rep.mentorFeedback}",
                                                style = MaterialTheme.typography.bodySmall,
                                                modifier = Modifier.padding(8.dp)
                                            )
                                        }
                                    }

                                    if (rep.authorizedSignature.isNotBlank()) {
                                        Text(
                                            text = "Approved by ${rep.authorizedSignature}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = VerifiedGreen,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }

                                    if (!isVerified && primaryRecord.recordStatus != RecordStatus.ARCHIVED.name) {
                                        FilledTonalButton(
                                            onClick = { editingWeekForRecord = primaryRecord to rep.weekNumber.toInt() },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                if (rep.verificationStatus == WeekVerificationStatus.NEEDS_REVISION.name) {
                                                    "Revise & Resubmit Week ${rep.weekNumber}"
                                                } else {
                                                    "Edit Week ${rep.weekNumber}"
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Any additional Off-Course records in the same term
                    val otherRecords = filtered.filter { it.id != primaryRecord.id }
                    if (otherRecords.isNotEmpty()) {
                        item {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            Text(
                                text = "Other Off-Course Subjects (${otherRecords.size})",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        items(otherRecords, key = { it.id }) { rec ->
                            ElevatedCard(
                                onClick = { onOpenRecord(rec.id) },
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(rec.subjectTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                        Text(
                                            "${rec.academicYear} (${rec.semester}) • Exempted: ${rec.exemptedSubject.ifBlank { "-" }}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        "${rec.utilizationPercent}%",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                item { Spacer(Modifier.height(72.dp)) }
            }
        }
    }

    // Inline Weekly Report Dialog
    val targetPair = editingWeekForRecord
    if (targetPair != null) {
        val (rec, wk) = targetPair
        val reportsForRec = (inlineReportsState as? UiState.Success)?.data.orEmpty()
            .filter { it.recordId == rec.id }
        val existing = reportsForRec.firstOrNull { it.weekNumber.toInt() == wk }
        WeeklyReportEditDialog(
            record = rec,
            weekNumber = wk,
            existingReport = existing,
            allReports = reportsForRec,
            onDismiss = { editingWeekForRecord = null },
            onSave = { updated ->
                onSaveInlineWeeklyReport(rec, updated)
                editingWeekForRecord = null
            }
        )
    }
}
