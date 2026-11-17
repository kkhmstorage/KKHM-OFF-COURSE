package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.model.AcademicTerm
import com.example.data.model.CIC_DAYS
import com.example.data.model.CIC_PERIODS
import com.example.data.model.CicStream
import com.example.data.model.DarkModePreference
import com.example.data.model.OffCourseRecord
import com.example.data.model.SignatureStatus
import com.example.data.model.WeekVerificationStatus
import com.example.data.model.WeeklyStudyReport
import com.example.ui.theme.PendingAmber
import com.example.ui.theme.VerifiedGreen

@Composable
fun ScholarlyEmblem(
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    tint: Color = MaterialTheme.colorScheme.onPrimary
) {
    val accent = MaterialTheme.colorScheme.tertiary
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height

        // Crescent arc inspired by CIC emblem
        drawArc(
            color = accent,
            startAngle = 110f,
            sweepAngle = 215f,
            useCenter = false,
            topLeft = Offset(w * 0.14f, h * 0.08f),
            size = Size(w * 0.62f, h * 0.62f),
            style = Stroke(width = w * 0.085f, cap = StrokeCap.Round)
        )

        // Pen nib silhouette
        val nib = Path().apply {
            moveTo(w * 0.52f, h * 0.12f)
            lineTo(w * 0.72f, h * 0.44f)
            lineTo(w * 0.63f, h * 0.68f)
            lineTo(w * 0.41f, h * 0.68f)
            lineTo(w * 0.32f, h * 0.44f)
            close()
        }
        drawPath(nib, color = tint)

        // Open book base
        val leftPage = Path().apply {
            moveTo(w * 0.14f, h * 0.72f)
            lineTo(w * 0.50f, h * 0.79f)
            lineTo(w * 0.50f, h * 0.92f)
            lineTo(w * 0.14f, h * 0.85f)
            close()
        }
        val rightPage = Path().apply {
            moveTo(w * 0.86f, h * 0.72f)
            lineTo(w * 0.52f, h * 0.79f)
            lineTo(w * 0.52f, h * 0.92f)
            lineTo(w * 0.86f, h * 0.85f)
            close()
        }
        drawPath(leftPage, color = tint.copy(alpha = 0.92f))
        drawPath(rightPage, color = tint.copy(alpha = 0.75f))
    }
}

@Composable
fun ThemeToggleIconButton(
    preference: DarkModePreference,
    onCycle: (DarkModePreference) -> Unit,
    modifier: Modifier = Modifier
) {
    val (icon, label, next) = when (preference) {
        DarkModePreference.SYSTEM -> Triple(Icons.Default.BrightnessAuto, "Theme: System", DarkModePreference.LIGHT)
        DarkModePreference.LIGHT -> Triple(Icons.Default.LightMode, "Theme: Light", DarkModePreference.DARK)
        DarkModePreference.DARK -> Triple(Icons.Default.DarkMode, "Theme: Dark", DarkModePreference.SYSTEM)
    }
    IconButton(
        onClick = { onCycle(next) },
        modifier = modifier
            .minimumInteractiveComponentSize()
            .testTag("theme_toggle_button")
    ) {
        Icon(imageVector = icon, contentDescription = label)
    }
}

@Composable
fun UtilizationGaugeRing(
    percent: Int,
    utilizedPeriods: Long,
    allocatedPeriods: Long,
    modifier: Modifier = Modifier,
    ringSize: Dp = 104.dp
) {
    val clamped = percent.coerceIn(0, 100)
    val animatedSweep by animateFloatAsState(
        targetValue = (clamped / 100f) * 260f,
        animationSpec = tween(durationMillis = 650),
        label = "gauge_sweep"
    )
    val activeColor = when {
        clamped >= 80 -> MaterialTheme.colorScheme.primary
        clamped >= 55 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Box(modifier = modifier.size(ringSize), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = size.minDimension * 0.11f
            val inset = strokeWidth / 2f
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
            drawArc(
                color = trackColor,
                startAngle = 140f,
                sweepAngle = 260f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
            drawArc(
                color = activeColor,
                startAngle = 140f,
                sweepAngle = animatedSweep,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$clamped%",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "$utilizedPeriods/$allocatedPeriods Per.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun WeeklyUtilizationBarChart(
    reports: List<WeeklyStudyReport>,
    modifier: Modifier = Modifier
) {
    val sorted = reports.sortedBy { it.weekNumber }.take(18)
    if (sorted.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(120.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Log your first weekly study report to view period utilization trends.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(16.dp)
            )
        }
        return
    }

    val allocatedColor = MaterialTheme.colorScheme.outlineVariant
    val utilizedColor = MaterialTheme.colorScheme.primary
    val maxPeriods = (sorted.maxOfOrNull { maxOf(it.allocatedPeriods, it.utilizedPeriods) } ?: 5L)
        .coerceAtLeast(1L)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Weekly Period Utilization",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LegendDot(color = allocatedColor, label = "Allocated")
                LegendDot(color = utilizedColor, label = "Utilized")
            }
        }
        Spacer(Modifier.height(10.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(126.dp)
        ) {
            val count = sorted.size.coerceAtLeast(1)
            val groupWidth = size.width / count
            val barWidth = (groupWidth * 0.28f).coerceIn(6.dp.toPx(), 16.dp.toPx())
            val gap = barWidth * 0.35f
            val chartBottom = size.height - 6.dp.toPx()
            val maxBarHeight = chartBottom - 8.dp.toPx()

            sorted.forEachIndexed { idx, rep ->
                val centerX = idx * groupWidth + groupWidth / 2f
                val allocH = (rep.allocatedPeriods.toFloat() / maxPeriods) * maxBarHeight
                val utilH = (rep.utilizedPeriods.toFloat() / maxPeriods) * maxBarHeight

                drawRoundRect(
                    color = allocatedColor,
                    topLeft = Offset(centerX - barWidth - gap / 2f, chartBottom - allocH),
                    size = Size(barWidth, allocH.coerceAtLeast(4f)),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                )
                drawRoundRect(
                    color = utilizedColor,
                    topLeft = Offset(centerX + gap / 2f, chartBottom - utilH),
                    size = Size(barWidth, utilH.coerceAtLeast(4f)),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            sorted.forEach { rep ->
                Text(
                    text = "W${rep.weekNumber}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun StreamBreakdownSection(
    records: List<OffCourseRecord>,
    modifier: Modifier = Modifier
) {
    val total = records.size.coerceAtLeast(1)
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        CicStream.entries.forEach { stream ->
            val count = records.count { it.stream == stream.label }
            val progress = count.toFloat() / total.toFloat()
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stream.label, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "$count records",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(7.dp)
                        .clip(RoundedCornerShape(4.dp))
                )
            }
        }
    }
}

@Composable
fun TimetableMatrixGrid(
    selectedSlots: Set<String>,
    editable: Boolean,
    onToggleSlot: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Weekly Timetable (Periods 1–8)",
                    style = MaterialTheme.typography.titleSmall
                )
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "${selectedSlots.size} / wk",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            Spacer(Modifier.height(10.dp))

            // Column headers
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Day",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(60.dp)
                )
                CIC_PERIODS.forEach { period ->
                    Text(
                        text = "P$period",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(Modifier.height(6.dp))

            CIC_DAYS.forEach { day ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = day.take(3),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.width(60.dp)
                    )
                    CIC_PERIODS.forEach { period ->
                        val key = "$day:$period"
                        val isSelected = key in selectedSlots
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 2.dp)
                                .heightIn(min = 38.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceContainerHigh
                                )
                                .then(
                                    if (editable) {
                                        Modifier.clickable { onToggleSlot(key) }
                                    } else Modifier
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "$day Period $period selected",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                            } else {
                                Text(
                                    text = period.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AcademicTermFilterBar(
    terms: List<AcademicTerm>,
    records: List<OffCourseRecord>,
    selectedYear: String,
    selectedSemester: String,
    onSelectYear: (String) -> Unit,
    onSelectSemester: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val years = (terms.map { it.academicYear } + records.map { it.academicYear })
        .filter { it.isNotBlank() }
        .distinct()
        .sortedDescending()
    val semesters = (terms.map { it.semester } + records.map { it.semester } + listOf("Nov", "May"))
        .filter { it.isNotBlank() }
        .distinct()

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedYear == "ALL",
                onClick = { onSelectYear("ALL") },
                label = { Text("All Years") },
                modifier = Modifier.testTag("filter_year_all")
            )
            years.forEach { year ->
                val isArchived = terms.any { it.academicYear == year && it.status == "ARCHIVED" }
                FilterChip(
                    selected = selectedYear == year,
                    onClick = { onSelectYear(year) },
                    label = { Text(if (isArchived) "$year (Archive)" else year) }
                )
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            FilterChip(
                selected = selectedSemester == "ALL",
                onClick = { onSelectSemester("ALL") },
                label = { Text("All Semesters") },
                modifier = Modifier.testTag("filter_sem_all")
            )
            semesters.forEach { sem ->
                FilterChip(
                    selected = selectedSemester == sem,
                    onClick = { onSelectSemester(sem) },
                    label = { Text(sem) }
                )
            }
        }
    }
}

@Composable
fun StatusBadge(
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = containerColor,
        shape = RoundedCornerShape(999.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun signatureStatusColors(status: String): Pair<Color, Color> = when (SignatureStatus.fromRaw(status)) {
    SignatureStatus.APPROVED -> VerifiedGreen.copy(alpha = 0.16f) to VerifiedGreen
    SignatureStatus.REVISION_REQUIRED -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.error
    SignatureStatus.PENDING -> PendingAmber.copy(alpha = 0.16f) to PendingAmber
}

@Composable
fun weekStatusColors(status: String): Pair<Color, Color> = when (WeekVerificationStatus.fromRaw(status)) {
    WeekVerificationStatus.VERIFIED -> VerifiedGreen.copy(alpha = 0.16f) to VerifiedGreen
    WeekVerificationStatus.NEEDS_REVISION -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.error
    WeekVerificationStatus.PENDING -> PendingAmber.copy(alpha = 0.16f) to PendingAmber
}
