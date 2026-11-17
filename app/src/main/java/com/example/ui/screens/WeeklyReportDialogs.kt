package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.OffCourseRecord
import com.example.data.model.PeriodLogEntry
import com.example.data.model.WeekVerificationStatus
import com.example.data.model.WeeklyStudyReport
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WeeklyReportEditDialog(
    record: OffCourseRecord,
    weekNumber: Int,
    existingReport: WeeklyStudyReport?,
    onDismiss: () -> Unit,
    onSave: (WeeklyStudyReport) -> Unit
) {
    val defaultAllocated = (existingReport?.allocatedPeriods ?: record.periodsPerWeek.coerceAtLeast(1L)).toInt()
    var dateFrom by remember { mutableStateOf(existingReport?.dateFrom ?: "") }
    var dateTo by remember { mutableStateOf(existingReport?.dateTo ?: "") }
    var description by remember { mutableStateOf(existingReport?.description ?: "") }
    var allocatedText by remember { mutableStateOf(defaultAllocated.toString()) }
    var utilizedText by remember {
        mutableStateOf((existingReport?.utilizedPeriods ?: defaultAllocated.toLong()).toString())
    }
    var focusRating by remember { mutableFloatStateOf((existingReport?.focusRating ?: 4L).toFloat()) }
    var utilizationNotes by remember { mutableStateOf(existingReport?.utilizationNotes ?: "") }

    // Interactive per-period effectiveness chips from timetableSlots
    val initialSlotStates = remember(existingReport, record) {
        val decoded = existingReport?.periodLogs?.mapNotNull { PeriodLogEntry.decode(it) }.orEmpty()
        record.timetableSlots.associateWith { slot ->
            val day = slot.substringBefore(":")
            val period = slot.substringAfter(":").toIntOrNull() ?: 1
            decoded.firstOrNull { it.day == day && it.period == period }?.effectiveness ?: "EFFECTIVE"
        }.toMutableMap()
    }
    var slotStates by remember { mutableStateOf(initialSlotStates.toMap()) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Week $weekNumber Study & Periods") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = dateFrom,
                        onValueChange = { dateFrom = it },
                        label = { Text("Date From") },
                        placeholder = { Text("dd/mm/yy") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = dateTo,
                        onValueChange = { dateTo = it },
                        label = { Text("Date To") },
                        placeholder = { Text("dd/mm/yy") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Study Activity Description *") },
                    placeholder = { Text("Chapters studied, translations, notes prepared...") },
                    minLines = 3,
                    maxLines = 5,
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

                if (record.timetableSlots.isNotEmpty()) {
                    Text(
                        text = "Tap scheduled periods to cycle status (Effective / Partial / Missed):",
                        style = MaterialTheme.typography.labelMedium
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        record.timetableSlots.forEach { slot ->
                            val state = slotStates[slot] ?: "EFFECTIVE"
                            FilterChip(
                                selected = state == "EFFECTIVE",
                                onClick = {
                                    val next = when (state) {
                                        "EFFECTIVE" -> "PARTIAL"
                                        "PARTIAL" -> "MISSED"
                                        else -> "EFFECTIVE"
                                    }
                                    val updated = slotStates.toMutableMap().apply { put(slot, next) }
                                    slotStates = updated
                                    val effectiveCount = updated.values.count { it != "MISSED" }
                                    utilizedText = effectiveCount.toString()
                                },
                                label = {
                                    Text("${slot.replace(":", " P")} • $state", style = MaterialTheme.typography.labelSmall)
                                }
                            )
                        }
                    }
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
                    label = { Text("Period Utilization Notes (Optional)") },
                    placeholder = { Text("How effectively were the periods used?") },
                    minLines = 2,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMsg != null) {
                    Text(
                        text = errorMsg.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (description.trim().isEmpty()) {
                        errorMsg = "Please enter the weekly study description."
                        return@Button
                    }
                    val alloc = (allocatedText.toLongOrNull() ?: defaultAllocated.toLong()).coerceIn(0L, 49L)
                    val util = (utilizedText.toLongOrNull() ?: 0L).coerceIn(0L, 49L)
                    val encodedLogs = slotStates.entries.map { (slot, eff) ->
                        val day = slot.substringBefore(":")
                        val period = slot.substringAfter(":").toIntOrNull() ?: 1
                        PeriodLogEntry(day, period, eff, if (eff == "EFFECTIVE") 45 else if (eff == "PARTIAL") 25 else 0, "").encode()
                    }
                    onSave(
                        (existingReport ?: WeeklyStudyReport()).copy(
                            recordId = record.id,
                            weekNumber = weekNumber.toLong(),
                            dateFrom = dateFrom.trim(),
                            dateTo = dateTo.trim(),
                            description = description.trim(),
                            allocatedPeriods = alloc,
                            utilizedPeriods = util,
                            focusRating = focusRating.roundToInt().toLong().coerceIn(1L, 5L),
                            utilizationNotes = utilizationNotes.trim(),
                            periodLogs = encodedLogs
                        )
                    )
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("save_week_report_button")
            ) {
                Text("Save Week $weekNumber")
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
        title = { Text("Authorize Week ${report.weekNumber}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Utilization: ${report.utilizedPeriods}/${report.allocatedPeriods} periods (${report.utilizationPercent}%)",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = report.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(WeekVerificationStatus.VERIFIED, WeekVerificationStatus.NEEDS_REVISION).forEach { opt ->
                        FilterChip(
                            selected = status == opt,
                            onClick = { status = opt },
                            label = { Text(opt.label) }
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
                    label = { Text("Mentor Feedback / Remarks") },
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
                Text("Confirm Stamp")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
