package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.AcademicTerm
import com.example.data.model.CicStream
import com.example.data.model.CollegeAccount
import com.example.data.model.OffCourseRecord
import com.example.data.model.TermStatus

private const val DEFAULT_KKHM_COLLEGE_NAME = "KKHM Islamic & Arts College (Affiliated to CIC)"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun OffCourseRecordFormScreen(
    existingRecord: OffCourseRecord?,
    activeAccount: CollegeAccount?,
    userEmail: String,
    terms: List<AcademicTerm>,
    isSaving: Boolean,
    onBack: () -> Unit,
    onSave: (OffCourseRecord) -> Unit
) {
    val activeTerm = terms.firstOrNull { it.status == TermStatus.ACTIVE.name } ?: terms.firstOrNull()
    val availableYears = (terms.map { it.academicYear } + listOf("2025 - 2026", "2026 - 2027", "2027 - 2028"))
        .filter { it.isNotBlank() }
        .distinct()
    val availableSemesters = (terms.map { it.semester } + listOf("Nov", "May"))
        .filter { it.isNotBlank() }
        .distinct()

    var studentName by remember(existingRecord, activeAccount) {
        mutableStateOf(existingRecord?.studentName ?: activeAccount?.displayName.orEmpty())
    }
    var cicAdNo by remember(existingRecord, activeAccount) {
        mutableStateOf(existingRecord?.cicAdNo ?: activeAccount?.cicAdNo.orEmpty())
    }
    var clgAdNo by remember(existingRecord) {
        mutableStateOf(existingRecord?.clgAdNo.orEmpty())
    }
    var classDepartment by remember(existingRecord, activeAccount) {
        mutableStateOf(existingRecord?.classDepartment ?: activeAccount?.classDepartment.orEmpty())
    }
    var academicYear by remember(existingRecord, activeTerm) {
        mutableStateOf(existingRecord?.academicYear ?: activeTerm?.academicYear ?: "2026 - 2027")
    }
    var semester by remember(existingRecord, activeTerm) {
        mutableStateOf(existingRecord?.semester ?: activeTerm?.semester ?: "Nov")
    }
    var selectedStream by remember(existingRecord, activeAccount) {
        mutableStateOf(
            CicStream.fromLabel(existingRecord?.stream ?: activeAccount?.stream ?: CicStream.WAFY.label)
        )
    }
    var collegeAfflName by remember(existingRecord) {
        mutableStateOf(existingRecord?.collegeAfflName?.ifBlank { DEFAULT_KKHM_COLLEGE_NAME } ?: DEFAULT_KKHM_COLLEGE_NAME)
    }

    var subjectTitle by remember(existingRecord) { mutableStateOf(existingRecord?.subjectTitle.orEmpty()) }
    var proposalAppNo by remember(existingRecord) { mutableStateOf(existingRecord?.proposalAppNo.orEmpty()) }
    var creditText by remember(existingRecord) { mutableStateOf((existingRecord?.credit ?: 4L).toString()) }
    var guideUsername by remember(existingRecord, activeAccount) {
        mutableStateOf(existingRecord?.guideUsername ?: activeAccount?.assignedGuideUsername.orEmpty())
    }
    var guideName by remember(existingRecord) { mutableStateOf(existingRecord?.guideName.orEmpty()) }
    var guideEmail by remember(existingRecord) { mutableStateOf(existingRecord?.guideEmail.orEmpty()) }
    var guidePhone by remember(existingRecord) { mutableStateOf(existingRecord?.guidePhone.orEmpty()) }
    var guideWhatsapp by remember(existingRecord) { mutableStateOf(existingRecord?.guideWhatsapp.orEmpty()) }
    var principalEmail by remember(existingRecord) {
        mutableStateOf(existingRecord?.principalEmail ?: "kkhmstorage@gmail.com")
    }
    var referenceBooks by remember(existingRecord) { mutableStateOf(existingRecord?.referenceBooks.orEmpty()) }
    var selectedSlots by remember(existingRecord) {
        mutableStateOf(existingRecord?.timetableSlots?.toSet() ?: setOf("Monday:2", "Wednesday:5"))
    }
    var validationError by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (existingRecord == null) "New Off-Course Record" else "Edit Off-Course Record")
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Page 1 — Student & Enrollment Details",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            OutlinedTextField(
                value = studentName,
                onValueChange = { studentName = it },
                label = { Text("Name of Student *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("form_student_name")
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = cicAdNo,
                    onValueChange = { cicAdNo = it },
                    label = { Text("CIC Ad No *") },
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("form_cic_ad_no")
                )
                OutlinedTextField(
                    value = clgAdNo,
                    onValueChange = { clgAdNo = it },
                    label = { Text("Clg Ad No") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            OutlinedTextField(
                value = classDepartment,
                onValueChange = { classDepartment = it },
                label = { Text("Class / Department *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("form_class_dept")
            )

            Text("Academic Year", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                availableYears.forEach { year ->
                    FilterChip(
                        selected = academicYear == year,
                        onClick = { academicYear = year },
                        label = { Text(year) }
                    )
                }
            }

            Text("Semester", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                availableSemesters.forEach { sem ->
                    FilterChip(
                        selected = semester == sem,
                        onClick = { semester = sem },
                        label = { Text(sem) }
                    )
                }
            }

            Text("Programme Stream", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CicStream.entries.forEach { stream ->
                    FilterChip(
                        selected = selectedStream == stream,
                        onClick = { selectedStream = stream },
                        label = { Text(stream.label) }
                    )
                }
            }

            OutlinedTextField(
                value = collegeAfflName,
                onValueChange = { collegeAfflName = it },
                label = { Text("College Affl. & Name *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("form_college_name")
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            Text(
                text = "Page 2 — Subject Details & Mentor Routing",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            OutlinedTextField(
                value = subjectTitle,
                onValueChange = { subjectTitle = it },
                label = { Text("Off-Course Subject Title *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("form_subject_title")
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = proposalAppNo,
                    onValueChange = { proposalAppNo = it },
                    label = { Text("Proposal App. No") },
                    singleLine = true,
                    modifier = Modifier.weight(1.4f)
                )
                OutlinedTextField(
                    value = creditText,
                    onValueChange = { creditText = it.filter(Char::isDigit).take(2) },
                    label = { Text("Credit") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(0.8f)
                )
            }
            OutlinedTextField(
                value = guideName,
                onValueChange = { guideName = it },
                label = { Text("Name of Guide (Mentor) *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("form_guide_name")
            )
            OutlinedTextField(
                value = guideUsername,
                onValueChange = { guideUsername = it.lowercase().filter { ch -> ch.isLetterOrDigit() || ch == '_' || ch == '-' } },
                label = { Text("Guide Portal Username (e.g. guide1)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("form_guide_username")
            )
            OutlinedTextField(
                value = guideEmail,
                onValueChange = { guideEmail = it },
                label = { Text("Guide Google Email (Optional)") },
                placeholder = { Text("teacher@college.edu") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("form_guide_email")
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = guidePhone,
                    onValueChange = { guidePhone = it },
                    label = { Text("Guide Phone") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = guideWhatsapp,
                    onValueChange = { guideWhatsapp = it },
                    label = { Text("WhatsApp Number") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            OutlinedTextField(
                value = principalEmail,
                onValueChange = { principalEmail = it },
                label = { Text("Principal / Dean Google Email") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = referenceBooks,
                onValueChange = { referenceBooks = it },
                label = { Text("Reference Books & Materials") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth()
            )

            com.example.ui.components.TimetableMatrixGrid(
                selectedSlots = selectedSlots,
                editable = true,
                onToggleSlot = { key ->
                    selectedSlots = if (key in selectedSlots) selectedSlots - key else selectedSlots + key
                }
            )

            if (validationError != null) {
                Text(
                    text = validationError.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Button(
                onClick = {
                    val missing = when {
                        studentName.isBlank() -> "Please enter Student Name."
                        cicAdNo.isBlank() -> "Please enter CIC Admission Number."
                        classDepartment.isBlank() -> "Please enter Class / Department."
                        collegeAfflName.isBlank() -> "Please enter College Affl. & Name."
                        subjectTitle.isBlank() -> "Please enter Subject Title."
                        guideName.isBlank() -> "Please enter Name of Guide."
                        else -> null
                    }
                    if (missing != null) {
                        validationError = missing
                    } else {
                        validationError = null
                        val base = existingRecord ?: OffCourseRecord()
                        onSave(
                            base.copy(
                                studentUsername = base.studentUsername.ifBlank { activeAccount?.username.orEmpty() },
                                studentEmail = userEmail.trim().lowercase(),
                                guideUsername = guideUsername.trim().lowercase(),
                                guideEmail = guideEmail.trim().lowercase(),
                                principalEmail = principalEmail.trim().lowercase(),
                                studentName = studentName.trim(),
                                cicAdNo = cicAdNo.trim(),
                                clgAdNo = clgAdNo.trim(),
                                classDepartment = classDepartment.trim(),
                                academicYear = academicYear.trim(),
                                semester = semester.trim(),
                                stream = selectedStream.label,
                                collegeAfflName = collegeAfflName.trim(),
                                subjectTitle = subjectTitle.trim(),
                                proposalAppNo = proposalAppNo.trim(),
                                credit = (creditText.toLongOrNull() ?: 4L).coerceIn(0L, 20L),
                                guideName = guideName.trim(),
                                guidePhone = guidePhone.trim(),
                                guideWhatsapp = guideWhatsapp.trim(),
                                referenceBooks = referenceBooks.trim(),
                                timetableSlots = selectedSlots.toList().sorted(),
                                periodsPerWeek = selectedSlots.size.toLong()
                            )
                        )
                    }
                },
                enabled = !isSaving,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("save_record_button")
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Save Digitized Off-Course Record")
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
