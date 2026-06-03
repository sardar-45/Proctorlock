package com.example.proctorlock.data

data class User(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val role: String = "student"   // "admin" or "student"
)

data class Question(
    val id: String = "",
    val text: String = "",
    val options: List<String> = emptyList(),
    val correctIndex: Int = 0
)

data class Exam(
    val id: String = "",
    val title: String = "",
    val durationMinutes: Int = 30,
    val questions: List<Question> = emptyList(),
    val isActive: Boolean = false,
    val createdBy: String = ""
)

data class ExamResult(
    val id: String = "",
    val examId: String = "",
    val examTitle: String = "",
    val studentId: String = "",
    val studentName: String = "",
    val score: Int = 0,
    val totalQuestions: Int = 0,
    val violations: Int = 0,
    val terminated: Boolean = false,
    val submittedAt: Long = System.currentTimeMillis()
)