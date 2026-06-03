package com.example.proctorlock.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.proctorlock.data.User
import com.example.proctorlock.firebase.FirebaseHelper

@Composable
fun AuthScreen(onLoggedIn: (User) -> Unit) {

    var isLogin by remember { mutableStateOf(true) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf("student") }
    var errorMsg by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A2E)),
        contentAlignment = Alignment.Center
    ) {
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

                Text(
                    "🔒 Proctorlock",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F3460)
                )
                Text(
                    if (isLogin) "Sign in to continue" else "Create your account",
                    fontSize = 13.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                // Name (register only)
                if (!isLogin) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Full Name", color = Color.Gray) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = outlinedTextFieldColors()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email", color = Color.Gray) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = outlinedTextFieldColors()
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password", color = Color.Gray) },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = outlinedTextFieldColors()
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Role selector (register only)
                if (!isLogin) {
                    Text("Register as:", color = Color.Gray, fontSize = 13.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = selectedRole == "student",
                            onClick = { selectedRole = "student" },
                            colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF0F3460))
                        )
                        Text("Student", color = Color.White)
                        Spacer(modifier = Modifier.width(16.dp))
                        RadioButton(
                            selected = selectedRole == "admin",
                            onClick = { selectedRole = "admin" },
                            colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF0F3460))
                        )
                        Text("Admin", color = Color.White)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Error message
                if (errorMsg.isNotEmpty()) {
                    Text(errorMsg, color = Color.Red, fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 8.dp))
                }

                // Action button
                Button(
                    onClick = {
                        errorMsg = ""
                        loading = true
                        if (isLogin) {
                            FirebaseHelper.login(email, password,
                                onSuccess = { loading = false; onLoggedIn(it) },
                                onError = { loading = false; errorMsg = it }
                            )
                        } else {
                            if (name.isBlank()) {
                                loading = false; errorMsg = "Please enter your name"; return@Button
                            }
                            FirebaseHelper.register(name, email, password, selectedRole,
                                onSuccess = { loading = false; onLoggedIn(it) },
                                onError = { loading = false; errorMsg = it }
                            )
                        }
                    },
                    enabled = email.isNotBlank() && password.isNotBlank() && !loading,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F3460))
                ) {
                    Text(if (loading) "Please wait..." else if (isLogin) "Login" else "Register",
                        color = Color.White)
                }

                Spacer(modifier = Modifier.height(12.dp))

                TextButton(onClick = {
                    isLogin = !isLogin
                    errorMsg = ""
                }) {
                    Text(
                        if (isLogin) "Don't have an account? Register" else "Already have an account? Login",
                        color = Color(0xFF0F3460)
                    )
                }
            }
        }
    }
}

@Composable
private fun outlinedTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedBorderColor = Color(0xFF0F3460),
    unfocusedBorderColor = Color.Gray
)