package com.example.data.remote

import android.content.Context
import com.example.R
import com.example.data.model.AccountStatus
import com.example.data.model.CicStream
import com.example.data.model.CollegeAccount
import com.example.data.model.DarkModePreference
import com.example.data.model.UserRole
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
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

private const val KKHM_BOOTSTRAPPED_ADMIN_EMAIL = "kkhmstorage@gmail.com"

data class SeededCredential(
    val username: String,
    val rawPassword: String,
    val role: UserRole,
    val displayName: String
)

class CollegeAccountRepository(
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
            ?: throw IllegalStateException("Google authentication required before accessing KKHM portal.")

    private fun currentEmailNormalized(): String =
        auth.currentUser?.email?.trim()?.lowercase().orEmpty()

    fun isBootstrappedAdminDevice(): Boolean =
        auth.currentUser?.isEmailVerified == true &&
            currentEmailNormalized() == KKHM_BOOTSTRAPPED_ADMIN_EMAIL

    private fun <T> Flow<T>.handleErrors(
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

    fun observeAccount(username: String): Flow<CollegeAccount?> = flow {
        val uid = requireUserId()
        val clean = PasswordHasher.normalizeUsername(username)
        if (!PasswordHasher.isValidUsername(clean)) {
            emit(null)
            return@flow
        }
        val path = "college_accounts/$clean"
        emitAll(
            db.collection("college_accounts").document(clean)
                .snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(CollegeAccount::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    } else null
                }
                .handleErrors(OperationType.GET, path, uid)
        )
    }

    fun observeAllAccounts(): Flow<List<CollegeAccount>> = flow {
        val uid = requireUserId()
        val path = "college_accounts"
        val query = if (isBootstrappedAdminDevice()) {
            db.collection(path)
        } else {
            db.collection(path).whereEqualTo("createdByUid", uid)
        }
        emitAll(
            query.snapshots()
                .map { snapshot ->
                    snapshot.toObjects(CollegeAccount::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedWith(compareBy<CollegeAccount> { it.role }.thenBy { it.username })
                }
                .handleErrors(OperationType.LIST, path, uid)
        )
    }

    suspend fun getAccountByUsername(rawUsername: String): CollegeAccount? {
        val uid = auth.currentUser?.uid ?: return null
        val clean = PasswordHasher.normalizeUsername(rawUsername)
        if (!PasswordHasher.isValidUsername(clean)) return null
        val docPath = "college_accounts/$clean"
        return try {
            val snap = db.collection("college_accounts").document(clean).get().await()
            if (snap.exists()) {
                snap.toObject(CollegeAccount::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            } else null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (auth.currentUser?.uid == uid) {
                handleFirestoreError(e, OperationType.GET, docPath)
            }
            null
        }
    }

    suspend fun authenticate(rawUsername: String, password: String): Result<CollegeAccount> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Google session missing."))
        val username = PasswordHasher.normalizeUsername(rawUsername)
        if (!PasswordHasher.isValidUsername(username)) {
            return Result.failure(IllegalArgumentException("Enter a valid username (3–40 letters, digits, _ or -)."))
        }
        if (password.isBlank()) {
            return Result.failure(IllegalArgumentException("Password is required."))
        }
        val docPath = "college_accounts/$username"
        return try {
            val docRef = db.collection("college_accounts").document(username)
            val snap = docRef.get().await()
            if (!snap.exists()) {
                return Result.failure(IllegalArgumentException("Account '$username' not found. Ask KKHM Admin to create it."))
            }
            val account = snap.toObject(CollegeAccount::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: return Result.failure(IllegalStateException("Unable to read account record."))

            if (account.accountStatus == AccountStatus.SUSPENDED.name) {
                return Result.failure(IllegalStateException("Account '$username' is suspended by KKHM Admin."))
            }
            if (!PasswordHasher.verifyPassword(password, account.passwordSalt, account.passwordHash)) {
                return Result.failure(IllegalArgumentException("Incorrect password for '$username'."))
            }

            val resolved = if (!isBootstrappedAdminDevice() &&
                (account.boundUid.isBlank() || account.boundUid == account.createdByUid) &&
                account.boundUid != uid
            ) {
                docRef.update(
                    mapOf(
                        "boundUid" to uid,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                account.copy(boundUid = uid)
            } else {
                account
            }
            syncDeviceProfileRole(resolved)
            Result.success(resolved)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docPath)
            Result.failure(e)
        }
    }

    suspend fun ensureBootstrappedAdminAccount(defaultPassword: String = "kkhm@admin123"): Result<CollegeAccount> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Google authentication required."))
        val docRef = db.collection("college_accounts").document("admin")
        val docPath = "college_accounts/admin"
        return try {
            val snap = docRef.get().await()
            val account = if (snap.exists()) {
                snap.toObject(CollegeAccount::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    ?: throw IllegalStateException("Corrupt admin account")
            } else {
                val salt = PasswordHasher.generateSalt()
                val hash = PasswordHasher.hashPassword(defaultPassword, salt)
                val payload = mapOf(
                    "username" to "admin",
                    "displayName" to "KKHM College Admin",
                    "role" to UserRole.ADMIN.name,
                    "passwordSalt" to salt,
                    "passwordHash" to hash,
                    "accountStatus" to AccountStatus.ACTIVE.name,
                    "cicAdNo" to "ADMIN-KKHM",
                    "classDepartment" to "Administration",
                    "stream" to "",
                    "assignedGuideUsername" to "",
                    "phone" to "",
                    "boundUid" to uid,
                    "createdByUid" to uid,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
                CollegeAccount(
                    id = "admin",
                    username = "admin",
                    displayName = "KKHM College Admin",
                    role = UserRole.ADMIN.name,
                    passwordSalt = salt,
                    passwordHash = hash,
                    accountStatus = AccountStatus.ACTIVE.name,
                    cicAdNo = "ADMIN-KKHM",
                    classDepartment = "Administration",
                    boundUid = uid,
                    createdByUid = uid
                )
            }
            if (account.accountStatus == AccountStatus.SUSPENDED.name) {
                return Result.failure(IllegalStateException("Admin account is suspended."))
            }
            syncDeviceProfileRole(account)
            Result.success(account)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun saveCollegeAccount(
        rawUsername: String,
        displayName: String,
        role: UserRole,
        rawPasswordOrNull: String?,
        accountStatus: AccountStatus,
        cicAdNo: String,
        classDepartment: String,
        stream: String,
        assignedGuideUsername: String,
        phone: String
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not authenticated"))
        val username = PasswordHasher.normalizeUsername(rawUsername)
        val docPath = "college_accounts/$username"
        return try {
            require(PasswordHasher.isValidUsername(username)) {
                "Username must be 3–40 lowercase letters, digits, '_' or '-'."
            }
            val cleanName = displayName.trim().take(100)
            require(cleanName.isNotEmpty()) { "Full name is required." }

            val cleanStream = if (stream.isBlank()) "" else CicStream.fromLabel(stream).label
            val cleanGuideUser = PasswordHasher.normalizeUsername(assignedGuideUsername).take(40)
            val docRef = db.collection("college_accounts").document(username)
            val existing = docRef.get().await()

            if (existing.exists()) {
                val updates = mutableMapOf<String, Any>(
                    "displayName" to cleanName,
                    "role" to role.name,
                    "accountStatus" to accountStatus.name,
                    "cicAdNo" to cicAdNo.trim().take(40),
                    "classDepartment" to classDepartment.trim().take(80),
                    "stream" to cleanStream,
                    "assignedGuideUsername" to cleanGuideUser,
                    "phone" to phone.trim().take(30),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (!rawPasswordOrNull.isNullOrBlank()) {
                    require(rawPasswordOrNull.length >= 4) { "Password must be at least 4 characters." }
                    val salt = PasswordHasher.generateSalt()
                    updates["passwordSalt"] = salt
                    updates["passwordHash"] = PasswordHasher.hashPassword(rawPasswordOrNull, salt)
                }
                docRef.update(updates).await()
            } else {
                val pwd = rawPasswordOrNull.orEmpty()
                require(pwd.length >= 4) { "Password must be at least 4 characters." }
                val salt = PasswordHasher.generateSalt()
                val hash = PasswordHasher.hashPassword(pwd, salt)
                val payload = mapOf(
                    "username" to username,
                    "displayName" to cleanName,
                    "role" to role.name,
                    "passwordSalt" to salt,
                    "passwordHash" to hash,
                    "accountStatus" to accountStatus.name,
                    "cicAdNo" to cicAdNo.trim().take(40),
                    "classDepartment" to classDepartment.trim().take(80),
                    "stream" to cleanStream,
                    "assignedGuideUsername" to cleanGuideUser,
                    "phone" to phone.trim().take(30),
                    "boundUid" to "",
                    "createdByUid" to uid,
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

    suspend fun resetPassword(username: String, newPassword: String): Result<Unit> {
        val clean = PasswordHasher.normalizeUsername(username)
        val docPath = "college_accounts/$clean"
        return try {
            requireUserId()
            require(newPassword.length >= 4) { "New password must be at least 4 characters." }
            val salt = PasswordHasher.generateSalt()
            val hash = PasswordHasher.hashPassword(newPassword, salt)
            db.collection("college_accounts").document(clean).update(
                mapOf(
                    "passwordSalt" to salt,
                    "passwordHash" to hash,
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

    suspend fun updateAccountStatus(username: String, status: AccountStatus): Result<Unit> {
        val clean = PasswordHasher.normalizeUsername(username)
        val docPath = "college_accounts/$clean"
        return try {
            requireUserId()
            db.collection("college_accounts").document(clean).update(
                mapOf(
                    "accountStatus" to status.name,
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

    suspend fun deleteAccount(username: String): Result<Unit> {
        val clean = PasswordHasher.normalizeUsername(username)
        val docPath = "college_accounts/$clean"
        return try {
            requireUserId()
            require(clean != "admin") { "Primary 'admin' account cannot be deleted." }
            db.collection("college_accounts").document(clean).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun seedSampleAccounts(): Result<List<SeededCredential>> {
        val created = mutableListOf<SeededCredential>()
        return try {
            requireUserId()
            val seeds = listOf(
                SeededCredential("guide1", "guide123", UserRole.GUIDE, "Usthad Abdul Rahman (Guide)"),
                SeededCredential("principal1", "prin123", UserRole.PRINCIPAL, "Dr. Muhammed Ali (Principal)"),
                SeededCredential("student1", "kkhm123", UserRole.STUDENT, "Fathima Zahra"),
                SeededCredential("student2", "kkhm123", UserRole.STUDENT, "Muhammed Anas")
            )
            for (seed in seeds) {
                val docRef = db.collection("college_accounts").document(seed.username)
                if (!docRef.get().await().exists()) {
                    val res = saveCollegeAccount(
                        rawUsername = seed.username,
                        displayName = seed.displayName,
                        role = seed.role,
                        rawPasswordOrNull = seed.rawPassword,
                        accountStatus = AccountStatus.ACTIVE,
                        cicAdNo = if (seed.role == UserRole.STUDENT) "CIC-2026-${seed.username.takeLast(2)}" else "",
                        classDepartment = if (seed.role == UserRole.STUDENT) "Final Wafy / Degree" else "Faculty",
                        stream = if (seed.role == UserRole.STUDENT) CicStream.WAFY.label else "",
                        assignedGuideUsername = if (seed.role == UserRole.STUDENT) "guide1" else "",
                        phone = ""
                    )
                    if (res.isSuccess) created.add(seed)
                }
            }
            Result.success(created)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun bulkImportAccounts(
        rows: List<BulkImportRow>,
        onProgress: (processed: Int, total: Int) -> Unit = { _, _ -> }
    ): BulkImportUiState {
        val validRows = rows.filter { it.isValid }
        val total = validRows.size
        var created = 0
        var updated = 0
        val failed = mutableListOf<Pair<String, String>>()

        validRows.forEachIndexed { index, row ->
            val username = PasswordHasher.normalizeUsername(row.username)
            val alreadyExists = try {
                db.collection("college_accounts").document(username).get().await().exists()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                row.isExistingUpdate
            }

            val res = saveCollegeAccount(
                rawUsername = username,
                displayName = row.displayName,
                role = row.role,
                rawPasswordOrNull = row.passwordOrNull,
                accountStatus = row.accountStatus,
                cicAdNo = row.cicAdNo,
                classDepartment = row.classDepartment,
                stream = row.stream,
                assignedGuideUsername = row.assignedGuideUsername,
                phone = row.phone
            )
            if (res.isSuccess) {
                if (alreadyExists) updated++ else created++
            } else {
                failed += username to (res.exceptionOrNull()?.localizedMessage ?: "Failed to save account")
            }
            onProgress(index + 1, total)
        }

        return BulkImportUiState(
            isImporting = false,
            processedCount = total,
            totalCount = total,
            createdCount = created,
            updatedCount = updated,
            failedRows = failed,
            completed = true
        )
    }

    suspend fun syncActiveAccountSession(account: CollegeAccount) {
        val uid = auth.currentUser?.uid ?: return
        try {
            val resolved = if (!isBootstrappedAdminDevice() &&
                (account.boundUid.isBlank() || account.boundUid == account.createdByUid) &&
                account.boundUid != uid
            ) {
                db.collection("college_accounts").document(account.username).update(
                    mapOf(
                        "boundUid" to uid,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                account.copy(boundUid = uid)
            } else {
                account
            }
            syncDeviceProfileRole(resolved)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "users/$uid")
        }
    }

    private suspend fun syncDeviceProfileRole(account: CollegeAccount) {
        val uid = auth.currentUser?.uid ?: return
        val effectiveRole = if (account.role == UserRole.ADMIN.name) UserRole.PRINCIPAL.name else account.role
        val verifiedPortalUsername = if (account.boundUid == uid || isBootstrappedAdminDevice()) {
            account.username.take(40)
        } else {
            ""
        }
        val docRef = db.collection("users").document(uid)
        val snap = docRef.get().await()
        if (snap.exists()) {
            val existingPortal = snap.getString("portalUsername").orEmpty()
            val existingRole = snap.getString("role").orEmpty()
            val existingName = snap.getString("displayName").orEmpty()
            if (existingPortal == verifiedPortalUsername &&
                existingRole == effectiveRole &&
                existingName == account.displayName.take(100)
            ) {
                return
            }
            docRef.update(
                mapOf(
                    "portalUsername" to verifiedPortalUsername,
                    "displayName" to account.displayName.take(100),
                    "role" to effectiveRole,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } else {
            docRef.set(
                mapOf(
                    "userId" to uid,
                    "portalUsername" to verifiedPortalUsername,
                    "displayName" to account.displayName.take(100),
                    "email" to currentEmailNormalized(),
                    "role" to effectiveRole,
                    "cicAdNo" to account.cicAdNo.take(40),
                    "clgAdNo" to "",
                    "classDepartment" to account.classDepartment.take(80),
                    "stream" to account.stream,
                    "collegeAfflName" to "KKHM Islamic & Arts College (Affiliated to CIC)",
                    "phone" to account.phone.take(30),
                    "whatsapp" to account.phone.take(30),
                    "darkModePreference" to DarkModePreference.SYSTEM.name,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
    }
}
