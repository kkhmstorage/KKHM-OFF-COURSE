package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.OffCourseRecord
import com.example.data.model.PeriodLogEntry
import com.example.data.model.STUDY_ACTIVITY_TYPES
import com.example.data.model.WeekAccessDecision
import com.example.data.model.WeekVerificationStatus
import com.example.data.model.WeeklyScheduleHelper
import com.example.data.model.WeeklyStudyReport
import com.example.ui.components.PeriodLogsBreakdown
import com.example.ui.theme.PendingAmber
import com.example.ui.theme.VerifiedGreen
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun WeeklyReportEditDialog(
    record: OffCourseRecord,
    weekNumber: Int,
    existingReport: WeeklyStudyReport?,
    allReports: List<WeeklyStudyReport> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (WeeklyStudyReport) -> Unit
) {
    var activeWeek by remember(weekNumber) { mutableIntStateOf(weekNumber.coerceIn(1, 18)) }
    var weekDropdownExpanded by remember { mutableStateOf(false) }

    val activeExisting = remember(activeWeek, existingReport, allReports) {
        if (activeWeek == weekNumber && existingReport != null) existingReport
        else allReports.firstOrNull { it.weekNumber.toInt() == activeWeek }
    }

    val accessDecision = remember(activeWeek, allReports) {
        WeeklyScheduleHelper.canAccessWeek(activeWeek, allReports)
    }

    val defaultAllocated = (activeExisting?.allocatedPeriods ?: record.periodsPerWeek.coerceAtLeast(1L)).toInt()
    val (suggestedFrom, suggestedTo) = remember(activeWeek, activeExisting, allReports) {
        WeeklyScheduleHelper.suggestWeekDateRange(activeWeek, allReports)
    }

    var dateFrom by remember(activeWeek) { mutableStateOf(activeExisting?.dateFrom?.ifBlank { suggestedFrom } ?: suggestedFrom) }
    var dateTo by remember(activeWeek) { mutableStateOf(activeExisting?.dateTo?.ifBlank { suggestedTo } ?: suggestedTo) }
    var description by remember(activeWeek) { mutableStateOf(activeExisting?.description ?: "") }
    var allocatedText by remember(activeWeek) { mutableStateOf(defaultAllocated.toString()) }
    var periodEntries by remember(activeWeek, record.id) {
        mutableStateOf(WeeklyScheduleHelper.buildInitialPeriodEntries(record, activeExisting))
    }
    var utilizedText by remember(activeWeek) {
        val initialUtil = activeExisting?.utilizedPeriods
            ?: periodEntries.count { it.effectiveness != "MISSED" }.toLong()
        mutableStateOf(initialUtil.toString())
    }
    var focusRating by remember(activeWeek) {
        mutableFloatStateOf((activeExisting?.focusRating ?: 4L).toFloat())
    }
    var utilizationNotes by remember(activeWeek) { mutableStateOf(activeExisting?.utilizationNotes ?: "") }
    var selfConfirmed by remember(activeWeek) { mutableStateOf(activeExisting != null) }
    var errorMsg by remember(activeWeek) { mutableStateOf<String?>(null) }

    LaunchedEffect(activeExisting?.id) {
        if (activeExisting != null) {
            dateFrom = activeExisting.dateFrom.ifBlank { suggestedFrom }
            dateTo = activeExisting.dateTo.ifBlank { suggestedTo }
            description = activeExisting.description
            allocatedText = activeExisting.allocatedPeriods.toString()
            periodEntries = WeeklyScheduleHelper.buildInitialPeriodEntries(record, activeExisting)
            utilizedText = activeExisting.utilizedPeriods.toString()
            focusRating = activeExisting.focusRating.toFloat()
            utilizationNotes = activeExisting.utilizationNotes
        }
    }

    val previousReport = remember(activeWeek, allReports) {
        allReports.filter { it.weekNumber.toInt() < activeWeek }
            .maxByOrNull { it.weekNumber }
    }

    val byWeek = remember(allReports) {
        allReports.associateBy { it.weekNumber.toInt() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Week $activeWeek Study & Periods") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Week Selector Dropdown showing Verified / Pending / Locked status
                ExposedDropdownMenuBox(
                    expanded = weekDropdownExpanded,
                    onExpandedChange = { weekDropdownExpanded = !weekDropdownExpanded }
                ) {
                    val statusHint = when (accessDecision) {
                        WeekAccessDecision.AlreadyVerifiedLocked -> "Verified & Locked"
                        is WeekAccessDecision.LockedAwaitingGuideApproval -> "Locked (Awaiting Guide)"
                        WeekAccessDecision.Allowed -> if (activeWeek in byWeek) "Editable" else "Unlocked"
                    }
                    OutlinedTextField(
                        value = "Week $activeWeek ($statusHint)",
                        onValueChange = {},
                        readOnly = true,
                        singleLine = true,
                        label = { Text("Reporting Week") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = weekDropdownExpanded) },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                            .testTag("week_selector_dropdown")
                    )
                    ExposedDropdownMenu(
                        expanded = weekDropdownExpanded,
                        onDismissRequest = { weekDropdownExpanded = false }
                    ) {
                        (1..18).forEach { wk ->
                            val rep = byWeek[wk]
                            val decision = WeeklyScheduleHelper.canAccessWeek(wk, allReports)
                            val label = when {
                                rep?.verificationStatus == WeekVerificationStatus.VERIFIED.name -> "Week $wk • Guide Verified ✓"
                                rep?.verificationStatus == WeekVerificationStatus.NEEDS_REVISION.name -> "Week $wk • Needs Revision"
                                rep != null -> "Week $wk • Pending Guide Approval"
                                decision is WeekAccessDecision.Allowed -> "Week $wk • Ready to Fill"
                                else -> "Week $wk • Locked (Approve earlier week)"
                            }
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    activeWeek = wk
                                    errorMsg = null
                                    weekDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                // Strict Guide-Approval Gate Banner
                when (accessDecision) {
                    is WeekAccessDecision.LockedAwaitingGuideApproval -> {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().testTag("week_locked_guide_approval_banner")
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Lock,
                                        contentDescription = "Locked awaiting guide approval",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                    Text(
                                        text = if (accessDecision.missingWeekOnly) {
                                            "Complete Week ${accessDecision.blockingWeekNumber} First"
                                        } else {
                                            "Week ${accessDecision.blockingWeekNumber} Awaiting Guide Approval"
                                        },
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                                Text(
                                    text = if (accessDecision.missingWeekOnly) {
                                        "You must complete Week ${accessDecision.blockingWeekNumber} and get your Guide's approval before adding Week $activeWeek."
                                    } else {
                                        "Please approach your Guide (${record.guideName.ifBlank { "Assigned Mentor" }}) to inspect and approve Week ${accessDecision.blockingWeekNumber} before adding Week $activeWeek.\n\nഗൈഡിൻ്റെ അപ്രൂവൽ ലഭിക്കാതെ അടുത്ത ആഴ്ചയിലെ റെക്കോർഡ് ചേർക്കാൻ കഴിയില്ല — അപ്രൂവലിനായി ഗൈഡിനെ സമീപിക്കുക."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                OutlinedButton(
                                    onClick = { activeWeek = accessDecision.blockingWeekNumber },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Switch to Week ${accessDecision.blockingWeekNumber}")
                                }
                            }
                        }
                    }
                    WeekAccessDecision.AlreadyVerifiedLocked -> {
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
                                    text = "Week $activeWeek has already been verified & signed by ${activeExisting?.authorizedSignature?.ifBlank { record.guideName }}. Approved reports are locked.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                    WeekAccessDecision.Allowed -> {
                        if (activeExisting?.verificationStatus == WeekVerificationStatus.NEEDS_REVISION.name) {
                            Surface(
                                color = PendingAmber.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Revision Requested by Guide: ${activeExisting.mentorFeedback.ifBlank { "Please update your period activity notes and resubmit." }}",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }

                        // Previous Week Context Banner
                        if (previousReport != null) {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.History,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            text = "Previous (Week ${previousReport.weekNumber}: ${previousReport.dateFrom} → ${previousReport.dateTo})",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                        Text(
                                            text = previousReport.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }

                        // Pre-filled Date Range
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = dateFrom,
                                onValueChange = { dateFrom = it },
                                label = { Text("Date From") },
                                placeholder = { Text("dd/mm/yyyy") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = dateTo,
                                onValueChange = { dateTo = it },
                                label = { Text("Date To") },
                                placeholder = { Text("dd/mm/yyyy") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Per-Period Activity Logging
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Period-by-Period Study Log",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${periodEntries.count { it.effectiveness != "MISSED" }}/${periodEntries.size} active",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        periodEntries.forEachIndexed { idx, entry ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${entry.day} • Period ${entry.period}",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        if (periodEntries.size > 1 && idx >= record.timetableSlots.size) {
                                            IconButton(
                                                onClick = {
                                                    val updated = periodEntries.toMutableList().apply { removeAt(idx) }
                                                    periodEntries = updated
                                                    utilizedText = updated.count { it.effectiveness != "MISSED" }.toString()
                                                },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Close,
                                                    contentDescription = "Remove period",
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }

                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        listOf(
                                            "EFFECTIVE" to "Effective",
                                            "PARTIAL" to "Partial",
                                            "MISSED" to "Missed"
                                        ).forEach { (code, label) ->
                                            FilterChip(
                                                selected = entry.effectiveness == code,
                                                onClick = {
                                                    val mins = when (code) {
                                                        "EFFECTIVE" -> 45
                                                        "PARTIAL" -> 25
                                                        else -> 0
                                                    }
                                                    val updated = periodEntries.toMutableList()
                                                    updated[idx] = entry.copy(
                                                        effectiveness = code,
                                                        minutes = mins,
                                                        activityType = if (code == "MISSED") "" else entry.activityType.ifBlank { STUDY_ACTIVITY_TYPES.first() }
                                                    )
                                                    periodEntries = updated
                                                    utilizedText = updated.count { it.effectiveness != "MISSED" }.toString()
                                                },
                                                label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                                            )
                                        }
                                    }

                                    if (entry.effectiveness != "MISSED") {
                                        Text(
                                            text = "Activity Type *",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            STUDY_ACTIVITY_TYPES.forEach { actType ->
                                                FilterChip(
                                                    selected = entry.activityType == actType,
                                                    onClick = {
                                                        val updated = periodEntries.toMutableList()
                                                        updated[idx] = entry.copy(activityType = actType)
                                                        periodEntries = updated
                                                    },
                                                    label = { Text(actType, style = MaterialTheme.typography.labelSmall) }
                                                )
                                            }
                                        }

                                        OutlinedTextField(
                                            value = if (entry.minutes > 0) entry.minutes.toString() else "",
                                            onValueChange = { raw ->
                                                val parsed = raw.filter(Char::isDigit).take(3).toIntOrNull() ?: 0
                                                val updated = periodEntries.toMutableList()
                                                updated[idx] = entry.copy(minutes = parsed)
                                                periodEntries = updated
                                            },
                                            label = { Text("Actual Minutes Studied (min 10)") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }

                                    OutlinedTextField(
                                        value = entry.note,
                                        onValueChange = { newNote ->
                                            val updated = periodEntries.toMutableList()
                                            updated[idx] = entry.copy(note = newNote)
                                            periodEntries = updated
                                        },
                                        label = {
                                            Text(
                                                if (entry.effectiveness == "MISSED") "Reason for missing period *"
                                                else "What did you study / complete? (min 12 chars) *"
                                            )
                                        },
                                        placeholder = {
                                            Text(
                                                if (entry.effectiveness == "MISSED") "e.g. Medical leave / College event"
                                                else "e.g. Translated Ch. 2 verses 1-15 & wrote key notes"
                                            )
                                        },
                                        minLines = 2,
                                        maxLines = 3,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("period_note_${idx}")
                                    )
                                }
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                val nextNum = periodEntries.size + 1
                                val updated = periodEntries + PeriodLogEntry(
                                    day = "Extra",
                                    period = nextNum,
                                    effectiveness = "EFFECTIVE",
                                    minutes = 45,
                                    note = "",
                                    activityType = STUDY_ACTIVITY_TYPES.first()
                                )
                                periodEntries = updated
                                utilizedText = updated.count { it.effectiveness != "MISSED" }.toString()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Add Extra Study Period")
                        }

                        OutlinedTextField(
                            value = description,
                            onValueChange = { description = it },
                            label = { Text("Weekly Overview Summary (Optional)") },
                            placeholder = { Text("Auto-filled from your period notes above if left blank") },
                            minLines = 2,
                            maxLines = 4,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("week_description_input")
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = allocatedText,
                                onValueChange = { allocatedText = it.filter(Char::isDigit).take(2) },
                                label = { Text("Allocated Per.") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = utilizedText,
                                onValueChange = { utilizedText = it.filter(Char::isDigit).take(2) },
                                label = { Text("Utilized Per.") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("week_utilized_input")
                            )
                        }

                        Column {
                            Text(
                                text = "Focus & Productivity Rating: ${focusRating.roundToInt()} / 5",
                                style = MaterialTheme.typography.labelLarge
                            )
                            Slider(
                                value = focusRating,
                                onValueChange = { focusRating = it },
                                valueRange = 1f..5f,
                                steps = 3
                            )
                        }

                        OutlinedTextField(
                            value = utilizationNotes,
                            onValueChange = { utilizationNotes = it },
                            label = { Text("Mentor / Study Reflection Note (Optional)") },
                            placeholder = { Text("Any doubts to discuss with your Guide...") },
                            minLines = 1,
                            maxLines = 3,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Authentic Utilization Self-Confirmation
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selfConfirmed = !selfConfirmed }
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Checkbox(
                                    checked = selfConfirmed,
                                    onCheckedChange = { selfConfirmed = it },
                                    modifier = Modifier.testTag("week_self_confirm_checkbox")
                                )
                                Text(
                                    text = "I genuinely utilized these Off-Course periods and submit this log for my Guide's verification.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }

                        if (errorMsg != null) {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = errorMsg.orEmpty(),
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val authenticError = WeeklyScheduleHelper.validateAuthenticPeriodReport(
                        periodEntries = periodEntries,
                        selfConfirmed = selfConfirmed,
                        manualSummary = description
                    )
                    if (authenticError != null) {
                        errorMsg = authenticError
                        return@Button
                    }
                    val composedDesc = WeeklyScheduleHelper.composeWeeklyDescription(description, periodEntries)
                    if (composedDesc.isEmpty()) {
                        errorMsg = "Please describe what was done in your study periods."
                        return@Button
                    }
                    val alloc = (allocatedText.toLongOrNull() ?: defaultAllocated.toLong()).coerceIn(0L, 49L)
                    val util = (utilizedText.toLongOrNull() ?: 0L).coerceIn(0L, 49L)
                    val encodedLogs = periodEntries.map { it.encode() }
                    onSave(
                        (activeExisting ?: WeeklyStudyReport()).copy(
                            recordId = record.id,
                            weekNumber = activeWeek.toLong(),
                            dateFrom = dateFrom.trim(),
                            dateTo = dateTo.trim(),
                            description = composedDesc,
                            allocatedPeriods = alloc,
                            utilizedPeriods = util,
                            focusRating = focusRating.roundToInt().toLong().coerceIn(1L, 5L),
                            utilizationNotes = utilizationNotes.trim(),
                            periodLogs = encodedLogs
                        )
                    )
                },
                enabled = accessDecision == WeekAccessDecision.Allowed,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("save_week_report_button")
            ) {
                Text("Submit Week $activeWeek")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WeeklyVerifyDialog(
    report: WeeklyStudyReport,
    defaultSignatureName: String,
    onDismiss: () -> Unit,
    onVerify: (WeekVerificationStatus, String, String) -> Unit
) {
    var status by remember {
        mutableStateOf(
            if (report.verificationStatus == WeekVerificationStatus.NEEDS_REVISION.name)
                WeekVerificationStatus.NEEDS_REVISION else WeekVerificationStatus.VERIFIED
        )
    }
    var signatureName by remember {
        mutableStateOf(report.authorizedSignature.ifBlank { defaultSignatureName })
    }
    var feedback by remember { mutableStateOf(report.mentorFeedback) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Inspect & Approve Week ${report.weekNumber}") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (report.dateFrom.isNotBlank() || report.dateTo.isNotBlank()) {
                    Text(
                        text = "Period Dates: ${report.dateFrom} → ${report.dateTo}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "Utilization: ${report.utilizedPeriods}/${report.allocatedPeriods} periods (${report.utilizationPercent}%) • Focus ${report.focusRating}/5",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                if (report.description.isNotBlank()) {
                    Text(
                        text = report.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                PeriodLogsBreakdown(periodLogs = report.periodLogs)
                if (report.utilizationNotes.isNotBlank()) {
                    Text(
                        text = "Student Note: ${report.utilizationNotes}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "Decision (Approving unlocks Week ${report.weekNumber + 1} for the student):",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        WeekVerificationStatus.VERIFIED to "Approve & Unlock Next Week",
                        WeekVerificationStatus.NEEDS_REVISION to "Request Revision"
                    ).forEach { (opt, label) ->
                        FilterChip(
                            selected = status == opt,
                            onClick = { status = opt },
                            label = { Text(label) }
                        )
                    }
                }
                OutlinedTextField(
                    value = signatureName,
                    onValueChange = { signatureName = it },
                    label = { Text("Authorized Signature Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = feedback,
                    onValueChange = { feedback = it },
                    label = { Text("Guide Feedback / Remarks") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onVerify(status, signatureName.trim().ifBlank { "Authorized Guide" }, feedback.trim())
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("confirm_verify_week_button")
            ) {
                Text(if (status == WeekVerificationStatus.VERIFIED) "Approve Week" else "Send Revision")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
