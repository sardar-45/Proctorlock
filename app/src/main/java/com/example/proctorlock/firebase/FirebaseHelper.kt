package com.example.proctorlock.firebase

import com.example.proctorlock.data.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

object FirebaseHelper {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    // ── Auth ──────────────────────────────────────────────

    fun register(
        name: String,
        email: String,
        password: String,
        role: String,
        onSuccess: (User) -> Unit,
        onError: (String) -> Unit
    ) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                val uid = result.user!!.uid
                val user = User(uid = uid, name = name, email = email, role = role)
                db.collection("users").document(uid).set(user)
                    .addOnSuccessListener { onSuccess(user) }
                    .addOnFailureListener { onError(it.message ?: "Failed to save user") }
            }
            .addOnFailureListener { onError(it.message ?: "Registration failed") }
    }

    fun login(
        email: String,
        password: String,
        onSuccess: (User) -> Unit,
        onError: (String) -> Unit
    ) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                val uid = result.user!!.uid
                db.collection("users").document(uid).get()
                    .addOnSuccessListener { doc ->
                        val user = doc.toObject(User::class.java)
                        if (user != null) onSuccess(user)
                        else onError("User profile not found")
                    }
                    .addOnFailureListener { onError(it.message ?: "Failed to fetch profile") }
            }
            .addOnFailureListener { onError(it.message ?: "Login failed") }
    }

    fun logout() = auth.signOut()

    fun currentUid(): String? = auth.currentUser?.uid

    // ── Exam ──────────────────────────────────────────────

    fun createExam(
        exam: Exam,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val ref = db.collection("exams").document()
        val withId = exam.copy(id = ref.id)
        ref.set(withId)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it.message ?: "Failed to create exam") }
    }

    fun getExams(onResult: (List<Exam>) -> Unit) {
        db.collection("exams")
            .addSnapshotListener { snapshot, _ ->
                val exams = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Exam::class.java)
                } ?: emptyList()
                onResult(exams)
            }
    }

    fun setExamActive(examId: String, active: Boolean, onDone: () -> Unit) {
        db.collection("exams").document(examId)
            .update("active", active)
            .addOnSuccessListener { onDone() }
            .addOnFailureListener { onDone() }
    }

    fun listenForActiveExam(onResult: (Exam?) -> Unit): ListenerRegistration {
        return db.collection("exams")
            .whereEqualTo("active", true)
            .addSnapshotListener { snapshot, _ ->
                val exam = snapshot?.documents?.firstOrNull()?.toObject(Exam::class.java)
                onResult(exam)
            }
    }

    // ── Results ───────────────────────────────────────────

    fun submitResult(
        result: ExamResult,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val ref = db.collection("results").document()
        val withId = result.copy(id = ref.id)
        ref.set(withId)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it.message ?: "Failed to submit result") }
    }

    fun getResults(onResult: (List<ExamResult>) -> Unit) {
        db.collection("results")
            .orderBy("submittedAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, _ ->
                val results = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(ExamResult::class.java)
                } ?: emptyList()
                onResult(results)
            }
    }
    fun hasStudentAttemptedExam(
        examId: String,
        studentId: String,
        onResult: (Boolean) -> Unit
    ) {
        db.collection("results")
            .whereEqualTo("examId", examId)
            .whereEqualTo("studentId", studentId)
            .limit(1)
            .get()
            .addOnSuccessListener { snapshot ->
                onResult(!snapshot.isEmpty)
            }
            .addOnFailureListener {
                onResult(false) // If check fails, allow attempt
            }
    }
}