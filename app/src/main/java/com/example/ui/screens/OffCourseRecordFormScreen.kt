package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.AcademicTerm
import com.example.data.model.CicStream
import com.example.data.model.CollegeAccount
import com.example.data.model.OffCourseRecord
import com.example.data.model.TermStatus
import com.example.ui.components.TimetableMatrixGrid

private const val DEFAULT_KKHM_COLLEGE_NAME = "KKHM Islamic & Arts College (Affiliated to CIC)"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun OffCourseRecordFormScreen(
    existingRecord: OffCourseRecord?,
    latestStudentRecord: OffCourseRecord?,
    activeAccount: CollegeAccount?,
    assignedGuideAccount: CollegeAccount?,
    selectedYear: String,
    selectedSemester: String,
    userEmail: String,
    terms: List<AcademicTerm>,
    isSaving: Boolean,
    onLookupGuide: suspend (String) -> CollegeAccount?,
    onBack: () -> Unit,
    onSave: (OffCourseRecord) -> Unit
) {
    val activeTerm = terms.firstOrNull { it.status == TermStatus.ACTIVE.name } ?: terms.firstOrNull()
    val defaultYear = existingRecord?.academicYear
        ?: selectedYear.takeIf { it.isNotBlank() && it != "ALL" }
        ?: activeTerm?.academicYear
        ?: "2026 - 2027"
    val defaultSem = existingRecord?.semester
        ?: selectedSemester.takeIf { it.isNotBlank() && it != "ALL" }
        ?: activeTerm?.semester
        ?: "Nov"

    val availableYears = (terms.map { it.academicYear } + listOf(defaultYear, "2025 - 2026", "2026 - 2027", "2027 - 2028"))
        .filter { it.isNotBlank() && it != "ALL" }
        .distinct()
        .sortedDescending()
    val availableSemesters = (terms.map { it.semester } + listOf(defaultSem, "Nov", "May"))
        .filter { it.isNotBlank() && it != "ALL" }
        .distinct()

    val prefillSource = existingRecord ?: latestStudentRecord

    var studentName by remember(existingRecord?.id, activeAccount?.username, latestStudentRecord?.id) {
        mutableStateOf(
            existingRecord?.studentName?.ifBlank { null }
                ?: activeAccount?.displayName?.ifBlank { null }
                ?: latestStudentRecord?.studentName.orEmpty()
        )
    }
    var cicAdNo by remember(existingRecord?.id, activeAccount?.username, latestStudentRecord?.id) {
        mutableStateOf(
            existingRecord?.cicAdNo?.ifBlank { null }
                ?: activeAccount?.cicAdNo?.ifBlank { null }
                ?: latestStudentRecord?.cicAdNo.orEmpty()
        )
    }
    var clgAdNo by remember(existingRecord?.id, latestStudentRecord?.id) {
        mutableStateOf(prefillSource?.clgAdNo.orEmpty())
    }
    var classDepartment by remember(existingRecord?.id, activeAccount?.username, latestStudentRecord?.id) {
        mutableStateOf(
            existingRecord?.classDepartment?.ifBlank { null }
                ?: activeAccount?.classDepartment?.ifBlank { null }
                ?: latestStudentRecord?.classDepartment.orEmpty()
        )
    }
    var academicYear by remember(existingRecord?.id, defaultYear) {
        mutableStateOf(defaultYear)
    }
    var semester by remember(existingRecord?.id, defaultSem) {
        mutableStateOf(defaultSem)
    }
    var selectedStream by remember(existingRecord?.id, activeAccount?.username, latestStudentRecord?.id) {
        mutableStateOf(
            CicStream.fromLabel(
                existingRecord?.stream?.ifBlank { null }
                    ?: activeAccount?.stream?.ifBlank { null }
                    ?: latestStudentRecord?.stream
                    ?: CicStream.WAFY.label
            )
        )
    }
    var collegeAfflName by remember(existingRecord?.id, latestStudentRecord?.id) {
        mutableStateOf(
            prefillSource?.collegeAfflName?.ifBlank { DEFAULT_KKHM_COLLEGE_NAME } ?: DEFAULT_KKHM_COLLEGE_NAME
        )
    }

    var subjectTitle by remember(existingRecord?.id) { mutableStateOf(existingRecord?.subjectTitle.orEmpty()) }
    var exemptedSubject by remember(existingRecord?.id) { mutableStateOf(existingRecord?.exemptedSubject.orEmpty()) }
    var proposalAppNo by remember(existingRecord?.id) { mutableStateOf(existingRecord?.proposalAppNo.orEmpty()) }
    var creditText by remember(existingRecord?.id) { mutableStateOf((existingRecord?.credit ?: 4L).toString()) }

    val initialGuideUser = existingRecord?.guideUsername?.ifBlank { null }
        ?: activeAccount?.assignedGuideUsername?.ifBlank { null }
        ?: latestStudentRecord?.guideUsername.orEmpty()

    var guideUsername by remember(existingRecord?.id, activeAccount?.username, latestStudentRecord?.id) {
        mutableStateOf(initialGuideUser)
    }
    var guideName by remember(existingRecord?.id, assignedGuideAccount?.username, latestStudentRecord?.id) {
        mutableStateOf(
            existingRecord?.guideName?.ifBlank { null }
                ?: assignedGuideAccount?.displayName?.ifBlank { null }
                ?: latestStudentRecord?.guideName.orEmpty()
        )
    }
    var guideType by remember(existingRecord?.id, assignedGuideAccount?.username, latestStudentRecord?.id) {
        mutableStateOf(
            existingRecord?.guideType?.ifBlank { null }
                ?: assignedGuideAccount?.guideType?.ifBlank { null }
                ?: latestStudentRecord?.guideType.orEmpty()
        )
    }
    var guideEmail by remember(existingRecord?.id, latestStudentRecord?.id) {
        mutableStateOf(existingRecord?.guideEmail?.ifBlank { null } ?: latestStudentRecord?.guideEmail.orEmpty())
    }
    var guidePhone by remember(existingRecord?.id, assignedGuideAccount?.username, latestStudentRecord?.id) {
        mutableStateOf(
            existingRecord?.guidePhone?.ifBlank { null }
                ?: assignedGuideAccount?.phone?.ifBlank { null }
                ?: latestStudentRecord?.guidePhone.orEmpty()
        )
    }
    var guideWhatsapp by remember(existingRecord?.id, assignedGuideAccount?.username, latestStudentRecord?.id) {
        mutableStateOf(
            existingRecord?.guideWhatsapp?.ifBlank { null }
                ?: assignedGuideAccount?.phone?.ifBlank { null }
                ?: latestStudentRecord?.guideWhatsapp.orEmpty()
        )
    }
    var principalEmail by remember(existingRecord?.id, latestStudentRecord?.id) {
        mutableStateOf(
            prefillSource?.principalEmail?.ifBlank { "kkhmstorage@gmail.com" } ?: "kkhmstorage@gmail.com"
        )
    }
    var referenceBooks by remember(existingRecord?.id) { mutableStateOf(existingRecord?.referenceBooks.orEmpty()) }
    var selectedSlots by remember(existingRecord?.id, latestStudentRecord?.id) {
        mutableStateOf(
            existingRecord?.timetableSlots?.takeIf { it.isNotEmpty() }?.toSet()
                ?: latestStudentRecord?.timetableSlots?.takeIf { it.isNotEmpty() }?.toSet()
                ?: setOf("Monday:2", "Wednesday:5")
        )
    }

    // Auto-fill Guide Name & Phone when guideUsername is typed/changed on new records
    LaunchedEffect(guideUsername) {
        val clean = guideUsername.trim().lowercase()
        if (clean.length >= 3) {
            val lookedUp = onLookupGuide(clean)
            if (lookedUp != null) {
                if (guideName.isBlank() || existingRecord == null) {
                    guideName = lookedUp.displayName
                }
                if (guidePhone.isBlank() && lookedUp.phone.isNotBlank()) {
                    guidePhone = lookedUp.phone
                }
                if (guideWhatsapp.isBlank() && lookedUp.phone.isNotBlank()) {
                    guideWhatsapp = lookedUp.phone
                }
                if (lookedUp.guideType.isNotBlank()) {
                    guideType = lookedUp.guideType
                }
            }
        }
    }

    val needsDetailsCheck = studentName.isBlank() || cicAdNo.isBlank() || classDepartment.isBlank() || guideName.isBlank()
    var showPrefilledDetails by remember(existingRecord?.id) { mutableStateOf(needsDetailsCheck) }
    var yearDropdownExpanded by remember { mutableStateOf(false) }
    var semDropdownExpanded by remember { mutableStateOf(false) }
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
            // 1. Auto-Prefilled Summary Card (Collapsible)
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column {
                                Text(
                                    text = "Pre-filled Student & Guide Details",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = listOfNotNull(
                                        studentName.ifBlank { "Name needed" },
                                        cicAdNo.ifBlank { null },
                                        classDepartment.ifBlank { null },
                                        selectedStream.label
                                    ).joinToString(" • "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Guide: ${guideName.ifBlank { "Tap Edit to assign" }}${if (guideUsername.isNotBlank()) " (@$guideUsername)" else ""}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        TextButton(
                            onClick = { showPrefilledDetails = !showPrefilledDetails },
                            modifier = Modifier.testTag("toggle_prefilled_details_button")
                        ) {
                            Icon(
                                imageVector = if (showPrefilledDetails) Icons.Default.ExpandLess else Icons.Default.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(if (showPrefilledDetails) "Hide" else "Edit")
                        }
                    }

                    AnimatedVisibility(visible = showPrefilledDetails) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            HorizontalDivider()
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

                            HorizontalDivider()
                            OutlinedTextField(
                                value = guideUsername,
                                onValueChange = { guideUsername = it.lowercase().filter { ch -> ch.isLetterOrDigit() || ch == '_' || ch == '-' } },
                                label = { Text("Guide Portal Username (e.g. guide1)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("form_guide_username")
                            )
                            OutlinedTextField(
                                value = guideName,
                                onValueChange = { guideName = it },
                                label = { Text("Name of Guide (Mentor) *") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("form_guide_name")
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
                        }
                    }
                }
            }

            // 2. Subject & Term Selection (Dropdowns!)
            Text(
                text = "Subject & Term Details",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            OutlinedTextField(
                value = subjectTitle,
                onValueChange = { subjectTitle = it },
                label = { Text("Off-Course Subject Title *") },
                placeholder = { Text("e.g., Comparative Islamic Jurisprudence") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("form_subject_title")
            )

            OutlinedTextField(
                value = exemptedSubject,
                onValueChange = { exemptedSubject = it },
                label = { Text("Exempted / Dropped Regular Subject *") },
                placeholder = { Text("Which regular subject was dropped for this Off-Course?") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("form_exempted_subject")
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ExposedDropdownMenuBox(
                    expanded = yearDropdownExpanded,
                    onExpandedChange = { yearDropdownExpanded = !yearDropdownExpanded },
                    modifier = Modifier.weight(1.2f)
                ) {
                    OutlinedTextField(
                        value = academicYear,
                        onValueChange = {},
                        readOnly = true,
                        singleLine = true,
                        label = { Text("Academic Year *") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = yearDropdownExpanded) },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                            .testTag("form_year_dropdown")
                    )
                    ExposedDropdownMenu(
                        expanded = yearDropdownExpanded,
                        onDismissRequest = { yearDropdownExpanded = false }
                    ) {
                        availableYears.forEach { year ->
                            DropdownMenuItem(
                                text = { Text(year) },
                                onClick = {
                                    academicYear = year
                                    yearDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                ExposedDropdownMenuBox(
                    expanded = semDropdownExpanded,
                    onExpandedChange = { semDropdownExpanded = !semDropdownExpanded },
                    modifier = Modifier.weight(0.95f)
                ) {
                    OutlinedTextField(
                        value = semester,
                        onValueChange = {},
                        readOnly = true,
                        singleLine = true,
                        label = { Text("Semester *") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = semDropdownExpanded) },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                            .testTag("form_semester_dropdown")
                    )
                    ExposedDropdownMenu(
                        expanded = semDropdownExpanded,
                        onDismissRequest = { semDropdownExpanded = false }
                    ) {
                        availableSemesters.forEach { sem ->
                            DropdownMenuItem(
                                text = { Text(sem) },
                                onClick = {
                                    semester = sem
                                    semDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = proposalAppNo,
                    onValueChange = { proposalAppNo = it },
                    label = { Text("Proposal App. No (Optional)") },
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
                value = referenceBooks,
                onValueChange = { referenceBooks = it },
                label = { Text("Reference Books & Materials (Optional)") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth()
            )

            TimetableMatrixGrid(
                selectedSlots = selectedSlots,
                editable = true,
                onToggleSlot = { key ->
                    selectedSlots = if (key in selectedSlots) selectedSlots - key else selectedSlots + key
                }
            )

            if (validationError != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = validationError.orEmpty(),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            Button(
                onClick = {
                    val missing = when {
                        studentName.isBlank() -> "Please enter Student Name (tap Edit on Details card)."
                        cicAdNo.isBlank() -> "Please enter CIC Admission Number (tap Edit on Details card)."
                        classDepartment.isBlank() -> "Please enter Class / Department."
                        collegeAfflName.isBlank() -> "Please enter College Affl. & Name."
                        subjectTitle.isBlank() -> "Please enter Off-Course Subject Title."
                        guideName.isBlank() -> "Please enter Name of Guide (tap Edit on Details card)."
                        else -> null
                    }
                    if (missing != null) {
                        validationError = missing
                        if (studentName.isBlank() || cicAdNo.isBlank() || classDepartment.isBlank() || guideName.isBlank()) {
                            showPrefilledDetails = true
                        }
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
                                exemptedSubject = exemptedSubject.trim(),
                                proposalAppNo = proposalAppNo.trim(),
                                credit = (creditText.toLongOrNull() ?: 4L).coerceIn(0L, 20L),
                                guideName = guideName.trim(),
                                guideType = guideType.trim(),
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
                    Text("Save Off-Course Record")
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
