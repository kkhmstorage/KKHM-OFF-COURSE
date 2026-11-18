package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.AcademicTerm
import com.example.data.model.OffCourseRecord
import com.example.data.model.RecordStatus
import com.example.data.model.SignatureStatus
import com.example.data.model.UiState
import com.example.notifications.PeriodNotificationScheduler
import com.example.ui.components.AcademicTermFilterBar
import com.example.ui.components.StatusBadge
import com.example.ui.components.UtilizationGaugeRing
import com.example.ui.components.signatureStatusColors
import java.util.Calendar

@Composable
fun StudentHomeScreen(
    recordsState: UiState<List<OffCourseRecord>>,
    terms: List<AcademicTerm>,
    selectedYear: String,
    selectedSemester: String,
    onSelectYear: (String) -> Unit,
    onSelectSemester: (String) -> Unit,
    onOpenRecord: (String) -> Unit,
    onQuickLogNextWeek: (String) -> Unit = onOpenRecord,
    onShowAllTerms: () -> Unit = {},
    onCreateNewRecord: () -> Unit,
    onExportPdf: (OffCourseRecord) -> Unit,
    modifier: Modifier = Modifier
) {
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
            val totalAllocated = filtered.sumOf { it.totalAllocatedPeriods }
            val totalUtilized = filtered.sumOf { it.totalUtilizedPeriods }
            val overallPct = if (totalAllocated > 0) {
                ((totalUtilized.toDouble() / totalAllocated.toDouble()) * 100.0).toInt().coerceIn(0, 100)
            } else 0
            val avgFocus = if (filtered.isNotEmpty()) {
                val nonZero = filtered.filter { it.averageFocusRating > 0.0 }
                if (nonZero.isNotEmpty()) nonZero.map { it.averageFocusRating }.average() else 0.0
            } else 0.0

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
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Today's scheduled period alert banner
                if (todaySlots.isNotEmpty()) {
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.NotificationsActive,
                                    contentDescription = "Today's period alert",
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Off-Course Periods Today ($todayName)",
                                        style = MaterialTheme.typography.titleSmall,
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

                // Period Utilization Hero Card
                item {
                    Card(
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                                            MaterialTheme.colorScheme.surfaceContainerLow
                                        )
                                    )
                                )
                                .padding(18.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                UtilizationGaugeRing(
                                    percent = overallPct,
                                    utilizedPeriods = totalUtilized,
                                    allocatedPeriods = totalAllocated,
                                    ringSize = 104.dp
                                )
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "Period Utilization",
                                        style = MaterialTheme.typography.titleLarge
                                    )
                                    Text(
                                        text = "${filtered.size} Active / Filtered Subject(s)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        MetricMiniPill(
                                            label = "Weeks",
                                            value = "${filtered.sumOf { it.completedWeeksCount }}"
                                        )
                                        MetricMiniPill(
                                            label = "Verified",
                                            value = "${filtered.sumOf { it.verifiedWeeksCount }}"
                                        )
                                        MetricMiniPill(
                                            label = "Focus",
                                            value = if (avgFocus > 0) String.format("%.1f/5", avgFocus) else "-"
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Year & Semester Filter Bar
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

                if (filtered.isEmpty()) {
                    item {
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
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
                                    text = if (allRecords.isEmpty()) "No Off-Course Records Yet"
                                    else "No Records in $selectedYear ($selectedSemester)",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = if (allRecords.isEmpty()) {
                                        "Create your KKHM Islamic & Arts College Off-Course Record once — your profile and Guide details are pre-filled automatically."
                                    } else {
                                        "You have ${allRecords.size} record(s) under other Academic Years or Semesters."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (allRecords.isNotEmpty()) {
                                    FilledTonalButton(
                                        onClick = onShowAllTerms,
                                        modifier = Modifier.testTag("student_show_all_terms_button")
                                    ) {
                                        Text("Show All Years & Semesters")
                                    }
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
                    items(filtered, key = { it.id }) { record ->
                        StudentRecordCard(
                            record = record,
                            onOpen = { onOpenRecord(record.id) },
                            onQuickLogNextWeek = { onQuickLogNextWeek(record.id) },
                            onExportPdf = { onExportPdf(record) }
                        )
                    }
                }

                item { Spacer(Modifier.height(72.dp)) }
            }
        }
    }
}

@Composable
private fun MetricMiniPill(label: String, value: String) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun StudentRecordCard(
    record: OffCourseRecord,
    onOpen: () -> Unit,
    onQuickLogNextWeek: () -> Unit,
    onExportPdf: () -> Unit
) {
    val (guideBg, guideFg) = signatureStatusColors(record.guideSignatureStatus)
    val (prinBg, prinFg) = signatureStatusColors(record.principalSignatureStatus)
    val nextWeekHint = (record.completedWeeksCount + 1).coerceIn(1, 18)

    ElevatedCard(
        onClick = onOpen,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("record_card_${record.id}")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = record.subjectTitle,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${record.academicYear} • ${record.semester} • ${record.stream}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = "${record.utilizationPercent}%",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${record.periodsPerWeek} Periods/wk • ${record.completedWeeksCount}/18 Weeks • Guide: ${record.guideName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusBadge(
                    text = "Guide: ${SignatureStatus.fromRaw(record.guideSignatureStatus).label}",
                    containerColor = guideBg,
                    contentColor = guideFg
                )
                StatusBadge(
                    text = "Principal: ${SignatureStatus.fromRaw(record.principalSignatureStatus).label}",
                    containerColor = prinBg,
                    contentColor = prinFg
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onQuickLogNextWeek,
                    modifier = Modifier.weight(1f).testTag("quick_log_week_${record.id}")
                ) {
                    Text("Log Week $nextWeekHint")
                }
                FilledTonalButton(
                    onClick = onOpen,
                    modifier = Modifier.weight(0.9f)
                ) {
                    Text("Details")
                }
                OutlinedButton(
                    onClick = onExportPdf
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = "Export PDF", modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
