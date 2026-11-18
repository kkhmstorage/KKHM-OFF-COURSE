package com.example.data.remote

import android.content.Context
import com.example.R
import com.example.data.model.AcademicTerm
import com.example.data.model.CicStream
import com.example.data.model.DarkModePreference
import com.example.data.model.OffCourseRecord
import com.example.data.model.RecordStatus
import com.example.data.model.SignatureStatus
import com.example.data.model.TermStatus
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.data.model.WeekVerificationStatus
import com.example.data.model.WeeklyStudyReport
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

private const val BOOTSTRAPPED_ADMIN_EMAIL = "kkhmstorage@gmail.com"

class OffCourseRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

    private fun currentEmailNormalized(): String =
        auth.currentUser?.email?.trim()?.lowercase().orEmpty()

    private fun isBootstrappedAdmin(): Boolean =
        auth.currentUser?.isEmailVerified == true &&
            currentEmailNormalized() == BOOTSTRAPPED_ADMIN_EMAIL

    private fun <T> Flow<T>.handleSnapshotErrors(
        operationType: OperationType,
        path: String,
        expectedUid: String
    ): Flow<T> = catch { error ->
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == expectedUid) {
            handleFirestoreError(exceptionToLog, operationType, path)
        }
        throw error
    }

    // --- 1. USER PROFILE ---
    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Refusing cross-user profile observation" }
        val path = "users/$userId"
        emitAll(
            db.collection("users").document(userId)
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    } else null
                }
                .handleSnapshotErrors(OperationType.GET, path, uid)
        )
    }

    suspend fun upsertUserProfile(profile: UserProfile): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not authenticated"))
        val docPath = "users/$uid"
        return try {
            val safeName = profile.displayName.trim().ifEmpty {
                auth.currentUser?.displayName?.trim()?.ifEmpty { "KKHM Scholar" } ?: "KKHM Scholar"
            }.take(100)
            val safeEmail = (profile.email.trim().lowercase().ifEmpty { currentEmailNormalized() }).take(120)
            val parsedRole = UserRole.fromRaw(profile.role)
            val safeRole = if (parsedRole == UserRole.ADMIN) UserRole.PRINCIPAL.name else parsedRole.name
            val safeDark = DarkModePreference.fromRaw(profile.darkModePreference).name
            val safeStream = if (profile.stream.isBlank()) "" else CicStream.fromLabel(profile.stream).label

            val docRef = db.collection("users").document(uid)
            val existing = docRef.get().await()
            if (existing.exists()) {
                val updates = mapOf(
                    "displayName" to safeName,
                    "email" to safeEmail,
                    "role" to safeRole,
                    "cicAdNo" to profile.cicAdNo.trim().take(40),
                    "clgAdNo" to profile.clgAdNo.trim().take(40),
                    "classDepartment" to profile.classDepartment.trim().take(80),
                    "stream" to safeStream,
                    "collegeAfflName" to profile.collegeAfflName.trim().take(160),
                    "phone" to profile.phone.trim().take(30),
                    "whatsapp" to profile.whatsapp.trim().take(30),
                    "darkModePreference" to safeDark,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updates).await()
            } else {
                val payload = mapOf(
                    "userId" to uid,
                    "displayName" to safeName,
                    "email" to safeEmail,
                    "role" to safeRole,
                    "cicAdNo" to profile.cicAdNo.trim().take(40),
                    "clgAdNo" to profile.clgAdNo.trim().take(40),
                    "classDepartment" to profile.classDepartment.trim().take(80),
                    "stream" to safeStream,
                    "collegeAfflName" to profile.collegeAfflName.trim().take(160),
                    "phone" to profile.phone.trim().take(30),
                    "whatsapp" to profile.whatsapp.trim().take(30),
                    "darkModePreference" to safeDark,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateRole(role: UserRole): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not authenticated"))
        val docPath = "users/$uid"
        val persistedRole = if (role == UserRole.ADMIN) UserRole.PRINCIPAL.name else role.name
        return try {
            val docRef = db.collection("users").document(uid)
            val snap = docRef.get().await()
            if (snap.exists()) {
                docRef.update(
                    mapOf(
                        "role" to persistedRole,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                Result.success(Unit)
            } else {
                upsertUserProfile(
                    UserProfile(
                        userId = uid,
                        displayName = auth.currentUser?.displayName ?: "KKHM User",
                        email = currentEmailNormalized(),
                        role = persistedRole
                    )
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateDarkModePreference(preference: DarkModePreference): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.success(Unit)
        val docPath = "users/$uid"
        return try {
            val docRef = db.collection("users").document(uid)
            val snap = docRef.get().await()
            if (snap.exists()) {
                docRef.update(
                    mapOf(
                        "darkModePreference" to preference.name,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    // --- 2. ACADEMIC TERMS (YEARS & SEMESTERS) ---
    fun observeAcademicTerms(): Flow<List<AcademicTerm>> = flow {
        val uid = requireUserId()
        val path = "academic_terms"
        emitAll(
            db.collection(path)
                .whereEqualTo("visibility", "campus")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(AcademicTerm::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedWith(compareByDescending<AcademicTerm> { it.academicYear }.thenBy { it.semester })
                }
                .handleSnapshotErrors(OperationType.LIST, path, uid)
        )
    }

    suspend fun createAcademicTerm(
        academicYear: String,
        semester: String,
        status: TermStatus,
        totalWeeks: Long,
        defaultPeriodsPerWeek: Long,
        notes: String
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not authenticated"))
        val cleanYear = academicYear.trim()
        val cleanSem = semester.trim()
        val safeId = "${cleanYear}_${cleanSem}".replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(64)
        val docPath = "academic_terms/$safeId"
        return try {
            require(cleanYear.length in 4..20) { "Academic year must be 4 to 20 characters (e.g., 2026 - 2027)." }
            require(cleanSem.length in 1..30) { "Semester name must be 1 to 30 characters." }
            val weeks = totalWeeks.coerceIn(1L, 24L)
            val periods = defaultPeriodsPerWeek.coerceIn(1L, 35L)

            val docRef = db.collection("academic_terms").document(safeId)
            val existing = docRef.get().await()
            if (existing.exists()) {
                docRef.update(
                    mapOf(
                        "academicYear" to cleanYear,
                        "semester" to cleanSem,
                        "status" to status.name,
                        "totalWeeks" to weeks,
                        "defaultPeriodsPerWeek" to periods,
                        "notes" to notes.trim().take(300),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "visibility" to "campus",
                        "academicYear" to cleanYear,
                        "semester" to cleanSem,
                        "status" to status.name,
                        "totalWeeks" to weeks,
                        "defaultPeriodsPerWeek" to periods,
                        "notes" to notes.trim().take(300),
                        "createdByUid" to uid,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateAcademicTermStatus(termId: String, status: TermStatus): Result<Unit> {
        val docPath = "academic_terms/$termId"
        return try {
            requireUserId()
            require(termId.isNotBlank()) { "Term ID cannot be blank" }
            db.collection("academic_terms").document(termId).update(
                mapOf(
                    "status" to status.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun seedDefaultAcademicTermsIfNeeded(role: UserRole) {
        if (role == UserRole.STUDENT && !isBootstrappedAdmin()) return
        val uid = auth.currentUser?.uid ?: return
        try {
            val existing = db.collection("academic_terms")
                .whereEqualTo("visibility", "campus")
                .limit(1)
                .get()
                .await()
            if (!existing.isEmpty) return

            val defaults = listOf(
                Triple("2024 - 2025", "Nov", TermStatus.ARCHIVED),
                Triple("2025 - 2026", "May", TermStatus.ARCHIVED),
                Triple("2026 - 2027", "Nov", TermStatus.ACTIVE),
                Triple("2026 - 2027", "May", TermStatus.UPCOMING)
            )
            for ((year, sem, status) in defaults) {
                createAcademicTerm(
                    academicYear = year,
                    semester = sem,
                    status = status,
                    totalWeeks = 18L,
                    defaultPeriodsPerWeek = 4L,
                    notes = if (status == TermStatus.ARCHIVED) "Historical archive term" else "KKHM 18-week CIC Off-Course semester"
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "academic_terms/$uid")
        }
    }

    // --- 3. OFF-COURSE RECORDS ---
    fun observeStudentRecords(studentUid: String, studentUsername: String): Flow<List<OffCourseRecord>> = flow {
        val uid = requireUserId()
        val cleanUser = PasswordHasher.normalizeUsername(studentUsername)
        val path = "off_course_records"
        val flows = buildList {
            if (!isBootstrappedAdmin()) {
                add(
                    db.collection(path)
                        .whereEqualTo("studentUid", studentUid)
                        .snapshots()
                        .map { it.toObjects(OffCourseRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                        .handleSnapshotErrors(OperationType.LIST, path, uid)
                )
            }
            if (cleanUser.isNotBlank()) {
                add(
                    db.collection(path)
                        .whereEqualTo("studentUsername", cleanUser)
                        .snapshots()
                        .map { it.toObjects(OffCourseRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                        .handleSnapshotErrors(OperationType.LIST, path, uid)
                )
            }
        }
        if (flows.isEmpty()) {
            emit(emptyList())
            return@flow
        }
        emitAll(
            combine(flows) { lists ->
                lists.asIterable()
                    .flatten()
                    .distinctBy { it.id }
                    .filter { rec ->
                        cleanUser.isBlank() || rec.studentUsername == cleanUser || rec.studentUsername.isBlank()
                    }
                    .sortedByDescending { it.updatedAt ?: it.createdAt }
            }
        )
    }

    fun observeGuideRecords(guideEmail: String, guideUsername: String): Flow<List<OffCourseRecord>> = flow {
        val uid = requireUserId()
        val normalizedEmail = guideEmail.trim().lowercase()
        val cleanGuideUser = PasswordHasher.normalizeUsername(guideUsername)
        val path = "off_course_records"
        val flows = buildList {
            if (cleanGuideUser.isNotBlank()) {
                add(
                    db.collection(path)
                        .whereEqualTo("guideUsername", cleanGuideUser)
                        .snapshots()
                        .map { it.toObjects(OffCourseRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                        .handleSnapshotErrors(OperationType.LIST, path, uid)
                )
            }
            if (!isBootstrappedAdmin() && normalizedEmail.isNotBlank()) {
                add(
                    db.collection(path)
                        .whereEqualTo("guideEmail", normalizedEmail)
                        .snapshots()
                        .map { it.toObjects(OffCourseRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                        .handleSnapshotErrors(OperationType.LIST, path, uid)
                )
            }
        }
        if (flows.isEmpty()) {
            emit(emptyList())
            return@flow
        }
        emitAll(
            combine(flows) { lists ->
                lists.asIterable()
                    .flatten()
                    .distinctBy { it.id }
                    .sortedByDescending { it.updatedAt ?: it.createdAt }
            }
        )
    }

    fun observePrincipalRecords(userEmail: String): Flow<List<OffCourseRecord>> = flow {
        val uid = requireUserId()
        val normalized = userEmail.trim().lowercase()
        val path = "off_course_records"
        val query = if (isBootstrappedAdmin()) {
            db.collection(path)
        } else {
            if (normalized.isEmpty()) {
                emit(emptyList())
                return@flow
            }
            db.collection(path).whereEqualTo("principalEmail", normalized)
        }
        emitAll(
            query.snapshots()
                .map { snapshot ->
                    snapshot.toObjects(OffCourseRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.updatedAt ?: it.createdAt }
                }
                .handleSnapshotErrors(OperationType.LIST, path, uid)
        )
    }

    fun observeAllAdminRecords(): Flow<List<OffCourseRecord>> = flow {
        val uid = requireUserId()
        val path = "off_course_records"
        val query = if (isBootstrappedAdmin()) {
            db.collection(path)
        } else {
            db.collection(path).whereEqualTo("studentUid", uid)
        }
        emitAll(
            query.snapshots()
                .map { snapshot ->
                    snapshot.toObjects(OffCourseRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.updatedAt ?: it.createdAt }
                }
                .handleSnapshotErrors(OperationType.LIST, path, uid)
        )
    }

    fun observeRecord(recordId: String): Flow<OffCourseRecord?> = flow {
        val uid = requireUserId()
        val path = "off_course_records/$recordId"
        emitAll(
            db.collection("off_course_records").document(recordId)
                .snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(OffCourseRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    } else null
                }
                .handleSnapshotErrors(OperationType.GET, path, uid)
        )
    }

    suspend fun saveOffCourseRecord(record: OffCourseRecord): Result<String> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not authenticated"))
        val isNew = record.id.isBlank()
        val docRef = if (isNew) {
            db.collection("off_course_records").document()
        } else {
            db.collection("off_course_records").document(record.id)
        }
        val docPath = docRef.path
        return try {
            val studentName = record.studentName.trim()
            val cicAdNo = record.cicAdNo.trim()
            val classDept = record.classDepartment.trim()
            val academicYear = record.academicYear.trim()
            val semester = record.semester.trim()
            val college = record.collegeAfflName.trim()
            val subjectTitle = record.subjectTitle.trim()
            val guideName = record.guideName.trim()

            require(studentName.length in 1..100) { "Student name is required (max 100 chars)." }
            require(cicAdNo.length in 1..40) { "CIC Admission Number is required." }
            require(classDept.length in 1..80) { "Class / Department is required." }
            require(academicYear.length in 4..20) { "Academic Year is required." }
            require(semester.length in 1..30) { "Semester is required." }
            require(college.length in 1..160) { "College Affiliation & Name is required." }
            require(subjectTitle.length in 1..160) { "Off-Course Subject Title is required." }
            require(guideName.length in 1..100) { "Name of Guide is required." }

            val cleanSlots = record.timetableSlots.distinct().take(49)
            val periodsPerWeek = cleanSlots.size.toLong().coerceIn(0L, 49L)
            val studentUsername = PasswordHasher.normalizeUsername(record.studentUsername).take(40)
            val guideUsername = PasswordHasher.normalizeUsername(record.guideUsername).take(40)
            val studentEmail = record.studentEmail.trim().lowercase().ifEmpty { currentEmailNormalized() }.take(120)
            val guideEmail = record.guideEmail.trim().lowercase().take(120)
            val principalEmail = record.principalEmail.trim().lowercase().take(120)
            val safeStream = CicStream.fromLabel(record.stream).label

            if (isNew) {
                val payload = mapOf(
                    "studentUid" to uid,
                    "studentUsername" to studentUsername,
                    "studentEmail" to studentEmail,
                    "guideUsername" to guideUsername,
                    "guideEmail" to guideEmail,
                    "principalEmail" to principalEmail,
                    "studentName" to studentName,
                    "cicAdNo" to cicAdNo,
                    "clgAdNo" to record.clgAdNo.trim().take(40),
                    "classDepartment" to classDept,
                    "academicYear" to academicYear,
                    "semester" to semester,
                    "stream" to safeStream,
                    "collegeAfflName" to college,
                    "subjectTitle" to subjectTitle,
                    "proposalAppNo" to record.proposalAppNo.trim().take(60),
                    "credit" to record.credit.coerceIn(0L, 20L),
                    "guideName" to guideName,
                    "guidePhone" to record.guidePhone.trim().take(30),
                    "guideWhatsapp" to record.guideWhatsapp.trim().take(30),
                    "referenceBooks" to record.referenceBooks.trim().take(600),
                    "timetableSlots" to cleanSlots,
                    "periodsPerWeek" to periodsPerWeek,
                    "guideSignatureStatus" to SignatureStatus.PENDING.name,
                    "guideSignatureName" to "",
                    "guideRemarks" to "",
                    "principalSignatureStatus" to SignatureStatus.PENDING.name,
                    "principalSignatureName" to "",
                    "principalRemarks" to "",
                    "recordStatus" to RecordStatus.ACTIVE.name,
                    "completedWeeksCount" to 0L,
                    "verifiedWeeksCount" to 0L,
                    "totalAllocatedPeriods" to 0L,
                    "totalUtilizedPeriods" to 0L,
                    "averageFocusRating" to 0.0,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            } else {
                val safeStatus = if (isBootstrappedAdmin()) {
                    RecordStatus.fromRaw(record.recordStatus).name
                } else if (record.recordStatus == RecordStatus.SUBMITTED.name) {
                    RecordStatus.SUBMITTED.name
                } else {
                    RecordStatus.ACTIVE.name
                }
                val updates = mapOf(
                    "studentUsername" to studentUsername,
                    "studentEmail" to studentEmail,
                    "guideUsername" to guideUsername,
                    "guideEmail" to guideEmail,
                    "principalEmail" to principalEmail,
                    "studentName" to studentName,
                    "cicAdNo" to cicAdNo,
                    "clgAdNo" to record.clgAdNo.trim().take(40),
                    "classDepartment" to classDept,
                    "academicYear" to academicYear,
                    "semester" to semester,
                    "stream" to safeStream,
                    "collegeAfflName" to college,
                    "subjectTitle" to subjectTitle,
                    "proposalAppNo" to record.proposalAppNo.trim().take(60),
                    "credit" to record.credit.coerceIn(0L, 20L),
                    "guideName" to guideName,
                    "guidePhone" to record.guidePhone.trim().take(30),
                    "guideWhatsapp" to record.guideWhatsapp.trim().take(30),
                    "referenceBooks" to record.referenceBooks.trim().take(600),
                    "timetableSlots" to cleanSlots,
                    "periodsPerWeek" to periodsPerWeek,
                    "recordStatus" to safeStatus,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updates).await()
                propagateRoutingToWeeklyReports(
                    recordId = docRef.id,
                    studentUsername = studentUsername,
                    guideUsername = guideUsername,
                    guideEmail = guideEmail,
                    principalEmail = principalEmail
                )
            }
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, if (isNew) OperationType.CREATE else OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    private suspend fun propagateRoutingToWeeklyReports(
        recordId: String,
        studentUsername: String,
        guideUsername: String,
        guideEmail: String,
        principalEmail: String
    ) {
        try {
            val uid = auth.currentUser?.uid ?: return
            val collectionRef = db.collection("off_course_records")
                .document(recordId)
                .collection("weekly_reports")
            val query = when {
                isBootstrappedAdmin() -> collectionRef
                studentUsername.isNotBlank() -> collectionRef.whereEqualTo("studentUsername", studentUsername)
                else -> collectionRef.whereEqualTo("studentUid", uid)
            }
            val reports = query.get().await().documents
            reports.forEach { doc ->
                val updates = mutableMapOf<String, Any>(
                    "guideUsername" to guideUsername,
                    "guideEmail" to guideEmail,
                    "principalEmail" to principalEmail,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (!isBootstrappedAdmin()) {
                    updates["studentUsername"] = studentUsername
                }
                doc.reference.update(updates).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Non-fatal sync if subcollection has no editable docs
        }
    }

    suspend fun adminReassignGuide(
        recordId: String,
        guideUsername: String,
        guideName: String,
        guideEmail: String
    ): Result<Unit> {
        val docPath = "off_course_records/$recordId"
        return try {
            requireUserId()
            require(guideName.trim().isNotEmpty()) { "Guide name is required." }
            val cleanGuideUser = PasswordHasher.normalizeUsername(guideUsername).take(40)
            val cleanGuideEmail = guideEmail.trim().lowercase().take(120)
            val recordRef = db.collection("off_course_records").document(recordId)
            recordRef.update(
                mapOf(
                    "guideUsername" to cleanGuideUser,
                    "guideName" to guideName.trim().take(100),
                    "guideEmail" to cleanGuideEmail,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            val subReports = recordRef.collection("weekly_reports").get().await().documents
            subReports.forEach { doc ->
                doc.reference.update(
                    mapOf(
                        "guideUsername" to cleanGuideUser,
                        "guideEmail" to cleanGuideEmail,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun submitRecordForReview(recordId: String): Result<Unit> {
        val docPath = "off_course_records/$recordId"
        return try {
            requireUserId()
            db.collection("off_course_records").document(recordId).update(
                mapOf(
                    "recordStatus" to RecordStatus.SUBMITTED.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun submitGuideReview(
        recordId: String,
        status: SignatureStatus,
        signatureName: String,
        remarks: String
    ): Result<Unit> {
        val docPath = "off_course_records/$recordId"
        return try {
            requireUserId()
            require(signatureName.trim().isNotEmpty()) { "Signature name is required" }
            db.collection("off_course_records").document(recordId).update(
                mapOf(
                    "guideSignatureStatus" to status.name,
                    "guideSignatureName" to signatureName.trim().take(100),
                    "guideRemarks" to remarks.trim().take(300),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun submitPrincipalApproval(
        recordId: String,
        signatureStatus: SignatureStatus,
        signatureName: String,
        remarks: String,
        recordStatus: RecordStatus
    ): Result<Unit> {
        val docPath = "off_course_records/$recordId"
        return try {
            requireUserId()
            require(signatureName.trim().isNotEmpty()) { "Principal/Dean signature name is required" }
            db.collection("off_course_records").document(recordId).update(
                mapOf(
                    "principalSignatureStatus" to signatureStatus.name,
                    "principalSignatureName" to signatureName.trim().take(100),
                    "principalRemarks" to remarks.trim().take(300),
                    "recordStatus" to recordStatus.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    // --- 4. WEEKLY STUDY REPORTS & PERIOD UTILIZATION ---
    fun observeWeeklyReports(
        recordId: String,
        role: UserRole,
        studentUid: String,
        guideUsername: String = "",
        studentUsername: String = ""
    ): Flow<List<WeeklyStudyReport>> = flow {
        val uid = requireUserId()
        val path = "off_course_records/$recordId/weekly_reports"
        val collectionRef = db.collection("off_course_records").document(recordId).collection("weekly_reports")
        val cleanGuideUser = PasswordHasher.normalizeUsername(guideUsername)
        val cleanStudentUser = PasswordHasher.normalizeUsername(studentUsername)
        val normalizedEmail = currentEmailNormalized()

        val flows: List<Flow<List<WeeklyStudyReport>>> = when {
            isBootstrappedAdmin() -> listOf(
                collectionRef.snapshots()
                    .map { it.toObjects(WeeklyStudyReport::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                    .handleSnapshotErrors(OperationType.LIST, path, uid)
            )
            role == UserRole.PRINCIPAL -> listOf(
                collectionRef.whereEqualTo("principalEmail", normalizedEmail)
                    .snapshots()
                    .map { it.toObjects(WeeklyStudyReport::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                    .handleSnapshotErrors(OperationType.LIST, path, uid)
            )
            role == UserRole.GUIDE -> buildList {
                if (cleanGuideUser.isNotBlank()) {
                    add(
                        collectionRef.whereEqualTo("guideUsername", cleanGuideUser)
                            .snapshots()
                            .map { it.toObjects(WeeklyStudyReport::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                            .handleSnapshotErrors(OperationType.LIST, path, uid)
                    )
                }
                if (normalizedEmail.isNotBlank()) {
                    add(
                        collectionRef.whereEqualTo("guideEmail", normalizedEmail)
                            .snapshots()
                            .map { it.toObjects(WeeklyStudyReport::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                            .handleSnapshotErrors(OperationType.LIST, path, uid)
                    )
                }
            }
            else -> buildList {
                add(
                    collectionRef.whereEqualTo("studentUid", studentUid)
                        .snapshots()
                        .map { it.toObjects(WeeklyStudyReport::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                        .handleSnapshotErrors(OperationType.LIST, path, uid)
                )
                if (cleanStudentUser.isNotBlank()) {
                    add(
                        collectionRef.whereEqualTo("studentUsername", cleanStudentUser)
                            .snapshots()
                            .map { it.toObjects(WeeklyStudyReport::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                            .handleSnapshotErrors(OperationType.LIST, path, uid)
                    )
                }
            }
        }

        if (flows.isEmpty()) {
            emit(emptyList())
            return@flow
        }

        emitAll(
            combine(flows) { lists ->
                lists.asIterable()
                    .flatten()
                    .distinctBy { it.id }
                    .sortedBy { it.weekNumber }
            }
        )
    }

    suspend fun saveWeeklyReport(
        record: OffCourseRecord,
        report: WeeklyStudyReport
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not authenticated"))
        val weekId = "week_${report.weekNumber.toString().padStart(2, '0')}"
        val docRef = db.collection("off_course_records")
            .document(record.id)
            .collection("weekly_reports")
            .document(weekId)
        val docPath = docRef.path

        return try {
            val weekNum = report.weekNumber.coerceIn(1L, 24L)
            val allocated = report.allocatedPeriods.coerceIn(0L, 49L)
            val utilized = report.utilizedPeriods.coerceIn(0L, 49L)
            val focus = report.focusRating.coerceIn(1L, 5L)
            val description = report.description.trim().take(1500)
            require(description.isNotEmpty()) { "Weekly study activity description is required." }

            val studentUsername = PasswordHasher.normalizeUsername(record.studentUsername).take(40)
            val guideUsername = PasswordHasher.normalizeUsername(record.guideUsername).take(40)
            val existing = docRef.get().await()
            if (existing.exists()) {
                docRef.update(
                    mapOf(
                        "studentUsername" to studentUsername,
                        "guideUsername" to guideUsername,
                        "guideEmail" to record.guideEmail.trim().lowercase().take(120),
                        "principalEmail" to record.principalEmail.trim().lowercase().take(120),
                        "weekNumber" to weekNum,
                        "dateFrom" to report.dateFrom.trim().take(20),
                        "dateTo" to report.dateTo.trim().take(20),
                        "description" to description,
                        "allocatedPeriods" to allocated,
                        "utilizedPeriods" to utilized,
                        "focusRating" to focus,
                        "utilizationNotes" to report.utilizationNotes.trim().take(600),
                        "periodLogs" to report.periodLogs.take(35),
                        "verificationStatus" to WeekVerificationStatus.PENDING.name,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "recordId" to record.id,
                        "studentUid" to uid,
                        "studentUsername" to studentUsername,
                        "guideUsername" to guideUsername,
                        "guideEmail" to record.guideEmail.trim().lowercase().take(120),
                        "principalEmail" to record.principalEmail.trim().lowercase().take(120),
                        "weekNumber" to weekNum,
                        "dateFrom" to report.dateFrom.trim().take(20),
                        "dateTo" to report.dateTo.trim().take(20),
                        "description" to description,
                        "allocatedPeriods" to allocated,
                        "utilizedPeriods" to utilized,
                        "focusRating" to focus,
                        "utilizationNotes" to report.utilizationNotes.trim().take(600),
                        "periodLogs" to report.periodLogs.take(35),
                        "verificationStatus" to WeekVerificationStatus.PENDING.name,
                        "authorizedSignature" to "",
                        "mentorFeedback" to "",
                        "updatedAt" to FieldValue.serverTimestamp(),
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            if (record.studentUid == uid && record.recordStatus in listOf(RecordStatus.ACTIVE.name, RecordStatus.SUBMITTED.name)) {
                val allReports = db.collection("off_course_records")
                    .document(record.id)
                    .collection("weekly_reports")
                    .whereEqualTo("studentUid", uid)
                    .get()
                    .await()
                    .toObjects(WeeklyStudyReport::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)

                val completedCount = allReports.size.toLong().coerceIn(0L, 24L)
                val verifiedCount = allReports.count { it.verificationStatus == WeekVerificationStatus.VERIFIED.name }.toLong().coerceIn(0L, 24L)
                val totalAllocated = allReports.sumOf { it.allocatedPeriods }.coerceIn(0L, 500L)
                val totalUtilized = allReports.sumOf { it.utilizedPeriods }.coerceIn(0L, 500L)
                val avgFocus = if (allReports.isNotEmpty()) {
                    allReports.map { it.focusRating.toDouble() }.average().coerceIn(0.0, 5.0)
                } else 0.0

                db.collection("off_course_records").document(record.id).update(
                    mapOf(
                        "completedWeeksCount" to completedCount,
                        "verifiedWeeksCount" to verifiedCount,
                        "totalAllocatedPeriods" to totalAllocated,
                        "totalUtilizedPeriods" to totalUtilized,
                        "averageFocusRating" to avgFocus,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun verifyWeeklyReport(
        record: OffCourseRecord,
        weekId: String,
        status: WeekVerificationStatus,
        authorizedSignature: String,
        mentorFeedback: String,
        role: UserRole
    ): Result<Unit> {
        val docPath = "off_course_records/${record.id}/weekly_reports/$weekId"
        return try {
            requireUserId()
            require(authorizedSignature.trim().isNotEmpty()) { "Authorized signature name is required." }
            val reportRef = db.collection("off_course_records")
                .document(record.id)
                .collection("weekly_reports")
                .document(weekId)

            reportRef.update(
                mapOf(
                    "verificationStatus" to status.name,
                    "authorizedSignature" to authorizedSignature.trim().take(100),
                    "mentorFeedback" to mentorFeedback.trim().take(500),
                    "verifiedAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            val collectionRef = db.collection("off_course_records")
                .document(record.id)
                .collection("weekly_reports")
            val cleanGuideUser = PasswordHasher.normalizeUsername(record.guideUsername)
            val query = when {
                isBootstrappedAdmin() -> collectionRef
                role == UserRole.PRINCIPAL || role == UserRole.ADMIN ->
                    collectionRef.whereEqualTo("principalEmail", currentEmailNormalized())
                cleanGuideUser.isNotBlank() && record.guideEmail.isBlank() ->
                    collectionRef.whereEqualTo("guideUsername", cleanGuideUser)
                record.guideEmail.isNotBlank() && record.guideEmail.equals(currentEmailNormalized(), ignoreCase = true) ->
                    collectionRef.whereEqualTo("guideEmail", currentEmailNormalized())
                cleanGuideUser.isNotBlank() ->
                    collectionRef.whereEqualTo("guideUsername", cleanGuideUser)
                else -> collectionRef.whereEqualTo("guideEmail", currentEmailNormalized())
            }
            val reports = query.get().await()
                .toObjects(WeeklyStudyReport::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            val verifiedCount = reports.count { it.verificationStatus == WeekVerificationStatus.VERIFIED.name }.toLong().coerceIn(0L, 24L)

            if (record.recordStatus != RecordStatus.ARCHIVED.name) {
                db.collection("off_course_records").document(record.id).update(
                    mapOf(
                        "verifiedWeeksCount" to verifiedCount,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteOffCourseRecord(recordId: String): Result<Unit> {
        val docPath = "off_course_records/$recordId"
        return try {
            requireUserId()
            db.collection("off_course_records").document(recordId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }
}
