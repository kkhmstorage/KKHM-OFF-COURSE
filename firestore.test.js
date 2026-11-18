const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const STUDENT_UID = "student_ali_101";
const OTHER_STUDENT_UID = "student_omar_202";
const GUIDE_UID = "guide_usthad_303";
const GUIDE_EMAIL = "usthad.guide@kkhm.edu";
const ADMIN_UID = "admin_kkhm_404";
const ADMIN_EMAIL = "kkhmstorage@gmail.com";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

function buildRecordPayload(now) {
  return {
    studentUid: STUDENT_UID,
    studentUsername: "ali_wafy",
    studentEmail: "ali@student.edu",
    guideUsername: "usthad_rahman",
    guideEmail: GUIDE_EMAIL,
    principalEmail: ADMIN_EMAIL,
    studentName: "Muhammed Ali",
    cicAdNo: "CIC-2026-104",
    clgAdNo: "CLG-441",
    classDepartment: "Degree II - Fiqh",
    academicYear: "2026 - 2027",
    semester: "Nov",
    stream: "Wafy",
    collegeAfflName: "KKHM Islamic & Arts College (Affiliated to CIC)",
    subjectTitle: "Comparative Islamic Jurisprudence",
    proposalAppNo: "OC-2026-88",
    credit: 4,
    guideName: "Usthad Rahman Wafy",
    guidePhone: "+919876543210",
    guideWhatsapp: "+919876543210",
    referenceBooks: "Al-Muwafaqat, Bidayat al-Mujtahid",
    timetableSlots: ["Monday:2", "Wednesday:5", "Thursday:7"],
    periodsPerWeek: 3,
    guideSignatureStatus: "PENDING",
    principalSignatureStatus: "PENDING",
    recordStatus: "ACTIVE",
    completedWeeksCount: 0,
    verifiedWeeksCount: 0,
    totalAllocatedPeriods: 0,
    totalUtilizedPeriods: 0,
    averageFocusRating: 0.0,
    createdAt: now,
    updatedAt: now,
  };
}

test("Unauthenticated users are denied on all collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(STUDENT_UID).get());
  await assertFails(unauthDb.collection("college_accounts").doc("ali_wafy").get());
  await assertFails(unauthDb.collection("academic_terms").where("visibility", "==", "campus").get());
  await assertFails(unauthDb.collection("off_course_records").where("studentUid", "==", STUDENT_UID).get());
});

test("User profile cannot self-escalate to ADMIN", async () => {
  const now = new Date(Date.now() - 2000);
  const studentDb = testEnv.authenticatedContext(STUDENT_UID, {
    email: "ali@student.edu",
    email_verified: true,
  }).firestore();

  await assertFails(
    studentDb.collection("users").doc(STUDENT_UID).set({
      userId: STUDENT_UID,
      displayName: "Ali",
      email: "ali@student.edu",
      role: "ADMIN",
      darkModePreference: "SYSTEM",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    studentDb.collection("users").doc(STUDENT_UID).set({
      userId: STUDENT_UID,
      displayName: "Ali",
      email: "ali@student.edu",
      role: "STUDENT",
      darkModePreference: "SYSTEM",
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("College accounts: Admin creates & lists credentials; non-admin cannot list all or escalate roles", async () => {
  const now = new Date(Date.now() - 2000);
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, {
    email: ADMIN_EMAIL,
    email_verified: true,
  }).firestore();
  const studentDb = testEnv.authenticatedContext(STUDENT_UID, {
    email: "ali@student.edu",
    email_verified: true,
  }).firestore();

  // Admin creates student credential
  await assertSucceeds(
    adminDb.collection("college_accounts").doc("ali_wafy").set({
      username: "ali_wafy",
      displayName: "Muhammed Ali",
      role: "STUDENT",
      passwordSalt: "random_salt_1234",
      passwordHash: "a".repeat(64),
      accountStatus: "ACTIVE",
      cicAdNo: "CIC-2026-104",
      classDepartment: "Degree II",
      stream: "Wafy",
      assignedGuideUsername: "usthad_rahman",
      phone: "+919876543210",
      boundUid: "",
      createdByUid: ADMIN_UID,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Admin can list all college accounts
  await assertSucceeds(adminDb.collection("college_accounts").get());

  // Student cannot list all college accounts or create arbitrary student/admin accounts
  await assertFails(studentDb.collection("college_accounts").get());
  await assertFails(
    studentDb.collection("college_accounts").doc("rogue_user").set({
      username: "rogue_user",
      displayName: "Rogue",
      role: "ADMIN",
      passwordSalt: "salt_123456",
      passwordHash: "b".repeat(64),
      accountStatus: "ACTIVE",
      boundUid: "",
      createdByUid: STUDENT_UID,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Student can look up their own username at login & bind their UID once
  await assertSucceeds(studentDb.collection("college_accounts").doc("ali_wafy").get());
  await assertSucceeds(
    studentDb.collection("college_accounts").doc("ali_wafy").update({
      boundUid: STUDENT_UID,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Student cannot change their own role or accountStatus
  await assertFails(
    studentDb.collection("college_accounts").doc("ali_wafy").update({
      role: "ADMIN",
      updatedAt: new Date(Date.now() - 300),
    })
  );
});

test("Off-Course Record & Weekly Reports: Student, Guide, Admin isolation & verification", async () => {
  const now = new Date(Date.now() - 5000);
  const studentDb = testEnv.authenticatedContext(STUDENT_UID, {
    email: "ali@student.edu",
    email_verified: true,
  }).firestore();
  const otherDb = testEnv.authenticatedContext(OTHER_STUDENT_UID, {
    email: "omar@student.edu",
    email_verified: true,
  }).firestore();
  const guideDb = testEnv.authenticatedContext(GUIDE_UID, {
    email: GUIDE_EMAIL,
    email_verified: true,
  }).firestore();
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, {
    email: ADMIN_EMAIL,
    email_verified: true,
  }).firestore();

  const recordId = "rec_ali_kkhm_2026";
  await assertSucceeds(
    studentDb.collection("off_course_records").doc(recordId).set(buildRecordPayload(now))
  );

  await assertSucceeds(
    studentDb
      .collection("off_course_records")
      .doc(recordId)
      .collection("weekly_reports")
      .doc("week_01")
      .set({
        recordId: recordId,
        studentUid: STUDENT_UID,
        studentUsername: "ali_wafy",
        guideUsername: "usthad_rahman",
        guideEmail: GUIDE_EMAIL,
        principalEmail: ADMIN_EMAIL,
        weekNumber: 1,
        dateFrom: "2026-11-02",
        dateTo: "2026-11-08",
        description: "Studied Usul al-Fiqh Chapter 1.",
        allocatedPeriods: 3,
        utilizedPeriods: 3,
        focusRating: 5,
        utilizationNotes: "All 3 periods effective.",
        periodLogs: ["Monday|P2|EFFECTIVE|45m|Notes"],
        verificationStatus: "PENDING",
        createdAt: now,
        updatedAt: now,
      })
  );

  // Guide verifies week 1
  await assertSucceeds(
    guideDb
      .collection("off_course_records")
      .doc(recordId)
      .collection("weekly_reports")
      .doc("week_01")
      .update({
        verificationStatus: "VERIFIED",
        authorizedSignature: "Usthad Rahman Wafy",
        mentorFeedback: "Good work",
        verifiedAt: new Date(Date.now() - 500),
        updatedAt: new Date(Date.now() - 500),
      })
  );

  // Admin can reassign guide and manage any record
  await assertSucceeds(
    adminDb.collection("off_course_records").doc(recordId).update({
      guideUsername: "usthad_karim",
      guideName: "Usthad Karim Wafy",
      updatedAt: new Date(Date.now() - 300),
    })
  );

  // Other student cannot read or modify Ali's record
  await assertFails(otherDb.collection("off_course_records").doc(recordId).get());
  await assertFails(otherDb.collection("off_course_records").get());
});

test("Guide sees records assigned via guideUsername even when guideEmail is empty; rogue cannot hijack portalUsername", async () => {
  const now = new Date(Date.now() - 5000);
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, {
    email: ADMIN_EMAIL,
    email_verified: true,
  }).firestore();
  const studentDb = testEnv.authenticatedContext(STUDENT_UID, {
    email: "ali@student.edu",
    email_verified: true,
  }).firestore();
  const guideDb = testEnv.authenticatedContext(GUIDE_UID, {
    email: "teacher.personal@gmail.com",
    email_verified: true,
  }).firestore();
  const rogueDb = testEnv.authenticatedContext(OTHER_STUDENT_UID, {
    email: "omar@student.edu",
    email_verified: true,
  }).firestore();

  // Admin creates guide1 account
  await assertSucceeds(
    adminDb.collection("college_accounts").doc("guide1").set({
      username: "guide1",
      displayName: "Usthad Abdul Rahman",
      role: "GUIDE",
      passwordSalt: "salt_guide_1234",
      passwordHash: "c".repeat(64),
      accountStatus: "ACTIVE",
      cicAdNo: "",
      classDepartment: "Faculty",
      stream: "",
      assignedGuideUsername: "",
      phone: "+919800000001",
      boundUid: "",
      createdByUid: ADMIN_UID,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Guide binds UID on college_accounts/guide1 and sets verified portalUsername in users/{guideUid}
  await assertSucceeds(
    guideDb.collection("college_accounts").doc("guide1").update({
      boundUid: GUIDE_UID,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertSucceeds(
    guideDb.collection("users").doc(GUIDE_UID).set({
      userId: GUIDE_UID,
      portalUsername: "guide1",
      displayName: "Usthad Abdul Rahman",
      email: "teacher.personal@gmail.com",
      role: "GUIDE",
      darkModePreference: "SYSTEM",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Rogue user cannot hijack portalUsername = guide1
  await assertFails(
    rogueDb.collection("users").doc(OTHER_STUDENT_UID).set({
      userId: OTHER_STUDENT_UID,
      portalUsername: "guide1",
      displayName: "Rogue",
      email: "omar@student.edu",
      role: "GUIDE",
      darkModePreference: "SYSTEM",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Student submits record with guideUsername = "guide1" and EMPTY guideEmail
  const payload = buildRecordPayload(now);
  payload.guideUsername = "guide1";
  payload.guideEmail = "";
  const recordId = "rec_username_only_guide";
  await assertSucceeds(
    studentDb.collection("off_course_records").doc(recordId).set(payload)
  );
  await assertSucceeds(
    studentDb
      .collection("off_course_records")
      .doc(recordId)
      .collection("weekly_reports")
      .doc("week_01")
      .set({
        recordId: recordId,
        studentUid: STUDENT_UID,
        studentUsername: "ali_wafy",
        guideUsername: "guide1",
        guideEmail: "",
        principalEmail: ADMIN_EMAIL,
        weekNumber: 1,
        dateFrom: "02/11/2026",
        dateTo: "08/11/2026",
        description: "Mon P2: Studied Kitab al-Ilm",
        allocatedPeriods: 2,
        utilizedPeriods: 2,
        focusRating: 5,
        utilizationNotes: "Done",
        periodLogs: ["Monday|P2|EFFECTIVE|45m|Studied Kitab al-Ilm"],
        verificationStatus: "PENDING",
        createdAt: now,
        updatedAt: now,
      })
  );

  // Guide can list, read, and verify both record & weekly_reports using guideUsername query!
  await assertSucceeds(
    guideDb.collection("off_course_records").where("guideUsername", "==", "guide1").get()
  );
  await assertSucceeds(
    guideDb
      .collection("off_course_records")
      .doc(recordId)
      .collection("weekly_reports")
      .where("guideUsername", "==", "guide1")
      .get()
  );
  await assertSucceeds(
    guideDb
      .collection("off_course_records")
      .doc(recordId)
      .collection("weekly_reports")
      .doc("week_01")
      .update({
        verificationStatus: "VERIFIED",
        authorizedSignature: "Usthad Abdul Rahman",
        mentorFeedback: "Clear period log!",
        verifiedAt: new Date(Date.now() - 500),
        updatedAt: new Date(Date.now() - 500),
      })
  );
});

