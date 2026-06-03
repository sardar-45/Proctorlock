package com.example.proctorlock.screens

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.proctorlock.data.*
import com.example.proctorlock.firebase.FirebaseHelper
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.delay

// Tracks what "phase" the student is in — can never go backwards
enum class ExamPhase {
    WAITING,        // no active exam
    READY,          // exam is live, student hasn't started
    IN_PROGRESS,    // student is taking the exam
    SUBMITTED,      // student submitted successfully
    TERMINATED,     // student was caught cheating
    ALREADY_DONE    // student already did this exam before
}

@Composable
fun StudentScreen(user: User, onLogout: () -> Unit) {

    var activeExam by remember { mutableStateOf<Exam?>(null) }
    var phase by remember { mutableStateOf(ExamPhase.WAITING) }
    var finalScore by remember { mutableStateOf(0) }
    var finalViolations by remember { mutableStateOf(0) }
    var totalQuestions by remember { mutableStateOf(0) }
    var checkingAttempt by remember { mutableStateOf(false) }

    // Listen for admin-activated exam in real time
    DisposableEffect(Unit) {
        val reg = FirebaseHelper.listenForActiveExam { exam ->
            activeExam = exam
            // Only update phase if student hasn't already finished
            if (phase != ExamPhase.SUBMITTED &&
                phase != ExamPhase.TERMINATED &&
                phase != ExamPhase.ALREADY_DONE
            ) {
                phase = if (exam != null) ExamPhase.READY else ExamPhase.WAITING
            }
        }
        onDispose { reg.remove() }
    }

    // When a new exam becomes active, check if this student already did it
    LaunchedEffect(activeExam?.id) {
        val examId = activeExam?.id ?: return@LaunchedEffect

        // Don't override a terminal state
        if (phase == ExamPhase.SUBMITTED ||
            phase == ExamPhase.TERMINATED ||
            phase == ExamPhase.ALREADY_DONE
        ) return@LaunchedEffect

        checkingAttempt = true
        FirebaseHelper.hasStudentAttemptedExam(
            examId = examId,
            studentId = user.uid,
            onResult = { attempted ->
                checkingAttempt = false
                phase = if (attempted) ExamPhase.ALREADY_DONE else ExamPhase.READY
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A2E))
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF16213E))
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "Student Portal",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(user.name, fontSize = 13.sp, color = Color.Gray)
            }
            // Only allow logout when NOT in the middle of an exam
            if (phase != ExamPhase.IN_PROGRESS) {
                TextButton(onClick = { FirebaseHelper.logout(); onLogout() }) {
                    Text("Logout", color = Color(0xFFE94560))
                }
            }
        }

        when {
            // ── Checking Firebase ──────────────────────────────────────────
            checkingAttempt -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color.White)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Checking eligibility...", color = Color.Gray)
                    }
                }
            }

            // ── Active exam taking ─────────────────────────────────────────
            phase == ExamPhase.IN_PROGRESS && activeExam != null -> {
                ExamTakingScreen(
                    exam = activeExam!!,
                    student = user,
                    onSubmitted = { score, questions ->
                        finalScore = score
                        totalQuestions = questions
                        phase = ExamPhase.SUBMITTED   // ← locked, can never go back
                    },
                    onTerminated = { violations ->
                        finalViolations = violations
                        phase = ExamPhase.TERMINATED  // ← locked, can never go back
                    }
                )
            }

            // ── Result screen after submission ─────────────────────────────
            phase == ExamPhase.SUBMITTED -> {
                StudentResultScreen(
                    name = user.name,
                    score = finalScore,
                    total = totalQuestions
                )
                // No onBack — screen is a dead end, student must logout
            }

            // ── Terminated screen ──────────────────────────────────────────
            phase == ExamPhase.TERMINATED -> {
                LockedTerminatedScreen(violations = finalViolations)
                // No onBack — screen is a dead end, student must logout
            }

            // ── Already attempted this exam ────────────────────────────────
            phase == ExamPhase.ALREADY_DONE -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Text("✅", fontSize = 56.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Already Submitted",
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "You have already completed this exam.",
                            color = Color.Gray,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            "Each exam can only be taken once.",
                            color = Color.Gray,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // ── Exam is live, student hasn't started ───────────────────────
            phase == ExamPhase.READY && activeExam != null -> {
                WaitingRoomScreen(
                    exam = activeExam!!,
                    onStart = { phase = ExamPhase.IN_PROGRESS }
                )
            }

            // ── No active exam ─────────────────────────────────────────────
            else -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("⏳", fontSize = 48.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "No exam is currently active.",
                            color = Color.White,
                            fontSize = 18.sp
                        )
                        Text(
                            "Please wait for your admin to start one.",
                            color = Color.Gray,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────
// WAITING ROOM
// ─────────────────────────────────────────────
@Composable
fun WaitingRoomScreen(exam: Exam, onStart: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF16213E))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("📋", fontSize = 48.sp)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    exam.title, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                    color = Color.White, textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "${exam.questions.size} questions  •  ${exam.durationMinutes} minutes",
                    color = Color.Gray, fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .background(Color(0xFF1B5E20), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        "🔴 LIVE", color = Color.White,
                        fontSize = 13.sp, fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    "⚠ This exam is proctored.\nYour camera will monitor you during the exam.",
                    color = Color(0xFFFFCC02), fontSize = 13.sp, textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = onStart,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F3460))
                ) {
                    Text("Begin Exam", fontSize = 16.sp)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────
// EXAM TAKING SCREEN
// ─────────────────────────────────────────────
@Composable
fun ExamTakingScreen(
    exam: Exam,
    student: User,
    onSubmitted: (score: Int, total: Int) -> Unit,
    onTerminated: (violations: Int) -> Unit
) {
    val shuffledQuestions = remember { exam.questions.shuffled() }
    val shuffledExam = remember { exam.copy(questions = shuffledQuestions) }

    var statusText by remember { mutableStateOf("Checking face...") }
    var violations by remember { mutableStateOf(0) }
    var examDone by remember { mutableStateOf(false) }  // prevents double-fire
    var timeLeft by remember { mutableStateOf(shuffledExam.durationMinutes * 60) }
    val selectedAnswers = remember { mutableStateListOf<Int?>(*arrayOfNulls(shuffledExam.questions.size)) }

    fun calcScore() = selectedAnswers
        .zip(shuffledExam.questions.map { it.correctIndex })
        .count { (s, c) -> s == c }

    fun submitExam() {
        if (examDone) return   // ← guard: only fires once
        examDone = true
        val score = calcScore()
        saveResult(shuffledExam, student, score, violations, false)
        onSubmitted(score, shuffledExam.questions.size)
    }

    fun terminateExam() {
        if (examDone) return   // ← guard: only fires once
        examDone = true
        saveResult(shuffledExam, student, 0, violations, true)
        onTerminated(violations)
    }

    // Countdown timer
    LaunchedEffect(Unit) {
        while (timeLeft > 0 && !examDone) {
            delay(1000)
            timeLeft--
        }
        if (timeLeft == 0 && !examDone) submitExam()
    }

    ActiveExamScreen(
        exam = shuffledExam,
        statusText = statusText,
        violations = violations,
        timeLeft = timeLeft,
        selectedAnswers = selectedAnswers,
        onStatusUpdate = { if (!examDone) statusText = it },
        onViolation = {
            if (!examDone) {
                violations++
                if (violations >= 10) terminateExam()
            }
        },
        onSubmit = { submitExam() }
    )
}

// ─────────────────────────────────────────────
// ACTIVE EXAM UI
// ─────────────────────────────────────────────
@Composable
fun ActiveExamScreen(
    exam: Exam,
    statusText: String,
    violations: Int,
    timeLeft: Int,
    selectedAnswers: MutableList<Int?>,
    onStatusUpdate: (String) -> Unit,
    onViolation: () -> Unit,
    onSubmit: () -> Unit
) {
    val minutes = timeLeft / 60
    val seconds = timeLeft % 60
    val timerColor = when {
        timeLeft < 60 -> Color(0xFFC62828)
        timeLeft < 300 -> Color(0xFFFF6F00)
        else -> Color(0xFF2E7D32)
    }

    Column(modifier = Modifier.fillMaxSize()) {

        ProctoringCamera(
            isActive = true,
            onStatusUpdate = onStatusUpdate,
            onViolation = onViolation
        )

        // Status + Timer bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF16213E))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val statusColor =
                if (statusText == "FACE DETECTED") Color(0xFF2E7D32) else Color(0xFFC62828)
            Text(statusText, color = statusColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text("Violations: $violations", color = Color.Gray, fontSize = 12.sp)
            Text(
                "%02d:%02d".format(minutes, seconds),
                color = timerColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Scrollable questions
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                exam.title, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                color = Color.White, modifier = Modifier.padding(bottom = 16.dp)
            )

            exam.questions.forEachIndexed { qIndex, question ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF16213E))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "${qIndex + 1}. ${question.text}",
                            color = Color.White, fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        question.options.forEachIndexed { oIndex, option ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                            ) {
                                RadioButton(
                                    selected = selectedAnswers[qIndex] == oIndex,
                                    onClick = { selectedAnswers[qIndex] = oIndex },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = Color(0xFF0F3460)
                                    )
                                )
                                Text(option, color = Color.White, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
        }

        // Submit pinned at bottom
        Button(
            onClick = onSubmit,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F3460))
        ) {
            Text("Submit Exam", fontSize = 16.sp)
        }
    }
}

// ─────────────────────────────────────────────
// PROCTORING CAMERA
// ─────────────────────────────────────────────
@Composable
fun ProctoringCamera(
    isActive: Boolean,
    onStatusUpdate: (String) -> Unit,
    onViolation: () -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val detectorOptions = remember {
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .enableTracking()
            .build()
    }
    val detector = remember { FaceDetection.getClient(detectorOptions) }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val future = ProcessCameraProvider.getInstance(ctx)
            future.addListener({
                val provider = future.get()
                val preview = Preview.Builder().build()
                preview.setSurfaceProvider(previewView.surfaceProvider)

                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                analysis.setAnalyzer(ContextCompat.getMainExecutor(ctx)) { proxy ->
                    if (!isActive) { proxy.close(); return@setAnalyzer }
                    val img = proxy.image
                    if (img != null) {
                        val input = InputImage.fromMediaImage(img, proxy.imageInfo.rotationDegrees)
                        detector.process(input)
                            .addOnSuccessListener { faces ->
                                if (!isActive) return@addOnSuccessListener
                                when {
                                    faces.isEmpty() -> {
                                        onStatusUpdate("NO FACE DETECTED")
                                        onViolation()
                                    }
                                    faces.size > 1 -> {
                                        onStatusUpdate("MULTIPLE FACES DETECTED")
                                        onViolation()
                                    }
                                    else -> {
                                        val y = faces[0].headEulerAngleY
                                        when {
                                            y > 25 -> { onStatusUpdate("LOOKING LEFT"); onViolation() }
                                            y < -25 -> { onStatusUpdate("LOOKING RIGHT"); onViolation() }
                                            else -> onStatusUpdate("FACE DETECTED")
                                        }
                                    }
                                }
                            }
                            .addOnCompleteListener { proxy.close() }
                    } else proxy.close()
                }

                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    preview, analysis
                )
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
    )
}

// ─────────────────────────────────────────────
// TERMINATED SCREEN (no back button — locked)
// ─────────────────────────────────────────────
@Composable
fun LockedTerminatedScreen(violations: Int) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFC62828)),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("⚠", fontSize = 64.sp, color = Color.White)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "EXAM TERMINATED",
            fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text("Too many violations detected", color = Color(0xFFFFCDD2), fontSize = 16.sp)
        Text("Violations: $violations", color = Color(0xFFFFCDD2), fontSize = 14.sp)
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            "Your exam has been recorded.\nPlease contact your admin.",
            color = Color(0xFFFFCDD2),
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        // Only logout is allowed — no way to retry
        Text("Please logout to exit.", color = Color(0xFFFFCDD2), fontSize = 13.sp)
    }
}

// ─────────────────────────────────────────────
// RESULT SCREEN (no back button — locked)
// ─────────────────────────────────────────────
@Composable
fun StudentResultScreen(name: String, score: Int, total: Int) {
    val pct = if (total > 0) (score.toFloat() / total * 100).toInt() else 0
    val passed = pct >= 50

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A2E))
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(130.dp)
                .background(
                    if (passed) Color(0xFF2E7D32) else Color(0xFF616161),
                    RoundedCornerShape(65.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text("$pct%", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            if (passed) "Congratulations!" else "Better luck next time",
            fontSize = 22.sp, fontWeight = FontWeight.Bold,
            color = if (passed) Color(0xFF2E7D32) else Color.Gray
        )
        Text(name, fontSize = 16.sp, color = Color.White, modifier = Modifier.padding(top = 4.dp))
        Spacer(modifier = Modifier.height(24.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF16213E))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Score", color = Color.Gray, fontSize = 14.sp)
                Text(
                    "$score / $total",
                    fontSize = 36.sp, fontWeight = FontWeight.Bold, color = Color.White
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    if (passed) "✓ PASSED" else "✗ FAILED",
                    fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    color = if (passed) Color(0xFF2E7D32) else Color(0xFFC62828)
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text("Your result has been submitted.", color = Color.Gray, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Please logout to exit.", color = Color.Gray, fontSize = 13.sp)
    }
}

fun saveResult(exam: Exam, student: User, score: Int, violations: Int, terminated: Boolean) {
    val result = ExamResult(
        examId = exam.id,
        examTitle = exam.title,
        studentId = student.uid,
        studentName = student.name,
        score = score,
        totalQuestions = exam.questions.size,
        violations = violations,
        terminated = terminated
    )
    FirebaseHelper.submitResult(result, onSuccess = {}, onError = {})
}