package com.example.proctorlock.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.proctorlock.data.*
import com.example.proctorlock.firebase.FirebaseHelper

@Composable
fun AdminScreen(user: User, onLogout: () -> Unit) {

    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Exams", "Results")

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
                Text("Admin Panel", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(user.name, fontSize = 13.sp, color = Color.Gray)
            }
            TextButton(onClick = { FirebaseHelper.logout(); onLogout() }) {
                Text("Logout", color = Color(0xFFE94560))
            }
        }

        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color(0xFF16213E),
            contentColor = Color.White
        ) {
            tabs.forEachIndexed { i, title ->
                Tab(selected = selectedTab == i, onClick = { selectedTab = i },
                    text = { Text(title) })
            }
        }

        when (selectedTab) {
            0 -> ExamsTab(adminId = user.uid)
            1 -> ResultsTab()
        }
    }
}

// ── Exams Tab ─────────────────────────────────────────────────────────────────

@Composable
fun ExamsTab(adminId: String) {
    var exams by remember { mutableStateOf<List<Exam>>(emptyList()) }
    var showCreateDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        FirebaseHelper.getExams { exams = it }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (exams.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No exams yet. Tap + to create one.", color = Color.Gray)
            }
        } else {
            LazyColumn(modifier = Modifier.padding(16.dp)) {
                items(exams) { exam ->
                    ExamCard(exam = exam)
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }

        FloatingActionButton(
            onClick = { showCreateDialog = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            containerColor = Color(0xFF0F3460)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Create Exam", tint = Color.White)
        }
    }

    if (showCreateDialog) {
        CreateExamDialog(
            adminId = adminId,
            onDismiss = { showCreateDialog = false }
        )
    }
}

@Composable
fun ExamCard(exam: Exam) {
    var active by remember(exam.isActive) { mutableStateOf(exam.isActive) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF16213E))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(exam.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("${exam.questions.size} questions  •  ${exam.durationMinutes} min",
                        fontSize = 13.sp, color = Color.Gray)
                }
                Box(
                    modifier = Modifier
                        .background(
                            if (active) Color(0xFF2E7D32) else Color(0xFF424242),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(if (active) "LIVE" else "OFF", color = Color.White, fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        active = !active
                        FirebaseHelper.setExamActive(exam.id, active) {}
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (active) Color(0xFFC62828) else Color(0xFF2E7D32)
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (active) "Stop Exam" else "Start Exam")
                }
            }
        }
    }
}

// ── Create Exam Dialog ────────────────────────────────────────────────────────

@Composable
fun CreateExamDialog(adminId: String, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var duration by remember { mutableStateOf("30") }
    var questions by remember { mutableStateOf(listOf(blankQuestion())) }
    var errorMsg by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF16213E),
        title = { Text("Create Exam", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp)
            ) {
                LazyColumn {
                    item {
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text("Exam Title", color = Color.Gray) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = dialogFieldColors()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = duration,
                            onValueChange = { duration = it },
                            label = { Text("Duration (minutes)", color = Color.Gray) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = dialogFieldColors()
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Questions", color = Color.White, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    items(questions.size) { qIndex ->
                        QuestionEditor(
                            question = questions[qIndex],
                            index = qIndex,
                            onUpdate = { updated ->
                                questions = questions.toMutableList().also { it[qIndex] = updated }
                            },
                            onDelete = {
                                if (questions.size > 1)
                                    questions = questions.toMutableList().also { it.removeAt(qIndex) }
                            }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    item {
                        TextButton(
                            onClick = { questions = questions + blankQuestion() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("+ Add Question", color = Color(0xFF0F3460))
                        }
                        if (errorMsg.isNotEmpty()) {
                            Text(errorMsg, color = Color.Red, fontSize = 13.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isBlank()) { errorMsg = "Enter exam title"; return@Button }
                    val dur = duration.toIntOrNull() ?: 0
                    if (dur <= 0) { errorMsg = "Enter valid duration"; return@Button }
                    if (questions.any { it.text.isBlank() }) {
                        errorMsg = "All questions need text"; return@Button
                    }
                    saving = true
                    val exam = Exam(
                        title = title,
                        durationMinutes = dur,
                        questions = questions,
                        createdBy = adminId
                    )
                    FirebaseHelper.createExam(exam,
                        onSuccess = { saving = false; onDismiss() },
                        onError = { saving = false; errorMsg = it }
                    )
                },
                enabled = !saving,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F3460))
            ) {
                Text(if (saving) "Saving..." else "Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color.Gray) }
        }
    )
}

@Composable
fun QuestionEditor(
    question: Question,
    index: Int,
    onUpdate: (Question) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Q${index + 1}", color = Color(0xFF0F3460), fontWeight = FontWeight.Bold)
                IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
                }
            }
            OutlinedTextField(
                value = question.text,
                onValueChange = { onUpdate(question.copy(text = it)) },
                label = { Text("Question", color = Color.Gray) },
                modifier = Modifier.fillMaxWidth(),
                colors = dialogFieldColors()
            )
            Spacer(modifier = Modifier.height(8.dp))
            question.options.forEachIndexed { oIndex, option ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    RadioButton(
                        selected = question.correctIndex == oIndex,
                        onClick = { onUpdate(question.copy(correctIndex = oIndex)) },
                        colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF2E7D32))
                    )
                    OutlinedTextField(
                        value = option,
                        onValueChange = { newVal ->
                            val newOptions = question.options.toMutableList()
                            newOptions[oIndex] = newVal
                            onUpdate(question.copy(options = newOptions))
                        },
                        label = { Text("Option ${oIndex + 1}", color = Color.Gray) },
                        modifier = Modifier.weight(1f),
                        colors = dialogFieldColors()
                    )
                }
            }
            Text("● Green radio = correct answer", color = Color.Gray, fontSize = 11.sp,
                modifier = Modifier.padding(top = 4.dp))
        }
    }
}

private fun blankQuestion() = Question(
    text = "",
    options = listOf("", "", "", ""),
    correctIndex = 0
)

@Composable
private fun dialogFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedBorderColor = Color(0xFF0F3460),
    unfocusedBorderColor = Color.Gray
)

// ── Results Tab ───────────────────────────────────────────────────────────────

@Composable
fun ResultsTab() {
    var results by remember { mutableStateOf<List<ExamResult>>(emptyList()) }

    LaunchedEffect(Unit) {
        FirebaseHelper.getResults { results = it }
    }

    if (results.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No submissions yet.", color = Color.Gray)
        }
    } else {
        LazyColumn(modifier = Modifier.padding(16.dp)) {
            items(results) { result ->
                ResultCard(result)
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }
}

@Composable
fun ResultCard(result: ExamResult) {
    val pct = if (result.totalQuestions > 0)
        (result.score.toFloat() / result.totalQuestions * 100).toInt() else 0
    val passed = pct >= 50 && !result.terminated

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF16213E))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(
                        if (result.terminated) Color(0xFF6A0000)
                        else if (passed) Color(0xFF1B5E20)
                        else Color(0xFF424242),
                        RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "$pct%",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(result.studentName, color = Color.White, fontWeight = FontWeight.Bold)
                Text(result.examTitle, color = Color.Gray, fontSize = 13.sp)
                Text(
                    "${result.score}/${result.totalQuestions}  •  ${result.violations} violations" +
                            if (result.terminated) "  • TERMINATED" else "",
                    color = if (result.terminated) Color.Red else Color.Gray,
                    fontSize = 12.sp
                )
            }

            Box(
                modifier = Modifier
                    .background(
                        if (result.terminated) Color(0xFFC62828)
                        else if (passed) Color(0xFF2E7D32) else Color(0xFF616161),
                        RoundedCornerShape(6.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    if (result.terminated) "TERM." else if (passed) "PASS" else "FAIL",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}